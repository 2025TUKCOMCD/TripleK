import os
import paho.mqtt.client as mqtt
from datetime import datetime

# MQTT 설정
BROKER_ADDRESS = "localhost"  # MQTT 브로커 주소
PORT = 1883                                # MQTT 브로커 포트
TOPICS = ["esp32/cam_0", "esp32/cam_1"]  # ESP32-CAM에서 보내는 토픽
SAVE_DIRS = {
    "esp32/cam_0": "./images_0",
    "esp32/cam_1": "./images_1",
}

# 디렉토리 생성
for directory in SAVE_DIRS.values():
    os.makedirs(directory, exist_ok=True)

# 메시지 수신 콜백 함수
def on_message(client, userdata, msg):
    print(f"Received message on topic {msg.topic}")
    print(f"Payload length: {len(msg.payload)} bytes")
    try:
        now = datetime.now()  # 현재 날짜와 시간으로 이미지 저장
        save_dir = SAVE_DIRS.get(msg.topic, "./unknown")
        if save_dir == "./unknown":
            os.makedirs(save_dir, exist_ok=True)
        # 파일 이름 생성
        image_path = os.path.join(save_dir, f"image_{now.strftime('%d%H%M%S_%f')}.jpg")
        with open(image_path, "wb") as f:
            f.write(msg.payload)  # 메시지 payload를 파일로 저장
        print(f"이미지 저장 완료: {image_path}\n")
    except Exception as e:
        print(f"이미지를 저장하는 중 오류 발생: {e}")

# MQTT 연결 콜백 함수
def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("MQTT 브로커에 연결 성공!")
        for topic in TOPICS:
            client.subscribe(topic)
            print(f"토픽 구독: {topic}")
    else:
        print(f"MQTT 브로커에 연결 실패, 코드: {rc}")

# MQTT 클라이언트 설정
client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message

# 브로커에 연결
print("MQTT 브로커에 연결 중...")
client.connect(BROKER_ADDRESS, PORT, 60)

# MQTT 이벤트 루프 실행
try:
    print("이미지 수신 대기 중...")
    client.loop_forever()
except KeyboardInterrupt:
    print("\n프로그램 종료")
    client.disconnect()
