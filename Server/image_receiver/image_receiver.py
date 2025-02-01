import os
import paho.mqtt.client as mqtt
from datetime import datetime

# 기본 설정
BROKER_ADDRESS = "localhost"
PORT = 1883
TOPIC = "esp32/cam_0"
SAVE_DIR = "./images"

# 디렉토리 생성
os.makedirs(SAVE_DIR, exist_ok=True)

def on_message(client, userdata, msg):
    try:
        now = datetime.now()  # 현재 날짜와 시간으로 이미지 저장
        
        # 파일 이름 생성 (images 폴더에 직접 저장)
        image_path = os.path.join(SAVE_DIR, f"image_{now.strftime('%d%H%M%S_%f')}.jpg")
        
        with open(image_path, "wb") as f:
            f.write(msg.payload)  # 메시지 payload를 파일로 저장

    except Exception as e:
        print(f"이미지를 저장하는 중 오류 발생: {e}")

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
client.on_message = on_message

# 브로커에 연결
print("MQTT 브로커에 연결 중...")
client.connect(BROKER_ADDRESS, PORT, 60)
