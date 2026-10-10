package com.habitat.app.platform.widget

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.DpSize
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
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.logic.WidgetCompletionDecision
import com.habitat.app.domain.logic.executeWidgetCompletionAction
import com.habitat.app.domain.logic.WidgetDisplayState
import com.habitat.app.domain.logic.WidgetHabitRow
import com.habitat.app.domain.logic.WidgetProgressRingMapper
import com.habitat.app.domain.logic.WidgetRowActionState
import com.habitat.app.domain.logic.WidgetStateBuilder
import com.habitat.app.domain.logic.widgetCompletionDecision
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.logic.rowActions
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
        val snapshot = try {
            withContext(Dispatchers.IO) {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    HabitWidgetEntryPoint::class.java
                )
                val habits = entryPoint.habitRepository().getAllHabits().first()
                val completions = entryPoint.completionRepository().getAllCompletions().first()
                val settings = entryPoint.settingsRepository().getSettings().first()

                val sourceClock = entryPoint.appClock()
                val renderInstant = sourceClock.instant()
                val renderZone = sourceClock.zoneId()
                val clock = object : AppClock {
                    override fun instant(): Instant = renderInstant
                    override fun zoneId(): ZoneId = renderZone
                }
                val renderedDate = clock.logicalDate(settings.dayBoundaryHour)
                WidgetSnapshot(
                    state = WidgetStateBuilder.build(
                        clock = clock,
                        habits = habits,
                        completions = completions,
                        settings = settings
                    ),
                    renderedDate = renderedDate
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to load today's widget state", exception)
            null
        }

        provideContent {
            if (snapshot == null) {
                WidgetFallback(
                    title = "Habitat couldn't load your habits",
                    detail = "Tap to open Habitat and try again."
                )
            } else {
                WidgetContent(snapshot.state, snapshot.renderedDate)
            }
        }
    }
}

private data class WidgetSnapshot(val state: WidgetDisplayState, val renderedDate: LocalDate)

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun WidgetContent(state: WidgetDisplayState, renderedDate: LocalDate) {
    val size = LocalSize.current
    val compact = size.width < MEDIUM_WIDGET_SIZE.width || size.height < MEDIUM_WIDGET_SIZE.height
    val cardModifier = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(R.color.widget_surface))
        .padding(if (compact) 8.dp else 12.dp)

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

        WidgetDisplayState.AllSkipped -> WidgetStatusCard(
            title = "All skipped today",
            detail = "You're ready when you are.",
            modifier = cardModifier
        )

        is WidgetDisplayState.Habits -> HabitsCard(state, compact, cardModifier, renderedDate)
    }
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun HabitsCard(
    state: WidgetDisplayState.Habits,
    compact: Boolean,
    modifier: GlanceModifier,
    renderedDate: LocalDate
) {
    val ringIndex = requireNotNull(WidgetProgressRingMapper.ringIndex(state))
    val ringDescription = "Today's progress: ${state.completedCount} of ${state.totalDueCount} done"
    val visibleRows = if (compact) state.rows.take(1) else state.rows
    val moreCount = state.remainingCount + (state.rows.size - visibleRows.size)

    Column(
        modifier = modifier.semantics { contentDescription = ringDescription },
        verticalAlignment = Alignment.Vertical.Top
    ) {
        if (compact) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                ProgressRing(ringIndex, ringDescription, 42)
                Column(
                    modifier = GlanceModifier.defaultWeight().padding(start = 8.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    val firstRow = visibleRows.firstOrNull()
                    if (firstRow != null) {
                        Text(
                            text = "${firstRow.emoji} ${firstRow.name}",
                            style = primaryStyle(13)
                        )
                    }
                    Text(
                        text = "${state.completedCount} of ${state.totalDueCount} done",
                        style = secondaryStyle(11)
                    )
                }
            }
            visibleRows.firstOrNull()?.let { row ->
                CompactHabitEntry(row, renderedDate)
            }
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(60.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                ProgressRing(ringIndex, ringDescription, 52)
                Column(
                    modifier = GlanceModifier.defaultWeight().padding(start = 10.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text("Today's progress", style = primaryStyle(16))
                    Text(
                        text = "${state.completedCount} of ${state.totalDueCount} done",
                        style = secondaryStyle(13)
                    )
                }
            }
            visibleRows.forEach { HabitRow(it, renderedDate) }
        }

        if (moreCount > 0) {
            Text(
                text = "+$moreCount more",
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(actionStartActivity<MainActivity>())
                    .semantics { contentDescription = "Open Habitat to see $moreCount more habits" },
                style = secondaryStyle(13)
            )
        }
    }
}

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
private fun CompactActions(row: WidgetHabitRow, renderedDate: LocalDate) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(48.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        VisualAction(row, CompletionState.DONE, renderedDate)
        Spacer(GlanceModifier.width(8.dp))
        VisualAction(row, CompletionState.MINIMUM, renderedDate)
    }
}

@androidx.glance.GlanceComposable
@androidx.compose.runtime.Composable
private fun CompactHabitEntry(row: WidgetHabitRow, renderedDate: LocalDate) {
    when (rowActions(row.completionState)) {
        WidgetRowActionState.SHOW_BUTTONS -> CompactActions(row, renderedDate)
        else -> Row(
            modifier = GlanceModifier.fillMaxWidth().height(48.dp),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "✓ ${rowStateLabel(row.completionState)}",
                modifier = GlanceModifier.semantics {
                    contentDescription = habitStateDescription(row)
                },
                style = primaryStyle(14)
            )
        }
    }
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
