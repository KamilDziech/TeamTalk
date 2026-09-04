'use strict';
/*
 * Modul Kalendarz: kalendarze wspoldzielone i wydarzenia.
 *
 * Ksztalt rekordow i regul = `api/src/modules/calendar` z board360:
 *  - kalendarz ma typ (personal / team / resource), kolor i wyliczony poziom
 *    dostepu `effectiveLevel` (freebusy < reader < writer < owner),
 *  - serie rozwija SERWER: `POST` z `recurrence` tworzy wystapienia ze wspolnym
 *    `recurrenceGroupId`, a `PATCH`/`DELETE` przyjmuja `scope`
 *    (this / following / all),
 *  - podwojna rezerwacja kalendarza typu `resource` konczy sie kodem 409,
 *    ktory wymusza sie parametrem `allowConflict=true`,
 *  - RSVP i archiwizacja oddaja 204 (bez ciala) — tak samo jak board360.
 *
 * Uprawnienie: `calendar.view` na odczyt i zapis; o tym, czy wolno pisac,
 * decyduje poziom dostepu do KALENDARZA, nie rola.
 */

const express = require('express');
const { uuid, nowIso } = require('../crypto');
const { can, requireAuth, requirePermission, unprocessable } = require('../middleware');
const { db } = require('../store');

const router = express.Router();

const LEVEL_RANK = { freebusy: 1, reader: 2, writer: 3, owner: 4 };
const RSVP = new Set(['needs_action', 'accepted', 'declined', 'tentative']);
const FREQ = new Set(['daily', 'weekly', 'monthly']);
const SCOPES = new Set(['this', 'following', 'all']);
/** Ile wystapien serii rozwijamy, gdy nie podano `count` ani `until`. */
const DEFAULT_COUNT = 12;
const MAX_COUNT = 400;

const calendarsOf = (req) =>
  db.calendars.filter((c) => c.organizationId === req.user.organizationId);

/**
 * Poziom dostepu uzytkownika do kalendarza: wlasciciel zawsze `owner`, poza tym
 * najwyzszy z grantow dla osoby, jej roli i „wszystkich".
 */
function effectiveLevel(calendar, user) {
  if (calendar.ownerId === user.id) return 'owner';
  let best = null;
  for (const share of calendar.shares || []) {
    const hit =
      (share.principalType === 'user' && share.principalId === user.id) ||
      (share.principalType === 'role' && share.principalId === user.role) ||
      share.principalType === 'everyone';
    if (!hit) continue;
    if (!best || LEVEL_RANK[share.level] > LEVEL_RANK[best]) best = share.level;
  }
  return best;
}

const visibleCalendars = (req) =>
  calendarsOf(req)
    .map((c) => ({ calendar: c, level: effectiveLevel(c, req.user) }))
    .filter((row) => row.level !== null);

function calendarView(calendar, level) {
  const owner = db.users.find((u) => u.id === calendar.ownerId);
  return {
    id: calendar.id,
    name: calendar.name,
    type: calendar.type,
    color: calendar.color,
    description: calendar.description || null,
    ownerId: calendar.ownerId,
    ownerEmail: owner ? owner.email : null,
    isArchived: Boolean(calendar.isArchived),
    effectiveLevel: level,
  };
}

function eventView(event) {
  const calendar = db.calendars.find((c) => c.id === event.calendarId);
  const assignee = event.assigneeId ? db.users.find((u) => u.id === event.assigneeId) : null;
  return {
    id: event.id,
    calendarId: event.calendarId,
    calendarColor: calendar ? calendar.color : null,
    title: event.title,
    description: event.description || null,
    location: event.location || null,
    color: event.color || null,
    startAt: event.startAt,
    endAt: event.endAt || null,
    allDay: Boolean(event.allDay),
    assigneeId: event.assigneeId || null,
    assigneeEmail: assignee ? assignee.email : null,
    attendees: (event.attendees || []).map((a) => {
      const user = db.users.find((u) => u.id === a.id);
      return { id: a.id, email: user ? user.email : null, response: a.response };
    }),
    recurrenceGroupId: event.recurrenceGroupId || null,
    recurrenceRule: event.recurrenceRule || null,
  };
}

/** Kalendarz, do ktorego uzytkownik ma co najmniej podany poziom. */
function calendarFor(req, calendarId, minLevel) {
  const calendar = calendarsOf(req).find((c) => c.id === calendarId);
  if (!calendar) return { error: 404 };
  const level = effectiveLevel(calendar, req.user);
  if (!level || LEVEL_RANK[level] < LEVEL_RANK[minLevel]) return { error: 403 };
  return { calendar, level };
}

const overlaps = (aFrom, aTo, bFrom, bTo) => aFrom < bTo && aTo > bFrom;

const endOf = (event) =>
  new Date(event.endAt || new Date(new Date(event.startAt).getTime() + 3600000)).getTime();

/**
 * Kolizja rezerwacji zasobu: dwa wydarzenia w tym samym kalendarzu typu
 * `resource` nie moga zachodzic na siebie. Kalendarze osobiste i zespolowe
 * kolizji nie pilnuja — dwa spotkania naraz to sprawa czlowieka, nie serwera.
 */
function resourceConflict(calendar, startAt, endAt, ignoreId) {
  if (calendar.type !== 'resource') return null;
  const from = new Date(startAt).getTime();
  const to = new Date(endAt || new Date(from + 3600000)).getTime();
  return (
    db.calendarEvents.find(
      (e) =>
        e.calendarId === calendar.id &&
        e.id !== ignoreId &&
        overlaps(from, to, new Date(e.startAt).getTime(), endOf(e)),
    ) || null
  );
}

/** Rozwiniecie serii — te same trzy czestotliwosci co w panelu. */
function expand(startAt, endAt, recurrence) {
  const count = recurrence.count
    ? Math.min(Number(recurrence.count), MAX_COUNT)
    : recurrence.until
      ? MAX_COUNT
      : DEFAULT_COUNT;
  const interval = Math.max(1, Number(recurrence.interval) || 1);
  const until = recurrence.until ? new Date(recurrence.until).getTime() : null;
  const start = new Date(startAt);
  const duration = endAt ? new Date(endAt).getTime() - start.getTime() : 3600000;

  const out = [];
  for (let i = 0; i < count; i += 1) {
    const occurrence = new Date(start.getTime());
    if (recurrence.freq === 'daily') occurrence.setDate(start.getDate() + i * interval);
    if (recurrence.freq === 'weekly') occurrence.setDate(start.getDate() + i * interval * 7);
    if (recurrence.freq === 'monthly') occurrence.setMonth(start.getMonth() + i * interval);
    if (until && occurrence.getTime() > until) break;
    out.push({
      startAt: occurrence.toISOString(),
      endAt: new Date(occurrence.getTime() + duration).toISOString(),
    });
  }
  return out;
}

// ── Kalendarze (warstwy) ─────────────────────────────────────────────────────

router.get('/calendars', requireAuth, requirePermission('calendar.view'), (req, res) => {
  res.json(visibleCalendars(req).map((row) => calendarView(row.calendar, row.level)));
});

router.post('/calendars', requireAuth, requirePermission('calendar.view'), (req, res) => {
  const body = req.body || {};
  const missing = [];
  if (!body.name || !String(body.name).trim()) missing.push('name');
  if (!['team', 'resource'].includes(body.type)) missing.push('type');
  if (!body.color) missing.push('color');
  if (missing.length) return unprocessable(res, 'Brakuje danych kalendarza.', missing);

  const calendar = {
    id: uuid(),
    organizationId: req.user.organizationId,
    name: String(body.name).trim(),
    type: body.type,
    color: body.color,
    description: body.description || null,
    ownerId: req.user.id,
    isArchived: false,
    // Nowy kalendarz zespolowy widza wszyscy — inaczej autor musialby zaraz po
    // zalozeniu isc do panelu nadac granty, zeby ktokolwiek go zobaczyl.
    shares: [{ id: uuid(), principalType: 'everyone', principalId: null, level: 'writer' }],
    createdAt: nowIso(),
  };
  db.calendars.push(calendar);
  return res.status(201).json(calendarView(calendar, 'owner'));
});

router.patch('/calendars/:id', requireAuth, requirePermission('calendar.view'), (req, res) => {
  const found = calendarFor(req, req.params.id, 'owner');
  if (found.error) return res.status(found.error).json({ message: 'Brak dostepu do kalendarza.' });
  const body = req.body || {};
  if (body.name !== undefined) found.calendar.name = String(body.name).trim();
  if (body.color !== undefined) found.calendar.color = body.color;
  if (body.description !== undefined) found.calendar.description = body.description;
  return res.json(calendarView(found.calendar, 'owner'));
});

for (const [path, archived] of [['archive', true], ['restore', false]]) {
  router.post(`/calendars/:id/${path}`, requireAuth, requirePermission('calendar.view'), (req, res) => {
    const found = calendarFor(req, req.params.id, 'owner');
    if (found.error) return res.status(found.error).json({ message: 'Brak dostepu do kalendarza.' });
    found.calendar.isArchived = archived;
    return res.status(204).end();
  });
}

router.get('/calendars/:id/shares', requireAuth, requirePermission('calendar.view'), (req, res) => {
  const found = calendarFor(req, req.params.id, 'owner');
  if (found.error) return res.status(found.error).json({ message: 'Brak dostepu do kalendarza.' });
  res.json(
    (found.calendar.shares || []).map((s) => ({
      id: s.id,
      principalType: s.principalType,
      principalId: s.principalId,
      principalEmail:
        s.principalType === 'user'
          ? (db.users.find((u) => u.id === s.principalId) || {}).email || null
          : null,
      level: s.level,
    })),
  );
});

router.put('/calendars/:id/shares', requireAuth, requirePermission('calendar.view'), (req, res) => {
  const found = calendarFor(req, req.params.id, 'owner');
  if (found.error) return res.status(found.error).json({ message: 'Brak dostepu do kalendarza.' });
  const shares = Array.isArray((req.body || {}).shares) ? req.body.shares : [];
  found.calendar.shares = shares.map((s) => ({
    id: uuid(),
    principalType: s.principalType,
    principalId: s.principalId || null,
    level: s.level,
  }));
  return res.json(found.calendar.shares);
});

// ── Wydarzenia ───────────────────────────────────────────────────────────────
// Trasy szczegolowe (`overlays`, `freebusy`) MUSZA stac przed `/:id`.

/**
 * Nakladki operacyjne: rekordy z innych modulow, tylko do podgladu. W atrapie
 * skladamy je z tego, co seed naprawde ma — zlecen serwisowych i zadan
 * z terminem.
 */
router.get('/calendar/events/overlays', requireAuth, requirePermission('calendar.view'), (req, res) => {
  const from = req.query.from ? new Date(req.query.from).getTime() : 0;
  const to = req.query.to ? new Date(req.query.to).getTime() : Date.now();
  const inRange = (iso) => {
    const at = new Date(iso).getTime();
    return !Number.isNaN(at) && at >= from && at < to;
  };

  const jobs = db.serviceJobs
    .filter((j) => j.organizationId === req.user.organizationId && j.scheduledAt && inRange(j.scheduledAt))
    .map((j) => ({
      source: 'service',
      id: j.id,
      title: j.note ? `Serwis: ${j.note}` : 'Zlecenie serwisowe',
      startAt: j.scheduledAt,
      allDay: false,
      link: `/app/service?job=${j.id}`,
      color: '#e0a500',
    }));

  const tasks = db.tasks
    .filter((t) => t.organizationId === req.user.organizationId && t.dueAt && inRange(t.dueAt))
    .map((t) => ({
      source: 'project',
      id: t.id,
      title: t.title,
      startAt: t.dueAt,
      allDay: true,
      link: `/app/tasks?task=${t.id}`,
      color: '#8a2cd6',
    }));

  res.json([...jobs, ...tasks]);
});

/** Zajetosc osob — same przedzialy, bez tresci wydarzen. */
router.get('/calendar/events/freebusy', requireAuth, requirePermission('calendar.view'), (req, res) => {
  const ids = String(req.query.userIds || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  const from = req.query.from ? new Date(req.query.from).getTime() : 0;
  const to = req.query.to ? new Date(req.query.to).getTime() : Date.now();

  res.json(
    ids.map((userId) => ({
      userId,
      busy: db.calendarEvents
        .filter((e) => {
          if (e.organizationId !== req.user.organizationId) return false;
          const involved =
            e.assigneeId === userId || (e.attendees || []).some((a) => a.id === userId);
          if (!involved) return false;
          return overlaps(from, to, new Date(e.startAt).getTime(), endOf(e));
        })
        .map((e) => ({ startAt: e.startAt, endAt: new Date(endOf(e)).toISOString() }))
        // Zajetosc z PRYWATNEGO kalendarza dokladamy do firmowej — inaczej
        // „Znajdz termin" proponowalby sloty, ktore i tak odbija sie o 409.
        .concat(
          db.privateBusy
            .filter(
              (b) =>
                b.organizationId === req.user.organizationId &&
                b.userId === userId &&
                overlaps(from, to, new Date(b.startAt).getTime(), new Date(b.endAt).getTime()),
            )
            .map((b) => ({ startAt: b.startAt, endAt: b.endAt })),
        )
        .sort((a, b) => new Date(a.startAt) - new Date(b.startAt)),
    })),
  );
});

// ── Prywatny kalendarz → zajetosc zespolu ────────────────────────────────────
// Board360 pobiera tu sekretny feed iCal i wyciaga z niego SAME GODZINY.
// Atrapa nie ma internetu ani konta Google, wiec przy podpieciu generuje bloki
// syntetyczne — ksztalt odpowiedzi i kody bledow zostaja te same, wiec klient
// mobilny nie widzi roznicy.

/** Bloki „zajete" na najblizsze trzy tygodnie: przerwa 12:00–13:00 i 17:30–19:00
 *  w dni robocze. Wystarcza, zeby zobaczyc szare pola i wywolac kolizje 409. */
function generateBusy(orgId, userId) {
  const rows = [];
  const day0 = new Date();
  day0.setHours(0, 0, 0, 0);
  for (let d = -3; d <= 21; d += 1) {
    const day = new Date(day0.getTime() + d * 86400000);
    const weekday = day.getDay();
    if (weekday === 0 || weekday === 6) continue;
    for (const [h1, m1, h2, m2] of [[12, 0, 13, 0], [17, 30, 19, 0]]) {
      const startAt = new Date(day);
      startAt.setHours(h1, m1, 0, 0);
      const endAt = new Date(day);
      endAt.setHours(h2, m2, 0, 0);
      rows.push({
        id: uuid(),
        organizationId: orgId,
        userId,
        startAt: startAt.toISOString(),
        endAt: endAt.toISOString(),
      });
    }
  }
  return rows;
}

/** Zajetosc osoby nachodzaca na przedzial — podstawa twardej kolizji 409. */
function privateBusyClash(orgId, userId, startAt, endAt) {
  if (!userId) return null;
  const s = new Date(startAt).getTime();
  const e = endAt ? new Date(endAt).getTime() : s + 60000;
  return (
    db.privateBusy.find(
      (b) =>
        b.organizationId === orgId &&
        b.userId === userId &&
        new Date(b.startAt).getTime() < e &&
        new Date(b.endAt).getTime() > s,
    ) || null
  );
}

const linkOf = (req) =>
  db.privateCalendarLinks.find(
    (l) => l.userId === req.user.id && l.organizationId === req.user.organizationId,
  ) || null;

const linkView = (link) =>
  link && {
    urlHint: link.urlHint,
    status: link.status,
    lastError: link.lastError || null,
    lastSyncedAt: link.lastSyncedAt,
    blockCount: link.blockCount,
  };

router.get('/calendar/private-link', requireAuth, requirePermission('calendar.view'), (req, res) => {
  res.json({
    link: linkView(linkOf(req)) || null,
    canOverrideBusy: can(req.user, 'calendar.override_busy'),
  });
});

router.put('/calendar/private-link', requireAuth, requirePermission('calendar.view'), (req, res) => {
  const raw = String((req.body || {}).url || '').trim();
  const url = raw.startsWith('webcal://') ? `https://${raw.slice('webcal://'.length)}` : raw;
  if (!/^https:\/\/[^\s]+$/i.test(url) || !/\.ics(\?|$)|ical/i.test(url)) {
    return res.status(400).json({ message: 'To nie jest adres w formacie iCal — link powinien konczyc sie na .ics.' });
  }

  const orgId = req.user.organizationId;
  db.privateBusy = db.privateBusy.filter((b) => b.userId !== req.user.id);
  const busy = generateBusy(orgId, req.user.id);
  db.privateBusy.push(...busy);

  const segments = url.split('/').filter(Boolean);
  const hint = `${segments[1] || 'kalendarz'}/…/${(segments[segments.length - 2] || '').slice(0, 4)}…/${segments[segments.length - 1]}`;
  const existing = linkOf(req);
  const link = existing || { userId: req.user.id, organizationId: orgId };
  Object.assign(link, {
    urlHint: hint,
    status: 'ok',
    lastError: null,
    lastSyncedAt: nowIso(),
    blockCount: busy.length,
  });
  if (!existing) db.privateCalendarLinks.push(link);
  res.json({ link: linkView(link) });
});

router.post('/calendar/private-link/refresh', requireAuth, requirePermission('calendar.view'), (req, res) => {
  const link = linkOf(req);
  if (!link) return res.json({ link: null });
  db.privateBusy = db.privateBusy.filter((b) => b.userId !== req.user.id);
  const busy = generateBusy(req.user.organizationId, req.user.id);
  db.privateBusy.push(...busy);
  Object.assign(link, { status: 'ok', lastError: null, lastSyncedAt: nowIso(), blockCount: busy.length });
  res.json({ link: linkView(link) });
});

router.delete('/calendar/private-link', requireAuth, requirePermission('calendar.view'), (req, res) => {
  db.privateBusy = db.privateBusy.filter((b) => b.userId !== req.user.id);
  db.privateCalendarLinks = db.privateCalendarLinks.filter((l) => l.userId !== req.user.id);
  res.status(204).end();
});

/** Szare pola „Zajete" dla siatki — cala organizacja, bez tresci wydarzen. */
router.get('/calendar/events/private-busy', requireAuth, requirePermission('calendar.view'), (req, res) => {
  const from = req.query.from ? new Date(req.query.from).getTime() : 0;
  const to = req.query.to ? new Date(req.query.to).getTime() : Date.now();
  const wanted = req.query.userIds
    ? new Set(String(req.query.userIds).split(',').map((s) => s.trim()).filter(Boolean))
    : null;
  res.json(
    db.privateBusy
      .filter((b) => b.organizationId === req.user.organizationId)
      .filter((b) => !wanted || wanted.has(b.userId))
      .filter((b) => overlaps(from, to, new Date(b.startAt).getTime(), new Date(b.endAt).getTime()))
      .map((b) => ({ userId: b.userId, startAt: b.startAt, endAt: b.endAt })),
  );
});

router.get('/calendar/events', requireAuth, requirePermission('calendar.view'), (req, res) => {
  const from = req.query.from ? new Date(req.query.from).getTime() : 0;
  const to = req.query.to ? new Date(req.query.to).getTime() : Number.MAX_SAFE_INTEGER;
  const wanted = req.query.calendarIds
    ? new Set(String(req.query.calendarIds).split(',').map((s) => s.trim()).filter(Boolean))
    : null;
  const readable = new Set(
    visibleCalendars(req)
      .filter((row) => LEVEL_RANK[row.level] >= LEVEL_RANK.reader)
      .map((row) => row.calendar.id),
  );

  const rows = db.calendarEvents.filter((e) => {
    if (e.organizationId !== req.user.organizationId) return false;
    if (!readable.has(e.calendarId)) return false;
    if (wanted && !wanted.has(e.calendarId)) return false;
    if (req.query.assignee && e.assigneeId !== req.query.assignee) return false;
    return overlaps(from, to, new Date(e.startAt).getTime(), endOf(e));
  });
  res.json(rows.map(eventView));
});

router.post('/calendar/events', requireAuth, requirePermission('calendar.view'), (req, res) => {
  const body = req.body || {};
  const missing = [];
  if (!body.calendarId) missing.push('calendarId');
  if (!body.title || !String(body.title).trim()) missing.push('title');
  if (!body.startAt) missing.push('startAt');
  if (missing.length) return unprocessable(res, 'Brakuje danych wydarzenia.', missing);

  const found = calendarFor(req, body.calendarId, 'writer');
  if (found.error) {
    return res.status(found.error).json({ message: 'Brak prawa zapisu w tym kalendarzu.' });
  }

  const recurrence = body.recurrence && FREQ.has(body.recurrence.freq) ? body.recurrence : null;
  const occurrences = recurrence
    ? expand(body.startAt, body.endAt, recurrence)
    : [{ startAt: new Date(body.startAt).toISOString(), endAt: body.endAt ? new Date(body.endAt).toISOString() : null }];

  if (req.query.allowConflict !== 'true') {
    for (const occurrence of occurrences) {
      const clash = resourceConflict(found.calendar, occurrence.startAt, occurrence.endAt, null);
      if (clash) {
        return res.status(409).json({
          message: `Zasob jest juz zajety: ${clash.title}.`,
          conflictWith: clash.id,
        });
      }
    }
  }

  // Twarda kolizja z prywatna zajetoscia WYKONAWCY. Przebic moze tylko
  // `calendar.manage` — dlatego samo `allowConflict=true` tu nie wystarcza.
  const canOverride = can(req.user, 'calendar.override_busy');
  if (!(req.query.allowConflict === 'true' && canOverride)) {
    const hits = occurrences.filter((o) =>
      privateBusyClash(req.user.organizationId, body.assigneeId || null, o.startAt, o.endAt),
    );
    if (hits.length) {
      return res.status(409).json({
        code: 'private_busy',
        message:
          occurrences.length > 1
            ? `Wykonawca ma prywatna zajetosc w ${hits.length} z ${occurrences.length} terminow.`
            : 'Wykonawca ma w tym czasie prywatna zajetosc.',
        userId: body.assigneeId,
        occurrences: hits.length,
        slots: hits.slice(0, 5).map((o) => ({ startAt: o.startAt, endAt: o.endAt })),
      });
    }
  }

  const groupId = recurrence ? uuid() : null;
  const created = occurrences.map((occurrence) => {
    const event = {
      id: uuid(),
      organizationId: req.user.organizationId,
      calendarId: body.calendarId,
      title: String(body.title).trim(),
      description: body.description || null,
      location: body.location || null,
      color: body.color || null,
      startAt: occurrence.startAt,
      endAt: occurrence.endAt,
      allDay: Boolean(body.allDay),
      assigneeId: body.assigneeId || null,
      attendees: (body.attendeeIds || []).map((id) => ({ id, response: 'needs_action' })),
      recurrenceGroupId: groupId,
      recurrenceRule: recurrence
        ? `FREQ=${recurrence.freq.toUpperCase()};INTERVAL=${recurrence.interval || 1}`
        : null,
      createdBy: req.user.id,
      createdAt: nowIso(),
    };
    db.calendarEvents.push(event);
    return event;
  });

  // Panel dostaje pierwsze wystapienie — reszte serii dociagnie lista zakresu.
  return res.status(201).json(eventView(created[0]));
});

router.patch('/calendar/events/:id', requireAuth, requirePermission('calendar.view'), (req, res) => {
  const event = db.calendarEvents.find(
    (e) => e.id === req.params.id && e.organizationId === req.user.organizationId,
  );
  if (!event) return res.status(404).json({ message: 'Wydarzenie nie istnieje.' });
  const found = calendarFor(req, event.calendarId, 'writer');
  if (found.error) {
    return res.status(found.error).json({ message: 'Brak prawa zapisu w tym kalendarzu.' });
  }

  const scope = SCOPES.has(req.query.scope) ? req.query.scope : 'this';
  const body = req.body || {};

  if (body.startAt && req.query.allowConflict !== 'true') {
    const clash = resourceConflict(found.calendar, body.startAt, body.endAt, event.id);
    if (clash) {
      return res.status(409).json({
        message: `Zasob jest juz zajety: ${clash.title}.`,
        conflictWith: clash.id,
      });
    }
  }

  // Prywatna zajetosc: liczymy przy zmianie czasu ALBO wykonawcy (nowa osoba
  // moze miec zajete to, co poprzednia miala wolne).
  const canOverride = can(req.user, 'calendar.override_busy');
  if (
    !(req.query.allowConflict === 'true' && canOverride) &&
    scope === 'this' &&
    (body.startAt !== undefined || body.endAt !== undefined || body.assigneeId !== undefined)
  ) {
    const assignee = body.assigneeId !== undefined ? body.assigneeId : event.assigneeId;
    const startAt = body.startAt !== undefined ? body.startAt : event.startAt;
    const endAt = body.endAt !== undefined ? body.endAt : event.endAt;
    const hit = privateBusyClash(req.user.organizationId, assignee, startAt, endAt);
    if (hit) {
      return res.status(409).json({
        code: 'private_busy',
        message: 'Wykonawca ma w tym czasie prywatna zajetosc.',
        userId: assignee,
        occurrences: 1,
        slots: [{ startAt: hit.startAt, endAt: hit.endAt }],
      });
    }
  }

  // Przesuniecie godziny nakladamy jako RÓŻNICĘ, zeby „ten i dalsze" nie
  // sciagnelo calej serii na jeden dzien.
  const shift = body.startAt ? new Date(body.startAt).getTime() - new Date(event.startAt).getTime() : 0;
  const targets = targetsFor(event, scope);

  for (const target of targets) {
    if (body.title !== undefined) target.title = String(body.title).trim();
    if (body.description !== undefined) target.description = body.description;
    if (body.location !== undefined) target.location = body.location;
    if (body.color !== undefined) target.color = body.color;
    if (body.allDay !== undefined) target.allDay = Boolean(body.allDay);
    if (body.assigneeId !== undefined) target.assigneeId = body.assigneeId;
    if (body.attendeeIds !== undefined) {
      const previous = new Map((target.attendees || []).map((a) => [a.id, a.response]));
      target.attendees = body.attendeeIds.map((id) => ({
        id,
        response: previous.get(id) || 'needs_action',
      }));
    }
    if (body.startAt !== undefined) {
      if (target.id === event.id) {
        target.startAt = new Date(body.startAt).toISOString();
      } else if (shift) {
        target.startAt = new Date(new Date(target.startAt).getTime() + shift).toISOString();
      }
    }
    if (body.endAt !== undefined) {
      if (target.id === event.id) {
        target.endAt = body.endAt ? new Date(body.endAt).toISOString() : null;
      } else if (shift && target.endAt) {
        target.endAt = new Date(new Date(target.endAt).getTime() + shift).toISOString();
      }
    }
  }

  return res.json(eventView(event));
});

router.delete('/calendar/events/:id', requireAuth, requirePermission('calendar.view'), (req, res) => {
  const event = db.calendarEvents.find(
    (e) => e.id === req.params.id && e.organizationId === req.user.organizationId,
  );
  if (!event) return res.status(404).json({ message: 'Wydarzenie nie istnieje.' });
  const found = calendarFor(req, event.calendarId, 'writer');
  if (found.error) {
    return res.status(found.error).json({ message: 'Brak prawa zapisu w tym kalendarzu.' });
  }

  const scope = SCOPES.has(req.query.scope) ? req.query.scope : 'this';
  const doomed = new Set(targetsFor(event, scope).map((e) => e.id));
  db.calendarEvents = db.calendarEvents.filter((e) => !doomed.has(e.id));
  return res.status(204).end();
});

router.post('/calendar/events/:id/rsvp', requireAuth, requirePermission('calendar.view'), (req, res) => {
  const event = db.calendarEvents.find(
    (e) => e.id === req.params.id && e.organizationId === req.user.organizationId,
  );
  if (!event) return res.status(404).json({ message: 'Wydarzenie nie istnieje.' });
  const response = (req.body || {}).response;
  if (!RSVP.has(response)) return unprocessable(res, 'Nieznana odpowiedz.', ['response']);

  const attendee = (event.attendees || []).find((a) => a.id === req.user.id);
  // Odpowiedziec moze tylko uczestnik — tak samo jak board360.
  if (!attendee) return res.status(403).json({ message: 'Nie jestes uczestnikiem wydarzenia.' });
  attendee.response = response;
  return res.status(204).end();
});

/** Wystapienia objete zakresem zmiany serii. */
function targetsFor(event, scope) {
  if (!event.recurrenceGroupId || scope === 'this') return [event];
  const group = db.calendarEvents.filter((e) => e.recurrenceGroupId === event.recurrenceGroupId);
  if (scope === 'all') return group;
  const from = new Date(event.startAt).getTime();
  return group.filter((e) => new Date(e.startAt).getTime() >= from);
}

module.exports = router;
