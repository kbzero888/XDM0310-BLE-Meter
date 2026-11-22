package com.xdm0310.meter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    isDarkTheme: Boolean,
    onRequestPermissionsAndScan: (Boolean) -> Unit
) {
    val connectionState by viewModel.bleManager.connectionState.collectAsState()
    val connectedDeviceName by viewModel.bleManager.connectedDeviceName.collectAsState()
    val scannedDevices by viewModel.bleManager.scannedDevices.collectAsState()

    val themeMode by viewModel.themeMode.collectAsState()
    val isSerialPage by viewModel.isSerialPage.collectAsState()
    val isPollingEnabled by viewModel.isPollingEnabled.collectAsState()
    val targetAddress by viewModel.targetAddress.collectAsState()
    val autoRecordEnabled by viewModel.autoRecordEnabled.collectAsState()
    val stableThreshold by viewModel.stableThreshold.collectAsState()
    val jumpThresholdMilliohms by viewModel.jumpThresholdMilliohms.collectAsState()
    val stableProgress by viewModel.stableProgress.collectAsState()
    val currentReading by viewModel.currentReading.collectAsState()
    val savedRecords by viewModel.savedRecords.collectAsState()
    val serialLogs by viewModel.serialLogs.collectAsState()

    var showDeviceDialog by remember { mutableStateOf(false) }
    var showAddressDialog by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var editingNoteRecord by remember { mutableStateOf<SavedRecord?>(null) }

    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            // 点击界面任意空白处自动清除输入框焦点并收起软键盘
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isSerialPage) "蓝牙串口助手" else "XDM0310 内阻仪",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = when (connectionState) {
                                BleConnectionState.CONNECTED -> "已连接: $connectedDeviceName"
                                BleConnectionState.CONNECTING -> "正在握手连接..."
                                BleConnectionState.SCANNING -> "正在扫描 W02 (FFE0)..."
                                BleConnectionState.DISCONNECTED -> "未连接蓝牙设备"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (connectionState == BleConnectionState.CONNECTED) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                actions = {
                    // 主题选择菜单（直接点选：跟随系统 / 亮色主题 / 暗色主题）
                    Box {
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            showThemeMenu = true
                        }) {
                            Icon(
                                imageVector = when (themeMode) {
                                    ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                    ThemeMode.LIGHT -> Icons.Default.LightMode
                                    ThemeMode.DARK -> Icons.Default.DarkMode
                                },
                                contentDescription = "选择主题"
                            )
                        }
                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false }
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = mode.label,
                                            fontWeight = if (themeMode == mode) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (mode) {
                                                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                                ThemeMode.DARK -> Icons.Default.DarkMode
                                            },
                                            contentDescription = null
                                        )
                                    },
                                    trailingIcon = {
                                        if (themeMode == mode) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "已选中",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.setThemeMode(mode)
                                        showThemeMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // 导出 CSV 按钮
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        viewModel.exportCsvAndShare()
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "导出 CSV")
                    }
                    // 切换主界面 / 蓝牙串口助手按钮
                    FilledTonalIconButton(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.togglePage()
                        },
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
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 顶部公共控制栏：蓝牙连接 + 轮询启停按钮 + 目标地址选择
            ControlHeaderBar(
                connectionState = connectionState,
                isPollingEnabled = isPollingEnabled,
                onAutoScanClick = {
                    focusManager.clearFocus()
                    onRequestPermissionsAndScan(true)
                },
                onManualSelectClick = {
                    focusManager.clearFocus()
                    onRequestPermissionsAndScan(false)
                    showDeviceDialog = true
                },
                onDisconnectClick = {
                    focusManager.clearFocus()
                    viewModel.bleManager.disconnect()
                },
                onTogglePolling = {
                    focusManager.clearFocus()
                    viewModel.togglePolling()
                },
                onOpenAddressSelector = {
                    focusManager.clearFocus()
                    showAddressDialog = true
                }
            )

            // 页面主体切换
            if (isSerialPage) {
                SerialMonitorPage(
                    serialLogs = serialLogs,
                    currentReading = currentReading,
                    targetAddress = targetAddress,
                    isDarkTheme = isDarkTheme,
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
                    jumpThresholdMilliohms = jumpThresholdMilliohms,
                    stableProgress = stableProgress,
                    savedRecords = savedRecords,
                    onToggleAutoRecord = { viewModel.setAutoRecordEnabled(it) },
                    onThresholdChange = { viewModel.setStableThreshold(it) },
                    onJumpThresholdChange = { viewModel.setJumpThresholdMilliohms(it) },
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
        val dialogFocusManager = LocalFocusManager.current
        AlertDialog(
            onDismissRequest = { editingNoteRecord = null },
            title = { Text("编辑电池备注") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("例如：1号电芯 / 组A-03") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { dialogFocusManager.clearFocus() }),
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
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (connectionState == BleConnectionState.DISCONNECTED) {
                Button(
                    onClick = onAutoScanClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("自动", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = onManualSelectClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("列表", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            } else {
                OutlinedButton(
                    onClick = onDisconnectClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("断开", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }

            Spacer(Modifier.weight(1f))

            FilledTonalButton(
                onClick = onTogglePolling,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isPollingEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = if (isPollingEnabled) Icons.Default.Sync else Icons.Default.SyncDisabled,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
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

            OutlinedButton(
                onClick = onOpenAddressSelector,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp)
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
    jumpThresholdMilliohms: Double,
    stableProgress: Int,
    savedRecords: List<SavedRecord>,
    onToggleAutoRecord: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onJumpThresholdChange: (Double) -> Unit,
    onManualSave: () -> Unit,
    onEditNote: (SavedRecord) -> Unit,
    onDeleteRecord: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var showPresetMenu by remember { mutableStateOf(false) }

    var thresholdInputText by remember(jumpThresholdMilliohms) {
        mutableStateOf(
            if (jumpThresholdMilliohms % 1.0 == 0.0) {
                jumpThresholdMilliohms.toInt().toString()
            } else {
                jumpThresholdMilliohms.toString()
            }
        )
    }

    val thresholdPresets = listOf(
        10.0 to "10 mΩ (高精稳定)",
        50.0 to "50 mΩ (常规电芯)",
        100.0 to "100 mΩ",
        200.0 to "200 mΩ",
        500.0 to "500 mΩ (0.5 Ω)",
        1000.0 to "1000 mΩ (1.0 Ω 默认)"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 主测量仪表盘卡片
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.US, "目标地址 0x%02X", targetAddress),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "供电: ${currentReading?.supplyVoltageText ?: "--"}  电量: ${currentReading?.batteryPercentText ?: "--"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("内阻 (IR)", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = currentReading?.resistanceText ?: "---- mΩ",
                            fontSize = 27.sp,
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
                        Text("电压 (VOLT)", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = currentReading?.voltageText ?: "---- V",
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 防抖自动记录 + 自定义相邻帧突变阈值控制卡
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            focusManager.clearFocus()
                            onToggleAutoRecord(!autoRecordEnabled)
                        }
                    ) {
                        Checkbox(
                            checked = autoRecordEnabled,
                            onCheckedChange = {
                                focusManager.clearFocus()
                                onToggleAutoRecord(it)
                            },
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "自动保存 ($stableProgress/$stableThreshold 帧)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            focusManager.clearFocus()
                            onManualSave()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("手动记一条", fontSize = 11.sp, maxLines = 1, softWrap = false)
                    }
                }

                if (autoRecordEnabled) {
                    // 第一行：防抖帧数选择条
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text("防抖帧数:", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = stableThreshold.toFloat(),
                            onValueChange = {
                                focusManager.clearFocus()
                                onThresholdChange(it.toInt())
                            },
                            valueRange = 1f..12f,
                            steps = 10,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            text = "${stableThreshold}帧",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // 第二行：宽敞的突变阈值输入框 + “预设”下拉菜单按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "突变阈值:",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false
                        )
                        OutlinedTextField(
                            value = thresholdInputText,
                            onValueChange = { raw ->
                                thresholdInputText = raw
                                raw.toDoubleOrNull()?.let { parsed ->
                                    if (parsed > 0.0) {
                                        onJumpThresholdChange(parsed)
                                    }
                                }
                            },
                            singleLine = true,
                            suffix = { Text("mΩ", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        )

                        Box {
                            FilledTonalButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    showPresetMenu = true
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = "预设",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showPresetMenu,
                                onDismissRequest = { showPresetMenu = false }
                            ) {
                                thresholdPresets.forEach { (valMohm, label) ->
                                    val selected = kotlin.math.abs(jumpThresholdMilliohms - valMohm) < 0.001
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = label,
                                                fontSize = 13.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        trailingIcon = {
                                            if (selected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "已选中",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        onClick = {
                                            onJumpThresholdChange(valMohm)
                                            thresholdInputText = valMohm.toInt().toString()
                                            showPresetMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 历史记录标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已保存记录 (${savedRecords.size} 条)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            if (savedRecords.isNotEmpty()) {
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        onClearAll()
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text("清空全部", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        }

        // 紧凑型历史记录列表
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(savedRecords, key = { it.id }) { record ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${record.voltage}  |  ${record.resistance}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.width(6.dp))
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
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                onEditNote(record)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "备注", modifier = Modifier.size(15.dp))
                        }
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                onDeleteRecord(record.id)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除", modifier = Modifier.size(15.dp))
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
    isDarkTheme: Boolean,
    onSendHex: (String, Boolean) -> Boolean,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var hexInput by remember { mutableStateOf("01 A6 02 8A 8A") }
    var appendChecksum by remember { mutableStateOf(true) }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val monitorBgColor = if (isDarkTheme) Color(0xFF14171A) else Color(0xFFF1F5F9)
    val monitorHeaderColor = if (isDarkTheme) Color(0xFF1F2429) else Color(0xFFE2E8F0)
    val monitorTitleColor = if (isDarkTheme) Color(0xFFE0E0E0) else Color(0xFF1E293B)
    val txTextColor = if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF1565C0)
    val rxTextColor = if (isDarkTheme) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
    val autoScrollToggleColor = if (isDarkTheme) Color(0xFF81C784) else Color(0xFF1B5E20)
    val clearBtnColor = if (isDarkTheme) Color(0xFFE57373) else Color(0xFFC62828)

    val latestLogId = serialLogs.lastOrNull()?.id
    LaunchedEffect(latestLogId, autoScroll) {
        if (autoScroll && serialLogs.isNotEmpty()) {
            listState.scrollToItem(serialLogs.lastIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 上半部分：HEX 报文监视区
        Card(
            colors = CardDefaults.cardColors(containerColor = monitorBgColor),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.58f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(monitorHeaderColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "HEX 报文监视 (${serialLogs.size}/150)",
                        color = monitorTitleColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                autoScroll = !autoScroll
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = if (autoScroll) "自动滚屏:开" else "自动滚屏:关",
                                fontSize = 11.sp,
                                color = autoScrollToggleColor
                            )
                        }
                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                onClearLogs()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("清屏", fontSize = 11.sp, color = clearBtnColor)
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
                            color = if (log.isTx) txTextColor else rxTextColor,
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
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { hexInput = it },
                        label = { Text("发送 HEX 指令", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                focusManager.clearFocus()
                                appendChecksum = !appendChecksum
                            }
                        ) {
                            Checkbox(
                                checked = appendChecksum,
                                onCheckedChange = {
                                    focusManager.clearFocus()
                                    appendChecksum = it
                                },
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

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onSendHex(hexInput, appendChecksum)
                            },
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
    val focusManager = LocalFocusManager.current
    var hexText by remember { mutableStateOf(String.format(Locale.US, "%02X", currentAddress)) }
    val quickPresets = listOf(1, 2, 3, 4, 5, 6, 7, 8)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置目标轮询地址 (HEX)") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
            ) {
                Text("常用地址快捷选择：", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPresets.take(4).forEach { addr ->
                        FilterChip(
                            selected = hexText.uppercase(Locale.US) == String.format(Locale.US, "%02X", addr),
                            onClick = {
                                focusManager.clearFocus()
                                hexText = String.format(Locale.US, "%02X", addr)
                            },
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
                            onClick = {
                                focusManager.clearFocus()
                                hexText = String.format(Locale.US, "%02X", addr)
                            },
                            label = { Text(String.format(Locale.US, "%02X", addr), fontFamily = FontFamily.Monospace) }
                        )
                    }
                }

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { if (it.length <= 2) hexText = it.uppercase(Locale.US) },
                    label = { Text("自定义十六进制地址 (01 ~ FE)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
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
