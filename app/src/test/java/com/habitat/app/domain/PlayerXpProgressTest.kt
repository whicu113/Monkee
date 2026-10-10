package com.habitat.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerXpProgressTest {

    @Test
    fun xpProgressMatchesLedgerTotalAcrossLevelBoundaries() {
        data class Row(
            val name: String,
            val totalXp: Long,
            val level: Long,
            val xpIntoLevel: Long,
            val xpRequired: Long
        )

        val rows = listOf(
            Row("zero XP", 0L, 1L, 0L, 100L),
            Row("99 XP before first boundary", 99L, 1L, 99L, 100L),
            Row("100 XP at level two", 100L, 2L, 0L, 125L),
            Row("225 XP at level three", 225L, 3L, 0L, 150L)
        )

        rows.forEach { row ->
            val progress = playerXpProgress(row.totalXp)
            assertEquals("${row.name}: level", row.level, progress.level)
            assertEquals("${row.name}: XP into level", row.xpIntoLevel, progress.xpIntoLevel)
            assertEquals("${row.name}: XP required", row.xpRequired, progress.xpRequiredForNextLevel)
            assertEquals(
                "${row.name}: progress fraction",
                row.xpIntoLevel.toFloat() / row.xpRequired,
                progress.fraction,
                0.0001f
            )
        }
    }
}
