import os
import cv2
import json
import numpy as np
from time import sleep
from datetime import datetime
import paho.mqtt.client as mqtt
from ultralytics import YOLO

# 카메라 간 거리 (Baseline, B) - 단위: cm
B = 5  # 예시: 5cm
# 카메라 초점 거리 (픽셀 단위, 실제 측정 필요)
f = 500  # 예시 값

# MQTT 설정
BROKER_ADDRESS = "localhost"
PORT = 1883
PUB_TOPIC = "esp32cam/processed"
STATUS_TOPIC = "esp32cam/status"

# 디렉토리 설정
SAVE_DIR_0 = "./images_0"
SAVE_DIR_1 = "./images_1"
os.makedirs(SAVE_DIR_0, exist_ok=True)
os.makedirs(SAVE_DIR_1, exist_ok=True)

# YOLO 모델 로드
model = YOLO("yolo11m.pt")

# StereoSGBM 객체 생성
stereo = cv2.StereoSGBM_create(
    minDisparity=0,
    numDisparities=16 * 5,
    blockSize=5,
    P1=8 * 3 * 5**2,
    P2=32 * 3 * 5**2,
    disp12MaxDiff=1,
    uniquenessRatio=10,
    speckleWindowSize=100,
    speckleRange=32
)

def connect_mqtt():
    """MQTT 브로커에 연결"""
    client = mqtt.Client()
    client.connect(BROKER_ADDRESS, PORT, 60)
    client.loop_start()
    client.publish(STATUS_TOPIC, "connected")
    return client

def publish_message(client, topic, message):
    """MQTT 메시지 전송"""
    client.publish(STATUS_TOPIC, "connected")
    client.publish(topic, json.dumps(message))

def get_latest_images():
    """디렉토리에서 가장 최신 이미지를 선택"""
    image_files_0 = sorted(os.listdir(SAVE_DIR_0), key=lambda f: -os.path.getmtime(os.path.join(SAVE_DIR_0, f)))
    image_files_1 = sorted(os.listdir(SAVE_DIR_1), key=lambda f: -os.path.getmtime(os.path.join(SAVE_DIR_1, f)))

    if not image_files_0 or not image_files_1:
        return None, None

    return os.path.join(SAVE_DIR_0, image_files_0[0]), os.path.join(SAVE_DIR_1, image_files_1[0])

def delete_images(img0_path, img1_path):
    """처리 완료된 이미지 삭제"""
    if os.path.exists(img0_path):
        os.remove(img0_path)
    if os.path.exists(img1_path):
        os.remove(img1_path)

def detect_objects(img):
    """YOLO를 이용한 객체 탐지"""
    results = model(img, verbose=False)
    detected_objects = [
        box for box in results[0].boxes if box.conf[0].item() >= 0.8
    ]
    return detected_objects

def assess_risk(distance):
    """객체의 거리 기반 위험도 평가"""
    if distance < 200:
        return "high"
    elif distance < 400:
        return "medium"
    return "low"

def compute_depth_map(img_left, img_right):
    """스테레오 이미지를 이용하여 깊이 맵을 계산"""
    gray_left = cv2.cvtColor(img_left, cv2.COLOR_BGR2GRAY)
    gray_right = cv2.cvtColor(img_right, cv2.COLOR_BGR2GRAY)
    
    disparity = stereo.compute(gray_left, gray_right).astype(np.float32) / 16.0
    disparity[disparity <= 0.0] = np.nan  # 유효하지 않은 값 제거
    depth_map = (B * f) / disparity
    return disparity, depth_map

def process_images(client):
    """이미지를 처리하고 MQTT로 결과 전송"""
    try:
        while True:
            img0_path, img1_path = get_latest_images()
            if img0_path is None or img1_path is None:
                sleep(1)
                continue

            img_right = cv2.imread(img0_path)
            img_left = cv2.imread(img1_path)
            if img_right is None or img_left is None:
                sleep(1)
                continue

            detected_objects = detect_objects(img_left)
            if not detected_objects:
                delete_images(img0_path, img1_path)
                sleep(1)
                continue

            # 심도 맵 계산
            disparity, depth_map = compute_depth_map(img_left, img_right)
            objects_data = []

            for box in detected_objects:
                coords = box.xyxy[0].cpu().numpy()
                x1, y1, x2, y2 = map(int, coords)
                h_img, w_img = img_left.shape[:2]
                x1, x2 = max(0, min(x1, w_img - 1)), max(0, min(x2, w_img - 1))
                y1, y2 = max(0, min(y1, h_img - 1)), max(0, min(y2, h_img - 1))

                if (x2 - x1) < 5 or (y2 - y1) < 5:
                    continue

                region_disp = disparity[y1:y2, x1:x2]
                valid_disp = region_disp[np.isfinite(region_disp)]
                if valid_disp.size == 0:
                    continue

                avg_disp = float(np.mean(valid_disp))
                distance = (B * f) / avg_disp
                label = model.names[int(box.cls[0].item())] if hasattr(model, "names") else str(int(box.cls[0].item()))
                risk_level = assess_risk(distance)

                objects_data.append({
                    "label": label,
                    "distance": round(distance, 2),
                    "risk_level": risk_level
                })

                print(f"Detected {label}: Distance = {round(distance, 2)} cm, Confidence = {box.conf[0].item()}")

            if objects_data:
                publish_message(client, PUB_TOPIC, {
                    "timestamp": datetime.now().isoformat(),
                    "objects": objects_data
                })

            delete_images(img0_path, img1_path)
            sleep(1)

    except KeyboardInterrupt:
        print("프로그램 종료")
    finally:
        client.loop_stop()
        client.disconnect()

# 실행
if __name__ == "__main__":
    mqtt_client = connect_mqtt()
    process_images(mqtt_client)
