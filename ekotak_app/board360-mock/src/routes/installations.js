'use strict';
/*
 * MONTAZE — zakladka „Montaz" karty deala (TeamTalk) i modul Montaze panelu.
 *
 * Zrodlo prawdy dla ksztaltu odpowiedzi: `InstallationsController` w board360
 * plus `DealMontazPanel.tsx`. Trasy sa dokladnie te, ktorych potrzebuje teczka
 * robocza ekipy:
 *
 *   GET    /installations?dealId=            lista montazy deala
 *   POST   /installations                    nowy etap robot
 *   PATCH  /installations/:id                zakres, obsada, ekipa, uwaga
 *   GET    /installations/crews              ekipy do obsady (modul Zespol)
 *   GET    /installations/:id/deal-materials lista wyjazdowa z magazynu
 *   POST   /installations/:id/deal-materials/issue   wydanie na budowe
 *   GET    /installations/:id/photos         zdjecia powykonawcze
 *   POST   /installations/:id/photos         zdjecie z aparatu (multipart)
 *   GET    /installations/:id/photos/:photoId   tresc zdjecia
 *
 * UPRAWNIENIA — na nich stoi caly test tej zakladki:
 *  • odczyt: `installation.view` (ma go montaz i serwisant — jada w teren),
 *  • planowanie i obsada: `installation.assign` (koordynator, admin, zarzad),
 *  • WYDANIE MATERIALU: prawo otwiera `installation.view`, ale przejsc moze
 *    tylko OBSADA tego montazu albo ktos z `installation.assign`. To druga,
 *    swiadomie waska furtka obok Magazynu: monter wyda material wylacznie na
 *    robote, na ktora sam jedzie (`IssueInstallationMaterials` w board360).
 *
 * Terminu z karty deala panel NIE zmienia (przesuniecie widzi pojemnosc okien
 * w module Montaze), ale `PATCH` przyjmuje `scheduledAt` — tak samo jak
 * board360, bo z tej samej trasy korzysta modul.
 */

const express = require('express');
const fs = require('fs');
const path = require('path');
const multer = require('multer');

const { UPLOADS_DIR, MAX_UPLOAD_BYTES } = require('../config');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission, can, unprocessable } = require('../middleware');
const { db, dealById, userById } = require('../store');

const router = express.Router();
const upload = multer({ dest: UPLOADS_DIR, limits: { fileSize: MAX_UPLOAD_BYTES } });

const STATUSES = ['reserved', 'planned', 'in_progress', 'done'];
const DIFFICULTIES = ['latwy', 'normalny', 'trudny'];

/**
 * Widok montazu. `assignees` (obsada z rolami) i `assigneeIds` (sama lista
 * osob) jada RAZEM, tak jak w board360: panel czyta pierwsze, starsze klienty
 * drugie, a rozjazd miedzy nimi wygladalby jak zniknieta obsada.
 */
const view = (i) => ({
  id: i.id,
  dealId: i.dealId,
  scheduledAt: i.scheduledAt,
  status: i.status,
  difficulty: i.difficulty || null,
  teamNote: i.teamNote || null,
  nodeIds: i.nodeIds || [],
  crewId: i.crewId || null,
  assignees: (i.assignees || []).map((a) => ({ userId: a.userId, role: a.role || null })),
  assigneeIds: (i.assignees || []).map((a) => a.userId),
  briefedAt: i.briefedAt || null,
  briefingMessageId: i.briefingMessageId || null,
  durationDays: i.durationDays || 2,
});

const installationById = (orgId, id) =>
  db.installations.find((i) => i.organizationId === orgId && i.id === id) || null;

/** Czy ta osoba jedzie na ten montaz — od tego zalezy wydanie materialu. */
const inCrew = (installation, userId) =>
  (installation.assignees || []).some((a) => a.userId === userId);

// ── Lista i tworzenie ────────────────────────────────────────────────────────

router.get('/installations', requireAuth, requirePermission('installation.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const { dealId } = req.query;
  let rows = db.installations.filter((i) => i.organizationId === orgId);
  if (dealId) rows = rows.filter((i) => i.dealId === dealId);
  rows = rows.sort((a, b) => String(a.scheduledAt).localeCompare(String(b.scheduledAt)));
  res.json(rows.map(view));
});

/**
 * Ekipy do obsady. Trasa MUSI stac przed `/installations/:id`, inaczej „crews"
 * wpadnie w parametr — ta sama pulapka co przy `/tasks/members`.
 */
router.get(
  '/installations/crews',
  requireAuth,
  requirePermission('installation.view'),
  (req, res) => {
    const rows = db.crews
      .filter((c) => c.organizationId === req.user.organizationId)
      .map((c) => ({
        id: c.id,
        name: c.name,
        color: c.color || null,
        leaderId: c.leaderId || null,
        memberIds: c.memberIds || [],
      }));
    res.json(rows);
  },
);

router.post('/installations', requireAuth, requirePermission('installation.assign'), (req, res) => {
  const orgId = req.user.organizationId;
  const body = req.body || {};
  const deal = dealById(orgId, body.dealId);
  if (!deal) return res.status(404).json({ message: 'Deal nie istnieje.' });
  if (!body.scheduledAt) return unprocessable(res, 'Podaj termin montazu.');

  const row = {
    id: uuid(),
    organizationId: orgId,
    dealId: deal.id,
    scheduledAt: body.scheduledAt,
    status: 'planned',
    difficulty: DIFFICULTIES.includes(body.difficulty) ? body.difficulty : null,
    teamNote: typeof body.teamNote === 'string' ? body.teamNote : null,
    nodeIds: Array.isArray(body.nodeIds) ? body.nodeIds : [],
    crewId: body.crewId || null,
    assignees: Array.isArray(body.assignees)
      ? body.assignees.map((a) => ({ userId: a.userId, role: a.role || null }))
      : (body.assigneeIds || []).map((id) => ({ userId: id, role: null })),
    briefedAt: null,
    briefingMessageId: null,
    durationDays: 2,
    createdAt: nowIso(),
  };
  db.installations.push(row);
  return res.status(201).json(view(row));
});

/**
 * Zmiana montazu. Kazde pole osobno opcjonalne, a `null` w `crewId`/`teamNote`
 * to WARTOSC (wyczysc), nie brak zmiany — dlatego sprawdzamy obecnosc klucza
 * w ciele, a nie jego wartosc.
 */
router.patch(
  '/installations/:id',
  requireAuth,
  requirePermission('installation.assign'),
  (req, res) => {
    const row = installationById(req.user.organizationId, req.params.id);
    if (!row) return res.status(404).json({ message: 'Montaz nie istnieje.' });
    const body = req.body || {};

    if ('scheduledAt' in body && body.scheduledAt) row.scheduledAt = body.scheduledAt;
    if ('status' in body) {
      if (!STATUSES.includes(body.status)) return unprocessable(res, 'Nieznany stan montazu.');
      row.status = body.status;
    }
    if ('difficulty' in body) {
      if (body.difficulty !== null && !DIFFICULTIES.includes(body.difficulty)) {
        return unprocessable(res, 'Nieznana trudnosc montazu.');
      }
      row.difficulty = body.difficulty;
    }
    if ('teamNote' in body) row.teamNote = body.teamNote || null;
    if ('crewId' in body) row.crewId = body.crewId || null;
    if ('nodeIds' in body) {
      if (!Array.isArray(body.nodeIds)) return unprocessable(res, 'Zakres musi byc lista.');
      row.nodeIds = body.nodeIds;
    }
    if ('assignees' in body) {
      if (!Array.isArray(body.assignees)) return unprocessable(res, 'Obsada musi byc lista.');
      row.assignees = body.assignees
        .filter((a) => a && a.userId)
        .map((a) => ({ userId: a.userId, role: a.role || null }));
    } else if ('assigneeIds' in body && Array.isArray(body.assigneeIds)) {
      row.assignees = body.assigneeIds.map((id) => ({ userId: id, role: null }));
    }
    if ('briefedAt' in body) row.briefedAt = body.briefedAt || null;
    if ('briefingMessageId' in body) row.briefingMessageId = body.briefingMessageId || null;

    return res.json(view(row));
  },
);

// ── Lista wyjazdowa (material z magazynu) ────────────────────────────────────

/**
 * Co magazyn trzyma odlozone pod deal tego montazu. `missing` liczymy tak samo
 * jak board360 — z pokrycia rezerwacji stanem magazynu — bo telefon tego NIE
 * przelicza i pokazuje liczbe, ktora dostanie.
 */
const materialView = (r) => ({
  id: r.id,
  itemName: r.productName || r.itemName,
  itemCode: r.itemCode || null,
  quantity: r.quantity,
  unit: r.unit || 'szt',
  status: r.status,
  covered: r.covered || 0,
  missing: r.status === 'active' ? Math.max(0, (r.quantity || 0) - (r.covered || 0)) : 0,
  issuedAt: r.issuedAt || null,
  issuedById: r.issuedById || null,
  note: r.note || null,
});

router.get(
  '/installations/:id/deal-materials',
  requireAuth,
  requirePermission('installation.view'),
  (req, res) => {
    const row = installationById(req.user.organizationId, req.params.id);
    if (!row) return res.status(404).json({ message: 'Montaz nie istnieje.' });
    const rows = db.reservations
      .filter(
        (r) =>
          r.organizationId === row.organizationId &&
          r.dealId === row.dealId &&
          (r.status === 'active' || r.status === 'done'),
      )
      .sort((a, b) => String(a.neededBy).localeCompare(String(b.neededBy)));
    return res.json(rows.map(materialView));
  },
);

/**
 * Wydanie na budowe. Brakow NIE blokujemy — zdarza sie, ze ekipa zabiera to,
 * co jest, a reszte dowozi kierownik; blokada wymuszalaby obchodzenie systemu.
 */
router.post(
  '/installations/:id/deal-materials/issue',
  requireAuth,
  requirePermission('installation.view'),
  (req, res) => {
    const row = installationById(req.user.organizationId, req.params.id);
    if (!row) return res.status(404).json({ message: 'Montaz nie istnieje.' });
    if (!can(req.user, 'installation.assign') && !inCrew(row, req.user.id)) {
      return res.status(403).json({
        message: 'Material wydaje obsada tego montazu albo koordynator.',
      });
    }
    const ids = Array.isArray(req.body && req.body.reservationIds) ? req.body.reservationIds : [];
    if (ids.length === 0) return unprocessable(res, 'Wskaz pozycje do wydania.');

    let issued = 0;
    for (const id of ids) {
      const reservation = db.reservations.find(
        (r) =>
          r.id === id &&
          r.organizationId === row.organizationId &&
          r.dealId === row.dealId &&
          r.status === 'active',
      );
      if (!reservation) continue;
      reservation.status = 'done';
      reservation.issuedAt = nowIso();
      reservation.issuedById = req.user.id;
      reservation.updatedAt = nowIso();
      issued += 1;
    }
    return res.json({ issued });
  },
);

// ── Zdjecia powykonawcze ─────────────────────────────────────────────────────

const photoView = (p) => ({
  id: p.id,
  installationId: p.installationId,
  contentType: p.contentType,
  size: p.size,
  caption: p.caption || null,
  createdAt: p.createdAt,
});

router.get(
  '/installations/:id/photos',
  requireAuth,
  requirePermission('installation.view'),
  (req, res) => {
    const row = installationById(req.user.organizationId, req.params.id);
    if (!row) return res.status(404).json({ message: 'Montaz nie istnieje.' });
    const rows = db.installationPhotos
      .filter((p) => p.installationId === row.id)
      .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));
    return res.json(rows.map(photoView));
  },
);

/**
 * Wgranie zdjecia. Prawo to samo co odczyt (`installation.view`) — zdjecia robi
 * ekipa na budowie, a nie koordynator w biurze; tak samo w board360.
 */
router.post(
  '/installations/:id/photos',
  requireAuth,
  requirePermission('installation.view'),
  upload.single('file'),
  (req, res) => {
    const row = installationById(req.user.organizationId, req.params.id);
    if (!row) return res.status(404).json({ message: 'Montaz nie istnieje.' });
    if (!req.file) return unprocessable(res, 'Brak pliku (pole „file").');

    const id = uuid();
    const name = req.file.originalname || 'zdjecie.jpg';
    const ext = (path.extname(name) || '.jpg').replace(/[^.\w]/g, '');
    const key = `installation-photos-${id}${ext}`;
    fs.renameSync(req.file.path, path.join(UPLOADS_DIR, key));

    const photo = {
      id,
      organizationId: row.organizationId,
      installationId: row.id,
      storageKey: key,
      size: req.file.size,
      contentType: req.file.mimetype || 'image/jpeg',
      caption: (req.body && req.body.caption) || null,
      uploadedBy: req.user.id,
      createdAt: nowIso(),
    };
    db.installationPhotos.push(photo);
    return res.status(201).json(photoView(photo));
  },
);

router.get(
  '/installations/:id/photos/:photoId',
  requireAuth,
  requirePermission('installation.view'),
  (req, res) => {
    const row = installationById(req.user.organizationId, req.params.id);
    if (!row) return res.status(404).json({ message: 'Montaz nie istnieje.' });
    const photo = db.installationPhotos.find(
      (p) => p.id === req.params.photoId && p.installationId === row.id,
    );
    if (!photo) return res.status(404).json({ message: 'Zdjecie nie istnieje.' });
    const file = path.join(UPLOADS_DIR, photo.storageKey);
    if (!fs.existsSync(file)) {
      return res.status(404).json({ message: 'Tresc zdjecia zniknela z dysku atrapy.' });
    }
    res.setHeader('Content-Type', photo.contentType);
    return res.sendFile(file);
  },
);

// ── Odprawa (modul Odprawy) ──────────────────────────────────────────────────

/*
 * Odprawa montazu to komunikat modulu Odprawy z wymaganym potwierdzeniem.
 * Odbiorcami sa KONKRETNE OSOBY z obsady, nie ekipa jako grupa: jada ci ludzie,
 * a ich potwierdzenia maja sie zgadzac z lista obecnosci na budowie.
 *
 * Atrapa obsluguje dokladnie tyle, ile potrzebuje karta montazu: publikacje
 * (`briefing.publish` — koordynator; SERWISANT DOSTANIE 403, i na tym polega
 * test bramki) oraz licznik potwierdzen widoczny wylacznie dla publikujacego.
 */

router.post('/briefing', requireAuth, requirePermission('briefing.publish'), (req, res) => {
  const body = req.body || {};
  const title = String(body.title || '').trim();
  if (!title) return unprocessable(res, 'Podaj tytul komunikatu.');
  const userIds = Array.isArray(body.audienceUserIds) ? body.audienceUserIds : [];
  if (body.audienceKind === 'users' && userIds.length === 0) {
    return unprocessable(res, 'Komunikat bez odbiorcow.');
  }

  const row = {
    id: uuid(),
    organizationId: req.user.organizationId,
    title,
    body: String(body.body || ''),
    priority: body.priority === 'high' ? 'high' : 'normal',
    requiresAck: body.requiresAck !== false,
    authorId: req.user.id,
    publishedAt: nowIso(),
    // Potwierdzenia zakladamy od razu, wszystkie puste — telefon pokazuje
    // „potwierdzili 0 z 3", a nie brak licznika.
    receipts: userIds.map((userId) => ({ userId, ackAt: null })),
  };
  db.briefings.push(row);
  return res.status(201).json({ id: row.id, recipients: row.receipts.length });
});

router.get(
  '/briefing/sent/:id/receipts',
  requireAuth,
  requirePermission('briefing.publish'),
  (req, res) => {
    const row = db.briefings.find(
      (b) => b.id === req.params.id && b.organizationId === req.user.organizationId,
    );
    if (!row) return res.status(404).json({ message: 'Komunikat nie istnieje.' });
    return res.json(
      row.receipts.map((r) => {
        const user = userById(req.user.organizationId, r.userId);
        return {
          userId: r.userId,
          name: user ? [user.firstName, user.lastName].filter(Boolean).join(' ') : r.userId,
          role: user ? user.role : '',
          ackAt: r.ackAt,
        };
      }),
    );
  },
);

module.exports = router;
