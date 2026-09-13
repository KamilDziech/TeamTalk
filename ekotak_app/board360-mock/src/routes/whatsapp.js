'use strict';
/*
 * WhatsApp — skrzynka JEDNEGO deala (board360 `modules/whatsapp`, FR-22).
 * Zakladka „Komunikacja" karty deala w panelu i w TeamTalku.
 *
 * Dwie rzeczy, ktore atrapa odwzorowuje celowo, bo bez nich telefon zobaczylby
 * tu inny swiat niz na produkcji:
 *
 *  1. OKNO 24h. Wiadomosc free-form wolno wyslac tylko w ciagu doby od
 *     ostatniej wiadomosci PRZYCHODZACEJ. Poza oknem board360 odpowiada 422
 *     i to nie jest nasza walidacja, tylko regula WhatsApp Business — telefon
 *     ma ja zobaczyc juz przy testach offline, a nie dopiero u klienta.
 *  2. BRAK KREDENCJI META. Bez konfiguracji integracji wysylka zapisuje sie ze
 *     statusem `pending_config`: wiadomosc JEST zapisana i wyjdzie po dopieciu
 *     integracji. To nie blad i ekran nie ma go tak pokazywac.
 */

const express = require('express');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission, unprocessable } = require('../middleware');
const { db, dealById } = require('../store');

const router = express.Router();

/** Czy skonfigurowano integracje Meta. Atrapa: nigdy (jak swieza produkcja). */
const META_CONFIGURED = false;

const WINDOW_MS = 24 * 60 * 60 * 1000;

const messagesOf = (orgId, dealId) =>
  db.whatsappMessages
    .filter((m) => m.organizationId === orgId && m.dealId === dealId)
    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));

/** Czy jestesmy w oknie 24h od ostatniej wiadomosci klienta. */
function withinWindow(orgId, dealId) {
  const last = messagesOf(orgId, dealId)
    .filter((m) => m.direction === 'inbound')
    .pop();
  if (!last) return false;
  return Date.now() - new Date(last.createdAt).getTime() < WINDOW_MS;
}

/** Skrzynka komunikacji panelu — ostatnia wiadomosc per deal. */
router.get('/whatsapp/threads', requireAuth, requirePermission('crm.view'), (req, res) => {
  const byDeal = new Map();
  db.whatsappMessages
    .filter((m) => m.organizationId === req.user.organizationId)
    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))
    .forEach((m) => byDeal.set(m.dealId, m));
  res.json(
    [...byDeal.entries()].map(([dealId, last]) => ({
      dealId,
      lastMessage: last,
      lastAt: last.createdAt,
    })),
  );
});

router.get('/deals/:id/whatsapp', requireAuth, requirePermission('crm.view'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Deal nie istnieje.' });
  return res.json(messagesOf(req.user.organizationId, deal.id));
});

router.post('/deals/:id/whatsapp', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Deal nie istnieje.' });

  const body = typeof (req.body || {}).body === 'string' ? req.body.body.trim() : '';
  const template = (req.body || {}).template || null;
  if (!body && !template) {
    return unprocessable(res, 'Wiadomosc musi miec tresc albo szablon.', ['tresc']);
  }
  // Szablon wolno wyslac zawsze; free-form wylacznie w oknie 24h.
  if (!template && !withinWindow(req.user.organizationId, deal.id)) {
    return unprocessable(
      res,
      'Okno 24h zamkniete — poza nim mozna wyslac wylacznie zatwierdzony szablon.',
      [],
    );
  }

  const row = {
    id: uuid(),
    organizationId: req.user.organizationId,
    dealId: deal.id,
    direction: 'outbound',
    body: body || null,
    template,
    status: META_CONFIGURED ? 'sent' : 'pending_config',
    createdAt: nowIso(),
  };
  db.whatsappMessages.push(row);
  return res.status(201).json(row);
});

/**
 * Symulacja wiadomosci przychodzacej. Na produkcji wola to adapter webhooka
 * Meta; tutaj sluzy do OTWARCIA OKNA 24h w testach — bez niej kazda wysylka
 * z telefonu konczylaby sie kodem 422 i nie dalo by sie sprawdzic sciezki
 * udanej.
 */
router.post('/deals/:id/whatsapp/inbound', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Deal nie istnieje.' });
  const body = typeof (req.body || {}).body === 'string' ? req.body.body.trim() : '';
  if (!body) return unprocessable(res, 'Tresc wiadomosci jest wymagana.', ['tresc']);

  const row = {
    id: uuid(),
    organizationId: req.user.organizationId,
    dealId: deal.id,
    direction: 'inbound',
    body,
    template: null,
    status: 'received',
    createdAt: nowIso(),
  };
  db.whatsappMessages.push(row);
  return res.status(201).json(row);
});

module.exports = router;
