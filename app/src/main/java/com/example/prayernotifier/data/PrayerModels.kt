package com.example.prayernotifier.data

import java.time.LocalDate

/** The five prayer times of one day, "HH:mm" in the place's local time. */
data class PrayerTimings(
    val fajr: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String
)

/** A day of the Umm al-Qura Hijri calendar; [month] runs 1 (Muharram) to 12. */
data class HijriDate(
    val day: Int,
    val month: Int,
    val year: Int
)

data class PrayerDay(
    val date: LocalDate,
    val timings: PrayerTimings
)
