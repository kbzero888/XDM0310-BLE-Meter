package com.xdm0310.meter

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedRecord(
    val id: Long,
    val timeText: String,
    val addressHex: String,
    val voltage: String,
    val resistance: String,
    val note: String = ""
)

data class SerialLogEntry(
    val id: Long,
    val timeText: String,
    val isTx: Boolean,
    val hexText: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val bleManager = XdmBleManager(application)

    // 页面模式：false = 仪表盘视图，true = 蓝牙串口助手视图
    private val _isSerialPage = MutableStateFlow(false)
    val isSerialPage: StateFlow<Boolean> = _isSerialPage.asStateFlow()

    // 轮询控制开关（默认开启，用户可随时点击按钮启停）
    private val _isPollingEnabled = MutableStateFlow(true)
    val isPollingEnabled: StateFlow<Boolean> = _isPollingEnabled.asStateFlow()

    // 目标模块地址（1 ~ 254，默认 0x01）
    private val _targetAddress = MutableStateFlow(1)
    val targetAddress: StateFlow<Int> = _targetAddress.asStateFlow()

    // 自动记录开关 & 稳定帧数阈值（默认连续 8 帧相同才锁定记录）
    private val _autoRecordEnabled = MutableStateFlow(true)
    val autoRecordEnabled: StateFlow<Boolean> = _autoRecordEnabled.asStateFlow()

    private val _stableThreshold = MutableStateFlow(8)
    val stableThreshold: StateFlow<Int> = _stableThreshold.asStateFlow()

    private val _stableProgress = MutableStateFlow(0)
    val stableProgress: StateFlow<Int> = _stableProgress.asStateFlow()

    // 实时测量读数
    private val _currentReading = MutableStateFlow<MeterReading?>(null)
    val currentReading: StateFlow<MeterReading?> = _currentReading.asStateFlow()

    // 持久化历史记录列表
    private val _savedRecords = MutableStateFlow<List<SavedRecord>>(emptyList())
    val savedRecords: StateFlow<List<SavedRecord>> = _savedRecords.asStateFlow()

    // 串口助手日志列表
    private val _serialLogs = MutableStateFlow<List<SerialLogEntry>>(emptyList())
    val serialLogs: StateFlow<List<SerialLogEntry>> = _serialLogs.asStateFlow()

    // 防抖与单次连续测量会话状态
    private var lastResistanceText = ""
    private var consecutiveSameCount = 0
    private var hasCommittedCurrentStability = false
    // 当前未断开（未出现 oL）的测量会话所对应的记录 ID；为 null 表示下一次稳定需新增记录
    private var activeSessionRecordId: Long? = null

    // 拆包重组缓冲区
    private val rxBuffer = mutableListOf<Byte>()
    private var logSequence = 0L

    private val storageFile = File(application.filesDir, "xdm0310_records.tsv")
    private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val shortTimeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    init {
        loadRecordsFromDisk()
        startIncomingDataCollector()
        startPollingLoop()
    }

    fun togglePage() {
        _isSerialPage.value = !_isSerialPage.value
    }

    fun togglePolling() {
        _isPollingEnabled.value = !_isPollingEnabled.value
    }

    fun setTargetAddress(address: Int) {
        val clamped = address.coerceIn(1, 254)
        _targetAddress.value = clamped
        resetMeasurementSession()
    }

    fun setAutoRecordEnabled(enabled: Boolean) {
        _autoRecordEnabled.value = enabled
        resetMeasurementSession()
    }

    fun setStableThreshold(count: Int) {
        _stableThreshold.value = count.coerceIn(1, 20)
        resetMeasurementSession()
    }

    private fun resetMeasurementSession() {
        lastResistanceText = ""
        consecutiveSameCount = 0
        hasCommittedCurrentStability = false
        activeSessionRecordId = null
        _stableProgress.value = 0
    }

    private fun startPollingLoop() {
        viewModelScope.launch {
            while (isActive) {
                if (bleManager.connectionState.value == BleConnectionState.CONNECTED && _isPollingEnabled.value) {
                    val cmd = BleProtocol.buildPollCommand(_targetAddress.value)
                    sendRawBytes(cmd)
                }
                delay(250L)
            }
        }
    }

    private fun startIncomingDataCollector() {
        viewModelScope.launch {
            bleManager.incomingBytes.collect { chunk ->
                appendSerialLog(isTx = false, bytes = chunk)

                for (b in chunk) {
                    rxBuffer.add(b)
                }
                while (rxBuffer.size >= 16) {
                    val candidate = rxBuffer.take(16).toByteArray()
                    val parsed = BleProtocol.parseFrame(candidate)
                    if (parsed != null) {
                        repeat(16) { rxBuffer.removeAt(0) }
                        handleParsedFrame(parsed)
                    } else {
                        rxBuffer.removeAt(0)
                    }
                }
                if (rxBuffer.size > 64) {
                    rxBuffer.clear()
                }
            }
        }
    }

    private fun handleParsedFrame(reading: MeterReading) {
        // 仅处理与当前选定目标地址匹配的仪表应答
        if (reading.moduleAddress != _targetAddress.value) return

        _currentReading.value = reading

        if (!_autoRecordEnabled.value) return

        // 表笔离开（检测到 oL 超量程）：结束当前测量会话，下一次有效稳定读数将追加新记录
        if (reading.isOverload) {
            resetMeasurementSession()
            return
        }

        if (reading.resistanceText == lastResistanceText) {
            consecutiveSameCount++
            _stableProgress.value = consecutiveSameCount.coerceAtMost(_stableThreshold.value)
            if (consecutiveSameCount >= _stableThreshold.value && !hasCommittedCurrentStability) {
                hasCommittedCurrentStability = true
                commitSessionReading(reading)
            }
        } else {
            // 表笔未断开（未出现 oL）的情况下读数发生变化：重新累计稳定帧数，但保留 activeSessionRecordId
            lastResistanceText = reading.resistanceText
            consecutiveSameCount = 1
            hasCommittedCurrentStability = false
            _stableProgress.value = 1
            if (_stableThreshold.value == 1) {
                hasCommittedCurrentStability = true
                commitSessionReading(reading)
            }
        }
    }

    /**
     * 同一次连续测量中：
     * - 若 activeSessionRecordId 为空（首次接触或刚从 oL 恢复），追加一条新记录；
     * - 若 activeSessionRecordId 仍有效（表笔未断开期间阻值漂移并重新稳定），直接覆盖更新原记录。
     */
    private fun commitSessionReading(reading: MeterReading) {
        val existingId = activeSessionRecordId
        val newTime = timeFormatter.format(Date(reading.timestamp))
        val newAddr = String.format(Locale.US, "%02X", reading.moduleAddress)

        if (existingId != null && _savedRecords.value.any { it.id == existingId }) {
            val updated = _savedRecords.value.map { item ->
                if (item.id == existingId) {
                    item.copy(
                        timeText = newTime,
                        addressHex = newAddr,
                        voltage = reading.voltageText,
                        resistance = reading.resistanceText
                    )
                } else {
                    item
                }
            }
            _savedRecords.value = updated
            persistRecordsToDisk(updated)
        } else {
            val newId = System.nanoTime()
            activeSessionRecordId = newId
            val record = SavedRecord(
                id = newId,
                timeText = newTime,
                addressHex = newAddr,
                voltage = reading.voltageText,
                resistance = reading.resistanceText,
                note = ""
            )
            val updated = listOf(record) + _savedRecords.value
            _savedRecords.value = updated
            persistRecordsToDisk(updated)
        }
    }

    fun manualSaveCurrentReading() {
        val current = _currentReading.value
        if (current == null || current.isOverload) {
            Toast.makeText(getApplication(), "当前无有效测量读数", Toast.LENGTH_SHORT).show()
            return
        }
        val newId = System.nanoTime()
        activeSessionRecordId = newId
        val record = SavedRecord(
            id = newId,
            timeText = timeFormatter.format(Date(current.timestamp)),
            addressHex = String.format(Locale.US, "%02X", current.moduleAddress),
            voltage = current.voltageText,
            resistance = current.resistanceText,
            note = ""
        )
        val updated = listOf(record) + _savedRecords.value
        _savedRecords.value = updated
        persistRecordsToDisk(updated)
        Toast.makeText(getApplication(), "已手动记录一组数据", Toast.LENGTH_SHORT).show()
    }

    fun updateRecordNote(id: Long, newNote: String) {
        val updated = _savedRecords.value.map {
            if (it.id == id) it.copy(note = newNote.replace("\t", " ").replace("\n", " ")) else it
        }
        _savedRecords.value = updated
        persistRecordsToDisk(updated)
    }

    fun deleteRecord(id: Long) {
        if (activeSessionRecordId == id) {
            activeSessionRecordId = null
        }
        val updated = _savedRecords.value.filterNot { it.id == id }
        _savedRecords.value = updated
        persistRecordsToDisk(updated)
    }

    fun clearAllRecords() {
        activeSessionRecordId = null
        _savedRecords.value = emptyList()
        persistRecordsToDisk(emptyList())
    }

    private fun persistRecordsToDisk(records: List<SavedRecord>) {
        viewModelScope.launch {
            try {
                val content = records.joinToString("\n") {
                    "${it.id}\t${it.timeText}\t${it.addressHex}\t${it.voltage}\t${it.resistance}\t${it.note}"
                }
                storageFile.writeText(content, Charsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadRecordsFromDisk() {
        try {
            if (!storageFile.exists()) return
            val list = storageFile.readLines(Charsets.UTF_8).mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size >= 5) {
                    SavedRecord(
                        id = parts[0].toLongOrNull() ?: System.nanoTime(),
                        timeText = parts[1],
                        addressHex = parts[2],
                        voltage = parts[3],
                        resistance = parts[4],
                        note = parts.getOrElse(5) { "" }
                    )
                } else null
            }
            _savedRecords.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun exportCsvAndShare() {
        val context = getApplication<Application>()
        val records = _savedRecords.value
        if (records.isEmpty()) {
            Toast.makeText(context, "暂无可导出的测量记录", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val fileName = "XDM0310_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.csv"
            val csvFile = File(context.cacheDir, fileName)
            val sb = StringBuilder()
            sb.append("\uFEFF") // UTF-8 BOM 防止 Excel 中文乱码
            sb.append("测量时间,模块地址,电压,内阻,备注\n")
            for (r in records) {
                sb.append("${r.timeText},0x${r.addressHex},${r.voltage},${r.resistance},\"${r.note}\"\n")
            }
            csvFile.writeText(sb.toString(), Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "导出 CSV 测量数据").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendCustomHex(hexInput: String, appendChecksum: Boolean): Boolean {
        val bytes = BleProtocol.hexToBytes(hexInput, appendChecksum)
        if (bytes == null) {
            Toast.makeText(getApplication(), "HEX 格式错误（需为偶数位十六进制字符）", Toast.LENGTH_SHORT).show()
            return false
        }
        if (bleManager.connectionState.value != BleConnectionState.CONNECTED) {
            Toast.makeText(getApplication(), "请先连接 W02 蓝牙模块", Toast.LENGTH_SHORT).show()
            return false
        }
        sendRawBytes(bytes)
        return true
    }

    private fun sendRawBytes(bytes: ByteArray) {
        if (bleManager.writeBytes(bytes)) {
            appendSerialLog(isTx = true, bytes = bytes)
        }
    }

    private fun appendSerialLog(isTx: Boolean, bytes: ByteArray) {
        logSequence++
        val entry = SerialLogEntry(
            id = logSequence,
            timeText = shortTimeFormatter.format(Date()),
            isTx = isTx,
            hexText = BleProtocol.bytesToHex(bytes)
        )
        val current = _serialLogs.value
        val trimmed = if (current.size >= 300) current.drop(current.size - 299) else current
        _serialLogs.value = trimmed + entry
    }

    fun clearSerialLogs() {
        _serialLogs.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}
