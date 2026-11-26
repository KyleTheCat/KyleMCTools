# Changelog

Newest first. Each section starts with `## <version>`; the release workflow copies the matching section into the GitHub release notes, and the app shows it in About.

## 0.4.0 - 2026-11-27
- DeluxeMenus Editor: open or create a menu, edit title, open command and size, tap slots on a chest grid to edit items (material, name, lore, amount, click commands), warnings for bad slots and missing [tags], saves the .yml plus the config.yml entry. Settings the editor doesn't know are kept.
- ShopGUI Editor (EconomyShopGUI v6 / Premium v5 layout): edit pages, items, buy/sell prices, names and lore; change all prices by a factor; edit the section (main menu button) file; saves shops/ and sections/ with matching names. Premium keys are kept.
- A YAML reader for plugin configs in core, with friendly errors.

## 0.3.0 - 2026-11-26
- MEG Converter: Blockbench .bbmodel files (single file or a zip) to Bedrock geometry, animations and texture, laid out as input/<modelId>/ for the GeyserModelEngine extension, plus a zip and install notes.
- Version Converter: moves a Java pack between Minecraft 1.13 and 26.2. Rewrites pack.mcmeta (pack_format, or min_format/max_format from format 65), converts model overrides to 1.21.4 item definitions and back, moves armor textures for 1.21.2, and lists everything else that needs a manual check.

## 0.2.0 - 2026-11-25
- Pack Optimizer: lossless PNG compression (palette conversion, metadata removal, best filters), JSON minify, junk-file removal, optional removal of unreferenced textures, before/after size and a report.
- Pack Protector: JSON minify, decoy files, optional asset renaming (Java), self-check that the result still loads, and an honest notes file about what protection can and can't do.
- Works on Java resource packs and Bedrock texture packs (.zip, .mcpack).

## 0.1.1 - 2026-11-25
- New look: near-black theme, bordered cards, status pills.
- Floating bottom navigation: Tools, Activity, About.
- Activity page: your last 50 runs with result, log, open folder and remove.
- About page: your GitHub profile picture, profile and repo links, changelog, and an update notice when a newer release is published.
- New app icon.
- Builds are now signed with one fixed key so new versions install over old ones.

## 0.1.0 - 2026-11-24
- Java to Bedrock converter with Geyser mappings.
- Pack to Nexo / ItemsAdder / Oraxen config generator.
- Swipe-card home, Minecraft font option, light and dark mode.
