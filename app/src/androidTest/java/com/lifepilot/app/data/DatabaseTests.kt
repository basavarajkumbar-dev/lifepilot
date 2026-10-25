package com.lifepilot.app.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lifepilot.app.data.database.LifePilotDatabase
import com.lifepilot.app.data.repository.RoomCategoryRepository
import com.lifepilot.app.data.repository.RoomHabitRepository
import com.lifepilot.app.data.repository.RoomTaskRepository
import com.lifepilot.app.domain.model.Habit
import com.lifepilot.app.domain.model.HabitSchedule
import com.lifepilot.app.domain.model.HabitType
import com.lifepilot.app.domain.model.Occurrence
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.domain.model.RecurrenceFrequency
import com.lifepilot.app.domain.model.RecurrenceRule
import com.lifepilot.app.domain.model.ReminderConfig
import com.lifepilot.app.domain.model.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DatabasePersistenceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "persistence-test.db"

    @After fun cleanup() { context.deleteDatabase(name) }

    private fun task(title: String, rule: RecurrenceRule? = null) = Task(
        0, title, "notes", 1, Priority.HIGH, listOf("a", "b"), 30, null, null, 540,
        ReminderConfig(intervalMinutes = 10, maxPerDay = 4), rule, Instant.ofEpochMilli(1000), Instant.ofEpochMilli(2000),
    )

    @Test fun dataSurvivesDatabaseReopen() = runBlocking {
        val today = LocalDate.of(2026, 10, 25)
        var db = LifePilotDatabase.build(context, name)
        var repo = RoomTaskRepository(db)
        val id = repo.insertTask(task("Persist me", RecurrenceRule(RecurrenceFrequency.WEEKLY, weekdays = setOf(java.time.DayOfWeek.MONDAY), startDate = today)))
        val occId = repo.insertOccurrence(Occurrence.new(id, today, 540, Instant.ofEpochMilli(3000)))!!
        RoomHabitRepository(db).upsertHabit(Habit(0, "Read", "", "book", 0, HabitType.CHECK, 1.0, "", HabitSchedule.DAILY, emptySet(), 3, null, false, today, Instant.EPOCH, false))
        db.close()

        // "Restart": a brand-new database instance reading the same file.
        db = LifePilotDatabase.build(context, name)
        repo = RoomTaskRepository(db)
        val loaded = repo.getTask(id)!!
        assertEquals("Persist me", loaded.title)
        assertEquals(listOf("a", "b"), loaded.tags)
        assertEquals(10, loaded.reminder.intervalMinutes)
        assertEquals(4, loaded.reminder.maxPerDay)
        assertEquals(setOf(java.time.DayOfWeek.MONDAY), loaded.recurrence!!.weekdays)
        assertEquals(OccurrenceStatus.PENDING, repo.getOccurrence(occId)!!.status)
        assertEquals(1, RoomHabitRepository(db).getHabits().size)
        assertEquals(7, RoomCategoryRepository(db).getCategories().size) // seeded built-ins
        db.close()
    }

    @Test fun uniqueSeriesDatePreventsDuplicateOccurrences() = runBlocking {
        val db = LifePilotDatabase.build(context, null)
        val repo = RoomTaskRepository(db)
        val id = repo.insertTask(task("Daily"))
        val d = LocalDate.of(2026, 10, 25)
        assertNotNull(repo.insertOccurrence(Occurrence.new(id, d, 540, Instant.EPOCH)))
        assertNull(repo.insertOccurrence(Occurrence.new(id, d, 600, Instant.EPOCH)))
        assertEquals(1, repo.occurrencesForTask(id).size)
        db.close()
    }

    @Test fun conditionalCompletionOnlySucceedsOnce() = runBlocking {
        val db = LifePilotDatabase.build(context, null)
        val repo = RoomTaskRepository(db)
        val id = repo.insertTask(task("Once"))
        val occ = repo.insertOccurrence(Occurrence.new(id, LocalDate.of(2026, 10, 25), 540, Instant.EPOCH))!!
        assertTrue(repo.completeIfPending(occ, Instant.ofEpochMilli(5)))
        assertFalse(repo.completeIfPending(occ, Instant.ofEpochMilli(6)))
        assertEquals(Instant.ofEpochMilli(5), repo.getOccurrence(occ)!!.completedAt)
        db.close()
    }

    @Test fun deletingTaskCascadesAndCategoryDeleteMovesTasks() = runBlocking {
        val db = LifePilotDatabase.build(context, null)
        val repo = RoomTaskRepository(db)
        val cats = RoomCategoryRepository(db)
        val custom = cats.addCategory("Garden", 2, "star")
        val id = repo.insertTask(task("Water plants").copy(categoryId = custom))
        val occ = repo.insertOccurrence(Occurrence.new(id, LocalDate.of(2026, 10, 25), 540, Instant.EPOCH))!!
        cats.deleteCategory(custom)
        assertEquals(LifePilotDatabase.OTHER_CATEGORY_ID, repo.getTask(id)!!.categoryId)
        repo.deleteTask(id)
        assertNull(repo.getOccurrence(occ))
        assertTrue(repo.observeItems().first().isEmpty())
        db.close()
    }
}

/**
 * Validates the exported Room schema against the entity definitions and runs every registered
 * migration. When a new version is added, add a migration test here (create at N-1, migrate to N).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val testDb = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LifePilotDatabase::class.java)

    @Test fun currentSchemaMatchesExportedSchemaAndOpensWithRoom() {
        helper.createDatabase(testDb, LifePilotDatabase.VERSION).apply {
            execSQL("INSERT INTO categories (id, name, colorIndex, iconKey, isBuiltIn, sortOrder) VALUES (1, 'Work', 0, 'work', 1, 0)")
            execSQL("INSERT INTO tasks (id, title, description, categoryId, priority, tags, estimatedMinutes, startDate, startMinute, dueMinute, createdAt, updatedAt) VALUES (1, 'Kept', '', 1, 'HIGH', '', NULL, NULL, NULL, 540, 0, 0)")
            close()
        }
        // Validates every table, column, index and foreign key against the entities.
        helper.runMigrationsAndValidate(testDb, LifePilotDatabase.VERSION, true, *LifePilotDatabase.MIGRATIONS).close()
    }
}
