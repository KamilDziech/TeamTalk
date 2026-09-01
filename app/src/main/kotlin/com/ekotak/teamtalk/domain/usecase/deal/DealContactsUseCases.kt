package com.ekotak.teamtalk.domain.usecase.deal

import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.model.AssistantReply
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.repository.DealRepository
import javax.inject.Inject

/**
 * Kontakty towarzyszące karty deala i asystent tej karty.
 *
 * Cztery operacje na kontaktach chodzą zawsze razem (jeden pasek w zakładce
 * „Dane"), więc trzymamy je w jednym pliku zamiast rozbijać na cztery klasy po
 * cztery linie — inaczej pakiet `usecase/deal` puchnie bez zysku dla czytelnika.
 */

class GetDealCompanionsUseCase @Inject constructor(
    private val dealRepository: DealRepository,
) {
    suspend operator fun invoke(dealId: String): List<Client> =
        dealRepository.getCompanions(dealId)
}

class AddDealCompanionUseCase @Inject constructor(
    private val dealRepository: DealRepository,
) {
    /** Zwraca listę kontaktów PO dopięciu — bez drugiego zapytania. */
    suspend operator fun invoke(dealId: String, clientId: String): List<Client> =
        dealRepository.addCompanion(dealId, clientId)
}

class RemoveDealCompanionUseCase @Inject constructor(
    private val dealRepository: DealRepository,
) {
    suspend operator fun invoke(dealId: String, clientId: String): List<Client> =
        dealRepository.removeCompanion(dealId, clientId)
}

/**
 * Zamiana głównego kontaktu deala z towarzyszącym. Nie zwraca nic: operacja
 * zmienia `Deal.clientId`, więc cała karta jest po niej nieaktualna i i tak
 * trzeba ją przeładować.
 */
class SetPrimaryDealContactUseCase @Inject constructor(
    private val dealRepository: DealRepository,
) {
    suspend operator fun invoke(dealId: String, clientId: String) =
        dealRepository.setPrimaryContact(dealId, clientId)
}

/**
 * Q&A o tym jednym dealu. Odpowiada z `configured = false`, gdy serwer nie ma
 * klucza LLM — wtedy tekst jest informacyjny i nie należy go czytać jak analizy.
 */
class AskDealAssistantUseCase @Inject constructor(
    private val dealRepository: DealRepository,
) {
    suspend operator fun invoke(
        dealId: String,
        messages: List<AssistantMessage>,
    ): AssistantReply = dealRepository.askAssistant(dealId, messages)
}
