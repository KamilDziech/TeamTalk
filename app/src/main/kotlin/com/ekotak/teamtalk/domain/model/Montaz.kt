package com.ekotak.teamtalk.domain.model

/**
 * TECZKA ROBOCZA MONTAŻU — model zakładki „Montaż" karty deala.
 *
 * Deal ma zwykle kilka montaży (podłogówkę teraz, pompę ciepła po wylewce),
 * więc karta zaczyna się od paska etapów, a wszystko poniżej dotyczy JEDNEGO
 * wybranego montażu. Zakres ([Montaz.nodeIds]) jest tu polem pierwszym wśród
 * równych: z niego wynikają wymagane role obsady, lista sprzętu i rysunki
 * wykonawcze.
 *
 * NIC się tu nie wpisuje po raz drugi — wszystko powstało wcześniej w audycie,
 * ofercie, umowie i magazynie. Telefon dokłada tylko to, czego biuro nie ma:
 * odhaczanie pakowania, zdjęcia z aparatu i nawigację pod adres.
 */

/** Jedna osoba w obsadzie z rolą montażową (`Category.montageRoles`). */
data class MontazAssignee(
    val userId: String,
    /** `null` = dopisana bez wskazania roli; to normalny stan, nie brak danych. */
    val role: String? = null,
)

/**
 * Jeden wyjazd ekipy pod ten deal.
 *
 * Rezerwacje terminu (`status = reserved`) do tej listy NIE wchodzą — odsiewa
 * je repozytorium, tak samo jak `listDealInstallations` w panelu: to jeszcze
 * nie montaż, tylko zaklepane okno z zakładki „Oferta".
 */
data class Montaz(
    val id: String,
    val dealId: String,
    /** ISO-8601; `null` przy montażu bez ustalonego terminu. */
    val scheduledAt: String?,
    val status: MontazStatus,
    val difficulty: DealDifficulty?,
    val teamNote: String?,
    /** Zakres — węzły drzewa instalacji deala objęte tą robotą. */
    val nodeIds: List<String> = emptyList(),
    val crewId: String? = null,
    val assignees: List<MontazAssignee> = emptyList(),
    val briefedAt: String? = null,
    val briefingMessageId: String? = null,
    /** Ile dni roboczych zajmuje wyjazd; panel domyśla się dwóch. */
    val durationDays: Int = 2,
    /**
     * Kiedy zmiana tego montażu trafiła do kolejki; `null` = zgodny z serwerem.
     * Po tym karta pokazuje „czeka na wysyłkę" — ekipa ma wiedzieć, że jej
     * decyzja jest zapisana w telefonie, ale koordynator jeszcze jej nie widzi.
     */
    val pendingSince: Long? = null,
    /** Etap założony bez zasięgu — nie ma jeszcze id z serwera. */
    val local: Boolean = false,
)

/** Stan pozycji listy wyjazdowej w magazynie. */
enum class MaterialStatus(val wire: String) {
    ACTIVE("active"),
    DONE("done"),
    CANCELLED("cancelled");

    companion object {
        fun fromWire(value: String?): MaterialStatus =
            entries.firstOrNull { it.wire == value } ?: ACTIVE
    }
}

/**
 * Linia listy wyjazdowej — pozycja odłożona w magazynie pod deal montażu.
 * [covered] i [missing] liczy serwer ze stanu magazynu; telefon ich nie
 * przelicza, bo stan zmienia się przy każdym wydaniu w hali.
 */
data class MontazMaterial(
    val id: String,
    val itemName: String,
    val itemCode: String?,
    val quantity: Double,
    val unit: String,
    val status: MaterialStatus,
    val covered: Double,
    val missing: Double,
    val issuedAt: String?,
    val issuedById: String?,
    val note: String?,
    /** Wydanie czeka w kolejce — dla ekipy „już wydane", dla magazynu jeszcze nie. */
    val pending: Boolean = false,
)

/** Ekipa z modułu Zespół — skrót do obsady montażu. */
data class MontazCrew(
    val id: String,
    val name: String,
    val color: String?,
    val leaderId: String?,
    val memberIds: List<String>,
)

/** Zdjęcie powykonawcze montażu. */
data class MontazPhoto(
    val id: String,
    val installationId: String,
    val caption: String?,
    val createdAt: String,
    /**
     * Ścieżka kopii czekającej na wysyłkę; `null` = zdjęcie jest już na serwerze.
     * To JEDYNA kopia kadru zrobionego w kotłowni bez zasięgu, więc leży
     * w `filesDir`, a nie w cache.
     */
    val localPath: String? = null,
) {
    val pending: Boolean get() = localPath != null
}

/** Ile osób odhaczyło odprawę; widzi to wyłącznie publikujący komunikat. */
data class BriefingAck(val acked: Int, val total: Int)

/**
 * CO JEST W CENIE — Załącznik nr 1 PODPISANEJ umowy, a nie przeliczenie
 * z audytu. Różnica jest istotna na budowie: audyt zmienia się dalej, a ekipa
 * ma wykonać to, pod czym klient się podpisał.
 */
data class MontazZakres(
    val numer: String,
    /** Data podpisu; `null` = umowa podpisana bez daty w migawce. */
    val podpisana: String?,
    val pozycje: List<ContractItem>,
    /**
     * Czego w cenie NIE MA (§ zakres wyłączony umowy). Dla ekipy to
     * najważniejsza połowa listy — „drobne dorzucenie" na budowie to praca
     * poza umową.
     */
    val wylaczony: List<String>,
)

/**
 * Wszystko, co zakładka wie o montażach deala.
 *
 * [fromCache] mówi, że listy są kopią z telefonu — na budowie to informacja
 * praktyczna: obsada mogła się zmienić w biurze godzinę temu.
 */
data class MontazSnapshot(
    val montaze: List<Montaz> = emptyList(),
    val crews: List<MontazCrew> = emptyList(),
    val fromCache: Boolean = false,
    /**
     * Odmowa odczytu (`installation.view`) — inaczej niż brak sieci. Bez tego
     * prawa zakładka mówi wprost, czego brakuje, zamiast udawać deal bez montaży.
     */
    val error: String? = null,
)
