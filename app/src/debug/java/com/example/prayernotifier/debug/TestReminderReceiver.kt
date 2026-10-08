package com.example.prayernotifier.debug

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.prayernotifier.data.PrayerMath
import com.example.prayernotifier.data.notifications.ExactAlarmScheduler
import com.example.prayernotifier.data.notifications.PrayerAlarmReceiver
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Debug-only end-to-end reminder test, driven from adb:
 *
 *   adb shell am broadcast -a com.example.prayernotifier.debug.TEST_REMINDER \
 *       -n com.example.prayernotifier.debug/com.example.prayernotifier.debug.TestReminderReceiver \
 *       --es prayer Maghrib --ei delay 10 --ei lead 5
 *
 * Schedules the same alarm the real scheduler uses, [delay] seconds from now,
 * so the whole path runs: AlarmManager -> PrayerAlarmReceiver -> handler ->
 * notification with the app's sound. Omit "prayer" to queue all five, one
 * minute apart (so Android's notification cooldown doesn't quiet them).
 * "lead" is the reminder's minutes-before (0 = "it's time"; default 5).
 *
 * With "--ei silence 60" it tests Do Not Disturb instead: on after [delay]
 * seconds, through the real start alarm, and off again [silence] seconds
 * later through the real end alarm.
 */
class TestReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val delaySeconds = intent.getIntExtra("delay", 10).coerceAtLeast(1)
        val lead = intent.getIntExtra("lead", 5).coerceAtLeast(0)
        val prayers = intent.getStringExtra("prayer")?.let { listOf(it) } ?: PrayerMath.ORDER
        val alarms = context.getSystemService(AlarmManager::class.java)
        val start = System.currentTimeMillis() + delaySeconds * 1000L
        val silenceSeconds = intent.getIntExtra("silence", 0)
        if (silenceSeconds > 0) {
            val silence = Intent(context, PrayerAlarmReceiver::class.java)
                .setAction(ExactAlarmScheduler.ACTION_SILENCE_START)
                .putExtra(ExactAlarmScheduler.EXTRA_PRAYER, prayers.first())
                .putExtra(ExactAlarmScheduler.EXTRA_SILENCE_UNTIL, (start + silenceSeconds * 1000L).toString())
            val pending = PendingIntent.getBroadcast(
                context, TEST_REQUEST_BASE + 50, silence,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, start, pending)
            return
        }
        prayers.forEachIndexed { index, prayer ->
            val fireAt = start + index * 60_000L
            // The pretend prayer time is [lead] minutes after the notification,
            // so a test reads exactly like a real reminder.
            val prayerAt = fireAt + lead * 60_000L
            val time = TIME.format(Instant.ofEpochMilli(prayerAt).atZone(ZoneId.systemDefault()))
            val fire = Intent(context, PrayerAlarmReceiver::class.java)
                .setAction(ExactAlarmScheduler.ACTION_PRAYER_ALARM)
                .putExtra(ExactAlarmScheduler.EXTRA_PRAYER, prayer)
                .putExtra(ExactAlarmScheduler.EXTRA_TIME, time)
                .putExtra(ExactAlarmScheduler.EXTRA_LEAD, lead.toString())
            val pending = PendingIntent.getBroadcast(
                context,
                TEST_REQUEST_BASE + PrayerMath.ORDER.indexOf(prayer),
                fire,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        // Distinct from the real reminders (1..5) and the daily re-plan (100).
        const val TEST_REQUEST_BASE = 900
    }
}
