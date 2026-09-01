package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.ArticleGate
import com.ekotak.teamtalk.domain.model.KnowledgeArticle

/**
 * Artykuły wiedzy karty deala. Bez cache: artykuł zmienia się przy każdej
 * regeneracji (także z panelu), a wysłanie klientowi nieaktualnej wersji
 * kosztowałoby więcej niż jedno zapytanie po wejściu na ekran.
 */
interface KnowledgeArticleRepository {
    /** Czy deal ma komplet danych do wygenerowania artykułu. */
    suspend fun getGate(dealId: String): ArticleGate

    /** Artykuły wygenerowane dla deala — po jednym na instalację. */
    suspend fun listArticles(dealId: String): List<KnowledgeArticle>

    /** Generuje (lub odświeża) artykuł dla wskazanego węzła katalogu. */
    suspend fun generate(dealId: String, categoryId: String): KnowledgeArticle
}
