'use strict';
/*
 * Maszyna stanow lejka i blokady walidacyjne.
 *
 * Przejscia sa kopia `ALLOWED_TRANSITIONS` z aplikacji mobilnej
 * (`domain/model/Deal.kt`), zeby mock nie odrzucal przyciskow, ktore appka
 * pokazuje. Blokady (`missing[]`) to PRZYBLIZENIE board360 — dobrane tak, zeby
 * dalo sie przecwiczyc komunikat 422 na telefonie. Etykiety w `missing` ida
 * wprost na ekran (CrmError.kt sklada je w "Uzupelnij w panelu: ..."), wiec sa
 * po polsku i czytelne dla handlowca.
 */

const STAGES = [
  'lead',
  'qualifikacja',
  'edukacja',
  'audit',
  'angebot',
  'on_hold',
  'sold',
  'przed_montazem',
  'oczekiwanie_na_montaz',
  'montaz',
  'fertig',
  'lost',
  'zakonczony',
];

const STAGE_LABEL = {
  lead: 'Lead',
  qualifikacja: 'Kwalifikacja',
  edukacja: 'Remarketing',
  audit: 'Audyt',
  angebot: 'Oferta',
  on_hold: 'Wstrzymane',
  sold: 'Sprzedane',
  przed_montazem: 'Przed montazem',
  oczekiwanie_na_montaz: 'Oczekiwanie',
  montaz: 'Montaz',
  fertig: 'Po montazu',
  lost: 'Stracone',
  zakonczony: 'Zakonczony',
};

const ALLOWED_TRANSITIONS = {
  lead: ['qualifikacja', 'lost'],
  qualifikacja: ['edukacja', 'audit', 'lost'],
  edukacja: ['audit', 'lost'],
  audit: ['angebot', 'lost'],
  angebot: ['sold', 'on_hold', 'lost'],
  on_hold: ['angebot', 'lost'],
  sold: ['przed_montazem', 'lost'],
  przed_montazem: ['oczekiwanie_na_montaz', 'lost'],
  oczekiwanie_na_montaz: ['montaz', 'lost'],
  montaz: ['fertig', 'lost'],
  fertig: ['zakonczony'],
  lost: ['lead'],
  zakonczony: ['fertig'],
};

/** Kategorie powodow utraty — zestaw zalezy od etapu, na ktorym tracimy deal. */
const LEAD_LOST_REASONS = ['odleglosc', 'wlasny_material', 'harmonogram', 'brak_odzewu', 'niepelne_dane'];
const LOST_REASONS = ['cena', 'konkurencja', 'rezygnacja', 'brak_kontaktu', 'termin', 'inne'];
const lostReasonsForStage = (stage) => (stage === 'lead' ? LEAD_LOST_REASONS : LOST_REASONS);

/**
 * Wymagania wejscia w etap. Kazde to para: etykieta pokazywana uzytkownikowi
 * i predykat na parze (deal, klient glowny).
 */
const GATES = {
  qualifikacja: [
    ['telefon klienta', (d, c) => Boolean(c && (c.phone || c.phone2))],
    // Wyjatek "osoba starsza" zdejmuje wymog e-maila (FR z board360).
    ['adres e-mail klienta', (d, c) => d.elderlyContactException || Boolean(c && (c.email || c.email2))],
    ['zgoda RODO', (d) => d.rodoConsent === true],
  ],
  audit: [
    ['termin spotkania wstepnego', (d) => Boolean(d.meetingAt)],
    ['miejsce spotkania wstepnego', (d) => Boolean(d.meetingKind)],
  ],
  angebot: [
    ['termin audytu', (d) => Boolean(d.auditMeetingAt)],
    ['powierzchnia budynku', (d) => Number.isFinite(Number(d.buildingData && d.buildingData.areaM2))],
    ['moc budynku (OZC)', (d) => Number.isFinite(Number(d.ozcData && d.ozcData.buildingKw))],
  ],
  sold: [
    ['potwierdzenie OZC przez audytora', (d) => Boolean(d.ozcData && d.ozcData.confirmed)],
    [
      'dane do faktury',
      (d) => d.billingSameAsInstall === true || Boolean(d.billingName || d.billingCompany),
    ],
  ],
  przed_montazem: [['opiekun etapu', (d) => Boolean(d.stageOwnerId)]],
};

/** Lista brakow blokujacych wejscie w `target`; pusta = przejscie dozwolone. */
function missingFor(target, deal, client) {
  return (GATES[target] || []).filter(([, ok]) => !ok(deal, client)).map(([label]) => label);
}

const canTransition = (from, to) => (ALLOWED_TRANSITIONS[from] || []).includes(to);

module.exports = {
  STAGES,
  STAGE_LABEL,
  ALLOWED_TRANSITIONS,
  LOST_REASONS,
  LEAD_LOST_REASONS,
  lostReasonsForStage,
  missingFor,
  canTransition,
};
