package com.example.prayernotifier.data.calculation

/**
 * The calculation conventions of the authorities behind each country's
 * official prayer times. Angles are degrees of the sun below the horizon;
 * the values and the fixed minute offsets (Dubai, Morocco, Turkey's
 * "temkin", Portugal, Jordan) match what the Aladhan API used before the
 * app calculated times itself, so times stay the same.
 *
 * Isha is either an angle or a fixed number of minutes after Maghrib
 * (plus [ramadanIshaExtraMinutes] in Ramadan). Maghrib is sunset, plus
 * [maghribMinutes], or the moment the sun reaches [maghribAngle] (Tehran)
 * when set.
 */
enum class CalculationMethod(
    val fajrAngle: Double,
    val ishaAngle: Double = 0.0,
    val ishaMinutes: Int = 0,
    val maghribAngle: Double? = null,
    val dhuhrMinutes: Int = 0,
    val asrMinutes: Int = 0,
    val maghribMinutes: Int = 0,
    val ramadanIshaExtraMinutes: Int = 0
) {
    MUSLIM_WORLD_LEAGUE(fajrAngle = 18.0, ishaAngle = 17.0),
    EGYPT(fajrAngle = 19.5, ishaAngle = 17.5),
    UMM_AL_QURA(fajrAngle = 18.5, ishaMinutes = 90, ramadanIshaExtraMinutes = 30),
    KARACHI(fajrAngle = 18.0, ishaAngle = 18.0),
    NORTH_AMERICA(fajrAngle = 15.0, ishaAngle = 15.0),
    ALGERIA(fajrAngle = 18.0, ishaAngle = 17.0),
    MOROCCO(fajrAngle = 19.0, ishaAngle = 17.0, dhuhrMinutes = 5, maghribMinutes = 5),
    TUNISIA(fajrAngle = 18.0, ishaAngle = 18.0),
    JORDAN(fajrAngle = 18.0, ishaAngle = 18.0, maghribMinutes = 5),
    DUBAI(fajrAngle = 18.2, ishaAngle = 18.2, dhuhrMinutes = 3, maghribMinutes = 3),
    QATAR(fajrAngle = 18.0, ishaMinutes = 90),
    KUWAIT(fajrAngle = 18.0, ishaAngle = 17.5),
    TURKEY(fajrAngle = 18.0, ishaAngle = 17.0, dhuhrMinutes = 5, asrMinutes = 4, maghribMinutes = 7),
    TEHRAN(fajrAngle = 17.7, ishaAngle = 14.0, maghribAngle = 4.5),
    RUSSIA(fajrAngle = 16.0, ishaAngle = 15.0),
    FRANCE(fajrAngle = 12.0, ishaAngle = 12.0),
    PORTUGAL(fajrAngle = 18.0, ishaMinutes = 77, dhuhrMinutes = 5, maghribMinutes = 3),
    SINGAPORE(fajrAngle = 20.0, ishaAngle = 18.0),
    MALAYSIA(fajrAngle = 20.0, ishaAngle = 18.0),
    INDONESIA(fajrAngle = 20.0, ishaAngle = 18.0);

    companion object {
        /** A saved method name, or null when unknown (e.g. removed in an update). */
        fun fromName(name: String?): CalculationMethod? = entries.firstOrNull { it.name == name }
    }
}
