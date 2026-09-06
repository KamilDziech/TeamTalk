'use strict';
/*
 * Sprzedaz i magazyn dla zakladki „Zamowienie" karty deala w TeamTalku.
 *
 * Ksztalt rekordow i regul = board360:
 *  - `api/src/modules/sales` — oferty i zamowienia,
 *  - `api/src/modules/inventory` — rezerwacje materialu i zapotrzebowanie
 *    zakupowe (lista zakupowa magazynu).
 *
 * Trzy reguly przeniesione 1:1, bo na nich stoi cala zakladka:
 *  - status zamowienia WYNIKA z pozycji (`deriveOrderStatus`): wszystkie
 *    odebrane -> received; inaczej wszystkie zamowione -> ordered; inaczej open,
 *  - zamowienie da sie zalozyc TYLKO z oferty `won` (inaczej 422),
 *  - rezerwacja nie rusza stanu magazynu; `covered` i `missing` liczy serwer,
 *    bo przydzial idzie po dacie montazu przez wszystkie deale naraz.
 *
 * Uprawnienia: oferty `crm.view`, zamowienia `order.manage` (takze odczyt —
 * tak jak w board360), magazyn `inventory.view` / `inventory.manage`.
 */

const express = require('express');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission, unprocessable } = require('../middleware');
const { db, dealById } = require('../store');

const router = express.Router();

// ── Widoki (ksztalt odpowiedzi) ──────────────────────────────────────────────

const offerView = (o) => ({
  id: o.id,
  dealId: o.dealId,
  number: o.number,
  status: o.status,
  netTotal: o.netTotal,
  grossTotal: o.grossTotal,
  margin: o.margin,
  pdfUrl: null,
  createdAt: o.createdAt,
  updatedAt: o.updatedAt,
  items: o.items,
});

const orderView = (o) => ({
  id: o.id,
  dealId: o.dealId,
  supplierId: o.supplierId,
  status: deriveOrderStatus(o.items),
  contractId: o.contractId,
  installationId: o.installationId,
  installationName: o.installationName,
  source: o.source,
  createdAt: o.createdAt,
  items: o.items.map((i) => ({
    id: i.id,
    name: i.name,
    quantity: i.quantity,
    ordered: i.ordered,
    received: i.received,
  })),
});

/** Rezerwacja z pokryciem — `covered`/`missing` liczy serwer, nie telefon. */
const reservationView = (r) => ({
  id: r.id,
  dealId: r.dealId,
  productId: r.productId,
  itemName: r.itemName,
  itemCode: r.itemCode,
  clientLabel: r.clientLabel,
  quantity: r.quantity,
  unit: r.unit,
  status: r.status,
  source: r.source,
  neededBy: r.neededBy,
  note: r.note,
  covered: r.status === 'active' ? r.covered : 0,
  missing: r.status === 'active' ? Math.max(0, r.quantity - r.covered) : 0,
  productName: r.productName,
});

const purchaseView = (p) => ({
  id: p.id,
  productId: p.productId,
  dealId: p.dealId,
  contractId: null,
  reservationId: p.reservationId,
  source: p.source,
  quantity: p.quantity,
  receivedQty: p.receivedQty,
  status: p.status,
  distributor: p.distributor,
  unitPrice: p.unitPrice,
  expectedAt: p.expectedAt,
  note: p.note,
  createdAt: p.createdAt,
  updatedAt: p.updatedAt,
});

/** FR-14: status zamowienia jest POCHODNA pozycji, nie osobnym polem. */
function deriveOrderStatus(items) {
  if (!items || items.length === 0) return 'open';
  if (items.every((i) => i.received)) return 'received';
  if (items.every((i) => i.ordered)) return 'ordered';
  return 'open';
}

// ── Oferty ───────────────────────────────────────────────────────────────────

router.get(
  '/deals/:id/offers',
  requireAuth,
  requirePermission('crm.view'),
  (req, res) => {
    const deal = dealById(req.user.organizationId, req.params.id);
    if (!deal) return res.status(404).json({ message: 'Deal nie istnieje.' });
    res.json(
      db.offers
        .filter((o) => o.dealId === deal.id && o.organizationId === deal.organizationId)
        .map(offerView),
    );
  },
);

// ── Zamowienia ───────────────────────────────────────────────────────────────

router.get(
  '/deals/:id/orders',
  requireAuth,
  requirePermission('order.manage'),
  (req, res) => {
    const deal = dealById(req.user.organizationId, req.params.id);
    if (!deal) return res.status(404).json({ message: 'Deal nie istnieje.' });
    res.json(
      db.orders
        .filter((o) => o.dealId === deal.id && o.organizationId === deal.organizationId)
        .map(orderView),
    );
  },
);

/**
 * Zamowienie z WYGRANEJ oferty. Pozycje przepisuje serwer z oferty — telefon
 * podaje sam `offerId`, wiec zamowienie zalozone bez zasiegu i wyslane pozniej
 * dostanie dokladnie te same pozycje, co zalozone przy biurku.
 */
router.post(
  '/deals/:id/orders',
  requireAuth,
  requirePermission('order.manage'),
  (req, res) => {
    const orgId = req.user.organizationId;
    const deal = dealById(orgId, req.params.id);
    if (!deal) return res.status(404).json({ message: 'Deal nie istnieje.' });

    const offerId = req.body && req.body.offerId;
    if (!offerId) return unprocessable(res, 'Podaj `offerId`.');

    const offer = db.offers.find(
      (o) => o.id === offerId && o.organizationId === orgId && o.dealId === deal.id,
    );
    if (!offer) return res.status(404).json({ message: 'Oferta nie istnieje.' });
    if (offer.status !== 'won') {
      return unprocessable(res, 'Zamowienie powstaje wylacznie z wygranej oferty.');
    }

    const order = {
      id: uuid(),
      organizationId: orgId,
      dealId: deal.id,
      supplierId: (req.body && req.body.supplierId) || null,
      contractId: null,
      installationId: null,
      installationName: null,
      source: 'offer',
      createdAt: nowIso(),
      items: offer.items.map((i) => ({
        id: uuid(),
        name: i.name,
        quantity: i.quantity,
        ordered: false,
        received: false,
      })),
    };
    db.orders.push(order);
    res.status(201).json(orderView(order));
  },
);

/**
 * Ptaszek „zamowione" / „odebrane". Pominiete pole zostaje NIETKNIETE — telefon
 * wysyla tylko to, ktore magazynier odhaczyl.
 */
router.patch(
  '/orders/:id/items/:itemId',
  requireAuth,
  requirePermission('order.manage'),
  (req, res) => {
    const order = db.orders.find(
      (o) => o.id === req.params.id && o.organizationId === req.user.organizationId,
    );
    if (!order) return res.status(404).json({ message: 'Zamowienie nie istnieje.' });

    const item = order.items.find((i) => i.id === req.params.itemId);
    if (!item) return res.status(404).json({ message: 'Pozycja nie istnieje.' });

    const body = req.body || {};
    if (body.ordered === undefined && body.received === undefined) {
      return unprocessable(res, 'Podaj `ordered` i/lub `received`.');
    }
    if (body.ordered !== undefined) item.ordered = !!body.ordered;
    if (body.received !== undefined) item.received = !!body.received;

    res.json(orderView(order));
  },
);

// ── Magazyn: rezerwacje materialu ────────────────────────────────────────────

/**
 * Brak `status` = SAME AKTYWNE (magazyn pyta o to, co realnie trzyma towar);
 * `all` = takze historia, ktorej potrzebuje karta deala.
 */
router.get(
  '/inventory/reservations',
  requireAuth,
  requirePermission('inventory.view'),
  (req, res) => {
    const orgId = req.user.organizationId;
    const { status, dealId, productId } = req.query;
    let rows = db.reservations.filter((r) => r.organizationId === orgId);
    if (dealId) rows = rows.filter((r) => r.dealId === dealId);
    if (productId) rows = rows.filter((r) => r.productId === productId);
    if (status !== 'all') {
      const wanted = status ? String(status).split(',') : ['active'];
      rows = rows.filter((r) => wanted.includes(r.status));
    }
    res.json(rows.map(reservationView));
  },
);

/** Korekta linii: telefon zmienia sam `status` („Wydane" / „Zwolnij"). */
router.patch(
  '/inventory/reservations/:id',
  requireAuth,
  requirePermission('inventory.manage'),
  (req, res) => {
    const row = db.reservations.find(
      (r) => r.id === req.params.id && r.organizationId === req.user.organizationId,
    );
    if (!row) return res.status(404).json({ message: 'Rezerwacja nie istnieje.' });

    const body = req.body || {};
    if (body.status !== undefined) {
      if (!['active', 'done', 'cancelled'].includes(body.status)) {
        return unprocessable(res, 'Nieznany status rezerwacji.');
      }
      row.status = body.status;
      // Podpis wydania bierzemy z sesji, nie z ciala zadania — front nie
      // decyduje, kto wydal towar.
      row.issuedById = body.status === 'done' ? req.user.id : null;
    }
    if (body.quantity !== undefined) row.quantity = Number(body.quantity);
    if (body.note !== undefined) row.note = body.note;
    row.updatedAt = nowIso();

    res.json(reservationView(row));
  },
);

// ── Magazyn: zapotrzebowanie zakupowe ────────────────────────────────────────

/** `open` = pozycje w obiegu (`to_order` + `ordered`); `all` = takze zamkniete. */
router.get(
  '/inventory/orders',
  requireAuth,
  requirePermission('inventory.view'),
  (req, res) => {
    const orgId = req.user.organizationId;
    const { status, dealId } = req.query;
    let rows = db.purchaseOrders.filter((p) => p.organizationId === orgId);
    if (dealId) rows = rows.filter((p) => p.dealId === dealId);
    if (status === 'open') {
      rows = rows.filter((p) => ['to_order', 'ordered'].includes(p.status));
    } else if (status && status !== 'all') {
      const wanted = String(status).split(',');
      rows = rows.filter((p) => wanted.includes(p.status));
    }
    res.json(rows.map(purchaseView));
  },
);

/**
 * Brak dolozony na liste zakupowa magazynu. Pozycja rusza liste zakupowa,
 * a NIE stan magazynowy — ten zmienia sie dopiero przy przyjeciu dostawy.
 *
 * `reservationId` doklejamy po kartotece: panel go nie wysyla, a bez niego
 * wiersz rezerwacji w telefonie nie poznalby, ze jego brak juz ktos kupuje.
 */
router.post(
  '/inventory/orders',
  requireAuth,
  requirePermission('inventory.manage'),
  (req, res) => {
    const orgId = req.user.organizationId;
    const body = req.body || {};
    if (!body.productId) return unprocessable(res, 'Podaj `productId`.');
    if (!(Number(body.quantity) > 0)) return unprocessable(res, 'Ilosc musi byc dodatnia.');

    const reservation = db.reservations.find(
      (r) => r.organizationId === orgId && r.productId === body.productId && r.status === 'active',
    );
    const item = {
      id: uuid(),
      organizationId: orgId,
      productId: body.productId,
      dealId: reservation ? reservation.dealId : null,
      reservationId: reservation ? reservation.id : null,
      source: 'manual',
      quantity: Number(body.quantity),
      receivedQty: 0,
      status: 'to_order',
      distributor: body.distributor || null,
      unitPrice: null,
      expectedAt: null,
      note: body.note || null,
      createdAt: nowIso(),
      updatedAt: nowIso(),
    };
    db.purchaseOrders.push(item);
    res.status(201).json(purchaseView(item));
  },
);

module.exports = router;
