package com.example.prayernotifier.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prayernotifier.data.PrayerDay
import com.example.prayernotifier.data.UiGraph
import com.example.prayernotifier.data.location.CurrentLocation
import com.example.prayernotifier.data.location.LocationException
import com.example.prayernotifier.data.location.SavedLocation
import com.example.prayernotifier.data.persistence.AppSettings
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface HomeError {
    data class LocationRequired(val cause: LocationCause) : HomeError
    data class LoadFailed(val message: String) : HomeError
}

enum class LocationCause {
    /** System dialog can still pop up. */
    PermissionRequestable,
    /** User picked "Don't allow" twice — only app settings helps now. */
    PermissionLocked,
    /** Device location services are switched off. */
    ServiceDisabled,
    /** No GPS fix available. */
    NoFix
}

/**
 * One-shot messages for things that happen while data is already on screen
 * (where a full-screen error would wipe the prayer times away).
 */
sealed interface HomeNotice {
    data class LocationUpdated(val name: String) : HomeNotice
    data class LocationFailed(val cause: LocationCause) : HomeNotice
    data object LoadFailed : HomeNotice
}

data class HomeUiState(
    val loading: Boolean = true,
    /** Blank until named; the UI shows a localized "Current location". */
    val locationName: String = "",
    val days: List<PrayerDay> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val settings: AppSettings = AppSettings(),
    val error: HomeError? = null,
    val savedLocations: List<SavedLocation> = emptyList(),
    val askForPermission: Boolean = false,
    /** A location fix is in progress (can take a few seconds outdoors). */
    val locating: Boolean = false,
    val notice: HomeNotice? = null
)

class HomeViewModel(private val graph: UiGraph) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var current: CurrentLocation? = null

    init {
        start()
    }

    fun start() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val settings = withContext(Dispatchers.IO) { graph.settingsStore.load() }
            _state.update { it.copy(settings = settings) }
            val cached = withContext(Dispatchers.IO) {
                graph.locationService.getCurrentSavedLocation()
            }
            if (cached != null) {
                current = cached
                _state.update { it.copy(locationName = cached.name) }
                loadMonth(itSelected())
                // Saved by a version that didn't record the place's time zone
                // and country: a fresh fix records both.
                if (cached.timeZone == null) obtainFreshLocation()
            } else {
                obtainFreshLocation()
            }
        }
    }

    fun retry() = start()

    /** System dialog answered (first-run auto request or tap-initiated). */
    fun onPermissionResult(granted: Boolean, locked: Boolean) {
        _state.update { it.copy(askForPermission = false) }
        if (granted) {
            viewModelScope.launch { obtainFreshLocation() }
        } else {
            val cause = if (locked) {
                LocationCause.PermissionLocked
            } else {
                LocationCause.PermissionRequestable
            }
            _state.update {
                it.copy(loading = false, error = HomeError.LocationRequired(cause))
            }
        }
    }

    /** Permission can no longer pop up — point the user at app settings. */
    fun onPermissionPermanentlyDenied() {
        _state.update {
            it.copy(
                loading = false,
                error = HomeError.LocationRequired(LocationCause.PermissionLocked)
            )
        }
    }

    /**
     * Called when Home becomes visible: reload settings and recover whenever
     * the location permission is now granted — whatever the previous error
     * was (granted from a system page, adb, or a fresh dialog answer).
     */
    fun onResumed(locationPermissionGranted: Boolean) {
        refreshSettings()
        if (locationPermissionGranted &&
            _state.value.error is HomeError.LocationRequired
        ) {
            viewModelScope.launch { obtainFreshLocation() }
        }
    }

    fun refreshLocation() {
        viewModelScope.launch { obtainFreshLocation() }
    }

    fun selectDate(date: LocalDate) {
        val before = _state.value.selectedDate
        if (before == date) return
        _state.update { it.copy(selectedDate = date, error = null) }
        if (before.year != date.year || before.monthValue != date.monthValue) {
            viewModelScope.launch { loadMonth(date) }
        }
    }

    /** Instant when today's month is already loaded (the usual case). */
    fun goToToday() = selectDate(LocalDate.now())

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    fun selectSavedLocation(location: SavedLocation) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val updated = withContext(Dispatchers.IO) {
                graph.locationService.selectSavedLocation(location)
            }
            current = updated
            _state.update {
                it.copy(locationName = updated.name, selectedDate = LocalDate.now())
            }
            loadMonth(LocalDate.now())
        }
    }

    fun loadSavedLocations() {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                graph.locationService.getSavedLocations()
            }
            _state.update { it.copy(savedLocations = saved) }
        }
    }

    /** A new calculation method or Asr rule changes the times themselves: recalculate. */
    fun refreshSettings() {
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) { graph.settingsStore.load() }
            val before = _state.value.settings
            val methodChanged = settings.calculationMethod != before.calculationMethod ||
                settings.asrMethod != before.asrMethod
            _state.update { it.copy(settings = settings) }
            if (methodChanged && current != null) loadMonth(itSelected())
        }
    }

    private fun itSelected(): LocalDate = _state.value.selectedDate

    /**
     * Fresh GPS fix → name → load times. With prayer times already on screen
     * the page stays put: the header shows "Locating…" and the outcome
     * arrives as a [HomeNotice]; on first run failures are full-screen.
     */
    private suspend fun obtainFreshLocation() {
        val hasData = _state.value.days.isNotEmpty()
        _state.update {
            if (hasData) it.copy(locating = true) else it.copy(loading = true, error = null, locating = true)
        }
        fun fail(cause: LocationCause) = _state.update {
            if (hasData) {
                it.copy(locating = false, notice = HomeNotice.LocationFailed(cause))
            } else {
                it.copy(loading = false, locating = false, error = HomeError.LocationRequired(cause))
            }
        }
        try {
            val fresh = withContext(Dispatchers.IO) {
                graph.locationService.refreshLocation()
            }
            current = fresh
            _state.update {
                it.copy(
                    locationName = fresh.name,
                    locating = false,
                    notice = if (hasData) HomeNotice.LocationUpdated(fresh.name) else it.notice
                )
            }
            loadMonth(itSelected())
        } catch (e: LocationException.PermissionDenied) {
            _state.update { it.copy(loading = false, locating = false, askForPermission = true) }
        } catch (e: LocationException.ServiceDisabled) {
            fail(LocationCause.ServiceDisabled)
        } catch (e: LocationException.NoFix) {
            fail(LocationCause.NoFix)
        } catch (e: Exception) {
            _state.update {
                if (hasData) {
                    it.copy(locating = false, notice = HomeNotice.LoadFailed)
                } else {
                    it.copy(loading = false, locating = false, error = HomeError.LoadFailed(e.message.orEmpty()))
                }
            }
        }
    }

    private suspend fun loadMonth(forDate: LocalDate) {
        val loc = current
        if (loc == null) {
            _state.update {
                it.copy(
                    loading = false,
                    error = HomeError.LocationRequired(LocationCause.PermissionRequestable)
                )
            }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        try {
            val days = withContext(Dispatchers.Default) {
                graph.prayerTimes.month(YearMonth.from(forDate), loc, _state.value.settings)
            }
            _state.update { it.copy(loading = false, days = days) }
            if (forDate == LocalDate.now()) {
                withContext(Dispatchers.IO) { graph.rescheduleToday() }
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    loading = false,
                    error = HomeError.LoadFailed(e.message.orEmpty()),
                    notice = if (it.days.isNotEmpty()) HomeNotice.LoadFailed else it.notice
                )
            }
        }
    }
}
