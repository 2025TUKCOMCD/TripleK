import os
import cv2
import numpy as np
import paho.mqtt.client as mqtt
from datetime import datetime

# MQTT 설정
BROKER_ADDRESS = "localhost"
PORT = 1883
TOPICS = ["esp32/cam_0", "esp32/cam_1"]
SAVE_DIRS = {
    "esp32/cam_0": "./images_0",
    "esp32/cam_1": "./images_1",
}

def create_directories():
    """이미지 저장 디렉토리 생성"""
    for directory in SAVE_DIRS.values():
        os.makedirs(directory, exist_ok=True)

def rotate_image(image_data):
    """수신된 JPEG 이미지를 90도 반시계 방향으로 회전"""
    image = cv2.imdecode(np.frombuffer(image_data, np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        print("이미지 디코딩 실패!")
        return None
    return cv2.rotate(image, cv2.ROTATE_90_COUNTERCLOCKWISE)

def save_image(topic, image_data):
    """이미지 파일 저장"""
    now = datetime.now()
    save_dir = SAVE_DIRS.get(topic, "./unknown")
    if save_dir == "./unknown":
        os.makedirs(save_dir, exist_ok=True)

    rotated_image = rotate_image(image_data)
    if rotated_image is not None:
        image_path = os.path.join(save_dir, f"image_{now.strftime('%d%H%M%S_%f')}.jpg")
        cv2.imwrite(image_path, rotated_image)
        print(f"이미지 저장 완료: {image_path}\n")
    else:
        print("이미지 저장 실패!")

def on_message(client, userdata, msg):
    """MQTT 메시지를 수신하면 실행되는 콜백 함수"""
    print(f"Received message on topic {msg.topic}")
    print(f"Payload length: {len(msg.payload)} bytes")
    try:
        save_image(msg.topic, msg.payload)
    except Exception as e:
        print(f"이미지를 저장하는 중 오류 발생: {e}")

def on_connect(client, userdata, flags, rc):
    """MQTT 브로커 연결 시 실행되는 콜백 함수"""
    if rc == 0:
        print("MQTT 브로커에 연결 성공!")
        for topic in TOPICS:
            client.subscribe(topic)
            print(f"토픽 구독: {topic}")
    else:
        print(f"MQTT 브로커에 연결 실패, 코드: {rc}")

def connect_mqtt():
    """MQTT 클라이언트 설정 및 연결"""
    client = mqtt.Client()
    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(BROKER_ADDRESS, PORT, 60)
    return client

def start_mqtt(client):
    """MQTT 이벤트 루프 실행"""
    try:
        print("이미지 수신 대기 중...")
        client.loop_forever()
    except KeyboardInterrupt:
        print("\n프로그램 종료")
        client.disconnect()

if __name__ == "__main__":
    create_directories()
    mqtt_client = connect_mqtt()
    start_mqtt(mqtt_client)
