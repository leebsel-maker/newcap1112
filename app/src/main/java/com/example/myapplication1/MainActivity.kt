package com.example.myapplication1

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.example.myapplication1.ui.theme.MyApplicationTheme
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 권한 요청
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

// 2. BLE UUID (아두이노 코드와 동일)
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

    // BLE 연결 관리자 (코드 생략)
    val gattCallback = remember { /* ... (GATT 콜백 로직 유지) ... */
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
        horizontalAlignment = Alignment.CenterHorizontally, // ⬅️ [정렬 변경] 전체를 왼쪽 정렬로 변경
        verticalArrangement = Arrangement.Center
    ) {
        Text("작업자 안전 (BLE 버전)", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))
        Text("상태: $connectionStatus", color = Color.Gray,fontSize = 18.sp,fontWeight = FontWeight.Bold )
        Spacer(modifier = Modifier.height(20.dp))

        // [BLE 연결 버튼] ⬅️ [색상 변경] 하늘색으로 변경
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
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03A9F4)) // ⬅️ 하늘색 (Sky Blue)
        ) {
            Text("BLE 장치 연결하기", color = Color.White) // ⬅️ 텍스트 색상 하얀색으로 유지
        }

        Spacer(modifier = Modifier.height(40.dp))

        // 🎨 [센서 데이터 카드 및 경고 로직] ---------------------------

        val GAS_DANGER_THRESHOLD = 1300
        val gasInt = gasValue.toIntOrNull() ?: 0
        val gasIsDanger = gasInt > GAS_DANGER_THRESHOLD

        // 1. 가스 농도 카드: 4분할 디자인
        GasDataCard(
            gasValue = gasValue,
            gasIsDanger = gasIsDanger,
            dangerThreshold = GAS_DANGER_THRESHOLD
        )

        // 충격 감지 설정
        val shockIsDanger = shockValue == "1"

        // 2. 충격 감지 카드0000
        val shockColor = if (shockIsDanger) Color.Red else Color(0xFF0D47A1)
        val shockText = if (shockValue == "1") "충격 감지!" else "정상"
        DataCard("충격 감지", shockText, "", shockColor)

        // 3. 안전고리 카드 (거리)
        val distColor = Color(0XFF00897B)
        DataCard("안전고리", distValue, "cm", distColor)

        // -------------------------------------------------
    }
}

// ------------------------------------------------------------------
// GasDataCard (가스 농도 전용 - 4분할 레이아웃) ⬅️ [사이즈/정렬 변경]
// ------------------------------------------------------------------

@Composable
fun GasDataCard(gasValue: String, gasIsDanger: Boolean, dangerThreshold: Int) {
    val cardColor = if (gasIsDanger) Color.Red else Color(0xFF00897B)
    val statusText = if (gasIsDanger) "평균 초과!!" else "정상"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [왼쪽 영역: 센서 이름 및 상태 텍스트] ⬅️ [정렬/사이즈 변경]
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "가스 농도",
                    color = Color.White,
                    fontSize = 20.sp, // ⬅️ 20sp로 통일
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 20.sp, // ⬅️ 20sp로 통일
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // [오른쪽 영역: 평균 농도 및 현재 농도] ⬅️ [정렬/사이즈 변경]
            Column(
                horizontalAlignment = Alignment.Start, // ⬅️ 오른쪽도 왼쪽 정렬로 변경
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "평균 농도: $dangerThreshold",
                    color = Color.White,
                    fontSize = 20.sp, // ⬅️ 20sp로 통일
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "현재 농도: $gasValue",
                    color = Color.White,
                    fontSize = 20.sp, // ⬅️ 20sp로 통일
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// DataCard (충격 및 거리 센서용) ⬅️ [사이즈/정렬 변경]
@Composable
fun DataCard(title: String, value: String, unit: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        // ⬅️ [정렬 변경] 가운데 정렬에서 왼쪽 정렬로 변경
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) // ⬅️ 20sp로 통일
            // Text(value + unit, ...) 두 줄을 합치지 않고 20sp로 통일
            Text(value + unit, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) // ⬅️ 20sp로 통일
        }
    }
}