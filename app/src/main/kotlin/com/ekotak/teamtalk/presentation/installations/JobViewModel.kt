package com.ekotak.teamtalk.presentation.installations

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.files.MontazPhotoStore
import com.ekotak.teamtalk.domain.model.MontazJob
import com.ekotak.teamtalk.domain.model.MontazMaterial
import com.ekotak.teamtalk.domain.model.MontazPhoto
import com.ekotak.teamtalk.domain.model.MontazProtocol
import com.ekotak.teamtalk.domain.model.MontazStatus
import com.ekotak.teamtalk.domain.model.ProtocolAnswer
import com.ekotak.teamtalk.domain.model.ProtocolQuestion
import com.ekotak.teamtalk.domain.model.TaskLink
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskTeam
import com.ekotak.teamtalk.domain.model.membersFrom
import com.ekotak.teamtalk.domain.model.packKey
import com.ekotak.teamtalk.domain.model.missing
import com.ekotak.teamtalk.domain.model.progress
import com.ekotak.teamtalk.domain.montaz.MontazTool
import com.ekotak.teamtalk.domain.repository.MontazJobRepository
import com.ekotak.teamtalk.domain.repository.MontazRepository
import com.ekotak.teamtalk.domain.repository.MontazSaveResult
import com.ekotak.teamtalk.domain.repository.TaskRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import com.ekotak.teamtalk.presentation.crm.formatQty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * JEDEN WYJAZD — karta, pakowanie i protokół na jednym stanie.
 *
 * Trzy ekrany, jeden ViewModel, bo to jedna robota: licznik spakowanych pozycji
 * musi być ten sam na karcie i na liście pakowania, a pasek kroków — zgodny
 * z tym, czy protokół jest już zamknięty. Rozbicie na trzy stany kończyłoby się
 * kartą mówiącą „12/21" i listą pokazującą co innego.
 *
 * Materiał, wydanie i zdjęcia biorą się z [MontazRepository] karty deala:
 * magazyn ma jedną prawdę o tym, co wydano, i nie dublujemy jej dla telefonu.
 */
@HiltViewModel
class JobViewModel @Inject constructor(
    private val jobs: MontazJobRepository,
    private val montaz: MontazRepository,
    private val photoStore: MontazPhotoStore,
    private val tasks: TaskRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val installationId: String = checkNotNull(savedStateHandle["jobId"])

    /** Krok dnia — pasek na karcie wyjazdu. */
    enum class Step { PAKOWANIE, DOJAZD, ROBOTA, PROTOKOL }

    data class UiState(
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val job: MontazJob? = null,
        val fromCache: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        // ── pakowanie ──
        val materials: List<MontazMaterial> = emptyList(),
        val materialsLoaded: Boolean = false,
        val packed: Set<String> = emptySet(),
        // ── protokół ──
        val protocol: MontazProtocol = MontazProtocol(""),
        val photos: List<MontazPhoto> = emptyList(),
        /** Braki zgłoszone koordynatorowi w tym wejściu na ekran. */
        val shortageReported: Boolean = false,
    ) {
        val tools: List<MontazTool> get() = job?.tools.orEmpty()

        /** Do wydania: pozycje magazynowe jeszcze nieoddane na budowę. */
        val toIssue: List<MontazMaterial>
            get() = materials.filter {
                it.issuedAt == null && !it.pending && it.packKey in packed
            }
        val shortages: List<MontazMaterial> get() = materials.filter { it.missing > 0 }

        val materialsPacked: Int get() = materials.count { it.packKey in packed }
        val toolsPacked: Int get() = tools.count { it.key in packed }

        /** Sprzęt obowiązkowy, którego jeszcze nie ma na aucie. */
        val toolsMissing: List<MontazTool>
            get() = tools.filter { it.required && it.key !in packed }
        val toolsRequiredLeft: Int get() = toolsMissing.size

        /** Co jest do zgłoszenia koordynatorowi: brak sprzętu i brak na stanie. */
        val shortageCount: Int get() = toolsMissing.size + shortages.size
        val packedTotal: Int get() = materialsPacked + toolsPacked
        val itemsTotal: Int get() = materials.size + tools.size

        val protocolProgress: Pair<Int, Int> get() = protocol.progress()
        val protocolMissing: List<ProtocolQuestion> get() = protocol.missing()

        /** Na którym kroku stoi ekipa — pasek na karcie nie jest ozdobą. */
        val step: Step
            get() = when {
                protocol.closed || job?.row?.status == MontazStatus.DONE -> Step.PROTOKOL
                job?.row?.status == MontazStatus.IN_PROGRESS -> Step.ROBOTA
                packedTotal >= itemsTotal && itemsTotal > 0 -> Step.DOJAZD
                else -> Step.PAKOWANIE
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val job = runCatching { jobs.getJob(installationId) }.getOrNull()
            if (job == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Nie mamy tej teczki w telefonie. Otwórz ten wyjazd raz " +
                            "w zasięgu — wtedy będzie dostępny także na budowie.",
                    )
                }
                return@launch
            }
            val materials = runCatching { montaz.getMaterials(installationId) }.getOrNull()
            val packed = runCatching { montaz.packedItems(installationId) }.getOrDefault(emptySet())
            val protocol = runCatching { jobs.getProtocol(job) }
                .getOrDefault(MontazProtocol(installationId))
            val photos = runCatching { montaz.getPhotos(installationId) }.getOrDefault(emptyList())
            _uiState.update {
                it.copy(
                    isLoading = false,
                    job = job,
                    fromCache = job.fromCache,
                    materials = materials.orEmpty(),
                    materialsLoaded = materials != null,
                    packed = packed,
                    protocol = protocol,
                    photos = photos,
                    error = null,
                )
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    // ── Pakowanie ─────────────────────────────────────────────────────────────

    /**
     * Ptaszek „leży na aucie". Zostaje w telefonie: wydanie z magazynu to
     * osobny, świadomy krok z podpisem, a nie efekt uboczny odhaczania rolek.
     */
    fun togglePacked(itemKey: String) {
        val checked = itemKey !in _uiState.value.packed
        _uiState.update {
            it.copy(packed = if (checked) it.packed + itemKey else it.packed - itemKey)
        }
        viewModelScope.launch {
            runCatching { montaz.setPacked(installationId, itemKey, checked) }
        }
    }

    /** Wydanie spakowanych pozycji — jedno kliknięcie na koniec pakowania. */
    fun issuePacked() {
        val job = _uiState.value.job ?: return
        val ids = _uiState.value.toIssue.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching {
                montaz.issueMaterials(job.row.dealId, installationId, ids)
            }
            val materials = runCatching { montaz.getMaterials(installationId) }
                .getOrDefault(_uiState.value.materials)
            _uiState.update { state ->
                state.copy(
                    isSaving = false,
                    materials = materials,
                    message = when (result.getOrNull()) {
                        MontazSaveResult.SENT -> "Materiał wydany na budowę."
                        MontazSaveResult.QUEUED ->
                            "Wydanie zapisane w telefonie — magazyn zobaczy je w zasięgu."
                        null -> crmErrorMessage(
                            result.exceptionOrNull() ?: Exception(),
                            "Nie udało się wydać materiału",
                        )
                    },
                )
            }
        }
    }

    /**
     * ZGŁOSZENIE BRAKU koordynatorowi — jedno zbiorcze zadanie, nie telefon
     * o 6:40 i nie pięć osobnych zgłoszeń.
     *
     * Do zadania idzie wszystko, czego ekipa nie zabiera: sprzęt obowiązkowy,
     * którego nie ma na aucie, i materiał, którego magazyn nie pokrywa. Jedno
     * i drugie jest problemem tej samej osoby, a rozbijanie tego na dwa kanały
     * kończy się tym, że o jednym z nich nikt nie pamięta.
     *
     * Zadanie wisi pod dealem (koordynator ma tam kontekst), z terminem na dzień
     * montażu i wysokim priorytetem. Bez zasięgu ląduje w kolejce zadań — tej
     * samej, co zadania spisane w terenie.
     */
    fun reportShortages() {
        val state = _uiState.value
        val job = state.job ?: return
        if (state.shortageCount == 0) {
            _uiState.update {
                it.copy(message = "Nie ma czego zgłaszać — komplet sprzętu i materiału.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            // Koordynatora szukamy po FUNKCJI, nie po roli (ADR-0013): koordynacja
            // jest funkcją, więc bywa doklejona komuś z inną rolą. Gdy nikogo nie
            // ma (albo nie ma zasięgu na listę osób), zadanie idzie bez wskazania
            // — lepiej nieprzypisane niż nieistniejące.
            val coordinator = runCatching { tasks.getMembers() }
                .getOrDefault(emptyList())
                .let { TaskTeam.KOORDYNATOR.membersFrom(it, selfId = null) }
                .firstOrNull()

            val result = runCatching {
                tasks.createTask(
                    title = shortageTitle(job),
                    description = shortageBody(state, job),
                    assigneeId = coordinator?.id,
                    dueAt = job.row.scheduledAt,
                    priority = TaskPriority.HIGH,
                    link = TaskLink.Deal(job.row.dealId),
                )
            }
            val task = result.getOrNull()
            _uiState.update { s ->
                s.copy(
                    isSaving = false,
                    shortageReported = task != null,
                    message = when {
                        task == null -> crmErrorMessage(
                            result.exceptionOrNull() ?: Exception(),
                            "Nie udało się zgłosić braku",
                        )
                        task.id.startsWith(LOCAL_TASK_PREFIX) ->
                            "Brak zapisany w telefonie — zgłoszenie pójdzie, gdy wróci zasięg."
                        coordinator == null ->
                            "Brak zgłoszony. Zadanie czeka nieprzypisane — koordynator znajdzie " +
                                "je na liście zadań deala."
                        else -> "Brak zgłoszony — zadanie dla: ${coordinator.displayName}."
                    },
                )
            }
        }
    }

    private fun shortageTitle(job: MontazJob): String {
        val dzien = job.row.scheduledAt?.take(10)
        return listOfNotNull(
            "Brak na montaż: ${job.row.clientName}",
            job.row.dealCode?.let { "($it)" },
            dzien,
        ).joinToString(" ")
    }

    /**
     * Treść zgłoszenia. Wypisujemy POZYCJE, a nie liczbę — koordynator ma po
     * przeczytaniu wiedzieć, co dowieźć, bez oddzwaniania na budowę.
     */
    private fun shortageBody(state: UiState, job: MontazJob): String = buildString {
        append("Zgłoszenie z telefonu ekipy (moduł Montaż).\n")
        job.row.scopeNames.takeIf { it.isNotEmpty() }?.let {
            append("Zakres: ${it.joinToString(" · ")}\n")
        }
        job.row.address?.let { append("Adres: $it\n") }

        if (state.toolsMissing.isNotEmpty()) {
            append("\nSPRZĘT — niespakowany, obowiązkowy:\n")
            state.toolsMissing.forEach { tool ->
                val ile = tool.qty?.let { " — ${formatQty(it)} ${tool.unit}" }.orEmpty()
                val skad = tool.owner.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
                append("• ${tool.name}$ile$skad\n")
            }
        }
        if (state.shortages.isNotEmpty()) {
            append("\nMATERIAŁ — magazyn nie pokrywa rezerwacji:\n")
            state.shortages.forEach { m ->
                append("• ${m.itemName} — brakuje ${formatQty(m.missing)} ${m.unit}\n")
            }
        }
    }

    // ── Stan roboty ───────────────────────────────────────────────────────────

    /** „Wyjeżdżamy" — montaż idzie w realizację, telefon odpala nawigację. */
    fun startWork() = setStatus(MontazStatus.IN_PROGRESS, "Montaż oznaczony jako w realizacji.")

    private fun setStatus(status: MontazStatus, sent: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching { jobs.setStatus(installationId, status) }
            val job = runCatching { jobs.getJob(installationId) }.getOrNull()
            _uiState.update { state ->
                state.copy(
                    isSaving = false,
                    job = job ?: state.job,
                    message = when (result.getOrNull()) {
                        MontazSaveResult.SENT -> sent
                        MontazSaveResult.QUEUED ->
                            "Zapisane w telefonie — biuro zobaczy to, gdy wróci zasięg."
                        null -> crmErrorMessage(
                            result.exceptionOrNull() ?: Exception(),
                            "Nie udało się zmienić stanu montażu",
                        )
                    },
                )
            }
        }
    }

    // ── Protokół ──────────────────────────────────────────────────────────────

    fun setAnswer(questionId: String, patch: (ProtocolAnswer) -> ProtocolAnswer) {
        _uiState.update { state ->
            val current = state.protocol.answers[questionId] ?: ProtocolAnswer()
            state.copy(
                protocol = state.protocol.copy(
                    answers = state.protocol.answers + (questionId to patch(current)),
                ),
            )
        }
    }

    fun setIssues(text: String) = editProtocol { it.copy(issues = text) }
    fun setAccepted(value: Boolean) = editProtocol { it.copy(accepted = value) }
    fun setClientName(name: String) = editProtocol { it.copy(clientName = name) }
    fun setSignature(dataUrl: String?) = editProtocol { it.copy(signature = dataUrl) }

    private fun editProtocol(patch: (MontazProtocol) -> MontazProtocol) {
        _uiState.update { it.copy(protocol = patch(it.protocol)) }
    }

    /**
     * Kadr do pytania protokołu. Zdjęcie idzie tą samą drogą co galeria montażu
     * (a bez zasięgu do kolejki), a do odpowiedzi wpisujemy jego identyfikator —
     * po nim panel i PDF wiedzą, że to zdjęcie manometru, a nie „jakieś".
     */
    fun addPhoto(questionId: String, bytes: ByteArray, fileName: String) {
        val job = _uiState.value.job ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val saved = runCatching {
                montaz.addPhoto(job.row.dealId, installationId, bytes, fileName)
            }.getOrNull()
            val photos = runCatching { montaz.getPhotos(installationId) }
                .getOrDefault(_uiState.value.photos)
            if (saved != null && saved.photoId.isNotBlank()) {
                setAnswer(questionId) { it.copy(photoIds = it.photoIds + saved.photoId) }
            }
            _uiState.update { state ->
                state.copy(
                    isSaving = false,
                    photos = photos,
                    message = when {
                        saved == null -> "Nie udało się dodać zdjęcia."
                        saved.result == MontazSaveResult.QUEUED ->
                            "Zdjęcie zapisane w telefonie — wyślemy, gdy wróci zasięg."
                        else -> null
                    },
                )
            }
            // Zdjęcie jest częścią odpowiedzi, więc zapisujemy protokół od razu:
            // telefon w kieszeni na budowie gubi stan łatwiej niż ktokolwiek
            // zdąży kliknąć „zapisz".
            saveProtocol(close = false, quiet = true)
        }
    }

    fun removeLastPhoto(questionId: String) {
        setAnswer(questionId) { it.copy(photoIds = it.photoIds.dropLast(1)) }
    }

    /**
     * Zapis protokołu. [close] domyka dokument i ustawia montaż na „gotowy" —
     * ale dopiero wtedy, gdy nie brakuje żadnej wymaganej odpowiedzi ani zdjęcia.
     */
    fun saveProtocol(close: Boolean, quiet: Boolean = false) {
        val protocol = _uiState.value.protocol
        if (close) {
            val missing = protocol.missing()
            if (missing.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        message = "Do zamknięcia brakuje: " +
                            missing.joinToString(", ") { q -> q.label } + ".",
                    )
                }
                return
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching { jobs.saveProtocol(protocol, close) }
            val job = runCatching { jobs.getJob(installationId) }.getOrNull()
            val fresh = job?.let { runCatching { jobs.getProtocol(it) }.getOrNull() }
            _uiState.update { state ->
                state.copy(
                    isSaving = false,
                    job = job ?: state.job,
                    protocol = fresh ?: state.protocol,
                    message = when {
                        quiet && result.isSuccess -> state.message
                        result.getOrNull() == MontazSaveResult.SENT && close ->
                            "Protokół zamknięty — montaż oznaczony jako gotowy."
                        result.getOrNull() == MontazSaveResult.SENT -> "Zapisano protokół."
                        result.getOrNull() == MontazSaveResult.QUEUED ->
                            "Protokół zapisany w telefonie — wyślemy, gdy wróci zasięg."
                        else -> crmErrorMessage(
                            result.exceptionOrNull() ?: Exception(),
                            "Nie udało się zapisać protokołu",
                        )
                    },
                )
            }
        }
    }

    // ── Miniatury ─────────────────────────────────────────────────────────────

    suspend fun photo(photo: MontazPhoto, targetPx: Int): ImageBitmap? =
        photoStore.image(photo, targetPx)

    fun cachedPhoto(photo: MontazPhoto): ImageBitmap? = photoStore.cached(photo)

    private companion object {
        /** Zadanie spisane bez zasięgu ma id z tym prefiksem (kolejka zadań). */
        const val LOCAL_TASK_PREFIX = "local:"
    }
}
