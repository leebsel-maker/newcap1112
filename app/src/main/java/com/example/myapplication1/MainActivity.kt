package com.example.myapplication1 // ⬅️ 사용자님 프로젝트 이름 확인!

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication1.ui.theme.MyApplicationTheme // ⬅️ 사용자님 테마 이름 확인!

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RealtimeDataScreen()
                }
            }
        }
    }
}

@Composable
fun RealtimeDataScreen() {
    // 수신된 숫자를 저장할 기억 상자들 (초기값은 0)
    var gasValue by remember { mutableStateOf("0") }
    var shockValue by remember { mutableStateOf("0") }
    var distValue by remember { mutableStateOf("0") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("실시간 센서 데이터", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(40.dp))

        // 1. 가스 값 표시 카드
        DataCard(title = "가스 농도 (MQ-2)", value = gasValue, unit = "", color = Color(0xFF4CAF50))

        // 2. 충격 값 표시 카드
        DataCard(title = "충격 감지 (SW-420)", value = shockValue, unit = "(0=없음, 1=충격)", color = Color(0xFFFF9800))

        // 3. 거리 값 표시 카드
        DataCard(title = "안전고리 거리 (HC-SR04)", value = distValue, unit = "cm", color = Color(0xFF2196F3))

        Spacer(modifier = Modifier.height(50.dp))

        // 테스트용 버튼 (블루투스 연결 전 화면 확인용)
        Button(onClick = {
            // 버튼을 누르면 랜덤한 숫자가 들어온 것처럼 화면이 바뀜
            gasValue = (100..2000).random().toString()
            shockValue = (0..1).random().toString()
            distValue = (5..50).random().toString()
        }) {
            Text("데이터 수신 테스트 (랜덤)")
        }
    }
}

@Composable
fun DataCard(title: String, value: String, unit: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, color = Color.White, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(text = unit, color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MyApplicationTheme {
        RealtimeDataScreen()
    }
}