# Android TV logo clean pack

## What was done
- Removed remaining colour artefacts/speckles from the red/orange gradient.
- Preserved the logo shape, internal white marks, and transparent exterior.
- Cropped the master PNG to the exact non-transparent logo bounding box: 683 × 784 px.
- Exported Android TV launcher icon and banner density assets.

## Master files
- `master/logo_clean_transparent_cropped.png` — exact transparent bounding-box PNG, no outside padding.
- `master/logo_clean_transparent_2048_canvas.png` — same cleaned logo on original transparent 2048 × 2048 canvas.
- `master/logo_clean_transparent_print_4096h.png` — high-resolution transparent PNG for print/design handoff.

## Android TV resource files
Launcher icon, 1:1:
- mdpi: 80 × 80
- hdpi: 120 × 120
- xhdpi: 160 × 160
- xxhdpi: 240 × 240
- xxxhdpi: 320 × 320

Banner, 16:9:
- mdpi: 160 × 90
- hdpi: 240 × 135
- xhdpi: 320 × 180
- xxhdpi: 480 × 270
- xxxhdpi: 640 × 360

## Suggested Android manifest snippet
```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:banner="@mipmap/banner">
</application>
```

## Note
The banner files are logo-only because no app wordmark/name was supplied. Android TV guidelines recommend including text in the banner if the banner is used as the app launcher tile.
