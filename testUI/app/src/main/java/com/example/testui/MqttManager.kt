package com.example.testui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject

class MqttManager(private val context: Context) {
    var mqttClient: MqttClient? = null
    var mqttConnected by mutableStateOf(false)
    var receivedMessages = mutableStateListOf<String>()

    fun connect() {
        if (mqttClient?.isConnected == true) return
        try {
            val (endpoint, port) = loadConfig(context)
            val brokerUri = "ssl://$endpoint:$port"
            val clientId = "AndroidClient_${System.currentTimeMillis()}"
            mqttClient = MqttClient(brokerUri, clientId, null)
            val options = MqttConnectOptions().apply {
                socketFactory = getSocketFactory(context)
                isAutomaticReconnect = true
                isCleanSession = false
            }
            mqttClient?.setCallback(object : MqttCallback {
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
                        "esp32cam/processed" -> {
                            try {
                                val jsonObject = JSONObject(msg)
                                val objectsArray = jsonObject.getJSONArray("objects")
                                val sb = StringBuilder()
                                for (i in 0 until objectsArray.length()) {
                                    val obj = objectsArray.getJSONObject(i)
                                    val label = obj.getString("label")
                                    val distance = obj.getDouble("distance")
                                    val riskLevel = obj.getString("risk_level")
                                    sb.append("$label $distance $riskLevel")
                                    if (i < objectsArray.length() - 1) {
                                        sb.append("\n")
                                    }
                                }
                                val finalMsg = sb.toString()
                                receivedMessages.add(finalMsg)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            mqttClient?.connect(options)
            mqttClient?.subscribe("esp32cam/status", 1)
            mqttClient?.subscribe("esp32cam/processed", 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// AWS IoT Core 설정 파일 로드
internal fun loadConfig(context: Context): Pair<String, Int> {
    val assetManager = context.assets
    val inputStream = assetManager.open("certs/aws_config.json")
    val json = inputStream.bufferedReader().use { it.readText() }
    val jsonObject = JSONObject(json)
    val endpoint = jsonObject.getString("endpoint")
    val port = jsonObject.getInt("port")
    return Pair(endpoint, port)
}

// AWS IoT Core 인증서 기반 SSL 설정
internal fun getSocketFactory(context: Context): javax.net.ssl.SSLSocketFactory {
    val assetManager = context.assets
    val cf = java.security.cert.CertificateFactory.getInstance("X.509")
    val caInput = assetManager.open("certs/rootCA.pem")
    val ca = caInput.use { cf.generateCertificate(it) }
    val certInput = assetManager.open("certs/cert.crt")
    val cert = certInput.use { cf.generateCertificate(it) }
    val keyInput = assetManager.open("certs/private.key")
    val privateKey = getPrivateKeyFromPEM(keyInput.readBytes())
    val keyStore = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType()).apply {
        load(null, null)
        setCertificateEntry("ca", ca)
        setCertificateEntry("cert", cert)
        setKeyEntry("private-key", privateKey, null, arrayOf(cert))
    }
    val tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()).apply {
        init(keyStore)
    }
    val kmf = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm()).apply {
        init(keyStore, null)
    }
    return javax.net.ssl.SSLContext.getInstance("TLS").apply {
        init(kmf.keyManagers, tmf.trustManagers, null)
    }.socketFactory
}

internal fun getPrivateKeyFromPEM(pemBytes: ByteArray): java.security.PrivateKey {
    val pemString = String(pemBytes, Charsets.UTF_8)
    val pemReader = org.bouncycastle.util.io.pem.PemReader(java.io.StringReader(pemString))
    val pemObject = pemReader.readPemObject()
    pemReader.close()
    val keyBytes = pemObject.content
    return convertPKCS1ToPKCS8(keyBytes)
}

internal fun convertPKCS1ToPKCS8(pkcs1Bytes: ByteArray): java.security.PrivateKey {
    val pkcs8Spec = java.security.spec.PKCS8EncodedKeySpec(pkcs1Bytes)
    val keyFactory = java.security.KeyFactory.getInstance("RSA")
    return keyFactory.generatePrivate(pkcs8Spec)
}
