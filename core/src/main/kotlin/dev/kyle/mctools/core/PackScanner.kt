package dev.kyle.mctools.core

import kotlinx.serialization.json.*

enum class ItemKind { LEGACY_CMD, ITEM_MODEL }

/** Neutral description of one custom item found in a Java pack. All converters read this. */
data class PackItem(
    val id: String,
    val kind: ItemKind,
    /** "minecraft:diamond_sword" for legacy items; empty for ITEM_MODEL items (any base item works). */
    val baseMaterial: String,
    /** "ns:path" of the model file (assets/ns/models/path.json), if known. */
    val modelRef: String?,
    /** "ns:path" item_model id (ITEM_MODEL only). */
    val itemModelId: String?,
    val customModelData: Int?,
    /** "ns:path" of the first texture (assets/ns/textures/path.png). */
    val iconTexture: String?,
    val is3d: Boolean,
) {
    val namespace: String
        get() = (itemModelId ?: modelRef)?.substringBefore(':') ?: "minecraft"
}

data class ScanResult(
    val items: List<PackItem>,
    val packFormat: String?,
    val warnings: List<String>,
    /** Vanilla override files (item model lists) that plugins regenerate themselves. */
    val overrideFiles: Set<String>,
)

class PackScanner(private val pack: PackArchive, private val rep: Reporter) {
    private val warnings = mutableListOf<String>()
    private val modelCache = HashMap<String, ModelInfo>()
    private val overrideFiles = HashSet<String>()

    private data class ModelInfo(val found: Boolean, val is3d: Boolean, val texture: String?)

    private fun warn(m: String) { warnings += m; rep.warn(m) }

    fun scan(): ScanResult {
        val used = HashSet<String>()
        val seen = HashSet<String>()
        val items = ArrayList<PackItem>()

        // 1) Legacy: assets/minecraft/models/item/<material>.json with custom_model_data overrides
        for (path in pack.list("assets/minecraft/models/item/")) {
            if (!path.endsWith(".json") || path.count { it == '/' } != 4) continue
            val material = path.substringAfterLast('/').removeSuffix(".json")
            val json = pack.readJson(path)
            if (json == null) { warn("Couldn't read $path (invalid JSON), skipped"); continue }
            val overrides = json.arr("overrides") ?: continue
            var found = false
            for (el in overrides) {
                val o = el as? JsonObject ?: continue
                val cmd = o.obj("predicate")?.get("custom_model_data").num()?.toInt() ?: continue
                val model = o.str("model") ?: continue
                if (!seen.add("$material#$cmd")) continue
                found = true
                addLegacy(items, used, material, cmd, Ids.ref(model))
            }
            if (found) overrideFiles += path
        }

        // 2) 1.21.4+: vanilla item definitions using range_dispatch on custom_model_data
        for (path in pack.list("assets/minecraft/items/")) {
            if (!path.endsWith(".json") || path.count { it == '/' } != 3) continue
            val material = path.substringAfterLast('/').removeSuffix(".json")
            val json = pack.readJson(path)
            if (json == null) { warn("Couldn't read $path (invalid JSON), skipped"); continue }
            val found = ArrayList<Pair<Int, String>>()
            collectRange(json, found)
            for ((cmd, model) in found) {
                if (!seen.add("$material#$cmd")) continue
                addLegacy(items, used, material, cmd, Ids.ref(model))
            }
            if (found.isNotEmpty()) overrideFiles += path
        }

        // 3) 1.21.4+: item model definitions in custom namespaces (used with the item_model component)
        val defRe = Regex("^assets/([^/]+)/items/(.+)\\.json$")
        for (path in pack.names) {
            val m = defRe.find(path) ?: continue
            val ns = m.groupValues[1]
            if (ns == "minecraft") continue
            val rest = m.groupValues[2]
            val json = pack.readJson(path)
            if (json == null) { warn("Couldn't read $path (invalid JSON), skipped"); continue }
            val modelRef = firstModelRef(json)?.let { Ids.ref(it) }
            val info = modelRef?.let { model(it) }
            if (modelRef != null && info != null && !info.found) warn("Model $modelRef used by $ns:$rest was not found in the pack")
            val id = Ids.unique(Ids.slug(rest.substringAfterLast('/')), used)
            items += PackItem(id, ItemKind.ITEM_MODEL, "", modelRef, "$ns:$rest", null, info?.texture, info?.is3d ?: false)
        }

        return ScanResult(items, readPackFormat(), warnings, overrideFiles)
    }

    private fun addLegacy(items: MutableList<PackItem>, used: MutableSet<String>, material: String, cmd: Int, ref: String) {
        val info = model(ref)
        if (!info.found) warn("Model $ref (used in $material.json) was not found in the pack")
        val id = Ids.unique(Ids.slug(ref.substringAfter(':').substringAfterLast('/')), used)
        items += PackItem(id, ItemKind.LEGACY_CMD, "minecraft:$material", ref, null, cmd, info.texture, info.is3d)
    }

    private fun collectRange(el: JsonElement?, out: MutableList<Pair<Int, String>>) {
        when (el) {
            is JsonObject -> {
                val type = el.str("type")?.removePrefix("minecraft:")
                val prop = el.str("property")?.removePrefix("minecraft:")
                if (type == "range_dispatch" && prop == "custom_model_data") {
                    el.arr("entries")?.forEach { e ->
                        val eo = e as? JsonObject ?: return@forEach
                        val th = eo["threshold"].num()?.toInt() ?: return@forEach
                        val ref = firstModelRef(eo["model"]) ?: return@forEach
                        out.add(th to ref)
                    }
                }
                for ((_, v) in el) collectRange(v, out)
            }
            is JsonArray -> for (v in el) collectRange(v, out)
            else -> {}
        }
    }

    private fun firstModelRef(el: JsonElement?): String? {
        when (el) {
            is JsonObject -> {
                if (el.str("type")?.removePrefix("minecraft:") == "model") el.str("model")?.let { return it }
                for ((_, v) in el) firstModelRef(v)?.let { return it }
            }
            is JsonArray -> for (v in el) firstModelRef(v)?.let { return it }
            else -> {}
        }
        return null
    }

    private fun model(ref: String, depth: Int = 0): ModelInfo {
        modelCache[ref]?.let { return it }
        val ns = ref.substringBefore(':')
        val path = ref.substringAfter(':')
        val json = pack.readJson("assets/$ns/models/$path.json")
        val info = if (json == null) ModelInfo(false, false, null) else {
            var is3d = (json.arr("elements")?.size ?: 0) > 0
            var parentTexture: String? = null
            val parent = json.str("parent")?.let { Ids.ref(it) }
            if (parent != null && depth < 6) {
                val pp = parent.substringAfter(':')
                val customParent = pack.exists("assets/${parent.substringBefore(':')}/models/$pp.json")
                if (customParent) {
                    val pi = model(parent, depth + 1)
                    if (pi.is3d) is3d = true
                    parentTexture = pi.texture
                } else if (pp.startsWith("block/") || pp == "builtin/entity") {
                    is3d = true
                }
            }
            val textures = LinkedHashMap<String, String>()
            json.obj("textures")?.forEach { (k, v) ->
                val s = (v as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (s != null && !s.startsWith("#")) textures[k] = Ids.ref(s)
            }
            val tex = textures["layer0"]
                ?: textures.entries.firstOrNull { it.key != "particle" }?.value
                ?: textures["particle"]
                ?: parentTexture
            ModelInfo(true, is3d, tex)
        }
        modelCache[ref] = info
        return info
    }

    private fun readPackFormat(): String? {
        val p = pack.readJson("pack.mcmeta")?.obj("pack") ?: return null
        p["pack_format"].num()?.let { return it.toInt().toString() }
        return p["min_format"]?.toString()?.let { "min_format $it" }
    }
}
