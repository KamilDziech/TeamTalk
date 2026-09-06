'use strict';
/*
 * Modul HR — urlopy (zakladka „Urlop" panelu, modul Urlop w TeamTalku).
 *
 * Ksztalt rekordow i regul = `api/src/modules/hr` z board360:
 *  - RODZAJ UMOWY decyduje o trybie: umowa o prace -> wymiar (pula dni),
 *    kazda inna -> same dni bezplatnego, bez limitu; poza umowa o prace API
 *    przyjmuje WYLACZNIE rodzaj `bezplatny` (422),
 *  - dni robocze liczy serwer, z pominieciem sobot, niedziel i polskich swiat,
 *  - wniosek nachodzacy na inny wlasny wniosek konczy sie kodem 409
 *    z lista `conflictIds` (front otwiera edycje istniejacego),
 *  - decyzje podejmuje ZWIERZCHNIK; gdy jest na urlopie — jego backup
 *    decyzyjny; gdy pracownik podlega Zarzadowi — dowolny admin albo zarzad.
 *
 * DWIE TRASY, KTORYCH BOARD360 JESZCZE NIE MA (ustalenie 2026-09-06, do
 * dopisania po stronie panelu — atrapa jest tu referencja kontraktu):
 *
 *  1. `GET /api/hr/leave/inbox` pod `hr.view`. Dzis skrzynka zwierzchnika jest
 *     dostepna wylacznie przez `GET /hr/overview`, ktore wymaga `hr.manage` —
 *     a koordynator, czyli typowy zwierzchnik montazu i serwisu, ma samo
 *     `hr.view` i dostaje 403 ZANIM zadziala regula `canDecideLeave`. Nowa
 *     trasa oddaje wylacznie wnioski jego podwladnych, bez kartotek i limitow.
 *  2. `GET /api/hr/absences` pod `hr.view` — SAM FAKT nieobecnosci kolegow
 *     (osoba, zakres, status), bez rodzaju urlopu i bez powodu. Tego wymaga
 *     tlo kalendarza urlopowego i os czasu zespolu na telefonie; przez
 *     `hr/overview` nie da sie tego podac, bo tam ida pelne dane kadrowe.
 *
 * Analogicznie schodzi bramka `POST /hr/leave/:id/decision`: z `hr.manage` na
 * `hr.view`, bo prawdziwym filtrem jest `canDecideLeave`, a nie rola.
 */

const express = require('express');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission, unprocessable } = require('../middleware');
const { db } = require('../store');
const {
  LEAVE_TYPES,
  canDecideLeave,
  computeLeaveBalance,
  countWorkingDays,
  leaveModeOf,
  overlaps,
  parseDay,
} = require('../hr-domain');

const router = express.Router();

const DEFAULTS = { annualLeaveDays: 26, onDemandDays: 4, carriedOverDays: 0, unpaidLeaveDays: 0 };

const usersOf = (orgId) => db.users.filter((u) => u.organizationId === orgId);
const profilesOf = (orgId) => db.hrProfiles.filter((p) => p.organizationId === orgId);
const requestsOf = (orgId) => db.leaveRequests.filter((r) => r.organizationId === orgId);

const userById = (orgId, userId) => usersOf(orgId).find((u) => u.id === userId) || null;
const profileOf = (orgId, userId) => profilesOf(orgId).find((p) => p.userId === userId) || null;

const personName = (user) =>
  user ? [user.firstName, user.lastName].filter(Boolean).join(' ').trim() || user.email : null;

/** Kartoteka do liczenia licznikow — brak wpisu nie moze zerowac wymiaru. */
const profileForBalance = (orgId, userId) => {
  const p = profileOf(orgId, userId);
  return {
    annualLeaveDays: p ? p.annualLeaveDays : DEFAULTS.annualLeaveDays,
    onDemandDays: p ? p.onDemandDays : DEFAULTS.onDemandDays,
    carriedOverDays: p ? p.carriedOverDays : DEFAULTS.carriedOverDays,
    unpaidLeaveDays: p ? p.unpaidLeaveDays : DEFAULTS.unpaidLeaveDays,
    employmentType: p ? p.employmentType : null,
  };
};

/** Wniosek w ksztalcie, ktory czyta panel i mobilka. */
const viewRequest = (r) => {
  const user = userById(r.organizationId, r.userId);
  return {
    id: r.id,
    organizationId: r.organizationId,
    userId: r.userId,
    employeeEmail: user ? user.email : null,
    employeeName: personName(user),
    type: r.type,
    startDate: r.startDate,
    endDate: r.endDate,
    workingDays: r.workingDays,
    status: r.status,
    reason: r.reason,
    decidedById: r.decidedById,
    decidedAt: r.decidedAt,
    decisionNote: r.decisionNote,
    createdAt: r.createdAt,
    updatedAt: r.updatedAt,
  };
};

/** Kartoteka w ksztalcie odpowiedzi (dane osoby doklejane przy odczycie). */
const viewProfile = (p) => {
  const user = userById(p.organizationId, p.userId);
  return {
    id: p.id,
    organizationId: p.organizationId,
    userId: p.userId,
    email: user ? user.email : null,
    firstName: user ? user.firstName : null,
    lastName: user ? user.lastName : null,
    role: user ? user.role : null,
    managerId: p.managerId,
    backupDecisionId: p.backupDecisionId,
    annualLeaveDays: p.annualLeaveDays,
    onDemandDays: p.onDemandDays,
    carriedOverDays: p.carriedOverDays,
    unpaidLeaveDays: p.unpaidLeaveDays,
    employmentType: p.employmentType,
    employmentStart: p.employmentStart,
    position: p.position,
    createdAt: p.createdAt,
    updatedAt: p.updatedAt,
  };
};

/** Czy osoba ma DZIS zatwierdzony urlop (nieobecnosc zwierzchnika). */
function isOnApprovedLeave(orgId, userId, today) {
  const day = today.toISOString().slice(0, 10);
  return requestsOf(orgId).some(
    (r) =>
      r.userId === userId &&
      r.status === 'zatwierdzony' &&
      r.startDate.slice(0, 10) <= day &&
      r.endDate.slice(0, 10) >= day,
  );
}

/** Aktywne wnioski osoby nachodzace na zakres (pomijajac wskazany wniosek). */
const overlappingFor = (orgId, userId, startDate, endDate, exceptId) =>
  requestsOf(orgId).filter(
    (r) =>
      r.userId === userId &&
      r.id !== exceptId &&
      (r.status === 'oczekuje' || r.status === 'zatwierdzony') &&
      overlaps(r.startDate, r.endDate, startDate, endDate),
  );

/**
 * Walidacja ciala wniosku. Zwraca `{ error }` w ksztalcie odpowiedzi HTTP albo
 * `{ value }` z gotowymi polami.
 *
 * Uwaga na kody: „zakres bez dni roboczych" to w board360 blad STANU wniosku,
 * czyli 409, a nie 422 — odwzorowujemy to co do kodu, bo mobilka rozroznia te
 * dwie sciezki (409 otwiera edycje, 422 tlumaczy sie napisem).
 */
function validateLeaveBody(req) {
  const body = req.body || {};
  const type = body.type || 'wypoczynkowy';
  if (!LEAVE_TYPES.includes(type)) {
    return { error: { status: 422, message: `Nieznany rodzaj urlopu: ${type}` } };
  }
  const start = parseDay(body.startDate);
  const end = parseDay(body.endDate);
  if (!start || !end) {
    return { error: { status: 422, message: 'Podaj date rozpoczecia i zakonczenia urlopu.' } };
  }
  if (end < start) {
    return { error: { status: 422, message: 'Data zakonczenia nie moze byc wczesniejsza niz rozpoczecia.' } };
  }
  const workingDays = countWorkingDays(start, end);
  if (workingDays === 0) {
    return { error: { status: 409, message: 'Wybrany zakres nie obejmuje dni roboczych.' } };
  }
  const reason = typeof body.reason === 'string' && body.reason.trim() !== '' ? body.reason.trim() : null;
  return { value: { type, startDate: start.toISOString(), endDate: end.toISOString(), workingDays, reason } };
}

/** Poza umowa o prace zostaje wylacznie urlop bezplatny (422). */
function leaveTypeAllowed(orgId, userId, type) {
  if (type === 'bezplatny') return true;
  const p = profileOf(orgId, userId);
  return leaveModeOf(p ? p.employmentType : null) !== 'bezplatny';
}

const TYPE_NOT_AVAILABLE = 'Przy tym rodzaju umowy dostepny jest wylacznie urlop bezplatny.';

// ── Pulpit pracownika ────────────────────────────────────────────────────────

/** Profil, liczniki i wlasne wnioski — jedno wywolanie na caly ekran „Moje". */
router.get('/hr/me', requireAuth, requirePermission('hr.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const profile = profileOf(orgId, req.user.id);
  const requests = requestsOf(orgId)
    .filter((r) => r.userId === req.user.id)
    .sort((a, b) => b.startDate.localeCompare(a.startDate));
  const balance = computeLeaveBalance(
    profileForBalance(orgId, req.user.id),
    requests,
    new Date(),
    new Date().getFullYear(),
  );
  res.json({
    profile: profile ? viewProfile(profile) : null,
    balance,
    requests: requests.map(viewRequest),
  });
});

// ── Wlasne wnioski ───────────────────────────────────────────────────────────

router.post('/hr/leave', requireAuth, requirePermission('hr.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const parsed = validateLeaveBody(req);
  if (parsed.error) return res.status(parsed.error.status).json({ message: parsed.error.message });
  const { type, startDate, endDate, workingDays, reason } = parsed.value;

  if (!leaveTypeAllowed(orgId, req.user.id, type)) {
    return unprocessable(res, TYPE_NOT_AVAILABLE);
  }
  const conflicts = overlappingFor(orgId, req.user.id, startDate, endDate, null);
  if (conflicts.length) {
    return res.status(409).json({
      message: 'Wniosek naklada sie na istniejacy urlop.',
      conflictIds: conflicts.map((c) => c.id),
    });
  }

  const row = {
    id: uuid(),
    organizationId: orgId,
    userId: req.user.id,
    type,
    startDate,
    endDate,
    workingDays,
    status: 'oczekuje',
    reason,
    decidedById: null,
    decidedAt: null,
    decisionNote: null,
    createdAt: nowIso(),
    updatedAt: nowIso(),
  };
  db.leaveRequests.push(row);
  res.status(201).json(viewRequest(row));
});

/** Edycja wlasnego wniosku — cofa go do akceptacji (status wraca na „oczekuje"). */
router.patch('/hr/leave/:id', requireAuth, requirePermission('hr.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const row = requestsOf(orgId).find((r) => r.id === req.params.id && r.userId === req.user.id);
  if (!row) return res.status(404).json({ message: 'Wniosek urlopowy nie zostal znaleziony.' });
  if (row.status === 'odrzucony' || row.status === 'anulowany') {
    return res.status(409).json({ message: 'Wniosek jest juz zamkniety.' });
  }
  const parsed = validateLeaveBody(req);
  if (parsed.error) return res.status(parsed.error.status).json({ message: parsed.error.message });
  const { type, startDate, endDate, workingDays } = parsed.value;

  if (!leaveTypeAllowed(orgId, req.user.id, type)) {
    return unprocessable(res, TYPE_NOT_AVAILABLE);
  }
  const conflicts = overlappingFor(orgId, req.user.id, startDate, endDate, row.id);
  if (conflicts.length) {
    return res.status(409).json({
      message: 'Wniosek naklada sie na istniejacy urlop.',
      conflictIds: conflicts.map((c) => c.id),
    });
  }

  Object.assign(row, {
    type,
    startDate,
    endDate,
    workingDays,
    status: 'oczekuje',
    decidedById: null,
    decidedAt: null,
    decisionNote: null,
    updatedAt: nowIso(),
  });
  res.json(viewRequest(row));
});

router.post('/hr/leave/:id/cancel', requireAuth, requirePermission('hr.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const row = requestsOf(orgId).find((r) => r.id === req.params.id && r.userId === req.user.id);
  if (!row) return res.status(404).json({ message: 'Wniosek urlopowy nie zostal znaleziony.' });
  if (row.status === 'odrzucony' || row.status === 'anulowany') {
    return res.status(409).json({ message: 'Wniosek jest juz zamkniety.' });
  }
  row.status = 'anulowany';
  row.updatedAt = nowIso();
  res.json(viewRequest(row));
});

// ── Skrzynka zwierzchnika (trasa spoza dzisiejszego board360) ────────────────

/**
 * Kto rozstrzyga wniosek danej osoby i czy jest to pytajacy.
 * Cala regula zostaje po stronie serwera — tylko on wie, czy zwierzchnik jest
 * dzis na urlopie i kogo zostawil jako backup.
 */
function approverInfo(orgId, employeeUserId, decider, today) {
  const profile = profileOf(orgId, employeeUserId);
  const managerId = profile ? profile.managerId : null;
  let managerAbsent = false;
  let backupId = null;
  if (managerId) {
    const manager = profileOf(orgId, managerId);
    backupId = manager ? manager.backupDecisionId : null;
    managerAbsent = isOnApprovedLeave(orgId, managerId, today);
  }
  const canDecide = canDecideLeave({
    employeeManagerId: managerId,
    deciderId: decider.id,
    deciderRole: decider.role,
    managerAbsent,
    managerBackupDecisionId: backupId,
  });
  const awaitingIsBackup = Boolean(managerAbsent && backupId);
  const awaitingId = managerId === null ? null : awaitingIsBackup ? backupId : managerId;
  const awaitingName = managerId === null ? 'Zarzad' : personName(userById(orgId, awaitingId));
  return { managerId, managerAbsent, backupId, canDecide, awaitingIsBackup, awaitingName };
}

/**
 * Wnioski, o ktorych decyduje pytajacy: jego bezposredni podwladni, osoby
 * zastepowane w czasie nieobecnosci ich zwierzchnika oraz — dla admina
 * i zarzadu — pracownicy podlegli bezposrednio Zarzadowi.
 *
 * Swiadome zawezenie: NIE jest to podglad wnioskow calej firmy. Kto ma widziec
 * wszystko, ma `hr.manage` i `GET /hr/overview`.
 */
router.get('/hr/leave/inbox', requireAuth, requirePermission('hr.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const today = new Date();
  const isBoard = req.user.role === 'admin' || req.user.role === 'zarzad';

  const mine = profilesOf(orgId).filter((p) => {
    if (p.userId === req.user.id) return false;
    if (p.managerId === req.user.id) return true;
    if (p.managerId === null) return isBoard;
    // Zastepstwo: zwierzchnik tej osoby jest dzis na urlopie i zostawil mnie.
    const manager = profileOf(orgId, p.managerId);
    return Boolean(
      manager &&
        manager.backupDecisionId === req.user.id &&
        isOnApprovedLeave(orgId, p.managerId, today),
    );
  });
  const userIds = new Set(mine.map((p) => p.userId));

  const requests = requestsOf(orgId)
    .filter((r) => userIds.has(r.userId))
    .sort((a, b) => {
      // Najpierw to, co czeka na decyzje; potem po dacie rozpoczecia.
      if (a.status !== b.status) {
        if (a.status === 'oczekuje') return -1;
        if (b.status === 'oczekuje') return 1;
      }
      return a.startDate.localeCompare(b.startDate);
    })
    .map((r) => {
      const info = approverInfo(orgId, r.userId, req.user, today);
      const user = userById(orgId, r.userId);
      return {
        request: viewRequest(r),
        canDecide: info.canDecide,
        awaitingName: info.canDecide ? null : info.awaitingName,
        awaitingIsBackup: info.awaitingIsBackup,
        employeeRole: user ? user.role : null,
      };
    });

  res.json({ requests });
});

/**
 * Decyzja zwierzchnika. Bramka to `hr.view` + `canDecideLeave` — rola sama
 * z siebie nie wystarcza i nie jest tez wymagana (koordynator bez `hr.manage`
 * MUSI moc zatwierdzic urlop swojego montera).
 */
router.post('/hr/leave/:id/decision', requireAuth, requirePermission('hr.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const row = requestsOf(orgId).find((r) => r.id === req.params.id);
  if (!row) return res.status(404).json({ message: 'Wniosek urlopowy nie zostal znaleziony.' });
  if (row.status !== 'oczekuje') {
    return res.status(409).json({ message: 'Wniosek zostal juz rozpatrzony.' });
  }
  const status = (req.body || {}).status;
  if (status !== 'zatwierdzony' && status !== 'odrzucony') {
    return unprocessable(res, 'Decyzja moze byc tylko „zatwierdzony" albo „odrzucony".');
  }
  const info = approverInfo(orgId, row.userId, req.user, new Date());
  if (!info.canDecide) {
    return res.status(403).json({
      message: 'Wniosek moze zatwierdzic lub odrzucic wylacznie przelozony pracownika.',
    });
  }
  const note = (req.body || {}).decisionNote;
  Object.assign(row, {
    status,
    decidedById: req.user.id,
    decidedAt: nowIso(),
    decisionNote: typeof note === 'string' && note.trim() !== '' ? note.trim() : null,
    updatedAt: nowIso(),
  });
  res.json(viewRequest(row));
});

// ── Nieobecnosci zespolu (trasa spoza dzisiejszego board360) ─────────────────

/**
 * SAM FAKT nieobecnosci kolegow: kto, kiedy i czy juz zatwierdzone. Bez rodzaju
 * urlopu i bez powodu — to tlo kalendarza i wiersz osi czasu, nie kartoteka.
 * Wlasne wnioski sa tu POMIJANE: te przychodza pelne przez `GET /hr/me`.
 *
 * Zakres `from`/`to` opcjonalny; bez niego oddajemy biezacy rok.
 */
router.get('/hr/absences', requireAuth, requirePermission('hr.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const year = new Date().getFullYear();
  const from = (req.query.from ? String(req.query.from) : `${year}-01-01`).slice(0, 10);
  const to = (req.query.to ? String(req.query.to) : `${year}-12-31`).slice(0, 10);

  const rows = requestsOf(orgId)
    .filter(
      (r) =>
        r.userId !== req.user.id &&
        (r.status === 'oczekuje' || r.status === 'zatwierdzony') &&
        overlaps(r.startDate, r.endDate, from, to),
    )
    .sort((a, b) => a.startDate.localeCompare(b.startDate))
    .map((r) => {
      const user = userById(orgId, r.userId);
      return {
        id: r.id,
        userId: r.userId,
        employeeName: personName(user),
        employeeRole: user ? user.role : null,
        startDate: r.startDate,
        endDate: r.endDate,
        workingDays: r.workingDays,
        status: r.status,
      };
    });

  res.json(rows);
});

// ── Kadry ────────────────────────────────────────────────────────────────────

/** Liczniki calego zespolu i wszystkie wnioski — wylacznie dla kadr. */
router.get('/hr/overview', requireAuth, requirePermission('hr.manage'), (req, res) => {
  const orgId = req.user.organizationId;
  const today = new Date();
  const year = today.getFullYear();
  const requests = requestsOf(orgId);
  const employees = profilesOf(orgId).map((p) => ({
    profile: viewProfile(p),
    balance: computeLeaveBalance(
      profileForBalance(orgId, p.userId),
      requests.filter((r) => r.userId === p.userId),
      today,
      year,
    ),
  }));
  res.json({ employees, requests: requests.map(viewRequest) });
});

module.exports = router;
