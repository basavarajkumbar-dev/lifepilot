package com.lifepilot.app.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

enum class HabitType(val label: String) {
    /** Done / not done. */
    CHECK("Yes / No"),
    /** Numeric progress toward a target (e.g. 30 minutes, 8 glasses). Partial progress allowed. */
    MEASURABLE("Measurable"),
}

enum class HabitSchedule(val label: String) {
    DAILY("Every day"),
    WEEKDAYS("Specific days"),
    WEEKLY_TARGET("Times per week"),
}

data class Habit(
    val id: Long,
    val name: String,
    val description: String,
    val iconKey: String,
    val colorIndex: Int,
    val type: HabitType,
    /** Target per day; 1.0 for CHECK habits. */
    val target: Double,
    val unit: String,
    val schedule: HabitSchedule,
    val weekdays: Set<DayOfWeek>,
    val timesPerWeek: Int,
    val preferredMinute: Int?,
    val reminderEnabled: Boolean,
    val startDate: LocalDate,
    val createdAt: Instant,
    val archived: Boolean,
)

data class HabitLog(
    val id: Long,
    val habitId: Long,
    val date: LocalDate,
    val value: Double,
)

enum class StreakUnit { DAYS, WEEKS }

data class StreakInfo(val current: Int, val best: Int, val unit: StreakUnit)
