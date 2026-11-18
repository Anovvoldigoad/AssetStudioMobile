package com.assetstudio.mobile.core

/*
 * 来源: AssetStudio/ResourceReader.cs
 *
 * C# 版基于 Stream/BinaryReader 延迟读取；Kotlin 版统一改为 lambda 提供器：
 * - 内联数据路径: provider 捕获 (fileBuffer, absoluteOffset, size)，与 reader 当前位置解耦，
 *   保证 reader 之后被复用/移动仍能读到正确数据。
 * - 流数据路径 (StreamingInfo): provider 在调用时才从 assetsManager.resourceFileReaders
 *   按文件名解析资源文件字节，再截取 [offset, offset+size)。
 */

import com.assetstudio.mobile.core.io.EndianBinaryReader
import com.assetstudio.mobile.core.io.FileReader
import com.assetstudio.mobile.core.io.getFileName
import com.assetstudio.mobile.core.serialized.SerializedFile
import java.io.FileNotFoundException
import java.io.IOException

/**
 * 资源数据延迟读取器，供 Texture2D/AudioClip/VideoClip 使用。
 */
class ResourceReader(private val provider: () -> ByteArray, val size: Long) {

    constructor(provider: () -> ByteArray, size: Int) : this(provider, size.toLong())

    /** 读取全部数据（每次调用重新执行 provider，注意大数据多次调用会产生多份拷贝） */
    fun getData(): ByteArray = provider()

    /** 数据是否为空 */
    val isEmpty: Boolean get() = size <= 0L
}

/**
 * 流式资源（.resS）解析辅助。
 *
 * 查找顺序：
 * 1. assetsFile.assetsManager.resourceFileReaders[文件名]（兼容 ByteArray / FileReader /
 *    EndianBinaryReader / ResourceReader 等注册形态）
 * 2. [ResourceFileRegistry] 中注册的内存资源（AssetsManager 未接线时的兜底）
 */
object ResourceFileResolver {

    /** 按 ignoreCase 匹配资源名 */
    fun resolve(assetsFile: SerializedFile, path: String): ByteArray {
        val resourceFileName = getFileName(path)
        val candidate = lookupFromManager(assetsFile, resourceFileName)
            ?: assetsFile.assetsManager.getResourceFileData(resourceFileName)
            ?: ResourceFileRegistry.lookup(resourceFileName)
            ?: throw FileNotFoundException("Can't find the resource file $resourceFileName")
        return candidate
    }

    /** 从资源文件字节中截取 [offset, offset+size) */
    fun slice(data: ByteArray, offset: Long, size: Long): ByteArray {
        if (offset < 0 || size < 0 || offset > data.size.toLong() ||
            size > data.size.toLong() - offset) {
            throw IOException(
                "External resource incomplete: offset=$offset length=$size fileSize=${data.size}"
            )
        }
        val start = offset.toInt()
        return data.copyOfRange(start, (offset + size).toInt())
    }

    /** 解析流数据：查找资源文件并截取对应区间 */
    fun readStreamData(assetsFile: SerializedFile, path: String, offset: Long, size: Long): ByteArray {
        val fileName = getFileName(path)
        // Already decoded from bundle? Reuse registered bytes.
        val stored = lookupFromManager(assetsFile, fileName)
        if (stored != null) return slice(stored, offset, size)
        // Android filesystem: read only the requested range of .resS.
        // This works even if the parent resource file is hundreds of MB.
        val stream = assetsFile.assetsManager.externalResourceSliceOpener
        val range = try { stream?.invoke(path, offset, size) } catch (_: IOException) { null }
        if (range != null) {
            if (range.size.toLong() != size) {
                throw IOException("External resource slice incomplete: $fileName")
            }
            return range
        }
        // SAF/memory-backed bundles still use the original fallback.
        val data = resolve(assetsFile, path)
        return slice(data, offset, size)
    }

    @Suppress("UNCHECKED_CAST")
    private fun lookupFromManager(assetsFile: SerializedFile, resourceFileName: String): ByteArray? {
        val readers = try {
            assetsFile.assetsManager.resourceFileReaders
        } catch (e: Throwable) {
            return null
        } ?: return null
        val raw = readers[resourceFileName]
            ?: readers.entries.firstOrNull { it.key.equals(resourceFileName, ignoreCase = true) }?.value
            ?: return null
        return when (raw) {
            is ByteArray -> raw
            is FileReader -> raw.data
            is EndianBinaryReader -> raw.buffer
            is ResourceReader -> raw.getData()
            else -> null
        }
    }
}

/**
 * 简单的内存资源注册表（fileName -> 资源文件全部字节）。
 * 当 AssetsManager.resourceFileReaders 未包含目标时作为兜底查找源。
 */
object ResourceFileRegistry {

    private val readers = LinkedHashMap<String, ByteArray>()

    fun register(fileName: String, data: ByteArray) {
        readers[fileName] = data
    }

    fun unregister(fileName: String) {
        readers.remove(fileName)
    }

    fun clear() = readers.clear()

    fun lookup(fileName: String): ByteArray? =
        readers[fileName] ?: readers.entries.firstOrNull { it.key.equals(fileName, ignoreCase = true) }?.value
}

/** 便捷工厂：内联数据（捕获缓冲与绝对偏移，避免 reader 位置变化影响） */
fun resourceReaderOfData(buffer: ByteArray, offset: Int, size: Int): ResourceReader =
    ResourceReader(
        provider = {
            if (offset < 0 || size < 0 || offset > buffer.size ||
                size > buffer.size - offset) {
                throw IOException("Inline resource incomplete: offset=$offset length=$size fileSize=${buffer.size}")
            }
            buffer.copyOfRange(offset, offset + size)
        },
        size = size.toLong()
    )

/** 便捷工厂：流数据（.resS，延迟到 getData 时才解析资源文件） */
fun resourceReaderOfStream(
    assetsFile: SerializedFile,
    path: String,
    offset: Long,
    size: Long
): ResourceReader =
    ResourceReader(
        provider = { ResourceFileResolver.readStreamData(assetsFile, path, offset, size) },
        size = size
    )
