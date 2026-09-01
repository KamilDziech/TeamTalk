package com.ekotak.teamtalk.domain.usecase.deal

import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.DealDraft
import com.ekotak.teamtalk.domain.repository.DealRepository
import javax.inject.Inject

/** Zapis zmian karty deala (`PATCH /api/deals/:id`, tylko pola różniące się). */
class UpdateDealUseCase @Inject constructor(
    private val dealRepository: DealRepository,
) {
    suspend operator fun invoke(original: Deal, draft: DealDraft): Deal =
        dealRepository.updateDeal(original, draft)
}
