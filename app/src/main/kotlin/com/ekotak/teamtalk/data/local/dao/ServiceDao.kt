package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.ServiceClientEntity
import com.ekotak.teamtalk.data.local.entity.ServiceJobEntity
import com.ekotak.teamtalk.data.local.entity.ServiceMutationEntity
import com.ekotak.teamtalk.data.local.entity.ServiceTechnicianEntity
import com.ekotak.teamtalk.data.local.entity.WarrantyCardEntity
import kotlinx.coroutines.flow.Flow

/** Cache modułu Serwis: zlecenia, karty, klienci i serwisanci. */
@Dao
interface ServiceDao {

    @Query("SELECT * FROM service_jobs")
    fun observeJobs(): Flow<List<ServiceJobEntity>>

    @Query("SELECT * FROM warranty_cards")
    fun observeCards(): Flow<List<WarrantyCardEntity>>

    @Query("SELECT * FROM service_clients")
    fun observeClients(): Flow<List<ServiceClientEntity>>

    @Query("SELECT * FROM service_technicians")
    fun observeTechnicians(): Flow<List<ServiceTechnicianEntity>>

    @Query("SELECT * FROM service_jobs WHERE id = :id")
    suspend fun getJob(id: String): ServiceJobEntity?

    @Query("SELECT * FROM service_jobs WHERE localOnly = 0")
    suspend fun getSyncedJobs(): List<ServiceJobEntity>

    @Query("SELECT * FROM warranty_cards WHERE id = :id")
    suspend fun getCard(id: String): WarrantyCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJob(job: ServiceJobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJobs(jobs: List<ServiceJobEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: WarrantyCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCards(cards: List<WarrantyCardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClients(clients: List<ServiceClientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTechnicians(technicians: List<ServiceTechnicianEntity>)

    @Query("DELETE FROM service_jobs WHERE id = :id")
    suspend fun deleteJob(id: String)

    /**
     * Podmiana listy z serwera. Zgłoszenia zapisane bez zasięgu (`localOnly`)
     * przeżywają odświeżenie — serwer o nich jeszcze nie wie, a skasowanie ich
     * tutaj byłoby utratą jedynej kopii decyzji człowieka.
     */
    @Query("DELETE FROM service_jobs WHERE localOnly = 0")
    suspend fun deleteSyncedJobs()

    @Query("DELETE FROM warranty_cards")
    suspend fun deleteCards()

    @Query("DELETE FROM service_clients")
    suspend fun deleteClients()

    @Query("DELETE FROM service_technicians")
    suspend fun deleteTechnicians()

    @Transaction
    suspend fun replaceJobs(jobs: List<ServiceJobEntity>) {
        deleteSyncedJobs()
        upsertJobs(jobs)
    }

    @Transaction
    suspend fun replaceCards(cards: List<WarrantyCardEntity>) {
        deleteCards()
        upsertCards(cards)
    }

    @Transaction
    suspend fun replaceDirectory(
        clients: List<ServiceClientEntity>,
        technicians: List<ServiceTechnicianEntity>,
    ) {
        if (clients.isNotEmpty()) {
            deleteClients()
            upsertClients(clients)
        }
        if (technicians.isNotEmpty()) {
            deleteTechnicians()
            upsertTechnicians(technicians)
        }
    }
}

/** Kolejka niewysłanych zmian modułu Serwis. */
@Dao
interface ServiceMutationDao {

    /** Zlecenia z niewysłaną zmianą — znacznik „czeka na wysyłkę" na liście. */
    @Query("SELECT DISTINCT jobId FROM service_mutations")
    fun observePendingJobIds(): Flow<List<String>>

    /**
     * Cała kolejka, od najstarszej zmiany. Tworzenie (`__create`) ma pierwszeństwo
     * w obrębie tego samego zlecenia — inaczej `PATCH` poleciałby na id, którego
     * serwer jeszcze nie zna.
     */
    @Query("SELECT * FROM service_mutations ORDER BY createdAt ASC, field != '__create'")
    suspend fun getAll(): List<ServiceMutationEntity>

    @Query("SELECT * FROM service_mutations WHERE jobId = :jobId")
    suspend fun getForJob(jobId: String): List<ServiceMutationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mutations: List<ServiceMutationEntity>)

    /** Kasujemy po `field`, nie po zleceniu — człowiek mógł w tym czasie
     *  zmienić coś jeszcze, a to ma zostać w kolejce. */
    @Query("DELETE FROM service_mutations WHERE jobId = :jobId AND field IN (:fields)")
    suspend fun delete(jobId: String, fields: List<String>)

    @Query("DELETE FROM service_mutations WHERE jobId = :jobId")
    suspend fun deleteForJob(jobId: String)

    /** Po wysłaniu zgłoszenia lokalnego reszta jego kolejki dostaje id serwera. */
    @Query("UPDATE service_mutations SET jobId = :newId WHERE jobId = :oldId")
    suspend fun rekeyJob(oldId: String, newId: String)

    @Query("DELETE FROM service_mutations")
    suspend fun deleteAll()
}
