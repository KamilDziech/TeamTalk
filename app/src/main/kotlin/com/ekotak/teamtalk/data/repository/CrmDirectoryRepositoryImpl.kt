package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.domain.model.Category
import com.ekotak.teamtalk.domain.repository.CrmDirectoryRepository
import javax.inject.Inject

class CrmDirectoryRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
) : CrmDirectoryRepository {

    override suspend fun getInstallationsByDeal(): Map<String, List<String>> =
        api.getCurrentInstallations()

    override suspend fun getRootCategoryNames(): Map<String, String> =
        getCategories()
            .filter { it.parentId == null }
            .sortedWith(compareBy({ it.position }, { it.name }))
            .associate { it.id to it.name }

    override suspend fun getCategories(): List<Category> =
        api.getCategories().map { it.toDomain() }

    override suspend fun getDealValues(): Map<String, Double> = api.getDealValues()

    override suspend fun getDealContacts(): Map<String, List<String>> =
        api.getDealContacts()
            .groupBy { it.dealId }
            .mapValues { (_, links) -> links.map { it.clientId } }
}
