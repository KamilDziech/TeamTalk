package com.ekotak.teamtalk.domain.model

/**
 * Komentarz pod zadaniem. To ten sam byt, co wiadomość w Komunikatorze —
 * dyskusja JEST wątkiem komentarzy zadania (ustalenia 2026-09-01), więc
 * odpowiedź napisana w skrzynce wraca tu jako komentarz i odwrotnie.
 */
data class TaskComment(
    val id: String,
    val authorId: String,
    /** „Imię Nazwisko" albo e-mail — podpisuje backend. */
    val authorName: String,
    val body: String,
    val createdAt: String,
    /** Czy to komentarz zalogowanego — decyduje o stronie dymka. */
    val mine: Boolean,
)

/**
 * Załącznik karty zadania. Treść leży po stronie serwera — telefon pobiera ją
 * dopiero na żądanie, do pliku tymczasowego, i oddaje systemowi do otwarcia.
 */
data class TaskAttachment(
    val id: String,
    val name: String,
    val size: Long,
    val contentType: String?,
    /** „Imię Nazwisko" albo e-mail osoby, która plik wgrała. */
    val uploaderName: String?,
    val createdAt: String,
) {
    /** „412 kB" / „2,4 MB" — rozmiar w podpisie pliku. */
    val sizeLabel: String
        get() = when {
            size <= 0 -> ""
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} kB"
            else -> String.format(java.util.Locale("pl"), "%.1f MB", size / (1024.0 * 1024))
        }
}

/**
 * Pozycja skrzynki Komunikatora. [title] to nazwa KLIENTA („Nowak · a3dc"),
 * [taskTitle] zostaje zajawką — po tym poznaje się, o które zadanie chodzi.
 */
data class Discussion(
    val taskId: String,
    val taskTitle: String,
    val title: String,
    val clientName: String?,
    val dealId: String?,
    val dealCode: String?,
    val projectName: String?,
    val lastComment: TaskComment?,
    val commentCount: Int,
    val unreadCount: Int,
    /** Czy ktoś wywołał tu zalogowanego przez @ — takie wątki idą na górę. */
    val mentionedMe: Boolean,
)

/** Pełny wątek dyskusji: nagłówek jak w [Discussion] plus komentarze. */
data class DiscussionThread(
    val taskId: String,
    val taskTitle: String,
    val title: String,
    val clientName: String?,
    val dealId: String?,
    val projectName: String?,
    val comments: List<TaskComment>,
)

/**
 * Wywołanie w komentarzu. Panel wstawia do tekstu „@Imię Nazwisko", a osobno
 * wysyła token — dzięki temu zmiana nazwiska nie psuje starych wzmianek.
 */
sealed interface MentionTarget {
    val token: String
    val label: String

    data class Person(val member: TaskMember) : MentionTarget {
        override val token: String get() = "user:${member.id}"
        override val label: String get() = member.displayName
    }

    /** Grupa (rola, obserwujący, wszyscy) — backend rozwija ją do osób. */
    data class Group(override val token: String, override val label: String, val hint: String) :
        MentionTarget
}

/** Grupy do wywołania — ten sam zestaw, co `DEFAULT_MENTION_GROUPS` w panelu. */
val DEFAULT_MENTION_GROUPS: List<MentionTarget.Group> = listOf(
    MentionTarget.Group("role:zarzad", "Zarząd", "Grupa: Zarząd"),
    MentionTarget.Group("role:koordynator", "Koordynatorzy", "Grupa: Koordynatorzy"),
    MentionTarget.Group("role:serwisant", "Serwis", "Grupa: Serwis"),
    MentionTarget.Group("role:biuro", "Biuro", "Grupa: Biuro"),
    MentionTarget.Group("role:montaz", "Monterzy", "Grupa: Monterzy"),
    MentionTarget.Group("watchers", "obserwujący", "Osoby biorące udział w dyskusji"),
    MentionTarget.Group("all", "wszyscy", "Wszyscy z dostępem"),
)
