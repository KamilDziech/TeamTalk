package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.SubmitTrainingRequest
import com.ekotak.teamtalk.data.remote.dto.TrainingAnswerDto
import com.ekotak.teamtalk.domain.model.GradeResult
import com.ekotak.teamtalk.domain.model.MySkills
import com.ekotak.teamtalk.domain.model.PlayableLesson
import com.ekotak.teamtalk.domain.model.TrainingAnswer
import com.ekotak.teamtalk.domain.model.TrainingAssignment
import com.ekotak.teamtalk.domain.repository.TrainingRepository
import java.io.File
import javax.inject.Inject

/**
 * Czysta warstwa sieciowa — moduł działa wyłącznie online, więc nie ma tu ani
 * DAO, ani schedulera synchronizacji. Wyjątki lecą wyżej nietknięte: ekran musi
 * odróżnić 403 („to szkolenie nie jest Ci przypisane") od braku zasięgu.
 */
class TrainingRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
) : TrainingRepository {

    override suspend fun listMyTrainings(): List<TrainingAssignment> =
        api.getMyTrainings().map { it.toDomain() }

    override suspend fun getPlayable(lessonId: String): PlayableLesson =
        api.getPlayableLesson(lessonId).toDomain()

    override suspend fun submit(
        assignmentId: String,
        answers: List<TrainingAnswer>,
    ): GradeResult = api.submitTraining(
        assignmentId,
        SubmitTrainingRequest(answers.map { TrainingAnswerDto(it.questionId, it.selected) }),
    ).toDomain()

    /** Strumień prosto do pliku — certyfikat idzie potem do systemowego czytnika. */
    override suspend fun downloadCertificate(lessonId: String, target: File) {
        api.downloadTrainingCertificate(lessonId).byteStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    override suspend fun getMySkills(): MySkills = api.getMySkills().data.toDomain()
}
