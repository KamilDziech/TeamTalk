package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.BriefingAck
import com.ekotak.teamtalk.domain.model.DealDifficulty
import com.ekotak.teamtalk.domain.model.MontazAssignee
import com.ekotak.teamtalk.domain.model.MontazMaterial
import com.ekotak.teamtalk.domain.model.MontazPhoto
import com.ekotak.teamtalk.domain.model.MontazSnapshot

/**
 * Zakładka „Montaż" karty deala — cache Room i kolejka offline.
 *
 * Dlaczego pełny offline, a nie sam cache do odczytu: ta karta jest jedynym
 * dokumentem ekipy na budowie, a budowa to zwykle miejsce bez zasięgu. Zapisy,
 * które tu powstają (dopisanie kogoś do obsady, ptaszek przy zakresie, wydanie
 * materiału, zdjęcie powykonawcze), robi się PRZY ROBOCIE — odesłanie ekipy do
 * panelu znaczyłoby „wpisz to wieczorem z pamięci", czyli nie wpisze tego nikt.
 */
interface MontazRepository {

    /**
     * Montaże deala razem z ekipami do obsady. Bez sieci oddaje kopię
     * z ostatniego pobrania z nałożoną kolejką ([MontazSnapshot.fromCache]).
     * Rezerwacje terminu (`reserved`) odsiewamy tak samo jak panel.
     */
    suspend fun getMontaze(dealId: String): MontazSnapshot

    /** Lista wyjazdowa — co magazyn trzyma odłożone pod deal tego montażu. */
    suspend fun getMaterials(installationId: String): List<MontazMaterial>

    /** Zdjęcia powykonawcze razem z kadrami czekającymi w kolejce. */
    suspend fun getPhotos(installationId: String): List<MontazPhoto>

    /** Nowy etap robót (np. pompa ciepła po wylewce). */
    suspend fun createMontaz(dealId: String, scheduledAt: String): MontazSaveResult

    /**
     * Zmiana montażu z karty: zakres, obsada, ekipa, uwaga, trudność. Pola
     * pominięte zostają bez zmian; [MontazPatch] rozróżnia „nie ruszaj" od
     * „wyczyść" własnymi znacznikami.
     */
    suspend fun patchMontaz(dealId: String, id: String, patch: MontazPatch): MontazSaveResult

    /** Wydanie materiału na budowę — braków NIE blokujemy, tak jak panel. */
    suspend fun issueMaterials(
        dealId: String,
        installationId: String,
        reservationIds: List<String>,
    ): MontazSaveResult

    /** Zdjęcie powykonawcze; bez zasięgu kopia leży w telefonie do wysyłki. */
    suspend fun addPhoto(
        dealId: String,
        installationId: String,
        bytes: ByteArray,
        fileName: String,
    ): MontazSaveResult

    /**
     * Odprawa do OSÓB z obsady (nie do ekipy jako grupy) z wymaganym
     * potwierdzeniem. Publikacja wymaga `briefing.publish` — odmowa serwera
     * leci dalej, bo ponowienie jej nie zmieni.
     */
    suspend fun sendBriefing(
        dealId: String,
        installationId: String,
        title: String,
        body: String,
        userIds: List<String>,
    ): MontazSaveResult

    /** Ile osób odhaczyło odprawę; `null` = ta sesja nie widzi potwierdzeń. */
    suspend fun briefingAck(messageId: String): BriefingAck?

    /** Klucze pozycji odhaczonych na liście pakowania (stan lokalny telefonu). */
    suspend fun packedItems(installationId: String): Set<String>

    /** Ptaszek przy pozycji listy pakowania — nie idzie na serwer. */
    suspend fun setPacked(installationId: String, itemKey: String, checked: Boolean)

    /** Opróżnienie kolejki — woła `MontazSyncWorker`, gdy wróci sieć. */
    suspend fun syncPendingMutations(): MontazSyncResult
}

/**
 * Zmiana montażu. `null` znaczy „nie ruszaj tego pola"; wyczyszczenie ekipy
 * i uwagi ma własny znacznik, bo `null` jest tu wartością docelową, a nie
 * brakiem zmiany.
 */
data class MontazPatch(
    val nodeIds: List<String>? = null,
    val assignees: List<MontazAssignee>? = null,
    val crewId: String? = null,
    val clearCrew: Boolean = false,
    val teamNote: String? = null,
    val clearNote: Boolean = false,
    val difficulty: DealDifficulty? = null,
)

/**
 * Co się stało z zapisem. Rozróżnienie idzie aż do komunikatu na ekranie:
 * „zapisano" i „zapisano w telefonie, wyślemy w zasięgu" to dla ekipy na
 * budowie dwie różne informacje.
 */
enum class MontazSaveResult { SENT, QUEUED }

/** Wynik przebiegu kolejki: `RETRY` = sieć znowu zawiodła, wpisy zostają. */
enum class MontazSyncResult { DONE, RETRY }
