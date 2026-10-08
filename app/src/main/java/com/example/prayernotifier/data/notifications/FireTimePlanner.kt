package com.example.prayernotifier.data.notifications

import com.example.prayernotifier.data.PrayerMath
import com.example.prayernotifier.data.PrayerTimings
import com.example.prayernotifier.data.persistence.AppSettings
import com.example.prayernotifier.data.persistence.SilenceSettings
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * One notification to fire: which prayer, at what exact moment, and how
 * many minutes before the prayer that is (0 = at the prayer time).
 */
data class PlannedNotification(
    val prayer: String,
    val fireAt: ZonedDateTime,
    val timeString: String,
    val leadMinutes: Int = 0
)

/** Do Not Disturb for one prayer: on at the adhan, off at [endAt]. */
data class PlannedSilence(
    val prayer: String,
    val startAt: ZonedDateTime,
    val endAt: ZonedDateTime
)

data class DayPlan(
    val planned: List<PlannedNotification>,
    val skippedPast: Int,
    val silences: List<PlannedSilence> = emptyList()
)

/**
 * Pure scheduling math: per-prayer enable flags + "remind me X minutes
 * before" → exact fire moments. Past moments are skipped and counted.
 * On Fridays Dhuhr is planned as Jumua, with Jumua's reminder and silence.
 * Silences start at the adhan; one whose adhan has passed is not started
 * late, so re-planning mid-prayer never turns Do Not Disturb back on.
 */
object FireTimePlanner {
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    fun plan(
        date: LocalDate,
        timings: PrayerTimings,
        settings: AppSettings,
        now: ZonedDateTime
    ): DayPlan {
        val planned = mutableListOf<PlannedNotification>()
        val silences = mutableListOf<PlannedSilence>()
        var skipped = 0
        for (prayer in PrayerMath.ORDER) {
            val shown = PrayerMath.prayerOn(prayer, date)
            val at = PrayerMath
                .dateTimeFor(timings, settings.timeAdjustments, prayer, date)
                .atZone(now.zone)
            if (settings.silence.isOnFor(shown) && at.isAfter(now)) {
                silences += PlannedSilence(shown, at, at.plusMinutes(SilenceSettings.minutesFor(shown).toLong()))
            }
            val prayerSettings = settings.getSettingsForPrayer(shown)
            if (!prayerSettings.enabled) continue
            val fireAt = at.minusMinutes(prayerSettings.prePrayerReminderMinutes.toLong())
            if (!fireAt.isAfter(now)) {
                skipped++
                continue
            }
            planned += PlannedNotification(
                shown, fireAt, at.format(TIME_FORMAT), prayerSettings.prePrayerReminderMinutes
            )
        }
        return DayPlan(planned, skipped, silences)
    }

    /** Daily re-plan moment: 00:01 the next day, so tomorrow is always covered. */
    fun nextReplenishAt(now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(1)
}
