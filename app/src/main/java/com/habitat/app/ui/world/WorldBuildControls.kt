package com.habitat.app.ui.world

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.habitat.app.R
import com.habitat.app.domain.model.BuiltInPlaceableCatalog

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorldBuildControls(
    editing: WorldBuildUiState.Editing,
    batchMode: Boolean,
    onSelectDefinition: (String) -> Unit,
    onSelectTool: (WorldBuildTool) -> Unit,
    onToggleBatchMode: () -> Unit,
    onApplyBatch: () -> Unit,
    onCancelBatch: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRetry: () -> Unit
) {
    val editable = editing.saveStatus is WorldBuildSaveStatus.Idle

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (editing.saveStatus) {
            WorldBuildSaveStatus.Idle -> Unit
            is WorldBuildSaveStatus.Saving -> Text(stringResource(R.string.world_build_saving))
            is WorldBuildSaveStatus.Failed -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.world_build_save_failed))
                Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.world_retry))
                }
            }
        }

        Text(stringResource(R.string.world_build_palette_title))
        Text(stringResource(R.string.world_build_placeholder_notice))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PaletteButton(
                definitionId = BuiltInPlaceableCatalog.SMALL_STONE_ID,
                label = R.string.world_build_stone_placeholder,
                editing = editing,
                editable = editable,
                onSelectDefinition = onSelectDefinition
            )
            PaletteButton(
                definitionId = BuiltInPlaceableCatalog.TWO_TILE_LOG_ID,
                label = R.string.world_build_log_placeholder,
                editing = editing,
                editable = editable,
                onSelectDefinition = onSelectDefinition
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ToolButton(R.string.world_build_tool_place, WorldBuildTool.PLACE, editing, editable, onSelectTool)
            ToolButton(R.string.world_build_tool_move, WorldBuildTool.MOVE, editing, editable, onSelectTool)
            ToolButton(R.string.world_build_tool_rotate, WorldBuildTool.ROTATE, editing, editable, onSelectTool)
            ToolButton(R.string.world_build_tool_flip, WorldBuildTool.FLIP, editing, editable, onSelectTool)
            ToolButton(R.string.world_build_tool_erase, WorldBuildTool.ERASER, editing, editable, onSelectTool)
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(
                onClick = onToggleBatchMode,
                enabled = editable,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(if (batchMode) R.string.world_build_batch_mode_on else R.string.world_build_batch_mode_off))
            }
            OutlinedButton(
                onClick = onApplyBatch,
                enabled = editable && editing.queuedPlacements.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.world_build_apply_batch, editing.queuedPlacements.size)) }
            OutlinedButton(
                onClick = onCancelBatch,
                enabled = editable && editing.queuedPlacements.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.world_build_cancel_batch)) }
            OutlinedButton(onClick = onUndo, enabled = editable && editing.session.canUndo, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.world_build_undo))
            }
            OutlinedButton(onClick = onRedo, enabled = editable && editing.session.canRedo, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.world_build_redo))
            }
        }
    }
}

@Composable
private fun PaletteButton(
    definitionId: String,
    label: Int,
    editing: WorldBuildUiState.Editing,
    editable: Boolean,
    onSelectDefinition: (String) -> Unit
) {
    val onClick = { onSelectDefinition(definitionId) }
    if (editing.selectedDefinitionId == definitionId) {
        Button(onClick = onClick, enabled = editable, modifier = Modifier.heightIn(min = 48.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("✓")
                Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = editable, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ToolButton(
    label: Int,
    tool: WorldBuildTool,
    editing: WorldBuildUiState.Editing,
    editable: Boolean,
    onSelectTool: (WorldBuildTool) -> Unit
) {
    val selected = editing.activeTool == tool
    if (selected) {
        Button(onClick = { onSelectTool(tool) }, enabled = editable, modifier = Modifier.heightIn(min = 48.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("✓")
                Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    } else {
        OutlinedButton(onClick = { onSelectTool(tool) }, enabled = editable, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(label))
        }
    }
}
