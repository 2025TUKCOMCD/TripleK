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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.*

@SuppressLint("MissingPermission", "ObsoleteSdkInt")
@Composable
fun MqttScreen(mqttManager: MqttManager) {
    val context = LocalContext.current
    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    // TTS 초기화 (2배속 설정)
    DisposableEffect(context) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(2.0f)  // 2배속
            }
        }
        onDispose { tts?.shutdown() }
    }

    // 메시지 수신 시 읽기 + 진동
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
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
