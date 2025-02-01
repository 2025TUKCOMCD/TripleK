package com.example.testui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

@Composable
fun MqttScreen() {
    val context = LocalContext.current

    // MQTT 연결 상태 및 실시간 수신 메시지 상태
    var mqttConnected by remember { mutableStateOf(false) }
    val receivedMessages = remember { mutableStateListOf<String>() }

    // MQTT 연결 및 구독 설정 (한번만 실행)
    LaunchedEffect(Unit) {
        val brokerUri = "tcp://your.mqtt.server.com:1883"  // 실제 MQTT 브로커 주소
        val clientId = "AndroidClient_${System.currentTimeMillis()}"
        val mqttClient = MqttAndroidClient(context, brokerUri, clientId)

        val options = MqttConnectOptions().apply {
            isCleanSession = true
        }

        try {
            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    mqttClient.subscribe("esp32cam/status", 0)
                    mqttClient.subscribe("esp32cam/data", 0)
                    mqttClient.setCallback(object : MqttCallback {
                        override fun connectionLost(cause: Throwable?) {
                            mqttConnected = false
                        }
                        override fun messageArrived(topic: String?, message: MqttMessage?) {
                            val msg = message.toString()
                            when (topic) {
                                "esp32cam/status" -> {
                                    if (msg == "connected") {
                                        mqttConnected = true
                                    }
                                }
                                "esp32cam/data" -> {
                                    receivedMessages.add(msg)
                                }
                            }
                        }
                        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                    })
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    // 연결 실패 처리 (필요시 재시도 로직 추가)
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    // UI: MQTT 연결 대기 또는 실시간 데이터 화면 표시
    if (!mqttConnected) {
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(receivedMessages) { msg ->
                    Text(text = msg)
                }
            }
        }
    }
}
