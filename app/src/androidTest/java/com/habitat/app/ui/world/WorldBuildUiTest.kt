package com.habitat.app.ui.world

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitat.app.MainActivity
import com.habitat.app.domain.model.BuildWorld
import com.habitat.app.domain.model.BuiltInPlaceableCatalog
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldIsland
import com.habitat.app.domain.model.createStarterIsland
import com.habitat.app.domain.repository.WorldRepository
import com.habitat.app.ui.theme.MonkeeTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import androidx.compose.ui.unit.dp

@RunWith(AndroidJUnit4::class)
class WorldBuildUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val viewModelStores = mutableListOf<ViewModelStore>()

    @After
    fun clearViewModels() {
        viewModelStores.forEach(ViewModelStore::clear)
        viewModelStores.clear()
    }

    @Test
    fun enterAndExitBuildKeepsSavedWorld() {
        val fixture = showWorld()
        composeRule.onNodeWithText("Build").performClick()
        waitForEditing(fixture)
        composeRule.onNodeWithText("Exit Build").performClick()
        composeRule.waitUntil(5_000) { fixture.viewModel.buildModeState.value == WorldBuildUiState.Inactive }
        assertTrue("exit returns to read-only World", fixture.viewModel.buildModeState.value == WorldBuildUiState.Inactive)
        assertTrue("exit preserves saved world", fixture.repository.current.value?.items.orEmpty().isEmpty())
    }

    @Test
    fun placeViaPaletteAndUndoRedoPersistThroughUi() {
        val fixture = showWorld()
        enterBuild(fixture)
        composeRule.onNodeWithText("Stone").performClick()
        tapCanvas()
        composeRule.waitUntil(5_000) { fixture.repository.current.value?.items?.size == 1 }
        assertEquals("palette placement persists", 1, fixture.repository.current.value?.items?.size)

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitUntil(5_000) { fixture.repository.current.value?.items?.isEmpty() == true }
        composeRule.onNodeWithText("Redo").performClick()
        composeRule.waitUntil(5_000) { fixture.repository.current.value?.items?.size == 1 }
    }

    @Test
    fun savingDisablesEditingControls() {
        val fixture = showWorld()
        enterBuild(fixture)
        val gate = fixture.repository.pauseNextSave()
        composeRule.onNodeWithText("Stone").performClick()
        tapCanvas()
        composeRule.waitUntil(5_000) {
            fixture.repository.saveStarted.isCompleted &&
                (fixture.viewModel.buildModeState.value as? WorldBuildUiState.Editing)?.saveStatus is WorldBuildSaveStatus.Saving
        }

        composeRule.onNodeWithText("Stone").assertIsNotEnabled()
        composeRule.onNodeWithText("Undo").assertIsNotEnabled()
        composeRule.onNodeWithText("Saving changes…").assertIsDisplayed()
        gate.complete(Unit)
        composeRule.waitUntil(5_000) { fixture.viewModel.buildModeState.value is WorldBuildUiState.Editing && fixture.repository.current.value?.items?.isNotEmpty() == true }
    }

    @Test
    fun failedSaveShowsRetryAndRetryPersistsCandidate() {
        val fixture = showWorld()
        enterBuild(fixture)
        fixture.repository.failNextSave = true
        composeRule.onNodeWithText("Stone").performClick()
        tapCanvas()
        composeRule.waitUntil(5_000) {
            (fixture.viewModel.buildModeState.value as? WorldBuildUiState.Editing)?.saveStatus is WorldBuildSaveStatus.Failed
        }
        composeRule.onNodeWithText("Your changes haven't been saved yet.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.waitUntil(5_000) { fixture.repository.current.value?.items?.size == 1 }
    }

    @Test
    fun tilePickerHasLocalizedDescriptionsAndAccessibleTargets() {
        val fixture = showWorld()
        enterBuild(fixture)
        composeRule.onNodeWithText("Tiles").performClick()
        val tile = composeRule.onNodeWithContentDescription("row 1, column 1, grass, empty")
        tile.assertIsDisplayed()
        tile.assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithText("Stone").performClick()
        tile.performClick()
        composeRule.waitUntil(5_000) { fixture.repository.current.value?.items?.any { it.anchor == TileCoordinate(0, 0) } == true }
    }

    @Test
    fun itemListActionsMoveRotateFlipAndErase() {
        val initialItem = PlacedWorldItem(
            id = "listed-stone",
            definitionId = BuiltInPlaceableCatalog.SMALL_STONE_ID,
            anchor = TileCoordinate(1, 1)
        )
        val fixture = showWorld(listOf(initialItem))
        enterBuild(fixture)
        composeRule.onNodeWithText("Items").performClick()
        composeRule.onNodeWithText("Stone · row 2, column 2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Move Stone from row 2, column 2")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.waitUntil(5_000) {
            val editing = fixture.viewModel.buildModeState.value as? WorldBuildUiState.Editing
            editing?.activeTool == WorldBuildTool.MOVE && editing.selectedItemId == initialItem.id
        }
        composeRule.onNodeWithText("Tiles").performClick()
        composeRule.onNodeWithContentDescription("row 3, column 3, grass, empty").performClick()
        composeRule.waitUntil(5_000) { fixture.repository.current.value?.items?.singleOrNull()?.anchor == TileCoordinate(2, 2) }

        composeRule.onNodeWithText("Items").performClick()
        composeRule.onNodeWithContentDescription("Rotate Stone at row 3, column 3").performClick()
        composeRule.waitUntil(5_000) { fixture.repository.current.value?.items?.singleOrNull()?.rotationQuarterTurns == 1 }
        composeRule.onNodeWithContentDescription("Flip Stone at row 3, column 3").performClick()
        composeRule.waitUntil(5_000) { fixture.repository.current.value?.items?.singleOrNull()?.flipped == true }
        composeRule.onNodeWithContentDescription("Erase Stone at row 3, column 3").performClick()
        composeRule.waitUntil(5_000) { fixture.repository.current.value?.items?.isEmpty() == true }
    }

    @Test
    fun activityRecreationPreservesWorldRouteAndBuildSession() {
        composeRule.onNodeWithContentDescription("Open world").performClick()
        waitForText("Build")
        composeRule.onNodeWithText("Build").assertIsDisplayed()
        composeRule.onNodeWithText("Build").performClick()
        waitForText("Exit Build")
        composeRule.onNodeWithText("Exit Build").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        waitForText("Exit Build")
        composeRule.onNodeWithText("Exit Build").assertIsDisplayed()
    }

    @Test
    fun freshViewModelAfterProcessDeathShowsSavedWorldReadOnly() {
        val saved = PlacedWorldItem("persisted-piece", BuiltInPlaceableCatalog.SMALL_STONE_ID, TileCoordinate(2, 2))
        val fixture = showWorld(listOf(saved))
        composeRule.onNodeWithText("Build").assertIsDisplayed()
        assertTrue(
            "fresh World must not expose Build-mode Exit action",
            composeRule.onAllNodesWithText("Exit Build").fetchSemanticsNodes().isEmpty()
        )
        assertEquals("fresh ViewModel has no Build session", WorldBuildUiState.Inactive, fixture.viewModel.buildModeState.value)
        assertEquals("saved item is retained after process death", listOf(saved), fixture.repository.current.value?.items)
    }

    private fun showWorld(items: List<PlacedWorldItem> = emptyList()): Fixture {
        val repository = FakeWorldRepository(BuildWorld(createStarterIsland(), items))
        val viewModel = WorldViewModel(repository)
        val store = ViewModelStore().apply { put("world-ui-test-${viewModelStores.size}", viewModel) }
        viewModelStores += store
        composeRule.setContent {
            MonkeeTheme {
                WorldScreen(viewModel = viewModel, onNavigateBack = {})
            }
        }
        composeRule.waitUntil(5_000) { viewModel.uiState.value is WorldUiState.Content }
        return Fixture(repository, viewModel)
    }

    private fun enterBuild(fixture: Fixture) {
        composeRule.onNodeWithText("Build").performClick()
        waitForEditing(fixture)
    }

    private fun waitForEditing(fixture: Fixture) {
        composeRule.waitUntil(5_000) { fixture.viewModel.buildModeState.value is WorldBuildUiState.Editing }
    }

    private fun tapCanvas() {
        composeRule.onNodeWithContentDescription("Saved island, 8 by 8 tiles")
            .performTouchInput { click(center) }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private data class Fixture(
        val repository: FakeWorldRepository,
        val viewModel: WorldViewModel
    )

    private class FakeWorldRepository(initial: BuildWorld) : WorldRepository {
        val current = MutableStateFlow<BuildWorld?>(initial)
        var failNextSave = false
        var saveStarted = CompletableDeferred<Unit>()
        private var nextSaveGate: CompletableDeferred<Unit>? = null

        override fun observeStarterIsland(): Flow<WorldIsland?> = current.map { it?.island }
        override fun observeStarterBuildWorld(): Flow<BuildWorld?> = current
        override suspend fun ensureStarterIsland() = Unit

        fun pauseNextSave(): CompletableDeferred<Unit> {
            saveStarted = CompletableDeferred()
            return CompletableDeferred<Unit>().also { nextSaveGate = it }
        }

        override suspend fun saveBuildWorld(world: BuildWorld) {
            saveStarted.complete(Unit)
            nextSaveGate?.also { gate ->
                nextSaveGate = null
                gate.await()
            }
            if (failNextSave) {
                failNextSave = false
                throw IOException("simulated save failure")
            }
            current.value = world
        }
    }
}
