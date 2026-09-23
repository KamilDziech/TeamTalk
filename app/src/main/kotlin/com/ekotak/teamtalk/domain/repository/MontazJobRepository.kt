package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.MontazJob
import com.ekotak.teamtalk.domain.model.MontazJobRow
import com.ekotak.teamtalk.domain.model.MontazProtocol
import com.ekotak.teamtalk.domain.model.MontazStatus

/**
 * MODUŁ MONTAŻ — moje wyjazdy, teczka wyjazdu i protokół odbioru.
 *
 * Pełny offline, jak w Zadaniach i karcie deala, i z tego samego powodu tylko
 * mocniejszego: budowa to najgorszy zasięg w firmie, a teczka jest tam jedynym
 * dokumentem ekipy. Odczyty schodzą do cache'u przy każdym udanym pobraniu,
 * zapisy (start roboty, protokół) idą do WSPÓLNEJ kolejki montaży
 * (`montaz_mutations`) — tej samej, którą opróżnia `MontazSyncWorker`.
 *
 * Materiału i zdjęć NIE dublujemy: listę wyjazdową, wydanie i aparat obsługuje
 * [MontazRepository] karty deala. Dwa moduły, jedna prawda o magazynie.
 */
interface MontazJobRepository {

    /**
     * Moje montaże. [crew] = `true` pokazuje wyjazdy całej firmy (zmiennik,
     * koordynator). Bez sieci oddaje kopię z ostatniego pobrania.
     */
    suspend fun getJobs(crew: Boolean = false): MontazJobsSnapshot

    /**
     * Teczka jednego wyjazdu. Bez sieci oddaje kopię — a gdy wyjazdu nigdy nie
     * otwierano w zasięgu, `null`: ekran mówi wtedy wprost, że teczki nie ma,
     * zamiast rysować pustą kartę.
     */
    suspend fun getJob(installationId: String): MontazJob?

    /**
     * Start i koniec roboty (`planned` → `in_progress` → `done`). Bez zasięgu
     * ląduje w kolejce; karta pokazuje wtedy „czeka na wysyłkę".
     */
    suspend fun setStatus(installationId: String, status: MontazStatus): MontazSaveResult

    /**
     * Protokół odbioru: zapisany na serwerze albo wersja robocza z telefonu,
     * z pytaniami nałożonymi ze świeżej teczki. Protokół ZAMKNIĘTY zostaje
     * nietknięty — jego migawka pytań jest dokumentem.
     */
    suspend fun getProtocol(job: MontazJob): MontazProtocol

    /**
     * Zapis protokołu. [close] domyka go i ustawia montaż na „gotowy" —
     * protokół z podpisem klienta to jedyny wiarygodny dowód, że robota jest
     * skończona.
     */
    suspend fun saveProtocol(protocol: MontazProtocol, close: Boolean): MontazSaveResult
}

/**
 * Lista wyjazdów. [fromCache] mówi, że to kopia z telefonu — na budowie to
 * informacja praktyczna, bo obsada mogła się zmienić w biurze godzinę temu.
 */
data class MontazJobsSnapshot(
    val jobs: List<MontazJobRow> = emptyList(),
    val fromCache: Boolean = false,
    /** Odmowa serwera (np. brak `installation.view`) — inaczej niż brak sieci. */
    val error: String? = null,
)
