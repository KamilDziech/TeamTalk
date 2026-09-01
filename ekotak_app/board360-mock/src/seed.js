'use strict';
/*
 * Dane startowe. Celowo tak dobrane, zeby na telefonie dalo sie przeklikac
 * KAZDY ekran bez ruszania panelu:
 *  - kartoteka: klienci z geo i dojazdem, jeden bez adresu, jedna para duplikatow
 *    (Marek Nowak) do przecwiczenia scalania,
 *  - lejek: po jednym dealu w kluczowych etapach + jeden zalegly + jeden stracony,
 *  - LEAD: trzy kanaly zgloszen (targi / www / tel) i deale bez zgloszenia,
 *  - instalacje, wartosci ofert i "deal wspolny" dla badge'y w kartotece.
 */

const { uuid, nowIso, hashPassword } = require('./crypto');

const DAY = 24 * 60 * 60 * 1000;
const daysAgo = (n) => new Date(Date.now() - n * DAY).toISOString();
const daysAhead = (n) => new Date(Date.now() + n * DAY).toISOString();

/** Katalog technologii — kategorie glowne daja nazwy instalacji na badge'ach. */
function seedCategories(db) {
  const main = [
    ['Ogrzewanie', ['Pompa ciepla', 'Kociol gazowy', 'Kociol na pellet']],
    ['Fotowoltaika', ['Instalacja on-grid', 'Instalacja hybrydowa']],
    ['Magazyn energii', ['Magazyn LFP', 'Magazyn hybrydowy']],
    ['Klimatyzacja', ['Split', 'Multi-split']],
    ['Rekuperacja', ['Centrala z odzyskiem']],
  ];
  const ids = {};
  main.forEach(([name, children], i) => {
    const parent = { id: uuid(), parentId: null, name, position: i };
    db.categories.push(parent);
    ids[name] = { id: parent.id, children: {} };
    children.forEach((child, j) => {
      const node = { id: uuid(), parentId: parent.id, name: child, position: j };
      db.categories.push(node);
      ids[name].children[child] = node.id;
    });
  });
  return ids;
}

function seedUsers(db) {
  const orgId = db.organization.id;
  // `functions` (ADR-0013 board360) sa osobne od ROLI: po nich kreator zadania
  // filtruje osoby pod kafelkami zespolow. `additionalRoles` licza sie na rowni
  // z rola glowna — stad monter, ktory na co dzien siedzi w biurze.
  const make = (email, password, role, firstName, lastName, opts = {}) => {
    const user = {
      id: uuid(),
      organizationId: orgId,
      email,
      passwordHash: hashPassword(password),
      role,
      firstName,
      lastName,
      clientVisibility: opts.clientVisibility || 'all',
      functions: opts.functions || [],
      additionalRoles: opts.additionalRoles || [],
    };
    db.users.push(user);
    return user;
  };
  return {
    serwisant: make('serwisant@ekotak.pl', 'test1234', 'serwisant', 'Jan', 'Serwisant', {
      functions: ['serwis', 'inzynier'],
      additionalRoles: ['montaz'], // wpada tez pod kafelek "Monter"
    }),
    admin: make('admin@ekotak.pl', 'admin1234', 'admin', 'Anna', 'Admin', {
      functions: ['ksiegowosc', 'dotacje'],
    }),
    koordynator: make('koordynator@ekotak.pl', 'test1234', 'koordynator', 'Piotr', 'Koordynator', {
      functions: ['koordynator', 'zaopatrzenie'],
    }),
  };
}

function seedClients(db) {
  const orgId = db.organization.id;
  const rows = [
    // imie, nazwisko, telefon, email, miasto, ulica, kod, geo, dojazd, typ, kategoria
    ['Marek', 'Nowak', '+48501234567', 'marek.nowak@example.com', 'Katowice', 'ul. Kwiatowa 1', '40-001',
      [50.2649, 19.0238], { kobiernice: [62.4, 51], gliwice: [28.1, 27] }, 'wlasny', 'klient'],
    // Duplikat powyzszego (ten sam telefon) — cel testu scalania w kartotece.
    ['Marek', 'Nowak', '501 234 567', null, 'Katowice', 'ul. Kwiatowa 1a', null,
      null, null, 'wlasny', 'klient'],
    ['Ewa', 'Kowalska', '+48502345678', 'ewa.kowalska@example.com', 'Gliwice', 'ul. Lesna 5', '44-100',
      [50.2945, 18.6714], { kobiernice: [83.0, 63], gliwice: [3.2, 8] }, 'wlasny', 'klient'],
    ['Piotr', 'Wisniewski', '+48503456789', null, 'Bielsko-Biala', 'ul. Gorna 12', '43-300',
      [49.8224, 19.0584], { kobiernice: [14.6, 18], gliwice: [72.5, 58] }, 'wlasny', 'klient'],
    ['Katarzyna', 'Wojcik', '+48504567890', 'k.wojcik@example.com', 'Tychy', 'ul. Polna 8', '43-100',
      [50.1372, 18.9662], { kobiernice: [44.2, 39], gliwice: [37.8, 34] }, 'wlasny', 'klient'],
    ['Tomasz', 'Kaminski', '+48505678901', null, 'Sosnowiec', 'ul. Dluga 3', '41-200',
      null, null, 'wlasny', 'klient'],
    ['Agnieszka', 'Nowak', '+48506789012', 'a.nowak@example.com', 'Zywiec', 'ul. Sloneczna 21', '34-300',
      [49.6853, 19.1922], { kobiernice: [11.3, 15], gliwice: [88.0, 70] }, 'wlasny', 'klient'],
    // Kontrahent i afiliant — zakladki kategorii w kartotece maja co pokazac.
    ['Instal', 'Serwis Sp. z o.o.', '+48338123456', 'biuro@instal-serwis.example', 'Bielsko-Biala',
      'ul. Przemyslowa 40', '43-300', null, null, 'obcy', 'kontrahent'],
    ['Rafal', 'Zielinski', '+48507890123', 'rafal@partner.example', 'Czechowice-Dziedzice',
      'ul. Legionow 2', '43-502', null, null, 'obcy', 'afiliant'],
  ];

  return rows.map(([firstName, lastName, phone, email, city, street, postalCode, geo, travel, type, category], i) => {
    const client = {
      id: uuid(),
      organizationId: orgId,
      firstName,
      lastName,
      email,
      email2: null,
      phone,
      phone2: null,
      address: `${street}, ${city}`,
      postalCode,
      city,
      street,
      geo: geo ? { lat: geo[0], lng: geo[1] } : null,
      geoCity: geo ? city : null,
      geoMunicipality: geo ? city : null,
      travel: travel
        ? {
            kobiernice: { km: travel.kobiernice[0], min: travel.kobiernice[1] },
            gliwice: { km: travel.gliwice[0], min: travel.gliwice[1] },
          }
        : null,
      type,
      category,
      createdAt: daysAgo(120 - i * 7),
      updatedAt: daysAgo(3),
    };
    db.clients.push(client);
    return client;
  });
}

/**
 * Krotki kod karty deala pokazywany ludziom (4 znaki [a-z0-9], jak
 * `deal_code_gen()` w board360). Wchodzi m.in. w tytul dyskusji w Komunikatorze
 * ("Nazwisko · kod deala"), wiec musi byc unikatem.
 */
function dealCode(db) {
  const taken = new Set(db.deals.map((d) => d.code));
  for (;;) {
    const code = Math.random().toString(36).slice(2, 6).padEnd(4, '0');
    if (!taken.has(code)) return code;
  }
}

/** Deal z pelnym kompletem pol — odpowiedz API nigdy nie gubi klucza. */
function makeDeal(db, overrides) {
  const deal = {
    id: uuid(),
    code: dealCode(db),
    organizationId: db.organization.id,
    clientId: '',
    ownerId: '',
    stageOwnerId: null,
    stage: 'lead',
    stageEnteredAt: daysAgo(1),
    source: null,
    nextContactAt: null,
    segment: 'indywidualny',
    buildingKind: 'nowy',
    difficulty: null,
    buyerPersona: null,
    projectName: null,
    buildingData: null,
    ozcData: null,
    description: null,
    discountCode: null,
    driveFolder: null,
    rodoConsent: false,
    rodoConsentAt: null,
    elderlyContactException: false,
    meetingKind: null,
    meetingAt: null,
    meetingOwnerId: null,
    meetingDurationMin: null,
    meetingUrl: null,
    auditAddressKind: null,
    auditAddress: null,
    auditMeetingAt: null,
    auditOwnerId: null,
    billingSameAsInstall: true,
    billingName: null,
    billingCompany: null,
    billingNip: null,
    billingAddress: null,
    qualReview: false,
    qualReviewAt: null,
    qualReviewReason: null,
    lostReason: null,
    lostReasonCategory: null,
    createdAt: daysAgo(30),
    updatedAt: daysAgo(1),
    ...overrides,
  };
  db.deals.push(deal);
  return deal;
}

function seed(db) {
  const cat = seedCategories(db);
  const users = seedUsers(db);
  const clients = seedClients(db);
  const [nowak, nowakDup, kowalska, wisniewski, wojcik, kaminski, agnieszka, kontrahent] = clients;

  const heat = cat['Ogrzewanie'];
  const pv = cat['Fotowoltaika'];
  const storage = cat['Magazyn energii'];
  const ac = cat['Klimatyzacja'];

  // ── Lejek ──────────────────────────────────────────────────────────────────
  // 1. Swiezy lead z leadowni (targi), czeka na decyzje auto-kwalifikacji.
  const dLead = makeDeal(db, {
    clientId: nowak.id,
    ownerId: users.koordynator.id,
    stage: 'lead',
    stageEnteredAt: daysAgo(2),
    source: 'targi',
    projectName: 'Nowak — dom jednorodzinny',
    description: 'Zgloszenie z targow w Katowicach, klient pyta o pompe ciepla + PV.',
    nextContactAt: daysAhead(1),
    qualReview: true,
    qualReviewAt: daysAgo(2),
    qualReviewReason: 'Brak adresu e-mail w zgloszeniu',
    createdAt: daysAgo(2),
    updatedAt: daysAgo(2),
  });

  // 2. Kwalifikacja — komplet danych, zaplanowane spotkanie wstepne.
  const dQual = makeDeal(db, {
    clientId: kowalska.id,
    ownerId: users.koordynator.id,
    stageOwnerId: users.koordynator.id,
    stage: 'qualifikacja',
    stageEnteredAt: daysAgo(5),
    source: 'strona www',
    projectName: 'Kowalska — modernizacja',
    buildingKind: 'modernizacja',
    segment: 'indywidualny',
    difficulty: 'normalny',
    buyerPersona: 'analityk',
    rodoConsent: true,
    rodoConsentAt: daysAgo(12),
    meetingKind: 'online',
    meetingAt: daysAhead(2),
    meetingDurationMin: 45,
    meetingOwnerId: users.koordynator.id,
    meetingUrl: 'https://meet.example/ekotak-kowalska',
    nextContactAt: daysAhead(2),
    createdAt: daysAgo(14),
    updatedAt: daysAgo(5),
  });

  // 3. Audyt — zalegly termin kontaktu (filtr "Zalegle" ma co pokazac).
  const dAudit = makeDeal(db, {
    clientId: wisniewski.id,
    ownerId: users.serwisant.id,
    stageOwnerId: users.serwisant.id,
    stage: 'audit',
    stageEnteredAt: daysAgo(9),
    source: 'polecenie',
    projectName: 'Wisniewski — pompa ciepla',
    difficulty: 'trudny',
    rodoConsent: true,
    rodoConsentAt: daysAgo(40),
    meetingKind: 'klient',
    meetingAt: daysAgo(7),
    meetingDurationMin: 60,
    auditAddressKind: 'instalacja',
    auditAddress: 'ul. Gorna 12, Bielsko-Biala',
    auditMeetingAt: daysAgo(2),
    auditOwnerId: users.serwisant.id,
    buildingData: {
      people: 4,
      areaM2: 168,
      floors: 2,
      shape: 'prostokat',
      construction: 'murowany',
      stage: 'zamieszkaly',
      windows: 'trzyszybowe',
      heatedBasement: false,
      heatedGarage: true,
    },
    ozcData: { buildingKw: 8.4, dhwKw: 1.2, sourceUrl: 'https://cieplo.app/raport/demo', confirmed: false },
    nextContactAt: daysAgo(3), // zaleglosc
    createdAt: daysAgo(45),
    updatedAt: daysAgo(3),
  });

  // 4. Oferta — komplet do sprzedazy poza potwierdzeniem OZC (blokada 422).
  const dOffer = makeDeal(db, {
    clientId: wojcik.id,
    ownerId: users.koordynator.id,
    stageOwnerId: users.koordynator.id,
    stage: 'angebot',
    stageEnteredAt: daysAgo(4),
    source: 'strona www',
    projectName: 'Wojcik — PV + magazyn',
    segment: 'indywidualny',
    buyerPersona: 'premium',
    rodoConsent: true,
    rodoConsentAt: daysAgo(60),
    meetingKind: 'biuro',
    meetingAt: daysAgo(20),
    auditAddressKind: 'instalacja',
    auditAddress: 'ul. Polna 8, Tychy',
    auditMeetingAt: daysAgo(10),
    auditOwnerId: users.serwisant.id,
    buildingData: {
      people: 5,
      areaM2: 210,
      floors: 2,
      shape: 'litera L',
      construction: 'murowany',
      stage: 'w budowie',
      windows: 'dwuszybowe',
      heatedBasement: true,
      heatedGarage: false,
    },
    ozcData: { buildingKw: 11.2, dhwKw: 1.6, sourceUrl: 'https://cieplo.app/raport/demo2', confirmed: false },
    nextContactAt: daysAhead(3),
    driveFolder: 'https://drive.example/ekotak/wojcik',
    createdAt: daysAgo(70),
    updatedAt: daysAgo(4),
  });

  // 5. Etap montazowy — deal firmowy z danymi do faktury.
  const dSold = makeDeal(db, {
    clientId: kontrahent.id,
    ownerId: users.admin.id,
    stageOwnerId: users.serwisant.id,
    stage: 'przed_montazem',
    stageEnteredAt: daysAgo(6),
    source: 'polecenie',
    projectName: 'Instal Serwis — klimatyzacja biura',
    segment: 'b2b',
    buildingKind: 'modernizacja',
    difficulty: 'latwy',
    rodoConsent: true,
    rodoConsentAt: daysAgo(90),
    billingSameAsInstall: false,
    billingCompany: 'Instal Serwis Sp. z o.o.',
    billingNip: '5472183920',
    billingAddress: 'ul. Przemyslowa 40, 43-300 Bielsko-Biala',
    ozcData: { buildingKw: 24.0, dhwKw: 0, sourceUrl: null, confirmed: true },
    nextContactAt: daysAhead(5),
    createdAt: daysAgo(150),
    updatedAt: daysAgo(6),
  });

  // 6. Po montazu — karta domknieta, zostaje serwis.
  const dDone = makeDeal(db, {
    clientId: agnieszka.id,
    ownerId: users.serwisant.id,
    stage: 'fertig',
    stageEnteredAt: daysAgo(25),
    source: 'targi',
    projectName: 'Nowak A. — pompa ciepla',
    rodoConsent: true,
    rodoConsentAt: daysAgo(200),
    ozcData: { buildingKw: 7.1, dhwKw: 1.0, sourceUrl: null, confirmed: true },
    createdAt: daysAgo(210),
    updatedAt: daysAgo(25),
  });

  // 7. Stracone — archiwum, powod z zestawu leadowego.
  const dLost = makeDeal(db, {
    clientId: kaminski.id,
    ownerId: users.koordynator.id,
    stage: 'lost',
    stageEnteredAt: daysAgo(18),
    source: 'telefon',
    projectName: 'Kaminski — zapytanie',
    lostReasonCategory: 'odleglosc',
    lostReason: 'Inwestycja poza obszarem dzialania.',
    createdAt: daysAgo(35),
    updatedAt: daysAgo(18),
  });

  // 8. Drugi deal tego samego klienta — kartoteka pokazuje wtedy "2 deale".
  const dSecond = makeDeal(db, {
    clientId: kowalska.id,
    ownerId: users.serwisant.id,
    stage: 'edukacja',
    stageEnteredAt: daysAgo(11),
    source: 'strona www',
    projectName: 'Kowalska — klimatyzacja',
    rodoConsent: true,
    rodoConsentAt: daysAgo(12),
    nextContactAt: daysAhead(9),
    createdAt: daysAgo(20),
    updatedAt: daysAgo(11),
  });

  // ── Wartosci ofert (badge kwoty na karcie klienta) ─────────────────────────
  Object.assign(db.dealValues, {
    [dOffer.id]: 128400.0,
    [dSold.id]: 46990.5,
    [dDone.id]: 71250.0,
    [dAudit.id]: 89900.0,
  });

  // ── Kontakty towarzyszace ("deal wspolny") ─────────────────────────────────
  db.dealContacts.push({ dealId: dQual.id, clientId: agnieszka.id });
  db.dealContacts.push({ dealId: dSold.id, clientId: wojcik.id });

  // ── Instalacje per etap (z dziedziczeniem licznym po stronie API) ──────────
  db.dealInstallations[dLead.id] = { lead: [heat.children['Pompa ciepla'], pv.id] };
  db.dealInstallations[dQual.id] = { lead: [heat.children['Pompa ciepla']] };
  db.dealInstallations[dAudit.id] = {
    lead: [heat.children['Pompa ciepla']],
    audit: [heat.children['Pompa ciepla'], pv.children['Instalacja on-grid']],
  };
  db.dealInstallations[dOffer.id] = {
    lead: [pv.children['Instalacja on-grid']],
    audit: [pv.children['Instalacja on-grid'], storage.children['Magazyn LFP']],
    angebot: [pv.children['Instalacja on-grid'], storage.children['Magazyn LFP']],
  };
  db.dealInstallations[dSold.id] = { lead: [ac.children['Multi-split']], sold: [ac.children['Multi-split']] };
  db.dealInstallations[dDone.id] = { lead: [heat.children['Pompa ciepla']] };
  db.dealInstallations[dSecond.id] = { lead: [ac.children['Split']] };

  // ── Zgloszenia z leadowni (zakladka LEAD) ──────────────────────────────────
  db.leads.push({
    dealId: dLead.id,
    channel: 'targi',
    source: 'targi-katowice-2026',
    sourceLabel: 'Targi Katowice 2026',
    fullName: 'Marek Nowak',
    phone: nowak.phone,
    email: null,
    city: 'Katowice',
    interest: 'Pompa ciepla + fotowoltaika',
    budget: '80-120 tys. zl',
    message: 'Stoisko nr 14, klient prosi o kontakt po 16:00.',
    note: 'Stoisko nr 14, klient prosi o kontakt po 16:00.',
    consent: true,
    submittedBy: 'Anna Admin',
    createdAt: daysAgo(2),
    building: {
      shape: 'prostokat',
      construction: 'murowany',
      area: '150-200 m2',
      people: '4 osoby',
      floors: 2,
      stage: 'w budowie',
      windows: 'trzyszybowe',
      heatedBasement: false,
      heatedGarage: true,
    },
  });
  db.leads.push({
    dealId: dQual.id,
    channel: 'www',
    source: 'cennikinstalacji.pl',
    sourceLabel: 'cennikinstalacji.pl',
    fullName: 'Ewa Kowalska',
    phone: kowalska.phone,
    email: kowalska.email,
    city: 'Gliwice',
    interest: 'Wymiana kotla na pompe ciepla',
    budget: 'do 80 tys. zl',
    message: 'Formularz /targi, dom z 1998 r., ogrzewanie podlogowe na parterze.',
    note: 'Dom z 1998 r., podlogowka na parterze, grzejniki na pietrze.',
    consent: true,
    submittedBy: null,
    createdAt: daysAgo(14),
    building: {
      shape: 'prostokat',
      construction: 'murowany',
      area: '100-150 m2',
      people: '3 osoby',
      floors: 2,
      stage: 'zamieszkaly',
      windows: 'dwuszybowe',
      heatedBasement: true,
      heatedGarage: false,
    },
  });
  db.leads.push({
    dealId: dLost.id,
    channel: 'tel',
    source: 'infolinia',
    sourceLabel: 'Infolinia',
    fullName: 'Tomasz Kaminski',
    phone: kaminski.phone,
    email: null,
    city: 'Sosnowiec',
    interest: 'Fotowoltaika',
    budget: null,
    message: null,
    note: 'Rozmowa telefoniczna: klient poza obszarem dojazdu, przekazany partnerowi.',
    consent: false,
    submittedBy: 'Piotr Koordynator',
    createdAt: daysAgo(35),
    building: null,
  });
  // dAudit, dOffer, dSold, dDone, dSecond celowo BEZ zgloszenia — zakladka LEAD
  // musi umiec pokazac komunikat "deal spoza leadowni" (API oddaje puste cialo).

  // ── Historia zmian ─────────────────────────────────────────────────────────
  const activity = (deal, action, userId, createdAt, diff) =>
    db.activities.push({
      id: uuid(),
      organizationId: db.organization.id,
      dealId: deal.id,
      action,
      userId,
      createdAt,
      diff: diff || null,
    });

  activity(dLead, 'deal_created', users.koordynator.id, daysAgo(2), { source: 'targi' });
  activity(dQual, 'deal_created', users.koordynator.id, daysAgo(14), { source: 'www' });
  activity(dQual, 'stage_change', users.koordynator.id, daysAgo(5), { from: 'lead', to: 'qualifikacja' });
  activity(dAudit, 'stage_change', users.serwisant.id, daysAgo(20), { from: 'lead', to: 'qualifikacja' });
  activity(dAudit, 'stage_change', users.serwisant.id, daysAgo(9), { from: 'qualifikacja', to: 'audit' });
  activity(dOffer, 'stage_change', users.koordynator.id, daysAgo(4), { from: 'audit', to: 'angebot' });
  activity(dSold, 'stage_change', users.admin.id, daysAgo(6), { from: 'sold', to: 'przed_montazem' });
  activity(dLost, 'stage_change', users.koordynator.id, daysAgo(18), {
    from: 'lead',
    to: 'lost',
    lostReasonCategory: 'odleglosc',
    lostReason: 'Inwestycja poza obszarem dzialania.',
  });

  // ── Kolejka polaczen i notatki (ekrany "Zgloszenia" / "Historia") ──────────
  const call = (user, phone, direction, startedAt, durationSec, clientId) => {
    const row = {
      id: uuid(),
      organizationId: db.organization.id,
      userId: user.id,
      clientId: clientId || null,
      phoneNumber: phone,
      direction,
      simSlot: 1,
      startedAt,
      endedAt: new Date(new Date(startedAt).getTime() + (durationSec || 0) * 1000).toISOString(),
      durationSec: durationSec || 0,
      createdAt: startedAt,
    };
    db.callLogs.push(row);
    return row;
  };

  const missedNowak = call(users.serwisant, nowak.phone, 'missed', daysAgo(0.08), 0, nowak.id);
  call(users.serwisant, nowak.phone, 'missed', daysAgo(0.05), 0, nowak.id); // ten sam numer -> badge x2
  call(users.serwisant, kowalska.phone, 'missed', daysAgo(0.4), 0, kowalska.id);
  const doneCall = call(users.serwisant, wisniewski.phone, 'outbound', daysAgo(1.2), 412, wisniewski.id);
  call(users.koordynator, wojcik.phone, 'inbound', daysAgo(2.3), 168, wojcik.id);

  db.voiceReports.push({
    id: uuid(),
    organizationId: db.organization.id,
    userId: users.serwisant.id,
    callLogId: doneCall.id,
    clientId: wisniewski.id,
    text: 'Klient prosi o przesuniecie audytu na przyszly tydzien.',
    transcript: 'Klient prosi o przesuniecie audytu na przyszly tydzien, najlepiej wtorek rano.',
    recordingKey: null,
    durationSec: 24,
    createdAt: daysAgo(1.2),
    updatedAt: daysAgo(1.2),
  });

  // ── Projekty (krok "kogo dotyczy" w kreatorze, zrodlo zadan bez klienta) ───
  const project = (name, color, opts = {}) => {
    const row = {
      id: uuid(),
      organizationId: db.organization.id,
      name,
      status: opts.status || 'active',
      color,
      isTemplate: Boolean(opts.isTemplate),
      createdAt: daysAgo(30),
    };
    db.projects.push(row);
    return row;
  };
  const pMontaze = project('Montaze wrzesien', '#44D62C');
  project('Audyty energetyczne 2026', '#38BDF8');
  project('Szablon: uruchomienie instalacji', '#C084FC', { isTemplate: true }); // ma NIE wracac z GET /projects
  project('Targi Enex 2026', '#F778BA', { status: 'archived' });

  // ── Zadania zespolu ────────────────────────────────────────────────────────
  // Zestaw dobrany pod moduly listy: kazda sekcja, oba progi SLA, jedno zaległe,
  // jedno zamkniete, jedno bez wykonawcy i jedno spiete z projektem zamiast klienta.
  const task = (o) => {
    const row = {
      id: uuid(),
      organizationId: db.organization.id,
      dealId: o.dealId || null,
      projectId: o.projectId || null,
      title: o.title,
      description: o.description || null,
      assigneeId: o.assignee ? o.assignee.id : null,
      assigneeEmail: o.assignee ? o.assignee.email : null,
      dueAt: o.dueAt || null,
      status: o.status || 'open',
      priority: o.priority || 'normal',
      section: o.section || null,
      estimatedMinutes: o.estimatedMinutes || null,
      slaHours: o.slaHours || null,
      commentCount: o.commentCount || 0,
      createdBy: (o.createdBy || users.koordynator).id,
      createdAt: o.createdAt || daysAgo(1),
      updatedAt: o.createdAt || daysAgo(1),
    };
    db.tasks.push(row);
    return row;
  };

  task({
    dealId: dAudit.id,
    title: 'Umowic audyt u p. Wisniewskiego',
    description: 'Termin przesuniety na wniosek klienta.',
    assignee: users.serwisant,
    dueAt: daysAhead(2),
    priority: 'high',
    section: 'audyt',
    slaHours: 168,
    estimatedMinutes: 30,
  });
  const tOffer = task({
    dealId: dOffer.id,
    title: 'Wyslac oferte po kalkulacji 10 kW',
    description: 'Klient chce wariant z magazynem i bez.',
    assignee: users.serwisant,
    dueAt: daysAhead(0.2),
    priority: 'high',
    section: 'oferta',
    slaHours: 24,
    estimatedMinutes: 45,
    commentCount: 2,
    createdAt: daysAgo(0.9), // SLA 24 h juz na ostatniej prostej
  });
  task({
    dealId: dOffer.id,
    title: 'Doslac rzut dachu do kalkulacji',
    assignee: users.serwisant,
    dueAt: daysAhead(1),
    section: 'oferta',
    slaHours: 168,
  });
  task({
    projectId: pMontaze.id,
    title: 'Zamowic inwerter Fronius 10 kW',
    description: 'Dostawa na magazyn przed 12 wrzesnia.',
    assignee: users.koordynator,
    dueAt: daysAgo(2), // zalegle — filtr "Zalegle" ma co pokazac
    priority: 'high',
    section: 'przed_montazem',
    slaHours: 24,
    createdAt: daysAgo(3),
  });
  const tCrew = task({
    dealId: dSold.id,
    title: 'Potwierdzic termin ekipy z klientem',
    assignee: users.koordynator,
    dueAt: daysAhead(4),
    section: 'przed_montazem',
  });
  task({
    dealId: dSold.id,
    title: 'Przygotowac liste materialu na montaz',
    // Bez wykonawcy — filtr "Nieprzypisane" ma co pokazac.
    dueAt: daysAhead(5),
    section: 'sprzedane',
    estimatedMinutes: 90,
  });
  task({
    dealId: dDone.id,
    title: 'Zgloszenie do Moj Prad',
    assignee: users.admin,
    status: 'done',
    section: 'dotacja',
    slaHours: 720,
    createdAt: daysAgo(6),
  });
  task({
    dealId: dDone.id,
    title: 'Wystawic fakture koncowa',
    assignee: users.admin,
    status: 'in_progress',
    dueAt: daysAhead(3),
    section: 'po_montazu',
    createdBy: users.serwisant,
  });

  // ── Komentarze i wywolania (@) ─────────────────────────────────────────────
  // Dyskusja w Komunikatorze = watek komentarzy zadania. Zestaw dobrany tak, by
  // `serwisant@ekotak.pl` mial po zalogowaniu jedno wywolanie nieprzeczytane
  // (wywolal go koordynator) i jeden watek, w ktorym sam pisal.
  const comment = (task, author, body, mentioned = [], createdAt = null) => {
    const row = {
      id: uuid(),
      organizationId: db.organization.id,
      taskId: task.id,
      authorId: author.id,
      body,
      createdAt: createdAt || nowIso(),
    };
    db.taskComments.push(row);
    for (const user of mentioned) {
      db.taskCommentMentions.push({
        id: uuid(),
        organizationId: db.organization.id,
        taskId: task.id,
        commentId: row.id,
        userId: user.id,
        createdAt: row.createdAt,
      });
    }
    task.commentCount = db.taskComments.filter((c) => c.taskId === task.id).length;
    return row;
  };

  comment(
    tOffer,
    users.koordynator,
    '@Jan Serwisant klient dopytuje o wariant z magazynem — masz gotowa kalkulacje?',
    [users.serwisant],
    daysAgo(0.8),
  );
  comment(tOffer, users.serwisant, 'Kalkulacja gotowa, dosylam PDF jeszcze dzis.', [], daysAgo(0.6));
  comment(
    tCrew,
    users.serwisant,
    'Klient prosi o poniedzialek. @Piotr Koordynator potwierdzisz ekipe?',
    [users.koordynator],
    daysAgo(0.3),
  );

  return { users, clients, missedNowak };
}

module.exports = { seed, daysAgo, daysAhead, nowIso };
