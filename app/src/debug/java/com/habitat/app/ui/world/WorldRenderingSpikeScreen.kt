package com.habitat.app.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val WORLD_SPIKE_COLUMNS = 20
internal const val WORLD_SPIKE_ROWS = 10
internal const val WORLD_SPIKE_TILE_COUNT = WORLD_SPIKE_COLUMNS * WORLD_SPIKE_ROWS
internal const val WORLD_SPIKE_TILE_WIDTH_DP = 24f
internal const val WORLD_SPIKE_TILE_HEIGHT_DP = 12f

internal data class WorldSpikeTile(
    val id: Int,
    val column: Int,
    val row: Int,
    val leftDp: Float,
    val topDp: Float,
    val colorIndex: Int
)

internal fun createWorldSpikeTiles(): List<WorldSpikeTile> = buildList(WORLD_SPIKE_TILE_COUNT) {
    for (row in 0 until WORLD_SPIKE_ROWS) {
        for (column in 0 until WORLD_SPIKE_COLUMNS) {
            add(
                WorldSpikeTile(
                    id = row * WORLD_SPIKE_COLUMNS + column,
                    column = column,
                    row = row,
                    leftDp = (column - row + WORLD_SPIKE_ROWS - 1) * WORLD_SPIKE_TILE_WIDTH_DP / 2f,
                    topDp = (column + row) * WORLD_SPIKE_TILE_HEIGHT_DP / 2f,
                    colorIndex = (column + row) % 4
                )
            )
        }
    }
}

internal fun isWorldSpikeTileFullyVisible(
    tile: WorldSpikeTile,
    viewportWidthDp: Float,
    viewportHeightDp: Float,
    originX: Float,
    originY: Float,
    panX: Float,
    panY: Float
): Boolean {
    val left = originX + tile.leftDp + panX
    val top = originY + tile.topDp + panY
    return left >= 0f && top >= 0f &&
        left + WORLD_SPIKE_TILE_WIDTH_DP <= viewportWidthDp &&
        top + WORLD_SPIKE_TILE_HEIGHT_DP <= viewportHeightDp
}

internal data class WorldSpikeDrawSnapshot(
    val completedFrames: Int,
    val minimumTilesDrawn: Int,
    val latestTilesDrawn: Int
)

/** Counts unique tile draw callbacks in each animation frame. */
internal class WorldSpikeFrameDrawCounter {
    private var activeFrame = Long.MIN_VALUE
    private val seenTiles = BooleanArray(WORLD_SPIKE_TILE_COUNT)
    private var activeCount = 0
    private var completedFrames = 0
    private var minimumTilesDrawn = Int.MAX_VALUE
    private var latestTilesDrawn = 0

    @Synchronized
    fun record(frameTimeNanos: Long, tileId: Int) {
        if (frameTimeNanos != activeFrame) {
            finishActiveFrame()
            activeFrame = frameTimeNanos
            seenTiles.fill(false)
            activeCount = 0
        }
        if (!seenTiles[tileId]) {
            seenTiles[tileId] = true
            activeCount += 1
        }
    }

    @Synchronized
    fun reset() {
        activeFrame = Long.MIN_VALUE
        seenTiles.fill(false)
        activeCount = 0
        completedFrames = 0
        minimumTilesDrawn = Int.MAX_VALUE
        latestTilesDrawn = 0
    }

    @Synchronized
    fun snapshot(): WorldSpikeDrawSnapshot {
        return WorldSpikeDrawSnapshot(
            completedFrames = completedFrames,
            minimumTilesDrawn = if (minimumTilesDrawn == Int.MAX_VALUE) 0 else minimumTilesDrawn,
            latestTilesDrawn = latestTilesDrawn
        )
    }

    private fun finishActiveFrame() {
        if (activeFrame == Long.MIN_VALUE) return
        completedFrames += 1
        latestTilesDrawn = activeCount
        minimumTilesDrawn = minOf(minimumTilesDrawn, activeCount)
    }
}

internal enum class WorldSpikeRenderer { CANVAS, COMPOSE_ELEMENTS }

@Composable
internal fun WorldRenderingSpikeScreen(initialRenderer: WorldSpikeRenderer) {
    val tiles = remember { createWorldSpikeTiles() }
    val drawCounter = remember { WorldSpikeFrameDrawCounter() }
    var renderer by remember(initialRenderer) { mutableStateOf(initialRenderer) }
    val frameTimeNanos = remember { mutableLongStateOf(0L) }
    var manualPanX by remember { mutableFloatStateOf(0f) }
    var manualPanY by remember { mutableFloatStateOf(0f) }
    var drawSnapshot by remember { mutableStateOf(WorldSpikeDrawSnapshot(0, 0, 0)) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { frameTimeNanos.longValue = it }
        }
    }

    val tileColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surfaceVariant
    )
    val tileOutline = MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(com.habitat.app.R.string.world_spike_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(com.habitat.app.R.string.world_spike_workload, WORLD_SPIKE_TILE_COUNT),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(com.habitat.app.R.string.world_spike_drag_hint),
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = renderer == WorldSpikeRenderer.CANVAS,
                onClick = { renderer = WorldSpikeRenderer.CANVAS },
                label = { Text(stringResource(com.habitat.app.R.string.world_spike_canvas)) }
            )
            FilterChip(
                selected = renderer == WorldSpikeRenderer.COMPOSE_ELEMENTS,
                onClick = { renderer = WorldSpikeRenderer.COMPOSE_ELEMENTS },
                label = { Text(stringResource(com.habitat.app.R.string.world_spike_compose)) }
            )
        }
        Text(
            text = stringResource(
                com.habitat.app.R.string.world_spike_draw_report,
                drawSnapshot.completedFrames,
                drawSnapshot.minimumTilesDrawn,
                drawSnapshot.latestTilesDrawn
            ),
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                manualPanX = 0f
                manualPanY = 0f
                drawCounter.reset()
                drawSnapshot = WorldSpikeDrawSnapshot(0, 0, 0)
            }) {
                Text(stringResource(com.habitat.app.R.string.world_spike_reset_counter))
            }
            Button(onClick = { drawSnapshot = drawCounter.snapshot() }) {
                Text(stringResource(com.habitat.app.R.string.world_spike_read_counter))
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val density = this
                        manualPanX = (manualPanX + dragAmount.x / density.density).coerceIn(-8f, 8f)
                        manualPanY = (manualPanY + dragAmount.y / density.density).coerceIn(-4f, 4f)
                    }
                }
        ) {
            val sceneWidth = WORLD_SPIKE_COLUMNS * WORLD_SPIKE_TILE_WIDTH_DP / 2f +
                WORLD_SPIKE_ROWS * WORLD_SPIKE_TILE_WIDTH_DP / 2f
            val sceneHeight = WORLD_SPIKE_COLUMNS * WORLD_SPIKE_TILE_HEIGHT_DP / 2f +
                WORLD_SPIKE_ROWS * WORLD_SPIKE_TILE_HEIGHT_DP / 2f
            val originX = ((maxWidth.value - sceneWidth) / 2f).coerceAtLeast(0f)
            val originY = ((maxHeight.value - sceneHeight) / 2f).coerceAtLeast(0f)
            val viewportWidth = maxWidth.value
            val viewportHeight = maxHeight.value

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            ) {
                when (renderer) {
                    WorldSpikeRenderer.CANVAS -> CanvasTileRenderer(
                        tiles = tiles,
                        frameTimeNanos = frameTimeNanos,
                        manualPanX = manualPanX,
                        manualPanY = manualPanY,
                        originX = originX,
                        originY = originY,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                        colors = tileColors,
                        outline = tileOutline,
                        drawCounter = drawCounter
                    )
                    WorldSpikeRenderer.COMPOSE_ELEMENTS -> ComposeElementRenderer(
                        tiles = tiles,
                        frameTimeNanos = frameTimeNanos,
                        manualPanX = manualPanX,
                        manualPanY = manualPanY,
                        originX = originX,
                        originY = originY,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                        colors = tileColors,
                        outline = tileOutline,
                        drawCounter = drawCounter
                    )
                }
            }
        }
    }
}

@Composable
private fun CanvasTileRenderer(
    tiles: List<WorldSpikeTile>,
    frameTimeNanos: State<Long>,
    manualPanX: Float,
    manualPanY: Float,
    originX: Float,
    originY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    colors: List<Color>,
    outline: Color,
    drawCounter: WorldSpikeFrameDrawCounter
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widthPx = with(density) { WORLD_SPIKE_TILE_WIDTH_DP.dp.toPx() }
    val heightPx = with(density) { WORLD_SPIKE_TILE_HEIGHT_DP.dp.toPx() }
    val path = remember(widthPx, heightPx) { createTilePath(widthPx, heightPx) }
    Canvas(Modifier.fillMaxSize()) {
        val frame = frameTimeNanos.value
        val automaticPan = autoPanAt(frame)
        val drawWidthDp = size.width / density.density
        val drawHeightDp = size.height / density.density
        val offsetX = (originX + manualPanX + automaticPan.x) * density.density
        val offsetY = (originY + manualPanY + automaticPan.y) * density.density
        tiles.forEach { tile ->
            if (isWorldSpikeTileFullyVisible(
                    tile,
                    minOf(viewportWidth, drawWidthDp),
                    minOf(viewportHeight, drawHeightDp),
                    originX,
                    originY,
                    manualPanX + automaticPan.x,
                    manualPanY + automaticPan.y
                )
            ) {
                drawCounter.record(frame, tile.id)
            }
            drawContext.transform.translate(
                left = offsetX + tile.leftDp * density.density,
                top = offsetY + tile.topDp * density.density
            )
            drawPath(path, colors[tile.colorIndex])
            drawPath(path, outline, style = Stroke(width = density.density))
            drawContext.transform.translate(
                left = -(offsetX + tile.leftDp * density.density),
                top = -(offsetY + tile.topDp * density.density)
            )
        }
    }
}

@Composable
private fun ComposeElementRenderer(
    tiles: List<WorldSpikeTile>,
    frameTimeNanos: State<Long>,
    manualPanX: Float,
    manualPanY: Float,
    originX: Float,
    originY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    colors: List<Color>,
    outline: Color,
    drawCounter: WorldSpikeFrameDrawCounter
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val width = WORLD_SPIKE_TILE_WIDTH_DP.dp
    val height = WORLD_SPIKE_TILE_HEIGHT_DP.dp
    val widthPx = with(density) { width.toPx() }
    val heightPx = with(density) { height.toPx() }
    val path = remember(widthPx, heightPx) { createTilePath(widthPx, heightPx) }

    Box(Modifier.fillMaxSize()) {
        tiles.forEach { tile ->
            key(tile.id) {
                Canvas(
                    modifier = Modifier
                        .offset {
                            val frame = frameTimeNanos.value
                            val currentPan = autoPanAt(frame)
                            IntOffset(
                                x = with(density) {
                                    (originX + tile.leftDp + manualPanX + currentPan.x).dp.roundToPx()
                                },
                                y = with(density) {
                                    (originY + tile.topDp + manualPanY + currentPan.y).dp.roundToPx()
                                }
                            )
                        }
                        .size(width, height)
                        .drawWithContent {
                            val frame = frameTimeNanos.value
                            val currentPan = autoPanAt(frame)
                            if (isWorldSpikeTileFullyVisible(
                                    tile,
                                    viewportWidth,
                                    viewportHeight,
                                    originX,
                                    originY,
                                    manualPanX + currentPan.x,
                                    manualPanY + currentPan.y
                                )
                            ) {
                                drawCounter.record(frame, tile.id)
                            }
                            drawContent()
                        }
                ) {
                    drawPath(path, colors[tile.colorIndex])
                    drawPath(path, outline, style = Stroke(width = density.density))
                }
            }
        }
    }
}

private fun createTilePath(width: Float, height: Float): Path = Path().apply {
    moveTo(width / 2f, 0f)
    lineTo(width, height / 2f)
    lineTo(width / 2f, height)
    lineTo(0f, height / 2f)
    close()
}

private fun autoPanAt(frameTimeNanos: Long): Offset {
    val periodNanos = 5_000_000_000L
    val phase = (frameTimeNanos % periodNanos).toDouble() / periodNanos * 2.0 * PI
    return Offset(
        x = (8.0 * sin(phase)).toFloat(),
        y = (4.0 * sin(phase * 2.0)).toFloat()
    )
}

@Preview(showBackground = true, widthDp = 411, heightDp = 850)
@Composable
private fun WorldRenderingSpikePreview() {
    MaterialTheme {
        WorldRenderingSpikeScreen(WorldSpikeRenderer.CANVAS)
    }
}
