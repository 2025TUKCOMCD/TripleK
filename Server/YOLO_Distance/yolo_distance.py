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

# YOLO 모델 로드 (GPU 사용)
model = YOLO("best.pt")
model.to('cuda')
model.model.half()
model.fuse()

# SORT 트래커 초기화
tracker = Sort(min_hits=1, max_age=5)

# 클래스별 area 기반 근접 판단 임계값
proximity_thresholds = {
    "person": [2000, 4000, 6000],
    "car": [5000, 10000, 15000],
    "bus": [8000, 14000, 22000],
    "truck": [8000, 14000, 22000],
    "bike": [1500, 3000, 4500],
    "bollard": [1000, 2000, 2500],
    "fireplug": [1000, 2000, 3000],
    "kickboard": [1500, 3000, 4500],
    "motorcycle": [1500, 3000, 4500],
    "trafficcone": [1000, 2000, 3000],
    "trafficlight": [1000, 2000, 3000],
    "tubular marker": [1000, 2000, 3000],
    "pillar": [3000, 6000, 9000],
    "default": [2000, 3000, 4000]
}

def connect_mqtt():
    client = mqtt.Client()
    client.connect(BROKER_ADDRESS, PORT, 60)
    client.loop_start()
    client.publish(STATUS_TOPIC, "connected")
    return client

def publish_message(client, topic, message):
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

def bbox_area(box):
    x1, y1, x2, y2 = box.xyxy[0]
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
    previous_areas = {}  # track_id 기준으로 관리
    # SIFT 대신 ORB 사용
    orb = cv2.ORB_create(nfeatures=700)  # 특징점 최대 1000개

    # 특징점 매칭 함수 수정
    def is_similar(box_l, box_r):
        x1_l, y1_l, x2_l, y2_l = map(int, box_l)
        x1_r, y1_r, x2_r, y2_r = map(int, box_r)

        roi_left = img_left[y1_l:y2_l, x1_l:x2_l]
        roi_right = img_right[y1_r:y2_r, x1_r:x2_r]

        if roi_left.size == 0 or roi_right.size == 0:
            return False

        kp1, des1 = orb.detectAndCompute(roi_left, None)
        kp2, des2 = orb.detectAndCompute(roi_right, None)

        if des1 is None or des2 is None:
            return False

        # BFMatcher(Hamming) 사용
        bf = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=True)
        matches = bf.match(des1, des2)

        # 매칭 거리 기준 필터링
        good_matches = [m for m in matches if m.distance < 50]  # 임계값 조절 가능

        return len(good_matches) >= 10  # 매칭 임계값

    try:
        while True:
            img0_path, img1_path = get_latest_images()
            if img0_path is None or img1_path is None:
                sleep(0.01)
                continue

            img_right = cv2.imread(img0_path)
            img_left = cv2.imread(img1_path)
            if img_right is None or img_left is None:
                sleep(0.01)
                continue

            # 🔹 YOLO 예측을 두 이미지를 batch로 한 번에
            results = model.predict(
                [img_left, img_right],
                conf=0.6,           # 원하는 confidence
                verbose=True        # 로그 출력 줄이기
            )

            # 결과 분리
            boxes_left = results[0].boxes
            boxes_right = results[1].boxes

            h_img, w_img = img_left.shape[:2]

            # 왼쪽 객체들 detection (x1,y1,x2,y2,conf)
            dets_left = []
            for box in boxes_left:
                coords = box.xyxy[0].cpu().numpy()
                dets_left.append([*coords, box.conf[0].item()])
            dets_left = np.array(dets_left)

            # SORT 트래커로 ID 추적 (왼쪽 카메라 기준)
            tracked_left = tracker.update(dets_left)  # [x1,y1,x2,y2,id]

            # ID -> 왼쪽 박스 매핑
            id_to_box = {}
            for i, track in enumerate(tracked_left):
                x1, y1, x2, y2, track_id = track
                id_to_box[int(track_id)] = boxes_left[i]

            # 오른쪽과 매칭
            matched_ids = set()
            for track_id, box_l in id_to_box.items():
                label_l = model.names[int(box_l.cls[0].item())]
                box_l_coords = box_l.xyxy[0].cpu().numpy()

                for box_r in boxes_right:
                    label_r = model.names[int(box_r.cls[0].item())]
                    box_r_coords = box_r.xyxy[0].cpu().numpy()

                    if label_l == label_r and is_similar(box_l_coords, box_r_coords):
                        matched_ids.add(int(track_id))  # 매칭된 ID 저장
                        break

            objects_data = []
            for track_id, box_l in id_to_box.items():
                coords_l = box_l.xyxy[0].cpu().numpy()
                x1_l, y1_l, x2_l, y2_l = map(int, coords_l)
                area = bbox_area(box_l)
                label = model.names[int(box_l.cls[0].item())]

                # 중앙 영역 판단
                center_region_left = x2_l > w_img * 0.6
                # 오른쪽 매칭 박스가 있으면 어드밴티지
                if track_id in matched_ids:
                    center_region_right = True  # 매칭되면 오른쪽 중앙 영역을 확보한 것으로 간주
                else:
                    center_region_right = False

                both_center = center_region_left and center_region_right
                proximity = get_proximity(label, area)

                # 위험도 판단 (중앙에 있으면 위험도 강화)
                if proximity in ["very_close"] or (proximity in ["close"] and both_center):
                    risk_level = "high"
                elif proximity in ["close"] or (proximity in ["medium"] and both_center):
                    risk_level = "medium"
                else:
                    risk_level = "low"

                # 접근 여부 판단
                approaching = False
                if track_id in previous_areas:
                    if area > previous_areas[track_id] * 1.2:
                        approaching = True
                previous_areas[track_id] = area

                if approaching:
                    risk_levels = ["low", "medium", "high"]
                    current_index = risk_levels.index(risk_level)
                    if current_index < len(risk_levels) - 1:
                        risk_level = risk_levels[current_index + 1]

                objects_data.append({
                    "id": int(track_id),
                    "label": label,
                    "approaching": approaching,
                    "proximity": proximity,
                    "risk_level": risk_level
                })

                print(f"Detected {label} (ID {track_id}): Area={area}, Proximity={proximity}, Risk={risk_level}, Approaching={approaching}")

            # 고위험 객체가 하나라도 있으면 MQTT 전송
            should_publish = any(obj["risk_level"] == "high" for obj in objects_data)

            if should_publish:
                # 고위험 객체만 필터링
                filtered_objects = [obj for obj in objects_data if obj["risk_level"] == "high"]
                
                publish_message(client, PUB_TOPIC, {
                    "timestamp": datetime.now().isoformat(),
                    "objects": filtered_objects  # 필터된 객체만 전송
                })
                client.publish(STATUS_TOPIC, "connected")
                print("MQTT 메시지 전송!\n")

            delete_images(img0_path, img1_path)
            sleep(0.01)

    except KeyboardInterrupt:
        print("프로그램 종료")
    finally:
        client.loop_stop()
        client.disconnect()

if __name__ == "__main__":
    mqtt_client = connect_mqtt()
    process_images(mqtt_client)
