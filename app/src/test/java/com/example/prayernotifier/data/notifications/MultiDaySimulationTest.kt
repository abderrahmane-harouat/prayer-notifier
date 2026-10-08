package com.example.prayernotifier.data.notifications

import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.prayernotifier.data.PrayerMath
import com.example.prayernotifier.data.PrayerTimesRepository
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
import com.example.prayernotifier.data.persistence.PrayerNotificationSettings
import com.example.prayernotifier.data.persistence.PrayerTimeAdjustments
import com.example.prayernotifier.data.persistence.SettingsStore
import com.example.prayernotifier.data.persistence.SilenceSettings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Runs the real reminder and Do Not Disturb machinery over years of
 * simulated days, in seconds: the real planner, scheduler, handler and
 * alarm dispatch, against a virtual clock and an alarm queue that behaves
 * like AlarmManager (setting a PendingIntent replaces its earlier alarm,
 * cancel removes it, the earliest alarm fires next).
 *
 * Every reminder and every Do Not Disturb start and end is checked against
 * the times worked out independently from the calculated prayer times:
 * nothing missing, nothing extra, nothing early or late, every day.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiDaySimulationTest {

    private data class Place(val name: String, val latitude: Double, val longitude: Double, val zone: String, val country: String)

    private val algiers = Place("Algiers", 36.7538, 3.0588, "Africa/Algiers", "DZ")
    private val london = Place("London", 51.5074, -0.1278, "Europe/London", "GB")
    private val oslo = Place("Oslo", 59.9139, 10.7522, "Europe/Oslo", "NO")
    private val makkah = Place("Makkah", 21.4225, 39.8262, "Asia/Riyadh", "SA")
    private val tromso = Place("Tromsø", 69.6492, 18.9553, "Europe/Oslo", "NO")

    @Test fun `Algiers, ten years, default settings`() =
        simulate(algiers, LocalDate.of(2026, 10, 8), days = 3653)

    @Test fun `London across daylight saving changes, three years`() =
        simulate(london, LocalDate.of(2026, 10, 8), days = 1096)

    @Test fun `Oslo, where summer Isha passes midnight, three years`() =
        simulate(oslo, LocalDate.of(2026, 10, 8), days = 1096)

    @Test fun `Makkah with Umm al-Qura's Ramadan Isha, three years`() =
        simulate(makkah, LocalDate.of(2026, 10, 8), days = 1096)

    @Test fun `Tromsø, polar night and midnight sun, days that can't be calculated never stop the chain`() =
        simulate(tromso, LocalDate.of(2026, 10, 8), days = 731, disruptions = Random(3))

    @Test fun `far future, 2099 to 2101`() =
        simulate(algiers, LocalDate.of(2099, 6, 1), days = 731)

    @Test fun `on-time reminders, corrections, Jumua and silence switches`() = simulate(
        algiers, LocalDate.of(2026, 10, 8), days = 1096,
        settings = AppSettings(
            fajrSettings = PrayerNotificationSettings(prePrayerReminderMinutes = 0),
            dhuhrSettings = PrayerNotificationSettings(enabled = false),
            asrSettings = PrayerNotificationSettings(prePrayerReminderMinutes = 30),
            ishaSettings = PrayerNotificationSettings(prePrayerReminderMinutes = 0),
            jumuaSettings = PrayerNotificationSettings(prePrayerReminderMinutes = 60),
            timeAdjustments = PrayerTimeAdjustments(fajrAdjustment = -7, maghribAdjustment = 3, ishaAdjustment = 30),
            silence = SilenceSettings(asr = false, jumua = true)
        )
    )

    @Test fun `random reboots and re-plans from the app, three years`() =
        simulate(london, LocalDate.of(2026, 10, 8), days = 1096, disruptions = Random(7))

    @Test fun `random reboots and re-plans in Oslo, three years`() =
        simulate(oslo, LocalDate.of(2026, 10, 8), days = 1096, disruptions = Random(11))

    // region Simulation

    private data class Event(val at: ZonedDateTime, val what: String)

    private fun simulate(
        place: Place,
        start: LocalDate,
        days: Int,
        settings: AppSettings = AppSettings(),
        disruptions: Random? = null
    ) = runBlocking {
        val zone = ZoneId.of(place.zone)
        val location = CurrentLocation(place.name, place.latitude, place.longitude, place.country, place.zone)
        val clock = Clock(start.atTime(0, 0, 30).atZone(zone))
        val alarms = SimulatedAlarms(clock)
        val settingsStore = SettingsStore(InMemoryKeyValueStore()).also { it.save(settings) }
        val storage = InMemoryLocationStorage().also { it.saveCurrentLocation(location) }
        val prayerTimes = PrayerTimesRepository(deviceCountry = { place.country })
        val log = mutableListOf<Event>()
        val silencer = RecordingSilencer(clock, log)
        val handler = PrayerAlarmHandler(
            LocationService(NoPositions, NoGeocoder, storage, FixedRegion(place)),
            prayerTimes,
            settingsStore,
            ExactAlarmScheduler(ApplicationProvider.getApplicationContext(), alarms, canScheduleExactAlarms = { true }),
            RecordingNotifier(clock, log),
            silencer,
            SilenceState(InMemoryKeyValueStore()),
            now = { clock.now }
        )

        // The phone starts: the boot alarm plans the first day.
        handler.onDayChanged()
        val end = start.plusDays(days.toLong()).atStartOfDay(zone)
        val disruptionTimes = disruptions?.let { random ->
            (0 until days / 3).map {
                start.atStartOfDay(zone).plusMinutes(random.nextLong(days * 24L * 60))
            }.sorted().toMutableList()
        } ?: mutableListOf()

        while (true) {
            val next = alarms.next()
                ?: error("${place.name}: no alarm pending at ${clock.now}, the daily chain broke")
            val nextAt = Instant.ofEpochMilli(next.at).atZone(zone)
            // A reboot or an app re-plan happening before the next alarm.
            val disruption = disruptionTimes.firstOrNull()
            if (disruption != null && disruption.isBefore(nextAt) && disruption.isBefore(end)) {
                disruptionTimes.removeAt(0)
                clock.now = maxOf(clock.now, disruption)
                if (disruptions!!.nextBoolean()) {
                    alarms.reboot()
                    handler.onDayChanged() // BOOT_COMPLETED
                } else {
                    handler.onDayChanged() // what the app does when settings change or a place loads
                }
                continue
            }
            if (!nextAt.isBefore(end)) break
            alarms.remove(next)
            clock.now = maxOf(clock.now, nextAt)
            handleAlarm(handler, next.intent)
        }

        val expected = expectedEvents(place, prayerTimes, location, settings, start, end, zone)
        println("${place.name} from $start, $days days: ${expected.size} events checked, ${log.size} happened")
        assertTrue("${place.name}: too few events to mean anything (${expected.size})", expected.size >= days * 2)
        val firstPlanned = start.atTime(0, 0, 30).atZone(zone)
        val actual = log.filter { it.at.isAfter(firstPlanned) }
        val missing = expected - actual.toSet()
        val extra = actual - expected.toSet()
        assertTrue(
            "${place.name}: ${missing.size} missing, ${extra.size} unexpected.\n" +
                "Missing: ${missing.take(8).joinToString("\n  ", prefix = "\n  ")}\n" +
                "Unexpected: ${extra.take(8).joinToString("\n  ", prefix = "\n  ")}",
            missing.isEmpty() && extra.isEmpty()
        )
        assertTrue("${place.name}: alarms set in the past: ${alarms.lateSets.take(5)}", alarms.lateSets.isEmpty())
        assertTrue("${place.name}: Do Not Disturb left on at the end", !silencer.on || silencer.endsAfter(end))
    }

    /**
     * What should happen, worked out from the calculated times only: each
     * enabled reminder at the adhan minus its lead (Jumua's on Fridays),
     * Do Not Disturb on at each adhan and off 35 min (Fajr), 60 min
     * (Jumua) or 15 min later. An evening time earlier than Dhuhr belongs
     * to the next calendar day (Isha after midnight in northern summers).
     */
    private fun expectedEvents(
        place: Place,
        prayerTimes: PrayerTimesRepository,
        location: CurrentLocation,
        settings: AppSettings,
        start: LocalDate,
        end: ZonedDateTime,
        zone: ZoneId
    ): List<Event> {
        val events = mutableListOf<Event>()
        val windows = mutableListOf<Pair<ZonedDateTime, ZonedDateTime>>()
        var date = start.minusDays(1)
        while (!date.isAfter(end.toLocalDate())) {
            val timings = runCatching { prayerTimes.day(date, location, settings).timings }.getOrNull()
            if (timings == null) {
                date = date.plusDays(1)
                continue
            }
            val dhuhr = LocalTime.parse(timings.dhuhr)
            for (prayer in PrayerMath.ORDER) {
                val shown = PrayerMath.prayerOn(prayer, date)
                val base = LocalTime.parse(PrayerMath.timeOf(timings, prayer))
                val dayOffset = when {
                    prayer in listOf("Maghrib", "Isha") && base.isBefore(dhuhr) -> 1L
                    prayer == "Fajr" && base.isAfter(dhuhr) -> -1L
                    else -> 0L
                }
                val adhan = date.plusDays(dayOffset).atTime(base).atZone(zone)
                    .plusMinutes(settings.timeAdjustments.getAdjustmentForPrayer(prayer).toLong())
                val reminder = settings.getSettingsForPrayer(shown)
                if (reminder.enabled) {
                    val lead = reminder.prePrayerReminderMinutes
                    events += Event(
                        adhan.minusMinutes(lead.toLong()),
                        "notify " + EnglishNotificationTexts.body(shown, adhan.toLocalTime().toString(), lead)
                    )
                }
                if (settings.silence.isOnFor(shown)) {
                    windows += adhan to adhan.plusMinutes(SilenceSettings.minutesFor(shown).toLong())
                }
            }
            date = date.plusDays(1)
        }
        // Overlapping silences (Jumua's hour reaching Asr in a northern
        // winter) are one silence: on at the first adhan, off at the last end.
        val firstPlanned = start.atTime(0, 0, 30).atZone(zone)
        windows.filter { it.first.isAfter(firstPlanned) }.sortedBy { it.first }.fold(mutableListOf<Pair<ZonedDateTime, ZonedDateTime>>()) { merged, window ->
            val last = merged.lastOrNull()
            if (last != null && !window.first.isAfter(last.second)) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, window.second)
            } else {
                merged += window
            }
            merged
        }.forEach { (on, off) ->
            events += Event(on, "dnd on")
            events += Event(off, "dnd off")
        }
        return events.filter { it.at.isAfter(firstPlanned) && it.at.isBefore(end) }.sortedBy { it.at }
    }

    private class Clock(var now: ZonedDateTime)

    /** AlarmManager's rules: one alarm per PendingIntent, the earliest fires first. */
    private class SimulatedAlarms(private val clock: Clock) : AlarmOps {
        data class Alarm(val key: Pair<Int, String?>, val at: Long, val intent: Intent)

        private val pending = mutableMapOf<Pair<Int, String?>, Alarm>()
        val lateSets = mutableListOf<String>()

        private fun keyOf(operation: PendingIntent): Pair<Int, String?> {
            val shadow = shadowOf(operation)
            return shadow.requestCode to shadow.savedIntent.action
        }

        private fun add(triggerMillis: Long, operation: PendingIntent) {
            if (triggerMillis < clock.now.toInstant().toEpochMilli()) {
                lateSets += "${Instant.ofEpochMilli(triggerMillis)} set at ${clock.now} (${shadowOf(operation).savedIntent.action})"
            }
            val key = keyOf(operation)
            pending[key] = Alarm(key, triggerMillis, Intent(shadowOf(operation).savedIntent))
        }

        override fun setExactAndAllowWhileIdle(type: Int, triggerMillis: Long, operation: PendingIntent) =
            add(triggerMillis, operation)

        override fun setAndAllowWhileIdle(type: Int, triggerMillis: Long, operation: PendingIntent) =
            add(triggerMillis, operation)

        override fun cancel(operation: PendingIntent) {
            pending.remove(keyOf(operation))
        }

        /**
         * The earliest alarm. Android doesn't order alarms due at the same
         * moment; here a silence's end goes last, so two silences that
         * touch read as one, like overlapping ones.
         */
        fun next(): Alarm? = pending.values.minWithOrNull(
            compareBy<Alarm>({ it.at }, { if (it.intent.action == ExactAlarmScheduler.ACTION_SILENCE_END) 1 else 0 })
        )
        fun remove(alarm: Alarm) { pending.remove(alarm.key) }
        /** A restart clears every alarm; BOOT_COMPLETED re-plans. */
        fun reboot() = pending.clear()
    }

    private class RecordingNotifier(private val clock: Clock, private val log: MutableList<Event>) : Notifier {
        override fun showPrayerNotification(id: Int, title: String, body: String) {
            log += Event(clock.now.withSecond(0).withNano(0), "notify $body")
        }
    }

    /** Logs real transitions only: on→on and off→off are not events. */
    private class RecordingSilencer(private val clock: Clock, private val log: MutableList<Event>) : Silencer {
        var on = false
        private var lastOn: ZonedDateTime? = null
        override fun available() = true
        override fun start() {
            if (!on) log += Event(clock.now.withSecond(0).withNano(0), "dnd on")
            on = true
            lastOn = clock.now
        }
        override fun stop() {
            if (on) log += Event(clock.now.withSecond(0).withNano(0), "dnd off")
            on = false
        }
        override fun remove() = Unit
        fun endsAfter(end: ZonedDateTime) = lastOn?.isAfter(end.minusHours(2)) == true
    }

    private object NoPositions : PositionProvider {
        override suspend fun currentFix() = PositionOutcome.NoFix
    }

    private object NoGeocoder : GeocodeProvider {
        override suspend fun lookup(latitude: Double, longitude: Double): GeocodedPlace? = null
    }

    private class FixedRegion(private val place: Place) : DeviceRegion {
        override fun countryCode() = place.country
        override fun timeZone() = place.zone
    }

    // endregion
}
