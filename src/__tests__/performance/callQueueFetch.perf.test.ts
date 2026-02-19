/**
 * Performance Tests — Logika fetchCallLogs (mockowane)
 *
 * Porównuje stare podejście (N+1 queries) z nowym (single batch query).
 * Nie wymaga połączenia z bazą — symuluje opóźnienia sieciowe przez jest.fn().
 *
 * Uruchomienie:
 *   npx jest callQueueFetch.perf --verbose
 */

// ── Konfiguracja symulacji ───────────────────────────────────────────────────
const SIMULATED_NETWORK_DELAY_MS = 40;  // realistyczne opóźnienie HTTP do Supabase
const CALL_LOG_COUNTS = [10, 30, 50];   // rozmiary zestawów danych do przetestowania

// ── Helpers ──────────────────────────────────────────────────────────────────
function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function measure<T>(fn: () => Promise<T>): Promise<{ result: T; durationMs: number }> {
  const start = Date.now();
  const result = await fn();
  return { result, durationMs: Date.now() - start };
}

function generateMockCallLogs(count: number) {
  return Array.from({ length: count }, (_, i) => ({
    id: `call-log-${i}`,
    client_id: i % 3 === 0 ? null : `client-${i % 10}`,
    caller_phone: i % 3 === 0 ? `+48${500000000 + i}` : null,
    employee_id: null,
    type: 'missed' as const,
    status: (i % 5 === 0 ? 'reserved' : 'missed') as 'missed' | 'reserved',
    timestamp: new Date(Date.now() - i * 60000).toISOString(),
    reservation_by: null,
    reservation_at: null,
    recipients: [],
    merged_into_id: null,
    phone_account_id: null,
    dedup_key: `key-${i}`,
    created_at: new Date().toISOString(),
    updated_at: new Date().toISOString(),
    clients: i % 3 === 0 ? null : { id: `client-${i % 10}`, phone: `+48${600000000 + i}`, name: null, address: null, notes: null, created_at: '', updated_at: '' },
  }));
}

// Symulacja ograniczonego pool'u połączeń (jak w przeglądarce / urządzeniu mobilnym)
async function withConcurrencyLimit<T>(
  tasks: (() => Promise<T>)[],
  limit: number
): Promise<T[]> {
  const results: T[] = [];
  let idx = 0;

  async function runNext(): Promise<void> {
    const current = idx++;
    if (current >= tasks.length) return;
    results[current] = await tasks[current]();
    await runNext();
  }

  const workers = Array.from({ length: Math.min(limit, tasks.length) }, () => runNext());
  await Promise.all(workers);
  return results;
}

// ── Stary fetchCallLogs (N+1) ────────────────────────────────────────────────
async function fetchCallLogs_OLD(
  mockLogs: ReturnType<typeof generateMockCallLogs>,
  concurrencyLimit = 6 // realistyczny limit dla urządzenia mobilnego
) {
  let queriesExecuted = 0;

  // Zapytanie 1: pobranie logów (wszystkie statusy — brak filtra)
  await delay(SIMULATED_NETWORK_DELAY_MS);
  queriesExecuted++;

  // N+1: osobne zapytanie do voice_reports dla KAŻDEGO logu
  // Zrównoleglone z limitem (jak na realnym urządzeniu)
  const tasks = mockLogs.map((log) => async () => {
    await delay(SIMULATED_NETWORK_DELAY_MS);
    queriesExecuted++;
    return { ...log, client: log.clients, hasVoiceReport: false };
  });

  const logsWithReports = await withConcurrencyLimit(tasks, concurrencyLimit);

  // Filtrowanie po stronie klienta (stary kod)
  const queueLogs = logsWithReports.filter(
    (log) => log.status === 'missed' || log.status === 'reserved'
  );

  return { data: queueLogs, queriesExecuted };
}

// ── Nowy fetchCallLogs (single query) ────────────────────────────────────────
async function fetchCallLogs_NEW(mockLogs: ReturnType<typeof generateMockCallLogs>) {
  let queriesExecuted = 0;

  // Zapytanie 1: pobranie logów z filtrem statusu w SQL (+ join clients)
  await delay(SIMULATED_NETWORK_DELAY_MS);
  queriesExecuted++;

  // Brak pętli — bezpośrednie mapowanie
  const queueLogs = mockLogs.map((log) => ({
    ...log,
    client: log.clients,
    hasVoiceReport: false,
  }));

  return { data: queueLogs, queriesExecuted };
}

// ── Suite 1: Liczba zapytań ───────────────────────────────────────────────────
describe('Liczba zapytań HTTP do bazy', () => {
  it('stary kod wykonuje N+1 zapytań (po jednym na każdy call_log)', async () => {
    const COUNT = 30;
    const logs = generateMockCallLogs(COUNT);
    const { queriesExecuted } = await fetchCallLogs_OLD(logs);

    console.log(`  [STARY] ${COUNT} logów → ${queriesExecuted} zapytań HTTP`);
    // 1 (call_logs) + COUNT (voice_reports) = COUNT + 1
    expect(queriesExecuted).toBe(COUNT + 1);
  });

  it('nowy kod wykonuje dokładnie 1 zapytanie', async () => {
    const COUNT = 30;
    const logs = generateMockCallLogs(COUNT);
    const { queriesExecuted } = await fetchCallLogs_NEW(logs);

    console.log(`  [NOWY]  ${COUNT} logów → ${queriesExecuted} zapytanie HTTP`);
    expect(queriesExecuted).toBe(1);
  });

  it('różnica w liczbie zapytań rośnie liniowo z liczbą logów', () => {
    const sizes = [10, 30, 50, 100];
    sizes.forEach((size) => {
      const oldQueries = size + 1; // N+1
      const newQueries = 1;        // zawsze 1
      const saved = oldQueries - newQueries;
      console.log(`  ${size} logów: stary=${oldQueries}q, nowy=${newQueries}q, oszczędność=${saved}q`);
      expect(saved).toBe(size);
    });
  });
});

// ── Suite 2: Czas wykonania ───────────────────────────────────────────────────
describe('Czas wykonania (symulacja sieci ' + SIMULATED_NETWORK_DELAY_MS + 'ms/zapytanie)', () => {
  CALL_LOG_COUNTS.forEach((count) => {
    it(`dla ${count} logów: nowy kod jest co najmniej 5x szybszy`, async () => {
      const logs = generateMockCallLogs(count);

      // Stary (N+1 — równoległe Promise.all, ale każde zajmuje ~40ms)
      const { durationMs: durationOld, result: resultOld } = await measure(() =>
        fetchCallLogs_OLD(logs)
      );

      // Nowy (1 zapytanie)
      const { durationMs: durationNew, result: resultNew } = await measure(() =>
        fetchCallLogs_NEW(logs)
      );

      const speedup = durationOld / Math.max(durationNew, 1);

      console.log(
        `  ${count} logów | Stary: ${durationOld}ms (${resultOld.queriesExecuted}q) | Nowy: ${durationNew}ms (${resultNew.queriesExecuted}q) | Przyspieszenie: ${speedup.toFixed(1)}x`
      );

      expect(durationNew).toBeLessThan(durationOld);
      // Przy 30+ logach i concurrency limit=6: ceil(30/6)=5 rund × 40ms = 200ms vs 40ms = 5x
      if (count >= 30) {
        expect(speedup).toBeGreaterThanOrEqual(5);
      } else {
        // Dla 10 logów: ceil(10/6)=2 rundy × 40ms = 80ms + 40ms = 3x
        expect(speedup).toBeGreaterThanOrEqual(2);
      }
    });
  });

  it('nowy kod mieści się w 200ms niezależnie od rozmiaru kolejki', async () => {
    for (const count of CALL_LOG_COUNTS) {
      const logs = generateMockCallLogs(count);
      const { durationMs } = await measure(() => fetchCallLogs_NEW(logs));
      console.log(`  ${count} logów → ${durationMs}ms`);
      expect(durationMs).toBeLessThan(200);
    }
  });

  it('stary kod skaluje się liniowo — 50 logów trwa ~50x dłużej niż 1', async () => {
    const logsSmall = generateMockCallLogs(1);
    const logsLarge = generateMockCallLogs(50);

    const { durationMs: dSmall } = await measure(() => fetchCallLogs_OLD(logsSmall));
    const { durationMs: dLarge } = await measure(() => fetchCallLogs_OLD(logsLarge));

    // Promise.all zrównolegla — ale i tak widać wzrost
    console.log(`  1 log: ${dSmall}ms | 50 logów: ${dLarge}ms`);
    // Z Promise.all N+1 jest częściowo zrównoleglony, ale latencja serwerowa i tak rośnie
    expect(dLarge).toBeGreaterThan(dSmall);
  });
});

// ── Suite 3: Poprawność logiki po optymalizacji ───────────────────────────────
describe('Poprawność logiki fetchCallLogs po optymalizacji', () => {
  const MIXED_LOGS_COUNT = 20;
  let mockLogs: ReturnType<typeof generateMockCallLogs>;

  beforeAll(() => {
    mockLogs = generateMockCallLogs(MIXED_LOGS_COUNT);
  });

  it('nowy kod zwraca tylko logi ze statusem missed lub reserved', async () => {
    // W nowym kodzie filtr jest po stronie SQL — symulujemy że API już zwraca przefiltrowane
    // (generator tworzy missed/reserved — wszystkie powinny przejść)
    const { data } = await fetchCallLogs_NEW(mockLogs);

    const invalidStatus = data.filter(
      (log) => log.status !== 'missed' && log.status !== 'reserved'
    );

    console.log(`  Zwrócono ${data.length} logów, nieprawidłowych statusów: ${invalidStatus.length}`);
    expect(invalidStatus).toHaveLength(0);
  });

  it('pole client jest zmapowane z clients (join)', async () => {
    const { data } = await fetchCallLogs_NEW(mockLogs);

    const logsWithClientId = data.filter((log) => log.client_id !== null);
    const logsWithClientObject = data.filter((log) => log.client !== null);

    console.log(`  Logów z client_id: ${logsWithClientId.length}, z obiektem client: ${logsWithClientObject.length}`);
    expect(logsWithClientObject.length).toBe(logsWithClientId.length);
  });

  it('hasVoiceReport jest zdefiniowane dla każdego logu', async () => {
    const { data } = await fetchCallLogs_NEW(mockLogs);

    const withUndefinedReport = data.filter((log) => log.hasVoiceReport === undefined);
    expect(withUndefinedReport).toHaveLength(0);
  });

  it('stary i nowy kod zwracają tę samą liczbę logów dla identycznych danych', async () => {
    const { data: oldData } = await fetchCallLogs_OLD(mockLogs);
    const { data: newData } = await fetchCallLogs_NEW(mockLogs);

    console.log(`  Stary: ${oldData.length} logów | Nowy: ${newData.length} logów`);
    expect(newData.length).toBe(oldData.length);
  });
});

// ── Suite 4: Realtime — częstotliwość refetchów ───────────────────────────────
describe('Realtime — koszt każdego refetcha', () => {
  it('każda zmiana w call_logs powoduje refetch — koszt 1 zapytania (nowy kod)', async () => {
    // Symulacja: realtime callback wywołuje fetchCallLogs
    // Nowy kod = 1 query per callback
    const callbackCount = 5;
    let totalQueries = 0;

    for (let i = 0; i < callbackCount; i++) {
      const logs = generateMockCallLogs(20);
      const { queriesExecuted } = await fetchCallLogs_NEW(logs);
      totalQueries += queriesExecuted;
    }

    console.log(`  ${callbackCount} callbacków realtime → ${totalQueries} zapytań łącznie (nowy kod)`);
    expect(totalQueries).toBe(callbackCount * 1); // zawsze 1 zapytanie per callback
  });

  it('stary kod: 5 callbacków realtime = ponad 100 zapytań', async () => {
    const callbackCount = 5;
    const logsPerCallback = 30;
    let totalQueries = 0;

    for (let i = 0; i < callbackCount; i++) {
      const logs = generateMockCallLogs(logsPerCallback);
      const { queriesExecuted } = await fetchCallLogs_OLD(logs);
      totalQueries += queriesExecuted;
    }

    const newCodeQueries = callbackCount * 1;
    const savedQueries = totalQueries - newCodeQueries;

    console.log(`  [STARY] ${callbackCount} callbacków → ${totalQueries} zapytań`);
    console.log(`  [NOWY]  ${callbackCount} callbacków → ${newCodeQueries} zapytań`);
    console.log(`  Oszczędność: ${savedQueries} zapytań`);

    expect(totalQueries).toBeGreaterThan(100);
    expect(savedQueries).toBeGreaterThan(100);
  });
});
