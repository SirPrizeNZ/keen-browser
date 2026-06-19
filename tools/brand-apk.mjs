import fs from "node:fs";
import path from "node:path";

const [decodedRoot, brandingRoot] = process.argv.slice(2);
if (!decodedRoot || !brandingRoot) {
  throw new Error("Usage: node brand-apk.mjs <decoded-root> <branding-root>");
}

const resourceRoot = path.join(decodedRoot, "resources", "package_1", "res");

for (const dirent of fs.readdirSync(resourceRoot, { withFileTypes: true })) {
  if (!dirent.isDirectory() || !dirent.name.startsWith("values")) continue;
  const stringsPath = path.join(resourceRoot, dirent.name, "strings.xml");
  if (!fs.existsSync(stringsPath)) continue;
  const source = fs.readFileSync(stringsPath, "utf8");
  let branded = source.replace(/>([^<]*)</g, (match, text) =>
    `>${text.replaceAll("Brave", "Keen").replaceAll("BRAVE", "KEEN")}<`);
  branded = branded.replace(
    /(<string name="app_name">)[^<]*(<\/string>)/,
    "$1Keen$2",
  );
  branded = branded.replace(
    /(<string name="omnibox_empty_hint_with_dse_name">)[^<]*(<\/string>)/,
    "$1Search or type URL$2",
  );
  fs.writeFileSync(stringsPath, branded);
}

const toolbarPath = path.join(resourceRoot, "layout", "brave_toolbar.xml");
let toolbar = fs.readFileSync(toolbarPath, "utf8");

function frameBlockContaining(source, id) {
  const idIndex = source.indexOf(`android:id="@id/${id}"`);
  if (idIndex < 0) throw new Error(`Missing toolbar view: ${id}`);
  const start = source.lastIndexOf("<FrameLayout", idIndex);
  const end = source.indexOf("</FrameLayout>", idIndex) + "</FrameLayout>".length;
  return { start, end, text: source.slice(start, end) };
}

const shieldsBlock = frameBlockContaining(toolbar, "brave_shields_button_layout");
const rewardsBlock = frameBlockContaining(toolbar, "brave_rewards_button_layout");
let disabledRewards = rewardsBlock.text
  .replace(/android:visibility="[^"]+"/, 'android:visibility="gone"')
  .replace(
    'android:background="@drawable/modern_toolbar_background_grey_end_segment"',
    'android:background="@drawable/modern_toolbar_background_grey_middle_segment"',
  )
  .replace('android:layout_width="45.0dp"',
    'android:layout_width="53.0dp"\n               android:layout_marginStart="-8.0dp"')
  .replace(
    /(android:id="@id\/brave_rewards_button"[\s\S]*?)(\s+style="@style\/ToolbarButton" \/>)/,
    '$1\n                 android:enabled="false"\n                 android:clickable="false"\n                 android:focusable="false"$2',
  );
if (shieldsBlock.start < rewardsBlock.start) {
  toolbar = toolbar.slice(0, shieldsBlock.start)
    + disabledRewards
    + toolbar.slice(shieldsBlock.end, rewardsBlock.start)
    + shieldsBlock.text
    + toolbar.slice(rewardsBlock.end);
}
fs.writeFileSync(toolbarPath, toolbar);

const shieldDensities = [
  "drawable",
  "drawable-hdpi",
  "drawable-xhdpi",
  "drawable-xxhdpi",
];
for (const drawableDir of shieldDensities) {
  const targetDir = path.join(resourceRoot, drawableDir);
  for (const resourceName of ["btn_brave", "btn_brave_off"]) {
    fs.rmSync(path.join(targetDir, `${resourceName}.webp`), { force: true });
    fs.copyFileSync(
      path.join(brandingRoot, "toolbar", drawableDir, "btn_keen.png"),
      path.join(targetDir, `${resourceName}.png`),
    );
  }

  fs.rmSync(path.join(targetDir, "btn_bat.webp"), { force: true });
  fs.writeFileSync(
    path.join(targetDir, "btn_bat.png"),
    Buffer.from(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+v5vF4QAAAABJRU5ErkJggg==",
      "base64",
    ),
  );
}

for (const density of ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]) {
  const targetDir = path.join(resourceRoot, `mipmap-${density}`);
  const oldIcon = path.join(targetDir, "layered_app_icon.webp");
  const newIcon = path.join(targetDir, "layered_app_icon.png");
  if (fs.existsSync(oldIcon)) fs.rmSync(oldIcon);
  fs.copyFileSync(
    path.join(brandingRoot, "android_tv_res", `mipmap-${density}`, "ic_launcher.png"),
    newIcon,
  );
  fs.copyFileSync(
    path.join(brandingRoot, "android_tv_res", `mipmap-${density}`, "banner.png"),
    path.join(targetDir, "banner.png"),
  );
}

// 6. Patch AndroidManifest.xml to register as a TV app natively
// Disabling direct TV app registration for com.brave.browser so only the helper launcher package shows on Leanback/Google TV Home launcher.
/*
const manifestPath = path.join(decodedRoot, "AndroidManifest.xml");
if (fs.existsSync(manifestPath)) {
  let manifest = fs.readFileSync(manifestPath, "utf8");
  
  // Add TV banner to application tag
  if (!manifest.includes("android:banner=")) {
    manifest = manifest.replace("<application ", '<application android:banner="@mipmap/banner" ');
  }
  
  // Add LEANBACK_LAUNCHER intent filter category to main activity launcher
  const launcherIntent = '<category android:name="android.intent.category.LAUNCHER"/>';
  const launcherIntentSpaced = '<category android:name="android.intent.category.LAUNCHER" />';
  const leanbackIntent = '\n                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />';
  
  if (manifest.includes(launcherIntent) && !manifest.includes("LEANBACK_LAUNCHER")) {
    manifest = manifest.replace(launcherIntent, launcherIntent + leanbackIntent);
  } else if (manifest.includes(launcherIntentSpaced) && !manifest.includes("LEANBACK_LAUNCHER")) {
    manifest = manifest.replace(launcherIntentSpaced, launcherIntentSpaced + leanbackIntent);
  }
  
  fs.writeFileSync(manifestPath, manifest);
}
*/

// 7. Register the new banner resource in public.xml
const publicXmlPath = path.join(decodedRoot, "resources", "package_1", "res", "values", "public.xml");
if (fs.existsSync(publicXmlPath)) {
  let publicXml = fs.readFileSync(publicXmlPath, "utf8");
  if (!publicXml.includes('name="banner"')) {
    const replacement = '  <public id="0x7f110005" type="mipmap" name="banner" />\n</resources>';
    publicXml = publicXml.replace("</resources>", replacement);
    fs.writeFileSync(publicXmlPath, publicXml);
  }
}


