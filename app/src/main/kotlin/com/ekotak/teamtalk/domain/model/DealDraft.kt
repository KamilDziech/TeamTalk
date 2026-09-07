package com.ekotak.teamtalk.domain.model

/**
 * Formularz edycji karty deala — wartości „takie, jak mają być po zapisie".
 *
 * `PATCH /api/deals/:id` rozróżnia trzy sytuacje: pole nieobecne = bez zmian,
 * `null` = wyczyść, wartość = ustaw. Draft niesie tylko stan docelowy; różnicę
 * względem oryginału (a więc i to, które pola w ogóle trafią do żądania) liczy
 * warstwa danych. Dzięki temu ekran nie musi śledzić „co użytkownik dotknął",
 * a jednocześnie nie nadpisujemy w bazie pól, których nikt nie ruszał.
 *
 * Puste pole tekstowe = `null` = wyczyszczenie wartości.
 */
data class DealDraft(
    // ── Dane podstawowe ──────────────────────────────────────────────────────
    val source: String? = null,
    val description: String? = null,
    val projectName: String? = null,
    val discountCode: String? = null,
    val driveFolder: String? = null,
    val segment: DealSegment = DealSegment.INDYWIDUALNY,
    val buildingKind: DealBuildingKind = DealBuildingKind.NOWY,
    val difficulty: DealDifficulty? = null,
    val buyerPersona: DealBuyerPersona? = null,
    val rodoConsent: Boolean = false,
    val elderlyContactException: Boolean = false,
    /** Termin następnego kontaktu w millis; `null` = brak terminu. */
    val nextContactAt: Long? = null,
    // ── Dane budynku ─────────────────────────────────────────────────────────
    val people: Int? = null,
    /** API przyjmuje wyłącznie liczbę całkowitą m². */
    val areaM2: Int? = null,
    val floors: Int? = null,
    val shape: String? = null,
    val construction: String? = null,
    val buildingStage: String? = null,
    val windows: String? = null,
    val heatedBasement: Boolean? = null,
    val heatedGarage: Boolean? = null,
    // ── OZC ──────────────────────────────────────────────────────────────────
    val ozcBuildingKw: Double? = null,
    val ozcDhwKw: Double? = null,
    val ozcSourceUrl: String? = null,
    val ozcConfirmed: Boolean = false,
    // ── Spotkanie wstępne / wizja ────────────────────────────────────────────
    val meetingKind: MeetingKind? = null,
    val meetingAt: Long? = null,
    val meetingOwnerId: String? = null,
    val meetingDurationMin: Int? = null,
    val meetingUrl: String? = null,
    // ── Audyt ────────────────────────────────────────────────────────────────
    val auditAddressKind: AuditAddressKind? = null,
    val auditAddress: String? = null,
    val auditMeetingAt: Long? = null,
    val auditOwnerId: String? = null,
    // ── Opiekunowie ──────────────────────────────────────────────────────────
    val ownerId: String = "",
    val stageOwnerId: String? = null,
    // ── Dane do faktury ──────────────────────────────────────────────────────
    val billingSameAsInstall: Boolean = true,
    val billingName: String? = null,
    val billingCompany: String? = null,
    val billingNip: String? = null,
    val billingAddress: String? = null,
) {
    /** Czy blok „Dane budynku" jest w całości pusty (→ `buildingData: null`). */
    val buildingDataEmpty: Boolean
        get() = people == null && areaM2 == null && floors == null && shape.isNullOrBlank() &&
            construction.isNullOrBlank() && buildingStage.isNullOrBlank() &&
            windows.isNullOrBlank() && heatedBasement == null && heatedGarage == null

    /** Czy blok OZC jest w całości pusty (→ `ozcData: null`). */
    val ozcEmpty: Boolean
        get() = ozcBuildingKw == null && ozcDhwKw == null && ozcSourceUrl.isNullOrBlank() &&
            !ozcConfirmed
}

/**
 * Czy formularz różni się od zapisanego stanu deala. Draft jest kompletnym
 * odwzorowaniem edytowalnych pól, więc równość draftów to dokładnie „nic do
 * zapisania" — stąd steruje przyciskiem „Zapisz" i ostrzeżeniem przy wyjściu.
 */
fun Deal.hasChangesFrom(draft: DealDraft): Boolean = toDraft() != draft

/** Draft wypełniony bieżącymi wartościami deala — punkt wyjścia formularza. */
fun Deal.toDraft(): DealDraft = DealDraft(
    source = source,
    description = description,
    projectName = projectName,
    discountCode = discountCode,
    driveFolder = driveFolder,
    segment = segment,
    buildingKind = buildingKind,
    difficulty = difficulty,
    buyerPersona = buyerPersona,
    rodoConsent = rodoConsent,
    elderlyContactException = elderlyContactException,
    nextContactAt = parseIso(nextContactAt),
    people = buildingData?.people,
    areaM2 = buildingData?.areaM2?.toInt(),
    floors = buildingData?.floors,
    shape = buildingData?.shape,
    construction = buildingData?.construction,
    buildingStage = buildingData?.stage,
    windows = buildingData?.windows,
    heatedBasement = buildingData?.heatedBasement,
    heatedGarage = buildingData?.heatedGarage,
    ozcBuildingKw = ozcData?.buildingKw,
    ozcDhwKw = ozcData?.dhwKw,
    ozcSourceUrl = ozcData?.sourceUrl,
    ozcConfirmed = ozcData?.confirmed ?: false,
    meetingKind = meetingKind,
    meetingAt = parseIso(meetingAt),
    meetingOwnerId = meetingOwnerId,
    meetingDurationMin = meetingDurationMin,
    meetingUrl = meetingUrl,
    auditAddressKind = auditAddressKind,
    auditAddress = auditAddress,
    auditMeetingAt = parseIso(auditMeetingAt),
    auditOwnerId = auditOwnerId,
    ownerId = ownerId,
    stageOwnerId = stageOwnerId,
    billingSameAsInstall = billingSameAsInstall,
    billingName = billingName,
    billingCompany = billingCompany,
    billingNip = billingNip,
    billingAddress = billingAddress,
)

/**
 * ISO 8601 → millis. Domena nie ma dostępu do pomocników warstwy prezentacji,
 * a porównanie dat musi działać na tej samej reprezentacji co formularz.
 */
private fun parseIso(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(iso)?.time
    } catch (_: Exception) {
        null
    }
}

/**
 * Draft nałożony na deala — kopia „jak po zapisie", robiona U SIEBIE, gdy
 * `PATCH` nie doleciał (brak zasięgu) i żądanie poszło do kolejki.
 *
 * Odwrotność `toDraft()` i musi trzymać się tych samych reguł co ciało żądania
 * (`buildDealPatch`): `buildingData` i `ozcData` API podmienia w CAŁOŚCI, a blok
 * pusty czyści nullem — więc tak samo składamy je tutaj. Dzięki temu karta po
 * zapisie offline pokazuje dokładnie to, co za chwilę zobaczy serwer, i kolejne
 * porównanie draftu z dealem nie zgłasza fałszywej różnicy.
 *
 * Pól stemplowanych przez serwer (`updatedAt`, `rodoConsentAt`, podpis OZC) nie
 * zgadujemy — dopisze je odpowiedź na wysłane żądanie.
 */
fun Deal.applyDraft(draft: DealDraft): Deal = copy(
    source = draft.source,
    description = draft.description,
    projectName = draft.projectName,
    discountCode = draft.discountCode,
    driveFolder = draft.driveFolder,
    segment = draft.segment,
    buildingKind = draft.buildingKind,
    difficulty = draft.difficulty,
    buyerPersona = draft.buyerPersona,
    rodoConsent = draft.rodoConsent,
    elderlyContactException = draft.elderlyContactException,
    nextContactAt = draft.nextContactAt?.let(::formatIso),
    buildingData = if (draft.buildingDataEmpty) {
        null
    } else {
        DealBuildingData(
            people = draft.people,
            areaM2 = draft.areaM2?.toDouble(),
            floors = draft.floors,
            shape = draft.shape,
            construction = draft.construction,
            stage = draft.buildingStage,
            windows = draft.windows,
            heatedBasement = draft.heatedBasement,
            heatedGarage = draft.heatedGarage,
        )
    },
    ozcData = if (draft.ozcEmpty) {
        null
    } else {
        DealOzcData(
            buildingKw = draft.ozcBuildingKw,
            dhwKw = draft.ozcDhwKw,
            sourceUrl = draft.ozcSourceUrl,
            confirmed = draft.ozcConfirmed,
        )
    },
    meetingKind = draft.meetingKind,
    meetingAt = draft.meetingAt?.let(::formatIso),
    meetingOwnerId = draft.meetingOwnerId,
    meetingDurationMin = draft.meetingDurationMin,
    meetingUrl = draft.meetingUrl,
    auditAddressKind = draft.auditAddressKind,
    auditAddress = draft.auditAddress,
    auditMeetingAt = draft.auditMeetingAt?.let(::formatIso),
    auditOwnerId = draft.auditOwnerId,
    ownerId = draft.ownerId.ifBlank { ownerId },
    stageOwnerId = draft.stageOwnerId,
    billingSameAsInstall = draft.billingSameAsInstall,
    billingName = draft.billingName,
    billingCompany = draft.billingCompany,
    billingNip = draft.billingNip,
    billingAddress = draft.billingAddress,
)

/** millis → ISO 8601 w UTC, w formacie, który czyta z powrotem `parseIso`. */
private fun formatIso(millis: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        .format(java.util.Date(millis))
