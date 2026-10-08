# Privacy

[← README](../README.md) · [العربية](privacy.ar.md)

The goal is a prayer app that trusts no one with where you are: not the developer, not Google, and as little as possible Android itself. It is not fully there yet. This section says exactly what happens today, and [TODO.md](../TODO.md#privacy) tracks the work to remove each remaining dependency.

## What the app never does

- **It cannot go online.** It has no internet permission, so Android blocks every network connection it might try to open. Nothing can be sent anywhere, even by mistake or by a library.
- No accounts, analytics, crash reporting, ads or trackers.
- Prayer times, the calculation method, the Hijri date and the reminders are all worked out on the phone.

## What stays on the phone

Your current place and saved places (name, coordinates, country, time zone) and your settings, in the app's private storage. Android backup is turned off (`allowBackup="false"`), so none of it is copied to Google Drive or to a new phone, and uninstalling the app deletes all of it.

## Where Google is still involved

| What | How Google is involved | What Google can learn |
|---|---|---|
| Getting your position | The app asks Google Play services' Fused Location Provider for a fix (`FusedPositionProvider.kt`). | With "Google Location Accuracy" on, Play services also uses nearby Wi-Fi networks and cell towers and can send them to Google. With it off, the fix comes from GPS on the phone. |
| Naming the place and finding its country | Android's `Geocoder` (`AndroidGeocoderProvider.kt`). On phones with Google Play services, Google's servers answer it. | Your coordinates, once each time you refresh your location. Nothing when the phone is offline. |
| The "Turn on device location?" dialog | Shown by Google Play services when location is off. | That the app checked the location setting. No coordinates. |
| The `play-services-location` library | Closed-source Google code inside the app, used to talk to Play services. | Nothing directly: it runs inside the app, which cannot go online. But it cannot be audited. |

**The app cannot get a location yet on phones without Google Play services** (GrapheneOS without sandboxed Play, LineageOS without microG): the location request fails (tested with Play services disabled). A place saved earlier keeps working, but a fresh install there has no way to set one. This is the first item in [TODO.md](../TODO.md#privacy).

## How to share less today

- Turn off **Google Location Accuracy** (Settings → Location → Location services). The position then comes from GPS only. Outdoors it is just as accurate; indoors the first fix can take longer.
- Refresh your location **without internet** (airplane mode, or Wi-Fi and data off). The geocoder can't reach Google, so the place is named by its coordinates and the country comes from the mobile network or the time zone. The prayer times are exactly the same.
- Once your place is set, the app needs nothing else: reminders and times keep working with location off.
- Do Not Disturb is switched through Android on the phone itself; nothing about it leaves the phone.

## What has to be trusted to Android

Every app runs on its operating system and depends on it: the GPS hardware and location stack, the clock and time zone, the alarms that fire reminders, notifications, and the app's private storage. These parts of Android run on the phone and the app sends them nothing beyond what they need (no coordinates leave the app except through the services listed above). The app also reads the mobile network's country and the time zone's region, both answered on the phone without a network request. What the operating system, or the phone maker's version of it, does on its own cannot be checked from inside an app. A privacy-focused Android such as GrapheneOS reduces this, and the app aims to fully work there.
