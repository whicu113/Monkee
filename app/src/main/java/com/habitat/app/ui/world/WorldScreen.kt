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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitat.app.R
import com.habitat.app.domain.logic.projectWorldTiles
import com.habitat.app.domain.model.WorldIsland
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
    WorldScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldScreenContent(
    uiState: WorldUiState,
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
                island = state.island,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@Composable
private fun WorldIslandCanvas(island: WorldIsland, modifier: Modifier = Modifier) {
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

    Canvas(
        modifier = modifier.semantics { contentDescription = description }
    ) {
        val projectedTiles = projectWorldTiles(island, size.width, size.height)
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
    }
}

@Preview(showBackground = true)
@Composable
private fun WorldScreenPreview() {
    MonkeeTheme {
        WorldScreenContent(
            uiState = WorldUiState.Content(createStarterIsland()),
            onNavigateBack = {},
            onRetry = {}
        )
    }
}
