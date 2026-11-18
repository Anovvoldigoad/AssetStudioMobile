package com.assetstudio.mobile.core.classes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidAudioProbeTest {
    @Test fun recognizesSimpleContainers() {
        assertEquals("wav", AndroidAudioProbe.fileExtension(
            "RIFF1234WAVEfmt ".toByteArray()))
        assertEquals("ogg", AndroidAudioProbe.fileExtension("OggS1234".toByteArray()))
        assertEquals("flac", AndroidAudioProbe.fileExtension("fLaC1234".toByteArray()))
        assertEquals("mp3", AndroidAudioProbe.fileExtension("ID3tag".toByteArray()))
        assertEquals("mp3", AndroidAudioProbe.fileExtension(
            byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0x64)))
        assertEquals("aac", AndroidAudioProbe.fileExtension(
            byteArrayOf(0xff.toByte(), 0xf1.toByte(), 0x50.toByte(), 0x80.toByte())))
    }

    @Test fun rejectsFsbAndUnknownHeaders() {
        assertNull(AndroidAudioProbe.fileExtension("FSB5data".toByteArray()))
        assertNull(AndroidAudioProbe.fileExtension("RIFF1234OGGS".toByteArray()))
        assertNull(AndroidAudioProbe.fileExtension(byteArrayOf(1, 2, 3)))
        assertNull(AndroidAudioProbe.fileExtension(byteArrayOf(0xff.toByte(), 0xf0.toByte(), 0, 0)))
    }
}
