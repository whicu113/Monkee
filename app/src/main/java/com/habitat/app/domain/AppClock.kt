package com.habitat.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Clock interface providing controlled access to time for domain logic and testability.
 * SPEC Section 4.3 & CLAUDE.md Architecture Rules.
 */
interface AppClock {
    fun instant(): Instant
    fun zoneId(): ZoneId

    fun zonedDateTime(): ZonedDateTime = ZonedDateTime.ofInstant(instant(), zoneId())
    fun localDate(): LocalDate = zonedDateTime().toLocalDate()

    /**
     * Calculates the logical date based on the user's day boundary hour.
     * SPEC Section 4.3.
     */
    fun logicalDate(dayBoundaryHour: Int = GameConfig.DEFAULT_DAY_BOUNDARY_HOUR): LocalDate {
        val now = zonedDateTime()
        return if (now.hour < dayBoundaryHour) {
            now.toLocalDate().minusDays(1)
        } else {
            now.toLocalDate()
        }
    }
}

/**
 * Default implementation of [AppClock] using system clock with debug shift support.
 */
class SystemAppClock(
    private val zoneSupplier: () -> ZoneId = { ZoneId.systemDefault() }
) : AppClock {

    private var shiftedDays: Long = 0
    private var shiftedHours: Long = 0

    override fun instant(): Instant {
        return Instant.now()
            .plusSeconds(shiftedDays * 86400)
            .plusSeconds(shiftedHours * 3600)
    }

    override fun zoneId(): ZoneId = zoneSupplier()

    fun shiftTime(days: Long, hours: Long) {
        shiftedDays += days
        shiftedHours += hours
    }

    fun resetShift() {
        shiftedDays = 0
        shiftedHours = 0
    }
}
