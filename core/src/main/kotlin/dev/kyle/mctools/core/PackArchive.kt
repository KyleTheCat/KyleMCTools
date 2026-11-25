package dev.kyle.mctools.core

import kotlinx.serialization.json.JsonObject
import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Read-only view of a resource pack zip. Never writes to the zip.
 * Handles packs wrapped in a root folder, and ignores unsafe entries (zip-slip, __MACOSX).
 */
class PackArchive(file: File, allowAnyZip: Boolean = false) : Closeable {
    private val zip = ZipFile(file)
    private val entries = HashMap<String, ZipEntry>()
    val names: List<String>

    init {
        val raw = ArrayList<Pair<String, ZipEntry>>()
        val en = zip.entries()
        while (en.hasMoreElements()) {
            val e = en.nextElement()
            if (e.isDirectory) continue
            val n = e.name.replace('\\', '/')
            if (n.startsWith("/") || n.split('/').any { it == ".." }) continue
            if (n.startsWith("__MACOSX/") || n.endsWith(".DS_Store")) continue
            raw.add(n to e)
        }
        val allNames = raw.map { it.first }
        val mcmeta = allNames.filter { it == "pack.mcmeta" || it.endsWith("/pack.mcmeta") }.minByOrNull { it.length }
        val prefix: String? = when {
            mcmeta != null -> mcmeta.removeSuffix("pack.mcmeta")
            else -> {
                val manifest = if (allowAnyZip) allNames.filter { it == "manifest.json" || it.endsWith("/manifest.json") }.minByOrNull { it.length } else null
                val assets = allNames.firstOrNull { it.startsWith("assets/") || it.contains("/assets/") }?.let {
                    if (it.startsWith("assets/")) "" else it.substring(0, it.indexOf("/assets/") + 1)
                }
                when {
                    manifest != null -> manifest.removeSuffix("manifest.json")
                    assets != null -> assets
                    allowAnyZip -> ""
                    else -> null
                }
            }
        }
        if (prefix == null) {
            zip.close()
            throw ToolException("This zip doesn't look like a resource pack: no pack.mcmeta or assets folder found.")
        }
        val list = ArrayList<String>()
        for ((n, e) in raw) {
            if (!n.startsWith(prefix)) continue
            val rel = n.substring(prefix.length)
            if (rel.isEmpty()) continue
            entries[rel] = e
            list.add(rel)
        }
        names = list
    }

    fun exists(path: String) = entries.containsKey(path)

    /** Uncompressed size of an entry, or 0 if missing/unknown. */
    fun size(path: String): Long = entries[path]?.size?.coerceAtLeast(0) ?: 0L

    /** Bedrock packs have manifest.json and no pack.mcmeta. */
    val isBedrock: Boolean get() = exists("manifest.json") && !exists("pack.mcmeta")

    fun list(prefix: String): List<String> = names.filter { it.startsWith(prefix) }

    fun readBytes(path: String, maxBytes: Long = 64L * 1024 * 1024): ByteArray? {
        val e = entries[path] ?: return null
        if (e.size > maxBytes) throw ToolException("The file $path is too large to process (${e.size / 1024 / 1024} MB).")
        return zip.getInputStream(e).use { it.readBytes() }
    }

    fun readText(path: String): String? = readBytes(path)?.toString(Charsets.UTF_8)?.removePrefix("\uFEFF")

    fun readJson(path: String): JsonObject? = readText(path)?.let { Js.parseObject(it) }

    fun copyTo(path: String, out: OutputStream) {
        val e = entries[path] ?: return
        zip.getInputStream(e).use { it.copyTo(out) }
    }

    override fun close() = zip.close()
}
