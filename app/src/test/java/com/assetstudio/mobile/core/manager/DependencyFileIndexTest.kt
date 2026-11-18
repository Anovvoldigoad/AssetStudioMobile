package com.assetstudio.mobile.core.manager

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertContentEquals

class DependencyFileIndexTest {
    @Test fun resolvesSiblingResourceAndDoesNotInventMissingFile() {
        val dir = createTempDir(prefix = "mlbb-dep-")
        try {
            val asset = File(dir, "hero.assets").apply { writeText("reference") }
            File(dir, "HERO.RESS").writeBytes(byteArrayOf(1, 2, 3))
            val index = DependencyFileIndex.fromFiles(listOf(asset))
            assertEquals(DependencyFileIndex.State.FOUND, index.inspect("archive:/hero.ress").state)
            assertContentEquals(byteArrayOf(1, 2, 3), index.readUnique("hero.resS"))
            assertEquals(DependencyFileIndex.State.MISSING, index.inspect("missing.resS").state)
            assertNull(index.readUnique("missing.resS"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun exactParentSuffixDisambiguatesRepeatedResourceNames() {
        val root = createTempDir(prefix = "mlbb-scoped-sidecar-")
        try {
            val effects = File(root, "effects").apply { mkdirs() }
            val heroes = File(root, "heroes").apply { mkdirs() }
            val a = File(effects, "data.resS").apply { writeBytes(byteArrayOf(9, 8, 7, 6)) }
            val b = File(heroes, "DATA.RESS").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val index = DependencyFileIndex.fromFiles(listOf(a, b), false)
            assertEquals(DependencyFileIndex.State.AMBIGUOUS, index.inspect("data.resS").state)
            assertEquals(DependencyFileIndex.State.FOUND,
                index.inspect("effects/data.resS").state)
            assertContentEquals(byteArrayOf(9, 8, 7, 6), index.readUnique("effects/data.resS"))
            assertContentEquals(byteArrayOf(2, 3), index.readSlice("heroes/data.resS", 1, 2))
            assertNull(index.readUnique("other/data.resS"))
        } finally { root.deleteRecursively() }
    }

    @Test fun duplicateBasenamesAreRejected() {
        val dir = createTempDir(prefix = "mlbb-dep-collision-")
        try {
            val a = File(dir, "a").apply { mkdirs() }
            val b = File(dir, "b").apply { mkdirs() }
            val first = File(a, "data.resS").apply { writeText("A") }
            val second = File(b, "DATA.RESS").apply { writeText("B") }
            val index = DependencyFileIndex.fromFiles(listOf(first, second), false)
            assertEquals(DependencyFileIndex.State.AMBIGUOUS, index.inspect("data.resS").state)
            assertNull(index.readUnique("data.resS"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun readsByteRangeFromVeryLargeSidecar() {
        val dir = createTempDir(prefix = "mlbb-dep-stream-")
        try {
            val path = File(dir, "huge.resS")
            java.io.RandomAccessFile(path, "rw").use { file ->
                file.setLength(110L * 1024 * 1024)
                file.seek(109L * 1024 * 1024)
                file.write(byteArrayOf(8, 7, 6, 5))
            }
            val index = DependencyFileIndex.fromFiles(listOf(path), false)
            assertEquals(DependencyFileIndex.State.TOO_LARGE, index.inspect("huge.resS").state)
            assertContentEquals(byteArrayOf(8, 7, 6, 5),
                index.readSlice("huge.resS", 109L * 1024 * 1024, 4))
            assertNull(index.readSlice("huge.resS", 110L * 1024 * 1024 - 1, 10))
        } finally { dir.deleteRecursively() }
    }

    @Test fun oversizedResourceIsNotRead() {
        val dir = createTempDir(prefix = "mlbb-dep-limit-")
        try {
            val resource = File(dir, "audio.resS").apply { writeBytes(ByteArray(9)) }
            val index = DependencyFileIndex.fromFiles(listOf(resource), false)
            assertEquals(DependencyFileIndex.State.TOO_LARGE,
                index.inspect("audio.resS", 8).state)
            assertNull(index.readUnique("audio.resS", 8))
        } finally { dir.deleteRecursively() }
    }
}
