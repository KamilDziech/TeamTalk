'use strict';
/*
 * Leadownia cennikinstalacji.pl — zakladka "LEAD" karty deala.
 *
 * WAZNE: deal spoza leadowni nie ma rekordu zgloszenia i board360 odpowiada
 * wtedy 200 z PUSTYM cialem (nie 404, nie `null`). Mobilka na tym polega
 * (`LeadIntakeRepositoryImpl` czyta surowe `ResponseBody`), wiec mock musi
 * odpowiadac tak samo — stad `res.end()` bez JSON-a.
 */

const express = require('express');
const { nowIso, uuid } = require('../crypto');
const { requireAuth, requirePermission, unprocessable } = require('../middleware');
const { db, dealById, userById } = require('../store');
const { makeDeal } = require('../seed');

const router = express.Router();

const leadFor = (dealId) => db.leads.find((l) => l.dealId === dealId) || null;

router.get('/intake/deal/:dealId/lead', requireAuth, requirePermission('crm.view'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.dealId);
  if (!deal) return res.status(404).json({ message: 'Nie znaleziono deala.' });

  const lead = leadFor(deal.id);
  if (!lead) return res.status(200).end(); // deal spoza leadowni

  const { dealId, ...body } = lead;
  return res.json(body);
});

router.patch('/intake/deal/:dealId/lead/note', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.dealId);
  if (!deal) return res.status(404).json({ message: 'Nie znaleziono deala.' });

  const lead = leadFor(deal.id);
  if (!lead) return res.status(404).json({ message: 'Ten deal nie ma zgloszenia z leadowni.' });

  const body = req.body || {};
  if (!('note' in body)) return unprocessable(res, 'Pole `note` jest wymagane (moze byc null).');
  const note = body.note === null ? null : String(body.note).trim() || null;

  lead.note = note;
  deal.updatedAt = nowIso();
  return res.json({ note });
});

/*
 * Reczna korekta danych budynku ze zgloszenia (panel: okno "Zmien dane
 * budynku" pod ikonografika; mobilka: karta "Budynek wg zgloszenia").
 * Podmieniamy CALY rekord, tak jak board360 — schemat wymaga obecnosci
 * kazdego pola, wiec brak klucza to 422, a nie ciche pominiecie.
 */
const BUILDING_TEXT = ['shape', 'construction', 'area', 'people', 'stage', 'windows'];

router.patch(
  '/intake/deal/:dealId/lead/building',
  requireAuth,
  requirePermission('deal.manage'),
  (req, res) => {
    const deal = dealById(req.user.organizationId, req.params.dealId);
    if (!deal) return res.status(404).json({ message: 'Nie znaleziono deala.' });

    const lead = leadFor(deal.id);
    if (!lead) return res.status(404).json({ message: 'Ten deal nie ma zgloszenia z leadowni.' });

    const body = req.body || {};
    for (const key of BUILDING_TEXT) {
      if (!(key in body)) return unprocessable(res, 'Pole `' + key + '` jest wymagane (moze byc null).');
    }
    for (const key of ['heatedBasement', 'heatedGarage']) {
      if (typeof body[key] !== 'boolean') return unprocessable(res, 'Pole `' + key + '` musi byc boolean.');
    }
    const floors = body.floors == null ? null : Number(body.floors);
    if (floors !== null && (!Number.isInteger(floors) || floors < 1 || floors > 20)) {
      return unprocessable(res, 'Pole `floors` musi byc liczba 1-20 albo null.');
    }

    const building = { floors, heatedBasement: body.heatedBasement, heatedGarage: body.heatedGarage };
    for (const key of BUILDING_TEXT) {
      const raw = body[key];
      building[key] = raw == null ? null : String(raw).trim() || null;
    }

    // installTiming (dom zamieszkaly) jest opcjonalny — brak klucza = bez zmian,
    // jak w board360 (starsze klienty nie wysylaja tego pola).
    building.installTiming = 'installTiming' in body
      ? (body.installTiming == null ? null : String(body.installTiming).trim() || null)
      : (lead.building && lead.building.installTiming) || null;

    lead.building = building;
    deal.updatedAt = nowIso();
    return res.json(building);
  },
);

/*
 * Kreator LEAD w aplikacji (board360: `IntakeAppController`). Zaklada klienta,
 * deal na etapie `lead` i zgloszenie — w przyblizeniu, bez geokodowania
 * i skrotu podlogowki. `clientRef` z telefonu chroni przed duplikatem, gdy
 * kolejka offline wysle ten sam lead drugi raz.
 */
const APP_CHANNELS = ['tel', 'spotkanie', 'polecenie', 'targi'];

// Wydarzenia marketingowe board360 — atrapa nie ma modulu Marketing.
const EVENTS = [
  { id: uuid(), name: 'Targi Bielsko-Biala 2026', eventDate: '2026-10-03' },
  { id: uuid(), name: 'Katowice Budma', eventDate: '2026-09-26' },
  { id: uuid(), name: 'Krakow Dom i Ogrod', eventDate: null },
];

router.get('/intake/app/events', requireAuth, requirePermission('deal.manage'), (_req, res) => {
  res.json(EVENTS);
});

router.post('/intake/app/lead', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const b = req.body || {};
  if (!b.clientRef) return res.status(400).json({ message: 'Niepoprawny identyfikator zgloszenia.' });
  if (!APP_CHANNELS.includes(b.channel)) return res.status(400).json({ message: 'Nieznany kanal leada.' });
  if (!b.fullName || !String(b.fullName).trim()) return res.status(400).json({ message: 'Podaj imie i nazwisko.' });
  if (!b.phone && !b.email) return res.status(400).json({ message: 'Podaj telefon lub e-mail.' });
  if (!b.city && !b.postalCode) return res.status(400).json({ message: 'Podaj miejscowosc lub kod pocztowy.' });

  const orgId = req.user.organizationId;
  const existing = db.leads.find((l) => l.clientRef === b.clientRef);
  if (existing) return res.status(201).json({ dealId: existing.dealId, duplicate: true });

  const taker = userById(orgId, b.takenById) || userById(orgId, req.user.id);
  const takerName = taker ? `${taker.firstName || ''} ${taker.lastName || ''}`.trim() : null;
  const [firstName, ...rest] = String(b.fullName).trim().split(/\s+/);
  const now = nowIso();

  const client = {
    id: uuid(),
    organizationId: orgId,
    firstName,
    lastName: rest.join(' ') || '—',
    email: b.email || null,
    email2: null,
    phone: b.phone || null,
    phone2: null,
    address: b.city || null,
    postalCode: b.postalCode || null,
    city: b.city || null,
    street: null,
    geo: null,
    geoCity: null,
    geoMunicipality: null,
    travel: null,
    type: 'klient',
    category: null,
    createdAt: now,
    updatedAt: now,
  };
  db.clients.push(client);

  const event = b.channel === 'targi' ? EVENTS.find((e) => e.id === b.eventId) : null;
  const origin = b.channel === 'targi' ? null : b.leadOrigin;
  const label = event ? event.name : origin ? (b.referralFrom ? `${origin}: ${b.referralFrom}` : origin) : null;
  const source = label ? `${b.channel}/${label.replace(/\//g, ' ')}` : b.channel;
  const newHouse = b.occupancy === 'w_budowie';
  const building = b.building || {};

  const deal = makeDeal(db, {
    organizationId: orgId,
    clientId: client.id,
    ownerId: req.user.id,
    source,
    leadIntroducer: takerName,
    buildingKind: newHouse ? 'nowy' : 'modernizacja',
    projectName: newHouse ? b.projectName || null : null,
    buildingData: { occupancy: b.occupancy },
    stageEnteredAt: now,
    createdAt: now,
    updatedAt: now,
  });

  db.leads.push({
    dealId: deal.id,
    clientRef: b.clientRef,
    channel: b.channel,
    source,
    sourceLabel: label,
    fullName: String(b.fullName).trim(),
    phone: b.phone || null,
    email: b.email || null,
    city: b.city || null,
    interest: (b.installations || []).join(', ') || null,
    budget: null,
    message: null,
    note: null,
    consent: false,
    submittedBy: takerName,
    createdAt: now,
    floorHeating: b.floorHeating || null,
    building: {
      shape: newHouse ? building.shape || null : null,
      construction: newHouse ? building.construction || null : null,
      area: newHouse && building.areaM2 ? `${building.areaM2} m²` : null,
      people: null,
      floors: null,
      stage: newHouse ? null : 'Modernizuję instalacje — mieszkam już',
      windows: null,
      installTiming: null,
      heatedBasement: newHouse && building.heatedBasement === true,
      heatedGarage: newHouse && building.heatedGarage === true,
    },
  });

  return res.status(201).json({ dealId: deal.id, duplicate: false });
});

module.exports = router;
