package com.ekotak.teamtalk.domain.usecase.client

import com.ekotak.teamtalk.domain.model.ClientDeal
import com.ekotak.teamtalk.domain.repository.CrmDirectoryRepository
import com.ekotak.teamtalk.domain.repository.DealRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Dane, którymi kartoteka wzbogaca wiersze klientów: szanse (główny etap,
 * wartość), instalacje i deale wspólne. Odpowiednik doładowań, które panel robi
 * w `ClientsPage` przed wyrenderowaniem kartoteki.
 */
data class ClientDirectoryData(
    val dealsByClient: Map<String, List<ClientDeal>> = emptyMap(),
    val installByClient: Map<String, List<String>> = emptyMap(),
    /** clientId → id współkontaktów z deali wspólnych (nazwy dokleja warstwa UI). */
    val sharedIdsByClient: Map<String, List<String>> = emptyMap(),
    /** Nazwy instalacji występujące w organizacji — opcje filtra. */
    val installOptions: List<String> = emptyList(),
)

class GetClientDirectoryUseCase @Inject constructor(
    private val dealRepository: DealRepository,
    private val crmDirectoryRepository: CrmDirectoryRepository,
) {
    /**
     * Rzuca tylko wtedy, gdy nie uda się pobrać lejka — bez niego nie ma czym
     * wzbogacić kartoteki. Braki w danych pobocznych (instalacje, wartości,
     * kontakty) zjadamy: lista klientów ma się pokazać nawet wtedy, gdy któryś
     * z tych endpointów odmówi (np. brak uprawnień do ofert).
     */
    suspend operator fun invoke(): ClientDirectoryData = coroutineScope {
        val dealsAsync = async { dealRepository.getDeals() }
        val valuesAsync = async { runCatching { crmDirectoryRepository.getDealValues() }.getOrDefault(emptyMap()) }
        val installAsync = async { runCatching { crmDirectoryRepository.getInstallationsByDeal() }.getOrDefault(emptyMap()) }
        val categoriesAsync = async { runCatching { crmDirectoryRepository.getRootCategoryNames() }.getOrDefault(emptyMap()) }
        val contactsAsync = async { runCatching { crmDirectoryRepository.getDealContacts() }.getOrDefault(emptyMap()) }

        val deals = dealsAsync.await()
        val values = valuesAsync.await()
        val installationsByDeal = installAsync.await()
        val categoryNames = categoriesAsync.await()
        val contactsByDeal = contactsAsync.await()

        val dealsByClient = HashMap<String, MutableList<ClientDeal>>()
        val installByClient = HashMap<String, MutableList<String>>()
        for (deal in deals) {
            dealsByClient.getOrPut(deal.clientId) { mutableListOf() }.add(
                ClientDeal(
                    id = deal.id,
                    stage = deal.stage,
                    value = values[deal.id] ?: 0.0,
                    createdAt = deal.createdAt,
                    updatedAt = deal.updatedAt,
                ),
            )
            // Instalacje klienta to unia kategorii z wszystkich jego deali.
            val names = installByClient.getOrPut(deal.clientId) { mutableListOf() }
            for (categoryId in installationsByDeal[deal.id].orEmpty()) {
                val name = categoryNames[categoryId] ?: continue
                if (name !in names) names.add(name)
            }
        }

        // Deal wspólny = główny kontakt + towarzyszący; każdy widzi pozostałych.
        val sharedIdsByClient = HashMap<String, MutableList<String>>()
        for (deal in deals) {
            val ids = listOf(deal.clientId) + contactsByDeal[deal.id].orEmpty()
            if (ids.size < 2) continue
            for (id in ids) {
                val others = sharedIdsByClient.getOrPut(id) { mutableListOf() }
                for (other in ids) if (other != id && other !in others) others.add(other)
            }
        }

        ClientDirectoryData(
            dealsByClient = dealsByClient,
            installByClient = installByClient,
            sharedIdsByClient = sharedIdsByClient,
            installOptions = installByClient.values.flatten().distinct().sorted(),
        )
    }
}
