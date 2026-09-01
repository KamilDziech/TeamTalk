package com.ekotak.teamtalk.domain.usecase.task

import com.ekotak.teamtalk.domain.model.Discussion
import com.ekotak.teamtalk.domain.model.DiscussionThread
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskAttachment
import com.ekotak.teamtalk.domain.model.TaskComment
import com.ekotak.teamtalk.domain.repository.DiscussionRepository
import com.ekotak.teamtalk.domain.repository.TaskRepository
import java.io.File
import javax.inject.Inject

/** Jedno zadanie po id — wejście w kartę z powiadomienia albo z dyskusji. */
class GetTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(id: String): Task = taskRepository.getTask(id)
}

/** Komentarze karty zadania (one same są wątkiem dyskusji). */
class GetTaskCommentsUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(taskId: String): List<TaskComment> =
        taskRepository.getComments(taskId)
}

/**
 * Komentarz z wywołaniami. Tokeny wywołań idą osobno od tekstu — backend
 * rozwija je do osób i wciąga zadanie do skrzynki każdej z nich.
 */
class AddTaskCommentUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(
        taskId: String,
        body: String,
        mentions: List<String>,
    ): TaskComment = taskRepository.addComment(taskId, body, mentions)
}

/** Skrzynka Komunikatora — dyskusje, w których bierzemy udział. */
class ListDiscussionsUseCase @Inject constructor(
    private val discussionRepository: DiscussionRepository,
) {
    suspend operator fun invoke(): List<Discussion> = discussionRepository.listDiscussions()
}

/** Licznik nieprzeczytanych — plakietka na pulpicie i powiadomienia. */
class GetUnreadDiscussionsUseCase @Inject constructor(
    private val discussionRepository: DiscussionRepository,
) {
    suspend operator fun invoke(): Int = discussionRepository.unreadCount()
}

class GetDiscussionThreadUseCase @Inject constructor(
    private val discussionRepository: DiscussionRepository,
) {
    suspend operator fun invoke(taskId: String): DiscussionThread =
        discussionRepository.getThread(taskId)
}

/** Oznaczenie dyskusji jako przeczytanej (wejście w wątek). */
class MarkDiscussionReadUseCase @Inject constructor(
    private val discussionRepository: DiscussionRepository,
) {
    suspend operator fun invoke(taskId: String) = discussionRepository.markRead(taskId)
}

/** Załączniki karty zadania (metadane). */
class GetTaskAttachmentsUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(taskId: String): List<TaskAttachment> =
        taskRepository.getAttachments(taskId)
}

/** Wgranie pliku do zadania — bajty czyta ekran, tu jedzie gotowa zawartość. */
class UploadTaskAttachmentUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(
        taskId: String,
        name: String,
        contentType: String,
        bytes: ByteArray,
    ): TaskAttachment = taskRepository.uploadAttachment(taskId, name, contentType, bytes)
}

/** Pobranie treści załącznika do pliku (cache aplikacji) — do otwarcia w systemie. */
class DownloadTaskAttachmentUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(id: String, target: File) =
        taskRepository.downloadAttachmentTo(id, target)
}

class DeleteTaskAttachmentUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(id: String) = taskRepository.deleteAttachment(id)
}
