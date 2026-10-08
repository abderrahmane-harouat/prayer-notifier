package com.example.prayernotifier.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * [GeocodeProvider] backed by Android's [Geocoder], English locale
 * (the app is English-first; the Flutter app used Arabic).
 */
class AndroidGeocoderProvider(context: Context) : GeocodeProvider {
    private val geocoder = Geocoder(context.applicationContext, Locale.ENGLISH)

    override suspend fun lookup(latitude: Double, longitude: Double): GeocodedPlace? =
        withContext(Dispatchers.IO) {
            val addresses = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(results: List<Address>) {
                                if (cont.isActive) cont.resume(results)
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(emptyList())
                            }
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1) ?: emptyList()
                }
            } catch (e: Exception) {
                return@withContext null
            }
            val address = addresses.firstOrNull() ?: return@withContext null
            GeocodedPlace(
                name = pickPlaceName(address.locality, address.adminArea, address.countryName, address.featureName),
                countryCode = address.countryCode?.takeIf { it.isNotBlank() }
            )
        }
}
