# Prayer Notifier

**English** · [العربية](README.ar.md)

A prayer times notifier for Android that works fully offline. It shows the five daily prayer times for where you are and sends a reminder before each prayer. The times are calculated on your phone from the sun's position, so there is nothing to download and it works for any date, with or without internet. Available in Arabic and English, with no ads, no analytics and no tracking.

<p>
  <img src="docs/screenshots/home_en.png" width="200" alt="Home screen: next prayer, countdown and today's prayers" />
  <img src="docs/screenshots/settings_en.png" width="200" alt="Settings: reminders for every prayer" />
  <img src="docs/screenshots/prayer_sheet_en.png" width="200" alt="One prayer's reminder and time correction" />
  <img src="docs/screenshots/home_ar.png" width="200" alt="Home screen in Arabic, right-to-left" />
</p>

## Features

- **Prayer times for your location**: Fajr, Dhuhr, Asr, Maghrib and Isha, from your GPS position. Save places and switch between them.
- **Countdown to the next prayer**, with the Hijri and Gregorian dates. Browse any other day, and go back to today with one tap.
- **Reminders, not alarms**: a normal notification with a soft sound before each prayer, worded to match your setting ("5 minutes until Maghrib", or "Time for Maghrib prayer" when set to on time). Choose the lead time for all prayers at once (on time, 5, 10, 15 or 30 minutes), or per prayer, and turn any prayer on or off.
- **Time corrections**: shift any prayer by up to ±30 minutes, and the Hijri date by ±2 days, to match your local mosque or moon sighting. While you adjust a prayer, its time today is shown and updates with each tap ("12:35 › 12:40"), so you can match the mosque without going back to check.
- **Calculated on your phone**: no internet needed, ever, and no date limit. The calculation method follows your country's official authority (for example Algeria's ministry in Algeria, Umm al-Qura in Saudi Arabia, Diyanet in Turkey), or pick another one in Settings to match your mosque. Asr follows the country too: the later Hanafi time in Pakistan, India, Bangladesh and Afghanistan, the standard time elsewhere, and either can be chosen in Settings.
- **English and Arabic**: full right-to-left layout in Arabic. Pick the language in the app, or in Android's per-app language settings (Android 13+).
- **A museum-book design**: warm dark tones, classical serif titles and a hand-drawn scene for each prayer, inspired by the [Wonderous](https://wonderous.app) app. An animated Alhambra splash plays on launch (Android 12+).

## How it works

| Part | Details |
|---|---|
| Prayer times | Calculated on the device with the [Adhan](https://github.com/batoulapps/adhan-java) library from your coordinates and the date: Fajr and Isha from the sun's angle below the horizon, Dhuhr at solar noon, Asr when a shadow equals its object plus its noon shadow (Shafi'i, Maliki, Hanbali) or twice its object (Hanafi), Maghrib at sunset. Shown in the place's time zone. |
| Calculation method | Chosen by the place's country (`data/calculation/CountryMethods.kt`): the country's own authority where there is one (Algeria, Morocco, Tunisia, Egypt, Umm al-Qura, UAE, Qatar, Kuwait, Jordan, Turkey, Iran, Pakistan, Russia, France, Portugal, Singapore, Malaysia, Indonesia, ISNA for the US and Canada), otherwise the region's usual method (Egyptian for Africa, Umm al-Qura for the Arabian Peninsula, Karachi for South Asia), otherwise Muslim World League. Hanafi Asr in Pakistan, India, Bangladesh and Afghanistan, standard Asr elsewhere. The angles and minute offsets match the [Aladhan API](https://aladhan.com/prayer-times-api) methods the app used before, and unit tests check the results against Aladhan within a minute. You can pick any method and Asr rule yourself in Settings. |
| Country | From the geocoder when the location is taken; offline, from the mobile network's country or the phone's time zone. Never from the language setting. |
| Hijri date | Android's built-in Umm al-Qura calendar, with your ±2 day correction. |
| Reminders | Scheduled with `AlarmManager` as plain notifications (no alarm-clock UI, no full-screen alert). Exact timing when Android allows it; otherwise they still arrive, possibly a few minutes late. They are re-planned every night and after a reboot. |
| Location | One GPS fix through the Fused Location Provider, named with Android's geocoder (or by its coordinates when no name is found). |

### Permissions

| Permission | Why |
|---|---|
| Location (while using the app) | Prayer times depend on where you are. Asked on first launch. |
| Notifications (Android 13+) | To show prayer reminders. Asked once the prayer times are on screen. |
| Exact alarms (`USE_EXACT_ALARM`, and `SCHEDULE_EXACT_ALARM` on Android 12) | Granted automatically, so reminders arrive on the minute. Never shown to you as an "alarms" permission screen. |
| Run at startup | To re-plan reminders after the phone restarts. |

The app has no accounts, analytics, ads or tracking, and no internet permission. Your coordinates go only to Android's built-in geocoder (to name the place and find its country).

## Build and run

Requirements: Android Studio (or the command line), **JDK 17–21** (newer JDKs are not supported by this Gradle/AGP version), Android SDK 36. The app runs on Android 8.0 (API 26) and later.

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

### Signed release build

Release builds are signed only on machines that have the private key. Create a `keystore.properties` file in the project root (it is git-ignored) that points to a keystore kept **outside** the repository:

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Then run `./gradlew :app:assembleRelease`; the APK is `app/build/outputs/apk/release/app-release.apk`. Without the file, release builds still work but are unsigned.

### Test a reminder (debug builds only)

Debug builds include a small test trigger that fires real reminders through the same path as scheduled ones, without changing the phone's clock (it is not part of release builds):

```sh
# All five prayers, one minute apart, starting in 10 seconds, "5 minutes before" wording
adb shell am broadcast -a com.example.prayernotifier.debug.TEST_REMINDER \
    -n com.example.prayernotifier/.debug.TestReminderReceiver

# One prayer, custom delay and lead time (lead 0 = "it's time")
adb shell am broadcast -a com.example.prayernotifier.debug.TEST_REMINDER \
    -n com.example.prayernotifier/.debug.TestReminderReceiver \
    --es prayer Maghrib --ei delay 10 --ei lead 5
```

### Optional: the Thmanyah Arabic font

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
├── data/                    # prayer times, location, settings
│   ├── calculation/         # on-device calculation, methods, country → method table
│   └── notifications/       # reminder scheduling, alarm receiver, notifications
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
archive/                     # the original Flutter version of the app (not maintained)
```

## Credits

- Prayer time calculation: [Adhan](https://github.com/batoulapps/adhan-java) by Batoul Apps (MIT, license in `app/src/main/assets/licenses/`); method parameters from the [Aladhan API](https://aladhan.com/prayer-times-api)
- Design inspiration: [Wonderous](https://wonderous.app) by gskinner
- Icons: [Phosphor Icons](https://phosphoricons.com) (MIT, license in `app/src/main/assets/licenses/`)
- Fonts (SIL Open Font License, licenses in `app/src/main/assets/licenses/`): [Yeseva One](https://fonts.google.com/specimen/Yeseva+One), [Tenor Sans](https://fonts.google.com/specimen/Tenor+Sans), [Raleway](https://fonts.google.com/specimen/Raleway), [Amiri](https://fonts.google.com/specimen/Amiri)
- Arabic typeface (optional, not included): [Thmanyah](https://font.thmanyah.com/)

## License

The code is released under the [MIT License](LICENSE) © 2026 Abderrahmane Harouat.

Bundled third-party assets keep their own licenses: the fonts (SIL Open Font License) and Phosphor icons (MIT), with their license texts in `app/src/main/assets/licenses/`. The Thmanyah font is not part of this repository and is not covered by the MIT License.
