package dev.kyle.mctools.optimizer

import dev.kyle.mctools.core.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class OptimizeOptions(
    val projectName: String,
    val pngs: Boolean = true,
    val json: Boolean = true,
    val junk: Boolean = true,
    /** Java only. Off by default: plugin source packs reference textures from plugin config. */
    val unused: Boolean = false,
)

object PackOptimizer {
    private val junkExt = setOf("psd", "xcf", "kra", "blend", "blend1", "ase", "aseprite", "pdn", "bak", "tmp", "orig", "afphoto")
    private val junkNames = setOf(".ds_store", "thumbs.db", "desktop.ini", ".gitkeep", ".gitignore", ".gitattributes")

    fun isJunk(path: String): Boolean {
        val lower = path.lowercase()
        val name = lower.substringAfterLast('/')
        if (name in junkNames) return true
        if (name.substringAfterLast('.', "") in junkExt) return true
        return lower.split('/').dropLast(1).any { it.startsWith(".") }
    }

    fun size(b: Long): String = when {
        b >= 1024L * 1024 -> "%.1f MB".format(b / 1024.0 / 1024.0)
        b >= 1024 -> "%.0f KB".format(b / 1024.0)
        else -> "$b B"
    }

    fun run(input: File, outDir: File, o: OptimizeOptions, rep: Reporter): ToolResult {
        outDir.mkdirs()
        val slug = Ids.slug(o.projectName)
        val before = input.length()
        PackArchive(input, allowAnyZip = true).use { pack ->
            val bedrock = pack.isBedrock
            rep.info(if (bedrock) "Bedrock texture pack detected" else "Java resource pack detected")
            rep.progress(0.05f, "Scanning ${pack.names.size} files...")

            val remove = LinkedHashSet<String>()
            if (o.junk) pack.names.filterTo(remove) { isJunk(it) }
            val junkCount = remove.size
            if (o.unused) {
                if (bedrock) rep.warn("Unreferenced-texture removal is Java only, so it was skipped")
                else remove += findUnusedTextures(pack, rep)
            }

            val outFile = File(outDir, "${slug}_optimized.${if (bedrock) "mcpack" else "zip"}")
            var pngBefore = 0L; var pngAfter = 0L; var pngCount = 0
            var jsonBefore = 0L; var jsonAfter = 0L; var jsonCount = 0
            var removedBytes = 0L
            val kept = ArrayList<Pair<String, Long>>()
            val names = pack.names
            ZipOutputStream(BufferedOutputStream(outFile.outputStream())).use { z ->
                z.setLevel(9)
                for ((i, name) in names.withIndex()) {
                    if (name in remove) { removedBytes += pack.size(name); continue }
                    val lower = name.lowercase()
                    var data: ByteArray? = null
                    val sz = pack.size(name)
                    if (o.pngs && lower.endsWith(".png") && sz in 1L..33_554_432L) {
                        val b = pack.readBytes(name)!!
                        val opt = PngOptimizer.optimize(b)
                        pngBefore += b.size
                        if (opt != null) { data = opt; pngAfter += opt.size; pngCount++ } else { data = b; pngAfter += b.size }
                    } else if (o.json && (lower.endsWith(".json") || lower.endsWith(".mcmeta")) && sz in 1L..16_777_216L) {
                        val text = pack.readText(name)
                        val min = text?.let { Js.minify(it) }
                        if (text == null || min == null) {
                            rep.warn("Left $name as it is (not valid JSON)")
                        } else if (min.length < text.length) {
                            data = min.toByteArray(); jsonBefore += sz; jsonAfter += data.size; jsonCount++
                        }
                    }
                    z.putNextEntry(ZipEntry(name))
                    if (data != null) { z.write(data); kept += name to data.size.toLong() } else { pack.copyTo(name, z); kept += name to sz }
                    z.closeEntry()
                    if (i % 20 == 0) rep.progress(0.1f + 0.8f * i / names.size, "Optimizing ${i + 1}/${names.size}")
                }
            }
            try { ZipFile(outFile).close() } catch (e: Exception) { throw ToolException("The optimized pack could not be verified, so it was not kept.", e) }

            val after = outFile.length()
            rep.ok("PNG: $pngCount files smaller, ${size(pngBefore)} to ${size(pngAfter)}")
            rep.ok("JSON: $jsonCount files minified, ${size(jsonBefore)} to ${size(jsonAfter)}")
            if (remove.isNotEmpty()) rep.ok("Removed ${remove.size} files (${size(removedBytes)}), $junkCount of them junk")
            rep.ok("Before ${size(before)}, after ${size(after)}")

            val bigSounds = kept.filter { it.first.lowercase().endsWith(".ogg") && it.second > 400_000 }.sortedByDescending { it.second }.take(10)
            if (bigSounds.isNotEmpty()) rep.warn("${bigSounds.size} large sound files; see report.txt for how to shrink them")
            File(outDir, "report.txt").writeText(report(o, bedrock, before, after, pngCount, pngBefore, pngAfter, jsonCount, jsonBefore, jsonAfter, remove, removedBytes, kept, bigSounds))
            rep.progress(1f, "Done")

            val pct = if (before > 0) (before - after) * 100 / before else 0
            return ToolResult(
                if (after < before) "${size(before)} to ${size(after)} ($pct% smaller)"
                else "${size(before)} to ${size(after)}: this pack was already well optimized",
            )
        }
    }

    private fun report(
        o: OptimizeOptions, bedrock: Boolean, before: Long, after: Long, pngCount: Int, pngB: Long, pngA: Long,
        jsonCount: Int, jsonB: Long, jsonA: Long, removed: Set<String>, removedBytes: Long,
        kept: List<Pair<String, Long>>, bigSounds: List<Pair<String, Long>>,
    ) = buildString {
        appendLine("KyleMCTools - Pack Optimizer report")
        appendLine("Pack type: ${if (bedrock) "Bedrock" else "Java"}")
        appendLine("Before: ${size(before)}   After: ${size(after)}")
        appendLine()
        appendLine("PNG (lossless): $pngCount files improved, ${size(pngB)} -> ${size(pngA)}")
        appendLine("JSON minified: $jsonCount files, ${size(jsonB)} -> ${size(jsonA)}")
        appendLine("Removed: ${removed.size} files, ${size(removedBytes)}")
        appendLine()
        appendLine("Largest files in the result:")
        kept.sortedByDescending { it.second }.take(10).forEach { appendLine("  ${size(it.second)}  ${it.first}") }
        if (bigSounds.isNotEmpty()) {
            appendLine()
            appendLine("Large sounds (this app can't re-encode audio yet). Shrink them on a computer, e.g. with ffmpeg:")
            appendLine("  ffmpeg -i in.ogg -ac 1 -c:a libvorbis -q:a 2 out.ogg")
            bigSounds.forEach { appendLine("  ${size(it.second)}  ${it.first}") }
        }
        if (removed.isNotEmpty()) {
            appendLine()
            appendLine("Removed files:")
            removed.take(300).forEach { appendLine("  $it") }
            if (removed.size > 300) appendLine("  ... and ${removed.size - 300} more")
        }
        appendLine()
        appendLine("Your original pack was not changed.")
    }

    /**
     * Textures in custom namespaces (not "minecraft") that no JSON file mentions.
     * Textures in the minecraft namespace are never touched: vanilla uses them by path without any reference.
     */
    private fun findUnusedTextures(pack: PackArchive, rep: Reporter): Set<String> {
        if (pack.names.any { it.contains("/optifine/") || it.contains("/mcpatcher/") }) {
            rep.warn("OptiFine/MCPatcher files found, so unused-texture removal was skipped (their .properties files reference textures)")
            return emptySet()
        }
        val refs = HashSet<String>()
        val particleRe = Regex("^assets/[^/]+/particles/.+\\.json$")
        fun addStr(s: String, particle: Boolean) {
            val t = s.removeSuffix(".png")
            val ns = if (':' in t) t.substringBefore(':') else "minecraft"
            val path = t.substringAfter(':')
            refs += "$ns:$path"
            if (particle) refs += "$ns:particle/$path"
        }
        fun walk(el: JsonElement, particle: Boolean) {
            when (el) {
                is JsonObject -> el.values.forEach { walk(it, particle) }
                is JsonArray -> el.forEach { walk(it, particle) }
                is JsonPrimitive -> if (el.isString) addStr(el.content, particle)
                else -> {}
            }
        }
        for (name in pack.names) {
            if (!(name.endsWith(".json") || name.endsWith(".mcmeta")) || "/lang/" in name) continue
            val el = pack.readText(name)?.let { Js.parseElement(it) } ?: continue
            walk(el, particleRe.matches(name))
        }
        val texRe = Regex("^assets/([^/]+)/textures/(.+)\\.png$")
        val unused = LinkedHashSet<String>()
        for (name in pack.names) {
            val m = texRe.find(name) ?: continue
            val ns = m.groupValues[1]
            val path = m.groupValues[2]
            if (ns == "minecraft" || path.startsWith("gui/sprites/")) continue
            if ("$ns:$path" !in refs) { unused += name; if (pack.exists("$name.mcmeta")) unused += "$name.mcmeta" }
        }
        rep.info("Found ${unused.size} textures that no file refers to")
        return unused
    }
}
