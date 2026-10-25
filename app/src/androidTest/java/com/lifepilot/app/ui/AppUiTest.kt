package com.lifepilot.app.ui

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifepilot.app.LifePilotApp
import com.lifepilot.app.MainActivity
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.ThemeMode
import com.lifepilot.app.domain.service.TaskDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * End-to-end UI tests against the real app (real Room database, DataStore and scheduler). Each
 * test starts from cleared data so results are deterministic.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppUiTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as LifePilotApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun reset() = runBlocking {
        container.backupManager.clearAll()
        container.settings.update { it.copy(onboardingCompleted = true, themeMode = ThemeMode.DARK) }
    }

    @After fun close() { scenario?.close() }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    private fun waitForText(text: String, timeout: Long = 5000) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), timeout)

    /** Scrolls a lazy list until a matching item is composed (lazy lists only compose visible items). */
    private fun scrollListTo(listTag: String, matcher: SemanticsMatcher, timeout: Long = 5000) {
        compose.waitUntil(timeout) { runCatching { compose.onNodeWithTag(listTag).performScrollToNode(matcher) }.isSuccess }
    }

    private fun waitForDb(timeout: Long = 5000, condition: suspend () -> Boolean) = compose.waitUntil(timeout) { runBlocking { condition() } }

    @Test fun onboardingFlowLeadsToHome() {
        runBlocking { container.settings.update { it.copy(onboardingCompleted = false) } }
        launch()
        compose.onNodeWithTag("onboarding_next").performClick()
        compose.onNodeWithTag("onboarding_next").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Make reminders reliable"), 5000)
        compose.onNodeWithTag("onboarding_start").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("fab_add"), 5000)
        assertTrue(runBlocking { container.settings.current().onboardingCompleted })
    }

    @Test fun emptyStatesAreShown() {
        launch()
        scrollListTo("home_list", hasText("A clear day"))
        compose.onNodeWithTag("tab_tasks").performClick()
        waitForText("No tasks yet")
        compose.onNodeWithTag("tab_insights").performClick()
        waitForText("No data yet")
    }

    @Test fun quickAddCreatesTask() {
        launch()
        compose.onNodeWithTag("fab_add").performClick()
        compose.onNodeWithTag("quickadd_input").performTextInput("Buy milk")
        compose.onNodeWithTag("quickadd_save").performClick()
        waitForDb { container.tasks.observeItems().first().isNotEmpty() }
        val items = runBlocking { container.tasks.observeItems().first() }
        assertEquals(listOf("Buy milk"), items.map { it.task.title })
        assertEquals(LocalDate.now(), items.single().occurrence.dueDate)
    }

    @Test fun fullEditorCreatesTaskWithReminder() {
        launch()
        compose.onNodeWithTag("tab_tasks").performClick()
        waitForText("No tasks yet")
        compose.onNodeWithText("Create a task").performClick()
        compose.onNodeWithTag("editor_title").performTextInput("Study Python")
        compose.onNodeWithTag("editor_save").performScrollTo().performClick()
        waitForDb { container.tasks.getAllTasks().isNotEmpty() }
        scrollListTo("task_list", hasText("Study Python"))
        val task = runBlocking { container.tasks.getAllTasks() }.single()
        assertEquals("Study Python", task.title)
    }

    @Test fun completingFromListPersists() {
        val occId = runBlocking {
            val id = container.taskService.createTask(TaskDraft(title = "Water plants", dueDate = LocalDate.now()))
            container.tasks.occurrencesForTask(id).single().id
        }
        launch()
        compose.onNodeWithTag("tab_tasks").performClick()
        scrollListTo("task_list", hasContentDescription("Complete Water plants"))
        compose.onNodeWithContentDescription("Complete Water plants").performClick()
        compose.waitUntil(5000) { runBlocking { container.tasks.getOccurrence(occId)?.status } == OccurrenceStatus.COMPLETED }
    }

    @Test fun snoozeFromDetailKeepsTaskPending() {
        val occId = runBlocking {
            val id = container.taskService.createTask(TaskDraft(title = "Call bank", dueDate = LocalDate.now().plusDays(1), dueMinute = 10 * 60))
            container.tasks.occurrencesForTask(id).single().id
        }
        launch()
        compose.onNodeWithTag("tab_tasks").performClick()
        scrollListTo("task_list", hasText("Call bank"))
        compose.onNodeWithText("Call bank").performClick()
        compose.onNodeWithTag("detail_snooze").performClick()
        compose.onNodeWithTag("snooze_10").performClick()
        compose.waitUntil(5000) { runBlocking { container.tasks.getOccurrence(occId)?.snoozeCount } == 1 }
        assertEquals(OccurrenceStatus.PENDING, runBlocking { container.tasks.getOccurrence(occId)!!.status })
    }

    @Test fun editAndDeleteTask() {
        runBlocking { container.taskService.createTask(TaskDraft(title = "Old title", dueDate = LocalDate.now())) }
        launch()
        compose.onNodeWithTag("tab_tasks").performClick()
        scrollListTo("task_list", hasText("Old title"))
        compose.onNodeWithText("Old title").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("detail_edit"), 5000)
        compose.onNodeWithTag("detail_edit").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("editor_title"), 5000)
        compose.onNodeWithTag("editor_title").performTextClearance()
        compose.onNodeWithTag("editor_title").performTextInput("New title")
        compose.onNodeWithTag("editor_save_top").performClick()
        waitForDb { container.tasks.getAllTasks().singleOrNull()?.title == "New title" }
        compose.waitUntilAtLeastOneExists(hasTestTag("detail_title"), 5000)

        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithTag("detail_delete").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5000) { runBlocking { container.tasks.getAllTasks().isEmpty() } }
    }

    @Test fun createAndTrackHabit() {
        launch()
        compose.onNodeWithTag("tab_habits").performClick()
        compose.onNodeWithTag("fab_add_habit").performClick()
        compose.onNodeWithTag("habit_name").performTextInput("Read")
        compose.onNodeWithTag("habit_save").performScrollTo().performClick()
        waitForDb { container.habits.getHabits().isNotEmpty() }
        compose.waitUntilAtLeastOneExists(hasContentDescription("Mark Read done today"), 5000)
        compose.onNodeWithContentDescription("Mark Read done today").performClick()
        compose.waitUntil(5000) {
            runBlocking {
                val h = container.habits.getHabits().singleOrNull() ?: return@runBlocking false
                container.habits.getLog(h.id, LocalDate.now()) != null
            }
        }
    }

    @Test fun switchingThemePersists() {
        launch()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("theme_LIGHT").performClick()
        compose.waitUntil(5000) { runBlocking { container.settings.current().themeMode } == ThemeMode.LIGHT }
        compose.onNodeWithTag("theme_DARK").performClick()
        compose.waitUntil(5000) { runBlocking { container.settings.current().themeMode } == ThemeMode.DARK }
    }

    @Test fun editorShowsValidationError() {
        launch()
        compose.onNodeWithTag("tab_tasks").performClick()
        waitForText("No tasks yet")
        compose.onNodeWithText("Create a task").performClick()
        compose.onNodeWithTag("editor_save").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("editor_errors"), 5000)
        assertTrue(runBlocking { container.tasks.getAllTasks().isEmpty() })
    }
}
