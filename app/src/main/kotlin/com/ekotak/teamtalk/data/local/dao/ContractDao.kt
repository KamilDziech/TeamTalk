package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.ContractFillingEntity
import com.ekotak.teamtalk.data.local.entity.ContractMutationEntity
import com.ekotak.teamtalk.data.local.entity.ContractPreviewEntity
import com.ekotak.teamtalk.data.local.entity.DealContractEntity

/** Cache i kolejka zakładki „Umowa" karty deala. */
@Dao
interface ContractDao {

    // ── Umowy ─────────────────────────────────────────────────────────────────

    /**
     * Umowy deala od najnowszej — tak samo, jak oddaje je panel. Kolejność ma
     * znaczenie: to po niej rozstrzyga się, który dokument jest „ten aktualny".
     */
    @Query("SELECT * FROM deal_contracts WHERE dealId = :dealId ORDER BY createdAt DESC")
    suspend fun getContracts(dealId: String): List<DealContractEntity>

    @Query("SELECT * FROM deal_contracts WHERE id = :id")
    suspend fun getContract(id: String): DealContractEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContract(contract: DealContractEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContracts(contracts: List<DealContractEntity>)

    @Query("DELETE FROM deal_contracts WHERE dealId = :dealId")
    suspend fun deleteContracts(dealId: String)

    /**
     * Podmiana umów deala odpowiedzią serwera. Umowy wystawionej bez zasięgu tu
     * nie ma — ona żyje w kolejce i dokłada się dopiero przy odczycie, więc
     * odpowiedź serwera nie ma czego skasować.
     */
    @Transaction
    suspend fun replaceContracts(dealId: String, contracts: List<DealContractEntity>) {
        deleteContracts(dealId)
        upsertContracts(contracts)
    }

    // ── Treść umowy (prefill formularza) ──────────────────────────────────────

    @Query("SELECT * FROM contract_fillings WHERE contractId = :contractId")
    suspend fun getFilling(contractId: String): ContractFillingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFilling(filling: ContractFillingEntity)

    // ── Podgląd dokumentu ─────────────────────────────────────────────────────

    @Query("SELECT * FROM contract_previews WHERE contractId = :contractId")
    suspend fun getPreview(contractId: String): ContractPreviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreview(preview: ContractPreviewEntity)

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /** Cała kolejka, od najstarszego zapisu — tak ją opróżnia worker. */
    @Query("SELECT * FROM contract_mutations ORDER BY createdAt ASC")
    suspend fun getMutations(): List<ContractMutationEntity>

    /** Kolejka jednego deala — po niej zakładka rysuje znaczniki „czeka". */
    @Query("SELECT * FROM contract_mutations WHERE dealId = :dealId ORDER BY createdAt ASC")
    suspend fun getMutationsForDeal(dealId: String): List<ContractMutationEntity>

    /** Ciało zapisu czekającego pod tym kluczem; `null` = nic nie czeka. */
    @Query("SELECT payload FROM contract_mutations WHERE targetId = :targetId AND kind = :kind")
    suspend fun getMutationPayload(targetId: String, kind: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMutation(mutation: ContractMutationEntity)

    /**
     * Kasujemy dokładnie ten wiersz (para `targetId` + `kind`): kiedy worker
     * wysyła zmianę umowy, zarząd może w tej samej chwili zakolejkować jej
     * akceptację — sprzątamy więc tylko to, co poszło na serwer.
     */
    @Query("DELETE FROM contract_mutations WHERE targetId = :targetId AND kind = :kind")
    suspend fun deleteMutation(targetId: String, kind: String)

    /** Wszystkie zapisy o jednej umowie — po jej unieważnieniu nie ma czego wysyłać. */
    @Query("DELETE FROM contract_mutations WHERE targetId = :targetId")
    suspend fun deleteMutationsFor(targetId: String)
}
