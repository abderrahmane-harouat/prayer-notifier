package com.example.prayernotifier.data

import com.example.prayernotifier.data.calculation.CalculationMethod
import com.example.prayernotifier.data.calculation.PrayerCalculator
import com.example.prayernotifier.data.location.CurrentLocation
import com.example.prayernotifier.data.persistence.AppSettings
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerTimesRepositoryTest {

    private val algiers = CurrentLocation("Algiers", 36.7538, 3.0588, "DZ", "Africa/Algiers")
    private val repository = PrayerTimesRepository(
        deviceCountry = { "FR" },
        deviceZone = { ZoneId.of("Europe/Paris") }
    )

    @Test
    fun `the place's country picks the method`() {
        assertEquals(CalculationMethod.ALGERIA, repository.methodFor(algiers, AppSettings()))
    }

    @Test
    fun `the user's choice beats the country`() {
        val settings = AppSettings(calculationMethod = CalculationMethod.EGYPT.name)
        assertEquals(CalculationMethod.EGYPT, repository.methodFor(algiers, settings))
        assertEquals(CalculationMethod.ALGERIA, repository.automaticMethodFor(algiers))
    }

    @Test
    fun `an unknown saved choice falls back to the country`() {
        val settings = AppSettings(calculationMethod = "REMOVED_METHOD")
        assertEquals(CalculationMethod.ALGERIA, repository.methodFor(algiers, settings))
    }

    @Test
    fun `places saved before countries were recorded use the phone's country and zone`() {
        val old = CurrentLocation("Somewhere", 48.8566, 2.3522)
        assertEquals(CalculationMethod.FRANCE, repository.methodFor(old, AppSettings()))

        val date = LocalDate.of(2026, 10, 8)
        val expected = PrayerCalculator.day(date, 48.8566, 2.3522, ZoneId.of("Europe/Paris"), CalculationMethod.FRANCE)
        assertEquals(expected, repository.day(date, old, AppSettings()))
    }

    @Test
    fun `times use the place's time zone, not the phone's`() {
        val date = LocalDate.of(2026, 10, 8)
        val expected = PrayerCalculator.day(date, 36.7538, 3.0588, ZoneId.of("Africa/Algiers"), CalculationMethod.ALGERIA)
        assertEquals(expected, repository.day(date, algiers, AppSettings()))
        assertEquals("05:23", expected.timings.fajr)
    }

    @Test
    fun `month covers every day`() {
        assertEquals(31, repository.month(YearMonth.of(2026, 10), algiers, AppSettings()).size)
    }
}
