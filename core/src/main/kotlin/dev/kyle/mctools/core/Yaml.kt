package dev.kyle.mctools.core

/** Tiny YAML writer for the config files we generate (maps, lists of scalars, scalars). */
object Yaml {
    private val plain = Regex("^[A-Za-z_][A-Za-z0-9_/]*$")
    private val plainKey = Regex("^[A-Za-z0-9_.\\-]+$")
    private val digits = Regex("^[0-9]+$")
    private val reserved = setOf("true", "false", "yes", "no", "on", "off", "null", "y", "n")

    fun dump(root: Map<String, Any?>, header: String? = null): String {
        val sb = StringBuilder()
        header?.lines()?.forEach { sb.append("# ").append(it).append('\n') }
        writeMap(root, 0, sb)
        return sb.toString()
    }

    private fun writeMap(m: Map<*, *>, indent: Int, sb: StringBuilder) {
        val pad = " ".repeat(indent)
        for ((k, v) in m) {
            sb.append(pad).append(key(k.toString())).append(':')
            when (v) {
                is Map<*, *> ->
                    if (v.isEmpty()) sb.append(" {}\n") else { sb.append('\n'); writeMap(v, indent + 2, sb) }
                is List<*> ->
                    if (v.isEmpty()) sb.append(" []\n") else { sb.append('\n'); writeList(v, indent + (if (sameIndentLists) 0 else 2), sb) }
                else -> sb.append(' ').append(scalar(v)).append('\n')
            }
        }
    }

    /** Set to true to write lists at the same indent as their key (the style many plugin docs use). */
    var sameIndentLists = false

    private fun writeList(l: List<*>, indent: Int, sb: StringBuilder) {
        val pad = " ".repeat(indent)
        for (item in l) {
            if (item is Map<*, *> && item.isNotEmpty()) {
                val inner = StringBuilder()
                writeMap(item, indent + 2, inner)
                sb.append(pad).append("- ").append(inner.substring(indent + 2))
            } else if (item is Map<*, *>) sb.append(pad).append("- {}\n")
            else if (item is List<*>) sb.append(pad).append("- ").append(if (item.isEmpty()) "[]" else item.joinToString(", ", "[", "]") { scalar(it) }).append('\n')
            else sb.append(pad).append("- ").append(scalar(item)).append('\n')
        }
    }

    private fun scalar(v: Any?): String = when (v) {
        null -> "null"
        is Boolean, is Number -> v.toString()
        else -> str(v.toString())
    }

    private fun quoted(s: String) =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun str(s: String) = if (plain.matches(s) && s.lowercase() !in reserved) s else quoted(s)

    private fun key(k: String) =
        if (plainKey.matches(k) && !digits.matches(k) && k.lowercase() !in reserved) k else quoted(k)
}
