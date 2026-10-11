package com.habitat.app.ui.world

import com.habitat.app.domain.model.WorldIsland

sealed interface WorldUiState {
    data object Loading : WorldUiState
    data class Content(val island: WorldIsland) : WorldUiState
    data object Error : WorldUiState
}

internal fun worldUiStateFor(island: WorldIsland?): WorldUiState =
    island?.let(WorldUiState::Content) ?: WorldUiState.Loading
