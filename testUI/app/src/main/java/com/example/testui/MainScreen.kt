package com.example.testui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.eclipse.paho.client.mqttv3.*

// MainScreen: 두 상태(연결 전/후)에 따라 화면을 전환함
@Composable
fun MainScreen() {
    var showMqttScreen by remember { mutableStateOf(false) }

    if (!showMqttScreen) {
        // 백그라운드에서 MQTT "connected" 상태 감지 시작
        MqttStatusListener(onConnected = { showMqttScreen = true })
        // Wi‑Fi 및 웹 페이지 연결 화면 표시
        LocationAndWifiScreen()
    } else {
        // MQTT 연결되면 바로 MqttScreen으로 전환
        MqttScreen()
    }
}

@Composable
fun LocationAndWifiScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(false) }

    // 위치 권한 및 Android 13 이상에서는 NEARBY_WIFI_DEVICES 권한 요청
    val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }

    // 권한 요청 런처
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasPermissions = permissions.all { it.value }
        }
    )

    LaunchedEffect(Unit) {
        val alreadyGranted = requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (!alreadyGranted) {
            launcher.launch(requiredPermissions.toTypedArray())
        } else {
            hasPermissions = true
        }
    }

    if (!hasPermissions) {
        Column(modifier.padding(16.dp)) {
            Text(text = "Wi-Fi 설정을 위해 위치 권한이 필요합니다.")
        }
    } else {
        // 버튼을 2개만 표시하는 Wi-Fi 설정 화면 호출
        WifiSettingsScreen()
    }
}

@Composable
fun WifiSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { openWifiSettings(context) }) {
            Text(text = "Wi-Fi 설정 열기")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { openWebPage(context) }) {
            Text(text = "ESP32-CAM 웹 페이지 열기")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "ESP32-CAM에 연결하려면 Wi-Fi 설정에서 해당 네트워크를 선택하세요.")
    }
}

// 시스템 Wi-Fi 설정창을 여는 함수
fun openWifiSettings(context: Context) {
    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

// 지정된 웹 페이지 (http://192.168.4.1/) 열기 함수
fun openWebPage(context: Context) {
    val webpage = Uri.parse("http://192.168.4.1/")
    val intent = Intent(Intent.ACTION_VIEW, webpage)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

// MQTT "connected" 상태를 백그라운드에서 감지하여 onConnected 콜백 실행
@Composable
fun MqttStatusListener(onConnected: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        var mqttClient: MqttClient? = null
        try {
            // loadConfig()와 getSocketFactory()는 MqttScreen.kt에 정의되어 있음 (또는 공용 함수로 이동)
            val (endpoint, port) = loadConfig(context)
            val brokerUri = "ssl://${endpoint}:${port}"
            val clientId = "AndroidClient_Main_${System.currentTimeMillis()}"
            mqttClient = MqttClient(brokerUri, clientId, null)
            val options = MqttConnectOptions().apply {
                socketFactory = getSocketFactory(context)
                isAutomaticReconnect = true
                isCleanSession = false
            }
            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    // 필요 시 재연결 처리
                }
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic == "esp32cam/status" && message.toString() == "connected") {
                        onConnected()
                    }
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
            mqttClient.connect(options)
            mqttClient.subscribe("esp32cam/status", 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onDispose {
            try {
                mqttClient?.disconnect()
            } catch (e: Exception) {
                // 예외 무시
            }
        }
    }
}
