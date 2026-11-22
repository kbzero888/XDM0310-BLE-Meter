package com.xdm0310.meter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class ScannedBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice
)

enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

@SuppressLint("MissingPermission")
class XdmBleManager(context: Context) {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val CHAR_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var rwCharacteristic: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow("")
    val connectedDeviceName: StateFlow<String> = _connectedDeviceName.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedBleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedBleDevice>> = _scannedDevices.asStateFlow()

    private val _incomingBytes = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incomingBytes: SharedFlow<ByteArray> = _incomingBytes.asSharedFlow()

    private var autoConnectFirstFfe0 = true

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val deviceName = result.scanRecord?.deviceName ?: device.name ?: "未知 BLE 设备"
            val item = ScannedBleDevice(
                name = deviceName,
                address = device.address,
                rssi = result.rssi,
                device = device
            )

            val currentList = _scannedDevices.value.toMutableList()
            val idx = currentList.indexOfFirst { it.address == item.address }
            if (idx >= 0) {
                currentList[idx] = item
            } else {
                currentList.add(item)
            }
            _scannedDevices.value = currentList

            // 自动连接首个匹配 FFE0 服务的设备（与原版行为一致）
            if (autoConnectFirstFfe0) {
                autoConnectFirstFfe0 = false
                stopScan()
                connect(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                _connectedDeviceName.value = gatt.device.name ?: gatt.device.address
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                rwCharacteristic = null
                _connectionState.value = BleConnectionState.DISCONNECTED
                _connectedDeviceName.value = ""
                gatt.close()
                if (bluetoothGatt == gatt) {
                    bluetoothGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnect()
                return
            }
            val service = gatt.getService(SERVICE_UUID)
            val characteristic = service?.getCharacteristic(CHAR_UUID)
            if (characteristic == null) {
                disconnect()
                return
            }
            rwCharacteristic = characteristic
            gatt.setCharacteristicNotification(characteristic, true)

            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            } else {
                _connectionState.value = BleConnectionState.CONNECTED
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == CCCD_UUID) {
                _connectionState.value = BleConnectionState.CONNECTED
            }
        }

        // Android 13+ 回调
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == CHAR_UUID) {
                _incomingBytes.tryEmit(value.copyOf())
            }
        }

        // Android 12 及以下回调
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == CHAR_UUID) {
                @Suppress("DEPRECATION")
                characteristic.value?.let { _incomingBytes.tryEmit(it.copyOf()) }
            }
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun startScan(autoConnect: Boolean = true) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        if (_connectionState.value == BleConnectionState.CONNECTED) return

        autoConnectFirstFfe0 = autoConnect
        _scannedDevices.value = emptyList()
        _connectionState.value = BleConnectionState.SCANNING

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_connectionState.value == BleConnectionState.SCANNING) {
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        disconnect()
        _connectionState.value = BleConnectionState.CONNECTING
        bluetoothGatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        stopScan()
        rwCharacteristic = null
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.DISCONNECTED
        _connectedDeviceName.value = ""
    }

    fun writeBytes(data: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: return false
        val char = rwCharacteristic ?: return false
        if (_connectionState.value != BleConnectionState.CONNECTED) return false

        char.writeType = if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, data, char.writeType) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            char.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
    }
}
