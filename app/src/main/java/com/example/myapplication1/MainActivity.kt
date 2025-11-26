package com.example.myapplication1

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
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
import com.example.myapplication1.ui.theme.MyApplicationTheme
// 🔥 Firestore 관련 Import 추가
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

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

// 🔥 Firestore 저장 함수: 문서 ID와 필드 Timestamp를 KST로 저장
fun saveSensorDataKst(gas: String, shock: String, dist: String) {
    // Android Studio 환경에서는 Firebase SDK가 프로젝트에 초기화되어 있다고 가정합니다.
    val db = FirebaseFirestore.getInstance()

    // 1. 현재 한국 시간(KST) 포맷 생성 (필드에 저장할 시간 문자열)
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
    // 타임존을 'Asia/Seoul'로 명시적으로 설정하여 KST를 보장합니다.
    sdf.timeZone = TimeZone.getTimeZone("Asia/Seoul")
    val currentTimeString = sdf.format(Date())

    // 2. 문서 ID로 사용할 시간 포맷 (밀리초까지 포함하여 고유성 확보)
    val idFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.KOREA)
    idFormat.timeZone = TimeZone.getTimeZone("Asia/Seoul")
    val documentId = idFormat.format(Date())

    // 3. 필드에 저장할 데이터 구성
    val data = hashMapOf(
        "gas" to gas,
        "shock" to shock,
        "distance" to dist,
        "timestamp_kst" to currentTimeString // KST 문자열 시간 저장
    )

    // 4. set()을 사용하여 지정된 문서 ID로 저장
    db.collection("sensorData")
        .document(documentId)
        .set(data)
        .addOnSuccessListener {
            println("✅ Firestore 저장 성공 - ID: $documentId, Data: $gas/$shock/$dist")
        }
        .addOnFailureListener { e ->
            println("❌ Firestore 저장 실패: ${e.localizedMessage}")
        }
}

// 🎨 **수정된 색상 정의:** 버튼 색상 (중간톤 블루)
val ActionBlue = Color(0xFF42A5F5)
// 🎨 **수정된 색상 정의:** 안전 녹색 (정상 상태 배경색) - 가독성 높은 짙은 청록색 계열
val SafetyGreen = Color(0XFF00897B)

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

    // BLE 연결 관리자
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
                    val newGas = parts[0]
                    val newShock = parts[1]
                    val newDist = parts[2]

                    // Compose 상태 업데이트
                    gasValue = newGas
                    shockValue = newShock
                    distValue = newDist

                    // 🔥 Firestore에 실시간 데이터 저장 (KST ID/Timestamp 사용)
                    saveSensorDataKst(newGas, newShock, newDist)
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
        Text("상태: $connectionStatus", color = Color.Gray,fontSize = 18.sp,fontWeight = FontWeight.Bold )
        Spacer(modifier = Modifier.height(20.dp))

        // [BLE 연결 버튼]
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
            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue) // 🎨 액션 블루 유지
        ) {
            Text("BLE 장치 연결하기", color = Color.White)
        }

        Spacer(modifier = Modifier.height(40.dp))

        // 🎨 [센서 데이터 카드 및 경고 로직] ---------------------------

        val GAS_DANGER_THRESHOLD = 1300
        val gasInt = gasValue.toIntOrNull() ?: 0
        val gasIsDanger = gasInt > GAS_DANGER_THRESHOLD

        // 1. 가스 농도 카드: 4분할 디자인
        // 🎨 정상: SafetyGreen, 위험: Red
        GasDataCard(
            gasValue = gasValue,
            gasIsDanger = gasIsDanger,
            dangerThreshold = GAS_DANGER_THRESHOLD
        )

        // 충격 감지 설정
        val shockIsDanger = shockValue == "1"

        // 2. 충격 감지 카드
        // 🎨 정상: SafetyGreen, 위험: Red
        val shockColor = if (shockIsDanger) Color.Red else SafetyGreen // 🎨 SafetyGreen 적용
        val shockText = if (shockValue == "1") "충격 감지! 💥" else "정상 👍"
        DataCard("충격 감지", shockText, "", shockColor)

        // 3. 안전고리 카드 (거리 -> 체결/미체결 로직 적용)
        val DIST_THRESHOLD_CM = 3 // 🔥 임시 기준: 3cm 초과 시 미체결(위험)

        // 주의: 3cm 초과(> 3)이면 미체결/위험
        val distInt = distValue.toIntOrNull() ?: 999
        val distIsUnfastened = distInt <= DIST_THRESHOLD_CM // 3cm 초과 시 미체결

        // 🎨 미체결(위험): Red, 체결(정상): SafetyGreen
        val distColor = if (distIsUnfastened) Color.Red else SafetyGreen
        val distStatusText = if (distIsUnfastened) "미체결! 🚨" else "체결 👍"

        // DataCard 호출: 거리 값 대신 상태 텍스트 전달
        DataCard("안전고리 상태", distStatusText, "", distColor)

        // -------------------------------------------------
    }
}

// ------------------------------------------------------------------
// GasDataCard (가스 농도 전용 - 4분할 레이아웃)
// ------------------------------------------------------------------

@Composable
fun GasDataCard(gasValue: String, gasIsDanger: Boolean, dangerThreshold: Int) {
    val cardColor = if (gasIsDanger) Color.Red else SafetyGreen // 🎨 SafetyGreen 적용
    val statusText = if (gasIsDanger) "평균 초과! ⚠️" else "정상 👍"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
// (내부 텍스트 로직은 그대로 유지)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [왼쪽 영역: 센서 이름 및 상태 텍스트]
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "가스 농도",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // [오른쪽 영역: 평균 농도 및 현재 농도]
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "평균 농도: $dangerThreshold",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "현재 농도: $gasValue",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// DataCard (충격 및 안전고리 센서용)
@Composable
fun DataCard(title: String, value: String, unit: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        // 가운데 정렬에서 왼쪽 정렬로 변경
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(value + unit, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}