package com.habitat.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutSelectorTest {
    @Test
    fun `table driven size selection keeps the habit list at compact sizes`() {
        data class TestCase(val row: String, val size: WidgetSizeDp, val expected: WidgetLayout)

        val cases = listOf(
            TestCase("declared minimum size", WidgetSizeDp(130f, 130f), WidgetLayout.COMPACT_LIST),
            TestCase("shorter widget at medium width", WidgetSizeDp(250f, 260f), WidgetLayout.COMPACT_LIST),
            TestCase("narrow widget at medium height", WidgetSizeDp(220f, 380f), WidgetLayout.COMPACT_LIST),
            TestCase("medium widget", WidgetSizeDp(250f, 380f), WidgetLayout.STANDARD_LIST)
        )

        cases.forEach { case ->
            assertEquals(case.row, case.expected, WidgetLayoutSelector.choose(case.size))
        }
    }
}
