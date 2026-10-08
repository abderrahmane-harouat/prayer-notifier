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

- [ ] **Silence the phone during prayer (Do Not Disturb)**

  When a prayer time arrives, turn on Do Not Disturb for a few minutes, then turn it back off automatically.

  Ideas for the behaviour:
  - A switch per prayer (like reminders), plus a duration: 10, 15, 20 or 30 minutes.
  - Starts at the prayer time itself (not at the earlier reminder), using the same exact scheduling as reminders.
  - Ends on its own; if the user changes Do Not Disturb manually in between, leave their choice alone.
  - Optional: let the prayer reminder itself still be heard (priority exception).

  Technical notes:
  - Needs Do Not Disturb access (`ACCESS_NOTIFICATION_POLICY`). Android grants it **only from a system Settings screen**, never from an in-app dialog, so the app has to open "Do Not Disturb access" once, with a short explanation first. This is the one case where sending the user to Settings can't be avoided.
  - Prefer `AutomaticZenRule` (a named "Prayer time" mode the user can see and edit in system settings, and Android 15's Modes) over flipping `setInterruptionFilter` directly, so it's clear which app silenced the phone.
  - Schedule a "start" and an "end" alarm per prayer, re-planned nightly and after reboot like reminders; if the end alarm is missed (phone off), clear the rule on the next launch.
  - Test on Samsung One UI as well as stock Android: Do Not Disturb behaves differently there.
