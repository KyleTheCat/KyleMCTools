package dev.kyle.mctools.meg

import dev.kyle.mctools.core.*
import kotlinx.serialization.json.*
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

data class MegOptions(
    val projectName: String,
    /** If side faces of textured cubes look mirrored in-game, turn this on and convert again. */
    val swapEastWest: Boolean = false,
)

/** One model ready to be written: Bedrock geometry, optional animations, optional texture. */
class MegModel(val id: String, val geo: String, val animations: String?, val texture: ByteArray?, val warnings: List<String>)

/**
 * Converts Blockbench .bbmodel files (as used by ModelEngine) into Bedrock entity geometry, animations and a texture,
 * laid out as input/<modelId>/ for the GeyserModelEngine extension. The coordinate rules follow Blockbench's own
 * Bedrock export (x axis mirrored); they could not be checked against a Bedrock client here, so test in-game.
 */
object MegConverter {
    private fun n(v: Double): Double = if (v == 0.0 || v.isNaN()) 0.0 else v

    private fun JsonObject.vec(key: String, def: Double = 0.0): List<Double> {
        val a = this[key] as? JsonArray
        return (0 until 3).map { i -> a?.getOrNull(i).let { e -> (e as? JsonPrimitive)?.content?.toDoubleOrNull() } ?: def }
    }

    fun run(input: File, outDir: File, o: MegOptions, rep: Reporter): ToolResult {
        outDir.mkdirs()
        val sources = readSources(input, o.projectName, rep)
        if (sources.isEmpty()) throw ToolException("No .bbmodel files were found. Drop a .bbmodel file, or a zip that contains .bbmodel files.")
        rep.info("Found ${sources.size} model file(s)")
        val usedIds = HashSet<String>()
        val models = ArrayList<MegModel>()
        for ((i, src) in sources.withIndex()) {
            rep.progress(0.1f + 0.7f * i / sources.size, "Converting ${src.first}")
            val json = Js.parseObject(src.second)
            if (json == null) { rep.warn("${src.first}: not valid JSON, skipped"); continue }
            if (json["elements"] !is JsonArray) { rep.warn("${src.first}: has no cubes (elements), skipped"); continue }
            val id = Ids.unique(Ids.slug(json.str("name") ?: src.first.substringAfterLast('/').removeSuffix(".bbmodel")), usedIds)
            try {
                val m = convert(json, id, o)
                m.warnings.forEach { rep.warn("$id: $it") }
                models += m
                rep.ok("$id: ${if (m.animations != null) "geometry + animations" else "geometry only"}${if (m.texture != null) " + texture" else ", no texture"}")
            } catch (e: Exception) {
                rep.warn("${src.first}: couldn't be converted (${e.javaClass.simpleName}: ${e.message})")
            }
        }
        if (models.isEmpty()) throw ToolException("None of the models could be converted. See the log for the reason for each file.")

        rep.progress(0.85f, "Writing files...")
        val slug = Ids.slug(o.projectName)
        val zipFile = File(outDir, "${slug}_meg_input.zip")
        ZipOutputStream(BufferedOutputStream(zipFile.outputStream())).use { z ->
            fun put(path: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(path)); z.write(bytes); z.closeEntry() }
            for (m in models) {
                put("${m.id}/${m.id}.geo.json", m.geo.toByteArray())
                m.animations?.let { put("${m.id}/${m.id}.animation.json", it.toByteArray()) }
                m.texture?.let { put("${m.id}/${m.id}.png", it) }
            }
        }
        for (m in models) {
            val d = File(outDir, "input/${m.id}").also { it.mkdirs() }
            File(d, "${m.id}.geo.json").writeText(m.geo)
            m.animations?.let { File(d, "${m.id}.animation.json").writeText(it) }
            m.texture?.let { File(d, "${m.id}.png").writeBytes(it) }
        }
        File(outDir, "README.txt").writeText(readme(models))
        rep.progress(1f, "Done")
        return ToolResult("${models.size} model(s) converted")
    }

    private fun readSources(input: File, project: String, rep: Reporter): List<Pair<String, String>> {
        val head = input.inputStream().use { s -> ByteArray(2).also { s.read(it) } }
        if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
            PackArchive(input, allowAnyZip = true).use { z ->
                return z.names.filter { it.lowercase().endsWith(".bbmodel") }.mapNotNull { name ->
                    z.readText(name)?.let { name to it }
                }
            }
        }
        rep.info("Single .bbmodel file")
        return listOf("$project.bbmodel" to input.readText().removePrefix("\uFEFF"))
    }

    private class Bone(val name: String, val parent: String?, val pivot: List<Double>, val rot: List<Double>, val cubes: MutableList<JsonObject> = ArrayList())

    fun convert(json: JsonObject, id: String, o: MegOptions): MegModel {
        val warnings = ArrayList<String>()
        val res = json.obj("resolution")
        val texW = res?.get("width").num()?.toInt() ?: 16
        val texH = res?.get("height").num()?.toInt() ?: 16

        val cubes = HashMap<String, JsonObject>()
        var skipped = 0
        for (el in json.arr("elements") ?: JsonArray(emptyList())) {
            val e = el as? JsonObject ?: continue
            val type = e.str("type") ?: "cube"
            val uuid = e.str("uuid") ?: continue
            if (type == "cube") cubes[uuid] = e else skipped++
        }
        if (skipped > 0) warnings += "$skipped non-cube element(s) (meshes, locators, null objects) were skipped"

        val bones = ArrayList<Bone>()
        val boneByUuid = HashMap<String, String>()
        val usedNames = HashSet<String>()
        var rootBone: Bone? = null
        fun walk(items: JsonArray, parent: Bone?) {
            for (node in items) {
                if (node is JsonPrimitive && node.isString) {
                    val cube = cubes[node.content] ?: continue
                    var holder = parent ?: rootBone
                    if (holder == null) {
                        holder = Bone(Ids.unique("root", usedNames), null, listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0))
                        bones += holder
                        rootBone = holder
                    }
                    holder.cubes += cube
                } else if (node is JsonObject) {
                    val b = Bone(Ids.unique(node.str("name") ?: "bone", usedNames), parent?.name, node.vec("origin"), node.vec("rotation"))
                    bones += b
                    node.str("uuid")?.let { u -> boneByUuid[u] = b.name }
                    (node["children"] as? JsonArray)?.let { ch -> walk(ch, b) }
                }
            }
        }
        walk(json.arr("outliner") ?: JsonArray(emptyList()), null)

        var maxX = 8.0; var maxY = 16.0
        val boneJson = bones.map { b ->
            buildJsonObject {
                put("name", b.name)
                b.parent?.let { put("parent", it) }
                putJsonArray("pivot") { add(n(-b.pivot[0])); add(n(b.pivot[1])); add(n(b.pivot[2])) }
                if (b.rot.any { it != 0.0 }) putJsonArray("rotation") { add(n(-b.rot[0])); add(n(-b.rot[1])); add(n(b.rot[2])) }
                if (b.cubes.isNotEmpty()) putJsonArray("cubes") {
                    for (c in b.cubes) {
                        val from = c.vec("from"); val to = c.vec("to")
                        maxX = max(maxX, max(abs(from[0]), abs(to[0])).let { v -> max(v, max(abs(from[2]), abs(to[2]))) })
                        maxY = max(maxY, to[1])
                        add(cubeJson(c, from, to, o, warnings))
                    }
                }
            }
        }
        val bw = max(3.0, ceil(maxX * 2 / 16.0) + 1)
        val bh = max(3.0, ceil(maxY / 16.0) + 1)
        val geo = Js.compact(
            buildJsonObject {
                put("format_version", "1.12.0")
                putJsonArray("minecraft:geometry") {
                    add(
                        buildJsonObject {
                            putJsonObject("description") {
                                put("identifier", "geometry.$id")
                                put("texture_width", texW); put("texture_height", texH)
                                put("visible_bounds_width", bw); put("visible_bounds_height", bh)
                                putJsonArray("visible_bounds_offset") { add(0); add(bh / 2); add(0) }
                            }
                            putJsonArray("bones") { boneJson.forEach { add(it) } }
                        },
                    )
                }
            },
        )

        val anim = animations(json, id, boneByUuid, warnings)

        var texture: ByteArray? = null
        val textures = json.arr("textures")
        if (textures != null && textures.isNotEmpty()) {
            if (textures.size > 1) warnings += "${textures.size} textures found; only the first is exported (GeyserModelEngine's Blockbench packer plugin handles multi-texture models)"
            val src = (textures[0] as? JsonObject)?.str("source")
            if (src != null && src.startsWith("data:")) {
                texture = try { Base64.getDecoder().decode(src.substringAfter(',').trim()) } catch (e: IllegalArgumentException) { null }
                if (texture == null) warnings += "the embedded texture could not be decoded"
            } else warnings += "the texture is not embedded in the .bbmodel; export it from Blockbench and add it yourself"
        } else warnings += "the model has no texture"
        return MegModel(id, geo, anim, texture, warnings.distinct())
    }

    private fun cubeJson(c: JsonObject, from: List<Double>, to: List<Double>, o: MegOptions, warnings: MutableList<String>): JsonObject = buildJsonObject {
        putJsonArray("origin") { add(n(-to[0])); add(n(from[1])); add(n(from[2])) }
        putJsonArray("size") { add(n(to[0] - from[0])); add(n(to[1] - from[1])); add(n(to[2] - from[2])) }
        val rot = c.vec("rotation")
        if (rot.any { it != 0.0 }) {
            val org = c.vec("origin")
            putJsonArray("pivot") { add(n(-org[0])); add(n(org[1])); add(n(org[2])) }
            putJsonArray("rotation") { add(n(-rot[0])); add(n(-rot[1])); add(n(rot[2])) }
        }
        val inflate = c.get("inflate").num() ?: 0.0
        if (inflate != 0.0) put("inflate", inflate)
        val boxUv = (c["box_uv"] as? JsonPrimitive)?.booleanOrNull ?: false
        if (boxUv) {
            val off = c.vec("uv_offset")
            putJsonArray("uv") { add(n(off[0])); add(n(off[1])) }
            if ((c["mirror_uv"] as? JsonPrimitive)?.booleanOrNull == true) put("mirror", true)
        } else {
            val faces = c.obj("faces")
            if (faces != null) putJsonObject("uv") {
                for ((dirName, fe) in faces) {
                    val f = fe as? JsonObject ?: continue
                    if (f["texture"] is JsonNull || f["texture"] == null) continue
                    val uv = (f["uv"] as? JsonArray)?.map { (it as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0 } ?: continue
                    if (uv.size < 4) continue
                    if (f["rotation"].num()?.let { it != 0.0 } == true) warnings += "a face uses UV rotation, which Bedrock geometry can't express; it will look rotated"
                    val key = if (o.swapEastWest) when (dirName) { "east" -> "west"; "west" -> "east"; else -> dirName } else dirName
                    putJsonObject(key) {
                        putJsonArray("uv") { add(n(uv[0])); add(n(uv[1])) }
                        putJsonArray("uv_size") { add(n(uv[2] - uv[0])); add(n(uv[3] - uv[1])) }
                    }
                }
            }
        }
    }

    private fun fmtTime(t: Double): String {
        val s = "%.4f".format(java.util.Locale.ROOT, t).trimEnd('0')
        return if (s.endsWith(".")) s + "0" else s
    }

    private fun dp(el: JsonElement?): Any = when (el) {
        is JsonPrimitive -> if (el.isString) (el.content.toDoubleOrNull() ?: el.content.ifBlank { "0" }) else (el.doubleOrNull ?: 0.0)
        else -> 0.0
    }

    private fun neg(v: Any): Any = if (v is Double) n(-v) else "-($v)"
    private fun jv(v: Any): JsonElement = if (v is Double) JsonPrimitive(n(v)) else JsonPrimitive(v.toString())

    private fun animations(model: JsonObject, id: String, boneByUuid: Map<String, String>, warnings: MutableList<String>): String? {
        val list = model.arr("animations") ?: return null
        if (list.isEmpty()) return null
        var interpolationWarned = false
        val anims = LinkedHashMap<String, JsonElement>()
        for (ael in list) {
            val a = ael as? JsonObject ?: continue
            val name = (a.str("name") ?: "anim").replace(Regex("[^A-Za-z0-9_.-]+"), "_")
            val length = a["length"].num() ?: 0.0
            val loop = a.str("loop") ?: "once"
            val bones = LinkedHashMap<String, JsonElement>()
            for ((uuid, animatorEl) in a.obj("animators") ?: JsonObject(emptyMap())) {
                val animator = animatorEl as? JsonObject ?: continue
                val boneName = boneByUuid[uuid] ?: animator.str("name") ?: continue
                if ((animator.str("type") ?: "bone") != "bone") continue
                val channels = LinkedHashMap<String, MutableMap<Double, JsonElement>>()
                for (kfEl in animator.arr("keyframes") ?: JsonArray(emptyList())) {
                    val kf = kfEl as? JsonObject ?: continue
                    val channel = kf.str("channel") ?: continue
                    if (channel !in setOf("rotation", "position", "scale")) continue
                    val pts = kf.arr("data_points") ?: continue
                    if (pts.isEmpty()) continue
                    val time = kf["time"].num() ?: 0.0
                    fun vecOf(p: JsonElement): JsonElement {
                        val po = p as? JsonObject ?: return JsonArray(listOf(JsonPrimitive(0.0), JsonPrimitive(0.0), JsonPrimitive(0.0)))
                        var x = dp(po["x"]); var y = dp(po["y"]); val z = dp(po["z"])
                        when (channel) { "rotation" -> { x = neg(x); y = neg(y) }; "position" -> x = neg(x) }
                        return JsonArray(listOf(jv(x), jv(y), jv(z)))
                    }
                    val interp = kf.str("interpolation") ?: "linear"
                    if ((interp == "step" || interp == "bezier") && !interpolationWarned) {
                        warnings += "step/bezier keyframe interpolation was converted to linear"; interpolationWarned = true
                    }
                    val lerp = if (interp == "catmullrom") "catmullrom" else null
                    val value: JsonElement = if (pts.size >= 2 || lerp != null) buildJsonObject {
                        if (pts.size >= 2) { put("pre", vecOf(pts[0])); put("post", vecOf(pts[1])) } else put("post", vecOf(pts[0]))
                        if (lerp != null) put("lerp_mode", lerp)
                    } else vecOf(pts[0])
                    channels.getOrPut(channel) { java.util.TreeMap() }[time] = value
                }
                if (channels.isEmpty()) continue
                bones[boneName] = buildJsonObject {
                    for ((ch, frames) in channels) put(ch, buildJsonObject { for ((t, v) in frames) put(fmtTime(t), v) })
                }
            }
            anims["animation.$id.$name"] = buildJsonObject {
                when (loop) { "loop" -> put("loop", true); "hold" -> put("loop", "hold_on_last_frame") }
                put("animation_length", length)
                if (bones.isNotEmpty()) put("bones", JsonObject(bones))
            }
        }
        if (anims.isEmpty()) return null
        return Js.compact(buildJsonObject { put("format_version", "1.8.0"); put("animations", JsonObject(anims)) })
    }

    private fun readme(models: List<MegModel>) = buildString {
        appendLine("KyleMCTools - GeyserModelEngine input")
        appendLine()
        appendLine("Models: " + models.joinToString { it.id })
        appendLine()
        appendLine("How to use")
        appendLine("1. Unzip the *_meg_input.zip (or copy the 'input' folder) so each model has its own folder.")
        appendLine("2. Put the model folders into the GeyserModelEngine extension's input folder, e.g.")
        appendLine("   plugins/Geyser-<Platform>/extensions/geysermodelengineextension/input/<modelId>/")
        appendLine("   (older versions of the extension use .../geysermodelenginepackgenerator/input/)")
        appendLine("3. Restart the server or reload Geyser. The extension builds the Bedrock pack itself.")
        appendLine("   It only regenerates when the number of models changes; delete its generated_pack folder to force a rebuild.")
        appendLine()
        appendLine("Notes")
        appendLine("- Folder name = the ModelEngine model id. Names here come from each model's name; rename the folders if your ids differ.")
        appendLine("- File names inside the folders (<id>.geo.json, <id>.animation.json, <id>.png) follow Blockbench's Bedrock export; check them against your extension version.")
        appendLine("- Models with several textures or animated textures need the Blockbench packer plugin from GeyserExtensionists.")
        appendLine("- Open a .geo.json in Blockbench (File > Import) to compare the shape with your original before using it.")
        appendLine("- If side faces look mirrored, convert again with 'Swap east/west faces' turned on.")
        val warned = models.filter { it.warnings.isNotEmpty() }
        if (warned.isNotEmpty()) {
            appendLine(); appendLine("Warnings")
            warned.forEach { m -> m.warnings.forEach { appendLine("- ${m.id}: $it") } }
        }
    }
}
