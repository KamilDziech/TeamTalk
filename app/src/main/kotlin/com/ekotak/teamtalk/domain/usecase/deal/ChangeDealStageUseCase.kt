package com.ekotak.teamtalk.domain.usecase.deal

import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.repository.DealRepository
import javax.inject.Inject

/**
 * Przejście etapu deala. Powód utraty jest wymagany przez domenę board360 przy
 * przejściu na „Stracone" — odcinamy taką próbę lokalnie, żeby nie generować
 * pewnego 422 z sieci.
 */
class ChangeDealStageUseCase @Inject constructor(
    private val dealRepository: DealRepository,
) {
    /**
     * @param lostReasonCategory kategoria utraty — wymagana przy „Stracone",
     *   bo po niej liczą się statystyki Archiwum.
     * @param lostReason opis słowny. board360 rozróżnia go od kategorii; gdy
     *   handlowiec nie dopisze własnego, wysyłamy samą kategorię, żeby wpis
     *   w historii nie był pusty.
     */
    suspend operator fun invoke(
        id: String,
        stage: DealStage,
        lostReasonCategory: String? = null,
        lostReason: String? = null,
        note: String? = null,
    ): Deal {
        require(stage != DealStage.LOST || !lostReasonCategory.isNullOrBlank()) {
            "Powód utraty jest wymagany."
        }
        return dealRepository.changeStage(
            id = id,
            stage = stage,
            lostReasonCategory = lostReasonCategory,
            lostReason = lostReason?.takeIf { it.isNotBlank() } ?: lostReasonCategory,
            note = note,
        )
    }
}
