package com.ekotak.teamtalk.domain.usecase.deal

import com.ekotak.teamtalk.domain.model.DealDetail
import com.ekotak.teamtalk.domain.repository.DealRepository
import javax.inject.Inject

class GetDealDetailUseCase @Inject constructor(
    private val dealRepository: DealRepository,
) {
    suspend operator fun invoke(id: String): DealDetail = dealRepository.getDealDetail(id)
}
