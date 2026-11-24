package dev.kyle.mctools.core

/** Tiny YAML writer for the config files we generate (maps, lists of scalars, scalars). */
object Yaml {
    private val plain = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
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
                    if (v.isEmpty()) sb.append(" []\n") else {
                        sb.append('\n')
                        for (item in v) sb.append(" ".repeat(indent + 2)).append("- ").append(scalar(item)).append('\n')
                    }
                else -> sb.append(' ').append(scalar(v)).append('\n')
            }
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
