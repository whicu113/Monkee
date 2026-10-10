package com.habitat.app.platform.widget

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.habitat.app.MainActivity
import com.habitat.app.R
import com.habitat.app.domain.logic.WidgetCompletionDecision
import com.habitat.app.domain.logic.executeWidgetCompletionAction
import com.habitat.app.domain.logic.WidgetDisplayState
import com.habitat.app.domain.logic.WidgetHabitRow
import com.habitat.app.domain.logic.WidgetProgressRingMapper
import com.habitat.app.domain.logic.WidgetRowActionState
import com.habitat.app.domain.logic.WidgetStateFlow
import com.habitat.app.domain.logic.WidgetRenderState
import com.habitat.app.domain.logic.widgetCompletionDecision
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.logic.rowActions
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.nio.charset.StandardCharsets
import java.util.UUID

private val SMALL_WIDGET_SIZE = DpSize(130.dp, 160.dp)
private val MEDIUM_WIDGET_SIZE = DpSize(250.dp, 380.dp)
private const val TAG = "HabitGlanceWidget"
private val HabitIdParameter = ActionParameters.Key<String>("habitId")
private val CompletionStateParameter = ActionParameters.Key<String>("completionState")
private val RenderedDateParameter = ActionParameters.Key<String>("renderedLogicalDate")

/** Glance widget that derives today's display state from the current repositories and clock. */
class HabitGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL_WIDGET_SIZE, MEDIUM_WIDGET_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val renderStartedAt = Instant.now()
        Log.d(TAG, "provideGlance started timestamp=$renderStartedAt glanceId=$id")
        val stateFlow = try {
            withContext(Dispatchers.IO) {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    HabitWidgetEntryPoint::class.java
                )
                WidgetStateFlow.build(
                    clock = entryPoint.appClock(),
                    habits = entryPoint.habitRepository().getAllHabits(),
                    completions = entryPoint.completionRepository().getAllCompletions(),
                    settings = entryPoint.settingsRepository().getSettings(),
                    refreshGeneration = entryPoint.widgetRefreshSignal().refreshGeneration
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to load today's widget state", exception)
            null
        }
        val renderFlow = stateFlow?.flowOn(Dispatchers.IO)
            ?.map<WidgetRenderState, WidgetDataState> { WidgetDataState.Ready(it) }
            ?.catch { exception ->
                if (exception is CancellationException) throw exception
                Log.e(TAG, "Widget state stream failed glanceId=$id", exception)
                emit(WidgetDataState.Error)
            }

        provideContent {
            val contentState = renderFlow?.collectAsState(initial = WidgetDataState.Loading)?.value
                ?: WidgetDataState.Error
            val snapshot = (contentState as? WidgetDataState.Ready)?.renderState
            Log.d(
                TAG,
                "WidgetContent recomposed timestamp=${Instant.now()} " +
                    "state=${snapshot?.displayState?.toDiagnosticLabel() ?: contentState.javaClass.simpleName} " +
                    "logicalDate=${snapshot?.renderedLogicalDate} refreshGeneration=${snapshot?.refreshGeneration} glanceId=$id"
            )
            when (contentState) {
                WidgetDataState.Loading -> WidgetFallback(
                    title = "Loading your habits…",
                    detail = "Tap to open Habitat."
                )
                WidgetDataState.Error -> WidgetFallback(
                    title = "Habitat couldn't load your habits",
                    detail = "Tap to open Habitat and try again."
                )
                is WidgetDataState.Ready -> WidgetContent(
                    contentState.renderState.displayState,
                    contentState.renderState.renderedLogicalDate
                )
            }
        }
    }
}

private sealed interface WidgetDataState {
    object Loading : WidgetDataState
    object Error : WidgetDataState
    data class Ready(val renderState: WidgetRenderState) : WidgetDataState
}

private fun WidgetDisplayState.toDiagnosticLabel(): String = when (this) {
    WidgetDisplayState.NoHabits -> "NoHabits"
    WidgetDisplayState.NothingDueToday -> "NothingDueToday"
    is WidgetDisplayState.AllSkipped -> "AllSkipped(rows=${rows.size})"
    is WidgetDisplayState.Habits ->
        "Habits(rows=${rows.size}, completed=$completedCount/$totalDueCount, allCompleted=$allCompleted)"
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun WidgetContent(state: WidgetDisplayState, renderedDate: LocalDate) {
    val size = LocalSize.current
    val smallest = size.width < MEDIUM_WIDGET_SIZE.width || size.height < MEDIUM_WIDGET_SIZE.height
    val cardModifier = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(R.color.widget_surface))
        .padding(12.dp)

    if (smallest) {
        SmallWidgetRing(state)
    } else {
        when (state) {
            WidgetDisplayState.NoHabits -> WidgetFallback(
                title = "Start with one small habit",
                detail = "Tap to open Habitat and add a habit.",
                modifier = cardModifier
            )

            WidgetDisplayState.NothingDueToday -> WidgetStatusCard(
                title = "Nothing due today",
                detail = "Your habits are on their own schedule.",
                modifier = cardModifier
            )

            is WidgetDisplayState.AllSkipped -> AllSkippedCard(state, cardModifier, renderedDate)

            is WidgetDisplayState.Habits -> HabitsCard(state, cardModifier, renderedDate)
        }
    }
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun SmallWidgetRing(state: WidgetDisplayState) {
    val description = when (state) {
        WidgetDisplayState.NoHabits -> "No habits yet. Tap to open Habitat."
        WidgetDisplayState.NothingDueToday -> "Nothing due today. Tap to open Habitat."
        is WidgetDisplayState.AllSkipped -> "All habits skipped today. Tap to open Habitat."
        is WidgetDisplayState.Habits ->
            "Today's progress: ${state.completedCount} of ${state.totalDueCount} done. Tap to open Habitat."
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(R.color.widget_surface))
            .clickable(actionStartActivity<MainActivity>())
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        if (state is WidgetDisplayState.Habits) {
            ProgressRing(
                index = requireNotNull(WidgetProgressRingMapper.ringIndex(state)),
                description = description,
                size = 56
            )
        } else {
            NeutralRing(description, 56)
        }
    }
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun HabitsCard(
    state: WidgetDisplayState.Habits,
    modifier: GlanceModifier,
    renderedDate: LocalDate
) {
    val ringIndex = requireNotNull(WidgetProgressRingMapper.ringIndex(state))
    val ringDescription = "Today's progress: ${state.completedCount} of ${state.totalDueCount} done"

    Column(
        modifier = modifier.semantics { contentDescription = ringDescription },
        verticalAlignment = Alignment.Vertical.Top
    ) {
        ProgressHeader(ringIndex, ringDescription, state.completedCount, state.totalDueCount)
        LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            items(state.rows, itemId = { stableWidgetItemId(it.habitId) }) { row ->
                HabitRow(row, renderedDate)
            }
        }
    }
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun AllSkippedCard(
    state: WidgetDisplayState.AllSkipped,
    modifier: GlanceModifier,
    renderedDate: LocalDate
) {
    Column(modifier = modifier, verticalAlignment = Alignment.Vertical.Top) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(60.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            NeutralRing("All skipped today", 52)
            Column(
                modifier = GlanceModifier.defaultWeight().padding(start = 10.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text("All skipped today", style = primaryStyle(16))
                Text("You're ready when you are.", style = secondaryStyle(13))
            }
        }
        LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            items(state.rows, itemId = { stableWidgetItemId(it.habitId) }) { row ->
                HabitRow(row, renderedDate)
            }
        }
    }
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun ProgressHeader(index: Int, description: String, completed: Int, total: Int) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(60.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        ProgressRing(index, description, 52)
        Column(
            modifier = GlanceModifier.defaultWeight().padding(start = 10.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text("Today's progress", style = primaryStyle(16))
            Text("$completed of $total done", style = secondaryStyle(13))
        }
    }
}

private fun stableWidgetItemId(habitId: String): Long =
    UUID.nameUUIDFromBytes(habitId.toByteArray(StandardCharsets.UTF_8)).leastSignificantBits and Long.MAX_VALUE

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun ProgressRing(index: Int, description: String, size: Int) {
    Image(
        provider = ImageProvider(ringDrawable(index)),
        contentDescription = description,
        modifier = GlanceModifier.size(size.dp)
    )
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun NeutralRing(description: String, size: Int) {
    Image(
        provider = ImageProvider(R.drawable.ic_widget_ring_neutral),
        contentDescription = description,
        modifier = GlanceModifier.size(size.dp)
    )
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun HabitRow(row: WidgetHabitRow, renderedDate: LocalDate) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        when (rowActions(row.completionState)) {
            WidgetRowActionState.SHOW_BUTTONS -> {
                Text(
                    text = "${row.emoji} ${row.name}",
                    modifier = GlanceModifier.defaultWeight().semantics {
                        contentDescription = row.name
                    },
                    style = primaryStyle(14)
                )
                VisualAction(row, CompletionState.DONE, renderedDate)
                Spacer(GlanceModifier.width(4.dp))
                VisualAction(row, CompletionState.MINIMUM, renderedDate)
            }

            else -> Text(
                text = "✓ ${row.emoji} ${row.name} · ${rowStateLabel(row.completionState)}",
                modifier = GlanceModifier.defaultWeight().semantics {
                    contentDescription = habitStateDescription(row)
                },
                style = primaryStyle(14)
            )
        }
    }
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun VisualAction(row: WidgetHabitRow, state: CompletionState, renderedDate: LocalDate) {
    val label = if (state == CompletionState.DONE) "Done" else "Minimum"
    val description = "Mark ${row.name} as $label"
    Column(
        modifier = GlanceModifier
            .width(48.dp)
            .height(48.dp)
            .background(ColorProvider(R.color.widget_action_surface))
            .semantics { contentDescription = description }
            .clickable(
                actionRunCallback<WidgetCompletionActionCallback>(
                    actionParametersOf(
                        HabitIdParameter to row.habitId,
                        CompletionStateParameter to state.name,
                        RenderedDateParameter to renderedDate.toString()
                    )
                )
            ),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(R.color.widget_action_text),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun WidgetStatusCard(title: String, detail: String, modifier: GlanceModifier) {
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        NeutralRing(title, 48)
        Spacer(GlanceModifier.height(8.dp))
        Text(title, modifier = GlanceModifier.semantics { contentDescription = title }, style = primaryStyle(16))
        Text(detail, style = secondaryStyle(13))
    }
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun WidgetFallback(
    title: String,
    detail: String,
    modifier: GlanceModifier = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(R.color.widget_surface))
        .padding(16.dp)
) {
    Column(
        modifier = modifier
            .clickable(actionStartActivity<MainActivity>())
            .semantics { contentDescription = "$title. $detail" },
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(title, style = primaryStyle(16))
        Spacer(GlanceModifier.height(6.dp))
        Text(detail, style = secondaryStyle(13))
    }
}

private fun ringDrawable(index: Int): Int = when (index.coerceIn(0, 10)) {
    0 -> R.drawable.ic_widget_ring_0
    1 -> R.drawable.ic_widget_ring_1
    2 -> R.drawable.ic_widget_ring_2
    3 -> R.drawable.ic_widget_ring_3
    4 -> R.drawable.ic_widget_ring_4
    5 -> R.drawable.ic_widget_ring_5
    6 -> R.drawable.ic_widget_ring_6
    7 -> R.drawable.ic_widget_ring_7
    8 -> R.drawable.ic_widget_ring_8
    9 -> R.drawable.ic_widget_ring_9
    else -> R.drawable.ic_widget_ring_10
}

private fun primaryStyle(sizeSp: Int) = TextStyle(
    color = ColorProvider(R.color.widget_text_primary),
    fontSize = sizeSp.sp,
    fontWeight = FontWeight.Medium
)

private fun secondaryStyle(sizeSp: Int) = TextStyle(
    color = ColorProvider(R.color.widget_text_secondary),
    fontSize = sizeSp.sp
)

private fun rowStateLabel(completionState: com.habitat.app.domain.model.CompletionState?): String =
    when (completionState) {
        null -> ""
        com.habitat.app.domain.model.CompletionState.DONE -> "Done"
        com.habitat.app.domain.model.CompletionState.MINIMUM -> "Minimum"
        com.habitat.app.domain.model.CompletionState.SKIPPED -> "Skipped"
    }

private fun habitStateDescription(row: WidgetHabitRow): String = when (row.completionState) {
    null -> row.name
    com.habitat.app.domain.model.CompletionState.DONE -> "${row.name}, done"
    com.habitat.app.domain.model.CompletionState.MINIMUM -> "${row.name}, minimum"
    com.habitat.app.domain.model.CompletionState.SKIPPED -> "${row.name}, skipped"
}

/** BroadcastReceiver for the Habitat Glance widget. */
class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitGlanceWidget()
}

class WidgetCompletionActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val habitId = parameters[HabitIdParameter]
        val renderedDate = parameters[RenderedDateParameter]?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val completionState = parameters[CompletionStateParameter]?.let {
            runCatching { CompletionState.valueOf(it) }.getOrNull()
        }?.takeIf { it == CompletionState.DONE || it == CompletionState.MINIMUM }

        if (habitId == null || renderedDate == null || completionState == null) {
            Log.e(TAG, "Widget action row '${habitId ?: "unknown"}' has invalid parameters")
            return
        }
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            HabitWidgetEntryPoint::class.java
        )
        executeWidgetCompletionAction(
            rowName = habitId,
            habitId = habitId,
            state = completionState,
            renderedDate = renderedDate,
            resolveDecision = {
                val settings = entryPoint.settingsRepository().getSettings().first()
                val currentDate = entryPoint.appClock().logicalDate(settings.dayBoundaryHour)
                val habit = entryPoint.habitRepository().getHabitById(habitId)
                val existing = entryPoint.completionRepository().getCompletion(habitId, renderedDate)
                val habitCompletions = entryPoint.completionRepository()
                    .getCompletionsForHabit(habitId)
                    .first()
                widgetCompletionDecision(
                    renderedDate = renderedDate,
                    currentLogicalDate = currentDate,
                    alreadyLogged = existing != null,
                    habitExists = habit != null,
                    habitArchived = habit?.archived == true,
                    schedule = habit?.schedule,
                    startOfWeekDay = settings.startOfWeekDay,
                    habitCompletions = habitCompletions
                )
            },
            completionLogger = entryPoint.logCompletionUseCase(),
            widgetUpdater = entryPoint.widgetUpdater(),
            onFailure = { rowName, exception ->
                Log.e(TAG, "Widget action failed for row '$rowName'", exception)
            }
        )
    }
}
