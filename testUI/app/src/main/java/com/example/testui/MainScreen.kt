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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val mqttManager = remember { MqttManager(context) }
    LaunchedEffect(Unit) { mqttManager.connect() }

    var hasPermissions by remember { mutableStateOf(false) }
    val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms -> hasPermissions = perms.all { it.value } }
    )
    LaunchedEffect(Unit) {
        val granted = requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (!granted) launcher.launch(requiredPermissions.toTypedArray())
        else hasPermissions = true
    }

    if (!hasPermissions) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .semantics { contentDescription = "위치 권한 필요 안내" },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Wi-Fi 설정을 위해 위치 권한이 필요합니다.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        if (!mqttManager.mqttConnected) WifiSettingsScreen()
        else MqttScreen(mqttManager)
    }
}

@Composable
fun WifiSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .semantics { contentDescription = "Wi-Fi 및 ESP32-CAM 설정 화면" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { openWifiSettings(context) },
            modifier = Modifier.semantics { contentDescription = "Wi-Fi 설정 열기 버튼" }
        ) { Text(text = "Wi-Fi 설정 열기") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { openWebPage(context) },
            modifier = Modifier.semantics { contentDescription = "ESP32-CAM 웹 페이지 열기 버튼" }
        ) { Text(text = "ESP32-CAM 웹 페이지 열기") }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "ESP32-CAM에 연결하려면 Wi-Fi 설정에서 해당 네트워크를 선택하세요.")
    }
}

fun openWifiSettings(context: Context) {
    Intent(Settings.ACTION_WIFI_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(this)
    }
}

fun openWebPage(context: Context) {
    Intent(Intent.ACTION_VIEW, Uri.parse("http://192.168.4.1/")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(this)
    }
}