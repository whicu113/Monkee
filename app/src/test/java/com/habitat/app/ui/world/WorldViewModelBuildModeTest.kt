package com.habitat.app.ui.world

import androidx.lifecycle.ViewModelStore
import com.habitat.app.domain.model.BuildOperation
import com.habitat.app.domain.model.BuildWorld
import com.habitat.app.domain.model.BuiltInPlaceableCatalog
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldIsland
import com.habitat.app.domain.model.createStarterIsland
import com.habitat.app.domain.repository.WorldRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WorldViewModelBuildModeTest {
    private lateinit var testScope: TestScope

    @Test
    fun buildModeViewModelRequirementsReportEveryNamedRow() = runTest {
        testScope = this
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val failures = mutableListOf<String>()
        val rows: List<Pair<String, suspend () -> Unit>> = listOf(
            "enter starts with empty history" to { enterStartsWithEmptyHistory(failures) },
            "place saves then publishes" to {
                operationSavesThenPublishes(
                    row = "place saves then publishes",
                    initial = emptyBuildWorld(),
                    operation = BuildOperation.Place(item("placed", 1, 1)),
                    expected = emptyBuildWorld(listOf(item("placed", 1, 1))),
                    failures = failures
                )
            },
            "move saves then publishes" to {
                operationSavesThenPublishes(
                    row = "move saves then publishes",
                    initial = emptyBuildWorld(listOf(item("moved", 1, 1))),
                    operation = BuildOperation.Move("moved", TileCoordinate(2, 1)),
                    expected = emptyBuildWorld(listOf(item("moved", 2, 1))),
                    failures = failures
                )
            },
            "rotate saves then publishes" to {
                operationSavesThenPublishes(
                    row = "rotate saves then publishes",
                    initial = emptyBuildWorld(listOf(item("rotated", 1, 1, BuiltInPlaceableCatalog.TWO_TILE_LOG_ID))),
                    operation = BuildOperation.RotateClockwise("rotated"),
                    expected = emptyBuildWorld(listOf(item("rotated", 1, 1, BuiltInPlaceableCatalog.TWO_TILE_LOG_ID, rotation = 1))),
                    failures = failures
                )
            },
            "flip saves then publishes" to {
                operationSavesThenPublishes(
                    row = "flip saves then publishes",
                    initial = emptyBuildWorld(listOf(item("flipped", 1, 1, BuiltInPlaceableCatalog.TWO_TILE_LOG_ID))),
                    operation = BuildOperation.FlipHorizontal("flipped"),
                    expected = emptyBuildWorld(listOf(item("flipped", 1, 1, BuiltInPlaceableCatalog.TWO_TILE_LOG_ID, flipped = true))),
                    failures = failures
                )
            },
            "erase saves then publishes" to {
                operationSavesThenPublishes(
                    row = "erase saves then publishes",
                    initial = emptyBuildWorld(listOf(item("erased", 1, 1))),
                    operation = BuildOperation.Erase(TileCoordinate(1, 1)),
                    expected = emptyBuildWorld(),
                    failures = failures
                )
            },
            "rejected and no-op edits preserve world and history" to {
                rejectedAndNoOpPreserveWorldAndHistory(failures)
            },
            "single edit save failure preserves session and retries" to {
                failedSingleEditPreservesSessionAndRetries(failures)
            },
            "undo save failure preserves session and retries" to { failedUndoPreservesSessionAndRetries(failures) },
            "redo save failure preserves session and retries" to { failedRedoPreservesSessionAndRetries(failures) },
            "failedPlaceManySaveRetainsQueueAndRetryCommitsOnce" to {
                failedPlaceManyRetainsQueueAndRetryCommitsOnce(failures)
            },
            "place-many is one undo step" to { placeManyIsOneUndoStep(failures) },
            "undo and redo persist" to { undoRedoPersist(failures) },
            "exit clears transient state while saved items remain" to {
                exitClearsTransientStateAndKeepsSavedItems(failures)
            },
            "re-entry loads saved items with empty history" to { reentryLoadsSavedItems(failures) },
            "two rapid dependent taps are serialized" to { rapidDependentTapsAreSerialized(failures) },
            "process death reopens the route read-only" to { processDeathStartsReadOnly(failures) }
        )

        try {
            rows.forEach { (row, check) ->
                try {
                    check()
                } catch (failure: Throwable) {
                    failures += "$row: unexpected ${failure::class.simpleName}: ${failure.message}"
                }
            }
            assertTrue(
                "Build-mode ViewModel rows failed (${failures.size}):\n${failures.joinToString("\n")}",
                failures.isEmpty()
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun enterStartsWithEmptyHistory(failures: MutableList<String>) {
        fixture().use { fixture ->
            fixture.vm.enterBuildMode()
            fixture.advance()
            val editing = fixture.editing("enter starts with empty history", failures) ?: return
            expect(failures, "enter starts with empty history", !editing.session.canUndo, "new session has no undo history")
            expect(failures, "enter starts with empty history", !editing.session.canRedo, "new session has no redo history")
            expect(failures, "enter starts with empty history", editing.session.world == fixture.repository.current.value, "session starts from saved world")
        }
    }

    private suspend fun operationSavesThenPublishes(
        row: String,
        initial: BuildWorld,
        operation: BuildOperation,
        expected: BuildWorld,
        failures: MutableList<String>
    ) {
        fixture(initial).use { fixture ->
            fixture.vm.enterBuildMode()
            fixture.advance()
            val events = mutableListOf<String>()
            fixture.observeBuildState { state ->
                if (state is WorldBuildUiState.Editing && state.session.world == expected) events += "published"
            }
            fixture.repository.onSaveSuccess = { events += "saved" }

            fixture.vm.applyOperation(operation)
            fixture.advance()

            expect(failures, row, fixture.repository.saveAttempts.size == 1, "expected exactly one save, got ${fixture.repository.saveAttempts.size}")
            expect(failures, row, fixture.repository.current.value == expected, "repository did not persist the expected world")
            val editing = fixture.editing(row, failures)
            if (editing != null) expect(failures, row, editing.session.world == expected, "published session differs from saved world")
            val saveIndex = events.indexOf("saved")
            val publishIndex = events.indexOf("published")
            expect(failures, row, saveIndex >= 0 && publishIndex > saveIndex, "candidate state must publish after repository save; events=$events")
        }
    }

    private suspend fun rejectedAndNoOpPreserveWorldAndHistory(failures: MutableList<String>) {
        fixture().use { fixture ->
            fixture.vm.enterBuildMode()
            fixture.advance()
            fixture.vm.applyOperation(BuildOperation.Place(item("first", 1, 1)))
            fixture.advance()
            val afterFirst = fixture.repository.current.value
            val attemptsAfterFirst = fixture.repository.saveAttempts.size
            fixture.vm.applyOperation(BuildOperation.Place(item("collision", 1, 1)))
            fixture.vm.applyOperation(BuildOperation.Move("first", TileCoordinate(1, 1)))
            fixture.advance()
            expect(failures, "rejected and no-op edits preserve world and history", fixture.repository.current.value == afterFirst, "rejected/no-op commands changed persisted world")
            expect(failures, "rejected and no-op edits preserve world and history", fixture.repository.saveAttempts.size == attemptsAfterFirst, "rejected/no-op commands must not save")
            val editing = fixture.editing("rejected and no-op edits preserve world and history", failures)
            if (editing != null) {
                expect(failures, "rejected and no-op edits preserve world and history", editing.session.world == afterFirst, "rejected/no-op commands changed session world")
                expect(failures, "rejected and no-op edits preserve world and history", editing.session.canUndo, "successful edit history was lost")
            }
        }
    }

    private suspend fun failedSingleEditPreservesSessionAndRetries(failures: MutableList<String>) {
        fixture().use { fixture ->
            fixture.vm.enterBuildMode()
            fixture.advance()
            fixture.repository.failNextSaves = 1
            fixture.vm.applyOperation(BuildOperation.Place(item("retry-edit", 1, 1)))
            fixture.advance()
            val failed = fixture.editing("single edit save failure preserves session and retries", failures)
            expect(failures, "single edit save failure preserves session and retries", fixture.repository.current.value.items.isEmpty(), "failed save changed persisted world")
            if (failed != null) {
                expect(failures, "single edit save failure preserves session and retries", failed.saveStatus is WorldBuildSaveStatus.Failed, "failed save did not offer retry")
                expect(failures, "single edit save failure preserves session and retries", failed.session.world.items.isEmpty(), "failed candidate was published")
            }
            fixture.vm.retryBuildSave()
            fixture.advance()
            expect(failures, "single edit save failure preserves session and retries", fixture.repository.saveAttempts.size == 2, "retry should make one second save attempt")
            expect(failures, "single edit save failure preserves session and retries", fixture.repository.current.value.items.map { it.id } == listOf("retry-edit"), "retry did not persist candidate")
        }
    }

    private suspend fun failedUndoPreservesSessionAndRetries(failures: MutableList<String>) {
        val original = emptyBuildWorld(listOf(item("undo-retry", 0, 0)))
        fixture(original).use { fixture ->
            fixture.vm.enterBuildMode()
            fixture.advance()
            fixture.vm.applyOperation(BuildOperation.Move("undo-retry", TileCoordinate(1, 0)))
            fixture.advance()
            fixture.repository.failNextSaves = 1
            fixture.vm.undoBuild()
            fixture.advance()
            val failed = fixture.editing("undo save failure preserves session and retries", failures)
            expect(failures, "undo save failure preserves session and retries", fixture.repository.current.value.items.singleOrNull()?.anchor == TileCoordinate(1, 0), "failed undo changed persisted world")
            if (failed != null) {
                expect(failures, "undo save failure preserves session and retries", failed.saveStatus is WorldBuildSaveStatus.Failed, "failed undo did not offer retry")
                expect(failures, "undo save failure preserves session and retries", failed.session.world.items.singleOrNull()?.anchor == TileCoordinate(1, 0), "failed undo changed session")
            }
            fixture.vm.retryBuildSave()
            fixture.advance()
            expect(failures, "undo save failure preserves session and retries", fixture.repository.current.value.items.singleOrNull()?.anchor == TileCoordinate(0, 0), "undo retry did not persist prior world")
        }
    }

    private suspend fun failedRedoPreservesSessionAndRetries(failures: MutableList<String>) {
        val original = emptyBuildWorld(listOf(item("redo-retry", 0, 0)))
        fixture(original).use { fixture ->
            fixture.vm.enterBuildMode()
            fixture.advance()
            fixture.vm.applyOperation(BuildOperation.Move("redo-retry", TileCoordinate(1, 0)))
            fixture.advance()
            fixture.vm.undoBuild()
            fixture.advance()
            fixture.repository.failNextSaves = 1
            fixture.vm.redoBuild()
            fixture.advance()
            val failed = fixture.editing("redo save failure preserves session and retries", failures)
            expect(failures, "redo save failure preserves session and retries", fixture.repository.current.value.items.singleOrNull()?.anchor == TileCoordinate(0, 0), "failed redo changed persisted world")
            if (failed != null) {
                expect(failures, "redo save failure preserves session and retries", failed.saveStatus is WorldBuildSaveStatus.Failed, "failed redo did not offer retry")
                expect(failures, "redo save failure preserves session and retries", failed.session.world.items.singleOrNull()?.anchor == TileCoordinate(0, 0), "failed redo changed session")
            }
            fixture.vm.retryBuildSave()
            fixture.advance()
            expect(failures, "redo save failure preserves session and retries", fixture.repository.current.value.items.singleOrNull()?.anchor == TileCoordinate(1, 0), "redo retry did not persist next world")
        }
    }

    private suspend fun failedPlaceManyRetainsQueueAndRetryCommitsOnce(failures: MutableList<String>) {
        fixture().use { fixture ->
            fixture.vm.enterBuildMode()
            fixture.advance()
            val queued = listOf(item("batch-a", 0, 0), item("batch-b", 2, 0))
            queued.forEach(fixture.vm::queuePlacement)
            fixture.repository.failNextSaves = 1
            fixture.vm.applyQueuedPlacements()
            fixture.advance()
            val failed = fixture.editing("failedPlaceManySaveRetainsQueueAndRetryCommitsOnce", failures)
            expect(failures, "failedPlaceManySaveRetainsQueueAndRetryCommitsOnce", fixture.repository.current.value.items.isEmpty(), "failed batch partially persisted")
            if (failed != null) {
                expect(failures, "failedPlaceManySaveRetainsQueueAndRetryCommitsOnce", failed.queuedPlacements == queued, "failed batch queue was not retained")
                expect(failures, "failedPlaceManySaveRetainsQueueAndRetryCommitsOnce", failed.saveStatus is WorldBuildSaveStatus.Failed, "failed batch did not offer retry")
            }
            fixture.vm.retryBuildSave()
            fixture.advance()
            expect(failures, "failedPlaceManySaveRetainsQueueAndRetryCommitsOnce", fixture.repository.saveAttempts.size == 2, "retry should attempt the batch exactly once more")
            expect(failures, "failedPlaceManySaveRetainsQueueAndRetryCommitsOnce", fixture.repository.successfulSaves.size == 1, "retry should commit exactly once")
            expect(failures, "failedPlaceManySaveRetainsQueueAndRetryCommitsOnce", fixture.repository.current.value.items.toSet() == queued.toSet(), "retry did not persist the full batch")
        }
    }

    private suspend fun placeManyIsOneUndoStep(failures: MutableList<String>) {
        fixture().use { fixture ->
            fixture.vm.enterBuildMode()
            fixture.advance()
            val batch = listOf(item("undo-batch-a", 0, 0), item("undo-batch-b", 2, 0))
            batch.forEach(fixture.vm::queuePlacement)
            fixture.vm.applyQueuedPlacements()
            fixture.advance()
            expect(failures, "place-many is one undo step", fixture.repository.current.value.items.toSet() == batch.toSet(), "batch was not persisted")
            fixture.vm.undoBuild()
            fixture.advance()
            expect(failures, "place-many is one undo step", fixture.repository.current.value.items.isEmpty(), "one undo did not remove the entire batch")
            val undone = fixture.editing("place-many is one undo step", failures)
            if (undone != null) expect(failures, "place-many is one undo step", !undone.session.canUndo && undone.session.canRedo, "batch did not occupy exactly one history step")
            fixture.vm.redoBuild()
            fixture.advance()
            expect(failures, "place-many is one undo step", fixture.repository.current.value.items.toSet() == batch.toSet(), "one redo did not restore the entire batch")
        }
    }

    private suspend fun undoRedoPersist(failures: MutableList<String>) {
        val initial = emptyBuildWorld(listOf(item("history", 0, 0)))
        fixture(initial).use { fixture ->
            fixture.vm.enterBuildMode()
            fixture.advance()
            fixture.vm.applyOperation(BuildOperation.Move("history", TileCoordinate(1, 0)))
            fixture.advance()
            expect(failures, "undo and redo persist", fixture.repository.current.value.items.singleOrNull()?.anchor == TileCoordinate(1, 0), "edit was not persisted")
            fixture.vm.undoBuild()
            fixture.advance()
            expect(failures, "undo and redo persist", fixture.repository.current.value.items.singleOrNull()?.anchor == TileCoordinate(0, 0), "undo was not persisted")
            fixture.vm.redoBuild()
            fixture.advance()
            expect(failures, "undo and redo persist", fixture.repository.current.value.items.singleOrNull()?.anchor == TileCoordinate(1, 0), "redo was not persisted")
        }
    }

    private suspend fun exitClearsTransientStateAndKeepsSavedItems(failures: MutableList<String>) {
        fixture(emptyBuildWorld(listOf(item("persisted-before-exit", 0, 0)))).use { fixture ->
            fixture.vm.enterBuildMode()
            fixture.advance()
            fixture.vm.selectDefinition(BuiltInPlaceableCatalog.SMALL_STONE_ID)
            fixture.vm.selectPlacedItem("persisted-before-exit")
            fixture.vm.queuePlacement(item("queued-before-exit", 2, 0))
            fixture.vm.applyOperation(BuildOperation.Place(item("saved-before-exit", 1, 0)))
            fixture.advance()
            fixture.vm.exitBuildMode()
            fixture.advance()
            expect(failures, "exit clears transient state and keeps saved items", fixture.vm.buildModeState.value == WorldBuildUiState.Inactive, "exit did not clear Build-mode selection, queue and history")
            expect(
                failures,
                "exit clears transient state and keeps saved items",
                fixture.repository.current.value.items.map { it.id }.toSet() == setOf("persisted-before-exit", "saved-before-exit"),
                "exit discarded saved items or persisted the queued placement"
            )
        }
    }

    private suspend fun reentryLoadsSavedItems(failures: MutableList<String>) {
        val saved = emptyBuildWorld(listOf(item("saved-reentry", 3, 2)))
        fixture(saved).use { fixture ->
            fixture.vm.enterBuildMode()
            fixture.advance()
            fixture.vm.applyOperation(BuildOperation.Place(item("history-reentry", 1, 1)))
            fixture.advance()
            fixture.vm.exitBuildMode()
            fixture.advance()
            fixture.vm.enterBuildMode()
            fixture.advance()
            val editing = fixture.editing("re-entry loads saved items with empty history", failures) ?: return
            expect(failures, "re-entry loads saved items with empty history", editing.session.world.items.map { it.id }.toSet() == setOf("saved-reentry", "history-reentry"), "re-entry did not load persisted items")
            expect(failures, "re-entry loads saved items with empty history", !editing.session.canUndo && !editing.session.canRedo, "history was restored across Build-mode sessions")
        }
    }

    private suspend fun rapidDependentTapsAreSerialized(failures: MutableList<String>) {
        fixture().use { fixture ->
            fixture.vm.enterBuildMode()
            fixture.advance()
            val first = item("first-tap", 1, 1)
            val second = item("second-tap", 1, 1)
            fixture.repository.holdFirstSave = true
            fixture.vm.applyOperation(BuildOperation.Place(first))
            testScope.runCurrent()
            val firstEnteredSave = fixture.repository.firstSaveStarted.isCompleted
            expect(failures, "two rapid dependent taps are serialized", firstEnteredSave, "first tap did not enter the suspended repository save")
            fixture.vm.applyOperation(BuildOperation.Place(second))
            testScope.runCurrent()
            fixture.repository.releaseFirstSave.complete(Unit)
            testScope.advanceUntilIdle()
            expect(failures, "two rapid dependent taps are serialized", fixture.repository.current.value.items == listOf(first), "second tap did not observe the first placement's occupied cell")
            expect(failures, "two rapid dependent taps are serialized", fixture.repository.successfulSaves.size == 1, "dependent second tap should be rejected without a second save")
        }
    }

    private suspend fun processDeathStartsReadOnly(failures: MutableList<String>) {
        val persisted = emptyBuildWorld(listOf(item("after-process-death", 4, 4)))
        val repository = FakeWorldRepository(persisted)
        val first = fixture(repository)
        first.vm.enterBuildMode()
        first.advance()
        first.close()

        fixture(repository).use { reopened ->
            reopened.advance()
            expect(failures, "process death reopens the route read-only", reopened.vm.buildModeState.value == WorldBuildUiState.Inactive, "fresh ViewModel restored transient Build mode")
            expect(failures, "process death reopens the route read-only", reopened.vm.uiState.value == WorldUiState.Content(persisted.island), "fresh route did not reopen saved World content")
            expect(failures, "process death reopens the route read-only", repository.current.value.items == persisted.items, "process restart changed saved items")
        }
    }

    private fun fixture(world: BuildWorld = emptyBuildWorld()) = fixture(FakeWorldRepository(world))

    private fun fixture(repository: FakeWorldRepository): Fixture {
        val store = ViewModelStore()
        val viewModel = WorldViewModel(repository)
        store.put("world-build-test", viewModel)
        return Fixture(repository, viewModel, store, testScope)
    }

    private fun expect(failures: MutableList<String>, row: String, condition: Boolean, message: String) {
        if (!condition) failures += "$row: $message"
    }

    private fun Fixture.editing(row: String, failures: MutableList<String>): WorldBuildUiState.Editing? {
        val state = vm.buildModeState.value
        if (state is WorldBuildUiState.Editing) return state
        failures += "$row: expected Editing state, got $state"
        return null
    }

    private fun item(
        id: String,
        x: Int,
        y: Int,
        definitionId: String = BuiltInPlaceableCatalog.SMALL_STONE_ID,
        rotation: Int = 0,
        flipped: Boolean = false
    ) = PlacedWorldItem(
        id = id,
        definitionId = definitionId,
        anchor = TileCoordinate(x, y),
        rotationQuarterTurns = rotation,
        flipped = flipped
    )

    private fun emptyBuildWorld(items: List<PlacedWorldItem> = emptyList()) = BuildWorld(
        island = createStarterIsland(),
        items = items
    )

    private inner class Fixture(
        val repository: FakeWorldRepository,
        val vm: WorldViewModel,
        private val store: ViewModelStore,
        private val scope: TestScope
    ) : AutoCloseable {
        private val stateCollectors = mutableListOf<Job>()

        suspend fun advance() = scope.advanceUntilIdle()

        fun observeBuildState(onState: (WorldBuildUiState) -> Unit) {
            stateCollectors += scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
                vm.buildModeState.collect(onState)
            }
        }

        override fun close() {
            stateCollectors.forEach(Job::cancel)
            store.clear()
        }
    }

    private class FakeWorldRepository(initialWorld: BuildWorld) : WorldRepository {
        val current = MutableStateFlow(initialWorld)
        val saveAttempts = mutableListOf<BuildWorld>()
        val successfulSaves = mutableListOf<BuildWorld>()
        var failNextSaves = 0
        var holdFirstSave = false
        val firstSaveStarted = CompletableDeferred<Unit>()
        val releaseFirstSave = CompletableDeferred<Unit>()
        var onSaveSuccess: (() -> Unit)? = null

        override fun observeStarterIsland(): Flow<WorldIsland?> = current.map { it.island }

        override fun observeStarterBuildWorld(): Flow<BuildWorld?> = current

        override suspend fun ensureStarterIsland() = Unit

        override suspend fun saveBuildWorld(world: BuildWorld) {
            saveAttempts += world
            if (holdFirstSave && saveAttempts.size == 1) {
                firstSaveStarted.complete(Unit)
                releaseFirstSave.await()
            }
            if (failNextSaves > 0) {
                failNextSaves -= 1
                throw IOException("scripted world save failure")
            }
            current.value = world
            successfulSaves += world
            onSaveSuccess?.invoke()
        }
    }
}
