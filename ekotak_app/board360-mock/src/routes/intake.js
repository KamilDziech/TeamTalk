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

module.exports = router;
