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

        val failures = mutableListOf<String>()
        rows.forEach { row ->
            val progress = playerXpProgress(row.totalXp)
            if (row.level != progress.level) {
                failures += "${row.name}: level expected:<${row.level}> but was:<${progress.level}>"
            }
            if (row.xpIntoLevel != progress.xpIntoLevel) {
                failures += "${row.name}: XP into level expected:<${row.xpIntoLevel}> but was:<${progress.xpIntoLevel}>"
            }
            if (row.xpRequired != progress.xpRequiredForNextLevel) {
                failures += "${row.name}: XP required expected:<${row.xpRequired}> but was:<${progress.xpRequiredForNextLevel}>"
            }
            val expectedFraction = row.xpIntoLevel.toFloat() / row.xpRequired
            if (kotlin.math.abs(expectedFraction - progress.fraction) > 0.0001f) {
                failures += "${row.name}: progress fraction expected:<$expectedFraction> but was:<${progress.fraction}>"
            }
        }
        assertEquals("all XP progress rows", emptyList<String>(), failures)
    }
}
