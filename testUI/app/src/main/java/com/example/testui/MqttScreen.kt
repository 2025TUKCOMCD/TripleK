package com.example.testui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.eclipse.paho.client.mqttv3.*
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemReader
import org.json.JSONObject
import java.io.InputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun ChatBubble(message: String, isUserMessage: Boolean = false) {
    val alignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isUserMessage) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)

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
fun MqttScreen() {
    val context = LocalContext.current
    var mqttConnected by remember { mutableStateOf(false) }
    val receivedMessages = remember { mutableStateListOf<String>() }
    var mqttClient: MqttClient? = null

    LaunchedEffect(Unit) {
        val (endpoint, port) = loadConfig(context)
        val brokerUri = "ssl://${endpoint}:${port}"  // 실제 MQTT 브로커 주소로 변경하세요.
        val clientId = "AndroidClient_${System.currentTimeMillis()}"
        mqttClient = MqttClient(brokerUri, clientId, null)
        try {
            mqttClient = MqttClient(brokerUri, MqttClient.generateClientId(), null)
            val options = MqttConnectOptions().apply {
                socketFactory = getSocketFactory(context)
                isAutomaticReconnect = true  // 자동 재연결 활성화
                isCleanSession = false  // 세션 유지
            }

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    mqttClient?.reconnect()  // 연결이 끊어지면 자동 재연결
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val msg = message.toString()
                    when (topic) {
                        "esp32cam/status" -> {
                            if (msg == "connected") {
                                mqttConnected = true
                            }
                        }
                        "esp32cam/processed" -> {
                            try {
                                val jsonObject = JSONObject(msg)
                                val objectsArray = jsonObject.getJSONArray("objects")
                                val sb = StringBuilder()
                                for (i in 0 until objectsArray.length()) {
                                    val obj = objectsArray.getJSONObject(i)
                                    val objectName = obj.getString("object")
                                    val confidence = obj.getDouble("confidence")
                                    // 원하는 형식: "person - 0.87"
                                    sb.append("$objectName - $confidence")
                                    if (i < objectsArray.length() - 1) {
                                        sb.append("\n")
                                    }
                                }
                                // 새로운 메시지가 도착할 때마다 기존 메시지 아래에 추가
                                receivedMessages.add(sb.toString())
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // 발행한 메시지의 전달 완료 처리 (필요시 구현)
                }
            })

            mqttClient?.connect(options)

            mqttClient?.subscribe("esp32cam/status", 1)
            mqttClient?.subscribe("esp32cam/processed", 1)
        }
        catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    if (!mqttConnected) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "MQTT 서버에 연결 중...\nESP32-CAM의 상태 대기 중...")
        }
    } else {
        // 연결 성공 후 실시간 데이터(채팅 형태) 화면
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(text = "ESP32-CAM 연결 성공!\n실시간 데이터를 수신 중:")
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(receivedMessages) { msg ->
                    ChatBubble(message = msg)
                }
            }
        }
    }
}

/**
 * AWS IoT Core 설정 파일 로드
 */
private fun loadConfig(context: Context): Pair<String, Int> {
    val assetManager = context.assets
    val inputStream: InputStream = assetManager.open("certs/aws_config.json")
    val json = inputStream.bufferedReader().use { it.readText() }

    val jsonObject = JSONObject(json)
    val endpoint = jsonObject.getString("endpoint")
    val port = jsonObject.getInt("port")

    return Pair(endpoint, port)
}

/**
 * AWS IoT Core 인증서 기반 SSL 설정
 */
private fun getSocketFactory(context: Context): javax.net.ssl.SSLSocketFactory {
    val assetManager = context.assets
    val cf = CertificateFactory.getInstance("X.509")

    // rootCA.pem 로드
    val caInput: InputStream = assetManager.open("certs/rootCA.pem")
    val ca = caInput.use { cf.generateCertificate(it) }

    // cert.crt 로드
    val certInput: InputStream = assetManager.open("certs/cert.crt")
    val cert = certInput.use { cf.generateCertificate(it) }

    // private.key 로드 (PEM 포맷 변환 적용)
    val keyInput: InputStream = assetManager.open("certs/private.key")
    val privateKey = getPrivateKeyFromPEM(keyInput.readBytes())

    // KeyStore 생성 및 초기화
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
        setCertificateEntry("ca", ca)
        setCertificateEntry("cert", cert)
        setKeyEntry("private-key", privateKey, null, arrayOf(cert))
    }

    // TrustManager 설정 (서버 인증)
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
        init(keyStore)
    }

    // KeyManager 설정 (클라이언트 인증)
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
        init(keyStore, null)
    }

    // SSLContext 설정
    return SSLContext.getInstance("TLS").apply {
        init(kmf.keyManagers, tmf.trustManagers, null)
    }.socketFactory
}

private fun getPrivateKeyFromPEM(pemBytes: ByteArray): PrivateKey {
    val pemString = String(pemBytes, StandardCharsets.UTF_8)
    val pemReader = PemReader(StringReader(pemString))
    val pemObject: PemObject = pemReader.readPemObject()
    pemReader.close()

    val keyBytes = pemObject.content
    return convertPKCS1ToPKCS8(keyBytes)
}

private fun convertPKCS1ToPKCS8(pkcs1Bytes: ByteArray): PrivateKey {
    val pkcs8Spec = PKCS8EncodedKeySpec(pkcs1Bytes)
    val keyFactory = KeyFactory.getInstance("RSA")
    return keyFactory.generatePrivate(pkcs8Spec)
}
