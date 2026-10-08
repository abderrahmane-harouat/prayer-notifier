package com.example.prayernotifier.data.notifications

import com.example.prayernotifier.data.PrayerTimesRepository
import com.example.prayernotifier.data.PrayerTimings
import com.example.prayernotifier.data.calculation.CalculationMethod
import com.example.prayernotifier.data.calculation.PrayerCalculator
import com.example.prayernotifier.data.location.CurrentLocation
import com.example.prayernotifier.data.location.DeviceRegion
import com.example.prayernotifier.data.location.GeocodeProvider
import com.example.prayernotifier.data.location.GeocodedPlace
import com.example.prayernotifier.data.location.InMemoryLocationStorage
import com.example.prayernotifier.data.location.LocationService
import com.example.prayernotifier.data.location.PositionOutcome
import com.example.prayernotifier.data.location.PositionProvider
import com.example.prayernotifier.data.persistence.AppSettings
import com.example.prayernotifier.data.persistence.InMemoryKeyValueStore
import com.example.prayernotifier.data.persistence.SettingsStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrayerAlarmHandlerTest {

    private lateinit var storage: InMemoryLocationStorage
    private lateinit var settingsStore: SettingsStore
    private lateinit var scheduler: FakeScheduler
    private lateinit var notifier: FakeNotifier
    private lateinit var handler: PrayerAlarmHandler

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val mecca = CurrentLocation("Mecca", 21.4225, 39.8262, "SA", "Asia/Riyadh")

    @Before
    fun setUp() {
        storage = InMemoryLocationStorage()
        settingsStore = SettingsStore(InMemoryKeyValueStore())
        scheduler = FakeScheduler()
        notifier = FakeNotifier()
        val location = LocationService(FakePositions(), FakeGeocode(), storage, FakeRegion())
        handler = PrayerAlarmHandler(
            location,
            PrayerTimesRepository(deviceCountry = { null }),
            settingsStore,
            scheduler,
            notifier,
            now = { today.atTime(10, 0).atZone(zone) }
        )
    }

    private fun expected(method: CalculationMethod): PrayerTimings =
        PrayerCalculator.day(today, mecca.latitude, mecca.longitude, ZoneId.of("Asia/Riyadh"), method).timings

    @Test fun `day change re-plans today with times calculated for the place`() = runTest {
        storage.saveCurrentLocation(mecca)

        assertTrue(handler.onDayChanged())

        assertEquals(1, scheduler.calls.size)
        val call = scheduler.calls[0]
        assertEquals(today, call.first)
        assertEquals(expected(CalculationMethod.UMM_AL_QURA), call.second)
    }

    @Test fun `day change uses the method the user picked`() = runTest {
        storage.saveCurrentLocation(mecca)
        settingsStore.save(AppSettings(calculationMethod = CalculationMethod.EGYPT.name))

        assertTrue(handler.onDayChanged())

        assertEquals(expected(CalculationMethod.EGYPT), scheduler.calls.single().second)
    }

    @Test fun `day change without saved place plans nothing`() = runTest {
        assertFalse(handler.onDayChanged())
        assertTrue(scheduler.calls.isEmpty())
    }

    @Test fun `reminder before the prayer says how many minutes are left`() = runTest {
        handler.onAlarmFired("Maghrib", "18:52", leadMinutes = 5)

        val post = notifier.posts.single()
        assertEquals("Prayer reminder", post.second)
        assertEquals("5 minutes until Maghrib · 18:52", post.third)
    }

    @Test fun `one minute is singular`() = runTest {
        handler.onAlarmFired("Fajr", "05:12", leadMinutes = 1)
        assertEquals("1 minute until Fajr · 05:12", notifier.posts.single().third)
    }

    @Test fun `fired alarm shows the right notification`() = runTest {
        handler.onAlarmFired("Maghrib", "18:52")

        assertEquals(1, notifier.posts.size)
        val post = notifier.posts[0]
        assertEquals(4, post.first)
        assertEquals("Prayer time", post.second)
        assertTrue(post.third.contains("Maghrib"))
        assertTrue(post.third.contains("18:52"))
    }

    private class FakePositions : PositionProvider {
        override suspend fun currentFix(): PositionOutcome =
            PositionOutcome.Fix(com.example.prayernotifier.data.location.LatLng(21.4225, 39.8262))
    }

    private class FakeGeocode : GeocodeProvider {
        override suspend fun lookup(latitude: Double, longitude: Double) = GeocodedPlace("Mecca", "SA")
    }

    private class FakeRegion : DeviceRegion {
        override fun countryCode(): String? = null
        override fun timeZone(): String = "Asia/Riyadh"
    }

    private class FakeScheduler : NotificationScheduler {
        val calls = mutableListOf<Triple<LocalDate, PrayerTimings, AppSettings>>()

        override fun scheduleDay(
            date: LocalDate,
            timings: PrayerTimings,
            settings: AppSettings,
            now: ZonedDateTime
        ): ScheduleReport {
            calls += Triple(date, timings, settings)
            return ScheduleReport(5, 0, false)
        }

        override fun cancelAll() = Unit
    }

    private class FakeNotifier : Notifier {
        val posts = mutableListOf<Triple<Int, String, String>>()

        override fun showPrayerNotification(id: Int, title: String, body: String) {
            posts += Triple(id, title, body)
        }
    }
}
