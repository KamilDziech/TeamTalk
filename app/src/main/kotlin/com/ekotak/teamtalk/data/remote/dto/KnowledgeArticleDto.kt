package com.ekotak.teamtalk.data.remote.dto

import com.ekotak.teamtalk.domain.model.ArticleGate
import com.ekotak.teamtalk.domain.model.KnowledgeArticle
import kotlinx.serialization.Serializable

/**
 * Artykuł wiedzy deala (`/api/deals/:id/knowledge-articles`). `costTiers`
 * pomijamy — to widełki kosztów renderowane tabelą w panelu; na telefonie
 * artykuł czyta się liniowo, a niesparsowane pole i tak nie miałoby gdzie
 * trafić.
 */
@Serializable
data class KnowledgeArticleDto(
    val id: String = "",
    val categoryId: String = "",
    val title: String = "",
    val bodyMarkdown: String = "",
    val status: String = "",
    val llmGenerated: Boolean = false,
    val version: Int = 1,
    val generatedAt: String? = null,
)

/** Bramka generowania — `{ready, reasons[]}` z gotowymi zdaniami po polsku. */
@Serializable
data class ArticleGateDto(
    val ready: Boolean = false,
    val reasons: List<String> = emptyList(),
)

@Serializable
data class GenerateArticleRequest(val categoryId: String)

/** Pełny wybór instalacji etapu (`PUT /api/deals/:id/installations/:stage`). */
@Serializable
data class SetInstallationsRequest(val categoryIds: List<String>)

/** Wiadomość wychodząca na wątku deala (`POST /api/deals/:id/whatsapp`). */
@Serializable
data class SendWhatsappRequest(val body: String)

fun KnowledgeArticleDto.toDomain(): KnowledgeArticle = KnowledgeArticle(
    id = id,
    categoryId = categoryId,
    title = title,
    bodyMarkdown = bodyMarkdown,
    status = status,
    llmGenerated = llmGenerated,
    version = version,
    generatedAt = generatedAt,
)

fun ArticleGateDto.toDomain(): ArticleGate = ArticleGate(ready = ready, reasons = reasons)
