import os
import cv2
import json
import numpy as np
from time import sleep
from datetime import datetime
import paho.mqtt.client as mqtt
from ultralytics import YOLO
from sort import Sort  # SORT 트래커 추가

# MQTT 설정
BROKER_ADDRESS = "localhost"
PORT = 1883
PUB_TOPIC = "esp32cam/processed"
STATUS_TOPIC = "esp32cam/status"

# 디렉토리 설정
SAVE_DIR_0 = "./images_0"  # 오른쪽 카메라
SAVE_DIR_1 = "./images_1"  # 왼쪽 카메라
os.makedirs(SAVE_DIR_0, exist_ok=True)
os.makedirs(SAVE_DIR_1, exist_ok=True)

# YOLO 모델 로드
model = YOLO("last.pt")

# SORT 트래커
tracker = Sort()

# 이전 프레임 객체 크기 저장
previous_areas = {}

# 클래스별 area 기반 근접 판단 임계값
proximity_thresholds = {
    "person": [2000, 5000, 10000],
    "car": [5000, 10000, 20000],
    "bus": [8000, 16000, 25000],
    "truck": [8000, 16000, 25000],
    "bike": [1500, 3000, 6000],
    "bollard": [1000, 2000, 4000],
    "fireplug": [1000, 2000, 4000],
    "kickboard": [1500, 3000, 6000],
    "motorcycle": [1500, 3000, 6000],
    "trafficcone": [1000, 2000, 4000],
    "trafficlight": [1000, 2000, 4000],
    "tubular marker": [1000, 2000, 4000],
    "pillar": [1500, 3000, 6000],
    # 기타 클래스 기본값
    "default": [2000, 5000, 10000]
}

def connect_mqtt():
    client = mqtt.Client()
    client.connect(BROKER_ADDRESS, PORT, 60)
    client.loop_start()
    client.publish(STATUS_TOPIC, "connected")
    return client

def publish_message(client, topic, message):
    client.publish(STATUS_TOPIC, "connected")
    client.publish(topic, json.dumps(message))

def get_latest_images():
    image_files_0 = sorted(os.listdir(SAVE_DIR_0), key=lambda f: -os.path.getmtime(os.path.join(SAVE_DIR_0, f)))
    image_files_1 = sorted(os.listdir(SAVE_DIR_1), key=lambda f: -os.path.getmtime(os.path.join(SAVE_DIR_1, f)))
    if not image_files_0 or not image_files_1:
        return None, None
    return os.path.join(SAVE_DIR_0, image_files_0[0]), os.path.join(SAVE_DIR_1, image_files_1[0])

def delete_images(img0_path, img1_path):
    if os.path.exists(img0_path):
        os.remove(img0_path)
    if os.path.exists(img1_path):
        os.remove(img1_path)

def detect_objects(img):
    results = model(img, verbose=False)
    return [box for box in results[0].boxes if box.conf[0].item() >= 0.8]

def bbox_area(box):
    x1, y1, x2, y2 = map(int, box.xyxy[0].cpu().numpy())
    return (x2 - x1) * (y2 - y1)

def get_proximity(label, area):
    thresholds = proximity_thresholds.get(label.lower(), proximity_thresholds["default"])
    if area > thresholds[2]:
        return "very_close"
    elif area > thresholds[1]:
        return "close"
    elif area > thresholds[0]:
        return "medium"
    else:
        return "far"

def process_images(client):
    global previous_areas

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

            boxes_left = detect_objects(img_left)
            h_img, w_img = img_left.shape[:2]

            detections = []
            for box in boxes_left:
                coords = box.xyxy[0].cpu().numpy()
                x1, y1, x2, y2 = coords
                detections.append([x1, y1, x2, y2, box.conf[0].item()])

            tracked_objects = tracker.update(np.array(detections))
            objects_data = []

            for i, box in enumerate(boxes_left):
                coords = box.xyxy[0].cpu().numpy()
                x1, y1, x2, y2 = map(int, coords)
                area = bbox_area(box)
                label = model.names[int(box.cls[0].item())] if hasattr(model, "names") else str(int(box.cls[0].item()))
                x_center = (x1 + x2) / 2
                center_region = (w_img * 0.3 < x_center < w_img * 0.7)

                proximity = get_proximity(label, area)

                # 위험도 판단
                if proximity in ["very_close", "close"] and center_region:
                    risk_level = "high"
                elif proximity in ["very_close", "close", "medium"]:
                    risk_level = "medium"
                else:
                    risk_level = "low"

                # 접근 여부 판단
                approaching = False
                if label in previous_areas:
                    if area > previous_areas[label] * 1.2:
                        approaching = True
                previous_areas[label] = area

                objects_data.append({
                    "label": label,
                    "approaching": approaching,
                    "proximity": proximity,
                    "risk_level": risk_level
                })

                print(f"Detected {label}: Area = {area}, Proximity = {proximity}, Risk = {risk_level}, Approaching = {approaching}")

            if objects_data and (risk_level == "high" and approaching == True):
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

if __name__ == "__main__":
    mqtt_client = connect_mqtt()
    process_images(mqtt_client)
