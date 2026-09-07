'use strict';
/*
 * Pliki deala — zakladka „Pliki" karty. Odczyt i pobranie: `crm.view`;
 * wgranie, usuniecie, zmiana sekcji i zapis przygotowania rzutu: `deal.manage`.
 *
 * Zrodlo prawdy dla ksztaltu odpowiedzi: `DocumentsController` w board360 oraz
 * `DealDocumentDto` aplikacji. Tresc lezy na dysku w UPLOADS_DIR (w board360 —
 * w MinIO), tutaj trzymamy same metadane.
 *
 * `planData` to SWOBODNY JSON: mieszka w nim przygotowanie rzutu (skala plus
 * obrysy pomieszczen) zapisywane i przez panel, i przez telefon. Atrapa go NIE
 * interpretuje — sprawdza tylko typ i rozsadny rozmiar, dokladnie jak board360.
 */

const express = require('express');
const fs = require('fs');
const path = require('path');
const multer = require('multer');

const { UPLOADS_DIR, MAX_UPLOAD_BYTES } = require('../config');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission, unprocessable } = require('../middleware');
const { db, dealById } = require('../store');

const router = express.Router();
const upload = multer({ dest: UPLOADS_DIR, limits: { fileSize: MAX_UPLOAD_BYTES } });

const CATEGORIES = ['projekt', 'dotacja', 'protokol', 'audyt', 'montaz', 'umowa', 'inne'];

/** Zapora na smieciowy JSON — rzut z obrysami miesci sie swobodnie. */
const PLAN_DATA_MAX_CHARS = 512 * 1024;

const isPlainObject = (v) => typeof v === 'object' && v !== null && !Array.isArray(v);

/**
 * Sekcja zgadywana z nazwy pliku, gdy klient nie podal `category` („wykryj
 * automatycznie" w panelu). Regula jest umowna i celowo prosta — chodzi o to,
 * zeby zachowanie „bez category" bylo widoczne w testach, a nie o trafnosc.
 */
function guessCategory(name) {
  const n = String(name || '').toLowerCase();
  if (/rzut|projekt|przekr[oó]j/.test(n)) return 'projekt';
  if (/dotacj|czyste|moje\s*ciep/.test(n)) return 'dotacja';
  if (/protok/.test(n)) return 'protokol';
  if (/audyt/.test(n)) return 'audyt';
  if (/monta[zż]/.test(n)) return 'montaz';
  if (/umow|aneks/.test(n)) return 'umowa';
  return 'inne';
}

const shape = (row) => ({
  id: row.id,
  name: row.name,
  size: row.size,
  contentType: row.contentType,
  category: row.category,
  planData: row.planData ?? null,
  createdAt: row.createdAt,
});

const documentById = (orgId, id) =>
  db.dealDocuments.find((d) => d.id === id && d.organizationId === orgId) || null;

const fileOf = (row) => path.join(UPLOADS_DIR, path.basename(row.storageKey));

// ── Odczyt ───────────────────────────────────────────────────────────────────

router.get('/deals/:id/documents', requireAuth, requirePermission('crm.view'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Deal nie istnieje.' });
  const list = db.dealDocuments
    .filter((d) => d.organizationId === req.user.organizationId && d.dealId === deal.id)
    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
  return res.json(list.map(shape));
});

router.get('/documents/:id', requireAuth, requirePermission('crm.view'), (req, res) => {
  const row = documentById(req.user.organizationId, req.params.id);
  if (!row) return res.status(404).json({ message: 'Plik nie istnieje.' });
  const file = fileOf(row);
  if (!fs.existsSync(file)) return res.status(404).json({ message: 'Brak tresci pliku.' });
  res.setHeader('Content-Type', row.contentType);
  // `filename*` w UTF-8 — nazwy z polskimi znakami inaczej sie sypia.
  res.setHeader(
    'Content-Disposition',
    `attachment; filename*=UTF-8''${encodeURIComponent(row.name)}`,
  );
  return fs.createReadStream(file).pipe(res);
});

/**
 * Metadane podgladu. Atrapa nie ma czym renderowac PDF-ow, wiec mowi wprost
 * `pdf: false` — telefon renderuje strony u siebie (`PdfRenderer`), a panel po
 * prostu nie pokaze paska miniatur.
 */
router.get('/documents/:id/preview', requireAuth, requirePermission('crm.view'), (req, res) => {
  const row = documentById(req.user.organizationId, req.params.id);
  if (!row) return res.status(404).json({ message: 'Plik nie istnieje.' });
  return res.json({ pdf: false, pages: 0 });
});

router.get('/documents/:id/preview/:page', requireAuth, requirePermission('crm.view'), (_req, res) =>
  res.status(501).json({ message: 'Atrapa nie renderuje stron PDF.' }),
);

// ── Zapis ────────────────────────────────────────────────────────────────────

router.post(
  '/deals/:id/documents',
  requireAuth,
  requirePermission('deal.manage'),
  upload.single('file'),
  (req, res) => {
    const deal = dealById(req.user.organizationId, req.params.id);
    if (!deal) return res.status(404).json({ message: 'Deal nie istnieje.' });
    if (!req.file) return unprocessable(res, 'Brak pliku (pole „file").');

    const name = req.file.originalname || 'plik';
    const raw = req.body && req.body.category;
    const category = CATEGORIES.includes(raw) ? raw : guessCategory(name);

    const id = uuid();
    const ext = (path.extname(name) || '').replace(/[^.\w]/g, '');
    const key = `deal-documents/${req.user.organizationId}/${id}${ext}`;
    fs.renameSync(req.file.path, path.join(UPLOADS_DIR, path.basename(key)));

    const row = {
      id,
      organizationId: req.user.organizationId,
      dealId: deal.id,
      name,
      storageKey: key,
      size: req.file.size,
      contentType: req.file.mimetype || 'application/octet-stream',
      category,
      planData: null,
      uploadedBy: req.user.id,
      createdAt: nowIso(),
    };
    db.dealDocuments.push(row);
    return res.status(201).json(shape(row));
  },
);

router.patch('/documents/:id', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const row = documentById(req.user.organizationId, req.params.id);
  if (!row) return res.status(404).json({ message: 'Plik nie istnieje.' });
  const category = req.body && req.body.category;
  if (!CATEGORIES.includes(category)) {
    return res.status(400).json({ message: 'Nieprawidlowa sekcja pliku.' });
  }
  row.category = category;
  return res.json(shape(row));
});

/**
 * Przygotowanie rzutu. Cialo: `{ planData: {...} | null }` — `null` KASUJE
 * przygotowanie i musi dojsc jako jawny null, a nie jako brak pola.
 */
router.patch(
  '/documents/:id/plan-data',
  requireAuth,
  requirePermission('deal.manage'),
  (req, res) => {
    const row = documentById(req.user.organizationId, req.params.id);
    if (!row) return res.status(404).json({ message: 'Plik nie istnieje.' });
    const planData = req.body ? req.body.planData : undefined;
    if (planData !== null && !isPlainObject(planData)) {
      return res.status(400).json({ message: 'Nieprawidlowe przygotowanie rzutu.' });
    }
    if (planData !== null && JSON.stringify(planData).length > PLAN_DATA_MAX_CHARS) {
      return res.status(400).json({ message: 'Przygotowanie rzutu jest za duze.' });
    }
    row.planData = planData;
    return res.json(shape(row));
  },
);

router.delete('/documents/:id', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const idx = db.dealDocuments.findIndex(
    (d) => d.id === req.params.id && d.organizationId === req.user.organizationId,
  );
  if (idx < 0) return res.status(404).json({ message: 'Plik nie istnieje.' });
  const [row] = db.dealDocuments.splice(idx, 1);
  // Plik z dysku tez leci — atrapa nie ma po co zbierac smieci miedzy testami.
  try {
    fs.unlinkSync(fileOf(row));
  } catch (_) {}
  return res.status(204).end();
});

module.exports = router;
