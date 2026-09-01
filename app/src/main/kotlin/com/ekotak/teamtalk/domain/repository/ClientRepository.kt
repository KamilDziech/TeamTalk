package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.model.AssistantReply
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.ClientDraft
import com.ekotak.teamtalk.domain.model.NewClient
import kotlinx.coroutines.flow.Flow

/**
 * Kartoteka klientów board360 z cache Room. Odczyt jest offline-first (lista z
 * bazy lokalnej, odświeżana z sieci w tle), zapisy idą wprost do API i dopiero
 * po jego potwierdzeniu aktualizują cache — inaczej telefon pokazywałby zmianę,
 * której serwer nie przyjął.
 */
interface ClientRepository {
    /** Strumień klientów z lokalnego cache, odświeżany z sieci. */
    fun getClients(query: String? = null): Flow<List<Client>>

    /** Pojedynczy klient z cache (żywy — reaguje na odświeżenia listy). */
    fun observeClient(id: String): Flow<Client?>

    suspend fun getClientById(id: String): Client

    suspend fun getClientByPhone(phone: String): Client?

    /** Wymuszone pobranie kartoteki z API (pull-to-refresh, po zapisie). */
    suspend fun refresh()

    suspend fun createClient(input: NewClient): Client

    /**
     * Zapisuje wyłącznie pola różniące się od `original`, żeby nie nadpisać
     * zmian zrobionych równolegle w panelu. Bez zmian nie rusza sieci.
     */
    suspend fun updateClient(original: Client, draft: ClientDraft): Client

    /** Scala `sourceIds` w klienta `targetId`. Operacji nie można cofnąć. */
    suspend fun mergeClients(targetId: String, sourceIds: List<String>): Client

    /** Anonimizacja danych osobowych (RODO). Rekord zostaje w kartotece. */
    suspend fun eraseClient(id: String): Client

    /** Asystent karty klienta — Q&A po notatkach i komunikacji z jego deali. */
    suspend fun askAssistant(clientId: String, messages: List<AssistantMessage>): AssistantReply
}
