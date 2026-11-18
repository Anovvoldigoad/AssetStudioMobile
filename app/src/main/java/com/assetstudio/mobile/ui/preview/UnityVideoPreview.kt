package com.assetstudio.mobile.ui.preview

import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.assetstudio.mobile.core.classes.VideoClip
import com.assetstudio.mobile.core.classes.VideoContainerProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class VideoPreviewFile(val file: File? = null, val reason: String = "")

/**
 * Displays actual VideoClip stream bytes only when the OS recognizes the
 * container. Does not render the Unity/MLBB scene or shaders.
 */
@Composable
fun UnityVideoPreview(clip: VideoClip) {
    val context = LocalContext.current
    val video by produceState(VideoPreviewFile(reason = "Memeriksa video…"), clip) {
        value = withContext(Dispatchers.IO) {
            try {
                if (clip.m_ExternalResources.m_Size > 48L * 1024 * 1024) {
                    VideoPreviewFile(reason = "Video di atas 48 MB. Ekspor untuk diputar " +
                        "di pemutar Android tanpa menyalin semuanya ke memori aplikasi.")
                } else {
                    val bytes = clip.getVideoData()
                    val extension = VideoContainerProbe.extension(bytes)
                    if (extension == null) {
                        VideoPreviewFile(reason = "Kontainer video tidak dikenali sebagai MP4/3GP/WebM.")
                    } else {
                        File.createTempFile("mlbb_video_", "." + extension, context.cacheDir).let {
                            it.writeBytes(bytes)
                            VideoPreviewFile(file = it)
                        }
                    }
                }
            } catch (e: Exception) {
                VideoPreviewFile(reason = "Video atau file .resource tidak bisa dibaca: " + e.message)
            }
        }
    }
    var widget by remember(clip) { mutableStateOf<VideoView?>(null) }
    var ready by remember(video.file) { mutableStateOf(false) }
    var playing by remember(video.file) { mutableStateOf(false) }
    var error by remember(video.file) { mutableStateOf<String?>(null) }

    DisposableEffect(video.file) {
        onDispose {
            runCatching { widget?.stopPlayback() }
            widget = null
            video.file?.delete()
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pratinjau VideoClip", style = MaterialTheme.typography.titleMedium)
            Text(clip.m_Width.toString() + " × " + clip.m_Height +
                " • resource: " + clip.m_ExternalResources.m_Size + " byte",
                style = MaterialTheme.typography.bodySmall)
            val asset = video.file
            if (asset != null) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setOnPreparedListener {
                                ready = true
                                seekTo(1)
                            }
                            setOnCompletionListener { playing = false }
                            setOnErrorListener { _, _, _ ->
                                error = "Decoder video Android tidak mendukung codec ini."
                                playing = false
                                true
                            }
                            setVideoPath(asset.absolutePath)
                            widget = this
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
                Button(onClick = {
                    widget?.let { view ->
                        if (playing) {
                            view.pause()
                            playing = false
                        } else if (ready) {
                            view.start()
                            playing = true
                        }
                    }
                }, enabled = ready && error == null) {
                    Text(if (playing) "Jeda video" else "Putar video")
                }
                if (!ready && error == null) {
                    Text("Menyiapkan decoder Android…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(video.reason, style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Preview hanya video yang tersimpan dalam aset. Bukan tampilan " +
                "skill/cutscene dari engine MLBB.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
