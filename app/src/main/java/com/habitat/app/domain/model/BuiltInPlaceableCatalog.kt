package com.habitat.app.domain.model

/** Stable, free placeholder definitions available without shop or inventory state. */
object BuiltInPlaceableCatalog {
    const val SMALL_STONE_ID = "placeholder-small-stone"
    const val TWO_TILE_LOG_ID = "placeholder-two-tile-log"

    val definitions: Map<String, PlaceableDefinition> = listOf(
        PlaceableDefinition(
            id = SMALL_STONE_ID,
            footprint = setOf(FootprintOffset(0, 0))
        ),
        PlaceableDefinition(
            id = TWO_TILE_LOG_ID,
            footprint = setOf(FootprintOffset(0, 0), FootprintOffset(1, 0))
        )
    ).associateBy(PlaceableDefinition::id)
}
