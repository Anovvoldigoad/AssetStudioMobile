package com.assetstudio.mobile.core.classes

/**
 * Detect raw containers supported by Android MediaPlayer.
 * This is not an FSB5 decoder: FSB5 non-PCM requires separate processing.
 */
internal object AndroidAudioProbe {
    fun fileExtension(data: ByteArray): String? {
        if (data.size < 4) return null
        fun startsWith(value: String): Boolean {
            val bytes = value.toByteArray(Charsets.US_ASCII)
            return data.size >= bytes.size && bytes.indices.all { data[it] == bytes[it] }
        }
        return when {
            data.size >= 12 && startsWith("RIFF") &&
                data.copyOfRange(8, 12).contentEquals("WAVE".toByteArray()) -> "wav"
            startsWith("OggS") -> "ogg"
            startsWith("fLaC") -> "flac"
            startsWith("ID3") -> "mp3"
            (data[0].toInt() and 255) == 0xff &&
                (data[1].toInt() and 0xe6) == 0xe2 -> "mp3"
            (data[0].toInt() and 255) == 0xff &&
                (data[1].toInt() and 0xf6) == 0xf0 -> "aac"
            else -> null
        }
    }
}
