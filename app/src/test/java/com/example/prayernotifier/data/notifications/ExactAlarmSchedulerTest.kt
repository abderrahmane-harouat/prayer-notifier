package com.example.prayernotifier.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import androidx.test.core.app.ApplicationProvider
import com.example.prayernotifier.data.PrayerTimings
import com.example.prayernotifier.data.persistence.AppSettings
import com.example.prayernotifier.data.persistence.PrayerNotificationSettings
import com.example.prayernotifier.data.persistence.SilenceSettings
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExactAlarmSchedulerTest {

    private val zone = ZoneId.systemDefault()
    private val date = LocalDate.of(2026, Month.SEPTEMBER, 15)
    private val timings = PrayerTimings(
        fajr = "05:12", dhuhr = "12:45", asr = "16:10",
        maghrib = "18:52", isha = "20:20"
    )

    /** Reminders alone; Do Not Disturb has its own tests below. */
    private val remindersOnly = AppSettings(silence = SilenceSettings(enabled = false))

    private lateinit var ops: RecordingAlarmOps
    private lateinit var scheduler: ExactAlarmScheduler

    @Before
    fun setUp() {
        ops = RecordingAlarmOps()
        scheduler = ExactAlarmScheduler(
            ApplicationProvider.getApplicationContext(), ops, canScheduleExactAlarms = { true }
        )
    }

    private fun at(hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), zone)

    private fun millis(hour: Int, minute: Int, day: Int = 15): Long =
        ZonedDateTime.of(LocalDate.of(2026, Month.SEPTEMBER, day), LocalTime.of(hour, minute), zone)
            .toInstant().toEpochMilli()

    @Test fun `schedules exact RTC alarms at precise moments plus replenish`() {
        val report = scheduler.scheduleDay(date, timings, remindersOnly, at(4, 0))

        assertEquals(5, report.scheduled)
        assertEquals(0, report.skippedPast)
        assertFalse(report.exactAlarmDenied)
        assertEquals(
            listOf(
                AlarmManager.RTC_WAKEUP to millis(5, 7),
                AlarmManager.RTC_WAKEUP to millis(12, 40),
                AlarmManager.RTC_WAKEUP to millis(16, 5),
                AlarmManager.RTC_WAKEUP to millis(18, 42),
                AlarmManager.RTC_WAKEUP to millis(20, 15),
                AlarmManager.RTC_WAKEUP to millis(0, 1, day = 16)
            ),
            ops.sets
        )
    }

    @Test fun `past prayers are reported as skipped`() {
        val report = scheduler.scheduleDay(date, timings, remindersOnly, at(13, 0))
        assertEquals(3, report.scheduled)
        assertEquals(2, report.skippedPast)
        assertEquals(4, ops.sets.size) // 3 prayers + replenish.
    }

    @Test fun `disabled prayer gets no alarm`() {
        val settings = remindersOnly.copy(ishaSettings = PrayerNotificationSettings(enabled = false))
        val report = scheduler.scheduleDay(date, timings, settings, at(4, 0))
        assertEquals(4, report.scheduled)
        // 4 prayers + replenish:
        assertEquals(5, ops.sets.size)
    }

    @Test fun `reschedule cancels previous alarms first`() {
        scheduler.scheduleDay(date, timings, remindersOnly, at(4, 0))
        assertEquals(21, ops.cancels) // scheduleDay always clears before setting.
        assertEquals(6, ops.sets.size)
        scheduler.scheduleDay(date, timings, remindersOnly, at(4, 0))
        assertEquals(42, ops.cancels)
        assertEquals(12, ops.sets.size)
    }

    @Test fun `cancelAll removes reminders, silence starts and the re-plan`() {
        scheduler.cancelAll()
        // Reminders and silence starts for both day parities, and the re-plan.
        assertEquals(21, ops.cancels)
    }

    @Test fun `silence starts at each adhan by default`() {
        scheduler.scheduleDay(date, timings, AppSettings(), at(4, 0))
        val starts = listOf(millis(5, 12), millis(12, 45), millis(16, 10), millis(18, 52), millis(20, 20))
        assertEquals(11, ops.sets.size) // 5 reminders + 5 silence starts + replenish.
        assertTrue(ops.sets.map { it.second }.containsAll(starts))
    }

    @Test fun `an on-time reminder and its silence share one alarm, reminder first`() {
        val settings = AppSettings(fajrSettings = PrayerNotificationSettings(prePrayerReminderMinutes = 0))
        scheduler.scheduleDay(date, timings, settings, at(4, 0))
        assertEquals(10, ops.sets.size)
        assertEquals(1, ops.sets.count { it.second == millis(5, 12) })
    }

    @Test fun `silence end is its own alarm, outside day plans`() {
        scheduler.scheduleSilenceEnd(millis(21, 0))
        assertEquals(listOf(AlarmManager.RTC_WAKEUP to millis(21, 0)), ops.sets)
        scheduler.cancelSilenceEnd()
        assertEquals(1, ops.cancels)
    }

    @Test fun `alarms fire to the minute, not rounded or shifted`() {
        scheduler.scheduleDay(date, timings, remindersOnly, at(4, 0))
        val fajrMillis = ops.sets[0].second
        // Exactly 05:07:00.000 — no seconds drift, no inexact window.
        assertEquals(millis(5, 7), fajrMillis)
        assertEquals(0, fajrMillis % 60_000)
    }

    @Test fun `denied exact timing still schedules every notification, inexactly`() {
        val denied = ExactAlarmScheduler(
            ApplicationProvider.getApplicationContext(), ops, canScheduleExactAlarms = { false }
        )
        val report = denied.scheduleDay(date, timings, remindersOnly, at(4, 0))
        assertEquals(5, report.scheduled)
        assertTrue(report.exactAlarmDenied)
        assertTrue(ops.sets.isEmpty())
        // 5 prayers + the daily re-plan, all through the inexact path.
        assertEquals(6, ops.inexactSets.size)
        assertEquals(millis(5, 7), ops.inexactSets[0].second)
    }

    private class RecordingAlarmOps : AlarmOps {
        val sets = mutableListOf<Pair<Int, Long>>()
        val inexactSets = mutableListOf<Pair<Int, Long>>()
        var cancels = 0

        override fun setExactAndAllowWhileIdle(type: Int, triggerMillis: Long, operation: PendingIntent) {
            sets += type to triggerMillis
        }

        override fun setAndAllowWhileIdle(type: Int, triggerMillis: Long, operation: PendingIntent) {
            inexactSets += type to triggerMillis
        }

        override fun cancel(operation: PendingIntent) {
            cancels++
        }
    }
}
