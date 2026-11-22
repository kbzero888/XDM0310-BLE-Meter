package com.xdm0310.meter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestPermissionsAndScan: (Boolean) -> Unit
) {
    val connectionState by viewModel.bleManager.connectionState.collectAsState()
    val connectedDeviceName by viewModel.bleManager.connectedDeviceName.collectAsState()
    val scannedDevices by viewModel.bleManager.scannedDevices.collectAsState()

    val isSerialPage by viewModel.isSerialPage.collectAsState()
    val isPollingEnabled by viewModel.isPollingEnabled.collectAsState()
    val targetAddress by viewModel.targetAddress.collectAsState()
    val autoRecordEnabled by viewModel.autoRecordEnabled.collectAsState()
    val stableThreshold by viewModel.stableThreshold.collectAsState()
    val stableProgress by viewModel.stableProgress.collectAsState()
    val currentReading by viewModel.currentReading.collectAsState()
    val savedRecords by viewModel.savedRecords.collectAsState()
    val serialLogs by viewModel.serialLogs.collectAsState()

    var showDeviceDialog by remember { mutableStateOf(false) }
    var showAddressDialog by remember { mutableStateOf(false) }
    var editingNoteRecord by remember { mutableStateOf<SavedRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isSerialPage) "蓝牙串口助手" else "XDM0310 内阻仪",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (connectionState) {
                                BleConnectionState.CONNECTED -> "已连接: $connectedDeviceName"
                                BleConnectionState.CONNECTING -> "正在握手连接..."
                                BleConnectionState.SCANNING -> "正在扫描 W02 (FFE0)..."
                                BleConnectionState.DISCONNECTED -> "未连接蓝牙设备"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (connectionState == BleConnectionState.CONNECTED) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                actions = {
                    // 导出 CSV 按钮
                    IconButton(onClick = { viewModel.exportCsvAndShare() }) {
                        Icon(Icons.Default.Share, contentDescription = "导出 CSV")
                    }
                    // 切换主界面 / 蓝牙串口助手按钮
                    FilledTonalIconButton(
                        onClick = { viewModel.togglePage() },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSerialPage) Icons.Default.Speed else Icons.Default.Terminal,
                            contentDescription = "切换视图"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 顶部公共控制栏：蓝牙连接 + 轮询启停按钮 + 目标地址选择
            ControlHeaderBar(
                connectionState = connectionState,
                isPollingEnabled = isPollingEnabled,
                onAutoScanClick = { onRequestPermissionsAndScan(true) },
                onManualSelectClick = {
                    onRequestPermissionsAndScan(false)
                    showDeviceDialog = true
                },
                onDisconnectClick = { viewModel.bleManager.disconnect() },
                onTogglePolling = { viewModel.togglePolling() },
                onOpenAddressSelector = { showAddressDialog = true }
            )

            // 页面主体切换
            if (isSerialPage) {
                SerialMonitorPage(
                    serialLogs = serialLogs,
                    currentReading = currentReading,
                    targetAddress = targetAddress,
                    onSendHex = { hex, appendSum -> viewModel.sendCustomHex(hex, appendSum) },
                    onClearLogs = { viewModel.clearSerialLogs() },
                    modifier = Modifier.weight(1f)
                )
            } else {
                DashboardPage(
                    currentReading = currentReading,
                    targetAddress = targetAddress,
                    autoRecordEnabled = autoRecordEnabled,
                    stableThreshold = stableThreshold,
                    stableProgress = stableProgress,
                    savedRecords = savedRecords,
                    onToggleAutoRecord = { viewModel.setAutoRecordEnabled(it) },
                    onThresholdChange = { viewModel.setStableThreshold(it) },
                    onManualSave = { viewModel.manualSaveCurrentReading() },
                    onEditNote = { editingNoteRecord = it },
                    onDeleteRecord = { viewModel.deleteRecord(it) },
                    onClearAll = { viewModel.clearAllRecords() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 设备列表选择弹窗
    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.bleManager.stopScan()
                showDeviceDialog = false
            },
            title = { Text("选择 W02 蓝牙模块") },
            text = {
                if (scannedDevices.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("正在搜索 FFE0 广播设备，请确认已开启 GPS 定位...")
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(scannedDevices) { item ->
                            ListItem(
                                headlineContent = { Text(item.name) },
                                supportingContent = { Text("${item.address}  (${item.rssi} dBm)") },
                                modifier = Modifier.clickable {
                                    showDeviceDialog = false
                                    viewModel.bleManager.connect(item.device)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.bleManager.stopScan()
                    showDeviceDialog = false
                }) { Text("关闭") }
            }
        )
    }

    // 目标模块地址选择弹窗
    if (showAddressDialog) {
        AddressSelectorDialog(
            currentAddress = targetAddress,
            onDismiss = { showAddressDialog = false },
            onConfirm = { newAddr ->
                viewModel.setTargetAddress(newAddr)
                showAddressDialog = false
            }
        )
    }

    // 记录备注编辑弹窗
    editingNoteRecord?.let { record ->
        var noteText by remember(record) { mutableStateOf(record.note) }
        AlertDialog(
            onDismissRequest = { editingNoteRecord = null },
            title = { Text("编辑电池备注") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("例如：1号电芯 / 组A-03") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateRecordNote(record.id, noteText)
                    editingNoteRecord = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingNoteRecord = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ControlHeaderBar(
    connectionState: BleConnectionState,
    isPollingEnabled: Boolean,
    onAutoScanClick: () -> Unit,
    onManualSelectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onTogglePolling: () -> Unit,
    onOpenAddressSelector: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 蓝牙连接按钮组（精简文案 + 单行不折行，留足右侧空间）
            if (connectionState == BleConnectionState.DISCONNECTED) {
                Button(
                    onClick = onAutoScanClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("自动", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = onManualSelectClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("列表", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            } else {
                OutlinedButton(
                    onClick = onDisconnectClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("断开", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }

            Spacer(Modifier.weight(1f))

            // 轮询启停按钮
            FilledTonalButton(
                onClick = onTogglePolling,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isPollingEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = if (isPollingEnabled) Icons.Default.Sync else Icons.Default.SyncDisabled,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isPollingEnabled) "轮询中" else "已暂停",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // 目标地址选择按钮（纯文字“地址”，禁止换行裁切）
            OutlinedButton(
                onClick = onOpenAddressSelector,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = "地址",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun DashboardPage(
    currentReading: MeterReading?,
    targetAddress: Int,
    autoRecordEnabled: Boolean,
    stableThreshold: Int,
    stableProgress: Int,
    savedRecords: List<SavedRecord>,
    onToggleAutoRecord: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onManualSave: () -> Unit,
    onEditNote: (SavedRecord) -> Unit,
    onDeleteRecord: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 主测量仪表盘卡片
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.US, "目标地址 0x%02X", targetAddress),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "供电: ${currentReading?.supplyVoltageText ?: "--"}  电量: ${currentReading?.batteryPercentText ?: "--"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("内阻 (IR)", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = currentReading?.resistanceText ?: "---- mΩ",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (currentReading?.isOverload == true) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("电压 (VOLT)", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = currentReading?.voltageText ?: "---- V",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 防抖自动记录控制卡
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = autoRecordEnabled,
                            onCheckedChange = onToggleAutoRecord
                        )
                        Text("稳定自动保存 ($stableProgress/$stableThreshold 帧)", style = MaterialTheme.typography.bodyMedium)
                    }
                    FilledTonalButton(
                        onClick = onManualSave,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("手动记一条", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                }

                if (autoRecordEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text("防抖帧数:", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = stableThreshold.toFloat(),
                            onValueChange = { onThresholdChange(it.toInt()) },
                            valueRange = 1f..15f,
                            steps = 13,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Text("${stableThreshold}帧", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 历史记录标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已保存记录 (${savedRecords.size} 条)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (savedRecords.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("清空全部", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }

        // 历史记录列表
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(savedRecords, key = { it.id }) { record ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${record.voltage}  |  ${record.resistance}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "[0x${record.addressHex}]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = if (record.note.isNotBlank()) "${record.timeText} · ${record.note}" else record.timeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onEditNote(record) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "备注", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onDeleteRecord(record.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SerialMonitorPage(
    serialLogs: List<SerialLogEntry>,
    currentReading: MeterReading?,
    targetAddress: Int,
    onSendHex: (String, Boolean) -> Boolean,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hexInput by remember { mutableStateOf("01 A6 02 8A 8A") }
    var appendChecksum by remember { mutableStateOf(true) }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(serialLogs.size, autoScroll) {
        if (autoScroll && serialLogs.isNotEmpty()) {
            listState.scrollToItem(serialLogs.lastIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 上半部分：内阻仪返回的 16 进制消息监视区
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14171A)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.58f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1F2429))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "HEX 报文监视 (${serialLogs.size})",
                        color = Color(0xFFE0E0E0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { autoScroll = !autoScroll },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = if (autoScroll) "自动滚屏:开" else "自动滚屏:关",
                                fontSize = 11.sp,
                                color = Color(0xFF81C784)
                            )
                        }
                        TextButton(
                            onClick = onClearLogs,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("清屏", fontSize = 11.sp, color = Color(0xFFE57373))
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(serialLogs, key = { it.id }) { log ->
                        Text(
                            text = "[${log.timeText}] ${if (log.isTx) "TX >" else "RX <"} ${log.hexText}",
                            color = if (log.isTx) Color(0xFF64B5F6) else Color(0xFFA5D6A7),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // 下半部分：左侧 HEX 编辑发送区 + 右侧实时电压内阻显示区
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 下半部分左侧：编辑与发送 16 进制消息
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { hexInput = it },
                        label = { Text("发送 HEX 指令", fontSize = 11.sp) },
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { appendChecksum = !appendChecksum }
                        ) {
                            Checkbox(
                                checked = appendChecksum,
                                onCheckedChange = { appendChecksum = it },
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "自动校验和",
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // 小巧协调的“发送”按钮，单行禁止截断
                        Button(
                            onClick = { onSendHex(hexInput, appendChecksum) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = "发送",
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // 下半部分右侧：同步显示实时电压与内阻数值
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = String.format(Locale.US, "实时读数 [0x%02X]", targetAddress),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Column {
                        Text("内阻", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = currentReading?.resistanceText ?: "-- mΩ",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            color = if (currentReading?.isOverload == true) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }

                    HorizontalDivider()

                    Column {
                        Text("电压", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = currentReading?.voltageText ?: "-- V",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressSelectorDialog(
    currentAddress: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var hexText by remember { mutableStateOf(String.format(Locale.US, "%02X", currentAddress)) }
    val quickPresets = listOf(1, 2, 3, 4, 5, 6, 7, 8)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置目标轮询地址 (HEX)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("常用地址快捷选择：", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPresets.take(4).forEach { addr ->
                        FilterChip(
                            selected = hexText.uppercase(Locale.US) == String.format(Locale.US, "%02X", addr),
                            onClick = { hexText = String.format(Locale.US, "%02X", addr) },
                            label = { Text(String.format(Locale.US, "%02X", addr), fontFamily = FontFamily.Monospace) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPresets.drop(4).forEach { addr ->
                        FilterChip(
                            selected = hexText.uppercase(Locale.US) == String.format(Locale.US, "%02X", addr),
                            onClick = { hexText = String.format(Locale.US, "%02X", addr) },
                            label = { Text(String.format(Locale.US, "%02X", addr), fontFamily = FontFamily.Monospace) }
                        )
                    }
                }

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { if (it.length <= 2) hexText = it.uppercase(Locale.US) },
                    label = { Text("自定义十六进制地址 (01 ~ FE)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = hexText.toIntOrNull(16)?.coerceIn(1, 254) ?: 1
                onConfirm(parsed)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
