package dev.kyle.mctools.optimizer

import dev.kyle.mctools.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class PackOptimizerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")

    private fun zip(entries: Map<String, ByteArray>): File {
        val f = tmp.newFile()
        ZipOutputStream(f.outputStream()).use { z -> for ((n, b) in entries) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return f
    }

    private fun b(s: String) = s.toByteArray()

    @Test fun junkRemovedAndJsonMinified() {
        val pretty = "{\n    \"parent\": \"item/generated\",\n    \"textures\": {\n        \"layer0\": \"ns:item/a\"\n    }\n}\n"
        val input = zip(mapOf(
            "pack.mcmeta" to b("""{"pack":{"pack_format":34}}"""),
            "assets/ns/models/item/a.json" to b(pretty),
            "assets/ns/textures/item/a.png" to png,
            "assets/ns/textures/item/.DS_Store" to b("x"),
            "art/source.psd" to b("big"),
        ))
        val out = tmp.newFolder("o")
        val res = PackOptimizer.run(input, out, OptimizeOptions("my pack"), CollectingReporter())
        assertTrue(res.summary, res.summary.contains(" to "))
        ZipFile(File(out, "my_pack_optimized.zip")).use { z ->
            assertNull(z.getEntry("assets/ns/textures/item/.DS_Store"))
            assertNull(z.getEntry("art/source.psd"))
            val json = z.getInputStream(z.getEntry("assets/ns/models/item/a.json")).readBytes().toString(Charsets.UTF_8)
            assertFalse(json.contains("\n"))
            assertNotNull(Js.parseObject(json))
            assertNotNull(z.getEntry("assets/ns/textures/item/a.png"))
        }
        assertTrue(File(out, "report.txt").readText().contains("Pack Optimizer"))
    }

    @Test fun unreferencedTexturesOnlyInCustomNamespaces() {
        val input = zip(mapOf(
            "pack.mcmeta" to b("""{"pack":{"pack_format":34}}"""),
            "assets/ns/models/item/a.json" to b("""{"textures":{"layer0":"ns:item/used"}}"""),
            "assets/ns/textures/item/used.png" to png,
            "assets/ns/textures/item/orphan.png" to png,
            "assets/minecraft/textures/block/stone.png" to png,
        ))
        val out = tmp.newFolder("o2")
        PackOptimizer.run(input, out, OptimizeOptions("p", unused = true), CollectingReporter())
        ZipFile(File(out, "p_optimized.zip")).use { z ->
            assertNotNull(z.getEntry("assets/ns/textures/item/used.png"))
            assertNull(z.getEntry("assets/ns/textures/item/orphan.png"))
            assertNotNull("vanilla-path textures must never be removed", z.getEntry("assets/minecraft/textures/block/stone.png"))
        }
    }

    @Test fun bedrockPackKeepsMcpackExtensionAndRootFolderIsStripped() {
        val input = zip(mapOf(
            "MyBedrock/manifest.json" to b("""{ "format_version": 2, "header": {"name": "x"} }"""),
            "MyBedrock/textures/items/a.png" to png,
        ))
        val out = tmp.newFolder("o3")
        PackOptimizer.run(input, out, OptimizeOptions("bed"), CollectingReporter())
        ZipFile(File(out, "bed_optimized.mcpack")).use { z -> assertNotNull(z.getEntry("manifest.json")) }
    }

    @Test fun invalidJsonIsLeftAloneWithAWarning() {
        val input = zip(mapOf("pack.mcmeta" to b("""{"pack":{"pack_format":34}}"""), "assets/ns/models/x.json" to b("{ oops")))
        val rep = CollectingReporter()
        PackOptimizer.run(input, tmp.newFolder("o4"), OptimizeOptions("q"), rep)
        assertTrue(rep.lines.any { it.level == LogLevel.WARN && it.text.contains("not valid JSON") })
    }

    @Test fun notAZipPackGivesNoCrash() {
        val f = tmp.newFile(); f.writeBytes(b("PK this is not a zip"))
        try { PackOptimizer.run(f, tmp.newFolder("o5"), OptimizeOptions("z"), CollectingReporter()); fail() } catch (e: java.util.zip.ZipException) { }
    }

    @Test fun junkRules() {
        assertTrue(PackOptimizer.isJunk("a/Thumbs.db"))
        assertTrue(PackOptimizer.isJunk(".git/config"))
        assertTrue(PackOptimizer.isJunk("art/x.PSD"))
        assertFalse(PackOptimizer.isJunk("assets/ns/textures/a.png"))
        assertEquals("1.5 MB", PackOptimizer.size(1_572_864))
    }
}
