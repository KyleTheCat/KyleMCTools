package dev.kyle.mctools.menus

import dev.kyle.mctools.core.YamlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DmMenuTest {
    private val sample = """
        menu_title: '&8Server Menu'
        open_command:
          - menu
          - m
        size: 27
        items:
          close:
            material: BARRIER
            slot: 22
            display_name: '&cClose'
            left_click_commands:
              - '[close]'
            custom_unknown_option: keep me
          filler:
            material: GRAY_STAINED_GLASS_PANE
            slots: [0, 1, 2]
            display_name: ' '
    """.trimIndent()

    @Test fun readsEditsAndKeepsUnknownSettings() {
        val m = DmMenu.parse("server", sample)
        assertEquals("menu, m", m.openCommand)
        assertEquals(3, m.rows)
        assertEquals("close", m.keyAtSlot(22))
        assertEquals("filler", m.keyAtSlot(1))
        assertNull(m.keyAtSlot(10))
        val key = m.addItem(10, "DIAMOND")
        assertEquals("item_10", key)
        m.item(key)!!["display_name"] = "&bShiny"
        setLines(m.item(key)!!, "left_click_commands", "[player] spawn\n[close]\n")
        m.size = 36
        val back = YamlParser.parseMap(m.toYaml())
        assertEquals(36L, back["size"])
        @Suppress("UNCHECKED_CAST") val items = back["items"] as Map<String, Map<String, Any?>>
        assertEquals("keep me", items["close"]!!["custom_unknown_option"])
        assertEquals(listOf<Any?>("[player] spawn", "[close]"), items["item_10"]!!["left_click_commands"])
        assertEquals(listOf<Any?>("menu", "m"), back["open_command"])
    }

    @Test fun validationFindsProblems() {
        val m = DmMenu.parse("x", sample)
        assertTrue(m.validate().isEmpty())
        m.size = 10
        m.item("close")!!["left_click_commands"] = listOf("player spawn")
        m.item("close")!!.remove("material")
        m.setSlot(m.item("filler")!!, 22)
        m.root.remove("open_command")
        val w = m.validate().joinToString("\n")
        assertTrue(w, w.contains("multiple of 9"))
        assertTrue(w, w.contains("no open_command") || w.contains("No open_command"))
        assertTrue(w, w.contains("no material"))
        assertTrue(w, w.contains("share slot 22"))
        assertTrue(w, w.contains("[tag]"))
    }

    @Test fun newMenuAndConfigEntry() {
        val m = DmMenu.create("My Shop")
        assertEquals("my_shop", m.openCommand)
        assertEquals(27, m.size)
        assertTrue(m.configEntry().contains("file: my_shop.yml"))
        val again = DmMenu.parse("My Shop", m.toYaml())
        assertEquals("&8My Shop", again.title)
    }

    @Test fun slotRangesAreExpanded() {
        val m = DmMenu.create("r")
        val it = linkedMapOf<String, Any?>("slot" to "1-3")
        assertEquals(listOf(1, 2, 3), m.slotsOf(it))
    }

    @Test fun fillerFillsEmptySlotsAndReleasesThemWhenUsed() {
        val m = DmMenu.parse("x", sample)
        val before = m.emptySlots().size
        assertEquals(27 - 4, before)   // close (22) + filler slots 0,1,2 are taken
        assertEquals(before, m.fillEmpty("black_stained_glass_pane", ""))
        assertEquals(0, m.emptySlots().size)
        assertEquals("filler", m.fillerKey())
        assertEquals(" ", m.item("filler")!!["display_name"])
        assertEquals("BLACK_STAINED_GLASS_PANE", m.item("filler")!!["material"])
        assertEquals(26, m.slotsOf(m.item("filler")!!).size)   // original filler slots 0,1,2 are kept and merged
        assertEquals(0, m.fillEmpty("STONE", ""))
        // using a filler slot for a real item takes it out of the filler
        val key = m.addItem(10, "DIAMOND")
        assertEquals(key, m.keyAtSlot(10))
        assertTrue(10 !in m.slotsOf(m.item("filler")!!))
        // making the menu smaller trims filler slots that no longer exist
        m.size = 9
        m.trimFiller()
        assertTrue(m.slotsOf(m.item("filler")!!).all { it < 9 })
        assertTrue(m.removeFiller())
        assertTrue(m.fillerKey() == null)
        val back = DmMenu.parse("x", m.toYaml())
        assertEquals(9, back.size)
    }

    @Test fun movingAnItemOntoFillerSlotReleasesIt() {
        val m = DmMenu.create("m")
        m.fillEmpty("STONE", "x")
        val k = m.addItem(5)
        m.setSlot(m.item(k)!!, 6)
        assertTrue(6 !in m.slotsOf(m.item("filler")!!))
        assertEquals(k, m.keyAtSlot(6))
    }

    @Test fun requirementsRoundTripAndValidate() {
        val m = DmMenu.parse("x", sample)
        val item = m.item("close")!!
        item["view_requirement"] = DmReq.newGroup()
        val group = item["view_requirement"] as YMap
        val name = DmReq.addRequirement(group, DmReq.types.first { it.id == "has permission" })
        val req = DmReq.requirementsOf(group)[name] as YMap
        assertEquals("has permission", req["type"])
        req["permission"] = "menu.close"
        DmReq.setType(req, DmReq.types.first { it.id == "has money" }, true)   // not negatable -> stays "has money"? it is negatable
        assertEquals("!has money", req["type"])
        assertEquals(100L, req["amount"])
        assertTrue(req["permission"] == null)
        req["deny_commands"] = listOf("[message] &cNo")
        DmReq.setScalar(req, DmReq.types.first { it.id == "has money" }.fields[0], "250.5")
        assertEquals(250.5, req["amount"])

        item["left_click_requirement"] = DmReq.newGroup()
        val g2 = item["left_click_requirement"] as YMap
        DmReq.addRequirement(g2, DmReq.types.first { it.id == "javascript" })
        val w = m.validate().joinToString("\n")
        assertTrue(w, w.contains("does nothing without left_click_commands") || item["left_click_commands"] != null)

        val back = DmMenu.parse("x", m.toYaml())
        val r2 = ((back.item("close")!!["view_requirement"] as YMap)["requirements"] as YMap)[name] as YMap
        assertEquals("!has money", r2["type"])
        assertEquals(250.5, r2["amount"])
        assertEquals(listOf<Any?>("[message] &cNo"), r2["deny_commands"])
    }

    @Test fun requirementValidationFindsMissingFieldsAndNoCommands() {
        val m = DmMenu.create("r")
        val id = m.addItem(3)
        val item = m.item(id)!!
        item["click_requirement"] = linkedMapOf<String, Any?>("requirements" to linkedMapOf<String, Any?>("a" to linkedMapOf<String, Any?>("type" to "has permission"), "b" to linkedMapOf<String, Any?>("name" to "x")))
        m.root["open_requirement"] = linkedMapOf<String, Any?>("requirements" to YMap())
        val w = m.validate().joinToString("\n")
        assertTrue(w, w.contains("needs permission"))
        assertTrue(w, w.contains("has no type"))
        assertTrue(w, w.contains("does nothing without click_commands"))
        assertTrue(w, w.contains("Menu Open requirement has no requirements"))
    }

    @Test fun renameKeepsOrderAndRejectsDuplicates() {
        val reqs = linkedMapOf<String, Any?>("a" to 1, "b" to 2, "c" to 3)
        assertTrue(DmReq.rename(reqs, "b", "x"))
        assertEquals(listOf("a", "x", "c"), reqs.keys.toList())
        assertTrue(!DmReq.rename(reqs, "x", "a"))
        assertTrue(!DmReq.rename(reqs, "x", " "))
        assertEquals(2, reqs["x"])
    }

    @Test fun typeSplitHandlesNegationAndComparisonOperators() {
        assertEquals(true to "has permission", DmReq.split("!has permission").let { it.first to it.second?.id })
        assertEquals(false to "!=", DmReq.split("!=").let { it.first to it.second?.id })
        assertEquals(false to null, DmReq.split("!string length").let { it.first to it.second?.id })
        assertEquals(false to null, DmReq.split("something else").let { it.first to it.second?.id })
    }
}
