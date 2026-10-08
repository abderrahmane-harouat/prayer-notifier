package com.example.prayernotifier.data.notifications

import android.content.Context
import com.example.prayernotifier.R
import com.example.prayernotifier.data.PrayerTimesRepository
import com.example.prayernotifier.data.location.AndroidDeviceRegion
import com.example.prayernotifier.data.location.AndroidGeocoderProvider
import com.example.prayernotifier.data.location.FusedPositionProvider
import com.example.prayernotifier.data.location.LocationService
import com.example.prayernotifier.data.location.PrefsLocationStorage
import com.example.prayernotifier.data.persistence.PrefsKeyValueStore
import com.example.prayernotifier.data.persistence.SettingsStore
import com.example.prayernotifier.i18n.AppLanguage
import com.example.prayernotifier.i18n.prayerNameRes

/** Production wiring. Thin glue only — logic lives in the testable classes. */
class AppGraph(context: Context) {
    private val app = context.applicationContext

    private val region by lazy { AndroidDeviceRegion(app) }
    private val locationService by lazy {
        LocationService(
            FusedPositionProvider(app),
            AndroidGeocoderProvider(app),
            PrefsLocationStorage(app),
            region
        )
    }
    private val prayerTimes by lazy { PrayerTimesRepository(deviceCountry = region::countryCode) }
    private val settings by lazy { SettingsStore(PrefsKeyValueStore(app, "prayer_notifier_settings")) }
    private val scheduler by lazy { ExactAlarmScheduler(app, RealAlarmOps(app)) }
    private val notifier by lazy { SystemNotifier(app) }

    val handler by lazy {
        PrayerAlarmHandler(
            locationService, prayerTimes, settings, scheduler, notifier,
            texts = LocalizedNotificationTexts(app)
        )
    }
}

/** Notification text in the app's chosen language, resolved at fire time. */
private class LocalizedNotificationTexts(private val app: Context) : NotificationTexts {
    private fun res() = AppLanguage.localizedContext(app).resources
    override fun title(leadMinutes: Int): String = res().getString(
        if (leadMinutes > 0) R.string.notif_title_reminder else R.string.notif_title
    )
    override fun body(prayer: String, time: String, leadMinutes: Int): String {
        val name = res().getString(prayerNameRes(prayer))
        return if (leadMinutes > 0) {
            res().getQuantityString(R.plurals.notif_body_before, leadMinutes, leadMinutes.toString(), name, time)
        } else {
            res().getString(R.string.notif_body, name, time)
        }
    }
}
