package dev.kyle.mctools.geyser

import dev.kyle.mctools.core.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class JavaToBedrockTest {
    @get:Rule val tmp = TemporaryFolder()

    private val png = byteArrayOf(1, 2, 3)

    private fun zipOf(entries: Map<String, ByteArray>) = makeZipTo(tmp.newFile("in.zip"), entries)

    private fun legacyPack() = zipOf(
        mapOf(
            "pack.mcmeta" to """{"pack":{"pack_format":34}}""".b(),
            "assets/minecraft/models/item/diamond_sword.json" to
                """{"parent":"item/handheld","textures":{"layer0":"item/diamond_sword"},"overrides":[{"predicate":{"custom_model_data":1001},"model":"custom/ruby_sword"}]}""".b(),
            "assets/minecraft/models/custom/ruby_sword.json" to """{"parent":"item/handheld","textures":{"layer0":"custom/ruby_sword"}}""".b(),
            "assets/minecraft/textures/custom/ruby_sword.png" to png,
            "assets/minecraft/sounds.json" to """{"ruby.hit":{"sounds":[{"name":"custom/hit","volume":0.5}]}}""".b(),
            "assets/minecraft/sounds/custom/hit.ogg" to byteArrayOf(9, 9),
        ),
    )

    @Test fun legacyPackProducesLegacyMapping() {
        val out = tmp.newFolder("out")
        val rep = CollectingReporter()
        val res = JavaToBedrock.convert(legacyPack(), out, GeyserOptions("My Pack"), rep)
        assertTrue(res.summary, res.summary.startsWith("1 items converted"))

        val map = Js.parseObject(File(out, "geyser_mappings.json").readText())!!
        assertEquals(2, map["format_version"].num()?.toInt())
        val entry = (map.obj("items")!!["minecraft:diamond_sword"] as JsonArray)[0] as JsonObject
        assertEquals("legacy", entry.str("type"))
        assertEquals(1001, entry["custom_model_data"].num()?.toInt())
        assertEquals("geyser_custom:ruby_sword", entry.str("bedrock_identifier"))
        assertEquals("ruby_sword", entry.obj("bedrock_options")!!.str("icon"))

        ZipFile(File(out, "my_pack_bedrock.mcpack")).use { z ->
            for (n in listOf("manifest.json", "textures/item_texture.json", "textures/items/ruby_sword.png",
                "sounds/sound_definitions.json", "sounds/minecraft/custom/hit.ogg")) {
                assertTrue("missing $n", z.getEntry(n) != null)
            }
        }
        assertTrue(File(out, "INSTALL.txt").exists())
        assertTrue(File(out, "geyser_config_snippet.yml").readText().contains("enable-custom-content: true"))
    }

    @Test fun itemModelPackProducesDefinitionMapping() {
        val zip = zipOf(
            mapOf(
                "pack.mcmeta" to """{"pack":{"min_format":[69,0],"max_format":[75,0]}}""".b(),
                "assets/kyle/items/ruby.json" to """{"model":{"type":"minecraft:model","model":"kyle:item/ruby"}}""".b(),
                "assets/kyle/models/item/ruby.json" to """{"parent":"minecraft:item/generated","textures":{"layer0":"kyle:item/ruby"}}""".b(),
                "assets/kyle/textures/item/ruby.png" to png,
            ),
        )
        val out = tmp.newFolder("out2")
        JavaToBedrock.convert(zip, out, GeyserOptions("kp"), CollectingReporter())
        val map = Js.parseObject(File(out, "geyser_mappings.json").readText())!!
        val entry = (map.obj("items")!!["minecraft:paper"] as JsonArray)[0] as JsonObject
        assertEquals("definition", entry.str("type"))
        assertEquals("kyle:ruby", entry.str("model"))
        assertEquals("kyle:ruby", entry.str("bedrock_identifier"))
    }

    @Test fun rangeDispatchDefinitionIsReadAsLegacyItems() {
        val zip = zipOf(
            mapOf(
                "pack.mcmeta" to """{"pack":{"pack_format":46}}""".b(),
                "assets/minecraft/items/stick.json" to
                    """{"model":{"type":"minecraft:range_dispatch","property":"minecraft:custom_model_data","fallback":{"type":"minecraft:model","model":"minecraft:item/stick"},"entries":[{"threshold":7,"model":{"type":"minecraft:model","model":"custom:magic_wand"}}]}}""".b(),
                "assets/custom/models/magic_wand.json" to """{"parent":"item/generated","textures":{"layer0":"custom:magic_wand"}}""".b(),
                "assets/custom/textures/magic_wand.png" to png,
            ),
        )
        val out = tmp.newFolder("out3")
        JavaToBedrock.convert(zip, out, GeyserOptions("wand"), CollectingReporter())
        val map = Js.parseObject(File(out, "geyser_mappings.json").readText())!!
        val entry = (map.obj("items")!!["minecraft:stick"] as JsonArray)[0] as JsonObject
        assertEquals(7, entry["custom_model_data"].num()?.toInt())
        assertEquals("custom:magic_wand", entry.str("bedrock_identifier"))
    }

    @Test fun packWithoutCustomItemsGivesFriendlyError() {
        val zip = zipOf(mapOf("pack.mcmeta" to """{"pack":{"pack_format":34}}""".b(), "assets/minecraft/lang/en_us.json" to "{}".b()))
        try { JavaToBedrock.convert(zip, tmp.newFolder("o4"), GeyserOptions("x"), CollectingReporter()); fail() }
        catch (e: ToolException) { assertTrue(e.userMessage.startsWith("No custom items found")) }
    }

    @Test fun brokenJsonDoesNotCrash() {
        val zip = zipOf(
            mapOf(
                "pack.mcmeta" to """{"pack":{"pack_format":34}}""".b(),
                "assets/minecraft/models/item/paper.json" to "{ this is not json".b(),
            ),
        )
        val rep = CollectingReporter()
        try { JavaToBedrock.convert(zip, tmp.newFolder("o5"), GeyserOptions("x"), rep); fail() } catch (e: ToolException) { }
        assertTrue(rep.lines.any { it.level == LogLevel.WARN && it.text.contains("invalid JSON") })
    }

    private fun makeZipTo(f: File, e: Map<String, ByteArray>): File {
        java.util.zip.ZipOutputStream(f.outputStream()).use { z ->
            for ((n, b) in e) { z.putNextEntry(java.util.zip.ZipEntry(n)); z.write(b); z.closeEntry() }
        }
        return f
    }
}
