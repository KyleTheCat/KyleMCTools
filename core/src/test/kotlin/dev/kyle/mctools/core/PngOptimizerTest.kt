package dev.kyle.mctools.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import javax.imageio.ImageIO

class PngOptimizerTest {
    private fun png(img: BufferedImage): ByteArray {
        val o = ByteArrayOutputStream(); ImageIO.write(img, "png", o); return o.toByteArray()
    }

    private fun pixels(bytes: ByteArray): IntArray {
        val img = ImageIO.read(ByteArrayInputStream(bytes))
        return IntArray(img.width * img.height) { img.getRGB(it % img.width, it / img.width) }
    }

    @Test fun fewColorsBecomePaletteLosslessly() {
        val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        val cols = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0x80000000.toInt(), 0x00000000)
        for (y in 0 until 32) for (x in 0 until 32) img.setRGB(x, y, cols[(x / 4 + y / 8) % 4])
        val src = png(img)
        val out = PngOptimizer.optimize(src)
        assertNotNull(out)
        assertTrue("smaller", out!!.size < src.size)
        assertArrayEquals(pixels(src), pixels(out))
    }

    @Test fun manyColorsStayIdenticalPixels() {
        val rnd = Random(7)
        val img = BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 48) for (x in 0 until 48) img.setRGB(x, y, (rnd.nextInt() and 0x00FFFFFF) or 0xFF000000.toInt())
        val src = png(img)
        val out = PngOptimizer.optimize(src)
        if (out != null) assertArrayEquals(pixels(src), pixels(out))
    }

    @Test fun garbageAndTruncatedFilesAreIgnored() {
        assertNull(PngOptimizer.optimize(ByteArray(0)))
        assertNull(PngOptimizer.optimize("not a png at all, just some text".toByteArray()))
        val src = png(BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB))
        assertNull(PngOptimizer.optimize(src.copyOf(src.size / 2)))
    }

    @Test fun minifyKeepsDataAndDropsWhitespace() {
        val min = Js.minify("{ // note\n \"a\": [1, 2.50, \"x y\"],\n \"b\": {\"c\": true,} }")!!
        assertEquals("{\"a\":[1,2.50,\"x y\"],\"b\":{\"c\":true}}", min)
        assertNull(Js.minify("{ broken"))
    }
}
