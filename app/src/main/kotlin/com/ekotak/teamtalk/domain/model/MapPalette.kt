package com.ekotak.teamtalk.domain.model

/**
 * Paleta modułu Mapa — 1:1 z `web/src/app/app/map/mapColors.ts`. WŁASNA, nie
 * współdzielona z Kanbanem: ten sam deal ma być tym samym kolorem na mapie
 * w panelu i na mapie w telefonie, nawet gdy tablica zmieni swoje barwy.
 *
 * Kolory trzymamy jako ARGB (`Long`), żeby nie wciągać typów Compose do warstwy
 * domenowej — badge punktu liczy się tu, a rysuje w prezentacji.
 */
object MapPalette {

    /** Etapy lejka. Zieleń marki (#44d62c) = „Montaż" (aktywna realizacja). */
    fun stageColor(stage: DealStage): Long = when (stage) {
        DealStage.LEAD -> 0xFF4AA3FF
        DealStage.QUALIFIKACJA -> 0xFFA371F7
        DealStage.EDUKACJA -> 0xFF3FB9D4
        DealStage.AUDIT -> 0xFFF778BA
        DealStage.ANGEBOT -> 0xFFFFA657
        DealStage.ON_HOLD -> 0xFF8B949E
        DealStage.SOLD -> 0xFFF2C94C
        DealStage.PRZED_MONTAZEM -> 0xFFB5C93A
        DealStage.OCZEKIWANIE_NA_MONTAZ -> 0xFF7FB800
        DealStage.MONTAZ -> 0xFF44D62C
        DealStage.FERTIG -> 0xFF17B3A3
        DealStage.LOST -> 0xFFF85149
        DealStage.ZAKONCZONY -> 0xFF6E7681
    }

    /** Status zlecenia serwisowego (awaria po SLA rysowana [SLA] — patrz mapper). */
    fun serviceStatusColor(status: ServiceJobStatus): Long = when (status) {
        ServiceJobStatus.NEW -> 0xFFFFA657
        ServiceJobStatus.IN_PROGRESS -> 0xFF4AA3FF
        ServiceJobStatus.DONE -> 0xFF17B3A3
    }

    /** Awaria po przekroczeniu SLA — czerwień, przed wszystkimi statusami. */
    const val SLA: Long = 0xFFF85149

    /** Status karty gwarancyjnej (przeglądy Panasonic). */
    fun warrantyStatusColor(status: WarrantyCardStatus): Long = when (status) {
        WarrantyCardStatus.WYKONANE -> 0xFF17B3A3
        WarrantyCardStatus.OCZEKUJACE -> 0xFFFFA657
        WarrantyCardStatus.UMOWIONE -> 0xFF4AA3FF
        WarrantyCardStatus.REZYGNACJA -> 0xFFF85149
        WarrantyCardStatus.CZEKAMY_NA_KONTAKT -> 0xFFA371F7
        WarrantyCardStatus.BRAK_KONTAKTU -> 0xFF8B949E
        WarrantyCardStatus.INNE -> 0xFF6E7681
    }

    /**
     * Gradient heatmapy — punkty przystankowe jak w `leaflet.heat` w panelu
     * (niebieski → zielony → żółty → czerwony wraz z zagęszczeniem).
     */
    val HEAT_STOPS: List<Pair<Float, Long>> = listOf(
        0.20f to 0xFF4AA3FF,
        0.45f to 0xFF44D62C,
        0.70f to 0xFFF2C94C,
        1.00f to 0xFFF85149,
    )
}
