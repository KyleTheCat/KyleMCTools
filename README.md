<p align="center">
  <img src="docs/icon.svg" width="112" alt="KyleMCTools icon">
</p>

<h1 align="center">KyleMCTools</h1>
<p align="center"><b>All-in-one toolkit for Minecraft server owners.</b><br>Native Android app by KyleTheCat.</p>

<p align="center">
  <a href="../../actions/workflows/build.yml"><img src="../../actions/workflows/build.yml/badge.svg" alt="Build"></a>
  <a href="../../releases/latest"><img src="https://img.shields.io/github/v/release/KyleTheCat/KyleMCTools?label=latest" alt="Latest release"></a>
</p>

> The badge links assume the repo is `KyleTheCat/KyleMCTools`. If yours is different, edit the badge URL above.

## What it is

Server owners often have a resource pack and a pile of config work around it. KyleMCTools does that work on your phone: pick a zip, choose options, and get ready-to-use files in a folder you choose. It never edits your original files; every run works on a copy and writes to a new timestamped folder.

## Tools

| # | Tool | What it does | Status |
|---|------|--------------|--------|
| 1 | Java to Bedrock | Converts a Java resource pack to a Bedrock pack and writes the Geyser mappings, a `config.yml` snippet and install notes | Ready |
| 2 | Pack to plugin config | Generates Nexo, ItemsAdder or Oraxen item config plus server-ready files from a pack | Ready |
| 3 | Pack Optimizer | Shrinks Java and Bedrock packs: lossless PNG, JSON minify, junk removal, before/after sizes | Ready |
| 4 | Pack Protector | Makes casual copying harder: minify, decoys, optional renaming. Honest about the limits | Ready |
| 5 | MEG Converter | ModelEngine `.bbmodel` to Bedrock geometry, animations and texture for GeyserModelEngine | Ready |
| 6 | Version Converter | Moves a Java pack between Minecraft 1.13 and 26.2 (pack format, item overrides/definitions, armor folders) | Ready |
| 7 | DeluxeMenus Editor | Visual chest-grid editor for DeluxeMenus menus | Ready |
| 8 | ShopGUI Editor | Editor for EconomyShopGUI shops, prices and sections | Ready |
| 9 | Rank Prefix Editor | Rank prefixes with image previews | Phase 5 |

Targets: Paper, Spigot, Purpur, Folia, with Velocity and BungeeCord setups, on a wide range of Minecraft versions (1.16 up to the newest).

## How the app works

1. **Pick a tool** on the Tools tab (swipe, drag, arrow keys or tap).
2. **Drop or choose a zip.** The app copies it into private cache, so your original is untouched.
3. **Run.** Progress and a live log show what is happening. Bad or corrupted files give a friendly message instead of a crash.
4. **Get results** in `KyleMCTools_Output/<ToolName>/<project>_<timestamp>/` inside the folder you picked on first run.
5. **Activity** keeps your last 50 runs with their logs. **About** shows the author, repo link and changelog, and tells you when a newer release is out.

## Install

1. Open the [latest release](../../releases/latest) and download the `.apk`.
2. Open it on your phone and allow installs from your browser or Files app.
3. New versions install over the old one (all builds use the same signing key).

## Build it yourself

Everything builds on GitHub Actions, no Android Studio needed:

1. Fork or upload this repo to GitHub.
2. **Actions > Build APK > Run workflow**, then download the `KyleMCTools-debug-apk` artifact.
3. To publish a release the app can detect: add a section for the version to `CHANGELOG.md`, then push a tag:
   ```
   git tag v0.2.0
   git push origin v0.2.0
   ```
   The **Release APK** workflow builds, tests and attaches the APK. The app's About page compares that tag with the installed version.

Locally: JDK 17, Gradle 8.9, Android SDK 35.

## Project layout

```
app/                      shell: floating nav, Tools / Activity / About, settings
core/                     pure Kotlin: zip reading, pack scanner, JSON/YAML, versions, activity log model
ui/                       theme, Minecraft font, shared components, task runner, output exporter
tools/geyser-converter    Tool 1
tools/plugin-config       Tool 2
tools/pack-optimizer      Tool 3
tools/pack-protector      Tool 4
tools/meg-converter       Tool 5
tools/version-converter   Tool 6
tools/deluxemenus-editor  Tool 7
tools/shop-editor         Tool 8
testdata/                 sample packs (python3 testdata/make_samples.py regenerates them)
docs/                     icon and assets
```

## Testing

`gradle :core:test testDebugUnitTest` runs the unit tests (zip handling, scanner, converters, PNG optimizer, protector, YAML, versions). The `testdata/` packs cover legacy and 1.21.4+ packs, a wrapped zip with a broken JSON, a corrupt zip and a non-pack zip.

## Notes

- The signing key in `app/kmt.keystore` is committed on purpose so updates install cleanly. It is meant for sideloaded builds, not Play Store publishing.
- The Minecraft font is a fan-made font; check its license before publishing the app on a store.
- Not affiliated with Mojang, Microsoft or the plugins mentioned.

## Credits

Made by [KyleTheCat](https://github.com/KyleTheCat).
