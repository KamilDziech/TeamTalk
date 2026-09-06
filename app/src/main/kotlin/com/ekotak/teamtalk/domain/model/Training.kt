package com.ekotak.teamtalk.domain.model

/**
 * Model modułu SZKOLENIA — odwzorowanie `api/src/modules/training/domain`
 * z board360. Na telefon wchodzi wyłącznie widok PRACOWNIKA (zakładka
 * HR → Szkolenia): lista przypisań, lekcja z filmem, test i certyfikat.
 * Katalog lekcji, pytania i przypisywanie osób wymagają `training.manage`
 * i zostają w panelu (ustalenie 2026-09-06, `design/mockups/modul-szkolenia.html`).
 */

/** Stan przypisania — te same cztery wartości co `AssignmentStatus` w API. */
enum class AssignmentStatus(val wire: String, val label: String) {
    ASSIGNED("assigned", "Do zrobienia"),
    IN_PROGRESS("in_progress", "W toku"),
    PASSED("passed", "Zaliczone"),
    FAILED("failed", "Do poprawy"),
    ;

    companion object {
        fun from(wire: String?): AssignmentStatus =
            entries.firstOrNull { it.wire == wire } ?: ASSIGNED
    }
}

enum class QuestionKind(val wire: String) {
    SINGLE("single"),
    MULTI("multi"),
    ;

    companion object {
        fun from(wire: String?): QuestionKind = if (wire == "multi") MULTI else SINGLE
    }
}

/**
 * Pozycja listy „Moje szkolenia" (`GET /api/training/my`).
 *
 * `expiresAt` liczy serwer z `validityMonths` — aplikacja tylko porównuje je
 * z dzisiejszą datą, żeby zaliczone szkolenie po terminie ważności pokazać jako
 * „Do odnowienia". Własnej arytmetyki miesięcy tu nie ma i być nie powinno.
 */
data class TrainingAssignment(
    val id: String,
    val lessonId: String,
    val title: String,
    val description: String?,
    val status: AssignmentStatus,
    val score: Int?,
    val passThreshold: Int,
    val questionCount: Int,
    /** ISO-8601 albo `null` — termin wykonania nadany przy przypisaniu. */
    val dueDate: String?,
    val completedAt: String?,
    /** Do kiedy zaliczenie jest ważne (`null` = bezterminowo). */
    val expiresAt: String?,
    val assignedBy: String?,
)

/** Materiał lekcji — bez pytań, te idą osobno i bez klucza odpowiedzi. */
data class TrainingLesson(
    val id: String,
    val title: String,
    /** Pole „Transkrypcja" z panelu; bywa bardzo długie (do 100 tys. znaków). */
    val description: String?,
    val videoUrl: String,
    /** `youtube`, `loom` albo cokolwiek, co rozpoznał serwer przy zapisie. */
    val videoProvider: String,
    val passThreshold: Int,
    val validityMonths: Int?,
)

/** Pytanie w wersji dla playera — API nie wysyła poprawnych odpowiedzi. */
data class PlayableQuestion(
    val id: String,
    val prompt: String,
    val kind: QuestionKind,
    val options: List<String>,
)

/** Komplet do odtworzenia lekcji (`GET /api/training/lessons/:id/play`). */
data class PlayableLesson(
    val assignmentId: String,
    val lesson: TrainingLesson,
    val questions: List<PlayableQuestion>,
    val status: AssignmentStatus,
    val lastScore: Int?,
)

/** Odpowiedź na jedno pytanie — indeksy zaznaczonych opcji. */
data class TrainingAnswer(val questionId: String, val selected: List<Int>)

/** Wynik oceniony przez serwer (`POST /api/training/assignments/:id/submit`). */
data class GradeResult(
    val score: Int,
    val passed: Boolean,
    val passThreshold: Int,
    val correctCount: Int,
    val total: Int,
)
