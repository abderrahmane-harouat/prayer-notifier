package com.example.prayernotifier.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prayernotifier.data.PrayerMath
import com.example.prayernotifier.data.UiGraph
import com.example.prayernotifier.data.calculation.CalculationMethod
import com.example.prayernotifier.data.persistence.AppSettings
import com.example.prayernotifier.data.persistence.PrayerNotificationSettings
import com.example.prayernotifier.data.persistence.PrayerTimeAdjustments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val notificationsAllowed: Boolean = true,
    /** The method "automatic" picks for the current place's country. */
    val automaticMethod: CalculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE
) {
    /** The method the times actually use. */
    val method: CalculationMethod get() = settings.chosenMethod ?: automaticMethod
}

class SettingsViewModel(private val graph: UiGraph) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Re-read saved settings; called whenever the Settings page is shown. */
    fun refresh() {
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) { graph.settingsStore.load() }
            val place = withContext(Dispatchers.IO) { graph.locationService.getCurrentSavedLocation() }
            val automatic = place?.let { graph.prayerTimes.automaticMethodFor(it) }
                ?: CalculationMethod.MUSLIM_WORLD_LEAGUE
            _state.update { it.copy(loading = false, settings = settings, automaticMethod = automatic) }
        }
    }

    fun refreshCapabilities(notificationsAllowed: Boolean) {
        _state.update { it.copy(notificationsAllowed = notificationsAllowed) }
    }

    private fun persist(next: AppSettings) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                graph.settingsStore.save(next)
                graph.rescheduleToday()
            }
            _state.update { it.copy(settings = next) }
        }
    }

    /** Null goes back to the method of the place's country. */
    fun setMethod(method: CalculationMethod?) {
        persist(_state.value.settings.copy(calculationMethod = method?.name))
    }

    fun setHijriOffset(days: Int) {
        persist(_state.value.settings.copy(hijriDateAdjustment = days.coerceIn(-2, 2)))
    }

    fun setAdjustment(prayer: String, minutes: Int) {
        val settings = _state.value.settings
        persist(settings.copy(timeAdjustments = settings.timeAdjustments.with(prayer, minutes)))
    }

    fun adjustmentOf(prayer: String): Int =
        _state.value.settings.timeAdjustments.getAdjustmentForPrayer(prayer)

    fun setPrayerNotifications(prayer: String, next: PrayerNotificationSettings) {
        persist(_state.value.settings.withPrayerSettings(prayer, next))
    }

    /** Same "remind me X min before" for every prayer; on/off stays per prayer. */
    fun setReminderForAll(minutes: Int) {
        var next = _state.value.settings
        PrayerMath.ORDER.forEach { prayer ->
            next = next.withPrayerSettings(
                prayer, next.getSettingsForPrayer(prayer).copy(prePrayerReminderMinutes = minutes)
            )
        }
        persist(next)
    }

    /** One save for a prayer's sheet: its reminder and its time correction. */
    fun savePrayer(prayer: String, notifications: PrayerNotificationSettings, adjustment: Int) {
        val current = _state.value.settings
        persist(
            current.withPrayerSettings(prayer, notifications)
                .copy(timeAdjustments = current.timeAdjustments.with(prayer, adjustment))
        )
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
