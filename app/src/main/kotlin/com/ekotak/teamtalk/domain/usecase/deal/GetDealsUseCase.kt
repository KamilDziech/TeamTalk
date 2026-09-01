package com.ekotak.teamtalk.domain.usecase.deal

import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.repository.DealRepository
import javax.inject.Inject

class GetDealsUseCase @Inject constructor(
    private val dealRepository: DealRepository,
) {
    suspend operator fun invoke(
        stage: DealStage? = null,
        overdue: Boolean = false,
    ): List<Deal> = dealRepository.getDeals(stage = stage, overdue = overdue)
}
