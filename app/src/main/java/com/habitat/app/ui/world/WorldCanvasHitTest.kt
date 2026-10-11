package com.habitat.app.ui.world

import com.habitat.app.domain.logic.ProjectedWorldTile
import com.habitat.app.domain.logic.WorldPoint
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.model.TileCoordinate

/**
 * Finds the island tile under a Canvas point. The geometric hit test is implemented in step 4c;
 * this seam keeps the current read-only World behavior unchanged in step 4a.
 */
@Suppress("UNUSED_PARAMETER")
internal fun hitTestWorldTile(
    point: WorldPoint,
    projectedTiles: List<ProjectedWorldTile>
): TileCoordinate? = null

/**
 * Finds the placed item occupying [coordinate], including taps on any cell of a multi-cell item.
 * Occupancy lookup is implemented in step 4c.
 */
@Suppress("UNUSED_PARAMETER")
internal fun hitTestPlacedWorldItem(
    coordinate: TileCoordinate,
    renderModel: WorldRenderModel
): PlacedWorldItem? = null
