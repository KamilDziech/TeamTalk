package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Umowy deala (`/api/deals/:dealId/contracts`) — pełny kontrakt zakładki
 * „Umowa", 1:1 z `ContractsController` panelu.
 *
 * Wszystkie pola mają wartości domyślne: odpowiedź serwera rośnie razem
 * z modułem umów w panelu, a telefon nie może się wywalić na polu, którego
 * jeszcze nie zna (albo którego starsze wydanie API nie oddaje).
 */
@Serializable
data class ContractDto(
    val id: String,
    val numer: String = "",
    val status: String = "",
    /** `umowa` | `aneks`. */
    val rodzaj: String = "umowa",
    val utworzona: String = "",
    val wyslana: String? = null,
    val podpisana: String? = null,
    val podpisanaIp: String? = null,
    /** `sent` | `failed` | `pending_config` | `brak-adresu`. */
    val wysylka: String? = null,
    val wyslanaMailem: String? = null,
    val parafaZalacznika: String? = null,
    val brakParafy: Boolean = false,
    val wygasaLink: String? = null,
    /** Ścieżka publiczna `/umowa/<token>`; telefon skleja z niej pełny link. */
    val sciezkaPodpisu: String? = null,
    val wersja: Int = 1,
    val zastepuje: String? = null,
    val zastapionaPrzez: String? = null,
    val rodzajNastepcy: String? = null,
    val nastepcaPodpisany: Boolean = false,
    val zmiana: ContractChangeDto? = null,
    val czekaNaAkceptacje: Boolean = false,
    val zarzad: Boolean = false,
    val mogeZdecydowac: Boolean = false,
    val mozeZmienic: Boolean = false,
)

@Serializable
data class ContractChangeDto(
    val powod: String? = null,
    val zgloszona: String = "",
    val zgloszonaPrzez: String? = null,
    val zaakceptowana: String? = null,
    val zaakceptowanaPrzez: String? = null,
    val odrzucona: String? = null,
    val odrzuconaPrzez: String? = null,
    val powodOdrzucenia: String? = null,
)

/** Podgląd dokumentu (`GET .../preview`) — ten sam HTML, z którego jest PDF. */
@Serializable
data class ContractPreviewDto(
    val numer: String = "",
    val podpisana: Boolean = false,
    val html: String = "",
)

@Serializable
data class ContractItemDto(
    val lp: Int = 0,
    val opis: String = "",
    val ilosc: Double = 0.0,
    val jm: String = "",
    val cenaNetto: Double = 0.0,
    val etap: Int = 1,
    val klucz: String? = null,
)

@Serializable
data class ContractStageDto(val nr: Int = 1, val nazwa: String = "")

/**
 * Pozycja zestawienia materiałowego migawki umowy. Telefon jej NIE LICZY —
 * przenosi zestawienie z poprzedniej wersji umowy albo z rezerwacji deala,
 * którą policzył panel (patrz `ContractRepositoryImpl`).
 */
@Serializable
data class ContractMaterialDto(
    val productId: String? = null,
    val kod: String? = null,
    val nazwa: String = "",
    val ilosc: Double = 0.0,
    val jm: String = "",
    val klucz: String? = null,
    val uwaga: String? = null,
    val instalacjaId: String? = null,
    val instalacja: String? = null,
)

/** Treść umowy w kształcie formularza — ciało generowania i zmiany. */
@Serializable
data class ContractFillingDto(
    val przedmiot: String = "",
    val termin: String = "",
    val podstawaZalacznika: String = "",
    val etapy: List<ContractStageDto> = emptyList(),
    val pozycje: List<ContractItemDto> = emptyList(),
    val vatStawka: Int? = null,
    val zaliczkaProc: Int? = null,
    val terminKoncowyDni: Int? = null,
    val materialy: List<ContractMaterialDto>? = null,
)

/** Odpowiedź `GET .../wypelnienie` — treść podpisanej umowy do prefillu zmiany. */
@Serializable
data class ContractFillingResponse(
    val numer: String = "",
    val wersja: Int = 1,
    val wypelnienie: ContractFillingDto = ContractFillingDto(),
)

/**
 * Ciało zmiany umowy: treść + uzasadnienie + rodzaj dokumentu + świadome
 * `potwierdzam`. API bez potwierdzenia odmawia, żeby dwuklik nie wysłał
 * klientowi dokumentu po raz drugi przypadkiem.
 */
@Serializable
data class ContractChangeRequest(
    val przedmiot: String = "",
    val termin: String = "",
    val podstawaZalacznika: String = "",
    val etapy: List<ContractStageDto> = emptyList(),
    val pozycje: List<ContractItemDto> = emptyList(),
    val vatStawka: Int? = null,
    val zaliczkaProc: Int? = null,
    val terminKoncowyDni: Int? = null,
    val materialy: List<ContractMaterialDto>? = null,
    val powod: String = "",
    val rodzaj: String = "umowa",
    val potwierdzam: Boolean = true,
)

@Serializable
data class ContractGenerateResponse(
    val id: String = "",
    val numer: String = "",
    val status: String = "",
    val sciezkaPodpisu: String? = null,
)

@Serializable
data class ContractChangeResponse(
    val id: String = "",
    val numer: String = "",
    val wersja: Int = 1,
    val rodzaj: String = "umowa",
    /** `do-podpisu` = poszło do klienta; `czeka-na-akceptacje` = czeka na zarząd. */
    val stan: String = "",
    val sciezkaPodpisu: String? = null,
)

@Serializable
data class ContractResendResponse(
    val sciezkaPodpisu: String? = null,
    /** `zalacznik` = klient dosyła samą parafę; `pelny` = podpisuje całość. */
    val zakres: String? = null,
)

/** Wynik odtworzenia zamówienia z umowy (`POST .../zamowienie`). */
@Serializable
data class ContractOrderResponse(
    val numer: String = "",
    /** `utworzone` | `zmienione` | `istnialo`. */
    val status: String = "",
    val pozycje: Int = 0,
    /** Ile dokumentów — tyle, ile instalacji ma materiał w tej umowie. */
    val zamowienia: Int = 0,
)

/** Puste ciało dla POST-ów bez treści (`resend`, `zamowienie`). */
@Serializable
class EmptyBody

@Serializable
data class ContractApproveRequest(val potwierdzam: Boolean = true)

@Serializable
data class ContractRejectRequest(val powod: String? = null)
