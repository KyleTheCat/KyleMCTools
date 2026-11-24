package dev.kyle.mctools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PackArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun detectsWrappedRootAndIgnoresUnsafeEntries() {
        val zip = makeZip(
            tmp.newFile("p.zip"),
            mapOf(
                "MyPack/pack.mcmeta" to """{"pack":{"pack_format":34}}""".b(),
                "MyPack/assets/minecraft/x.txt" to "x".b(),
                "../evil.txt" to "bad".b(),
                "__MACOSX/junk" to "j".b(),
            ),
        )
        PackArchive(zip).use { p ->
            assertTrue(p.exists("assets/minecraft/x.txt"))
            assertTrue(p.exists("pack.mcmeta"))
            assertFalse(p.names.any { it.contains("evil") || it.contains("MACOSX") })
        }
    }

    @Test fun readsJsonWithCommentsAndTrailingCommas() {
        val zip = makeZip(
            tmp.newFile("p.zip"),
            mapOf("pack.mcmeta" to "{ // c\n \"pack\": {\"pack_format\": 5,},\n}".b()),
        )
        PackArchive(zip).use { p -> assertEquals(5, p.readJson("pack.mcmeta")?.obj("pack")?.get("pack_format").num()?.toInt()) }
    }

    @Test fun rejectsZipThatIsNotAPack() {
        val zip = makeZip(tmp.newFile("n.zip"), mapOf("readme.txt" to "hi".b()))
        try { PackArchive(zip).close(); fail("expected ToolException") } catch (e: ToolException) { assertTrue(e.userMessage.contains("resource pack")) }
    }
}
