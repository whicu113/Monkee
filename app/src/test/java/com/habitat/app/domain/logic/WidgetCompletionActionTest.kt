package com.habitat.app.domain.logic

import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.usecase.WidgetCompletionLogger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WidgetCompletionActionTest {
    @Test
    fun `action table always refreshes and isolates database and updater failures by row`() = runTest {
        data class Case(
            val name: String,
            val decision: WidgetCompletionDecision = WidgetCompletionDecision.LOG_AND_REFRESH,
            val decisionFailure: Boolean = false,
            val databaseFailure: Boolean = false,
            val updaterFailure: Boolean = false,
            val expectedLogCalls: Int,
            val expectedFailures: Int
        )
        val cases = listOf(
            Case("daily active row logs and refreshes", expectedLogCalls = 1, expectedFailures = 0),
            Case("stale row only refreshes", WidgetCompletionDecision.REFRESH_ONLY, expectedLogCalls = 0, expectedFailures = 0),
            Case("database read failure still refreshes row", decisionFailure = true, expectedLogCalls = 0, expectedFailures = 1),
            Case("database failure still refreshes row", databaseFailure = true, expectedLogCalls = 1, expectedFailures = 1),
            Case("updater failure does not undo logging row", updaterFailure = true, expectedLogCalls = 1, expectedFailures = 1),
            Case("database and updater failures remain contained row", databaseFailure = true, updaterFailure = true, expectedLogCalls = 1, expectedFailures = 2)
        )

        for (row in cases) {
            var logCalls = 0
            var refreshCalls = 0
            val failures = mutableListOf<String>()
            val logger = WidgetCompletionLogger { _, _, _ ->
                logCalls++
                if (row.databaseFailure) error("database failed")
                true
            }
            val updater = WidgetUpdater {
                refreshCalls++
                if (row.updaterFailure) error("updater failed")
            }

            val thrown = runCatching {
                executeWidgetCompletionAction(
                    rowName = "Morning walk",
                    habitId = "walk-id",
                    state = CompletionState.DONE,
                    renderedDate = LocalDate.of(2026, 10, 10),
                    resolveDecision = {
                        if (row.decisionFailure) error("database read failed")
                        row.decision
                    },
                    completionLogger = logger,
                    widgetUpdater = updater,
                    onFailure = { failedRow, _ -> failures += failedRow }
                )
            }.exceptionOrNull()

            assertEquals("row '${row.name}' callback exception", null, thrown)
            assertEquals("row '${row.name}' log count", row.expectedLogCalls, logCalls)
            assertEquals("row '${row.name}' refresh count", 1, refreshCalls)
            assertEquals("row '${row.name}' failure count", row.expectedFailures, failures.size)
            assertEquals("row '${row.name}' failure label", List(row.expectedFailures) { "Morning walk" }, failures)
        }
    }
}
