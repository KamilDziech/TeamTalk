package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.GradeResult
import com.ekotak.teamtalk.domain.model.MySkills
import com.ekotak.teamtalk.domain.model.PlayableLesson
import com.ekotak.teamtalk.domain.model.TrainingAnswer
import com.ekotak.teamtalk.domain.model.TrainingAssignment
import java.io.File

/**
 * Szkolenia pracownika. **Bez cache i bez kolejki offline** (ustalenie
 * 2026-09-06): film i tak wymaga sieci, a test ocenia serwer — odpowiedzi
 * leżące w kolejce znaczyłyby wynik nieznany do powrotu zasięgu, czyli
 * najgorszy możliwy stan dla kogoś, kto właśnie skończył test.
 *
 * Metody rzucają wyjątkami (`IOException` = brak sieci, `HttpException` =
 * odmowa serwera); rozróżnia je warstwa prezentacji, bo 403 przy nieprzypisanej
 * lekcji to inny ekran niż brak połączenia.
 */
interface TrainingRepository {
    /** Przypisania zalogowanego — `GET /api/training/my`. */
    suspend fun listMyTrainings(): List<TrainingAssignment>

    /** Lekcja z pytaniami bez klucza; 403, gdy nie jest przypisana. */
    suspend fun getPlayable(lessonId: String): PlayableLesson

    /** Wysyła odpowiedzi i zwraca ocenę policzoną przez serwer. */
    suspend fun submit(assignmentId: String, answers: List<TrainingAnswer>): GradeResult

    /** Pobiera certyfikat PDF do wskazanego pliku (tylko przy zaliczeniu). */
    suspend fun downloadCertificate(lessonId: String, target: File)

    /** Poziomy i wymogi zalogowanego — `GET /api/domain-skills/me`. */
    suspend fun getMySkills(): MySkills
}
