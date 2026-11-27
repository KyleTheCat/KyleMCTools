package dev.kyle.mctools.menus

import dev.kyle.mctools.core.Yaml
import dev.kyle.mctools.core.YamlParser

typealias YMap = LinkedHashMap<String, Any?>

/**
 * A DeluxeMenus menu file held as plain maps, so every setting the editor doesn't know about is kept as it was.
 * (Comments in the original file are not kept; the original file itself is never changed.)
 */
class DmMenu(var name: String, val root: YMap) {
    companion object {
        fun parse(name: String, text: String): DmMenu {
            val root = YamlParser.parseMap(text)
            return DmMenu(name, root).also { it.items }
        }

        fun create(name: String): DmMenu = DmMenu(
            name,
            linkedMapOf(
                "menu_title" to "&8$name",
                "open_command" to name.lowercase().replace(' ', '_'),
                "size" to 27L,
                "items" to YMap(),
            ),
        )

        /** Click command prefixes DeluxeMenus understands as the leading [tag]. */
        val commandKeys = listOf("left_click_commands", "right_click_commands", "shift_left_click_commands", "shift_right_click_commands", "middle_click_commands", "click_commands")
    }

    var title: String
        get() = root["menu_title"]?.toString() ?: ""
        set(v) { root["menu_title"] = v }

    var size: Int
        get() = (root["size"] as? Number)?.toInt() ?: 27
        set(v) { root["size"] = v.toLong() }

    val rows: Int get() = (size / 9).coerceIn(1, 6)

    /** open_command may be a string or a list; the editor shows it as comma separated text. */
    var openCommand: String
        get() = when (val v = root["open_command"]) { is List<*> -> v.joinToString(", "); null -> ""; else -> v.toString() }
        set(v) {
            val parts = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) root.remove("open_command") else root["open_command"] = if (parts.size == 1) parts[0] else parts
        }

    @Suppress("UNCHECKED_CAST")
    val items: YMap
        get() {
            val v = root["items"]
            if (v is LinkedHashMap<*, *>) return v as YMap
            return YMap().also { root["items"] = it }
        }

    @Suppress("UNCHECKED_CAST")
    fun item(key: String): YMap? = items[key] as? YMap

    fun slotsOf(item: YMap): List<Int> {
        val out = ArrayList<Int>()
        fun add(v: Any?) {
            when (v) {
                is Number -> out += v.toInt()
                is String -> {
                    val parts = v.split('-')
                    val a = parts.getOrNull(0)?.trim()?.toIntOrNull()
                    val b = parts.getOrNull(1)?.trim()?.toIntOrNull()
                    if (parts.size == 2 && a != null && b != null && b >= a && b - a < 100) for (s in a..b) out += s
                    else v.trim().toIntOrNull()?.let { out += it }
                }
                is List<*> -> v.forEach { add(it) }
            }
        }
        add(item["slot"]); add(item["slots"])
        return out
    }

    /** The first item (by file order) that occupies [slot]. */
    fun keyAtSlot(slot: Int): String? = items.entries.firstOrNull { (it.value as? Map<*, *>)?.let { m -> slotsOf(m as YMap).contains(slot) } == true }?.key

    fun addItem(slot: Int, material: String = "STONE"): String {
        releaseFillerSlot(slot)
        var key = "item_$slot"
        var n = 2
        while (items.containsKey(key)) { key = "item_${slot}_$n"; n++ }
        items[key] = linkedMapOf<String, Any?>("material" to material, "slot" to slot.toLong(), "display_name" to "&f${material.lowercase().replace('_', ' ')}")
        return key
    }

    fun removeItem(key: String) { items.remove(key) }

    fun setSlot(item: YMap, slot: Int) {
        if (!isFillerItem(item)) releaseFillerSlot(slot)
        item.remove("slots"); item["slot"] = slot.toLong()
    }

    // ---- filler ----
    fun isFillerKey(k: String) = k.startsWith("filler", ignoreCase = true)
    private fun isFillerItem(item: YMap) = items.entries.any { it.value === item && isFillerKey(it.key) }
    fun fillerKey(): String? = items.keys.firstOrNull { isFillerKey(it) }
    fun emptySlots(): List<Int> = (0 until size).filter { keyAtSlot(it) == null }

    /** One filler item (with a slots list) on every empty slot. Returns how many slots were filled. */
    fun fillEmpty(material: String, name: String): Int {
        val empty = emptySlots()
        if (empty.isEmpty()) return 0
        val key = fillerKey() ?: "filler"
        val fi = item(key) ?: YMap().also { items[key] = it }
        val all = (slotsOf(fi) + empty).distinct().sorted()
        fi["material"] = material.trim().uppercase().ifEmpty { "GRAY_STAINED_GLASS_PANE" }
        fi["display_name"] = if (name.isEmpty()) " " else name
        fi.remove("slot")
        fi["slots"] = all.map { it.toLong() }
        return empty.size
    }

    /** Takes one slot away from the filler so a real item can go there. */
    fun releaseFillerSlot(slot: Int) {
        val key = fillerKey() ?: return
        val fi = item(key) ?: return
        val rest = slotsOf(fi).filter { it != slot }
        if (rest.size == slotsOf(fi).size) return
        if (rest.isEmpty()) items.remove(key) else { fi.remove("slot"); fi["slots"] = rest.map { it.toLong() } }
    }

    fun removeFiller(): Boolean {
        val key = fillerKey() ?: return false
        items.remove(key)
        return true
    }

    /** Drops filler slots that are outside the menu after it was made smaller. */
    fun trimFiller() {
        val key = fillerKey() ?: return
        val fi = item(key) ?: return
        val keep = slotsOf(fi).filter { it in 0 until size }
        if (keep.isEmpty()) items.remove(key) else { fi.remove("slot"); fi["slots"] = keep.map { it.toLong() } }
    }

    fun validate(): List<String> {
        val w = ArrayList<String>()
        if (size % 9 != 0 || size !in 9..54) w += "size must be a multiple of 9 between 9 and 54 (now $size)."
        if (!root.containsKey("open_command")) w += "No open_command: the menu can only be opened by other menus or commands."
        (root["open_requirement"] as? YMap)?.let { w += DmReq.validateGroup("Menu", "open_requirement", it, true) }
        val used = HashMap<Int, String>()
        for ((key, v) in items) {
            val it = v as? YMap
            if (it == null) { w += "Item $key is empty."; continue }
            if ((it["material"]?.toString() ?: "").isBlank()) w += "Item $key has no material."
            val slots = slotsOf(it)
            if (slots.isEmpty()) w += "Item $key has no slot."
            for (s in slots) {
                if (s < 0 || s >= size) w += "Item $key uses slot $s, outside this menu (0 to ${size - 1})."
                val other = used[s]
                if (other != null && other != key && it["priority"] == null && (items[other] as? YMap)?.get("priority") == null) w += "Items $other and $key share slot $s. Give them a priority or view requirements."
                used.putIfAbsent(s, key)
            }
            for (gk in DmReq.itemGroups) {
                val group = it[gk] as? YMap ?: continue
                val ck = DmReq.commandsKeyFor(gk)
                val hasCommands = ck == null || ((it[ck] as? List<*>)?.isNotEmpty() == true)
                w += DmReq.validateGroup("Item $key", gk, group, hasCommands)
            }
            for (ck in commandKeys) for (cmd in (it[ck] as? List<*>) ?: emptyList<Any?>()) {
                val s = cmd?.toString()?.trim() ?: continue
                if (!s.startsWith("[")) w += "Item $key: command \"${s.take(30)}\" doesn't start with a [tag] like [player], [console] or [close]."
            }
        }
        return w.distinct()
    }

    fun toYaml(): String = Yaml.dump(root, "Edited with KyleMCTools. Comments from the original file are not kept.")

    fun configEntry(): String {
        val id = name.lowercase().replace(Regex("[^a-z0-9_]+"), "_")
        return "# Add this under gui_menus: in DeluxeMenus/config.yml, and put $id.yml in DeluxeMenus/gui_menus/\ngui_menus:\n  $id:\n    file: $id.yml\n"
    }
}

fun listOfLines(v: Any?): String = (v as? List<*>)?.joinToString("\n") { it?.toString() ?: "" } ?: (v?.toString() ?: "")

fun setLines(map: YMap, key: String, text: String) {
    val lines = text.lines().map { it.trimEnd() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) map.remove(key) else map[key] = lines
}
