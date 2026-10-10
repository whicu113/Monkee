package com.habitat.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WidgetCompletionDecisionTest {
    private val today = LocalDate.of(2026, 10, 10)

    @Test
    fun `decision table names each row`() {
        data class Case(
            val name: String,
            val renderedDate: LocalDate = today,
            val currentDate: LocalDate = today,
            val alreadyLogged: Boolean = false,
            val habitExists: Boolean = true,
            val archived: Boolean = false,
            val unknown: Boolean = false,
            val expected: WidgetCompletionDecision
        )
        val cases = listOf(
            Case("matching date and pending active habit", expected = WidgetCompletionDecision.LOG_AND_REFRESH),
            Case("rendered date rolled over", renderedDate = today.minusDays(1), expected = WidgetCompletionDecision.REFRESH_ONLY),
            Case("habit already logged", alreadyLogged = true, expected = WidgetCompletionDecision.REFRESH_ONLY),
            Case("habit archived", archived = true, expected = WidgetCompletionDecision.REFRESH_ONLY),
            Case("unknown schedule", unknown = true, expected = WidgetCompletionDecision.REFRESH_ONLY),
            Case("habit missing", habitExists = false, expected = WidgetCompletionDecision.REFRESH_ONLY)
        )

        val failures = cases.mapNotNull { row ->
            val actual = widgetCompletionDecision(
                row.renderedDate, row.currentDate, row.alreadyLogged,
                row.habitExists, row.archived, row.unknown
            )
            if (actual == row.expected) null else "row '${row.name}': expected ${row.expected}, got $actual"
        }
        assertEquals("Decision rows failed: ${failures.joinToString()}", emptyList<String>(), failures)
    }
}
