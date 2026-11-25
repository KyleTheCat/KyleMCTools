package dev.kyle.mctools.version

import dev.kyle.mctools.core.*
import kotlinx.serialization.json.*
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class VersionOptions(val projectName: String, val targetFormat: Int)

/**
 * Moves a Java resource pack (1.13 or newer) to another Minecraft version. It changes what can be changed safely:
 * pack.mcmeta, item model overrides <-> 1.21.4 item definitions (custom_model_data), and the 1.21.2 armor
 * texture folders. Anything else that changed between the versions is listed in the report, not guessed.
 */
object VersionConverter {
    private const val ITEM_DEF_FORMAT = 46      // 1.21.4: assets/<ns>/items/*.json replace model overrides
    private const val ARMOR_FORMAT = 42         // 1.21.2: armor textures moved to textures/entity/equipment
    private const val GUI_SPRITES_FORMAT = 18   // 1.20.2: GUI textures became sprites

    private val armorRe = Regex("^assets/minecraft/textures/models/armor/(chainmail|diamond|gold|iron|leather|netherite|turtle)_layer_([12])(_overlay)?\\.png$")
    private val equipRe = Regex("^assets/minecraft/textures/entity/equipment/(humanoid|humanoid_leggings)/(chainmail|diamond|gold|iron|leather|netherite|turtle_scute)(_overlay)?\\.png$")

    fun run(input: File, outDir: File, o: VersionOptions, rep: Reporter): ToolResult {
        outDir.mkdirs()
        val slug = Ids.slug(o.projectName)
        PackArchive(input, allowAnyZip = true).use { pack ->
            if (pack.isBedrock) throw ToolException("This looks like a Bedrock pack. The Version Converter only handles Java resource packs.")
            val mcmeta = pack.readJson("pack.mcmeta") ?: throw ToolException("pack.mcmeta is missing or not valid JSON, so the pack's version can't be read.")
            val src = McVersions.sourceFormat(mcmeta)
                ?: throw ToolException("pack.mcmeta has no pack_format or min_format, so the pack's version can't be read.")
            val tgt = o.targetFormat
            if (src < 4) throw ToolException("This pack is older than Minecraft 1.13 (format $src). The 1.13 'flattening' renamed almost every file, which this tool doesn't convert.")
            if (tgt < 4) throw ToolException("Target versions older than 1.13 are not supported.")
            rep.info("From ${McVersions.label(src)} (format $src) to ${McVersions.label(tgt)} (format $tgt)")
            if (src == tgt) rep.warn("The pack already uses this format. Only pack.mcmeta is rewritten.")
            rep.progress(0.1f, "Planning changes...")

            val edits = LinkedHashMap<String, ByteArray>()   // new or replaced files
            val renames = LinkedHashMap<String, String>()    // old path -> new path
            val changes = ArrayList<String>()
            val notes = ArrayList<String>()
            fun warn(m: String) { notes += m; rep.warn(m) }

            // 1) pack.mcmeta
            edits["pack.mcmeta"] = Js.write(rewriteMcmeta(mcmeta, tgt)).toByteArray()
            changes += "pack.mcmeta now declares " + (if (tgt >= McVersions.RANGE_FORMAT_FROM) "min_format/max_format [$tgt, 0]" else "pack_format $tgt")
            if (mcmeta.obj("overlays") != null) warn("The pack uses overlays. They were left as they are; check their format ranges.")

            // 2) item model overrides <-> item definitions (1.21.4)
            if (src < ITEM_DEF_FORMAT && tgt >= ITEM_DEF_FORMAT) overridesToDefinitions(pack, edits, changes, ::warn)
            if (src >= ITEM_DEF_FORMAT && tgt < ITEM_DEF_FORMAT) definitionsToOverrides(pack, edits, changes, ::warn)

            // 3) armor textures (1.21.2)
            if (src < ARMOR_FORMAT && tgt >= ARMOR_FORMAT) moveArmor(pack, renames, changes, up = true, ::warn)
            if (src >= ARMOR_FORMAT && tgt < ARMOR_FORMAT) moveArmor(pack, renames, changes, up = false, ::warn)

            // 4) things we only report
            val gui = pack.list("assets/minecraft/textures/gui/").filter { it.endsWith(".png") && !it.startsWith("assets/minecraft/textures/gui/sprites/") }
            if ((src < GUI_SPRITES_FORMAT) != (tgt < GUI_SPRITES_FORMAT) && gui.isNotEmpty()) {
                warn("GUI textures changed in 1.20.2 (single sheets became separate sprites). ${gui.size} GUI texture(s) were NOT converted, e.g. ${gui.take(3).joinToString { it.substringAfterLast('/') }}.")
            }

            rep.progress(0.4f, "Writing pack...")
            val out = File(outDir, "${slug}_${McVersions.label(tgt).substringBefore(' ').replace(Regex("[^0-9a-z.]"), "")}.zip")
            val written = HashSet<String>()
            val names = pack.names
            ZipOutputStream(BufferedOutputStream(out.outputStream())).use { z ->
                z.setLevel(9)
                for ((i, name) in names.withIndex()) {
                    val dest = renames[name] ?: name
                    if (!written.add(dest)) continue
                    z.putNextEntry(ZipEntry(dest))
                    val edited = edits[name]
                    if (edited != null) z.write(edited) else pack.copyTo(name, z)
                    z.closeEntry()
                    if (i % 25 == 0) rep.progress(0.4f + 0.5f * i / names.size, "Writing ${i + 1}/${names.size}")
                }
                for ((path, bytes) in edits) {
                    if (path in names || !written.add(path)) continue
                    z.putNextEntry(ZipEntry(path)); z.write(bytes); z.closeEntry()
                }
            }
            try { ZipFile(out).close() } catch (e: Exception) { throw ToolException("The converted pack could not be verified, so it was not kept.", e) }
            changes.forEach { rep.ok(it) }

            File(outDir, "conversion_report.txt").writeText(buildString {
                appendLine("KyleMCTools - Version Converter report")
                appendLine("From ${McVersions.label(src)} (format $src) to ${McVersions.label(tgt)} (format $tgt)")
                appendLine()
                appendLine("Changed:"); changes.forEach { appendLine("- $it") }
                if (notes.isNotEmpty()) { appendLine(); appendLine("Check by hand:"); notes.forEach { appendLine("- $it") } }
                appendLine()
                appendLine("Not converted automatically: anything else that changed between these versions (new or renamed vanilla files, shaders, fonts, GUI sprites, block models).")
                appendLine("Load the converted pack in-game and check the log for missing textures. Your original pack was not changed.")
            })
            rep.progress(1f, "Done")
            return ToolResult("${McVersions.label(src)} to ${McVersions.label(tgt)}: ${changes.size} changes, ${notes.size} things to check")
        }
    }

    private fun rewriteMcmeta(root: JsonObject, tgt: Int): JsonObject {
        val old = root.obj("pack") ?: JsonObject(emptyMap())
        val pack = buildJsonObject {
            for ((k, v) in old) if (k !in setOf("pack_format", "supported_formats", "min_format", "max_format")) put(k, v)
            if (tgt >= McVersions.RANGE_FORMAT_FROM) {
                putJsonArray("min_format") { add(tgt); add(0) }
                putJsonArray("max_format") { add(tgt); add(0) }
            } else {
                put("pack_format", tgt)
            }
        }
        return buildJsonObject { for ((k, v) in root) if (k != "pack") put(k, v); put("pack", pack) }
    }

    /** Returns (customModelData -> model ref) for overrides whose only predicate is custom_model_data. */
    private fun cmdOverrides(model: JsonObject, warn: (String) -> Unit, label: String): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        for (el in model.arr("overrides") ?: return out) {
            val o = el as? JsonObject ?: continue
            val pred = o.obj("predicate") ?: continue
            val cmd = pred["custom_model_data"].num()?.toInt()
            val ref = o.str("model")
            if (cmd == null || ref == null) continue
            if (pred.keys.any { it != "custom_model_data" }) { warn("$label: an override for custom_model_data $cmd also uses other predicates; it was not converted."); continue }
            out += cmd to ref
        }
        return out
    }

    private fun overridesToDefinitions(pack: PackArchive, edits: MutableMap<String, ByteArray>, changes: MutableList<String>, warn: (String) -> Unit) {
        var made = 0
        for (path in pack.list("assets/minecraft/models/item/")) {
            if (!path.endsWith(".json") || path.count { it == '/' } != 4) continue
            val mat = path.substringAfterLast('/').removeSuffix(".json")
            val model = pack.readJson(path) ?: continue
            val entries = cmdOverrides(model, warn, "$mat.json").sortedBy { it.first }
            if (entries.isEmpty()) continue
            val target = "assets/minecraft/items/$mat.json"
            if (pack.exists(target)) { warn("assets/minecraft/items/$mat.json already exists, so the overrides in $mat.json were not converted."); continue }
            edits[target] = Js.write(
                buildJsonObject {
                    putJsonObject("model") {
                        put("type", "minecraft:range_dispatch")
                        put("property", "minecraft:custom_model_data")
                        putJsonObject("fallback") { put("type", "minecraft:model"); put("model", "minecraft:item/$mat") }
                        putJsonArray("entries") {
                            for ((cmd, ref) in entries) add(
                                buildJsonObject {
                                    put("threshold", cmd)
                                    putJsonObject("model") { put("type", "minecraft:model"); put("model", Ids.ref(ref)) }
                                },
                            )
                        }
                    }
                },
            ).toByteArray()
            made++
            changes += "Created items/$mat.json with ${entries.size} custom_model_data entries (the old overrides stay in models/item/$mat.json, which 1.21.4+ ignores)"
        }
        if (made == 0) warn("No custom_model_data overrides were found to convert to 1.21.4 item definitions.")
        else warn("1.21.4 changed the custom_model_data component. Commands that gave items an old-style number may need to use custom_model_data={floats:[N]}.")
    }

    private fun definitionsToOverrides(pack: PackArchive, edits: MutableMap<String, ByteArray>, changes: MutableList<String>, warn: (String) -> Unit) {
        val customDefs = pack.names.count { Regex("^assets/(?!minecraft/)[^/]+/items/.+\\.json$").matches(it) }
        if (customDefs > 0) warn("$customDefs item definition(s) in custom namespaces (assets/<namespace>/items) have no equivalent before 1.21.4 and are ignored by older versions.")
        for (path in pack.list("assets/minecraft/items/")) {
            if (!path.endsWith(".json") || path.count { it == '/' } != 3) continue
            val mat = path.substringAfterLast('/').removeSuffix(".json")
            val def = pack.readJson(path) ?: continue
            val found = ArrayList<Pair<Int, String>>()
            collectRange(def, found)
            if (found.isEmpty()) continue
            val modelPath = "assets/minecraft/models/item/$mat.json"
            val existing = pack.readJson(modelPath)
            if (existing == null) {
                warn("items/$mat.json has custom_model_data entries, but models/item/$mat.json is not in the pack, so the vanilla model can't be copied safely. Add that model file and run again.")
                continue
            }
            val have = (existing.arr("overrides") ?: JsonArray(emptyList())).toMutableList()
            val known = cmdOverrides(existing, {}, "").map { it.first }.toSet()
            var added = 0
            for ((cmd, ref) in found.sortedBy { it.first }) {
                if (cmd in known) continue
                have += buildJsonObject {
                    putJsonObject("predicate") { put("custom_model_data", cmd) }
                    put("model", ref.removePrefix("minecraft:"))
                }
                added++
            }
            if (added == 0) continue
            edits[modelPath] = Js.write(buildJsonObject { for ((k, v) in existing) if (k != "overrides") put(k, v); put("overrides", JsonArray(have)) }).toByteArray()
            changes += "Added $added custom_model_data overrides to models/item/$mat.json"
        }
    }

    private fun collectRange(el: JsonElement?, out: MutableList<Pair<Int, String>>) {
        when (el) {
            is JsonObject -> {
                if (el.str("type")?.removePrefix("minecraft:") == "range_dispatch" && el.str("property")?.removePrefix("minecraft:") == "custom_model_data") {
                    el.arr("entries")?.forEach { e ->
                        val eo = e as? JsonObject ?: return@forEach
                        val th = eo["threshold"].num()?.toInt() ?: return@forEach
                        val m = eo.obj("model")
                        if (m?.str("type")?.removePrefix("minecraft:") != "model") return@forEach
                        val ref = m.str("model") ?: return@forEach
                        out += th to ref
                    }
                }
                for ((_, v) in el) collectRange(v, out)
            }
            is JsonArray -> for (v in el) collectRange(v, out)
            else -> {}
        }
    }

    private fun moveArmor(pack: PackArchive, renames: MutableMap<String, String>, changes: MutableList<String>, up: Boolean, warn: (String) -> Unit) {
        var moved = 0
        for (name in pack.names) {
            val dest: String? = if (up) {
                armorRe.find(name)?.let { m ->
                    val (mat, layer, overlay) = m.destructured
                    val folder = if (layer == "1") "humanoid" else "humanoid_leggings"
                    "assets/minecraft/textures/entity/equipment/$folder/${if (mat == "turtle") "turtle_scute" else mat}$overlay.png"
                }
            } else {
                equipRe.find(name)?.let { m ->
                    val (folder, mat, overlay) = m.destructured
                    "assets/minecraft/textures/models/armor/${if (mat == "turtle_scute") "turtle" else mat}_layer_${if (folder == "humanoid") 1 else 2}$overlay.png"
                }
            }
            if (dest == null) continue
            if (pack.exists(dest)) { warn("$dest already exists, so $name was not moved."); continue }
            renames[name] = dest
            moved++
        }
        if (moved > 0) changes += "Moved $moved vanilla armor texture(s) " + (if (up) "to textures/entity/equipment/ (1.21.2 layout)" else "back to textures/models/armor/")
        val custom = pack.names.count { Regex("^assets/(?!minecraft/)[^/]+/textures/models/armor/").containsMatchIn(it) }
        if (up && custom > 0) warn("$custom custom armor texture(s) in other namespaces were not moved. From 1.21.2 they need an equipment definition (assets/<namespace>/equipment/<id>.json).")
    }
}
