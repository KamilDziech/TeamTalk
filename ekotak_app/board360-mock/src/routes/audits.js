'use strict';
/*
 * Audyty deala (zakladka „Audyt" karty deala w TeamTalku).
 *
 * Ksztalt rekordow i regul = `api/src/modules/inspections` z board360:
 *  - jeden rekord `Audit` obsluguje DWIE rzeczy, rozroznia je `formData.kind`:
 *    audyt Heizlast (tryb + kW + notatka) i formularz audytu instalacji
 *    (`kind: 'underfloorHeating'`, jeden na pare deal + wezel katalogu),
 *  - Heizlast w trybie „szybki" liczy SERWER ze wskaznikow W/m2 — telefon
 *    podaje wejscia (`heatloadInputs`), nie wynik,
 *  - zapis audytu ofertowego dla deala z PODPISANA umowa -> 409, dopoki cialo
 *    nie niesie `zmianaOferty: true` (tu: umow nie ma, wiec 409 nie pada).
 *
 * Uprawnienia: odczyt `crm.view`, zapis `deal.manage`.
 */

const express = require('express');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission, unprocessable } = require('../middleware');
const { db, dealById } = require('../store');

const router = express.Router();

/** Wskazniki W/m2 wg standardu budynku — te same wartosci co panel. */
const STANDARD_INDEX = {
  nieocieplony: 120,
  slabo_ocieplony: 90,
  standard: 60,
  dobrze_ocieplony: 45,
  energooszczedny: 30,
  pasywny: 15,
};

const MODES = new Set(['szybki', 'din']);

/**
 * Heizlast [kW] z wejsc szybkiego szacunku. `null` = za malo danych, zeby
 * cokolwiek policzyc (rekord zapisze sie wtedy bez kW).
 */
function quickHeatload(inputs) {
  if (!inputs || typeof inputs !== 'object') return null;
  const idx = STANDARD_INDEX[inputs.standard];
  const area = Number(inputs.area);
  if (!idx || !(area > 0)) return null;
  const height = Number(inputs.height);
  const factor = height > 0 ? height / 2.6 : 1;
  return Math.round((area * idx * factor) / 100) / 10;
}

const view = (a) => ({
  id: a.id,
  dealId: a.dealId,
  heatloadMode: a.heatloadMode,
  heatloadKw: a.heatloadKw,
  heatloadInputs: a.heatloadInputs,
  formData: a.formData,
  createdAt: a.createdAt,
  updatedAt: a.updatedAt,
});

/**
 * Walidacja wspolna dla POST i PATCH. Zwraca `null`, gdy cialo jest w porzadku,
 * albo gotowy komunikat 422.
 */
function invalid(body) {
  if (body.heatloadMode != null && !MODES.has(body.heatloadMode)) {
    return 'Nieznany tryb Heizlast';
  }
  if (body.heatloadKw != null && !(Number(body.heatloadKw) > 0)) {
    return 'Heizlast musi byc liczba dodatnia';
  }
  if (body.formData != null && typeof body.formData !== 'object') {
    return 'formData musi byc obiektem';
  }
  return null;
}

router.get('/deals/:id/audits', requireAuth, requirePermission('crm.view'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Deal nie istnieje' });
  // Najnowsze pierwsze — panel i telefon pokazuja liste w tej kolejnosci.
  const list = db.audits
    .filter((a) => a.organizationId === req.user.organizationId && a.dealId === deal.id)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  res.json(list.map(view));
});

router.post('/deals/:id/audits', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Deal nie istnieje' });

  const body = req.body || {};
  const message = invalid(body);
  if (message) return unprocessable(res, message);

  const audit = {
    id: uuid(),
    organizationId: req.user.organizationId,
    dealId: deal.id,
    heatloadMode: body.heatloadMode ?? null,
    heatloadInputs: body.heatloadInputs ?? null,
    heatloadKw:
      body.heatloadMode === 'szybki'
        ? quickHeatload(body.heatloadInputs)
        : (body.heatloadKw ?? null),
    formData: body.formData ?? null,
    createdAt: nowIso(),
    updatedAt: nowIso(),
  };
  db.audits.push(audit);
  res.status(201).json(view(audit));
});

router.patch('/audits/:id', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const audit = db.audits.find(
    (a) => a.id === req.params.id && a.organizationId === req.user.organizationId,
  );
  if (!audit) return res.status(404).json({ message: 'Audyt nie istnieje' });

  const body = req.body || {};
  const message = invalid(body);
  if (message) return unprocessable(res, message);

  // PATCH nadpisuje tylko podane pola — `formData` w calosci, bo to jeden
  // dokument formularza, a nie zbior niezaleznych kluczy.
  if ('heatloadMode' in body) audit.heatloadMode = body.heatloadMode ?? null;
  if ('heatloadInputs' in body) audit.heatloadInputs = body.heatloadInputs ?? null;
  if ('formData' in body) audit.formData = body.formData ?? null;
  if (body.heatloadMode === 'szybki') {
    audit.heatloadKw = quickHeatload(body.heatloadInputs ?? audit.heatloadInputs);
  } else if ('heatloadKw' in body) {
    audit.heatloadKw = body.heatloadKw ?? null;
  }
  audit.updatedAt = nowIso();

  res.json(view(audit));
});

/**
 * Umowy deala. Atrapa nie ma modulu Umowy, ale telefon pyta o ta liste, zeby
 * wiedziec, czy oferta jest juz zamknieta podpisem — pusta lista znaczy
 * „nic nie podpisane, audyt otwarty" i to jest tutaj stan docelowy.
 */
router.get('/deals/:id/contracts', requireAuth, requirePermission('crm.view'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Deal nie istnieje' });
  res.json([]);
});

module.exports = router;
