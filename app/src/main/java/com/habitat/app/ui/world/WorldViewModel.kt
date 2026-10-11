package com.habitat.app.ui.world

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitat.app.domain.model.BuildModeHistoryResult
import com.habitat.app.domain.model.BuildModeSession
import com.habitat.app.domain.model.BuildModeSessionResult
import com.habitat.app.domain.model.BuildOperation
import com.habitat.app.domain.model.BuildWorld
import com.habitat.app.domain.model.BuiltInPlaceableCatalog
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.repository.WorldRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class WorldViewModel @Inject constructor(
    private val worldRepository: WorldRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorldUiState>(WorldUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _renderModel = MutableStateFlow<WorldRenderModel?>(null)
    val renderModel = _renderModel.asStateFlow()

    private val _buildModeState = MutableStateFlow<WorldBuildUiState>(WorldBuildUiState.Inactive)
    val buildModeState = _buildModeState.asStateFlow()

    private val buildFeedbackChannel = Channel<WorldBuildFeedback>(Channel.BUFFERED)
    val buildFeedback = buildFeedbackChannel.receiveAsFlow()

    private val buildCommandMutex = Mutex()
    private var loadJob: Job? = null

    init {
        loadWorld()
    }

    fun retry() {
        loadWorld()
    }

    /** Opens a fresh, transient Build-mode session around the repository's saved world. */
    fun enterBuildMode() = launchBuildCommand {
        if (_buildModeState.value is WorldBuildUiState.Editing) return@launchBuildCommand
        try {
            worldRepository.ensureStarterIsland()
            val savedWorld = worldRepository.observeStarterBuildWorld().filterNotNull().first()
            _buildModeState.value = WorldBuildUiState.Editing(
                session = BuildModeSession.start(savedWorld)
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the ordinary World screen available if the saved build world cannot be read.
        }
    }

    /** Ends the transient editing session; persisted world items remain in the repository. */
    fun exitBuildMode() = launchBuildCommand {
        _buildModeState.value = WorldBuildUiState.Inactive
    }

    fun selectDefinition(definitionId: String?) = launchBuildCommand {
        val editing = currentIdleEditing() ?: return@launchBuildCommand
        if (definitionId != null && definitionId !in BuiltInPlaceableCatalog.definitions) return@launchBuildCommand
        _buildModeState.value = editing.copy(selectedDefinitionId = definitionId)
    }

    fun selectPlacedItem(itemId: String?) = launchBuildCommand {
        val editing = currentIdleEditing() ?: return@launchBuildCommand
        if (itemId != null && editing.session.world.items.none { it.id == itemId }) return@launchBuildCommand
        _buildModeState.value = editing.copy(selectedItemId = itemId)
    }

    fun selectTool(tool: WorldBuildTool) = launchBuildCommand {
        val editing = currentIdleEditing() ?: return@launchBuildCommand
        _buildModeState.value = editing.copy(activeTool = tool)
    }

    fun queuePlacement(item: PlacedWorldItem) = launchBuildCommand {
        val editing = currentIdleEditing() ?: return@launchBuildCommand
        _buildModeState.value = editing.copy(queuedPlacements = editing.queuedPlacements + item)
    }

    fun applyOperation(operation: BuildOperation) = launchBuildCommand {
        applyOperationLocked(operation)
    }

    fun onCanvasTileTapped(coordinate: TileCoordinate, batchMode: Boolean) = launchBuildCommand {
        val editing = currentIdleEditing() ?: return@launchBuildCommand
        val itemAtTile = hitTestPlacedWorldItem(
            coordinate = coordinate,
            renderModel = worldRenderModelFor(editing.session.world)
        )
        when (editing.activeTool) {
            WorldBuildTool.PLACE -> {
                if (itemAtTile != null) {
                    _buildModeState.value = editing.copy(selectedItemId = itemAtTile.id)
                    return@launchBuildCommand
                }
                val definitionId = editing.selectedDefinitionId
                if (definitionId == null) {
                    buildFeedbackChannel.trySend(WorldBuildFeedback.NO_CHANGE)
                    return@launchBuildCommand
                }
                val item = PlacedWorldItem(
                    id = UUID.randomUUID().toString(),
                    definitionId = definitionId,
                    anchor = coordinate
                )
                if (batchMode) {
                    _buildModeState.value = editing.copy(queuedPlacements = editing.queuedPlacements + item)
                } else {
                    applyOperationLocked(BuildOperation.Place(item))
                }
            }

            WorldBuildTool.MOVE -> {
                val selectedItemId = editing.selectedItemId
                if (selectedItemId == null) {
                    if (itemAtTile != null) _buildModeState.value = editing.copy(selectedItemId = itemAtTile.id)
                    else buildFeedbackChannel.trySend(WorldBuildFeedback.NO_CHANGE)
                } else {
                    applyOperationLocked(BuildOperation.Move(selectedItemId, coordinate))
                }
            }

            WorldBuildTool.ROTATE -> {
                val targetId = itemAtTile?.id ?: editing.selectedItemId
                if (targetId == null) buildFeedbackChannel.trySend(WorldBuildFeedback.NO_CHANGE)
                else {
                    _buildModeState.value = editing.copy(selectedItemId = targetId)
                    applyOperationLocked(BuildOperation.RotateClockwise(targetId))
                }
            }

            WorldBuildTool.FLIP -> {
                val targetId = itemAtTile?.id ?: editing.selectedItemId
                if (targetId == null) buildFeedbackChannel.trySend(WorldBuildFeedback.NO_CHANGE)
                else {
                    _buildModeState.value = editing.copy(selectedItemId = targetId)
                    applyOperationLocked(BuildOperation.FlipHorizontal(targetId))
                }
            }

            WorldBuildTool.ERASER -> applyOperationLocked(BuildOperation.Erase(coordinate))
        }
    }

    fun applyQueuedPlacements() = launchBuildCommand {
        val editing = currentIdleEditing() ?: return@launchBuildCommand
        if (editing.queuedPlacements.isEmpty()) return@launchBuildCommand
        applyOperationLocked(
            operation = BuildOperation.PlaceMany(editing.queuedPlacements),
            successfulQueue = emptyList()
        )
    }

    fun cancelQueuedPlacements() = launchBuildCommand {
        val editing = currentIdleEditing() ?: return@launchBuildCommand
        _buildModeState.value = editing.copy(queuedPlacements = emptyList())
    }

    fun undoBuild() = launchBuildCommand {
        val editing = currentIdleEditing() ?: return@launchBuildCommand
        when (val result = editing.session.undo()) {
            is BuildModeHistoryResult.NoOp -> buildFeedbackChannel.trySend(WorldBuildFeedback.NO_CHANGE)
            is BuildModeHistoryResult.Changed -> saveCandidateLocked(
                editing = editing,
                command = WorldBuildSaveCommand.Undo,
                candidate = result.session,
                queuedOnSuccess = editing.queuedPlacements
            )
        }
    }

    fun redoBuild() = launchBuildCommand {
        val editing = currentIdleEditing() ?: return@launchBuildCommand
        when (val result = editing.session.redo()) {
            is BuildModeHistoryResult.NoOp -> buildFeedbackChannel.trySend(WorldBuildFeedback.NO_CHANGE)
            is BuildModeHistoryResult.Changed -> saveCandidateLocked(
                editing = editing,
                command = WorldBuildSaveCommand.Redo,
                candidate = result.session,
                queuedOnSuccess = editing.queuedPlacements
            )
        }
    }

    fun retryBuildSave() = launchBuildCommand {
        val editing = _buildModeState.value as? WorldBuildUiState.Editing ?: return@launchBuildCommand
        val failed = editing.saveStatus as? WorldBuildSaveStatus.Failed ?: return@launchBuildCommand
        val retry = failed.retry
        _buildModeState.value = editing.copy(saveStatus = WorldBuildSaveStatus.Saving(retry.command))
        try {
            worldRepository.saveBuildWorld(retry.candidateSession.world)
        } catch (cancelled: CancellationException) {
            _buildModeState.value = editing.copy(saveStatus = WorldBuildSaveStatus.Failed(retry))
            throw cancelled
        } catch (failure: Exception) {
            Log.e(TAG, "World save failed (${failure.javaClass.simpleName})")
            _buildModeState.value = editing.copy(saveStatus = WorldBuildSaveStatus.Failed(retry))
            return@launchBuildCommand
        }
        publishSavedCandidate(
            editing = editing,
            candidate = retry.candidateSession,
            queuedPlacements = retry.queuedPlacements
        )
    }

    /** Serializes the full calculate-save-publish sequence for Build-mode commands. */
    internal suspend fun <T> withBuildCommandLock(block: suspend () -> T): T =
        buildCommandMutex.withLock { block() }

    private fun launchBuildCommand(command: suspend () -> Unit) {
        viewModelScope.launch {
            withBuildCommandLock { command() }
        }
    }

    /** Calculates, persists and only then publishes an edit, all while holding the command Mutex. */
    private suspend fun applyOperationLocked(
        operation: BuildOperation,
        successfulQueue: List<PlacedWorldItem>? = null
    ) {
        val editing = currentIdleEditing() ?: return
        when (val result = editing.session.apply(operation, BuiltInPlaceableCatalog.definitions)) {
            is BuildModeSessionResult.NoOp -> buildFeedbackChannel.trySend(WorldBuildFeedback.NO_CHANGE)
            is BuildModeSessionResult.Rejected -> buildFeedbackChannel.trySend(WorldBuildFeedback.EDIT_REJECTED)
            is BuildModeSessionResult.Applied -> saveCandidateLocked(
                editing = editing,
                command = WorldBuildSaveCommand.Edit(operation),
                candidate = result.session,
                queuedOnSuccess = successfulQueue ?: editing.queuedPlacements
            )
        }
    }

    /** The caller holds [buildCommandMutex] through candidate calculation, save, and publication. */
    private suspend fun saveCandidateLocked(
        editing: WorldBuildUiState.Editing,
        command: WorldBuildSaveCommand,
        candidate: BuildModeSession,
        queuedOnSuccess: List<PlacedWorldItem>
    ) {
        val retry = WorldBuildRetry(
            command = command,
            candidateSession = candidate,
            queuedPlacements = queuedOnSuccess
        )
        _buildModeState.value = editing.copy(saveStatus = WorldBuildSaveStatus.Saving(command))
        try {
            worldRepository.saveBuildWorld(candidate.world)
        } catch (cancelled: CancellationException) {
            _buildModeState.value = editing
            throw cancelled
        } catch (failure: Exception) {
            Log.e(TAG, "World save failed (${failure.javaClass.simpleName})")
            _buildModeState.value = editing.copy(saveStatus = WorldBuildSaveStatus.Failed(retry))
            return
        }
        publishSavedCandidate(editing, candidate, queuedOnSuccess)
    }

    private fun publishSavedCandidate(
        editing: WorldBuildUiState.Editing,
        candidate: BuildModeSession,
        queuedPlacements: List<PlacedWorldItem>
    ) {
        val selectedItem = editing.selectedItemId
            ?.takeIf { id -> candidate.world.items.any { it.id == id } }
        _buildModeState.value = editing.copy(
            session = candidate,
            selectedItemId = selectedItem,
            queuedPlacements = queuedPlacements,
            saveStatus = WorldBuildSaveStatus.Idle
        )
    }

    private fun currentIdleEditing(): WorldBuildUiState.Editing? =
        (_buildModeState.value as? WorldBuildUiState.Editing)
            ?.takeIf { it.saveStatus == WorldBuildSaveStatus.Idle }

    private companion object {
        const val TAG = "WorldViewModel"
    }

    private fun loadWorld() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = WorldUiState.Loading
            try {
                worldRepository.ensureStarterIsland()
                worldRepository.observeStarterBuildWorld().collect { world ->
                    _uiState.value = worldUiStateFor(world?.island)
                    _renderModel.value = world?.let(::worldRenderModelFor)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = WorldUiState.Error
            }
        }
    }
}
