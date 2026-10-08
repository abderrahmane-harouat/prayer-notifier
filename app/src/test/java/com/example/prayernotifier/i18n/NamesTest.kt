package com.example.prayernotifier.i18n

import com.example.prayernotifier.data.calculation.CalculationMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class NamesTest {
    @Test
    fun `every calculation method has its own name`() {
        val names = CalculationMethod.entries.map { methodNameRes(it) }
        assertEquals(names.size, names.distinct().size)
    }
}
