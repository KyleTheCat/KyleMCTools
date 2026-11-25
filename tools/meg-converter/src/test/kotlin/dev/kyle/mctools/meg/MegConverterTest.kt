package dev.kyle.mctools.meg

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

class MegConverterTest {
    @get:Rule val tmp = TemporaryFolder()

    private val png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="

    private val bbmodel = """
    {"meta":{"format_version":"4.10","model_format":"free"},"name":"Test Golem","resolution":{"width":64,"height":32},
     "elements":[
       {"uuid":"c1","type":"cube","name":"body","from":[-4,0,-2],"to":[4,12,2],"origin":[0,8,0],"rotation":[0,45,0],"box_uv":false,
        "faces":{"north":{"uv":[0,0,8,12],"texture":0},"east":{"uv":[8,0,12,12],"texture":0},"south":{"uv":[0,0,8,12],"texture":0},"west":{"uv":[8,0,12,12],"texture":0},"up":{"uv":[0,0,8,4],"texture":0},"down":{"uv":[0,0,8,4],"texture":null}}},
       {"uuid":"c2","type":"cube","name":"loose","from":[0,0,0],"to":[1,1,1],"origin":[0,0,0],"rotation":[0,0,0],"box_uv":true,"uv_offset":[10,20]},
       {"uuid":"loc","type":"locator","name":"mount"}
     ],
     "outliner":[{"uuid":"g1","name":"body_group","origin":[0,8,0],"rotation":[10,20,0],"children":["c1",{"uuid":"g2","name":"arm","origin":[5,10,0],"rotation":[0,0,0],"children":[]}]},"c2","loc"],
     "textures":[{"name":"t","source":"data:image/png;base64,$png"}],
     "animations":[{"uuid":"a1","name":"idle","loop":"loop","length":1.0,"animators":{"g1":{"name":"body_group","type":"bone","keyframes":[
        {"channel":"rotation","time":0,"interpolation":"linear","data_points":[{"x":10,"y":20,"z":30}]},
        {"channel":"position","time":0.5,"interpolation":"catmullrom","data_points":[{"x":1,"y":2,"z":3}]}]}}}]}
    """.trimIndent()

    private fun geo(m: MegModel) = Js.parseObject(m.geo)!!.arr("minecraft:geometry")!![0] as JsonObject
    private fun nums(a: JsonArray?) = a!!.map { (it as kotlinx.serialization.json.JsonPrimitive).content.toDouble() }

    @Test fun convertsGeometryWithMirroredXAndBones() {
        val m = MegConverter.convert(Js.parseObject(bbmodel)!!, "test_golem", MegOptions("p"))
        val g = geo(m)
        val d = g.obj("description")!!
        assertEquals("geometry.test_golem", d.str("identifier"))
        assertEquals(64, d["texture_width"].num()?.toInt())
        val bones = g.arr("bones")!!.map { it as JsonObject }
        assertEquals(listOf("body_group", "arm", "root"), bones.map { it.str("name") })
        val body = bones[0]
        assertEquals(listOf(-0.0 + 0.0, 8.0, 0.0), nums(body.arr("pivot")))          // x mirrored
        assertEquals(listOf(-10.0, -20.0, 0.0), nums(body.arr("rotation")))
        val cube = body.arr("cubes")!![0] as JsonObject
        assertEquals(listOf(-4.0, 0.0, -2.0), nums(cube.arr("origin")))              // -(to.x), from.y, from.z
        assertEquals(listOf(8.0, 12.0, 4.0), nums(cube.arr("size")))
        assertEquals(listOf(-0.0 + 0.0, 8.0, 0.0), nums(cube.arr("pivot")))
        assertEquals(listOf(-0.0 + 0.0, -45.0, 0.0), nums(cube.arr("rotation")))
        val uv = cube.obj("uv")!!
        assertEquals(listOf(8.0, 12.0), nums(uv.obj("north")!!.arr("uv_size")))
        assertNull("a face without a texture is not written", uv["down"])
        assertEquals("arm", bones[1].str("name")); assertEquals("body_group", bones[1].str("parent"))
        assertEquals(listOf(10.0, 20.0), nums(((bones[2].arr("cubes")!![0] as JsonObject)["uv"] as JsonArray)))
        assertNotNull(m.texture)
        assertTrue(m.warnings.any { it.contains("non-cube") })
    }

    @Test fun convertsAnimationsWithSignsAndInterpolation() {
        val m = MegConverter.convert(Js.parseObject(bbmodel)!!, "test_golem", MegOptions("p"))
        val a = Js.parseObject(m.animations!!)!!.obj("animations")!!.obj("animation.test_golem.idle")!!
        assertEquals(true, (a["loop"] as kotlinx.serialization.json.JsonPrimitive).content.toBoolean())
        assertEquals(1.0, a["animation_length"].num())
        val bone = a.obj("bones")!!.obj("body_group")!!
        assertEquals(listOf(-10.0, -20.0, 30.0), nums(bone.obj("rotation")!!.arr("0.0")))
        val pos = bone.obj("position")!!.obj("0.5")!!
        assertEquals(listOf(-1.0, 2.0, 3.0), nums(pos.arr("post")))
        assertEquals("catmullrom", pos.str("lerp_mode"))
    }

    @Test fun swapOptionSwapsSideFaces() {
        val m = MegConverter.convert(Js.parseObject(bbmodel)!!, "g", MegOptions("p", swapEastWest = true))
        val uv = ((geo(m).arr("bones")!![0] as JsonObject).arr("cubes")!![0] as JsonObject).obj("uv")!!
        assertEquals(listOf(8.0, 0.0), nums(uv.obj("west")!!.arr("uv")))   // came from "east"
    }

    @Test fun runWritesFolderLayoutAndZipFromSingleFile() {
        val f = tmp.newFile("x.bbmodel"); f.writeText(bbmodel)
        val out = tmp.newFolder("o")
        val res = MegConverter.run(f, out, MegOptions("my pack"), CollectingReporter())
        assertEquals("1 model(s) converted", res.summary)
        assertTrue(File(out, "input/test_golem/test_golem.geo.json").exists())
        assertTrue(File(out, "input/test_golem/test_golem.animation.json").exists())
        assertTrue(File(out, "input/test_golem/test_golem.png").exists())
        ZipFile(File(out, "my_pack_meg_input.zip")).use { z -> assertNotNull(z.getEntry("test_golem/test_golem.geo.json")) }
        assertTrue(File(out, "README.txt").readText().contains("geysermodelengineextension"))
    }

    @Test fun zipWithSeveralModelsAndBrokenOnes() {
        val f = tmp.newFile("pack.zip")
        ZipOutputStream(f.outputStream()).use { z ->
            for ((n, t) in mapOf("a/one.bbmodel" to bbmodel, "two.bbmodel" to "{ broken", "three.bbmodel" to """{"name":"x"}""")) {
                z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry()
            }
        }
        val rep = CollectingReporter()
        val res = MegConverter.run(f, tmp.newFolder("o2"), MegOptions("p"), rep)
        assertEquals("1 model(s) converted", res.summary)
        assertTrue(rep.lines.any { it.text.contains("two.bbmodel") && it.text.contains("not valid JSON") })
        assertTrue(rep.lines.any { it.text.contains("three.bbmodel") && it.text.contains("no cubes") })
    }

    @Test fun noModelsGivesFriendlyError() {
        val f = tmp.newFile("e.zip")
        ZipOutputStream(f.outputStream()).use { z -> z.putNextEntry(ZipEntry("readme.txt")); z.write(byteArrayOf(1)); z.closeEntry() }
        try { MegConverter.run(f, tmp.newFolder("o3"), MegOptions("p"), CollectingReporter()); fail() }
        catch (e: ToolException) { assertTrue(e.userMessage.contains(".bbmodel")) }
    }
}
