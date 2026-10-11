package com.habitat.app.domain

import com.habitat.app.domain.model.BuildEditFailure
import com.habitat.app.domain.model.BuildEditNoOp
import com.habitat.app.domain.model.BuildModeHistoryResult
import com.habitat.app.domain.model.BuildModeSession
import com.habitat.app.domain.model.BuildModeSessionResult
import com.habitat.app.domain.model.BuildOperation
import com.habitat.app.domain.model.BuildWorld
import com.habitat.app.domain.model.FootprintOffset
import com.habitat.app.domain.model.PlaceableDefinition
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldIsland
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildModeSessionTest {
    private val one = PlaceableDefinition("one", setOf(FootprintOffset(0, 0)))
    private val bar = PlaceableDefinition("bar", setOf(FootprintOffset(0, 0), FootprintOffset(1, 0)))
    private val definitions = mapOf(one.id to one, bar.id to bar)

    @Test
    fun placeManyIsOneUndoAndRedoEntry() = rows(
        "batch applies once" to {
            val initial = BuildModeSession.start(world(width = 5))
            val applied = initial.apply(BuildOperation.PlaceMany(listOf(item("a", 0, 0), item("b", 2, 0))), definitions)
            assertTrue("batch applies once: applied", applied is BuildModeSessionResult.Applied)
            val edited = applied.session
            assertEquals("batch applies once: both saved in session world", setOf("a", "b"), edited.world.items.map { it.id }.toSet())
            assertTrue("batch applies once: undo available", edited.canUndo)
            val undone = edited.undo()
            assertTrue("batch applies once: undo changed", undone is BuildModeHistoryResult.Changed)
            assertTrue("batch applies once: single undo removed whole batch", undone.session.world.items.isEmpty())
            assertFalse("batch applies once: no earlier history", undone.session.canUndo)
            val redone = undone.session.redo()
            assertTrue("batch applies once: redo changed", redone is BuildModeHistoryResult.Changed)
            assertEquals("batch applies once: redo restored both", setOf("a", "b"), redone.session.world.items.map { it.id }.toSet())
        },
        "empty batch makes no history entry" to {
            val initial = BuildModeSession.start(world())
            val result = initial.apply(BuildOperation.PlaceMany(emptyList()), definitions)
            assertTrue("empty batch makes no history entry: no-op", result is BuildModeSessionResult.NoOp)
            assertFalse("empty batch makes no history entry: cannot undo", result.session.canUndo)
        }
    )

    @Test
    fun undoRedoFollowEditOrderAndNewEditClearsRedo() = rows(
        "edits undo and redo in reverse and forward order" to {
            var session = BuildModeSession.start(world(width = 6))
            session = applied("edits undo and redo in reverse and forward order: place", session.apply(BuildOperation.Place(item("a", 0, 0)), definitions))
            session = applied("edits undo and redo in reverse and forward order: move", session.apply(BuildOperation.Move("a", TileCoordinate(1, 0)), definitions))
            session = applied("edits undo and redo in reverse and forward order: second place", session.apply(BuildOperation.Place(item("b", 4, 0)), definitions))
            session = changed("edits undo and redo in reverse and forward order: undo second place", session.undo())
            assertEquals("edits undo and redo in reverse and forward order: first undo", setOf("a"), session.world.items.map { it.id }.toSet())
            assertEquals("edits undo and redo in reverse and forward order: moved anchor", TileCoordinate(1, 0), session.world.items.single().anchor)
            session = changed("edits undo and redo in reverse and forward order: undo move", session.undo())
            assertEquals("edits undo and redo in reverse and forward order: second undo anchor", TileCoordinate(0, 0), session.world.items.single().anchor)
            session = changed("edits undo and redo in reverse and forward order: redo move", session.redo())
            assertEquals("edits undo and redo in reverse and forward order: redo anchor", TileCoordinate(1, 0), session.world.items.single().anchor)
            session = changed("edits undo and redo in reverse and forward order: redo place", session.redo())
            assertEquals("edits undo and redo in reverse and forward order: redo item", setOf("a", "b"), session.world.items.map { it.id }.toSet())
            assertFalse("edits undo and redo in reverse and forward order: redo exhausted", session.canRedo)

            session = changed("new edit after undo clears redo: undo", session.undo())
            assertTrue("new edit after undo clears redo: redo initially available", session.canRedo)
            session = applied("new edit after undo clears redo: new edit applies", session.apply(BuildOperation.FlipHorizontal("a"), definitions))
            assertFalse("new edit after undo clears redo: redo cleared", session.canRedo)
        }
    )

    @Test
    fun rejectedAndNoOpEditsDoNotChangeHistory() = rows(
        "invalid edit preserves undo history" to {
            var session = BuildModeSession.start(world(width = 4))
            session = applied("invalid edit preserves undo history: place", session.apply(BuildOperation.Place(item("a", 0, 0)), definitions))
            val before = session
            val rejected = session.apply(BuildOperation.Place(item("b", 0, 0)), definitions)
            assertTrue("invalid edit preserves undo history: rejected", rejected is BuildModeSessionResult.Rejected)
            assertEquals("invalid edit preserves undo history: reason", BuildEditFailure.OCCUPIED, (rejected as BuildModeSessionResult.Rejected).reason)
            assertEquals("invalid edit preserves undo history: session unchanged", before, rejected.session)
            assertTrue("invalid edit preserves undo history: undo remains", rejected.session.canUndo)
        },
        "no-op preserves undo history" to {
            var session = BuildModeSession.start(world(width = 4))
            session = applied("no-op preserves undo history: place", session.apply(BuildOperation.Place(item("a", 0, 0)), definitions))
            val noOp = session.apply(BuildOperation.Move("a", TileCoordinate(0, 0)), definitions)
            assertTrue("no-op preserves undo history: no-op type", noOp is BuildModeSessionResult.NoOp)
            assertEquals("no-op preserves undo history: reason", BuildEditNoOp.SAME_ANCHOR, (noOp as BuildModeSessionResult.NoOp).reason)
            assertEquals("no-op preserves undo history: same session", session, noOp.session)
            assertTrue("no-op preserves undo history: undo remains", noOp.session.canUndo)
        },
        "no-op after undo preserves redo" to {
            var session = BuildModeSession.start(world(width = 4))
            session = applied("no-op after undo preserves redo: place", session.apply(BuildOperation.Place(item("a", 0, 0)), definitions))
            session = changed("no-op after undo preserves redo: undo", session.undo())
            val noOp = session.apply(BuildOperation.Erase(TileCoordinate(3, 3)), definitions)
            assertTrue("no-op after undo preserves redo: no-op type", noOp is BuildModeSessionResult.NoOp)
            assertTrue("no-op after undo preserves redo: redo retained", noOp.session.canRedo)
            assertTrue("no-op after undo preserves redo: redo works", noOp.session.redo() is BuildModeHistoryResult.Changed)
        },
        "rejected edit after undo preserves redo" to {
            var session = BuildModeSession.start(world(width = 4))
            session = applied("rejected edit after undo preserves redo: place", session.apply(BuildOperation.Place(item("a", 0, 0)), definitions))
            session = changed("rejected edit after undo preserves redo: undo", session.undo())
            val rejected = session.apply(BuildOperation.Place(item("outside", 4, 0)), definitions)
            assertTrue("rejected edit after undo preserves redo: rejected type", rejected is BuildModeSessionResult.Rejected)
            assertEquals("rejected edit after undo preserves redo: reason", BuildEditFailure.OUT_OF_BOUNDS,
                (rejected as BuildModeSessionResult.Rejected).reason)
            assertTrue("rejected edit after undo preserves redo: redo retained", rejected.session.canRedo)
        }
    )

    @Test
    fun historyRetainsOnlyLatestOneHundredSuccessfulEdits() {
        val row = "latest 100 successful edits are retained"
        val wideIsland = world(width = GameConfig.BUILD_MODE_HISTORY_LIMIT + 2)
        var session = BuildModeSession.start(wideIsland)
        repeat(GameConfig.BUILD_MODE_HISTORY_LIMIT + 1) { index ->
            session = applied("$row: edit $index", session.apply(BuildOperation.Place(item("p$index", index, 0)), definitions))
        }
        repeat(GameConfig.BUILD_MODE_HISTORY_LIMIT) { index ->
            session = changed("$row: undo ${index + 1}", session.undo())
        }
        assertEquals("$row: oldest retained world", listOf("p0"), session.world.items.map { it.id })
        assertFalse("$row: history cap reached", session.canUndo)
        assertTrue("$row: undone edits can be redone", session.canRedo)
    }

    @Test
    fun emptyHistoryAndNewSessionHaveNoUndoOrRedo() = rows(
        "empty undo is no-op" to {
            val initial = BuildModeSession.start(world())
            val result = initial.undo()
            assertTrue("empty undo is no-op: result", result is BuildModeHistoryResult.NoOp)
            assertEquals("empty undo is no-op: unchanged session", initial, result.session)
        },
        "empty redo is no-op" to {
            val initial = BuildModeSession.start(world())
            val result = initial.redo()
            assertTrue("empty redo is no-op: result", result is BuildModeHistoryResult.NoOp)
            assertEquals("empty redo is no-op: unchanged session", initial, result.session)
        },
        "new session keeps saved world and clears history" to {
            var editing = BuildModeSession.start(world())
            editing = applied("new session keeps saved world and clears history: place", editing.apply(BuildOperation.Place(item("a", 0, 0)), definitions))
            val saved = editing.finish()
            val reopened = BuildModeSession.start(saved)
            assertEquals("new session keeps saved world and clears history: data persists", saved, reopened.world)
            assertFalse("new session keeps saved world and clears history: undo cleared", reopened.canUndo)
            assertFalse("new session keeps saved world and clears history: redo cleared", reopened.canRedo)
        }
    )

    private fun applied(row: String, result: BuildModeSessionResult): BuildModeSession {
        assertTrue("$row: expected applied, got $result", result is BuildModeSessionResult.Applied)
        return (result as BuildModeSessionResult.Applied).session
    }

    private fun changed(row: String, result: BuildModeHistoryResult): BuildModeSession {
        assertTrue("$row: expected history change, got $result", result is BuildModeHistoryResult.Changed)
        return (result as BuildModeHistoryResult.Changed).session
    }

    private fun item(id: String, x: Int, y: Int) =
        PlacedWorldItem(id, "one", TileCoordinate(x, y))

    private fun world(width: Int = 5) = BuildWorld(WorldIsland("session-test", width, 4, emptyList()))

    private fun rows(vararg rows: Pair<String, () -> Unit>) {
        val failures = mutableListOf<String>()
        rows.forEach { (name, check) ->
            try {
                check()
            } catch (failure: AssertionError) {
                failures += "$name: ${failure.message}"
            } catch (failure: Throwable) {
                failures += "$name: unexpected ${failure::class.simpleName}: ${failure.message}"
            }
        }
        assertTrue("BuildModeSession rows failed:\n${failures.joinToString("\n")}", failures.isEmpty())
    }
}
