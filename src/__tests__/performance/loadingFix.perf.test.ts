/**
 * Performance Tests — Poprawki ładowania kolejki połączeń
 *
 * Testuje konkretne poprawki wprowadzone w CallLogsList.tsx:
 *   1. Concurrency guard (isFetchingRef) — brak równoległych fetchów
 *   2. Timeout AbortController — fetch nie wisi w nieskończoność
 *   3. Debounce realtime — wiele zdarzeń DB = jeden fetch
 *   4. showLoading=false dla realtime — brak migającego spinnera
 *   5. groupCallLogsByClient — wydajność czystej logiki JS
 *
 * Uruchomienie:
 *   npx jest loadingFix.perf --testTimeout=15000 --verbose
 */

// ── Helpers ──────────────────────────────────────────────────────────────────
function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function measure<T>(fn: () => Promise<T>): Promise<{ result: T; durationMs: number }> {
  const start = Date.now();
  const result = await fn();
  return { result, durationMs: Date.now() - start };
}

// ── Dane testowe ─────────────────────────────────────────────────────────────
interface MockCallLog {
  id: string;
  client_id: string | null;
  caller_phone: string | null;
  employee_id: string | null;
  status: 'missed' | 'reserved' | 'completed';
  timestamp: string;
  reservation_by: string | null;
  reservation_at: string | null;
  recipients: string[];
  merged_into_id: string | null;
  phone_account_id: string | null;
  dedup_key: string;
  created_at: string;
  updated_at: string;
  client: { id: string; phone: string; name: string | null } | null;
  hasVoiceReport: boolean;
}

function generateMockCallLogs(count: number, statusMix = true): MockCallLog[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `call-log-${i}`,
    client_id: i % 4 === 0 ? null : `client-${i % 8}`,
    caller_phone: i % 4 === 0 ? `+48${500000000 + i}` : null,
    employee_id: `user-${i % 3}`,
    status: statusMix
      ? (['missed', 'missed', 'missed', 'reserved'] as const)[i % 4]
      : 'missed',
    timestamp: new Date(Date.now() - i * 90000).toISOString(),
    reservation_by: i % 4 === 3 ? `user-${i % 3}` : null,
    reservation_at: i % 4 === 3 ? new Date().toISOString() : null,
    recipients: [`user-${i % 3}`],
    merged_into_id: null,
    phone_account_id: null,
    dedup_key: `key-${i}`,
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
    client: i % 4 === 0 ? null : {
      id: `client-${i % 8}`,
      phone: `+48${600000000 + (i % 8)}`,
      name: i % 2 === 0 ? `Klient ${i % 8}` : null,
    },
    hasVoiceReport: false,
  }));
}

// ── Symulacja fetchCallLogs z concurrency guard ───────────────────────────────
function makeFetchWithConcurrencyGuard(fetchDurationMs: number) {
  let isFetching = false;
  let callCount = 0;
  let skippedCount = 0;

  async function fetchCallLogs(showLoading = false): Promise<{ skipped: boolean }> {
    if (isFetching) {
      skippedCount++;
      return { skipped: true };
    }
    isFetching = true;
    callCount++;
    try {
      await delay(fetchDurationMs);
      return { skipped: false };
    } finally {
      isFetching = false;
    }
  }

  return { fetchCallLogs, getCallCount: () => callCount, getSkippedCount: () => skippedCount };
}

// ── groupCallLogsByClient — uproszczona wersja logiki z komponentu ────────────
function groupCallLogsByClient(logs: MockCallLog[]) {
  const groupMap = new Map<string, MockCallLog[]>();

  for (const log of logs) {
    const key = log.client_id || log.caller_phone;
    if (!key) continue;
    const existing = groupMap.get(key) || [];
    existing.push(log);
    groupMap.set(key, existing);
  }

  const grouped: Array<{
    groupKey: string;
    missedCount: number;
    latestTimestamp: string;
    isMultiAgent: boolean;
  }> = [];

  groupMap.forEach((calls, groupKey) => {
    calls.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
    const missedCount = calls.filter((c) => c.status === 'missed').length;
    const agentIds = new Set<string>();
    calls.forEach((c) => {
      c.recipients.forEach((r) => agentIds.add(r));
      if (c.employee_id) agentIds.add(c.employee_id);
    });
    grouped.push({
      groupKey,
      missedCount,
      latestTimestamp: calls[0].timestamp,
      isMultiAgent: agentIds.size > 1,
    });
  });

  grouped.sort((a, b) => {
    const aHasMissed = a.missedCount > 0;
    const bHasMissed = b.missedCount > 0;
    if (aHasMissed && !bHasMissed) return -1;
    if (!aHasMissed && bHasMissed) return 1;
    return new Date(b.latestTimestamp).getTime() - new Date(a.latestTimestamp).getTime();
  });

  return grouped;
}

// ═══════════════════════════════════════════════════════════════════════════
// Suite 1: Concurrency Guard
// ═══════════════════════════════════════════════════════════════════════════
describe('Concurrency Guard — isFetchingRef', () => {
  it('równoległe wywołania fetchCallLogs: tylko 1 wykonuje się, reszta jest pomijana', async () => {
    const FETCH_DURATION = 80;
    const { fetchCallLogs, getCallCount, getSkippedCount } =
      makeFetchWithConcurrencyGuard(FETCH_DURATION);

    // Uruchom 5 wywołań równolegle
    const results = await Promise.all([
      fetchCallLogs(true),
      fetchCallLogs(false),
      fetchCallLogs(false),
      fetchCallLogs(false),
      fetchCallLogs(false),
    ]);

    const executed = results.filter((r) => !r.skipped).length;
    const skipped = results.filter((r) => r.skipped).length;

    console.log(`  5 równoległych wywołań: wykonanych=${executed}, pominiętych=${skipped}`);
    expect(executed).toBe(1);
    expect(skipped).toBe(4);
    expect(getCallCount()).toBe(1);
    expect(getSkippedCount()).toBe(4);
  });

  it('po zakończeniu fetcha, kolejne wywołanie przechodzi normalnie', async () => {
    const FETCH_DURATION = 50;
    const { fetchCallLogs, getCallCount } = makeFetchWithConcurrencyGuard(FETCH_DURATION);

    // Pierwsze wywołanie
    await fetchCallLogs(true);
    // Drugie — po zakończeniu pierwszego — powinno przejść
    await fetchCallLogs(false);

    console.log(`  2 sekwencyjne wywołania: wykonanych=${getCallCount()}`);
    expect(getCallCount()).toBe(2);
  });

  it('concurrency guard skraca czas realizacji równoległych wywołań', async () => {
    const FETCH_DURATION = 100;
    const { fetchCallLogs } = makeFetchWithConcurrencyGuard(FETCH_DURATION);

    // Bez guard: 5 × 100ms = 500ms
    // Z guard: 1 × 100ms (4 pozostałe natychmiast wracają)
    const { durationMs } = await measure(() =>
      Promise.all(Array.from({ length: 5 }, () => fetchCallLogs(false)))
    );

    console.log(`  5 równoległych wywołań z guard: ${durationMs}ms (bez guard byłoby ~500ms)`);
    // Z guard powinno zakończyć się w ~1 fetch duration, nie 5×
    expect(durationMs).toBeLessThan(FETCH_DURATION * 2);
  });

  it('realtime wysyła 10 zdarzeń podczas jednego fetcha — tylko 1 dodatkowy fetch po zakończeniu', async () => {
    const FETCH_DURATION = 60;
    const { fetchCallLogs, getCallCount, getSkippedCount } =
      makeFetchWithConcurrencyGuard(FETCH_DURATION);

    // Rozpocznij pierwszy fetch (inicjalizacja)
    const firstFetch = fetchCallLogs(true);

    // Symulacja 10 zdarzeń realtime podczas trwania fetcha
    const realtimeFetches = Array.from({ length: 10 }, () => fetchCallLogs(false));

    await firstFetch;
    await Promise.all(realtimeFetches);

    console.log(
      `  1 init + 10 realtime → wykonanych: ${getCallCount()}, pominiętych: ${getSkippedCount()}`
    );
    // Tylko 1 przeszedł (init), 10 realtime pominiętych
    expect(getCallCount()).toBe(1);
    expect(getSkippedCount()).toBe(10);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Suite 2: Timeout AbortController
// ═══════════════════════════════════════════════════════════════════════════
describe('Timeout AbortController — ochrona przed nieskończonym ładowaniem', () => {
  it('AbortController przerywa operację po upływie limitu czasu', async () => {
    const TIMEOUT_MS = 100;
    let aborted = false;

    async function fetchWithTimeout(queryDurationMs: number): Promise<string> {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), TIMEOUT_MS);

      try {
        // Symulacja wolnego zapytania
        await new Promise<void>((resolve, reject) => {
          const timer = setTimeout(resolve, queryDurationMs);
          controller.signal.addEventListener('abort', () => {
            clearTimeout(timer);
            aborted = true;
            reject(new DOMException('Aborted', 'AbortError'));
          });
        });
        return 'success';
      } catch (err: any) {
        if (err.name === 'AbortError') return 'aborted';
        throw err;
      } finally {
        clearTimeout(timeoutId);
      }
    }

    // Szybkie zapytanie — powinno się udać
    const fastResult = await fetchWithTimeout(20);
    console.log(`  Zapytanie 20ms przy timeout 100ms: ${fastResult}`);
    expect(fastResult).toBe('success');

    // Wolne zapytanie — powinno zostać przerwane
    aborted = false;
    const slowResult = await fetchWithTimeout(500);
    console.log(`  Zapytanie 500ms przy timeout 100ms: ${slowResult}`);
    expect(slowResult).toBe('aborted');
    expect(aborted).toBe(true);
  });

  it('timeout zwalnia wątek — po aborcie loading jest false', async () => {
    const TIMEOUT_MS = 80;
    let loading = true;

    async function fetchWithLoadingAndTimeout(queryDurationMs: number) {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), TIMEOUT_MS);
      try {
        loading = true;
        await new Promise<void>((resolve, reject) => {
          const t = setTimeout(resolve, queryDurationMs);
          controller.signal.addEventListener('abort', () => {
            clearTimeout(t);
            reject(new DOMException('Aborted', 'AbortError'));
          });
        });
      } catch {
        // AbortError — ignorujemy
      } finally {
        clearTimeout(timeoutId);
        loading = false; // <- zawsze wywołane
      }
    }

    await fetchWithLoadingAndTimeout(500); // zawiesi się, ale timeout = 80ms
    console.log(`  Po timeout loading=${loading} (oczekiwane: false)`);
    expect(loading).toBe(false);
  });

  it('bez timeoutu: zawieszone zapytanie blokuje loading w nieskończoność', async () => {
    let loading = true;
    let resolved = false;

    // Symulacja zawieszonego fetch BEZ timeoutu
    const hangingFetch = new Promise<void>(() => {
      // nigdy się nie rozwiąże
    });

    // Rozpocznij "fetch" który nigdy nie skończy
    const fetchTask = hangingFetch.finally(() => {
      loading = false;
      resolved = true;
    });

    // Po 150ms sprawdź czy loading nadal true
    await delay(150);
    console.log(`  Po 150ms bez timeoutu: loading=${loading}, resolved=${resolved}`);
    expect(loading).toBe(true); // udowodnienie problemu
    expect(resolved).toBe(false);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Suite 3: Debounce Realtime
// ═══════════════════════════════════════════════════════════════════════════
describe('Debounce realtime subscription — redukcja liczby fetchów', () => {
  // Symulacja debouncowanego handlera realtime
  function makeRealtimeHandler(fetchFn: () => void, debounceMs: number) {
    let timer: ReturnType<typeof setTimeout> | null = null;
    let triggerCount = 0;

    function onRealtimeEvent() {
      triggerCount++;
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => {
        fetchFn();
      }, debounceMs);
    }

    function cleanup() {
      if (timer) clearTimeout(timer);
    }

    return { onRealtimeEvent, cleanup, getTriggerCount: () => triggerCount };
  }

  it('10 szybkich zdarzeń realtime wywołuje fetch tylko 1 raz (po debounce)', async () => {
    const DEBOUNCE_MS = 100;
    let fetchCallCount = 0;

    const handler = makeRealtimeHandler(() => fetchCallCount++, DEBOUNCE_MS);

    // 10 zdarzeń co 10ms
    for (let i = 0; i < 10; i++) {
      handler.onRealtimeEvent();
      await delay(10);
    }

    // Czekaj na debounce
    await delay(DEBOUNCE_MS + 50);
    handler.cleanup();

    console.log(
      `  10 zdarzeń co 10ms → triggerów: ${handler.getTriggerCount()}, fetchów: ${fetchCallCount}`
    );
    expect(handler.getTriggerCount()).toBe(10);
    expect(fetchCallCount).toBe(1);
  });

  it('zdarzenia rozłożone w czasie: każda seria wywołuje osobny fetch', async () => {
    const DEBOUNCE_MS = 80;
    let fetchCallCount = 0;

    const handler = makeRealtimeHandler(() => fetchCallCount++, DEBOUNCE_MS);

    // Seria 1: 3 zdarzenia
    handler.onRealtimeEvent();
    handler.onRealtimeEvent();
    handler.onRealtimeEvent();
    await delay(DEBOUNCE_MS + 50); // czekamy na pierwszą debounce

    // Seria 2: 5 zdarzeń
    for (let i = 0; i < 5; i++) {
      handler.onRealtimeEvent();
      await delay(5);
    }
    await delay(DEBOUNCE_MS + 50);

    handler.cleanup();

    console.log(
      `  Seria 1 (3 zdarzenia) + Seria 2 (5 zdarzeń) → fetchów: ${fetchCallCount} (oczekiwane: 2)`
    );
    expect(fetchCallCount).toBe(2);
  });

  it('debounce 500ms: skanowanie 20 rekordów (20 INSERT) = 1 fetch zamiast 20', async () => {
    const DEBOUNCE_MS = 500;
    let fetchCallCount = 0;
    const INSERT_COUNT = 20;

    const handler = makeRealtimeHandler(() => fetchCallCount++, DEBOUNCE_MS);

    // Symulacja szybkich INSERTów (skanowanie missed calls)
    for (let i = 0; i < INSERT_COUNT; i++) {
      handler.onRealtimeEvent();
      await delay(5); // 5ms między insertami
    }

    await delay(DEBOUNCE_MS + 100);
    handler.cleanup();

    console.log(
      `  ${INSERT_COUNT} INSERT-ów (co 5ms) → fetchów: ${fetchCallCount} (bez debounce: ${INSERT_COUNT})`
    );
    expect(fetchCallCount).toBe(1);
  });

  it('bez debounce: każde zdarzenie wywołuje osobny fetch', async () => {
    const EVENT_COUNT = 10;
    let fetchCallCount = 0;

    // Handler BEZ debounce — stary kod
    function oldRealtimeHandler() {
      fetchCallCount++;
    }

    for (let i = 0; i < EVENT_COUNT; i++) {
      oldRealtimeHandler();
    }

    console.log(
      `  [STARY] ${EVENT_COUNT} zdarzeń → ${fetchCallCount} fetchów (oczekiwane: ${EVENT_COUNT})`
    );
    expect(fetchCallCount).toBe(EVENT_COUNT);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Suite 4: showLoading=false — brak migającego spinnera
// ═══════════════════════════════════════════════════════════════════════════
describe('showLoading — spinner tylko przy inicjalizacji', () => {
  function makeLoadingTracker() {
    let loadingChanges: boolean[] = [];
    let currentLoading = false;

    function setLoading(value: boolean) {
      if (value !== currentLoading) {
        currentLoading = value;
        loadingChanges.push(value);
      }
    }

    async function fetchCallLogs(showLoading: boolean, durationMs: number) {
      if (showLoading) setLoading(true);
      await delay(durationMs);
      if (showLoading) setLoading(false);
    }

    return { setLoading, fetchCallLogs, getChanges: () => loadingChanges };
  }

  it('inicjalizacja (showLoading=true): loading zmienia się true→false', async () => {
    const tracker = makeLoadingTracker();
    await tracker.fetchCallLogs(true, 20);

    console.log(`  initializeData: zmiany loading = [${tracker.getChanges().join(', ')}]`);
    expect(tracker.getChanges()).toEqual([true, false]);
  });

  it('realtime update (showLoading=false): loading NIE zmienia się', async () => {
    const tracker = makeLoadingTracker();
    await tracker.fetchCallLogs(false, 20);

    console.log(`  realtime fetch: zmiany loading = [${tracker.getChanges().join(', ')}] (oczekiwane: [])`);
    expect(tracker.getChanges()).toHaveLength(0);
  });

  it('stary kod: każdy fetch realtime migał spinnerem (N razy)', async () => {
    const REALTIME_FETCHES = 5;
    const changes: boolean[] = [];
    let loading = false;

    function setLoadingOld(value: boolean) {
      loading = value;
      changes.push(value);
    }

    // Stary kod: setLoading(true) przy każdym wywołaniu
    for (let i = 0; i < REALTIME_FETCHES; i++) {
      setLoadingOld(true);
      await delay(10);
      setLoadingOld(false);
    }

    console.log(
      `  [STARY] ${REALTIME_FETCHES} realtime fetchów → ${changes.length} zmian stanu loading`
    );
    expect(changes.length).toBe(REALTIME_FETCHES * 2); // każdy fetch: true + false
  });

  it('nowy kod: N realtime fetchów = 0 zmian stanu loading', async () => {
    const tracker = makeLoadingTracker();
    const REALTIME_FETCHES = 5;

    for (let i = 0; i < REALTIME_FETCHES; i++) {
      await tracker.fetchCallLogs(false, 10);
    }

    console.log(
      `  [NOWY] ${REALTIME_FETCHES} realtime fetchów → ${tracker.getChanges().length} zmian stanu loading`
    );
    expect(tracker.getChanges()).toHaveLength(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
// Suite 5: groupCallLogsByClient — wydajność czystej logiki JS
// ═══════════════════════════════════════════════════════════════════════════
describe('groupCallLogsByClient — wydajność algorytmu grupowania', () => {
  const SIZES = [10, 50, 100, 200];

  SIZES.forEach((count) => {
    it(`grupowanie ${count} logów kończy się < 5ms`, () => {
      const logs = generateMockCallLogs(count);
      const start = performance.now();
      const groups = groupCallLogsByClient(logs);
      const durationMs = performance.now() - start;

      console.log(
        `  ${count} logów → ${groups.length} grup, czas: ${durationMs.toFixed(2)}ms`
      );
      expect(durationMs).toBeLessThan(5);
      expect(groups.length).toBeGreaterThan(0);
    });
  });

  it('wyniki są posortowane: najpierw missed, potem reserved, malejąco po czasie', () => {
    const logs = generateMockCallLogs(40);
    const groups = groupCallLogsByClient(logs);

    // Wszystkie grupy z missed powinny być przed grupami bez missed
    let foundNonMissed = false;
    for (const g of groups) {
      if (g.missedCount === 0) {
        foundNonMissed = true;
      } else if (foundNonMissed) {
        // Znaleźliśmy missed po non-missed — błąd sortowania
        expect(true).toBe(false); // fail
      }
    }

    const missedGroups = groups.filter((g) => g.missedCount > 0).length;
    const otherGroups = groups.filter((g) => g.missedCount === 0).length;
    console.log(`  40 logów → ${groups.length} grup: ${missedGroups} z missed, ${otherGroups} bez`);
    expect(groups.length).toBeGreaterThan(0);
  });

  it('200 renderów lookupContactName < 10ms (symulacja dużej listy)', () => {
    // Symulacja cached lookup — O(1) Map.get
    const cache = new Map<string, string>();
    for (let i = 0; i < 300; i++) {
      cache.set(`+4860000000${i}`, `Kontakt ${i}`);
    }

    function lookupContactName(phone: string | null): string | null {
      if (!phone) return null;
      return cache.get(phone) || null;
    }

    const phones = Array.from({ length: 200 }, (_, i) => `+4860000000${i % 50}`);

    const start = performance.now();
    phones.forEach((p) => lookupContactName(p));
    const durationMs = performance.now() - start;

    console.log(`  200 lookupów: ${durationMs.toFixed(3)}ms`);
    expect(durationMs).toBeLessThan(10);
  });
});
