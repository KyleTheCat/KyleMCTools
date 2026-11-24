package dev.kyle.mctools.pluginconfig

import dev.kyle.mctools.core.*
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class PluginTarget(val label: String) { NEXO("Nexo"), ITEMSADDER("ItemsAdder"), ORAXEN("Oraxen") }

data class PluginOptions(
    val projectName: String,
    val target: PluginTarget,
    /** Base item for 1.21.4+ item_model items (any item works), e.g. PAPER. */
    val modelItemMaterial: String = "PAPER",
)

object PluginConfigGenerator {
    fun generate(input: File, outDir: File, opts: PluginOptions, rep: Reporter): ToolResult {
        outDir.mkdirs()
        val slug = Ids.slug(opts.projectName)
        PackArchive(input).use { pack ->
            rep.progress(0.05f, "Reading pack...")
            val scan = PackScanner(pack, rep).scan()
            if (scan.items.isEmpty()) {
                throw ToolException(
                    "No custom items found. The pack needs custom_model_data overrides " +
                        "(assets/minecraft/models/item/*.json) or item definitions (assets/<namespace>/items/*.json).",
                )
            }
            rep.ok("Found ${scan.items.size} custom items")
            rep.progress(0.3f, "Writing ${opts.target.label} config...")

            val referenced = scan.items.mapNotNull { it.modelRef?.let { r -> modelFile(r) } }.toSet()
            val skip = scan.overrideFiles - referenced
            val ymls = LinkedHashMap<String, String>()   // path inside zip -> yaml
            val review = ArrayList<String>()
            val assetDest: (String) -> String?

            when (opts.target) {
                PluginTarget.NEXO, PluginTarget.ORAXEN -> {
                    val plugin = if (opts.target == PluginTarget.NEXO) "Nexo" else "Oraxen"
                    ymls["plugins/$plugin/items/$slug.yml"] = nexoOrOraxen(scan.items, opts)
                    assetDest = { "plugins/$plugin/pack/$it" }
                    review += "Namespaced models are written as \"namespace:path\". Check in-game that $plugin resolves them."
                    review += "Vanilla override files (assets/minecraft/models/item/<material>.json and assets/minecraft/items/*.json) were left out because $plugin generates them itself."
                }
                PluginTarget.ITEMSADDER -> {
                    val byNs = LinkedHashMap<String, MutableList<PackItem>>()
                    for (item in scan.items) {
                        val ref = item.modelRef
                        if (ref == null) { rep.warn("Skipped ${item.id}: no model found"); continue }
                        val ns = ref.substringBefore(':').let { if (it == "minecraft") slug else Ids.slug(it) }
                        byNs.getOrPut(ns) { ArrayList() }.add(item)
                    }
                    for ((ns, list) in byNs) ymls["plugins/ItemsAdder/contents/$ns/configs/$slug.yml"] = itemsAdder(ns, list, opts)
                    assetDest = { path ->
                        val assetNs = path.removePrefix("assets/").substringBefore('/')
                        val group = if (assetNs == "minecraft") slug else Ids.slug(assetNs)
                        "plugins/ItemsAdder/contents/$group/resourcepack/${path.removePrefix("assets/")}"
                    }
                    if (scan.items.any { it.modelRef?.startsWith("minecraft:") == true }) {
                        review += "Some models are in the minecraft namespace. ItemsAdder expects models inside your own namespace folder; move them if items don't load."
                    }
                    review += "ItemsAdder builds the base-item overrides itself, so vanilla override files were left out."
                }
            }

            rep.progress(0.5f, "Packing server files...")
            val zipFile = File(outDir, "${opts.target.name.lowercase()}_${slug}_server_files.zip")
            var assets = 0
            ZipOutputStream(BufferedOutputStream(zipFile.outputStream())).use { z ->
                for ((path, text) in ymls) { z.putNextEntry(ZipEntry(path)); z.write(text.toByteArray()); z.closeEntry() }
                val toCopy = pack.names.filter { it.startsWith("assets/") && it !in skip }
                for ((i, name) in toCopy.withIndex()) {
                    val dest = assetDest(name) ?: continue
                    z.putNextEntry(ZipEntry(dest)); pack.copyTo(name, z); z.closeEntry()
                    assets++
                    if (i % 50 == 0) rep.progress(0.5f + 0.4f * i / toCopy.size, "Copying assets ${i + 1}/${toCopy.size}")
                }
            }
            rep.ok("Wrote ${zipFile.name} ($assets asset files)")

            for ((path, text) in ymls) {
                val loose = "${opts.target.label}_" + path.substringAfter("plugins/").replace('/', '_')
                File(outDir, loose).writeText(text)
            }
            File(outDir, "REVIEW.txt").writeText(reviewText(opts, scan, review))
            rep.progress(1f, "Done")
            return ToolResult("${scan.items.size} items written for ${opts.target.label}")
        }
    }

    private fun modelFile(ref: String) = "assets/${ref.substringBefore(':')}/models/${ref.substringAfter(':')}.json"

    private fun materialOf(item: PackItem, opts: PluginOptions): String {
        val raw = if (item.kind == ItemKind.ITEM_MODEL) opts.modelItemMaterial else item.baseMaterial
        return raw.trim().removePrefix("minecraft:").removePrefix("MINECRAFT:").uppercase().replace(' ', '_')
    }

    private fun cfgModelPath(ref: String): String {
        val ns = ref.substringBefore(':')
        val path = ref.substringAfter(':')
        return if (ns == "minecraft") path else "$ns:$path"
    }

    private fun nexoOrOraxen(items: List<PackItem>, opts: PluginOptions): String {
        val nexo = opts.target == PluginTarget.NEXO
        val root = LinkedHashMap<String, Any?>()
        for (item in items) {
            val m = LinkedHashMap<String, Any?>()
            m[if (nexo) "itemname" else "displayname"] = Ids.pretty(item.id)
            m["material"] = materialOf(item, opts)
            if (item.kind == ItemKind.ITEM_MODEL) {
                m["Components"] = linkedMapOf("item_model" to item.itemModelId)
            } else {
                val pack = LinkedHashMap<String, Any?>()
                if (!nexo) pack["generate_model"] = false
                pack["model"] = cfgModelPath(item.modelRef!!)
                pack["custom_model_data"] = item.customModelData
                m["Pack"] = pack
            }
            root[item.id] = m
        }
        return Yaml.dump(root, "Generated by KyleMCTools (${opts.target.label}). Review before use.")
    }

    private fun itemsAdder(ns: String, items: List<PackItem>, opts: PluginOptions): String {
        val list = LinkedHashMap<String, Any?>()
        for (item in items) {
            list[item.id] = linkedMapOf(
                "display_name" to Ids.pretty(item.id),
                "resource" to linkedMapOf(
                    "material" to materialOf(item, opts),
                    "generate" to false,
                    "model_path" to item.modelRef!!.substringAfter(':'),
                ),
            )
        }
        val root = linkedMapOf<String, Any?>("info" to linkedMapOf("namespace" to ns), "items" to list)
        return Yaml.dump(root, "Generated by KyleMCTools (ItemsAdder). Review before use.")
    }

    private fun reviewText(opts: PluginOptions, scan: ScanResult, notes: List<String>) = buildString {
        appendLine("KyleMCTools - ${opts.target.label} config review")
        appendLine("Items: ${scan.items.size} (legacy custom_model_data: ${scan.items.count { it.kind == ItemKind.LEGACY_CMD }}, item_model: ${scan.items.count { it.kind == ItemKind.ITEM_MODEL }})")
        appendLine("Display names were made from item ids. Edit them, and add lore, permissions and mechanics yourself.")
        if (scan.items.any { it.kind == ItemKind.ITEM_MODEL }) appendLine("item_model items use base item ${opts.modelItemMaterial.uppercase()}; change it per item if needed.")
        appendLine()
        notes.forEach { appendLine("- $it") }
        if (scan.warnings.isNotEmpty()) { appendLine(); appendLine("Warnings:"); scan.warnings.forEach { appendLine("- $it") } }
        appendLine()
        appendLine("Copy the contents of the server_files zip into the server folder (merge with existing plugins/ folders), then restart or reload the plugin.")
    }
}
