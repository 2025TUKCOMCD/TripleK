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

@Composable
fun MainScreen() {
    val context = LocalContext.current

    // 단일 MQTT 클라이언트(매니저) 생성
    val mqttManager = remember { MqttManager(context) }

    // MQTT 연결 시도 (이미 연결되어 있으면 재시도하지 않음)
    LaunchedEffect(Unit) {
        mqttManager.connect()
    }

    var hasPermissions by remember { mutableStateOf(false) }
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
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Wi-Fi 설정을 위해 위치 권한이 필요합니다.")
        }
    } else {
        // MQTT 연결 상태에 따라 Wi‑Fi 설정 화면 또는 MQTT 메시지 화면을 표시
        if (!mqttManager.mqttConnected) {
            WifiSettingsScreen()
        } else {
            MqttScreen(mqttManager)
        }
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

fun openWifiSettings(context: Context) {
    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun openWebPage(context: Context) {
    val webpage = Uri.parse("http://192.168.4.1/")
    val intent = Intent(Intent.ACTION_VIEW, webpage)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
