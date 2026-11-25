package dev.kyle.mctools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class McVersionsTest {
    private fun j(s: String) = Js.parseObject(s)

    @Test fun readsFormatFromAllMcmetaStyles() {
        assertEquals(34, McVersions.sourceFormat(j("""{"pack":{"pack_format":34}}""")))
        assertEquals(75, McVersions.sourceFormat(j("""{"pack":{"min_format":[75,0],"max_format":[75,0]}}""")))
        assertEquals(88, McVersions.sourceFormat(j("""{"pack":{"min_format":88,"max_format":88}}""")))
        assertNull(McVersions.sourceFormat(j("""{"nothing":1}""")))
        assertNull(McVersions.sourceFormat(null))
    }

    @Test fun labelsAndTable() {
        assertEquals("1.21.4", McVersions.label(46))
        assertEquals("26.2", McVersions.all.last().label)
        assertEquals(true, McVersions.all.zipWithNext().all { (a, b) -> a.format < b.format })
    }
}
