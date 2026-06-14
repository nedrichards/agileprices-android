# Agile Prices for Android and Wear OS

Agile Prices is a standalone Android app for Octopus Agile electricity prices. It is built for quick glanceable decisions on a watch and a richer phone/adaptive view: what the price is now, when the cheapest upcoming window for your chosen run time starts, and what the next half-hour prices look like.

The app is independent and is not affiliated with, endorsed by or sponsored by Octopus Energy.

## Current scope

- One app package for phones, resizable Android/ChromeOS windows and Wear OS watches.
- Phone and watch state is local to each installed device; there is no Data Layer sync yet.
- Octopus Agile import tariffs only.
- Region-based setup using the public Octopus products API.
- Direct-debit Agile tariff selection where Octopus publishes multiple payment methods for a region.
- Current p/kWh price from standard unit rates.
- Cheapest future window for the remembered run time from cached half-hour rates.
- Phone/adaptive Material 3 UI showing current price, cheapest remembered-duration window, planning controls and an interactive 24-hour price graph.
- Wear app UI tuned for round watch screens.
- Wear Tile showing current price and cheapest window.
- Wear SHORT_TEXT complication showing current price.
- Periodic background refresh with a network-connected WorkManager constraint and no retry wakeups between the normal 30-minute cadence.

Out of scope for this version: account API keys, usage history, export tariffs, Go, Intelligent Go, spend analysis, notifications, home-screen widgets and watch/phone Data Layer sync.

## Project layout

- `app/src/main/java/com/nedrichards/agileprices/` - app, data, pricing logic, phone/adaptive UI, Wear UI, Tile, complication and worker code.
- `app/src/test/java/com/nedrichards/agileprices/` - unit tests for tariff and price logic.
- `app/src/main/AndroidManifest.xml` - Wear OS app, Tile and complication declarations.
- `gradle/libs.versions.toml` - dependency versions.

## Key implementation details

The app stores its configuration and cached price windows in Preferences DataStore:

- `selectedRegionCode`
- `selectedTariffCode`
- `loadDurationMinutes`, default `60`
- `searchHorizonMinutes`, default `480`
- cached future price windows
- last refresh metadata

Prices are represented internally as pence per kWh. This keeps display and calculation units aligned with the Octopus standard unit-rate API and avoids unnecessary GBP conversion churn.

The cheapest-window calculation tries continuous starts from the next half-hour boundary at 30-minute cadence, then falls back to practical half-hour and whole-hour starts if needed. That keeps the answer aligned with Agile half-hour price periods. Repeated local clock times around the UK autumn daylight-saving transition are disambiguated with `BST` or `GMT`.

## Build and test

Use a project-local Gradle cache if the normal home cache is not writable:

```sh
env GRADLE_USER_HOME="$PWD/.gradle-local" ./gradlew testPhoneDebugUnitTest testWearDebugUnitTest assemblePhoneDebug assembleWearDebug
```

Generate Android project metadata:

```sh
android describe --project_dir="$PWD"
```

The standard phone debug APK is generated at:

```text
app/build/outputs/apk/phone/debug/app-phone-debug.apk
```

For Android Studio runs, open **Build Variants** and choose the form-factor
variant for the device:

- Phone or Chromebook window: `phoneDebug`
- Wear OS emulator or watch: `wearDebug`

The Wear flavor keeps the same application id but adds
`android.hardware.type.watch` as a required feature so Android Studio's Wear
deployment check accepts it. Its debug APK is generated at:

```text
app/build/outputs/apk/wear/debug/app-wear-debug.apk
```

For Google Play, build and upload separate phone and Wear artifacts under the
same application listing:

```sh
env GRADLE_USER_HOME="$PWD/.gradle-local" ./gradlew assemblePhoneRelease assembleWearRelease
```

- Phone artifact: `app/build/outputs/apk/phone/release/app-phone-release-unsigned.apk`
- Wear artifact: `app/build/outputs/apk/wear/release/app-wear-release-unsigned.apk`

The phone artifact does not declare the watch hardware feature. The Wear
artifact declares `android.hardware.type.watch` as required and uses a distinct
version code. This follows Google Play's Wear OS packaging requirement that
watch APKs are separate from mobile APKs.

Release signing and versioning are documented in
`release-verification/signing-and-versioning.md`. Debug builds do not require a
release keystore.

## Manual verification

After installing on a phone or resizable Android/ChromeOS window:

1. Launch Agile Prices.
2. Choose a UK electricity region.
3. Confirm the app loads a current price, current half-hour period and cheapest window for the selected run time.
4. Change run time and search horizon, then confirm the cheapest window recomputes from cached rates.
5. Confirm the interactive 24-hour graph supports left/right selection while vertical swipes or up/down navigation scroll the page.
6. Confirm stale/error messaging and manual refresh remain usable at compact and wider window sizes.

After installing on a Wear OS emulator or device:

1. Launch Agile Prices.
2. Choose a UK electricity region.
3. Confirm the app loads a current price and a cheapest window for the selected run time.
4. Change run time and search horizon, then confirm the cheapest window recomputes from cached rates.
5. Add the Tile and confirm it shows the current price plus cheapest window.
6. Add the SHORT_TEXT complication and confirm it opens the app when tapped.
