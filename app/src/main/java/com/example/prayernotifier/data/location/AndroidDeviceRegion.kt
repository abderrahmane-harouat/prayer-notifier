package com.example.prayernotifier.data.location

import android.content.Context
import android.telephony.TelephonyManager
import java.time.ZoneId
import java.util.Locale

/**
 * [DeviceRegion] from the mobile network the phone is registered on, then
 * the region of its time zone ("Africa/Algiers" → DZ). Both work offline
 * and need no permission. Not the language setting: many people use a
 * language from another country.
 */
class AndroidDeviceRegion(context: Context) : DeviceRegion {
    private val app = context.applicationContext

    override fun countryCode(): String? = networkCountry() ?: timeZoneCountry()

    override fun timeZone(): String = ZoneId.systemDefault().id

    private fun networkCountry(): String? = runCatching {
        app.getSystemService(TelephonyManager::class.java)?.networkCountryIso
    }.getOrNull()?.takeIf { it.length == 2 }?.uppercase(Locale.ROOT)

    private fun timeZoneCountry(): String? = runCatching {
        android.icu.util.TimeZone.getRegion(timeZone())
    }.getOrNull()?.takeIf { it.length == 2 && it.all(Char::isLetter) }
}
