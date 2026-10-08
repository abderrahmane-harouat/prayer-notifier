package com.example.prayernotifier.data

import com.example.prayernotifier.data.calculation.AsrMethod
import com.example.prayernotifier.data.calculation.CalculationMethod
import com.example.prayernotifier.data.calculation.CountryMethods
import com.example.prayernotifier.data.calculation.PrayerCalculator
import com.example.prayernotifier.data.location.CurrentLocation
import com.example.prayernotifier.data.persistence.AppSettings
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Prayer times for a place, calculated on the device: works offline, for
 * any date, with nothing to download or store.
 *
 * The method and the Asr rule are the user's choice when they made one,
 * otherwise the place's country's. Places saved before countries were recorded
 * fall back to where the phone is now.
 */
class PrayerTimesRepository(
    private val deviceCountry: () -> String?,
    private val deviceZone: () -> ZoneId = { ZoneId.systemDefault() }
) {
    fun methodFor(place: CurrentLocation, settings: AppSettings): CalculationMethod =
        settings.chosenMethod ?: automaticMethodFor(place)

    /** What "automatic" means for this place, whatever the user picked. */
    fun automaticMethodFor(place: CurrentLocation): CalculationMethod =
        CountryMethods.forCountry(countryOf(place))

    fun asrFor(place: CurrentLocation, settings: AppSettings): AsrMethod =
        settings.chosenAsr ?: automaticAsrFor(place)

    fun automaticAsrFor(place: CurrentLocation): AsrMethod =
        CountryMethods.asrForCountry(countryOf(place))

    fun month(month: YearMonth, place: CurrentLocation, settings: AppSettings): List<PrayerDay> =
        PrayerCalculator.month(
            month, place.latitude, place.longitude, zoneOf(place),
            methodFor(place, settings), asrFor(place, settings)
        )

    fun day(date: LocalDate, place: CurrentLocation, settings: AppSettings): PrayerDay =
        PrayerCalculator.day(
            date, place.latitude, place.longitude, zoneOf(place),
            methodFor(place, settings), asrFor(place, settings)
        )

    private fun countryOf(place: CurrentLocation): String? = place.countryCode ?: deviceCountry()

    private fun zoneOf(place: CurrentLocation): ZoneId =
        place.timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: deviceZone()
}
