package com.habitat.app.domain.repository

import com.habitat.app.domain.model.BuildWorld
import com.habitat.app.domain.model.WorldIsland
import kotlinx.coroutines.flow.Flow

interface WorldRepository {
    fun observeStarterIsland(): Flow<WorldIsland?>
    fun observeStarterBuildWorld(): Flow<BuildWorld?>
    suspend fun ensureStarterIsland()
    suspend fun saveBuildWorld(world: BuildWorld)
}
