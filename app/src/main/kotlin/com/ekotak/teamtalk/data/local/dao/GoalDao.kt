package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekotak.teamtalk.data.local.entity.GoalCatalogEntity
import com.ekotak.teamtalk.data.local.entity.GoalMutationEntity
import com.ekotak.teamtalk.data.local.entity.GoalTrendEntity
import com.ekotak.teamtalk.data.local.entity.GoalViewEntity
import kotlinx.coroutines.flow.Flow

/** Cache i kolejka modułu Cele. */
@Dao
interface GoalDao {

    // ── Migawki widoków ───────────────────────────────────────────────────────

    /**
     * Widok dla klucza zakładki. Strumień, nie odczyt jednorazowy: wysłanie
     * kolejki podmienia migawkę i ekran ma się odświeżyć sam, bez wracania
     * użytkownika do modułu.
     */
    @Query("SELECT * FROM goal_views WHERE `key` = :key")
    fun observeView(key: String): Flow<GoalViewEntity?>

    @Query("SELECT * FROM goal_views WHERE `key` = :key")
    suspend fun getView(key: String): GoalViewEntity?

    /**
     * Wszystkie migawki — po zapisie bez zasięgu przeglądamy je i wstawiamy
     * zmieniony cel tam, gdzie się mieści. Bez tego człowiek klika „Zapisz"
     * i nie widzi żadnej zmiany, dopóki telefon nie złapie sieci.
     */
    @Query("SELECT * FROM goal_views")
    suspend fun getAllViews(): List<GoalViewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertView(view: GoalViewEntity)

    /** Po wysłaniu kolejki migawki są nieaktualne — pobierze się je od nowa. */
    @Query("DELETE FROM goal_views")
    suspend fun clearViews()

    // ── Przebieg celu (wykres) ────────────────────────────────────────────────

    @Query("SELECT * FROM goal_trends WHERE goalId = :goalId")
    fun observeTrend(goalId: String): Flow<GoalTrendEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrend(trend: GoalTrendEntity)

    @Query("DELETE FROM goal_trends")
    suspend fun clearTrends()

    // ── Katalog mierników ─────────────────────────────────────────────────────

    @Query("SELECT * FROM goal_catalog WHERE id = 1")
    fun observeCatalog(): Flow<GoalCatalogEntity?>

    @Query("SELECT * FROM goal_catalog WHERE id = 1")
    suspend fun getCatalog(): GoalCatalogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalog(catalog: GoalCatalogEntity)

    // ── Kolejka zapisów ───────────────────────────────────────────────────────

    /** Identyfikatory celów z czymś w kolejce — po nich rysujemy „W kolejce". */
    @Query("SELECT DISTINCT goalId FROM goal_mutations")
    fun observePendingIds(): Flow<List<String>>

    /**
     * Kolejka w kolejności podejmowania decyzji. Sortujemy dodatkowo po polu,
     * żeby tworzenie (`__create`) wyszło przed łatkami tego samego celu nawet
     * wtedy, gdy oba wiersze mają ten sam znacznik czasu.
     */
    @Query("SELECT * FROM goal_mutations ORDER BY createdAt ASC, field ASC")
    suspend fun getMutations(): List<GoalMutationEntity>

    @Query("SELECT COUNT(*) FROM goal_mutations")
    suspend fun countMutations(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMutation(mutation: GoalMutationEntity)

    @Query("DELETE FROM goal_mutations WHERE goalId = :goalId AND field = :field")
    suspend fun deleteMutation(goalId: String, field: String)

    /** Kasowanie celu unieważnia wszystko, co na niego czekało w kolejce. */
    @Query("DELETE FROM goal_mutations WHERE goalId = :goalId")
    suspend fun deleteMutations(goalId: String)

    @Query("SELECT * FROM goal_mutations WHERE goalId = :goalId")
    suspend fun getMutationsFor(goalId: String): List<GoalMutationEntity>

    /** Podmiana identyfikatora lokalnego na serwerowy po wysłaniu celu. */
    @Query("UPDATE goal_mutations SET goalId = :serverId WHERE goalId = :localId")
    suspend fun remapMutations(localId: String, serverId: String)
}
