package com.ekotak.teamtalk.presentation.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.InstallationType
import com.ekotak.teamtalk.domain.model.Product
import com.ekotak.teamtalk.domain.model.StockLevel
import com.ekotak.teamtalk.domain.model.StockType
import com.ekotak.teamtalk.domain.repository.InventoryRepository
import com.ekotak.teamtalk.domain.repository.InventorySnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * Zakładka „Stan" magazynu — mobilny odpowiednik karty „Produkty" z panelu
 * (`InventoryView.tsx`), zawężonej do tego, co da się przeczytać przy regale.
 *
 * Dane przychodzą jedną migawką z repozytorium, a filtrowanie i sortowanie robimy
 * lokalnie: przełączenie chipa nie ma prawa czekać na sieć, skoro cała kartoteka
 * i tak leży w Room.
 *
 * Etap E1 jest wyłącznie do odczytu — żadna akcja tutaj nie rusza stanu magazynu.
 */
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        /** Magazyn (zapas) vs Pod klienta — `stockType` z panelu. */
        val stockType: StockType = StockType.STOCK,
        val query: String = "",
        /** `null` = wszystkie poziomy. */
        val level: StockLevel? = null,
        val installation: String? = null,
        val zone: String? = null,
        val rows: List<Product> = emptyList(),
        /** Liczniki do chipów — z listy PRZED filtrem poziomu, żeby nie znikały. */
        val levelCounts: Map<StockLevel, Int> = emptyMap(),
        val installations: List<InstallationType> = emptyList(),
        val zones: List<String> = emptyList(),
        val totalInWorld: Int = 0,
        val reservationsAvailable: Boolean = true,
        val ordersAvailable: Boolean = true,
        val syncedAt: Long? = null,
    ) {
        /** Liczba filtrów odbiegających od domyślnych — kropka przy ikonie filtra. */
        val activeFilterCount: Int
            get() = listOf(level != null, installation != null, zone != null).count { it }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var snapshot = InventorySnapshot()

    init {
        observe()
        load(initial = true)
    }

    private fun observe() {
        viewModelScope.launch {
            repository.observe().collect { snap ->
                snapshot = snap
                _uiState.update {
                    it.copy(
                        reservationsAvailable = snap.reservationsAvailable,
                        ordersAvailable = snap.ordersAvailable,
                        syncedAt = snap.syncedAt,
                    )
                }
                recompute()
            }
        }
    }

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                if (initial) it.copy(isLoading = true, error = null)
                else it.copy(isRefreshing = true, error = null)
            }
            val error = runCatching { repository.refresh() }.exceptionOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    // Cache zostaje na ekranie — komunikat mówi tylko, że świeżych
                    // danych nie ma; pusta lista przy zerwanej sieci byłaby kłamstwem.
                    error = error?.let(::inventoryErrorMessage),
                )
            }
        }
    }

    fun refresh() = load(initial = false)

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun setStockType(type: StockType) {
        if (_uiState.value.stockType == type) return
        // Filtry są własnością świata: „poniżej minimum" nie ma sensu wśród
        // pozycji pod klienta, które celu nie mają.
        _uiState.update { it.copy(stockType = type, level = null, installation = null, zone = null) }
        recompute()
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
        recompute()
    }

    fun setLevel(value: StockLevel?) {
        _uiState.update { it.copy(level = if (it.level == value) null else value) }
        recompute()
    }

    fun setInstallation(value: String?) {
        _uiState.update { it.copy(installation = if (it.installation == value) null else value) }
        recompute()
    }

    fun setZone(value: String?) {
        _uiState.update { it.copy(zone = if (it.zone == value) null else value) }
        recompute()
    }

    fun clearFilters() {
        _uiState.update { it.copy(level = null, installation = null, zone = null) }
        recompute()
    }

    private fun recompute() {
        val state = _uiState.value

        // Pozycje ukryte (ofertowe) są poza obiegiem magazynu — panel chowa je
        // z listy i telefon robi tak samo.
        val world = snapshot.products
            .filter { !it.isHidden && it.stockType == state.stockType }

        val searched = world.filter { matchesQuery(it, state.query) }
        val byInstall = searched.filter { matchesInstallation(it, state.installation) }
        val byZone = byInstall.filter { state.zone == null || it.storageZone == state.zone }

        val rows = byZone
            .filter { state.level == null || it.level == state.level }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        _uiState.update {
            it.copy(
                rows = rows,
                // Liczniki liczymy po wyszukiwarce i pozostałych filtrach, ale
                // PRZED filtrem poziomu — inaczej chip zjadałby własny licznik.
                levelCounts = byZone.groupingBy { p -> p.level }.eachCount(),
                installations = world.mapNotNull { p -> InstallationType.from(p.installation) }
                    .distinct()
                    .sortedBy { inst -> inst.ordinal },
                zones = world.mapNotNull { p -> p.storageZone }.distinct().sorted(),
                totalInWorld = world.size,
            )
        }
    }

    private fun matchesQuery(p: Product, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return listOfNotNull(
            p.name,
            p.code,
            p.producer,
            p.distributor,
            p.storageZone,
            p.storageShelf,
        ).any { it.contains(q, ignoreCase = true) }
    }

    /**
     * Filtr instalacji łapie też podkategorie: „Materiały ogólne" musi pokazać
     * hydraulikę i elektrykę, bo tak działa dwupoziomowa szyna w panelu.
     */
    private fun matchesInstallation(p: Product, filter: String?): Boolean {
        if (filter == null) return true
        if (p.installation == filter) return true
        return InstallationType.from(p.installation)?.parent == filter
    }
}

/** Komunikaty błędów magazynu — 403 znaczy tu co innego niż w CRM. */
fun inventoryErrorMessage(e: Throwable): String = when (e) {
    is HttpException -> when (e.code()) {
        401 -> "Sesja wygasła — zaloguj się ponownie"
        403 -> "Brak uprawnienia do magazynu (inventory.view)"
        in 500..599 -> "Błąd serwera (${e.code()}) — spróbuj ponownie"
        else -> "Nie udało się pobrać magazynu (kod ${e.code()})"
    }
    is IOException -> "Brak połączenia — pokazujemy ostatnie pobrane dane"
    else -> e.message ?: "Nie udało się pobrać magazynu"
}
