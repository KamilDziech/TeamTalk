'use strict';
/*
 * Modul EMAIL — poczta e-mail (hub Komunikacja panelu, modul Email w TeamTalku).
 *
 * Ksztalt odpowiedzi i regul = `api/src/modules/email` z board360. Sedno, ktore
 * ta atrapa ma odtwarzac wiernie, bo na tym stoi caly modul na telefonie:
 *
 *  DWIE SKRZYNKI NA OSOBE
 *   - `shared`   — firmowa kontakt@ekotak.pl. Widzi ja kazdy, ale DOMYSLNIE
 *                  zawezona do WYCINKA OPIEKUNA. Cala skrzynke otwiera dopiero
 *                  `email.view_all` (parametr `scope=all`; bez uprawnienia 403).
 *   - `personal` — skrzynka pracownika pod adresem z jego konta. Zakladana
 *                  LENIWIE, przy pierwszym `GET /api/email/accounts`. Widzi ja
 *                  wylacznie wlasciciel — takze admin dostaje 404 na cudzy watek.
 *
 *  WYCINEK OPIEKUNA (kolejnosc ma znaczenie — dowiazanie bije adres):
 *   0. watek, w ktorym SAM cokolwiek napisalem              -> moj, ZAWSZE;
 *      inaczej mail z kontakt@ do hurtowni czy urzedu nie nalezalby do nikogo
 *      i znikalby autorowi z "Wyslanych",
 *   1. watek dowiazany do deala, w ktorym jestem opiekunem  -> moj,
 *   2. watek dowiazany do CUDZEGO deala                     -> NIE moj,
 *      nawet jesli pisze z niego adres, ktory mam w kartotece,
 *   3. watek niedowiazany                                   -> moj, gdy nadawca
 *      albo odbiorca jest adresem e-mail klienta z mojego deala.
 *
 * Wysylka jest zaslepiona jak w board360 bez kredencji SMTP: wiadomosc zapisuje
 * sie ze statusem `pending_config` i laduje w „Wyslane".
 */

const express = require('express');
const fs = require('fs');
const path = require('path');
const multer = require('multer');

const { UPLOADS_DIR, MAX_UPLOAD_BYTES } = require('../config');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission, can, unprocessable } = require('../middleware');
const { db, clientById, dealById } = require('../store');

const router = express.Router();
const upload = multer({ dest: UPLOADS_DIR, limits: { fileSize: MAX_UPLOAD_BYTES } });

const FOLDERS = ['inbox', 'sent', 'drafts', 'archive', 'spam', 'trash'];

const lower = (s) => String(s || '').trim().toLowerCase();
const accountsOf = (orgId) => db.emailAccounts.filter((a) => a.organizationId === orgId);
const threadsOf = (orgId) => db.emailThreads.filter((t) => t.organizationId === orgId);
const messagesOf = (threadId) =>
  db.emailMessages
    .filter((m) => m.threadId === threadId)
    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));

/** Skrzynka personalna osoby; zakladana przy pierwszym wejsciu w modul. */
function ensurePersonal(user) {
  const address = lower(user.email);
  const existing = accountsOf(user.organizationId).find(
    (a) => a.kind === 'personal' && a.userId === user.id,
  );
  if (existing) return existing;
  if (!address) return null;
  // Adres zajety przez skrzynke firmowa — nie oddajemy poczty firmy jednej osobie.
  if (accountsOf(user.organizationId).some((a) => a.address === address)) return null;
  const account = {
    id: uuid(),
    organizationId: user.organizationId,
    address,
    displayName: [user.firstName, user.lastName].filter(Boolean).join(' ').trim() || null,
    kind: 'personal',
    userId: user.id,
    createdAt: nowIso(),
  };
  db.emailAccounts.push(account);
  return account;
}

/** Skrzynki widoczne dla osoby: wszystkie firmowe + JEJ personalna. */
function mailboxesFor(user) {
  const shared = accountsOf(user.organizationId)
    .filter((a) => a.kind === 'shared')
    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
  const personal = ensurePersonal(user);
  return personal ? [...shared, personal] : shared;
}

/**
 * Wycinek opiekuna: deale osoby (opiekun karty, etapu, audytu lub spotkania),
 * ich klienci i adresy e-mail tych klientow.
 */
function ownerScope(user) {
  // `userId` wchodzi do wycinka: patrz punkt 0 w naglowku pliku.
  const deals = db.deals.filter(
    (d) =>
      d.organizationId === user.organizationId &&
      !d.deletedAt &&
      [d.ownerId, d.stageOwnerId, d.auditOwnerId, d.meetingOwnerId].includes(user.id),
  );
  const clientIds = new Set();
  const addresses = new Set();
  for (const d of deals) {
    clientIds.add(d.clientId);
    const client = clientById(user.organizationId, d.clientId);
    for (const addr of [client && client.email, client && client.email2]) {
      if (lower(addr)) addresses.add(lower(addr));
    }
  }
  return {
    userId: user.id,
    dealIds: deals.map((d) => d.id),
    clientIds: [...clientIds],
    addresses: [...addresses],
  };
}

/** Adresy wszystkich wiadomosci watku (nadawcy + odbiorcy), malymi literami. */
function threadAddresses(threadId) {
  const out = new Set();
  for (const m of messagesOf(threadId)) {
    out.add(lower(m.fromAddr));
    for (const t of m.toAddrs || []) out.add(lower(t));
  }
  return [...out];
}

/** Czy watek nalezy do wycinka opiekuna. Recznie dowiazany deal ma pierwszenstwo. */
function matchesOwner(thread, scope) {
  // Co sam napisalem, to widze — bez wzgledu na dowiazania.
  if (messagesOf(thread.id).some((m) => m.sentById === scope.userId)) return true;
  if (thread.dealId) return scope.dealIds.includes(thread.dealId);
  if (thread.clientId) return scope.clientIds.includes(thread.clientId);
  return threadAddresses(thread.id).some((a) => scope.addresses.includes(a));
}

/** Czy osoba moze w ogole otworzyc ten watek (skrzynka + wycinek/uprawnienie). */
function visible(user, thread) {
  const mailbox = mailboxesFor(user).find((m) => m.id === thread.accountId);
  if (!mailbox) return false;
  if (mailbox.kind === 'personal') return true;
  if (can(user, 'email.view_all')) return true;
  return matchesOwner(thread, ownerScope(user));
}

/**
 * Zawezenie listy do (skrzynka, widok). Zwraca `{ error }`, gdy osoba prosi o
 * `scope=all` bez uprawnienia albo o skrzynke, ktorej nie widzi — tak samo jak
 * board360 (403), zeby telefon nie musial zgadywac.
 */
function resolveFilter(user, accountId, scopeParam) {
  const mailboxes = mailboxesFor(user);
  const mailbox = accountId ? mailboxes.find((m) => m.id === accountId) : mailboxes[0];
  if (!mailbox) return { error: { code: 403, message: 'Nie masz dostepu do tej skrzynki.' } };
  const wantsAll = scopeParam === 'all';
  if (mailbox.kind === 'personal') return { mailbox, owner: null };
  if (wantsAll) {
    if (!can(user, 'email.view_all')) {
      return { error: { code: 403, message: 'Nie masz prawa do podgladu calej skrzynki firmowej.' } };
    }
    return { mailbox, owner: null };
  }
  return { mailbox, owner: ownerScope(user) };
}

const inFilter = (thread, filter) =>
  thread.accountId === filter.mailbox.id && (!filter.owner || matchesOwner(thread, filter.owner));

const labelsOfThread = (threadId) =>
  db.emailThreadLabels
    .filter((l) => l.threadId === threadId)
    .map((l) => db.emailLabels.find((x) => x.id === l.labelId))
    .filter(Boolean)
    .map((l) => ({ id: l.id, name: l.name, color: l.color }));

const snippet = (bodyText, subject) => {
  const src = String(bodyText || '').replace(/\s+/g, ' ').trim();
  if (!src) return subject;
  return src.length > 140 ? `${src.slice(0, 140)}…` : src;
};

/** Naglowek watku na liscie — dokladnie to, co rysuje wiersz w Gmailu. */
function presentThread(thread) {
  const messages = messagesOf(thread.id);
  const last = messages[messages.length - 1] || null;
  const outgoing = thread.folder === 'sent' || thread.folder === 'drafts';
  const hasAttachment = messages.some((m) =>
    db.emailAttachments.some((a) => a.messageId === m.id),
  );
  return {
    id: thread.id,
    subject: thread.subject,
    folder: thread.folder,
    lastAt: thread.lastAt,
    unread: thread.unread,
    starred: thread.starred,
    dealId: thread.dealId,
    clientId: thread.clientId,
    fromName: outgoing ? null : (last && last.fromName) || null,
    fromAddr: outgoing
      ? (last && last.toAddrs && last.toAddrs[0]) || '—'
      : (last && last.fromAddr) || '—',
    snippet: snippet(last && last.bodyText, thread.subject),
    messageCount: messages.length,
    hasAttachment,
    labels: labelsOfThread(thread.id),
  };
}

const presentMessage = (m) => ({
  id: m.id,
  threadId: m.threadId,
  direction: m.direction,
  fromAddr: m.fromAddr,
  fromName: m.fromName,
  toAddrs: m.toAddrs,
  ccAddrs: m.ccAddrs,
  bccAddrs: m.bccAddrs,
  subject: m.subject,
  bodyText: m.bodyText,
  bodyHtml: m.bodyHtml,
  status: m.status,
  createdAt: m.createdAt,
  attachments: db.emailAttachments
    .filter((a) => a.messageId === m.id)
    .map((a) => ({
      id: a.id,
      filename: a.filename,
      mimeType: a.mimeType,
      sizeBytes: a.sizeBytes,
      storageKey: a.storageKey,
    })),
});

/** Etykieta dowiazanego deala — „Nazwisko · etap", jak chip w panelu. */
function dealLabel(orgId, dealId) {
  const deal = dealById(orgId, dealId);
  if (!deal) return null;
  const client = clientById(orgId, deal.clientId);
  const name = client ? `${client.firstName} ${client.lastName}`.trim() : '';
  return `${name} · ${deal.stage}`;
}

// ── Skrzynki ────────────────────────────────────────────────────────────────

router.get('/email/accounts', requireAuth, requirePermission('crm.view'), (req, res) => {
  const user = req.user;
  const scope = ownerScope(user);
  const list = mailboxesFor(user).map((m) => {
    const owner = m.kind === 'personal' ? null : scope;
    const unread = threadsOf(user.organizationId).filter(
      (t) =>
        t.accountId === m.id &&
        t.folder === 'inbox' &&
        t.unread &&
        (!owner || matchesOwner(t, owner)),
    ).length;
    return {
      id: m.id,
      address: m.address,
      displayName: m.displayName,
      kind: m.kind,
      // Przelacznik „Moje / Wszystkie" ma sens wylacznie w skrzynce firmowej.
      canViewAll: m.kind === 'shared' && can(user, 'email.view_all'),
      unread,
    };
  });
  return res.json(list);
});

router.get('/email/folders', requireAuth, requirePermission('crm.view'), (req, res) => {
  const filter = resolveFilter(req.user, req.query.accountId, req.query.scope);
  if (filter.error) return res.status(filter.error.code).json({ message: filter.error.message });
  const mine = threadsOf(req.user.organizationId).filter((t) => inFilter(t, filter));
  return res.json(
    FOLDERS.map((folder) => ({
      folder,
      total: mine.filter((t) => t.folder === folder).length,
      unread: mine.filter((t) => t.folder === folder && t.unread).length,
    })),
  );
});

router.get('/email/labels', requireAuth, requirePermission('crm.view'), (req, res) =>
  res.json(
    db.emailLabels
      .filter((l) => l.organizationId === req.user.organizationId)
      .sort((a, b) => a.name.localeCompare(b.name, 'pl'))
      .map((l) => ({ id: l.id, name: l.name, color: l.color })),
  ),
);

router.get('/email/deal-options', requireAuth, requirePermission('crm.view'), (req, res) => {
  const q = lower(req.query.q);
  const rows = db.deals
    .filter((d) => d.organizationId === req.user.organizationId && !d.deletedAt)
    .map((d) => ({ deal: d, client: clientById(req.user.organizationId, d.clientId) }))
    .filter(({ client }) => {
      if (!q) return true;
      if (!client) return false;
      return lower(`${client.firstName} ${client.lastName}`).includes(q);
    })
    .slice(0, 20);
  return res.json(
    rows.map(({ deal, client }) => ({
      dealId: deal.id,
      label: `${client ? `${client.firstName} ${client.lastName}`.trim() : '—'} · ${deal.stage}`,
    })),
  );
});

// ── Watki ───────────────────────────────────────────────────────────────────

router.get('/email/threads', requireAuth, requirePermission('crm.view'), (req, res) => {
  const orgId = req.user.organizationId;

  // Karta deala → korespondencja tego deala, ze wszystkich folderow i skrzynek:
  // kto ma dostep do karty, ten widzi jej watki (tak samo jak board360).
  if (req.query.dealId && String(req.query.dealId).trim()) {
    const dealId = String(req.query.dealId).trim();
    const rows = threadsOf(orgId)
      .filter((t) => t.dealId === dealId && t.folder !== 'trash')
      .sort((a, b) => new Date(b.lastAt) - new Date(a.lastAt));
    return res.json(rows.map(presentThread));
  }

  const filter = resolveFilter(req.user, req.query.accountId, req.query.scope);
  if (filter.error) return res.status(filter.error.code).json({ message: filter.error.message });
  const folder = FOLDERS.includes(req.query.folder) ? req.query.folder : 'inbox';
  const q = lower(req.query.q);

  const rows = threadsOf(orgId)
    .filter((t) => t.folder === folder && inFilter(t, filter))
    .filter((t) => {
      if (!q) return true;
      if (lower(t.subject).includes(q)) return true;
      return messagesOf(t.id).some(
        (m) =>
          lower(m.bodyText).includes(q) ||
          lower(m.fromAddr).includes(q) ||
          lower(m.fromName).includes(q),
      );
    })
    .sort((a, b) => new Date(b.lastAt) - new Date(a.lastAt));
  return res.json(rows.map(presentThread));
});

router.get('/email/threads/:id', requireAuth, requirePermission('crm.view'), (req, res) => {
  const thread = threadsOf(req.user.organizationId).find((t) => t.id === req.params.id);
  // Watek spoza wycinka celowo daje 404, a nie 403 — inaczej sama odpowiedz
  // potwierdzalaby, ze w cudzej skrzynce lezy watek o tym id.
  if (!thread || !visible(req.user, thread)) {
    return res.status(404).json({ message: 'Watek e-mail nie istnieje.' });
  }
  if (thread.unread) thread.unread = false;
  return res.json({
    thread: {
      id: thread.id,
      accountId: thread.accountId,
      subject: thread.subject,
      folder: thread.folder,
      lastAt: thread.lastAt,
      unread: thread.unread,
      starred: thread.starred,
      dealId: thread.dealId,
      clientId: thread.clientId,
    },
    messages: messagesOf(thread.id).map(presentMessage),
    labels: labelsOfThread(thread.id),
    dealLabel: thread.dealId ? dealLabel(req.user.organizationId, thread.dealId) : null,
  });
});

router.patch('/email/threads/:id', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const thread = threadsOf(req.user.organizationId).find((t) => t.id === req.params.id);
  if (!thread || !visible(req.user, thread)) {
    return res.status(404).json({ message: 'Watek e-mail nie istnieje.' });
  }
  const body = req.body || {};
  if (Object.keys(body).length === 0) return unprocessable(res, 'Brak zmian.');
  if (body.folder !== undefined && !FOLDERS.includes(body.folder)) {
    return unprocessable(res, 'Nieznany folder.', ['folder']);
  }
  if (body.starred !== undefined) thread.starred = !!body.starred;
  if (body.unread !== undefined) thread.unread = !!body.unread;
  if (body.folder !== undefined) thread.folder = body.folder;
  if (body.dealId !== undefined) thread.dealId = body.dealId || null;
  if (body.clientId !== undefined) thread.clientId = body.clientId || null;
  if (body.labelIds !== undefined) {
    db.emailThreadLabels = db.emailThreadLabels.filter((l) => l.threadId !== thread.id);
    for (const labelId of body.labelIds || []) {
      db.emailThreadLabels.push({ threadId: thread.id, labelId });
    }
  }
  return res.json({ ok: true });
});

router.delete('/email/threads/:id', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const thread = threadsOf(req.user.organizationId).find((t) => t.id === req.params.id);
  if (!thread || !visible(req.user, thread)) {
    return res.status(404).json({ message: 'Watek e-mail nie istnieje.' });
  }
  // Z kosza usuwa sie na trwale; z kazdego innego folderu — do kosza.
  if (thread.folder !== 'trash') {
    thread.folder = 'trash';
    return res.json({ ok: true });
  }
  db.emailThreads = db.emailThreads.filter((t) => t.id !== thread.id);
  const ids = db.emailMessages.filter((m) => m.threadId === thread.id).map((m) => m.id);
  db.emailMessages = db.emailMessages.filter((m) => m.threadId !== thread.id);
  db.emailAttachments = db.emailAttachments.filter((a) => !ids.includes(a.messageId));
  db.emailThreadLabels = db.emailThreadLabels.filter((l) => l.threadId !== thread.id);
  return res.json({ ok: true });
});

// ── Wysylka i wersje robocze ────────────────────────────────────────────────

/** Skrzynka nadawcza — wylacznie taka, do ktorej osoba ma dostep. */
function senderFor(user, body) {
  const mailboxes = mailboxesFor(user);
  if (body.accountId) return mailboxes.find((m) => m.id === body.accountId) || null;
  if (body.fromAccount) return mailboxes.find((m) => m.address === lower(body.fromAccount)) || null;
  return mailboxes[0] || null;
}

const cleanList = (v) =>
  (Array.isArray(v) ? v : []).map((s) => String(s).trim()).filter(Boolean);

function createMessage(orgId, threadId, account, body, status, authorId) {
  const message = {
    id: uuid(),
    organizationId: orgId,
    threadId,
    direction: 'outbound',
    // Autor wysylki — czesc wycinka opiekuna (punkt 0 w naglowku pliku).
    sentById: authorId || null,
    fromAddr: account.address,
    fromName: account.displayName,
    toAddrs: cleanList(body.to),
    ccAddrs: cleanList(body.cc),
    bccAddrs: cleanList(body.bcc),
    subject: String(body.subject || '(bez tematu)'),
    bodyText: body.bodyText == null ? null : String(body.bodyText),
    bodyHtml: body.bodyHtml == null ? null : String(body.bodyHtml),
    status,
    createdAt: nowIso(),
  };
  db.emailMessages.push(message);
  return message;
}

router.post('/email/messages', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const body = req.body || {};
  const to = cleanList(body.to);
  if (to.length === 0) return unprocessable(res, 'Podaj co najmniej jednego odbiorce.', ['to']);

  const account = senderFor(req.user, body);
  if (!account) return res.status(403).json({ message: 'Nie masz dostepu do tej skrzynki.' });

  // Bez kredencji SMTP atrapa robi to samo co board360: zapisuje jako oczekujace.
  const status = 'pending_config';

  if (body.threadId) {
    const thread = threadsOf(req.user.organizationId).find((t) => t.id === body.threadId);
    if (!thread || !visible(req.user, thread)) {
      return res.status(404).json({ message: 'Watek e-mail nie istnieje.' });
    }
    const message = createMessage(
      req.user.organizationId, thread.id, account, body, status, req.user.id,
    );
    thread.lastAt = message.createdAt;
    return res.status(201).json(presentMessage(message));
  }

  const thread = {
    id: uuid(),
    organizationId: req.user.organizationId,
    accountId: account.id,
    subject: String(body.subject || '(bez tematu)'),
    folder: 'sent',
    lastAt: nowIso(),
    unread: false,
    starred: false,
    dealId: body.dealId || null,
    clientId: null,
  };
  db.emailThreads.push(thread);
  const message = createMessage(
    req.user.organizationId, thread.id, account, body, status, req.user.id,
  );
  thread.lastAt = message.createdAt;
  return res.status(201).json(presentMessage(message));
});

router.post('/email/drafts', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const body = req.body || {};
  const account = senderFor(req.user, body);
  if (!account) return res.status(403).json({ message: 'Nie masz dostepu do tej skrzynki.' });
  const thread = {
    id: uuid(),
    organizationId: req.user.organizationId,
    accountId: account.id,
    subject: String(body.subject || '').trim() || '(bez tematu)',
    folder: 'drafts',
    lastAt: nowIso(),
    unread: false,
    starred: false,
    dealId: null,
    clientId: null,
  };
  db.emailThreads.push(thread);
  const message = createMessage(
    req.user.organizationId, thread.id, account, body, 'draft', req.user.id,
  );
  return res.status(201).json(presentMessage(message));
});

// ── Zalaczniki ──────────────────────────────────────────────────────────────

router.post(
  '/email/messages/:id/attachments',
  requireAuth,
  requirePermission('deal.manage'),
  upload.single('file'),
  (req, res) => {
    const message = db.emailMessages.find(
      (m) => m.id === req.params.id && m.organizationId === req.user.organizationId,
    );
    if (!message) return res.status(404).json({ message: 'Wiadomosc nie istnieje.' });
    const thread = threadsOf(req.user.organizationId).find((t) => t.id === message.threadId);
    if (!thread || !visible(req.user, thread)) {
      return res.status(404).json({ message: 'Wiadomosc nie istnieje.' });
    }
    if (!req.file) return unprocessable(res, 'Brak pliku (pole „file").');

    const id = uuid();
    const ext = (path.extname(req.file.originalname || '') || '').replace(/[^.\w]/g, '');
    const key = `email-attachments/${req.user.organizationId}/${id}${ext}`;
    fs.renameSync(req.file.path, path.join(UPLOADS_DIR, path.basename(key)));

    const row = {
      id,
      organizationId: req.user.organizationId,
      messageId: message.id,
      filename: req.file.originalname || 'plik',
      mimeType: req.file.mimetype || 'application/octet-stream',
      sizeBytes: req.file.size,
      storageKey: key,
    };
    db.emailAttachments.push(row);
    return res.status(201).json({
      id: row.id,
      filename: row.filename,
      mimeType: row.mimeType,
      sizeBytes: row.sizeBytes,
      storageKey: row.storageKey,
    });
  },
);

router.get('/email/attachments/:id', requireAuth, requirePermission('crm.view'), (req, res) => {
  const row = db.emailAttachments.find(
    (a) => a.id === req.params.id && a.organizationId === req.user.organizationId,
  );
  if (!row) return res.status(404).json({ message: 'Zalacznik nie istnieje.' });
  // Zalacznik dziedziczy widocznosc po watku — samo id pliku nie otwiera poczty
  // z cudzej skrzynki.
  const message = db.emailMessages.find((m) => m.id === row.messageId);
  const thread = message ? threadsOf(req.user.organizationId).find((t) => t.id === message.threadId) : null;
  if (!thread || !visible(req.user, thread)) {
    return res.status(404).json({ message: 'Zalacznik nie istnieje.' });
  }
  const file = path.join(UPLOADS_DIR, path.basename(row.storageKey));
  if (!fs.existsSync(file)) return res.status(404).json({ message: 'Brak tresci pliku.' });
  res.setHeader('Content-Type', row.mimeType);
  res.setHeader(
    'Content-Disposition',
    `attachment; filename*=UTF-8''${encodeURIComponent(row.filename)}`,
  );
  return fs.createReadStream(file).pipe(res);
});

module.exports = router;
