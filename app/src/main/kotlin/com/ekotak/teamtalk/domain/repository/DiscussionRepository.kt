package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Discussion
import com.ekotak.teamtalk.domain.model.DiscussionThread

/**
 * Komunikator wewnętrzny. Dyskusja = wątek komentarzy jednego zadania, więc
 * ten interfejs celowo NIE ma tworzenia rozmowy: rozmowa zaczyna się od
 * wywołania kogoś przez @ w komentarzu zadania.
 *
 * Bez cache Room — skrzynka ma sens tylko na świeżych danych, a listę zadań
 * (z której wchodzi się w kartę) i tak trzymamy lokalnie.
 */
interface DiscussionRepository {
    suspend fun listDiscussions(): List<Discussion>

    /** Licznik nieprzeczytanych — plakietka i robotnik powiadomień. */
    suspend fun unreadCount(): Int

    suspend fun getThread(taskId: String): DiscussionThread

    /** Znacznik przeczytania — zeruje licznik dla tej dyskusji. */
    suspend fun markRead(taskId: String)

    /** Odpowiedź w skrzynce; ląduje też jako komentarz pod zadaniem. */
    suspend fun reply(taskId: String, body: String, mentions: List<String>)
}
