# Agile Prices for Android and Wear OS

Agile Prices is a standalone Android app for Octopus Agile electricity prices. It is built for quick glanceable decisions on a watch and a richer phone/adaptive view: what the price is now, when the cheapest upcoming window for your chosen run time starts, and what the next half-hour prices look like.

The app is independent and is not affiliated with, endorsed by or sponsored by Octopus Energy.

## Current scope

- One app package for phones, resizable Android/ChromeOS windows and Wear OS watches.
- Phone and watch state is local to each installed device; there is no Data Layer sync yet.
- Octopus Agile import tariffs only.
- Region-based setup using the public Octopus products API.
- Optional one-shot location suggestion for the electricity region, with an account-verification warning.
- Direct-debit Agile tariff selection where Octopus publishes multiple payment methods for a region.
- Current p/kWh price from standard unit rates.
- Exact cheapest future window for the remembered run time, plus independently priced whole-hour start- and finish-timer recommendations.
- Phone/adaptive Material 3 UI showing current price, cheapest remembered-duration window, planning controls and an interactive 24-hour price graph.
- Wear app UI tuned for round watch screens.
- Wear Tile showing current price and cheapest window.
- Wear SHORT_TEXT complication showing current price.
- Android home-screen widget showing cached current price and cheapest window.
- Optional phone notification while the current price is at or below zero, with the time until it becomes positive.
- Periodic background refresh with a network-connected WorkManager constraint, plus bounded exponential retries for temporary network/server failures or an incomplete price cache.

The location suggestion uses GB electricity-region boundaries obtained from
[Northern Powergrid Open Data](https://northernpowergrid.opendatasoft.com/api/explore/v2.1/catalog/datasets/all_dno_boundaries/exports/geojson), under the [Northern Powergrid Open Data Licence v1.0](https://northernpowergrid.opendatasoft.com/p/opendatalicence/). Supported by Northern Powergrid Open Data. The suggestion is only a convenience: the electricity account remains authoritative, especially near boundaries.

Out of scope for this version: account API keys, usage history, export tariffs, Go, Intelligent Go, spend analysis and watch/phone Data Layer sync.

## Project layout

- `app/src/main/java/com/nedrichards/agileprices/` - app, data, pricing logic, phone/adaptive UI, Wear UI, Tile, complication and worker code.
- `app/src/test/java/com/nedrichards/agileprices/` - unit tests for tariff and price logic.
- `app/src/main/AndroidManifest.xml` - shared app, Tile and complication declarations.
- `app/src/phone/AndroidManifest.xml` - phone widget and notification declarations.
- `app/src/wear/AndroidManifest.xml` - Wear-only hardware requirement.
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

The cheapest-window calculation tries continuous starts from the next minute so the run can use part of the current or upcoming half-hour when that is cheapest. Start-timer and finish-timer recommendations separately price runs scheduled at whole-hour delays, and only use windows fully covered by cached tariff data inside the search horizon. Repeated local clock times around the UK autumn daylight-saving transition are disambiguated with `BST` or `GMT` in phone window ranges.

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

For Google Play, build and upload separate phone and Wear Android App Bundles
under the same application listing. Release signing must be configured first:

```sh
env GRADLE_USER_HOME="$PWD/.gradle-local" ./gradlew bundlePhoneRelease bundleWearRelease
```

- Phone artifact: `app/build/outputs/bundle/phoneRelease/app-phone-release.aab`
- Wear artifact: `app/build/outputs/bundle/wearRelease/app-wear-release.aab`

The phone artifact does not declare the watch hardware feature. The Wear
artifact declares `android.hardware.type.watch` as required and uses a distinct
version code. This follows Google Play's Wear OS packaging requirement that the
watch and mobile builds are uploaded as separate artifacts.

Release signing and versioning are documented in
`release-verification/signing-and-versioning.md`. Debug builds do not require a
release keystore.

## Manual verification

After installing on a phone or resizable Android/ChromeOS window:

1. Launch Agile Prices.
2. Either choose a UK electricity region manually or press **Use my location**. Confirm location permission is requested only after that press, the resulting suggestion says to check the electricity account, and denying permission leaves manual selection usable.
3. Confirm the app loads a current price, current half-hour period, exact cheapest window and independently priced start- and finish-timer recommendations for the selected run time.
4. Change run time and search horizon, then confirm all three recommendations recompute from cached rates.
5. Confirm the interactive 24-hour graph supports left/right selection while vertical swipes or up/down navigation scroll the page.
6. Confirm stale/error messaging and manual refresh remain usable at compact and wider window sizes.
7. Add the Android home-screen widget and confirm it shows cached current-price and cheapest-window data, then opens the app when tapped.
8. Enable at-or-below-zero alerts, then confirm a negative price posts an ongoing notification, a zero price keeps it, and a £0.01-or-higher price removes it.
9. Confirm the cheapest panel shows the exact range and price plus separate priced **Start in** and **Finish in** timer recommendations without wrapping awkwardly.

After installing on a Wear OS emulator or device:

1. Launch Agile Prices.
2. Either choose a UK electricity region manually or press **Use my location**. Confirm location permission is requested only after that press, and denying permission leaves the scrollable manual list usable.
3. Confirm the app shows the selected-duration average for starting now, followed by separate `Start in Nh` and `Finish in Nh` recommendations with their averages, without exact ranges or dates in the watch summary.
4. Change run time and search horizon, then confirm both timer recommendations recompute from cached rates.
5. With negative prices in the visible horizon, confirm the sparkline includes a horizontal zero-price baseline, including when every visible price is negative.
6. Add the Tile and confirm it shows the current price plus cheapest window.
7. Add the SHORT_TEXT complication and confirm it opens the app when tapped.
