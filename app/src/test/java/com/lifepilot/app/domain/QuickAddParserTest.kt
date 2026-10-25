package com.lifepilot.app.domain

import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.domain.quickadd.QuickAddParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class QuickAddParserTest {

    private val now = LocalDateTime.of(2026, 10, 25, 9, 0) // Sunday 09:00
    private val today = now.toLocalDate()

    @Test fun plainTitleIsUntouched() {
        val r = QuickAddParser.parse("Buy groceries", now)
        assertEquals("Buy groceries", r.title)
        assertNull(r.date); assertNull(r.minute); assertNull(r.priority)
    }

    @Test fun tomorrowAtPm() {
        val r = QuickAddParser.parse("Call mom tomorrow at 6pm", now)
        assertEquals("Call mom", r.title)
        assertEquals(today.plusDays(1), r.date)
        assertEquals(18 * 60, r.minute)
    }

    @Test fun timeThenDayOrder() {
        val r = QuickAddParser.parse("Gym at 6:30 am tomorrow", now)
        assertEquals("Gym", r.title)
        assertEquals(today.plusDays(1), r.date)
        assertEquals(6 * 60 + 30, r.minute)
    }

    @Test fun twentyFourHourClock() {
        val r = QuickAddParser.parse("Standup at 17:45", now)
        assertEquals("Standup", r.title)
        assertEquals(17 * 60 + 45, r.minute)
        assertEquals(today, r.date)
    }

    @Test fun pastTimeWithoutDateMeansTomorrow() {
        val r = QuickAddParser.parse("Meditate 7am", now)
        assertEquals(today.plusDays(1), r.date)
        assertEquals(7 * 60, r.minute)
    }

    @Test fun weekdayMeansNextOccurrenceAfterToday() {
        assertEquals(LocalDate.of(2026, 10, 26), QuickAddParser.parse("Report monday", now).date)
        assertEquals(LocalDate.of(2026, 11, 1), QuickAddParser.parse("Review goals sunday", now).date)
    }

    @Test fun priorityMarkerAnywhere() {
        val r = QuickAddParser.parse("Fix !high server bug", now)
        assertEquals("Fix server bug", r.title)
        assertEquals(Priority.HIGH, r.priority)
        assertEquals(Priority.CRITICAL, QuickAddParser.parse("Taxes !4", now).priority)
    }

    @Test fun relativeTimes() {
        val r = QuickAddParser.parse("Take pills in 30 min", now)
        assertEquals("Take pills", r.title)
        assertEquals(9 * 60 + 30, r.minute)
        assertEquals(today.plusDays(3), QuickAddParser.parse("Renew in 3 days", now).date)
    }

    @Test fun tonightDefaultsToEvening() {
        val r = QuickAddParser.parse("Journal tonight", now)
        assertEquals(today, r.date)
        assertEquals(20 * 60, r.minute)
    }

    @Test fun ambiguousOrEmbeddedPhrasesAreNotParsed() {
        assertEquals("Plan monday meeting", QuickAddParser.parse("Plan monday meeting", now).title)
        assertEquals("Read today's news", QuickAddParser.parse("Read today's news", now).title)
        val r = QuickAddParser.parse("Meet Sam at 5", now)
        assertEquals("Meet Sam at 5", r.title)
        assertNull(r.minute)
        assertNull(QuickAddParser.parse("Lunch at 25:00", now).minute)
    }

    @Test fun neverEatsWholeInput() {
        val r = QuickAddParser.parse("tomorrow", now)
        assertEquals("tomorrow", r.title)
        assertNull(r.date)
    }
}
