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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.testui.ui.theme.TestUITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TestUITheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 위치 권한 체크 + Wi-Fi 스캔 기능
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
            // FINE_LOCATION 권한이 승인되면 true
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
    Column(modifier = modifier) {
        if (!hasLocationPermission) {
            // 권한이 없으면 안내만 표시
            Text(text = "위치 권한이 필요합니다.")
        } else {
            // Wi-Fi 스캔 버튼 표시
            Text(text = "위치 권한이 허용되었습니다.")
            WifiScanButton()
        }
    }
}

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

    val permissionState = remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 모든 권한이 허가되었는지 체크
        permissionState.value = permissions.all { it.value == true }
    }

    // 버튼 클릭 시 권한이 없으면 요청 -> 있으면 스캔
    Button(onClick = {
        // 1) 권한 체크
        val hasAllPermissions = requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasAllPermissions) {
            // 2) 아직 권한이 없다면 요청
            launcher.launch(requiredPermissions.toTypedArray())
        } else {
            // 3) 권한이 있으면 Wi-Fi 스캔(ScanResults 확인)
            val results = wifiManager.scanResults
            results.forEach {
                Log.i("WIFI_RESULT", "SSID: ${it.SSID}, BSSID: ${it.BSSID}")
            }
        }
    }) {
        Text(text = "SCAN Wi-Fi")
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
