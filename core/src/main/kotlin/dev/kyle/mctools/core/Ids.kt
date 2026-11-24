package dev.kyle.mctools.core

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Ids {
    fun slug(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifEmpty { "item" }

    fun pretty(id: String): String =
        id.split('_', '-', '/').filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }

    fun unique(base: String, used: MutableSet<String>): String {
        var candidate = base
        var n = 2
        while (!used.add(candidate)) { candidate = "${base}_$n"; n++ }
        return candidate
    }

    /** "Diamond Sword" / "minecraft:diamond_sword" -> "minecraft:diamond_sword" */
    fun material(s: String): String {
        val t = s.trim().lowercase().replace(' ', '_')
        return if (':' in t) t else "minecraft:$t"
    }

    /** "ns:path" with the namespace always present. */
    fun ref(s: String): String = if (':' in s) s else "minecraft:$s"

    fun timestamp(now: LocalDateTime = LocalDateTime.now()): String =
        now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    fun folderName(project: String, ts: String = timestamp()): String = "${slug(project)}_$ts"
}
