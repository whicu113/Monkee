package com.habitat.app.domain.logic

import com.habitat.app.domain.model.CompletionState
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRowActionsTest {

    private data class Case(
        val completionState: CompletionState?,
        val expected: WidgetRowActionState,
        val name: String
    )

    private val cases = listOf(
        Case(null, WidgetRowActionState.SHOW_BUTTONS, "pending row shows completion buttons"),
        Case(CompletionState.DONE, WidgetRowActionState.SHOW_DONE, "done row shows its state"),
        Case(CompletionState.MINIMUM, WidgetRowActionState.SHOW_MINIMUM, "minimum row shows its state"),
        Case(CompletionState.SKIPPED, WidgetRowActionState.SHOW_SKIPPED, "skipped row shows its state")
    )

    @Test
    fun `table driven row action decision handles pending and completed states`() {
        val failures = cases.mapNotNull { case ->
            val actual = rowActions(case.completionState)
            if (actual == case.expected) null else "${case.name}: expected ${case.expected}, got $actual"
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
