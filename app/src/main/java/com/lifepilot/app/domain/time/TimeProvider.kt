package com.lifepilot.app.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Injectable clock. Production uses the system clock and the device's *current* zone, read on
 * every call so that timezone changes are picked up without restarting the process. Tests use a
 * fake implementation to make time-based behaviour deterministic.
 */
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
}

object SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Formats minutes-of-day helpers shared by domain and UI. */
object MinuteOfDay {
    fun of(hour: Int, minute: Int): Int = hour * 60 + minute
    fun hour(m: Int) = m / 60
    fun minute(m: Int) = m % 60
    fun isValid(m: Int?) = m == null || m in 0 until 24 * 60
}
