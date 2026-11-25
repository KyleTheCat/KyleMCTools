package dev.kyle.mctools.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class McVersion(val label: String, val format: Int)

/** Resource pack formats from the Minecraft wiki / community tables (1.13 and newer). */
object McVersions {
    val all = listOf(
        McVersion("1.13 - 1.14.4", 4), McVersion("1.15 - 1.16.1", 5), McVersion("1.16.2 - 1.16.5", 6),
        McVersion("1.17", 7), McVersion("1.18", 8), McVersion("1.19 - 1.19.2", 9), McVersion("1.19.3", 12),
        McVersion("1.19.4", 13), McVersion("1.20 - 1.20.1", 15), McVersion("1.20.2", 18), McVersion("1.20.3 - 1.20.4", 22),
        McVersion("1.20.5 - 1.20.6", 32), McVersion("1.21 - 1.21.1", 34), McVersion("1.21.2 - 1.21.3", 42),
        McVersion("1.21.4", 46), McVersion("1.21.5", 55), McVersion("1.21.6", 63), McVersion("1.21.7 - 1.21.8", 64),
        McVersion("1.21.9 - 1.21.10", 69), McVersion("1.21.11", 75), McVersion("26.1", 84), McVersion("26.2", 88),
    )

    /** Formats from this number on need min_format/max_format instead of pack_format. */
    const val RANGE_FORMAT_FROM = 65

    fun label(format: Int): String = all.firstOrNull { it.format == format }?.label
        ?: all.lastOrNull { it.format <= format }?.let { "${it.label} or newer (format $format)" } ?: "format $format"

    /** Reads the format from a parsed pack.mcmeta: pack_format, or the major part of min_format. */
    fun sourceFormat(mcmeta: JsonObject?): Int? {
        val pack = mcmeta?.obj("pack") ?: return null
        pack["pack_format"].num()?.let { return it.toInt() }
        val min = pack["min_format"]
        min.num()?.let { return it.toInt() }
        return (min as? JsonArray)?.firstOrNull().num()?.toInt()
    }
}
