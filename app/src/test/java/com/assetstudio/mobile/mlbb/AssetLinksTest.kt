package com.assetstudio.mobile.mlbb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssetLinksTest {
    private val assets = listOf(
        AssetCandidate("b1:hero#7", 1, 7, "hero.bundle"),
        AssetCandidate("b2:hero#7", 2, 7, "hero.bundle"),
        AssetCandidate("b1:other#7", 1, 7, "another.bundle"),
        AssetCandidate("b1:mesh#8", 1, 8, "mesh.bundle")
    )
    @Test fun selectsSameSessionAndFile() {
        assertEquals("b2:hero#7", uniqueAssetLink(2, 7, "hero.bundle", assets))
        assertEquals("b1:other#7", uniqueAssetLink(1, 7, "ANOTHER.BUNDLE", assets))
    }
    @Test fun neverLinksAcrossSessionsOrFiles() {
        assertNull(uniqueAssetLink(3, 7, "hero.bundle", assets))
        assertNull(uniqueAssetLink(1, 7, "wrong.bundle", assets))
    }
    @Test fun duplicatesFailClosed() {
        assertNull(uniqueAssetLink(1, 7, "hero.bundle",
            assets + AssetCandidate("duplicate", 1, 7, "hero.bundle")))
    }
    @Test fun unknownPathIdFailsClosed() {
        assertNull(uniqueAssetLink(1, 0, "hero.bundle", assets))
        assertNull(uniqueAssetLink(1, 9, "hero.bundle", assets))
    }
}
