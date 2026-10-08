package com.example.prayernotifier.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.prayernotifier.data.PrayerDay
import com.example.prayernotifier.data.PrayerMath
import com.example.prayernotifier.data.PrayerTimings
import com.example.prayernotifier.data.persistence.AppSettings
import java.time.LocalDate
import java.time.ZonedDateTime

/** Pure description of one system alarm: stable ID + intent contents. */
data class AlarmSpec(val requestCode: Int, val action: String, val extras: Map<String, String>)

data class ScheduleReport(val scheduled: Int, val skippedPast: Int, val exactAlarmDenied: Boolean)

interface NotificationScheduler {
    /**
     * Replaces every planned alarm with those of [days] still ahead, and
     * the next daily re-plan. Pass yesterday too: far north in summer its
     * Isha can still be ahead after midnight. An empty list still sets the
     * re-plan, so the daily chain never stops.
     */
    fun scheduleDays(days: List<PrayerDay>, settings: AppSettings, now: ZonedDateTime): ScheduleReport

    fun cancelAll()

    /** The moment the current Do Not Disturb ends; kept apart from day plans. */
    fun scheduleSilenceEnd(atMillis: Long)

    fun cancelSilenceEnd()
}

/** One day's plan, for callers with a single day. */
fun NotificationScheduler.scheduleDay(
    date: LocalDate,
    timings: PrayerTimings,
    settings: AppSettings,
    now: ZonedDateTime
): ScheduleReport = scheduleDays(listOf(PrayerDay(date, timings)), settings, now)

/** Thin seam over [AlarmManager] so timing is unit-testable. */
interface AlarmOps {
    fun setExactAndAllowWhileIdle(type: Int, triggerMillis: Long, operation: PendingIntent)
    /** Fallback when exact timing is not permitted: may run a few minutes late. */
    fun setAndAllowWhileIdle(type: Int, triggerMillis: Long, operation: PendingIntent)
    fun cancel(operation: PendingIntent)
}

class RealAlarmOps(context: Context) : AlarmOps {
    private val manager = context.applicationContext.getSystemService(AlarmManager::class.java)

    override fun setExactAndAllowWhileIdle(type: Int, triggerMillis: Long, operation: PendingIntent) {
        manager.setExactAndAllowWhileIdle(type, triggerMillis, operation)
    }

    override fun setAndAllowWhileIdle(type: Int, triggerMillis: Long, operation: PendingIntent) {
        manager.setAndAllowWhileIdle(type, triggerMillis, operation)
    }

    override fun cancel(operation: PendingIntent) {
        manager.cancel(operation)
    }
}

/**
 * Schedules timers that each deliver one plain notification — never an
 * alarm-clock alarm (no setAlarmClock, no full-screen intent, no alarm icon).
 * Exact timing is Android's mechanism for firing precisely on time, even in
 * Doze. Where exact timing is not permitted, the same notifications are
 * scheduled inexactly (possibly a few minutes late) rather than dropped.
 *
 * Do Not Disturb starts with its own timer at each adhan. When an "on
 * time" reminder falls at that same moment, one timer does both, reminder
 * first, so the reminder isn't silenced. The end of the current silence
 * has a single timer that day plans never cancel, so a silence that runs
 * past midnight still ends.
 */
class ExactAlarmScheduler(
    private val context: Context,
    private val alarms: AlarmOps,
    private val canScheduleExactAlarms: () -> Boolean = { defaultCanScheduleExactAlarms(context) }
) : NotificationScheduler {

    private fun set(triggerMillis: Long, operation: PendingIntent) = if (canScheduleExactAlarms()) {
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, operation)
    } else {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, operation)
    }

    override fun scheduleDays(days: List<PrayerDay>, settings: AppSettings, now: ZonedDateTime): ScheduleReport {
        cancelAll()
        val exact = canScheduleExactAlarms()
        var scheduled = 0
        var skipped = 0
        days.forEach { day ->
            val plan = FireTimePlanner.plan(day.date, day.timings, settings, now)
            val parity = dayParity(day.date)
            val merged = mutableSetOf<PlannedSilence>()
            plan.planned.forEach { item ->
                val silence = plan.silences.firstOrNull { it.prayer == item.prayer && it.startAt == item.fireAt }
                if (silence != null) merged += silence
                set(
                    item.fireAt.millis(),
                    pendingIntent(
                        prayerAlarmSpec(item.prayer, item.timeString, item.leadMinutes, silence?.endAt?.millis(), parity)
                    )
                )
            }
            (plan.silences - merged).forEach { silence ->
                set(
                    silence.startAt.millis(),
                    pendingIntent(silenceStartSpec(silence.prayer, silence.endAt.millis(), parity))
                )
            }
            scheduled += plan.planned.size
            skipped += plan.skippedPast
        }
        set(FireTimePlanner.nextReplenishAt(now).millis(), pendingIntent(REPLENISH_SPEC))
        return ScheduleReport(scheduled, skipped, exactAlarmDenied = !exact)
    }

    override fun cancelAll() {
        listOf(0, 1).forEach { parity ->
            PrayerMath.ORDER.forEach {
                cancelSpec(prayerAlarmSpec(it, "", parity = parity))
                cancelSpec(silenceStartSpec(it, 0, parity))
            }
        }
        cancelSpec(REPLENISH_SPEC)
    }

    override fun scheduleSilenceEnd(atMillis: Long) {
        set(atMillis, pendingIntent(SILENCE_END_SPEC))
    }

    override fun cancelSilenceEnd() {
        cancelSpec(SILENCE_END_SPEC)
    }

    private fun cancelSpec(spec: AlarmSpec) {
        alarms.cancel(pendingIntent(spec))
    }

    private fun pendingIntent(spec: AlarmSpec): PendingIntent {
        val intent = Intent(context.applicationContext, PrayerAlarmReceiver::class.java)
            .setAction(spec.action)
        spec.extras.forEach { (key, value) -> intent.putExtra(key, value) }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            spec.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ZonedDateTime.millis(): Long = toInstant().toEpochMilli()

    companion object {
        const val ACTION_PRAYER_ALARM = "com.example.prayernotifier.ACTION_PRAYER_ALARM"
        const val ACTION_REPLENISH = "com.example.prayernotifier.ACTION_REPLENISH"
        const val ACTION_SILENCE_START = "com.example.prayernotifier.ACTION_SILENCE_START"
        const val ACTION_SILENCE_END = "com.example.prayernotifier.ACTION_SILENCE_END"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_TIME = "time"
        /** Minutes between the notification and the prayer (0 = on time). */
        const val EXTRA_LEAD = "lead"
        /** Epoch millis when the silence that starts now ends. */
        const val EXTRA_SILENCE_UNTIL = "silence_until"
        const val REQUEST_REPLENISH = 100
        const val REQUEST_SILENCE_END = 20

        val REPLENISH_SPEC = AlarmSpec(REQUEST_REPLENISH, ACTION_REPLENISH, emptyMap())
        val SILENCE_END_SPEC = AlarmSpec(REQUEST_SILENCE_END, ACTION_SILENCE_END, emptyMap())

        /** 1..5 for Fajr..Isha; Jumua shares Dhuhr's, since they never fall on the same day. */
        private fun slotOf(prayer: String): Int =
            PrayerMath.ORDER.indexOf(if (prayer == PrayerMath.JUMUA) "Dhuhr" else prayer) + 1

        /**
         * Consecutive days alternate between two sets of IDs, so yesterday's
         * late Isha and today's never replace each other.
         */
        fun dayParity(date: LocalDate): Int = Math.floorMod(date.toEpochDay(), 2L).toInt()

        /** Stable IDs 1..5 (Fajr..Isha), 31..35 on odd days, so re-scheduling replaces cleanly. */
        fun prayerAlarmSpec(
            prayer: String,
            timeString: String,
            leadMinutes: Int = 0,
            silenceUntil: Long? = null,
            parity: Int = 0
        ): AlarmSpec = AlarmSpec(
            requestCode = slotOf(prayer) + 30 * parity,
            action = ACTION_PRAYER_ALARM,
            extras = buildMap {
                put(EXTRA_PRAYER, prayer)
                put(EXTRA_TIME, timeString)
                put(EXTRA_LEAD, leadMinutes.toString())
                if (silenceUntil != null) put(EXTRA_SILENCE_UNTIL, silenceUntil.toString())
            }
        )

        /** Stable IDs 11..15 (Fajr..Isha), 41..45 on odd days. */
        fun silenceStartSpec(prayer: String, untilMillis: Long, parity: Int = 0): AlarmSpec = AlarmSpec(
            requestCode = 10 + slotOf(prayer) + 30 * parity,
            action = ACTION_SILENCE_START,
            extras = mapOf(EXTRA_PRAYER to prayer, EXTRA_SILENCE_UNTIL to untilMillis.toString())
        )

        fun notificationIdFor(prayer: String): Int =
            slotOf(prayer).takeIf { it > 0 } ?: prayer.hashCode()

        private fun defaultCanScheduleExactAlarms(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            return context.applicationContext.getSystemService(AlarmManager::class.java)
                ?.canScheduleExactAlarms() == true
        }
    }
}
