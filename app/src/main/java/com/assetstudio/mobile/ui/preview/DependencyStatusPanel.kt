package com.assetstudio.mobile.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assetstudio.mobile.core.classes.AudioClip
import com.assetstudio.mobile.core.classes.Texture2D
import com.assetstudio.mobile.core.classes.UnityObject
import com.assetstudio.mobile.core.classes.VideoClip
import com.assetstudio.mobile.core.io.getFileName

/** Read-only status report. Does not allocate the resource bytes on UI thread. */
@Composable
fun DependencyStatusPanel(obj: UnityObject) {
    val names = remember(obj) {
        val collected = mutableListOf<String>()
        obj.assetsFile.m_Externals.forEach { ext ->
            if (ext.fileName.isNotEmpty()) collected.add(ext.fileName)
        }
        when (obj) {
            is Texture2D -> obj.m_StreamData?.path?.takeIf { it.isNotBlank() }?.let(collected::add)
            is AudioClip -> obj.m_Source?.takeIf { it.isNotBlank() }?.let(collected::add)
            is VideoClip -> obj.m_ExternalResources.m_Source.takeIf { it.isNotBlank() }?.let(collected::add)
        }
        collected.map(::getFileName).filter { it.isNotBlank() }.distinct().take(90)
    }
    if (names.isEmpty()) return
    val manager = obj.assetsFile.assetsManager
    val statuses = remember(obj, names) {
        names.map { name -> name to manager.describeDependency(name) }
    }
    val missing = statuses.count { (_, status) ->
        status == "MISSING" || status == "AMBIGUOUS" ||
            status == "TOO_LARGE" || status == "Tidak ditemukan di sesi ini"
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Diagnosis dependensi", style = MaterialTheme.typography.titleMedium)
            Text(names.size.toString() + " referensi eksternal • " + missing +
                " perlu perhatian", style = MaterialTheme.typography.bodySmall)
            statuses.forEach { (name, state) ->
                val label = when (state) {
                    "FOUND" -> "Ada di folder; dapat dimuat saat diperlukan"
                    "MISSING" -> "Tidak ada di folder pilihan"
                    "AMBIGUOUS" -> "Ada beberapa file bernama sama; tidak dipilih otomatis"
                    "TOO_LARGE" -> "Melebihi batas pembacaan otomatis"
                    "STREAM_ONLY" -> "File besar tersedia; dibaca per offset tanpa memuat seluruh file"
                    else -> state
                }
                Text(name, style = MaterialTheme.typography.labelMedium)
                Text(label, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Jika berstatus hilang: buka folder mod lengkap yang memuat " +
                "AssetBundle, .assets, dan .resS pendampingnya. File dari server " +
                "MLBB yang tidak disertakan tidak dapat dibuat otomatis oleh editor.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
