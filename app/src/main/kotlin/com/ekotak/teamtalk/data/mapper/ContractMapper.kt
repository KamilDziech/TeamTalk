package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.remote.dto.ContractChangeRequest
import com.ekotak.teamtalk.data.remote.dto.ContractDto
import com.ekotak.teamtalk.data.remote.dto.ContractFillingDto
import com.ekotak.teamtalk.data.remote.dto.ContractItemDto
import com.ekotak.teamtalk.data.remote.dto.ContractMaterialDto
import com.ekotak.teamtalk.data.remote.dto.ContractStageDto
import com.ekotak.teamtalk.domain.model.ContractChange
import com.ekotak.teamtalk.domain.model.ContractEmailStatus
import com.ekotak.teamtalk.domain.model.ContractFilling
import com.ekotak.teamtalk.domain.model.ContractItem
import com.ekotak.teamtalk.domain.model.ContractKind
import com.ekotak.teamtalk.domain.model.ContractMaterial
import com.ekotak.teamtalk.domain.model.ContractStage
import com.ekotak.teamtalk.domain.model.ContractStatus
import com.ekotak.teamtalk.domain.model.DOMYSLNE_DNI_KONCOWE
import com.ekotak.teamtalk.domain.model.DOMYSLNA_ZALICZKA
import com.ekotak.teamtalk.domain.model.DOMYSLNY_VAT
import com.ekotak.teamtalk.domain.model.DealContract

/**
 * Przekłady zakładki „Umowa": odpowiedź API ↔ model karty ↔ ciało żądania.
 *
 * Nazwy pól są polskie po obu stronach (tak mówi API modułu umów), więc mapa
 * jest płaska — tłumaczymy wyłącznie stringi na enumy i uzupełniamy domyślne
 * warunki, których starsze umowy nie mają zapisanych.
 */

fun ContractDto.toDomain(): DealContract = DealContract(
    id = id,
    numer = numer,
    status = ContractStatus.fromWire(status),
    rodzaj = ContractKind.fromWire(rodzaj),
    utworzona = utworzona,
    wyslana = wyslana,
    podpisana = podpisana,
    podpisanaIp = podpisanaIp,
    wysylka = ContractEmailStatus.fromWire(wysylka),
    wyslanaMailem = wyslanaMailem,
    parafaZalacznika = parafaZalacznika,
    brakParafy = brakParafy,
    wygasaLink = wygasaLink,
    sciezkaPodpisu = sciezkaPodpisu,
    wersja = wersja,
    zastepuje = zastepuje,
    zastapionaPrzez = zastapionaPrzez,
    rodzajNastepcy = rodzajNastepcy?.let { ContractKind.fromWire(it) },
    nastepcaPodpisany = nastepcaPodpisany,
    zmiana = zmiana?.let {
        ContractChange(
            powod = it.powod,
            zgloszona = it.zgloszona,
            zgloszonaPrzez = it.zgloszonaPrzez,
            zaakceptowana = it.zaakceptowana,
            zaakceptowanaPrzez = it.zaakceptowanaPrzez,
            odrzucona = it.odrzucona,
            odrzuconaPrzez = it.odrzuconaPrzez,
            powodOdrzucenia = it.powodOdrzucenia,
        )
    },
    czekaNaAkceptacje = czekaNaAkceptacje,
    zarzad = zarzad,
    mogeZdecydowac = mogeZdecydowac,
    mozeZmienic = mozeZmienic,
)

fun ContractFillingDto.toDomain(): ContractFilling = ContractFilling(
    przedmiot = przedmiot,
    termin = termin,
    podstawaZalacznika = podstawaZalacznika,
    // Umowa bez ani jednego etapu nie przeszłaby przez § 7 — pusta lista
    // z API (dokument sprzed etapów) dostaje ten sam etap, co nowa umowa.
    etapy = etapy.map { ContractStage(it.nr, it.nazwa) }
        .ifEmpty { listOf(ContractStage(1, "Etap 1 — montaż instalacji")) },
    pozycje = pozycje.map {
        ContractItem(
            lp = it.lp,
            opis = it.opis,
            ilosc = it.ilosc,
            jm = it.jm,
            cenaNetto = it.cenaNetto,
            etap = it.etap,
            klucz = it.klucz,
        )
    },
    // Warunki sprzed ich wprowadzenia biorą te same wartości, co panel:
    // 8 % VAT (budownictwo mieszkaniowe), 30 % zaliczki, 3 dni na płatność.
    vatStawka = vatStawka ?: DOMYSLNY_VAT,
    zaliczkaProc = zaliczkaProc ?: DOMYSLNA_ZALICZKA,
    terminKoncowyDni = terminKoncowyDni ?: DOMYSLNE_DNI_KONCOWE,
    materialy = materialy.orEmpty().map {
        ContractMaterial(
            productId = it.productId,
            kod = it.kod,
            nazwa = it.nazwa,
            ilosc = it.ilosc,
            jm = it.jm,
            klucz = it.klucz,
            uwaga = it.uwaga,
            instalacjaId = it.instalacjaId,
            instalacja = it.instalacja,
        )
    },
)

fun ContractFilling.toDto(): ContractFillingDto = ContractFillingDto(
    przedmiot = przedmiot.trim(),
    termin = termin,
    podstawaZalacznika = podstawaZalacznika.trim(),
    etapy = etapy.map { ContractStageDto(it.nr, it.nazwa) },
    // Pusty opis znaczy „linia porzucona w formularzu" — na dokument nie idzie.
    pozycje = pozycje.filter { it.opis.isNotBlank() }.map { it.toDto() },
    vatStawka = vatStawka,
    zaliczkaProc = zaliczkaProc,
    terminKoncowyDni = terminKoncowyDni,
    materialy = materialy.map { it.toDto() },
)

fun ContractFilling.toChangeRequest(powod: String, rodzaj: ContractKind): ContractChangeRequest =
    ContractChangeRequest(
        przedmiot = przedmiot.trim(),
        termin = termin,
        podstawaZalacznika = podstawaZalacznika.trim(),
        etapy = etapy.map { ContractStageDto(it.nr, it.nazwa) },
        pozycje = pozycje.filter { it.opis.isNotBlank() }.map { it.toDto() },
        vatStawka = vatStawka,
        zaliczkaProc = zaliczkaProc,
        terminKoncowyDni = terminKoncowyDni,
        materialy = materialy.map { it.toDto() },
        powod = powod.trim(),
        rodzaj = rodzaj.wire,
        // Świadome „tak, dać klientowi dokument do podpisu" — pytanie zadaje
        // zakładka, API bez tego pola odmawia.
        potwierdzam = true,
    )

private fun ContractItem.toDto(): ContractItemDto = ContractItemDto(
    lp = lp,
    opis = opis.trim(),
    ilosc = ilosc,
    jm = jm,
    cenaNetto = cenaNetto,
    etap = etap,
    klucz = klucz,
)

private fun ContractMaterial.toDto(): ContractMaterialDto = ContractMaterialDto(
    productId = productId,
    kod = kod,
    nazwa = nazwa,
    ilosc = ilosc,
    jm = jm,
    klucz = klucz,
    uwaga = uwaga,
    instalacjaId = instalacjaId,
    instalacja = instalacja,
)
