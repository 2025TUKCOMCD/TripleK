#include "secrets.h"
#include <WiFiClientSecure.h>
#include <MQTTClient.h>
#include "WiFi.h"
#include "esp_camera.h"
#include <WiFiManager.h>

// GPIO 설정
#define TRIGGER_PIN 14  // 마스터 신호를 받을 핀
#define ESP32CAM_PUBLISH_TOPIC "esp32/cam_1"

// AWS 및 MQTT 설정
WiFiClientSecure net = WiFiClientSecure();
MQTTClient client = MQTTClient(1024 * 23);

void connectAWS() {
  WiFiManager wm;
  wm.autoConnect("ESP32_Config", "12345678");

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  net.setCACert(AWS_CERT_CA);
  net.setCertificate(AWS_CERT_CRT);
  net.setPrivateKey(AWS_CERT_PRIVATE);

  client.begin(AWS_IOT_ENDPOINT, 8883, net);
  client.setCleanSession(true);

  Serial.println("\n\n=====================");
  Serial.println("Connecting to AWS IOT");
  Serial.println("=====================\n\n");

  while (!client.connect(THINGNAME)) {
    Serial.print(".");
    delay(100);
  }

  if (!client.connected()) {
    Serial.println("AWS IoT Timeout!");
    ESP.restart();
  }

  Serial.println("\n\n=====================");
  Serial.println("AWS IoT Connected!");
  Serial.println("=====================\n\n");
}

void cameraInit() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = 5;
  config.pin_d1 = 18;
  config.pin_d2 = 19;
  config.pin_d3 = 21;
  config.pin_d4 = 36;
  config.pin_d5 = 39;
  config.pin_d6 = 34;
  config.pin_d7 = 35;
  config.pin_xclk = 0;
  config.pin_pclk = 22;
  config.pin_vsync = 25;
  config.pin_href = 23;
  config.pin_sscb_sda = 26;
  config.pin_sscb_scl = 27;
  config.pin_pwdn = 32;
  config.pin_reset = -1;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.frame_size = FRAMESIZE_VGA;
  config.jpeg_quality = 10;
  config.fb_count = 2;

  if (esp_camera_init(&config) != ESP_OK) {
    ESP.restart();
  }
}

void grabImage() {
  // WiFi 상태 유지 확인
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Disconnected! Reconnecting...");
    ESP.restart();
  }

  camera_fb_t *fb = esp_camera_fb_get();
  if (fb != NULL && fb->format == PIXFORMAT_JPEG && fb->len < 1024 * 23) {
    Serial.print("Image Length: ");
    Serial.print(fb->len);
    Serial.print("\t Publish Image: ");
    bool result = client.publish(ESP32CAM_PUBLISH_TOPIC, (const char*)fb->buf, fb->len);
    Serial.println(result);

    if (!result) {
      ESP.restart();
    }
  }
  esp_camera_fb_return(fb);
}

void setup() {
  Serial.begin(115200);
  pinMode(TRIGGER_PIN, INPUT); // TRIGGER_PIN을 입력으로 설정
  cameraInit();
  connectAWS();
}

void loop() {
  client.loop();

  // 마스터 신호 감지
  if (digitalRead(TRIGGER_PIN) == HIGH && client.connected()) {
    grabImage(); // 이미지를 캡처하고 AWS로 전송
  }
}
