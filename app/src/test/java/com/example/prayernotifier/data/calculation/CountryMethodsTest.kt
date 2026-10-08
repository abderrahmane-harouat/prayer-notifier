package com.example.prayernotifier.data.calculation

import org.junit.Assert.assertEquals
import org.junit.Test

class CountryMethodsTest {

    @Test
    fun `countries with their own authority use its method`() {
        assertEquals(CalculationMethod.ALGERIA, CountryMethods.forCountry("DZ"))
        assertEquals(CalculationMethod.MOROCCO, CountryMethods.forCountry("MA"))
        assertEquals(CalculationMethod.EGYPT, CountryMethods.forCountry("EG"))
        assertEquals(CalculationMethod.UMM_AL_QURA, CountryMethods.forCountry("SA"))
        assertEquals(CalculationMethod.TURKEY, CountryMethods.forCountry("TR"))
        assertEquals(CalculationMethod.FRANCE, CountryMethods.forCountry("FR"))
        assertEquals(CalculationMethod.NORTH_AMERICA, CountryMethods.forCountry("US"))
    }

    @Test
    fun `countries without one follow their region`() {
        assertEquals(CalculationMethod.EGYPT, CountryMethods.forCountry("NG"))
        assertEquals(CalculationMethod.EGYPT, CountryMethods.forCountry("SY"))
        assertEquals(CalculationMethod.UMM_AL_QURA, CountryMethods.forCountry("YE"))
        assertEquals(CalculationMethod.KARACHI, CountryMethods.forCountry("IN"))
    }

    @Test
    fun `a country's own method beats its region`() {
        // African, but with a national method.
        assertEquals(CalculationMethod.ALGERIA, CountryMethods.forCountry("DZ"))
        assertEquals(CalculationMethod.TUNISIA, CountryMethods.forCountry("TN"))
    }

    @Test
    fun `codes match in any case`() {
        assertEquals(CalculationMethod.ALGERIA, CountryMethods.forCountry("dz"))
    }

    @Test
    fun `unknown or missing country falls back to Muslim World League`() {
        assertEquals(CalculationMethod.MUSLIM_WORLD_LEAGUE, CountryMethods.forCountry("GB"))
        assertEquals(CalculationMethod.MUSLIM_WORLD_LEAGUE, CountryMethods.forCountry("ZZ"))
        assertEquals(CalculationMethod.MUSLIM_WORLD_LEAGUE, CountryMethods.forCountry(null))
    }
}
