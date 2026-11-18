package com.assetstudio.mobile.core.classes

/** Inspect the file signature, not the Unity asset name or its extension. */
internal object VideoContainerProbe {
    fun extension(data: ByteArray): String? {
        if (data.size < 12) return null
        if (data[4] == 'f'.code.toByte() && data[5] == 't'.code.toByte() &&
            data[6] == 'y'.code.toByte() && data[7] == 'p'.code.toByte()) {
            return if (data.size >= 11 && data[8] == '3'.code.toByte() &&
                data[9] == 'g'.code.toByte() && data[10] == 'p'.code.toByte()) "3gp"
            else "mp4"
        }
        if (data[0] == 0x1a.toByte() && data[1] == 0x45.toByte() &&
            data[2] == 0xdf.toByte() && data[3] == 0xa3.toByte()) return "webm"
        return null
    }
}
