package com.assetstudio.mobile.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assetstudio.mobile.AssetItem
import com.assetstudio.mobile.mlbb.AssetCandidate
import com.assetstudio.mobile.mlbb.assetReferences
import com.assetstudio.mobile.mlbb.uniqueAssetLink

/** Actual Unity pointer graph. Links are limited to the same loaded session. */
@Composable
fun RelatedAssetsPanel(source: AssetItem, items: List<AssetItem>, onOpenAsset: (String) -> Unit) {
    val obj = source.obj ?: return
    val refs = remember(obj) { assetReferences(obj) }
    if (refs.isEmpty()) return
    val candidates = remember(items) {
        items.map { AssetCandidate(it.id, it.sessionId, it.pathID, it.fileName) }
            .groupBy { it.session to it.pathId }
    }
    var showAll by remember(obj) { mutableStateOf(false) }
    val shown = if (showAll) refs else refs.take(20)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Aset terkait (" + refs.size + ")", style = MaterialTheme.typography.titleMedium)
            Text("Referensi PathID asli, bukan hasil tebakan dari nama.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            shown.forEach { relation ->
                val target = relation.target
                val matched = if (target == null) null else uniqueAssetLink(
                    source.sessionId, target.m_PathID, target.assetsFile.fileName,
                    candidates[source.sessionId to target.m_PathID].orEmpty()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(relation.role, style = MaterialTheme.typography.labelMedium)
                        Text(target?.displayName ?: "PathID #" + relation.pathId + " • dependensi belum dimuat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (matched != null && matched != source.id) {
                        TextButton(onClick = { onOpenAsset(matched) }) { Text("Lihat") }
                    }
                }
            }
            if (refs.size > 20) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Tampilkan lebih sedikit" else "Lihat semua " + refs.size + " relasi")
                }
            }
        }
    }
}
