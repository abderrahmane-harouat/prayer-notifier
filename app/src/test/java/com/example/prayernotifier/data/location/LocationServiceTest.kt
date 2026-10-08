package com.example.prayernotifier.data.location

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LocationServiceTest {

    private lateinit var positions: FakePositionProvider
    private lateinit var geocoder: FakeGeocodeProvider
    private lateinit var storage: InMemoryLocationStorage
    private lateinit var service: LocationService

    @Before
    fun setUp() {
        positions = FakePositionProvider()
        geocoder = FakeGeocodeProvider()
        storage = InMemoryLocationStorage()
        service = LocationService(positions, geocoder, storage, FakeRegion())
    }

    @Test
    fun `refresh persists current, cached name and saved entry`() = runTest {
        positions.outcome = PositionOutcome.Fix(LatLng(21.4225, 39.8262))
        geocoder.name = "Mecca"
        geocoder.country = "SA"

        val pos = service.refreshLocation(nowEpochMs = 1_000L)

        assertEquals(21.4225, pos.latitude, 0.0)
        assertEquals(
            CurrentLocation("Mecca", 21.4225, 39.8262, "SA", "Asia/Riyadh"),
            service.getCurrentSavedLocation()
        )
        assertEquals("Mecca", service.getCachedLocationName())
        assertEquals(
            listOf(SavedLocation("Mecca", 21.4225, 39.8262, 1_000L, "SA", "Asia/Riyadh")),
            service.getSavedLocations()
        )
    }

    @Test
    fun `refresh without geocoder still persists, named by coordinates, country from the phone`() = runTest {
        positions.outcome = PositionOutcome.Fix(LatLng(21.4225, 39.8262))
        geocoder.unavailable = true

        val current = service.refreshLocation(nowEpochMs = 1_000L)

        assertEquals(CurrentLocation("21.42°N, 39.83°E", 21.4225, 39.8262, "XX", "Asia/Riyadh"), current)
        assertEquals(current, service.getCurrentSavedLocation())
        assertEquals(1, service.getSavedLocations().size)
    }

    @Test
    fun `geocoder country wins over the phone's`() = runTest {
        positions.outcome = PositionOutcome.Fix(LatLng(36.75, 3.06))
        geocoder.name = "Algiers"
        geocoder.country = "DZ"

        assertEquals("DZ", service.refreshLocation().countryCode)
    }

    @Test
    fun `coordinate label covers every hemisphere`() {
        assertEquals("33.87°S, 151.21°E", coordinateLabel(-33.8688, 151.2093))
        assertEquals("40.71°N, 74.01°W", coordinateLabel(40.7128, -74.0060))
    }

    @Test(expected = LocationException.PermissionDenied::class)
    fun `missing permission throws`() = runTest {
        positions.outcome = PositionOutcome.PermissionMissing
        service.determinePosition()
    }

    @Test(expected = LocationException.ServiceDisabled::class)
    fun `disabled service throws`() = runTest {
        positions.outcome = PositionOutcome.ServiceDisabled
        service.determinePosition()
    }

    @Test(expected = LocationException.NoFix::class)
    fun `no fix throws`() = runTest {
        positions.outcome = PositionOutcome.NoFix
        service.determinePosition()
    }

    @Test
    fun `second refresh in same area updates entry instead of duplicating`() = runTest {
        positions.outcome = PositionOutcome.Fix(LatLng(21.4225, 39.8262))
        geocoder.name = "Mecca"
        service.refreshLocation(nowEpochMs = 1_000L)

        positions.outcome = PositionOutcome.Fix(LatLng(21.4249, 39.8299))
        geocoder.name = "Makkah"
        service.refreshLocation(nowEpochMs = 2_000L)

        val saved = service.getSavedLocations()
        assertEquals(1, saved.size)
        assertEquals("Makkah", saved[0].name)
        assertEquals(2_000L, saved[0].savedAtEpochMs)
    }

    @Test
    fun `select saved location promotes it to current`() = runTest {
        val place = SavedLocation("Medina", 24.5247, 39.5692, 5_000L, "SA", "Asia/Riyadh")
        storage.saveLocation(place)

        val current = service.selectSavedLocation(place)

        assertEquals(CurrentLocation("Medina", 24.5247, 39.5692, "SA", "Asia/Riyadh"), current)
        assertEquals(current, service.getCurrentSavedLocation())
        assertEquals("Medina", service.getCachedLocationName())
    }

    @Test
    fun `delete removes same-area entry`() = runTest {
        storage.saveLocation(SavedLocation("Mecca", 21.4225, 39.8262, 1_000L))
        storage.saveLocation(SavedLocation("Medina", 24.5247, 39.5692, 1_000L))

        service.deleteLocation(21.4249, 39.8299)

        assertEquals(listOf("Medina"), service.getSavedLocations().map { it.name })
    }

    private class FakePositionProvider(var outcome: PositionOutcome = PositionOutcome.NoFix) : PositionProvider {
        override suspend fun currentFix(): PositionOutcome = outcome
    }

    private class FakeGeocodeProvider(
        var name: String? = null,
        var country: String? = null,
        var unavailable: Boolean = false
    ) : GeocodeProvider {
        override suspend fun lookup(latitude: Double, longitude: Double): GeocodedPlace? =
            if (unavailable) null else GeocodedPlace(name, country)
    }

    /** The phone: on a network of country "XX", in Riyadh's time zone. */
    private class FakeRegion : DeviceRegion {
        override fun countryCode(): String = "XX"
        override fun timeZone(): String = "Asia/Riyadh"
    }
}
