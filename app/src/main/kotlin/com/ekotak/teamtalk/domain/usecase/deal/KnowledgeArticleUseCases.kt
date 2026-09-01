package com.ekotak.teamtalk.domain.usecase.deal

import com.ekotak.teamtalk.domain.model.ArticleGate
import com.ekotak.teamtalk.domain.model.KnowledgeArticle
import com.ekotak.teamtalk.domain.repository.DealMessageRepository
import com.ekotak.teamtalk.domain.repository.KnowledgeArticleRepository
import javax.inject.Inject

/**
 * Artykuł wiedzy dla instalacji wybranej na etapie LEAD: odczyt, generowanie
 * i wysłanie klientowi. Trzy operacje jednego ekranu, więc jeden plik — tak jak
 * przy `LeadTabUseCases`.
 */

class GetArticleGateUseCase @Inject constructor(
    private val repository: KnowledgeArticleRepository,
) {
    suspend operator fun invoke(dealId: String): ArticleGate = repository.getGate(dealId)
}

class GetKnowledgeArticlesUseCase @Inject constructor(
    private val repository: KnowledgeArticleRepository,
) {
    suspend operator fun invoke(dealId: String): List<KnowledgeArticle> =
        repository.listArticles(dealId)
}

class GenerateKnowledgeArticleUseCase @Inject constructor(
    private val repository: KnowledgeArticleRepository,
) {
    suspend operator fun invoke(dealId: String, categoryId: String): KnowledgeArticle =
        repository.generate(dealId, categoryId)
}

/**
 * Wysyłka artykułu klientowi wątkiem WhatsApp deala. Treść składamy tutaj,
 * a nie na ekranie: to samo wyszłoby z każdego miejsca, które kiedyś zechce
 * wysłać artykuł, a wiadomość zostaje w historii deala i czyta ją potem cały
 * zespół — więc jej kształt jest decyzją domeny, nie widoku.
 *
 * Uwaga: poza oknem 24h od ostatniej wiadomości klienta API odrzuca wysyłkę
 * free-form (422). Nie obchodzimy tego szablonem — reguła jest po stronie
 * WhatsApp Business, a nie nasza.
 */
class SendArticleToClientUseCase @Inject constructor(
    private val messages: DealMessageRepository,
) {
    suspend operator fun invoke(dealId: String, article: KnowledgeArticle) {
        val body = buildString {
            append(article.title.trim())
            append("\n\n")
            append(article.bodyMarkdown.trim())
        }
        messages.sendWhatsApp(dealId, body)
    }
}
