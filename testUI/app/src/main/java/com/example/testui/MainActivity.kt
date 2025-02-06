package com.example.testui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.testui.ui.theme.TestUITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TestUITheme {
                // 최상위 화면(MainScreen)이 내부에서 Wi-Fi 연결/ MQTT 화면 전환을 처리합니다.
                MainScreen()
            }
        }
    }
}
