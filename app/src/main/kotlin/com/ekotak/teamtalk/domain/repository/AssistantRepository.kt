package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.AssistantAction
import com.ekotak.teamtalk.domain.model.AssistantActionOutcome
import com.ekotak.teamtalk.domain.model.AssistantAnswer
import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.model.CardScanResult

/**
 * Asystent firmowy — czat oparty o zasoby serwera ekotak.app i model językowy.
 *
 * Świadomie BEZ warstwy offline: odpowiedź powstaje na serwerze (grounding z
 * Kontekstu i CRM + wywołanie LLM), więc bez sieci nie ma czego kolejkować —
 * ekran mówi to wprost zamiast udawać, że pytanie zostało przyjęte. Historia
 * rozmowy żyje tylko w pamięci ekranu (ustalenie z zamawiającym).
 */
interface AssistantRepository {
    /** Wysyła całą dotychczasową rozmowę i zwraca odpowiedź + propozycje akcji. */
    suspend fun ask(messages: List<AssistantMessage>): AssistantAnswer

    /** Wykonuje ZATWIERDZONĄ przez użytkownika propozycję. Zwraca podsumowanie. */
    suspend fun runAction(action: AssistantAction): AssistantActionOutcome

    /**
     * Odczyt wizytówki ze zdjęcia (JPEG, już zmniejszonego). Nic nie zapisuje —
     * kontakt zakłada dopiero zatwierdzona akcja `create_contact`.
     */
    suspend fun scanCard(jpeg: ByteArray): CardScanResult
}
