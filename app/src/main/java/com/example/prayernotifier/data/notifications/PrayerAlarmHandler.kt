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
 * Answers the receiver's two questions without the network: an alarm fired
 * → show its notification; the day changed (replenish alarm or reboot) →
 * calculate today's times for the saved place and re-plan the alarms.
 */
class PrayerAlarmHandler(
    private val location: LocationService,
    private val prayerTimes: PrayerTimesRepository,
    private val settings: SettingsStore,
    private val scheduler: NotificationScheduler,
    private val notifier: Notifier,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
    private val texts: NotificationTexts = EnglishNotificationTexts
) {
    suspend fun onAlarmFired(prayer: String, timeString: String, leadMinutes: Int = 0) {
        notifier.showPrayerNotification(
            ExactAlarmScheduler.notificationIdFor(prayer),
            texts.title(leadMinutes),
            texts.body(prayer, timeString, leadMinutes)
        )
    }

    /** Re-plans today. Returns false when there is no place to plan for. */
    suspend fun onDayChanged(): Boolean {
        val moment = now()
        val today = moment.toLocalDate()
        val current = location.getCurrentSavedLocation() ?: return false
        val saved = settings.load()
        val todays = runCatching { prayerTimes.day(today, current, saved) }.getOrNull() ?: return false
        scheduler.scheduleDay(today, todays.timings, saved, moment)
        return true
    }
}
