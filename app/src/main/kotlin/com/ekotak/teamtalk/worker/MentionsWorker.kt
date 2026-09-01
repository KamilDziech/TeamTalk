package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.repository.DiscussionRepository
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Powiadomienia o wywołaniach (@) w komentarzach zadań.
 *
 * board360 nie wysyła pusha, więc pytamy sami — co 15 minut (krócej WorkManager
 * i tak nie pozwala), wzorem skanera nieodebranych połączeń. Trąbimy tylko o
 * dyskusjach z nieprzeczytanymi, których ostatni komentarz jest NOWSZY niż
 * ostatnie pokazane powiadomienie — inaczej ten sam komentarz wracałby co
 * kwadrans do skutku.
 *
 * Bez sesji nie ma czego pytać: robotnik kończy się sukcesem, żeby WorkManager
 * nie próbował w kółko po wylogowaniu.
 */
@HiltWorker
class MentionsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val discussions: DiscussionRepository,
    private val sessionPreferences: SessionPreferences,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "mentions_poll"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        val seenAt = sessionPreferences.mentionsSeenAt.first()
        val list = runCatching { discussions.listDiscussions() }
            .getOrElse { return Result.retry() }

        var newest = seenAt
        for (discussion in list) {
            val comment = discussion.lastComment ?: continue
            val at = parseIsoMillis(comment.createdAt) ?: continue
            if (at > newest) newest = at
            if (discussion.unreadCount == 0 || at <= seenAt) continue
            // Pierwsze uruchomienie (seenAt == 0) nie zasypuje szuflady zaległą
            // korespondencją — zapamiętujemy stan i trąbimy dopiero od następnego.
            if (seenAt == 0L) continue
            notifications.showMentionNotification(
                title = discussion.title,
                teaser = "${comment.authorName}: ${comment.body}",
                taskId = discussion.taskId,
            )
        }

        if (newest > seenAt) sessionPreferences.saveMentionsSeenAt(newest)
        return Result.success()
    }
}
