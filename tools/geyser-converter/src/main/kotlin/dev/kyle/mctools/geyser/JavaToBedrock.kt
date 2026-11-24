package dev.kyle.mctools.geyser

import dev.kyle.mctools.core.*
import kotlinx.serialization.json.*
import java.io.BufferedOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class GeyserOptions(
    val projectName: String,
    /** Java item that carries item_model items (1.21.4+). */
    val baseMaterial: String = "minecraft:paper",
    val includeSounds: Boolean = true,
)

object JavaToBedrock {
    private class Mapped(val item: PackItem, val bedrockId: String, val texturePath: String)

    fun convert(input: File, outDir: File, opts: GeyserOptions, rep: Reporter): ToolResult {
        outDir.mkdirs()
        val slug = Ids.slug(opts.projectName)
        PackArchive(input).use { pack ->
            rep.progress(0.05f, "Reading pack...")
            val scan = PackScanner(pack, rep).scan()
            rep.info("Pack format: ${scan.packFormat ?: "unknown"}")
            if (scan.items.isEmpty()) {
                throw ToolException(
                    "No custom items found. Geyser needs items that use custom_model_data overrides " +
                        "(assets/minecraft/models/item/*.json) or item definitions (assets/<namespace>/items/*.json).",
                )
            }
            rep.ok("Found ${scan.items.size} custom items")

            rep.progress(0.25f, "Checking textures...")
            val base = Ids.material(opts.baseMaterial)
            val mapped = ArrayList<Mapped>()
            var skipped = 0
            var flat3d = 0
            for (item in scan.items) {
                val tex = item.iconTexture
                val path = tex?.let { "assets/${it.substringBefore(':')}/textures/${it.substringAfter(':')}.png" }
                if (path == null || !pack.exists(path)) {
                    rep.warn("Skipped ${item.id}: its texture was not found in the pack")
                    skipped++
                    continue
                }
                if (item.is3d) { rep.warn("${item.id} uses a 3D model; mapped with a flat icon for now"); flat3d++ }
                val ns = item.namespace.let { if (it == "minecraft") "geyser_custom" else Ids.slug(it) }
                mapped += Mapped(item, "$ns:${item.id}", path)
            }
            if (mapped.isEmpty()) throw ToolException("None of the custom items had a usable texture, so there is nothing to convert.")

            rep.progress(0.4f, "Building Bedrock pack...")
            val mcpack = File(outDir, "${slug}_bedrock.mcpack")
            var soundCount = 0
            ZipOutputStream(BufferedOutputStream(mcpack.outputStream())).use { z ->
                fun put(name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
                put("manifest.json", manifest(opts.projectName, slug).toByteArray())
                pack.readBytes("pack.png")?.let { put("pack_icon.png", it) }
                for ((i, m) in mapped.withIndex()) {
                    put("textures/items/${m.item.id}.png", pack.readBytes(m.texturePath)!!)
                    rep.progress(0.4f + 0.3f * (i + 1) / mapped.size, "Copying textures ${i + 1}/${mapped.size}")
                }
                put("textures/item_texture.json", itemTextureJson(slug, mapped).toByteArray())
                if (opts.includeSounds) {
                    rep.progress(0.75f, "Converting sounds...")
                    soundCount = addSounds(pack, z, rep)
                }
            }
            rep.ok("Wrote ${mcpack.name}")

            rep.progress(0.85f, "Writing Geyser files...")
            File(outDir, "geyser_mappings.json").writeText(mappingsJson(mapped, base))
            rep.ok("Wrote geyser_mappings.json (${mapped.size} items)")
            File(outDir, "geyser_config_snippet.yml").writeText(CONFIG_SNIPPET)
            File(outDir, "INSTALL.txt").writeText(INSTALL)
            File(outDir, "report.txt").writeText(report(opts, scan, mapped.size, skipped, flat3d, soundCount))
            rep.progress(1f, "Done")
            return ToolResult("${mapped.size} items converted, $skipped skipped, $soundCount sound events")
        }
    }

    private fun manifest(name: String, slug: String): String {
        val header = UUID.nameUUIDFromBytes("kmt-header-$slug".toByteArray()).toString()
        val module = UUID.nameUUIDFromBytes("kmt-module-$slug".toByteArray()).toString()
        return Js.write(
            buildJsonObject {
                put("format_version", 2)
                putJsonObject("header") {
                    put("name", name)
                    put("description", "Converted by KyleMCTools")
                    put("uuid", header)
                    putJsonArray("version") { add(1); add(0); add(0) }
                    putJsonArray("min_engine_version") { add(1); add(16); add(0) }
                }
                putJsonArray("modules") {
                    add(
                        buildJsonObject {
                            put("type", "resources")
                            put("uuid", module)
                            putJsonArray("version") { add(1); add(0); add(0) }
                        },
                    )
                }
            },
        )
    }

    private fun itemTextureJson(slug: String, mapped: List<Mapped>): String = Js.write(
        buildJsonObject {
            put("resource_pack_name", slug)
            put("texture_name", "atlas.items")
            putJsonObject("texture_data") {
                for (m in mapped) putJsonObject(m.item.id) { putJsonArray("textures") { add("textures/items/${m.item.id}") } }
            }
        },
    )

    private fun mappingsJson(mapped: List<Mapped>, base: String): String {
        val byBase = LinkedHashMap<String, MutableList<JsonObject>>()
        for (m in mapped) {
            val item = m.item
            val key = if (item.kind == ItemKind.LEGACY_CMD) item.baseMaterial else base
            val def = buildJsonObject {
                if (item.kind == ItemKind.ITEM_MODEL) {
                    put("type", "definition")
                    put("model", item.itemModelId!!)
                } else {
                    put("type", "legacy")
                    put("custom_model_data", item.customModelData!!)
                }
                put("bedrock_identifier", m.bedrockId)
                put("display_name", Ids.pretty(item.id))
                putJsonObject("bedrock_options") { put("icon", item.id) }
            }
            byBase.getOrPut(key) { ArrayList() }.add(def)
        }
        return Js.write(
            buildJsonObject {
                put("format_version", 2)
                putJsonObject("items") {
                    for ((k, list) in byBase) putJsonArray(k) { list.forEach { add(it) } }
                }
            },
        )
    }

    private fun addSounds(pack: PackArchive, z: ZipOutputStream, rep: Reporter): Int {
        val re = Regex("^assets/([^/]+)/sounds\\.json$")
        val defs = LinkedHashMap<String, JsonElement>()
        val written = HashSet<String>()
        for (path in pack.names) {
            val ns = re.find(path)?.groupValues?.get(1) ?: continue
            val json = pack.readJson(path)
            if (json == null) { rep.warn("Couldn't read $path (invalid JSON)"); continue }
            for ((event, v) in json) {
                val arr = (v as? JsonObject)?.arr("sounds") ?: continue
                val entries = ArrayList<JsonObject>()
                for (s in arr) {
                    val raw = when (s) {
                        is JsonPrimitive -> s.content
                        is JsonObject -> s.str("name")
                        else -> null
                    } ?: continue
                    val vol = (s as? JsonObject)?.get("volume").num() ?: 1.0
                    val sns = if (':' in raw) raw.substringBefore(':') else ns
                    val spath = raw.substringAfter(':')
                    val src = "assets/$sns/sounds/$spath.ogg"
                    if (!pack.exists(src)) { rep.warn("Sound file missing: $src"); continue }
                    val dest = "sounds/$sns/$spath.ogg"
                    if (written.add(dest)) { z.putNextEntry(ZipEntry(dest)); pack.copyTo(src, z); z.closeEntry() }
                    entries += buildJsonObject { put("name", "sounds/$sns/$spath"); put("volume", vol) }
                }
                if (entries.isEmpty()) continue
                defs["$ns.$event"] = buildJsonObject {
                    put("category", "neutral")
                    putJsonArray("sounds") { entries.forEach { add(it) } }
                }
            }
        }
        if (defs.isEmpty()) return 0
        val json = Js.write(
            buildJsonObject {
                put("format_version", "1.14.0")
                putJsonObject("sound_definitions") { for ((k, v) in defs) put(k, v) }
            },
        )
        z.putNextEntry(ZipEntry("sounds/sound_definitions.json")); z.write(json.toByteArray()); z.closeEntry()
        return defs.size
    }

    private fun report(o: GeyserOptions, scan: ScanResult, mapped: Int, skipped: Int, flat3d: Int, sounds: Int) = buildString {
        appendLine("KyleMCTools - Java to Bedrock report")
        appendLine("Project: ${o.projectName}")
        appendLine("Java pack format: ${scan.packFormat ?: "unknown"}")
        appendLine("Items mapped: $mapped, skipped (no texture): $skipped")
        appendLine("Items using a 3D model (flat icon only for now): $flat3d")
        appendLine("Sound events converted: $sounds")
        appendLine()
        appendLine("Not converted in this version: 3D item models in hand (attachables), blocks, fonts, shaders, lang files.")
        if (scan.warnings.isNotEmpty()) { appendLine(); appendLine("Warnings:"); scan.warnings.forEach { appendLine("- $it") } }
    }

    private const val CONFIG_SNIPPET = """# Merge this into Geyser's config.yml (do not replace the whole file).
gameplay:
  enable-custom-content: true
"""

    private const val INSTALL = """KyleMCTools - installing the Geyser files

1. Find your Geyser folder inside the server's plugins folder:
   - Paper / Spigot / Purpur / Folia:  plugins/Geyser-Spigot/
   - Velocity:                          plugins/Geyser-Velocity/
   - BungeeCord:                        plugins/Geyser-BungeeCord/
   (On a proxy, install into the proxy's Geyser folder, not the backend server.)
2. Put geyser_mappings.json into  <Geyser folder>/custom_mappings/
3. Put the .mcpack file into      <Geyser folder>/packs/
4. Open config.yml in the Geyser folder and set the value shown in geyser_config_snippet.yml.
5. Restart the server. Join with a Bedrock device to check the items.
"""
}
