# Agile Prices for Wear OS

Agile Prices is a standalone Wear OS app for Octopus Agile electricity prices. It is built for quick glanceable decisions on a watch: what the price is now, when the cheapest upcoming load window starts, and what the next half-hour prices look like.

The app is independent and is not affiliated with, endorsed by or sponsored by Octopus Energy.

## Current scope

- Standalone watch-only app with no phone companion.
- Octopus Agile import tariffs only.
- Region-based setup using the public Octopus products API.
- Direct-debit Agile tariff selection where Octopus publishes multiple payment methods for a region.
- Current p/kWh price from standard unit rates.
- Cheapest future load window from cached half-hour rates.
- Tile showing current price and best window.
- SHORT_TEXT complication showing current price.
- Periodic background refresh with a network-connected WorkManager constraint.

Out of scope for this version: account API keys, usage history, export tariffs, Go, Intelligent Go, spend analysis and phone Data Layer sync.

## Project layout

- `app/src/main/java/com/nedrichards/agileprices/` - app, data, pricing logic, Tile, complication and worker code.
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

The cheapest-window calculation tries continuous starts from the next half-hour boundary at 30-minute cadence, then falls back to practical half-hour and whole-hour starts if needed. That keeps the watch answer aligned with Agile half-hour price periods.

## Build and test

Use a project-local Gradle cache if the normal home cache is not writable:

```sh
env GRADLE_USER_HOME="$PWD/.gradle-local" ./gradlew testDebugUnitTest assembleDebug
```

Generate Android project metadata:

```sh
android describe --project_dir="$PWD"
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release signing and versioning are documented in
`release-verification/signing-and-versioning.md`. Debug builds do not require a
release keystore.

## Manual verification

After installing on a Wear OS emulator or device:

1. Launch Agile Prices.
2. Choose a UK electricity region.
3. Confirm the app loads a current price and a best load window.
4. Change load duration and search horizon, then confirm the best window recomputes from cached rates.
5. Add the Tile and confirm it shows the current price plus best window.
6. Add the SHORT_TEXT complication and confirm it opens the app when tapped.
