package com.example.prayernotifier.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.prayernotifier.data.UiGraph
import com.example.prayernotifier.data.location.CurrentLocation
import com.example.prayernotifier.data.location.PrefsLocationStorage
import com.example.prayernotifier.ui.home.HomeViewModel
import com.example.prayernotifier.ui.settings.SettingsViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real view models over the real graph (preferences, calculation,
 * alarm planning), checking that what the screens show keeps up with
 * quick changes and with the date.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewModelStateTest {

    private lateinit var context: Context
    private lateinit var graph: UiGraph

    /** Kiritimati, UTC+14: its date is often not the phone's. */
    private val kiritimati = CurrentLocation("Kiritimati", 1.8721, -157.4278, "KI", "Pacific/Kiritimati")
    private val placeToday get() = LocalDate.now(ZoneId.of("Pacific/Kiritimati"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        // One graph per process in the app; each test has a fresh application.
        UiGraph::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        graph = UiGraph.get(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `quick changes in a row in Settings all stick`() = runBlocking {
        val vm = SettingsViewModel(graph)
        awaitUntil("settings loaded") { !vm.state.value.loading }

        vm.setSilenceFor("Fajr", false)
        vm.setSilenceFor("Dhuhr", false)
        vm.setAdjustment("Asr", 2)

        val shown = vm.state.value.settings
        assertTrue(!shown.silence.fajr && !shown.silence.dhuhr && shown.timeAdjustments.asrAdjustment == 2)
        awaitUntil("all three saved") {
            val saved = graph.settingsStore.load()
            !saved.silence.fajr && !saved.silence.dhuhr && saved.timeAdjustments.asrAdjustment == 2
        }
    }

    @Test fun `Home shows the place's today, in the place's zone`() = runBlocking {
        PrefsLocationStorage(context).saveCurrentLocation(kiritimati)

        val vm = HomeViewModel(graph)
        awaitUntil("times loaded") { vm.state.value.days.isNotEmpty() }

        assertEquals(ZoneId.of("Pacific/Kiritimati"), vm.state.value.zone)
        assertEquals(placeToday, vm.state.value.selectedDate)
    }

    @Test fun `Home follows the date when it moves on, into the next month too`() = runBlocking {
        PrefsLocationStorage(context).saveCurrentLocation(kiritimati)
        val vm = HomeViewModel(graph)
        awaitUntil("times loaded") { vm.state.value.days.isNotEmpty() }
        vm.onToday(placeToday)

        // The date moves on into another month, as on the 1st: today and its month follow.
        val nextMonth = placeToday.withDayOfMonth(1).plusMonths(1)
        vm.onToday(nextMonth)

        assertEquals(nextMonth, vm.state.value.selectedDate)
        awaitUntil("the new month loaded") { vm.state.value.days.any { it.date == nextMonth } }
    }

    @Test fun `Home stays on a date the user picked when the day moves on`() = runBlocking {
        PrefsLocationStorage(context).saveCurrentLocation(kiritimati)
        val vm = HomeViewModel(graph)
        awaitUntil("times loaded") { vm.state.value.days.isNotEmpty() }
        vm.onToday(placeToday)
        val picked = placeToday.plusDays(10)
        vm.selectDate(picked)

        vm.onToday(placeToday.plusDays(1))

        assertEquals(picked, vm.state.value.selectedDate)
    }

    private suspend fun awaitUntil(what: String, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting: $what")
            Thread.sleep(10)
        }
    }
}
