#include "secret.h"
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <MQTTClient.h>
#include "WiFi.h"
#include "esp_camera.h"
#include <WiFiManager.h>

// ESP32-S3-EYE 카메라 핀 설정 (OV5640 사용)
#define PWDN_GPIO_NUM  -1
#define RESET_GPIO_NUM -1
#define XCLK_GPIO_NUM  15
#define SIOD_GPIO_NUM  4
#define SIOC_GPIO_NUM  5
#define Y9_GPIO_NUM    16
#define Y8_GPIO_NUM    17
#define Y7_GPIO_NUM    18
#define Y6_GPIO_NUM    12
#define Y5_GPIO_NUM    10
#define Y4_GPIO_NUM    8
#define Y3_GPIO_NUM    9
#define Y2_GPIO_NUM    11
#define VSYNC_GPIO_NUM 6
#define HREF_GPIO_NUM  7
#define PCLK_GPIO_NUM  13

#define ESP32CAM_PUBLISH_TOPIC   "esp32/cam_0"

const int bufferSize = 1024 * 35;

WiFiClientSecure net = WiFiClientSecure();
MQTTClient client = MQTTClient(bufferSize);

void connectAWS() {
  Serial.println("\n\n=====================");
  Serial.println("Connecting to Wi-Fi");
  Serial.println("=====================\n\n");
  
  WiFiManager wm;
  // 이전 WiFi 설정 삭제 (필요 시 주석 해제)
  //wm.resetSettings();
  wm.autoConnect("ESP32_Config");

  while (WiFi.status() != WL_CONNECTED){
    delay(500);
    Serial.print(".");
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("WiFi Connected!");
    Serial.println("IP Address: " + WiFi.localIP().toString());
  } else {
    Serial.println("Failed to connect to WiFi. Restarting...");
    ESP.restart();
  }

  net.setCACert(AWS_CERT_CA);
  net.setCertificate(AWS_CERT_CRT);
  net.setPrivateKey(AWS_CERT_PRIVATE);

  client.begin(AWS_IOT_ENDPOINT, 8883, net);
  client.setCleanSession(true);

  Serial.println("\n\n=====================");
  Serial.println("Connecting to AWS IoT");
  Serial.println("=====================\n\n");

  while (!client.connect(THINGNAME)) {
    Serial.print(".");
    delay(100);
  }

  if(!client.connected()){
    Serial.println("AWS IoT Timeout!");
    ESP.restart();
    return;
  }

  Serial.println("\n\n=====================");
  Serial.println("AWS IoT Connected!");
  Serial.println("=====================\n\n");
}

void cameraInit() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 24000000;  // OV5640은 24MHz 권장
  config.pixel_format = PIXFORMAT_JPEG;
  config.frame_size = FRAMESIZE_VGA;
  config.jpeg_quality = 7;
  config.fb_count = 2; // PSRAM 있음

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed with error 0x%x", err);
    ESP.restart();
    return;
  }

  sensor_t *s = esp_camera_sensor_get();
  if (s->id.PID == OV5640_PID) {
    s->set_vflip(s, 1);  // 이미지 상하 반전 (필요 시)
    s->set_hmirror(s, 1); // 좌우 반전
    s->set_brightness(s, 1);
    s->set_saturation(s, 2);
    s->set_sharpness(s, 2);     // 선명도 증가 (-2 ~ 2) → 윤곽선 더 뚜렷하게
    s->set_whitebal(s, 1);      // 자동 화이트 밸런스 활성화
    s->set_awb_gain(s, 1);      // 화이트 밸런스 보정 활성화
  }
}

void grabImage() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Disconnected! Reconnecting...");
    ESP.restart();
  }

  camera_fb_t *fb = esp_camera_fb_get();
  if (fb != NULL && fb->format == PIXFORMAT_JPEG && fb->len < bufferSize) {
    Serial.print("Image Length: ");
    Serial.print(fb->len);
    Serial.print("\t Publish Image: ");
    bool result = client.publish(ESP32CAM_PUBLISH_TOPIC, (const char*)fb->buf, fb->len, false, 0);
    Serial.println(result);

    if (!result) {
      ESP.restart();
    }
  }
  esp_camera_fb_return(fb);
  delay(500);
}

void setup() {
  Serial.begin(115200);
  cameraInit();
  connectAWS();
}

void loop() {
  client.loop();
  if(client.connected()) grabImage();
}
