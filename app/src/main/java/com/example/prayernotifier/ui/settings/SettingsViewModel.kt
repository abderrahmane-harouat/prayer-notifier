package com.example.prayernotifier.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prayernotifier.data.PrayerMath
import com.example.prayernotifier.data.PrayerTimings
import com.example.prayernotifier.data.UiGraph
import com.example.prayernotifier.data.calculation.AsrMethod
import com.example.prayernotifier.data.calculation.CalculationMethod
import com.example.prayernotifier.data.persistence.AppSettings
import com.example.prayernotifier.data.persistence.PrayerNotificationSettings
import com.example.prayernotifier.data.persistence.PrayerTimeAdjustments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val notificationsAllowed: Boolean = true,
    /** Do Not Disturb can be switched by the app: Android 10+ with access granted. */
    val dndAllowed: Boolean = true,
    /** The method "automatic" picks for the current place's country. */
    val automaticMethod: CalculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
    /** The Asr rule "automatic" picks for the current place's country. */
    val automaticAsr: AsrMethod = AsrMethod.STANDARD,
    /**
     * Today's calculated times at the current place, before the user's
     * corrections, so a correction can be judged against them. Null
     * without a place.
     */
    val today: PrayerTimings? = null
) {
    /** The method the times actually use. */
    val method: CalculationMethod get() = settings.chosenMethod ?: automaticMethod

    /** The Asr rule the times actually use. */
    val asr: AsrMethod get() = settings.chosenAsr ?: automaticAsr

    /** Today's adhan of a daily prayer with its correction ("12:38"), or null without a place. */
    fun adhanToday(prayer: String): String? = today?.let {
        PrayerMath.adjustTime(PrayerMath.timeOf(it, prayer), settings.timeAdjustments.getAdjustmentForPrayer(prayer))
    }
}

class SettingsViewModel(private val graph: UiGraph) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** Loads, saves and recalculations run one at a time, in the order asked. */
    private val work = Mutex()

    init {
        refresh()
    }

    /** Re-read saved settings; called whenever the Settings page is shown. */
    fun refresh() {
        viewModelScope.launch {
            work.withLock {
                val settings = withContext(Dispatchers.IO) { graph.settingsStore.load() }
                _state.update { it.copy(settings = settings) }
                showPlaceTimes()
            }
            _state.update { it.copy(loading = false) }
        }
    }

    /** What "automatic" means at the current place, and today's times there, for the settings on screen. */
    private suspend fun showPlaceTimes() {
        val settings = _state.value.settings
        val place = withContext(Dispatchers.IO) { graph.locationService.getCurrentSavedLocation() }
        val today = place?.let {
            withContext(Dispatchers.Default) {
                runCatching {
                    graph.prayerTimes.day(LocalDate.now(graph.prayerTimes.zoneOf(it)), it, settings).timings
                }.getOrNull()
            }
        }
        _state.update {
            it.copy(
                automaticMethod = place?.let(graph.prayerTimes::automaticMethodFor)
                    ?: CalculationMethod.MUSLIM_WORLD_LEAGUE,
                automaticAsr = place?.let(graph.prayerTimes::automaticAsrFor) ?: AsrMethod.STANDARD,
                today = today
            )
        }
    }

    fun refreshCapabilities(notificationsAllowed: Boolean) {
        _state.update { it.copy(notificationsAllowed = notificationsAllowed, dndAllowed = graph.silencer.available()) }
    }

    /**
     * Shows a change at once, then saves it. Each change applies to the
     * latest settings, not to a copy taken before an earlier change was
     * saved, so quick taps in a row all stick. Saves run one at a time and
     * each writes the settings on screen, right away, so Home reads them
     * even when the page is closed at once.
     */
    private fun persist(change: (AppSettings) -> AppSettings, afterSave: suspend () -> Unit = {}) {
        _state.update { it.copy(settings = change(it.settings)) }
        viewModelScope.launch {
            work.withLock {
                withContext(Dispatchers.IO) { graph.settingsStore.save(_state.value.settings) }
                // The method or Asr rule may have changed today's times.
                showPlaceTimes()
            }
            // Re-plans run one at a time on their own and read the latest
            // saved settings, so the last one plans the last change. Never
            // skipped, even when the app is closed right after a change.
            withContext(NonCancellable + Dispatchers.IO) {
                afterSave()
                graph.rescheduleToday()
            }
        }
    }

    /** Null goes back to the method of the place's country. */
    fun setMethod(method: CalculationMethod?) {
        persist({ it.copy(calculationMethod = method?.name) })
    }

    /** Null goes back to the Asr rule of the place's country. */
    fun setAsr(asr: AsrMethod?) {
        persist({ it.copy(asrMethod = asr?.name) })
    }

    /** Off ends a silence that is on now and removes the app's rule from Android. */
    fun setSilenceEnabled(on: Boolean) {
        persist(
            { it.copy(silence = it.silence.copy(enabled = on)) },
            afterSave = { if (!on) graph.turnOffSilence() }
        )
    }

    fun setSilenceMinutes(prayer: String, minutes: Int) {
        persist({ it.copy(silence = it.silence.withMinutes(prayer, minutes)) })
    }

    fun setSilenceFor(prayer: String, on: Boolean) {
        persist({ it.copy(silence = it.silence.with(prayer, on)) })
    }

    fun setPrayerNotifications(prayer: String, next: PrayerNotificationSettings) {
        persist({ it.withPrayerSettings(prayer, next) })
    }

    fun setHijriOffset(days: Int) {
        persist({ it.copy(hijriDateAdjustment = days.coerceIn(-2, 2)) })
    }

    fun setAdjustment(prayer: String, minutes: Int) {
        persist({ it.copy(timeAdjustments = it.timeAdjustments.with(prayer, minutes)) })
    }

    fun adjustmentOf(prayer: String): Int =
        _state.value.settings.timeAdjustments.getAdjustmentForPrayer(prayer)


    /** Same "remind me X min before" for every prayer; on/off stays per prayer. */
    fun setReminderForAll(minutes: Int) {
        persist({ settings ->
            PrayerMath.ORDER.fold(settings) { next, prayer ->
                next.withPrayerSettings(
                    prayer, next.getSettingsForPrayer(prayer).copy(prePrayerReminderMinutes = minutes)
                )
            }
        })
    }

    /** One save for a prayer's sheet: its reminder and its time correction. */
    fun savePrayer(prayer: String, notifications: PrayerNotificationSettings, adjustment: Int) {
        persist({
            it.withPrayerSettings(prayer, notifications)
                .copy(timeAdjustments = it.timeAdjustments.with(prayer, adjustment))
        })
    }

    fun prayerNames(): List<String> = PrayerMath.ORDER
}

private fun AppSettings.withPrayerSettings(prayer: String, next: PrayerNotificationSettings) =
    when (prayer) {
        "Fajr" -> copy(fajrSettings = next)
        "Dhuhr" -> copy(dhuhrSettings = next)
        "Asr" -> copy(asrSettings = next)
        "Maghrib" -> copy(maghribSettings = next)
        "Isha" -> copy(ishaSettings = next)
        PrayerMath.JUMUA -> copy(jumuaSettings = next)
        else -> this
    }

private fun PrayerTimeAdjustments.with(prayer: String, minutes: Int) = when (prayer) {
    "Fajr" -> copy(fajrAdjustment = minutes)
    "Dhuhr" -> copy(dhuhrAdjustment = minutes)
    "Asr" -> copy(asrAdjustment = minutes)
    "Maghrib" -> copy(maghribAdjustment = minutes)
    "Isha" -> copy(ishaAdjustment = minutes)
    else -> this
}
