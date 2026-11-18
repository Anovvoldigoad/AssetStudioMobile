package com.assetstudio.mobile.core.manager

import com.assetstudio.mobile.core.io.getFileName
import java.io.File
import java.util.Locale

/**
 * Builds a read-only dependency index from selected files and their immediate
 * parent folders. Case-insensitive names match Unity external references.
 * Ambiguous names deliberately never resolve to a random asset.
 */
class DependencyFileIndex private constructor(
    private val filesByName: Map<String, List<File>>
) {
    enum class State { FOUND, MISSING, AMBIGUOUS, TOO_LARGE }
    data class Result(val state: State, val matches: Int = 0)

    fun inspect(path: String, maxBytes: Long = MAX_READ_BYTES): Result {
        val key = getFileName(path).lowercase(Locale.ROOT)
        if (key.isEmpty()) return Result(State.MISSING)
        val files = filesByName[key].orEmpty().filter { it.isFile }
        return when {
            files.isEmpty() -> Result(State.MISSING)
            files.size != 1 -> Result(State.AMBIGUOUS, files.size)
            files[0].length() !in 1..maxBytes -> Result(State.TOO_LARGE, 1)
            else -> Result(State.FOUND, 1)
        }
    }

    fun readUnique(path: String, maxBytes: Long = MAX_READ_BYTES): ByteArray? {
        if (inspect(path, maxBytes).state != State.FOUND) return null
        val key = getFileName(path).lowercase(Locale.ROOT)
        val file = filesByName[key]?.singleOrNull() ?: return null
        // File could have changed between indexing and reading.
        val size = file.length()
        if (size !in 1..maxBytes) return null
        return try {
            val data = file.readBytes()
            data.takeIf { it.size.toLong() <= maxBytes }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val MAX_READ_BYTES = 96L * 1024 * 1024

        fun fromFiles(selected: List<File>, inspectSiblings: Boolean = true): DependencyFileIndex {
            val all = LinkedHashMap<String, File>()
            fun insert(f: File) {
                if (!f.isFile) return
                // Canonical paths prevent counting the selected file twice.
                val canonical = runCatching { f.canonicalFile }.getOrDefault(f.absoluteFile)
                all.putIfAbsent(canonical.path, canonical)
            }
            selected.forEach(::insert)
            if (inspectSiblings) {
                selected.mapNotNull { it.parentFile?.absoluteFile }.distinctBy { it.path }
                    .take(24).forEach { dir ->
                        // Do not perform an unbounded recursive search of Android storage.
                        val children = dir.listFiles()?.take(2500).orEmpty()
                        children.forEach(::insert)
                    }
            }
            return DependencyFileIndex(all.values.groupBy {
                it.name.lowercase(Locale.ROOT)
            })
        }
    }
}
