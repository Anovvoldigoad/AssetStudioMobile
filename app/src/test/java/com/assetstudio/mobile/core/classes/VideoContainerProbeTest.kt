package com.assetstudio.mobile.core.classes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoContainerProbeTest {
    @Test fun recognizesMp4And3gp() {
        assertEquals("mp4", VideoContainerProbe.extension(
            byteArrayOf(0, 0, 0, 24) + "ftypisom1234".toByteArray()))
        assertEquals("3gp", VideoContainerProbe.extension(
            byteArrayOf(0, 0, 0, 24) + "ftyp3gp61234".toByteArray()))
    }
    @Test fun recognizesWebmAndRejectsUnknown() {
        assertEquals("webm", VideoContainerProbe.extension(
            byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte()) +
                ByteArray(12)))
        assertNull(VideoContainerProbe.extension("UnityFS12345678".toByteArray()))
        assertNull(VideoContainerProbe.extension(ByteArray(4)))
    }
}
