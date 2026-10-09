# Prayer Notifier

**English** · [العربية](README.ar.md) · Version 0.3.3 · [Changelog](CHANGELOG.md)

A privacy-first prayer times app for Android. Prayer times are calculated on your phone from the sun's position, so it works offline for any date, and the app has no internet permission at all. A reminder before each prayer, Do Not Disturb during prayer, and Jumua on Fridays, in Arabic and English. No ads, no analytics, no tracking.

Google is still involved in finding and naming your location for now; [Privacy](docs/privacy.md) says exactly how, and how to avoid it, and [TODO](TODO.md#privacy) has the plan to remove it.

<p>
  <img src="docs/screenshots/home_en.png" width="160" alt="Home screen: next prayer, countdown and today's prayers" />
  <img src="docs/screenshots/settings_en.png" width="160" alt="Settings: each prayer with today's time and its reminder, and Jumua on Fridays" />
  <img src="docs/screenshots/prayer_sheet_en.png" width="160" alt="One prayer's reminder and time correction, with today's time changing from 12:35 to 12:40" />
  <img src="docs/screenshots/dnd_en.png" width="160" alt="Do Not Disturb at prayer time: how long each prayer stays silent, and today's window" />
  <img src="docs/screenshots/home_ar.png" width="160" alt="Home screen in Arabic, right-to-left" />
</p>

## Features

- **Calculated on your phone**: any date, no internet, nothing to download. The method and the Asr time follow your country's official authority, or the one your mosque uses.
- **A reminder before each prayer**: a quiet notification, on time or 5 to 30 minutes before, for all prayers at once or each one.
- **Do Not Disturb at prayer time**: on at each adhan, off by itself after 35 minutes for Fajr, an hour for Jumua and 25 minutes for the others, or the length you choose.
- **Jumua on Fridays**: its own reminder (30 minutes before by default) and its own silence.
- **Time corrections**: move a prayer up to 30 minutes either way to match your mosque, seeing today's time change as you do, and the Hijri date up to 2 days.
- **Countdown and calendar**: the next prayer, Hijri and Gregorian dates, any day you pick, saved places.
- **Arabic and English**, right to left in Arabic, with a design inspired by [Wonderous](https://wonderous.app).

## Documentation

| Document | What's in it |
|---|---|
| [How it works](docs/how-it-works.md) | Every feature in detail, the calculation and methods by country, reminders, Do Not Disturb, Jumua, permissions |
| [Privacy](docs/privacy.md) | What never leaves the phone, where Google is still involved, how to share less today |
| [Development](docs/development.md) | Building, signed releases, test commands, the multi-day simulation, project structure, the optional Arabic font |
| [Changelog](CHANGELOG.md) | What changed in each version |
| [TODO](TODO.md) | Planned work, starting with removing Google services |

## Quick start

```sh
git clone https://github.com/abderrahmane-harouat/prayer-notifier.git
cd prayer-notifier
git config core.hooksPath .githooks   # blocks keys and licensed fonts from commits
./gradlew :app:installDebug           # build and install on a device or emulator
```

Needs JDK 17–21 and Android SDK 36. The app runs on Android 8.0 and later; Do Not Disturb at prayer time needs Android 10. More in [Development](docs/development.md).

## Credits

- Prayer time calculation: [Adhan](https://github.com/batoulapps/adhan-java) by Batoul Apps (MIT); method parameters from the [Aladhan API](https://aladhan.com/prayer-times-api)
- Design inspiration: [Wonderous](https://wonderous.app) by gskinner
- Icons: [Phosphor Icons](https://phosphoricons.com) (MIT)
- Fonts (SIL Open Font License): [Yeseva One](https://fonts.google.com/specimen/Yeseva+One), [Tenor Sans](https://fonts.google.com/specimen/Tenor+Sans), [Raleway](https://fonts.google.com/specimen/Raleway), [Amiri](https://fonts.google.com/specimen/Amiri)
- Arabic typeface (optional, not included): [Thmanyah](https://font.thmanyah.com/)

## License

The code is released under the [MIT License](LICENSE) © 2026 Abderrahmane Harouat. Bundled fonts and icons keep their own licenses, with the texts in `app/src/main/assets/licenses/`. The Thmanyah font is not part of this repository and is not covered by the MIT License.
