package com.lifepilot.app.data

import com.lifepilot.app.data.backup.BackupFormatException
import com.lifepilot.app.data.backup.BackupJson
import com.lifepilot.app.domain.backup.BackupData
import com.lifepilot.app.domain.backup.BackupValidator
import com.lifepilot.app.domain.backup.CategoryDto
import com.lifepilot.app.domain.backup.DayPlanDto
import com.lifepilot.app.domain.backup.HabitDto
import com.lifepilot.app.domain.backup.HabitLogDto
import com.lifepilot.app.domain.backup.OccurrenceDto
import com.lifepilot.app.domain.backup.ReminderLogDto
import com.lifepilot.app.domain.backup.TaskDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs on the JVM with the real org.json implementation (test dependency). */
class BackupJsonTest {

    private val sample = BackupData(
        schemaVersion = 1, exportedAtMillis = 1_793_000_000_000, appVersion = "1.0.0",
        categories = listOf(CategoryDto(1, "Work", 0, "work", true, 0)),
        tasks = listOf(
            TaskDto(
                1, "Pay rent, \"now\"", "multi\nline", 1, "HIGH", listOf("home", "money"), 15, "2026-10-25", 540, 600,
                true, 10, true, 15, 5, true, false, 120, true, 1320, 420,
                "MONTHLY", 1, emptyList(), listOf(5, -1), "2026-10-25", null, "KEEP_OVERDUE", 1L, 2L,
            ),
            TaskDto(2, "Read", "", null, "LOW", emptyList(), null, null, null, null, false, 0, true, 15, null, true, true, null, true, null, null,
                null, null, emptyList(), emptyList(), null, null, null, 3L, 4L),
        ),
        occurrences = listOf(OccurrenceDto(1, 1, "2026-11-05", "2026-11-06", 600, "COMPLETED", 5L, null, 4L, 2, "2026-11-06", 3, 1, 1, 1L, 2L)),
        habits = listOf(HabitDto(1, "Walk", "", "walk", 2, "MEASURABLE", 30.5, "min", "WEEKDAYS", listOf(1, 3, 5), 3, 420, true, "2026-10-01", 1L, false)),
        habitLogs = listOf(HabitLogDto(1, 1, "2026-10-24", 12.5)),
        reminderLogs = listOf(ReminderLogDto(1, 1, 1, "FIRED", 9L, null)),
        dayPlans = listOf(DayPlanDto("2026-10-25", "Ship it", listOf(1, 2))),
    )

    @Test fun roundTripPreservesEverything() {
        val decoded = BackupJson.decode(BackupJson.encode(sample))
        assertEquals(sample, decoded)
        assertTrue(BackupValidator.validate(decoded).isValid)
    }

    @Test(expected = BackupFormatException::class)
    fun rejectsForeignJson() { BackupJson.decode("""{"hello":"world"}""") }

    @Test(expected = BackupFormatException::class)
    fun rejectsTruncatedFile() { BackupJson.decode(BackupJson.encode(sample).dropLast(40)) }

    @Test(expected = BackupFormatException::class)
    fun rejectsWrongTypes() {
        BackupJson.decode("""{"format":"lifepilot-backup","schemaVersion":"one","tasks":[]}""")
    }
}
