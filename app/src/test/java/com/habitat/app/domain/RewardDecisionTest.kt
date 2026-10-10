package com.habitat.app.domain

import com.habitat.app.domain.logic.DropRarity
import com.habitat.app.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RewardDecisionTest {

    private val monday = LocalDate.of(2025, 5, 12)
    private val friday = monday.plusDays(4)

    @Test
    fun `eligibility table covers schedule date and weekly quota decisions`() {
        data class Row(
            val name: String,
            val schedule: ScheduleType,
            val date: LocalDate = monday,
            val today: LocalDate = monday,
            val created: LocalDate = monday.minusDays(20),
            val weekStart: DayOfWeek = DayOfWeek.MONDAY,
            val paidDates: Set<LocalDate> = emptySet(),
            val inheritedEligibility: Boolean = false,
            val expected: RewardNoRewardReason?
        )
        val rows = listOf(
            Row("daily today", ScheduleType.Daily, expected = null),
            Row("weekday scheduled today", ScheduleType.Weekdays(setOf(DayOfWeek.MONDAY)), expected = null),
            Row("weekday off day", ScheduleType.Weekdays(setOf(DayOfWeek.TUESDAY)), expected = RewardNoRewardReason.NOT_SCHEDULED),
            Row("every n anchor day", ScheduleType.EveryNDays(3, monday), expected = null),
            Row("every n off day", ScheduleType.EveryNDays(3, monday.minusDays(1)), expected = RewardNoRewardReason.NOT_SCHEDULED),
            Row("n per week available with zero paid dates", ScheduleType.NPerWeek(3), date = friday, today = friday, expected = null),
            Row("n per week available with one paid date", ScheduleType.NPerWeek(3), date = friday, today = friday, paidDates = setOf(monday), expected = null),
            Row("n per week available with two paid dates", ScheduleType.NPerWeek(3), date = friday, today = friday, paidDates = setOf(monday, monday.plusDays(1)), expected = null),
            Row("n per week quota reached with three paid dates", ScheduleType.NPerWeek(3), date = friday, today = friday, paidDates = setOf(monday, monday.plusDays(1), monday.plusDays(2)), expected = RewardNoRewardReason.WEEKLY_GOAL_REACHED),
            Row("paid dates from previous week ignored", ScheduleType.NPerWeek(1), paidDates = setOf(monday.minusDays(7)), expected = null),
            Row("monday start excludes prior sunday", ScheduleType.NPerWeek(1), paidDates = setOf(monday.minusDays(1)), expected = null),
            Row("monday start quota reached after monday payment", ScheduleType.NPerWeek(1), date = friday, today = friday, paidDates = setOf(monday), expected = RewardNoRewardReason.WEEKLY_GOAL_REACHED),
            Row("sunday start includes prior sunday", ScheduleType.NPerWeek(1), weekStart = DayOfWeek.SUNDAY, paidDates = setOf(monday.minusDays(1)), expected = RewardNoRewardReason.WEEKLY_GOAL_REACHED),
            Row("0200 at 0400 boundary uses previous logical saturday week", ScheduleType.NPerWeek(1), date = monday.minusDays(2), today = monday.minusDays(2), weekStart = DayOfWeek.SUNDAY, paidDates = setOf(monday.minusDays(3)), expected = RewardNoRewardReason.WEEKLY_GOAL_REACHED),
            Row("partial creation week uses effective target", ScheduleType.NPerWeek(2), date = monday.plusDays(5), today = monday.plusDays(5), created = monday.plusDays(3), paidDates = setOf(monday.plusDays(3), monday.plusDays(4)), expected = RewardNoRewardReason.WEEKLY_GOAL_REACHED),
            Row("unknown schedule has its own reason", ScheduleType.Unknown("v9:future"), expected = RewardNoRewardReason.UNKNOWN_SCHEDULE),
            Row("backfill does not earn", ScheduleType.Daily, date = monday.minusDays(1), expected = RewardNoRewardReason.BACKFILL),
            Row("stage upgrade inherits first grant eligibility", ScheduleType.Weekdays(setOf(DayOfWeek.TUESDAY)), inheritedEligibility = true, expected = null)
        )

        rows.forEach { row ->
            assertEquals(
                "${row.name}: no-reward reason",
                row.expected,
                evaluateRewardEligibility(
                    schedule = row.schedule,
                    logicalDate = row.date,
                    today = row.today,
                    habitCreatedDate = row.created,
                    startOfWeekDay = row.weekStart,
                    paidLogicalDatesInWeek = row.paidDates,
                    eligibilityAlreadyGranted = row.inheritedEligibility
                )
            )
        }
    }

    @Test
    fun `result table preserves granted values and enum reasons without copy`() {
        data class Row(val name: String, val reason: RewardNoRewardReason)
        val rows = RewardNoRewardReason.entries.map { Row("${it.name} reason", it) }
        rows.forEach { row ->
            val result = rewardResult(
                noRewardReason = row.reason,
                alreadyRewarded = row.reason == RewardNoRewardReason.ALREADY_REWARDED,
                seeds = 17,
                xp = 10,
                drop = DropRarity.RARE,
                comebackApplied = true,
                capApplied = 4
            )
            assertTrue("${row.name}: result is no reward", result is HabitRewardResult.NoReward)
            assertEquals("${row.name}: enum reason", row.reason, (result as HabitRewardResult.NoReward).reason)
        }

        val granted = rewardResult(
            noRewardReason = null,
            alreadyRewarded = false,
            seeds = 17,
            xp = 10,
            drop = DropRarity.RARE,
            comebackApplied = true,
            capApplied = 4
        ) as HabitRewardResult.Granted
        assertEquals("granted row: Seeds", 17, granted.seeds)
        assertEquals("granted row: XP", 10, granted.xp)
        assertEquals("granted row: drop", DropRarity.RARE, granted.drop)
        assertEquals("granted row: comeback", true, granted.comebackApplied)
        assertEquals("granted row: cap applied", 4, granted.capApplied)
    }
}
