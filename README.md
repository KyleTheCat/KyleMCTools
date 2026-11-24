# KyleMCTools (by KyleTheCat)

All-in-one toolkit for Minecraft server owners. Native Android app: Kotlin + Jetpack Compose.

## Build the APK with GitHub Actions
1. Create a new GitHub repository and upload this whole folder (keep `.github/workflows/build.yml`).
2. Open the **Actions** tab, then **Build APK**, then **Run workflow** (it also runs on every push).
3. When it finishes, download the **KyleMCTools-debug-apk** artifact, unzip it, and install `app-debug.apk` on your phone (allow installs from your browser or Files app).
4. The **test-reports** artifact has the unit-test results. If the build fails, open the failed step and send me the log.

No Gradle wrapper is included on purpose: the workflow installs Gradle 8.9 itself. (Android Studio users: open the folder and let it sync.)

## Layout
```
app/                      shell: swipe-card home, settings, navigation
core/                     pure Kotlin: zip reading, pack scanner, JSON/YAML helpers, reporter
ui/                       theme + Minecraft font, shared components, task runner, output exporter
tools/geyser-converter    Tool 1: Java to Bedrock + Geyser files
tools/plugin-config       Tool 2: pack to Nexo / ItemsAdder / Oraxen config
testdata/                 sample packs (make_samples.py regenerates them)
```

## Output
Results go to `KyleMCTools_Output/<ToolName>/<project>_<timestamp>/` inside the folder you pick on first run (Settings can change it).
Originals are never modified: the input is copied to app cache first, and every run creates a new timestamped folder.

## Font
`ui/src/main/res/font/minecraft.ttf` is used by default. Settings has a Minecraft / Clean switch.
The font has no arrow, ellipsis or check glyphs, so the UI avoids them.

## Phase 1 status
Tools 1 and 2 are built. Tools 3 to 9 show a "coming in phase N" card.
