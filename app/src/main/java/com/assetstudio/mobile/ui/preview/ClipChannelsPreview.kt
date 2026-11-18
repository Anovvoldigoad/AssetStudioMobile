package com.assetstudio.mobile.ui.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.assetstudio.mobile.core.classes.AnimationClip
import com.assetstudio.mobile.core.classes.Vector3Curve
import kotlinx.coroutines.delay
import kotlin.math.max

data class ClipTrack(val label: String, val samples: List<Pair<Float, Float>>)

/** Uses only decoded animation values; capped to protect Android RAM. */
fun extractClipTracks(clip: AnimationClip): List<ClipTrack> {
    val tracks = ArrayList<ClipTrack>()
    fun vec(label: String, curves: Array<Vector3Curve>) {
        curves.take(12).forEach { c ->
            val keys = c.curve.m_Curve.take(600)
            for (axis in 0..2) {
                val name = when (axis) { 0 -> "X"; 1 -> "Y"; else -> "Z" }
                tracks.add(ClipTrack(label + "/" + c.path + "/" + name,
                    keys.map { key ->
                        val value = when (axis) {
                            0 -> key.value.x
                            1 -> key.value.y
                            else -> key.value.z
                        }
                        key.time to value
                    }))
            }
        }
    }
    vec("Position", clip.m_PositionCurves)
    vec("Scale", clip.m_ScaleCurves)
    clip.m_EulerCurves?.let { vec("Euler", it) }
    clip.m_RotationCurves.take(12).forEach { curve ->
        val keys = curve.curve.m_Curve.take(600)
        for (axis in 0..3) {
            val name = listOf("X", "Y", "Z", "W")[axis]
            tracks.add(ClipTrack("Rotation/" + curve.path + "/" + name,
                keys.map { key ->
                    val value = when (axis) {
                        0 -> key.value.x
                        1 -> key.value.y
                        2 -> key.value.z
                        else -> key.value.w
                    }
                    key.time to value
                }))
        }
    }
    clip.m_FloatCurves.take(32).forEach { curve ->
        tracks.add(ClipTrack("Float/" + curve.path + "/" + curve.attribute,
            curve.curve.m_Curve.take(600).map { it.time to it.value }))
    }
    return tracks
}

/** NaN/Infinity must not reach drawing calculations. */
fun previewDuration(tracks: List<ClipTrack>, eventsEnd: Float, muscleDuration: Float): Float {
    val maxKey = tracks.asSequence().flatMap { it.samples.asSequence() }
        .map { it.first }.filter { it.isFinite() && it >= 0f }.maxOrNull() ?: 0f
    val event = eventsEnd.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val muscle = muscleDuration.takeIf { it.isFinite() && it >= 0f } ?: 0f
    return max(0.1f, max(maxKey, max(event, muscle)))
}

@Composable
fun ClipChannelsPreview(clip: AnimationClip) {
    val tracks = remember(clip) { extractClipTracks(clip) }
    val eventMax = clip.m_Events.maxOfOrNull { it.time } ?: 0f
    val muscle = clip.m_MuscleClip
    val span = if (muscle == null) 0f else muscle.m_StopTime - muscle.m_StartTime
    val duration = remember(clip, tracks) { previewDuration(tracks, eventMax, span) }
    var channel by remember(clip) { mutableIntStateOf(0) }
    var cursor by remember(clip) { mutableFloatStateOf(0f) }
    var playing by remember(clip) { mutableStateOf(false) }
    val current = tracks.getOrNull(channel)
    LaunchedEffect(playing, clip, duration) {
        while (playing) {
            delay(33)
            cursor = (cursor + 0.033f).let { if (it >= duration) 0f else it }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pratinjau channel animasi", style = MaterialTheme.typography.titleMedium)
            Text((clip.m_Name ?: "AnimationClip") + " • " +
                String.format(java.util.Locale.US, "%.2f", duration) + "s • " +
                clip.m_SampleRate + " FPS", style = MaterialTheme.typography.bodySmall)
            Text("Channel: " + tracks.size + " • Posisi: " + clip.m_PositionCurves.size +
                " • Rotasi: " + clip.m_RotationCurves.size +
                " • Skala: " + clip.m_ScaleCurves.size +
                " • Float: " + clip.m_FloatCurves.size,
                style = MaterialTheme.typography.bodySmall)
            if (tracks.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { channel = max(0, channel - 1) },
                        enabled = channel > 0) { Text("‹") }
                    Text((channel + 1).toString() + " / " + tracks.size,
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        channel = minOf(tracks.lastIndex, channel + 1)
                    }, enabled = channel < tracks.lastIndex) { Text("›") }
                }
                Text(current?.label ?: "", style = MaterialTheme.typography.labelMedium)
                val valid = remember(current) {
                    current?.samples?.filter { it.first.isFinite() && it.second.isFinite() }.orEmpty()
                }
                val curveColor = MaterialTheme.colorScheme.primary
                val markColor = MaterialTheme.colorScheme.tertiary
                Canvas(Modifier.fillMaxWidth().height(160.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)) {
                    if (valid.size >= 2) {
                        val lo = valid.minOf { it.second }
                        val delta = max(0.001f, valid.maxOf { it.second } - lo)
                        val path = Path()
                        valid.forEachIndexed { i, sample ->
                            val x = sample.first.coerceIn(0f, duration) / duration * size.width
                            val y = (1f - (sample.second - lo) / delta) * size.height
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, curveColor, style = Stroke(width = 2.dp.toPx()))
                    }
                    val x = cursor.coerceIn(0f, duration) / duration * size.width
                    drawLine(markColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                }
                if (valid.size < 2) {
                    Text("Channel ini tidak memiliki cukup keyframe untuk grafik.",
                        style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text("Tidak ada kurva standar yang dapat digambar. Clip mungkin " +
                    "menyimpan data dense, compressed, atau muscle yang memerlukan decoder lain.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { playing = !playing }) {
                    Text(if (playing) "Jeda" else "Putar timeline")
                }
                Text(String.format(java.util.Locale.US, "%.2f / %.2f s", cursor, duration),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp))
            }
            Slider(value = cursor.coerceIn(0f, duration),
                onValueChange = { cursor = it; playing = false }, valueRange = 0f..duration)
            Text("Event: " + clip.m_Events.size +
                " • Compressed rotation: " + clip.m_CompressedRotationCurves.size,
                style = MaterialTheme.typography.bodySmall)
            clip.m_Events.take(12).forEach { event ->
                Text(String.format(java.util.Locale.US, "%.2fs", event.time) +
                    " • " + event.functionName, style = MaterialTheme.typography.bodySmall)
            }
            Text("Grafik keyframe asli. Scrub timeline tidak menjalankan rig 3D " +
                "atau engine gameplay MLBB.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
