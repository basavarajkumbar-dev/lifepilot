package com.lifepilot.app.domain.reminder

import com.lifepilot.app.domain.model.Occurrence
import com.lifepilot.app.domain.model.OccurrenceStatus
import com.lifepilot.app.domain.model.QuietHours
import com.lifepilot.app.domain.model.ReminderConfig
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

sealed interface ReminderDecision {
    data class Schedule(val at: Instant, val deferredByQuietHours: Boolean = false, val deferredByDailyLimit: Boolean = false) : ReminderDecision
    data class None(val reason: StopReason) : ReminderDecision
}

enum class StopReason {
    MASTER_SWITCH_OFF,
    TASK_REMINDERS_OFF,
    NOT_PENDING,
    NO_DUE_TIME,
    SINGLE_REMINDER_SENT,
    DUE_TIME_PASSED,
    STOP_TIME_REACHED,
    OVERDUE_NOT_ALLOWED,
}

/**
 * Pure function computing when the next reminder for an occurrence should fire.
 *
 * The result depends only on persisted state (config + occurrence fields) and "now", which makes
 * the engine idempotent: recomputing after a reboot, a process death, a timezone change or a
 * duplicate job always yields the same answer, so re-scheduling never creates duplicate alarms.
 *
 * Rules, in order:
 *  1. Nothing is scheduled when reminders are off, the occurrence is not pending, or it has no time.
 *  2. An active snooze wins: the next reminder is the snooze time.
 *  3. Before the first reminder: fire at (due - offset), or immediately if that time already passed
 *     (e.g. the device was off) — a missed reminder is delivered late rather than lost.
 *  4. After a reminder: if repeating, fire at lastReminder + interval, but never skip the exact due
 *     time. Repeats stop when the due time passed (continueAfterDue = false), the optional stop time
 *     is reached, or the due date ended (allowOverdue = false).
 *  5. Quiet hours defer a reminder to the end of the window; the daily cap defers to the next day.
 *     The very first reminder and an explicit snooze are never dropped by these limits.
 */
object ReminderPolicy {

    /** Alarms firing up to this much early are treated as on time (inexact alarms, clock drift). */
    val FIRE_TOLERANCE: Duration = Duration.ofSeconds(60)

    fun dueInstant(date: LocalDate, minute: Int, zone: ZoneId): Instant =
        // ZonedDateTime.of moves a time inside a DST gap forward and picks the earlier offset in an
        // overlap, so a 02:30 reminder on a spring-forward day fires at 03:30 instead of vanishing.
        ZonedDateTime.of(date, LocalTime.of(minute / 60, minute % 60), zone).toInstant()

    fun effectiveQuietHours(config: ReminderConfig, global: QuietHours): QuietHours? {
        if (!config.respectQuietHours) return null
        val cs = config.customQuietStart
        val ce = config.customQuietEnd
        val q = if (cs != null && ce != null) QuietHours(true, cs, ce) else global
        return if (q.isActiveWindow) q else null
    }

    fun next(
        config: ReminderConfig,
        occurrence: Occurrence,
        globalQuietHours: QuietHours,
        masterEnabled: Boolean,
        now: Instant,
        zone: ZoneId,
    ): ReminderDecision {
        if (!masterEnabled) return ReminderDecision.None(StopReason.MASTER_SWITCH_OFF)
        if (!config.enabled) return ReminderDecision.None(StopReason.TASK_REMINDERS_OFF)
        if (occurrence.status != OccurrenceStatus.PENDING) return ReminderDecision.None(StopReason.NOT_PENDING)
        val dueMinute = occurrence.dueMinute ?: return ReminderDecision.None(StopReason.NO_DUE_TIME)

        val due = dueInstant(occurrence.dueDate, dueMinute, zone)
        val firstAt = due.minus(Duration.ofMinutes(config.offsetMinutes.toLong().coerceAtLeast(0)))
        val last = occurrence.lastReminderAt
        val snooze = occurrence.snoozedUntil

        // "Protected" reminders (first one, explicit snooze) are deferred but never dropped.
        val isProtected: Boolean
        var candidate: Instant
        when {
            snooze != null -> {
                candidate = if (snooze.isAfter(now)) snooze else now
                isProtected = true
            }
            last == null -> {
                candidate = if (firstAt.isAfter(now)) firstAt else now
                isProtected = true
            }
            else -> {
                if (!config.repeatUntilComplete) return ReminderDecision.None(StopReason.SINGLE_REMINDER_SENT)
                val interval = Duration.ofMinutes(config.intervalMinutes.toLong().coerceIn(1, ReminderConfig.MAX_INTERVAL.toLong()))
                // If the wall clock was moved backwards, lastReminderAt can be "in the future";
                // measure the interval from now so the user is not left waiting for hours.
                val base = if (last.isAfter(now)) now else last
                var c = base.plus(interval)
                // Always ping exactly at the due time when the interval would jump over it.
                if (last.isBefore(due) && c.isAfter(due)) c = due
                candidate = if (c.isAfter(now)) c else now
                isProtected = false
            }
        }

        if (!isProtected) {
            stopReason(config, occurrence, candidate, due, zone)?.let { return ReminderDecision.None(it) }
        }

        // Apply quiet hours and the daily cap until stable (each step only moves time forward).
        val quiet = effectiveQuietHours(config, globalQuietHours)
        var deferredQuiet = false
        var deferredLimit = false
        repeat(6) {
            var changed = false
            if (quiet != null && quiet.contains(candidate.atZone(zone).toLocalTime())) {
                candidate = quiet.windowEndAfter(candidate, zone)
                deferredQuiet = true
                changed = true
            }
            val max = config.maxPerDay
            if (max != null && max > 0) {
                val day = candidate.atZone(zone).toLocalDate()
                val countThatDay = if (occurrence.reminderCountDate == day) occurrence.remindersOnCountDate else 0
                if (countThatDay >= max) {
                    candidate = day.plusDays(1).atStartOfDay(zone).toInstant()
                    deferredLimit = true
                    changed = true
                }
            }
            if (!changed) return@repeat
        }

        if (!isProtected && (deferredQuiet || deferredLimit)) {
            stopReason(config, occurrence, candidate, due, zone)?.let { return ReminderDecision.None(it) }
        }
        return ReminderDecision.Schedule(candidate, deferredQuiet, deferredLimit)
    }

    private fun stopReason(config: ReminderConfig, occ: Occurrence, candidate: Instant, due: Instant, zone: ZoneId): StopReason? {
        if (!config.continueAfterDue && candidate.isAfter(due)) return StopReason.DUE_TIME_PASSED
        val stopAfter = config.stopAfterMinutes
        if (stopAfter != null && candidate.isAfter(due.plus(Duration.ofMinutes(stopAfter.toLong())))) return StopReason.STOP_TIME_REACHED
        if (!config.allowOverdue && candidate.atZone(zone).toLocalDate().isAfter(occ.dueDate)) return StopReason.OVERDUE_NOT_ALLOWED
        return null
    }

    /** Snooze target. Kept as a function so the UI and notification action share one rule. */
    fun snoozeUntil(now: Instant, minutes: Int): Instant = now.plus(Duration.ofMinutes(minutes.toLong().coerceIn(1, 24L * 60)))

    /** Updates the per-day counter for a reminder delivered at [firedAt]. */
    fun countAfterFiring(occ: Occurrence, firedAt: Instant, zone: ZoneId): Pair<LocalDate, Int> {
        val day = firedAt.atZone(zone).toLocalDate()
        val count = if (occ.reminderCountDate == day) occ.remindersOnCountDate + 1 else 1
        return day to count
    }
}
