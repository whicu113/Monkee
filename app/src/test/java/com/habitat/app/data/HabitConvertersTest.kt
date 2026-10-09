package com.habitat.app.data

import com.habitat.app.data.local.converters.HabitConverters
import com.habitat.app.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitConvertersTest {

    private val converters = HabitConverters()

    @Test
    fun `versioned v1 scheduleType daily conversion round trip`() {
        val schedule = ScheduleType.Daily
        val stringVal = converters.fromScheduleType(schedule)
        assertEquals("v1:DAILY", stringVal)
        val restored = converters.toScheduleType(stringVal)
        assertEquals(schedule, restored)
    }

    @Test
    fun `versioned v1 scheduleType weekdays conversion round trip`() {
        val schedule = ScheduleType.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        val stringVal = converters.fromScheduleType(schedule)
        assertEquals("v1:WEEKDAYS:MONDAY,FRIDAY", stringVal)
        val restored = converters.toScheduleType(stringVal)
        assertEquals(schedule, restored)
    }

    @Test
    fun `versioned v1 scheduleType everyNDays conversion round trip`() {
        val schedule = ScheduleType.EveryNDays(3, LocalDate.of(2025, 5, 10))
        val stringVal = converters.fromScheduleType(schedule)
        assertEquals("v1:EVERY_N_DAYS:3:2025-05-10", stringVal)
        val restored = converters.toScheduleType(stringVal)
        assertEquals(schedule, restored)
    }

    @Test
    fun `versioned v1 scheduleType nPerWeek conversion round trip`() {
        val schedule = ScheduleType.NPerWeek(4)
        val stringVal = converters.fromScheduleType(schedule)
        assertEquals("v1:N_PER_WEEK:4", stringVal)
        val restored = converters.toScheduleType(stringVal)
        assertEquals(schedule, restored)
    }

    @Test
    fun `legacy unversioned strings load correctly`() {
        assertEquals(ScheduleType.Daily, converters.toScheduleType("DAILY"))

        val expectedWeekdays = ScheduleType.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertEquals(expectedWeekdays, converters.toScheduleType("WEEKDAYS:MONDAY,WEDNESDAY"))

        val expectedEveryN = ScheduleType.EveryNDays(4, LocalDate.of(2025, 1, 1))
        assertEquals(expectedEveryN, converters.toScheduleType("EVERY_N_DAYS:4:2025-01-01"))

        val expectedNPerWeek = ScheduleType.NPerWeek(3)
        assertEquals(expectedNPerWeek, converters.toScheduleType("N_PER_WEEK:3:MONDAY"))
    }

    @Test
    fun `unknown future versions safely convert to Unknown and preserve raw string`() {
        val rawV2 = "v2:SUPER_SCHEDULE_TYPE:1:2:3"
        val restoredV2 = converters.toScheduleType(rawV2)
        assertEquals(ScheduleType.Unknown(rawV2), restoredV2)
        assertEquals(rawV2, converters.fromScheduleType(restoredV2))

        val rawV99 = "v99:UNKNOWN"
        val restoredV99 = converters.toScheduleType(rawV99)
        assertEquals(ScheduleType.Unknown(rawV99), restoredV99)
        assertEquals(rawV99, converters.fromScheduleType(restoredV99))
    }
}
