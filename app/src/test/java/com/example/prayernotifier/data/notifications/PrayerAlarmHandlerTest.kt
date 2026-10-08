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
    private lateinit var silencer: FakeSilencer
    private lateinit var silenceState: SilenceState
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
        silencer = FakeSilencer()
        silenceState = SilenceState(InMemoryKeyValueStore())
        val location = LocationService(FakePositions(), FakeGeocode(), storage, FakeRegion())
        handler = PrayerAlarmHandler(
            location,
            PrayerTimesRepository(deviceCountry = { null }),
            settingsStore,
            scheduler,
            notifier,
            silencer,
            silenceState,
            now = { today.atTime(10, 0).atZone(zone) }
        )
    }

    private fun expected(method: CalculationMethod): PrayerTimings =
        PrayerCalculator.day(today, mecca.latitude, mecca.longitude, ZoneId.of("Asia/Riyadh"), method).timings

    @Test fun `day change re-plans yesterday's late prayers and today, calculated for the place`() = runTest {
        storage.saveCurrentLocation(mecca)

        assertTrue(handler.onDayChanged())

        assertEquals(1, scheduler.plans)
        assertEquals(listOf(today.minusDays(1), today), scheduler.calls.map { it.first })
        assertEquals(expected(CalculationMethod.UMM_AL_QURA), scheduler.calls.last().second)
    }

    @Test fun `a day that can't be calculated still sets the next re-plan`() = runTest {
        // Tromsø in midsummer: the sun doesn't set, so there are no times.
        storage.saveCurrentLocation(CurrentLocation("Tromsø", 69.6492, 18.9553, "NO", "Europe/Oslo"))
        val midsummer = PrayerAlarmHandler(
            LocationService(FakePositions(), FakeGeocode(), storage, FakeRegion()),
            PrayerTimesRepository(deviceCountry = { null }),
            settingsStore, scheduler, notifier, silencer, silenceState,
            now = { LocalDate.of(2027, 6, 21).atTime(10, 0).atZone(ZoneId.of("Europe/Oslo")) }
        )

        assertFalse(midsummer.onDayChanged())
        assertEquals(1, scheduler.plans)
        assertTrue(scheduler.calls.isEmpty())
    }

    @Test fun `day change uses the method the user picked`() = runTest {
        storage.saveCurrentLocation(mecca)
        settingsStore.save(AppSettings(calculationMethod = CalculationMethod.EGYPT.name))

        assertTrue(handler.onDayChanged())

        assertEquals(expected(CalculationMethod.EGYPT), scheduler.calls.last().second)
    }

    @Test fun `day change without saved place plans nothing`() = runTest {
        assertFalse(handler.onDayChanged())
        assertEquals(0, scheduler.plans)
    }

    private val tenAm get() = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun `alarm with a silence shows the reminder, then turns Do Not Disturb on until the end`() = runTest {
        val until = tenAm + 15 * 60_000L
        handler.onAlarmFired("Dhuhr", "10:00", leadMinutes = 0, silenceUntil = until)

        assertEquals(1, notifier.posts.size)
        assertEquals(listOf("start"), silencer.calls)
        assertEquals(until, silenceState.until())
        assertEquals(listOf(until), scheduler.silenceEnds)
    }

    @Test fun `silence needs access, the feature on, and an end still ahead`() = runTest {
        silencer.access = false
        handler.startSilence(tenAm + 60_000L)
        assertTrue(silencer.calls.isEmpty())

        silencer.access = true
        settingsStore.save(AppSettings(silence = com.example.prayernotifier.data.persistence.SilenceSettings(enabled = false)))
        handler.startSilence(tenAm + 60_000L)
        assertTrue(silencer.calls.isEmpty())

        settingsStore.save(AppSettings())
        handler.startSilence(tenAm - 60_000L)
        assertTrue(silencer.calls.isEmpty())
        assertEquals(null, silenceState.until())
    }

    @Test fun `end turns Do Not Disturb off and forgets the silence`() = runTest {
        handler.startSilence(tenAm + 60_000L)
        handler.endSilence()

        assertEquals(listOf("start", "stop"), silencer.calls)
        assertEquals(null, silenceState.until())
        assertEquals(1, scheduler.silenceEndCancels)
    }

    @Test fun `after a reboot an overdue silence is ended`() = runTest {
        silenceState.set(tenAm - 60_000L)
        handler.onDayChanged()
        assertEquals(listOf("stop"), silencer.calls)
        assertEquals(null, silenceState.until())
    }

    @Test fun `after a reboot a silence still running is kept on until its end`() = runTest {
        val until = tenAm + 10 * 60_000L
        silenceState.set(until)
        handler.onDayChanged()
        assertEquals(listOf("start"), silencer.calls)
        assertEquals(listOf(until), scheduler.silenceEnds)
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

        var plans = 0

        override fun scheduleDays(
            days: List<com.example.prayernotifier.data.PrayerDay>,
            settings: AppSettings,
            now: ZonedDateTime
        ): ScheduleReport {
            plans++
            days.forEach { calls += Triple(it.date, it.timings, settings) }
            return ScheduleReport(5, 0, false)
        }

        override fun cancelAll() = Unit

        val silenceEnds = mutableListOf<Long>()
        var silenceEndCancels = 0

        override fun scheduleSilenceEnd(atMillis: Long) {
            silenceEnds += atMillis
        }

        override fun cancelSilenceEnd() {
            silenceEndCancels++
        }
    }

    private class FakeSilencer : Silencer {
        var access = true
        val calls = mutableListOf<String>()
        override fun available() = access
        override fun start() { calls += "start" }
        override fun stop() { calls += "stop" }
        override fun remove() { calls += "remove" }
    }

    private class FakeNotifier : Notifier {
        val posts = mutableListOf<Triple<Int, String, String>>()

        override fun showPrayerNotification(id: Int, title: String, body: String) {
            posts += Triple(id, title, body)
        }
    }
}
