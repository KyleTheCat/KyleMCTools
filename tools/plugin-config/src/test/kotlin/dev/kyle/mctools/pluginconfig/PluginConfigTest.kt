package dev.kyle.mctools.pluginconfig

import dev.kyle.mctools.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class PluginConfigTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zip(entries: Map<String, ByteArray>): File {
        val f = tmp.newFile()
        ZipOutputStream(f.outputStream()).use { z -> for ((n, b) in entries) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return f
    }

    private fun b(s: String) = s.toByteArray()
    private val png = byteArrayOf(1, 2, 3)

    private fun legacy() = zip(
        mapOf(
            "pack.mcmeta" to b("""{"pack":{"pack_format":34}}"""),
            "assets/minecraft/models/item/diamond_sword.json" to
                b("""{"parent":"item/handheld","overrides":[{"predicate":{"custom_model_data":1001},"model":"custom/ruby_sword"}]}"""),
            "assets/minecraft/models/custom/ruby_sword.json" to b("""{"parent":"item/handheld","textures":{"layer0":"custom/ruby_sword"}}"""),
            "assets/minecraft/textures/custom/ruby_sword.png" to png,
        ),
    )

    private fun itemModel() = zip(
        mapOf(
            "pack.mcmeta" to b("""{"pack":{"pack_format":46}}"""),
            "assets/kyle/items/ruby.json" to b("""{"model":{"type":"minecraft:model","model":"kyle:item/ruby"}}"""),
            "assets/kyle/models/item/ruby.json" to b("""{"parent":"minecraft:item/generated","textures":{"layer0":"kyle:item/ruby"}}"""),
            "assets/kyle/textures/item/ruby.png" to png,
        ),
    )

    @Test fun nexoLegacy() {
        val out = tmp.newFolder("o1")
        PluginConfigGenerator.generate(legacy(), out, PluginOptions("test pack", PluginTarget.NEXO), CollectingReporter())
        ZipFile(File(out, "nexo_test_pack_server_files.zip")).use { z ->
            val yml = z.getInputStream(z.getEntry("plugins/Nexo/items/test_pack.yml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(yml, yml.contains("ruby_sword:"))
            assertTrue(yml, yml.contains("itemname: \"Ruby Sword\""))
            assertTrue(yml, yml.contains("material: DIAMOND_SWORD"))
            assertTrue(yml, yml.contains("custom_model_data: 1001"))
            assertNotNull(z.getEntry("plugins/Nexo/pack/assets/minecraft/textures/custom/ruby_sword.png"))
            assertNotNull(z.getEntry("plugins/Nexo/pack/assets/minecraft/models/custom/ruby_sword.json"))
            assertEquals("vanilla override must be left out", null, z.getEntry("plugins/Nexo/pack/assets/minecraft/models/item/diamond_sword.json"))
        }
        assertTrue(File(out, "REVIEW.txt").exists())
    }

    @Test fun oraxenItemModel() {
        val out = tmp.newFolder("o2")
        PluginConfigGenerator.generate(itemModel(), out, PluginOptions("kp", PluginTarget.ORAXEN), CollectingReporter())
        ZipFile(File(out, "oraxen_kp_server_files.zip")).use { z ->
            val yml = z.getInputStream(z.getEntry("plugins/Oraxen/items/kp.yml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(yml, yml.contains("displayname: Ruby"))
            assertTrue(yml, yml.contains("material: PAPER"))
            assertTrue(yml, yml.contains("item_model: \"kyle:ruby\""))
        }
    }

    @Test fun itemsAdderUsesNamespaceFolders() {
        val out = tmp.newFolder("o3")
        PluginConfigGenerator.generate(itemModel(), out, PluginOptions("kp", PluginTarget.ITEMSADDER), CollectingReporter())
        ZipFile(File(out, "itemsadder_kp_server_files.zip")).use { z ->
            val yml = z.getInputStream(z.getEntry("plugins/ItemsAdder/contents/kyle/configs/kp.yml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(yml, yml.contains("namespace: kyle"))
            assertTrue(yml, yml.contains("model_path: item/ruby"))
            assertTrue(yml, yml.contains("generate: false"))
            assertNotNull(z.getEntry("plugins/ItemsAdder/contents/kyle/resourcepack/kyle/textures/item/ruby.png"))
        }
    }

    @Test fun emptyPackGivesFriendlyError() {
        val z = zip(mapOf("pack.mcmeta" to b("""{"pack":{"pack_format":34}}"""), "assets/minecraft/lang/en_us.json" to b("{}")))
        try { PluginConfigGenerator.generate(z, tmp.newFolder("o4"), PluginOptions("x", PluginTarget.NEXO), CollectingReporter()); fail() }
        catch (e: ToolException) { assertFalse(e.userMessage.isEmpty()) }
    }
}
