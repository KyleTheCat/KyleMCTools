package dev.kyle.mctools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YamlTest {
    @Test fun nestedMapsListsAndQuoting() {
        val out = Yaml.dump(
            linkedMapOf(
                "ruby_sword" to linkedMapOf(
                    "itemname" to "Ruby Sword",
                    "material" to "DIAMOND_SWORD",
                    "Pack" to linkedMapOf("custom_model_data" to 1001, "generate_model" to false),
                    "lore" to listOf("one", "two: x"),
                    "empty" to emptyMap<String, Any?>(),
                ),
            ),
        )
        assertTrue(out, out.startsWith("ruby_sword:\n  itemname: \"Ruby Sword\"\n  material: DIAMOND_SWORD\n"))
        assertTrue(out, out.contains("  Pack:\n    custom_model_data: 1001\n    generate_model: false\n"))
        assertTrue(out, out.contains("  lore:\n    - one\n    - \"two: x\"\n"))
        assertTrue(out, out.contains("  empty: {}\n"))
    }

    @Test fun reservedWordsAndNumericKeysAreQuoted() {
        val out = Yaml.dump(linkedMapOf("50" to linkedMapOf("v" to "yes")))
        assertEquals("\"50\":\n  v: \"yes\"\n", out)
    }

    @Test fun pathsWithSlashesStayPlainButColonsAreQuoted() {
        val out = Yaml.dump(linkedMapOf("model_path" to "item/ruby", "item_model" to "kyle:ruby"))
        assertEquals("model_path: item/ruby\nitem_model: \"kyle:ruby\"\n", out)
    }
}
