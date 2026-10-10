package com.habitat.app.domain.logic

import java.time.Instant
import java.time.ZoneId

/**
 * Pure domain logic for calculating day boundary alarm instants.
 * SPEC Section 4.3 & 8.7.
 */
object DayBoundaryEngine {

    /**
     * Calculates the next [Instant] when [dayBoundaryHour] occurs in [zone] strictly after [now].
     *
     * - If [now] is before [dayBoundaryHour]:00 on its local date, returns today at [dayBoundaryHour]:00.
     * - If [now] is at or after [dayBoundaryHour]:00 on its local date, returns tomorrow at [dayBoundaryHour]:00.
     *
     * Exactly at the boundary picks the next day.
     */
    fun nextBoundaryInstant(
        now: Instant,
        zone: ZoneId,
        dayBoundaryHour: Int
    ): Instant {
        val zdt = now.atZone(zone)
        val todayBoundary = zdt.toLocalDate().atTime(dayBoundaryHour, 0).atZone(zone)
        val targetZdt = if (zdt.isBefore(todayBoundary)) {
            todayBoundary
        } else {
            zdt.toLocalDate().plusDays(1).atTime(dayBoundaryHour, 0).atZone(zone)
        }
        return targetZdt.toInstant()
    }
}
