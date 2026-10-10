package com.habitat.app.domain.logic

import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.usecase.WidgetCompletionLogger
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

/** Runs a widget action and always requests a database-backed widget refresh afterward. */
suspend fun executeWidgetCompletionAction(
    rowName: String,
    habitId: String,
    state: CompletionState,
    renderedDate: LocalDate,
    resolveDecision: suspend () -> WidgetCompletionDecision,
    completionLogger: WidgetCompletionLogger,
    widgetUpdater: WidgetUpdater,
    onFailure: (rowName: String, exception: Exception) -> Unit
) {
    try {
        if (resolveDecision() == WidgetCompletionDecision.LOG_AND_REFRESH) {
            completionLogger.executeLog(habitId, state, renderedDate)
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        safelyReport(onFailure, rowName, exception)
    } finally {
        try {
            widgetUpdater.updateWidget()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            safelyReport(onFailure, rowName, exception)
        }
    }
}

private fun safelyReport(
    onFailure: (String, Exception) -> Unit,
    rowName: String,
    exception: Exception
) {
    try {
        onFailure(rowName, exception)
    } catch (_: Exception) {
        // Error reporting must not turn a handled widget failure into a callback crash.
    }
}
