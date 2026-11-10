package com.assetstudio.mobile.export

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import com.assetstudio.mobile.core.ClassIDType
import com.assetstudio.mobile.core.classes.AudioClip
import com.assetstudio.mobile.core.classes.Font
import com.assetstudio.mobile.core.classes.Material
import com.assetstudio.mobile.core.classes.Mesh
import com.assetstudio.mobile.core.classes.MovieTexture
import com.assetstudio.mobile.core.classes.MonoBehaviour
import com.assetstudio.mobile.core.classes.Shader
import com.assetstudio.mobile.core.classes.Sprite
import com.assetstudio.mobile.core.classes.TextAsset
import com.assetstudio.mobile.core.classes.Texture2D
import com.assetstudio.mobile.core.classes.UnityObject
import com.assetstudio.mobile.core.classes.VideoClip
import com.assetstudio.mobile.core.serialized.TypeTreeHelper
import com.assetstudio.mobile.core.texture.PixelFlip
import com.assetstudio.mobile.core.texture.TextureConverter
import com.assetstudio.mobile.render.RenderDumper
import java.io.ByteArrayOutputStream
import java.io.File

/*
 * 资产导出器（对应 AssetStudio Exporter 的常用子集）：
 * - Texture2D / Sprite → PNG
 * - Mesh → OBJ（顶点/法线/UV0/子网格分组）
 * - Material / Shader → 文本转储（RenderDumper）
 * - AudioClip → WAV（可解码时）或原始数据
 * - TextAsset → 文本
 * - Font → 字体文件
 * - MonoBehaviour / 其他 → TypeTree 转储或原始数据
 */
object AssetExporter {

    class ExportResult(val success: Boolean, val message: String)

    /** SAF（ContentResolver + Uri）入口 */
    fun export(obj: UnityObject, resolver: ContentResolver, uri: Uri): ExportResult {
        return export(obj) {
            resolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("Tidak dapat membuka file tujuan")
        }
    }

    /** 直写文件入口（配合应用内文件管理器，后缀完全由调用方控制） */
    fun exportToFile(obj: UnityObject, file: File): ExportResult {
        return export(obj) {
            file.parentFile?.mkdirs()
            file.outputStream()
        }
    }

    fun export(obj: UnityObject, openOutput: () -> java.io.OutputStream): ExportResult {
        return try {
            when (obj) {
                is Texture2D -> exportBitmap(decodeTexture(obj), openOutput)
                is Sprite -> exportBitmap(decodeSprite(obj), openOutput)
                is Mesh -> {
                    val text = ObjExporter.write(obj)
                    writeBytes(openOutput, text.toByteArray(Charsets.UTF_8))
                    ExportResult(
                        true,
                        "OBJ berhasil diekspor (${obj.m_VertexCount}  titik, ${obj.m_SubMeshes.size}  submesh, ${obj.m_Indices.size / 3}  segitiga)"
                    )
                }
                is Material -> {
                    writeBytes(openOutput, RenderDumper.dumpMaterial(obj).toByteArray(Charsets.UTF_8))
                    ExportResult(true, "Data material berhasil diekspor")
                }
                is Shader -> {
                    writeBytes(openOutput, RenderDumper.dumpShader(obj).toByteArray(Charsets.UTF_8))
                    ExportResult(true, "Data shader berhasil diekspor")
                }
                is TextAsset -> {
                    writeBytes(openOutput, obj.m_Script)
                    ExportResult(true, "Teks berhasil diekspor")
                }
                is AudioClip -> {
                    val wav = obj.toWav()
                    if (wav != null) {
                        writeBytes(openOutput, wav)
                        ExportResult(true, "Audio WAV berhasil diekspor")
                    } else {
                        val data = obj.getAudioData()
                        writeBytes(openOutput, data)
                        ExportResult(true, "Data audio mentah berhasil diekspor (${data.size}  byte; tidak bisa dikonversi ke WAV)")
                    }
                }
                is Font -> {
                    val fontData = obj.m_FontData
                    if (fontData != null && fontData.isNotEmpty()) {
                        writeBytes(openOutput, fontData)
                        ExportResult(true, "Font berhasil diekspor")
                    } else {
                        ExportResult(false, "Font ini tidak memiliki data font yang tersimpan di dalamnya.")
                    }
                }
                is MovieTexture -> {
                    writeBytes(openOutput, obj.m_MovieData)
                    ExportResult(true, "Data video berhasil diekspor")
                }
                is VideoClip -> {
                    val data = try { obj.m_VideoData.getData() } catch (e: Exception) { null }
                    if (data != null && data.isNotEmpty()) {
                        writeBytes(openOutput, data)
                        ExportResult(true, "Data video berhasil diekspor")
                    } else {
                        ExportResult(false, "Data video kosong")
                    }
                }
                is MonoBehaviour -> exportMonoBehaviour(obj, openOutput)
                else -> exportDump(obj, openOutput)
            }
        } catch (e: Exception) {
            ExportResult(false, "Gagal mengekspor: ${e.message}")
        }
    }

    // ============================ 贴图 ============================

    fun decodeTexture(texture: Texture2D): Bitmap? {
        return try {
            if (texture.m_Width * texture.m_Height > 33_554_432) return null
            val pixels = TextureConverter(texture).decodeToPixels() ?: return null
            // Unity 纹理 row 0 = 底部（OpenGL 习惯），Bitmap row 0 = 顶部
            // 对应 C# RotateFlip(RotateNoneFlipY)；漏掉此步导出的 PNG 上下颠倒（v1.7.7 修复）
            PixelFlip.flipVerticalInPlace(pixels, texture.m_Width, texture.m_Height)
            Bitmap.createBitmap(pixels, texture.m_Width, texture.m_Height, Bitmap.Config.ARGB_8888)
        } catch (e: Throwable) {
            null
        }
    }

    fun decodeSprite(sprite: Sprite): Bitmap? {
        return try {
            val info = sprite.getTextureInfo() ?: return null
            val full = decodeTexture(info.texture) ?: return null
            val rect = info.textureRect
            val x = rect.x.toInt().coerceIn(0, full.width - 1)
            val y = rect.y.toInt().coerceIn(0, full.height - 1)
            val w = rect.width.toInt().coerceAtMost(full.width - x).coerceAtLeast(1)
            val h = rect.height.toInt().coerceAtMost(full.height - y).coerceAtLeast(1)
            val top = (full.height - y - h).coerceIn(0, full.height - 1)
            Bitmap.createBitmap(full, x, top, w, h)
        } catch (e: Throwable) {
            null
        }
    }

    private fun exportBitmap(bitmap: Bitmap?, openOutput: () -> java.io.OutputStream): ExportResult {
        if (bitmap == null) return ExportResult(false, "Gagal membuka tekstur (format tidak didukung atau data tidak ada)")
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            return ExportResult(false, "Gagal membuat PNG")
        }
        writeBytes(openOutput, out.toByteArray())
        return ExportResult(true, "PNG berhasil diekspor (${bitmap.width}x${bitmap.height})")
    }

    // ============================ 其他类型 ============================

    private fun exportMonoBehaviour(obj: MonoBehaviour, openOutput: () -> java.io.OutputStream): ExportResult {
        val typeTree = obj.reader.serializedType?.m_Type
        val dump = if (typeTree != null) {
            try {
                obj.reader.reset()
                TypeTreeHelper.readTypeString(typeTree, obj.reader)
            } catch (e: Exception) {
                null
            }
        } else null
        if (dump != null) {
            writeBytes(openOutput, dump.toByteArray(Charsets.UTF_8))
            return ExportResult(true, "Data TypeTree berhasil diekspor")
        }
        return exportDump(obj, openOutput)
    }

    private fun exportDump(obj: UnityObject, openOutput: () -> java.io.OutputStream): ExportResult {
        val info = obj.assetsFile.m_Objects.firstOrNull { it.m_PathID == obj.m_PathID }
            ?: return ExportResult(false, "Informasi objek tidak tersedia")
        val bytes = obj.assetsFile.getRawObjectBytes(info)
        writeBytes(openOutput, bytes)
        return ExportResult(true, "Data mentah berhasil diekspor (${bytes.size}  byte)")
    }

    // ============================ IO ============================

    private fun writeBytes(openOutput: () -> java.io.OutputStream, data: ByteArray) {
        openOutput().use { out ->
            out.write(data)
            out.flush()
        }
    }

    /** 按类型推断导出文件扩展名 */
    fun suggestedExtension(obj: UnityObject): String = when (obj) {
        is Texture2D, is Sprite -> "png"
        is Mesh -> "obj"
        is Material, is Shader -> "txt"
        is TextAsset -> "txt"
        is AudioClip -> "wav"
        is Font -> "ttf"
        is MovieTexture -> "ogv"
        is VideoClip -> "mp4"
        is MonoBehaviour -> "txt"
        else -> "dat"
    }
}
