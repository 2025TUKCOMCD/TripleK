package com.example.testui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun MainScreen() {
    // showMqttScreen: true이면 MQTT 관련 화면, false이면 Wi-Fi 설정 화면을 보여줌
    var showMqttScreen by remember { mutableStateOf(false) }

    if (!showMqttScreen) {
        // Wi-Fi 연결(설정) 화면에서 연결이 완료되면 onConnected 콜백을 통해 MQTT 화면으로 전환
        LocationAndWifiScreen(onConnected = { showMqttScreen = true })
    } else {
        // MQTT 화면: ESP32-CAM의 상태 및 실시간 데이터를 수신 (MqttScreen.kt에서 구현)
        MqttScreen()
    }
}

@Composable
fun LocationAndWifiScreen(modifier: Modifier = Modifier, onConnected: () -> Unit) {
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
        // Wi-Fi 스캔 및 연결 기능 대신, 시스템 Wi-Fi 설정 창을 여는 화면으로 변경
        WifiSettingsScreen(onConnected = onConnected)
    }
}

@Composable
fun WifiSettingsScreen(modifier: Modifier = Modifier, onConnected: () -> Unit) {
    val context = LocalContext.current

    // Wi-Fi 설정창 열기 버튼을 누르면 시스템의 Wi-Fi 설정 화면으로 전환됩니다.
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            openWifiSettings(context)
        }) {
            Text(text = "Wi-Fi 설정 열기")
        }
        Spacer(modifier = Modifier.height(16.dp))
        // 추가된 버튼: ESP32-CAM 웹 페이지 열기
        Button(onClick = {
            openWebPage(context)
        }) {
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

// 추가된 함수: 지정된 웹 페이지 열기 (http://192.168.4.1/)
fun openWebPage(context: Context) {
    val webpage = Uri.parse("http://192.168.4.1/")
    val intent = Intent(Intent.ACTION_VIEW, webpage)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/* 기존 WifiItem(), WifiConnectDialog(), connectWifiQAndAbove() 등은 더 이상 사용하지 않거나,
   시스템 설정창으로 전환하므로 삭제하거나 별도로 보관할 수 있습니다. */
@Composable
fun WifiItem(scanResult: ScanResult, onClick: () -> Unit) {
    val level = WifiManager.calculateSignalLevel(scanResult.level, 5)
    val capabilities = scanResult.capabilities ?: ""
    val encryption = if (capabilities.contains("WPA") || capabilities.contains("WEP")) "(보안)" else "(오픈)"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Wifi,
            contentDescription = "Wifi Icon",
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(text = "${scanResult.SSID.ifBlank { "(숨김 SSID)" }} $encryption / 신호 레벨: $level/4")
    }
}
