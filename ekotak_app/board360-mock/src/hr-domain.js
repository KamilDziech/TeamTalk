'use strict';
/*
 * Reguly modulu HR (urlopy) — port `api/src/modules/hr/domain` z board360.
 *
 * Trzy rzeczy, ktore musza sie zgadzac co do dnia, bo aplikacja mobilna liczy
 * je RÓWNIEZ u siebie (podglad przed wyslaniem i praca bez zasiegu):
 *  1. dni robocze = bez sobot, niedziel i polskich swiat ustawowych,
 *  2. tryb urlopu bierze sie z RODZAJU UMOWY (umowa o prace -> wymiar,
 *     kazda inna -> same dni bezplatnego, bez limitu),
 *  3. liczniki roku (`computeLeaveBalance`).
 */

// ── Polskie dni ustawowo wolne ───────────────────────────────────────────────

/** Niedziela wielkanocna — algorytm Meeusa/Jonesa/Butchera. */
function easterSunday(year) {
  const a = year % 19;
  const b = Math.floor(year / 100);
  const c = year % 100;
  const d = Math.floor(b / 4);
  const e = b % 4;
  const f = Math.floor((b + 8) / 25);
  const g = Math.floor((b - f + 1) / 3);
  const h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4);
  const k = c % 4;
  const l = (32 + 2 * e + 2 * i - h - k) % 7;
  const m = Math.floor((a + 11 * h + 22 * l) / 451);
  const month = Math.floor((h + l - 7 * m + 114) / 31);
  const day = ((h + l - 7 * m + 114) % 31) + 1;
  return new Date(Date.UTC(year, month - 1, day));
}

const addUtcDays = (d, n) =>
  new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() + n));

const isoDay = (d) => d.toISOString().slice(0, 10);

const holidayCache = new Map();

/** Zbior dni wolnych (ISO yyyy-mm-dd, UTC) w danym roku. */
function polishHolidays(year) {
  const hit = holidayCache.get(year);
  if (hit) return hit;
  const set = new Set();
  const fixed = [
    [0, 1],   // Nowy Rok
    [0, 6],   // Trzech Kroli
    [4, 1],   // Swieto Pracy
    [4, 3],   // Konstytucji 3 Maja
    [7, 15],  // Wniebowziecie NMP
    [10, 1],  // Wszystkich Swietych
    [10, 11], // Niepodleglosci
    [11, 25], // Boze Narodzenie
    [11, 26], // drugi dzien
  ];
  for (const [m, d] of fixed) set.add(isoDay(new Date(Date.UTC(year, m, d))));
  const easter = easterSunday(year);
  set.add(isoDay(easter));                  // Wielkanoc
  set.add(isoDay(addUtcDays(easter, 1)));   // Poniedzialek Wielkanocny
  set.add(isoDay(addUtcDays(easter, 49)));  // Zielone Swiatki
  set.add(isoDay(addUtcDays(easter, 60)));  // Boze Cialo
  holidayCache.set(year, set);
  return set;
}

const isPolishHoliday = (d) => polishHolidays(d.getUTCFullYear()).has(isoDay(d));

/** Polnoc UTC — urlop liczy sie w dniach kalendarzowych, strefa nie gra roli. */
const atUtcMidnight = (d) =>
  new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));

/** Data z `yyyy-MM-dd` albo pelnego ISO — zawsze polnoc UTC. */
const parseDay = (value) => {
  const s = String(value || '').slice(0, 10);
  const [y, m, d] = s.split('-').map(Number);
  if (!y || !m || !d) return null;
  return new Date(Date.UTC(y, m - 1, d));
};

/** Dni robocze w przedziale [start, end] wlacznie. 0, gdy end < start. */
function countWorkingDays(start, end) {
  const s = atUtcMidnight(start);
  const e = atUtcMidnight(end);
  if (e < s) return 0;
  let count = 0;
  for (let d = new Date(s); d <= e; d = addUtcDays(d, 1)) {
    const dow = d.getUTCDay();
    if (dow !== 0 && dow !== 6 && !isPolishHoliday(d)) count += 1;
  }
  return count;
}

// ── Rodzaj umowy → tryb urlopu ───────────────────────────────────────────────

/** Kategoria dla Zarzadu — wspolnik spolki, bez limitu urlopowego. */
const EMPLOYMENT_TYPE_PARTNER = 'Wspólnik';

const EMPLOYMENT_TYPES = [
  'Umowa o pracę',
  'Umowa o pracę (na okres próbny)',
  EMPLOYMENT_TYPE_PARTNER,
  'Umowa zlecenie',
  'Umowa o dzieło',
  'B2B (kontrakt)',
  'Staż',
  'Praktyka',
];

const normalizeEmploymentType = (value) =>
  String(value)
    .trim()
    .toLowerCase()
    .replace(/ł/g, 'l')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/\s+/g, ' ');

const isEmploymentTypeMissing = (t) => !t || String(t).trim() === '';

/** Kazdy wariant umowy o prace: probna, czas okreslony, skrot „UoP". */
function isEmploymentContract(t) {
  if (isEmploymentTypeMissing(t)) return false;
  const n = normalizeEmploymentType(t);
  return n.startsWith('umowa o prace') || n === 'uop';
}

/**
 * Tryb urlopu. Pusty rodzaj umowy = NIEOKRESLONY, zostaje przy wymiarze — tak
 * jak board360, bo kartoteki zakladane automatycznie z konta nie maja go
 * wypelnionego i wyzerowanie limitu skasowaloby dane kadrowe calego zespolu.
 */
const leaveModeOf = (t) =>
  isEmploymentTypeMissing(t) ? 'wymiar' : isEmploymentContract(t) ? 'wymiar' : 'bezplatny';

// ── Wnioski i liczniki ───────────────────────────────────────────────────────

const LEAVE_TYPES = ['wypoczynkowy', 'na_zadanie', 'okolicznosciowy', 'bezplatny'];
const LEAVE_STATUSES = ['oczekuje', 'zatwierdzony', 'odrzucony', 'anulowany'];
/** Rodzaje obciazajace wymiar (pule dni). */
const PAID_POOL_TYPES = ['wypoczynkowy', 'na_zadanie'];

/**
 * Liczniki roku — port `computeLeaveBalance`. W trybie `bezplatny` limitu nie
 * ma: `entitled`/`remaining` sa zerami, a `unpaidDays` to LICZBA dni urlopu.
 */
function computeLeaveBalance(profile, requests, today, year) {
  const mode = leaveModeOf(profile.employmentType);
  const todayMid = atUtcMidnight(today);
  const inYear = requests.filter((r) => parseDay(r.startDate).getUTCFullYear() === year);
  const open = inYear.filter((r) => r.status !== 'odrzucony' && r.status !== 'anulowany');

  if (mode === 'bezplatny') {
    let used = 0;
    let planned = 0;
    let pending = 0;
    for (const r of open) {
      if (r.status === 'oczekuje') {
        pending += r.workingDays;
        continue;
      }
      if (parseDay(r.endDate) < todayMid) used += r.workingDays;
      else planned += r.workingDays;
    }
    return {
      year, mode,
      entitled: 0, used, planned, pending, remaining: 0,
      onDemandUsed: 0, onDemandTotal: 0, specialDays: 0,
      unpaidDays: used + planned, unpaidTotal: 0,
    };
  }

  let used = 0;
  let planned = 0;
  let pending = 0;
  let onDemandUsed = 0;
  let specialDays = 0;
  let unpaidDays = 0;

  for (const r of open) {
    if (r.type === 'okolicznosciowy') {
      if (r.status === 'zatwierdzony') specialDays += r.workingDays;
      continue;
    }
    if (r.type === 'bezplatny') {
      if (r.status === 'zatwierdzony') unpaidDays += r.workingDays;
      continue;
    }
    if (!PAID_POOL_TYPES.includes(r.type)) continue;
    if (r.status === 'oczekuje') {
      pending += r.workingDays;
      continue;
    }
    if (parseDay(r.endDate) < todayMid) used += r.workingDays;
    else planned += r.workingDays;
    if (r.type === 'na_zadanie') onDemandUsed += r.workingDays;
  }

  const entitled = (profile.annualLeaveDays || 0) + (profile.carriedOverDays || 0);
  return {
    year, mode,
    entitled,
    used, planned, pending,
    remaining: entitled - used - planned,
    onDemandUsed,
    onDemandTotal: profile.onDemandDays || 0,
    specialDays,
    unpaidDays,
    unpaidTotal: profile.unpaidLeaveDays || 0,
  };
}

/**
 * Kto moze rozpatrzyc wniosek — port `canDecideLeave`.
 * Regula: zwierzchnik pracownika; gdy jest nieobecny — jego backup decyzyjny;
 * gdy pracownik podlega Zarzadowi (`managerId === null`) — dowolny admin albo
 * zarzad. `admin` ma globalny override, zeby akceptacja sie nie zablokowala.
 */
function canDecideLeave({ employeeManagerId, deciderId, deciderRole, managerAbsent, managerBackupDecisionId }) {
  if (deciderRole === 'admin') return true;
  if (employeeManagerId !== null && employeeManagerId !== undefined) {
    if (deciderId === employeeManagerId) return true;
    if (managerAbsent && managerBackupDecisionId && deciderId === managerBackupDecisionId) return true;
    return false;
  }
  return deciderRole === 'zarzad';
}

/** Czy dwa przedzialy dni (ISO) zachodza na siebie. */
const overlaps = (aStart, aEnd, bStart, bEnd) =>
  String(aStart).slice(0, 10) <= String(bEnd).slice(0, 10) &&
  String(aEnd).slice(0, 10) >= String(bStart).slice(0, 10);

module.exports = {
  easterSunday,
  polishHolidays,
  isPolishHoliday,
  atUtcMidnight,
  parseDay,
  isoDay,
  addUtcDays,
  countWorkingDays,
  EMPLOYMENT_TYPES,
  EMPLOYMENT_TYPE_PARTNER,
  isEmploymentContract,
  isEmploymentTypeMissing,
  leaveModeOf,
  LEAVE_TYPES,
  LEAVE_STATUSES,
  PAID_POOL_TYPES,
  computeLeaveBalance,
  canDecideLeave,
  overlaps,
};
