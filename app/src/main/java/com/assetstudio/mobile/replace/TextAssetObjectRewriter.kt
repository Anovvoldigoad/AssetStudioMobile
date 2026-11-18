package com.assetstudio.mobile.replace

import com.assetstudio.mobile.core.ObjectReader
import com.assetstudio.mobile.core.classes.TextAsset
import com.assetstudio.mobile.core.io.EndianType
import java.io.ByteArrayOutputStream

/**
 * Rebuilds a TextAsset using the actual TextAsset parser to find the
 * m_Script array offset. Never pattern-searches or edits unrelated bytes.
 */
object TextAssetObjectRewriter {
    const val MAX_TEXT_BYTES = 2 * 1024 * 1024

    fun buildReplacement(asset: TextAsset, text: String): ByteArray {
        val payload = text.toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_TEXT_BYTES) { "Teks terlalu besar (maksimal 2 MB)" }
        val file = asset.assetsFile
        val info = file.m_Objects.singleOrNull { it.m_PathID == asset.m_PathID }
            ?: throw IllegalArgumentException("PathID TextAsset tidak ditemukan")
        val start = info.byteStart.toInt()
        val size = info.byteSize.toInt()
        require(start >= 0 && size > 0 && start.toLong() + size <= file.fileBytes.size) {
            "Rentang TextAsset tidak valid"
        }

        // Fresh reader, so no shared cursor/state corruption from UI inspection.
        val reader = ObjectReader(file.fileBytes, file, info)
        val parsed = TextAsset(reader)
        require(parsed.m_Script.contentEquals(asset.m_Script)) {
            "Data TextAsset berbeda dari sumber"
        }
        val lengthOffset = reader.position - parsed.m_Script.size - 4 - start
        val raw = file.fileBytes.copyOfRange(start, start + size)
        return replaceTextPayload(raw, lengthOffset, parsed.m_Script, payload, file.readerEndian)
    }
}

/**
 * Pure, testable script-array rewrite; validates the embedded length and data.
 * A trailing 1..3 zero bytes are treated as optional object-alignment padding.
 * Nonzero or longer unknown data is rejected instead of silently discarded.
 */
fun replaceTextPayload(raw: ByteArray, lengthOffset: Int,
    oldScript: ByteArray, newScript: ByteArray, endian: EndianType): ByteArray {
    require(newScript.size <= TextAssetObjectRewriter.MAX_TEXT_BYTES) { "Teks terlalu besar" }
    require(lengthOffset >= 0 && lengthOffset <= raw.size - 4) { "Offset skrip tidak valid" }
    val readLength = if (endian == EndianType.LittleEndian) {
        (raw[lengthOffset].toInt() and 0xff) or
            ((raw[lengthOffset + 1].toInt() and 0xff) shl 8) or
            ((raw[lengthOffset + 2].toInt() and 0xff) shl 16) or
            ((raw[lengthOffset + 3].toInt() and 0xff) shl 24)
    } else {
        ((raw[lengthOffset].toInt() and 0xff) shl 24) or
            ((raw[lengthOffset + 1].toInt() and 0xff) shl 16) or
            ((raw[lengthOffset + 2].toInt() and 0xff) shl 8) or
            (raw[lengthOffset + 3].toInt() and 0xff)
    }
    require(readLength == oldScript.size) { "Panjang skrip asli tidak cocok" }
    val bodyStart = lengthOffset + 4
    val bodyEnd = bodyStart.toLong() + oldScript.size
    require(bodyEnd <= raw.size) { "Data skrip melewati batas objek" }
    for (i in oldScript.indices) {
        require(raw[bodyStart + i] == oldScript[i]) { "Skrip asli tidak cocok" }
    }
    if (oldScript.contentEquals(newScript)) return raw.copyOf()

    val trailing = raw.size - bodyEnd.toInt()
    require(trailing in 0..3 &&
        (0 until trailing).all { raw[bodyEnd.toInt() + it].toInt() == 0 }) {
        "TextAsset berisi data tambahan yang belum didukung; perubahan dibatalkan"
    }
    val pad = if (trailing == 0) 0 else (4 - ((bodyStart + newScript.size) % 4)) % 4
    val out = ByteArrayOutputStream(bodyStart + newScript.size + pad)
    out.write(raw, 0, lengthOffset)
    val count = newScript.size
    for (n in 0..3) {
        val bits = if (endian == EndianType.LittleEndian) n * 8 else (3 - n) * 8
        out.write((count ushr bits) and 0xff)
    }
    out.write(newScript)
    repeat(pad) { out.write(0) }
    return out.toByteArray()
}
