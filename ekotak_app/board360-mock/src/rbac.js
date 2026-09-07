'use strict';
/*
 * Uprawnienia — uproszczone odwzorowanie board360 (prompt A4).
 * `settings.company` steruje anonimizacja RODO w karcie klienta,
 * `deal.manage` wszystkimi zapisami w CRM.
 */

const ALL_PERMS = [
  'crm.view',
  'deal.manage',
  'telephony.use',
  'settings.team',
  'settings.company',
  'reports.view',
  'tasks.view',
  'tasks.manage',
  'projects.view',
  'projects.manage',
  // Modul Serwis (zlecenia + karty przegladow gwarancyjnych).
  'service.view',
  'service.manage',
  // Modul Kalendarz — jedno uprawnienie na odczyt i zapis; o prawie pisania
  // decyduje poziom dostepu do kalendarza, nie rola (jak w board360).
  'calendar.view',
  // Przebicie twardej blokady 409 od PRYWATNEJ zajetosci wykonawcy
  // ("Zaplanuj mimo to"). Celowo NIE dla serwisanta i montera — tak samo
  // jak w board360, gdzie to prawo maja planisci: admin/koordynator/biuro.
  'calendar.override_busy',
  // Sprzedaz i magazyn (zakladka „Zamowienie"). W board360 `order.manage`
  // gate-uje takze ODCZYT listy zamowien, nie tylko zapis — trzymamy to tak
  // samo, bo na tym stoi rozroznienie „brak zamowien" od „brak dostepu".
  'offer.manage',
  'order.manage',
  'inventory.view',
  'inventory.manage',
  // Modul HR — wlasny urlop widzi i planuje KAZDY pracownik (`hr.view`).
  // `hr.manage` to kadry: kartoteki, limity i podglad calego zespolu.
  //
  // UWAGA, to jest sedno testu modulu Urlop: koordynator — zwierzchnik montazu
  // i serwisu — celowo NIE ma `hr.manage`, tak jak w board360. Wnioski
  // podwladnych zatwierdza przez `GET /api/hr/leave/inbox`, ktore chodzi pod
  // `hr.view`; `GET /api/hr/overview` musi mu oddac 403.
  'hr.view',
  'hr.manage',
  // Modul Email — PELNY wglad we wspoldzielona skrzynke kontakt@ekotak.pl.
  // Bez tego prawa kazdy widzi w niej tylko SWOJ WYCINEK (watki jego deali plus
  // niedowiazane od adresow jego klientow), a `scope=all` konczy sie kodem 403.
  // Skrzynki PERSONALNEJ to prawo nie otwiera nikomu — takze adminowi.
  'email.view_all',
];

const ROLE_PERMS = {
  admin: ALL_PERMS,
  zarzad: ALL_PERMS,
  koordynator: [
    'crm.view', 'deal.manage', 'telephony.use', 'reports.view',
    'tasks.view', 'tasks.manage', 'projects.view', 'projects.manage',
    'service.view', 'service.manage', 'calendar.view', 'calendar.override_busy',
    'offer.manage', 'order.manage', 'inventory.view', 'inventory.manage',
    'hr.view',
  ],
  // Serwisant widzi projekty, ale nie zaklada w nich zadan — na tym koncie da sie
  // na telefonie sprawdzic, ze krok "projekt" w kreatorze konczy sie kodem 403.
  // Od poluzowania z 2026-09-06 `projects.view` wystarcza takze do ZGLOSZENIA
  // POMYSLU i do domkniecia WLASNEGO zadania (cudze nadal 403).
  serwisant: [
    'crm.view', 'telephony.use', 'tasks.view', 'tasks.manage', 'projects.view',
    'service.view', 'service.manage', 'calendar.view', 'hr.view',
  ],
  // Biuro celowo BEZ serwisu — tak samo jak w board360, gdzie modul Serwis ma
  // role admin / koordynator / serwisant / montaz. Konta biurowego seed nie
  // zaklada, wiec sciezke 403 sprawdza sie recznie (zmiana roli w seedzie).
  biuro: [
    'crm.view', 'deal.manage', 'tasks.view', 'tasks.manage', 'projects.view',
    'calendar.view', 'calendar.override_busy',
    'offer.manage', 'order.manage', 'inventory.view', 'inventory.manage',
    'hr.view', 'hr.manage',
    // Biuro obsluguje kontakt@ekotak.pl na co dzien — widzi cala skrzynke.
    'email.view_all',
  ],
  // Montaz widzi magazyn, ale nie zaklada zamowien — na tym koncie sprawdza sie
  // zakladka „Zamowienie" w wariancie „rezerwacja jest, zamowien nie widac".
  montaz: [
    'crm.view', 'tasks.view', 'tasks.manage', 'projects.view',
    'service.view', 'service.manage', 'calendar.view', 'inventory.view',
    'hr.view',
  ],
  stazysta: ['hr.view'],
};

const permsFor = (role) => ROLE_PERMS[role] || [];

/** AuthContext board360 — to samo cialo zwraca mobile-login i GET /api/me. */
const authContext = (user) => ({
  userId: user.id,
  organizationId: user.organizationId,
  email: user.email,
  role: user.role,
  permissions: permsFor(user.role),
  clientVisibility: user.clientVisibility || 'all',
});

module.exports = { ALL_PERMS, ROLE_PERMS, permsFor, authContext };
