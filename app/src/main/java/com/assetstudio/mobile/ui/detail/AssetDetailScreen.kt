package com.assetstudio.mobile.ui.detail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assetstudio.mobile.AssetItem
import com.assetstudio.mobile.DemandState
import com.assetstudio.mobile.MainViewModel
import com.assetstudio.mobile.core.classes.AudioClip
import com.assetstudio.mobile.core.classes.Font
import com.assetstudio.mobile.core.classes.Material
import com.assetstudio.mobile.core.classes.Mesh
import com.assetstudio.mobile.core.classes.MeshFilter
import com.assetstudio.mobile.core.classes.MonoBehaviour
import com.assetstudio.mobile.core.classes.Shader
import com.assetstudio.mobile.core.classes.SkinnedMeshRenderer
import com.assetstudio.mobile.core.classes.Sprite
import com.assetstudio.mobile.core.classes.TextAsset
import com.assetstudio.mobile.core.classes.Texture2D
import com.assetstudio.mobile.core.classes.VideoClip
import com.assetstudio.mobile.core.io.EndianBinaryReader
import com.assetstudio.mobile.core.serialized.TypeTree
import com.assetstudio.mobile.core.serialized.TypeTreeHelper
import com.assetstudio.mobile.export.AssetExporter
import com.assetstudio.mobile.render.RenderDumper
import com.assetstudio.mobile.ui.browser.FilePickerDialog
import com.assetstudio.mobile.ui.browser.FileSaveDialog
import com.assetstudio.mobile.ui.browser.rememberStoragePermission
import com.assetstudio.mobile.ui.components.InfoRow
import com.assetstudio.mobile.ui.components.LoadingOverlay
import com.assetstudio.mobile.ui.components.MeshPreview
import com.assetstudio.mobile.ui.components.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    viewModel: MainViewModel,
    assetId: String,
    onBack: () -> Unit,
    /** 点击关联资产跳转其详情页（如 SkinnedMeshRenderer 引用的 Mesh） */
    onOpenAsset: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assets by viewModel.assets.collectAsState()
    val item = remember(assetId, assets) { assets.firstOrNull { it.id == assetId } }
    val pendingSave by viewModel.pendingSave.collectAsState()
    // v1.10.0：按需装载状态——懒条目（所在文件未驻留内存）点开时自动从磁盘载入
    val demandState by viewModel.demandState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    // v1.10.0 核心补全：懒条目进入详情页即自动装载（只读它所在的那一个文件）。
    // 装载成功后 MainViewModel 会回填统一列表，item.obj 变为非空并自动重组出预览
    LaunchedEffect(assetId, item?.isLazy) {
        if (item?.isLazy == true) {
            viewModel.requestAssetLive(assetId)
        }
    }

    // 替换确认对话框状态
    var pendingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var keepFormat by remember { mutableStateOf(true) }
    // 待确认的模型替换内容：文本 + IO 线程统计好的摘要（主线程不做任何全文扫描）
    var pendingObj by remember { mutableStateOf<PendingObj?>(null) }

    val texture = item?.obj as? Texture2D
    val meshObj = item?.obj as? Mesh

    // ---------- 渲染器类组件（SkinnedMeshRenderer / MeshFilter）引用的网格 ----------
    // 这些组件本身不含几何数据，几何在 m_Mesh PPtr 指向的 Mesh 对象里：
    // - 预览：解引用后复用 Mesh 预览器
    // - 替换：替换目标就是这个 Mesh（pathID 重写走 Mesh 对象所在文件）
    val referencedMesh: Mesh? = remember(item) {
        when (val o = item?.obj) {
            is SkinnedMeshRenderer -> if (!o.m_Mesh.isNull) o.m_Mesh.tryGet() else null
            is MeshFilter -> if (!o.m_Mesh.isNull) o.m_Mesh.tryGet() else null
            else -> null
        }
    }

    // 模型替换目标：直接是 Mesh，或是渲染器组件引用的 Mesh
    val replaceTargetMesh = meshObj ?: referencedMesh

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }

    // ---------- 应用内文件选择（替代 SAF OpenDocument） ----------
    /** 当前选择目标：替换贴图（选图片）/ 替换模型（选 OBJ） */
    var pickTarget by remember { mutableStateOf<PickTarget?>(null) }

    // ---------- 应用内保存（替代 SAF：SAF 会按 MIME 强制改后缀 .txt/.bin） ----------
    val (storageGranted, requestStorage) = rememberStoragePermission()

    /** 当前等待保存的请求：导出当前资产 / 另存替换结果 */
    var saveRequest by remember { mutableStateOf<SaveRequest?>(null) }
    var showPermDialog by remember { mutableStateOf(false) }

    if (item == null) {
        Scaffold(topBar = { DetailTopBar("Aset", onBack) }) { padding ->
            Box(Modifier.padding(padding)) {
                Text("Aset tidak ditemukan. Mungkin file sudah dimuat ulang.", Modifier.padding(24.dp))
            }
        }
        return
    }

    Scaffold(
        topBar = { DetailTopBar(item.name.ifEmpty { item.type.name }, onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // ---------- 预览区 ----------
                // v1.10.0：懒条目（obj=null）先显示装载占位卡——按需装载由上方
                // LaunchedEffect 自动触发，成功回填后此处自动重组为真实预览
                if (item.obj == null) {
                    when (val ds = demandState) {
                        is DemandState.Failed -> Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Gagal memuat dari penyimpanan", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    ds.reason.ifEmpty { "File asal aset tidak bisa dibaca lagi. Mungkin sudah dipindahkan atau dihapus." },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(onClick = { viewModel.requestAssetLive(assetId) }) {
                                    Text("Coba lagi")
                                }
                            }
                        }
                        else -> Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Column {
                                    Text("Memuat ${item.name} dari penyimpanan…", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Hanya file yang memuat aset ini yang dibuka.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else when (val obj = item.obj) {
                    is Texture2D -> TexturePreview(viewModel, obj)
                    is Sprite -> SpritePreview(viewModel, obj)
                    is Mesh -> MeshPreview(obj)
                    is TextAsset -> TextPreview(obj)
                    is MonoBehaviour -> MonoPreview(obj)
                    is Material -> RenderDumpPreview("Informasi material") { RenderDumper.dumpMaterial(obj) }
                    is Shader -> RenderDumpPreview("Informasi shader") { RenderDumper.dumpShader(obj) }
                    // 渲染器组件：预览其引用的网格（几何数据在 Mesh 对象里，不在组件内）
                    is SkinnedMeshRenderer, is MeshFilter -> {
                        if (referencedMesh != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                MeshPreview(referencedMesh)
                                Text(
                                    "Mesh ini dirujuk oleh ${item.type.name}" +
                                        " (${referencedMesh.m_VertexCount} titik / ${referencedMesh.m_Indices.size / 3} segitiga)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // 引用为空或指向未加载的外部文件（如剥离的依赖包）
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Pratinjau aset", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${item.type.name} tidak memiliki mesh yang bisa dimuat" +
                                            " (referensi kosong atau menunjuk file lain yang belum dimuat).\nKamu masih bisa mengekspor data mentah atau TypeTree.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    else -> GenericPreview(item)
                }

                // ---------- 信息卡 ----------
                // SelectionContainer：信息卡内所有文本支持长按自由选择复制
                //（长按出现系统选择手柄，可跨行拖动选择任意范围文字，点"复制"即可）
                Card(modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Informasi aset", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            InfoRow("Jenis", item.type.name)
                            InfoRow("PathID", item.pathID.toString())
                            InfoRow("Ukuran", formatBytes(item.byteSize))
                            InfoRow("File asal", item.fileName)
                            if (item.containerPath.isNotEmpty()) {
                                InfoRow("Lokasi dalam paket", item.containerPath.substringAfterLast('/'))
                            }
                            if (item.obj is Texture2D) {
                                val t = item.obj as Texture2D
                                InfoRow("Dimensi", "${t.m_Width} x ${t.m_Height}")
                                InfoRow("Format", t.m_TextureFormat.name)
                                InfoRow("Jumlah tingkat mipmap", t.m_MipCount.toString())
                                val streamed = t.m_StreamData
                                if (streamed != null && streamed.path.isNotEmpty()) {
                                    InfoRow("Sumber data", "Eksternal (${streamed.path.substringAfterLast('/')})")
                                } else {
                                    InfoRow("Sumber data", "Tersimpan langsung")
                                }
                            }
                            if (item.obj is Sprite) {
                                val s = item.obj as Sprite
                                val info = try { s.getTextureInfo() } catch (e: Exception) { null }
                                if (info != null) {
                                    InfoRow(
                                        "Area tekstur",
                                        "%.0f x %.0f".format(info.textureRect.width, info.textureRect.height)
                                    )
                                }
                            }
                            if (item.obj is Mesh) {
                                val m = item.obj as Mesh
                                InfoRow("submesh", m.m_SubMeshes.size.toString())
                                InfoRow("Jumlah titik", m.m_VertexCount.toString())
                                InfoRow("Segitiga", (m.m_Indices.size / 3).toString())
                                InfoRow("Mesh terkompresi", if (m.m_CompressedMesh != null &&
                                    (m.m_CompressedMesh!!.m_Vertices.m_NumItems > 0 ||
                                        m.m_CompressedMesh!!.m_Triangles.m_NumItems > 0)
                                ) "Ya (sudah dibuka)" else "Tidak")
                            }
                            if (item.obj is SkinnedMeshRenderer) {
                                val smr = item.obj as SkinnedMeshRenderer
                                InfoRow("Jumlah tulang", smr.m_Bones.size.toString())
                                InfoRow("Slot material", smr.m_Materials.size.toString())
                                smr.m_BlendShapeWeights?.let {
                                    InfoRow("Bobot blend shape", it.size.toString())
                                }
                                InfoRow(
                                    "Skinned mesh",
                                    if (smr.m_Mesh.isNull) "Tidak ada referensi"
                                    else referencedMesh?.let { m ->
                                        "${m.m_VertexCount} titik / ${m.m_SubMeshes.size} submesh"
                                    } ?: "Referensi belum dimuat (file eksternal)"
                                )
                            }
                            if (item.obj is MeshFilter) {
                                val mf = item.obj as MeshFilter
                                InfoRow(
                                    "Referensi mesh",
                                    if (mf.m_Mesh.isNull) "Tidak ada referensi"
                                    else referencedMesh?.let { m ->
                                        "${m.m_VertexCount} titik / ${m.m_SubMeshes.size} submesh"
                                    } ?: "Referensi belum dimuat (file eksternal)"
                                )
                            }
                            if (item.obj is AudioClip) {
                                val samples = try { (item.obj as AudioClip).listSamples().size } catch (e: Exception) { 0 }
                                InfoRow("Jumlah sampel audio", "$samples")
                            }
                        }
                    }
                }

                // ---------- 关联资产卡（渲染器组件引用的 Mesh，点击跳转详情） ----------
                referencedMesh?.let { mesh ->
                    // 关联 Mesh 是否在已加载资产列表中（跨文件引用未加载时跳转无效，只展示信息）
                    val meshAssetId = remember(mesh, assets) {
                        val id = "${mesh.assetsFile.fileName}#${mesh.m_PathID}"
                        if (assets.any { it.id == id }) id else null
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Aset terkait", style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        mesh.m_Name ?: "Mesh #${mesh.m_PathID}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "${mesh.m_VertexCount} titik · ${mesh.m_SubMeshes.size} submesh · ${mesh.m_Indices.size / 3} Segitiga",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (meshAssetId != null) {
                                    TextButton(onClick = { onOpenAsset(meshAssetId) }) {
                                        Text("Lihat")
                                    }
                                } else {
                                    Text(
                                        "File eksternal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // ---------- 操作区 ----------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!storageGranted) {
                                showPermDialog = true
                                return@OutlinedButton
                            }
                            // 按钮在懒条目装载完成前已禁用（enabled = item.obj != null），此处必非空
                            val ext = AssetExporter.suggestedExtension(item.obj!!)
                            val base = item.name.ifEmpty { item.type.name }
                                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            saveRequest = SaveRequest.Export("$base.$ext")
                        },
                        // v1.10.0：懒条目装载完成前禁用（装载自动进行，通常 1 秒内解锁）
                        enabled = item.obj != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (item.obj == null) "Memuat…" else "Ekspor")
                    }
                    if (texture != null) {
                        Button(
                            onClick = {
                                if (storageGranted) {
                                    pickTarget = PickTarget.Texture
                                } else {
                                    showPermDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Ganti tekstur")
                        }
                    }
                    // 替换模型：Mesh 资产本身，或渲染器组件（SkinnedMeshRenderer/MeshFilter）引用的 Mesh
                    if (replaceTargetMesh != null) {
                        Button(
                            onClick = {
                                if (storageGranted) {
                                    pickTarget = PickTarget.Model
                                } else {
                                    showPermDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(if (meshObj != null) "Ganti model" else "Ganti mesh")
                        }
                    }
                }

                // ---------- 待保存提示 ----------
                pendingSave?.let { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Penggantian selesai. Simpan hasilnya.",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Berhasil dikemas ulang: ${formatBytes(result.bytes.size.toLong())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Saran: gunakan nama dan ekstensi file asli (misalnya .ab/.bundle) agar dapat menggantikan file yang sama di game.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Button(
                                onClick = {
                                    if (!storageGranted) {
                                        showPermDialog = true
                                    } else {
                                        saveRequest = SaveRequest.Pending(result.suggestedName)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Simpan sebagai file baru")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }

            // ---------- 忙碌遮罩 ----------
            if (busy != null) {
                LoadingOverlay(busy!!)
            }
        }
    }

    // ---------- 替换模型确认对话框 ----------
    pendingObj?.let { pending ->
        if (replaceTargetMesh != null) {
            AlertDialog(
                onDismissRequest = { pendingObj = null },
                title = { Text("Konfirmasi penggantian model") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Model lama: ${replaceTargetMesh.m_VertexCount} titik / ${replaceTargetMesh.m_SubMeshes.size} submesh / ${replaceTargetMesh.m_Indices.size / 3} Segitiga")
                        Text("Model baru: ${pending.summary}")
                        if (meshObj == null) {
                            Text(
                                "Mesh yang dirujuk oleh ${item.type.name} akan diganti (${replaceTargetMesh.m_Name ?: "#" + replaceTargetMesh.m_PathID}). Hubungan tulang dan slot material tetap dipertahankan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            "Penggantian akan memperbarui titik, normal, UV, indeks, dan batas model tanpa mengubah namanya." +
                                "Jika normal atau UV tidak ada, data sementara akan dibuat otomatis. Ubah triangle strip menjadi daftar segitiga terlebih dahulu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val text = pending.text
                        pendingObj = null
                        busy = "Sedang mengganti model dan mengemas ulang…"
                        viewModel.replaceMesh(replaceTargetMesh, text) { ok, msg ->
                            busy = null
                            message = msg
                        }
                    }) {
                        Text("Ganti")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingObj = null }) {
                        Text("Batal")
                    }
                }
            )
        }
    }

    // ---------- 替换贴图确认对话框 ----------
    if (pendingBitmap != null && texture != null) {
        AlertDialog(
            onDismissRequest = { pendingBitmap = null },
            title = { Text("Konfirmasi penggantian tekstur") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tekstur lama: ${texture.m_Width} x ${texture.m_Height} (${texture.m_TextureFormat.name})")
                    Text("Tekstur baru: ${pendingBitmap!!.width} x ${pendingBitmap!!.height}")
                    if (pendingBitmap!!.width != texture.m_Width || pendingBitmap!!.height != texture.m_Height) {
                        Text(
                            "Perhatian: ukuran baru berbeda. Dimensi aset akan diperbarui, tetapi area potong sprite yang memakai tekstur ini mungkin bergeser.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keepFormat, onCheckedChange = { keepFormat = it })
                        Text(
                            "Pertahankan format asli jika didukung; jika tidak, gunakan RGBA32",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val bmp = pendingBitmap!!
                    pendingBitmap = null
                    busy = "Sedang mengganti dan mengemas ulang…"
                    viewModel.replaceTexture(texture, bmp, keepFormat) { ok, msg ->
                        busy = null
                        message = msg
                    }
                }) {
                    Text("Ganti")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBitmap = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // ---------- 存储权限说明 ----------
    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { showPermDialog = false },
            title = { Text("Perlu izin akses penyimpanan") },
            text = {
                Text(
                    "Untuk menyimpan file, aplikasi memerlukan izin akses semua file.\n\n" +
                        "Tekan 'Buka pengaturan', temukan aplikasi ini, lalu aktifkan izin " +
                        "\"Izinkan mengelola semua file\". Setelah itu, kembali ke aplikasi.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermDialog = false
                    requestStorage()
                }) { Text("Buka pengaturan") }
            },
            dismissButton = {
                TextButton(onClick = { showPermDialog = false }) { Text("Batal") }
            }
        )
    }

    // ---------- 应用内保存文件管理器 ----------
    saveRequest?.let { request ->
        FileSaveDialog(
            initialName = request.suggestedName,
            onDismiss = { saveRequest = null },
            onConfirm = { file ->
                saveRequest = null
                when (request) {
                    is SaveRequest.Export -> {
                        val target = item ?: return@FileSaveDialog
                        busy = "Sedang mengekspor…"
                        viewModel.exportAssetToFile(target, file) { ok, msg ->
                            busy = null
                            message = msg
                        }
                    }
                    is SaveRequest.Pending -> {
                        busy = "Sedang menyimpan…"
                        viewModel.savePendingToFile(file) { ok, msg ->
                            busy = null
                            message = msg
                        }
                    }
                }
            }
        )
    }
    // ---------- 应用内文件选择器（替换贴图选图片 / 替换模型选 OBJ） ----------
    pickTarget?.let { target ->
        FilePickerDialog(
            extensions = if (target == PickTarget.Texture) {
                setOf("png", "jpg", "jpeg", "webp", "bmp")
            } else {
                // 兼容此前被 SAF 存成 .txt 的 OBJ 导出文件
                setOf("obj", "txt")
            },
            onDismiss = { pickTarget = null },
            onConfirm = { files ->
                pickTarget = null
                val file = files.firstOrNull() ?: return@FilePickerDialog
                when (target) {
                    PickTarget.Texture -> {
                        busy = "Sedang membaca gambar…"
                        scope.launch {
                            val bmp = withContext(Dispatchers.IO) {
                                try {
                                    BitmapFactory.decodeFile(file.absolutePath)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            busy = null
                            if (bmp != null) {
                                pendingBitmap = bmp
                            } else {
                                message = "Gambar tidak bisa dibuka. Pilih gambar lain."
                            }
                        }
                    }
                    PickTarget.Model -> {
                        busy = "Sedang membaca file model…"
                        scope.launch {
                            // 读取 + 顶点/面统计全部在 IO 线程完成后才回主线程弹确认框，
                            // 主线程不做任何全文扫描（否则大文件会 ANR）
                            val result = withContext(Dispatchers.IO) {
                                try {
                                    val bytes = file.readBytes()
                                    if (bytes.size > 96 * 1024 * 1024) {
                                        return@withContext null to
                                            "File terlalu besar (${bytes.size / 1024 / 1024} MB). Pastikan file yang dipilih berformat OBJ teks."
                                    }
                                    val text = bytes.toString(Charsets.UTF_8)
                                    var v = 0
                                    var f = 0
                                    for (line in text.lineSequence()) {
                                        if (line.startsWith("v ")) v++
                                        else if (line.startsWith("f ")) f++
                                    }
                                    PendingObj(text, "$v  titik / $f bidang") to null
                                } catch (e: OutOfMemoryError) {
                                    null to "RAM tidak cukup untuk membaca file sebesar ini."
                                } catch (e: Exception) {
                                    null to "Gagal membaca file OBJ: ${e.message}"
                                }
                            }
                            busy = null
                            val (pending, err) = result
                            if (pending != null) {
                                pendingObj = pending
                            } else {
                                message = err
                            }
                        }
                    }
                }
            }
        )
    }
}

/** 文件选择目标 */
private enum class PickTarget { Texture, Model }

/** 保存请求：导出当前资产 / 另存替换重打包结果 */
private sealed interface SaveRequest {
    val suggestedName: String
    data class Export(override val suggestedName: String) : SaveRequest
    data class Pending(override val suggestedName: String) : SaveRequest
}

/** 待确认的模型替换内容：完整 OBJ 文本 + IO 线程统计好的摘要 */
private data class PendingObj(
    val text: String,
    val summary: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
        }
    )
}

// ============================ 预览组件 ============================

@Composable
private fun TexturePreview(viewModel: MainViewModel, texture: Texture2D) {
    val bitmap by produceState<Bitmap?>(initialValue = null, texture) {
        value = viewModel.decodeTexture(texture)
    }
    PreviewCard(bitmap, "Pratinjau tekstur")
}

@Composable
private fun SpritePreview(viewModel: MainViewModel, sprite: Sprite) {
    val bitmap by produceState<Bitmap?>(initialValue = null, sprite) {
        value = viewModel.decodeSprite(sprite)
    }
    PreviewCard(bitmap, "Pratinjau sprite")
}

@Composable
private fun PreviewCard(bitmap: Bitmap?, title: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (bitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Tidak bisa menampilkan pratinjau",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Format tidak didukung atau data aset tidak tersedia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                AdaptiveImagePreview(bitmap)
            }
        }
    }
}

/**
 * 自适应贴图预览：小尺寸贴图等比放大到可辨识大小（原始比例不变），
 * 并提供滑杆拖拉控制等比缩放。
 *
 * 适配规则：
 * 1. 基准：完整放入（可用宽度 × 最大预览高度 340dp）——小图放大、大图缩小；
 *    可辨识下限（显示高度 ≥96dp、宽度 ≥48dp，受总高 640dp 上限约束）
 * 2. 滑杆在此基础上乘缩放系数（25% ~ 800%），切换贴图时自动重置为 100%
 * 3. 缩放后内容超出视口（480dp 高 / 卡片宽）时双向滑动查看
 * 4. 放大超过原始像素时用 FilterQuality.None（最近邻，像素块清晰）
 */
@Composable
private fun AdaptiveImagePreview(bitmap: Bitmap) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val density = LocalDensity.current
        val w = bitmap.width
        val h = bitmap.height
        if (w > 0 && h > 0) {
            // 用户缩放系数（相对自适应基准），切换贴图时重置
            var zoom by remember(bitmap) { mutableStateOf(1f) }

            val maxWpx = constraints.maxWidth.toFloat()
            val maxHpx = with(density) { 340.dp.toPx() }
            val minHpx = with(density) { 96.dp.toPx() }
            val minWpx = with(density) { 48.dp.toPx() }
            val hardMaxHpx = with(density) { 640.dp.toPx() }

            // 自适应基准（小图等比放大 + 可辨识下限）
            var fitScale = minOf(maxWpx / w, maxHpx / h)
            fitScale = maxOf(fitScale, minOf(minHpx / h, hardMaxHpx / h))
            fitScale = maxOf(fitScale, minOf(minWpx / w, hardMaxHpx / h))

            val scale = fitScale * zoom
            val dispW = (w * scale).roundToInt()
            val dispH = (h * scale).roundToInt()
            val scrollable = dispW > constraints.maxWidth ||
                dispH > with(density) { 480.dp.toPx() }

            Column {
                // 预览视口：内容超出（宽 > 卡片 / 高 > 480dp）时双向滑动
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = with(density) { 480.dp })
                        .clipToBounds()
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = BitmapPainter(
                            bitmap.asImageBitmap(),
                            filterQuality = if (scale > 1f) FilterQuality.None else FilterQuality.Low
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.size(
                            with(density) { dispW.toDp() },
                            with(density) { dispH.toDp() }
                        )
                    )
                }
                // 缩放滑杆：拖拉控制等比缩放（相对自适应基准的百分比）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Perbesar/perkecil",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        valueRange = 0.25f..8f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(zoom * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(44.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
                Text(
                    buildString {
                        append("$w x $h")
                        if (scale > 1.01f) append(" (pratinjau diperbesar ${"%.1f".format(scale)}x）")
                        if (scrollable) append(". Geser untuk melihat bagian yang tidak tampak.")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TextPreview(textAsset: TextAsset) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Isi teks", style = MaterialTheme.typography.titleSmall)
            val content = remember(textAsset) {
                try {
                    val raw = textAsset.m_Script
                    if (raw.size > 200_000) {
                        String(raw.copyOfRange(0, 200_000), Charsets.UTF_8) + "\n… (dipotong, total ${raw.size}  byte)"
                    } else {
                        String(raw, Charsets.UTF_8)
                    }
                } catch (e: Exception) {
                    "(Teks bukan UTF-8, total ${textAsset.m_Script.size} byte)"
                }
            }
            Text(
                content,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .height(320.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun MonoPreview(mono: MonoBehaviour) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Data TypeTree", style = MaterialTheme.typography.titleSmall)
            val dump by produceState<String?>(initialValue = null, mono) {
                value = withContext(Dispatchers.IO) {
                    try {
                        val typeTree = mono.reader.serializedType?.m_Type
                        if (typeTree != null) {
                            mono.reader.reset()
                            TypeTreeHelper.readTypeString(typeTree, mono.reader)
                        } else {
                            "(Objek ini tidak memiliki informasi TypeTree)"
                        }
                    } catch (e: Exception) {
                        "Gagal menampilkan data: ${e.message}"
                    }
                }
            }
            Text(
                dump ?: "Menyiapkan data…",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .height(320.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

/**
 * 渲染资产（Material / Shader）文本转储预览：
 * 转储在 IO 线程执行，结果可滚动查看，支持长按选择复制。
 */
@Composable
private fun RenderDumpPreview(title: String, dump: () -> String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            val text by produceState<String?>(initialValue = null, title) {
                value = withContext(Dispatchers.IO) {
                    try {
                        dump()
                    } catch (e: Exception) {
                        "Gagal menampilkan data: ${e.message}"
                    }
                }
            }
            SelectionContainer {
                Text(
                    text ?: "Sedang membaca data…",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .height(360.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun GenericPreview(item: AssetItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pratinjau aset", style = MaterialTheme.typography.titleSmall)
            Text(
                "Pratinjau untuk jenis ${item.type.name} belum tersedia.\nKamu masih bisa mengekspor data mentah atau TypeTree.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
