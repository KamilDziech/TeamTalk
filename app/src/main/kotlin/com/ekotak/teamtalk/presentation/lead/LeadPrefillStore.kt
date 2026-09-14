package com.ekotak.teamtalk.presentation.lead

import com.ekotak.teamtalk.domain.model.LeadCardPrefill
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Skrzynka „asystent → kreator LEAD": asystent odkłada tu dane z wizytówki tuż
 * przed nawigacją, kreator zabiera je raz przy starcie. Bez argumentów trasy,
 * bo dane osobowe nie mają lądować w back stacku ani w logach nawigacji.
 */
@Singleton
class LeadPrefillStore @Inject constructor() {
    @Volatile
    private var pending: LeadCardPrefill? = null

    fun put(prefill: LeadCardPrefill) {
        pending = prefill
    }

    /** Zwraca i czyści — drugi kreator otwarty z kafelka startuje pusty. */
    fun take(): LeadCardPrefill? = synchronized(this) {
        pending.also { pending = null }
    }
}
