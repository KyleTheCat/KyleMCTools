package dev.kyle.mctools.shop

import dev.kyle.mctools.core.YamlException
import dev.kyle.mctools.core.YamlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EsShopTest {
    private val sample = """
        pages:
          page1:
            gui-rows: 3
            title: "&8Food"
            items:
              1:
                material: APPLE
                buy: 10
                sell: 2.5
                lore:
                - "&7Tasty"
                stock-limit-player: 5
              2:
                material: BEDROCK
                buy: -1
                sell: 100
              3:
                material: BREAD
                buy: 4
                sell: 6
                slot: 40
    """.trimIndent()

    @Test fun editsKeepUnknownKeysAndRoundTrip() {
        val s = EsShop.parse("Food", sample)
        assertEquals(listOf("page1"), s.pageNames())
        assertEquals(3, s.rows("page1"))
        val key = s.addItem("page1", "diamond", 100.0, 25.5)
        assertEquals("4", key)
        s.item("page1", "1")!!["buy"] = numValue(12.0)
        val back = EsShop.parse("Food", s.toYaml())
        assertEquals(5L, (back.item("page1", "1")!!["stock-limit-player"] as Long))
        assertEquals(12L, back.item("page1", "1")!!["buy"])
        assertEquals("DIAMOND", back.item("page1", "4")!!["material"])
        assertEquals(25.5, back.item("page1", "4")!!["sell"])
        assertEquals(listOf<Any?>("&7Tasty"), back.item("page1", "1")!!["lore"])
    }

    @Test fun scalingSkipsNegativePricesAndRounds() {
        val s = EsShop.parse("Food", sample)
        val changed = s.scalePrices("page1", 1.5)
        assertEquals(5, changed)   // apple buy+sell, bedrock sell, bread buy+sell... buy -1 skipped
        assertEquals(15L, s.item("page1", "1")!!["buy"])
        assertEquals(3.75, s.item("page1", "1")!!["sell"])
        assertEquals(-1L, s.item("page1", "2")!!["buy"])
        assertEquals(150L, s.item("page1", "2")!!["sell"])
    }

    @Test fun validationFindsProblems() {
        val w = EsShop.parse("Food", sample).validate().joinToString("\n")
        assertTrue(w, w.contains("slot 40 is outside"))
        assertTrue(w, w.contains("sells for more"))
    }

    @Test fun rotatingAndWrongFilesGiveFriendlyErrors() {
        for (bad in listOf("items:\n  a:\n    material: STONE", "enable: true\nslot: 4")) {
            try { EsShop.parse("x", bad); fail() } catch (e: YamlException) { assertTrue(e.message!!.isNotBlank()) }
        }
    }

    @Test fun pagesAndSections() {
        val s = EsShop.create("Tools")
        assertEquals(listOf("page1"), s.pageNames())
        assertEquals("page2", s.addPage())
        s.removePage("page1")
        assertEquals(listOf("page2"), s.pageNames())
        val sec = EsSection.parse(EsSection.create("Tools").toYaml())
        sec.slot = 12
        sec.sub("item")["material"] = "ANVIL"
        val back = YamlParser.parseMap(sec.toYaml())
        assertEquals(12L, back["slot"])
        assertTrue(sec.validate().isEmpty())
        sec.slot = 99
        assertTrue(sec.validate().isNotEmpty())
    }
}
