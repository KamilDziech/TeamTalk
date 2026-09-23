'use strict';
/*
 * Modul CELE — cele osobiste, dzialu i firmy (board360 `api/src/modules/goals`).
 *
 * Ksztalt odpowiedzi 1:1 z panelem, bo telefon wola DOKLADNIE te same trasy.
 * Sedno kontraktu, ktore atrapa ma odwzorowac:
 *
 *  1. REALIZACJE LICZY SERWER. Telefon dostaje `value`, `pct`, `pacePct`
 *     i `status` gotowe — jesli atrapa zwroci sama wartosc docelowa, ekran nie
 *     ma z czego narysowac ani pierscienia, ani kreski tempa.
 *  2. STATUS TO REALIZACJA ZESTAWIONA Z TEMPEM okresu, nie goly procent:
 *     60% w polowie kwartalu to co innego niz 60% na trzy dni przed koncem.
 *  3. IMIENNE WYNIKI DZIALU (`detailed`) dostaje WYLACZNIE zwierzchnik kogos
 *     z tego dzialu albo zarzad — zwykly czlonek widzi karty celu i tyle.
 *     Na tym stoi test zakladki „Zespolu" z konta serwisanta.
 *  4. ZAPIS bramkuje `goals.manage` ALBO bycie zwierzchnikiem adresata
 *     (`hrProfiles.managerId`) — ta sama konstrukcja, co skrzynka urlopowa.
 */

const express = require('express');
const { uuid, nowIso } = require('../crypto');
const { can, requireAuth, requirePermission, unprocessable } = require('../middleware');
const { db } = require('../store');

const router = express.Router();

// ── Katalog miernikow (lustro `domain/goal-metrics.ts`) ──────────────────────

const METRICS = [
  { code: 'revenue_net', label: 'Przychód netto z wygranych', unit: 'pln', source: 'Lejek', direction: 'up', hint: 'Suma kwot netto ofert ze statusem „wygrana”, liczona po opiekunie deala.' },
  { code: 'margin', label: 'Marża z wygranych', unit: 'pln', source: 'Lejek', direction: 'up', hint: 'Suma marż z wygranych ofert.' },
  { code: 'deals_won', label: 'Wygrane instalacje', unit: 'szt', source: 'Lejek', direction: 'up', hint: 'Liczba ofert, które w tym okresie dostały status „wygrana”.' },
  { code: 'offers_issued', label: 'Oferty wystawione', unit: 'szt', source: 'Lejek', direction: 'up', hint: 'Oferty utworzone w okresie.' },
  { code: 'conversion', label: 'Konwersja oferta → sprzedaż', unit: 'pct', source: 'Lejek', direction: 'up', hint: 'Wygrane ÷ rozstrzygnięte w okresie.' },
  { code: 'leads_new', label: 'Nowe leady', unit: 'szt', source: 'Lejek', direction: 'up', hint: 'Karty deala założone w okresie.' },
  { code: 'audits_done', label: 'Audyty wykonane', unit: 'szt', source: 'Audyt', direction: 'up', hint: 'Audyty zapisane w okresie.' },
  { code: 'installations_done', label: 'Montaże odebrane', unit: 'szt', source: 'Montaże', direction: 'up', hint: 'Montaże ze statusem „zakończony”.' },
  { code: 'service_closed', label: 'Zlecenia serwisowe zamknięte', unit: 'szt', source: 'Serwis', direction: 'up', hint: 'Zlecenia zamknięte w okresie.' },
  { code: 'service_sla', label: 'Dotrzymanie SLA (awarie)', unit: 'pct', source: 'Serwis', direction: 'up', hint: 'Odsetek awarii zamkniętych w oknie SLA.' },
  { code: 'motivation_points', label: 'Punkty motywacyjne', unit: 'pkt', source: 'Zespół', direction: 'up', hint: 'Suma zatwierdzonych punktów.' },
  { code: 'trainings_passed', label: 'Szkolenia zaliczone', unit: 'szt', source: 'Szkolenia', direction: 'up', hint: 'Lekcje zaliczone w okresie.' },
  { code: 'manual', label: 'Własny — wpisywany ręcznie', unit: 'szt', source: 'Wpis ręczny', direction: 'up', hint: 'Stan podaje człowiek w check-inie.' },
];

const SCOPES = ['personal', 'team', 'company'];
const metricsWithScopes = () => METRICS.map((m) => ({ ...m, scopes: SCOPES }));
const metricOf = (code) => METRICS.find((m) => m.code === code) || null;

const DEPARTMENTS = [
  { key: 'biuro', label: 'Biuro' },
  { key: 'serwis', label: 'Serwis' },
  { key: 'montaz', label: 'Montaż' },
  { key: 'pozostali', label: 'Pozostali' },
];

/**
 * Dzial osoby — PIERWSZY pasujacy warunek, wiec kazdy trafia dokladnie raz
 * (lustro `members.ts` panelu i `domain/department.ts` board360).
 */
function departmentOf(user) {
  const roles = [user.role, ...(user.additionalRoles || [])].filter(Boolean);
  const functions = user.functions || [];
  if (functions.includes('serwis')) return 'serwis';
  if (roles.includes('montaz')) return 'montaz';
  if (roles.includes('biuro') || roles.includes('zarzad') || roles.includes('admin')) return 'biuro';
  return 'pozostali';
}

// ── Okresy ───────────────────────────────────────────────────────────────────

/** Okno etykiety okresu: `2026-09`, `2026-Q3`, `2026`. Oba konce wlacznie. */
function rangeOfPeriod(key) {
  let m = /^(\d{4})-(\d{2})$/.exec(key || '');
  if (m) {
    const y = Number(m[1]);
    const mo = Number(m[2]) - 1;
    return { start: Date.UTC(y, mo, 1), end: Date.UTC(y, mo + 1, 1) - 1, key };
  }
  m = /^(\d{4})-Q([1-4])$/.exec(key || '');
  if (m) {
    const y = Number(m[1]);
    const q = Number(m[2]);
    return { start: Date.UTC(y, (q - 1) * 3, 1), end: Date.UTC(y, q * 3, 1) - 1, key };
  }
  m = /^(\d{4})$/.exec(key || '');
  if (m) {
    const y = Number(m[1]);
    return { start: Date.UTC(y, 0, 1), end: Date.UTC(y + 1, 0, 1) - 1, key };
  }
  const now = new Date();
  const q = Math.floor(now.getUTCMonth() / 3) + 1;
  return rangeOfPeriod(`${now.getUTCFullYear()}-Q${q}`);
}

/** Czesc okresu, ktora minela (0–1) — z tego bierze sie tempo i status. */
function elapsedFraction(range) {
  const now = Date.now();
  if (now <= range.start) return 0;
  if (now >= range.end) return 1;
  return (now - range.start) / (range.end - range.start);
}

function progressPct(value, target, direction) {
  if (!target) return 0;
  if (direction === 'down') {
    if (value <= target) return 100;
    return Math.max(0, Math.round((target / value) * 1000) / 10);
  }
  return Math.round((value / target) * 1000) / 10;
}

function statusOf(pct, elapsed, warnAtPct) {
  if (pct >= 100) return 'done';
  const expected = elapsed * 100;
  if (expected <= 0) return 'ok';
  const pace = (pct / expected) * 100;
  if (warnAtPct > 0 && pace < warnAtPct) return 'bad';
  if (pace < 100) return 'warn';
  return 'ok';
}

// ── Liczenie realizacji z danych atrapy ──────────────────────────────────────

const inRange = (iso, range, until) => {
  if (!iso) return false;
  const t = Date.parse(iso);
  return t >= range.start && t <= (until != null ? Math.min(until, range.end) : range.end);
};

const dealsOf = (orgId) => db.deals.filter((d) => d.organizationId === orgId && !d.deletedAt);

/**
 * Wartosc miernika dla zbioru osob (`userIds === null` = cala firma) w oknie.
 * `until` zawezasz koniec okna — z tego powstaje przebieg narastajacy.
 */
function metricValue(orgId, metric, userIds, range, until) {
  const owns = (deal) => userIds === null || userIds.includes(deal.ownerId);
  const deals = dealsOf(orgId);
  const dealById = new Map(deals.map((d) => [d.id, d]));
  const offers = (db.offers || []).filter((o) => o.organizationId === orgId);

  switch (metric) {
    case 'revenue_net':
    case 'margin': {
      const field = metric === 'revenue_net' ? 'netTotal' : 'margin';
      return Math.round(
        offers
          .filter((o) => o.status === 'won' && inRange(o.updatedAt || o.createdAt, range, until))
          .filter((o) => { const d = dealById.get(o.dealId); return d && owns(d); })
          .reduce((sum, o) => sum + Number(o[field] || 0), 0) * 100,
      ) / 100;
    }
    case 'deals_won':
      return offers
        .filter((o) => o.status === 'won' && inRange(o.updatedAt || o.createdAt, range, until))
        .filter((o) => { const d = dealById.get(o.dealId); return d && owns(d); }).length;
    case 'offers_issued':
      return offers
        .filter((o) => inRange(o.createdAt, range, until))
        .filter((o) => { const d = dealById.get(o.dealId); return d && owns(d); }).length;
    case 'conversion': {
      const decided = offers
        .filter((o) => ['won', 'lost'].includes(o.status) && inRange(o.updatedAt || o.createdAt, range, until))
        .filter((o) => { const d = dealById.get(o.dealId); return d && owns(d); });
      if (!decided.length) return 0;
      const won = decided.filter((o) => o.status === 'won').length;
      return Math.round((won / decided.length) * 1000) / 10;
    }
    case 'leads_new':
      return deals.filter((d) => owns(d) && inRange(d.createdAt, range, until)).length;
    case 'audits_done':
      return (db.audits || [])
        .filter((a) => a.organizationId === orgId && inRange(a.createdAt, range, until))
        .filter((a) => { const d = dealById.get(a.dealId); return d && owns(d); }).length;
    case 'installations_done':
      return (db.installations || [])
        .filter((i) => i.organizationId === orgId && i.status === 'done')
        .filter((i) => inRange(i.scheduledAt, range, until))
        .filter((i) => userIds === null || (i.assignees || []).some((a) => userIds.includes(a.userId || a)))
        .length;
    case 'service_closed':
      return (db.serviceJobs || [])
        .filter((j) => j.organizationId === orgId && j.status === 'done')
        .filter((j) => inRange(j.updatedAt || j.createdAt, range, until))
        .filter((j) => userIds === null || userIds.includes(j.technicianId))
        .length;
    case 'service_sla': {
      const jobs = (db.serviceJobs || [])
        .filter((j) => j.organizationId === orgId && j.type === 'awaria' && j.status === 'done')
        .filter((j) => inRange(j.updatedAt || j.createdAt, range, until))
        .filter((j) => userIds === null || userIds.includes(j.technicianId));
      if (!jobs.length) return 0;
      const ok = jobs.filter((j) => {
        const windowMs = (j.slaHours || 24) * 3600 * 1000;
        return Date.parse(j.updatedAt || j.createdAt) - Date.parse(j.createdAt) <= windowMs;
      }).length;
      return Math.round((ok / jobs.length) * 1000) / 10;
    }
    default:
      // `manual` i mierniki bez danych w atrapie — wartosc z check-inow albo zero.
      return 0;
  }
}

/** Najnowszy wpis reczny w oknie — cel `manual` trzyma STAN, nie przyrost. */
function manualValue(goalId, range, until) {
  const rows = (db.goalCheckins || [])
    .filter((c) => c.goalId === goalId)
    .filter((c) => inRange(c.reportedOn, range, until))
    .sort((a, b) => Date.parse(a.reportedOn) - Date.parse(b.reportedOn));
  return rows.length ? Number(rows[rows.length - 1].value) : 0;
}

// ── Sklejanie odpowiedzi ─────────────────────────────────────────────────────

const usersOf = (orgId) => db.users.filter((u) => u.organizationId === orgId && !u.deletedAt);

const personName = (u) =>
  u ? [u.firstName, u.lastName].filter(Boolean).join(' ').trim() || u.email : null;

const personDto = (u) => ({ id: u.id, name: personName(u), department: departmentOf(u) });

const goalsOf = (orgId) => (db.goals || []).filter((g) => g.organizationId === orgId);

function audienceOf(orgId, goal) {
  if (goal.scope === 'company') return null;
  if (goal.scope === 'personal') return goal.ownerUserId ? [goal.ownerUserId] : [];
  return usersOf(orgId).filter((u) => departmentOf(u) === goal.teamKey).map((u) => u.id);
}

function goalDto(orgId, goal) {
  const def = metricOf(goal.metric);
  const range = rangeOfPeriod(goal.periodKey);
  const snapshot = (db.goalSnapshots || []).find((s) => s.goalId === goal.id) || null;

  const value = snapshot
    ? Number(snapshot.value)
    : goal.metric === 'manual'
      ? manualValue(goal.id, range)
      : metricValue(orgId, goal.metric, audienceOf(orgId, goal), range);

  const pct = snapshot ? snapshot.pct : progressPct(value, Number(goal.target), goal.direction);
  const elapsed = elapsedFraction(range);
  const lastCheckin = (db.goalCheckins || [])
    .filter((c) => c.goalId === goal.id)
    .sort((a, b) => Date.parse(b.reportedOn) - Date.parse(a.reportedOn))[0];
  const owner = goal.ownerUserId ? usersOf(orgId).find((u) => u.id === goal.ownerUserId) : null;

  return {
    id: goal.id,
    scope: goal.scope,
    ownerUserId: goal.ownerUserId || null,
    ownerName: owner ? personName(owner) : null,
    teamKey: goal.teamKey || null,
    metric: goal.metric,
    metricLabel: def ? def.label : goal.metric,
    unit: def ? def.unit : 'szt',
    source: def ? def.source : '',
    name: goal.name || (def ? def.label : goal.metric),
    target: Number(goal.target),
    value,
    pct,
    pacePct: Math.round(elapsed * 1000) / 10,
    status: statusOf(pct, elapsed, goal.warnAtPct),
    direction: goal.direction,
    periodStart: new Date(range.start).toISOString(),
    periodEnd: new Date(range.end).toISOString(),
    periodKey: goal.periodKey,
    warnAtPct: goal.warnAtPct,
    goalStatusRaw: goal.status,
    closedAt: snapshot ? snapshot.closedAt : null,
    lastCheckinAt: lastCheckin ? lastCheckin.reportedOn : null,
    ruleId: goal.ruleId || null,
    ruleLabel: (() => {
      if (!goal.ruleId) return null;
      const rule = (db.motivationRules || []).find((r) => r.id === goal.ruleId);
      return rule ? `${rule.name} (+${rule.points} pkt)` : null;
    })(),
  };
}

function historyOf(orgId, filter) {
  return goalsOf(orgId)
    .filter((g) => g.status === 'closed')
    .filter(filter)
    .sort((a, b) => (a.periodKey < b.periodKey ? 1 : -1))
    .slice(0, 12)
    .map((g) => {
      const snap = (db.goalSnapshots || []).find((s) => s.goalId === g.id);
      const def = metricOf(g.metric);
      return {
        id: g.id,
        periodKey: g.periodKey,
        name: g.name || (def ? def.label : g.metric),
        unit: def ? def.unit : 'szt',
        target: Number(g.target),
        value: snap ? Number(snap.value) : 0,
        pct: snap ? snap.pct : 0,
      };
    });
}

// ── Uprawnienia zapisu ───────────────────────────────────────────────────────

const hasManage = (req) => can(req.user, 'goals.manage');

const isManagerOf = (orgId, actorId, userId) => {
  const profile = (db.hrProfiles || []).find(
    (p) => p.organizationId === orgId && p.userId === userId,
  );
  return !!profile && profile.managerId === actorId;
};

/** Zarzad widzi wszystkich, zwierzchnik swoich podwladnych, reszta siebie. */
function managedPeople(orgId, actorId, canManageAll) {
  const users = usersOf(orgId);
  if (canManageAll) return users.map(personDto);
  const mine = users.filter((u) => isManagerOf(orgId, actorId, u.id));
  const self = users.find((u) => u.id === actorId);
  return [...(self ? [personDto(self)] : []), ...mine.map(personDto)];
}

// ── Trasy ────────────────────────────────────────────────────────────────────

router.use(requireAuth);

// Odczyt ma kazda rola; zapis bramkujemy w handlerach, bo zwierzchnik robi to
// bez `goals.manage` — dokladnie jak w board360.

router.get('/goals/metrics', requirePermission('goals.view'), (req, res) => {
  // Zasady ida TEDY, a nie z /motivation-rules (tam stoi `settings.team`) —
  // cele osobiste ustawia tez ZWIERZCHNIK i musi miec z czego wybrac nagrode.
  const rules = (db.motivationRules || [])
    .filter((r) => r.organizationId === req.user.organizationId && r.active !== false)
    .map((r) => ({ id: r.id, code: r.code, name: r.name, category: r.category || '', points: r.points }));
  res.json({ metrics: metricsWithScopes(), departments: DEPARTMENTS, rules });
});

router.get('/goals/personal', requirePermission('goals.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const targetId = req.query.userId || req.user.id;
  if (targetId !== req.user.id && !hasManage(req) && !isManagerOf(orgId, req.user.id, targetId)) {
    return res.status(403).json({ message: 'Cele tej osoby widzi jej zwierzchnik i zarząd.' });
  }
  const period = rangeOfPeriod(req.query.period).key;
  const person = usersOf(orgId).find((u) => u.id === targetId);
  const items = goalsOf(orgId)
    .filter((g) => g.scope === 'personal' && g.ownerUserId === targetId && g.periodKey === period)
    .map((g) => goalDto(orgId, g));

  res.json({
    periodKey: period,
    person: person ? personDto(person) : null,
    canManage: hasManage(req) || isManagerOf(orgId, req.user.id, targetId),
    managed: managedPeople(orgId, req.user.id, hasManage(req)),
    items,
    history: historyOf(orgId, (g) => g.scope === 'personal' && g.ownerUserId === targetId),
  });
});

router.get('/goals/team', requirePermission('goals.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const teamKey = DEPARTMENTS.some((d) => d.key === req.query.team) ? req.query.team : 'biuro';
  const period = rangeOfPeriod(req.query.period).key;
  const members = usersOf(orgId).filter((u) => departmentOf(u) === teamKey);
  const memberIds = members.map((u) => u.id);

  const items = goalsOf(orgId)
    .filter((g) => g.scope === 'team' && g.teamKey === teamKey && g.periodKey === period)
    .map((g) => goalDto(orgId, g));

  // Imienne wyniki tylko dla zwierzchnika kogos z dzialu i dla zarzadu.
  const detailed = hasManage(req) || memberIds.some((id) => isManagerOf(orgId, req.user.id, id));
  if (!detailed) {
    return res.json({
      periodKey: period,
      teamKey,
      teamLabel: (DEPARTMENTS.find((d) => d.key === teamKey) || {}).label,
      detailed: false,
      canManage: false,
      leadGoalId: null,
      items,
      members: [],
      personalGoals: [],
      history: historyOf(orgId, (g) => g.scope === 'team' && g.teamKey === teamKey),
    });
  }

  const leadGoal = goalsOf(orgId).find(
    (g) => g.scope === 'team' && g.teamKey === teamKey && g.periodKey === period,
  ) || null;
  const range = rangeOfPeriod(period);

  res.json({
    periodKey: period,
    teamKey,
    teamLabel: (DEPARTMENTS.find((d) => d.key === teamKey) || {}).label,
    detailed: true,
    canManage: hasManage(req),
    leadGoalId: leadGoal ? leadGoal.id : null,
    items,
    members: members.map((u) => ({
      ...personDto(u),
      contribution: leadGoal && leadGoal.metric !== 'manual'
        ? metricValue(orgId, leadGoal.metric, [u.id], range)
        : 0,
    })),
    personalGoals: goalsOf(orgId)
      .filter((g) => g.scope === 'personal' && memberIds.includes(g.ownerUserId) && g.periodKey === period)
      .filter((g) => !leadGoal || g.metric === leadGoal.metric)
      .map((g) => goalDto(orgId, g)),
    history: historyOf(orgId, (g) => g.scope === 'team' && g.teamKey === teamKey),
  });
});

router.get('/goals/company', requirePermission('goals.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const period = rangeOfPeriod(req.query.period).key;
  const goals = goalsOf(orgId).filter((g) => g.scope === 'company' && g.periodKey === period);
  const lead = goals[0] || null;
  const range = rangeOfPeriod(period);

  const byDepartment = [];
  if (lead && lead.metric !== 'manual') {
    for (const dept of DEPARTMENTS) {
      const ids = usersOf(orgId).filter((u) => departmentOf(u) === dept.key).map((u) => u.id);
      if (!ids.length) continue;
      byDepartment.push({
        key: dept.key,
        label: dept.label,
        value: metricValue(orgId, lead.metric, ids, range),
      });
    }
  }

  res.json({
    periodKey: period,
    canManage: hasManage(req),
    items: goals.map((g) => goalDto(orgId, g)),
    leadGoalId: lead ? lead.id : null,
    leadMetricLabel: lead ? (metricOf(lead.metric) || {}).label || lead.metric : null,
    byDepartment,
    history: historyOf(orgId, (g) => g.scope === 'company'),
  });
});

/** Przebieg narastajacy — osiem ciec od poczatku okresu do dzis. */
router.get('/goals/:id/trend', requirePermission('goals.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const goal = goalsOf(orgId).find((g) => g.id === req.params.id);
  if (!goal) return res.status(404).json({ message: 'Nie znaleziono celu.' });

  const range = rangeOfPeriod(goal.periodKey);
  const end = Math.min(Date.now(), range.end);
  const steps = 8;
  const points = [];
  for (let i = 1; i <= steps; i++) {
    const cut = range.start + ((end - range.start) * i) / steps;
    points.push({
      at: new Date(cut).toISOString(),
      value: goal.metric === 'manual'
        ? manualValue(goal.id, range, cut)
        : metricValue(orgId, goal.metric, audienceOf(orgId, goal), range, cut),
    });
  }
  res.json({ goalId: goal.id, target: Number(goal.target), points });
});

router.post('/goals', requirePermission('goals.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const body = req.body || {};
  if (!SCOPES.includes(body.scope)) return unprocessable(res, 'Nieznany zakres celu.');
  if (!metricOf(body.metric)) return unprocessable(res, 'Nieznany miernik.');
  if (!(Number(body.target) > 0)) return unprocessable(res, 'Wartość docelowa musi być dodatnia.');
  if (body.scope === 'personal' && !body.ownerUserId) {
    return unprocessable(res, 'Cel osobisty musi wskazywać osobę.');
  }
  if (body.scope === 'team' && !DEPARTMENTS.some((d) => d.key === body.teamKey)) {
    return unprocessable(res, 'Cel zespołu musi wskazywać dział.');
  }
  const canWrite = hasManage(req)
    || (body.scope === 'personal' && isManagerOf(orgId, req.user.id, body.ownerUserId));
  if (!canWrite) return res.status(403).json({ message: 'Cele ustawia zwierzchnik albo zarząd.' });

  const def = metricOf(body.metric);
  const goal = {
    id: uuid(),
    organizationId: orgId,
    scope: body.scope,
    ownerUserId: body.scope === 'personal' ? body.ownerUserId : null,
    teamKey: body.scope === 'team' ? body.teamKey : null,
    metric: body.metric,
    name: body.name || '',
    target: Number(body.target),
    direction: body.direction || def.direction,
    periodKey: rangeOfPeriod(body.periodKey).key,
    warnAtPct: body.warnAtPct == null ? 90 : Number(body.warnAtPct),
    // Nagroda dotyczy wylacznie celow osobistych — punkty sa indywidualne.
    ruleId: body.scope === 'personal' ? body.ruleId || null : null,
    status: 'active',
    createdById: req.user.id,
    createdAt: nowIso(),
  };
  db.goals.push(goal);
  res.status(201).json(goalDto(orgId, goal));
});

router.patch('/goals/:id', requirePermission('goals.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const goal = goalsOf(orgId).find((g) => g.id === req.params.id);
  if (!goal) return res.status(404).json({ message: 'Nie znaleziono celu.' });
  const canWrite = hasManage(req)
    || (goal.scope === 'personal' && isManagerOf(orgId, req.user.id, goal.ownerUserId));
  if (!canWrite) return res.status(403).json({ message: 'Cele ustawia zwierzchnik albo zarząd.' });

  const body = req.body || {};
  if (body.metric != null) {
    if (!metricOf(body.metric)) return unprocessable(res, 'Nieznany miernik.');
    goal.metric = body.metric;
  }
  if (body.name != null) goal.name = body.name;
  if (body.target != null) goal.target = Number(body.target);
  if (body.direction != null) goal.direction = body.direction;
  if (body.periodKey != null) goal.periodKey = rangeOfPeriod(body.periodKey).key;
  if (body.warnAtPct != null) goal.warnAtPct = Number(body.warnAtPct);
  res.json(goalDto(orgId, goal));
});

router.delete('/goals/:id', requirePermission('goals.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const idx = (db.goals || []).findIndex((g) => g.organizationId === orgId && g.id === req.params.id);
  if (idx < 0) return res.status(404).json({ message: 'Nie znaleziono celu.' });
  const goal = db.goals[idx];
  const canWrite = hasManage(req)
    || (goal.scope === 'personal' && isManagerOf(orgId, req.user.id, goal.ownerUserId));
  if (!canWrite) return res.status(403).json({ message: 'Cele ustawia zwierzchnik albo zarząd.' });

  db.goals.splice(idx, 1);
  db.goalCheckins = (db.goalCheckins || []).filter((c) => c.goalId !== goal.id);
  db.goalSnapshots = (db.goalSnapshots || []).filter((s) => s.goalId !== goal.id);
  res.status(204).end();
});

/** Wpis reczny — STAN celu na dany dzien. Wolno go dopisac takze wlascicielowi. */
router.post('/goals/:id/checkin', requirePermission('goals.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const goal = goalsOf(orgId).find((g) => g.id === req.params.id);
  if (!goal) return res.status(404).json({ message: 'Nie znaleziono celu.' });
  const isOwner = goal.scope === 'personal' && goal.ownerUserId === req.user.id;
  const canWrite = isOwner || hasManage(req)
    || (goal.scope === 'personal' && isManagerOf(orgId, req.user.id, goal.ownerUserId));
  if (!canWrite) return res.status(403).json({ message: 'Brak uprawnień do tego celu.' });

  const body = req.body || {};
  if (body.value == null || Number.isNaN(Number(body.value))) {
    return unprocessable(res, 'Podaj wartość wpisu.');
  }
  const checkin = {
    id: uuid(),
    organizationId: orgId,
    goalId: goal.id,
    value: Number(body.value),
    note: body.note || '',
    reportedOn: body.reportedOn ? `${body.reportedOn}T00:00:00.000Z` : nowIso(),
    createdById: req.user.id,
    createdAt: nowIso(),
  };
  db.goalCheckins.push(checkin);
  res.status(201).json(checkin);
});

/**
 * Zamkniecie okresu: migawka wyniku i — gdy cel wskazuje pozycje regulaminu,
 * a wynik siegnal 100% — PROPOZYCJA punktow (status `proposed`, zatwierdza
 * zarzad). Migawka jest po to, zeby poprawka starego deala nie przepisywala
 * zamknietego kwartalu.
 */
router.post('/goals/:id/close', requirePermission('goals.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const goal = goalsOf(orgId).find((g) => g.id === req.params.id);
  if (!goal) return res.status(404).json({ message: 'Nie znaleziono celu.' });
  const canWrite = hasManage(req)
    || (goal.scope === 'personal' && isManagerOf(orgId, req.user.id, goal.ownerUserId));
  if (!canWrite) return res.status(403).json({ message: 'Okres zamyka zwierzchnik albo zarząd.' });

  const view = goalDto(orgId, goal);

  // Propozycja punktow: tylko cel OSOBISTY, tylko ze wskazana zasada i tylko
  // przy wyniku >= 100%. Status `proposed` — zatwierdza zarzad w module Zespol.
  let motivationPointId = null;
  if (goal.scope === 'personal' && goal.ownerUserId && goal.ruleId && view.pct >= 100) {
    const rule = (db.motivationRules || []).find(
      (r) => r.id === goal.ruleId && r.organizationId === orgId,
    );
    if (rule) {
      const point = {
        id: uuid(),
        organizationId: orgId,
        userId: goal.ownerUserId,
        points: rule.points,
        reason: `Cel osiagniety: ${view.name} (${view.periodKey})`,
        category: rule.category || '',
        ruleId: rule.id,
        ruleCode: rule.code || '',
        status: 'proposed',
        awardedOn: view.periodEnd,
        createdById: req.user.id,
        createdAt: nowIso(),
      };
      (db.motivationPoints = db.motivationPoints || []).push(point);
      motivationPointId = point.id;
    }
  }

  db.goalSnapshots = (db.goalSnapshots || []).filter((s) => s.goalId !== goal.id);
  db.goalSnapshots.push({
    id: uuid(),
    organizationId: orgId,
    goalId: goal.id,
    value: view.value,
    pct: view.pct,
    motivationPointId,
    closedById: req.user.id,
    closedAt: nowIso(),
  });
  goal.status = 'closed';
  res.json(goalDto(orgId, goal));
});

/**
 * Przyznania punktow (board360: `GET /api/motivation-points` pod `settings.team`).
 * Atrapa oddaje je po to, zeby dalo sie sprawdzic ostatni krok decyzji D4:
 * zamkniecie celu z nagroda zaklada wpis ze statusem `proposed`, a nie dopisuje
 * punktow do salda od razu.
 */
router.get('/motivation-points', requirePermission('settings.team'), (req, res) => {
  const rows = (db.motivationPoints || [])
    .filter((p) => p.organizationId === req.user.organizationId)
    .sort((a, b) => (a.awardedOn < b.awardedOn ? 1 : -1));
  res.json(rows);
});

module.exports = router;
