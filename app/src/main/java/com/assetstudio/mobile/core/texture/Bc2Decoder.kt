package com.assetstudio.mobile.core.texture

/**
 * BC2 / DXT3: 16-byte blocks covering a 4x4 image area.
 * Bytes 0..7: explicit 4-bit alpha per texel, low nibble first.
 * Bytes 8..15: two RGB565 endpoints + 16 two-bit indices (four-color mode).
 */
internal object Bc2Decoder {
    fun decode(data: ByteArray, width: Int, height: Int): IntArray? {
        if (width <= 0 || height <= 0) return null
        val pixels = width.toLong() * height.toLong()
        if (pixels > Int.MAX_VALUE) return null
        val blocksX = (width.toLong() + 3L) / 4L
        val blocksY = (height.toLong() + 3L) / 4L
        val needed = blocksX * blocksY * 16L
        if (needed > data.size.toLong()) return null

        val output = IntArray(pixels.toInt())
        fun u16(offset: Int) = (data[offset].toInt() and 255) or
            ((data[offset + 1].toInt() and 255) shl 8)
        fun rgb565(value: Int): Int {
            val r5 = (value ushr 11) and 31
            val g6 = (value ushr 5) and 63
            val b5 = value and 31
            val r = (r5 shl 3) or (r5 ushr 2)
            val g = (g6 shl 2) or (g6 ushr 4)
            val b = (b5 shl 3) or (b5 ushr 2)
            return (r shl 16) or (g shl 8) or b
        }
        fun interpolate(c0: Int, c1: Int, w0: Int, w1: Int): Int {
            val r = (((c0 ushr 16) and 255) * w0 + ((c1 ushr 16) and 255) * w1) / 3
            val g = (((c0 ushr 8) and 255) * w0 + ((c1 ushr 8) and 255) * w1) / 3
            val b = ((c0 and 255) * w0 + (c1 and 255) * w1) / 3
            return (r shl 16) or (g shl 8) or b
        }

        for (by in 0 until blocksY.toInt()) {
            for (bx in 0 until blocksX.toInt()) {
                val offset = (by * blocksX + bx).toInt() * 16
                val color0 = rgb565(u16(offset + 8))
                val color1 = rgb565(u16(offset + 10))
                val colors = intArrayOf(
                    color0, color1, interpolate(color0, color1, 2, 1),
                    interpolate(color0, color1, 1, 2)
                )
                for (y in 0..3) {
                    val py = by * 4 + y
                    if (py >= height) continue
                    val alphaRow = u16(offset + y * 2)
                    val bitsAtRow = offset + 12 + y
                    val indices = data[bitsAtRow].toInt() and 255
                    for (x in 0..3) {
                        val px = bx * 4 + x
                        if (px >= width) continue
                        val alpha = ((alphaRow ushr (4 * x)) and 15) * 17
                        val index = (indices ushr (2 * x)) and 3
                        output[py * width + px] = (alpha shl 24) or colors[index]
                    }
                }
            }
        }
        return output
    }
}
