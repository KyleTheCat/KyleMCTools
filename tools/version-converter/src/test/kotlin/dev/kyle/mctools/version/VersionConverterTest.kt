package dev.kyle.mctools.version

import dev.kyle.mctools.core.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class VersionConverterTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun b(s: String) = s.toByteArray()

    private fun zip(entries: Map<String, ByteArray>): File {
        val f = tmp.newFile()
        ZipOutputStream(f.outputStream()).use { z -> for ((n, x) in entries) { z.putNextEntry(ZipEntry(n)); z.write(x); z.closeEntry() } }
        return f
    }

    private fun json(z: ZipFile, name: String) = Js.parseObject(z.getInputStream(z.getEntry(name)).readBytes().toString(Charsets.UTF_8))!!
    private fun out(dir: File) = dir.listFiles()!!.first { it.name.endsWith(".zip") }

    private fun oldPack() = zip(mapOf(
        "pack.mcmeta" to b("""{"pack":{"pack_format":34,"description":"old"}}"""),
        "assets/minecraft/models/item/stick.json" to b("""{"parent":"item/handheld","textures":{"layer0":"item/stick"},"overrides":[{"predicate":{"custom_model_data":2},"model":"custom/b"},{"predicate":{"custom_model_data":1},"model":"custom/a"},{"predicate":{"custom_model_data":9,"damage":0.5},"model":"custom/c"}]}"""),
        "assets/minecraft/textures/models/armor/iron_layer_1.png" to byteArrayOf(1),
        "assets/minecraft/textures/models/armor/iron_layer_2.png" to byteArrayOf(2),
        "assets/minecraft/textures/models/armor/turtle_layer_1.png" to byteArrayOf(3),
        "assets/minecraft/textures/models/armor/leather_layer_1_overlay.png" to byteArrayOf(4),
    ))

    @Test fun upgradeTo1214ConvertsOverridesAndArmor() {
        val dir = tmp.newFolder("o")
        val rep = CollectingReporter()
        VersionConverter.run(oldPack(), dir, VersionOptions("p", 46), rep)
        ZipFile(out(dir)).use { z ->
            val mc = json(z, "pack.mcmeta").obj("pack")!!
            assertEquals(46, mc["pack_format"].num()?.toInt())
            assertEquals("old", mc.str("description"))
            val model = json(z, "assets/minecraft/items/stick.json").obj("model")!!
            assertEquals("minecraft:range_dispatch", model.str("type"))
            assertEquals("minecraft:item/stick", model.obj("fallback")!!.str("model"))
            val entries = model.arr("entries")!!
            assertEquals(2, entries.size)   // the override with a second predicate is skipped
            assertEquals(1, (entries[0] as JsonObject)["threshold"].num()?.toInt())
            assertEquals("minecraft:custom/a", (entries[0] as JsonObject).obj("model")!!.str("model"))
            assertNotNull(z.getEntry("assets/minecraft/textures/entity/equipment/humanoid/iron.png"))
            assertNotNull(z.getEntry("assets/minecraft/textures/entity/equipment/humanoid_leggings/iron.png"))
            assertNotNull(z.getEntry("assets/minecraft/textures/entity/equipment/humanoid/turtle_scute.png"))
            assertNotNull(z.getEntry("assets/minecraft/textures/entity/equipment/humanoid/leather_overlay.png"))
            assertNull(z.getEntry("assets/minecraft/textures/models/armor/iron_layer_1.png"))
        }
        assertTrue(rep.lines.any { it.level == LogLevel.WARN && it.text.contains("other predicates") })
    }

    @Test fun targetsFrom65UseMinAndMaxFormat() {
        val dir = tmp.newFolder("o2")
        VersionConverter.run(oldPack(), dir, VersionOptions("p", 88), CollectingReporter())
        ZipFile(out(dir)).use { z ->
            val pack = json(z, "pack.mcmeta").obj("pack")!!
            assertNull(pack["pack_format"])
            assertEquals(88, ((pack["min_format"] as JsonArray)[0]).let { (it as kotlinx.serialization.json.JsonPrimitive).content.toInt() })
            assertNotNull(pack["max_format"])
        }
    }

    @Test fun roundTripBackTo1213RestoresOverridesAndArmorPaths() {
        val up = tmp.newFolder("up")
        VersionConverter.run(oldPack(), up, VersionOptions("p", 46), CollectingReporter())
        val down = tmp.newFolder("down")
        VersionConverter.run(out(up), down, VersionOptions("p", 34), CollectingReporter())
        ZipFile(out(down)).use { z ->
            assertEquals(34, json(z, "pack.mcmeta").obj("pack")!!["pack_format"].num()?.toInt())
            assertNotNull(z.getEntry("assets/minecraft/textures/models/armor/iron_layer_2.png"))
            assertNotNull(z.getEntry("assets/minecraft/textures/models/armor/turtle_layer_1.png"))
            assertNotNull(z.getEntry("assets/minecraft/textures/models/armor/leather_layer_1_overlay.png"))
            val ov = json(z, "assets/minecraft/models/item/stick.json").arr("overrides")!!
            assertTrue(ov.size >= 3)   // original three stay; converted ones are already present, none duplicated
        }
    }

    @Test fun bedrockAndTooOldPacksGiveFriendlyErrors() {
        val bed = zip(mapOf("manifest.json" to b("""{"format_version":2}"""), "textures/a.png" to byteArrayOf(1)))
        try { VersionConverter.run(bed, tmp.newFolder("e1"), VersionOptions("x", 46), CollectingReporter()); fail() }
        catch (e: Exception) { assertTrue(e.message!!, e.message!!.contains("Bedrock") || e is ToolException) }
        val old = zip(mapOf("pack.mcmeta" to b("""{"pack":{"pack_format":3}}""")))
        try { VersionConverter.run(old, tmp.newFolder("e2"), VersionOptions("x", 46), CollectingReporter()); fail() }
        catch (e: ToolException) { assertTrue(e.userMessage.contains("1.13")) }
        val noMeta = zip(mapOf("pack.mcmeta" to b("{ broken"), "assets/a" to byteArrayOf(1)))
        try { VersionConverter.run(noMeta, tmp.newFolder("e3"), VersionOptions("x", 46), CollectingReporter()); fail() }
        catch (e: ToolException) { assertTrue(e.userMessage.contains("pack.mcmeta")) }
    }
}
