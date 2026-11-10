package com.assetstudio.mobile.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.assetstudio.mobile.AssetItem
import com.assetstudio.mobile.LoadState
import com.assetstudio.mobile.MainViewModel
import com.assetstudio.mobile.core.crypto.EncryptedBundleDecoder
import com.assetstudio.mobile.ui.browser.FilePickerDialog
import com.assetstudio.mobile.ui.browser.rememberStoragePermission
import com.assetstudio.mobile.ui.components.InfoRow

/*
 * 主页：选择文件 → 加载 → 查看资产列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenList: () -> Unit
) {
    val context = LocalContext.current
    val loadState by viewModel.loadState.collectAsState()
    val fileName by viewModel.loadedFileName.collectAsState()
    val assets by viewModel.assets.collectAsState()
    val errors by viewModel.loadErrors.collectAsState()
    val decryptNote by viewModel.decryptNote.collectAsState()
    val mcpStatus by viewModel.mcpStatus.collectAsState()
    val mcpStarting by viewModel.mcpStarting.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val folderScan by viewModel.folderScan.collectAsState()
    val folderSummary by viewModel.folderSummary.collectAsState()
    val pendingRemaining by viewModel.pendingRemainingFiles.collectAsState()
    // v1.10.0：批次已彻底隐形（纯内部实现）——主页不再收集/展示任何批次状态
    var versionInput by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    // 手动解密对话框
    var showDecryptDialog by remember { mutableStateOf(false) }
    // 顶部菜单弹出的对话框
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(toast) {
        if (toast != null) {
            snackbar.showSnackbar(toast!!)
            viewModel.consumeToast()
        }
    }

    // ---------- 应用内文件选择（替代 SAF OpenMultipleDocuments） ----------
    val (storageGranted, requestStorage) = rememberStoragePermission()
    var showFilePicker by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showPermDialog by remember { mutableStateOf(false) }
    // 文件夹扫描选项（切换后重新扫描）
    var folderRecursive by remember { mutableStateOf(true) }

    LaunchedEffect(loadState) {
        val msg = when (val s = loadState) {
            is LoadState.Failed -> "Gagal memuat: ${s.message}"
            else -> null
        }
        if (msg != null) snackbar.showSnackbar(msg)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AssetStudio") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Pengaturan") },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                showSettings = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Tentang") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                showAbout = true
                            }
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ---------- 打开文件卡片 ----------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Buka file aset Unity",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Mendukung AssetBundle (.bundle/.unity3d), file .assets,\n" +
                            "serta arsip gzip, brotli, dan zip. Pilih file .assets beserta .resS jika ada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                if (storageGranted) {
                                    showFilePicker = true
                                } else {
                                    showPermDialog = true
                                }
                            },
                            enabled = loadState !is LoadState.Loading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Pilih file")
                        }
                        OutlinedButton(
                            onClick = {
                                if (storageGranted) {
                                    showFolderPicker = true
                                } else {
                                    showPermDialog = true
                                }
                            },
                            enabled = loadState !is LoadState.Loading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Muat folder")
                        }
                    }
                }
            }

            // ---------- Unity 版本（可选） ----------
            OutlinedTextField(
                value = versionInput,
                onValueChange = { versionInput = it },
                label = { Text("Versi Unity (opsional, misalnya 2019.4.1f1)") },
                placeholder = { Text("Isi jika informasi versi Unity tidak ada di file") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ---------- 加载状态 ----------
            when (val s = loadState) {
                is LoadState.Loading -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text(
                                    s.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2
                                )
                            }
                            // 批量加载：确定性进度条（当前/总数）
                            if (s.total > 0) {
                                LinearProgressIndicator(
                                    progress = { s.progress ?: 0f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            // 批量加载允许中途取消（保留已加载部分）
                            if (s.cancellable) {
                                TextButton(onClick = { viewModel.requestCancelLoad() }) {
                                    Text("Batalkan (aset yang sudah dimuat tetap ada)")
                                }
                            }
                        }
                    }
                }
                is LoadState.Loaded -> {
                    // v1.10.0：主页只有一个概念——「一个资产库」。文件数/资产数/磁盘暂存数，
                    // 批次、驻留、释放等内存概念全部退到幕后，用户无感
                    LoadedSummaryCard(
                        fileName = fileName,
                        fileCount = s.fileCount,
                        assets = assets,
                        errors = errors,
                        lazyCount = assets.count { it.isLazy },
                        onOpenList = { viewModel.openUnifiedList { onOpenList() } },
                        onClearAll = {
                            viewModel.consumeFolderSummary()
                            viewModel.clearAllSessions()
                        }
                    )
                    // 文件夹批量加载汇总（成功/跳过/失败明细）
                    if (folderSummary != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                folderSummary!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // 剩余文件提示卡：极少数超大文件内存一次装不下时，一键自动继续
                    pendingRemaining?.let { pr ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Masih ada ${pr.files.size} file yang belum dimuat",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    "File terlalu besar untuk dimuat sekaligus. Proses dijeda agar aplikasi tidak berhenti mendadak." +
                                        "${assets.size} aset yang sudah ditampilkan tetap aman. Tekan tombol di bawah untuk melanjutkan file yang tersisa secara otomatis.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Button(
                                    onClick = { viewModel.continueBatchLoad() },
                                    enabled = loadState !is LoadState.Loading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(8.dp))
                                    Text("Lanjutkan ${pr.files.size} file tersisa")
                                }
                            }
                        }
                    }
                    if (decryptNote != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Text(
                                "Berhasil didekripsi otomatis: $decryptNote",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                is LoadState.Failed -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Gagal memuat",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                s.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "Jika bundle memakai kunci enkripsi yang diketahui, coba dekripsi manual.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                            OutlinedButton(onClick = { showDecryptDialog = true }) {
                                Text("Coba dekripsi manual")
                            }
                        }
                    }
                    // v1.10.2：加载失败不再顶掉已加载内容——之前失败分支只显示错误卡，
                    // 用户已加载的资产库“凭空消失”，误以为内容丢了/新文件没显示。
                    // 已有内容时错误卡下方照常显示汇总卡（可继续查看/导出）
                    if (assets.isNotEmpty()) {
                        LoadedSummaryCard(
                            fileName = fileName,
                            fileCount = viewModel.sessions.collectAsState().value.sumOf { it.fileCount },
                            assets = assets,
                            errors = errors,
                            lazyCount = assets.count { it.isLazy },
                            onOpenList = { viewModel.openUnifiedList { onOpenList() } },
                            onClearAll = {
                                viewModel.consumeFolderSummary()
                                viewModel.clearAllSessions()
                            }
                        )
                    }
                }
                LoadState.Idle -> {}
            }

            // v1.10.0：批次管理卡已删除——批次是纯内部实现（自动分装/自动腾内存/
            // 自动按需装载），用户界面上不再出现任何批次概念；资产查看唯一入口
            // = 上方汇总卡「查看资产」，清空 = 汇总卡右上角按钮

            // ---------- 手动解密对话框 ----------
            if (showDecryptDialog) {
                ManualDecryptDialog(
                    onDismiss = { showDecryptDialog = false },
                    onConfirm = { keyHex, mode, skipPrefix ->
                        showDecryptDialog = false
                        viewModel.reloadWithDecrypt(
                            context,
                            MainViewModel.ManualDecryptSpec(keyHex, mode, skipPrefix)
                        )
                    }
                )
            }

            // ---------- 应用内文件选择器（多选，加载 bundle/.assets/.resS） ----------
            if (showFilePicker) {
                FilePickerDialog(
                    allowMultiple = true,
                    onDismiss = { showFilePicker = false },
                    onConfirm = { files ->
                        showFilePicker = false
                        viewModel.specifyUnityVersion = versionInput.trim()
                        viewModel.manager.specifyUnityVersion = versionInput.trim().ifEmpty { null }
                        viewModel.consumeFolderSummary()
                        viewModel.loadFiles(files)
                    }
                )
            }

            // ---------- 文件夹选择器（选目录 → 扫描 → 确认后批量加载） ----------
            if (showFolderPicker) {
                FilePickerDialog(
                    pickDirectory = true,
                    onDismiss = { showFolderPicker = false },
                    onConfirm = { dirs ->
                        showFolderPicker = false
                        val dir = dirs.firstOrNull() ?: return@FilePickerDialog
                        viewModel.scanFolder(dir, folderRecursive)
                    }
                )
            }

            // ---------- 文件夹扫描确认对话框 ----------
            val scan = folderScan
            if (scan != null) {
                FolderLoadConfirmDialog(
                    scan = scan,
                    recursive = folderRecursive,
                    onRecursiveChange = { checked ->
                        folderRecursive = checked
                        // 切换后重扫（含/不含子文件夹的清单不同）
                        viewModel.scanFolder(scan.folder, checked)
                    },
                    onDismiss = { viewModel.consumeFolderScan() },
                    onConfirm = {
                        viewModel.consumeFolderScan()
                        viewModel.specifyUnityVersion = versionInput.trim()
                        viewModel.manager.specifyUnityVersion = versionInput.trim().ifEmpty { null }
                        viewModel.consumeFolderSummary()
                        viewModel.loadFolder(scan)
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
                            "Untuk membuka file di penyimpanan HP, aplikasi memerlukan izin akses semua file.\n\n" +
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

            // ---------- 设置对话框（MCP 服务器在此管理） ----------
            if (showSettings) {
                SettingsDialog(
                    mcpStatus = mcpStatus,
                    mcpStarting = mcpStarting,
                    onDismiss = { showSettings = false },
                    onStartMcp = { viewModel.startMcp(context) },
                    onStopMcp = { viewModel.stopMcp() }
                )
            }

            // ---------- 关于对话框 ----------
            if (showAbout) {
                AboutDialog(onDismiss = { showAbout = false })
            }

            // ---------- 功能说明 ----------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Fitur utama", style = MaterialTheme.typography.titleMedium)
                    FeatureRow(Icons.Filled.Inventory2, "Jelajahi aset", "Lihat seluruh aset dalam bundle, cari dan saring berdasarkan jenis. Tahan kategori untuk mengubah urutan.")
                    FeatureRow(Icons.Filled.Image, "Pratinjau tekstur", "Pratinjau tekstur DXT, BC, ETC, ASTC, PVRTC, dan format lainnya")
                    FeatureRow(Icons.Filled.SwapHoriz, "Ganti tekstur / model", "Ganti tekstur memakai PNG atau model memakai OBJ, lalu kemas ulang file")
                    FeatureRow(Icons.Filled.ViewInAr, "Info rendering", "Lihat informasi material dan shader dalam bentuk teks")
                    FeatureRow(Icons.Filled.Rule, "Ekspor aset", "Ekspor PNG, OBJ, WAV, teks, atau data TypeTree")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/*
 * 已加载汇总卡（v1.10.0「无感加载」）：用户视角只有一个资产库——
 * 标题 = 文件数 · 资产数；内存装不下的部分显示为「暂存磁盘」一行轻提示。
 * 批次/驻留/释放等实现细节全部隐形；右上角提供一键清空。
 */
@Composable
private fun LoadedSummaryCard(
    fileName: String,
    fileCount: Int,
    assets: List<AssetItem>,
    errors: List<String>,
    lazyCount: Int,
    onOpenList: () -> Unit,
    onClearAll: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    fileName.ifEmpty { "Sudah dimuat" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearAll) {
                    Text("Kosongkan")
                }
            }
            if (lazyCount > 0) {
                Text(
                    "Sebanyak $lazyCount aset disimpan sementara di penyimpanan karena RAM terbatas. Daftar, pencarian, dan ekspor tetap bisa digunakan. " +
                        "Saat dibuka, aset akan dimuat otomatis dari penyimpanan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            InfoRow("File .assets", "$fileCount file")
            InfoRow("Total aset", "${assets.size} aset")
            val textures = assets.count { it.type.value == 28 }
            val sprites = assets.count { it.type.value == 213 || it.type.value == 68 }
            val audios = assets.count { it.type.value == 83 }
            val texts = assets.count { it.type.value == 49 }
            InfoRow("Tekstur / sprite", "$textures / $sprites")
            InfoRow("Audio / teks", "$audios / $texts")
            if (errors.isNotEmpty()) {
                InfoRow("Peringatan saat membaca data", "${errors.size} peringatan (sebagian aset mungkin tidak bisa dibuka)")
            }
            // 唯一入口：全部资产的统一列表
            Button(onClick = onOpenList, modifier = Modifier.fillMaxWidth()) {
                Text("Lihat ${assets.size} aset")
            }
        }
    }
}

/*
 * v1.10.0：BatchListCard / BatchCardItem 已删除。
 * 批次退化为 AssetsManager 内部的分装单位（自动分装、自动腾内存、索引常驻、
 * 点开自动按需装载），不再有任何用户可见的批次界面。
 */

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/*
 * 设置对话框：MCP 服务器（AI 助手接入）的启动 / 停止与状态展示。
 * mcpStatus 仅在服务器真正运行时非空：null → 显示说明 + 「启动服务器」；
 * 非 null → 显示运行状态（含连接地址）+ 「停止服务器」。启动中禁用按钮防重复点击。
 */
@Composable
private fun SettingsDialog(
    mcpStatus: String?,
    mcpStarting: Boolean,
    onDismiss: () -> Unit,
    onStartMcp: () -> Unit,
    onStopMcp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Hubungkan asisten AI (MCP)", style = MaterialTheme.typography.titleSmall)
                if (mcpStatus == null) {
                    Text(
                        "Setelah server MCP diaktifkan, asisten AI bisa mencari dan mengekspor " +
                            "aset yang sudah dimuat melalui jaringan lokal. Gunakan Wi-Fi yang sama dengan HP.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (mcpStarting) {
                        Text(
                            "Sedang memulai…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        mcpStatus,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            if (mcpStatus == null) {
                TextButton(
                    onClick = onStartMcp,
                    enabled = !mcpStarting
                ) { Text(if (mcpStarting) "Memulai…" else "Jalankan server") }
            } else {
                TextButton(onClick = onStopMcp) { Text("Hentikan server") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

/*
 * 关于对话框：版本与功能简介。
 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tentang AssetStudio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Versi 1.10.2", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Aplikasi Android untuk melihat, mengekspor, dan mengganti aset Unity. Mendukung AssetBundle, " +
                        "file .assets, serta arsip gzip, brotli, dan zip. Sejumlah bundle terenkripsi dapat dikenali dan didekripsi otomatis.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Fitur: memuat satu folder (aset disimpan sementara jika RAM tidak cukup), " +
                        "pratinjau tekstur, sprite, dan mesh, ekspor PNG, OBJ, WAV, dan teks, " +
                        "ganti tekstur PNG dan model OBJ lalu kemas ulang, lihat informasi material dan shader, " +
                        "serta server MCP untuk akses asisten AI di jaringan lokal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Pengembang: Zuimo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Oke") }
        }
    )
}

/*
 * 文件夹加载确认对话框：展示扫描结果与风险提示，用户确认后批量加载。
 *
 * 防崩溃信息透明化：
 * - 待加载文件数 / 总大小 / 内存预算（超了会在加载时逐个跳过大文件）
 * - 单文件超限、数量截断等警示
 * - "包含子文件夹"开关（切换即重扫）
 */
@Composable
private fun FolderLoadConfirmDialog(
    scan: com.assetstudio.mobile.FolderScanResult,
    recursive: Boolean,
    onRecursiveChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val memCap = com.assetstudio.mobile.MainViewModel.folderMemCapBytes()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Muat seluruh isi folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    scan.folder.absolutePath,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
                if (scan.scanError != null) {
                    Text(
                        scan.scanError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (scan.files.isEmpty()) {
                    Text(
                        "Tidak ditemukan file aset yang didukung (bundle, unity3d, assets, zip, resS, dll.).\n" +
                            "Coba aktifkan 'Sertakan subfolder'.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    InfoRow("File yang akan dimuat", "${scan.files.size} file")
                    InfoRow("Ukuran total", MainViewModel.formatBytes(scan.totalBytes))
                    if (scan.files.size < scan.scannedCount) {
                        InfoRow("Jumlah file dalam folder", "${scan.scannedCount} file (file non-aset sudah disaring)")
                    }
                    InfoRow("Batas penggunaan RAM", MainViewModel.formatBytes(memCap) + " (file besar yang melebihi batas akan dilewati)")
                    if (scan.skippedTooBig.isNotEmpty()) {
                        Text(
                            "${scan.skippedTooBig.size} file berikut melebihi batas 768 MB per file dan akan dilewati:\n" +
                                scan.skippedTooBig.take(3).joinToString("、") +
                                if (scan.skippedTooBig.size > 3) " dan lainnya" else "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (scan.truncatedCount > 0) {
                        Text(
                            "Jumlah file melebihi batas ${MainViewModel.MAX_FOLDER_FILES}. Hanya ${scan.files.size} file yang lebih kecil akan dimuat.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (scan.overBudgetHint(memCap)) {
                        Text(
                            "Ukuran total melebihi batas RAM. File besar yang tidak muat akan dilewati.\n" +
                                "Jika ingin memuat semuanya, pilih subfolder secara bertahap.",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        // 没有截断、没有超限：明确告知全部加载
                        Text(
                            "Semua ${scan.files.size} file muat dalam batas RAM dan akan dimuat.",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Switch(checked = recursive, onCheckedChange = onRecursiveChange)
                        Text("Sertakan subfolder", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "File dimuat satu per satu dan bisa dibatalkan kapan saja. File rusak atau terlalu besar akan dilewati tanpa mengganggu file lain.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = scan.files.isNotEmpty()
            ) { Text("Mulai memuat") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/*
 * 手动解密对话框：已知密钥的加密 bundle 兜底入口。
 * - XOR：循环多字节密钥（hex），也可只填 2 位十六进制做单字节
 * - AES-ECB / AES-CBC：密钥长度自动识别 128/192/256（hex 输入 32/48/64 位）
 * - 前缀偏移：先跳过文件头部若干字节再解密（部分游戏仅加密头部之后的数据）
 */
@Composable
private fun ManualDecryptDialog(
    onDismiss: () -> Unit,
    onConfirm: (keyHex: String, mode: EncryptedBundleDecoder.DecryptMode, skipPrefix: Int) -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var modeIndex by remember { mutableStateOf(0) }
    var prefixInput by remember { mutableStateOf("0") }
    var keyError by remember { mutableStateOf<String?>(null) }
    val modes = listOf("XOR", "AES-ECB", "AES-CBC")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dekripsi dan muat secara manual") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Jika file tidak dikenali secara otomatis tetapi kamu mengetahui kunci enkripsinya, " +
                        "kamu bisa mendekripsi lalu memuat ulang. Masukkan kunci dalam heksadesimal (misalnya 0d0a1b2c).",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        keyError = null
                    },
                    label = { Text("Kunci (hex)") },
                    placeholder = { Text("Contoh: 1a2b3c4d atau 1a") },
                    isError = keyError != null,
                    supportingText = keyError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modes.forEachIndexed { i, label ->
                        FilterChip(
                            selected = modeIndex == i,
                            onClick = { modeIndex = i },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = prefixInput,
                    onValueChange = { prefixInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Offset awal (jumlah byte yang dilewati)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "Catatan: panjang kunci AES harus 16, 24, atau 32 byte (32, 48, atau 64 digit hex). " +
                        "IV untuk mode CBC diambil dari 16 byte pertama data terenkripsi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hex = keyInput.trim().removePrefix("0x")
                // 校验
                try {
                    EncryptedBundleDecoder.hexToBytes(hex)
                    val prefix = prefixInput.toIntOrNull() ?: 0
                    onConfirm(hex, EncryptedBundleDecoder.DecryptMode.entries[modeIndex], prefix)
                } catch (e: Exception) {
                    keyError = "Format kunci tidak valid: ${e.message}"
                }
            }) { Text("Dekripsi lalu muat") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
