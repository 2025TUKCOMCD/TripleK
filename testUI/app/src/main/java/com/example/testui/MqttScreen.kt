package com.example.testui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.Alignment
import java.util.*

@SuppressLint("MissingPermission", "ObsoleteSdkInt")
@Composable
fun MqttScreen(mqttManager: MqttManager) {
    val context = LocalContext.current
    var ttsRate by remember { mutableStateOf(2.0f) }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    // TTS 초기화 및 속도 설정
    DisposableEffect(context) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(ttsRate)
            }
        }
        onDispose { tts?.shutdown() }
    }

    // TTS 속도 변경 시 반영
    LaunchedEffect(ttsRate) {
        tts?.setSpeechRate(ttsRate)
    }

    // 메시지 수신 시 TTS 읽기 + 진동
    LaunchedEffect(mqttManager.receivedMessages.size) {
        val lastMsg = mqttManager.receivedMessages.lastOrNull()
        lastMsg?.let {
            tts?.speak(
                it,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "msg_${System.currentTimeMillis()}"
            )
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.VIBRATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                WindowInsets.statusBars
                    .union(WindowInsets.navigationBars)
                    .asPaddingValues()
            )
            .padding(16.dp)  // 기존 여백 유지
    ) {
        Text(
            text = if (!mqttManager.mqttConnected)
                "MQTT 서버에 연결 중…" else "ESP32-CAM 연결 성공! 수신 중",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics {
                contentDescription = "MQTT 연결 상태 텍스트"
            }
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "메시지 목록"
                }
        ) {
            items(mqttManager.receivedMessages) { msg ->
                ChatBubble(msg)
            }
        }

        Spacer(Modifier.height(16.dp))

        // TTS 속도 조절 UI
        TtsSpeedControls(ttsRate = ttsRate, onTtsRateChange = {
            ttsRate = it
            tts?.speak("속도 ${"%.1f".format(it)}배속", TextToSpeech.QUEUE_FLUSH, null, null)
        })
    }
}

@Composable
fun ChatBubble(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .semantics {
                contentDescription = message
                liveRegion = LiveRegionMode.Polite
            }
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 2.dp,
            modifier = Modifier
                .focusable()
                .clickable { /* 선택 피드백 용도 */ }
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp),
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun TtsSpeedControls(ttsRate: Float, onTtsRateChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "TTS 속도 조절"
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = {
                val newRate = (ttsRate - 0.5f).coerceAtLeast(0.5f)
                onTtsRateChange(newRate)
            },
            modifier = Modifier.padding(8.dp)
        ) {
            Text("속도 -")
        }

        Text(
            text = "현재 속도: ${"%.1f".format(ttsRate)}배속",
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Button(
            onClick = {
                val newRate = (ttsRate + 0.5f).coerceAtMost(3.0f)
                onTtsRateChange(newRate)
            },
            modifier = Modifier.padding(8.dp)
        ) {
            Text("속도 +")
        }
    }
}
