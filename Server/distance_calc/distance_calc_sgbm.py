import os
import cv2
import json
import numpy as np
from time import sleep
from datetime import datetime
import paho.mqtt.client as mqtt
from ultralytics import YOLO

# 카메라 보정 파라미터 (정확한 측정을 위해 실제 값으로 보정 필요)
B = 6    # cm, 카메라 간 거리 (Baseline)
f = 500  # 픽셀 단위 초점 거리

# MQTT 설정
BROKER_ADDRESS = "localhost"
PORT = 1883
PUB_TOPIC = "esp32cam/processed"
STATUS_TOPIC = "esp32cam/status"

# 이미지 저장 디렉토리
SAVE_DIR_0 = "./images_0"
SAVE_DIR_1 = "./images_1"
os.makedirs(SAVE_DIR_0, exist_ok=True)
os.makedirs(SAVE_DIR_1, exist_ok=True)

# YOLO 모델 로드
model = YOLO("yolo11m.pt")

# StereoSGBM 설정 (필요시 추가 튜닝 가능)
stereo = cv2.StereoSGBM_create(
    minDisparity=0,
    numDisparities=16 * 8,
    blockSize=7,
    P1=8 * 3 * 7**2,
    P2=32 * 3 * 7**2,
    disp12MaxDiff=1,
    uniquenessRatio=15,
    speckleWindowSize=50,
    speckleRange=16
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
    try:
        image_files_0 = sorted(
            [f for f in os.listdir(SAVE_DIR_0) if os.path.isfile(os.path.join(SAVE_DIR_0, f))],
            key=lambda f: -os.path.getmtime(os.path.join(SAVE_DIR_0, f))
        )
        image_files_1 = sorted(
            [f for f in os.listdir(SAVE_DIR_1) if os.path.isfile(os.path.join(SAVE_DIR_1, f))],
            key=lambda f: -os.path.getmtime(os.path.join(SAVE_DIR_1, f))
        )
        if not image_files_0 or not image_files_1:
            return None, None
        img0_path = os.path.join(SAVE_DIR_0, image_files_0[0])
        img1_path = os.path.join(SAVE_DIR_1, image_files_1[0])
        if not os.path.exists(img0_path) or not os.path.exists(img1_path):
            return None, None
        return img0_path, img1_path
    except Exception as e:
        print(f"[ERROR] get_latest_images() 오류: {e}")
        return None, None

def delete_images(img0_path, img1_path):
    """처리 완료된 이미지 삭제"""
    if os.path.exists(img0_path):
        os.remove(img0_path)
    if os.path.exists(img1_path):
        os.remove(img1_path)

def detect_objects(img):
    """YOLO를 이용한 객체 탐지 (모든 객체 포함)"""
    results = model(img, verbose=False)
    return results[0].boxes  # 모든 객체 반환

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
    # 노이즈 제거를 위한 median filtering 적용
    disparity = cv2.medianBlur(disparity, 5)
    disparity[disparity <= 0.0] = np.nan  # 유효하지 않은 값은 nan으로 처리
    depth_map = (B * f) / disparity
    return disparity, depth_map

def process_images(client):
    """이미지를 처리하고 MQTT로 결과 전송"""
    try:
        while True:
            img0_path, img1_path = get_latest_images()
            if img0_path is None or img1_path is None:
                continue

            img_right = cv2.imread(img0_path)
            img_left = cv2.imread(img1_path)

            if img_right is None or img_left is None:
                continue

            detected_objects = detect_objects(img_left)
            if not detected_objects:
                delete_images(img0_path, img1_path)
                continue

            # 깊이 맵 계산
            disparity, depth_map = compute_depth_map(img_left, img_right)
            objects_data = []
            h_img, w_img = img_left.shape[:2]

            for box in detected_objects:
                coords = box.xyxy[0].cpu().numpy()
                x1, y1, x2, y2 = map(int, coords)
                # 이미지 경계를 넘어가지 않도록 좌표 클램핑
                x1 = max(0, min(x1, w_img - 1))
                x2 = max(0, min(x2, w_img - 1))
                y1 = max(0, min(y1, h_img - 1))
                y2 = max(0, min(y2, h_img - 1))

                if (x2 - x1) < 5 or (y2 - y1) < 5:
                    continue

                # 바운딩 박스 중앙의 일정 비율 영역을 ROI로 사용하여 노이즈 감소
                margin_x = int(0.25 * (x2 - x1))
                margin_y = int(0.25 * (y2 - y1))
                x1_c = x1 + margin_x
                x2_c = x2 - margin_x
                y1_c = y1 + margin_y
                y2_c = y2 - margin_y

                region_disp = disparity[y1_c:y2_c, x1_c:x2_c]
                valid_disp = region_disp[np.isfinite(region_disp)]
                if valid_disp.size == 0:
                    continue

                # 중앙값을 사용하여 outlier 영향을 줄임
                avg_disp = float(np.median(valid_disp))
                if avg_disp == 0:
                    continue

                # 거리 계산: 깊이 = (Baseline * 초점거리) / disparity
                distance = (B * f) / avg_disp
                label = model.names[int(box.cls[0].item())] if hasattr(model, "names") else str(int(box.cls[0].item()))
                risk_level = assess_risk(distance)

                objects_data.append({
                    "label": label,
                    "distance": round(distance, 2),
                    "risk_level": risk_level
                })

                print(f"Detected {label}: Distance = {round(distance, 2)} cm")

            if objects_data:
                publish_message(client, PUB_TOPIC, {
                    "timestamp": datetime.now().isoformat(),
                    "objects": objects_data
                })

            delete_images(img0_path, img1_path)

    except KeyboardInterrupt:
        print("프로그램 종료")
    finally:
        client.loop_stop()
        client.disconnect()

if __name__ == "__main__":
    mqtt_client = connect_mqtt()
    process_images(mqtt_client)
