package com.example.prayernotifier.data.calculation

import com.example.prayernotifier.data.HijriDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerCalculatorTest {

    /** Times the Aladhan API gave for the same method, place and date. */
    private data class Reference(
        val city: String,
        val latitude: Double,
        val longitude: Double,
        val zone: String,
        val method: CalculationMethod,
        val date: String,
        val times: String
    )

    /**
     * Asr is left out: Aladhan's is up to 5 minutes off the sun's real
     * position (see the next test), and the app keeps the accurate one.
     */
    @Test
    fun `every method matches Aladhan within a minute`() {
        val failures = REFERENCES.flatMap { ref ->
            val day = PrayerCalculator.day(
                LocalDate.parse(ref.date), ref.latitude, ref.longitude, ZoneId.of(ref.zone), ref.method
            )
            val ours = with(day.timings) { listOf(fajr, dhuhr, asr, maghrib, isha) }
            val theirs = ref.times.split(" ")
            ours.zip(theirs).mapIndexedNotNull { index, (mine, expected) ->
                val diff = minutesBetween(mine, expected)
                if (PRAYERS[index] == "Asr" || diff <= 1) null
                else "${ref.city} ${ref.date} ${PRAYERS[index]}: $mine, Aladhan $expected"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Asr against a separate computation: NOAA solar positions, Asr when a
     * shadow is its object's length plus the noon shadow.
     */
    @Test
    fun `Asr matches the sun's position within a minute`() {
        listOf(
            Triple("2026-01-15", 51.5074 to -0.1278, "Europe/London") to "14:02",
            Triple("2026-10-08", 51.5074 to -0.1278, "Europe/London") to "15:44",
            Triple("2026-01-15", 55.7558 to 37.6173, "Europe/Moscow") to "14:11",
            Triple("2026-10-08", 55.7558 to 37.6173, "Europe/Moscow") to "15:05",
            Triple("2027-02-20", 48.8566 to 2.3522, "Europe/Paris") to "15:49",
            Triple("2026-10-08", 36.7538 to 3.0588, "Africa/Algiers") to "15:51"
        ).forEach { (place, expected) ->
            val (date, coordinates, zone) = place
            val asr = PrayerCalculator.day(
                LocalDate.parse(date), coordinates.first, coordinates.second, ZoneId.of(zone),
                CalculationMethod.MUSLIM_WORLD_LEAGUE
            ).timings.asr
            assertTrue("$zone $date: $asr, expected $expected", minutesBetween(asr, expected) <= 1)
        }
    }

    /** Same independent computation, for a shadow twice its object's length. */
    @Test
    fun `Hanafi Asr matches the sun's position within a minute`() {
        listOf(
            Triple("2026-01-15", 24.8607 to 67.0011, "Asia/Karachi") to "16:28",
            Triple("2026-06-21", 24.8607 to 67.0011, "Asia/Karachi") to "17:16",
            Triple("2026-10-08", 31.5204 to 74.3587, "Asia/Karachi") to "15:59",
            Triple("2027-02-20", 23.8103 to 90.4125, "Asia/Dhaka") to "16:19",
            Triple("2026-10-08", 51.5074 to -0.1278, "Europe/London") to "16:29"
        ).forEach { (place, expected) ->
            val (date, coordinates, zone) = place
            val asr = PrayerCalculator.day(
                LocalDate.parse(date), coordinates.first, coordinates.second, ZoneId.of(zone),
                CalculationMethod.KARACHI, AsrMethod.HANAFI
            ).timings.asr
            assertTrue("$zone $date: $asr, expected $expected", minutesBetween(asr, expected) <= 1)
        }
    }

    @Test
    fun `Hanafi changes only Asr`() {
        fun karachi(asr: AsrMethod) = PrayerCalculator.day(
            LocalDate.of(2026, 10, 8), 24.8607, 67.0011, ZoneId.of("Asia/Karachi"), CalculationMethod.KARACHI, asr
        ).timings
        val standard = karachi(AsrMethod.STANDARD)
        val hanafi = karachi(AsrMethod.HANAFI)
        assertEquals(standard.copy(asr = hanafi.asr), hanafi)
        assertTrue(LocalTime.parse(hanafi.asr).isAfter(LocalTime.parse(standard.asr)))
    }

    @Test
    fun `Turkey adds Diyanet's temkin minutes`() {
        fun istanbul(method: CalculationMethod) = PrayerCalculator.day(
            LocalDate.of(2026, 10, 8), 41.0082, 28.9784, ZoneId.of("Europe/Istanbul"), method
        ).timings
        val plain = istanbul(CalculationMethod.MUSLIM_WORLD_LEAGUE)
        val diyanet = istanbul(CalculationMethod.TURKEY)
        assertEquals(5, minutesBetween(plain.dhuhr, diyanet.dhuhr))
        assertEquals(4, minutesBetween(plain.asr, diyanet.asr))
        assertEquals(7, minutesBetween(plain.maghrib, diyanet.maghrib))
    }

    @Test
    fun `month has one day per date, in order`() {
        val days = PrayerCalculator.month(
            YearMonth.of(2027, 2), 36.7538, 3.0588, ZoneId.of("Africa/Algiers"), CalculationMethod.ALGERIA
        )
        assertEquals(28, days.size)
        assertEquals((1..28).map { LocalDate.of(2027, 2, it) }, days.map { it.date })
    }

    @Test
    fun `times are shown in the place's own time zone`() {
        val date = LocalDate.of(2026, 10, 8)
        val local = PrayerCalculator.day(date, 21.4225, 39.8262, ZoneId.of("Asia/Riyadh"), CalculationMethod.UMM_AL_QURA)
        val utc = PrayerCalculator.day(date, 21.4225, 39.8262, ZoneId.of("UTC"), CalculationMethod.UMM_AL_QURA)
        assertEquals(180, minutesBetween(local.timings.dhuhr, utc.timings.dhuhr))
    }

    @Test
    fun `Umm al-Qura Isha is 30 minutes later in Ramadan only`() {
        fun ishaGap(date: LocalDate): Long {
            val t = PrayerCalculator.day(date, 21.4225, 39.8262, ZoneId.of("Asia/Riyadh"), CalculationMethod.UMM_AL_QURA).timings
            return ChronoUnit.MINUTES.between(LocalTime.parse(t.maghrib), LocalTime.parse(t.isha))
        }
        assertEquals(120, ishaGap(LocalDate.of(2027, 2, 20)))
        assertEquals(90, ishaGap(LocalDate.of(2027, 4, 20)))
    }

    @Test
    fun `Hijri date follows Umm al-Qura and applies the correction`() {
        assertEquals(HijriDate(27, 4, 1448), PrayerCalculator.hijri(LocalDate.of(2026, 10, 8)))
        assertEquals(HijriDate(28, 4, 1448), PrayerCalculator.hijri(LocalDate.of(2026, 10, 8), offsetDays = 1))
        // Corrections cross month ends instead of producing day 0 or 31.
        val first = PrayerCalculator.hijri(LocalDate.of(2026, 10, 8), offsetDays = -26)
        val lastOfPrevious = PrayerCalculator.hijri(LocalDate.of(2026, 10, 8), offsetDays = -27)
        assertEquals(1, first?.day)
        assertEquals(3, lastOfPrevious?.month)
    }

    @Test
    fun `Hijri date is null outside the calendar's range`() {
        assertNull(PrayerCalculator.hijri(LocalDate.of(1800, 1, 1)))
    }

    private fun minutesBetween(a: String, b: String): Long {
        val diff = Math.abs(ChronoUnit.MINUTES.between(LocalTime.parse(a), LocalTime.parse(b)))
        return minOf(diff, 24 * 60 - diff)
    }

    private companion object {
        val PRAYERS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

        // api.aladhan.com/v1/timings with the matching method id, Shafi'i Asr,
        // angle-based high-latitude rule, fetched 2026-10-08.
        val REFERENCES = listOf(
        Reference("Algiers", 36.7538, 3.0588, "Africa/Algiers", CalculationMethod.ALGERIA, "2026-01-15", "06:28 12:57 15:35 17:55 19:21"),
        Reference("Algiers", 36.7538, 3.0588, "Africa/Algiers", CalculationMethod.ALGERIA, "2026-06-21", "03:37 12:50 16:41 20:10 21:54"),
        Reference("Algiers", 36.7538, 3.0588, "Africa/Algiers", CalculationMethod.ALGERIA, "2026-10-08", "05:23 12:35 15:52 18:21 19:42"),
        Reference("Algiers", 36.7538, 3.0588, "Africa/Algiers", CalculationMethod.ALGERIA, "2027-02-20", "06:04 13:01 16:06 18:33 19:54"),
        Reference("Rabat", 34.0209, -6.8416, "Africa/Casablanca", CalculationMethod.MOROCCO, "2026-01-15", "07:00 13:42 16:21 18:46 20:04"),
        Reference("Rabat", 34.0209, -6.8416, "Africa/Casablanca", CalculationMethod.MOROCCO, "2026-06-21", "04:25 13:34 17:14 20:47 22:20"),
        // Rabat 2026-10-08 left out: time-zone databases disagree on Morocco's clock that day.
        Reference("Rabat", 34.0209, -6.8416, "Africa/Casablanca", CalculationMethod.MOROCCO, "2027-02-20", "05:39 12:46 15:49 18:21 19:34"),
        Reference("Tunis", 36.8065, 10.1815, "Africa/Tunis", CalculationMethod.TUNISIA, "2026-01-15", "06:00 12:29 15:06 17:27 18:58"),
        Reference("Tunis", 36.8065, 10.1815, "Africa/Tunis", CalculationMethod.TUNISIA, "2026-06-21", "03:08 12:21 16:13 19:42 21:34"),
        Reference("Tunis", 36.8065, 10.1815, "Africa/Tunis", CalculationMethod.TUNISIA, "2026-10-08", "04:54 12:07 15:24 17:53 19:19"),
        Reference("Tunis", 36.8065, 10.1815, "Africa/Tunis", CalculationMethod.TUNISIA, "2027-02-20", "05:36 12:33 15:37 18:04 19:31"),
        Reference("Cairo", 30.0444, 31.2357, "Africa/Cairo", CalculationMethod.EGYPT, "2026-01-15", "05:21 12:04 14:57 17:17 18:39"),
        Reference("Cairo", 30.0444, 31.2357, "Africa/Cairo", CalculationMethod.EGYPT, "2026-06-21", "04:08 12:57 16:32 19:59 21:33"),
        Reference("Cairo", 30.0444, 31.2357, "Africa/Cairo", CalculationMethod.EGYPT, "2026-10-08", "05:26 12:43 16:04 18:32 19:49"),
        Reference("Cairo", 30.0444, 31.2357, "Africa/Cairo", CalculationMethod.EGYPT, "2027-02-20", "05:04 12:09 15:20 17:47 19:05"),
        Reference("Makkah", 21.4225, 39.8262, "Asia/Riyadh", CalculationMethod.UMM_AL_QURA, "2026-01-15", "05:41 12:30 15:37 17:59 19:29"),
        Reference("Makkah", 21.4225, 39.8262, "Asia/Riyadh", CalculationMethod.UMM_AL_QURA, "2026-06-21", "04:11 12:22 15:42 19:06 20:36"),
        Reference("Makkah", 21.4225, 39.8262, "Asia/Riyadh", CalculationMethod.UMM_AL_QURA, "2026-10-08", "04:58 12:08 15:31 18:02 19:32"),
        Reference("Makkah", 21.4225, 39.8262, "Asia/Riyadh", CalculationMethod.UMM_AL_QURA, "2027-02-20", "05:32 12:34 15:52 18:21 20:21"),
        Reference("Doha", 25.2854, 51.531, "Asia/Qatar", CalculationMethod.QATAR, "2026-01-15", "05:01 11:43 14:44 17:05 18:35"),
        Reference("Doha", 25.2854, 51.531, "Asia/Qatar", CalculationMethod.QATAR, "2026-06-21", "03:15 11:36 14:58 18:27 19:57"),
        Reference("Doha", 25.2854, 51.531, "Asia/Qatar", CalculationMethod.QATAR, "2026-10-08", "04:13 11:21 14:44 17:14 18:44"),
        Reference("Doha", 25.2854, 51.531, "Asia/Qatar", CalculationMethod.QATAR, "2027-02-20", "04:48 11:48 15:03 17:31 19:01"),
        Reference("Dubai", 25.2048, 55.2708, "Asia/Dubai", CalculationMethod.DUBAI, "2026-01-15", "05:45 12:31 15:29 17:54 19:12"),
        Reference("Dubai", 25.2048, 55.2708, "Asia/Dubai", CalculationMethod.DUBAI, "2026-06-21", "03:59 12:24 15:43 19:15 20:43"),
        Reference("Dubai", 25.2048, 55.2708, "Asia/Dubai", CalculationMethod.DUBAI, "2026-10-08", "04:57 12:09 15:29 18:02 19:16"),
        Reference("Dubai", 25.2048, 55.2708, "Asia/Dubai", CalculationMethod.DUBAI, "2027-02-20", "05:32 12:36 15:48 18:19 19:33"),
        Reference("Kuwait City", 29.3759, 47.9774, "Asia/Kuwait", CalculationMethod.KUWAIT, "2026-01-15", "05:20 11:57 14:51 17:12 18:33"),
        Reference("Kuwait City", 29.3759, 47.9774, "Asia/Kuwait", CalculationMethod.KUWAIT, "2026-06-21", "03:13 11:50 15:24 18:51 20:23"),
        Reference("Kuwait City", 29.3759, 47.9774, "Asia/Kuwait", CalculationMethod.KUWAIT, "2026-10-08", "04:26 11:36 14:57 17:26 18:42"),
        Reference("Kuwait City", 29.3759, 47.9774, "Asia/Kuwait", CalculationMethod.KUWAIT, "2027-02-20", "05:04 12:02 15:14 17:41 18:58"),
        Reference("Amman", 31.9454, 35.9284, "Asia/Amman", CalculationMethod.JORDAN, "2026-01-15", "06:11 12:46 15:34 18:00 19:20"),
        Reference("Amman", 31.9454, 35.9284, "Asia/Amman", CalculationMethod.JORDAN, "2026-06-21", "03:51 12:38 16:18 19:50 21:26"),
        Reference("Amman", 31.9454, 35.9284, "Asia/Amman", CalculationMethod.JORDAN, "2026-10-08", "05:14 12:24 15:44 18:18 19:34"),
        Reference("Amman", 31.9454, 35.9284, "Asia/Amman", CalculationMethod.JORDAN, "2027-02-20", "05:52 12:50 16:00 18:32 19:48"),
        Reference("Istanbul", 41.0082, 28.9784, "Europe/Istanbul", CalculationMethod.TURKEY, "2026-01-15", "06:50 13:18 15:44 18:07 19:32"),
        Reference("Istanbul", 41.0082, 28.9784, "Europe/Istanbul", CalculationMethod.TURKEY, "2026-06-21", "03:24 13:11 17:11 20:47 22:38"),
        Reference("Istanbul", 41.0082, 28.9784, "Europe/Istanbul", CalculationMethod.TURKEY, "2026-10-08", "05:36 12:57 16:09 18:42 20:01"),
        Reference("Istanbul", 41.0082, 28.9784, "Europe/Istanbul", CalculationMethod.TURKEY, "2027-02-20", "06:20 13:23 16:19 18:51 20:11"),
        Reference("Tehran", 35.6892, 51.389, "Asia/Tehran", CalculationMethod.TEHRAN, "2026-01-15", "05:45 12:14 14:54 17:34 18:24"),
        Reference("Tehran", 35.6892, 51.389, "Asia/Tehran", CalculationMethod.TEHRAN, "2026-06-21", "03:02 12:06 15:55 19:45 20:44"),
        Reference("Tehran", 35.6892, 51.389, "Asia/Tehran", CalculationMethod.TEHRAN, "2026-10-08", "04:42 11:52 15:10 17:57 18:44"),
        Reference("Tehran", 35.6892, 51.389, "Asia/Tehran", CalculationMethod.TEHRAN, "2027-02-20", "05:22 12:18 15:23 18:09 18:56"),
        Reference("Karachi", 24.8607, 67.0011, "Asia/Karachi", CalculationMethod.KARACHI, "2026-01-15", "05:58 12:41 15:43 18:04 19:24"),
        Reference("Karachi", 24.8607, 67.0011, "Asia/Karachi", CalculationMethod.KARACHI, "2026-06-21", "04:14 12:34 15:55 19:24 20:53"),
        Reference("Karachi", 24.8607, 67.0011, "Asia/Karachi", CalculationMethod.KARACHI, "2026-10-08", "05:11 12:20 15:42 18:12 19:28"),
        Reference("Karachi", 24.8607, 67.0011, "Asia/Karachi", CalculationMethod.KARACHI, "2027-02-20", "05:46 12:46 16:01 18:29 19:45"),
        Reference("Moscow", 55.7558, 37.6173, "Europe/Moscow", CalculationMethod.RUSSIA, "2026-01-15", "06:49 12:39 14:07 16:29 18:22"),
        Reference("Moscow", 55.7558, 37.6173, "Europe/Moscow", CalculationMethod.RUSSIA, "2026-06-21", "02:02 12:31 17:03 21:18 22:55"),
        Reference("Moscow", 55.7558, 37.6173, "Europe/Moscow", CalculationMethod.RUSSIA, "2026-10-08", "04:57 12:17 15:08 17:47 19:29"),
        Reference("Moscow", 55.7558, 37.6173, "Europe/Moscow", CalculationMethod.RUSSIA, "2027-02-20", "05:54 12:43 15:05 17:44 19:26"),
        Reference("Paris", 48.8566, 2.3522, "Europe/Paris", CalculationMethod.FRANCE, "2026-01-15", "07:24 13:00 15:00 17:22 18:37"),
        Reference("Paris", 48.8566, 2.3522, "Europe/Paris", CalculationMethod.FRANCE, "2026-06-21", "04:13 13:52 18:10 21:58 23:32"),
        Reference("Paris", 48.8566, 2.3522, "Europe/Paris", CalculationMethod.FRANCE, "2026-10-08", "06:52 13:38 16:41 19:15 20:23"),
        Reference("Paris", 48.8566, 2.3522, "Europe/Paris", CalculationMethod.FRANCE, "2027-02-20", "06:42 13:04 15:46 18:19 19:28"),
        Reference("Lisbon", 38.7223, -9.1393, "Europe/Lisbon", CalculationMethod.PORTUGAL, "2026-01-15", "06:19 12:51 15:19 17:42 18:59"),
        Reference("Lisbon", 38.7223, -9.1393, "Europe/Lisbon", CalculationMethod.PORTUGAL, "2026-06-21", "04:14 13:43 17:34 21:08 22:25"),
        Reference("Lisbon", 38.7223, -9.1393, "Europe/Lisbon", CalculationMethod.PORTUGAL, "2026-10-08", "06:11 13:29 16:39 19:12 20:29"),
        Reference("Lisbon", 38.7223, -9.1393, "Europe/Lisbon", CalculationMethod.PORTUGAL, "2027-02-20", "05:53 12:55 15:52 18:22 19:39"),
        Reference("Singapore", 1.3521, 103.8198, "Asia/Singapore", CalculationMethod.SINGAPORE, "2026-01-15", "05:50 13:14 16:38 19:16 20:29"),
        Reference("Singapore", 1.3521, 103.8198, "Asia/Singapore", CalculationMethod.SINGAPORE, "2026-06-21", "05:36 13:06 16:33 19:13 20:28"),
        Reference("Singapore", 1.3521, 103.8198, "Asia/Singapore", CalculationMethod.SINGAPORE, "2026-10-08", "05:33 12:52 16:05 18:55 20:04"),
        Reference("Singapore", 1.3521, 103.8198, "Asia/Singapore", CalculationMethod.SINGAPORE, "2027-02-20", "05:58 13:18 16:35 19:21 20:31"),
        Reference("Kuala Lumpur", 3.139, 101.6869, "Asia/Kuala_Lumpur", CalculationMethod.MALAYSIA, "2026-01-15", "06:01 13:23 16:46 19:21 20:35"),
        Reference("Kuala Lumpur", 3.139, 101.6869, "Asia/Kuala_Lumpur", CalculationMethod.MALAYSIA, "2026-06-21", "05:41 13:15 16:42 19:24 20:40"),
        Reference("Kuala Lumpur", 3.139, 101.6869, "Asia/Kuala_Lumpur", CalculationMethod.MALAYSIA, "2026-10-08", "05:42 13:01 16:16 19:03 20:12"),
        Reference("Kuala Lumpur", 3.139, 101.6869, "Asia/Kuala_Lumpur", CalculationMethod.MALAYSIA, "2027-02-20", "06:08 13:27 16:45 19:28 20:38"),
        Reference("Jakarta", -6.2088, 106.8456, "Asia/Jakarta", CalculationMethod.INDONESIA, "2026-01-15", "04:25 12:02 15:26 18:15 19:30"),
        Reference("Jakarta", -6.2088, 106.8456, "Asia/Jakarta", CalculationMethod.INDONESIA, "2026-06-21", "04:38 11:54 15:16 17:47 19:02"),
        Reference("Jakarta", -6.2088, 106.8456, "Asia/Jakarta", CalculationMethod.INDONESIA, "2026-10-08", "04:17 11:40 14:42 17:46 18:56"),
        Reference("Jakarta", -6.2088, 106.8456, "Asia/Jakarta", CalculationMethod.INDONESIA, "2027-02-20", "04:39 12:06 15:16 18:15 19:25"),
        Reference("New York", 40.7128, -74.006, "America/New_York", CalculationMethod.NORTH_AMERICA, "2026-01-15", "05:58 12:06 14:33 16:53 18:14"),
        Reference("New York", 40.7128, -74.006, "America/New_York", CalculationMethod.NORTH_AMERICA, "2026-06-21", "03:45 12:58 16:58 20:31 22:11"),
        Reference("New York", 40.7128, -74.006, "America/New_York", CalculationMethod.NORTH_AMERICA, "2026-10-08", "05:45 12:43 15:56 18:27 19:41"),
        Reference("New York", 40.7128, -74.006, "America/New_York", CalculationMethod.NORTH_AMERICA, "2027-02-20", "05:28 12:10 15:08 17:37 18:52"),
        Reference("London", 51.5074, -0.1278, "Europe/London", CalculationMethod.MUSLIM_WORLD_LEAGUE, "2026-01-15", "05:59 12:10 13:59 16:21 18:15"),
        Reference("London", 51.5074, -0.1278, "Europe/London", CalculationMethod.MUSLIM_WORLD_LEAGUE, "2026-06-21", "02:31 13:02 17:25 21:22 23:27"),
        Reference("London", 51.5074, -0.1278, "Europe/London", CalculationMethod.MUSLIM_WORLD_LEAGUE, "2026-10-08", "05:21 12:48 15:46 18:23 20:07"),
        Reference("London", 51.5074, -0.1278, "Europe/London", CalculationMethod.MUSLIM_WORLD_LEAGUE, "2027-02-20", "05:14 12:14 14:49 17:24 19:09"),
        Reference("Oslo", 59.9139, 10.7522, "Europe/Oslo", CalculationMethod.MUSLIM_WORLD_LEAGUE, "2026-01-15", "06:28 12:26 13:30 15:50 18:18"),
        Reference("Oslo", 59.9139, 10.7522, "Europe/Oslo", CalculationMethod.MUSLIM_WORLD_LEAGUE, "2026-06-21", "02:21 13:19 18:00 22:44 00:12"),
        Reference("Oslo", 59.9139, 10.7522, "Europe/Oslo", CalculationMethod.MUSLIM_WORLD_LEAGUE, "2026-10-08", "05:20 13:05 15:45 18:29 20:39"),
        Reference("Oslo", 59.9139, 10.7522, "Europe/Oslo", CalculationMethod.MUSLIM_WORLD_LEAGUE, "2027-02-20", "05:23 12:31 14:38 17:21 19:32"),
        )
    }
}
