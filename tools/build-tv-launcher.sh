#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-/usr/local/share/android-commandlinetools}"
PLATFORM="$ANDROID_HOME/platforms/android-36/android.jar"
TOOLS="$ANDROID_HOME/build-tools/36.0.0"
OUT="$ROOT/build/tv-launcher"
KEYSTORE="$ROOT/.keys/brave-tv-test.keystore"

rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/classes" "$OUT/dex"
rm -rf "$ROOT/launcher/res/mipmap-"*
cp -R "$ROOT/branding/android_tv_res/"mipmap-* "$ROOT/launcher/res/"

"$TOOLS/aapt2" compile --dir "$ROOT/launcher/res" -o "$OUT/compiled/resources.zip"
"$TOOLS/aapt2" link \
  -I "$PLATFORM" \
  --manifest "$ROOT/launcher/AndroidManifest.xml" \
  --min-sdk-version 29 --target-sdk-version 36 \
  -o "$OUT/launcher-unsigned.apk" \
  "$OUT/compiled/resources.zip"

javac --release 8 -classpath "$PLATFORM" -d "$OUT/classes" \
  "$ROOT/launcher/src/com/keen/tv/launcher/MainActivity.java"
"$TOOLS/d8" --min-api 29 --output "$OUT/dex" "$OUT/classes/com/keen/tv/launcher/MainActivity.class"
(cd "$OUT/dex" && zip -q -u "$OUT/launcher-unsigned.apk" classes.dex)

"$TOOLS/zipalign" -p -f 4 "$OUT/launcher-unsigned.apk" "$OUT/launcher-aligned.apk"
"$TOOLS/apksigner" sign \
  --ks "$KEYSTORE" --ks-pass pass:braveedit --key-pass pass:braveedit \
  --out "$ROOT/build/Keen-Launcher.apk" "$OUT/launcher-aligned.apk"
"$TOOLS/apksigner" verify --verbose "$ROOT/build/Keen-Launcher.apk"
rm -rf "$OUT"
rm -f "$ROOT/build/"*.idsig
echo "Built $ROOT/build/Keen-Launcher.apk"
