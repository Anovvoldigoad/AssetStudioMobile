package com.assetstudio.mobile.core

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class ResourceSliceTest {
    private val file = byteArrayOf(1, 2, 3, 4, 5)

    @Test fun exactSlice() {
        assertContentEquals(byteArrayOf(2, 3, 4), ResourceFileResolver.slice(file, 1, 3))
        assertContentEquals(byteArrayOf(), ResourceFileResolver.slice(file, 5, 0))
    }

    @Test fun rejectsTruncationAndOverflow() {
        assertFailsWith<IOException> { ResourceFileResolver.slice(file, 3, 3) }
        assertFailsWith<IOException> { ResourceFileResolver.slice(file, Long.MAX_VALUE, 10) }
        assertFailsWith<IOException> { ResourceFileResolver.slice(file, -1, 1) }
        assertFailsWith<IOException> { ResourceFileResolver.slice(file, 0, -1) }
    }
}
