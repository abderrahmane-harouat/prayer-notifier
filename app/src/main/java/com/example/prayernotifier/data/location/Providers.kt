package com.example.prayernotifier.data.location

/** Provides a one-shot device position. Implementations must not touch the UI thread. */
interface PositionProvider {
    suspend fun currentFix(): PositionOutcome
}

/** What reverse geocoding found; either part may be missing. */
data class GeocodedPlace(val name: String?, val countryCode: String?)

/** Reverse-geocodes coords. Returns null when unavailable (often offline). */
interface GeocodeProvider {
    suspend fun lookup(latitude: Double, longitude: Double): GeocodedPlace?
}

/**
 * Where the phone itself is, known without the internet: the country of
 * the mobile network or, failing that, of the time zone. Valid for a fresh
 * GPS fix only, since the phone is then at the place.
 */
interface DeviceRegion {
    fun countryCode(): String?
    fun timeZone(): String
}

/**
 * Picks the most relevant place name, mirroring the Flutter fallback chain:
 * locality (city) → administrative area → country → feature name.
 * Pure function so the chain is unit-testable without Android's Geocoder.
 */
fun pickPlaceName(
    locality: String?,
    administrativeArea: String?,
    country: String?,
    featureName: String?
): String? = locality?.takeIf { it.isNotBlank() }
    ?: administrativeArea?.takeIf { it.isNotBlank() }
    ?: country?.takeIf { it.isNotBlank() }
    ?: featureName?.takeIf { it.isNotBlank() }
