package com.example.prayernotifier.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.example.prayernotifier.data.location.AndroidDeviceRegion
import com.example.prayernotifier.data.location.AndroidGeocoderProvider
import com.example.prayernotifier.data.location.FusedPositionProvider
import com.example.prayernotifier.data.location.LocationService
import com.example.prayernotifier.data.location.PrefsLocationStorage
import com.example.prayernotifier.data.notifications.DoNotDisturbSilencer
import com.example.prayernotifier.data.notifications.ExactAlarmScheduler
import com.example.prayernotifier.data.notifications.PrayerAlarmHandler
import com.example.prayernotifier.data.notifications.RealAlarmOps
import com.example.prayernotifier.data.notifications.SilenceState
import com.example.prayernotifier.data.notifications.SystemNotifier
import com.example.prayernotifier.data.persistence.PrefsKeyValueStore
import com.example.prayernotifier.data.persistence.SettingsStore

/**
 * UI-layer wiring. Thin glue only — logic lives in the tested data classes.
 * Exposed through [LocalUiGraph] so screens stay free of Android plumbing.
 */
class UiGraph private constructor(app: Context) {
    private val context = app.applicationContext
    private val region = AndroidDeviceRegion(context)

    private val settingsPrefs = PrefsKeyValueStore(context, "prayer_notifier_settings")
    val settingsStore = SettingsStore(settingsPrefs)
    val locationService = LocationService(
        FusedPositionProvider(context),
        AndroidGeocoderProvider(context),
        PrefsLocationStorage(context),
        region
    )
    val prayerTimes = PrayerTimesRepository(deviceCountry = region::countryCode)
    val scheduler = ExactAlarmScheduler(context, RealAlarmOps(context))
    val silencer = DoNotDisturbSilencer(context)
    private val alarmHandler by lazy {
        PrayerAlarmHandler(
            locationService, prayerTimes, settingsStore, scheduler, SystemNotifier(context),
            silencer, SilenceState(settingsPrefs)
        )
    }

    /**
     * Do Not Disturb at prayer time switched off: end a silence that is on
     * now and remove the app's rule from the system settings.
     */
    suspend fun turnOffSilence() {
        alarmHandler.endSilence()
        silencer.remove()
    }

    /**
     * Re-plans today's alarms for the current place. Called after the
     * place changes and after any settings change. Mirrors the Flutter app
     * re-scheduling on every settings save.
     */
    suspend fun rescheduleToday(): Boolean = alarmHandler.onDayChanged()

    companion object {
        /** Times downloaded by versions before on-device calculation. */
        private const val OLD_DOWNLOADS_DATABASE = "prayer.db"

        @Volatile
        private var instance: UiGraph? = null

        /** One graph per process, so it survives activity recreation, e.g. a language switch. */
        fun get(context: Context): UiGraph =
            instance ?: synchronized(this) {
                instance ?: UiGraph(context.applicationContext).also {
                    instance = it
                    // Nothing reads the old downloads any more: free the space.
                    context.applicationContext.deleteDatabase(OLD_DOWNLOADS_DATABASE)
                }
            }
    }
}

val LocalUiGraph = compositionLocalOf<UiGraph> { error("UiGraph not provided") }

@Composable
fun UiGraphProvider(content: @Composable () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val graph = remember(app) { UiGraph.get(app) }
    CompositionLocalProvider(LocalUiGraph provides graph) {
        content()
    }
}
