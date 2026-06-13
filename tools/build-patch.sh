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

if [[ ! -f "$APKTOOL_TREE/AndroidManifest.xml" ]]; then
  apktool d -f -r "$ROOT/original/BraveMonoarm.apk" -o "$APKTOOL_TREE"
fi

# Apply smali patches using our robust python script
python3 "$ROOT/tools/apply-smali-patches.py"


rm -rf "$BUILD"
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

apktool b "$APKTOOL_TREE" -o "$BUILD/Keen-base-unsigned.apk"

rm -rf "$BUILD/keen-resources"
java -Xmx4g -jar "$APKEDITOR" d -f -dex -t xml \
  -i "$BUILD/Keen-base-unsigned.apk" -o "$BUILD/keen-resources"
node "$ROOT/tools/brand-apk.mjs" "$BUILD/keen-resources" "$ROOT/branding"
java -Xmx4g -jar "$APKEDITOR" b -f \
  -i "$BUILD/keen-resources" -o "$BUILD/Keen-unsigned.apk"

"$ZIPALIGN" -p -f 4 \
  "$BUILD/Keen-unsigned.apk" \
  "$BUILD/Keen-aligned.apk"

if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair -noprompt \
    -keystore "$KEYSTORE" -storepass braveedit -keypass braveedit \
    -alias brave-tv-test -keyalg RSA -keysize 2048 -validity 3650 \
    -dname "CN=BraveEdit Android TV Test, O=Local Development, C=NZ"
fi

"$APKSIGNER" sign \
  --ks "$KEYSTORE" --ks-pass pass:braveedit --key-pass pass:braveedit \
  --out "$BUILD/Keen.apk" \
  "$BUILD/Keen-aligned.apk"

"$APKSIGNER" verify --verbose "$BUILD/Keen.apk"
"$ZIPALIGN" -c -p 4 "$BUILD/Keen.apk"

mkdir -p "$BUILD/archive"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
cp "$BUILD/Keen.apk" "$BUILD/archive/Keen_$TIMESTAMP.apk"
echo "Archived: build/archive/Keen_$TIMESTAMP.apk"

rm -rf "$BUILD/classes" "$BUILD/dex" "$BUILD/keen-resources"
rm -f "$BUILD/Keen-base-unsigned.apk" "$BUILD/Keen-unsigned.apk" \
  "$BUILD/Keen-aligned.apk" "$BUILD/"*.idsig
echo "Built $BUILD/Keen.apk"
