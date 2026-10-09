package com.habitat.app.data.local.converters

import androidx.room.TypeConverter
import com.habitat.app.domain.model.Difficulty
import com.habitat.app.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class HabitConverters {

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }

    @TypeConverter
    fun fromDifficulty(value: Difficulty): String = value.name

    @TypeConverter
    fun toDifficulty(value: String): Difficulty = Difficulty.valueOf(value)

    @TypeConverter
    fun fromScheduleType(schedule: ScheduleType): String {
        return when (schedule) {
            is ScheduleType.Daily -> "DAILY"
            is ScheduleType.Weekdays -> "WEEKDAYS:" + schedule.days.joinToString(",") { it.name }
            is ScheduleType.EveryNDays -> "EVERY_N_DAYS:${schedule.n}:${schedule.startDate}"
            is ScheduleType.NPerWeek -> "N_PER_WEEK:${schedule.n}:${schedule.startOfWeekDay.name}"
        }
    }

    @TypeConverter
    fun toScheduleType(value: String): ScheduleType {
        val parts = value.split(":")
        return when (parts[0]) {
            "DAILY" -> ScheduleType.Daily
            "WEEKDAYS" -> {
                val days = if (parts.size > 1 && parts[1].isNotBlank()) {
                    parts[1].split(",").map { DayOfWeek.valueOf(it) }.toSet()
                } else emptySet()
                ScheduleType.Weekdays(days)
            }
            "EVERY_N_DAYS" -> {
                val n = parts.getOrNull(1)?.toIntOrNull() ?: 1
                val startDate = parts.getOrNull(2)?.let { LocalDate.parse(it) } ?: LocalDate.now()
                ScheduleType.EveryNDays(n, startDate)
            }
            "N_PER_WEEK" -> {
                val n = parts.getOrNull(1)?.toIntOrNull() ?: 1
                val startDay = parts.getOrNull(2)?.let { DayOfWeek.valueOf(it) } ?: DayOfWeek.MONDAY
                ScheduleType.NPerWeek(n, startDay)
            }
            else -> ScheduleType.Daily
        }
    }
}
