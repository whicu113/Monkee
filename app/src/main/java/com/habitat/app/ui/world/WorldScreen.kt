package com.habitat.app.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitat.app.R
import com.habitat.app.domain.logic.projectWorldTiles
import com.habitat.app.domain.model.BuiltInPlaceableCatalog
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldTerrain
import com.habitat.app.domain.model.createStarterIsland
import com.habitat.app.ui.theme.MonkeeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldScreen(
    viewModel: WorldViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val renderModel by viewModel.renderModel.collectAsStateWithLifecycle()
    WorldScreenContent(
        uiState = uiState,
        renderModel = renderModel,
        onNavigateBack = onNavigateBack,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldScreenContent(
    uiState: WorldUiState,
    renderModel: WorldRenderModel?,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.world_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.world_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            WorldUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            WorldUiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.world_load_error))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.world_retry))
                }
            }

            is WorldUiState.Content -> WorldIslandCanvas(
                renderModel = renderModel ?: state.renderModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@Composable
private fun WorldIslandCanvas(
    renderModel: WorldRenderModel,
    selectedItemId: String? = null,
    modifier: Modifier = Modifier
) {
    val island = renderModel.island
    val description = stringResource(
        R.string.world_island_description,
        island.width,
        island.height
    )
    val grass = MaterialTheme.colorScheme.tertiaryContainer
    val water = MaterialTheme.colorScheme.primaryContainer
    val sand = MaterialTheme.colorScheme.secondaryContainer
    val dirt = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val stoneColor = MaterialTheme.colorScheme.primary
    val logColor = MaterialTheme.colorScheme.secondary
    val selectionColor = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier = modifier.semantics { contentDescription = description }
    ) {
        val projectedTiles = projectWorldTiles(island, size.width, size.height)
        val projectedByCoordinate = projectedTiles.associateBy { TileCoordinate(it.x, it.y) }
        projectedTiles.forEach { tile ->
            val centerColor = when (tile.terrain) {
                WorldTerrain.DEEP_WATER, WorldTerrain.SHALLOW_WATER -> water
                WorldTerrain.SAND -> sand
                WorldTerrain.DIRT -> dirt
                WorldTerrain.GRASS -> grass
            }
            val path = Path().apply {
                moveTo(tile.top.x, tile.top.y)
                lineTo(tile.right.x, tile.right.y)
                lineTo(tile.bottom.x, tile.bottom.y)
                lineTo(tile.left.x, tile.left.y)
                close()
            }
            drawPath(path, color = centerColor)
            drawPath(path, color = outline, style = Stroke(width = 1.dp.toPx()))
        }

        renderModel.items.forEach { renderedItem ->
            val occupiedCenters = renderedItem.occupiedTiles.mapNotNull { coordinate ->
                projectedByCoordinate[coordinate]?.let { tile ->
                    Offset(
                        x = (tile.left.x + tile.right.x) / 2f,
                        y = (tile.top.y + tile.bottom.y) / 2f
                    )
                }
            }
            if (occupiedCenters.isEmpty()) return@forEach

            val isLog = renderedItem.placement.definitionId == BuiltInPlaceableCatalog.TWO_TILE_LOG_ID
            val markerColor = if (isLog) logColor else stoneColor
            val tileWidth = projectedByCoordinate[renderedItem.occupiedTiles.first()]?.let { tile ->
                tile.right.x - tile.left.x
            } ?: 0f
            val radius = tileWidth * 0.18f

            if (isLog && occupiedCenters.size > 1) {
                drawLine(
                    color = markerColor,
                    start = occupiedCenters.first(),
                    end = occupiedCenters.last(),
                    strokeWidth = tileWidth * 0.24f,
                    cap = StrokeCap.Round
                )
            } else {
                occupiedCenters.forEach { center ->
                    drawCircle(color = markerColor, radius = radius, center = center)
                }
            }

            if (renderedItem.placement.id == selectedItemId) {
                occupiedCenters.forEach { center ->
                    drawCircle(
                        color = selectionColor,
                        radius = radius + 3.dp.toPx(),
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WorldScreenPreview() {
    MonkeeTheme {
        WorldScreenContent(
            uiState = WorldUiState.Content(createStarterIsland()),
            renderModel = null,
            onNavigateBack = {},
            onRetry = {}
        )
    }
}
