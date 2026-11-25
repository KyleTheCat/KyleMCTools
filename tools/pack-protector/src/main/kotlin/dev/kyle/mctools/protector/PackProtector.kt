package dev.kyle.mctools.protector

import dev.kyle.mctools.core.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ProtectOptions(
    val projectName: String,
    val minify: Boolean = true,
    val decoys: Boolean = true,
    /** Java only. Renames textures, models and sounds in custom namespaces. Breaks plugins that refer to them by file name. */
    val rename: Boolean = false,
)

/**
 * Realistic protection only. The game client must be able to read the pack, and Minecraft Java keeps downloaded
 * server packs on the player's disk, so anything that loads can be extracted by a determined person.
 * This raises the effort for casual copying; it does not stop a determined one.
 */
object PackProtector {
    private val tinyPng = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")
    private val reserved = listOf("aux", "con", "nul", "prn", "com1", "com2", "com3", "lpt1", "lpt2", "lpt3")

    fun run(input: File, outDir: File, o: ProtectOptions, rep: Reporter): ToolResult {
        outDir.mkdirs()
        val slug = Ids.slug(o.projectName)
        PackArchive(input, allowAnyZip = true).use { pack ->
            val bedrock = pack.isBedrock
            rep.info(if (bedrock) "Bedrock pack detected" else "Java pack detected")
            val doRename = o.rename && !bedrock
            if (o.rename && bedrock) rep.warn("Renaming is Java only, so it was skipped")

            rep.progress(0.05f, "Planning...")
            val seed = UUID.randomUUID().toString()
            val renames = if (doRename) planRename(pack, seed) else emptyMap()
            if (doRename) rep.info("Renaming ${renames.size} assets")

            val outFile = File(outDir, "${slug}_protected.${if (bedrock) "mcpack" else "zip"}")
            val written = HashSet<String>()
            var jsonRewritten = 0
            val names = pack.names.filter { !isJunk(it) }
            ZipOutputStream(BufferedOutputStream(outFile.outputStream())).use { z ->
                z.setLevel(9)
                for ((i, name) in names.withIndex()) {
                    val dest = if (doRename) renamePath(name, renames) else name
                    if (!written.add(dest)) { rep.warn("Skipped duplicate entry $dest"); continue }
                    var data: ByteArray? = null
                    val lower = name.lowercase()
                    val isJson = lower.endsWith(".json") || lower.endsWith(".mcmeta")
                    if (isJson && "/lang/" !in name && (o.minify || doRename) && pack.size(name) in 1L..16_777_216L) {
                        val el = pack.readText(name)?.let { Js.parseElement(it) }
                        if (el == null) {
                            rep.warn("Left $name unchanged (not valid JSON)")
                        } else {
                            val soundsNs = Regex("^assets/([^/]+)/sounds\\.json$").find(name)?.groupValues?.get(1)
                            val out = if (doRename) rewrite(el, renames, soundsNs) else el
                            data = (if (o.minify) Js.compact(out) else Js.write(out)).toByteArray()
                            jsonRewritten++
                        }
                    }
                    z.putNextEntry(ZipEntry(dest))
                    if (data != null) z.write(data) else pack.copyTo(name, z)
                    z.closeEntry()
                    if (i % 20 == 0) rep.progress(0.1f + 0.75f * i / names.size, "Protecting ${i + 1}/${names.size}")
                }
                if (o.decoys) addDecoys(z, bedrock, written)
            }
            if (o.decoys) rep.ok("Added decoy files with names Windows can't extract")
            if (o.minify || doRename) rep.ok("Rewrote $jsonRewritten JSON files")

            // Verify the result: every entry must open, otherwise the game could fail to load it.
            try {
                ZipFile(outFile).use { zf ->
                    val en = zf.entries()
                    while (en.hasMoreElements()) zf.getInputStream(en.nextElement()).use { it.readBytes() }
                }
            } catch (e: Exception) {
                outFile.delete()
                throw ToolException("The protected pack failed its self-check, so it was not kept. Nothing was changed.", e)
            }
            rep.ok("Self-check passed: every file in ${outFile.name} reads correctly")

            if (doRename) {
                File(outDir, "rename_map.json").writeText(Js.write(buildJsonObject { renames.forEach { (k, v) -> put(k, v) } }))
            }
            File(outDir, "PROTECTION_NOTES.txt").writeText(notes(o, bedrock, doRename, renames.size))
            rep.progress(1f, "Done")
            return ToolResult(
                "Protected pack written" + (if (doRename) ", ${renames.size} assets renamed" else "") + ". Read PROTECTION_NOTES.txt for what this does and doesn't do.",
            )
        }
    }

    private fun isJunk(path: String): Boolean {
        val n = path.lowercase().substringAfterLast('/')
        return n == ".ds_store" || n == "thumbs.db" || n == "desktop.ini"
    }

    private val assetRe = Regex("^assets/([^/]+)/(textures|models|sounds)/(.+)\\.(png|json|ogg)$")
    private val mcmetaRe = Regex("^assets/([^/]+)/textures/(.+)\\.png\\.mcmeta$")

    /** "ns:path" -> "ns:newpath" for textures, models and sounds in custom namespaces. Same name across types. */
    private fun planRename(pack: PackArchive, seed: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        val usedNew = HashSet<String>()
        val md = MessageDigest.getInstance("SHA-1")
        for (name in pack.names) {
            val m = assetRe.find(name) ?: continue
            val (ns, type, path, ext) = m.destructured
            if (ns == "minecraft") continue
            if ((type == "textures" && ext != "png") || (type == "models" && ext != "json") || (type == "sounds" && ext != "ogg")) continue
            if (type == "textures" && path.startsWith("gui/sprites/")) continue
            val key = "$ns:$path"
            if (key in map) continue
            val dir = path.substringBeforeLast('/', "")
            var attempt = 0
            var newKey: String
            do {
                val hash = md.digest("$seed$key$attempt".toByteArray()).joinToString("") { "%02x".format(it) }.take(10)
                newKey = "$ns:" + (if (dir.isEmpty()) "x$hash" else "$dir/x$hash")
                attempt++
            } while (!usedNew.add(newKey))
            map[key] = newKey
        }
        return map
    }

    private fun renamePath(name: String, renames: Map<String, String>): String {
        assetRe.find(name)?.let { m ->
            val (ns, type, path, ext) = m.destructured
            val newKey = renames["$ns:$path"] ?: return name
            return "assets/$ns/$type/${newKey.substringAfter(':')}.$ext"
        }
        mcmetaRe.find(name)?.let { m ->
            val (ns, path) = m.destructured
            val newKey = renames["$ns:$path"] ?: return name
            return "assets/$ns/textures/${newKey.substringAfter(':')}.png.mcmeta"
        }
        return name
    }

    private fun rewrite(el: JsonElement, renames: Map<String, String>, soundsNs: String?): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.mapValues { rewrite(it.value, renames, soundsNs) })
        is JsonArray -> JsonArray(el.map { rewrite(it, renames, soundsNs) })
        is JsonPrimitive -> {
            if (!el.isString) el else {
                val s = el.content
                val ext = if (s.endsWith(".png")) ".png" else ""
                val base = s.removeSuffix(ext)
                val key = if (':' in base) base else (if (soundsNs != null) "$soundsNs:$base" else "minecraft:$base")
                val newKey = renames[key]
                if (newKey == null) el else JsonPrimitive((if (':' in base) newKey else newKey.substringAfter(':')) + ext)
            }
        }
        else -> el
    }

    private fun addDecoys(z: ZipOutputStream, bedrock: Boolean, written: MutableSet<String>) {
        val dir = if (bedrock) "textures/kmt_decoy" else "assets/kmt_decoy/textures/item"
        fun put(path: String, bytes: ByteArray) {
            if (written.add(path)) { z.putNextEntry(ZipEntry(path)); z.write(bytes); z.closeEntry() }
        }
        for (n in reserved) put("$dir/$n.png", tinyPng)
        put("$dir/${"a".repeat(120)}.png", tinyPng)
        if (!bedrock) for (n in reserved.take(4)) put("assets/kmt_decoy/models/item/$n.json", "{\"parent\":\"item/generated\"}".toByteArray())
    }

    private fun notes(o: ProtectOptions, bedrock: Boolean, renamed: Boolean, count: Int) = buildString {
        appendLine("KyleMCTools - Pack Protector notes")
        appendLine()
        appendLine("What was done")
        if (o.minify) appendLine("- JSON minified: no whitespace or comments, harder to read at a glance.")
        if (renamed) appendLine("- $count textures/models/sounds renamed to random names (map in rename_map.json - keep it private and do NOT upload it).")
        if (o.decoys) appendLine("- Decoy files named like Windows reserved names (aux, con, nul...). Many unzip tools on Windows fail on these.")
        appendLine("- Every file was re-read after writing, so the pack still loads.")
        appendLine()
        appendLine("What this can NOT do (be realistic)")
        appendLine("- The game client has to download and read your pack. Whatever the client can read, a determined person can extract.")
        appendLine("- Minecraft Java stores downloaded server packs on the player's disk; they can be copied from there.")
        if (bedrock) {
            appendLine("- Bedrock encryption exists, but the Bedrock protocol sends the key to every connecting client, and the official Geyser docs do not cover encrypted packs. It was not added here.")
        }
        appendLine("- Obfuscation and decoys stop casual copying and quick re-uploads, not a person who is motivated.")
        appendLine()
        appendLine("Practical advice")
        appendLine("- Host the pack at a URL that is not public and not guessable. Change it when it leaks.")
        appendLine("- Keep an unprotected master copy; edit that and re-protect.")
        appendLine("- Don't put anything secret in a pack.")
        if (renamed) appendLine("- Renaming breaks plugins/configs that refer to texture or model file names. Use it on finished packs only.")
    }
}
