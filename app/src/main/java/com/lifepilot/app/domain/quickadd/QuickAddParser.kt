package com.lifepilot.app.domain.quickadd

import com.lifepilot.app.domain.model.Priority
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

data class QuickAddResult(
    val title: String,
    val date: LocalDate?,
    val minute: Int?,
    val priority: Priority?,
    /** Human-readable list of what was recognised, shown as chips so the user can verify it. */
    val recognized: List<String>,
)

/**
 * Deliberately conservative natural-language parser for quick capture. It only recognises
 * unambiguous phrases at the END of the text (plus explicit "!priority" markers anywhere), so a
 * title like "Plan monday meeting" or "Read today's news" is left untouched. Ambiguous input such
 * as "at 5" (am or pm?) is not interpreted. Anything not recognised stays in the title, and the
 * user can always adjust with the structured pickers.
 *
 * Supported: today, tonight, tomorrow/tmrw, weekday names (next occurrence after today),
 * "next week", "in N min/minutes/hours/days", "at 6pm", "at 6:30 pm", "at 18:30", "6pm",
 * "!low !medium !high !critical" (or !1–!4).
 */
object QuickAddParser {

    private val weekdays = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )

    private val priorityRegex = Regex("""(?i)(?:^|\s)!(low|medium|med|high|critical|urgent|[1-4])(?=\s|$)""")
    private val ampmTime = Regex("""(?i)\s+(?:at\s+)?(1[0-2]|0?[1-9])(?::([0-5]\d))?\s*(am|pm)$""")
    private val clockTime = Regex("""(?i)\s+at\s+([01]?\d|2[0-3]):([0-5]\d)$""")
    private val relative = Regex("""(?i)\s+in\s+(\d{1,3})\s*(m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days)$""")
    private val dayWord = Regex("""(?i)\s+(?:on\s+)?(today|tonight|tomorrow|tmrw|next week|monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thurs|thu|friday|fri|saturday|sat|sunday|sun)$""")

    fun parse(input: String, now: LocalDateTime): QuickAddResult {
        val recognized = mutableListOf<String>()
        var text = " " + input.trim()
        var priority: Priority? = null
        var date: LocalDate? = null
        var minute: Int? = null

        priorityRegex.find(text)?.let { m ->
            priority = when (m.groupValues[1].lowercase()) {
                "low", "1" -> Priority.LOW
                "medium", "med", "2" -> Priority.MEDIUM
                "high", "3" -> Priority.HIGH
                else -> Priority.CRITICAL
            }
            recognized += "${priority!!.label} priority"
            text = text.removeRange(m.range).let { " " + it.trim() }
        }

        // Peel recognised phrases off the end; at most one time and one date.
        repeat(3) {
            if (minute == null && date == null) {
                relative.find(text)?.let { m ->
                    val n = m.groupValues[1].toLong()
                    val unit = m.groupValues[2].lowercase()
                    val target = when {
                        unit.startsWith("m") -> now.plusMinutes(n)
                        unit.startsWith("h") -> now.plusHours(n)
                        else -> null
                    }
                    if (target != null) {
                        date = target.toLocalDate(); minute = target.hour * 60 + target.minute
                    } else {
                        date = now.toLocalDate().plusDays(n)
                    }
                    recognized += "in $n ${unit}"
                    text = text.removeRange(m.range)
                    return@repeat
                }
            }
            if (minute == null) {
                ampmTime.find(text)?.let { m ->
                    var h = m.groupValues[1].toInt() % 12
                    if (m.groupValues[3].lowercase() == "pm") h += 12
                    val min = m.groupValues[2].ifEmpty { "0" }.toInt()
                    minute = h * 60 + min
                    recognized += formatMinute(minute!!)
                    text = text.removeRange(m.range)
                    return@repeat
                }
                clockTime.find(text)?.let { m ->
                    minute = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                    recognized += formatMinute(minute!!)
                    text = text.removeRange(m.range)
                    return@repeat
                }
            }
            if (date == null) {
                dayWord.find(text)?.let { m ->
                    val word = m.groupValues[1].lowercase()
                    val today = now.toLocalDate()
                    date = when (word) {
                        "today" -> today
                        "tonight" -> { if (minute == null) minute = 20 * 60; today }
                        "tomorrow", "tmrw" -> today.plusDays(1)
                        "next week" -> today.plusWeeks(1)
                        else -> {
                            val dow = weekdays.getValue(word)
                            var d = today.plusDays(1)
                            while (d.dayOfWeek != dow) d = d.plusDays(1)
                            d
                        }
                    }
                    recognized += word
                    text = text.removeRange(m.range)
                    return@repeat
                }
            }
        }

        val title = text.trim()
        if (title.isEmpty()) {
            // Never let the parser eat the whole input.
            return QuickAddResult(input.trim(), null, null, null, emptyList())
        }
        // A time without a date means today, or tomorrow if that time has already passed.
        if (minute != null && date == null) {
            date = if (minute!! > now.hour * 60 + now.minute) now.toLocalDate() else now.toLocalDate().plusDays(1)
        }
        return QuickAddResult(title, date, minute, priority, recognized)
    }

    private fun formatMinute(m: Int): String = "%02d:%02d".format(m / 60, m % 60)
}
