package com.habitat.app.ui.world

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitat.app.domain.repository.WorldRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WorldViewModel @Inject constructor(
    private val worldRepository: WorldRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorldUiState>(WorldUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadWorld()
    }

    fun retry() {
        loadWorld()
    }

    private fun loadWorld() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = WorldUiState.Loading
            try {
                worldRepository.ensureStarterIsland()
                worldRepository.observeStarterIsland().collect { island ->
                    _uiState.value = worldUiStateFor(island)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = WorldUiState.Error
            }
        }
    }
}
