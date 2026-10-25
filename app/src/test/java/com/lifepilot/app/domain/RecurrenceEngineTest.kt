package com.lifepilot.app.domain

import com.lifepilot.app.domain.model.RecurrenceFrequency
import com.lifepilot.app.domain.model.RecurrenceRule
import com.lifepilot.app.domain.recurrence.RecurrenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceEngineTest {

    private val start = LocalDate.of(2026, 10, 25) // Sunday

    private fun dates(rule: RecurrenceRule, days: Long = 60) = RecurrenceEngine.occurrencesBetween(rule, rule.startDate, rule.startDate.plusDays(days))

    @Test fun everyDay() {
        val d = dates(RecurrenceRule(RecurrenceFrequency.DAILY, startDate = start), 4)
        assertEquals((0L..4L).map { start.plusDays(it) }, d)
    }

    @Test fun everyThreeDays() {
        val d = dates(RecurrenceRule(RecurrenceFrequency.DAILY, interval = 3, startDate = start), 9)
        assertEquals(listOf(start, start.plusDays(3), start.plusDays(6), start.plusDays(9)), d)
    }

    @Test fun selectedWeekdays() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), startDate = start)
        val d = dates(rule, 7)
        assertEquals(listOf(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 30)), d)
    }

    @Test fun everyWeekDefaultsToStartWeekday() {
        val d = dates(RecurrenceRule(RecurrenceFrequency.WEEKLY, startDate = start), 21)
        assertEquals(listOf(start, start.plusWeeks(1), start.plusWeeks(2), start.plusWeeks(3)), d)
    }

    @Test fun everyOtherWeek() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, interval = 2, weekdays = setOf(DayOfWeek.TUESDAY), startDate = LocalDate.of(2026, 10, 27))
        assertEquals(listOf(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 24)), dates(rule, 28))
    }

    @Test fun fifthOfEveryMonth() {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, monthDays = setOf(5), startDate = start)
        assertEquals(listOf(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 12, 5), LocalDate.of(2027, 1, 5)), dates(rule, 80))
    }

    @Test fun thirtyFirstClampsToLastDayOfShortMonths() {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, monthDays = setOf(31), startDate = LocalDate.of(2027, 1, 1))
        val d = RecurrenceEngine.occurrencesBetween(rule, rule.startDate, LocalDate.of(2027, 4, 30))
        assertEquals(listOf(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30)), d)
    }

    @Test fun lastDayOfMonth() {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, monthDays = setOf(RecurrenceRule.LAST_DAY_OF_MONTH), startDate = LocalDate.of(2028, 1, 15))
        val d = RecurrenceEngine.occurrencesBetween(rule, rule.startDate, LocalDate.of(2028, 3, 31))
        assertEquals(listOf(LocalDate.of(2028, 1, 31), LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 31)), d)
    }

    @Test fun multipleMonthDaysNoDuplicates() {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, monthDays = setOf(30, 31), startDate = LocalDate.of(2027, 2, 1))
        val d = RecurrenceEngine.occurrencesBetween(rule, rule.startDate, LocalDate.of(2027, 2, 28))
        assertEquals(listOf(LocalDate.of(2027, 2, 28)), d)
    }

    @Test fun yearlyLeapDay() {
        val rule = RecurrenceRule(RecurrenceFrequency.YEARLY, startDate = LocalDate.of(2028, 2, 29))
        val d = RecurrenceEngine.occurrencesBetween(rule, rule.startDate, LocalDate.of(2032, 12, 31))
        assertEquals(listOf(LocalDate.of(2028, 2, 29), LocalDate.of(2029, 2, 28), LocalDate.of(2030, 2, 28), LocalDate.of(2031, 2, 28), LocalDate.of(2032, 2, 29)), d)
    }

    @Test fun endDateExpiresSeries() {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, startDate = start, endDate = start.plusDays(2))
        assertEquals(3, dates(rule, 30).size)
        assertNull(RecurrenceEngine.nextAfter(rule, start.plusDays(2)))
    }

    @Test fun nextOnOrAfterBeforeStartReturnsStart() {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, startDate = start)
        assertEquals(start, RecurrenceEngine.nextOnOrAfter(rule, start.minusDays(10)))
    }

    @Test fun describeIsReadable() {
        assertEquals("Every day", RecurrenceEngine.describe(RecurrenceRule(RecurrenceFrequency.DAILY, startDate = start)))
        assertEquals("Monthly on the 5th", RecurrenceEngine.describe(RecurrenceRule(RecurrenceFrequency.MONTHLY, monthDays = setOf(5), startDate = start)))
        assertEquals("Weekly on Sun", RecurrenceEngine.describe(RecurrenceRule(RecurrenceFrequency.WEEKLY, startDate = start)))
        assertEquals("22nd", RecurrenceEngine.ordinal(22))
        assertEquals("11th", RecurrenceEngine.ordinal(11))
    }
}
