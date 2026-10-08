package com.example.prayernotifier.i18n

import androidx.annotation.StringRes
import com.example.prayernotifier.R
import com.example.prayernotifier.data.calculation.CalculationMethod

/** Display-name resource for a prayer id ("Fajr" … "Isha"). */
@StringRes
fun prayerNameRes(prayer: String): Int = when (prayer) {
    "Fajr" -> R.string.prayer_fajr
    "Dhuhr" -> R.string.prayer_dhuhr
    "Asr" -> R.string.prayer_asr
    "Maghrib" -> R.string.prayer_maghrib
    else -> R.string.prayer_isha
}

/** Display-name resource for a calculation method. */
@StringRes
fun methodNameRes(method: CalculationMethod): Int = when (method) {
    CalculationMethod.MUSLIM_WORLD_LEAGUE -> R.string.method_mwl
    CalculationMethod.EGYPT -> R.string.method_egypt
    CalculationMethod.UMM_AL_QURA -> R.string.method_umm_al_qura
    CalculationMethod.KARACHI -> R.string.method_karachi
    CalculationMethod.NORTH_AMERICA -> R.string.method_north_america
    CalculationMethod.ALGERIA -> R.string.method_algeria
    CalculationMethod.MOROCCO -> R.string.method_morocco
    CalculationMethod.TUNISIA -> R.string.method_tunisia
    CalculationMethod.JORDAN -> R.string.method_jordan
    CalculationMethod.DUBAI -> R.string.method_dubai
    CalculationMethod.QATAR -> R.string.method_qatar
    CalculationMethod.KUWAIT -> R.string.method_kuwait
    CalculationMethod.TURKEY -> R.string.method_turkey
    CalculationMethod.TEHRAN -> R.string.method_tehran
    CalculationMethod.RUSSIA -> R.string.method_russia
    CalculationMethod.FRANCE -> R.string.method_france
    CalculationMethod.PORTUGAL -> R.string.method_portugal
    CalculationMethod.SINGAPORE -> R.string.method_singapore
    CalculationMethod.MALAYSIA -> R.string.method_malaysia
    CalculationMethod.INDONESIA -> R.string.method_indonesia
}
