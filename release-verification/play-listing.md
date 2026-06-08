# Play Listing

## App Name

Agile Prices

## Short Description

Octopus Agile prices, cheapest load windows, Tile and complication for Wear OS.

## Full Description

Agile Prices is a standalone Wear OS app for Octopus Agile electricity prices.
It shows the current p/kWh price, the cheapest upcoming load window and the next
half-hour prices directly on your watch.

Choose your UK electricity region once, then let the app cache upcoming Agile
rates for quick checks throughout the day. You can adjust the load duration and
search horizon on the watch to find a practical start time for appliances,
charging and other flexible electricity use.

The app also includes a Wear OS Tile for glanceable current price and best-window
status, plus a SHORT_TEXT complication for showing the current Agile price on a
watch face.

Agile Prices is independent and is not affiliated with, endorsed by or sponsored
by Octopus Energy.

## Screenshot Set

Capture fresh Wear OS screenshots for the Play store listing after the UI copy
has settled. Keep generated screenshot dumps out of version control unless the
images have been reviewed as current public assets. Recommended order and
captions:

1. Current Agile price and cheapest load window on your watch.
2. Tune load duration and search horizon from Wear OS.
3. Check the next half-hour prices before starting a load.
4. Region, tariff and cache details stay available in the app.
5. Tile screenshot - Add the Agile Prices Tile for current price and best-window glances.
6. Complication screenshot - Add the SHORT_TEXT complication to show the current Agile price on a watch face.

Tile metadata is already present in the app manifest and uses
`app/src/main/res/drawable/tile_preview.xml` as the Wear Tile preview. The
complication service is declared as a SHORT_TEXT provider with the label
`Agile current price`.

The final Tile and complication screenshots should be captured from a configured
watch or emulator before Play submission, because they depend on the watch-face
and Tile carousel environment rather than the app activity alone.

## What's New

Initial Wear OS release with current Octopus Agile price, cheapest load-window
search, cached rates, Tile support and SHORT_TEXT complication support.
