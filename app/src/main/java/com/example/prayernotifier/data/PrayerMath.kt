package com.example.prayernotifier.data

import com.example.prayernotifier.data.persistence.PrayerTimeAdjustments
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

data class Countdown(val nextPrayer: String, val remaining: Duration)

/**
 * Pure prayer-time math. Ported from the three copies scattered across the
 * Flutter app (`home_screen`, `settings_screen`, `prayer_countdown`) into one
 * tested place. All functions take `now` explicitly so tests control time.
 *
 * Note: the Flutter version mishandles negative adjustments near midnight
 * (e.g. 00:05 minus 10 produced the invalid "24:55"); this port wraps
 * correctly ("23:55").
 */
object PrayerMath {

    val ORDER = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    /** Friday's prayer at Dhuhr time, with its own reminder and silence. */
    const val JUMUA = "Jumua"

    /** The prayer as it is on that date: Dhuhr is Jumua on Fridays. */
    fun prayerOn(prayer: String, date: LocalDate): String =
        if (prayer == "Dhuhr" && date.dayOfWeek == DayOfWeek.FRIDAY) JUMUA else prayer

    /**
     * Shifts a "HH:mm" time by minutes, wrapping around midnight. Always
     * Western digits, like the calculated times, whatever the language.
     */
    fun adjustTime(time: String, minutes: Int): String {
        if (minutes == 0) return time
        val parts = time.split(":")
        val total = parts[0].toInt() * 60 + parts[1].toInt() + minutes
        val wrapped = Math.floorMod(total, 24 * 60)
        return String.format(Locale.ROOT, "%02d:%02d", wrapped / 60, wrapped % 60)
    }

    /** One prayer's "HH:mm" time, before any correction. Jumua is at Dhuhr's. */
    fun timeOf(timings: PrayerTimings, prayer: String): String = when (prayer) {
        "Fajr" -> timings.fajr
        "Dhuhr", JUMUA -> timings.dhuhr
        "Asr" -> timings.asr
        "Maghrib" -> timings.maghrib
        "Isha" -> timings.isha
        else -> error("Unknown prayer: $prayer")
    }

    /**
     * Adjusted date-time of one prayer of a given date. Far north in summer,
     * Isha (even Maghrib) can fall after midnight: an evening time earlier
     * than Dhuhr is on the next calendar day, and a Fajr later than Dhuhr on
     * the one before. A correction that crosses midnight moves the date too.
     */
    fun dateTimeFor(
        timings: PrayerTimings,
        adjustments: PrayerTimeAdjustments,
        prayer: String,
        date: LocalDate
    ): LocalDateTime {
        val base = LocalTime.parse(timeOf(timings, prayer))
        val dhuhr = LocalTime.parse(timings.dhuhr)
        val dayOffset = when {
            prayer in EVENING && base.isBefore(dhuhr) -> 1L
            prayer == "Fajr" && base.isAfter(dhuhr) -> -1L
            else -> 0L
        }
        return date.plusDays(dayOffset).atTime(base)
            .plusMinutes(adjustments.getAdjustmentForPrayer(prayer).toLong())
    }

    private val EVENING = setOf("Maghrib", "Isha")

    /** First prayer strictly after `now`, or null when today's are all past. */
    fun nextPrayer(
        timings: PrayerTimings,
        adjustments: PrayerTimeAdjustments,
        now: LocalDateTime
    ): String? {
        val date = now.toLocalDate()
        return ORDER.firstOrNull { dateTimeFor(timings, adjustments, it, date).isAfter(now) }
    }

    /**
     * Time left until the next prayer. After Isha the answer is tomorrow's
     * Fajr — the countdown never shows zero/empty.
     */
    fun countdown(
        timings: PrayerTimings,
        adjustments: PrayerTimeAdjustments,
        now: LocalDateTime
    ): Countdown {
        val next = nextPrayer(timings, adjustments, now)
        if (next != null) {
            val at = dateTimeFor(timings, adjustments, next, now.toLocalDate())
            return Countdown(next, Duration.between(now, at))
        }
        val tomorrowFajr = dateTimeFor(timings, adjustments, "Fajr", now.toLocalDate().plusDays(1))
        return Countdown("Fajr", Duration.between(now, tomorrowFajr))
    }
}
