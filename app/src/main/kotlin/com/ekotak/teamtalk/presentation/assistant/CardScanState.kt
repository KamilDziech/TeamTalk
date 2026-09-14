package com.ekotak.teamtalk.presentation.assistant

import com.ekotak.teamtalk.domain.model.CardQuestions
import com.ekotak.teamtalk.domain.model.CardScanResult
import com.ekotak.teamtalk.domain.model.ContactKind
import com.ekotak.teamtalk.domain.model.ContactRole
import com.ekotak.teamtalk.domain.model.ScannedCard
import com.ekotak.teamtalk.domain.model.tabFor

/**
 * Stan jednej zeskanowanej wizytówki w wątku. Kroki jak w panelu:
 * rodzaj → (klient) lead czy karta / (inny) rola → zatwierdzenie zapisu
 * albo przejście do kreatora LEAD.
 */
data class CardScanState(
    val result: CardScanResult,
    val card: ScannedCard = result.card,
    val step: Step = Step.KIND,
    val kind: ContactKind? = null,
    /** true = lead, false = sama karta. */
    val lead: Boolean? = null,
    /** Rola z listy (`ContactRole.wire`) albo wpisana (`otherRole`). */
    val role: String? = null,
    val otherRole: Boolean = false,
    val editing: Boolean = false,
    val status: Status = Status.IDLE,
    val message: String? = null,
    val clientId: String? = null,
) {
    enum class Step { KIND, MODE, ROLE, CONFIRM, LEAD }
    enum class Status { IDLE, RUNNING, DONE, ERROR }

    val roleValue: String get() = role?.trim().orEmpty()

    val tab: String? get() = kind?.let { tabFor(it, roleValue) }

    /** Czeka na odpowiedź, którą da się też wpisać albo powiedzieć. */
    val awaitsAnswer: Boolean
        get() = status == Status.IDLE && result.canSave && step in setOf(Step.KIND, Step.MODE, Step.ROLE)

    val question: String?
        get() = when {
            status == Status.DONE -> null
            step == Step.KIND -> CardQuestions.KIND
            step == Step.MODE -> CardQuestions.MODE
            step == Step.ROLE -> CardQuestions.ROLE
            else -> null
        }

    fun chooseKind(kind: ContactKind, role: String? = null): CardScanState = when {
        kind.isClient -> copy(
            kind = kind,
            lead = null,
            role = null,
            otherRole = false,
            step = Step.MODE,
            // B2B bez nazwy firmy — od razu formularz, bo bez niej zapis nie przejdzie.
            editing = editing || (kind == ContactKind.B2B && card.companyName.isBlank()),
        )
        kind == ContactKind.INNY && role != null -> withRole(role)
        kind == ContactKind.INNY -> copy(kind = kind, lead = null, role = null, otherRole = false, step = Step.ROLE)
        else -> copy(kind = kind, lead = null, role = null, otherRole = false, step = Step.CONFIRM)
    }

    fun chooseLead(lead: Boolean): CardScanState =
        copy(lead = lead, step = if (lead) Step.LEAD else Step.CONFIRM)

    fun withRole(role: String): CardScanState {
        val known = ContactRole.entries.any { it.wire == role }
        return copy(kind = ContactKind.INNY, role = role, otherRole = !known, step = Step.CONFIRM)
    }
}
