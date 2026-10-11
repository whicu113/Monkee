package com.habitat.app.domain

import com.habitat.app.domain.model.BuildEditFailure
import com.habitat.app.domain.model.BuildEditNoOp
import com.habitat.app.domain.model.BuildEditResult
import com.habitat.app.domain.model.BuildOperation
import com.habitat.app.domain.model.BuildWorld
import com.habitat.app.domain.model.FootprintOffset
import com.habitat.app.domain.model.PlaceableDefinition
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldIsland
import com.habitat.app.domain.model.WorldTile
import com.habitat.app.domain.model.WorldTerrain
import com.habitat.app.domain.model.BuildModeRules
import com.habitat.app.domain.model.transformedFootprintOffsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildModeRulesTest {
    private val island = island()
    private val oneCell = PlaceableDefinition("one", setOf(FootprintOffset(0, 0)))
    private val bar = PlaceableDefinition(
        "bar",
        setOf(FootprintOffset(0, 0), FootprintOffset(1, 0))
    )
    private val tallBar = PlaceableDefinition(
        "tall-bar",
        setOf(FootprintOffset(0, 0), FootprintOffset(1, 0), FootprintOffset(2, 0))
    )
    private val lShape = PlaceableDefinition(
        "l-shape",
        setOf(FootprintOffset(0, 0), FootprintOffset(1, 0), FootprintOffset(0, 1))
    )
    private val asymmetric = PlaceableDefinition(
        "asymmetric",
        setOf(FootprintOffset(0, 0), FootprintOffset(1, 1))
    )
    private val definitions = listOf(oneCell, bar, tallBar, lShape, asymmetric)
        .associateBy { it.id }

    @Test
    fun placementTableChecksBoundsOccupancyDefinitionsAndTerrain() = rows(
        "single cell inside" to {
            val result = edit(BuildWorld(island), BuildOperation.Place(item("a", "one", 1, 1)))
            assertApplied("single cell inside", result)
            assertEquals("single cell inside: item count", 1, result.world.items.size)
        },
        "last island cell is in bounds" to {
            val result = edit(BuildWorld(island), BuildOperation.Place(item("a", "one", 4, 3)))
            assertApplied("last island cell is in bounds", result)
        },
        "negative x is rejected" to {
            assertRejected("negative x is rejected", BuildEditFailure.OUT_OF_BOUNDS,
                edit(BuildWorld(island), BuildOperation.Place(item("a", "one", -1, 0))))
        },
        "negative y is rejected" to {
            assertRejected("negative y is rejected", BuildEditFailure.OUT_OF_BOUNDS,
                edit(BuildWorld(island), BuildOperation.Place(item("a", "one", 0, -1))))
        },
        "x equal to island width is rejected" to {
            assertRejected("x equal to island width is rejected", BuildEditFailure.OUT_OF_BOUNDS,
                edit(BuildWorld(island), BuildOperation.Place(item("a", "one", 5, 0))))
        },
        "y equal to island height is rejected" to {
            assertRejected("y equal to island height is rejected", BuildEditFailure.OUT_OF_BOUNDS,
                edit(BuildWorld(island), BuildOperation.Place(item("a", "one", 0, 4))))
        },
        "wide item crossing right edge is rejected" to {
            assertRejected("wide item crossing right edge is rejected", BuildEditFailure.OUT_OF_BOUNDS,
                edit(BuildWorld(island), BuildOperation.Place(item("a", "bar", 4, 0))))
        },
        "wide item crossing bottom edge is rejected" to {
            val vertical = barItem("a", 0, 3, rotation = 1)
            assertRejected("wide item crossing bottom edge is rejected", BuildEditFailure.OUT_OF_BOUNDS,
                edit(BuildWorld(island), BuildOperation.Place(vertical)))
        },
        "occupied tile rejects placement" to {
            val world = BuildWorld(island, listOf(item("existing", "one", 2, 2)))
            assertRejected("occupied tile rejects placement", BuildEditFailure.OCCUPIED,
                edit(world, BuildOperation.Place(item("new", "one", 2, 2))))
        },
        "duplicate id rejects placement" to {
            val world = BuildWorld(island, listOf(item("same", "one", 0, 0)))
            assertRejected("duplicate id rejects placement", BuildEditFailure.DUPLICATE_ITEM_ID,
                edit(world, BuildOperation.Place(item("same", "one", 1, 0))))
        },
        "blank item id is rejected" to {
            assertRejected("blank item id is rejected", BuildEditFailure.INVALID_ITEM_ID,
                edit(BuildWorld(island), BuildOperation.Place(item("", "one", 0, 0))))
        },
        "unknown definition is rejected" to {
            assertRejected("unknown definition is rejected", BuildEditFailure.UNKNOWN_DEFINITION,
                BuildModeRules.apply(BuildWorld(island), BuildOperation.Place(item("a", "missing", 0, 0)), definitions))
        },
        "definition id mismatch is rejected" to {
            val wrongKey = definitions + ("one" to PlaceableDefinition("different", oneCell.footprint))
            assertRejected("definition id mismatch is rejected", BuildEditFailure.INVALID_DEFINITION,
                BuildModeRules.apply(BuildWorld(island), BuildOperation.Place(item("a", "one", 0, 0)), wrongKey))
        },
        "empty footprint is invalid" to {
            val invalid = PlaceableDefinition("empty", emptySet())
            assertRejected("empty footprint is invalid", BuildEditFailure.INVALID_DEFINITION,
                BuildModeRules.apply(BuildWorld(island), BuildOperation.Place(item("a", "empty", 0, 0)), definitions + ("empty" to invalid)))
        },
        "negative footprint offset is invalid" to {
            val invalid = PlaceableDefinition("negative", setOf(FootprintOffset(-1, 0)))
            assertRejected("negative footprint offset is invalid", BuildEditFailure.INVALID_DEFINITION,
                BuildModeRules.apply(BuildWorld(island), BuildOperation.Place(item("a", "negative", 0, 0)), definitions + ("negative" to invalid)))
        },
        "terrain does not restrict placement" to {
            val waterIsland = island(terrain = WorldTerrain.DEEP_WATER)
            assertApplied("terrain does not restrict placement",
                BuildModeRules.apply(BuildWorld(waterIsland), BuildOperation.Place(item("a", "one", 0, 0)), definitions))
        }
    )

    @Test
    fun moveTableChecksOccupiedBoundsOwnFootprintAndNoOps() = rows(
        "valid move" to {
            val result = edit(BuildWorld(island, listOf(item("a", "one", 0, 0))), BuildOperation.Move("a", TileCoordinate(2, 1)))
            assertApplied("valid move", result)
            assertEquals("valid move: new anchor", TileCoordinate(2, 1), result.world.items.single().anchor)
        },
        "move into another item is rejected" to {
            val world = BuildWorld(island, listOf(item("a", "one", 0, 0), item("b", "one", 2, 1)))
            val result = edit(world, BuildOperation.Move("a", TileCoordinate(2, 1)))
            assertRejected("move into another item is rejected", BuildEditFailure.OCCUPIED, result)
            assertEquals("move into another item is rejected: original world", world, result.world)
        },
        "move outside island is rejected" to {
            assertRejected("move outside island is rejected", BuildEditFailure.OUT_OF_BOUNDS,
                edit(BuildWorld(island, listOf(item("a", "one", 0, 0))), BuildOperation.Move("a", TileCoordinate(5, 0))))
        },
        "wide item move crossing edge is rejected" to {
            val world = BuildWorld(island, listOf(item("a", "bar", 0, 0)))
            assertRejected("wide item move crossing edge is rejected", BuildEditFailure.OUT_OF_BOUNDS,
                edit(world, BuildOperation.Move("a", TileCoordinate(4, 0))))
        },
        "move may overlap its own former footprint" to {
            val world = BuildWorld(island, listOf(item("a", "bar", 0, 0)))
            val result = edit(world, BuildOperation.Move("a", TileCoordinate(1, 0)))
            assertApplied("move may overlap its own former footprint", result)
            assertEquals("move may overlap its own former footprint: anchor", TileCoordinate(1, 0), result.world.items.single().anchor)
        },
        "same anchor is a no-op" to {
            val world = BuildWorld(island, listOf(item("a", "one", 1, 1)))
            val result = edit(world, BuildOperation.Move("a", TileCoordinate(1, 1)))
            assertTrue("same anchor is a no-op: result type", result is BuildEditResult.NoOp)
            assertEquals("same anchor is a no-op: reason", BuildEditNoOp.SAME_ANCHOR, (result as BuildEditResult.NoOp).reason)
        },
        "missing item cannot move" to {
            assertRejected("missing item cannot move", BuildEditFailure.UNKNOWN_ITEM,
                edit(BuildWorld(island), BuildOperation.Move("missing", TileCoordinate(1, 1))))
        }
    )

    @Test
    fun clockwiseRotationTableNormalizesOffsetsAndFourTurnsRestore() = rows(
        "one clockwise quarter turn" to { assertRotation("one clockwise quarter turn", 1, setOf(FootprintOffset(1, 0), FootprintOffset(1, 1), FootprintOffset(0, 0))) },
        "two clockwise quarter turns" to { assertRotation("two clockwise quarter turns", 2, setOf(FootprintOffset(1, 1), FootprintOffset(0, 1), FootprintOffset(1, 0))) },
        "three clockwise quarter turns" to { assertRotation("three clockwise quarter turns", 3, setOf(FootprintOffset(0, 1), FootprintOffset(0, 0), FootprintOffset(1, 1))) },
        "four clockwise quarter turns restore orientation" to { assertRotation("four clockwise quarter turns restore orientation", 4, lShape.footprint) }
    )

    @Test
    fun rotationTableChecksEdgesCollisionsAndMissingItems() = rows(
        "rotated footprint may reach last row" to {
            val world = BuildWorld(island, listOf(item("a", "tall-bar", 0, 1)))
            assertApplied("rotated footprint may reach last row", edit(world, BuildOperation.RotateClockwise("a")))
        },
        "rotation crossing bottom edge is rejected" to {
            val world = BuildWorld(island, listOf(item("a", "tall-bar", 0, 2)))
            val result = edit(world, BuildOperation.RotateClockwise("a"))
            assertRejected("rotation crossing bottom edge is rejected", BuildEditFailure.OUT_OF_BOUNDS, result)
            assertEquals("rotation crossing bottom edge is rejected: original item", 0, result.world.items.single().rotationQuarterTurns)
        },
        "rotation into occupied cell is rejected" to {
            val world = BuildWorld(island, listOf(item("a", "bar", 0, 0), item("blocker", "one", 0, 1)))
            assertRejected("rotation into occupied cell is rejected", BuildEditFailure.OCCUPIED,
                edit(world, BuildOperation.RotateClockwise("a")))
        },
        "missing item cannot rotate" to {
            assertRejected("missing item cannot rotate", BuildEditFailure.UNKNOWN_ITEM,
                edit(BuildWorld(island), BuildOperation.RotateClockwise("missing")))
        }
    )

    @Test
    fun horizontalFlipTableUsesMirrorAfterRotationAndChecksBoundaries() = rows(
        "flip mirrors asymmetric footprint horizontally" to {
            val item = item("a", "asymmetric", 0, 0)
            val result = edit(BuildWorld(island, listOf(item)), BuildOperation.FlipHorizontal("a"))
            assertApplied("flip mirrors asymmetric footprint horizontally", result)
            assertEquals("flip mirrors asymmetric footprint horizontally: occupied offsets",
                setOf(FootprintOffset(1, 0), FootprintOffset(0, 1)),
                transformedFootprintOffsets(result.world.items.single(), asymmetric))
            assertTrue("flip mirrors asymmetric footprint horizontally: state retained", result.world.items.single().flipped)
        },
        "double flip restores original occupied cells" to {
            val original = item("a", "asymmetric", 1, 1)
            val first = edit(BuildWorld(island, listOf(original)), BuildOperation.FlipHorizontal("a"))
            val second = edit(first.world, BuildOperation.FlipHorizontal("a"))
            assertApplied("double flip restores original occupied cells", second)
            assertEquals("double flip restores original occupied cells: offsets", asymmetric.footprint,
                transformedFootprintOffsets(second.world.items.single(), asymmetric))
            assertFalse("double flip restores original occupied cells: state", second.world.items.single().flipped)
        },
        "flip at right boundary stays in bounds" to {
            val edgeIsland = island(width = 3, height = 2)
            val world = BuildWorld(edgeIsland, listOf(item("a", "bar", 1, 0)))
            assertApplied("flip at right boundary stays in bounds",
                BuildModeRules.apply(world, BuildOperation.FlipHorizontal("a"), definitions))
        },
        "flip into occupied cell is rejected" to {
            val world = BuildWorld(island, listOf(item("a", "asymmetric", 0, 0), item("blocker", "one", 1, 0)))
            assertRejected("flip into occupied cell is rejected", BuildEditFailure.OCCUPIED,
                edit(world, BuildOperation.FlipHorizontal("a")))
        },
        "flip after rotation mirrors the rotated footprint" to {
            val item = item("a", "l-shape", 0, 0, rotation = 1, flipped = true)
            val expected = setOf(FootprintOffset(0, 0), FootprintOffset(0, 1), FootprintOffset(1, 0))
            assertEquals("flip after rotation mirrors the rotated footprint: exact normalized set", expected,
                transformedFootprintOffsets(item, lShape))
        },
        "missing item cannot flip" to {
            assertRejected("missing item cannot flip", BuildEditFailure.UNKNOWN_ITEM,
                edit(BuildWorld(island), BuildOperation.FlipHorizontal("missing")))
        }
    )

    @Test
    fun eraseTableRemovesWholeItemAndAllowsReuse() = rows(
        "erase at anchor removes whole item" to {
            val world = BuildWorld(island, listOf(item("a", "bar", 1, 1)))
            val result = edit(world, BuildOperation.Erase(TileCoordinate(1, 1)))
            assertApplied("erase at anchor removes whole item", result)
            assertTrue("erase at anchor removes whole item: empty", result.world.items.isEmpty())
        },
        "erase at non-anchor removes whole item" to {
            val world = BuildWorld(island, listOf(item("a", "bar", 1, 1)))
            val result = edit(world, BuildOperation.Erase(TileCoordinate(2, 1)))
            assertApplied("erase at non-anchor removes whole item", result)
            assertTrue("erase at non-anchor removes whole item: empty", result.world.items.isEmpty())
        },
        "erase empty tile is a no-op" to {
            val world = BuildWorld(island)
            val result = edit(world, BuildOperation.Erase(TileCoordinate(0, 0)))
            assertTrue("erase empty tile is a no-op: result type", result is BuildEditResult.NoOp)
            assertEquals("erase empty tile is a no-op: reason", BuildEditNoOp.EMPTY_TILE, (result as BuildEditResult.NoOp).reason)
        },
        "erase outside island is rejected" to {
            assertRejected("erase outside island is rejected", BuildEditFailure.OUT_OF_BOUNDS,
                edit(BuildWorld(island), BuildOperation.Erase(TileCoordinate(-1, 0))))
        },
        "erased id can be placed again" to {
            val world = BuildWorld(island, listOf(item("a", "bar", 0, 0)))
            val erased = edit(world, BuildOperation.Erase(TileCoordinate(1, 0)))
            val placed = edit(erased.world, BuildOperation.Place(item("a", "one", 3, 3)))
            assertApplied("erased id can be placed again", placed)
            assertEquals("erased id can be placed again: item", item("a", "one", 3, 3), placed.world.items.single())
        }
    )

    @Test
    fun placeManyTableIsAtomicAndValidatesAgainstEarlierRequests() = rows(
        "valid batch applies all items" to {
            val result = edit(BuildWorld(island), BuildOperation.PlaceMany(listOf(item("a", "one", 0, 0), item("b", "bar", 2, 2))))
            assertApplied("valid batch applies all items", result)
            assertEquals("valid batch applies all items: count", 2, result.world.items.size)
        },
        "later out of bounds request rolls back entire batch" to {
            val original = BuildWorld(island)
            val result = edit(original, BuildOperation.PlaceMany(listOf(item("a", "one", 0, 0), item("b", "bar", 4, 0))))
            assertRejected("later out of bounds request rolls back entire batch", BuildEditFailure.OUT_OF_BOUNDS, result)
            assertEquals("later out of bounds request rolls back entire batch: failing index", 1, (result as BuildEditResult.Rejected).itemIndex)
            assertEquals("later out of bounds request rolls back entire batch: unchanged", original, result.world)
        },
        "items in batch cannot collide with one another" to {
            val result = edit(BuildWorld(island), BuildOperation.PlaceMany(listOf(item("a", "one", 1, 1), item("b", "bar", 1, 1))))
            assertRejected("items in batch cannot collide with one another", BuildEditFailure.OCCUPIED, result)
            assertEquals("items in batch cannot collide with one another: failing index", 1, (result as BuildEditResult.Rejected).itemIndex)
            assertTrue("items in batch cannot collide with one another: rollback", result.world.items.isEmpty())
        },
        "duplicate ids within batch are rejected atomically" to {
            val result = edit(BuildWorld(island), BuildOperation.PlaceMany(listOf(item("same", "one", 0, 0), item("same", "one", 1, 0))))
            assertRejected("duplicate ids within batch are rejected atomically", BuildEditFailure.DUPLICATE_ITEM_ID, result)
            assertEquals("duplicate ids within batch are rejected atomically: failing index", 1, (result as BuildEditResult.Rejected).itemIndex)
            assertTrue("duplicate ids within batch are rejected atomically: rollback", result.world.items.isEmpty())
        },
        "empty batch is a no-op" to {
            val world = BuildWorld(island)
            val result = edit(world, BuildOperation.PlaceMany(emptyList()))
            assertTrue("empty batch is a no-op: result type", result is BuildEditResult.NoOp)
            assertEquals("empty batch is a no-op: reason", BuildEditNoOp.EMPTY_BATCH, (result as BuildEditResult.NoOp).reason)
            assertEquals("empty batch is a no-op: unchanged", world, result.world)
        }
    )

    @Test
    fun transformedFootprintsAreTheExactCellsValidatedForPlacementAndCollision() = rows(
        "unrotated unflipped" to { assertTransformAgreement("unrotated unflipped", 0, false, lShape.footprint) },
        "rotated unflipped" to { assertTransformAgreement("rotated unflipped", 1, false,
            setOf(FootprintOffset(1, 0), FootprintOffset(1, 1), FootprintOffset(0, 0))) },
        "unrotated flipped" to { assertTransformAgreement("unrotated flipped", 0, true,
            setOf(FootprintOffset(0, 0), FootprintOffset(1, 0), FootprintOffset(1, 1))) },
        "rotated then flipped" to { assertTransformAgreement("rotated then flipped", 1, true,
            setOf(FootprintOffset(0, 0), FootprintOffset(0, 1), FootprintOffset(1, 0))) },
        "three turns then flipped" to { assertTransformAgreement("three turns then flipped", 3, true,
            setOf(FootprintOffset(0, 1), FootprintOffset(1, 0), FootprintOffset(1, 1)) ) }
    )

    private fun assertRotation(row: String, turns: Int, expected: Set<FootprintOffset>) {
        var world = BuildWorld(island)
        val original = item("a", "l-shape", 1, 1)
        world = (edit(world, BuildOperation.Place(original)) as BuildEditResult.Applied).world
        repeat(turns) {
            val result = edit(world, BuildOperation.RotateClockwise("a"))
            assertApplied("$row: rotation ${it + 1}", result)
            world = result.world
        }
        val finalItem = world.items.single()
        assertEquals("$row: normalized quarter turns", turns % 4, finalItem.rotationQuarterTurns)
        val actualOffsets = transformedFootprintOffsets(finalItem, lShape)
        assertEquals("$row: exact occupied offsets", expected, actualOffsets)
        assertEquals("$row: x offsets normalized", 0, actualOffsets.minOf { it.x })
        assertEquals("$row: y offsets normalized", 0, actualOffsets.minOf { it.y })
    }

    private fun assertTransformAgreement(
        row: String,
        turns: Int,
        flipped: Boolean,
        expected: Set<FootprintOffset>
    ) {
        val item = item("candidate", "l-shape", 1, 1, rotation = turns, flipped = flipped)
        val local = transformedFootprintOffsets(item, lShape)
        assertTrue("$row: transformed footprint nonempty", local.isNotEmpty())
        assertEquals("$row: exact transform order", expected, local)
        assertEquals("$row: offsets normalized", 0, local.minOf { it.x })
        assertEquals("$row: y offsets normalized", 0, local.minOf { it.y })
        val result = edit(BuildWorld(island), BuildOperation.Place(item))
        assertApplied("$row: same transformed cells accepted by bounds validation", result)
        val absoluteCells = local.map { TileCoordinate(item.anchor.x + it.x, item.anchor.y + it.y) }.toSet()
        assertTrue("$row: translated occupied cells fit island", absoluteCells.all { it.x in 0 until island.width && it.y in 0 until island.height })
        assertEquals("$row: candidate retained", item, result.world.items.single())

        local.forEach { offset ->
            val blockedCell = TileCoordinate(item.anchor.x + offset.x, item.anchor.y + offset.y)
            val blocker = item("blocker", "one", blockedCell.x, blockedCell.y)
            val blockedWorld = BuildWorld(island, listOf(blocker))
            assertRejected("$row: transformed cell $offset collides", BuildEditFailure.OCCUPIED,
                edit(blockedWorld, BuildOperation.Place(item)))
        }
    }

    private fun edit(world: BuildWorld, operation: BuildOperation): BuildEditResult =
        BuildModeRules.apply(world, operation, definitions)

    private fun item(
        id: String,
        definitionId: String,
        x: Int,
        y: Int,
        rotation: Int = 0,
        flipped: Boolean = false
    ) = PlacedWorldItem(id, definitionId, TileCoordinate(x, y), rotation, flipped)

    private fun barItem(id: String, x: Int, y: Int, rotation: Int) = item(id, "bar", x, y, rotation)

    private fun island(width: Int = 5, height: Int = 4, terrain: WorldTerrain = WorldTerrain.GRASS) =
        WorldIsland("test-island", width, height, listOf(WorldTile(0, 0, terrain)))

    private fun assertApplied(row: String, result: BuildEditResult) {
        assertTrue("$row: expected applied, got $result", result is BuildEditResult.Applied)
    }

    private fun assertRejected(row: String, expected: BuildEditFailure, result: BuildEditResult) {
        assertTrue("$row: expected rejection $expected, got $result", result is BuildEditResult.Rejected)
        assertEquals("$row: rejection reason", expected, (result as BuildEditResult.Rejected).reason)
    }

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
        assertTrue("BuildModeRules rows failed:\n${failures.joinToString("\n")}", failures.isEmpty())
    }
}
