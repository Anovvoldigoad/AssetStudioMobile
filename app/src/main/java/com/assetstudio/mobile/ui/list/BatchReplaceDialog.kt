package com.assetstudio.mobile.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.assetstudio.mobile.AssetItem
import com.assetstudio.mobile.MainViewModel
import com.assetstudio.mobile.replace.AssetReplacer
import com.assetstudio.mobile.replace.BatchTextureMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * 批量替换贴图对话框（游戏美化包工作流）：
 *
 * 1. 扫描所选文件夹内的图片（png/jpg/jpeg/webp/bmp，不递归）；
 * 2. 文件名与贴图名自动匹配（BatchTextureMatcher：精确 → 包含 → 编号变体）；
 * 3. 展示匹配计划（可取消勾选单条），确认后逐张替换并按容器重打包；
 * 4. 单容器输出原文件，多容器输出 ZIP（详情见 AssetReplacer.replaceTexturesBatch）。
 */

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp")

/** 一条"贴图 → 图片文件"配对（带可取消勾选状态） */
private class PairRow(
    val item: AssetItem,
    val imageFile: File,
    var checked: Boolean = true
)

@Composable
fun BatchReplaceDialog(
    viewModel: MainViewModel,
    folder: File,
    textureItems: List<AssetItem>,
    onBatchDone: (ok: Boolean, message: String) -> Unit,
    onDismiss: () -> Unit
) {
    // 扫描 + 匹配结果（null = 扫描中）
    var pairs by remember { mutableStateOf<List<PairRow>?>(null) }
    var unmatchedTextures by remember { mutableStateOf<List<String>>(emptyList()) }
    var unmatchedFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var keepFormat by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }

    // ---------- 1+2. 扫描文件夹并匹配（IO 线程） ----------
    LaunchedEffect(folder) {
        val result = withContext(Dispatchers.IO) {
            try {
                val images = folder.listFiles()
                    ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
                    ?.sortedBy { it.name.lowercase() }
                    ?: emptyList()
                val plan = BatchTextureMatcher.match(
                    textureItems.map { it.name },
                    images.map { it.name }
                )
                // 按贴图名 → AssetItem 的映射回填（同名贴图按顺序消费）
                val byName = HashMap<String, ArrayDeque<AssetItem>>()
                for (t in textureItems) {
                    byName.getOrPut(t.name) { ArrayDeque() }.add(t)
                }
                val rows = plan.matches.mapNotNull { m ->
                    val item = byName[m.textureName]?.removeFirstOrNull() ?: return@mapNotNull null
                    val file = images.firstOrNull { it.name == m.fileName } ?: return@mapNotNull null
                    PairRow(item, file)
                }
                Triple(rows, plan.unmatchedTextures, plan.unmatchedFiles)
            } catch (e: Exception) {
                null
            }
        }
        if (result == null) {
            scanError = "Folder tidak bisa dibaca: ${folder.absolutePath}"
            pairs = emptyList()
        } else {
            pairs = result.first
            unmatchedTextures = result.second
            unmatchedFiles = result.third
        }
    }

    val currentPairs = pairs
    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("Ganti banyak tekstur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Folder: ${folder.name}\n${textureItems.size} tekstur dipilih",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when {
                    scanError != null -> Text(scanError!!, color = MaterialTheme.colorScheme.error)
                    currentPairs == null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                        Text("Sedang mencari gambar yang cocok…")
                    }
                    currentPairs.isEmpty() -> Text(
                        "Tidak ada gambar yang cocok.\n\nPastikan nama gambar sesuai dengan nama tekstur. Perbedaan huruf besar, spasi, dan garis bawah diabaikan.",
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> {
                        // ---------- 匹配结果 ----------
                        Text(
                            "${currentPairs.size} gambar cocok" +
                                (if (unmatchedTextures.isNotEmpty()) ", ${unmatchedTextures.size} tekstur belum cocok" else "") +
                                (if (unmatchedFiles.isNotEmpty()) ", ${unmatchedFiles.size} gambar tidak digunakan" else ""),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                            ) {
                                items(currentPairs, key = { it.item.id + "|" + it.imageFile.absolutePath }) { row ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp)
                                    ) {
                                        Checkbox(
                                            checked = row.checked,
                                            onCheckedChange = { row.checked = it }
                                        )
                                        Column(Modifier.padding(vertical = 4.dp)) {
                                            Text(
                                                row.item.name.ifEmpty { "(Tanpa nama)" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "← ${row.imageFile.name}（${row.item.type.name}）",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (unmatchedTextures.isNotEmpty()) {
                            Text(
                                "Tekstur yang tidak cocok (tidak diubah):\n" +
                                    unmatchedTextures.take(5).joinToString("、") +
                                    if (unmatchedTextures.size > 5) " ${unmatchedTextures.size} lainnya" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = keepFormat, onCheckedChange = { keepFormat = it })
                            Text(
                                "Pertahankan format asli jika memungkinkan (ASTC/DXT didukung, lainnya memakai RGBA32)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Text(
                            "Setelah diganti, setiap file dikemas ulang sekali. Jika ada beberapa file, hasilnya digabung menjadi ZIP.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (running) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                        Text("Sedang mengganti dan mengemas ulang… File besar mungkin butuh waktu lebih lama.")
                    }
                }
            }
        },
        confirmButton = {
            if (currentPairs != null && currentPairs.isNotEmpty() && !running) {
                Button(
                    onClick = {
                        running = true
                        val entries = currentPairs.filter { it.checked }.map {
                            AssetReplacer.BatchEntry(it.item.obj as com.assetstudio.mobile.core.classes.Texture2D, it.imageFile, keepFormat)
                        }
                        if (entries.isEmpty()) return@Button
                        viewModel.replaceTexturesBatch(entries) { ok, msg ->
                            running = false
                            onBatchDone(ok, msg)
                            onDismiss()
                        }
                    }
                ) {
                    Text("Mulai mengganti (${currentPairs.count { it.checked }})")
                }
            }
        },
        dismissButton = {
            if (!running) {
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        }
    )
}
