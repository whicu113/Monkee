package com.habitat.app.domain.model

import com.habitat.app.domain.GameConfig

/** A cell coordinate on a saved world island. */
data class TileCoordinate(val x: Int, val y: Int)

/** An occupied cell offset from a placeable's canonical top-left corner. */
data class FootprintOffset(val x: Int, val y: Int)

/** A placeable's canonical occupied cells, before rotation and reflection. */
data class PlaceableDefinition(
    val id: String,
    val footprint: Set<FootprintOffset>
)

/** A placed item; its anchor is the top-left cell after orientation is applied. */
data class PlacedWorldItem(
    val id: String,
    val definitionId: String,
    val anchor: TileCoordinate,
    val rotationQuarterTurns: Int = 0,
    val flipped: Boolean = false
)

/** A build-mode view of the saved island and its placed items. */
data class BuildWorld(
    val island: WorldIsland,
    val items: List<PlacedWorldItem> = emptyList()
)

sealed interface BuildOperation {
    data class Place(val item: PlacedWorldItem) : BuildOperation
    data class Move(val itemId: String, val newAnchor: TileCoordinate) : BuildOperation
    data class RotateClockwise(val itemId: String) : BuildOperation
    data class FlipHorizontal(val itemId: String) : BuildOperation
    data class Erase(val coordinate: TileCoordinate) : BuildOperation
    data class PlaceMany(val items: List<PlacedWorldItem>) : BuildOperation
}

enum class BuildEditFailure {
    UNKNOWN_ITEM,
    UNKNOWN_DEFINITION,
    INVALID_ITEM_ID,
    INVALID_DEFINITION,
    DUPLICATE_ITEM_ID,
    OUT_OF_BOUNDS,
    OCCUPIED
}

enum class BuildEditNoOp {
    SAME_ANCHOR,
    EMPTY_TILE,
    EMPTY_BATCH
}

sealed interface BuildEditResult {
    val world: BuildWorld

    data class Applied(override val world: BuildWorld) : BuildEditResult
    data class NoOp(override val world: BuildWorld, val reason: BuildEditNoOp) : BuildEditResult
    data class Rejected(
        override val world: BuildWorld,
        val reason: BuildEditFailure,
        val itemIndex: Int? = null
    ) : BuildEditResult
}

/**
 * Returns the canonical oriented local footprint, normalized to a top-left origin (SPEC 7.1).
 * Rotation is clockwise in grid coordinates; horizontal reflection is applied after rotation.
 */
fun transformedFootprintOffsets(
    item: PlacedWorldItem,
    definition: PlaceableDefinition
): Set<FootprintOffset> {
    if (!definition.isValidFor(item)) return emptySet()

    var cells = definition.footprint.map { LongOffset(it.x.toLong(), it.y.toLong()) }
    var width = cells.maxOf { it.x } + 1L
    var height = cells.maxOf { it.y } + 1L

    repeat(Math.floorMod(item.rotationQuarterTurns, 4)) {
        cells = cells.map { cell -> LongOffset(height - 1L - cell.y, cell.x) }
        val previousWidth = width
        width = height
        height = previousWidth
    }

    if (item.flipped) {
        cells = cells.map { cell -> LongOffset(width - 1L - cell.x, cell.y) }
    }

    val minX = cells.minOf { it.x }
    val minY = cells.minOf { it.y }
    return cells
        .map { FootprintOffset((it.x - minX).toInt(), (it.y - minY).toInt()) }
        .sortedWith(compareBy<FootprintOffset> { it.y }.thenBy { it.x })
        .toCollection(linkedSetOf())
}

/**
 * Applies the pure placement and editing rules for the build screen (SPEC 7.1).
 * Rejected operations and no-ops return the original world unchanged.
 */
object BuildModeRules {
    fun apply(
        world: BuildWorld,
        operation: BuildOperation,
        definitions: Map<String, PlaceableDefinition>
    ): BuildEditResult = when (operation) {
        is BuildOperation.Place -> place(world, operation.item, definitions)
        is BuildOperation.Move -> updateExisting(world, operation.itemId, definitions) { current ->
            current.copy(anchor = operation.newAnchor)
        }
        is BuildOperation.RotateClockwise -> updateExisting(world, operation.itemId, definitions) { current ->
            current.copy(rotationQuarterTurns = (Math.floorMod(current.rotationQuarterTurns, 4) + 1) % 4)
        }
        is BuildOperation.FlipHorizontal -> updateExisting(world, operation.itemId, definitions) { current ->
            current.copy(flipped = !current.flipped)
        }
        is BuildOperation.Erase -> erase(world, operation.coordinate, definitions)
        is BuildOperation.PlaceMany -> placeMany(world, operation.items, definitions)
    }

    private fun updateExisting(
        world: BuildWorld,
        itemId: String,
        definitions: Map<String, PlaceableDefinition>,
        transform: (PlacedWorldItem) -> PlacedWorldItem
    ): BuildEditResult {
        val index = world.items.indexOfFirst { it.id == itemId }
        if (index < 0) return BuildEditResult.Rejected(world, BuildEditFailure.UNKNOWN_ITEM)
        val updatedItem = transform(world.items[index])
        if (updatedItem == world.items[index]) return BuildEditResult.NoOp(world, BuildEditNoOp.SAME_ANCHOR)

        val validation = validateCandidate(world, updatedItem, definitions, ignoredItemIndex = index)
        if (validation != null) return BuildEditResult.Rejected(world, validation)
        return BuildEditResult.Applied(world.copy(items = world.items.toMutableList().also { it[index] = updatedItem }))
    }

    private fun place(
        world: BuildWorld,
        item: PlacedWorldItem,
        definitions: Map<String, PlaceableDefinition>
    ): BuildEditResult {
        val validation = validateCandidate(world, item, definitions)
        if (validation != null) return BuildEditResult.Rejected(world, validation)
        return BuildEditResult.Applied(world.copy(items = world.items + item))
    }

    private fun placeMany(
        original: BuildWorld,
        items: List<PlacedWorldItem>,
        definitions: Map<String, PlaceableDefinition>
    ): BuildEditResult {
        if (items.isEmpty()) return BuildEditResult.NoOp(original, BuildEditNoOp.EMPTY_BATCH)

        var candidate = original
        items.forEachIndexed { index, item ->
            when (val result = place(candidate, item, definitions)) {
                is BuildEditResult.Applied -> candidate = result.world
                is BuildEditResult.NoOp -> return BuildEditResult.Rejected(
                    original,
                    BuildEditFailure.INVALID_DEFINITION,
                    index
                )
                is BuildEditResult.Rejected -> return result.copy(world = original, itemIndex = index)
            }
        }
        return BuildEditResult.Applied(candidate)
    }

    private fun erase(
        world: BuildWorld,
        coordinate: TileCoordinate,
        definitions: Map<String, PlaceableDefinition>
    ): BuildEditResult {
        if (!coordinate.isWithin(world.island)) return BuildEditResult.Rejected(world, BuildEditFailure.OUT_OF_BOUNDS)

        for ((index, item) in world.items.withIndex()) {
            val definition = definitions[item.definitionId]
                ?: return BuildEditResult.Rejected(world, BuildEditFailure.UNKNOWN_DEFINITION)
            if (!definition.isValidFor(item)) return BuildEditResult.Rejected(world, BuildEditFailure.INVALID_DEFINITION)
            if (transformedFootprintOffsets(item, definition).any { offset ->
                    item.anchor.x.toLong() + offset.x == coordinate.x.toLong() &&
                        item.anchor.y.toLong() + offset.y == coordinate.y.toLong()
                }
            ) {
                return BuildEditResult.Applied(world.copy(items = world.items.filterIndexed { itemIndex, _ -> itemIndex != index }))
            }
        }
        return BuildEditResult.NoOp(world, BuildEditNoOp.EMPTY_TILE)
    }

    /** Validates a candidate against all other items using the canonical transformed footprint. */
    private fun validateCandidate(
        world: BuildWorld,
        item: PlacedWorldItem,
        definitions: Map<String, PlaceableDefinition>,
        ignoredItemIndex: Int? = null
    ): BuildEditFailure? {
        if (item.id.isBlank()) return BuildEditFailure.INVALID_ITEM_ID
        if (world.items.withIndex().any { (index, existing) -> index != ignoredItemIndex && existing.id == item.id }) {
            return BuildEditFailure.DUPLICATE_ITEM_ID
        }

        val definition = definitions[item.definitionId] ?: return BuildEditFailure.UNKNOWN_DEFINITION
        if (!definition.isValidFor(item)) return BuildEditFailure.INVALID_DEFINITION
        val candidateOffsets = transformedFootprintOffsets(item, definition)
        if (candidateOffsets.isEmpty()) return BuildEditFailure.INVALID_DEFINITION
        val candidateCells = candidateOffsets.map { offset ->
            LongOffset(item.anchor.x.toLong() + offset.x, item.anchor.y.toLong() + offset.y)
        }
        if (candidateCells.any { !it.isWithin(world.island) }) return BuildEditFailure.OUT_OF_BOUNDS

        val occupied = mutableSetOf<LongOffset>()
        world.items.forEachIndexed { index, existing ->
            if (index == ignoredItemIndex) return@forEachIndexed
            val existingDefinition = definitions[existing.definitionId]
                ?: return BuildEditFailure.UNKNOWN_DEFINITION
            if (!existingDefinition.isValidFor(existing)) return BuildEditFailure.INVALID_DEFINITION
            val existingOffsets = transformedFootprintOffsets(existing, existingDefinition)
            if (existingOffsets.isEmpty()) return BuildEditFailure.INVALID_DEFINITION
            val existingCells = existingOffsets.map { offset ->
                LongOffset(existing.anchor.x.toLong() + offset.x, existing.anchor.y.toLong() + offset.y)
            }
            if (existingCells.any { !it.isWithin(world.island) }) return BuildEditFailure.OUT_OF_BOUNDS
            if (existingCells.any { !occupied.add(it) }) return BuildEditFailure.OCCUPIED
        }
        if (candidateCells.any { it in occupied }) return BuildEditFailure.OCCUPIED
        return null
    }
}

sealed interface BuildModeSessionResult {
    val session: BuildModeSession

    data class Applied(override val session: BuildModeSession) : BuildModeSessionResult
    data class NoOp(
        override val session: BuildModeSession,
        val reason: BuildEditNoOp
    ) : BuildModeSessionResult
    data class Rejected(
        override val session: BuildModeSession,
        val reason: BuildEditFailure,
        val itemIndex: Int? = null
    ) : BuildModeSessionResult
}

sealed interface BuildModeHistoryResult {
    val session: BuildModeSession

    data class Changed(override val session: BuildModeSession) : BuildModeHistoryResult
    data class NoOp(override val session: BuildModeSession) : BuildModeHistoryResult
}

/**
 * Immutable, in-memory edit history for one Build-mode session. Callers persist each successful
 * [BuildModeSessionResult.Applied] world separately and discard this session when Build mode ends.
 */
class BuildModeSession private constructor(
    val world: BuildWorld,
    private val undoWorlds: List<BuildWorld>,
    private val redoWorlds: List<BuildWorld>
) {
    val canUndo: Boolean get() = undoWorlds.isNotEmpty()
    val canRedo: Boolean get() = redoWorlds.isNotEmpty()

    /** Applies one SPEC 7.1 edit and records one undo step only when the world changes. */
    fun apply(
        operation: BuildOperation,
        definitions: Map<String, PlaceableDefinition>
    ): BuildModeSessionResult = when (val result = BuildModeRules.apply(world, operation, definitions)) {
        is BuildEditResult.Applied -> BuildModeSessionResult.Applied(
            BuildModeSession(
                world = result.world,
                undoWorlds = (undoWorlds + world).takeLast(GameConfig.BUILD_MODE_HISTORY_LIMIT),
                redoWorlds = emptyList()
            )
        )
        is BuildEditResult.NoOp -> BuildModeSessionResult.NoOp(this, result.reason)
        is BuildEditResult.Rejected -> BuildModeSessionResult.Rejected(this, result.reason, result.itemIndex)
    }

    /** Restores the immediately preceding successful edit in this session (SPEC 7.1). */
    fun undo(): BuildModeHistoryResult {
        val previous = undoWorlds.lastOrNull() ?: return BuildModeHistoryResult.NoOp(this)
        return BuildModeHistoryResult.Changed(
            BuildModeSession(
                world = previous,
                undoWorlds = undoWorlds.dropLast(1),
                redoWorlds = (redoWorlds + world).takeLast(GameConfig.BUILD_MODE_HISTORY_LIMIT)
            )
        )
    }

    /** Reapplies the latest undone edit in this session (SPEC 7.1). */
    fun redo(): BuildModeHistoryResult {
        val next = redoWorlds.lastOrNull() ?: return BuildModeHistoryResult.NoOp(this)
        return BuildModeHistoryResult.Changed(
            BuildModeSession(
                world = next,
                undoWorlds = (undoWorlds + world).takeLast(GameConfig.BUILD_MODE_HISTORY_LIMIT),
                redoWorlds = redoWorlds.dropLast(1)
            )
        )
    }

    /** Ends the SPEC 7.1 editing session and returns its current world for independent persistence. */
    fun finish(): BuildWorld = world

    companion object {
        /** Starts a fresh SPEC 7.1 editing session around saved data with empty session history. */
        fun start(savedWorld: BuildWorld): BuildModeSession = BuildModeSession(
            world = savedWorld,
            undoWorlds = emptyList(),
            redoWorlds = emptyList()
        )
    }
}

private data class LongOffset(val x: Long, val y: Long) {
    fun isWithin(island: WorldIsland): Boolean =
        x >= 0L && y >= 0L && x < island.width.toLong() && y < island.height.toLong()
}

private fun PlaceableDefinition.isValidFor(item: PlacedWorldItem): Boolean =
    id.isNotBlank() && id == item.definitionId && footprint.isNotEmpty() &&
        footprint.all { it.x >= 0 && it.y >= 0 }

private fun TileCoordinate.isWithin(island: WorldIsland): Boolean =
    x >= 0 && y >= 0 && x < island.width && y < island.height
