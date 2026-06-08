# Release Plan

This plan tracks the work needed to move Agile Prices from working alpha to a release candidate. Work through these one item at a time and mark each item done only after implementation and verification.

## Release blockers

- [x] Tile preview asset and manifest metadata.
- [x] Small round 1.2 inch / 192dp Wear OS emulator or device check, including screenshot.
- [x] Release-quality app icon and splash appearance.
- [x] Signed release artifact, preferably an Android App Bundle, with documented signing and versioning.
- [x] Settings path to change or reset the selected region after first setup.
- [x] Stale and error states that clearly distinguish current, cached and unavailable price data across app, Tile and complication.

## Functionality hardening

- [x] Octopus product discovery pagination and tariff rollover handling.
- [x] Better network recovery, including clearer cached-data fallback and last-updated messaging.
- [x] Tile and complication update requests after successful refresh.
- [x] Duration and horizon controls with disabled bounds and clearer tomorrow/date labelling.
- [x] Compact details/settings view for region, tariff code, last refresh, cache validity and attribution.

## Pixel Watch 2 performance

- [x] Avoid pre-setup WorkManager retry churn and stop replacing periodic refresh work on every app launch.
- [x] Reduce passive Tile and complication refresh cadence so surfaces do not wake every five minutes for half-hourly price data.
- [x] Enable release minification and resource shrinking, then compare APK/AAB size and smoke-test the release build.
- [x] Remove repeated sorting from best-window calculation and require sorted price-window input through the hot path.
- [x] Avoid duplicate DataStore collection and snapshot derivation in the foreground app.

## Test coverage

- [x] Repository tests with mocked Octopus JSON for product pagination, latest Agile selection, export exclusion, tariff mapping, rate pagination, HTTP errors and empty responses.
- [x] DataStore tests for defaults, persisted setup, cached prices and corrupt-cache recovery.
- [x] Extra price tests for BST/GMT boundary days, tomorrow windows, negative prices, half-hour gaps, and horizon plus duration edge cases.
- [x] Compose UI tests for setup, loaded state, no-network/error state, stale state and duration/horizon recomputation.
- [x] Tile and complication tests for loaded, no setup, stale, no data and negative price states.
- [x] Instrumented or screenshot tests on small and large Wear OS profiles, including larger system font.

## Polish and listing

- [x] Wear-native scroll indicators and verified swipe-to-dismiss/back behaviour.
- [x] Primary screen tuned so current price, best start time and average fit without scrolling on common watches.
- [x] Precise price labelling without cramped VAT wording.
- [ ] Play listing copy and screenshots that mention Tile and complication support.
