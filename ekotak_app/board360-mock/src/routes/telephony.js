'use strict';
/*
 * Telefonia (prompty A2/A3): dziennik polaczen, notatki po rozmowie z
 * nagraniem i rejestr urzadzen. Wszystko za uprawnieniem `telephony.use`.
 */

const express = require('express');
const fs = require('fs');
const path = require('path');
const multer = require('multer');

const { UPLOADS_DIR, MAX_UPLOAD_BYTES, MOCK_TRANSCRIPT } = require('../config');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission } = require('../middleware');
const { db, findClientByPhone } = require('../store');

const router = express.Router();
const upload = multer({ dest: UPLOADS_DIR, limits: { fileSize: MAX_UPLOAD_BYTES } });

const sinceMs = (q) => (q ? new Date(q).getTime() : null);
const limitOf = (q) => (q ? Number(q) : null);

// ── Dziennik polaczen ────────────────────────────────────────────────────────

/** Idempotencja po (userId, phoneNumber, startedAt) — telefon moze wyslac dubla. */
function upsertCallLog(user, input) {
  const phoneNumber = String(input.phoneNumber || '');
  const startedAt = input.startedAt || nowIso();
  const existing = db.callLogs.find(
    (c) => c.userId === user.id && c.phoneNumber === phoneNumber && c.startedAt === startedAt,
  );
  if (existing) return existing;

  const client = input.clientId
    ? db.clients.find((c) => c.id === input.clientId && c.organizationId === user.organizationId) || null
    : findClientByPhone(user.organizationId, phoneNumber);

  const row = {
    id: uuid(),
    organizationId: user.organizationId,
    userId: user.id,
    clientId: client ? client.id : null,
    phoneNumber,
    direction: input.direction || 'outbound',
    simSlot: input.simSlot === undefined ? null : input.simSlot,
    startedAt,
    endedAt: input.endedAt || null,
    durationSec: input.durationSec === undefined ? null : input.durationSec,
    createdAt: nowIso(),
  };
  db.callLogs.push(row);
  return row;
}

router.post('/call-logs', requireAuth, requirePermission('telephony.use'), (req, res) => {
  const items = Array.isArray(req.body) ? req.body : [req.body];
  const saved = items.map((it) => upsertCallLog(req.user, it || {}));
  res.status(201).json(saved);
});

router.get('/call-logs', requireAuth, requirePermission('telephony.use'), (req, res) => {
  const since = sinceMs(req.query.since);
  const limit = limitOf(req.query.limit);
  let list = db.callLogs.filter((c) => c.organizationId === req.user.organizationId);
  if (req.user.clientVisibility === 'own') list = list.filter((c) => c.userId === req.user.id);
  if (since) list = list.filter((c) => new Date(c.startedAt).getTime() >= since);
  list.sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt));
  if (limit) list = list.slice(0, limit);
  res.json(list);
});

// ── Notatki po rozmowie ──────────────────────────────────────────────────────

router.post('/voice-reports', requireAuth, requirePermission('telephony.use'), (req, res) => {
  const { callLogId = null, clientId = null, text = null, durationSec = null } = req.body || {};
  const row = {
    id: uuid(),
    organizationId: req.user.organizationId,
    userId: req.user.id,
    callLogId,
    clientId,
    text,
    transcript: null,
    recordingKey: null,
    durationSec,
    createdAt: nowIso(),
    updatedAt: nowIso(),
  };
  db.voiceReports.push(row);
  res.status(201).json(row);
});

router.post(
  '/voice-reports/:id/recording',
  requireAuth,
  requirePermission('telephony.use'),
  upload.single('file'),
  (req, res) => {
    const report = db.voiceReports.find(
      (r) => r.id === req.params.id && r.organizationId === req.user.organizationId,
    );
    if (!report) return res.status(404).json({ message: 'Nie znaleziono raportu.' });
    if (!req.file) return res.status(400).json({ message: 'Brak pliku (pole `file`).' });

    const ext = (path.extname(req.file.originalname || '') || '.m4a').replace(/[^.\w]/g, '');
    const key = `voice-reports/${req.user.organizationId}/${report.id}${ext}`;
    fs.renameSync(req.file.path, path.join(UPLOADS_DIR, path.basename(key)));

    report.recordingKey = key;
    // board360 transkrybuje nagranie Whisperem po stronie serwera. Mock nie ma
    // modelu, wiec wstawia rozpoznawalna atrape (MOCK_TRANSCRIPT=0 wylacza).
    if (MOCK_TRANSCRIPT && !report.transcript) {
      report.transcript = `[mock] Transkrypcja nagrania ${path.basename(key)} — ${report.durationSec || '?'} s.`;
    }
    report.updatedAt = nowIso();
    return res.json(report);
  },
);

router.get('/voice-reports', requireAuth, requirePermission('telephony.use'), (req, res) => {
  const since = sinceMs(req.query.since);
  const limit = limitOf(req.query.limit);
  let list = db.voiceReports.filter((r) => r.organizationId === req.user.organizationId);
  if (req.user.clientVisibility === 'own') list = list.filter((r) => r.userId === req.user.id);
  if (since) list = list.filter((r) => new Date(r.createdAt).getTime() >= since);
  list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  if (limit) list = list.slice(0, limit);
  res.json(list);
});

// ── Urzadzenia ───────────────────────────────────────────────────────────────

router.post('/devices', requireAuth, (req, res) => {
  const {
    deviceId,
    model = null,
    osVersion = null,
    sim1Label = null,
    sim2Label = null,
    pushToken = null,
  } = req.body || {};
  if (!deviceId) return res.status(400).json({ message: 'deviceId jest wymagany.' });

  let row = db.devices.find(
    (d) => d.organizationId === req.user.organizationId && d.userId === req.user.id && d.deviceId === deviceId,
  );
  if (!row) {
    row = {
      id: uuid(),
      organizationId: req.user.organizationId,
      userId: req.user.id,
      deviceId,
      createdAt: nowIso(),
    };
    db.devices.push(row);
  }
  Object.assign(row, { model, osVersion, sim1Label, sim2Label, pushToken, lastSeenAt: nowIso() });
  return res.json(row);
});

module.exports = router;
