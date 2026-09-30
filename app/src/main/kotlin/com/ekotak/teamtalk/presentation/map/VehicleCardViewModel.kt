package com.ekotak.teamtalk.presentation.map

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.VehicleFile
import com.ekotak.teamtalk.domain.model.VehicleHistoryItem
import com.ekotak.teamtalk.domain.model.VehicleRule
import com.ekotak.teamtalk.domain.model.VehicleTasks
import com.ekotak.teamtalk.domain.usecase.map.VehicleCardUseCases
import com.ekotak.teamtalk.presentation.crm.PickedFile
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Zakładki karty auta — pierwsza to dotychczasowa historia trasy. */
enum class VehicleTab(val label: String) {
    TRASA("Trasa"),
    ZADANIA("Zadania"),
    PLIKI("Pliki"),
    HISTORIA("Historia"),
    REGULY("Reguły"),
}

/**
 * Zakładki karty auta poza trasą (flota-karta-auta.md, E5). Każda dociąga się
 * dopiero przy pierwszym wejściu i wyłącznie z sieci — tak samo jak trasa.
 * Na telefonie: pliki DODAJE się (aparat, D1), zadania otwiera w module Zadania,
 * a reguły tylko ogląda — ustawia się je w panelu.
 */
@HiltViewModel
class VehicleCardViewModel @Inject constructor(
    private val card: VehicleCardUseCases,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val assetId: String = savedStateHandle["assetId"] ?: ""

    /** Stan jednej zakładki: null = nie pobrano, błąd osobno, żeby nie gubić starej treści. */
    data class Slot<T>(val data: T? = null, val loading: Boolean = false, val error: String? = null)

    data class UiState(
        val tab: VehicleTab = VehicleTab.TRASA,
        val tasks: Slot<VehicleTasks> = Slot(),
        val files: Slot<List<VehicleFile>> = Slot(),
        val history: Slot<List<VehicleHistoryItem>> = Slot(),
        val rules: Slot<List<VehicleRule>> = Slot(),
        val uploading: Boolean = false,
        val message: String? = null,
        /** Kategoria, do której trafi następne zdjęcie/plik. */
        val uploadCategory: String = "dowod_rejestracyjny",
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun select(tab: VehicleTab) {
        _ui.update { it.copy(tab = tab) }
        when (tab) {
            VehicleTab.ZADANIA -> if (_ui.value.tasks.data == null) loadTasks()
            VehicleTab.PLIKI -> if (_ui.value.files.data == null) loadFiles()
            VehicleTab.HISTORIA -> if (_ui.value.history.data == null) loadHistory()
            VehicleTab.REGULY -> if (_ui.value.rules.data == null) loadRules()
            VehicleTab.TRASA -> Unit
        }
    }

    fun refresh() = when (_ui.value.tab) {
        VehicleTab.ZADANIA -> loadTasks()
        VehicleTab.PLIKI -> loadFiles()
        VehicleTab.HISTORIA -> loadHistory()
        VehicleTab.REGULY -> loadRules()
        VehicleTab.TRASA -> Unit
    }

    fun setUploadCategory(key: String) = _ui.update { it.copy(uploadCategory = key) }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    private fun loadTasks() =
        load({ card.tasks(assetId) }, "Nie udało się pobrać zadań auta", { it.tasks }) { s, v -> s.copy(tasks = v) }
    private fun loadFiles() =
        load({ card.files(assetId) }, "Nie udało się pobrać plików auta", { it.files }) { s, v -> s.copy(files = v) }
    private fun loadHistory() =
        load({ card.history(assetId) }, "Nie udało się pobrać historii auta", { it.history }) { s, v -> s.copy(history = v) }
    private fun loadRules() =
        load({ card.rules(assetId) }, "Nie udało się pobrać reguł auta", { it.rules }) { s, v -> s.copy(rules = v) }

    /** Stara treść zakładki zostaje na ekranie, gdy odświeżenie padnie bez zasięgu. */
    private fun <T> load(
        fetch: suspend () -> T,
        fallback: String,
        get: (UiState) -> Slot<T>,
        put: (UiState, Slot<T>) -> UiState,
    ) {
        if (assetId.isBlank()) return
        viewModelScope.launch {
            _ui.update { put(it, get(it).copy(loading = true, error = null)) }
            val result = runCatching { fetch() }
            _ui.update { state ->
                result.fold(
                    onSuccess = { put(state, Slot(data = it)) },
                    onFailure = { put(state, get(state).copy(loading = false, error = crmErrorMessage(it, fallback))) },
                )
            }
        }
    }

    /** Wgranie zdjęć/plików do wybranej kategorii — wymaga zasięgu (karta auta jest online). */
    fun upload(files: List<PickedFile>) {
        if (files.isEmpty() || assetId.isBlank()) return
        val category = _ui.value.uploadCategory
        _ui.update { it.copy(uploading = true, message = null) }
        viewModelScope.launch {
            var failed: Throwable? = null
            for (f in files) {
                runCatching { card.upload(assetId, category, f.name, f.contentType, f.bytes) }
                    .onFailure { failed = it }
            }
            _ui.update {
                it.copy(
                    uploading = false,
                    message = failed?.let { e -> crmErrorMessage(e, "Nie udało się wgrać pliku") }
                        ?: if (files.size == 1) "Wgrano plik." else "Wgrano ${files.size} pliki.",
                )
            }
            loadFiles()
        }
    }

    /** Pobiera plik do cache i oddaje systemowi do otwarcia. */
    fun open(file: VehicleFile) {
        viewModelScope.launch {
            val result = runCatching {
                val bytes = card.download(file.downloadPath)
                withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "deal-docs/open").apply { mkdirs() }
                    File(dir, file.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")).apply { writeBytes(bytes) }
                }
            }
            result.fold(
                onSuccess = { saved ->
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", saved)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, file.contentType.ifBlank { "*/*" })
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure { _ui.update { it.copy(message = "Brak aplikacji do otwarcia tego pliku.") } }
                },
                onFailure = { e -> _ui.update { it.copy(message = crmErrorMessage(e, "Nie udało się pobrać pliku")) } },
            )
        }
    }
}
