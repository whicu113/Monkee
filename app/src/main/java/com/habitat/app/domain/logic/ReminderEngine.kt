package com.habitat.app.domain.logic

import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max

sealed interface ReminderAllocation {
    data class Individual(val habit: Habit) : ReminderAllocation
    data class Summary(val habits: List<Habit>) : ReminderAllocation
}

/**
 * Pure domain engine for calculation of next trigger times, quiet hours deferral,
 * post eligibility, and daily cap allocation.
 * SPEC Section 8.1.
 */
object ReminderEngine {

    /**
     * Determines whether a reminder should be posted for [habit] on [currentLogicalDate].
     * Suppresses if archived, unknown schedule, not scheduled today, already logged today, or weekly quota met.
     *
     * SPEC Section 8.1.
     */
    fun shouldPost(
        habit: Habit,
        isLoggedToday: Boolean,
        isWeeklyQuotaMet: Boolean,
        currentLogicalDate: LocalDate
    ): Boolean {
        if (habit.archived) return false
        if (habit.schedule is ScheduleType.Unknown) return false
        if (!ScheduleEvaluator.isScheduledOn(habit.schedule, currentLogicalDate)) return false
        if (isLoggedToday) return false
        if (isWeeklyQuotaMet) return false
        return true
    }

    /**
     * Calculates the next trigger [Instant] for a single [habit].
     * Handles quiet hours deferral and DST transitions.
     *
     * SPEC Section 8.1.
     */
    fun nextTrigger(
        habit: Habit,
        fromInstant: Instant,
        zoneId: ZoneId,
        dayBoundaryHour: Int = 4,
        quietHoursStart: LocalTime = LocalTime.of(22, 0),
        quietHoursEnd: LocalTime = LocalTime.of(7, 0)
    ): Instant? {
        val reminderTime = habit.reminderTime ?: return null
        if (habit.archived || habit.schedule is ScheduleType.Unknown) return null

        val currentZdt = fromInstant.atZone(zoneId)
        val targetDate = currentZdt.toLocalDate()

        for (i in 0..365) {
            val candidateDate = targetDate.plusDays(i.toLong())
            if (ScheduleEvaluator.isScheduledOn(habit.schedule, candidateDate)) {
                var localDateTime = LocalDateTime.of(candidateDate, reminderTime)

                // Adjust for quiet hours
                if (isInsideQuietHours(reminderTime, quietHoursStart, quietHoursEnd)) {
                    localDateTime = LocalDateTime.of(candidateDate, quietHoursEnd)
                    if (quietHoursStart > quietHoursEnd && reminderTime >= quietHoursStart) {
                        localDateTime = LocalDateTime.of(candidateDate.plusDays(1), quietHoursEnd)
                    }
                }

                val candidateZdt = localDateTime.atZone(zoneId)
                val candidateInstant = candidateZdt.toInstant()

                if (candidateInstant > fromInstant) {
                    return candidateInstant
                }
            }
        }
        return null
    }

    /**
     * Determines whether [time] falls inside quiet hours.
     */
    fun isInsideQuietHours(
        time: LocalTime,
        start: LocalTime,
        end: LocalTime
    ): Boolean {
        return if (start < end) {
            time >= start && time < end
        } else {
            // Spans midnight e.g. 22:00 to 07:00
            time >= start || time < end
        }
    }

    /**
     * Allocates reminders for [dueHabits] given [alreadyPostedCount] and a daily [cap].
     * Orders habits by reminderTime ASC, then sortOrder ASC.
     *
     * SPEC Section 8.1.
     */
    fun allocateDay(
        dueHabits: List<Habit>,
        alreadyPostedCount: Int = 0,
        cap: Int = 4
    ): List<ReminderAllocation> {
        if (dueHabits.isEmpty()) return emptyList()

        val sorted = dueHabits.sortedWith(
            compareBy<Habit> { it.reminderTime ?: LocalTime.MAX }.thenBy { it.sortOrder }
        )

        val totalToSchedule = sorted.size
        val availableCapacity = max(0, cap - alreadyPostedCount)

        if (totalToSchedule <= availableCapacity) {
            return sorted.map { ReminderAllocation.Individual(it) }
        }

        val individualSlots = max(0, availableCapacity - 1)
        val result = mutableListOf<ReminderAllocation>()

        val individualHabits = sorted.take(individualSlots)
        val overflowHabits = sorted.drop(individualSlots)

        individualHabits.forEach { result.add(ReminderAllocation.Individual(it)) }
        if (overflowHabits.isNotEmpty()) {
            result.add(ReminderAllocation.Summary(overflowHabits))
        }

        return result
    }
}
