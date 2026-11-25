package dev.kyle.mctools.protector

import dev.kyle.mctools.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class PackProtectorTest {
    @get:Rule val tmp = TemporaryFolder()
    private val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")
    private fun b(s: String) = s.toByteArray()

    private fun zip(entries: Map<String, ByteArray>): File {
        val f = tmp.newFile()
        ZipOutputStream(f.outputStream()).use { z -> for ((n, x) in entries) { z.putNextEntry(ZipEntry(n)); z.write(x); z.closeEntry() } }
        return f
    }

    private fun text(z: ZipFile, name: String) = z.getInputStream(z.getEntry(name)).readBytes().toString(Charsets.UTF_8)

    private fun javaPack() = zip(mapOf(
        "pack.mcmeta" to b("""{"pack":{"pack_format":34}}"""),
        "assets/kyle/items/ruby.json" to b("""{"model":{"type":"minecraft:model","model":"kyle:item/ruby"}}"""),
        "assets/kyle/models/item/ruby.json" to b("""{"parent":"minecraft:item/generated","textures":{"layer0":"kyle:item/ruby"}}"""),
        "assets/kyle/textures/item/ruby.png" to png,
        "assets/kyle/sounds.json" to b("""{"staff.cast":{"sounds":[{"name":"cast","volume":0.5}]}}"""),
        "assets/kyle/sounds/cast.ogg" to b("OggS-data"),
        "assets/minecraft/textures/block/stone.png" to png,
    ))

    @Test fun renameKeepsReferencesConsistent() {
        val out = tmp.newFolder("o")
        PackProtector.run(javaPack(), out, ProtectOptions("p", rename = true), CollectingReporter())
        ZipFile(File(out, "p_protected.zip")).use { z ->
            assertNull(z.getEntry("assets/kyle/textures/item/ruby.png"))
            assertNull(z.getEntry("assets/kyle/models/item/ruby.json"))
            assertNull(z.getEntry("assets/kyle/sounds/cast.ogg"))
            assertNotNull("items definitions keep their names (the server refers to them)", z.getEntry("assets/kyle/items/ruby.json"))
            assertNotNull("vanilla-path files are never renamed", z.getEntry("assets/minecraft/textures/block/stone.png"))

            val map = Js.parseObject(File(out, "rename_map.json").readText())!!
            val newModel = map.str("kyle:item/ruby")!!.substringAfter(':')
            assertNotNull(z.getEntry("assets/kyle/models/$newModel.json"))
            assertNotNull(z.getEntry("assets/kyle/textures/$newModel.png"))

            val itemDef = text(z, "assets/kyle/items/ruby.json")
            assertTrue(itemDef, itemDef.contains("kyle:$newModel"))
            val model = text(z, "assets/kyle/models/$newModel.json")
            assertTrue(model, model.contains("kyle:$newModel"))
            assertFalse(model.contains("item/ruby"))

            val newSound = map.str("kyle:cast")!!.substringAfter(':')
            assertNotNull(z.getEntry("assets/kyle/sounds/$newSound.ogg"))
            val sounds = text(z, "assets/kyle/sounds.json")
            assertTrue(sounds, sounds.contains("\"name\":\"$newSound\""))
            assertTrue("event key must not change", sounds.contains("staff.cast"))
        }
    }

    @Test fun defaultRunOnlyMinifiesAndAddsDecoys() {
        val out = tmp.newFolder("o2")
        PackProtector.run(javaPack(), out, ProtectOptions("p"), CollectingReporter())
        ZipFile(File(out, "p_protected.zip")).use { z ->
            assertNotNull(z.getEntry("assets/kyle/textures/item/ruby.png"))
            assertNotNull(z.getEntry("assets/kmt_decoy/textures/item/aux.png"))
            assertFalse(text(z, "assets/kyle/models/item/ruby.json").contains(" "))
        }
        assertFalse(File(out, "rename_map.json").exists())
        assertTrue(File(out, "PROTECTION_NOTES.txt").readText().contains("can NOT do"))
    }

    @Test fun bedrockPackGetsMcpackAndNoRename() {
        val input = zip(mapOf("manifest.json" to b("""{"format_version":2,"header":{"name":"x"}}"""), "textures/items/a.png" to png))
        val out = tmp.newFolder("o3")
        PackProtector.run(input, out, ProtectOptions("bed", rename = true), CollectingReporter())
        ZipFile(File(out, "bed_protected.mcpack")).use { z ->
            assertNotNull(z.getEntry("manifest.json"))
            assertNotNull(z.getEntry("textures/items/a.png"))
            assertNotNull(z.getEntry("textures/kmt_decoy/aux.png"))
        }
        assertEquals(false, File(out, "rename_map.json").exists())
    }
}
