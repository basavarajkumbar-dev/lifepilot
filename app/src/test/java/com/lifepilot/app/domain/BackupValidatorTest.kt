package com.lifepilot.app.domain

import com.lifepilot.app.domain.backup.BackupData
import com.lifepilot.app.domain.backup.BackupValidator
import com.lifepilot.app.domain.backup.CategoryDto
import com.lifepilot.app.domain.backup.CsvExporter
import com.lifepilot.app.domain.backup.HabitDto
import com.lifepilot.app.domain.backup.HabitLogDto
import com.lifepilot.app.domain.backup.OccurrenceDto
import com.lifepilot.app.domain.backup.TaskDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorTest {

    private fun task(id: Long = 1, title: String = "Task", priority: String = "HIGH", categoryId: Long? = 1, interval: Int = 15, freq: String? = null) = TaskDto(
        id, title, "", categoryId, priority, listOf("a"), null, null, null, 540, true, 0, true, interval, null, true, true, null, true, null, null,
        freq, if (freq != null) 1 else null, emptyList(), emptyList(), if (freq != null) "2026-10-25" else null, null, if (freq != null) "MARK_MISSED" else null, 0, 0,
    )
    private fun occ(id: Long = 1, taskId: Long = 1, date: String = "2026-10-25", status: String = "PENDING") =
        OccurrenceDto(id, taskId, date, date, 540, status, null, null, null, 0, null, 0, 0, 0, 0, 0)
    private fun habit(id: Long = 1, target: Double = 1.0) = HabitDto(id, "Read", "", "book", 0, "CHECK", target, "", "DAILY", emptyList(), 3, 420, true, "2026-10-01", 0, false)

    private fun backup(
        version: Int = 1,
        tasks: List<TaskDto> = listOf(task()),
        occs: List<OccurrenceDto> = listOf(occ()),
        habits: List<HabitDto> = listOf(habit()),
        logs: List<HabitLogDto> = listOf(HabitLogDto(1, 1, "2026-10-24", 1.0)),
    ) = BackupData(version, 0, "1.0.0", listOf(CategoryDto(1, "Work", 0, "work", true, 0)), tasks, occs, habits, logs, emptyList(), emptyList())

    @Test fun validBackupPasses() {
        val r = BackupValidator.validate(backup(tasks = listOf(task(), task(id = 2, freq = "DAILY")), occs = listOf(occ(), occ(id = 2, taskId = 2))))
        assertTrue(r.errors.toString(), r.isValid)
    }

    @Test fun rejectsUnsupportedVersion() {
        assertFalse(BackupValidator.validate(backup(version = 99)).isValid)
        assertFalse(BackupValidator.validate(backup(version = 0)).isValid)
    }

    @Test fun rejectsBrokenReferencesAndDuplicates() {
        assertFalse(BackupValidator.validate(backup(occs = listOf(occ(taskId = 42)))).isValid)
        assertFalse(BackupValidator.validate(backup(tasks = listOf(task(), task()))).isValid)
        assertFalse(BackupValidator.validate(backup(occs = listOf(occ(1), occ(2)))).isValid) // same task+date
        assertFalse(BackupValidator.validate(backup(logs = listOf(HabitLogDto(1, 9, "2026-10-24", 1.0)))).isValid)
    }

    @Test fun rejectsInvalidValues() {
        assertFalse(BackupValidator.validate(backup(tasks = listOf(task(title = " ")))).isValid)
        assertFalse(BackupValidator.validate(backup(tasks = listOf(task(priority = "URGENT")))).isValid)
        assertFalse(BackupValidator.validate(backup(tasks = listOf(task(interval = 0)))).isValid)
        assertFalse(BackupValidator.validate(backup(occs = listOf(occ(status = "DONE")))).isValid)
        assertFalse(BackupValidator.validate(backup(occs = listOf(occ(date = "2026-13-40")))).isValid)
        assertFalse(BackupValidator.validate(backup(habits = listOf(habit(target = Double.NaN)))).isValid)
        assertFalse(BackupValidator.validate(backup(tasks = listOf(task(freq = "HOURLY")))).isValid)
    }

    @Test fun missingCategoryIsOnlyAWarning() {
        val r = BackupValidator.validate(backup(tasks = listOf(task(categoryId = 77))))
        assertTrue(r.isValid)
        assertEquals(1, r.warnings.size)
    }

    @Test fun csvEscapingAndFormulaGuard() {
        assertEquals("plain", CsvExporter.escape("plain"))
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvExporter.escape("line1\nline2"))
        assertEquals("'=SUM(A1)", CsvExporter.escape("=SUM(A1)"))
        assertEquals("", CsvExporter.escape(null))
    }
}
