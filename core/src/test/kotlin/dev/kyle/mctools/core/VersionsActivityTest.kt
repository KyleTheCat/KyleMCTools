package dev.kyle.mctools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsActivityTest {
    @Test fun versionComparison() {
        assertTrue(Versions.isNewer("v0.2.0", "0.1.1"))
        assertTrue(Versions.isNewer("1.0", "0.9.9"))
        assertTrue(Versions.isNewer("0.1.10", "0.1.9"))
        assertFalse(Versions.isNewer("v0.1.1", "0.1.1"))
        assertFalse(Versions.isNewer("0.1.0", "0.1.1-dev"))
        assertFalse(Versions.isNewer("garbage", "0.1.1"))
    }

    @Test fun activityRoundTrip() {
        val list = listOf(
            ActivityEntry(1700000000123, "JavaToBedrock", "Java to Bedrock", "a.zip", true, "4 items", "KyleMCTools_Output/x", null, 1700000000123, "ok line\nwarn line"),
            ActivityEntry(2, "PackToPluginConfig", "Pack to plugin config", "b.zip", false, "bad zip", null, "content://x", 5, ""),
        )
        val back = ActivityJson.fromJson(ActivityJson.toJson(list))
        assertEquals(list, back)
        assertNull(back[0].folderUri)
    }

    @Test fun brokenActivityJsonGivesEmptyList() {
        assertTrue(ActivityJson.fromJson("{ not json").isEmpty())
        assertTrue(ActivityJson.fromJson("").isEmpty())
    }
}
