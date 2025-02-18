import os
import cv2
import numpy as np
import paho.mqtt.client as mqtt
from datetime import datetime

# MQTT 설정
BROKER_ADDRESS = "localhost"  # MQTT 브로커 주소
PORT = 1883  # MQTT 브로커 포트
TOPICS = ["esp32/cam_0", "esp32/cam_1"]  # ESP32-CAM에서 보내는 토픽
SAVE_DIRS = {
    "esp32/cam_0": "./images_0",
    "esp32/cam_1": "./images_1",
}

# 디렉토리 생성
for directory in SAVE_DIRS.values():
    os.makedirs(directory, exist_ok=True)

def rotate_image(image_data):
    """
    수신된 JPEG 이미지를 90도 반시계 방향으로 회전
    """
    image = cv2.imdecode(np.frombuffer(image_data, np.uint8), cv2.IMREAD_COLOR)

    if image is None:
        print("이미지 디코딩 실패!")
        return None

    # 90도 반시계 방향 회전
    rotated_image = cv2.rotate(image, cv2.ROTATE_90_COUNTERCLOCKWISE)
    return rotated_image

# 메시지 수신 콜백 함수
def on_message(client, userdata, msg):
    print(f"Received message on topic {msg.topic}")
    print(f"Payload length: {len(msg.payload)} bytes")
    
    try:
        now = datetime.now()
        save_dir = SAVE_DIRS.get(msg.topic, "./unknown")
        if save_dir == "./unknown":
            os.makedirs(save_dir, exist_ok=True)

        # 이미지 회전
        rotated_image = rotate_image(msg.payload)

        if rotated_image is not None:
            # 파일 이름 생성
            image_path = os.path.join(save_dir, f"image_{now.strftime('%d%H%M%S_%f')}.jpg")
            cv2.imwrite(image_path, rotated_image)
            print(f"이미지 저장 완료: {image_path}\n")
        else:
            print("이미지 저장 실패!")

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
