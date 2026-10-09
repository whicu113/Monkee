package com.habitat.app.data.local.converters

import androidx.room.TypeConverter
import com.habitat.app.domain.model.CompletionState
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
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }

    @TypeConverter
    fun fromDifficulty(value: Difficulty): String = value.name

    @TypeConverter
    fun toDifficulty(value: String): Difficulty = Difficulty.valueOf(value)

    @TypeConverter
    fun fromCompletionState(value: CompletionState): String = value.name

    @TypeConverter
    fun toCompletionState(value: String): CompletionState = CompletionState.valueOf(value)

    @TypeConverter
    fun fromScheduleType(schedule: ScheduleType): String {
        return when (schedule) {
            is ScheduleType.Daily -> "v1:DAILY"
            is ScheduleType.Weekdays -> "v1:WEEKDAYS:" + schedule.days.joinToString(",") { it.name }
            is ScheduleType.EveryNDays -> "v1:EVERY_N_DAYS:${schedule.n}:${schedule.startDate}"
            is ScheduleType.NPerWeek -> "v1:N_PER_WEEK:${schedule.n}"
            is ScheduleType.Unknown -> schedule.rawString
        }
    }

    @TypeConverter
    fun toScheduleType(value: String): ScheduleType {
        if (value.isBlank()) return ScheduleType.Daily

        if (value.startsWith("v1:")) {
            val cleanValue = value.removePrefix("v1:")
            val parts = cleanValue.split(":")
            return try {
                when (parts[0]) {
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
                        ScheduleType.NPerWeek(n)
                    }
                    else -> ScheduleType.Unknown(value)
                }
            } catch (_: Exception) {
                ScheduleType.Unknown(value)
            }
        }

        if (value.contains(":") && value.substringBefore(":").matches(Regex("v\\d+"))) {
            return ScheduleType.Unknown(value)
        }

        // Unversioned legacy strings
        val parts = value.split(":")
        return try {
            when (parts[0]) {
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
                    ScheduleType.NPerWeek(n)
                }
                else -> ScheduleType.Unknown(value)
            }
        } catch (_: Exception) {
            ScheduleType.Unknown(value)
        }
    }
}
