package com.example.testui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun MainScreen() {
    // showMqttScreen: true이면 MQTT 관련 화면, false이면 Wi-Fi 연결 화면을 보여줌
    var showMqttScreen by remember { mutableStateOf(false) }

    if (!showMqttScreen) {
        // Wi-Fi 연결이 성공하면 onConnected 콜백을 통해 MQTT 화면으로 전환
        LocationAndWifiScreen(onConnected = { showMqttScreen = true })
    } else {
        // MQTT 화면: ESP32-CAM의 상태 및 실시간 데이터를 수신
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
            Text(text = "Wi-Fi 스캔 및 연결을 위해 위치 권한이 필요합니다.")
        }
    } else {
        WifiScanAndConnectScreen(modifier = modifier, onConnected = onConnected)
    }
}

@Composable
fun WifiScanAndConnectScreen(modifier: Modifier = Modifier, onConnected: () -> Unit) {
    val context = LocalContext.current
    val wifiManager = remember { context.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    val wifiList = remember { mutableStateListOf<ScanResult>() }
    var selectedAp by remember { mutableStateOf<ScanResult?>(null) }
    var isConnected by remember { mutableStateOf(false) }

    // BroadcastReceiver를 이용해 Wi-Fi 스캔 결과를 수신
    DisposableEffect(context) {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                if (success) {
                    // ACCESS_FINE_LOCATION 권한 체크 후 scanResults 사용
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val results = wifiManager.scanResults
                        wifiList.clear()
                        wifiList.addAll(results)
                        Log.d("WiFiScan", "Got scan results: ${results.size}")
                    } else {
                        Log.e("WiFiScan", "ACCESS_FINE_LOCATION permission is not granted.")
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        if (!isConnected) {
            // Wi-Fi 스캔 버튼
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    wifiManager.startScan()
                } else {
                    Log.e("WiFiScan", "위치 권한이 없어 Wi-Fi 스캔을 시작할 수 없습니다.")
                }
            }) {
                Text(text = "Wi-Fi Scan")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "검색된 Wi-Fi 목록 (${wifiList.size}개)")
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn {
                items(wifiList) { scanResult ->
                    WifiItem(scanResult = scanResult, onClick = { selectedAp = scanResult })
                }
            }
        } else {
            Text(text = "Wi-Fi에 성공적으로 연결되었습니다!")
            // 연결 성공 시 onConnected 콜백 호출하여 MQTT 화면으로 전환
            LaunchedEffect(Unit) {
                onConnected()
            }
        }
    }

    // AP 선택 시 연결 다이얼로그 표시
    selectedAp?.let { ap ->
        WifiConnectDialog(
            wifiManager = wifiManager,
            ap = ap,
            onDismiss = { selectedAp = null },
            onSuccessConnect = {
                isConnected = true
                selectedAp = null
            }
        )
    }
}

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

@Composable
fun WifiConnectDialog(
    wifiManager: WifiManager,
    ap: ScanResult,
    onDismiss: () -> Unit,
    onSuccessConnect: () -> Unit
) {
    val context = LocalContext.current
    val isSecure = remember(ap) {
        ap.capabilities.contains("WPA") || ap.capabilities.contains("WEP")
    }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Wi-Fi 연결") },
        text = {
            Column {
                Text(text = "네트워크: ${ap.SSID}")
                if (isSecure) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("비밀번호") }
                    )
                } else {
                    Text(text = "오픈 AP: 비밀번호가 필요 없습니다.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    connectWifiQAndAbove(
                        context = context,
                        ssid = ap.SSID,
                        password = if (isSecure) password else null,
                        onSuccess = { onSuccessConnect() },
                        onFail = { Log.e("WifiConnect", "연결에 실패했습니다.") }
                    )
                } else {
                    Log.d("WifiConnect", "Android 9 이하에서는 WifiConfiguration 방식 필요")
                }
                onDismiss()
            }) {
                Text(text = "연결")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "취소")
            }
        }
    )
}

@RequiresApi(Build.VERSION_CODES.Q)
fun connectWifiQAndAbove(
    context: Context,
    ssid: String,
    password: String?,
    onSuccess: () -> Unit,
    onFail: () -> Unit
) {
    val specifierBuilder = WifiNetworkSpecifier.Builder()
        .setSsid(ssid)
    if (!password.isNullOrEmpty()) {
        specifierBuilder.setWpa2Passphrase(password)
    }
    val wifiNetworkSpecifier = specifierBuilder.build()
    val networkRequest = NetworkRequest.Builder()
        .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        .setNetworkSpecifier(wifiNetworkSpecifier)
        .build()
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            connectivityManager.bindProcessToNetwork(network)
            onSuccess()
        }
        override fun onUnavailable() {
            super.onUnavailable()
            onFail()
        }
    }
    connectivityManager.requestNetwork(networkRequest, networkCallback)
}
