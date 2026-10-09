package com.example.monkee.data

import com.example.monkee.data.local.converters.HabitConverters
import com.example.monkee.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitConvertersTest {

    private val converters = HabitConverters()

    @Test
    fun `scheduleType daily conversion round trip`() {
        val schedule = ScheduleType.Daily
        val stringVal = converters.fromScheduleType(schedule)
        val restored = converters.toScheduleType(stringVal)
        assertEquals(schedule, restored)
    }

    @Test
    fun `scheduleType weekdays conversion round trip`() {
        val schedule = ScheduleType.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        val stringVal = converters.fromScheduleType(schedule)
        val restored = converters.toScheduleType(stringVal)
        assertEquals(schedule, restored)
    }

    @Test
    fun `scheduleType everyNDays conversion round trip`() {
        val schedule = ScheduleType.EveryNDays(3, LocalDate.of(2025, 5, 10))
        val stringVal = converters.fromScheduleType(schedule)
        val restored = converters.toScheduleType(stringVal)
        assertEquals(schedule, restored)
    }

    @Test
    fun `scheduleType nPerWeek conversion round trip`() {
        val schedule = ScheduleType.NPerWeek(4, DayOfWeek.SUNDAY)
        val stringVal = converters.fromScheduleType(schedule)
        val restored = converters.toScheduleType(stringVal)
        assertEquals(schedule, restored)
    }
}
