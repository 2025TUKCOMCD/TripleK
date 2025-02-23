package com.example.testui

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import java.util.Locale

@Composable
fun ChatBubble(message: String, isUserMessage: Boolean = false) {
    val alignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isUserMessage)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    else
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bubbleColor
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun MqttScreen(mqttManager: MqttManager) {
    val context = LocalContext.current
    var ttsRate by remember { mutableStateOf(1.0f) }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    // TTS 초기화
    DisposableEffect(context) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(ttsRate)
            }
        }
        onDispose { tts?.shutdown() }
    }

    LaunchedEffect(ttsRate) {
        tts?.setSpeechRate(ttsRate)
    }

    if (!mqttManager.mqttConnected) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "MQTT 서버에 연결 중...\nESP32-CAM의 상태 대기 중...")
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(text = "ESP32-CAM 연결 성공!\n실시간 데이터를 수신 중:")
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(mqttManager.receivedMessages) { msg ->
                    ChatBubble(message = msg)
                    // 새로운 메시지가 들어올 때마다 TTS로 읽기
                    LaunchedEffect(msg) {
                        tts?.speak(
                            msg,
                            TextToSpeech.QUEUE_ADD,
                            null,
                            "msg_${System.currentTimeMillis()}"
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "TTS 배속: ${"%.1f".format(ttsRate)}")
            Slider(
                value = ttsRate,
                onValueChange = { ttsRate = it },
                valueRange = 0.5f..2.0f,
                steps = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
