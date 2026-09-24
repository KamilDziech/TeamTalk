package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cache i kolejka zakładki „Montaż" karty deala.
 *
 * Ta zakładka pracuje w miejscu z najgorszym zasięgiem w całej firmie: piwnica
 * budowanego domu, kotłownia w bloku, wieś bez LTE. Karta montażu jest tam
 * jedynym dokumentem ekipy — mówi, co wykonać, co zabrać i kto jedzie — więc
 * ma działać bez sieci w OBIE strony: odczyt z ostatniego pobrania i zapis do
 * kolejki, jak Zadania, Zamówienie i Pliki.
 */

/**
 * Montaż deala w cache. Obsadę trzymamy jako surowy JSON listy par
 * (`userId`, `role`): to kontrakt z panelem, zawsze czytany razem z montażem,
 * a osobna tabela kazałaby go składać przy każdym odczycie.
 */
@Entity(tableName = "montaz_installations", indices = [Index("dealId")])
data class MontazEntity(
    /** Id serwerowe albo lokalne (`local:…`) do czasu wysłania nowego etapu. */
    @PrimaryKey val id: String,
    val dealId: String,
    val scheduledAt: String?,
    val status: String,
    val difficulty: String?,
    val teamNote: String?,
    /** Zakres montażu — id węzłów (konwerter `Converters.fromStringList`). */
    val nodeIds: List<String> = emptyList(),
    val crewId: String?,
    /** `[{"userId":…,"role":…}]` zserializowane wspólnym `Json` modułu. */
    val assigneesJson: String,
    val briefedAt: String?,
    val briefingMessageId: String?,
    val durationDays: Int,
    val syncedAt: Long,
)

/**
 * Ekipa do obsady. Cache jest tu wspólny dla całej organizacji (nie per deal),
 * bo lista ekip zmienia się raz na kwartał, a bez niej selektor „Ekipa" byłby
 * na budowie pusty.
 */
@Entity(tableName = "montaz_crews")
data class MontazCrewEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String?,
    val leaderId: String?,
    val memberIds: List<String> = emptyList(),
    val syncedAt: Long,
)

/** Pozycja listy wyjazdowej — co magazyn trzyma odłożone pod deal montażu. */
@Entity(tableName = "montaz_materials", indices = [Index("installationId")])
data class MontazMaterialEntity(
    @PrimaryKey val id: String,
    val installationId: String,
    val itemName: String,
    val itemCode: String?,
    val quantity: Double,
    val unit: String,
    val status: String,
    val covered: Double,
    val missing: Double,
    val issuedAt: String?,
    val issuedById: String?,
    val note: String?,
    val syncedAt: Long,
)

/**
 * Zdjęcie powykonawcze. Kadry zrobione bez zasięgu SĄ wierszami tego cache'u
 * (`local:…` + [localPath]) — inaczej zdjęcie z kotłowni nie miałoby gdzie
 * zaistnieć: serwer o nim nie wie, a nakładka nie ma czego nakładać. Ten sam
 * wybór, co przy plikach deala.
 */
@Entity(tableName = "montaz_photos", indices = [Index("installationId")])
data class MontazPhotoEntity(
    @PrimaryKey val id: String,
    val installationId: String,
    val caption: String?,
    val createdAt: String,
    /** Kopia treści czekającej na wysyłkę (`filesDir`), `null` = jest na serwerze. */
    val localPath: String?,
    val syncedAt: Long,
)

/**
 * Ptaszek przy pozycji listy pakowania. STAN LOKALNY, celowo nie wysyłany:
 * „spakowane do auta" to nie to samo, co „wydane z magazynu" (zapis księgowy),
 * a wysyłanie obu jako jednego myliłoby magazyniera. Trzymamy per montaż, bo
 * drugi etap pakuje się od nowa.
 */
@Entity(tableName = "montaz_pack", primaryKeys = ["installationId", "itemKey"])
data class MontazPackEntity(
    val installationId: String,
    /** Klucz pozycji: nazwa materiału albo sprzętu, znormalizowana. */
    val itemKey: String,
    val checked: Boolean,
    val changedAt: Long,
)

/**
 * Zmiana zakładki czekająca na wysyłkę.
 *
 * Jedna tabela na pięć rodzajów zapisu — wszystkie idą tą samą drogą i mają
 * wspólny porządek wysyłki, a to tutaj ma znaczenie: odprawa dla etapu
 * założonego bez zasięgu musi pójść PO tym etapie, bo inaczej nie ma czego
 * ostemplować.
 *
 * Klucz (`targetId`, `kind`) sprawia, że kolejna zmiana tego samego montażu
 * dopisuje się do czekającej (scalanie pól w [KIND_PATCH]) zamiast mnożyć wpisy:
 * liczy się ostatni stan obsady, a nie droga, którą do niego doszła.
 */
@Entity(tableName = "montaz_mutations", primaryKeys = ["targetId", "kind"], indices = [Index("dealId")])
data class MontazMutationEntity(
    /** Id montażu, a dla zdjęcia — lokalne id wiersza w `montaz_photos`. */
    val targetId: String,
    val kind: String,
    /** Gotowe ciało żądania. */
    val payload: String,
    val dealId: String,
    /** Montaż, którego zapis dotyczy — dla zdjęć różny od `targetId`. */
    val installationId: String,
    val createdAt: Long,
) {
    companion object {
        /** `POST /installations` — nowy etap robót pod tym samym dealem. */
        const val KIND_CREATE = "montaz_create"

        /** `PATCH /installations/{id}` — zakres, obsada, ekipa, uwaga, trudność. */
        const val KIND_PATCH = "montaz_patch"

        /** `POST /installations/{id}/deal-materials/issue` — wydanie na budowę. */
        const val KIND_ISSUE = "material_issue"

        /** `POST /installations/{id}/photos` — zdjęcie powykonawcze. */
        const val KIND_PHOTO = "photo_upload"

        /** `POST /briefing` + ostemplowanie montażu — odprawa do obsady. */
        const val KIND_BRIEFING = "briefing"

        /** `POST /installations/{id}/status` — start i koniec roboty (moduł Montaż). */
        const val KIND_STATUS = "job_status"

        /** `PUT /installations/{id}/protocol` — protokół odbioru (moduł Montaż). */
        const val KIND_PROTOCOL = "protocol_save"

        /**
         * `POST /schedule/move-person` — przeniesienie osoby do innej ekipy
         * (Harmonogram). `targetId` = `<id etapu docelowego>|<id osoby>`, więc
         * ponowne przeniesienie tej samej osoby na ten sam etap zastępuje wpis.
         */
        const val KIND_SCHEDULE_MOVE = "schedule_move"

        /** Prefiks identyfikatora nadawanego lokalnie do czasu wysyłki. */
        const val LOCAL_ID_PREFIX = "local:"
    }
}
