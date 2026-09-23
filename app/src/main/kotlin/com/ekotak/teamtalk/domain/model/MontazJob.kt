package com.ekotak.teamtalk.domain.model

import com.ekotak.teamtalk.domain.montaz.MontazTool
import com.ekotak.teamtalk.domain.montaz.MontazToolGroup
import com.ekotak.teamtalk.domain.montaz.TOOL_GROUPS

/**
 * MODUŁ MONTAŻ — dzień montażysty, a nie karta deala.
 *
 * Zakładka „Montaż" w karcie deala (`DealMontazTab`) jest drogą HANDLOWCA: CRM →
 * deal → zakładka. Montażysta ma inny dzień — rano magazyn, potem dojazd, potem
 * robota, na koniec podpis klienta — i inne prawa: rola `montaz` widzi montaże,
 * ale nie kartotekę, nie umowę i nie katalog. Dlatego cała teczka wyjazdu
 * przychodzi z serwera jedną odpowiedzią (`GET /installations/{id}/job`),
 * złożoną tam, gdzie prawa są: klient, adres, zakres z PODPISANEJ umowy, obsada,
 * lista sprzętu i pytania protokołu.
 *
 * Makieta: design/mockups/modul-montaz.html.
 */

/** Wiersz listy „moje montaże" — tyle, ile widać przed otwarciem teczki. */
data class MontazJobRow(
    val id: String,
    val dealId: String,
    /** Krótki kod karty deala — po nim ekipa gada z biurem. */
    val dealCode: String?,
    val clientName: String,
    /** Adres montażu: z umowy, a w jej braku z kartoteki. */
    val address: String?,
    val city: String?,
    /** ISO-8601; `null` przy montażu bez ustalonego terminu. */
    val scheduledAt: String?,
    val status: MontazStatus,
    val durationDays: Int,
    val difficulty: DealDifficulty?,
    /** Ścieżki węzłów zakresu — „Ogrzewanie / Podłogówka". */
    val scopeNames: List<String>,
    /** Zmiana tego montażu czeka w kolejce (start roboty, protokół). */
    val pending: Boolean = false,
)

/** Osoba w obsadzie razem z imieniem — telefon nie ma skąd wziąć nazwisk. */
data class MontazJobAssignee(
    val userId: String,
    val name: String,
    val role: String?,
)

/** Pozycja Załącznika nr 1 podpisanej umowy — bez cen, ekipa ich nie potrzebuje. */
data class MontazJobScopeItem(
    val lp: Int,
    val opis: String,
    val ilosc: Double,
    val jm: String,
    val etap: Int,
)

/** CO JEST W CENIE — migawka podpisanej umowy, a nie przeliczenie z audytu. */
data class MontazJobContract(
    val numer: String,
    val podpisana: String?,
    val pozycje: List<MontazJobScopeItem>,
    /** Czego w cenie NIE MA (§ zakres wyłączony) — połowa ważniejsza dla ekipy. */
    val wylaczony: List<String>,
)

/**
 * TECZKA WYJAZDU — jeden montaż w komplecie.
 *
 * [fromCache] mówi, że to kopia z telefonu: na budowie bez zasięgu ta
 * informacja jest praktyczna, bo obsada mogła się zmienić w biurze godzinę temu.
 */
data class MontazJob(
    val row: MontazJobRow,
    val phone: String?,
    val lat: Double?,
    val lng: Double?,
    val teamNote: String?,
    val nodeIds: List<String>,
    val contract: MontazJobContract?,
    val assignees: List<MontazJobAssignee>,
    val tools: List<MontazTool>,
    val toolNotes: List<String>,
    val protocol: List<ProtocolQuestion>,
    val fromCache: Boolean = false,
) {
    /** Sprzęt w kolejności PAKOWANIA AUTA — lista czyta się przy klapie bagażnika. */
    val toolGroups: List<MontazToolGroup> get() = groupJobTools(tools)
}

/** Sekcje listy sprzętu z płaskiej listy z serwera (kolejność `TOOL_GROUPS`). */
fun groupJobTools(tools: List<MontazTool>): List<MontazToolGroup> {
    val extra = tools.map { it.group }.filter { it.isNotBlank() && it !in TOOL_GROUPS }.distinct()
    return (TOOL_GROUPS + extra)
        .map { group -> MontazToolGroup(group, tools.filter { it.group == group }) }
        .filter { it.items.isNotEmpty() }
}

// ── Protokół odbioru ─────────────────────────────────────────────────────────

/** Rodzaj odpowiedzi; `PHOTO` = samo zdjęcie, bez pola do wpisania. */
enum class ProtocolKind(val wire: String) {
    CHECK("check"),
    TEXT("text"),
    NUMBER("number"),
    CHOICE("choice"),
    PHOTO("photo");

    companion object {
        fun fromWire(value: String?): ProtocolKind =
            entries.firstOrNull { it.wire == value } ?: CHECK
    }
}

/** Wymóg zdjęcia przy pytaniu. */
enum class ProtocolPhoto(val wire: String) {
    NONE("none"),
    OPTIONAL("optional"),
    REQUIRED("required");

    companion object {
        fun fromWire(value: String?): ProtocolPhoto =
            entries.firstOrNull { it.wire == value } ?: NONE
    }
}

/**
 * Jedno pytanie protokołu. Pytania NIE są wpisane w kod telefonu — składają się
 * z węzłów katalogu objętych zakresem montażu (zakładka „📋 Protokół"), tak samo
 * jak lista sprzętu. Dzięki temu montaż podłogówki z pompą ciepła ma jeden
 * protokół, a biuro zmienia pytania bez wydawania nowej wersji aplikacji.
 */
data class ProtocolQuestion(
    val id: String,
    val label: String,
    val group: String,
    val kind: ProtocolKind,
    val unit: String,
    val options: List<String>,
    /** Bez odpowiedzi nie da się zamknąć protokołu. */
    val required: Boolean,
    val photo: ProtocolPhoto,
    /** Ile kadrów minimum przy [ProtocolPhoto.REQUIRED]; `null` = jeden. */
    val photoMin: Int?,
    /** Podpowiedź — co dokładnie sfotografować, co wpisać. */
    val hint: String,
    /** Z których węzłów zakresu pytanie przyszło. */
    val fromNodes: List<String> = emptyList(),
) {
    val photosNeeded: Int get() = if (photo == ProtocolPhoto.REQUIRED) (photoMin ?: 1) else 0
}

/**
 * Odpowiedź na pytanie. Rozbita na typy zamiast jednego `Any` — na budowie
 * liczy się, żeby pole liczbowe otwierało klawiaturę numeryczną, a nie żeby
 * model był elastyczny.
 */
data class ProtocolAnswer(
    val checked: Boolean? = null,
    val number: Double? = null,
    val text: String = "",
    val note: String = "",
    /** Zdjęcia montażu przypięte do tego pytania (id z galerii albo `local:…`). */
    val photoIds: List<String> = emptyList(),
)

/**
 * WYPEŁNIONY PROTOKÓŁ. Pytania zapisujemy RAZEM z odpowiedziami: schemat
 * w katalogu żyje dalej, a protokół sprzed pół roku ma się wydrukować tak, jak
 * go wypełniono — z pytaniami, które ekipa wtedy widziała.
 */
data class MontazProtocol(
    val installationId: String,
    val scope: List<String> = emptyList(),
    val client: String = "",
    val address: String = "",
    val technicians: String = "",
    val items: List<ProtocolQuestion> = emptyList(),
    val answers: Map<String, ProtocolAnswer> = emptyMap(),
    val issues: String = "",
    val accepted: Boolean = false,
    /** Imię i nazwisko odbierającego — drukuje się pod podpisem. */
    val clientName: String = "",
    /** Podpis palcem jako data URL (`data:image/png;base64,…`). */
    val signature: String? = null,
    /** ISO; `null` = wersja robocza, protokół jeszcze niezamknięty. */
    val closedAt: String? = null,
    /** Zapis czeka w kolejce — biuro jeszcze go nie widzi. */
    val pending: Boolean = false,
) {
    val closed: Boolean get() = closedAt != null
}

/** Wersja kształtu `formData` — po niej panel i PDF poznają nowy protokół. */
const val PROTOCOL_SCHEMA_VERSION = 2

/** Czy pytanie ma odpowiedź (pusty tekst i brak wyboru się nie liczą). */
fun ProtocolQuestion.answered(a: ProtocolAnswer?): Boolean = when (kind) {
    ProtocolKind.PHOTO -> (a?.photoIds?.size ?: 0) > 0
    ProtocolKind.CHECK -> a?.checked != null
    ProtocolKind.NUMBER -> a?.number != null
    else -> !a?.text.isNullOrBlank()
}

/** Czy dołożono tyle kadrów, ile pytanie wymaga. */
fun ProtocolQuestion.photosOk(a: ProtocolAnswer?): Boolean =
    (a?.photoIds?.size ?: 0) >= photosNeeded

/**
 * Czego brakuje do zamknięcia protokołu — LISTA pytań, nie jeden komunikat.
 * „Uzupełnij formularz" na końcu roboty jest bezużyteczne; ekipa ma wiedzieć,
 * przy którym pytaniu stanąć.
 */
fun MontazProtocol.missing(): List<ProtocolQuestion> = items.filter { q ->
    val a = answers[q.id]
    (q.required && !q.answered(a)) || !q.photosOk(a)
}

/** Licznik „6 / 9" nad listą — ile wymaganych pytań jest gotowych. */
fun MontazProtocol.progress(): Pair<Int, Int> {
    val required = items.filter { it.required || it.photo == ProtocolPhoto.REQUIRED }
    val done = required.count { q ->
        val a = answers[q.id]
        (!q.required || q.answered(a)) && q.photosOk(a)
    }
    return done to required.size
}

/** Sekcje w kolejności pojawiania się — protokół czyta się od góry do dołu. */
fun MontazProtocol.groups(): List<Pair<String, List<ProtocolQuestion>>> {
    val order = LinkedHashSet(items.map { it.group })
    return order.map { g -> g to items.filter { it.group == g } }
}
