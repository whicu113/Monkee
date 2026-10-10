package com.habitat.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Table-driven tests for [DayBoundaryEngine.nextBoundaryInstant].
 * SPEC Section 4.3 & 8.7.
 */
class DayBoundaryEngineTest {

    private val zoneBerlin = ZoneId.of("Europe/Berlin")
    private val zoneUtc = ZoneId.of("UTC")

    data class TestCase(
        val description: String,
        val nowIso: String,
        val zone: ZoneId,
        val boundaryHour: Int,
        val expectedBoundaryIso: String
    )

    private val testCases = listOf(
        // ── Default hour 4: Before, exactly at, and after boundary ────────────
        TestCase(
            description = "Before boundary at 03:30 in Berlin -> today 04:00",
            nowIso = "2025-05-12T03:30:00+02:00",
            zone = zoneBerlin,
            boundaryHour = 4,
            expectedBoundaryIso = "2025-05-12T04:00:00+02:00"
        ),
        TestCase(
            description = "Exactly at boundary at 04:00 in Berlin -> tomorrow 04:00 (must pick next day)",
            nowIso = "2025-05-12T04:00:00+02:00",
            zone = zoneBerlin,
            boundaryHour = 4,
            expectedBoundaryIso = "2025-05-13T04:00:00+02:00"
        ),
        TestCase(
            description = "After boundary at 04:30 in Berlin -> tomorrow 04:00",
            nowIso = "2025-05-12T04:30:00+02:00",
            zone = zoneBerlin,
            boundaryHour = 4,
            expectedBoundaryIso = "2025-05-13T04:00:00+02:00"
        ),

        // ── Custom hour 6: Before, exactly at, and after boundary ─────────────
        TestCase(
            description = "Before custom boundary at 05:30 in Berlin -> today 06:00",
            nowIso = "2025-05-12T05:30:00+02:00",
            zone = zoneBerlin,
            boundaryHour = 6,
            expectedBoundaryIso = "2025-05-12T06:00:00+02:00"
        ),
        TestCase(
            description = "Exactly at custom boundary at 06:00 in Berlin -> tomorrow 06:00 (must pick next day)",
            nowIso = "2025-05-12T06:00:00+02:00",
            zone = zoneBerlin,
            boundaryHour = 6,
            expectedBoundaryIso = "2025-05-13T06:00:00+02:00"
        ),
        TestCase(
            description = "After custom boundary at 06:30 in Berlin -> tomorrow 06:00",
            nowIso = "2025-05-12T06:30:00+02:00",
            zone = zoneBerlin,
            boundaryHour = 6,
            expectedBoundaryIso = "2025-05-13T06:00:00+02:00"
        ),

        // ── Europe/Berlin Spring-Forward Day (March 30, 2025: 02:00 -> 03:00 CEST) ──
        TestCase(
            description = "Spring forward: Before 04:00 boundary at 03:30 CEST -> today 04:00 CEST",
            nowIso = "2025-03-30T03:30:00+02:00",
            zone = zoneBerlin,
            boundaryHour = 4,
            expectedBoundaryIso = "2025-03-30T04:00:00+02:00"
        ),
        TestCase(
            description = "Spring forward: Exactly at 04:00 boundary at 04:00 CEST -> tomorrow 04:00 CEST",
            nowIso = "2025-03-30T04:00:00+02:00",
            zone = zoneBerlin,
            boundaryHour = 4,
            expectedBoundaryIso = "2025-03-31T04:00:00+02:00"
        ),
        TestCase(
            description = "Spring forward: After 04:00 boundary at 04:30 CEST -> tomorrow 04:00 CEST",
            nowIso = "2025-03-30T04:30:00+02:00",
            zone = zoneBerlin,
            boundaryHour = 4,
            expectedBoundaryIso = "2025-03-31T04:00:00+02:00"
        ),
        TestCase(
            description = "Spring forward: Custom hour 6 before boundary at 05:30 CEST -> today 06:00 CEST",
            nowIso = "2025-03-30T05:30:00+02:00",
            zone = zoneBerlin,
            boundaryHour = 6,
            expectedBoundaryIso = "2025-03-30T06:00:00+02:00"
        ),

        // ── Europe/Berlin Fall-Back Day (October 26, 2025: 03:00 -> 02:00 CET) ──────
        TestCase(
            description = "Fall back: Before 04:00 boundary at 03:30 CET -> today 04:00 CET",
            nowIso = "2025-10-26T03:30:00+01:00",
            zone = zoneBerlin,
            boundaryHour = 4,
            expectedBoundaryIso = "2025-10-26T04:00:00+01:00"
        ),
        TestCase(
            description = "Fall back: Exactly at 04:00 boundary at 04:00 CET -> tomorrow 04:00 CET",
            nowIso = "2025-10-26T04:00:00+01:00",
            zone = zoneBerlin,
            boundaryHour = 4,
            expectedBoundaryIso = "2025-10-27T04:00:00+01:00"
        ),
        TestCase(
            description = "Fall back: After 04:00 boundary at 04:30 CET -> tomorrow 04:00 CET",
            nowIso = "2025-10-26T04:30:00+01:00",
            zone = zoneBerlin,
            boundaryHour = 4,
            expectedBoundaryIso = "2025-10-27T04:00:00+01:00"
        ),
        TestCase(
            description = "Fall back: Custom hour 6 before boundary at 05:30 CET -> today 06:00 CET",
            nowIso = "2025-10-26T05:30:00+01:00",
            zone = zoneBerlin,
            boundaryHour = 6,
            expectedBoundaryIso = "2025-10-26T06:00:00+01:00"
        ),

        // ── UTC Zone sanity check ─────────────────────────────────────────────
        TestCase(
            description = "UTC before 04:00 boundary at 02:00Z -> today 04:00Z",
            nowIso = "2025-05-12T02:00:00Z",
            zone = zoneUtc,
            boundaryHour = 4,
            expectedBoundaryIso = "2025-05-12T04:00:00Z"
        ),
        TestCase(
            description = "UTC exactly at 04:00 boundary -> tomorrow 04:00Z",
            nowIso = "2025-05-12T04:00:00Z",
            zone = zoneUtc,
            boundaryHour = 4,
            expectedBoundaryIso = "2025-05-13T04:00:00Z"
        )
    )

    @Test
    fun `table-driven nextBoundaryInstant tests verify all boundary conditions`() {
        for (case in testCases) {
            val nowInstant = ZonedDateTime.parse(case.nowIso).toInstant()
            val expectedInstant = ZonedDateTime.parse(case.expectedBoundaryIso).toInstant()
            val actual = DayBoundaryEngine.nextBoundaryInstant(
                now = nowInstant,
                zone = case.zone,
                dayBoundaryHour = case.boundaryHour
            )
            assertEquals(case.description, expectedInstant, actual)
        }
    }

    @Test
    fun `before boundary returns today's boundary instant`() {
        val now = ZonedDateTime.parse("2025-05-12T02:00:00+02:00").toInstant()
        val expected = ZonedDateTime.parse("2025-05-12T04:00:00+02:00").toInstant()
        val actual = DayBoundaryEngine.nextBoundaryInstant(now, zoneBerlin, 4)
        assertEquals(expected, actual)
    }

    @Test
    fun `after boundary returns tomorrow's boundary instant`() {
        val now = ZonedDateTime.parse("2025-05-12T05:00:00+02:00").toInstant()
        val expected = ZonedDateTime.parse("2025-05-13T04:00:00+02:00").toInstant()
        val actual = DayBoundaryEngine.nextBoundaryInstant(now, zoneBerlin, 4)
        assertEquals(expected, actual)
    }

    @Test
    fun `exactly at boundary returns tomorrow's boundary instant`() {
        val now = ZonedDateTime.parse("2025-05-12T04:00:00+02:00").toInstant()
        val expected = ZonedDateTime.parse("2025-05-13T04:00:00+02:00").toInstant()
        val actual = DayBoundaryEngine.nextBoundaryInstant(now, zoneBerlin, 4)
        assertEquals(expected, actual)
    }

    @Test
    fun `custom boundary hour 6 picks 0600 correctly`() {
        val now = ZonedDateTime.parse("2025-05-12T05:00:00+02:00").toInstant()
        val expected = ZonedDateTime.parse("2025-05-12T06:00:00+02:00").toInstant()
        val actual = DayBoundaryEngine.nextBoundaryInstant(now, zoneBerlin, 6)
        assertEquals(expected, actual)
    }

    @Test
    fun `Europe Berlin spring forward instant is exact UTC`() {
        // March 30, 2025: 04:00 CEST is 02:00 UTC
        val now = Instant.parse("2025-03-30T01:30:00Z") // 03:30 CEST
        val expected = Instant.parse("2025-03-30T02:00:00Z") // 04:00 CEST
        val actual = DayBoundaryEngine.nextBoundaryInstant(now, zoneBerlin, 4)
        assertEquals(expected, actual)
    }

    @Test
    fun `Europe Berlin fall back instant is exact UTC`() {
        // October 26, 2025: 04:00 CET is 03:00 UTC
        val now = Instant.parse("2025-10-26T02:30:00Z") // 03:30 CET
        val expected = Instant.parse("2025-10-26T03:00:00Z") // 04:00 CET
        val actual = DayBoundaryEngine.nextBoundaryInstant(now, zoneBerlin, 4)
        assertEquals(expected, actual)
    }
}
