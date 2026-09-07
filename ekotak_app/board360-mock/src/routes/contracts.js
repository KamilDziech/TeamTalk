'use strict';
/*
 * Umowy deala (zakladka „Umowa" karty deala w TeamTalku).
 *
 * Ksztalt rekordow i regul = `api/src/modules/contracts` z board360:
 *  - link do podpisu zyje 48 h (`WAZNOSC_LINKU_H`); po tym czasie umowa sama
 *    przechodzi w `expired` PRZY ODCZYCIE (board360 domyka ja leniwie),
 *  - zmiana PODPISANEJ umowy tworzy OSOBNY dokument: nowa wersja („/Z2",
 *    zastepuje poprzednia) albo aneks („/A1", zmienia ja punktowo),
 *  - zmiane zglaszal moze zarzad (leci od razu do klienta) albo opiekun deala
 *    (czeka na akceptacje zarzadu, `contract.change.approve`),
 *  - `sciezkaPodpisu` NIE wraca dla dokumentu w szkicu, unieważnionego,
 *    zastapionego i po terminie — martwy link prosi sie o wyslanie klientowi.
 *
 * Czego atrapa NIE robi: prawdziwego PDF-a i publicznej strony podpisu.
 * `GET .../pdf` oddaje minimalny, poprawny plik PDF z numerem umowy — tyle,
 * ile potrzeba, zeby telefon sprawdzil pobieranie i „Udostepnij".
 *
 * Uprawnienia: odczyt `crm.view`, generowanie/zmiana/uniewaznienie
 * `deal.manage`, decyzja o zmianie `contract.change.approve` (admin/zarzad).
 */

const express = require('express');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission, can, unprocessable } = require('../middleware');
const { db, dealById } = require('../store');

const router = express.Router();

/** Tyle czasu klient ma na odeslanie podpisu (board360: `WAZNOSC_LINKU_H`). */
const WAZNOSC_LINKU_H = 48;

const godziny = (h) => h * 60 * 60 * 1000;

const numerBazowy = (numer) => numer.replace(/\/[ZA]\d+$/, '');
const numerWersji = (numer, wersja) => `${numerBazowy(numer)}/Z${wersja}`;
const numerAneksu = (numer, nr) => `${numerBazowy(numer)}/A${nr}`;

/** Kolejny numer umowy w miesiacu: „UM/2026/09/001". */
function nextNumber(organizationId, teraz = new Date()) {
  const rok = teraz.getFullYear();
  const mies = String(teraz.getMonth() + 1).padStart(2, '0');
  const prefiks = `UM/${rok}/${mies}/`;
  const ile = db.contracts.filter(
    (c) => c.organizationId === organizationId && c.number.startsWith(prefiks) && !/\/[ZA]\d+$/.test(c.number),
  ).length;
  return `${prefiks}${String(ile + 1).padStart(3, '0')}`;
}

/**
 * Domkniecie po terminie — board360 robi to leniwie przy odczycie, wiec atrapa
 * tak samo. Bez tego telefon nigdy nie zobaczylby stanu „Termin odeslania minal".
 */
function domknijPoTerminie(contract) {
  if (contract.status !== 'sent' || !contract.tokenExpiresAt) return contract;
  if (new Date(contract.tokenExpiresAt).getTime() > Date.now()) return contract;
  contract.status = 'expired';
  return contract;
}

/** Czy sesja moze akceptowac zmiany umow (zarzad/admin). */
const mozeAkceptowac = (user) => can(user, 'contract.change.approve');

/** Czy sesja opiekuje sie dealem — po tym opiekun moze ZGLOSIC zmiane. */
function jestOpiekunem(user, dealId) {
  const deal = dealById(user.organizationId, dealId);
  return !!deal && (deal.ownerId === user.id || deal.stageOwnerId === user.id);
}

const czekaNaAkceptacje = (c) => c.status === 'draft' && !!c.changeRequestedAt && !c.changeApprovedAt;

/** Widok listy — 1:1 z `ContractsController.lista`. */
function contractView(c, kontekst) {
  const { poNumerze, nastepca, zarzad, wolnoZmieniac } = kontekst;
  const nast = nastepca.get(c.id) || null;
  const martwy =
    c.status === 'cancelled' || c.status === 'draft' || c.status === 'superseded' || c.status === 'expired';
  return {
    id: c.id,
    numer: c.number,
    status: c.status,
    rodzaj: c.kind,
    utworzona: c.createdAt,
    wyslana: c.sentAt,
    podpisana: c.signedAt,
    podpisanaIp: c.signedIp,
    wysylka: c.emailStatus,
    wyslanaMailem: c.emailSentAt,
    parafaZalacznika: c.attachmentSignedAt,
    // Umowa podpisana przed wprowadzeniem parafy — telefon proponuje doslanie.
    brakParafy: c.status === 'signed' && !c.attachmentSignedAt,
    wygasaLink: c.tokenExpiresAt,
    sciezkaPodpisu: martwy ? null : `/umowa/${c.token}`,
    wersja: c.revision,
    zastepuje: c.supersedesId ? poNumerze.get(c.supersedesId) || null : null,
    zastapionaPrzez: nast ? nast.number : null,
    rodzajNastepcy: nast ? nast.kind : null,
    nastepcaPodpisany: !!nast && nast.status === 'signed',
    zmiana: c.changeRequestedAt
      ? {
          powod: c.changeReason || null,
          zgloszona: c.changeRequestedAt,
          zgloszonaPrzez: c.changeRequestedBy || null,
          zaakceptowana: c.changeApprovedAt || null,
          zaakceptowanaPrzez: c.changeApprovedBy || null,
          odrzucona: c.changeRejectedAt || null,
          odrzuconaPrzez: c.changeRejectedBy || null,
          powodOdrzucenia: c.changeRejectReason || null,
        }
      : null,
    czekaNaAkceptacje: czekaNaAkceptacje(c),
    zarzad,
    mogeZdecydowac: zarzad && czekaNaAkceptacje(c),
    mozeZmienic: wolnoZmieniac && c.status === 'signed' && !nastepca.has(c.id),
  };
}

function kontekstListy(umowy, user, dealId) {
  const zarzad = mozeAkceptowac(user);
  const poNumerze = new Map();
  const nastepca = new Map();
  for (const c of umowy) {
    poNumerze.set(c.id, c.number);
    // Wycofana zmiana nie zastepuje niczego — umowa wraca do gry.
    if (c.supersedesId && c.status !== 'cancelled') nastepca.set(c.supersedesId, c);
  }
  return { poNumerze, nastepca, zarzad, wolnoZmieniac: zarzad || jestOpiekunem(user, dealId) };
}

const contractsOf = (organizationId, dealId) =>
  db.contracts
    .filter((c) => c.organizationId === organizationId && c.dealId === dealId)
    .map(domknijPoTerminie)
    .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));

/** Wypelnienie w ksztalcie formularza — to samo, co przyjmuje generowanie. */
const fillingOf = (c) => ({
  przedmiot: c.data.przedmiot || '',
  termin: c.data.termin || '',
  podstawaZalacznika: c.data.podstawaZalacznika || '',
  etapy: c.data.etapy || [],
  pozycje: c.data.pozycje || [],
  vatStawka: c.data.vatStawka ?? 8,
  zaliczkaProc: c.data.zaliczkaProc ?? 30,
  terminKoncowyDni: c.data.terminKoncowyDni ?? 3,
  materialy: c.data.materialy || [],
});

const grosz = (x) => Math.round((x + Number.EPSILON) * 100) / 100;

/** Sumy dokumentu — te same reguly, co podglad w panelu i na telefonie. */
function sumy(data) {
  const netto = grosz((data.pozycje || []).reduce((s, p) => s + grosz(p.ilosc * p.cenaNetto), 0));
  const vat = grosz((netto * (data.vatStawka ?? 8)) / 100);
  const brutto = grosz(netto + vat);
  return { netto, vat, brutto, zaliczka: grosz((brutto * (data.zaliczkaProc ?? 30)) / 100) };
}

/** Czy tresc nadaje sie na dokument — te same warunki, co w board360. */
function walidacja(body) {
  if (!body || typeof body !== 'object') return 'Brak tresci umowy';
  if (!String(body.przedmiot || '').trim()) return 'Podaj przedmiot umowy (§ 1)';
  if (!String(body.podstawaZalacznika || '').trim()) return 'Podaj podstawe Zalacznika nr 1';
  const pozycje = Array.isArray(body.pozycje) ? body.pozycje : [];
  if (!pozycje.some((p) => String(p.opis || '').trim() && Number(p.cenaNetto) > 0)) {
    return 'Zalacznik nr 1 nie ma zadnej pozycji z cena';
  }
  const etapy = new Set((body.etapy || []).map((e) => e.nr));
  const sieroty = [...new Set(pozycje.map((p) => p.etap).filter((n) => !etapy.has(n)))];
  if (sieroty.length) return `Pozycje wskazuja etapy spoza listy: ${sieroty.join(', ')}`;
  return null;
}

function nowyDokument({ organizationId, dealId, userId, numer, kind, data, revision, supersedesId }) {
  const teraz = new Date();
  return {
    id: uuid(),
    organizationId,
    dealId,
    number: numer,
    status: 'sent',
    kind: kind || 'umowa',
    revision: revision || 1,
    supersedesId: supersedesId || null,
    token: uuid().replace(/-/g, ''),
    tokenExpiresAt: new Date(teraz.getTime() + godziny(WAZNOSC_LINKU_H)).toISOString(),
    createdAt: nowIso(),
    createdBy: userId,
    sentAt: nowIso(),
    signedAt: null,
    signedIp: null,
    attachmentSignedAt: null,
    // Atrapa nie ma SMTP — tak samo jak produkcja bez konfiguracji poczty.
    emailStatus: 'pending_config',
    emailSentAt: null,
    changeReason: null,
    changeRequestedAt: null,
    changeRequestedBy: null,
    changeApprovedAt: null,
    changeApprovedBy: null,
    changeRejectedAt: null,
    changeRejectedBy: null,
    changeRejectReason: null,
    data,
  };
}

// ── Odczyt ───────────────────────────────────────────────────────────────────

router.get('/deals/:dealId/contracts', requireAuth, requirePermission('crm.view'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.dealId);
  if (!deal) return res.status(404).json({ message: 'Deal nie istnieje' });
  const umowy = contractsOf(req.user.organizationId, req.params.dealId);
  const kontekst = kontekstListy(umowy, req.user, req.params.dealId);
  res.json(umowy.map((c) => contractView(c, kontekst)));
});

const findContract = (req) =>
  db.contracts.find((c) => c.id === req.params.id && c.organizationId === req.user.organizationId);

/** Podglad dokumentu — HTML skladany z tresci, ten sam co idzie do PDF. */
router.get(
  '/deals/:dealId/contracts/:id/preview',
  requireAuth,
  requirePermission('crm.view'),
  (req, res) => {
    const c = findContract(req);
    if (!c) return res.status(404).json({ message: 'Umowa nie istnieje' });
    res.json({ numer: c.number, podpisana: c.status === 'signed', html: documentHtml(c) });
  },
);

/**
 * Minimalny, ale POPRAWNY PDF — telefon ma co pobrac, otworzyc i udostepnic.
 * Skladanie prawdziwego dokumentu zostaje w board360; tu liczy sie sciezka
 * „pobierz → FileProvider → Udostepnij".
 */
router.get('/deals/:dealId/contracts/:id/pdf', requireAuth, requirePermission('crm.view'), (req, res) => {
  const c = findContract(req);
  if (!c) return res.status(404).json({ message: 'Umowa nie istnieje' });
  const pdf = minimalPdf(`${c.number} — ${c.data.przedmiot || 'umowa'}`);
  res.set({
    'Content-Type': 'application/pdf',
    'Content-Disposition': `attachment; filename="${c.number.replace(/\//g, '-')}.pdf"`,
  });
  res.send(pdf);
});

router.get(
  '/deals/:dealId/contracts/:id/wypelnienie',
  requireAuth,
  requirePermission('crm.view'),
  (req, res) => {
    const c = findContract(req);
    if (!c) return res.status(404).json({ message: 'Umowa nie istnieje' });
    res.json({ numer: c.number, wersja: c.revision, wypelnienie: fillingOf(c) });
  },
);

// ── Wystawienie ──────────────────────────────────────────────────────────────

router.post('/deals/:dealId/contracts', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.dealId);
  if (!deal) return res.status(404).json({ message: 'Deal nie istnieje' });
  const blad = walidacja(req.body);
  if (blad) return unprocessable(res, blad);

  const umowa = nowyDokument({
    organizationId: req.user.organizationId,
    dealId: req.params.dealId,
    userId: req.user.id,
    numer: nextNumber(req.user.organizationId),
    kind: 'umowa',
    data: { ...req.body },
  });
  db.contracts.push(umowa);
  res.status(201).json({
    id: umowa.id,
    numer: umowa.number,
    status: umowa.status,
    sciezkaPodpisu: `/umowa/${umowa.token}`,
  });
});

/** Nowy link do podpisu — poprzedni przestaje dzialac. */
router.post(
  '/deals/:dealId/contracts/:id/resend',
  requireAuth,
  requirePermission('deal.manage'),
  (req, res) => {
    const c = findContract(req);
    if (!c) return res.status(404).json({ message: 'Umowa nie istnieje' });
    domknijPoTerminie(c);
    if (c.status === 'cancelled' || c.status === 'superseded') {
      return unprocessable(res, 'Ten dokument nie przyjmuje juz podpisu');
    }
    if (c.status === 'expired') {
      return unprocessable(res, 'Termin odeslania minal — wystaw nowa umowe z aktualnymi cenami');
    }
    const zalacznik = c.status === 'signed' && !c.attachmentSignedAt;
    if (c.status === 'signed' && !zalacznik) {
      return unprocessable(res, 'Umowa jest juz podpisana w calosci');
    }
    c.token = uuid().replace(/-/g, '');
    c.tokenExpiresAt = new Date(Date.now() + godziny(WAZNOSC_LINKU_H)).toISOString();
    if (!zalacznik) c.status = 'sent';
    c.sentAt = nowIso();
    res.json({ sciezkaPodpisu: `/umowa/${c.token}`, zakres: zalacznik ? 'zalacznik' : 'pelny' });
  },
);

router.delete('/deals/:dealId/contracts/:id', requireAuth, requirePermission('deal.manage'), (req, res) => {
  const c = findContract(req);
  if (!c) return res.status(404).json({ message: 'Umowa nie istnieje' });
  if (c.status === 'signed') return unprocessable(res, 'Podpisanej umowy nie da sie uniewaznic');
  c.status = 'cancelled';
  c.token = null;
  c.tokenExpiresAt = null;
  res.json({ ok: true });
});

// ── Zmiana podpisanej umowy ──────────────────────────────────────────────────

router.post(
  '/deals/:dealId/contracts/:id/change',
  requireAuth,
  requirePermission('deal.manage'),
  (req, res) => {
    const c = findContract(req);
    if (!c) return res.status(404).json({ message: 'Umowa nie istnieje' });
    if (c.status !== 'signed') return unprocessable(res, 'Zmienia sie tylko PODPISANA umowe');

    const powod = String(req.body && req.body.powod ? req.body.powod : '').trim();
    if (!powod) return res.status(400).json({ message: 'Podaj powod zmiany umowy' });
    const rodzaj = (req.body && req.body.rodzaj) || 'umowa';
    if (rodzaj !== 'umowa' && rodzaj !== 'aneks') {
      return res.status(400).json({ message: 'Nieznany rodzaj dokumentu zmiany' });
    }
    if (!(req.body && req.body.potwierdzam === true)) {
      return unprocessable(res, 'Zmiana wymaga potwierdzenia');
    }
    const zarzad = mozeAkceptowac(req.user);
    if (!zarzad && !jestOpiekunem(req.user, req.params.dealId)) {
      return res.status(403).json({ message: 'Zmiane zglasza zarzad albo opiekun deala' });
    }
    const blad = walidacja(req.body);
    if (blad) return unprocessable(res, blad);
    const wToku = db.contracts.find(
      (x) => x.supersedesId === c.id && x.status !== 'cancelled',
    );
    if (wToku) return unprocessable(res, `Zmiana tej umowy juz trwa (${wToku.number})`);

    const wersja = rodzaj === 'aneks'
      ? db.contracts.filter((x) => x.kind === 'aneks' && numerBazowy(x.number) === numerBazowy(c.number)).length + 1
      : c.revision + 1;
    const numer = rodzaj === 'aneks' ? numerAneksu(c.number, wersja) : numerWersji(c.number, wersja);

    const dokument = nowyDokument({
      organizationId: c.organizationId,
      dealId: c.dealId,
      userId: req.user.id,
      numer,
      kind: rodzaj,
      data: { ...req.body },
      revision: wersja,
      supersedesId: c.id,
    });
    dokument.changeReason = powod;
    dokument.changeRequestedAt = nowIso();
    dokument.changeRequestedBy = req.user.id;
    if (zarzad) {
      // Zarzad wysyla od razu — dokument rusza do klienta z wlasnym linkiem.
      dokument.changeApprovedAt = nowIso();
      dokument.changeApprovedBy = req.user.id;
    } else {
      // Opiekun tylko ZGLASZA: szkic bez linku, czeka na zarzad.
      dokument.status = 'draft';
      dokument.sentAt = null;
      dokument.tokenExpiresAt = null;
    }
    db.contracts.push(dokument);

    res.status(201).json({
      id: dokument.id,
      numer: dokument.number,
      wersja,
      rodzaj,
      stan: zarzad ? 'do-podpisu' : 'czeka-na-akceptacje',
      sciezkaPodpisu: zarzad ? `/umowa/${dokument.token}` : null,
    });
  },
);

router.post(
  '/deals/:dealId/contracts/:id/change/approve',
  requireAuth,
  requirePermission('contract.change.approve'),
  (req, res) => {
    const c = findContract(req);
    if (!c) return res.status(404).json({ message: 'Dokument nie istnieje' });
    if (!czekaNaAkceptacje(c)) return unprocessable(res, 'Ten dokument nie czeka na decyzje');
    if (!(req.body && req.body.potwierdzam === true)) {
      return unprocessable(res, 'Akceptacja wymaga potwierdzenia');
    }
    c.changeApprovedAt = nowIso();
    c.changeApprovedBy = req.user.id;
    c.status = 'sent';
    c.sentAt = nowIso();
    c.token = uuid().replace(/-/g, '');
    c.tokenExpiresAt = new Date(Date.now() + godziny(WAZNOSC_LINKU_H)).toISOString();
    res.json({ numer: c.number, sciezkaPodpisu: `/umowa/${c.token}` });
  },
);

router.post(
  '/deals/:dealId/contracts/:id/change/reject',
  requireAuth,
  requirePermission('contract.change.approve'),
  (req, res) => {
    const c = findContract(req);
    if (!c) return res.status(404).json({ message: 'Dokument nie istnieje' });
    if (!czekaNaAkceptacje(c)) return unprocessable(res, 'Ten dokument nie czeka na decyzje');
    c.changeRejectedAt = nowIso();
    c.changeRejectedBy = req.user.id;
    c.changeRejectReason = (req.body && req.body.powod) || null;
    // Odrzucona zmiana znika z obiegu; podpisana umowa wraca do gry.
    c.status = 'cancelled';
    c.token = null;
    c.tokenExpiresAt = null;
    res.json({ ok: true });
  },
);

// ── Odtworzenie zamowienia z umowy ───────────────────────────────────────────

/**
 * Ten sam automat, co po podpisie: z zestawienia materialowego umowy powstaje
 * po jednym zamowieniu na INSTALACJE. Idempotentne — powtorne wywolanie na
 * niezmienionej tresci oddaje `istnialo`.
 */
router.post(
  '/deals/:dealId/contracts/:id/zamowienie',
  requireAuth,
  requirePermission('deal.manage'),
  (req, res) => {
    const c = findContract(req);
    if (!c) return res.status(404).json({ message: 'Umowa nie istnieje' });
    if (c.status !== 'signed') return unprocessable(res, 'Zamowienie powstaje z PODPISANEJ umowy');
    const materialy = c.data.materialy || [];
    if (!materialy.length) {
      return unprocessable(res, 'Umowa nie niesie zestawienia materialowego — przelicz je w panelu');
    }

    const grupy = new Map();
    for (const m of materialy) {
      const key = m.instalacjaId || '-';
      if (!grupy.has(key)) grupy.set(key, { instalacja: m.instalacja || null, lines: [] });
      grupy.get(key).lines.push(m);
    }

    let status = 'istnialo';
    let pozycje = 0;
    for (const [instalacjaId, grupa] of grupy) {
      pozycje += grupa.lines.length;
      const items = grupa.lines.map((m) => ({
        id: uuid(),
        name: m.nazwa,
        quantity: m.ilosc,
        ordered: false,
        received: false,
      }));
      const istniejace = db.orders.find(
        (o) => o.dealId === c.dealId && o.contractId === c.id && (o.installationId || '-') === instalacjaId,
      );
      if (!istniejace) {
        db.orders.push({
          id: uuid(),
          organizationId: c.organizationId,
          dealId: c.dealId,
          supplierId: null,
          contractId: c.id,
          installationId: instalacjaId === '-' ? null : instalacjaId,
          installationName: grupa.instalacja,
          source: 'contract',
          createdAt: nowIso(),
          items,
        });
        status = 'utworzone';
      } else if (istniejace.items.length !== items.length) {
        // Ptaszki magazyniera przepadaja tylko wtedy, gdy tresc naprawde sie
        // zmienila — inaczej dokument zostaje nietkniety.
        istniejace.items = items;
        if (status !== 'utworzone') status = 'zmienione';
      }
    }

    res.json({ numer: c.number, status, pozycje, zamowienia: grupy.size });
  },
);

// ── Dokument ─────────────────────────────────────────────────────────────────

const esc = (s) =>
  String(s == null ? '' : s).replace(/[&<>]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[ch]));

/** Podglad dokumentu — na tyle wierny, zeby dalo sie sprawdzic tresc na ekranie. */
function documentHtml(c) {
  const s = sumy(c.data);
  const wiersze = (c.data.pozycje || [])
    .map(
      (p) =>
        `<tr><td>${p.lp}</td><td>${esc(p.opis)}</td><td>${p.ilosc}</td><td>${esc(p.jm)}</td>` +
        `<td>${p.cenaNetto.toFixed(2)}</td><td>${p.etap}</td></tr>`,
    )
    .join('');
  const etapy = (c.data.etapy || []).map((e) => `<li>${e.nr}. ${esc(e.nazwa)}</li>`).join('');
  return `<!doctype html><html lang="pl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
 body{font:14px/1.5 system-ui,sans-serif;color:#111;background:#fff;margin:0;padding:16px}
 h1{font-size:18px;margin:0 0 4px} h2{font-size:14px;margin:16px 0 6px}
 table{width:100%;border-collapse:collapse;font-size:12px}
 th,td{border:1px solid #ccc;padding:4px 6px;text-align:left}
 .sumy{margin-top:10px;font-size:13px}
</style></head><body>
<h1>Umowa ${esc(c.number)}${c.kind === 'aneks' ? ' (aneks)' : ''}</h1>
<div>${c.status === 'signed' ? 'Podpisana przez klienta' : 'Do podpisu'} · atrapa board360</div>
<h2>§ 1 Przedmiot umowy</h2><div>${esc(c.data.przedmiot)}</div>
<h2>§ 2 Termin wykonania</h2><div>${esc(c.data.termin)}</div>
<h2>§ 7 Etapy</h2><ol>${etapy}</ol>
<h2>Załącznik nr 1 — rozpis (${esc(c.data.podstawaZalacznika)})</h2>
<table><thead><tr><th>Lp.</th><th>Opis</th><th>Ilość</th><th>J.m.</th><th>Cena netto</th><th>Etap</th></tr></thead>
<tbody>${wiersze}</tbody></table>
<div class="sumy">Netto: ${s.netto.toFixed(2)} zł · VAT ${c.data.vatStawka ?? 8}%: ${s.vat.toFixed(2)} zł ·
 <b>Brutto: ${s.brutto.toFixed(2)} zł</b> · zaliczka ${c.data.zaliczkaProc ?? 30}%: ${s.zaliczka.toFixed(2)} zł</div>
${c.changeReason ? `<h2>Powód zmiany</h2><div>${esc(c.changeReason)}</div>` : ''}
</body></html>`;
}

/**
 * Jednostronicowy PDF zlozony recznie (bez zaleznosci) — struktura minimalna,
 * ale poprawna, wiec czytniki na telefonie go otworza.
 */
function minimalPdf(tytul) {
  const tekst = String(tytul).replace(/[\\()]/g, ' ').slice(0, 90);
  const strumien = `BT /F1 14 Tf 56 760 Td (${tekst}) Tj ET`;
  const obiekty = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>',
    `<< /Length ${strumien.length} >>\nstream\n${strumien}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let pdf = '%PDF-1.4\n';
  const offsets = [];
  obiekty.forEach((o, i) => {
    offsets.push(pdf.length);
    pdf += `${i + 1} 0 obj\n${o}\nendobj\n`;
  });
  const xref = pdf.length;
  pdf += `xref\n0 ${obiekty.length + 1}\n0000000000 65535 f \n`;
  for (const off of offsets) pdf += `${String(off).padStart(10, '0')} 00000 n \n`;
  pdf += `trailer\n<< /Size ${obiekty.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(pdf, 'latin1');
}

module.exports = router;
module.exports.helpers = { nowyDokument, nextNumber, WAZNOSC_LINKU_H };
