package dev.kyle.mctools.core

class YamlException(message: String) : Exception(message)

/**
 * Reads the block-style YAML used by Minecraft plugin configs: nested maps, lists (also lists written at the same
 * indent as their key), lists of maps, quoted and plain scalars, simple [a, b] lists and comments.
 * Not supported (reported as friendly errors): | and > text blocks, anchors, tabs for indentation.
 * Maps come back as LinkedHashMap<String, Any?>, lists as List<Any?>, numbers as Long/Double, true/false as Boolean.
 */
object YamlParser {
    private class Line(val indent: Int, val text: String, val no: Int)
    private class Ctx(val lines: MutableList<Line>) { var i = 0 }

    fun parse(src: String): Any? {
        val lines = ArrayList<Line>()
        src.removePrefix("\uFEFF").lines().forEachIndexed { idx, raw ->
            val lead = raw.takeWhile { it == ' ' || it == '\t' }
            if ('\t' in lead) throw YamlException("Line ${idx + 1}: tabs can't be used for indentation in YAML. Use spaces.")
            val stripped = stripComment(raw).trimEnd()
            val t = stripped.trim()
            if (t.isEmpty() || t == "---" || t == "...") return@forEachIndexed
            lines += Line(stripped.length - stripped.trimStart().length, t, idx + 1)
        }
        if (lines.isEmpty()) return LinkedHashMap<String, Any?>()
        val c = Ctx(lines)
        val result = parseBlock(c, lines[0].indent)
        if (c.i < lines.size) throw YamlException("Line ${lines[c.i].no}: unexpected indentation or content.")
        return result
    }

    @Suppress("UNCHECKED_CAST")
    fun parseMap(src: String): LinkedHashMap<String, Any?> =
        parse(src) as? LinkedHashMap<String, Any?> ?: throw YamlException("This file isn't a YAML map (key: value pairs), so it can't be edited here.")

    private fun isSeq(t: String) = t == "-" || t.startsWith("- ")

    private fun stripComment(raw: String): String {
        var inS = false
        var inD = false
        var last: Char? = null
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (inD) {
                if (ch == '\\') i++ else if (ch == '"') inD = false
            } else if (inS) {
                if (ch == '\'') inS = false
            } else {
                if ((ch == '\'' || ch == '"') && (last == null || last in ":-[{,")) { if (ch == '\'') inS = true else inD = true }
                else if (ch == '#' && (i == 0 || raw[i - 1] == ' ' || raw[i - 1] == '\t')) return raw.substring(0, i)
            }
            if (!ch.isWhitespace()) last = ch
            i++
        }
        return raw
    }

    private fun parseBlock(c: Ctx, indent: Int): Any? =
        if (isSeq(c.lines[c.i].text)) parseSeq(c, indent) else parseMapBlock(c, indent)

    private fun parseMapBlock(c: Ctx, indent: Int): LinkedHashMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        while (c.i < c.lines.size) {
            val ln = c.lines[c.i]
            if (ln.indent < indent) break
            if (ln.indent > indent) throw YamlException("Line ${ln.no}: unexpected indentation.")
            if (isSeq(ln.text)) break
            val colon = findColon(ln.text) ?: throw YamlException("Line ${ln.no}: expected 'key: value' but found \"${ln.text.take(40)}\".")
            val key = unquote(ln.text.substring(0, colon).trim())
            val rest = ln.text.substring(colon + 1).trim()
            c.i++
            if (rest.isEmpty()) {
                val next = c.lines.getOrNull(c.i)
                map[key] = when {
                    next == null -> null
                    next.indent > indent -> parseBlock(c, next.indent)
                    next.indent == indent && isSeq(next.text) -> parseSeq(c, indent)
                    else -> null
                }
            } else {
                map[key] = parseValue(rest, ln.no)
            }
        }
        return map
    }

    private fun parseSeq(c: Ctx, indent: Int): List<Any?> {
        val out = ArrayList<Any?>()
        while (c.i < c.lines.size) {
            val ln = c.lines[c.i]
            if (ln.indent > indent && !isSeq(ln.text)) throw YamlException("Line ${ln.no}: unexpected indentation.")
            if (ln.indent != indent || !isSeq(ln.text)) break
            val rest = ln.text.removePrefix("-").trimStart()
            val contentIndent = ln.indent + (ln.text.length - rest.length)
            when {
                rest.isEmpty() -> {
                    c.i++
                    val next = c.lines.getOrNull(c.i)
                    out += if (next != null && next.indent > indent) parseBlock(c, next.indent) else null
                }
                findColon(rest) != null && !(rest.startsWith("[") || rest.startsWith("{")) -> {
                    c.lines[c.i] = Line(contentIndent, rest, ln.no)
                    out += parseMapBlock(c, contentIndent)
                }
                else -> { c.i++; out += parseValue(rest, ln.no) }
            }
        }
        return out
    }

    /** Index of the ':' that ends a key (followed by space or end of text), skipping quoted parts. */
    private fun findColon(t: String): Int? {
        var i = 0
        if (t.startsWith("'") || t.startsWith("\"")) {
            val q = t[0]
            i = 1
            while (i < t.length) {
                if (q == '"' && t[i] == '\\') i += 2
                else if (t[i] == q) { if (q == '\'' && i + 1 < t.length && t[i + 1] == '\'') i += 2 else { i++; break } }
                else i++
            }
        }
        while (i < t.length) {
            if (t[i] == ':' && (i + 1 == t.length || t[i + 1] == ' ')) return i
            i++
        }
        return null
    }

    private fun unquote(s: String): String =
        if (s.length >= 2 && (s[0] == '\'' || s[0] == '"') && s.last() == s[0]) readQuoted(s) else s

    private fun readQuoted(s: String): String {
        val q = s[0]
        val body = s.substring(1, s.length - 1)
        if (q == '\'') return body.replace("''", "'")
        val sb = StringBuilder()
        var i = 0
        while (i < body.length) {
            val ch = body[i]
            if (ch == '\\' && i + 1 < body.length) {
                i++
                when (val e = body[i]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); '0' -> sb.append('\u0000')
                    'u' -> {
                        val hex = body.substring(i + 1, minOf(i + 5, body.length))
                        val code = if (hex.length == 4) hex.toIntOrNull(16) else null
                        if (code != null) { sb.append(code.toChar()); i += 4 } else sb.append('u')
                    }
                    else -> sb.append(e)
                }
            } else sb.append(ch)
            i++
        }
        return sb.toString()
    }

    private fun parseValue(s: String, line: Int): Any? {
        if (s == "|" || s == ">" || s.startsWith("|-") || s.startsWith(">-") || s.startsWith("|+") || s.startsWith(">+")) {
            throw YamlException("Line $line: multi-line text blocks (| or >) aren't supported by the editor.")
        }
        if (s.startsWith("'") || s.startsWith("\"")) {
            if (s.length < 2 || s.last() != s[0]) throw YamlException("Line $line: a quoted value isn't closed.")
            return readQuoted(s)
        }
        if (s.startsWith("[") && s.endsWith("]")) return splitFlow(s.substring(1, s.length - 1)).map { scalar(it.trim()) }
        if (s == "{}") return LinkedHashMap<String, Any?>()
        if (s.startsWith("{") && s.endsWith("}")) {
            val m = LinkedHashMap<String, Any?>()
            for (part in splitFlow(s.substring(1, s.length - 1))) {
                val colon = findColon(part.trim() + " ") ?: throw YamlException("Line $line: couldn't read the { } value.")
                m[unquote(part.trim().substring(0, colon).trim())] = scalar(part.trim().substring(colon + 1).trim())
            }
            return m
        }
        return scalar(s)
    }

    private fun splitFlow(s: String): List<String> {
        if (s.isBlank()) return emptyList()
        val out = ArrayList<String>()
        var depth = 0
        var q: Char? = null
        var start = 0
        for ((i, ch) in s.withIndex()) {
            if (q != null) { if (ch == q) q = null; continue }
            when (ch) {
                '\'', '"' -> q = ch
                '[', '{' -> depth++
                ']', '}' -> depth--
                ',' -> if (depth == 0) { out += s.substring(start, i); start = i + 1 }
            }
        }
        out += s.substring(start)
        return out
    }

    private val intRe = Regex("^-?\\d+$")
    private val floatRe = Regex("^-?\\d+\\.\\d+$")

    private fun scalar(s: String): Any? = when {
        s.isEmpty() || s == "~" || s.equals("null", true) -> null
        s.startsWith("'") || s.startsWith("\"") -> if (s.length >= 2 && s.last() == s[0]) readQuoted(s) else s
        s.equals("true", true) -> true
        s.equals("false", true) -> false
        intRe.matches(s) -> s.toLongOrNull() ?: s
        floatRe.matches(s) -> s.toDoubleOrNull() ?: s
        else -> s
    }
}
