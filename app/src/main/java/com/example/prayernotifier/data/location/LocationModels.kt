package com.example.prayernotifier.data.location

import kotlinx.serialization.Serializable

data class LatLng(val latitude: Double, val longitude: Double)

/**
 * Persisted "current location": coords, reverse-geocoded name, and the
 * country and time zone found when the fix was taken. Both are null for
 * places saved by older versions of the app.
 */
@Serializable
data class CurrentLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** ISO 3166 code ("DZ"); picks the calculation method. */
    val countryCode: String? = null,
    /** Time zone id ("Africa/Algiers"); prayer times are shown in it. */
    val timeZone: String? = null
)

/** A saved place. `savedAtEpochMs` replaces Flutter's ISO `savedAt` string. */
@Serializable
data class SavedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val savedAtEpochMs: Long,
    val countryCode: String? = null,
    val timeZone: String? = null
)

/** Typed failures mirroring the Flutter `LocationService` error cases. */
sealed class LocationException(message: String) : Exception(message) {
    data object ServiceDisabled : LocationException("Location services are disabled.")
    data object PermissionDenied : LocationException("Location permissions are denied.")
    data object NoFix : LocationException("Could not obtain a location fix.")
}

/** Raw outcome from a position provider; mapped to [LocationException] by the service. */
sealed interface PositionOutcome {
    data class Fix(val value: LatLng) : PositionOutcome
    data object ServiceDisabled : PositionOutcome
    data object PermissionMissing : PositionOutcome
    data object NoFix : PositionOutcome
}
