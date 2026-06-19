<p align="center">
  <img src="branding/master/logo_clean_transparent_cropped.png" alt="Keen Logo" width="180">
</p>

<p align="center">
  <b>Keen Browser. Browse Untamed.</b>
</p>

Keen is Brave Browser rebuilt for Android TV.

Brave is a great privacy-focused browser, but it doesn't have an official app for Android TV. Keen fixes that by adding TV remote navigation and cleaning up the layout for the big screen, all while keeping Brave's ad-blocking and privacy features intact.

---

## Features

- D-pad pointer with smooth movement, matched edge scrolling, and keyboard-aware input.
- Content-host lockdown blocks hijacked popups and unrelated full-page redirects.
- Remote-native Android dialogs and controls.
- Brave Shields and native ad blocking remain intact.

---

## Download

[Download Keen.apk (v1.2.1-alpha - 2026-06-19)](https://github.com/SirPrizeNZ/keen-browser/releases/download/v1.2.1-alpha/Keen.apk)

---

## Compatibility & Specs

- **Status**: Alpha / Experimental (Concept Build)
- **Tested on**: Xiaomi TV Box S (2nd Gen) - Android 14
- **Base APK**: Brave Stable `v1.91.172` (`com.brave.browser` - Monoarm armeabi-v7a)
  - *SHA-256*: `39c8fd9eb90a1b8e10a030ed9c682f6637bb71ea45bb2c11dd3e6e47055fab39`
- **Keen APK**: 
  - *SHA-256*: `b7c257c9f9ec4a8da883f1f1887831014cb00425354e41e48aa310f64ab2cfa1`

---

## Technical Overview

### What Changed
- **D-pad cursor**: Native pointer simulation mapped directly to your D-pad remote.
- **TV launcher banner**: Natively integrated Leanback launcher category and custom TV banner resources.
- **UI cleanup**: Stripped out mobile-only menu items (Brave Rewards, News, VPN, Wallet, Leo AI) to prevent D-pad remote focus hangs.
- **Navigation lockdown**: Content sites stay on their own host. Cross-site popup hijacks are blocked at Chromium's native navigation hooks.

### What Did Not Change
- Core Chromium rendering engine.
- Brave Shields and native adblock lists.

### Key Permissions
- `INTERNET` (Web access)
- `ACCESS_NETWORK_STATE` & `ACCESS_WIFI_STATE` (Connection checks)
- `READ_EXTERNAL_STORAGE` & `WRITE_EXTERNAL_STORAGE` (Downloads support)
- `RECORD_AUDIO` & `CAMERA` (Web audio/video features)

### Known Issues
- **Manual updates**: Since this is a custom patched build, it won't auto-update from the Play Store.
- **Custom signing**: Will trigger an Android security warning on installation.
- **Limited device testing**: Only verified on Xiaomi TV Box S (2nd Gen) running Android 14.

---

## How to Install (Android TV)

1. Download the Keen.apk onto your phone or computer.
2. Send the APK file to your TV using a USB drive or an app like Send Files to TV.
3. On your TV, open a File Manager app, find Keen.apk, and install it. You may need to allow "Install from Unknown Sources" in your TV's settings.

---

## Security & Privacy Note

- **Untouched Core**: All patches are strictly for UI layout, D-pad controls, and TV optimization. Brave's core Chromium engine, sandboxing, and native ad-blocking (Brave Shields) are completely untouched.
- Because this is a modified build, it is signed with a custom certificate instead of Brave's official signature. This means it will not auto-update via the Google Play Store; updates must be installed manually.

---

## How It Works (Technical Approach)

### Why this isn't a source fork
Compiling Chromium and Brave from source is a massive headache. It requires a powerful machine, hundreds of gigabytes of disk space, and takes hours to build just one update. Keeping it updated with upstream security patches is also a lot of work for a solo developer.

Instead, Keen uses a **patching pipeline**:
1. We decompile Brave's official APK using `apktool`.
2. We inject custom Java classes (for the virtual cursor, popup blocker, etc.) directly into the decompiled code.
3. We override Brave's layout settings and visibility checks in smali to hide mobile-only features (VPN, Wallet, Rewards, News, and Leo AI).
4. We swap in the new branding assets, register `@mipmap/banner` in `public.xml`, and add the `LEANBACK_LAUNCHER` intent category directly in the `AndroidManifest.xml`.
5. We pack it all back up and sign the APK.

---

## Building from Source

### Prerequisites
- macOS or Linux
- JDK 8 or higher
- Android SDK (with `d8`, `apksigner`, `zipalign`, and platform jar 36)
- Python 3
- Node.js
- `apktool` installed on your PATH

### Build Instructions

1. Place the original Brave APK (named `BraveMonoarm.apk`) in the `original/` folder.
2. Run the build script:
   ```bash
   ./tools/build-patch.sh
   ```
3. Get your finished APK from the `build/` folder:
   - `build/Keen.apk`

---

## Author

Built by **SirPrizeNZ** (https://github.com/SirPrizeNZ).

*Disclaimer: This is an unofficial community port of Brave Browser. It is not affiliated with or endorsed by Brave Software Inc. Brave, if you are reading this, please release an official Android TV compatible version.*
