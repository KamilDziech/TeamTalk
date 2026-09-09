package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekotak.teamtalk.data.local.entity.DealInvoicesEntity
import com.ekotak.teamtalk.data.local.entity.DealMontazeEntity

/** Cache zakładki „Faktura" — ostatnia odpowiedź serwera per deal. */
@Dao
interface InvoiceDao {

    @Query("SELECT * FROM deal_invoices WHERE dealId = :dealId")
    suspend fun getInvoices(dealId: String): DealInvoicesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoices(row: DealInvoicesEntity)

    @Query("SELECT * FROM deal_montaze WHERE dealId = :dealId")
    suspend fun getMontaze(dealId: String): DealMontazeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMontaze(row: DealMontazeEntity)
}
