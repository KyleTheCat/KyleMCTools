package dev.kyle.mctools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class YamlParserTest {
    @Suppress("UNCHECKED_CAST")
    private fun m(x: Any?) = x as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun l(x: Any?) = x as List<Any?>

    @Test fun parsesEconomyShopGuiStyleFile() {
        val y = YamlParser.parseMap(
            """
            # a comment
            pages:
              page1:
                gui-rows: 6
                title: "&dCustom title"  # trailing comment
                items:
                  1:
                    material: PAPER
                    name: "Egyptian Papyrus"
                    buy: 6
                    sell: 2.5
                    lore:
                    - "Papyrus from ancient Egypt"
                    - '&cIt''s old'
                  Special:
                    material: milk_bucket
                    slot: 1-9,36-45
                    hidePricingLore: true
            """.trimIndent(),
        )
        val page = m(m(y["pages"])["page1"])
        assertEquals(6L, page["gui-rows"])
        assertEquals("&dCustom title", page["title"])
        val item = m(m(page["items"])["1"])
        assertEquals(6L, item["buy"])
        assertEquals(2.5, item["sell"])
        assertEquals(listOf<Any?>("Papyrus from ancient Egypt", "&cIt's old"), l(item["lore"]))
        assertEquals("1-9,36-45", m(m(page["items"])["Special"])["slot"])
        assertEquals(true, m(m(page["items"])["Special"])["hidePricingLore"])
    }

    @Test fun parsesDeluxeMenusStyleFile() {
        val y = YamlParser.parseMap(
            """
            menu_title: '&8My Menu'
            open_command: menu
            size: 27
            items:
              close:
                material: BARRIER
                slot: 22
                display_name: '&cClose #1'
                left_click_commands:
                  - '[close]'
                  - '[player] spawn'
                view_requirement:
                  requirements:
                    perm:
                      type: has permission
                      permission: my.perm
              multi:
                material: STONE
                slots: [1, 2, 3]
                lore: []
            """.trimIndent(),
        )
        assertEquals("&8My Menu", y["menu_title"])
        val close = m(m(y["items"])["close"])
        assertEquals("&cClose #1", close["display_name"])
        assertEquals(listOf<Any?>("[close]", "[player] spawn"), l(close["left_click_commands"]))
        assertEquals("has permission", m(m(m(close["view_requirement"])["requirements"])["perm"])["type"])
        assertEquals(listOf<Any?>(1L, 2L, 3L), l(m(m(y["items"])["multi"])["slots"]))
        assertTrue(l(m(m(y["items"])["multi"])["lore"]).isEmpty())
    }

    @Test fun listsOfMapsAndRoundTrip() {
        val src = """
            requirements:
              - type: PERMISSION
                value: a.b
              - type: LEVEL
                value: 5
            flag: yes
            empty:
            n: null
        """.trimIndent()
        val y = YamlParser.parseMap(src)
        assertEquals("PERMISSION", m(l(y["requirements"])[0])["type"])
        assertEquals(5L, m(l(y["requirements"])[1])["value"])
        assertEquals("yes", y["flag"])
        assertNull(y["empty"])
        val back = YamlParser.parseMap(Yaml.dump(y))
        assertEquals(y, back)
    }

    @Test fun friendlyErrors() {
        for (bad in listOf("a: |\n  text", "a:\n\tb: 1", "a: 'open", "key without colon\nother: 1", "a:\n  b: 1\n c: 2")) {
            try { YamlParser.parse(bad); fail("expected error for: $bad") } catch (e: YamlException) { assertTrue(e.message!!.isNotBlank()) }
        }
    }

    @Test fun dumpQuotesRiskyStringsAndKeepsTypes() {
        val out = Yaml.dump(linkedMapOf("a" to "&aHello", "b" to "10", "c" to 10L, "d" to listOf(linkedMapOf("x" to 1L, "y" to "z w"))))
        assertEquals("a: \"&aHello\"\nb: \"10\"\nc: 10\nd:\n  - x: 1\n    y: \"z w\"\n", out)
    }
}
