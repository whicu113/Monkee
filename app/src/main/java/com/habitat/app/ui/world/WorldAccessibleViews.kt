package com.habitat.app.ui.world

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.habitat.app.R
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldTerrain

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorldBuildContentSelector(
    selected: WorldBuildContentMode,
    onSelect: (WorldBuildContentMode) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ContentModeChip(WorldBuildContentMode.CANVAS, selected, R.string.world_build_view_canvas, onSelect)
        ContentModeChip(WorldBuildContentMode.TILES, selected, R.string.world_build_view_tiles, onSelect)
        ContentModeChip(WorldBuildContentMode.ITEMS, selected, R.string.world_build_view_items, onSelect)
    }
}

@Composable
private fun ContentModeChip(
    mode: WorldBuildContentMode,
    selected: WorldBuildContentMode,
    label: Int,
    onSelect: (WorldBuildContentMode) -> Unit
) {
    FilterChip(
        selected = selected == mode,
        onClick = { onSelect(mode) },
        label = { Text(stringResource(label)) },
        modifier = Modifier.heightIn(min = 48.dp)
    )
}

@Composable
internal fun WorldAccessibleTilePicker(
    tiles: List<AccessibleWorldTile>,
    enabled: Boolean,
    onTileSelected: (TileCoordinate) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(tiles, key = { "${it.coordinate.x},${it.coordinate.y}" }) { tile ->
            val terrain = stringResource(terrainNameResource(tile.terrain))
            val occupancy = tile.occupiedBy?.let { kind ->
                stringResource(R.string.world_accessible_tile_occupied_by, stringResource(itemNameResource(kind)))
            } ?: stringResource(R.string.world_accessible_tile_empty)
            val description = stringResource(
                R.string.world_accessible_tile_description,
                tile.row,
                tile.column,
                terrain,
                occupancy
            )
            Button(
                onClick = { onTileSelected(tile.coordinate) },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = description }
            ) {
                Text(stringResource(R.string.world_accessible_tile_label, tile.row, tile.column))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorldAccessibleItemList(
    items: List<AccessibleWorldItem>,
    enabled: Boolean,
    onMove: (AccessibleWorldItem) -> Unit,
    onRotate: (AccessibleWorldItem) -> Unit,
    onFlip: (AccessibleWorldItem) -> Unit,
    onErase: (AccessibleWorldItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Column(modifier = modifier.padding(16.dp)) {
            Text(stringResource(R.string.world_build_items_empty))
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items, key = AccessibleWorldItem::id) { item ->
            val name = stringResource(itemNameResource(item.kind))
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(stringResource(R.string.world_accessible_item_location, name, item.row, item.column))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AccessibleActionButton(
                        label = R.string.world_build_tool_move,
                        description = stringResource(R.string.world_accessible_action_move, name, item.row, item.column),
                        enabled = enabled,
                        onClick = { onMove(item) }
                    )
                    AccessibleActionButton(
                        label = R.string.world_build_tool_rotate,
                        description = stringResource(R.string.world_accessible_action_rotate, name, item.row, item.column),
                        enabled = enabled,
                        onClick = { onRotate(item) }
                    )
                    AccessibleActionButton(
                        label = R.string.world_build_tool_flip,
                        description = stringResource(R.string.world_accessible_action_flip, name, item.row, item.column),
                        enabled = enabled,
                        onClick = { onFlip(item) }
                    )
                    AccessibleActionButton(
                        label = R.string.world_build_tool_erase,
                        description = stringResource(R.string.world_accessible_action_erase, name, item.row, item.column),
                        enabled = enabled,
                        onClick = { onErase(item) }
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun AccessibleActionButton(
    label: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description }
    ) {
        Text(stringResource(label))
    }
}

private fun terrainNameResource(terrain: WorldTerrain): Int = when (terrain) {
    WorldTerrain.DEEP_WATER -> R.string.world_accessible_terrain_deep_water
    WorldTerrain.SHALLOW_WATER -> R.string.world_accessible_terrain_shallow_water
    WorldTerrain.SAND -> R.string.world_accessible_terrain_sand
    WorldTerrain.DIRT -> R.string.world_accessible_terrain_dirt
    WorldTerrain.GRASS -> R.string.world_accessible_terrain_grass
}

private fun itemNameResource(kind: AccessibleWorldItemKind): Int = when (kind) {
    AccessibleWorldItemKind.STONE -> R.string.world_accessible_item_stone
    AccessibleWorldItemKind.LOG -> R.string.world_accessible_item_log
    AccessibleWorldItemKind.UNKNOWN -> R.string.world_accessible_item_unknown
}
