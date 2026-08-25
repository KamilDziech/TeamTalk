'use strict';
/*
 * board360-mock — testowy backend zgodny z kontraktem board360 dla aplikacji
 * mobilnej TeamTalk. Dane w pamieci (seed przy starcie), pliki nagran na dysku.
 *
 * Cel: testy end-to-end aplikacji Android bez czekania na wdrozenie board360
 * oraz zywa referencja kontraktu (A1-A5) dla autora board360.
 *
 * Auth = ten sam format co board360: podpisany token HMAC-SHA256 w cookie
 * `b360_session` = base64url(JSON{sub,userId,organizationId,role,exp}).<hmac>.
 * Dodatkowo akceptujemy `Authorization: Bearer <token>` (opcja A1b).
 */

const express = require('express');
const multer = require('multer');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const PORT = Number(process.env.PORT || 3001);
const SESSION_SECRET = process.env.SESSION_SECRET || 'dev-insecure-secret-change-me';
const MOBILE_SESSION_TTL = Number(process.env.MOBILE_SESSION_TTL || 60 * 60 * 24 * 30); // 30 dni
const UPLOADS_DIR = process.env.UPLOADS_DIR || path.join(__dirname, 'uploads');
const SESSION_COOKIE = 'b360_session';

fs.mkdirSync(UPLOADS_DIR, { recursive: true });

// ── Token sesji (zgodny z web/src/lib/auth.ts board360) ──────────────────────
const b64url = (buf) => Buffer.from(buf).toString('base64url');
const sign = (payload) => crypto.createHmac('sha256', SESSION_SECRET).update(payload).digest('base64url');

function issueToken({ sub, userId, organizationId, role, exp }) {
  const payload = b64url(JSON.stringify({ sub, userId, organizationId, role, exp }));
  return `${payload}.${sign(payload)}`;
}

function verifyToken(token) {
  if (!token) return null;
  const [payload, sig] = String(token).split('.');
  if (!payload || !sig) return null;
  const expected = sign(payload);
  const a = Buffer.from(sig);
  const b = Buffer.from(expected);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null;
  let data;
  try { data = JSON.parse(Buffer.from(payload, 'base64url').toString()); } catch { return null; }
  if (typeof data.exp !== 'number' || data.exp < Math.floor(Date.now() / 1000)) return null;
  return data;
}

// ── Hasla (scrypt, bez natywnych zaleznosci) ─────────────────────────────────
function hashPassword(pw) {
  const salt = crypto.randomBytes(16);
  const dk = crypto.scryptSync(pw, salt, 32);
  return `scrypt$${salt.toString('hex')}$${dk.toString('hex')}`;
}
function verifyPassword(stored, pw) {
  const parts = String(stored).split('$');
  if (parts.length !== 3) return false;
  const dk = crypto.scryptSync(pw, Buffer.from(parts[1], 'hex'), 32);
  const exp = Buffer.from(parts[2], 'hex');
  return dk.length === exp.length && crypto.timingSafeEqual(dk, exp);
}

// ── RBAC (uproszczone odwzorowanie board360) ─────────────────────────────────
const ALL_PERMS = ['crm.view', 'deal.manage', 'telephony.use', 'settings.team', 'reports.view', 'tasks.view', 'tasks.manage'];
const ROLE_PERMS = {
  admin: ALL_PERMS,
  zarzad: ALL_PERMS,
  koordynator: ['crm.view', 'deal.manage', 'telephony.use', 'reports.view', 'tasks.view', 'tasks.manage'],
  serwisant: ['crm.view', 'telephony.use', 'tasks.view', 'tasks.manage'],
  biuro: ['crm.view', 'deal.manage', 'tasks.view', 'tasks.manage'],
  montaz: ['crm.view', 'tasks.view', 'tasks.manage'],
  stazysta: [],
};
const ADMIN_ROLES = new Set(['admin', 'zarzad']);
const permsFor = (role) => ROLE_PERMS[role] || [];

// ── Dane w pamieci + seed ────────────────────────────────────────────────────
const uuid = () => crypto.randomUUID();
const nowIso = () => new Date().toISOString();

const db = {
  organization: { id: uuid(), name: 'EKOTAK' },
  users: [],
  clients: [],
  callLogs: [],
  voiceReports: [],
  devices: [],
  tasks: [],
};

function seed() {
  const orgId = db.organization.id;
  db.users.push({
    id: uuid(),
    organizationId: orgId,
    email: 'serwisant@ekotak.pl',
    passwordHash: hashPassword('test1234'),
    role: 'serwisant',
    firstName: 'Jan',
    lastName: 'Serwisant',
    clientVisibility: 'all',
  });
  db.users.push({
    id: uuid(),
    organizationId: orgId,
    email: 'admin@ekotak.pl',
    passwordHash: hashPassword('admin1234'),
    role: 'admin',
    firstName: 'Anna',
    lastName: 'Admin',
    clientVisibility: 'all',
  });

  const clientSeed = [
    ['Marek', 'Nowak', '+48501234567', 'marek.nowak@example.com', 'Katowice', 'ul. Kwiatowa 1'],
    ['Ewa', 'Kowalska', '+48502345678', 'ewa.kowalska@example.com', 'Gliwice', 'ul. Lesna 5'],
    ['Piotr', 'Wisniewski', '+48503456789', null, 'Bielsko-Biala', 'ul. Gorna 12'],
    ['Katarzyna', 'Wojcik', '+48504567890', 'k.wojcik@example.com', 'Tychy', 'ul. Polna 8'],
    ['Tomasz', 'Kaminski', '+48505678901', null, 'Sosnowiec', 'ul. Dluga 3'],
  ];
  for (const [fn, ln, phone, email, city, street] of clientSeed) {
    db.clients.push({
      id: uuid(),
      organizationId: orgId,
      firstName: fn,
      lastName: ln,
      email,
      email2: null,
      phone,
      phone2: null,
      address: `${street}, ${city}`,
      postalCode: null,
      city,
      street,
      geoLat: null,
      geoLng: null,
      type: 'wlasny',
      category: 'klient',
      createdAt: nowIso(),
      updatedAt: nowIso(),
    });
  }
}
seed();

// ── Helpery ──────────────────────────────────────────────────────────────────
function parseCookies(header) {
  const out = {};
  if (!header) return out;
  for (const part of header.split(';')) {
    const idx = part.indexOf('=');
    if (idx < 0) continue;
    const k = part.slice(0, idx).trim();
    if (!k) continue;
    out[k] = decodeURIComponent(part.slice(idx + 1).trim());
  }
  return out;
}

function normalizePhone(p) {
  return String(p || '').replace(/[^0-9]/g, '').replace(/^0+/, '');
}

function findClientByPhone(orgId, phone) {
  const n = normalizePhone(phone);
  if (!n) return null;
  return db.clients.find(
    (c) => c.organizationId === orgId && (normalizePhone(c.phone).endsWith(n) || normalizePhone(c.phone2).endsWith(n) || n.endsWith(normalizePhone(c.phone))),
  ) || null;
}

function authContext(user) {
  return {
    userId: user.id,
    organizationId: user.organizationId,
    email: user.email,
    role: user.role,
    permissions: permsFor(user.role),
    clientVisibility: user.clientVisibility || 'all',
  };
}

// ── App ──────────────────────────────────────────────────────────────────────
const app = express();
app.use(express.json({ limit: '2mb' }));

// Log prostych zadan (pomaga przy testach)
app.use((req, _res, next) => {
  console.log(`${new Date().toISOString()} ${req.method} ${req.url}`);
  next();
});

// Middleware auth: cookie b360_session lub Authorization: Bearer
function requireAuth(req, res, next) {
  const cookieTok = parseCookies(req.headers.cookie)[SESSION_COOKIE];
  const authHeader = req.headers.authorization || '';
  const bearer = authHeader.startsWith('Bearer ') ? authHeader.slice(7) : null;
  const payload = verifyToken(cookieTok || bearer);
  if (!payload) return res.status(401).json({ message: 'Wymagane logowanie.' });
  const user = db.users.find((u) => u.id === payload.userId && u.organizationId === payload.organizationId);
  if (!user) return res.status(401).json({ message: 'Wymagane logowanie.' });
  req.user = user;
  next();
}

function requirePermission(perm) {
  return (req, res, next) => {
    if (!permsFor(req.user.role).includes(perm)) {
      return res.status(403).json({ message: `Brak uprawnienia: ${perm}` });
    }
    next();
  };
}

const upload = multer({
  dest: UPLOADS_DIR,
  limits: { fileSize: 25 * 1024 * 1024 },
});

// ── Health ────────────────────────────────────────────────────────────────────
app.get('/api/health', (_req, res) => res.json({ ok: true, service: 'board360-mock', time: nowIso() }));

// ── Auth ────────────────────────────────────────────────────────────────────
app.post('/api/auth/mobile-login', (req, res) => {
  const { email, password } = req.body || {};
  if (!email || !password) return res.status(400).json({ message: 'email i password sa wymagane.' });
  const user = db.users.find((u) => u.email.toLowerCase() === String(email).trim().toLowerCase());
  if (!user || !verifyPassword(user.passwordHash, password)) {
    return res.status(401).json({ message: 'Nieprawidlowy e-mail lub haslo.' });
  }
  const exp = Math.floor(Date.now() / 1000) + MOBILE_SESSION_TTL;
  const token = issueToken({ sub: user.email, userId: user.id, organizationId: user.organizationId, role: user.role, exp });
  res.json({ token, expiresAt: exp, user: authContext(user) });
});

app.get('/api/me', requireAuth, (req, res) => res.json(authContext(req.user)));

// ── Clients ───────────────────────────────────────────────────────────────────
app.get('/api/clients', requireAuth, requirePermission('crm.view'), (req, res) => {
  const q = (req.query.q || '').toString().trim().toLowerCase();
  let list = db.clients.filter((c) => c.organizationId === req.user.organizationId);
  if (q) {
    list = list.filter((c) =>
      `${c.firstName} ${c.lastName}`.toLowerCase().includes(q) ||
      normalizePhone(c.phone).includes(normalizePhone(q)) ||
      normalizePhone(c.phone2).includes(normalizePhone(q)),
    );
  }
  res.json(list);
});

app.get('/api/clients/:id', requireAuth, requirePermission('crm.view'), (req, res) => {
  const c = db.clients.find((x) => x.id === req.params.id && x.organizationId === req.user.organizationId);
  if (!c) return res.status(404).json({ message: 'Nie znaleziono klienta.' });
  res.json(c);
});

// ── Call logs ─────────────────────────────────────────────────────────────────
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
    simSlot: input.simSlot ?? null,
    startedAt,
    endedAt: input.endedAt || null,
    durationSec: input.durationSec ?? null,
    createdAt: nowIso(),
  };
  db.callLogs.push(row);
  return row;
}

app.post('/api/call-logs', requireAuth, requirePermission('telephony.use'), (req, res) => {
  const body = req.body;
  const items = Array.isArray(body) ? body : [body];
  const saved = items.map((it) => upsertCallLog(req.user, it || {}));
  res.status(201).json(saved);
});

app.get('/api/call-logs', requireAuth, requirePermission('telephony.use'), (req, res) => {
  const since = req.query.since ? new Date(req.query.since).getTime() : null;
  const limit = req.query.limit ? Number(req.query.limit) : null;
  let list = db.callLogs.filter((c) => c.organizationId === req.user.organizationId);
  if (req.user.clientVisibility === 'own') list = list.filter((c) => c.userId === req.user.id);
  if (since) list = list.filter((c) => new Date(c.startedAt).getTime() >= since);
  list.sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt));
  if (limit) list = list.slice(0, limit);
  res.json(list);
});

// ── Voice reports ──────────────────────────────────────────────────────────────
app.post('/api/voice-reports', requireAuth, requirePermission('telephony.use'), (req, res) => {
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

app.post('/api/voice-reports/:id/recording', requireAuth, requirePermission('telephony.use'), upload.single('file'), (req, res) => {
  const report = db.voiceReports.find((r) => r.id === req.params.id && r.organizationId === req.user.organizationId);
  if (!report) return res.status(404).json({ message: 'Nie znaleziono raportu.' });
  if (!req.file) return res.status(400).json({ message: 'Brak pliku (pole `file`).' });
  const ext = (path.extname(req.file.originalname || '') || '.m4a').replace(/[^.\w]/g, '');
  const key = `voice-reports/${req.user.organizationId}/${report.id}${ext}`;
  const dest = path.join(UPLOADS_DIR, path.basename(key));
  fs.renameSync(req.file.path, dest);
  report.recordingKey = key;
  report.updatedAt = nowIso();
  res.json(report);
});

app.get('/api/voice-reports', requireAuth, requirePermission('telephony.use'), (req, res) => {
  const since = req.query.since ? new Date(req.query.since).getTime() : null;
  const limit = req.query.limit ? Number(req.query.limit) : null;
  let list = db.voiceReports.filter((r) => r.organizationId === req.user.organizationId);
  if (req.user.clientVisibility === 'own') list = list.filter((r) => r.userId === req.user.id);
  if (since) list = list.filter((r) => new Date(r.createdAt).getTime() >= since);
  list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  if (limit) list = list.slice(0, limit);
  res.json(list);
});

// ── Devices ─────────────────────────────────────────────────────────────────
app.post('/api/devices', requireAuth, (req, res) => {
  const { deviceId, model = null, osVersion = null, sim1Label = null, sim2Label = null, pushToken = null } = req.body || {};
  if (!deviceId) return res.status(400).json({ message: 'deviceId jest wymagany.' });
  let row = db.devices.find((d) => d.organizationId === req.user.organizationId && d.userId === req.user.id && d.deviceId === deviceId);
  if (!row) {
    row = { id: uuid(), organizationId: req.user.organizationId, userId: req.user.id, deviceId, createdAt: nowIso() };
    db.devices.push(row);
  }
  Object.assign(row, { model, osVersion, sim1Label, sim2Label, pushToken, lastSeenAt: nowIso() });
  res.json(row);
});

// ── Tasks (zadania zespolu, kontrakt board360 FR-26) ────────────────────────
// Lista czlonkow zespolu (do wyboru osoby przypisanej).
app.get('/api/tasks/members', requireAuth, requirePermission('tasks.view'), (req, res) => {
  const list = db.users
    .filter((u) => u.organizationId === req.user.organizationId)
    .map((u) => ({
      id: u.id,
      email: u.email,
      firstName: u.firstName || null,
      lastName: u.lastName || null,
      role: u.role,
    }));
  res.json(list);
});

const TASK_PRIORITIES = new Set(['low', 'normal', 'high']);

app.post('/api/tasks', requireAuth, requirePermission('tasks.manage'), (req, res) => {
  const b = req.body || {};
  const title = typeof b.title === 'string' ? b.title.trim() : '';
  if (!title) return res.status(422).json({ message: 'Tytul jest wymagany.' });
  const priority = TASK_PRIORITIES.has(b.priority) ? b.priority : 'normal';
  let assigneeId = null;
  let assigneeEmail = null;
  if (b.assigneeId) {
    const member = db.users.find((u) => u.id === b.assigneeId && u.organizationId === req.user.organizationId);
    if (!member) return res.status(422).json({ message: 'Nieznany pracownik (assigneeId).' });
    assigneeId = member.id;
    assigneeEmail = member.email;
  }
  const row = {
    id: uuid(),
    organizationId: req.user.organizationId,
    dealId: null,
    title,
    description: b.description ? String(b.description) : null,
    assigneeId,
    assigneeEmail,
    dueAt: b.dueAt ? new Date(b.dueAt).toISOString() : null,
    status: 'open',
    priority,
    section: null,
    estimatedMinutes: null,
    createdBy: req.user.id,
    createdAt: nowIso(),
    updatedAt: nowIso(),
  };
  db.tasks.push(row);
  res.status(201).json(row);
});

app.get('/api/tasks', requireAuth, requirePermission('tasks.view'), (req, res) => {
  const statusF = req.query.status ? String(req.query.status) : null;
  const assigneeF = req.query.assignee === 'me' ? req.user.id : (req.query.assignee || null);
  let list = db.tasks.filter((t) => t.organizationId === req.user.organizationId);
  if (statusF) list = list.filter((t) => t.status === statusF);
  if (assigneeF) list = list.filter((t) => t.assigneeId === assigneeF);
  list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  res.json(list);
});

app.use((req, res) => res.status(404).json({ message: `Brak trasy: ${req.method} ${req.path}` }));

app.listen(PORT, () => {
  console.log(`board360-mock nasluchuje na :${PORT}`);
  console.log(`Konta testowe: serwisant@ekotak.pl / test1234  |  admin@ekotak.pl / admin1234`);
  console.log(`Klientow w seedzie: ${db.clients.length}`);
});
