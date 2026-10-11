package com.habitat.app.ui.world

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitat.app.domain.model.BuildOperation
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.repository.WorldRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class WorldViewModel @Inject constructor(
    private val worldRepository: WorldRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorldUiState>(WorldUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _buildModeState = MutableStateFlow<WorldBuildUiState>(WorldBuildUiState.Inactive)
    val buildModeState = _buildModeState.asStateFlow()

    private val buildCommandMutex = Mutex()
    private var loadJob: Job? = null

    init {
        loadWorld()
    }

    fun retry() {
        loadWorld()
    }

    /** API skeleton for the Build-mode screen; behavior is added in the following implementation slice. */
    fun enterBuildMode() = Unit

    fun exitBuildMode() = Unit

    fun selectDefinition(definitionId: String?) = Unit

    fun selectPlacedItem(itemId: String?) = Unit

    fun selectTool(tool: WorldBuildTool) = Unit

    fun queuePlacement(item: PlacedWorldItem) = Unit

    fun applyOperation(operation: BuildOperation) = Unit

    fun applyQueuedPlacements() = Unit

    fun cancelQueuedPlacements() = Unit

    fun undoBuild() = Unit

    fun redoBuild() = Unit

    fun retryBuildSave() = Unit

    /** Serializes the full calculate-save-publish sequence in the Build-mode implementation. */
    internal suspend fun <T> withBuildCommandLock(block: suspend () -> T): T =
        buildCommandMutex.withLock { block() }

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
