package com.example.prayernotifier.data.calculation

/**
 * When Asr begins: once a shadow is as long as its object, plus its noon
 * shadow (Shafi'i, Maliki, Hanbali), or twice as long (Hanafi, later).
 */
enum class AsrMethod {
    STANDARD,
    HANAFI;

    companion object {
        /** A saved name, or null when unknown. */
        fun fromName(name: String?): AsrMethod? = entries.firstOrNull { it.name == name }
    }
}
