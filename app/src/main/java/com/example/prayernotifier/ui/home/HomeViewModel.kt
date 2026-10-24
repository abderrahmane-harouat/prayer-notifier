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
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
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
    /** The place's time zone: its times are in it, and so are "now" and "today". */
    val zone: ZoneId = ZoneId.systemDefault(),
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

    /** The place change in progress (start, a fix, a saved place); a newer one replaces it. */
    private var placeJob: Job? = null

    /** The month calculation in progress; a newer one replaces it. */
    private var monthJob: Job? = null

    /** Today at the place, as last seen by [onToday]. */
    private var today: LocalDate = _state.value.selectedDate

    init {
        start()
    }

    fun start() = changePlace {
        _state.update { it.copy(loading = true, error = null) }
        val settings = withContext(Dispatchers.IO) { graph.settingsStore.load() }
        _state.update { it.copy(settings = settings) }
        val cached = withContext(Dispatchers.IO) {
            graph.locationService.getCurrentSavedLocation()
        }
        if (cached != null) {
            show(cached)
            // Saved by a version that didn't record the place's time zone
            // and country: a fresh fix records both.
            if (cached.timeZone == null) obtainFreshLocation()
        } else {
            obtainFreshLocation()
        }
    }

    fun retry() = start()

    /** System dialog answered (first-run auto request or tap-initiated). */
    fun onPermissionResult(granted: Boolean, locked: Boolean) {
        _state.update { it.copy(askForPermission = false) }
        if (granted) {
            changePlace { obtainFreshLocation() }
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
            changePlace { obtainFreshLocation() }
        }
    }

    fun refreshLocation() = changePlace { obtainFreshLocation() }

    fun selectDate(date: LocalDate) {
        val before = _state.value.selectedDate
        if (before == date) return
        _state.update { it.copy(selectedDate = date, error = null) }
        if (YearMonth.from(before) != YearMonth.from(date)) reloadMonth()
    }

    /** Instant when today's month is already loaded (the usual case). */
    fun goToToday() = selectDate(LocalDate.now(_state.value.zone))

    /**
     * The place's date, as the clock on screen reads it. When it moves on
     * (midnight, or the app shown again the next morning) while today was
     * shown, the new today is shown.
     */
    fun onToday(date: LocalDate) {
        val before = today
        today = date
        if (date != before && _state.value.selectedDate == before) selectDate(date)
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    fun selectSavedLocation(location: SavedLocation) = changePlace {
        _state.update { it.copy(error = null) }
        val updated = withContext(Dispatchers.IO) {
            graph.locationService.selectSavedLocation(location)
        }
        show(updated, toToday = true)
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
            if (methodChanged && current != null) reloadMonth()
        }
    }

    /**
     * Runs a place change once the one in progress has stopped, so the
     * last request wins: a slow fix can't land after a saved place the
     * user picked meanwhile and replace it.
     */
    private fun changePlace(block: suspend () -> Unit) {
        val previous = placeJob
        placeJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            block()
        }
    }

    /**
     * Puts [place] on screen and re-plans its reminders. The date shown
     * moves to the place's today when today was shown (the new place's
     * date can differ), or always with [toToday].
     */
    private suspend fun show(place: CurrentLocation, toToday: Boolean = false) {
        current = place
        val zone = graph.prayerTimes.zoneOf(place)
        val placeToday = LocalDate.now(zone)
        _state.update {
            val date = if (toToday || it.selectedDate == today) placeToday else it.selectedDate
            it.copy(locationName = place.name, zone = zone, selectedDate = date)
        }
        today = placeToday
        reloadMonth()
        loadSavedLocations()
        // Never skipped, even when a newer place change cancels this one: the
        // place is saved already, so its alarms must follow. A failure keeps
        // the times on screen; the nightly re-plan tries again.
        withContext(NonCancellable + Dispatchers.IO) { runCatching { graph.rescheduleToday() } }
    }

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
        val fresh = try {
            withContext(Dispatchers.IO) {
                graph.locationService.refreshLocation()
            }
        } catch (e: CancellationException) {
            // Replaced by a newer place change, which sets its own state.
            _state.update { it.copy(locating = false) }
            throw e
        } catch (e: LocationException.PermissionDenied) {
            _state.update { it.copy(loading = false, locating = false, askForPermission = true) }
            return
        } catch (e: LocationException.ServiceDisabled) {
            fail(LocationCause.ServiceDisabled)
            return
        } catch (e: LocationException.NoFix) {
            fail(LocationCause.NoFix)
            return
        } catch (e: Exception) {
            _state.update {
                if (hasData) {
                    it.copy(locating = false, notice = HomeNotice.LoadFailed)
                } else {
                    it.copy(loading = false, locating = false, error = HomeError.LoadFailed(e.message.orEmpty()))
                }
            }
            return
        }
        _state.update {
            it.copy(
                locating = false,
                notice = if (hasData) HomeNotice.LocationUpdated(fresh.name) else it.notice
            )
        }
        show(fresh)
    }

    /**
     * Calculates the shown month for the current place and settings. Only
     * the latest request lands: an earlier month finishing late never
     * replaces the one now shown.
     */
    private fun reloadMonth() {
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
        val month = YearMonth.from(_state.value.selectedDate)
        val settings = _state.value.settings
        monthJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        monthJob = viewModelScope.launch {
            try {
                val days = withContext(Dispatchers.Default) {
                    graph.prayerTimes.month(month, loc, settings)
                }
                _state.update { it.copy(loading = false, days = days) }
            } catch (e: CancellationException) {
                throw e
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
}
