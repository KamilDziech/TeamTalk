package com.ekotak.teamtalk.presentation.training

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.GradeResult
import com.ekotak.teamtalk.domain.model.PlayableLesson
import com.ekotak.teamtalk.domain.model.QuestionKind
import com.ekotak.teamtalk.domain.model.TrainingAnswer
import com.ekotak.teamtalk.domain.repository.TrainingRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.File
import javax.inject.Inject

/** Etap lekcji: najpierw film, potem test, na końcu wynik. */
enum class LessonPhase { VIDEO, QUIZ, RESULT }

/** Sekundy na jedno pytanie (ustalenie 2026-09-06 — panel ma 60 s na całość). */
const val SECONDS_PER_QUESTION = 20

/**
 * Ile trzeba obejrzeć, żeby odblokować test. 95%, nie 100% — YouTube potrafi
 * skończyć raportowanie ułamek sekundy przed końcem, a przy filmie napisów
 * końcowych nikt nie ogląda.
 */
private const val WATCHED_THRESHOLD = 0.95f

/**
 * Minimalny czas na ekranie, po którym wolno ręcznie potwierdzić obejrzenie
 * filmu ze źródła, które nie raportuje postępu (Loom i reszta). Bez tego
 * „Obejrzałem" byłoby jednym kliknięciem po wejściu.
 */
private const val MANUAL_CONFIRM_AFTER_SECONDS = 30

/**
 * Lekcja: film → test → wynik.
 *
 * Dwie rzeczy różnią ten ekran od panelu (ustalenia 2026-09-06):
 * 1. test odblokowuje się dopiero PO obejrzeniu filmu — w panelu „Krok 2" był
 *    samym przyciskiem i nikt nie sprawdzał, czy ktokolwiek oglądał;
 * 2. limit czasu skaluje się z liczbą pytań (20 s/pyt.), bo na telefonie
 *    pytania przewija się kciukiem i 60 s na całość było nie do zrobienia.
 * Auto-wysyłka po zejściu do zera zostaje jak w panelu.
 */
@HiltViewModel
class LessonViewModel @Inject constructor(
    private val repository: TrainingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        /** 403 z `/play` — lekcja nie jest przypisana temu użytkownikowi. */
        val forbidden: Boolean = false,
        val error: String? = null,
        val data: PlayableLesson? = null,
        val phase: LessonPhase = LessonPhase.VIDEO,
        /** Ułamek obejrzanego materiału (0–1); tylko dla źródeł raportujących. */
        val watched: Float = 0f,
        /** Czy odtwarzacz w ogóle raportuje postęp (YouTube tak, Loom nie). */
        val trackable: Boolean = false,
        val manualWatched: Boolean = false,
        /** Sekundy spędzone na etapie filmu — otwiera ręczne potwierdzenie. */
        val dwellSeconds: Int = 0,
        val questionIndex: Int = 0,
        val answers: Map<String, List<Int>> = emptyMap(),
        val remainingSeconds: Int = 0,
        val submitting: Boolean = false,
        val result: GradeResult? = null,
        val message: String? = null,
    ) {
        val questions get() = data?.questions.orEmpty()
        val current get() = questions.getOrNull(questionIndex)
        val answeredCount get() = questions.count { answers[it.id].orEmpty().isNotEmpty() }
        val allAnswered get() = questions.isNotEmpty() && answeredCount == questions.size

        /** Bramka testu: obejrzane albo potwierdzone tam, gdzie mierzyć się nie da. */
        val videoDone: Boolean get() = when {
            questions.isEmpty() -> true
            trackable -> watched >= WATCHED_THRESHOLD || manualWatched
            else -> manualWatched
        }
        val canConfirmManually: Boolean get() = dwellSeconds >= MANUAL_CONFIRM_AFTER_SECONDS
        val totalSeconds: Int get() = questions.size * SECONDS_PER_QUESTION
    }

    private val lessonId: String = savedStateHandle.get<String>("lessonId").orEmpty()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var dwellJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, forbidden = false) }
            try {
                val data = repository.getPlayable(lessonId)
                _state.update {
                    it.copy(
                        isLoading = false,
                        data = data,
                        trackable = isTrackable(data),
                    )
                }
                startDwellCounter()
            } catch (e: HttpException) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        forbidden = e.code() == 403,
                        error = if (e.code() == 403) null else crmErrorMessage(e, "Nie udało się otworzyć lekcji"),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = crmErrorMessage(e, "Nie udało się otworzyć lekcji"))
                }
            }
        }
    }

    // ── Film ────────────────────────────────────────────────────────────────

    /** Postęp z mostka JS: ułamek FAKTYCZNIE odtworzonego materiału. */
    fun onWatchProgress(fraction: Float) {
        _state.update { it.copy(watched = maxOf(it.watched, fraction.coerceIn(0f, 1f))) }
    }

    fun onVideoEnded() = _state.update { it.copy(watched = 1f) }

    fun confirmWatchedManually() = _state.update { it.copy(manualWatched = true) }

    // ── Test ────────────────────────────────────────────────────────────────

    fun startQuiz() {
        val total = _state.value.totalSeconds
        dwellJob?.cancel()
        _state.update {
            it.copy(
                phase = LessonPhase.QUIZ,
                questionIndex = 0,
                answers = emptyMap(),
                result = null,
                remainingSeconds = total,
            )
        }
        startTimer()
    }

    fun select(questionId: String, option: Int, kind: QuestionKind) {
        if (_state.value.submitting) return
        _state.update { prev ->
            val current = prev.answers[questionId].orEmpty()
            val next = when {
                kind == QuestionKind.SINGLE -> listOf(option)
                option in current -> current - option
                else -> current + option
            }
            prev.copy(answers = prev.answers + (questionId to next))
        }
    }

    fun nextQuestion() = _state.update {
        it.copy(questionIndex = (it.questionIndex + 1).coerceAtMost(it.questions.lastIndex))
    }

    fun previousQuestion() = _state.update {
        it.copy(questionIndex = (it.questionIndex - 1).coerceAtLeast(0))
    }

    /**
     * Wysyłka odpowiedzi. [auto] znaczy „czas minął" — lecą wtedy zaznaczenia
     * w takim stanie, w jakim są, łącznie z pustymi. Ocenia serwer, aplikacja
     * nie zna klucza odpowiedzi.
     */
    fun submit(auto: Boolean = false) {
        val snapshot = _state.value
        val assignmentId = snapshot.data?.assignmentId ?: return
        if (snapshot.submitting || snapshot.result != null) return
        timerJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, message = null) }
            try {
                val payload = snapshot.questions.map {
                    TrainingAnswer(questionId = it.id, selected = snapshot.answers[it.id].orEmpty())
                }
                val result = repository.submit(assignmentId, payload)
                _state.update {
                    it.copy(
                        submitting = false,
                        result = result,
                        phase = LessonPhase.RESULT,
                        message = if (auto) "Czas minął — odpowiedzi poszły automatycznie." else null,
                    )
                }
            } catch (e: Exception) {
                // Nieudana wysyłka zostawia test otwarty: zegar stanął, więc
                // ponowienie nie kosztuje już czasu na odpowiedzi.
                _state.update {
                    it.copy(
                        submitting = false,
                        message = crmErrorMessage(e, "Nie udało się wysłać odpowiedzi"),
                    )
                }
            }
        }
    }

    /**
     * Kolejne podejście — bez limitu prób, jak w panelu, ale z **nowym
     * zestawem pytań**: `/play` losuje do 10 pytań z puli lekcji przy każdym
     * wywołaniu, więc powtórka na starym zestawie byłaby nauką odpowiedzi,
     * a nie materiału. Gdy dociągnięcie padnie, powtarzamy poprzedni zestaw —
     * lepsze to niż odesłanie kogoś z ekranu wyniku z niczym.
     */
    fun retry() {
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, message = null) }
            val fresh = runCatching { repository.getPlayable(lessonId) }.getOrNull()
            _state.update {
                it.copy(
                    submitting = false,
                    data = fresh ?: it.data,
                    phase = LessonPhase.QUIZ,
                    answers = emptyMap(),
                    questionIndex = 0,
                    result = null,
                    message = if (fresh == null) {
                        "Nie udało się pobrać nowych pytań — powtarzasz poprzedni zestaw."
                    } else {
                        null
                    },
                )
            }
            // Limit liczy się od NOWEJ liczby pytań, więc dopiero po podmianie.
            _state.update { it.copy(remainingSeconds = it.totalSeconds) }
            startTimer()
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun openCertificate(cacheDir: File, onReady: (File) -> Unit) {
        viewModelScope.launch {
            try {
                val dir = File(cacheDir, "certificates").apply { mkdirs() }
                val target = File(dir, "$lessonId.pdf")
                if (!target.exists() || target.length() == 0L) {
                    repository.downloadCertificate(lessonId, target)
                }
                onReady(target)
            } catch (e: Exception) {
                _state.update { it.copy(message = crmErrorMessage(e, "Nie udało się pobrać certyfikatu")) }
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        dwellJob?.cancel()
        super.onCleared()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val remaining = _state.value.remainingSeconds - 1
                _state.update { it.copy(remainingSeconds = maxOf(0, remaining)) }
                if (remaining <= 0) {
                    submit(auto = true)
                    return@launch
                }
            }
        }
    }

    private fun startDwellCounter() {
        dwellJob?.cancel()
        dwellJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { it.copy(dwellSeconds = it.dwellSeconds + 1) }
            }
        }
    }

    /** Postęp umie zaraportować tylko odtwarzacz YouTube (IFrame API). */
    private fun isTrackable(data: PlayableLesson): Boolean =
        data.lesson.videoProvider.equals("youtube", ignoreCase = true) &&
            youtubeId(data.lesson.videoUrl) != null
}
