# How it works

[← README](../README.md) · [العربية](how-it-works.ar.md)

## What the app does

- **Prayer times for your location**: Fajr, Dhuhr, Asr, Maghrib and Isha, from your GPS position. Save places and switch between them.
- **Countdown to the next prayer**, with the Hijri and Gregorian dates. Browse any other day, and go back to today with one tap.
- **Reminders, not alarms**: a normal notification with a soft sound before each prayer, worded to match your setting ("5 minutes until Maghrib", or "Time for Maghrib prayer" when set to on time). Choose the lead time for all prayers at once (on time, 5, 10, 15 or 30 minutes), or per prayer, and turn any prayer on or off.
- **Do Not Disturb at prayer time**: on by default. The phone goes quiet at each adhan and comes back by itself: 35 minutes after Fajr, 1 hour after Jumua, 25 minutes after the others by default, and you can set each prayer anywhere from 5 minutes to 2 hours. Switch it off entirely, or per prayer. Settings shows today's silence for each prayer ("today 12:35 to 13:00"); until Android grants Do Not Disturb access, the whole Do Not Disturb card, main switch included, is greyed out and locked, since none of it could take effect. Calls still arrive but silently, even when someone calls twice; only alarms and media make sound, and your own Do Not Disturb is never touched.
- **Jumua on Fridays**: Dhuhr becomes Jumua, with its own reminder (30 minutes before by default, so there is time to get to the mosque) and its own hour of silence.
- **Time corrections**: shift any prayer by up to ±30 minutes, and the Hijri date by ±2 days, to match your local mosque or moon sighting. While you adjust a prayer, its time today is shown and updates with each tap ("12:35 › 12:40"), so you can match the mosque without going back to check.
- **Calculated on your phone**: no internet needed, ever, and no date limit. The calculation method follows your country's official authority (for example Algeria's ministry in Algeria, Umm al-Qura in Saudi Arabia, Diyanet in Turkey), or pick another one in Settings to match your mosque. Asr follows the country too: the later Hanafi time in Pakistan, India, Bangladesh and Afghanistan, the standard time elsewhere, and either can be chosen in Settings.
- **Settings that explain themselves**: every section says what it is for, and each prayer shows today's time next to its reminder ("05:23 · 5 min before"). Tap a prayer to change its reminder or correct its time.
- **English and Arabic**: full right-to-left layout in Arabic. Pick the language in the app, or in Android's per-app language settings (Android 13+).
- **A museum-book design**: warm dark tones, classical serif titles and a hand-drawn scene for each prayer, inspired by the [Wonderous](https://wonderous.app) app. An animated Alhambra splash plays on launch (Android 12+).

## Under the hood

| Part | Details |
|---|---|
| Prayer times | Calculated on the device with the [Adhan](https://github.com/batoulapps/adhan-java) library from your coordinates and the date: Fajr and Isha from the sun's angle below the horizon, Dhuhr at solar noon, Asr when a shadow equals its object plus its noon shadow (Shafi'i, Maliki, Hanbali) or twice its object (Hanafi), Maghrib at sunset. Shown in the place's time zone. |
| Calculation method | Chosen by the place's country (`data/calculation/CountryMethods.kt`): the country's own authority where there is one (Algeria, Morocco, Tunisia, Egypt, Umm al-Qura, UAE, Qatar, Kuwait, Jordan, Turkey, Iran, Pakistan, Russia, France, Portugal, Singapore, Malaysia, Indonesia, ISNA for the US and Canada), otherwise the region's usual method (Egyptian for Africa, Umm al-Qura for the Arabian Peninsula, Karachi for South Asia), otherwise Muslim World League. Hanafi Asr in Pakistan, India, Bangladesh and Afghanistan, standard Asr elsewhere. The angles and minute offsets match the [Aladhan API](https://aladhan.com/prayer-times-api) methods the app used before, and unit tests check the results against Aladhan within a minute. You can pick any method and Asr rule yourself in Settings. |
| Country | From the geocoder when the location is taken; offline, from the mobile network's country or the phone's time zone. Never from the language setting. |
| Hijri date | Android's built-in Umm al-Qura calendar, with your ±2 day correction. |
| Reminders | Scheduled with `AlarmManager` as plain notifications (no alarm-clock UI, no full-screen alert). Exact timing when Android allows it; otherwise they still arrive, possibly a few minutes late. Re-planned every night just after midnight and after a reboot, for yesterday and today together: far north in summer, Isha can fall after midnight and still belongs to the day before. A day that can't be calculated (polar day or night) is skipped without breaking the nightly re-plan. |
| Do Not Disturb | The app's own automatic rule, named "Prayer time" and visible in Android's Do Not Disturb settings (Modes on Android 15+). It is switched on at the adhan and off at the end by exact alarms; the end time is saved, so a silence that was on during a restart is ended or resumed correctly. Each silence lasts the length set for that prayer (5 to 120 minutes; 35 for Fajr, 60 for Jumua and 25 for the others by default). Silences that overlap (a long Maghrib silence reaching Isha, or Jumua's hour reaching Asr in a northern winter) join into one. An "on time" reminder fires just before the silence starts, so it is still heard. Needs Android 10 or later and Do Not Disturb access, which Android grants only from its settings screen. |
| Jumua | On Fridays, Dhuhr's time (with Dhuhr's correction) under Jumua's own reminder and silence settings. |
| Location | One fix through Google Play services' Fused Location Provider, named with Android's geocoder (Google's servers on most phones), or by its coordinates when no name is found. See [Privacy](privacy.md). |

## Permissions

| Permission | Why |
|---|---|
| Location (while using the app) | Prayer times depend on where you are. Asked on first launch. |
| Notifications (Android 13+) | To show prayer reminders. Asked once the prayer times are on screen. |
| Exact alarms (`USE_EXACT_ALARM`, and `SCHEDULE_EXACT_ALARM` on Android 12) | Granted automatically, so reminders arrive on the minute. Never shown to you as an "alarms" permission screen. |
| Do Not Disturb access (`ACCESS_NOTIFICATION_POLICY`) | To silence the phone at prayer time. Android grants it only from its own "Do Not Disturb access" screen; the app opens it for you once, and Settings shows a banner while it is missing. |
| Run at startup | To re-plan reminders, and end or resume a silence, after the phone restarts. |

No internet permission: see [Privacy](privacy.md) for what that means and what still involves Google.
