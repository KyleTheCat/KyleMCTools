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
}
