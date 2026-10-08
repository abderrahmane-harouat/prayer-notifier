package com.example.prayernotifier.data.notifications

import com.example.prayernotifier.data.PrayerTimesRepository
import com.example.prayernotifier.data.location.LocationService
import com.example.prayernotifier.data.persistence.SettingsStore
import java.time.ZonedDateTime

/** Notification wording, so the handler stays free of Android resources. */
/**
 * Notification wording, so the handler stays free of Android resources.
 * [leadMinutes] is how early the reminder fires: 0 means "it's time",
 * anything else means "N minutes left".
 */
interface NotificationTexts {
    fun title(leadMinutes: Int): String
    fun body(prayer: String, time: String, leadMinutes: Int): String
}

object EnglishNotificationTexts : NotificationTexts {
    override fun title(leadMinutes: Int) = if (leadMinutes > 0) "Prayer reminder" else "Prayer time"
    override fun body(prayer: String, time: String, leadMinutes: Int) = when {
        leadMinutes <= 0 -> "Time for $prayer prayer · $time"
        leadMinutes == 1 -> "1 minute until $prayer · $time"
        else -> "$leadMinutes minutes until $prayer · $time"
    }
}

/**
 * Answers the receiver's questions without the network: an alarm fired →
 * show its notification; an adhan with silence → Do Not Disturb on until
 * its end; the end → off again; the day changed (replenish alarm or
 * reboot) → calculate today's times for the saved place, re-plan the
 * alarms, and finish or resume a silence that was on.
 */
class PrayerAlarmHandler(
    private val location: LocationService,
    private val prayerTimes: PrayerTimesRepository,
    private val settings: SettingsStore,
    private val scheduler: NotificationScheduler,
    private val notifier: Notifier,
    private val silencer: Silencer,
    private val silenceState: SilenceState,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
    private val texts: NotificationTexts = EnglishNotificationTexts
) {
    /** The reminder first, then any silence starting at the same moment. */
    suspend fun onAlarmFired(
        prayer: String,
        timeString: String,
        leadMinutes: Int = 0,
        silenceUntil: Long? = null
    ) {
        notifier.showPrayerNotification(
            ExactAlarmScheduler.notificationIdFor(prayer),
            texts.title(leadMinutes),
            texts.body(prayer, timeString, leadMinutes)
        )
        if (silenceUntil != null) startSilence(silenceUntil)
    }

    /**
     * Do Not Disturb on until [untilMillis]. Nothing happens when the
     * feature was switched off since planning, without Do Not Disturb
     * access, or when the end has already passed.
     */
    suspend fun startSilence(untilMillis: Long) {
        if (untilMillis <= nowMillis()) return
        if (!settings.load().silence.enabled || !silencer.available()) return
        val until = maxOf(untilMillis, silenceState.until() ?: 0L)
        silencer.start()
        silenceState.set(until)
        scheduler.scheduleSilenceEnd(until)
    }

    /** Do Not Disturb off: at the end of a silence, or when the feature is switched off. */
    suspend fun endSilence() {
        silencer.stop()
        silenceState.clear()
        scheduler.cancelSilenceEnd()
    }

    /**
     * Re-plans yesterday's late prayers (Isha after midnight far north) and
     * today's. A day that can't be calculated (polar summer) plans nothing
     * but still sets the next re-plan, so reminders resume by themselves.
     * Returns false when there is no place to plan for.
     */
    suspend fun onDayChanged(): Boolean {
        resumeSilence()
        val moment = now()
        val current = location.getCurrentSavedLocation() ?: return false
        val saved = settings.load()
        val days = listOf(moment.toLocalDate().minusDays(1), moment.toLocalDate()).mapNotNull { date ->
            runCatching { prayerTimes.day(date, current, saved) }.getOrNull()
        }
        scheduler.scheduleDays(days, saved, moment)
        return days.isNotEmpty()
    }

    /** After a reboot alarms are gone: end an overdue silence, or keep one going until its end. */
    private suspend fun resumeSilence() {
        val until = silenceState.until() ?: return
        if (until <= nowMillis() || !settings.load().silence.enabled) {
            endSilence()
        } else {
            silencer.start()
            scheduler.scheduleSilenceEnd(until)
        }
    }

    private fun nowMillis(): Long = now().toInstant().toEpochMilli()
}
