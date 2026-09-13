package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Zakładka „Montaż" karty deala — kontrakt z board360.
 *
 * Wszystko tu jest 1:1 z modułem `installations` panelu: karta montażu w web
 * czyta te same trasy (`/api/installations…`), więc rozjazd pól znaczyłby, że
 * ekipa na budowie i koordynator w biurze patrzą na dwie różne roboty.
 *
 * Montaż deala PEŁNY, a nie skrócony jak w [InstallationDto] zakładki „Faktura":
 * tam wystarczał termin i status do listy, tu potrzebny jest zakres (`nodeIds`),
 * obsada z rolami i ślad odprawy — z nich bierze się cała reszta karty.
 */
@Serializable
data class MontazDto(
    val id: String = "",
    val dealId: String = "",
    val scheduledAt: String? = null,
    val status: String = "planned",
    val difficulty: String? = null,
    val teamNote: String? = null,
    /** Zakres — węzły drzewa instalacji deala objęte TĄ robotą. */
    val nodeIds: List<String> = emptyList(),
    val crewId: String? = null,
    /** Obsada z rolami; starszy backend oddaje samo [assigneeIds]. */
    val assignees: List<MontazAssigneeDto> = emptyList(),
    val assigneeIds: List<String> = emptyList(),
    val briefedAt: String? = null,
    val briefingMessageId: String? = null,
    /** Ile dni roboczych zajmuje wyjazd (domyślnie 2, tak jak w panelu). */
    val durationDays: Int? = null,
)

@Serializable
data class MontazAssigneeDto(
    val userId: String = "",
    val role: String? = null,
)

// Ciało `PATCH /api/installations/{id}` budujemy jako `JsonObject`
// (`MontazRepositoryImpl`), a nie jako DTO: API rozróżnia BRAK pola od jawnego
// `null`, a wspólny `Json` aplikacji ma `explicitNulls = false` — odpięcie ekipy
// (`crewId: null`) wypadłoby wtedy z ciała i nic by się nie zmieniło.
//
// TERMINU stąd nie ruszamy, tak samo jak panel: przesunięcie montażu widzi
// pojemność okien, a tę pokazuje wyłącznie moduł „Montaże" (decyzja usera
// 2026-09-01).

/** Ciało `POST /api/installations` — nowy etap robót pod tym samym dealem. */
@Serializable
data class MontazCreateRequest(
    val dealId: String,
    val scheduledAt: String,
    val nodeIds: List<String>? = null,
    val crewId: String? = null,
    val teamNote: String? = null,
)

/** Ekipa z modułu Zespół — skrót do obsady (`GET /api/installations/crews`). */
@Serializable
data class MontazCrewDto(
    val id: String = "",
    val name: String = "",
    val color: String? = null,
    val leaderId: String? = null,
    val memberIds: List<String> = emptyList(),
)

/**
 * Linia listy wyjazdowej — pozycja odłożona w magazynie pod deal montażu
 * (`GET /api/installations/{id}/deal-materials`).
 *
 * `covered` / `missing` liczy serwer ze stanu magazynu. Telefon ich NIE liczy:
 * stan zmienia się przy każdym wydaniu w hali, a druga implementacja tej samej
 * arytmetyki rozjechałaby się z magazynierem w tydzień.
 */
@Serializable
data class MontazMaterialDto(
    val id: String = "",
    val itemName: String = "",
    val itemCode: String? = null,
    val quantity: Double = 0.0,
    val unit: String = "szt.",
    val status: String = "active",
    val covered: Double = 0.0,
    val missing: Double = 0.0,
    val issuedAt: String? = null,
    val issuedById: String? = null,
    val note: String? = null,
)

/** Ciało wydania materiału na budowę — id rezerwacji, nie pozycji katalogu. */
@Serializable
data class MontazIssueRequest(val reservationIds: List<String>)

/** Odpowiedź wydania: ile linii faktycznie poszło na budowę. */
@Serializable
data class MontazIssueResponse(val issued: Int = 0)

/** Metadane zdjęcia powykonawczego (`GET /api/installations/{id}/photos`). */
@Serializable
data class MontazPhotoDto(
    val id: String = "",
    val installationId: String = "",
    val contentType: String = "image/jpeg",
    val size: Long = 0,
    val caption: String? = null,
    val createdAt: String = "",
)

/**
 * Odprawa montażu — komunikat modułu Odprawy (`POST /api/briefing`).
 *
 * Odbiorcami są KONKRETNE OSOBY z obsady, nie ekipa jako grupa: jadą ci ludzie,
 * a ich potwierdzenia mają się zgadzać z listą obecności na budowie. Publikacja
 * wymaga `briefing.publish` — telefon tego nie sprawdza, odmowę oddaje serwer.
 */
@Serializable
data class BriefingCreateRequest(
    val title: String,
    val body: String,
    val priority: String = "normal",
    val requiresAck: Boolean = true,
    val audienceKind: String = "users",
    val audienceRoles: List<String> = emptyList(),
    val audienceCrewIds: List<String> = emptyList(),
    val audienceUserIds: List<String> = emptyList(),
    val expiresAt: String? = null,
)

@Serializable
data class BriefingCreatedDto(
    val id: String = "",
    val recipients: Int = 0,
)

/** Wiersz potwierdzeń komunikatu — `ackAt = null` znaczy „jeszcze nie odhaczył". */
@Serializable
data class BriefingReceiptDto(
    val userId: String = "",
    val name: String = "",
    val role: String = "",
    val ackAt: String? = null,
)
