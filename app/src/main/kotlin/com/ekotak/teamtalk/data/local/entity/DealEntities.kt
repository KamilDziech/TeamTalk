package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache i kolejka karty deala. Powód jest ten sam co przy Audycie i Plikach:
 * zakres instalacji i ustalenia spotkania zapadają PRZY KLIENCIE — w domu
 * w budowie albo pod nim — a nie przy biurku z zasięgiem.
 */

/**
 * Migawki instalacji jednego deala (`GET /api/deals/:id/installations`) trzymane
 * jako surowa odpowiedź. JSON, a nie kolumny: kontrakt niesie per etap wybór,
 * `editable` i `state`, a rozkładanie tego na tabelę tylko po to, żeby złożyć
 * z powrotem, dokładałoby migrację przy każdym nowym polu.
 *
 * Osobno od `audit_installations`: tamta tabela to projekcja pod zakładkę Audyt
 * (jedna lista węzłów + kaskady), tu leży pełny kontrakt z prawami edycji, bez
 * którego zakładka nie wie, czy wolno zmieniać zakres.
 */
@Entity(tableName = "deal_installations")
data class DealInstallationsEntity(
    @PrimaryKey val dealId: String,
    /** Ciało odpowiedzi API (`DealInstallationsDto`) jako JSON. */
    val payload: String,
    val syncedAt: Long,
)

/**
 * Zmiana karty deala czekająca na wysyłkę.
 *
 * Klucz (`dealId`, `kind`) sprawia, że kolejny zapis tego samego rodzaju
 * nadpisuje poprzedni — liczy się ostatnia decyzja handlowca. Dla zakresu
 * instalacji to naturalne (API i tak podmienia całą listę etapu), a dla `PATCH`
 * karty scalamy pola: nowe klucze wygrywają, a te ruszone wcześniej zostają
 * w ciele żądania.
 */
@Entity(tableName = "deal_mutations", primaryKeys = ["dealId", "kind"])
data class DealMutationEntity(
    val dealId: String,
    val kind: String,
    /** Gotowe ciało żądania: obiekt `PATCH`-a albo `{"categoryIds":[…]}`. */
    val payload: String,
    val createdAt: Long,
) {
    companion object {
        /** Zmiana pól karty (`PATCH /api/deals/:id`) — jeden scalony obiekt. */
        const val KIND_PATCH = "patch"

        /** Prefiks rodzaju „zakres instalacji etapu X". */
        private const val INSTALLATION_PREFIX = "inst:"

        /** Rodzaj dla migawki jednego etapu instalacyjnego. */
        fun installationKind(stageWire: String): String = "$INSTALLATION_PREFIX$stageWire"

        /** Etap zakolejkowanej migawki; `null` = to nie jest wpis o instalacjach. */
        fun stageOf(kind: String): String? =
            if (kind.startsWith(INSTALLATION_PREFIX)) kind.removePrefix(INSTALLATION_PREFIX) else null
    }
}
