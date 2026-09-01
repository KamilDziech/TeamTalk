package com.ekotak.teamtalk.domain.model

/**
 * Artykuł wiedzy dla jednej instalacji deala (`/api/deals/:id/knowledge-articles`).
 * Składany po stronie API z wiedzy węzła katalogu, danych budynku ze zgłoszenia
 * i strefy klimatycznej — telefon go tylko czyta, generuje i wysyła klientowi.
 */
data class KnowledgeArticle(
    val id: String,
    val categoryId: String,
    val title: String,
    val bodyMarkdown: String,
    val status: String = "",
    val llmGenerated: Boolean = false,
    val version: Int = 1,
    val generatedAt: String? = null,
)

/**
 * Bramka generowania: etap ≥ kwalifikacja i komplet danych budynku. `reasons`
 * to gotowe zdania po polsku prosto z API — pokazujemy je dosłownie, bo mówią,
 * czego konkretnie brakuje.
 */
data class ArticleGate(
    val ready: Boolean = false,
    val reasons: List<String> = emptyList(),
)
