package com.habitat.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerLevelTest {

    @Test
    fun `level table follows cumulative XP thresholds`() {
        data class Row(val name: String, val xpTotal: Long, val expectedLevel: Long)
        val rows = listOf(
            Row("zero XP starts at level one", 0L, 1L),
            Row("99 XP remains level one", 99L, 1L),
            Row("100 XP reaches level two", 100L, 2L),
            Row("224 XP remains level two", 224L, 2L),
            Row("225 XP reaches level three", 225L, 3L),
            Row("374 XP remains level three", 374L, 3L),
            Row("375 XP reaches level four", 375L, 4L),
            Row("large XP total stays in range", 1_000_000_000_000L, 282_840L)
        )

        rows.forEach { row ->
            assertEquals("${row.name}: level", row.expectedLevel, level(row.xpTotal))
        }
    }
}
