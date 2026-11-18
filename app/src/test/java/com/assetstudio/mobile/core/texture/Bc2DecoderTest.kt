package com.assetstudio.mobile.core.texture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Bc2DecoderTest {
    /** 4x4, alpha FF; red / green / 2:1 mix / 1:2 mix columns. */
    private fun sample(): ByteArray = byteArrayOf(
        0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
        0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
        0x00, 0xf8.toByte(), 0xe0.toByte(), 0x07,
        0xe4.toByte(), 0xe4.toByte(), 0xe4.toByte(), 0xe4.toByte()
    )

    @Test fun rgbaEndpointAndInterpolation() {
        val out = Bc2Decoder.decode(sample(), 4, 4)!!
        assertEquals(16, out.size)
        assertEquals(0xffff0000.toInt(), out[0])
        assertEquals(0xff00ff00.toInt(), out[1])
        assertEquals(0xffaa5500.toInt(), out[2])
        assertEquals(0xff55aa00.toInt(), out[3])
        assertEquals(out[0], out[12])
    }

    @Test fun alphaNibblesAreExplicit() {
        val block = sample().also {
            it[0] = 0x10
            it[1] = 0x8f.toByte()
        }
        val out = Bc2Decoder.decode(block, 4, 4)!!
        assertEquals(0x00ff0000, out[0])
        assertEquals(0x1100ff00, out[1])
        assertEquals(0xffaa5500.toInt(), out[2])
        assertEquals(0x8855aa00.toInt(), out[3])
    }

    @Test fun partialEdgeBlocksAreClippedAndShortInputRejected() {
        val oneBlock = sample()
        val clipped = Bc2Decoder.decode(oneBlock, 2, 3)!!
        assertEquals(6, clipped.size)
        assertEquals(0xffff0000.toInt(), clipped[0])
        assertEquals(0xff00ff00.toInt(), clipped[5])
        assertNull(Bc2Decoder.decode(oneBlock.copyOf(15), 4, 4))
        assertNull(Bc2Decoder.decode(oneBlock, 5, 4))
        assertNull(Bc2Decoder.decode(oneBlock, 0, 4))
        assertNull(Bc2Decoder.decode(oneBlock, Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test fun colorInterpolationNeverUsesBc1TransparentMode() {
        val reverseColors = sample().also {
            it[8] = 0xe0.toByte()
            it[9] = 0x07
            it[10] = 0x00
            it[11] = 0xf8.toByte()
        }
        val out = Bc2Decoder.decode(reverseColors, 4, 4)!!
        assertEquals(0xff00ff00.toInt(), out[0])
        assertEquals(0xff55aa00.toInt(), out[2])
        assertEquals(0xffaa5500.toInt(), out[3])
    }
}
