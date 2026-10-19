# Launcher artwork

`launcher.svg` is the editable master for phone and Wear OS. Keep the lime bolt,
open clock ring and cream hands together. The lower bolt tip now ends at the
ring's visual boundary, and the top and bottom openings leave room around it.
Do not centre only the bolt's bounding box or reintroduce the long lower tail.

Regenerate from the repository root with Python 3 and ImageMagick 7 (librsvg):

```sh
python3 scripts/generate-launcher-icons.py
```

The script produces:

- Android vector background, foreground and monochrome layers on a 108dp canvas.
  The entire mark fits inside the central 66dp diameter safe circle.
- Shared adaptive launcher definitions use those vectors on every supported
  Android version (the app's minimum is API 30). Launchers choose the mask;
  Android 13+ launchers can use the monochrome layer for themed icons.
- Lossless WebP fallback icons at 48, 72, 96, 144 and 192 pixels, in rounded-square
  and circular versions. These use the central 72dp viewport, matching the
  adaptive icon's default visible region.
- A full-bleed, opaque 1024px `drawable-nodpi/agile_launcher_icon.png` for
  high-resolution artwork. Android launchers use the vectors, so larger or
  denser displays do not rely on enlarging a bitmap. No device-size heuristic
  is needed.
- `preview.svg` and `preview.png`, showing example launcher masks and themed
  artwork at large and small sizes. The squircle-like preview is illustrative;
  each launcher supplies its own actual mask and theme colours.

The Android vectors preserve the SVG gradients and rounded stroke caps. The
monochrome layer preserves the complete silhouette and uses no coloured
background. Phone and Wear manifests both inherit the same launcher resources.

Review `preview.png` after editing. Build both flavours to verify Android's
resource compiler accepts the generated vectors. A rendered contact sheet is
design evidence, not proof of behaviour on a connected device.

Adaptive icon specification:
https://developer.android.com/develop/ui/compose/system/icon_design_adaptive
