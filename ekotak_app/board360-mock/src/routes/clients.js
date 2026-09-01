'use strict';
/*
 * Kartoteka klientow. Odczyt: `crm.view`, zapis: `deal.manage`,
 * anonimizacja RODO: `settings.company` — tak jak opisuje TeamTalkApi.kt.
 */

const express = require('express');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission, unprocessable } = require('../middleware');
const { db, clientById, normalizePhone } = require('../store');

const router = express.Router();

const PATCHABLE = ['firstName', 'lastName', 'email', 'email2', 'phone', 'phone2', 'address'];

/**
 * board360 po zmianie adresu przelicza geokodowanie i dojazd z obu baz.
 * Mock robi to samo, tyle ze na udawanych liczbach — chodzi o to, zeby ekran
 * "status walidacji adresu" mial czym zamigac po zapisie.
 */
function regeocode(client) {
  const address = (client.address || '').trim();
  if (!address) {
    client.geo = null;
    client.geoCity = null;
    client.geoMunicipality = null;
    client.travel = null;
    client.city = null;
    client.street = null;
    return;
  }
  const parts = address.split(',').map((s) => s.trim()).filter(Boolean);
  client.street = parts[0] || null;
  client.city = parts.length > 1 ? parts[parts.length - 1].replace(/^\d{2}-\d{3}\s*/, '') : client.city;

  // Deterministyczne "wspolrzedne" z hasha adresu — ten sam adres zawsze da
  // ten sam punkt, wiec da sie porownac widok przed i po edycji.
  let h = 0;
  for (const ch of address) h = (h * 31 + ch.charCodeAt(0)) % 100000;
  const lat = 49.6 + (h % 900) / 1000;
  const lng = 18.6 + ((h >> 3) % 900) / 1000;
  client.geo = { lat: Number(lat.toFixed(4)), lng: Number(lng.toFixed(4)) };
  client.geoCity = client.city;
  client.geoMunicipality = client.city;
  client.travel = {
    kobiernice: { km: Number((10 + (h % 90)).toFixed(1)), min: 12 + (h % 70) },
    gliwice: { km: Number((8 + ((h >> 2) % 95)).toFixed(1)), min: 10 + ((h >> 2) % 75) },
  };
}

// ── Lista i szczegoly ────────────────────────────────────────────────────────

router.get('/clients', requireAuth, requirePermission('crm.view'), (req, res) => {
  const q = (req.query.q || '').toString().trim().toLowerCase();
  let list = db.clients.filter((c) => c.organizationId === req.user.organizationId);
  if (q) {
    const qPhone = normalizePhone(q);
    list = list.filter(
      (c) =>
        `${c.firstName} ${c.lastName}`.toLowerCase().includes(q) ||
        (c.email || '').toLowerCase().includes(q) ||
        (c.address || '').toLowerCase().includes(q) ||
        (c.city || '').toLowerCase().includes(q) ||
        (qPhone && (normalizePhone(c.phone).includes(qPhone) || normalizePhone(c.phone2).includes(qPhone))),
    );
  }
  res.json(list);
});

router.get('/clients/:id', requireAuth, requirePermission('crm.view'), (req, res) => {
  const c = clientById(req.user.organizationId, req.params.id);
  if (!c) return res.status(404).json({ message: 'Nie znaleziono klienta.' });
  return res.json(c);
});

// ── Tworzenie i edycja ───────────────────────────────────────────────────────

router.post('/clients', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const b = req.body || {};
  const firstName = typeof b.firstName === 'string' ? b.firstName.trim() : '';
  const lastName = typeof b.lastName === 'string' ? b.lastName.trim() : '';
  const missing = [];
  if (!firstName) missing.push('imie');
  if (!lastName) missing.push('nazwisko');
  if (missing.length) return unprocessable(res, 'Brak wymaganych danych klienta.', missing);

  const client = {
    id: uuid(),
    organizationId: req.user.organizationId,
    firstName,
    lastName,
    email: b.email || null,
    email2: null,
    phone: b.phone || null,
    phone2: null,
    address: b.address || null,
    postalCode: null,
    city: null,
    street: null,
    geo: null,
    geoCity: null,
    geoMunicipality: null,
    travel: null,
    type: b.type || 'wlasny',
    category: b.category || 'klient',
    createdAt: nowIso(),
    updatedAt: nowIso(),
  };
  regeocode(client);
  db.clients.push(client);
  return res.status(201).json(client);
});

router.patch('/clients/:id', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const client = clientById(req.user.organizationId, req.params.id);
  if (!client) return res.status(404).json({ message: 'Nie znaleziono klienta.' });

  const patch = req.body || {};
  const keys = Object.keys(patch);
  if (!keys.length) return unprocessable(res, 'Brak pol do aktualizacji.');

  const unknown = keys.filter((k) => !PATCHABLE.includes(k));
  if (unknown.length) return unprocessable(res, `Nieznane pola: ${unknown.join(', ')}`);

  for (const key of ['firstName', 'lastName']) {
    if (key in patch && (typeof patch[key] !== 'string' || !patch[key].trim())) {
      return unprocessable(res, 'Imie i nazwisko nie moga byc puste.', [key === 'firstName' ? 'imie' : 'nazwisko']);
    }
  }

  const addressChanged = 'address' in patch && patch.address !== client.address;
  for (const key of keys) client[key] = patch[key] === undefined ? client[key] : patch[key];
  if (addressChanged) regeocode(client);
  client.updatedAt = nowIso();
  return res.json(client);
});

// ── Scalanie duplikatow ──────────────────────────────────────────────────────
// Nieodwracalne: wszystko, co wisialo na rekordach zrodlowych, przechodzi na
// `:id`, a puste pola celu uzupelniamy pierwsza znaleziona wartoscia.

router.post('/clients/:id/merge', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const orgId = req.user.organizationId;
  const target = clientById(orgId, req.params.id);
  if (!target) return res.status(404).json({ message: 'Nie znaleziono klienta docelowego.' });

  const sourceIds = Array.isArray((req.body || {}).sourceIds) ? req.body.sourceIds : [];
  if (!sourceIds.length) return unprocessable(res, 'Podaj rekordy do scalenia (sourceIds).');
  if (sourceIds.includes(target.id)) return unprocessable(res, 'Nie mozna scalic rekordu z samym soba.');

  const sources = sourceIds.map((id) => clientById(orgId, id));
  const notFound = sourceIds.filter((id, i) => !sources[i]);
  if (notFound.length) return res.status(404).json({ message: `Nie znaleziono klientow: ${notFound.join(', ')}` });

  for (const src of sources) {
    for (const field of ['email', 'email2', 'phone', 'phone2', 'address', 'postalCode', 'city', 'street']) {
      if (!target[field] && src[field]) target[field] = src[field];
    }
    if (!target.geo && src.geo) {
      target.geo = src.geo;
      target.geoCity = src.geoCity;
      target.geoMunicipality = src.geoMunicipality;
      target.travel = src.travel;
    }
    db.callLogs.forEach((c) => { if (c.clientId === src.id) c.clientId = target.id; });
    db.voiceReports.forEach((v) => { if (v.clientId === src.id) v.clientId = target.id; });
    db.deals.forEach((d) => { if (d.clientId === src.id) d.clientId = target.id; });
    db.dealContacts.forEach((l) => { if (l.clientId === src.id) l.clientId = target.id; });
  }

  // Kontakt towarzyszacy nie moze byc rownoczesnie glownym kontaktem deala.
  db.dealContacts = db.dealContacts.filter((link) => {
    const deal = db.deals.find((d) => d.id === link.dealId);
    return !deal || deal.clientId !== link.clientId;
  });
  // Duplikaty powiazan po przepieciu.
  const seen = new Set();
  db.dealContacts = db.dealContacts.filter((l) => {
    const key = `${l.dealId}:${l.clientId}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  const removed = new Set(sources.map((s) => s.id));
  db.clients = db.clients.filter((c) => !removed.has(c.id));
  target.updatedAt = nowIso();
  return res.json(target);
});

// ── Anonimizacja RODO ────────────────────────────────────────────────────────
// Rekord zostaje (deale i statystyki maja sie zgadzac), dane osobowe znikaja.

router.post('/clients/:id/erase', requireAuth, requirePermission('settings.company'), (req, res) => {
  const client = clientById(req.user.organizationId, req.params.id);
  if (!client) return res.status(404).json({ message: 'Nie znaleziono klienta.' });

  Object.assign(client, {
    firstName: 'Dane',
    lastName: 'usuniete (RODO)',
    email: null,
    email2: null,
    phone: null,
    phone2: null,
    address: null,
    postalCode: null,
    city: null,
    street: null,
    geo: null,
    geoCity: null,
    geoMunicipality: null,
    travel: null,
    updatedAt: nowIso(),
  });
  return res.json(client);
});

// ── Asystent karty klienta ───────────────────────────────────────────────────
// Bez klucza LLM board360 odpowiada trybem informacyjnym (`configured: false`)
// — mock zachowuje sie tak zawsze, ale liczy realna podstawe odpowiedzi.

router.post('/clients/:id/assistant', requireAuth, requirePermission('crm.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const client = clientById(orgId, req.params.id);
  if (!client) return res.status(404).json({ message: 'Nie znaleziono klienta.' });

  const deals = db.deals.filter((d) => d.organizationId === orgId && d.clientId === client.id);
  const notes = db.voiceReports.filter((v) => v.organizationId === orgId && v.clientId === client.id);
  const calls = db.callLogs.filter((c) => c.organizationId === orgId && c.clientId === client.id);
  const question = ((req.body || {}).messages || []).slice(-1)[0];

  const last = notes[notes.length - 1];
  const text = [
    'Odpowiedz z board360-mock (bez modelu jezykowego).',
    `Pytanie: ${(question && question.content) || '(brak)'}`,
    `Podstawa: ${deals.length} deal(i), ${notes.length} notatek, ${calls.length} polaczen.`,
    last ? `Ostatnia notatka: ${last.transcript || last.text}` : 'Brak notatek do zacytowania.',
  ].join('\n');

  return res.json({ text, configured: false, commsCount: notes.length + calls.length, dealCount: deals.length });
});

module.exports = router;
