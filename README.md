# Keen Browser for Android TV

**Keen Browser. Browse Untamed.**

Keen is a custom port of Brave Browser made specifically for Android TV. 

Brave is a great privacy-focused browser, but it doesn't have an official app for Android TV. Keen fixes that by adding TV remote navigation and cleaning up the layout for the big screen, all while keeping Brave's ad-blocking and privacy features intact.

---

## Features

- **Virtual Mouse Cursor**: Move the pointer around using your D-pad remote.
  - Press the directional keys to move.
  - Press the OK/Center button to click.
  - Hold **Back** to switch between Cursor Mode and Scroll Mode.
  - The cursor hides itself after 6 seconds of no movement, or when the on-screen keyboard opens.
- **TV-Friendly Menu**: We cleaned up Brave's 3-dot menu. Since things like Leo AI, Brave Rewards, News, VPN, and the Wallet don't work or make sense on a TV remote, we completely removed them. This stops the TV remote focus from getting stuck on invisible buttons.
- **Onboarding Skip**: Skips the first-run setup loops (like welcome tours and default search engine prompts) and goes straight to the homepage.
- **Popup Blocker**: Blocks intrusive popups and new tabs from opening unexpectedly on your TV.
- **DOM Scrubber**: Injects basic script changes to clean up and scale target websites so they look better on a TV screen.
- **TV Launcher Icon**: Includes a small companion app so Keen shows up directly in your TV's home screen apps row.

---

## How It Works (Technical Approach)

### Why this isn't a source fork
Compiling Chromium and Brave from source is a massive headache. It requires a powerful machine, hundreds of gigabytes of disk space, and takes hours to build just one update. Keeping it updated with upstream security patches is also a lot of work for a solo developer.

Instead, Keen uses a **patching pipeline**:
1. We use `apktool` to decompile Brave's official APK into readable bytecode (`Smali`) and resources.
2. We inject custom Java classes (for the virtual cursor, popup blocker, etc.) directly into the decompiled code.
3. We override Brave's layout settings and visibility checks in smali to hide mobile-only features.
4. We swap in the new branding assets, pack it all back up, and sign the APK.

This makes the project lightweight, fast to build, and much easier to update when a new Brave version comes out.

---

## Setup & Building

### Prerequisites
- macOS or Linux
- JDK 8 or higher
- Android SDK (with `d8`, `apksigner`, `zipalign`, and platform jar 36)
- Python 3
- Node.js
- `apktool` installed on your PATH

### Build Instructions

1. Place the original Brave APK (named `BraveMonoarm.apk`) in the `original/` folder.
2. Run the build scripts:
   ```bash
   # Build the patched browser
   ./tools/build-patch.sh

   # Build the TV Launcher companion icon
   ./tools/build-tv-launcher.sh
   ```
3. Get your finished APKs from the `build/` folder:
   - `build/Keen.apk`
   - `build/Keen-Launcher.apk`

### Install to TV
Deploy them directly to your TV over ADB:
```bash
adb connect <your-tv-ip>
adb install -r build/Keen.apk
adb install -r build/Keen-Launcher.apk
```

---

## Author

Built by **SirPrizeNZ** (https://github.com/SirPrizeNZ).

*Disclaimer: This is an unofficial community port of Brave Browser. It is not affiliated with or endorsed by Brave Software Inc.*
