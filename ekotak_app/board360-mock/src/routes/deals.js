'use strict';
/*
 * CRM — lejek sprzedazy. Odczyt: `crm.view`, zapis: `deal.manage`.
 *
 * Uwaga na kolejnosc tras: `/deals/installations/current` i `/deals/contacts`
 * musza byc zarejestrowane PRZED `/deals/:id`, inaczej Express potraktuje je
 * jako identyfikator deala.
 */

const express = require('express');
const { STAGE_GATES } = require('../config');
const { nowIso } = require('../crypto');
const { canTransition, lostReasonsForStage, missingFor, STAGE_LABEL, STAGES } = require('../deal-rules');
const { requireAuth, requirePermission, can, unprocessable } = require('../middleware');
const { db, clientById, dealById, userById, logActivity, mainCategoryOf, visibleDeals } = require('../store');

const router = express.Router();

// ── Ksztalt odpowiedzi ───────────────────────────────────────────────────────

/** Lista NIE zwraca klienta ani historii — mobilka doklejaja z kartoteki. */
const listShape = (deal) => ({ ...deal });

const detailShape = (deal, orgId) => ({
  ...deal,
  client: clientById(orgId, deal.clientId),
  activities: db.activities
    .filter((a) => a.dealId === deal.id)
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    .map(({ dealId, organizationId, ...rest }) => rest),
});

// ── Instalacje: os etapow i dziedziczenie ────────────────────────────────────

const INSTALL_STAGES = ['lead', 'audit', 'angebot', 'sold'];
const stageIndex = (stage) => STAGES.indexOf(stage);

/** Etap osi instalacyjnej, na ktorym stoi deal (lost/zakonczony -> ostatni przebyty). */
function currentInstallStage(dealStage) {
  if (dealStage === 'lost') return 'lead';
  const idx = stageIndex(dealStage === 'zakonczony' ? 'fertig' : dealStage);
  let current = INSTALL_STAGES[0];
  for (const s of INSTALL_STAGES) if (stageIndex(s) <= idx) current = s;
  return current;
}

/**
 * Wybor EFEKTYWNY: etap bez wlasnego wyboru dziedziczy po ostatnim
 * wczesniejszym, ktory wybor ma. Mobilka dostaje gotowa liste i nie liczy
 * carry-forward u siebie.
 */
function effectiveCategories(dealId, stage) {
  const snapshots = db.dealInstallations[dealId] || {};
  let picked = [];
  for (const s of INSTALL_STAGES) {
    if (Array.isArray(snapshots[s])) picked = snapshots[s];
    if (s === stage) break;
  }
  return picked;
}

/** Kategorie glowne (do badge'ow "PV / O / K" w kartotece). */
const toMainCategories = (ids) => [
  ...new Set(ids.map((id) => (mainCategoryOf(id) || {}).id).filter(Boolean)),
];

// ── Trasy statyczne (przed /:id) ─────────────────────────────────────────────

router.get('/deals/installations/current', requireAuth, requirePermission('crm.view'), (req, res) => {
  const out = {};
  for (const deal of visibleDeals(req.user)) {
    out[deal.id] = toMainCategories(effectiveCategories(deal.id, currentInstallStage(deal.stage)));
  }
  res.json(out);
});

router.get('/deals/contacts', requireAuth, requirePermission('crm.view'), (req, res) => {
  const ids = new Set(visibleDeals(req.user).map((d) => d.id));
  res.json(db.dealContacts.filter((l) => ids.has(l.dealId)).map((l) => ({ dealId: l.dealId, clientId: l.clientId })));
});

// ── Lista i karta ────────────────────────────────────────────────────────────

router.get('/deals', requireAuth, requirePermission('crm.view'), (req, res) => {
  const stage = req.query.stage ? String(req.query.stage) : null;
  const overdue = ['1', 'true', 'yes'].includes(String(req.query.overdue || '').toLowerCase());

  let list = visibleDeals(req.user);
  if (stage) list = list.filter((d) => d.stage === stage);
  if (overdue) {
    const now = Date.now();
    list = list.filter((d) => d.nextContactAt && new Date(d.nextContactAt).getTime() < now);
  }
  list.sort((a, b) => new Date(b.updatedAt || b.createdAt) - new Date(a.updatedAt || a.createdAt));
  res.json(list.map(listShape));
});

router.get('/deals/:id', requireAuth, requirePermission('crm.view'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Nie znaleziono deala.' });
  return res.json(detailShape(deal, req.user.organizationId));
});

// ── Zmiana etapu ─────────────────────────────────────────────────────────────

router.post('/deals/:id/stage', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Nie znaleziono deala.' });

  const { stage, lostReason = null, lostReasonCategory = null, note = null } = req.body || {};
  if (!STAGES.includes(stage)) return unprocessable(res, `Nieznany etap: ${stage}`);
  if (stage === deal.stage) return unprocessable(res, 'Deal juz jest na tym etapie.');
  if (!canTransition(deal.stage, stage)) {
    return unprocessable(
      res,
      `Nie mozna przejsc z "${STAGE_LABEL[deal.stage]}" do "${STAGE_LABEL[stage]}".`,
    );
  }

  if (stage === 'lost') {
    const allowed = lostReasonsForStage(deal.stage);
    if (!lostReasonCategory) {
      return unprocessable(res, 'Podaj powod utraty deala.', ['kategoria powodu utraty']);
    }
    if (!allowed.includes(lostReasonCategory)) {
      return unprocessable(res, `Nieznany powod utraty: ${lostReasonCategory}`);
    }
  }

  if (STAGE_GATES) {
    const missing = missingFor(stage, deal, clientById(req.user.organizationId, deal.clientId));
    if (missing.length) {
      return unprocessable(res, `Brakuje danych do przejscia na "${STAGE_LABEL[stage]}".`, missing);
    }
  }

  const from = deal.stage;
  deal.stage = stage;
  deal.stageEnteredAt = nowIso();
  deal.updatedAt = nowIso();
  if (stage === 'lost') {
    deal.lostReasonCategory = lostReasonCategory;
    deal.lostReason = lostReason;
  } else {
    deal.lostReasonCategory = null;
    deal.lostReason = null;
  }
  // Decyzja czlowieka zdejmuje flage auto-kwalifikacji.
  if (from === 'lead') deal.qualReview = false;

  logActivity(req.user, deal.id, 'stage_change', { from, to: stage, lostReason, lostReasonCategory, note });
  return res.json(detailShape(deal, req.user.organizationId));
});

// ── Edycja karty ─────────────────────────────────────────────────────────────

const ENUMS = {
  segment: ['indywidualny', 'b2b'],
  buildingKind: ['nowy', 'modernizacja'],
  difficulty: ['latwy', 'normalny', 'trudny'],
  buyerPersona: ['analityk', 'zaufany', 'premium'],
  meetingKind: ['klient', 'biuro', 'online'],
  auditAddressKind: ['instalacja', 'biuro', 'online'],
};
const TEXT_FIELDS = [
  'source', 'description', 'projectName', 'discountCode', 'driveFolder',
  'auditAddress', 'meetingUrl', 'billingName', 'billingCompany', 'billingNip', 'billingAddress',
];
const BOOL_FIELDS = ['rodoConsent', 'elderlyContactException', 'billingSameAsInstall'];
const DATE_FIELDS = ['nextContactAt', 'meetingAt', 'auditMeetingAt'];
const OWNER_FIELDS = ['ownerId', 'stageOwnerId', 'meetingOwnerId', 'auditOwnerId'];
const BUILDING_KEYS = ['people', 'areaM2', 'floors', 'shape', 'construction', 'stage', 'windows', 'heatedBasement', 'heatedGarage'];
const OZC_KEYS = ['buildingKw', 'dhwKw', 'sourceUrl', 'confirmed'];

const PATCHABLE = [
  ...TEXT_FIELDS,
  ...Object.keys(ENUMS),
  ...BOOL_FIELDS,
  ...DATE_FIELDS,
  ...OWNER_FIELDS,
  'meetingDurationMin',
  'buildingData',
  'ozcData',
];

/** Podmiana bloku w calosci — `null` czysci, obiekt musi miec znane klucze. */
function normalizeBlock(value, allowedKeys, label) {
  if (value === null) return { value: null };
  if (typeof value !== 'object' || Array.isArray(value)) return { error: `${label} musi byc obiektem albo null.` };
  const unknown = Object.keys(value).filter((k) => !allowedKeys.includes(k));
  if (unknown.length) return { error: `Nieznane pola w ${label}: ${unknown.join(', ')}` };
  const out = {};
  for (const key of allowedKeys) out[key] = key in value ? value[key] : null;
  return { value: out };
}

router.patch('/deals/:id', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const orgId = req.user.organizationId;
  const deal = dealById(orgId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Nie znaleziono deala.' });

  const patch = req.body || {};
  const keys = Object.keys(patch);
  if (!keys.length) return unprocessable(res, 'Brak pol do aktualizacji.');
  const unknown = keys.filter((k) => !PATCHABLE.includes(k));
  if (unknown.length) return unprocessable(res, `Nieznane pola: ${unknown.join(', ')}`);

  // Walidacja przed jakimkolwiek zapisem — patch jest wszystko-albo-nic.
  for (const [field, allowed] of Object.entries(ENUMS)) {
    if (field in patch && patch[field] !== null && !allowed.includes(patch[field])) {
      return unprocessable(res, `Nieznana wartosc pola ${field}: ${patch[field]}`);
    }
  }
  for (const field of BOOL_FIELDS) {
    if (field in patch && typeof patch[field] !== 'boolean') {
      return unprocessable(res, `Pole ${field} musi byc true/false.`);
    }
  }
  for (const field of DATE_FIELDS) {
    if (field in patch && patch[field] !== null && Number.isNaN(new Date(patch[field]).getTime())) {
      return unprocessable(res, `Pole ${field} nie jest poprawna data ISO 8601.`);
    }
  }
  if ('meetingDurationMin' in patch && patch.meetingDurationMin !== null) {
    const n = Number(patch.meetingDurationMin);
    if (!Number.isInteger(n) || n <= 0) return unprocessable(res, 'Czas trwania spotkania musi byc liczba minut.');
  }
  if ('ownerId' in patch && (patch.ownerId === null || !userById(orgId, patch.ownerId))) {
    return unprocessable(res, 'Opiekun deala jest wymagany i musi byc czlonkiem zespolu.');
  }
  for (const field of OWNER_FIELDS.filter((f) => f !== 'ownerId')) {
    if (field in patch && patch[field] !== null && !userById(orgId, patch[field])) {
      return unprocessable(res, `Nieznany pracownik w polu ${field}.`);
    }
  }

  let building;
  if ('buildingData' in patch) {
    const r = normalizeBlock(patch.buildingData, BUILDING_KEYS, 'buildingData');
    if (r.error) return unprocessable(res, r.error);
    building = r.value;
  }
  let ozc;
  if ('ozcData' in patch) {
    const r = normalizeBlock(patch.ozcData, OZC_KEYS, 'ozcData');
    if (r.error) return unprocessable(res, r.error);
    ozc = r.value;
    if (ozc) ozc.confirmed = Boolean(ozc.confirmed);
  }

  const before = {};
  for (const key of keys) before[key] = deal[key];

  for (const key of keys) {
    if (key === 'buildingData') deal.buildingData = building;
    else if (key === 'ozcData') deal.ozcData = ozc;
    else if (DATE_FIELDS.includes(key)) deal[key] = patch[key] === null ? null : new Date(patch[key]).toISOString();
    else deal[key] = patch[key];
  }
  // Zgode RODO API stempluje samo — panel pokazuje date obok checkboxa.
  if ('rodoConsent' in patch) deal.rodoConsentAt = patch.rodoConsent ? nowIso() : null;
  deal.updatedAt = nowIso();

  logActivity(req.user, deal.id, 'deal_update', { before, after: keys.reduce((a, k) => ({ ...a, [k]: deal[k] }), {}) });
  return res.json(detailShape(deal, orgId));
});

// ── Kontakty towarzyszace ────────────────────────────────────────────────────

const companionsOf = (dealId, orgId) =>
  db.dealContacts
    .filter((l) => l.dealId === dealId)
    .map((l) => clientById(orgId, l.clientId))
    .filter(Boolean);

router.get('/deals/:id/contacts', requireAuth, requirePermission('crm.view'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Nie znaleziono deala.' });
  return res.json(companionsOf(deal.id, req.user.organizationId));
});

router.post('/deals/:id/contacts', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const orgId = req.user.organizationId;
  const deal = dealById(orgId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Nie znaleziono deala.' });

  const clientId = (req.body || {}).clientId;
  const client = clientId ? clientById(orgId, clientId) : null;
  if (!client) return res.status(404).json({ message: 'Nie znaleziono klienta do dopiecia.' });
  if (client.id === deal.clientId) return unprocessable(res, 'Ten klient jest juz glownym kontaktem deala.');
  if (db.dealContacts.some((l) => l.dealId === deal.id && l.clientId === client.id)) {
    return unprocessable(res, 'Ten kontakt jest juz dopiety do deala.');
  }

  db.dealContacts.push({ dealId: deal.id, clientId: client.id });
  deal.updatedAt = nowIso();
  logActivity(req.user, deal.id, 'contact_added', { clientId: client.id });
  return res.status(201).json(companionsOf(deal.id, orgId));
});

router.patch('/deals/:id/contacts/primary', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const orgId = req.user.organizationId;
  const deal = dealById(orgId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Nie znaleziono deala.' });

  const clientId = (req.body || {}).clientId;
  const link = db.dealContacts.find((l) => l.dealId === deal.id && l.clientId === clientId);
  if (!link) return unprocessable(res, 'Ten klient nie jest kontaktem towarzyszacym tego deala.');

  const previousPrimary = deal.clientId;
  deal.clientId = clientId;
  link.clientId = previousPrimary; // zamiana miejscami: stary glowny zostaje towarzyszacym
  deal.updatedAt = nowIso();
  logActivity(req.user, deal.id, 'primary_contact_changed', { from: previousPrimary, to: clientId });
  return res.json({ ok: true });
});

router.delete('/deals/:id/contacts/:clientId', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Nie znaleziono deala.' });

  const before = db.dealContacts.length;
  db.dealContacts = db.dealContacts.filter((l) => !(l.dealId === deal.id && l.clientId === req.params.clientId));
  if (db.dealContacts.length === before) {
    return res.status(404).json({ message: 'Ten kontakt nie jest dopiety do deala.' });
  }
  deal.updatedAt = nowIso();
  logActivity(req.user, deal.id, 'contact_removed', { clientId: req.params.clientId });
  return res.status(204).end();
});

// ── Instalacje karty deala ───────────────────────────────────────────────────

router.get('/deals/:id/installations', requireAuth, requirePermission('crm.view'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Nie znaleziono deala.' });

  const current = currentInstallStage(deal.stage);
  const editable = can(req.user, 'deal.manage');
  const stages = INSTALL_STAGES.map((stage) => {
    const idx = stageIndex(stage);
    const currentIdx = stageIndex(current);
    return {
      stage,
      categories: effectiveCategories(deal.id, stage),
      editable: editable && stage === current,
      state: idx === currentIdx ? 'current' : idx < currentIdx ? 'past' : 'future',
    };
  });
  return res.json({ current, stages });
});

// ── Asystent karty deala (grounding tylko na tym dealu) ──────────────────────

router.post('/deals/:id/assistant', requireAuth, requirePermission('crm.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const deal = dealById(orgId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Nie znaleziono deala.' });

  const notes = db.voiceReports.filter((v) => v.organizationId === orgId && v.clientId === deal.clientId);
  const history = db.activities.filter((a) => a.dealId === deal.id);
  const question = ((req.body || {}).messages || []).slice(-1)[0];

  const text = [
    'Odpowiedz z board360-mock (bez modelu jezykowego).',
    `Pytanie: ${(question && question.content) || '(brak)'}`,
    `Deal "${deal.projectName || deal.id}" stoi na etapie ${STAGE_LABEL[deal.stage]}.`,
    `Podstawa: ${notes.length} notatek, ${history.length} wpisow historii.`,
  ].join('\n');

  return res.json({ text, configured: false, commsCount: notes.length, dealCount: 1 });
});

module.exports = router;

