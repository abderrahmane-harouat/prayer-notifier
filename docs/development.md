# Development

[← README](../README.md) · [العربية](development.ar.md)

Requirements: Android Studio (or the command line), **JDK 17–21** (newer JDKs are not supported by this Gradle/AGP version), Android SDK 36. The app runs on Android 8.0 (API 26) and later; Do Not Disturb at prayer time needs Android 10.

```sh
git clone https://github.com/abderrahmane-harouat/prayer-notifier.git
cd prayer-notifier
./gradlew :app:assembleDebug        # build the APK
./gradlew :app:testDebugUnitTest    # run the unit tests
./gradlew :app:installDebug         # install on a connected device or emulator
```

Or open the folder in Android Studio and press **Run**.

Before committing, turn on the safety check that blocks signing keys, passwords and licensed font files from ever being committed (the repository is public):

```sh
git config core.hooksPath .githooks
```

## Signed release build

Release builds are signed only on machines that have the private key. Create a `keystore.properties` file in the project root (it is git-ignored) that points to a keystore kept **outside** the repository:

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Then run `./gradlew :app:assembleRelease`; the APK is `app/build/outputs/apk/release/app-release.apk`. Without the file, release builds still work but are unsigned.

## Test a reminder (debug builds only)

Debug builds are installed as `com.example.prayernotifier.debug`, next to the release app, so a phone can keep its real reminders while a debug build is tested. They include a small test trigger that fires real reminders and silences through the same path as scheduled ones, without changing the phone's clock (it is not part of release builds):

```sh
# All five prayers, one minute apart, starting in 10 seconds, "5 minutes before" wording
adb shell am broadcast -a com.example.prayernotifier.debug.TEST_REMINDER \
    -n com.example.prayernotifier.debug/com.example.prayernotifier.debug.TestReminderReceiver

# One prayer, custom delay and lead time (lead 0 = "it's time"); "Jumua" works too
adb shell am broadcast -a com.example.prayernotifier.debug.TEST_REMINDER \
    -n com.example.prayernotifier.debug/com.example.prayernotifier.debug.TestReminderReceiver \
    --es prayer Maghrib --ei delay 10 --ei lead 5

# Do Not Disturb on in 5 seconds, off again 60 seconds later
adb shell am broadcast -a com.example.prayernotifier.debug.TEST_REMINDER \
    -n com.example.prayernotifier.debug/com.example.prayernotifier.debug.TestReminderReceiver \
    --es prayer Asr --ei delay 5 --ei silence 60
```

For Do Not Disturb, grant access first in Android's settings (or `adb shell cmd notification allow_dnd com.example.prayernotifier.debug`).

## Multi-day simulation

`MultiDaySimulationTest` runs the real planner, scheduler and alarm handling over years of days in a few seconds, against a virtual clock and an alarm queue that behaves like Android's. It checks every reminder and every Do Not Disturb start and end against the times worked out from the prayer times: ten years in Algiers, three in London (daylight saving), Oslo (Isha after midnight) and Makkah (Ramadan), two in Tromsø (polar day and night), 2099 to 2101, varied settings including changed silence lengths, and random restarts and re-plans. About 180,000 events in all, each to the minute.

```sh
./gradlew :app:testDebugUnitTest --tests '*MultiDaySimulationTest*'
```

## Optional: the Thmanyah Arabic font

The Arabic interface is designed for [Thmanyah (خط ثمانية)](https://font.thmanyah.com/). Its license allows embedding it in an app but **forbids hosting the font files**, so they are not in this repository. Without them, Arabic uses the bundled Amiri font and everything else works the same.

To build with Thmanyah:

1. Download the font from [font.thmanyah.com](https://font.thmanyah.com/) (free; the site asks for an email).
2. Copy these five files from the `otf/` folders into `app/src/main/res-licensed/font/` (the folder is git-ignored), renamed as shown:

   | From the download | Save as |
   |---|---|
   | `thmanyahserifdisplay-Regular.otf` | `thmanyah_serif_display.otf` |
   | `thmanyahserifdisplay-Bold.otf` | `thmanyah_serif_display_bold.otf` |
   | `thmanyahsans-Regular.otf` | `thmanyah_sans.otf` |
   | `thmanyahsans-Medium.otf` | `thmanyah_sans_medium.otf` |
   | `thmanyahsans-Bold.otf` | `thmanyah_sans_bold.otf` |

3. Build again. The app detects the files automatically.

## Project structure

```
app/src/main/java/com/example/prayernotifier/
├── MainActivity.kt          # edge-to-edge, splash animation, language restore
├── data/                    # prayer times for a place and date
│   ├── calculation/         # on-device calculation, methods, country → method table
│   ├── location/            # GPS fix, place name, country and time zone
│   ├── notifications/       # reminders and Do Not Disturb: planning, alarms, receiver
│   └── persistence/         # settings storage
├── i18n/                    # app language switching, prayer and method names
└── ui/
    ├── AppShell.kt          # Home ↔ Settings with animated page transitions
    ├── home/                # Home screen and its ViewModel
    ├── settings/            # Settings screen and its ViewModel
    ├── components/Wonder.kt # design system components (arch, ornaments, buttons, chips)
    └── theme/               # colors, typography (English and Arabic), shapes
app/src/main/res/
├── values/, values-ar/      # English and Arabic strings
├── drawable/                # Phosphor icons (ph_*), launcher icon, splash animation
└── font/                    # Yeseva One, Tenor Sans, Raleway, Amiri
app/src/test/                # unit tests, including the multi-day simulation
app/src/debug/               # debug-only trigger for test reminders and silences
archive/                     # the original Flutter version of the app (not maintained)
```
