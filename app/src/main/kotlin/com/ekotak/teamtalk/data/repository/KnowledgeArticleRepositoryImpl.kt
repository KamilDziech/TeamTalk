package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.GenerateArticleRequest
import com.ekotak.teamtalk.data.remote.dto.toDomain
import com.ekotak.teamtalk.domain.model.ArticleGate
import com.ekotak.teamtalk.domain.model.KnowledgeArticle
import com.ekotak.teamtalk.domain.repository.KnowledgeArticleRepository
import javax.inject.Inject

class KnowledgeArticleRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
) : KnowledgeArticleRepository {

    override suspend fun getGate(dealId: String): ArticleGate =
        api.getKnowledgeArticleGate(dealId).toDomain()

    override suspend fun listArticles(dealId: String): List<KnowledgeArticle> =
        api.getKnowledgeArticles(dealId).map { it.toDomain() }

    override suspend fun generate(dealId: String, categoryId: String): KnowledgeArticle =
        api.generateKnowledgeArticle(dealId, GenerateArticleRequest(categoryId)).toDomain()
}
