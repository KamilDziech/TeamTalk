package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.model.ChatKind
import com.ekotak.teamtalk.domain.repository.ChatRepository
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Powiadomienia Komunikatora.
 *
 * board360 nie wysyła jeszcze pusha (projekt Firebase dopiero przed nami), więc
 * pytamy sami — co 15 minut, bo krócej WorkManager i tak nie pozwala. To jest
 * TYMCZASOWE: gdy dojdzie FCM, ten robotnik zostaje wyłącznie jako siatka
 * bezpieczeństwa na telefony z ubitym kanałem pusha.
 *
 * Trąbimy o rozmowach z nieprzeczytanymi, których ostatnia wiadomość jest
 * NOWSZA niż ostatnie pokazane powiadomienie — inaczej ta sama wiadomość
 * wracałaby co kwadrans do skutku. Wyciszone rozmowy pomijamy, a z pozostałych
 * bierzemy wiadomości prywatne (rozmowa dwóch osób jest zawsze do mnie) oraz
 * wywołania przez „@" w grupach i wątkach zadań — reszta grupowego ruchu
 * zostaje przy liczniku w skrzynce, bez wibrowania telefonem.
 *
 * Bez sesji nie ma czego pytać: robotnik kończy się sukcesem, żeby WorkManager
 * nie próbował w kółko po wylogowaniu.
 */
@HiltWorker
class MentionsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val chat: ChatRepository,
    private val sessionPreferences: SessionPreferences,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "mentions_poll"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        val seenAt = sessionPreferences.mentionsSeenAt.first()
        val threads = runCatching { chat.listThreads() }.getOrElse { return Result.retry() }

        var newest = seenAt
        for (thread in threads) {
            val message = thread.lastMessage ?: continue
            val at = parseIsoMillis(message.createdAt) ?: continue
            if (at > newest) newest = at

            if (thread.muted || thread.unreadCount == 0 || at <= seenAt) continue
            // Pierwsze uruchomienie (seenAt == 0) nie zasypuje szuflady zaległą
            // korespondencją — zapamiętujemy stan i trąbimy od następnego razu.
            if (seenAt == 0L) continue
            val worthNotifying = thread.kind == ChatKind.DIRECT || thread.mentionedMe
            if (!worthNotifying) continue

            notifications.showChatNotification(
                title = thread.title,
                teaser = if (thread.kind == ChatKind.DIRECT) {
                    message.preview
                } else {
                    "${message.authorName}: ${message.preview}"
                },
                threadId = thread.id,
            )
        }

        if (newest > seenAt) sessionPreferences.saveMentionsSeenAt(newest)
        return Result.success()
    }
}
