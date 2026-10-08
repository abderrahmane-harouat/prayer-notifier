# TODO

Planned features and follow-ups. Nothing here is implemented yet.

## Privacy

Goal: the app trusts no one with the user's location, Google included, and works fully on Android without Google Play services. What is still involved today is listed in the README's [Privacy](README.md#privacy) section. In order of priority:

- [ ] **Get the position without Google Play services**

  Today `FusedPositionProvider` asks Google's Fused Location Provider, so on GrapheneOS, LineageOS or any phone without Play services the location request fails (verified by disabling Play services on the emulator), and with "Google Location Accuracy" on, nearby Wi-Fi and cell data can reach Google.
  - Use Android's own `LocationManager` with `GPS_PROVIDER` (AOSP, no Google): `getCurrentLocation` on Android 11+, `requestSingleUpdate` before that, then `getLastKnownLocation` as the fallback, like today.
  - GPS alone can take a minute indoors with no network help. Show "Looking for GPS…" with a way to cancel, and suggest stepping outside or entering the place by hand (see below).
  - Optional: offer `NETWORK_PROVIDER` only when the user turns it on, explaining that it can involve the phone's network location service (Google on most phones).

- [ ] **Replace the Play services "Turn on device location?" dialog**

  `turnOnLocation()` in `HomeScreen.kt` uses Play services' `SettingsClient`. Open Android's location settings (`ACTION_LOCATION_SOURCE_SETTINGS`) instead, with a short explanation first. One extra screen for the user, no Google.

- [ ] **Remove the `play-services-location` dependency**

  Once the two items above are done, nothing needs it. Then the APK has no closed-source Google code, which also makes the app eligible for F-Droid.

- [ ] **Name places and find their country offline**

  Today Android's `Geocoder` sends the coordinates to Google's servers on most phones.
  - Bundle a city list, e.g. GeoNames `cities5000` (CC BY 4.0, roughly 50,000 places), trimmed to name, Arabic name, coordinates and country code, and pick the nearest city. Expected size a few MB; measure it.
  - Take the country from bundled country borders (e.g. Natural Earth admin-0, public domain, simplified) instead of the nearest city, so places near a border get the right country and calculation method.
  - This also gives Arabic place names in the Arabic interface (today the geocoder is asked for English names).
  - Credit GeoNames in the README and in the app, as its license requires.

- [ ] **Find a place's time zone offline**

  A place stores the phone's time zone at the moment of the fix, which is right when you are there. Places saved by the old version have none and fall back to the phone's current zone, so a saved place in another time zone shows wrong times. Bundle time zone borders (timezone-boundary-builder, ODbL, simplified) and look the zone up from the coordinates.

- [ ] **Set a place without the location permission**

  Search the bundled city list, or type coordinates. Then the app can work with location permission denied entirely, and the user can add a place they are not at.

- [ ] **Distribute outside Google Play**

  F-Droid (needs the dependency removed) and signed GitHub releases, with the signing certificate's SHA-256 fingerprint published in the README so users can check an APK. Look into reproducible builds so anyone can confirm a release matches the source.

- [ ] **Test on a phone without Google**

  Before calling the app Google-free, run it on GrapheneOS (or an emulator image without Google APIs) through the whole flow: first launch, location, reminders, reboot.

## Planned

Do Not Disturb at prayer time and Jumua shipped in 0.2.0. Follow-ups:

- [ ] **Finish testing Do Not Disturb on Samsung One UI**

  Done on the stock Android 14 emulator: the rule turns on at the adhan and off at the end, survives a restart, leaves the user's own Do Not Disturb alone, keeps a manual "off" during prayer, and is removed when the feature is switched off. Done on a Samsung Galaxy M32 (One UI, Android 13): the rule is created, turns on and off on time, and the user's own Do Not Disturb is kept. Still to check on Samsung: a restart in the middle of a silence, and a real prayer time (not the test command) with the release build.

- [ ] **Let the user choose how long each silence lasts**

  Fixed today at 35 minutes for Fajr, 1 hour for Jumua and 15 minutes for the others (`SilenceSettings.minutesFor`). A duration per prayer in the Do Not Disturb card (for example 10 to 60 minutes) would fit mosques with longer prayers.

- [ ] **A time correction for Jumua**

  Jumua uses Dhuhr's time and correction. Many mosques start the sermon later than Dhuhr; a separate Jumua correction would move its reminder and silence without touching Dhuhr on other days.

- [ ] **Show the night's late Isha after midnight on Home**

  Far north in summer, Isha can fall after midnight. Reminders and Do Not Disturb handle it (tested in `MultiDaySimulationTest`), but after midnight Home shows the new day's list and counts down to Fajr, not to the previous night's Isha still ahead.

- [ ] **Reminders inside another prayer's silence**

  Where prayers are close together (Oslo in winter: Asr less than an hour after Dhuhr), Jumua's hour of silence can cover the Asr reminder, which then arrives without sound. Consider ending a silence a little before the next prayer's reminder, or letting the app's own reminders through.

- [ ] **Do Not Disturb on Android 8 and 9**

  The feature needs Android 10 (the app's own automatic rule). Older phones could use `setInterruptionFilter` with care not to undo the user's own Do Not Disturb; they are a small share today.
