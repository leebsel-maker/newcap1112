package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        setContent {
            MyApplicationTheme {
                BleSensorScreen()
            }
        }
    }
}

val SERVICE_UUID = UUID.fromString("0000180C-0000-1000-8000-00805F9B34FB")
val CHAR_UUID    = UUID.fromString("00002A56-0000-1000-8000-00805F9B34FB")

@SuppressLint("MissingPermission")
@Composable
fun BleSensorScreen() {
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter

    var connectionStatus by remember { mutableStateOf("연결 안됨") }
    var gasValue by remember { mutableStateOf("0") }
    var shockValue by remember { mutableStateOf("0") }
    var distValue by remember { mutableStateOf("0") }

    val gattCallback = remember {
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                    connectionStatus = "기기 연결됨! 데이터 찾는 중..."
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectionStatus = "연결 끊김"
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHAR_UUID)
                if (characteristic != null) {
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    connectionStatus = "데이터 수신 시작!"
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val data = characteristic.getStringValue(0)
                val parts = data.split(",")
                if (parts.size == 3) {
                    gasValue = parts[0]
                    shockValue = parts[1]
                    distValue = parts[2]
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("작업자 안전 (BLE 버전)", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))
        Text("상태: $connectionStatus", color = Color.Gray)
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                connectionStatus = "장치 검색 중..."
                val scanner = bluetoothAdapter.bluetoothLeScanner
                scanner.startScan(object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        if (result.device.name == "MySafetyWorker_BLE") {
                            connectionStatus = "장치 발견! 연결 시도..."
                            scanner.stopScan(this)
                            result.device.connectGatt(context, false, gattCallback)
                        }
                    }
                })
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
        ) {
            Text("BLE 장치 연결하기")
        }

        Spacer(modifier = Modifier.height(40.dp))

        // 🎨 [색상 수정 완료!] ---------------------------

        // 1. 가스 농도: 청록색 (Teal) - 차분하고 전문적인 느낌
        val gasColor = Color(0xFF00897B)
        DataCard("가스 농도", gasValue, "", gasColor)

        // 2. 충격 감지: 진한 주황색 (Dark Orange) - 회색과 잘 어울리는 포인트 컬러
        val shockColor = Color(0xFFF57C00)
        val shockText = if (shockValue == "1") "충격 감지!" else "정상"
        DataCard("충격 감지", shockText, "", shockColor)

        // 3. 안전고리: 회색 (Gray)
        val distColor = Color.Gray
        DataCard("안" +
                "전고리", distValue, "cm", distColor)

        // -------------------------------------------------
    }
}

@Composable
fun DataCard(title: String, value: String, unit: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White)
            Text(value + unit, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
}