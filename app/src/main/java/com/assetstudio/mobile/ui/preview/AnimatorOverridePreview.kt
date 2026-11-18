package com.assetstudio.mobile.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assetstudio.mobile.core.classes.AnimationClip
import com.assetstudio.mobile.core.classes.AnimatorOverrideController

/**
 * Displays concrete override pairs, not guessed "skill" mappings.
 * PPtr navigation to the exact clips is provided by RelatedAssetsPanel.
 */
@Composable
fun AnimatorOverridePreview(controller: AnimatorOverrideController) {
    val details = remember(controller) {
        controller.m_Clips.take(120).mapIndexed { index, entry ->
            val original = runCatching { entry.m_OriginalClip.tryGet<AnimationClip>() }.getOrNull()
            val replacement = runCatching { entry.m_OverrideClip.tryGet<AnimationClip>() }.getOrNull()
            Triple(
                index + 1,
                original?.m_Name ?: "PathID #${entry.m_OriginalClip.m_PathID} (belum dimuat)",
                replacement?.m_Name ?: "PathID #${entry.m_OverrideClip.m_PathID} (belum dimuat)"
            )
        }
    }
    val originalController = remember(controller) {
        runCatching { controller.m_Controller.tryGetAny()?.displayName }.getOrNull()
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Animator Override Controller", style = MaterialTheme.typography.titleMedium)
            Text("Controller dasar: " +
                (originalController ?: "PathID #${controller.m_Controller.m_PathID} (belum dimuat)"),
                style = MaterialTheme.typography.bodySmall)
            Text("${controller.m_Clips.size} pasangan animasi asli → pengganti.",
                style = MaterialTheme.typography.bodySmall)
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                details.forEach { (index, original, replacement) ->
                    Column {
                        Text("$index. $original", style = MaterialTheme.typography.labelMedium)
                        Text("↳ $replacement", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (controller.m_Clips.size > details.size) {
                    Text("Menampilkan ${details.size} dari ${controller.m_Clips.size} pasangan.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Perbandingan berdasarkan PPtr Unity asli. Untuk membuka clip, " +
                "gunakan 'Aset terkait' di bawah. Ini belum editor penggantian animasi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
