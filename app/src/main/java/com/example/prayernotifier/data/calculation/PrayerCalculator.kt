package com.example.prayernotifier.data.calculation

import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.HighLatitudeRule
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerAdjustments
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.example.prayernotifier.data.HijriDate
import com.example.prayernotifier.data.PrayerDay
import com.example.prayernotifier.data.PrayerTimings
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Date

/**
 * Prayer times from the sun's position, on the device: no network, no
 * stored tables, any date. Astronomy by the Adhan library; Asr by the
 * shadow-length rule of the majority (Shafi'i, Maliki, Hanbali), and the
 * twilight-angle rule at high latitudes, as the Aladhan API did.
 */
object PrayerCalculator {

    private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private const val RAMADAN = 9

    /** One day's times for a place, in the place's time zone. */
    fun day(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        method: CalculationMethod
    ): PrayerDay {
        val coordinates = Coordinates(latitude, longitude)
        val components = DateComponents(date.year, date.monthValue, date.dayOfMonth)
        val times = PrayerTimes(coordinates, components, parametersFor(method))
        val maghrib = when (val angle = method.maghribAngle) {
            // The evening moment the sun reaches the angle: what Adhan
            // computes as Isha for an Isha angle of that size.
            null -> times.maghrib?.plusMinutes(method.maghribMinutes)
            else -> PrayerTimes(coordinates, components, twilight(angle)).isha
        }
        val isha = if (method.ishaMinutes > 0) {
            val ramadan = hijri(date)?.month == RAMADAN
            maghrib?.plusMinutes(method.ishaMinutes + if (ramadan) method.ramadanIshaExtraMinutes else 0)
        } else {
            times.isha
        }
        fun format(time: Date?): String = time?.toInstant()?.atZone(zone)?.format(HH_MM)
            ?: throw UncalculableTimesException(date)
        return PrayerDay(
            date = date,
            timings = PrayerTimings(
                fajr = format(times.fajr),
                dhuhr = format(times.dhuhr),
                asr = format(times.asr),
                maghrib = format(maghrib),
                isha = format(isha)
            )
        )
    }

    /** Every day of a month, in order. */
    fun month(
        month: YearMonth,
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        method: CalculationMethod
    ): List<PrayerDay> = (1..month.lengthOfMonth()).map {
        day(month.atDay(it), latitude, longitude, zone, method)
    }

    /**
     * The Umm al-Qura Hijri date of a day, shifted by the user's correction
     * in days. Null outside the calendar's range (about 1882 to 2174).
     */
    fun hijri(date: LocalDate, offsetDays: Int = 0): HijriDate? = runCatching {
        val hijrah = HijrahDate.from(date.plusDays(offsetDays.toLong()))
        HijriDate(
            day = hijrah.get(ChronoField.DAY_OF_MONTH),
            month = hijrah.get(ChronoField.MONTH_OF_YEAR),
            year = hijrah.get(ChronoField.YEAR)
        )
    }.getOrNull()

    private fun parametersFor(method: CalculationMethod): CalculationParameters {
        val parameters = if (method.ishaMinutes > 0) {
            // Isha is set from Maghrib above; the angle here is unused.
            CalculationParameters(method.fajrAngle, method.ishaMinutes)
        } else {
            CalculationParameters(method.fajrAngle, method.ishaAngle)
        }
        parameters.madhab = Madhab.SHAFI
        parameters.highLatitudeRule = HighLatitudeRule.TWILIGHT_ANGLE
        // Maghrib's offset is applied above, where Isha intervals build on it.
        parameters.methodAdjustments = PrayerAdjustments(0, 0, method.dhuhrMinutes, method.asrMinutes, 0, 0)
        return parameters
    }

    private fun twilight(angle: Double) = CalculationParameters(0.0, angle).apply {
        highLatitudeRule = HighLatitudeRule.TWILIGHT_ANGLE
    }

    private fun Date.plusMinutes(minutes: Int): Date = Date(time + minutes * 60_000L)
}

/** The sun never reaches a needed angle there that day (far north or south). */
class UncalculableTimesException(val date: LocalDate) :
    IllegalStateException("Prayer times can't be calculated for this place on $date.")
