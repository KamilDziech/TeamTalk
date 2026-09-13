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
const { nowIso } = require('../crypto');
const { requireAuth, requirePermission, unprocessable } = require('../middleware');
const { db, dealById } = require('../store');

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

    lead.building = building;
    deal.updatedAt = nowIso();
    return res.json(building);
  },
);

module.exports = router;
