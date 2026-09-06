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

  // Jedyna technologia z USTRUKTURYZOWANYM formularzem audytu (`auditForm`) —
  // bez niej zakladka „Audyt" w telefonie nie mialaby czego dziedziczyc.
  // Ksztalt szablonu = `formData` audytu ogrzewania podlogowego w board360:
  // odpowiedzi, ktore firma daje z gory, wpisane; reszta pusta dla audytora.
  const ufh = {
    id: uuid(),
    parentId: null,
    name: 'Ogrzewanie podlogowe',
    position: main.length,
    auditForm: {
      kind: 'underfloorHeating',
      pipeSystem: 'pert-evoh-16',
      roomControl: '',
      systemFilling: '',
      cooling: false,
      install: {
        wallChase: '',
        leadInRouting: '',
        // Wartosci pol wyboru sa CZESCIA KONTRAKTU (mapuja sie na pozycje
        // cennika), wiec ida doslownie tak, jak w `ufh-install-params.ts`
        // board360 — z polskimi znakami, mimo ze reszta seeda ich unika.
        subfloorJoints: 'niedopuszczalne',
        leadInByWodKan: false,
        manifoldByWodKan: false,
        designScope: 'projekt przez ekotak',
        leadInPipeMm: 25,
        pressureTest: 'próba szczelności powietrzem z protokołem',
        systemPlateM2: null,
        wasteRemoval: 'całkowite usunięcie odpadów przez ekotak',
        heatMedium: null,
        biocide: null,
        warrantyDocs: null,
      },
      floors: [],
    },
  };
  db.categories.push(ufh);
  ids['Ogrzewanie podlogowe'] = { id: ufh.id, children: {} };

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
    // Kontrahent, afiliant i „inne" — zakladki kategorii w kartotece maja co pokazac.
    ['Instal', 'Serwis Sp. z o.o.', '+48338123456', 'biuro@instal-serwis.example', 'Bielsko-Biala',
      'ul. Przemyslowa 40', '43-300', null, null, 'obcy', 'kontrahent'],
    ['Rafal', 'Zielinski', '+48507890123', 'rafal@partner.example', 'Czechowice-Dziedzice',
      'ul. Legionow 2', '43-502', null, null, 'obcy', 'afiliant'],
    ['Urzad', 'Gminy Porabka', '+48338272800', 'sekretariat@porabka.example', 'Porabka',
      'ul. Krakowska 3', '43-353', null, null, 'obcy', 'inne'],
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
  const orgId = db.organization.id;
  const cat = seedCategories(db);
  const users = seedUsers(db);
  const clients = seedClients(db);
  const [nowak, nowakDup, kowalska, wisniewski, wojcik, kaminski, agnieszka, kontrahent] = clients;

  const heat = cat['Ogrzewanie'];
  const pv = cat['Fotowoltaika'];
  const storage = cat['Magazyn energii'];
  const ac = cat['Klimatyzacja'];
  const ufh = cat['Ogrzewanie podlogowe'];

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
  // Deal na etapie „Audyt" ma OP w migawce tego etapu — to jedyna technologia
  // z formularzem audytu, wiec bez niej zakladka „Audyt" w telefonie pokazywalaby
  // wylacznie komunikat „brak formularza w katalogu".
  db.dealInstallations[dAudit.id] = {
    lead: [heat.children['Pompa ciepla']],
    audit: [
      heat.children['Pompa ciepla'],
      pv.children['Instalacja on-grid'],
      ufh.id,
    ],
  };
  db.dealInstallations[dOffer.id] = {
    lead: [pv.children['Instalacja on-grid']],
    audit: [pv.children['Instalacja on-grid'], storage.children['Magazyn LFP']],
    angebot: [pv.children['Instalacja on-grid'], storage.children['Magazyn LFP']],
  };
  db.dealInstallations[dSold.id] = { lead: [ac.children['Multi-split']], sold: [ac.children['Multi-split']] };
  db.dealInstallations[dDone.id] = { lead: [heat.children['Pompa ciepla']] };
  db.dealInstallations[dSecond.id] = { lead: [ac.children['Split']] };

  // ── Audyty (zakladka „Audyt" karty deala) ─────────────────────────────────
  // Jeden wpis Heizlast i JEDEN formularz audytu instalacji, ktory udaje zapis
  // zrobiony wczesniej w panelu: niesie warstwe rzutu (kropki rozdzielaczy,
  // obrysy pomieszczen, kalibracje skali, historie i podpisy). Telefon tych pol
  // NIE edytuje i ma je oddac nietkniete — to jest scenariusz do sprawdzenia
  // na urzadzeniu (patrz TODO.md, sekcja „Audyt").
  db.audits.push({
    id: uuid(),
    organizationId: orgId,
    dealId: dAudit.id,
    heatloadMode: 'din',
    heatloadInputs: null,
    heatloadKw: 9.4,
    formData: { note: 'Heizlast z projektu branzowego' },
    createdAt: daysAgo(6),
    updatedAt: daysAgo(6),
  });
  db.audits.push({
    id: uuid(),
    organizationId: orgId,
    dealId: dAudit.id,
    heatloadMode: null,
    heatloadInputs: null,
    heatloadKw: null,
    formData: {
      kind: 'underfloorHeating',
      categoryId: ufh.id,
      pipeSystem: 'kan-therm-16',
      roomControl: 'nie (rekomendowane)',
      systemFilling: 'po zakończeniu instalacji ogrzewania podłogowego',
      cooling: true,
      install: {
        wallChase: 'nie',
        leadInRouting: 'położone w izolacji na chudziaku',
        subfloorJoints: 'niedopuszczalne',
        leadInByWodKan: false,
        manifoldByWodKan: false,
        designScope: 'projekt przez ekotak',
        leadInPipeMm: 25,
        pressureTest: 'próba szczelności powietrzem z protokołem',
        systemPlateM2: 96,
        wasteRemoval: 'całkowite usunięcie odpadów przez ekotak',
        heatMedium: 'woda demi',
        biocide: 'tak',
        warrantyDocs: 'tak',
      },
      floors: [
        {
          name: 'Parter',
          projectM2: 96,
          system: 'mokry — jastrych',
          comment: null,
          manifolds: 1,
          boxType: 'podtynkowa w ścianie działowej',
          m2_5: null,
          m2_10: 74,
          m2_15: null,
          m2_20: 8,
          noUfhM2: 6,
          leadInM2: 8,
          // ↓ warstwa rzutu — wylacznie panel ja tworzy i zmienia
          planSlot: 'parter',
          planDocId: 'doc-parter-1',
          manifoldMarks: [{ x: 0.412, y: 0.633, boxType: 'podtynkowa w ścianie działowej' }],
          heatSource: { x: 0.208, y: 0.741 },
          manifoldHistory: [
            { at: daysAgo(4), by: 'seed', byName: 'Piotr Koordynator', what: 'rozdzielacze' },
          ],
          rooms: [{ cat: 's10', name: 'Salon', points: [[0.1, 0.1], [0.5, 0.1], [0.5, 0.6]] }],
          planScale: { a: { x: 0.1, y: 0.9 }, b: { x: 0.6, y: 0.9 }, cm: 500, aspect: 1.41 },
          marksSavedAt: daysAgo(4),
          marksSavedBy: 'Piotr Koordynator',
          areaSavedAt: daysAgo(4),
          areaSavedBy: 'Piotr Koordynator',
        },
      ],
    },
    createdAt: daysAgo(4),
    updatedAt: daysAgo(4),
  });

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

  seedService(db, users, clients);
  seedCalendar(db, users);
  seedHr(db, users);
  seedSales(db, { dOffer, dSold, kontrahent, wojcik });

  return { users, clients, missedNowak };
}

/**
 * Sprzedaz i magazyn dla zakladki „Zamowienie" karty deala.
 *
 * Zestaw dobrany tak, zeby telefon mial co pokazac w KAZDYM stanie, w jakim
 * ta zakladka bywa:
 *  - `dOffer` (etap „Oferta") ma dwie oferty, w tym jedna WYGRANA i zadnego
 *    zamowienia — na nim sprawdza sie recznie zakladanie zamowienia,
 *  - `dSold` (etap montazowy) ma zamowienie z UMOWY, po jednym na instalacje,
 *    z pozycjami w trzech stanach (nieruszona / zamowiona / odebrana),
 *  - rezerwacja `dSold` niesie wszystkie cztery warianty wiersza: pokryty,
 *    z brakiem juz kupionym, z brakiem NIEkupionym (na nim dziala „ZAMOW braki")
 *    i bez kartoteki magazynu, plus jeden wiersz wydany (historia).
 */
function seedSales(db, { dOffer, dSold, kontrahent, wojcik }) {
  const orgId = db.organization.id;
  const label = (client, place) => `${client.lastName} ${client.firstName || ''}`.trim() +
    (place ? ` · ${place}` : '');

  const offerItems = [
    { name: 'Panel PV 450 W', quantity: 24, purchasePrice: 480, salePrice: 690 },
    { name: 'Falownik hybrydowy 10 kW', quantity: 1, purchasePrice: 8200, salePrice: 11900 },
    { name: 'Konstrukcja na blachodachowke', quantity: 24, purchasePrice: 95, salePrice: 150 },
  ].map((i) => ({
    id: uuid(),
    priceListItemId: null,
    ...i,
    margin: (i.salePrice - i.purchasePrice) * i.quantity,
  }));

  const netOf = (items) => items.reduce((s, i) => s + i.salePrice * i.quantity, 0);

  // Oferta odrzucona i wygrana — selektor „Wygrana oferta…" ma pokazac TYLKO te
  // druga, wiec obie musza istniec, zeby filtr dalo sie sprawdzic.
  db.offers.push({
    id: uuid(),
    organizationId: orgId,
    dealId: dOffer.id,
    number: 'OF/2026/041',
    status: 'lost',
    netTotal: 121500,
    grossTotal: 149445,
    margin: 24300,
    items: [],
    createdAt: daysAgo(30),
    updatedAt: daysAgo(20),
  });
  const wonOffer = {
    id: uuid(),
    organizationId: orgId,
    dealId: dOffer.id,
    number: 'OF/2026/052',
    status: 'won',
    netTotal: netOf(offerItems),
    grossTotal: Math.round(netOf(offerItems) * 1.23 * 100) / 100,
    margin: offerItems.reduce((s, i) => s + i.margin, 0),
    items: offerItems,
    createdAt: daysAgo(12),
    updatedAt: daysAgo(5),
  };
  db.offers.push(wonOffer);

  // Zamowienie z UMOWY — powstaje samo po podpisie, po jednym na instalacje.
  db.orders.push({
    id: uuid(),
    organizationId: orgId,
    dealId: dSold.id,
    supplierId: null,
    contractId: uuid(),
    installationId: null,
    installationName: 'Klimatyzacja multi-split',
    source: 'contract',
    createdAt: daysAgo(6),
    items: [
      { id: uuid(), name: 'Jednostka zewnetrzna multi 8 kW', quantity: 1, ordered: true, received: true },
      { id: uuid(), name: 'Jednostka wewnetrzna scienna 2,5 kW', quantity: 3, ordered: true, received: false },
      { id: uuid(), name: 'Rura miedziana 1/4 + 3/8 (zwoj 25 m)', quantity: 2, ordered: false, received: false },
      { id: uuid(), name: 'Uchwyt scienny pod jednostke', quantity: 1, ordered: false, received: false },
    ],
  });

  // ── Rezerwacja materialu deala `dSold` ──────────────────────────────────────
  const clientLabel = label(kontrahent, 'Bielsko-Biala');
  const productSplit = uuid();
  const productPipe = uuid();
  const productBracket = uuid();

  const reserve = (over) => {
    const row = {
      id: uuid(),
      organizationId: orgId,
      dealId: dSold.id,
      productId: null,
      itemName: '',
      itemCode: null,
      productName: null,
      clientLabel,
      quantity: 1,
      unit: 'szt',
      status: 'active',
      source: 'contract',
      neededBy: daysAhead(9),
      note: null,
      covered: 0,
      issuedById: null,
      createdAt: daysAgo(6),
      updatedAt: daysAgo(6),
      ...over,
    };
    db.reservations.push(row);
    return row;
  };

  // Pokryty w calosci — wiersz „● pokryte".
  reserve({
    productId: productSplit,
    itemName: 'Jednostka wewnetrzna scienna 2,5 kW',
    productName: 'Jednostka wewnetrzna scienna 2,5 kW',
    itemCode: 'AC-IN-25',
    quantity: 3,
    covered: 3,
  });
  // Brak, ktory KTOS JUZ KUPUJE — „ZAMOW braki" ma go pominac.
  const pipeRow = reserve({
    productId: productPipe,
    itemName: 'Rura miedziana 1/4 + 3/8 (zwoj 25 m)',
    productName: 'Rura miedziana 1/4 + 3/8',
    itemCode: 'CU-1438-25',
    quantity: 2,
    covered: 0,
    unit: 'zwoj',
  });
  // Brak NIEobjety zadnym zakupem — tylko on wchodzi do „ZAMOW braki".
  reserve({
    productId: productBracket,
    itemName: 'Uchwyt scienny pod jednostke',
    productName: 'Uchwyt scienny pod jednostke',
    itemCode: 'AC-BR-01',
    quantity: 4,
    covered: 1,
  });
  // Pozycja BEZ kartoteki magazynu — nie da sie jej ani zarezerwowac, ani kupic.
  reserve({
    itemName: 'Korytko maskujace 80 mm (dociac na miejscu)',
    quantity: 12,
    unit: 'mb',
    covered: 0,
    note: 'Do potwierdzenia obmiarem na budowie',
  });
  // Historia: linia wydana na budowe — widoczna tylko przy `status=all`.
  reserve({
    productId: productSplit,
    itemName: 'Jednostka zewnetrzna multi 8 kW',
    productName: 'Jednostka zewnetrzna multi 8 kW',
    itemCode: 'AC-OUT-80',
    quantity: 1,
    covered: 1,
    status: 'done',
  });

  // Zakup pod brak rury — wiersz rezerwacji ma pokazac „zamowione · ~data".
  db.purchaseOrders.push({
    id: uuid(),
    organizationId: orgId,
    productId: productPipe,
    dealId: dSold.id,
    reservationId: pipeRow.id,
    source: 'contract',
    quantity: 2,
    receivedQty: 0,
    status: 'ordered',
    distributor: 'https://hurtownia.example/oferta/cu-1438',
    unitPrice: 410,
    expectedAt: daysAhead(4),
    note: `Pod klienta: ${clientLabel}`,
    createdAt: daysAgo(3),
    updatedAt: daysAgo(2),
  });

  // Deal `dOffer` czeka na wlasne zamowienie — celowo bez rezerwacji, zeby dalo
  // sie sprawdzic pusty blok magazynu obok wypelnionego bloku zamowien.
  void wojcik;
}

/**
 * Kalendarze i wydarzenia biezacego tygodnia. Zestaw dobrany tak, zeby
 * telefon mial co pokazac w kazdym z czterech widokow i w kazdym stanie:
 *  - trzy warstwy: osobista (tylko wlasciciel), zespolowa (wszyscy pisza)
 *    i zasob (bus — na nim sprawdza sie kolizje 409),
 *  - seria „odprawa poranna" co tydzien (zakres this / following / all),
 *  - wydarzenie z uczestnikami i rozna odpowiedzia RSVP,
 *  - wydarzenie caloddniowe (urlop) i wielodniowy montaz.
 */
function seedCalendar(db, users) {
  const orgId = db.organization.id;

  const calendar = (fields) => {
    const row = {
      id: uuid(),
      organizationId: orgId,
      description: null,
      isArchived: false,
      shares: [],
      createdAt: nowIso(),
      ...fields,
    };
    db.calendars.push(row);
    return row;
  };

  const personal = calendar({
    name: 'Mój kalendarz',
    type: 'personal',
    color: '#2a78d6',
    ownerId: users.serwisant.id,
  });
  // Kalendarz osobisty koordynatora — serwisant widzi z niego tylko zajetosc,
  // wiec „Znajdz termin" ma na czym pokazac zajete pasy.
  const personalCoordinator = calendar({
    name: 'Kalendarz Piotra',
    type: 'personal',
    color: '#8a6df0',
    ownerId: users.koordynator.id,
    shares: [{ id: uuid(), principalType: 'everyone', principalId: null, level: 'freebusy' }],
  });
  const team = calendar({
    name: 'Montaże — zespół',
    type: 'team',
    color: '#1baf7a',
    ownerId: users.koordynator.id,
    shares: [{ id: uuid(), principalType: 'everyone', principalId: null, level: 'writer' }],
  });
  const readOnly = calendar({
    name: 'Serwis — dyżury',
    type: 'team',
    color: '#eb6834',
    ownerId: users.admin.id,
    shares: [{ id: uuid(), principalType: 'everyone', principalId: null, level: 'reader' }],
  });
  const resource = calendar({
    name: 'Bus Ducato RZ 4821K',
    type: 'resource',
    color: '#d55181',
    ownerId: users.koordynator.id,
    shares: [{ id: uuid(), principalType: 'everyone', principalId: null, level: 'writer' }],
  });

  /** Dzien wzgledem dzisiaj o podanej godzinie (czas lokalny serwera). */
  const at = (dayOffset, hour, minute = 0) => {
    const d = new Date();
    d.setDate(d.getDate() + dayOffset);
    d.setHours(hour, minute, 0, 0);
    return d.toISOString();
  };

  const event = (fields) => {
    const row = {
      id: uuid(),
      organizationId: orgId,
      description: null,
      location: null,
      color: null,
      endAt: null,
      allDay: false,
      assigneeId: null,
      attendees: [],
      recurrenceGroupId: null,
      recurrenceRule: null,
      createdBy: users.koordynator.id,
      createdAt: nowIso(),
      ...fields,
    };
    db.calendarEvents.push(row);
    return row;
  };

  // Seria: odprawa poranna w każdy dzień roboczy tego i przyszłego tygodnia.
  const briefingGroup = uuid();
  for (let day = -2; day <= 10; day += 1) {
    const weekday = new Date(Date.now() + day * DAY).getDay();
    if (weekday === 0 || weekday === 6) continue;
    event({
      calendarId: personal.id,
      title: 'Odprawa poranna',
      location: 'Sala duża',
      startAt: at(day, 8, 30),
      endAt: at(day, 9, 15),
      assigneeId: users.serwisant.id,
      attendees: [
        { id: users.serwisant.id, response: 'accepted' },
        { id: users.koordynator.id, response: 'needs_action' },
      ],
      recurrenceGroupId: briefingGroup,
      recurrenceRule: 'FREQ=WEEKLY;INTERVAL=1',
    });
  }

  event({
    calendarId: team.id,
    title: 'Montaż — Krosno, ul. Polna 4',
    location: 'Krosno, ul. Polna 4',
    startAt: at(0, 10, 0),
    endAt: at(0, 14, 0),
    assigneeId: users.serwisant.id,
    attendees: [
      { id: users.serwisant.id, response: 'accepted' },
      { id: users.admin.id, response: 'tentative' },
    ],
  });

  event({
    calendarId: team.id,
    title: 'Montaż Sanok (2 dni)',
    location: 'Sanok, ul. Lipowa 8',
    startAt: at(1, 0, 0),
    endAt: at(2, 23, 59),
    allDay: true,
    assigneeId: users.serwisant.id,
  });

  event({
    calendarId: personalCoordinator.id,
    title: 'Wycena — Kowalscy, Trzebownisko',
    startAt: at(1, 11, 0),
    endAt: at(1, 12, 0),
    assigneeId: users.koordynator.id,
    attendees: [{ id: users.koordynator.id, response: 'accepted' }],
  });

  event({
    calendarId: readOnly.id,
    title: 'Dyżur serwisowy — Jan',
    startAt: at(2, 7, 0),
    endAt: at(2, 19, 0),
    assigneeId: users.serwisant.id,
  });

  event({
    calendarId: personal.id,
    title: 'Urlop',
    startAt: at(5, 0, 0),
    endAt: at(6, 23, 59),
    allDay: true,
    color: '#79c0ff',
    assigneeId: users.serwisant.id,
  });

  // Rezerwacja zasobu — na niej sprawdza sie kolizja 409 przy drugim wpisie
  // w tych samych godzinach.
  event({
    calendarId: resource.id,
    title: 'Bus zajęty: wyjazd Sanok',
    startAt: at(1, 7, 0),
    endAt: at(1, 16, 0),
    assigneeId: users.serwisant.id,
  });

  // ── Prywatna zajetosc (podpiety kalendarz iCal) ─────────────────────────
  // Serwisant ma podpiety prywatny kalendarz — dzieki temu w aplikacji od razu
  // widac szare pola u KOGOS INNEGO niz zalogowany, a proba przypisania mu
  // zadania na 12:00 konczy sie kolizja 409 (`code: private_busy`). Tresci tych
  // wpisow nie ma nigdzie: przechowujemy wylacznie przedzialy czasu.
  const busy = (dayOffset, h1, m1, h2, m2) => {
    db.privateBusy.push({
      id: uuid(),
      organizationId: orgId,
      userId: users.serwisant.id,
      startAt: at(dayOffset, h1, m1),
      endAt: at(dayOffset, h2, m2),
    });
  };
  for (let d = -3; d <= 21; d += 1) {
    const weekday = new Date(Date.now() + d * DAY).getDay();
    if (weekday === 0 || weekday === 6) continue;
    busy(d, 12, 0, 13, 0); // przerwa poludniowa
    busy(d, 17, 30, 19, 0); // sprawy prywatne po godzinach
  }
  db.privateCalendarLinks.push({
    userId: users.serwisant.id,
    organizationId: orgId,
    urlHint: 'calendar.google.com/…/priv…/basic.ics',
    status: 'ok',
    lastError: null,
    lastSyncedAt: nowIso(),
    blockCount: db.privateBusy.length,
  });
}

/**
 * Modul Serwis: zlecenia obu dziedzin i karty gwarancyjne Panasonic.
 *
 * Dobrane tak, zeby na telefonie dalo sie przeklikac KAZDY stan z makiety
 * `design/mockups/modul-serwis.html`:
 *  - awaria po SLA, awaria z oknem konczacym sie za kilka godzin, awaria
 *    niedouzupelniona (bez klienta i terminu — czerwony wiersz) i wykonana,
 *  - przeglad zwykly i konserwacja w dziedzinie "Przeglad",
 *  - piec kart gwarancyjnych: po terminie, w toku, przepadla (> 12 miesiecy),
 *    zakonczona 5/5 i taka bez wspolrzednych (lista "bez lokalizacji" na mapie).
 */
function seedService(db, users, clients) {
  const orgId = db.organization.id;
  const [nowak, , kowalska, wisniewski, wojcik, kaminski] = clients;

  const job = (fields) => {
    const row = {
      id: uuid(),
      organizationId: orgId,
      clientId: null,
      dealId: null,
      type: 'awaria',
      status: 'new',
      priority: 'normal',
      technicianId: null,
      scheduledAt: null,
      note: null,
      slaHours: null,
      createdAt: nowIso(),
      updatedAt: nowIso(),
      ...fields,
    };
    db.serviceJobs.push(row);
    return row;
  };

  // Awaria po SLA — okno 24 h ruszylo trzy dni temu.
  job({
    clientId: nowak.id,
    technicianId: users.serwisant.id,
    status: 'in_progress',
    priority: 'high',
    note: 'Nie grzeje CWU',
    createdAt: daysAgo(3),
    scheduledAt: daysAgo(1),
  });
  // Awaria z oknem konczacym sie dzis — chip SLA na pomaranczowo.
  job({
    clientId: kowalska.id,
    technicianId: users.serwisant.id,
    note: 'Blad H76 na sterowniku',
    createdAt: daysAgo(0.85),
    scheduledAt: daysAhead(1),
  });
  // Zgloszenie "na szybko": bez klienta, bez terminu, bez serwisanta.
  job({ note: 'Cieknie zawor przy buforze', createdAt: daysAgo(0.4) });
  // Zamkniete — wiersz przekreslony na dole listy.
  job({
    clientId: wisniewski.id,
    technicianId: users.serwisant.id,
    status: 'done',
    note: 'Odpowietrzenie obiegu',
    createdAt: daysAgo(9),
    scheduledAt: daysAgo(8),
  });
  // Dziedzina "Przeglad" — zwykle zlecenia planowe.
  job({
    clientId: wojcik.id,
    type: 'przeglad',
    note: 'Przeglad roczny pompy ciepla',
    scheduledAt: daysAhead(12),
    createdAt: daysAgo(5),
  });
  job({
    clientId: kaminski.id,
    type: 'konserwacja',
    technicianId: users.serwisant.id,
    note: 'Konserwacja rekuperatora',
    scheduledAt: daysAhead(16),
    createdAt: daysAgo(4),
  });

  const card = (name, fields, inspections) => {
    const row = {
      id: uuid(),
      organizationId: orgId,
      brand: 'Panasonic',
      name,
      location: null,
      commissionedAt: null,
      status: 'oczekujace',
      outdoorModel: 'WH-MDC09J3E5',
      outdoorSerial: null,
      indoorModel: 'WH-SDC0309J3E5',
      indoorSerial: null,
      note: null,
      geo: null,
      createdAt: daysAgo(200),
      updatedAt: daysAgo(10),
      ...fields,
      inspections: inspections.map((i, idx) => ({
        id: uuid(),
        cardId: null,
        ordinal: idx + 1,
        plannedAt: i.planned || null,
        doneAt: i.done || null,
        price: i.price != null ? i.price : null,
        technicianId: i.technicianId || null,
        note: null,
      })),
    };
    row.inspections.forEach((i) => {
      i.cardId = row.id;
    });
    db.warrantyCards.push(row);
    return row;
  };

  const YEAR = 365;
  // 2/5 wykonane, trzeci po terminie — wiersz na czerwono, belka alarmowa.
  card(
    'Kowalski Jan',
    {
      location: 'Kielce, ul. Sandomierska 14',
      commissionedAt: daysAgo(4 * YEAR),
      status: 'umowione',
      outdoorSerial: '2201A00123',
      indoorSerial: '2201B00456',
      note: 'Klient prosi o kontakt po 16:00, pies na posesji.',
      geo: { lat: 50.87, lng: 20.63, city: 'Kielce' },
    },
    [
      { planned: daysAgo(3 * YEAR), done: daysAgo(3 * YEAR - 3), price: 0 },
      { planned: daysAgo(2 * YEAR), done: daysAgo(2 * YEAR - 8), price: 0 },
      { planned: daysAgo(Math.round(0.5 * YEAR)) },
      { planned: daysAhead(Math.round(0.5 * YEAR)), price: 450 },
      {},
    ],
  );
  // Swieza instalacja, jeden przeglad za soba — "W toku".
  card(
    'Wojcik Anna',
    {
      location: 'Chmielnik, ul. Polna 3',
      commissionedAt: daysAgo(Math.round(1.2 * YEAR)),
      status: 'oczekujace',
      outdoorSerial: '2404A00887',
      geo: { lat: 50.61, lng: 20.72, city: 'Chmielnik' },
    },
    [
      { planned: daysAgo(Math.round(0.2 * YEAR)), done: daysAgo(Math.round(0.18 * YEAR)), price: 0 },
      { planned: daysAhead(Math.round(0.8 * YEAR)) },
      {},
      {},
      {},
    ],
  );
  // Dwa terminy minely ponad rok temu — "Przepadl" (szare kropki).
  card(
    'Lewandowski Piotr',
    {
      location: 'Pinczow, ul. Nadrzeczna 8',
      commissionedAt: daysAgo(6 * YEAR),
      status: 'brak_kontaktu',
      geo: { lat: 50.52, lng: 20.53, city: 'Pinczow' },
    },
    [
      { planned: daysAgo(5 * YEAR), done: daysAgo(5 * YEAR - 5), price: 0 },
      { planned: daysAgo(4 * YEAR) },
      { planned: daysAgo(3 * YEAR) },
      {},
      {},
    ],
  );
  // Komplet 5/5 — wiersz "Zakonczony", zielona belka.
  card(
    'Sikora Dom',
    {
      location: 'Jedrzejow, ul. Klonowa 21',
      commissionedAt: daysAgo(7 * YEAR),
      status: 'wykonane',
      geo: { lat: 50.64, lng: 20.3, city: 'Jedrzejow' },
    },
    [1, 2, 3, 4, 5].map((n) => ({
      planned: daysAgo((7 - n) * YEAR),
      done: daysAgo((7 - n) * YEAR - 4),
      price: n === 5 ? 450 : 0,
    })),
  );
  // Bez wspolrzednych — trafia na liste "bez lokalizacji" pod mapa.
  card(
    'Malinowski Robert',
    {
      location: 'Morawica, dzialka bez numeru',
      commissionedAt: daysAgo(2 * YEAR),
      status: 'czekamy_na_kontakt',
    },
    [{ planned: daysAgo(YEAR), done: daysAgo(YEAR - 6), price: 0 }, { planned: daysAhead(20) }, {}, {}, {}],
  );
}

/**
 * Kadry i urlopy (modul Urlop).
 *
 * Seed jest napisany POD SCENARIUSZE z makiety `design/mockups/modul-urlop.html`,
 * a nie „dla objetosci":
 *  - koordynator (konto testowe) ma komplet stanow wlasnych wnioskow: miniony,
 *    zaplanowany, oczekujacy, odrzucony z notatka i anulowany,
 *  - jest zwierzchnikiem montazu i serwisu, wiec jego skrzynka ma co pokazac,
 *    MIMO ze nie ma `hr.manage` — to jest test nowej trasy `/hr/leave/inbox`,
 *  - Anna Wilk jest DZIS na urlopie i ma koordynatora jako backup decyzyjny,
 *    wiec jej podwladna trafia do jego skrzynki jako „backup",
 *  - Ewa Szot podlega Zarzadowi — jej wniosek widac, ale rozstrzyga go kto inny,
 *  - Tomasz Rak ma umowe „Wspolnik", czyli tryb BEZ wymiaru (6 dni bezplatnego:
 *    4 minione + 2 zaplanowane) — wariant ekranu bez puli i bez paska,
 *  - Kasia Duda nie ma wpisanego rodzaju umowy: pusty rodzaj zostaje przy
 *    wymiarze, tak jak w board360.
 */
function seedHr(db, users) {
  const orgId = db.organization.id;
  const { countWorkingDays } = require('./hr-domain');

  // Poniedzialek tygodnia oddalonego o `n` tygodni — dzieki temu zakresy nie
  // wypadaja na weekend niezaleznie od dnia, w ktorym atrapa wstaje.
  const mondayIn = (n) => {
    const now = new Date();
    const base = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
    const shift = (base.getUTCDay() + 6) % 7; // 0 = poniedzialek
    base.setUTCDate(base.getUTCDate() - shift + n * 7);
    return base;
  };
  const plusDays = (d, n) =>
    new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() + n));
  const iso = (d) => d.toISOString();
  const todayUtc = () => {
    const n = new Date();
    return new Date(Date.UTC(n.getUTCFullYear(), n.getUTCMonth(), n.getUTCDate()));
  };

  // ── Ludzie ────────────────────────────────────────────────────────────────
  // Pracownicy bez konta nie mieliby sensu (mobilka pobiera liste czlonkow
  // zespolu tym samym endpointem co kreator zadania), wiec zakladamy pelnych
  // uzytkownikow z haslem — wystarczy przelogowac sie w aplikacji, zeby
  // zobaczyc inny wariant ekranu.
  const worker = (email, role, firstName, lastName, opts = {}) => {
    const user = {
      id: uuid(),
      organizationId: orgId,
      email,
      passwordHash: hashPassword(opts.password || 'test1234'),
      role,
      firstName,
      lastName,
      clientVisibility: 'all',
      functions: opts.functions || [],
      additionalRoles: opts.additionalRoles || [],
    };
    db.users.push(user);
    return user;
  };

  const annaWilk = worker('anna.wilk@ekotak.pl', 'biuro', 'Anna', 'Wilk', { functions: ['kadry'] });
  const marek = worker('marek.kubiak@ekotak.pl', 'montaz', 'Marek', 'Kubiak', { functions: ['montaz'] });
  const kasia = worker('kasia.duda@ekotak.pl', 'montaz', 'Kasia', 'Duda', { functions: ['montaz'] });
  const grzegorz = worker('grzegorz.lis@ekotak.pl', 'serwisant', 'Grzegorz', 'Lis', { functions: ['serwis'] });
  const ola = worker('ola.zajac@ekotak.pl', 'biuro', 'Ola', 'Zajac');
  const ewa = worker('ewa.szot@ekotak.pl', 'biuro', 'Ewa', 'Szot');
  const tomasz = worker('tomasz.rak@ekotak.pl', 'zarzad', 'Tomasz', 'Rak');

  // ── Kartoteki kadrowe ─────────────────────────────────────────────────────
  const profile = (user, opts = {}) => {
    const row = {
      id: uuid(),
      organizationId: orgId,
      userId: user.id,
      managerId: opts.managerId === undefined ? null : opts.managerId,
      backupDecisionId: opts.backupDecisionId === undefined ? null : opts.backupDecisionId,
      annualLeaveDays: opts.annualLeaveDays === undefined ? 26 : opts.annualLeaveDays,
      onDemandDays: opts.onDemandDays === undefined ? 4 : opts.onDemandDays,
      carriedOverDays: opts.carriedOverDays === undefined ? 0 : opts.carriedOverDays,
      unpaidLeaveDays: opts.unpaidLeaveDays === undefined ? 0 : opts.unpaidLeaveDays,
      // Brak klucza znaczy „nie podano" i wpada w domyslna umowe o prace;
      // `null` przekazany WPROST zostawia rodzaj pusty (test trybu domyslnego).
      employmentType: 'employmentType' in opts ? opts.employmentType : 'Umowa o pracę',
      employmentStart: opts.employmentStart || daysAgo(900),
      position: opts.position || null,
      createdAt: nowIso(),
      updatedAt: nowIso(),
    };
    db.hrProfiles.push(row);
    return row;
  };

  profile(users.admin, { position: 'Zarzad' });
  // 26 + 4 przeniesione = 30 dni wymiaru — liczby jak na makiecie.
  profile(users.koordynator, {
    managerId: users.admin.id,
    carriedOverDays: 4,
    position: 'Koordynator montazu',
  });
  profile(users.serwisant, {
    managerId: users.koordynator.id,
    employmentType: 'Umowa o pracę (na okres próbny)',
    position: 'Serwisant',
  });
  profile(annaWilk, {
    managerId: users.admin.id,
    // Gdy Anna jest na urlopie, jej decyzje przejmuje koordynator.
    backupDecisionId: users.koordynator.id,
    position: 'Kadry',
  });
  profile(marek, { managerId: users.koordynator.id, position: 'Monter' });
  // Bez wpisanego rodzaju umowy — kartoteka zalozona automatycznie z konta.
  profile(kasia, { managerId: users.koordynator.id, employmentType: null, position: 'Monter' });
  profile(grzegorz, { managerId: users.koordynator.id, position: 'Serwisant' });
  profile(ola, { managerId: annaWilk.id, position: 'Biuro' });
  profile(ewa, { managerId: users.admin.id, position: 'Biuro' });
  // Wspolnik — bez wymiaru urlopu, same dni bezplatnego.
  profile(tomasz, { employmentType: 'Wspólnik', position: 'Zarzad' });

  // ── Wnioski ───────────────────────────────────────────────────────────────
  const decided = (status) => status === 'zatwierdzony' || status === 'odrzucony';
  const leave = (user, type, start, end, status, opts = {}) => {
    const row = {
      id: uuid(),
      organizationId: orgId,
      userId: user.id,
      type,
      startDate: iso(start),
      endDate: iso(end),
      workingDays: countWorkingDays(start, end),
      status,
      reason: opts.reason || null,
      decidedById: decided(status) ? opts.decidedById || users.admin.id : null,
      decidedAt: decided(status) ? daysAgo(opts.decidedDaysAgo === undefined ? 5 : opts.decidedDaysAgo) : null,
      decisionNote: opts.decisionNote || null,
      createdAt: daysAgo(opts.createdDaysAgo === undefined ? 20 : opts.createdDaysAgo),
      updatedAt: nowIso(),
    };
    db.leaveRequests.push(row);
    return row;
  };

  // Koordynator — komplet stanow na ekranie „Moje wnioski".
  leave(users.koordynator, 'wypoczynkowy', mondayIn(-26), plusDays(mondayIn(-26), 15), 'zatwierdzony', {
    createdDaysAgo: 200, decidedDaysAgo: 190,
  });
  leave(users.koordynator, 'wypoczynkowy', mondayIn(2), plusDays(mondayIn(2), 4), 'zatwierdzony', {
    createdDaysAgo: 12, decidedDaysAgo: 10,
  });
  leave(users.koordynator, 'na_zadanie', plusDays(mondayIn(1), 1), plusDays(mondayIn(1), 1), 'oczekuje', {
    createdDaysAgo: 1,
  });
  leave(users.koordynator, 'wypoczynkowy', mondayIn(-13), plusDays(mondayIn(-13), 11), 'odrzucony', {
    createdDaysAgo: 100,
    decidedDaysAgo: 96,
    decisionNote: 'Kolizja z montazem w Opolu — prosze o inny termin.',
  });
  leave(users.koordynator, 'bezplatny', mondayIn(-30), plusDays(mondayIn(-30), 1), 'anulowany', {
    createdDaysAgo: 230,
  });

  // Skrzynka koordynatora: dwoje monterow i serwisant.
  leave(marek, 'na_zadanie', plusDays(mondayIn(1), 1), plusDays(mondayIn(1), 1), 'oczekuje', { createdDaysAgo: 1 });
  leave(marek, 'wypoczynkowy', mondayIn(2), plusDays(mondayIn(2), 11), 'zatwierdzony', {
    createdDaysAgo: 30, decidedDaysAgo: 28, decidedById: users.koordynator.id,
  });
  leave(kasia, 'wypoczynkowy', mondayIn(2), plusDays(mondayIn(2), 4), 'zatwierdzony', {
    createdDaysAgo: 25, decidedDaysAgo: 24, decidedById: users.koordynator.id,
  });
  leave(users.serwisant, 'wypoczynkowy', mondayIn(3), plusDays(mondayIn(3), 11), 'oczekuje', { createdDaysAgo: 2 });
  leave(grzegorz, 'wypoczynkowy', mondayIn(5), plusDays(mondayIn(5), 1), 'zatwierdzony', {
    createdDaysAgo: 15, decidedDaysAgo: 14, decidedById: users.koordynator.id,
  });

  // Anna Wilk jest DZIS na urlopie — stad jej decyzje przejmuje backup.
  leave(annaWilk, 'wypoczynkowy', plusDays(todayUtc(), -2), plusDays(todayUtc(), 2), 'zatwierdzony', {
    createdDaysAgo: 20, decidedDaysAgo: 18,
  });
  // Podwladna Anny — do skrzynki koordynatora wpada jako „backup".
  leave(ola, 'bezplatny', mondayIn(4), plusDays(mondayIn(4), 1), 'oczekuje', { createdDaysAgo: 1 });
  // Ewa podlega Zarzadowi — koordynator jej wniosku NIE rozstrzyga.
  leave(ewa, 'okolicznosciowy', plusDays(mondayIn(3), 2), plusDays(mondayIn(3), 2), 'oczekuje', { createdDaysAgo: 3 });

  // Wspolnik: 4 dni minione + 2 zaplanowane = 6 dni bezplatnego, bez limitu.
  leave(tomasz, 'bezplatny', mondayIn(-4), plusDays(mondayIn(-4), 3), 'zatwierdzony', {
    createdDaysAgo: 40, decidedDaysAgo: 38,
  });
  leave(tomasz, 'bezplatny', mondayIn(6), plusDays(mondayIn(6), 1), 'zatwierdzony', {
    createdDaysAgo: 5, decidedDaysAgo: 4,
  });

  return { annaWilk, marek, kasia, grzegorz, ola, ewa, tomasz };
}

module.exports = { seed, daysAgo, daysAhead, nowIso };
