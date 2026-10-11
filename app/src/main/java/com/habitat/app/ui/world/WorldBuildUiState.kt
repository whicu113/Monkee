package com.habitat.app.ui.world

import com.habitat.app.domain.model.BuildModeSession
import com.habitat.app.domain.model.BuildOperation
import com.habitat.app.domain.model.PlacedWorldItem

/** Transient Build-mode UI state; none of this state is persisted across process death. */
sealed interface WorldBuildUiState {
    data object Inactive : WorldBuildUiState

    data class Editing(
        val session: BuildModeSession,
        val activeTool: WorldBuildTool = WorldBuildTool.PLACE,
        val selectedDefinitionId: String? = null,
        val selectedItemId: String? = null,
        val queuedPlacements: List<PlacedWorldItem> = emptyList(),
        val saveStatus: WorldBuildSaveStatus = WorldBuildSaveStatus.Idle
    ) : WorldBuildUiState
}

enum class WorldBuildTool {
    PLACE,
    MOVE,
    ROTATE,
    FLIP,
    ERASER
}

enum class WorldBuildFeedback {
    EDIT_REJECTED,
    NO_CHANGE
}

sealed interface WorldBuildSaveStatus {
    data object Idle : WorldBuildSaveStatus
    data class Saving(val command: WorldBuildSaveCommand) : WorldBuildSaveStatus
    data class Failed(val retry: WorldBuildRetry) : WorldBuildSaveStatus
}

sealed interface WorldBuildSaveCommand {
    data class Edit(val operation: BuildOperation) : WorldBuildSaveCommand
    data object Undo : WorldBuildSaveCommand
    data object Redo : WorldBuildSaveCommand
}

/** Exact candidate retained so a failed edit, undo, redo, or queued batch can be retried. */
data class WorldBuildRetry(
    val command: WorldBuildSaveCommand,
    val candidateSession: BuildModeSession,
    val queuedPlacements: List<PlacedWorldItem>
)
