package dev.kyle.mctools.shop

import dev.kyle.mctools.core.Yaml
import dev.kyle.mctools.core.YamlException
import dev.kyle.mctools.core.YamlParser
import kotlin.math.round

typealias YMap = LinkedHashMap<String, Any?>

fun toNum(v: Any?): Double? = when (v) { is Number -> v.toDouble(); is String -> v.trim().toDoubleOrNull(); else -> null }

/** Prices are written without a trailing .0 when they are whole numbers. */
fun numValue(d: Double): Any = if (d == round(d) && kotlin.math.abs(d) < 1e15) d.toLong() else d

/**
 * An EconomyShopGUI shop file (shops/<name>.yml): pages -> items. Settings the editor doesn't know about,
 * including Premium keys, are kept as they were.
 */
class EsShop(var name: String, val root: YMap) {
    companion object {
        fun parse(name: String, text: String): EsShop {
            val root = YamlParser.parseMap(text)
            if (root["pages"] !is Map<*, *>) {
                if (root["items"] is Map<*, *>) throw YamlException("This looks like a rotating shop (items without pages). The editor only handles normal shops with pages.")
                throw YamlException("No \"pages\" section found, so this doesn't look like an EconomyShopGUI shop file (shops/<name>.yml).")
            }
            return EsShop(name, root)
        }

        fun create(name: String): EsShop {
            val shop = EsShop(name, linkedMapOf("pages" to YMap()))
            shop.addPage()
            return shop
        }
    }

    @Suppress("UNCHECKED_CAST")
    val pages: YMap get() = root["pages"] as? YMap ?: YMap().also { root["pages"] = it }

    fun pageNames(): List<String> = pages.keys.toList()

    @Suppress("UNCHECKED_CAST")
    fun page(name: String): YMap = pages[name] as? YMap ?: YMap().also { pages[name] = it }

    fun addPage(): String {
        var n = pages.size + 1
        while (pages.containsKey("page$n")) n++
        val key = "page$n"
        pages[key] = linkedMapOf("gui-rows" to 6L, "title" to "&8$name &7(page $n)", "items" to YMap())
        return key
    }

    fun removePage(name: String) { pages.remove(name) }

    @Suppress("UNCHECKED_CAST")
    fun items(pg: String): YMap = page(pg)["items"] as? YMap ?: YMap().also { page(pg)["items"] = it }

    fun rows(pg: String): Int = (toNum(page(pg)["gui-rows"])?.toInt() ?: 6).coerceIn(1, 6)

    fun setRows(pg: String, rows: Int) { page(pg)["gui-rows"] = rows.toLong() }

    @Suppress("UNCHECKED_CAST")
    fun item(pg: String, key: String): YMap? = items(pg)[key] as? YMap

    fun addItem(pg: String, material: String, buy: Double?, sell: Double?): String {
        val items = items(pg)
        var n = items.size + 1
        while (items.containsKey(n.toString())) n++
        val key = n.toString()
        val m = YMap()
        m["material"] = material.trim().uppercase()
        buy?.let { m["buy"] = numValue(it) }
        sell?.let { m["sell"] = numValue(it) }
        items[key] = m
        return key
    }

    fun removeItem(pg: String, key: String) { items(pg).remove(key) }

    /** Multiplies every positive buy/sell price. Negative prices (meaning "not buyable/sellable") are left alone. Returns how many prices changed. */
    fun scalePrices(onlyPage: String?, factor: Double): Int {
        var changed = 0
        for (p in if (onlyPage == null) pageNames() else listOf(onlyPage)) {
            for ((_, v) in items(p)) {
                val it = v as? YMap ?: continue
                for (k in listOf("buy", "sell")) {
                    val d = toNum(it[k]) ?: continue
                    if (d <= 0.0) continue
                    it[k] = numValue(round(d * factor * 100.0) / 100.0)
                    changed++
                }
            }
        }
        return changed
    }

    fun validate(): List<String> {
        val w = ArrayList<String>()
        for (p in pageNames()) {
            val size = rows(p) * 9
            for ((key, v) in items(p)) {
                val it = v as? YMap
                if (it == null) { w += "$p / $key is empty."; continue }
                val mat = it["material"]?.toString().orEmpty()
                if (mat.isBlank() && it["action"] == null) w += "$p / $key has no material."
                val slot = it["slot"]
                val first = (slot as? Number)?.toInt() ?: slot?.toString()?.substringBefore('-')?.substringBefore(',')?.trim()?.toIntOrNull()
                if (first != null && first >= size) w += "$p / $key: slot $first is outside the page (0 to ${size - 1})."
                val buy = toNum(it["buy"]); val sell = toNum(it["sell"])
                if (it["buy"] != null && buy == null) w += "$p / $key: buy \"${it["buy"]}\" isn't a number."
                if (it["sell"] != null && sell == null) w += "$p / $key: sell \"${it["sell"]}\" isn't a number."
                if (buy != null && sell != null && buy > 0 && sell > buy) w += "$p / $key: sells for more ($sell) than it costs ($buy). Players could farm money."
            }
        }
        return w.distinct()
    }

    fun toYaml(): String = Yaml.dump(root, "Edited with KyleMCTools. Comments from the original file are not kept.")
}

/** sections/<name>.yml: the button for this shop in the main /shop menu. Only the common settings are edited. */
class EsSection(val root: YMap) {
    companion object {
        fun parse(text: String): EsSection = EsSection(YamlParser.parseMap(text))

        fun create(name: String): EsSection = EsSection(
            linkedMapOf(
                "enable" to true,
                "slot" to 0L,
                "title" to "&8$name",
                "fill-item" to linkedMapOf<String, Any?>("material" to "GRAY_STAINED_GLASS_PANE", "name" to " "),
                "item" to linkedMapOf<String, Any?>("material" to "CHEST", "name" to "&aClick to enter the $name shop"),
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun sub(key: String): YMap = root[key] as? YMap ?: YMap().also { root[key] = it }

    var enabled: Boolean
        get() = root["enable"] as? Boolean ?: true
        set(v) { root["enable"] = v }
    var slot: Int
        get() = toNum(root["slot"])?.toInt() ?: 0
        set(v) { root["slot"] = v.toLong() }
    var title: String
        get() = root["title"]?.toString() ?: ""
        set(v) { root["title"] = v }

    fun validate(): List<String> {
        val w = ArrayList<String>()
        if (slot !in 0..44) w += "Section slot must be 0 to 44 (now $slot)."
        if ((sub("item")["material"]?.toString() ?: "").isBlank()) w += "The section item has no material."
        return w
    }

    fun toYaml(): String = Yaml.dump(root, "Edited with KyleMCTools. Comments from the original file are not kept.")
}
