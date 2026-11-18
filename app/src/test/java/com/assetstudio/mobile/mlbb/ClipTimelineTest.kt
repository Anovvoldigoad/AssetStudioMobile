package com.assetstudio.mobile.mlbb

import com.assetstudio.mobile.ui.preview.ClipTrack
import com.assetstudio.mobile.ui.preview.previewDuration
import kotlin.test.Test
import kotlin.test.assertEquals

class ClipTimelineTest {
    @Test fun keepsLargestValidTime() {
        val tracks = listOf(ClipTrack("a", listOf(0f to 1f, 2.5f to 7f)))
        assertEquals(3f, previewDuration(tracks, 3f, 2f))
        assertEquals(4f, previewDuration(tracks, 0f, 4f))
    }
    @Test fun ignoresNonFiniteAndNegativeTimes() {
        val tracks = listOf(ClipTrack("a", listOf(Float.NaN to 1f,
            Float.POSITIVE_INFINITY to 1f, (-20f) to 1f)))
        assertEquals(0.1f, previewDuration(tracks, Float.NaN, Float.NEGATIVE_INFINITY))
    }
    @Test fun emptyClipStillHasScrubRange() {
        assertEquals(0.1f, previewDuration(emptyList(), 0f, 0f))
    }
}
