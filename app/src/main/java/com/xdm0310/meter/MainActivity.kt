package com.xdm0310.meter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var pendingAutoConnect = true

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            triggerScan()
        } else {
            Toast.makeText(this, "请授予蓝牙及位置权限以扫描 W02 模块", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 使用应用期间保持屏幕常亮（切后台或锁屏后系统自动释放）
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        viewModel = viewModel,
                        onRequestPermissionsAndScan = { autoConnect ->
                            pendingAutoConnect = autoConnect
                            checkPermissionsAndScan()
                        }
                    )
                }
            }
        }
    }

    private fun checkPermissionsAndScan() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            triggerScan()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun triggerScan() {
        if (!viewModel.bleManager.isBluetoothEnabled()) {
            Toast.makeText(this, "请先打开手机蓝牙开关", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.bleManager.startScan(autoConnect = pendingAutoConnect)
    }
}
