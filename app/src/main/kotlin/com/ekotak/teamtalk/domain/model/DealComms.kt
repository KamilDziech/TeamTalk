package com.ekotak.teamtalk.domain.model

/**
 * Zakładka „Komunikacja" karty deala — hub kanałów zawężony do JEDNEGO deala,
 * 1:1 z `DealCommsPanel` panelu.
 *
 * Kanały są cztery działające plus SMS w przygotowaniu. Każdy pokazuje wyłącznie
 * to, co dotyczy tego deala:
 *  • Komunikator — wewnętrzny wątek zespołu o dealu (`/discussions/deal/:id`),
 *    ten sam byt co komentarze zadania, tylko kluczowany dealem,
 *  • Email — korespondencja dowiązana do deala (`/email/threads?dealId=`),
 *    czytana i pisana ekranami modułu Email,
 *  • WhatsApp — skrzynka deala (`/deals/:id/whatsapp`),
 *  • Telefon — streszczenia rozmów przypięte do deala (`/voice-reports?dealId=`),
 *  • SMS — zaślepka, dopóki nie ma kredencji bramki (tak samo jak w panelu).
 */
enum class CommChannel(val wire: String, val label: String, val icon: String) {
    KOMUNIKATOR("internal", "Komunikator", "🗨"),
    EMAIL("email", "Email", "✉"),
    WHATSAPP("whatsapp", "WhatsApp", "💬"),
    TELEFON("telefon", "Telefon", "📞"),
    SMS("sms", "SMS", "📱"),
}

/**
 * Wiadomość wewnętrznego wątku deala. To ten sam rekord, co komentarz zadania
 * (board360 trzyma oba w `TaskComment`, kluczując wątek deala jego id), więc
 * wywołania przez „@" działają tu dokładnie tak samo.
 */
data class DealComment(
    val id: String,
    val authorId: String,
    /** „Imię Nazwisko" albo e-mail — podpisuje backend. */
    val authorName: String,
    val body: String,
    val createdAt: String,
    /** Czy to wiadomość zalogowanego — decyduje o stronie dymka. */
    val mine: Boolean,
    /** Czeka w kolejce offline; dymek dostaje znacznik „w kolejce". */
    val pending: Boolean = false,
)

/** Kierunek wiadomości WhatsApp — od klienta albo do klienta. */
enum class WhatsappDirection(val wire: String) {
    INBOUND("inbound"),
    OUTBOUND("outbound");

    companion object {
        fun fromWire(value: String?): WhatsappDirection =
            entries.firstOrNull { it.wire == value } ?: OUTBOUND
    }
}

/**
 * Wiadomość ze skrzynki WhatsApp deala. `status` niesie stan po stronie Meta;
 * `pending_config` (brak kredencji) to NIE błąd — wiadomość jest zapisana
 * i pójdzie po podpięciu integracji, dokładnie jak w panelu.
 */
data class WhatsappMessage(
    val id: String,
    val direction: WhatsappDirection,
    val body: String?,
    val template: String?,
    val status: String,
    val createdAt: String,
    /** Czeka w kolejce TELEFONU — serwer o niej jeszcze nie wie. */
    val pending: Boolean = false,
) {
    /** Treść do pokazania: tekst, a przy szablonie jego nazwa. */
    val display: String
        get() = body?.takeIf { it.isNotBlank() }
            ?: template?.let { "[szablon: $it]" }
            ?: "—"

    companion object {
        /** Statusy board360 po polsku — te same napisy co w panelu. */
        val STATUS_LABEL: Map<String, String> = mapOf(
            "received" to "odebrana",
            "queued" to "w kolejce",
            "sent" to "wysłana",
            "delivered" to "dostarczona",
            "failed" to "błąd",
            "pending_config" to "oczekuje (brak konfiguracji Meta)",
        )

        /** Zapisana na telefonie bez zasięgu — czeka na opróżnienie kolejki. */
        const val STATUS_QUEUED_LOCAL = "queued_local"
    }
}

/**
 * Streszczenie rozmowy telefonicznej przypięte do deala.
 *
 * Lista pokazuje WYŁĄCZNIE wpisy z `dealId` tego deala — pełny rejestr połączeń
 * klienta (w tym to, co TeamTalk zapisał sam, nie znając deala) jest w module
 * Komunikacja i na karcie klienta. Tak samo działa panel.
 */
data class DealCallSummary(
    val id: String,
    /** Przebieg rozmowy: tekst notatki albo transkrypcja nagrania. */
    val body: String,
    /** Ustalenia — tylko przy streszczeniu dopisanym ręcznie. */
    val agreements: String?,
    /** Następny krok po rozmowie — jw. */
    val nextStep: String?,
    val phoneNumber: String?,
    val direction: String?,
    /** Kiedy rozmowa się odbyła (albo kiedy zapisano wpis). */
    val occurredAt: String,
    val durationSec: Int?,
    val hasRecording: Boolean,
    /** Dopisane z ręki, nie z nagrania TeamTalka. */
    val manual: Boolean,
    /** Czeka w kolejce offline. */
    val pending: Boolean = false,
)

/** Treść okna „dopisz streszczenie rozmowy" — 1:1 z `CallSummaryModal` panelu. */
data class CallSummaryDraft(
    val text: String = "",
    val agreements: String = "",
    val nextStep: String = "",
    /** Czy z następnego kroku założyć zadanie dla siebie. */
    val createTask: Boolean = false,
    /** Termin zadania (ISO `yyyy-MM-dd`); pusty = bez terminu. */
    val taskDueAt: String = "",
) {
    val hasNextStep: Boolean get() = nextStep.isNotBlank()
    val canSave: Boolean get() = text.isNotBlank()
}

/** Co się stało z wysyłką — poszła na serwer czy czeka w kolejce telefonu. */
enum class CommsSendResult { SENT, QUEUED }

/** Wynik przebiegu kolejki komunikacji. */
enum class CommsSyncResult { DONE, RETRY }
