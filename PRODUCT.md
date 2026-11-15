# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

The product is a native Android app (Kotlin, plain Views). Its landing page is a separate web
surface (see Stack).

## Stack

Landing page: static hand-written HTML/CSS in `docs/`, hosted on GitHub Pages from the repo
(`tanim913.github.io/plain_launcher`). No build step.

## Users

Primary: owners of Xiaomi / Redmi / POCO phones (MIUI, HyperOS) who are fed up with the ads and
clutter MIUI shows on the home screen, App Vault, installer and system apps, and want a clean,
stock-Android-style home screen instead. Typically on budget and mid-range phones, often older
Android versions (the reference device is a Redmi Note 7 on MIUI 12.5 / Android 10, region BD).

Secondary: any Android 8+ user who wants a light, ad-free, stock-style launcher.

## Product Purpose

Replace MIUI's ad-carrying home screen with an open-source, ad-free, stock-Android-style launcher,
and give users a reversible way to switch off the system apps that serve MIUI's ads. Success: the
phone feels like stock Android, no ads, nothing lost (steps, weather, alarm, widgets still there).

## Positioning

- Built for Xiaomi phones first: reads MIUI's own step history (the data App Vault used to show),
  MIUI Clock's real next alarm, and opens MIUI's own Weather/Clock apps.
- Comes with Ad Shield, a PC script that reversibly disables MIUI's ad packages (nothing uninstalled,
  protected core list, full undo).
- Open source (Apache-2.0), no ads, no analytics, no accounts.

## Operating Context

- Distributed as an APK on GitHub Releases (not on the Play Store yet). Install: download, allow
  install from browser/file manager, set as home app.
- MIUI blocks `adb install` without a Mi account; users install by tapping the APK.
- Ad Shield runs from a PC over USB debugging.

## Capabilities and Constraints

- Android 8.0 (API 26) to Android 16 (API 36). Tested hands-on only on a Redmi Note 7; other
  phones are covered by code, not hands-on testing.
- Home pages, 5-slot dock, folders, drag-and-drop, widgets; swipe-up drawer with search and A-Z
  scroller; icon shapes and icon packs; themed icon on Android 13+.
- At-a-glance header: date, weather (Open-Meteo, no key), real next alarm; tapping opens the
  phone's Weather / Clock / Steps.
- Steps page (swipe right on first home page): day vs goal ring, distance, active time, calories,
  hourly chart, 7/30-day history. Sources: MIUI step history; Health Connect; or the phone's step
  sensor recorded in the background.
- Send walks to Health Connect as Walking workouts, so apps like MyNetDiary count them as exercise.
- Gestures: swipe down for notifications, double-tap to lock (accessibility service), notification dots, hidden apps.
- Recents and gesture-nav animations stay the phone maker's (limit of all third-party launchers).
- Ad Shield lists: MIUI tested; Realme untested; no Samsung list yet.

## Brand Commitments

- Name: Plain Launcher.
- App icon: brushed-silver 2×2 app grid (one round tile) on a graphite-to-black background
  (`docs/icon.svg`); the user asked for a silver/black/gray themed icon.
- Voice: plain, honest, specific; states limits openly.

## Evidence on Hand

- Source code, README, Apache-2.0 license, `docs/icon.svg`.
- Real device screenshots can be captured from the user's Redmi Note 7 over ADB, on a plain
  wallpaper (the user's personal wallpaper must not appear).
- No user testimonials, download counts, reviews, press, benchmarks or store ratings exist. Do not
  invent any. No Play Store listing.

## Product Principles

1. No ads, no tracking, nothing leaves the phone unless the user asks (weather request, Health Connect).
2. Keep what MIUI did well (steps, alarm, weather) while removing what it did badly (ads, clutter).
3. Everything reversible: Ad Shield undoes exactly what it did; nothing is uninstalled.
4. Honest about limits: say what is tested, what isn't, and what no launcher can change.
5. Light on old, cheap phones.
