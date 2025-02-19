import os
import cv2
import json
import numpy as np
from time import sleep
from datetime import datetime
from ultralytics import YOLO
import paho.mqtt.client as mqtt

# 카메라 간 거리 (Baseline, B) - 두 카메라 간 거리 (단위: cm)
B = 10  # cm

# 카메라 초점 거리 (OV2640 기준, 실험을 통해 조정 가능)
f = 500  # 임의 값, 실제 측정 필요

# MQTT 설정
BROKER_ADDRESS = "localhost"
PORT = 1883
PUB_TOPIC = "esp32cam/processed"
STATUS_TOPIC = "esp32cam/status"
SAVE_DIR_0 = "./images_0"  # 오른쪽 시야
SAVE_DIR_1 = "./images_1"  # 왼쪽 시야

os.makedirs(SAVE_DIR_0, exist_ok=True)
os.makedirs(SAVE_DIR_1, exist_ok=True)

print("MQTT 연결 시작...")

# MQTT 클라이언트 설정
client = mqtt.Client()
client.connect(BROKER_ADDRESS, PORT, 60)
client.loop_start()
print("MQTT 연결 완료")

client.publish(STATUS_TOPIC, "connected")
print("MQTT 상태 메시지 전송 완료: connected")

# YOLO 모델 로드
model = YOLO("yolo11m.pt")

def process_image(image_path):
    """
    YOLO 모델을 이용해 이미지에서 객체 탐지
    x, y 좌표 중심을 포함한 객체 리스트 반환
    """
    image = cv2.imread(image_path)
    if image is None:
        print(f"이미지를 로드할 수 없습니다: {image_path}")
        return None
    
    results = model(image, verbose=False)
    detected_objects = []

    print(f"\n [{image_path}] YOLO 탐지 결과:")
    
    for result in results[0].boxes:
        x1, y1, x2, y2 = map(int, result.xyxy[0])
        conf = result.conf[0].item()
        cls = int(result.cls[0])
        label = model.names[cls]
        
        center_x = (x1 + x2) // 2  # 중심 x 좌표
        center_y = (y1 + y2) // 2  # 중심 y 좌표

        print(f" 감지된 객체: {label}, 신뢰도: {conf:.2f}, 위치: ({center_x}, {center_y})")

        detected_objects.append({
            "object": label,
            "confidence": round(conf, 2),
            "x": center_x,
            "y": center_y
        })

    if not detected_objects:
        print(" 감지된 객체 없음\n")
    
    return detected_objects

def match_objects(obj1_list, obj2_list):
    """
    두 개의 이미지에서 감지된 객체 리스트를 비교하여 가장 가까운 매칭을 수행
    x 좌표 차이(disparity)와 y 좌표 차이(y_diff)를 동시에 고려
    """
    matched_pairs = []
    used_idx = set()  # 이미 매칭된 인덱스를 저장

    for obj1 in obj1_list:
        best_match = None
        min_score = float("inf")

        for i, obj2 in enumerate(obj2_list):
            if i in used_idx:  # 이미 매칭된 객체는 제외
                continue
            if obj1["object"] != obj2["object"]:  # 같은 종류의 객체만 매칭
                continue

            disparity = abs(obj1["x"] - obj2["x"])  # x 좌표 차이
            y_diff = abs(obj1["y"] - obj2["y"])  # y 좌표 차이

            # 최적의 매칭을 찾기 위해 가중합 계산 (x 차이를 우선 고려, y 차이는 보정 역할)
            score = disparity + (y_diff * 0.5)  # y 차이를 50% 가중치로 반영

            if score < min_score:
                min_score = score
                best_match = (obj1, obj2, disparity)
                best_idx = i

        if best_match:
            matched_pairs.append(best_match)
            used_idx.add(best_idx)  # 해당 인덱스를 사용했다고 표시

    return matched_pairs

def calculate_distance(disparity):
    """
    거리 계산 공식 적용 (삼각측량)
    D = (B * f) / disparity
    """
    if disparity == 0:
        return None  # 분모 0 방지
    return round((B * f) / (disparity * 100), 2)

try:
    while True:
        # 오른쪽 시야 이미지
        image_files_0 = sorted(os.listdir(SAVE_DIR_0), key=lambda f: os.path.getmtime(os.path.join(SAVE_DIR_0, f)), reverse=True)
        # 왼쪽 시야 이미지
        image_files_1 = sorted(os.listdir(SAVE_DIR_1), key=lambda f: os.path.getmtime(os.path.join(SAVE_DIR_1, f)), reverse=True)

        if len(image_files_0) > 0 and len(image_files_1) > 0:
            img0_path = os.path.join(SAVE_DIR_0, image_files_0[0])
            img1_path = os.path.join(SAVE_DIR_1, image_files_1[0])

            obj0 = process_image(img0_path)
            obj1 = process_image(img1_path)

            if obj0 and obj1:
                matched_objects = match_objects(obj0, obj1)
                distances = []

                for obj0, obj1, disparity in matched_objects:
                    distance = calculate_distance(disparity)
                    if distance:
                        distances.append({
                            "object": obj0["object"],
                            "distance_m": distance
                        })

                if distances:
                    mqtt_message = json.dumps({
                        "timestamp": datetime.now().isoformat(),
                        "distances": distances
                    })

                    # MQTT로 전송
                    client.publish(PUB_TOPIC, mqtt_message)

                    # 터미널 출력 포맷 변경
                    print("MQTT 전송 완료:")
                    for item in distances:
                        print(f"  종류: {item['object']}, 거리: {item['distance_m']}m")

            os.remove(img0_path)
            os.remove(img1_path)
        sleep(1)
except KeyboardInterrupt:
    print("프로그램 종료")
finally:
    client.loop_stop()
    client.disconnect()
