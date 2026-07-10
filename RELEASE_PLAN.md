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

## Phone/adaptive support

- [x] Make the watch hardware feature optional so the same app package can install on phones while keeping Tile and complication services declared for watches.
- [x] Route watch devices to the Wear surface and route non-watch devices to a Material 3 phone/adaptive surface without duplicating pricing or repository logic.
- [x] Port setup, current price, remembered load duration, search horizon, best window, timing relationship, 24h graph, next slots, stale/error states and manual refresh to the phone surface.
- [x] Preserve the existing 30-minute unique WorkManager refresh with network constraint; do not add polling, foreground services, notifications or widgets for the initial phone port.
- [x] Add compact phone and adaptive width Compose tests for setup, loaded, stale, error, refresh, duration, horizon and region-change flows.
- [ ] Install and smoke-test on a Pixel 9 or Pixel 9 emulator profile, including larger font scale.
- [ ] Re-run Wear emulator/watch smoke tests to confirm Tile, complication and watch UI behavior after the universal manifest change.
- [x] Android home-screen widget using cached data and existing successful-refresh updates.
- [ ] Optional follow-up: notification for unusually cheap or negative-price windows.
- [ ] Optional follow-up: watch/phone Data Layer sync.
- [ ] Optional follow-up: landscape/tablet polish after the first Pixel 9 validation pass.
- [ ] Optional follow-up: revisit whole-hour hint pricing risk after real-world use; see `docs/whole-hour-hints-follow-up.md`.

These remain deliberately deferred while a Play release is not being prepared.
The Pixel 9 and Wear smoke tests are release-validation work, while a widget,
notifications and Data Layer sync each need separate product, lifecycle and
cross-device decisions rather than being folded into layout polish.

## Polish and listing

- [x] Wear-native scroll indicators and verified swipe-to-dismiss/back behaviour.
- [x] Primary screen tuned so current price, best start time and average fit without scrolling on common watches.
- [x] Precise price labelling without cramped VAT wording.
- [ ] Play listing copy and screenshots that mention Tile and complication support.
