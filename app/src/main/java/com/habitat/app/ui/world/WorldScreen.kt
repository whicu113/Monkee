package com.habitat.app.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitat.app.R
import com.habitat.app.domain.logic.WorldPoint
import com.habitat.app.domain.logic.projectWorldTiles
import com.habitat.app.domain.model.BuiltInPlaceableCatalog
import com.habitat.app.domain.model.TileCoordinate
import com.habitat.app.domain.model.WorldTerrain
import com.habitat.app.domain.model.createStarterIsland
import com.habitat.app.ui.theme.MonkeeTheme
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldScreen(
    viewModel: WorldViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val renderModel by viewModel.renderModel.collectAsStateWithLifecycle()
    val buildModeState by viewModel.buildModeState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var batchMode by remember { mutableStateOf(false) }

    val rejectedMessage = stringResource(R.string.world_build_edit_rejected)
    val noChangeMessage = stringResource(R.string.world_build_no_change)
    LaunchedEffect(viewModel, snackbarHostState, rejectedMessage, noChangeMessage) {
        viewModel.buildFeedback.collectLatest { feedback ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = when (feedback) {
                    WorldBuildFeedback.EDIT_REJECTED -> rejectedMessage
                    WorldBuildFeedback.NO_CHANGE -> noChangeMessage
                },
                duration = SnackbarDuration.Short
            )
        }
    }
    LaunchedEffect(buildModeState is WorldBuildUiState.Editing) {
        if (buildModeState !is WorldBuildUiState.Editing) batchMode = false
    }

    WorldScreenContent(
        uiState = uiState,
        renderModel = renderModel,
        buildModeState = buildModeState,
        batchMode = batchMode,
        snackbarHostState = snackbarHostState,
        onNavigateBack = {
            if (buildModeState is WorldBuildUiState.Editing) {
                viewModel.exitBuildMode()
            } else {
                onNavigateBack()
            }
        },
        onRetry = viewModel::retry,
        onEnterBuild = viewModel::enterBuildMode,
        onExitBuild = viewModel::exitBuildMode,
        onSelectDefinition = viewModel::selectDefinition,
        onSelectTool = viewModel::selectTool,
        onToggleBatchMode = { batchMode = !batchMode },
        onApplyBatch = viewModel::applyQueuedPlacements,
        onCancelBatch = viewModel::cancelQueuedPlacements,
        onUndo = viewModel::undoBuild,
        onRedo = viewModel::redoBuild,
        onRetryBuildSave = viewModel::retryBuildSave,
        onCanvasTileTapped = { coordinate -> viewModel.onCanvasTileTapped(coordinate, batchMode) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldScreenContent(
    uiState: WorldUiState,
    renderModel: WorldRenderModel?,
    buildModeState: WorldBuildUiState = WorldBuildUiState.Inactive,
    batchMode: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onEnterBuild: () -> Unit = {},
    onExitBuild: () -> Unit = {},
    onSelectDefinition: (String) -> Unit = {},
    onSelectTool: (WorldBuildTool) -> Unit = {},
    onToggleBatchMode: () -> Unit = {},
    onApplyBatch: () -> Unit = {},
    onCancelBatch: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onRetryBuildSave: () -> Unit = {},
    onCanvasTileTapped: ((TileCoordinate) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                },
                actions = {
                    if (buildModeState is WorldBuildUiState.Editing) {
                        Button(onClick = onExitBuild) {
                            Text(stringResource(R.string.world_build_exit))
                        }
                    } else if (uiState is WorldUiState.Content) {
                        Button(onClick = onEnterBuild) {
                            Text(stringResource(R.string.world_build_enter))
                        }
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

            is WorldUiState.Content -> {
                val model = renderModel ?: state.renderModel
                val editing = buildModeState as? WorldBuildUiState.Editing
                if (editing == null) {
                    WorldIslandCanvas(
                        renderModel = model,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        WorldBuildControls(
                            editing = editing,
                            batchMode = batchMode,
                            onSelectDefinition = onSelectDefinition,
                            onSelectTool = onSelectTool,
                            onToggleBatchMode = onToggleBatchMode,
                            onApplyBatch = onApplyBatch,
                            onCancelBatch = onCancelBatch,
                            onUndo = onUndo,
                            onRedo = onRedo,
                            onRetry = onRetryBuildSave
                        )
                        WorldIslandCanvas(
                            renderModel = worldRenderModelFor(editing.session.world),
                            selectedItemId = editing.selectedItemId,
                            onTileTapped = onCanvasTileTapped,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldIslandCanvas(
    renderModel: WorldRenderModel,
    selectedItemId: String? = null,
    onTileTapped: ((TileCoordinate) -> Unit)? = null,
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
    val currentOnTileTapped = rememberUpdatedState(onTileTapped)
    val gestureModifier = if (onTileTapped == null) {
        Modifier
    } else {
        Modifier.pointerInput(renderModel) {
            detectTapGestures { position ->
                val projectedTiles = projectWorldTiles(
                    island = island,
                    viewportWidth = size.width.toFloat(),
                    viewportHeight = size.height.toFloat()
                )
                val coordinate = hitTestWorldTile(
                    point = WorldPoint(position.x, position.y),
                    projectedTiles = projectedTiles
                )
                if (coordinate != null) currentOnTileTapped.value?.invoke(coordinate)
            }
        }
    }

    Canvas(
        modifier = modifier
            .then(gestureModifier)
            .semantics { contentDescription = description }
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
