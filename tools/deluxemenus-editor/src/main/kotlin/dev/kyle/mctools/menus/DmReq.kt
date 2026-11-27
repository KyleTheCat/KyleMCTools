package dev.kyle.mctools.menus

class ReqField(val key: String, val label: String, val numeric: Boolean = false, val default: Any? = null, val required: Boolean = true)

class ReqType(val id: String, val label: String, val negatable: Boolean, val fields: List<ReqField>)

/** DeluxeMenus requirements: groups (view_requirement, click_requirement...) that hold named requirements with a type. */
object DmReq {
    val itemGroups = listOf("view_requirement", "left_click_requirement", "right_click_requirement", "shift_left_click_requirement", "shift_right_click_requirement", "click_requirement")
    val menuGroups = listOf("open_requirement")

    fun groupLabel(key: String) = when (key) {
        "open_requirement" -> "Open"
        "view_requirement" -> "View"
        "left_click_requirement" -> "Left click"
        "right_click_requirement" -> "Right click"
        "shift_left_click_requirement" -> "Shift + left click"
        "shift_right_click_requirement" -> "Shift + right click"
        "click_requirement" -> "Any click"
        else -> key
    }

    /** Click requirements do nothing unless the item also has the matching click commands. */
    fun commandsKeyFor(group: String): String? = when (group) {
        "left_click_requirement" -> "left_click_commands"
        "right_click_requirement" -> "right_click_commands"
        "shift_left_click_requirement" -> "shift_left_click_commands"
        "shift_right_click_requirement" -> "shift_right_click_commands"
        "click_requirement" -> "click_commands"
        else -> null
    }

    private val input = ReqField("input", "Input", default = "%player_name%")
    private val inOut = listOf(input, ReqField("output", "Output", default = "Steve"))
    private val compare = listOf(ReqField("input", "Input", default = "%vault_eco_balance%"), ReqField("output", "Output", numeric = true, default = 100L))

    val types: List<ReqType> = listOf(
        ReqType("has permission", "Has permission", true, listOf(ReqField("permission", "Permission", default = "my.permission"))),
        ReqType("has money", "Has money", true, listOf(ReqField("amount", "Amount", numeric = true, default = 100L))),
        ReqType("has item", "Has item", true, listOf(ReqField("material", "Material", default = "DIAMOND"), ReqField("amount", "Amount", numeric = true, default = 1L))),
        ReqType("string equals", "String equals", true, inOut),
        ReqType("string equals ignorecase", "String equals (any case)", true, inOut),
        ReqType("string contains", "String contains", true, inOut),
        ReqType("regex matches", "Regex matches", true, listOf(input, ReqField("regex", "Regex", default = "^[a-zA-Z]+$"))),
        ReqType("string length", "String length", false, listOf(input, ReqField("min", "Min", numeric = true, default = 1L), ReqField("max", "Max", numeric = true, required = false))),
        ReqType(">=", "Number >=", false, compare),
        ReqType(">", "Number >", false, compare),
        ReqType("<=", "Number <=", false, compare),
        ReqType("<", "Number <", false, compare),
        ReqType("==", "Number ==", false, compare),
        ReqType("!=", "Number !=", false, compare),
        ReqType("javascript", "JavaScript", false, listOf(ReqField("expression", "Expression", default = "%player_level% >= 5"))),
        ReqType("is object", "Is object", false, listOf(input, ReqField("object", "Object (int, double, uuid, player)", default = "int"))),
    )

    private val keepOnTypeChange = listOf("deny_commands", "success_commands", "optional")

    /** (negated, type) for a type string such as "!has permission". Unknown types give a null type. */
    fun split(typeStr: String): Pair<Boolean, ReqType?> {
        types.firstOrNull { it.id == typeStr }?.let { return false to it }
        if (typeStr.startsWith("!")) types.firstOrNull { it.id == typeStr.removePrefix("!") && it.negatable }?.let { return true to it }
        return false to null
    }

    fun typeString(t: ReqType, negate: Boolean) = if (negate && t.negatable) "!${t.id}" else t.id

    fun newGroup(): YMap = linkedMapOf("requirements" to YMap())

    @Suppress("UNCHECKED_CAST")
    fun requirementsOf(group: YMap): YMap = group["requirements"] as? YMap ?: YMap().also { group["requirements"] = it }

    /** Switches a requirement to another type: its old type settings are dropped and the new type's defaults added. */
    fun setType(req: YMap, t: ReqType, negate: Boolean) {
        val keep = LinkedHashMap<String, Any?>()
        for (k in keepOnTypeChange) req[k]?.let { keep[k] = it }
        req.clear()
        req["type"] = typeString(t, negate)
        for (f in t.fields) if (f.default != null) req[f.key] = f.default
        req.putAll(keep)
    }

    fun addRequirement(group: YMap, t: ReqType): String {
        val reqs = requirementsOf(group)
        var n = reqs.size + 1
        while (reqs.containsKey("requirement_$n")) n++
        val name = "requirement_$n"
        val req = YMap()
        setType(req, t, false)
        reqs[name] = req
        return name
    }

    /** Renames a requirement and keeps its position. Returns false if the new name is empty or already used. */
    fun rename(reqs: YMap, old: String, new: String): Boolean {
        if (new.isBlank() || new == old || reqs.containsKey(new)) return false
        val copy = LinkedHashMap(reqs)
        reqs.clear()
        for ((k, v) in copy) reqs[if (k == old) new else k] = v
        return true
    }

    fun setScalar(req: YMap, f: ReqField, text: String) {
        val t = text.trim()
        if (t.isEmpty()) { req.remove(f.key); return }
        req[f.key] = if (f.numeric) (t.toLongOrNull() ?: t.toDoubleOrNull() ?: t) else text
    }

    /** Warnings for one group. [hasCommands] is whether the item has the click commands this group needs. */
    fun validateGroup(owner: String, groupKey: String, group: YMap, hasCommands: Boolean): List<String> {
        val w = ArrayList<String>()
        val label = "$owner ${groupLabel(groupKey)} requirement"
        val reqs = group["requirements"] as? Map<*, *>
        if (reqs == null || reqs.isEmpty()) { w += "$label has no requirements."; return w }
        for ((name, v) in reqs) {
            val r = v as? Map<*, *>
            if (r == null) { w += "$label \"$name\" is empty."; continue }
            val typeStr = r["type"]?.toString().orEmpty()
            if (typeStr.isBlank()) { w += "$label \"$name\" has no type."; continue }
            val t = split(typeStr).second ?: continue   // unknown types may be valid aliases; leave them alone
            for (f in t.fields) if (f.required && (r[f.key] == null || r[f.key].toString().isBlank())) w += "$label \"$name\" needs ${f.label.lowercase()}."
        }
        if (commandsKeyFor(groupKey) != null && !hasCommands) w += "$label does nothing without ${commandsKeyFor(groupKey)} on the same item."
        return w
    }
}
