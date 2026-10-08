package com.example.prayernotifier.data.calculation

import java.util.Locale

/**
 * Which method a country's prayer times follow, by ISO 3166 country code.
 *
 * A country with its own authority gets that authority's method. Countries
 * without one follow their region's usual convention (praytimes.org's
 * method table): Egyptian for Africa, Syria and Lebanon, Umm al-Qura for
 * the Arabian Peninsula, Karachi for South Asia, ISNA for North America.
 * Everywhere else, and when the country is unknown, the Muslim World League.
 */
object CountryMethods {

    private val BY_COUNTRY: Map<String, CalculationMethod> = buildMap {
        // A national authority's own method.
        put("DZ", CalculationMethod.ALGERIA)
        put("MA", CalculationMethod.MOROCCO)
        put("EH", CalculationMethod.MOROCCO)
        put("TN", CalculationMethod.TUNISIA)
        put("EG", CalculationMethod.EGYPT)
        put("SA", CalculationMethod.UMM_AL_QURA)
        put("AE", CalculationMethod.DUBAI)
        put("QA", CalculationMethod.QATAR)
        put("KW", CalculationMethod.KUWAIT)
        put("JO", CalculationMethod.JORDAN)
        put("PS", CalculationMethod.JORDAN)
        put("TR", CalculationMethod.TURKEY)
        put("IR", CalculationMethod.TEHRAN)
        put("PK", CalculationMethod.KARACHI)
        put("RU", CalculationMethod.RUSSIA)
        put("FR", CalculationMethod.FRANCE)
        put("PT", CalculationMethod.PORTUGAL)
        put("SG", CalculationMethod.SINGAPORE)
        put("MY", CalculationMethod.MALAYSIA)
        put("BN", CalculationMethod.MALAYSIA)
        put("ID", CalculationMethod.INDONESIA)
        put("US", CalculationMethod.NORTH_AMERICA)
        put("CA", CalculationMethod.NORTH_AMERICA)

        // Regional conventions.
        listOf("YE", "OM", "BH").forEach { put(it, CalculationMethod.UMM_AL_QURA) }
        listOf("AF", "BD", "IN").forEach { put(it, CalculationMethod.KARACHI) }
        listOf("SY", "LB").forEach { put(it, CalculationMethod.EGYPT) }
        AFRICA.forEach { putIfAbsent(it, CalculationMethod.EGYPT) }
    }

    /** Where official and mosque timetables use the later, Hanafi Asr. */
    private val HANAFI_ASR = setOf("PK", "IN", "BD", "AF")

    /** The method for a country code (any case), or Muslim World League. */
    fun forCountry(countryCode: String?): CalculationMethod =
        countryCode?.uppercase(Locale.ROOT)?.let { BY_COUNTRY[it] }
            ?: CalculationMethod.MUSLIM_WORLD_LEAGUE

    /** Hanafi Asr in South Asia and Afghanistan, standard elsewhere. */
    fun asrForCountry(countryCode: String?): AsrMethod =
        if (countryCode?.uppercase(Locale.ROOT) in HANAFI_ASR) AsrMethod.HANAFI else AsrMethod.STANDARD
}

/** African countries; those with their own method above keep it. */
private val AFRICA = listOf(
    "AO", "BF", "BI", "BJ", "BW", "CD", "CF", "CG", "CI", "CM", "CV", "DJ", "ER", "ET",
    "GA", "GH", "GM", "GN", "GQ", "GW", "KE", "KM", "LR", "LS", "LY", "MG", "ML", "MR",
    "MU", "MW", "MZ", "NA", "NE", "NG", "RW", "SC", "SD", "SL", "SN", "SO", "SS", "ST",
    "SZ", "TD", "TG", "TZ", "UG", "ZA", "ZM", "ZW"
)
