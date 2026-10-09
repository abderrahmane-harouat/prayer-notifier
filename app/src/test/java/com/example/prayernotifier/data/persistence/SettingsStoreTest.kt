package com.example.prayernotifier.data.persistence

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsStoreTest {

    private lateinit var store: InMemoryKeyValueStore
    private lateinit var settings: SettingsStore

    @Before
    fun setUp() {
        store = InMemoryKeyValueStore()
        settings = SettingsStore(store)
    }

    @Test
    fun `empty store loads defaults`() = runTest {
        val loaded = settings.load()
        assertEquals(AppSettings(), loaded)
        assertEquals(5, loaded.fajrSettings.prePrayerReminderMinutes)
        assertEquals(10, loaded.maghribSettings.prePrayerReminderMinutes)
        assertEquals(0, loaded.hijriDateAdjustment)
        assertEquals(0, loaded.timeAdjustments.fajrAdjustment)
    }

    @Test
    fun `save then load roundtrips`() = runTest {
        val custom = AppSettings(
            fajrSettings = PrayerNotificationSettings(enabled = false, prePrayerReminderMinutes = 15),
            hijriDateAdjustment = 1,
            timeAdjustments = PrayerTimeAdjustments(maghribAdjustment = 2)
        )
        settings.save(custom)
        assertEquals(custom, settings.load())
    }

    @Test
    fun `corrupt data loads defaults`() = runTest {
        store.put(SettingsStore.KEY, "broken{{{")
        assertEquals(AppSettings(), settings.load())
    }

    @Test
    fun `prayer lookup helpers work`() {
        val loaded = AppSettings()
        assertEquals(loaded.maghribSettings, loaded.getSettingsForPrayer("Maghrib"))
        assertEquals(10, loaded.travelTimeSettings.getTravelTimeForPrayer("Fajr"))
        assertEquals(0, loaded.timeAdjustments.getAdjustmentForPrayer("Isha"))
    }

    @Test
    fun `settings saved before 0_2 get Do Not Disturb on and Jumua 30 minutes before`() = runTest {
        // What 0.1 stored: no silence, no Jumua.
        store.put(SettingsStore.KEY, """{"fajrSettings":{"prePrayerReminderMinutes":10},"hijriDateAdjustment":1}""")
        val loaded = settings.load()
        assertEquals(10, loaded.fajrSettings.prePrayerReminderMinutes)
        assertEquals(1, loaded.hijriDateAdjustment)
        assertEquals(SilenceSettings(), loaded.silence)
        assertEquals(true, loaded.silence.isOnFor("Fajr"))
        assertEquals(30, loaded.jumuaSettings.prePrayerReminderMinutes)
        assertEquals(loaded.jumuaSettings, loaded.getSettingsForPrayer("Jumua"))
    }

    @Test
    fun `silence lasts 35 min after Fajr, an hour after Jumua, 15 after the others`() {
        val defaults = SilenceSettings()
        assertEquals(35, defaults.minutesFor("Fajr"))
        assertEquals(60, defaults.minutesFor("Jumua"))
        listOf("Dhuhr", "Asr", "Maghrib", "Isha").forEach { assertEquals(25, defaults.minutesFor(it)) }
    }

    @Test
    fun `each silence length can be changed, within 5 to 120 minutes`() = runTest {
        val changed = SilenceSettings().withMinutes("Isha", 40).withMinutes("Jumua", 90)
        assertEquals(40, changed.minutesFor("Isha"))
        assertEquals(90, changed.minutesFor("Jumua"))
        assertEquals(25, changed.minutesFor("Asr"))
        assertEquals(5, SilenceSettings().withMinutes("Fajr", 0).minutesFor("Fajr"))
        assertEquals(120, SilenceSettings().withMinutes("Fajr", 500).minutesFor("Fajr"))

        settings.save(AppSettings(silence = changed))
        assertEquals(changed, settings.load().silence)
    }
}
