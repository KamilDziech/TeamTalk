package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.MontazCrewEntity
import com.ekotak.teamtalk.data.local.entity.MontazEntity
import com.ekotak.teamtalk.data.local.entity.MontazMaterialEntity
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity
import com.ekotak.teamtalk.data.local.entity.MontazPackEntity
import com.ekotak.teamtalk.data.local.entity.MontazPhotoEntity

/** Cache i kolejka zakładki „Montaż" karty deala. */
@Dao
interface MontazDao {

    // ── Montaże ───────────────────────────────────────────────────────────────

    @Query("SELECT * FROM montaz_installations WHERE dealId = :dealId ORDER BY scheduledAt ASC")
    suspend fun getMontaze(dealId: String): List<MontazEntity>

    @Query("SELECT * FROM montaz_installations WHERE id = :id")
    suspend fun getMontaz(id: String): MontazEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMontaze(rows: List<MontazEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMontaz(row: MontazEntity)

    @Query("DELETE FROM montaz_installations WHERE id = :id")
    suspend fun deleteMontaz(id: String)

    @Query("DELETE FROM montaz_installations WHERE dealId = :dealId AND id NOT LIKE 'local:%'")
    suspend fun deleteSyncedMontaze(dealId: String)

    /**
     * Podmiana montaży deala odpowiedzią serwera. Etapy założone bez zasięgu
     * (`local:…`) ZOSTAJĄ — serwer o nich jeszcze nie wie, a ich zniknięcie
     * wyglądałoby, jakby montaż nie powstał, i ktoś dopisałby go drugi raz.
     */
    @Transaction
    suspend fun replaceMontaze(dealId: String, rows: List<MontazEntity>) {
        deleteSyncedMontaze(dealId)
        upsertMontaze(rows)
    }

    // ── Ekipy ─────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM montaz_crews ORDER BY name COLLATE NOCASE")
    suspend fun getCrews(): List<MontazCrewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCrews(rows: List<MontazCrewEntity>)

    @Query("DELETE FROM montaz_crews")
    suspend fun deleteCrews()

    /**
     * Pustej listy nie bierzemy pod uwagę — to zwykle nieudany odczyt, a nie
     * firma bez ekip; skasowanie cache zostawiłoby selektor pusty do zasięgu.
     */
    @Transaction
    suspend fun replaceCrews(rows: List<MontazCrewEntity>) {
        if (rows.isEmpty()) return
        deleteCrews()
        upsertCrews(rows)
    }

    // ── Materiał ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM montaz_materials WHERE installationId = :installationId ORDER BY itemName COLLATE NOCASE")
    suspend fun getMaterials(installationId: String): List<MontazMaterialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMaterials(rows: List<MontazMaterialEntity>)

    @Query("DELETE FROM montaz_materials WHERE installationId = :installationId")
    suspend fun deleteMaterials(installationId: String)

    /** Rezerwacje żyją w magazynie — cache montażu podmieniamy w całości. */
    @Transaction
    suspend fun replaceMaterials(installationId: String, rows: List<MontazMaterialEntity>) {
        deleteMaterials(installationId)
        upsertMaterials(rows)
    }

    // ── Zdjęcia ───────────────────────────────────────────────────────────────

    @Query("SELECT * FROM montaz_photos WHERE installationId = :installationId ORDER BY createdAt DESC")
    suspend fun getPhotos(installationId: String): List<MontazPhotoEntity>

    @Query("SELECT * FROM montaz_photos WHERE id = :id")
    suspend fun getPhoto(id: String): MontazPhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhotos(rows: List<MontazPhotoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoto(row: MontazPhotoEntity)

    @Query("DELETE FROM montaz_photos WHERE id = :id")
    suspend fun deletePhoto(id: String)

    @Query("DELETE FROM montaz_photos WHERE installationId = :installationId AND localPath IS NULL")
    suspend fun deleteSyncedPhotos(installationId: String)

    /** Kadry z kolejki (`localPath`) zostają — to jedyna ich kopia. */
    @Transaction
    suspend fun replacePhotos(installationId: String, rows: List<MontazPhotoEntity>) {
        deleteSyncedPhotos(installationId)
        upsertPhotos(rows)
    }

    // ── Lista pakowania (stan lokalny) ────────────────────────────────────────

    @Query("SELECT * FROM montaz_pack WHERE installationId = :installationId AND checked = 1")
    suspend fun getPacked(installationId: String): List<MontazPackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPacked(row: MontazPackEntity)

    @Query("DELETE FROM montaz_pack WHERE installationId = :installationId")
    suspend fun clearPacked(installationId: String)

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /** Cała kolejka, od najstarszego zapisu — tak ją opróżnia worker. */
    @Query("SELECT * FROM montaz_mutations ORDER BY createdAt ASC")
    suspend fun getMutations(): List<MontazMutationEntity>

    /** Kolejka jednego deala — po niej zakładka rysuje znaczniki „czeka". */
    @Query("SELECT * FROM montaz_mutations WHERE dealId = :dealId")
    suspend fun getMutationsForDeal(dealId: String): List<MontazMutationEntity>

    /** Ciało zapisu czekającego pod tym kluczem; `null` = nic nie czeka. */
    @Query("SELECT payload FROM montaz_mutations WHERE targetId = :targetId AND kind = :kind")
    suspend fun getMutationPayload(targetId: String, kind: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMutation(mutation: MontazMutationEntity)

    /**
     * Kasujemy dokładnie ten wiersz (para `targetId` + `kind`): kiedy worker
     * wysyła zmianę obsady, ekipa może w tej samej chwili odhaczyć materiał —
     * sprzątamy więc tylko to, co faktycznie poszło na serwer.
     */
    @Query("DELETE FROM montaz_mutations WHERE targetId = :targetId AND kind = :kind")
    suspend fun deleteMutation(targetId: String, kind: String)

    /** Wszystko, co czekało pod montażem założonym offline — po jego wysyłce. */
    @Query("SELECT * FROM montaz_mutations WHERE installationId = :installationId")
    suspend fun getMutationsForInstallation(installationId: String): List<MontazMutationEntity>

    @Query("UPDATE montaz_mutations SET installationId = :serverId WHERE installationId = :localId")
    suspend fun retargetInstallation(localId: String, serverId: String)

    @Query("UPDATE montaz_mutations SET targetId = :serverId WHERE targetId = :localId AND kind != 'photo_upload'")
    suspend fun retargetTarget(localId: String, serverId: String)
}
