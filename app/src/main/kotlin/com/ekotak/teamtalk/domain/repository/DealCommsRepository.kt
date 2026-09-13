package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.CommsSendResult
import com.ekotak.teamtalk.domain.model.CommsSyncResult
import com.ekotak.teamtalk.domain.model.DealCallSummary
import com.ekotak.teamtalk.domain.model.DealComment
import com.ekotak.teamtalk.domain.model.WhatsappMessage

/**
 * Zakładka „Komunikacja" karty deala — trzy kanały, które nie mają własnego
 * modułu na telefonie: wewnętrzny wątek zespołu, skrzynka WhatsApp deala
 * i streszczenia rozmów przypięte do deala. Poczta chodzi `EmailRepository`
 * (ma własny cache i kolejkę), SMS-a nie ma jeszcze po stronie board360.
 *
 * Pełny offline z kolejką, jak w każdej zakładce karty: rozmowy z klientem
 * odbywają się U KLIENTA — w kotłowni, na budowie, w aucie — a odpowiedź
 * napisana bez zasięgu nie może zniknąć razem z ekranem.
 *
 * Odczyt jest cache-first: najpierw próbujemy sieci i podmieniamy cache, potem
 * ZAWSZE oddajemy to, co leży lokalnie, z nałożoną kolejką. Dzięki temu wysłana
 * offline wiadomość jest widoczna od razu i nie znika po odświeżeniu.
 */
interface DealCommsRepository {

    // ── Komunikator wewnętrzny (wątek deala) ──────────────────────────────────

    /** Wątek zespołu o tym dealu; kolejka doklejona na końcu jako `pending`. */
    suspend fun getComments(dealId: String): List<DealComment>

    /** Odpowiedź w wątku (z wywołaniami przez „@"). */
    suspend fun addComment(dealId: String, body: String, mentions: List<String>): CommsSendResult

    /**
     * Znacznik przeczytania. Bez zasięgu pomijamy go po cichu — to informacja
     * dla licznika w skrzynce, a nie decyzja, którą trzeba wozić w kolejce.
     */
    suspend fun markRead(dealId: String)

    // ── WhatsApp (skrzynka deala) ─────────────────────────────────────────────

    suspend fun getWhatsapp(dealId: String): List<WhatsappMessage>

    /**
     * Wiadomość wychodząca. Poza oknem 24h od ostatniej wiadomości klienta API
     * odrzuca treść free-form kodem 422 — to reguła WhatsApp Business, więc
     * wyjątek leci dalej do ekranu, zamiast lądować w kolejce.
     */
    suspend fun sendWhatsapp(dealId: String, body: String): CommsSendResult

    // ── Telefon (streszczenia rozmów deala) ───────────────────────────────────

    suspend fun getCallSummaries(dealId: String): List<DealCallSummary>

    /**
     * Ręczne streszczenie rozmowy, której TeamTalk nie nagrał (telefon
     * prywatny, stacjonarny, rozmowa u klienta).
     */
    suspend fun addCallSummary(
        dealId: String,
        clientId: String?,
        phoneNumber: String?,
        text: String,
        agreements: String?,
        nextStep: String?,
    ): CommsSendResult

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /** Opróżnia kolejkę; woła ją robotnik po powrocie zasięgu i przy starcie. */
    suspend fun syncPendingMutations(): CommsSyncResult
}
