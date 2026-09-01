package com.ekotak.teamtalk.domain.usecase.deal

import com.ekotak.teamtalk.domain.model.Category
import com.ekotak.teamtalk.domain.model.DealInstallations
import com.ekotak.teamtalk.domain.model.InstallationStage
import com.ekotak.teamtalk.domain.model.LeadIntake
import com.ekotak.teamtalk.domain.repository.CrmDirectoryRepository
import com.ekotak.teamtalk.domain.repository.DealRepository
import com.ekotak.teamtalk.domain.repository.LeadIntakeRepository
import javax.inject.Inject

/**
 * Dane zakładki „LEAD" karty deala: zgłoszenie z leadowni (z edycją notatki),
 * migawka instalacji wybranych przez klienta i katalog technologii, który
 * zamienia id węzłów na czytelne ścieżki. Trzy źródła chodzą wyłącznie razem
 * i tylko w tej jednej zakładce, więc stoją w jednym pliku.
 */

class GetLeadIntakeUseCase @Inject constructor(
    private val leadIntakeRepository: LeadIntakeRepository,
) {
    /** `null` = deal nie pochodzi z leadowni (wpisany ręcznie w panelu). */
    suspend operator fun invoke(dealId: String): LeadIntake? =
        leadIntakeRepository.getLeadIntake(dealId)
}

class UpdateLeadNoteUseCase @Inject constructor(
    private val leadIntakeRepository: LeadIntakeRepository,
) {
    /** Pusty tekst czyści notatkę. Zwraca treść rozwiązaną przez serwer. */
    suspend operator fun invoke(dealId: String, note: String?): String? =
        leadIntakeRepository.updateNote(dealId, note)
}

class GetDealInstallationsUseCase @Inject constructor(
    private val dealRepository: DealRepository,
) {
    suspend operator fun invoke(dealId: String): DealInstallations =
        dealRepository.getInstallations(dealId)
}

class GetCategoriesUseCase @Inject constructor(
    private val crmDirectoryRepository: CrmDirectoryRepository,
) {
    suspend operator fun invoke(): List<Category> = crmDirectoryRepository.getCategories()
}

/**
 * Zapis wyboru instalacji dla etapu LEAD. Wywołujący podaje pełną listę
 * zaznaczonych węzłów; walidację uprawnień (`deal.manage`) i sensowność etapu
 * robi API, więc tu nie dublujemy reguły.
 */
class SetDealInstallationsUseCase @Inject constructor(
    private val dealRepository: DealRepository,
) {
    suspend operator fun invoke(
        dealId: String,
        stage: InstallationStage,
        categoryIds: List<String>,
    ): DealInstallations = dealRepository.setInstallations(dealId, stage, categoryIds)
}
