import os
import paho.mqtt.client as mqtt

# 기본 설정
BROKER_ADDRESS = "localhost"
PORT = 1883
TOPIC = "esp32/cam_0"
SAVE_DIR = "./images"

# 디렉토리 생성
os.makedirs(SAVE_DIR, exist_ok=True)
# MQTT 연결 콜백 함수
def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("MQTT 브로커에 연결 성공!")
        client.subscribe(TOPIC)  # 토픽 구독
        print(f"토픽 구독: {TOPIC}")
    else:
        print(f"MQTT 브로커에 연결 실패, 코드: {rc}")

# MQTT 클라이언트 설정
client = mqtt.Client()
client.on_connect = on_connect

# 브로커에 연결
print("MQTT 브로커에 연결 중...")
client.connect(BROKER_ADDRESS, PORT, 60)
