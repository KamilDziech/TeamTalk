package com.ekotak.teamtalk.presentation.crm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.ArticleGate
import com.ekotak.teamtalk.domain.model.KnowledgeArticle
import com.ekotak.teamtalk.domain.repository.AuthRepository
import com.ekotak.teamtalk.domain.usecase.deal.GenerateKnowledgeArticleUseCase
import com.ekotak.teamtalk.domain.usecase.deal.GetArticleGateUseCase
import com.ekotak.teamtalk.domain.usecase.deal.GetKnowledgeArticlesUseCase
import com.ekotak.teamtalk.domain.usecase.deal.SendArticleToClientUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ekran artykułu wiedzy dla jednej instalacji deala. Osobny od karty, bo
 * artykuł to kilka ekranów tekstu — w kaflu zakładki LEAD zasłoniłby wszystko,
 * co jest wokół niego.
 *
 * Świadomie bez auto-generacji przy wejściu (panel ją ma): na telefonie
 * generowanie potrafi trwać kilkanaście sekund i kosztuje wywołanie modelu, więc
 * decyzję zostawiamy handlowcowi — z sieci w terenie to zauważalna różnica.
 */
@HiltViewModel
class KnowledgeArticleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getArticlesUseCase: GetKnowledgeArticlesUseCase,
    private val getGateUseCase: GetArticleGateUseCase,
    private val generateArticleUseCase: GenerateKnowledgeArticleUseCase,
    private val sendArticleToClientUseCase: SendArticleToClientUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val dealId: String = savedStateHandle["dealId"] ?: ""
    private val categoryId: String = savedStateHandle["categoryId"] ?: ""

    data class UiState(
        val isLoading: Boolean = true,
        val isGenerating: Boolean = false,
        val isSending: Boolean = false,
        val confirmingSend: Boolean = false,
        val canManage: Boolean = false,
        val article: KnowledgeArticle? = null,
        val gate: ArticleGate = ArticleGate(),
        val error: String? = null,
        val message: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        loadPermissions()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val article = getArticlesUseCase(dealId).firstOrNull { it.categoryId == categoryId }
                val gate = getGateUseCase(dealId)
                _uiState.update {
                    it.copy(isLoading = false, article = article, gate = gate)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = crmErrorMessage(e, "Nie udało się wczytać artykułu"),
                    )
                }
            }
        }
    }

    /**
     * Uprawnienia czytamy tak samo jak karta deala — z `GET /api/me`, świeże.
     * Bez `deal.manage` ekran zostaje czytelnią: generowanie i wysyłka znikają.
     */
    private fun loadPermissions() {
        viewModelScope.launch {
            val canManage = try {
                authRepository.getCurrentUser().permissions.contains(PERMISSION_DEAL_MANAGE)
            } catch (_: Exception) {
                false
            }
            _uiState.update { it.copy(canManage = canManage) }
        }
    }

    /** Generowanie (lub odświeżenie) artykułu. Bramkę egzekwuje API — 422. */
    fun generate() {
        if (_uiState.value.isGenerating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, message = null) }
            try {
                val article = generateArticleUseCase(dealId, categoryId)
                _uiState.update {
                    it.copy(isGenerating = false, article = article, message = "Artykuł gotowy")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        message = crmErrorMessage(e, "Nie udało się wygenerować artykułu"),
                    )
                }
            }
        }
    }

    fun askSend(confirming: Boolean) {
        _uiState.update { it.copy(confirmingSend = confirming) }
    }

    /** Wysyłka artykułu klientowi — potwierdzenie pokazuje ekran, nie ViewModel. */
    fun send() {
        val article = _uiState.value.article ?: return
        if (_uiState.value.isSending) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, message = null) }
            try {
                sendArticleToClientUseCase(dealId, article)
                _uiState.update {
                    it.copy(
                        isSending = false,
                        confirmingSend = false,
                        message = "Wysłano artykuł klientowi",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        confirmingSend = false,
                        message = crmErrorMessage(e, "Nie udało się wysłać artykułu"),
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
