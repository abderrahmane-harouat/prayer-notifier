package com.example.prayernotifier.data.persistence

import com.example.prayernotifier.data.PrayerMath
import com.example.prayernotifier.data.calculation.AsrMethod
import com.example.prayernotifier.data.calculation.CalculationMethod
import kotlinx.serialization.Serializable

/** Mirrors the Flutter `lib/models/settings.dart` defaults. */
@Serializable
data class PrayerNotificationSettings(
    val enabled: Boolean = true,
    val prePrayerReminderMinutes: Int = 5,
    val departureReminderMinutes: Int = 5
)

@Serializable
data class TravelTimeSettings(
    val fajrTravelMinutes: Int = 10,
    val dhuhrTravelMinutes: Int = 10,
    val asrTravelMinutes: Int = 10,
    val maghribTravelMinutes: Int = 10,
    val ishaTravelMinutes: Int = 10
) {
    fun getTravelTimeForPrayer(prayerName: String): Int = when (prayerName) {
        "Fajr" -> fajrTravelMinutes
        "Dhuhr" -> dhuhrTravelMinutes
        "Asr" -> asrTravelMinutes
        "Maghrib" -> maghribTravelMinutes
        "Isha" -> ishaTravelMinutes
        else -> 10
    }
}

@Serializable
data class PrayerTimeAdjustments(
    val fajrAdjustment: Int = 0,
    val dhuhrAdjustment: Int = 0,
    val asrAdjustment: Int = 0,
    val maghribAdjustment: Int = 0,
    val ishaAdjustment: Int = 0
) {
    fun getAdjustmentForPrayer(prayerName: String): Int = when (prayerName) {
        "Fajr" -> fajrAdjustment
        "Dhuhr" -> dhuhrAdjustment
        "Asr" -> asrAdjustment
        "Maghrib" -> maghribAdjustment
        "Isha" -> ishaAdjustment
        else -> 0
    }
}

/**
 * Do Not Disturb from each prayer's adhan, switched off by itself after
 * [minutesFor]: 35 minutes for Fajr, an hour for Jumua and 15 for the
 * others unless the user changes them. On by default; works once Android
 * grants the app Do Not Disturb access.
 */
@Serializable
data class SilenceSettings(
    val enabled: Boolean = true,
    val fajr: Boolean = true,
    val dhuhr: Boolean = true,
    val asr: Boolean = true,
    val maghrib: Boolean = true,
    val isha: Boolean = true,
    val jumua: Boolean = true,
    /** How long the phone stays silent from each adhan, in minutes. */
    val fajrMinutes: Int = 35,
    val dhuhrMinutes: Int = 15,
    val asrMinutes: Int = 15,
    val maghribMinutes: Int = 15,
    val ishaMinutes: Int = 15,
    val jumuaMinutes: Int = 60
) {
    /** Whether the phone goes silent at this prayer ("Jumua" on Fridays). */
    fun isOnFor(prayer: String): Boolean = enabled && when (prayer) {
        "Fajr" -> fajr
        "Dhuhr" -> dhuhr
        "Asr" -> asr
        "Maghrib" -> maghrib
        "Isha" -> isha
        PrayerMath.JUMUA -> jumua
        else -> false
    }

    /** The same settings with one prayer switched on or off. */
    fun with(prayer: String, on: Boolean): SilenceSettings = when (prayer) {
        "Fajr" -> copy(fajr = on)
        "Dhuhr" -> copy(dhuhr = on)
        "Asr" -> copy(asr = on)
        "Maghrib" -> copy(maghrib = on)
        "Isha" -> copy(isha = on)
        PrayerMath.JUMUA -> copy(jumua = on)
        else -> this
    }

    /** How long the phone stays silent from this prayer's adhan, within [MIN_MINUTES]..[MAX_MINUTES]. */
    fun minutesFor(prayer: String): Int = when (prayer) {
        "Fajr" -> fajrMinutes
        "Dhuhr" -> dhuhrMinutes
        "Asr" -> asrMinutes
        "Maghrib" -> maghribMinutes
        "Isha" -> ishaMinutes
        PrayerMath.JUMUA -> jumuaMinutes
        else -> 15
    }.coerceIn(MIN_MINUTES, MAX_MINUTES)

    /** The same settings with one prayer's silence lasting [minutes]. */
    fun withMinutes(prayer: String, minutes: Int): SilenceSettings {
        val value = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        return when (prayer) {
            "Fajr" -> copy(fajrMinutes = value)
            "Dhuhr" -> copy(dhuhrMinutes = value)
            "Asr" -> copy(asrMinutes = value)
            "Maghrib" -> copy(maghribMinutes = value)
            "Isha" -> copy(ishaMinutes = value)
            PrayerMath.JUMUA -> copy(jumuaMinutes = value)
            else -> this
        }
    }

    companion object {
        const val MIN_MINUTES = 5
        const val MAX_MINUTES = 120
    }
}

@Serializable
data class AppSettings(
    val fajrSettings: PrayerNotificationSettings = PrayerNotificationSettings(),
    val dhuhrSettings: PrayerNotificationSettings = PrayerNotificationSettings(),
    val asrSettings: PrayerNotificationSettings = PrayerNotificationSettings(),
    val maghribSettings: PrayerNotificationSettings = PrayerNotificationSettings(prePrayerReminderMinutes = 10),
    val ishaSettings: PrayerNotificationSettings = PrayerNotificationSettings(),
    /** Friday's reminder, in place of Dhuhr's: 30 minutes before by default. */
    val jumuaSettings: PrayerNotificationSettings = PrayerNotificationSettings(prePrayerReminderMinutes = 30),
    val silence: SilenceSettings = SilenceSettings(),
    val travelTimeSettings: TravelTimeSettings = TravelTimeSettings(),
    val timeAdjustments: PrayerTimeAdjustments = PrayerTimeAdjustments(),
    val darkMode: Boolean = false,
    val hijriDateAdjustment: Int = 0,
    /** A [CalculationMethod] name the user picked; null = by the place's country. */
    val calculationMethod: String? = null,
    /** An [AsrMethod] name the user picked; null = by the place's country. */
    val asrMethod: String? = null
) {
    fun getSettingsForPrayer(prayerName: String): PrayerNotificationSettings = when (prayerName) {
        "Fajr" -> fajrSettings
        "Dhuhr" -> dhuhrSettings
        "Asr" -> asrSettings
        "Maghrib" -> maghribSettings
        "Isha" -> ishaSettings
        PrayerMath.JUMUA -> jumuaSettings
        else -> PrayerNotificationSettings()
    }

    /** The user's own choice of method, or null to follow the country. */
    val chosenMethod: CalculationMethod?
        get() = CalculationMethod.fromName(calculationMethod)

    /** The user's own choice for Asr, or null to follow the country. */
    val chosenAsr: AsrMethod?
        get() = AsrMethod.fromName(asrMethod)
}
