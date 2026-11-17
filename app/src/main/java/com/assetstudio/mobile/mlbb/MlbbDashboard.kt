package com.assetstudio.mobile.mlbb

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.assetstudio.mobile.MainViewModel

/** Native Compose UI: tanpa Python, Termux, atau localhost. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MlbbDashboard(
    viewModel: MainViewModel,
    onOpenFiles: () -> Unit,
    onOpenCategory: (String) -> Unit
) {
    val assets by viewModel.assets.collectAsState()
    val counts = remember(assets) {
        MlbbCategory.values().associateWith { category ->
            assets.count { category.accepts(it.type, it.name) }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("MLBB Mod Studio", fontWeight = FontWeight.Bold)
                    Text(
                        "Android • Native • Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            })
        }
    ) { inset ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inset)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Workshop aset Mobile Legends",
                        style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Buka file Unity dari penyimpanan Android. Pilih kategori, " +
                            "lalu preview, ekstrak atau edit aset yang didukung.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(assets.size.toString() + " aset dalam sesi",
                        style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onOpenFiles, modifier = Modifier.fillMaxWidth()) {
                        Text("📁 Pilih file / folder mod")
                    }
                    OutlinedButton(
                        onClick = { onOpenCategory(MlbbCategory.ALL.name.lowercase()) },
                        enabled = assets.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Buka semua aset") }
                }
            }
            Text("Editor berdasarkan kategori", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pengelompokan otomatis berdasarkan tipe dan nama aset, " +
                    "bukan deteksi pasti hubungan skill/hero di engine MLBB.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            MlbbCategory.values().filter { it != MlbbCategory.ALL }.toList()
                .chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { category ->
                            val count = counts[category] ?: 0
                            val enabled = count > 0
                            Card(
                                modifier = Modifier.weight(1f).heightIn(min = 128.dp)
                                    .clickable(enabled = enabled) {
                                        onOpenCategory(category.name.lowercase())
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (enabled)
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(13.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(category.glyph,
                                        style = MaterialTheme.typography.headlineMedium)
                                    Text(category.title,
                                        style = MaterialTheme.typography.titleSmall)
                                    Text(category.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(count.toString() + " aset",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (pair.size < 2) Spacer(Modifier.weight(1f))
                    }
                }
            Spacer(Modifier.height(4.dp))
            Text("Preview model & tekstur tersedia di detail aset.",
                style = MaterialTheme.typography.bodyMedium)
            Text(
                "Preview adalah simulasi aplikasi, bukan render gameplay asli. " +
                    "Penggantian Texture2D dan Mesh OBJ memakai repacker native. " +
                    "Editor logika skill, retarget rig otomatis, dan animasi " +
                    "skeletal penuh belum didukung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
