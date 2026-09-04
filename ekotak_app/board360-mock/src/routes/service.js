'use strict';
/*
 * Modul Serwis: zlecenia serwisowe i karty przegladow gwarancyjnych.
 *
 * Ksztalt rekordow i regul = `api/src/modules/service` z board360:
 *  - maszyna statusow zlecenia: new -> in_progress -> done (bez przeskoku),
 *  - okno SLA liczone od utworzenia; awaria domyslnie 24 h, przeglad i
 *    konserwacja bez SLA (chyba ze `slaHours` podano jawnie),
 *  - `slaDueAt` i `slaBreached` doklejamy przy odczycie — telefon ich nie liczy,
 *  - karta gwarancyjna niesie liczniki i `computedStatus` kazdego przegladu.
 *
 * Uprawnienia: odczyt `service.view`, zapis `service.manage`.
 */

const express = require('express');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission, unprocessable } = require('../middleware');
const { db } = require('../store');

const router = express.Router();

const JOB_TYPES = new Set(['awaria', 'przeglad', 'konserwacja']);
const JOB_STATUSES = ['new', 'in_progress', 'done'];
/**
 * Maszyna statusow 1:1 z board360 (`domain/service-job-status.ts`): postep
 * o jeden krok i cofanie o jeden krok, BEZ przeskoku new -> done i BEZ
 * done -> new. Panel i telefon robia dlatego „wykonane" ze statusu `new`
 * dwoma zadaniami.
 */
const ALLOWED_STATUS = {
  new: ['in_progress'],
  in_progress: ['done', 'new'],
  done: ['in_progress'],
};
const canChangeStatus = (from, to) => from === to || ALLOWED_STATUS[from].includes(to);
const JOB_PRIORITIES = new Set(['low', 'normal', 'high']);
/** Okna SLA do wyboru — te same trzy co w panelu. */
const SLA_HOURS = new Set([24, 168, 720]);
const WARRANTY_STATUSES = new Set([
  'wykonane',
  'oczekujace',
  'umowione',
  'rezygnacja',
  'czekamy_na_kontakt',
  'brak_kontaktu',
  'inne',
]);
/** Gwarancja Panasonic = piec przegladow w pieciu latach. */
const MAX_INSPECTIONS = 5;

/** Domyslne okno SLA wg typu: awaria 24 h, planowa obsluga bez okna. */
const defaultSlaHours = (type) => (type === 'awaria' ? 24 : null);

/** Widok zlecenia: doklejone `slaDueAt` i `slaBreached` (liczy serwer). */
function jobView(job) {
  const hours = job.slaHours != null ? job.slaHours : defaultSlaHours(job.type);
  const dueAt = hours == null ? null : new Date(new Date(job.createdAt).getTime() + hours * 3600000);
  return {
    id: job.id,
    clientId: job.clientId,
    dealId: job.dealId,
    type: job.type,
    status: job.status,
    priority: job.priority,
    technicianId: job.technicianId,
    scheduledAt: job.scheduledAt,
    note: job.note,
    slaHours: job.slaHours,
    slaDueAt: dueAt ? dueAt.toISOString() : null,
    slaBreached: Boolean(dueAt && job.status !== 'done' && dueAt.getTime() < Date.now()),
  };
}

/**
 * Stan pojedynczego przegladu. `done` gdy jest data wykonania, `overdue` gdy
 * termin minal, `planned` gdy jeszcze przed nami, `unscheduled` bez terminu.
 */
function inspectionStatus(inspection, commissionedAt) {
  if (inspection.doneAt) return 'done';
  if (!inspection.plannedAt) return 'unscheduled';
  const planned = new Date(inspection.plannedAt).getTime();
  if (Number.isNaN(planned)) return 'unscheduled';
  return planned < Date.now() ? 'overdue' : 'planned';
}

/** Widok karty: liczniki + `computedStatus` i `suspect` kazdej pozycji. */
function cardView(card) {
  const commissioned = card.commissionedAt ? new Date(card.commissionedAt).getTime() : null;
  const inspections = [...card.inspections]
    .sort((a, b) => a.ordinal - b.ordinal)
    .map((i) => ({
      ...i,
      computedStatus: inspectionStatus(i, card.commissionedAt),
      // Data planowana przed uruchomieniem instalacji = blad w imporcie.
      suspect: Boolean(
        commissioned && i.plannedAt && new Date(i.plannedAt).getTime() < commissioned,
      ),
    }));
  const doneCount = inspections.filter((i) => i.computedStatus === 'done').length;
  const overdueCount = inspections.filter((i) => i.computedStatus === 'overdue').length;
  const nextPlanned = inspections
    .filter((i) => i.computedStatus === 'planned' || i.computedStatus === 'overdue')
    .map((i) => i.plannedAt)
    .filter(Boolean)
    .sort()[0];
  return {
    id: card.id,
    brand: card.brand,
    name: card.name,
    location: card.location,
    commissionedAt: card.commissionedAt,
    status: card.status,
    outdoorModel: card.outdoorModel,
    outdoorSerial: card.outdoorSerial,
    indoorModel: card.indoorModel,
    indoorSerial: card.indoorSerial,
    note: card.note,
    inspections,
    doneCount,
    overdueCount,
    suspectCount: inspections.filter((i) => i.suspect).length,
    nextPlannedAt: nextPlanned || null,
  };
}

const jobsOf = (req) => db.serviceJobs.filter((j) => j.organizationId === req.user.organizationId);
const cardsOf = (req) => db.warrantyCards.filter((c) => c.organizationId === req.user.organizationId);

// ── Zlecenia serwisowe ───────────────────────────────────────────────────────

router.get('/service-jobs', requireAuth, requirePermission('service.view'), (req, res) => {
  let rows = jobsOf(req);
  if (req.query.technicianId) rows = rows.filter((j) => j.technicianId === req.query.technicianId);
  if (req.query.status) rows = rows.filter((j) => j.status === req.query.status);
  if (req.query.type) rows = rows.filter((j) => j.type === req.query.type);
  res.json(rows.map(jobView));
});

router.post('/service-jobs', requireAuth, requirePermission('service.manage'), (req, res) => {
  const body = req.body || {};
  const type = body.type || 'awaria';
  if (!JOB_TYPES.has(type)) return unprocessable(res, 'Nieznany typ zlecenia.', ['type']);
  if (body.priority && !JOB_PRIORITIES.has(body.priority)) {
    return unprocessable(res, 'Nieznany priorytet.', ['priority']);
  }
  if (body.slaHours != null && !SLA_HOURS.has(body.slaHours)) {
    return unprocessable(res, 'Dozwolone okna SLA: 24, 168, 720 h.', ['slaHours']);
  }
  // Klient jest OPCJONALNY — zlecenie wolno zapisac "na szybko", z samym
  // opisem usterki; panel pokazuje takie wiersze na czerwono.
  if (body.clientId && !db.clients.some((c) => c.id === body.clientId)) {
    return unprocessable(res, 'Nie znaleziono klienta.', ['clientId']);
  }
  const job = {
    id: uuid(),
    organizationId: req.user.organizationId,
    clientId: body.clientId || null,
    dealId: body.dealId || null,
    type,
    status: 'new',
    priority: body.priority || 'normal',
    technicianId: body.technicianId || null,
    scheduledAt: body.scheduledAt || null,
    note: body.note || null,
    slaHours: body.slaHours != null ? body.slaHours : null,
    createdAt: nowIso(),
    updatedAt: nowIso(),
  };
  db.serviceJobs.push(job);
  res.status(201).json(jobView(job));
});

router.get('/service-jobs/:id', requireAuth, requirePermission('service.view'), (req, res) => {
  const job = jobsOf(req).find((j) => j.id === req.params.id);
  if (!job) return res.status(404).json({ message: 'Nie znaleziono zlecenia.' });
  res.json(jobView(job));
});

router.patch('/service-jobs/:id', requireAuth, requirePermission('service.manage'), (req, res) => {
  const job = jobsOf(req).find((j) => j.id === req.params.id);
  if (!job) return res.status(404).json({ message: 'Nie znaleziono zlecenia.' });
  const body = req.body || {};

  if ('status' in body) {
    if (!JOB_STATUSES.includes(body.status)) {
      return unprocessable(res, 'Nieznany status.', ['status']);
    }
    if (!canChangeStatus(job.status, body.status)) {
      return unprocessable(res, 'Niedozwolone przejscie statusu.', ['status']);
    }
    job.status = body.status;
  }
  if ('priority' in body) {
    if (!JOB_PRIORITIES.has(body.priority)) {
      return unprocessable(res, 'Nieznany priorytet.', ['priority']);
    }
    job.priority = body.priority;
  }
  if ('slaHours' in body) {
    if (body.slaHours != null && !SLA_HOURS.has(body.slaHours)) {
      return unprocessable(res, 'Dozwolone okna SLA: 24, 168, 720 h.', ['slaHours']);
    }
    job.slaHours = body.slaHours;
  }
  if ('clientId' in body) {
    if (body.clientId && !db.clients.some((c) => c.id === body.clientId)) {
      return unprocessable(res, 'Nie znaleziono klienta.', ['clientId']);
    }
    job.clientId = body.clientId;
  }
  if ('technicianId' in body) job.technicianId = body.technicianId;
  if ('scheduledAt' in body) job.scheduledAt = body.scheduledAt;
  if ('note' in body) job.note = body.note;
  job.updatedAt = nowIso();
  res.json(jobView(job));
});

/** Serwisanci do przypisania — konta z rola `serwisant` albo `montaz`. */
router.get('/technicians', requireAuth, requirePermission('service.view'), (req, res) => {
  res.json(
    db.users
      .filter(
        (u) =>
          u.organizationId === req.user.organizationId &&
          (u.role === 'serwisant' || u.role === 'montaz'),
      )
      .map((u) => ({
        id: u.id,
        email: u.email,
        firstName: u.firstName || null,
        lastName: u.lastName || null,
        region: null,
      })),
  );
});

// ── Karty przegladow gwarancyjnych ───────────────────────────────────────────

router.get('/warranty-cards', requireAuth, requirePermission('service.view'), (req, res) => {
  let rows = cardsOf(req);
  if (req.query.brand) rows = rows.filter((c) => c.brand === req.query.brand);
  if (req.query.status) rows = rows.filter((c) => c.status === req.query.status);
  if (req.query.search) {
    const q = String(req.query.search).toLowerCase();
    rows = rows.filter((c) =>
      [c.name, c.location, c.outdoorModel, c.outdoorSerial, c.indoorModel, c.indoorSerial]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
        .includes(q),
    );
  }
  res.json(rows.map(cardView));
});

/**
 * Wspolrzedne kart do mapy. MUSI stac przed `/warranty-cards/:id`, inaczej
 * Express potraktuje "geo" jako identyfikator karty — ta sama pulapka co
 * w board360.
 */
router.get('/warranty-cards/geo', requireAuth, requirePermission('service.view'), (req, res) => {
  res.json(
    cardsOf(req)
      .filter((c) => c.geo)
      .map((c) => ({ id: c.id, lat: c.geo.lat, lng: c.geo.lng, city: c.geo.city || '' })),
  );
});

router.post('/warranty-cards', requireAuth, requirePermission('service.manage'), (req, res) => {
  const body = req.body || {};
  if (!body.name || !String(body.name).trim()) {
    return unprocessable(res, 'Wymagana nazwa.', ['name']);
  }
  if (body.status && !WARRANTY_STATUSES.has(body.status)) {
    return unprocessable(res, 'Nieznany status karty.', ['status']);
  }
  const card = {
    id: uuid(),
    organizationId: req.user.organizationId,
    brand: body.brand || 'Panasonic',
    name: String(body.name).trim(),
    location: body.location || null,
    commissionedAt: body.commissionedAt || null,
    status: body.status || 'oczekujace',
    outdoorModel: body.outdoorModel || null,
    outdoorSerial: body.outdoorSerial || null,
    indoorModel: body.indoorModel || null,
    indoorSerial: body.indoorSerial || null,
    note: body.note || null,
    geo: null,
    inspections: [],
    createdAt: nowIso(),
    updatedAt: nowIso(),
  };
  db.warrantyCards.push(card);
  res.status(201).json(cardView(card));
});

router.get('/warranty-cards/:id', requireAuth, requirePermission('service.view'), (req, res) => {
  const card = cardsOf(req).find((c) => c.id === req.params.id);
  if (!card) return res.status(404).json({ message: 'Nie znaleziono karty.' });
  res.json(cardView(card));
});

router.patch('/warranty-cards/:id', requireAuth, requirePermission('service.manage'), (req, res) => {
  const card = cardsOf(req).find((c) => c.id === req.params.id);
  if (!card) return res.status(404).json({ message: 'Nie znaleziono karty.' });
  const body = req.body || {};
  if ('status' in body && !WARRANTY_STATUSES.has(body.status)) {
    return unprocessable(res, 'Nieznany status karty.', ['status']);
  }
  for (const field of [
    'brand',
    'name',
    'location',
    'commissionedAt',
    'status',
    'outdoorModel',
    'outdoorSerial',
    'indoorModel',
    'indoorSerial',
    'note',
  ]) {
    if (field in body) card[field] = body[field];
  }
  card.updatedAt = nowIso();
  res.json(cardView(card));
});

/** Upsert pozycji harmonogramu po numerze przegladu (1..5). */
router.put(
  '/warranty-cards/:id/inspections',
  requireAuth,
  requirePermission('service.manage'),
  (req, res) => {
    const card = cardsOf(req).find((c) => c.id === req.params.id);
    if (!card) return res.status(404).json({ message: 'Nie znaleziono karty.' });
    const body = req.body || {};
    const ordinal = Number(body.ordinal);
    if (!Number.isInteger(ordinal) || ordinal < 1 || ordinal > MAX_INSPECTIONS) {
      return unprocessable(res, `Numer przegladu musi byc z zakresu 1..${MAX_INSPECTIONS}.`, [
        'ordinal',
      ]);
    }
    if (body.price != null && (Number.isNaN(Number(body.price)) || Number(body.price) < 0)) {
      return unprocessable(res, 'Cena nie moze byc ujemna.', ['price']);
    }
    let inspection = card.inspections.find((i) => i.ordinal === ordinal);
    if (!inspection) {
      inspection = { id: uuid(), cardId: card.id, ordinal, technicianId: null, note: null };
      card.inspections.push(inspection);
    }
    if ('plannedAt' in body) inspection.plannedAt = body.plannedAt;
    if ('doneAt' in body) inspection.doneAt = body.doneAt;
    if ('price' in body) inspection.price = body.price == null ? null : Number(body.price);
    if ('technicianId' in body) inspection.technicianId = body.technicianId;
    if ('note' in body) inspection.note = body.note;
    card.updatedAt = nowIso();
    res.json(cardView(card));
  },
);

module.exports = router;
