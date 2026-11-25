package dev.kyle.mctools.core

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.abs

/**
 * Lossless PNG optimizer in pure Kotlin: drops metadata chunks, converts to a (bit-packed) palette when the
 * image has 256 colors or fewer, re-picks row filters, and recompresses at maximum level.
 * Returns the smaller PNG, or null when nothing could be saved or the file is unusual (animated, damaged...).
 */
object PngOptimizer {
    private val SIG = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private const val MAX_PIXEL_BYTES = 64 * 1024 * 1024

    private class Chunk(val type: String, val data: ByteArray)
    private class Pal(val depth: Int, val plte: ByteArray, val trns: ByteArray?, val raw: ByteArray)

    fun optimize(src: ByteArray): ByteArray? = try {
        run(src)
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun be32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun run(src: ByteArray): ByteArray? {
        if (src.size < 33 || !src.copyOfRange(0, 8).contentEquals(SIG)) return null
        val chunks = ArrayList<Chunk>()
        var p = 8
        while (p + 12 <= src.size) {
            val len = be32(src, p)
            if (len < 0 || p + 12 + len > src.size) return null
            val type = String(src, p + 4, 4, Charsets.ISO_8859_1)
            chunks += Chunk(type, src.copyOfRange(p + 8, p + 8 + len))
            p += 12 + len
            if (type == "IEND") break
        }
        if (chunks.isEmpty() || chunks.first().type != "IHDR" || chunks.last().type != "IEND") return null
        if (chunks.any { it.type == "acTL" || it.type == "fdAT" }) return null
        val ihdr = chunks.first().data
        if (ihdr.size != 13) return null
        val w = be32(ihdr, 0)
        val h = be32(ihdr, 4)
        val depth = ihdr[8].toInt() and 0xFF
        val ctype = ihdr[9].toInt() and 0xFF
        val interlace = ihdr[12].toInt() and 0xFF
        if (w <= 0 || h <= 0) return null

        val idat = ByteArrayOutputStream()
        chunks.filter { it.type == "IDAT" }.forEach { idat.write(it.data) }
        val raw = inflate(idat.toByteArray()) ?: return null
        val plte = chunks.firstOrNull { it.type == "PLTE" }
        val trns = chunks.firstOrNull { it.type == "tRNS" }

        var outIhdr = ihdr
        var newPlte: ByteArray? = plte?.data
        var newTrns: ByteArray? = trns?.data
        var newRaw: ByteArray = raw
        val bpp = when (ctype) { 2 -> 3; 6 -> 4; else -> 0 }
        if (interlace == 0 && depth == 8 && bpp > 0 && !(ctype == 2 && trns != null) && w.toLong() * h * bpp <= MAX_PIXEL_BYTES) {
            val pix = unfilter(raw, w, h, bpp)
            if (pix != null) {
                val pal = tryPalette(pix, w, h, bpp)
                if (pal != null) {
                    outIhdr = ihdr.copyOf().also { it[8] = pal.depth.toByte(); it[9] = 3 }
                    newPlte = pal.plte; newTrns = pal.trns; newRaw = pal.raw
                } else {
                    newRaw = filterRows(pix, w, h, bpp)
                }
            }
        }
        val out = ByteArrayOutputStream()
        out.write(SIG)
        writeChunk(out, "IHDR", outIhdr)
        newPlte?.let { writeChunk(out, "PLTE", it) }
        newTrns?.let { writeChunk(out, "tRNS", it) }
        writeChunk(out, "IDAT", deflate(newRaw))
        writeChunk(out, "IEND", ByteArray(0))
        val res = out.toByteArray()
        return if (res.size < src.size) res else null
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a); val pb = abs(p - b); val pc = abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }

    private fun unfilter(raw: ByteArray, w: Int, h: Int, bpp: Int): ByteArray? {
        val stride = w * bpp
        if (raw.size != (stride + 1) * h) return null
        val out = ByteArray(stride * h)
        for (y in 0 until h) {
            val f = raw[y * (stride + 1)].toInt()
            val inOff = y * (stride + 1) + 1
            val outOff = y * stride
            val prevOff = outOff - stride
            for (x in 0 until stride) {
                val cur = raw[inOff + x].toInt() and 0xFF
                val a = if (x >= bpp) out[outOff + x - bpp].toInt() and 0xFF else 0
                val b = if (y > 0) out[prevOff + x].toInt() and 0xFF else 0
                val c = if (y > 0 && x >= bpp) out[prevOff + x - bpp].toInt() and 0xFF else 0
                val v = when (f) {
                    0 -> cur
                    1 -> cur + a
                    2 -> cur + b
                    3 -> cur + ((a + b) shr 1)
                    4 -> cur + paeth(a, b, c)
                    else -> return null
                }
                out[outOff + x] = v.toByte()
            }
        }
        return out
    }

    private fun filterRows(pix: ByteArray, w: Int, h: Int, bpp: Int): ByteArray {
        val stride = w * bpp
        val out = ByteArray((stride + 1) * h)
        val cand = Array(5) { ByteArray(stride) }
        for (y in 0 until h) {
            val off = y * stride
            for (x in 0 until stride) {
                val cur = pix[off + x].toInt() and 0xFF
                val a = if (x >= bpp) pix[off + x - bpp].toInt() and 0xFF else 0
                val b = if (y > 0) pix[off - stride + x].toInt() and 0xFF else 0
                val c = if (y > 0 && x >= bpp) pix[off - stride + x - bpp].toInt() and 0xFF else 0
                cand[0][x] = cur.toByte()
                cand[1][x] = (cur - a).toByte()
                cand[2][x] = (cur - b).toByte()
                cand[3][x] = (cur - ((a + b) shr 1)).toByte()
                cand[4][x] = (cur - paeth(a, b, c)).toByte()
            }
            var best = 0
            var bestSum = Long.MAX_VALUE
            for (f in 0 until 5) {
                var sum = 0L
                for (x in 0 until stride) sum += abs(cand[f][x].toInt())
                if (sum < bestSum) { bestSum = sum; best = f }
            }
            out[y * (stride + 1)] = best.toByte()
            System.arraycopy(cand[best], 0, out, y * (stride + 1) + 1, stride)
        }
        return out
    }

    private fun tryPalette(pix: ByteArray, w: Int, h: Int, bpp: Int): Pal? {
        val n = w * h
        val keys = IntArray(n)
        val seen = HashSet<Int>()
        val colors = ArrayList<Int>()
        for (i in 0 until n) {
            val o = i * bpp
            val r = pix[o].toInt() and 0xFF
            val g = pix[o + 1].toInt() and 0xFF
            val b = pix[o + 2].toInt() and 0xFF
            val a = if (bpp == 4) pix[o + 3].toInt() and 0xFF else 255
            val k = (a shl 24) or (r shl 16) or (g shl 8) or b
            keys[i] = k
            if (seen.add(k)) { if (colors.size >= 256) return null; colors.add(k) }
        }
        val ordered = colors.filter { (it ushr 24) != 255 } + colors.filter { (it ushr 24) == 255 }
        val nTrans = colors.count { (it ushr 24) != 255 }
        val index = HashMap<Int, Int>()
        ordered.forEachIndexed { i, k -> index[k] = i }
        val plte = ByteArray(ordered.size * 3)
        ordered.forEachIndexed { i, k ->
            plte[i * 3] = (k shr 16).toByte(); plte[i * 3 + 1] = (k shr 8).toByte(); plte[i * 3 + 2] = k.toByte()
        }
        val trns = if (nTrans > 0) ByteArray(nTrans) { i -> (ordered[i] ushr 24).toByte() } else null
        val depth = when { ordered.size <= 2 -> 1; ordered.size <= 4 -> 2; ordered.size <= 16 -> 4; else -> 8 }
        val rowBytes = (w * depth + 7) / 8
        val raw = ByteArray((rowBytes + 1) * h)
        for (y in 0 until h) {
            val ro = y * (rowBytes + 1)
            for (x in 0 until w) {
                val idx = index[keys[y * w + x]]!!
                if (depth == 8) {
                    raw[ro + 1 + x] = idx.toByte()
                } else {
                    val bit = x * depth
                    val bi = ro + 1 + bit / 8
                    val shift = 8 - depth - (bit % 8)
                    raw[bi] = (raw[bi].toInt() or (idx shl shift)).toByte()
                }
            }
        }
        return Pal(depth, plte, trns, raw)
    }

    private fun inflate(d: ByteArray): ByteArray? {
        val inf = Inflater()
        inf.setInput(d)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(65536)
        try {
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > MAX_PIXEL_BYTES + 1_000_000) return null
            }
            if (!inf.finished()) return null
        } catch (e: DataFormatException) {
            return null
        } finally {
            inf.end()
        }
        return out.toByteArray()
    }

    private fun deflate(d: ByteArray): ByteArray {
        val def = Deflater(9)
        def.setInput(d)
        def.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(65536)
        while (!def.finished()) out.write(buf, 0, def.deflate(buf))
        def.end()
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val len = data.size
        out.write(byteArrayOf((len ushr 24).toByte(), (len ushr 16).toByte(), (len ushr 8).toByte(), len.toByte()))
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        val c = crc.value
        out.write(byteArrayOf((c ushr 24).toByte(), (c ushr 16).toByte(), (c ushr 8).toByte(), c.toByte()))
    }
}
