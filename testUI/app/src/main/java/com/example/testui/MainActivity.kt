package com.example.testui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.testui.ui.theme.TestUITheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TestUITheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LocationPermissionHandler(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun LocationPermissionHandler(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(false) }

    // 권한 요청 런처
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions: Map<String, Boolean> ->
            // FINE_LOCATION 권한이 승인되면 true 로 처리
            hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }
    )

    // 최초 진입 시 한 번만 체크
    LaunchedEffect(Unit) {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocationGranted || !coarseLocationGranted) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            hasLocationPermission = true
        }
    }

    // UI
    Column(modifier = modifier.padding(16.dp)) {
        if (!hasLocationPermission) {
            Text(text = "위치 권한이 필요합니다.")
        } else {
            Text(text = "위치 권한이 허용되었습니다.")
            Spacer(modifier = Modifier.height(8.dp))
            WifiScanButton()
        }
    }
}

/**
 * Start Scan 버튼을 누르면 와이파이 스캔 결과를
 * 목록으로 표시하는 함수
 */
@Composable
fun WifiScanButton() {
    val context = LocalContext.current
    val wifiManager = remember {
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    // Android 13 이상이면 NEARBY_WIFI_DEVICES 권한도 필요
    val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }

    // 실제 스캔 결과를 저장하는 리스트 (Compose 상태)
    val wifiList = remember { mutableStateListOf<ScanResult>() }

    // "Start Scan" 버튼 누를 때마다 필요한 권한이 있는지 확인
    val permissionState = remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 모든 권한이 허가되었는지 체크
        permissionState.value = permissions.all { it.value == true }
    }

    Column {
        Button(
            onClick = {
                // 1) 모든 권한 체크
                val hasAllPermissions = requiredPermissions.all { perm ->
                    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                }

                if (!hasAllPermissions) {
                    // 2) 아직 권한이 없다면 요청
                    launcher.launch(requiredPermissions.toTypedArray())
                } else {
                    // 3) 권한이 있으면 Wi-Fi 스캔(ScanResults 확인)
                    // 실제로는 wifiManager.startScan() + BroadcastReceiver가 정석이지만,
                    // 간단히 wifiManager.scanResults를 즉시 가져오는 예시
                    val results = wifiManager.scanResults

                    // 로그 확인
                    results.forEach {
                        Log.i("WIFI_RESULT", "SSID: ${it.SSID}, BSSID: ${it.BSSID}")
                    }

                    // UI 갱신
                    wifiList.clear()
                    wifiList.addAll(results)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Start Scan")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 스캔 결과를 리스트로 표시
        WifiNetworkList(wifiList = wifiList)
    }
}

/**
 * 실제로 와이파이 목록(SSID 등)을 LazyColumn 으로 표시하는 컴포저블
 */
@Composable
fun WifiNetworkList(wifiList: List<ScanResult>) {
    LazyColumn {
        items(wifiList) { scanResult ->
            WifiListItem(scanResult)
        }
    }
}

/**
 * 단일 ScanResult(와이파이 네트워크)를 표시하는 항목
 */
@Composable
fun WifiListItem(scanResult: ScanResult) {
    // WifiManager.calculateSignalLevel(dbm, 단계 수)
    val level = WifiManager.calculateSignalLevel(scanResult.level, 5)
    // 0 ~ 4 (총 5단계) 사이의 정수

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        // Wi-Fi 아이콘
        Icon(
            imageVector = Icons.Filled.Wifi,
            contentDescription = "Wi-Fi Icon",
            modifier = Modifier.padding(end = 8.dp)
        )
        // SSID
        Text(
            text = scanResult.SSID.ifBlank { "(숨김 SSID)" },
            modifier = Modifier.weight(1f)
        )
        // 신호 레벨 표시
        Text(text = "Level: $level/4")
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Welcome $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TestUITheme {
        Greeting("Denis")
    }
}
