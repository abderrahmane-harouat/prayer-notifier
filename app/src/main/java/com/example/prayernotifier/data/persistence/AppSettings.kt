package com.example.prayernotifier.data.persistence

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

@Serializable
data class AppSettings(
    val fajrSettings: PrayerNotificationSettings = PrayerNotificationSettings(),
    val dhuhrSettings: PrayerNotificationSettings = PrayerNotificationSettings(),
    val asrSettings: PrayerNotificationSettings = PrayerNotificationSettings(),
    val maghribSettings: PrayerNotificationSettings = PrayerNotificationSettings(prePrayerReminderMinutes = 10),
    val ishaSettings: PrayerNotificationSettings = PrayerNotificationSettings(),
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
        else -> PrayerNotificationSettings()
    }

    /** The user's own choice of method, or null to follow the country. */
    val chosenMethod: CalculationMethod?
        get() = CalculationMethod.fromName(calculationMethod)

    /** The user's own choice for Asr, or null to follow the country. */
    val chosenAsr: AsrMethod?
        get() = AsrMethod.fromName(asrMethod)
}
