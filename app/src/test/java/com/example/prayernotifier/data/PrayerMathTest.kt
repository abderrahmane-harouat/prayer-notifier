package com.example.prayernotifier.data

import com.example.prayernotifier.data.persistence.PrayerTimeAdjustments
import java.time.Duration
import java.time.LocalDateTime
import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrayerMathTest {

    private val timings = PrayerTimings(
        fajr = "05:12", dhuhr = "12:45", asr = "16:10",
        maghrib = "18:52", isha = "20:20"
    )
    private val none = PrayerTimeAdjustments()

    private fun now(hour: Int, minute: Int, day: Int = 15): LocalDateTime =
        LocalDateTime.of(2026, Month.SEPTEMBER, day, hour, minute)

    //region adjustTime

    @Test fun `zero minutes returns input untouched`() {
        assertEquals("05:12", PrayerMath.adjustTime("05:12", 0))
    }

    @Test fun `adds minutes`() {
        assertEquals("05:17", PrayerMath.adjustTime("05:12", 5))
    }

    @Test fun `subtracts minutes`() {
        assertEquals("05:07", PrayerMath.adjustTime("05:12", -5))
    }

    @Test fun `pads single digits`() {
        assertEquals("05:07", PrayerMath.adjustTime("5:2", 5))
    }

    @Test fun `wraps forward past midnight`() {
        assertEquals("00:20", PrayerMath.adjustTime("23:50", 30))
    }

    @Test fun `wraps a full day forward`() {
        assertEquals("00:20", PrayerMath.adjustTime("23:50", 30 + 24 * 60))
    }

    @Test fun `wraps backward past midnight`() {
        // The Flutter version produced the invalid "24:55" here.
        assertEquals("23:55", PrayerMath.adjustTime("00:05", -10))
    }

    @Test fun `wraps a full day backward`() {
        assertEquals("23:55", PrayerMath.adjustTime("00:05", -10 - 24 * 60))
    }

    @Test fun `large positive shift`() {
        assertEquals("07:12", PrayerMath.adjustTime("05:12", 120))
    }

    //endregion

    //region nextPrayer

    @Test fun `before fajr the next is fajr`() {
        assertEquals("Fajr", PrayerMath.nextPrayer(timings, none, now(4, 0)))
    }

    @Test fun `between prayers picks the coming one`() {
        assertEquals("Dhuhr", PrayerMath.nextPrayer(timings, none, now(6, 0)))
        assertEquals("Asr", PrayerMath.nextPrayer(timings, none, now(13, 0)))
        assertEquals("Maghrib", PrayerMath.nextPrayer(timings, none, now(17, 0)))
        assertEquals("Isha", PrayerMath.nextPrayer(timings, none, now(19, 0)))
    }

    @Test fun `after isha there is no next today`() {
        assertNull(PrayerMath.nextPrayer(timings, none, now(21, 0)))
    }

    @Test fun `exact prayer moment counts as past`() {
        // Strictly-after, same as Flutter: at 12:45 the next is Asr.
        assertEquals("Asr", PrayerMath.nextPrayer(timings, none, now(12, 45)))
    }

    @Test fun `adjustments move the boundary`() {
        val shifted = PrayerTimeAdjustments(fajrAdjustment = 120)
        // Fajr moved 05:12 -> 07:12, so at 05:30 Fajr is still coming.
        assertEquals("Fajr", PrayerMath.nextPrayer(timings, shifted, now(5, 30)))
        // Without the adjustment it would be Dhuhr.
        assertEquals("Dhuhr", PrayerMath.nextPrayer(timings, none, now(5, 30)))
    }

    @Test fun `negative adjustment pulls prayer earlier`() {
        val shifted = PrayerTimeAdjustments(dhuhrAdjustment = -60)
        // Dhuhr moved 12:45 -> 11:45, so at 12:00 it is past.
        assertEquals("Asr", PrayerMath.nextPrayer(timings, shifted, now(12, 0)))
    }

    //endregion

    //region countdown

    @Test fun `counts down to coming prayer`() {
        val result = PrayerMath.countdown(timings, none, now(12, 0))
        assertEquals("Dhuhr", result.nextPrayer)
        assertEquals(Duration.ofMinutes(45), result.remaining)
    }

    @Test fun `counts seconds too`() {
        val at = LocalDateTime.of(2026, Month.SEPTEMBER, 15, 12, 44, 30)
        val result = PrayerMath.countdown(timings, none, at)
        assertEquals("Dhuhr", result.nextPrayer)
        assertEquals(Duration.ofSeconds(30), result.remaining)
    }

    @Test fun `after isha counts to tomorrow fajr`() {
        val result = PrayerMath.countdown(timings, none, now(21, 0))
        assertEquals("Fajr", result.nextPrayer)
        assertEquals(Duration.ofHours(8).plusMinutes(12), result.remaining)
    }

    @Test fun `just before midnight rolls to next day`() {
        val result = PrayerMath.countdown(timings, none, now(23, 59))
        assertEquals("Fajr", result.nextPrayer)
        assertEquals(Duration.ofHours(5).plusMinutes(13), result.remaining)
    }

    @Test fun `countdown honors adjustments`() {
        val shifted = PrayerTimeAdjustments(maghribAdjustment = 10)
        val result = PrayerMath.countdown(timings, shifted, now(18, 50))
        assertEquals("Maghrib", result.nextPrayer)
        assertEquals(Duration.ofMinutes(12), result.remaining)
    }

    //endregion

    //region hijri

    //endregion
}
