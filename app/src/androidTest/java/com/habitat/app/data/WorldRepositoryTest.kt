package com.habitat.app.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.habitat.app.data.local.AppDatabase
import com.habitat.app.data.local.WorldDao
import com.habitat.app.data.repository.WorldRepositoryImpl
import com.habitat.app.domain.GameConfig
import com.habitat.app.domain.model.BuiltInPlaceableCatalog
import com.habitat.app.domain.model.BuildWorld
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldTerrain
import com.habitat.app.domain.model.createStarterIsland
import com.habitat.app.domain.repository.WorldRepository
import com.habitat.app.ui.world.WorldUiState
import com.habitat.app.ui.world.WorldViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorldRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "world-repository-test.db"
    private lateinit var database: AppDatabase
    private lateinit var worldDao: WorldDao
    private lateinit var repository: WorldRepository

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        openDatabase()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun freshWorldCreatesEightByEightStarterIslandWithExistingTerrain() = runBlocking {
        assertEquals("fresh database has no starter island", 0, worldDao.islandCount(GameConfig.STARTER_ISLAND_ID))

        repository.ensureStarterIsland()
        val island = repository.observeStarterIsland().first { it != null }

        assertNotNull("fresh world exposes the persisted starter island", island)
        assertEquals("starter width", 8, island!!.width)
        assertEquals("starter height", 8, island.height)
        assertEquals("starter has every terrain cell", 64, island.tiles.size)
        assertTrue("starter terrain uses the SPEC grass type", island.tiles.all { it.terrain == WorldTerrain.GRASS })
    }

    @Test
    fun repeatedInitializationDoesNotDuplicateIslandOrTiles() = runBlocking {
        repository.ensureStarterIsland()
        repository.ensureStarterIsland()
        repository.ensureStarterIsland()

        assertEquals("repeat initialization keeps one island", 1, worldDao.islandCount(GameConfig.STARTER_ISLAND_ID))
        assertEquals("repeat initialization keeps one row per starter tile", 64, worldDao.tileCount(GameConfig.STARTER_ISLAND_ID))
        assertEquals("repeat initialization preserves starter contents", createStarterIsland(), repository.observeStarterIsland().first())
    }

    @Test
    fun savedIslandSurvivesDatabaseAndRepositoryReopen() = runBlocking {
        repository.ensureStarterIsland()
        val saved = repository.observeStarterIsland().first { it != null }
        assertNotNull("island is saved before reopen", saved)

        database.close()
        openDatabase()
        repository.ensureStarterIsland()
        val reopened = repository.observeStarterIsland().first { it != null }

        assertEquals("reopened repository reads the same saved island", saved, reopened)
    }

    @Test
    fun placedItemsSurviveRepositoryReopenWithAllFieldsAndTerrainUnchanged() = runBlocking {
        repository.ensureStarterIsland()
        val island = repository.observeStarterIsland().first { it != null }!!
        val savedWorld = BuildWorld(
            island = island,
            items = listOf(
                PlacedWorldItem(
                    id = "placeholder-long-1",
                    definitionId = BuiltInPlaceableCatalog.TWO_TILE_LOG_ID,
                    anchor = TileCoordinate(5, 4),
                    rotationQuarterTurns = 3,
                    flipped = false
                ),
                PlacedWorldItem(
                    id = "placeholder-small-1",
                    definitionId = BuiltInPlaceableCatalog.SMALL_STONE_ID,
                    anchor = TileCoordinate(2, 3),
                    rotationQuarterTurns = 2,
                    flipped = true
                )
            )
        )

        repository.saveBuildWorld(savedWorld)
        assertEquals("all placed-item fields round-trip before close", savedWorld, repository.observeStarterBuildWorld().first())

        database.close()
        openDatabase()
        repository.ensureStarterIsland()
        val reopened = repository.observeStarterBuildWorld().first { it != null }

        assertEquals("placed items survive repository reopen", savedWorld, reopened)
        assertEquals("saving items does not rewrite terrain", island, reopened!!.island)
    }

    @Test
    fun failedPlacedItemSaveRollsBackThePreviousCollection() = runBlocking {
        repository.ensureStarterIsland()
        val island = repository.observeStarterIsland().first { it != null }!!
        val previousWorld = BuildWorld(
            island = island,
            items = listOf(PlacedWorldItem("kept", BuiltInPlaceableCatalog.SMALL_STONE_ID, TileCoordinate(1, 1)))
        )
        repository.saveBuildWorld(previousWorld)

        var constraintFailure: SQLiteConstraintException? = null
        try {
            repository.saveBuildWorld(
                BuildWorld(
                    island = island,
                    items = listOf(
                        PlacedWorldItem("duplicate", BuiltInPlaceableCatalog.SMALL_STONE_ID, TileCoordinate(2, 2)),
                        PlacedWorldItem("duplicate", BuiltInPlaceableCatalog.TWO_TILE_LOG_ID, TileCoordinate(4, 4))
                    )
                )
            )
        } catch (failure: SQLiteConstraintException) {
            constraintFailure = failure
        }

        assertNotNull("duplicate item ID reaches the Room primary-key constraint", constraintFailure)
        assertEquals(
            "failed replacement preserves the previous item collection",
            previousWorld,
            repository.observeStarterBuildWorld().first()
        )
        assertEquals("failed replacement preserves terrain", island, repository.observeStarterIsland().first())
    }

    @Test
    fun viewModelShowsLoadingThenRendersRepositoryIsland() = runBlocking {
        val store = androidx.lifecycle.ViewModelStore()
        val provider = androidx.lifecycle.ViewModelProvider(
            store,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    WorldViewModel(repository) as T
            }
        )
        try {
            val viewModel = provider[WorldViewModel::class.java]
            assertEquals("screen begins in loading state", WorldUiState.Loading, viewModel.uiState.value)

            val state = withTimeout(10_000) {
                viewModel.uiState.first { it is WorldUiState.Content }
            }
            assertEquals("screen renders the saved island data", WorldUiState.Content(createStarterIsland()), state)
        } finally {
            store.clear()
        }
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        worldDao = database.worldDao()
        repository = WorldRepositoryImpl(database, worldDao)
    }
}
