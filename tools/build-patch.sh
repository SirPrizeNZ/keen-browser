#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-/usr/local/share/android-commandlinetools}"
ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar"
D8="$ANDROID_HOME/build-tools/36.0.0/d8"
APKTOOL_TREE="$ROOT/analysis/apktool-smali"
BUILD="$ROOT/build"
KEYSTORE="$ROOT/.keys/brave-tv-test.keystore"
APKSIGNER="$ANDROID_HOME/build-tools/36.0.0/apksigner"
ZIPALIGN="$ANDROID_HOME/build-tools/36.0.0/zipalign"
APKEDITOR="$ROOT/tools/lib/APKEditor-1.4.9.jar"

# Check if original APKs exist, if not build can't proceed
if [[ ! -f "$ROOT/original/BraveMonoarm.apk" ]]; then
  echo "Error: Original 32-bit APK missing at original/BraveMonoarm.apk"
  exit 1
fi
if [[ ! -f "$ROOT/original/BraveMonoarm64.apk" ]]; then
  echo "Error: Original 64-bit APK missing at original/BraveMonoarm64.apk"
  exit 1
fi

# We build both 32-bit (armeabi-v7a) and 64-bit (arm64-v8a)
for ARCH in 32 64; do
  echo "========================================="
  echo "Building Keen for Arch: $ARCH"
  echo "========================================="

  if [[ "$ARCH" == "32" ]]; then
    APKTOOL_TREE="$ROOT/analysis/apktool-smali-32"
    ORIG_APK="$ROOT/original/BraveMonoarm.apk"
    OUT_NAME="Keen-32"
  else
    APKTOOL_TREE="$ROOT/analysis/apktool-smali-64"
    ORIG_APK="$ROOT/original/BraveMonoarm64.apk"
    OUT_NAME="Keen-64"
  fi

  if [[ ! -d "$APKTOOL_TREE" ]]; then
    echo "Decompiling base APK..."
    apktool d -f -r "$ORIG_APK" -o "$APKTOOL_TREE"
  fi

  # Apply smali patches using our robust python script
  python3 "$ROOT/tools/apply-smali-patches.py" "apktool-smali-$ARCH"

  rm -rf "$BUILD/classes" "$BUILD/dex" "$BUILD/keen-resources"
  mkdir -p "$BUILD/classes" "$BUILD/dex" "$APKTOOL_TREE/smali_classes3" "$(dirname "$KEYSTORE")"
  cp "$ROOT/branding/master/logo_clean_transparent_cropped.png" \
    "$APKTOOL_TREE/assets/keen_logo.png"

  javac --release 8 -classpath "$ANDROID_JAR" \
    -d "$BUILD/classes" \
    "$ROOT/src/com/brave/tv/"*.java

  "$D8" --min-api 29 --output "$BUILD/dex" "$BUILD/classes/com/brave/tv/"*.class
  
  SMALI_LIB="$ANDROID_HOME/cmdline-tools/latest/lib/external/com/android/tools/smali"
  GUAVA_LIB="$ANDROID_HOME/cmdline-tools/latest/lib/external/com/google/guava"
  JCOMMANDER="$ROOT/tools/lib/jcommander-1.69.jar"
  if [[ ! -f "$JCOMMANDER" ]]; then
    mkdir -p "$(dirname "$JCOMMANDER")"
    curl -L --fail -sS \
      -o "$JCOMMANDER" \
      "https://repo1.maven.org/maven2/com/beust/jcommander/1.69/jcommander-1.69.jar"
  fi
  BAKSMALI_CP="$JCOMMANDER:$SMALI_LIB/smali-baksmali/3.0.9/smali-baksmali-3.0.9.jar:$SMALI_LIB/smali-util/3.0.9/smali-util-3.0.9.jar:$SMALI_LIB/smali-dexlib2/3.0.9/smali-dexlib2-3.0.9.jar:$GUAVA_LIB/guava/33.3.1-jre/guava-33.3.1-jre.jar:$GUAVA_LIB/failureaccess/1.0.2/failureaccess-1.0.2.jar"
  java -cp "$BAKSMALI_CP" com.android.tools.smali.baksmali.Main \
    d "$BUILD/dex/classes.dex" -o "$APKTOOL_TREE/smali_classes3"

  apktool b "$APKTOOL_TREE" -o "$BUILD/$OUT_NAME-base-unsigned.apk"

  rm -rf "$BUILD/keen-resources"
  java -Xmx4g -jar "$APKEDITOR" d -f -dex -t xml \
    -i "$BUILD/$OUT_NAME-base-unsigned.apk" -o "$BUILD/keen-resources"
  node "$ROOT/tools/brand-apk.mjs" "$BUILD/keen-resources" "$ROOT/branding"
  java -Xmx4g -jar "$APKEDITOR" b -f \
    -i "$BUILD/keen-resources" -o "$BUILD/$OUT_NAME-unsigned.apk"

  "$ZIPALIGN" -p -f 4 \
    "$BUILD/$OUT_NAME-unsigned.apk" \
    "$BUILD/$OUT_NAME-aligned.apk"

  if [[ ! -f "$KEYSTORE" ]]; then
    keytool -genkeypair -noprompt \
      -keystore "$KEYSTORE" -storepass braveedit -keypass braveedit \
      -alias brave-tv-test -keyalg RSA -keysize 2048 -validity 3650 \
      -dname "CN=BraveEdit Android TV Test, O=Local Development, C=NZ"
  fi

  "$APKSIGNER" sign \
    --ks "$KEYSTORE" --ks-pass pass:braveedit --key-pass pass:braveedit \
    --out "$BUILD/$OUT_NAME.apk" \
    "$BUILD/$OUT_NAME-aligned.apk"

  # Run verification
  python3 "$ROOT/tools/verify-build.py" "$BUILD/$OUT_NAME.apk" "$ARCH"

  "$ZIPALIGN" -c -p 4 "$BUILD/$OUT_NAME.apk"

  mkdir -p "$BUILD/archive"
  TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
  cp "$BUILD/$OUT_NAME.apk" "$BUILD/archive/${OUT_NAME}_$TIMESTAMP.apk"
  echo "Archived: build/archive/${OUT_NAME}_$TIMESTAMP.apk"

  rm -rf "$BUILD/classes" "$BUILD/dex" "$BUILD/keen-resources"
  rm -f "$BUILD/$OUT_NAME-base-unsigned.apk" "$BUILD/$OUT_NAME-unsigned.apk" \
    "$BUILD/$OUT_NAME-aligned.apk" "$BUILD/"*.idsig
  echo "Built $BUILD/$OUT_NAME.apk successfully"
done

