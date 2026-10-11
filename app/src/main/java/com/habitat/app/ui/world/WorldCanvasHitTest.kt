package com.habitat.app.ui.world

import com.habitat.app.domain.logic.ProjectedWorldTile
import com.habitat.app.domain.logic.WorldPoint
import com.habitat.app.domain.model.PlacedWorldItem
import com.habitat.app.domain.model.TileCoordinate

/**
 * Finds the first projected island tile whose inclusive diamond contains the Canvas point.
 * The input order is the overlap tie-break order.
 */
internal fun hitTestWorldTile(
    point: WorldPoint,
    projectedTiles: List<ProjectedWorldTile>
): TileCoordinate? {
    if (!point.x.isFinite() || !point.y.isFinite()) return null
    return projectedTiles.firstOrNull { tile -> pointInDiamond(point, tile) }
        ?.let { TileCoordinate(it.x, it.y) }
}

/**
 * Finds the first placed item occupying [coordinate], including any cell in a multi-cell footprint.
 */
internal fun hitTestPlacedWorldItem(
    coordinate: TileCoordinate,
    renderModel: WorldRenderModel
): PlacedWorldItem? = renderModel.items.firstOrNull { coordinate in it.occupiedTiles }?.placement

private fun pointInDiamond(point: WorldPoint, tile: ProjectedWorldTile): Boolean {
    val vertices = listOf(tile.top, tile.right, tile.bottom, tile.left)
    var positive = false
    var negative = false

    vertices.indices.forEach { index ->
        val start = vertices[index]
        val end = vertices[(index + 1) % vertices.size]
        val edgeX = end.x - start.x
        val edgeY = end.y - start.y
        val relativeX = point.x - start.x
        val relativeY = point.y - start.y
        val cross = edgeX * relativeY - edgeY * relativeX
        val edgeLength = kotlin.math.sqrt(edgeX * edgeX + edgeY * edgeY)
        val tolerance = EDGE_DISTANCE_TOLERANCE_PX * edgeLength

        if (cross > tolerance) positive = true
        if (cross < -tolerance) negative = true
    }

    return !(positive && negative)
}

private const val EDGE_DISTANCE_TOLERANCE_PX = 0.0001f
