package com.assetstudio.mobile.core

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class InlineResourceTest {
    @Test fun validRangesStayExact() {
        val data = byteArrayOf(10, 20, 30, 40, 50)
        assertContentEquals(byteArrayOf(20, 30, 40),
            resourceReaderOfData(data, 1, 3).getData())
        assertContentEquals(byteArrayOf(), resourceReaderOfData(data, 5, 0).getData())
    }

    @Test fun missingOrOverflownRangesFail() {
        val data = byteArrayOf(1, 2, 3)
        listOf(-1 to 2, 0 to -1, 2 to 2, 4 to 0, Int.MAX_VALUE to 2)
            .forEach { (offset, length) ->
                assertFailsWith<IOException> {
                    resourceReaderOfData(data, offset, length).getData()
                }
            }
    }

    @Test fun originalArrayCannotBeMutatedThroughSlice() {
        val data = byteArrayOf(1, 2, 3)
        val output = resourceReaderOfData(data, 0, 2).getData()
        output[0] = 99
        assertContentEquals(byteArrayOf(1, 2, 3), data)
    }
}
