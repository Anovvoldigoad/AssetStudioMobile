package com.assetstudio.mobile.mlbb

import com.assetstudio.mobile.core.io.EndianType
import com.assetstudio.mobile.replace.replaceTextPayload
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class TextAssetRewriteTest {
    private val prefix = byteArrayOf(2, 3, 4, 5, 6, 7, 8, 9)
    @Test fun littleEndianChangesLengthAndPreservesPrefix() {
        val source = prefix + byteArrayOf(3, 0, 0, 0) + "abc".toByteArray() + byteArrayOf(0)
        val out = replaceTextPayload(source, 8, "abc".toByteArray(), "Hello".toByteArray(),
            EndianType.LittleEndian)
        assertContentEquals(prefix, out.copyOfRange(0, 8))
        assertEquals(5, out[8].toInt())
        assertContentEquals("Hello".toByteArray(), out.copyOfRange(12, 17))
        assertEquals(0, out[17].toInt())
        assertEquals(20, out.size)
    }
    @Test fun bigEndianWorks() {
        val source = byteArrayOf(0, 0, 0, 3) + "abc".toByteArray()
        val out = replaceTextPayload(source, 0, "abc".toByteArray(), "XX".toByteArray(),
            EndianType.BigEndian)
        assertContentEquals(byteArrayOf(0, 0, 0, 2), out.copyOfRange(0, 4))
        assertContentEquals("XX".toByteArray(), out.copyOfRange(4, 6))
    }
    @Test fun identityPreservesBytes() {
        val source = byteArrayOf(3, 0, 0, 0) + "abc".toByteArray()
        assertContentEquals(source, replaceTextPayload(source, 0, "abc".toByteArray(),
            "abc".toByteArray(), EndianType.LittleEndian))
    }
    @Test fun malformedDataIsRejected() {
        val source = byteArrayOf(3, 0, 0, 0) + "abc".toByteArray() + byteArrayOf(7)
        assertFailsWith<IllegalArgumentException> {
            replaceTextPayload(source, 0, "abc".toByteArray(), "q".toByteArray(),
                EndianType.LittleEndian)
        }
        assertFailsWith<IllegalArgumentException> {
            replaceTextPayload(byteArrayOf(4, 0, 0, 0, 7), 0, "abc".toByteArray(),
                "x".toByteArray(), EndianType.LittleEndian)
        }
    }
}
