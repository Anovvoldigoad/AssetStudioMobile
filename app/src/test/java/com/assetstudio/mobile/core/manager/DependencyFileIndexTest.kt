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
