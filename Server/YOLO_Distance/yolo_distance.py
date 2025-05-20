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

# 모델 및 트래커 초기화
model = YOLO("last.pt")
tracker = Sort()

# ORB 특징점 탐지기
orb = cv2.ORB_create(500)
bf_matcher = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=True)

previous_areas = {}

def connect_mqtt():
    client = mqtt.Client()
    client.connect(BROKER_ADDRESS, PORT, 60)
    client.loop_start()
    client.publish(STATUS_TOPIC, "connected")
    return client

def convert_numpy(obj):
    if isinstance(obj, np.integer):
        return int(obj)
    elif isinstance(obj, np.floating):
        return float(obj)
    elif isinstance(obj, np.ndarray):
        return obj.tolist()
    else:
        raise TypeError(f"Object of type {type(obj)} is not JSON serializable")

def publish_message(client, topic, message):
    client.publish(STATUS_TOPIC, "connected")
    client.publish(topic, json.dumps(message, default=convert_numpy))

def get_latest_images():
    image_files_0 = sorted(os.listdir(SAVE_DIR_0), key=lambda f: -os.path.getmtime(os.path.join(SAVE_DIR_0, f)))
    image_files_1 = sorted(os.listdir(SAVE_DIR_1), key=lambda f: -os.path.getmtime(os.path.join(SAVE_DIR_1, f)))
    if not image_files_0 or not image_files_1:
        return None, None
    return os.path.join(SAVE_DIR_0, image_files_0[0]), os.path.join(SAVE_DIR_1, image_files_1[0])

def delete_images(img0_path, img1_path):
    if os.path.exists(img0_path): os.remove(img0_path)
    if os.path.exists(img1_path): os.remove(img1_path)

def detect_objects(img):
    results = model(img, verbose=False)
    return [box for box in results[0].boxes if box.conf[0].item() >= 0.8]

def bbox_area(box):
    x1, y1, x2, y2 = map(int, box.xyxy[0].cpu().numpy())
    return (x2 - x1) * (y2 - y1)

def match_keypoints(img1, img2):
    kp1, des1 = orb.detectAndCompute(img1, None)
    kp2, des2 = orb.detectAndCompute(img2, None)
    if des1 is None or des2 is None: return []
    matches = bf_matcher.match(des1, des2)
    return matches

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
            boxes_right = detect_objects(img_right)

            matches = match_keypoints(img_left, img_right)
            match_ratio = len(matches) / max(1, len(boxes_left) + len(boxes_right))

            detections = []
            for box in boxes_left:
                x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()
                conf = box.conf[0].item()
                detections.append([x1, y1, x2, y2, conf])

            tracked_objects = tracker.update(np.array(detections))

            h_img, w_img = img_left.shape[:2]
            objects_data = {}

            for track in tracked_objects:
                x1, y1, x2, y2, track_id = track.astype(int)
                area = (x2 - x1) * (y2 - y1)
                x_center = (x1 + x2) / 2
                center_region = (w_img * 0.3 < x_center < w_img * 0.7)

                # YOLO의 클래스 이름 추정
                label = "Object"  # 여기서는 추적 대상이 어떤 클래스인지 YOLO로는 별도 식별 불가

                # 위험도 판단
                if area > 8000:
                    risk_level = "high"
                elif area > 3000:
                    risk_level = "medium"
                else:
                    risk_level = "low"

                if center_region:
                    if risk_level == "low":
                        risk_level = "medium"
                    elif risk_level == "medium":
                        risk_level = "high"

                approaching = False
                if track_id in previous_areas:
                    if area > previous_areas[track_id] * 1.2:
                        approaching = True
                previous_areas[track_id] = area

                objects_data[str(track_id)] = {
                    "id": int(track_id),
                    "bbox_area": area,
                    "center": x_center,
                    "match_score": round(match_ratio, 2),
                    "risk_level": risk_level
                }

                print(f"Track ID {track_id}: Area = {area}, Risk = {risk_level}, Approaching = {approaching}")

            if objects_data:
                publish_message(client, PUB_TOPIC, {
                    "timestamp": datetime.now().isoformat(),
                    "objects": list(objects_data.values())
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
