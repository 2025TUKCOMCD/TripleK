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

# 디렉토리 설정 (히트맵, 사용한 이미지 관련 디렉토리는 제거)
SAVE_DIR_0 = "./images_0"  # 오른쪽 시야
SAVE_DIR_1 = "./images_1"  # 왼쪽 시야

os.makedirs(SAVE_DIR_0, exist_ok=True)
os.makedirs(SAVE_DIR_1, exist_ok=True)

client = mqtt.Client()
client.connect(BROKER_ADDRESS, PORT, 60)
client.loop_start()
client.publish(STATUS_TOPIC, "connected")

# YOLO 모델 로드
model = YOLO("yolo11m.pt")

# StereoSGBM 객체 생성 (파라미터는 예시)
numDisparities = 16 * 5  # 80
blockSize = 5             
P1 = 8 * 3 * blockSize**2
P2 = 32 * 3 * blockSize**2
stereo = cv2.StereoSGBM_create(
    minDisparity=0,
    numDisparities=numDisparities,
    blockSize=blockSize,
    P1=P1,
    P2=P2,
    disp12MaxDiff=1,
    uniquenessRatio=10,
    speckleWindowSize=100,
    speckleRange=32
)

def compute_depth_map(img_left, img_right):
    gray_left = cv2.cvtColor(img_left, cv2.COLOR_BGR2GRAY)
    gray_right = cv2.cvtColor(img_right, cv2.COLOR_BGR2GRAY)
    
    disparity = stereo.compute(gray_left, gray_right).astype(np.float32) / 16.0
    # 0 이하 및 음수 값은 유효하지 않으므로 NaN으로 처리
    disparity[disparity <= 0.0] = np.nan
    depth_map = (B * f) / disparity
    return disparity, depth_map

try:
    while True:
        # 각 디렉토리에서 가장 최신 이미지 선택
        image_files_0 = sorted(os.listdir(SAVE_DIR_0), key=lambda f: -os.path.getmtime(os.path.join(SAVE_DIR_0, f)))
        image_files_1 = sorted(os.listdir(SAVE_DIR_1), key=lambda f: -os.path.getmtime(os.path.join(SAVE_DIR_1, f)))
        
        if not image_files_0 or not image_files_1:
            sleep(1)
            continue

        img0_path = os.path.join(SAVE_DIR_0, image_files_0[0])
        img1_path = os.path.join(SAVE_DIR_1, image_files_1[0])

        img_right = cv2.imread(img0_path)
        img_left = cv2.imread(img1_path)

        if img_right is None or img_left is None:
            sleep(1)
            continue

        # YOLO로 왼쪽 이미지에 대해 객체 검출 수행
        results = model(img_left, verbose=False)

        # 검출된 객체 중 80%(0.8) 이상의 신뢰도를 가진 객체가 없으면 이미지 삭제 후 다음 루프로
        if not any(box.conf[0].item() >= 0.8 for box in results[0].boxes):
            if os.path.exists(img0_path):
                os.remove(img0_path)
            if os.path.exists(img1_path):
                os.remove(img1_path)
            sleep(1)
            continue

        # 신뢰도 80% 이상의 객체가 있으므로, 한 번만 disparity와 depth map 계산
        disparity, depth_map = compute_depth_map(img_left, img_right)

        detected_objects = []
        # 객체별로 처리 (신뢰도 80% 이상인 경우만)
        for box in results[0].boxes:
            conf = box.conf[0].item()
            if conf < 0.8:
                continue

            coords = box.xyxy[0].cpu().numpy()
            x1, y1, x2, y2 = map(int, coords)
            h_img, w_img = img_left.shape[:2]
            x1 = max(0, min(x1, w_img - 1))
            y1 = max(0, min(y1, h_img - 1))
            x2 = max(0, min(x2, w_img - 1))
            y2 = max(0, min(y2, h_img - 1))
            
            if (x2 - x1) < 5 or (y2 - y1) < 5:
                continue

            region_disp = disparity[y1:y2, x1:x2]
            valid_disp = region_disp[np.isfinite(region_disp)]
            if valid_disp.size == 0:
                continue

            avg_disp = float(np.mean(valid_disp))
            distance = (B * f) / avg_disp

            cls = int(box.cls[0].item())
            label = model.names[cls] if hasattr(model, "names") else str(cls)

            # 거리 기반 위험도 산정 (단위: cm)
            risk_level = "low"
            if distance < 200:
                risk_level = "high"
            elif distance < 400:
                risk_level = "medium"

            detected_objects.append({
                "label": label,
                "distance": round(distance, 2),
                "risk_level": risk_level
            })

            print(f"Detected {label}: Distance = {round(distance, 2)} cm, Confidence = {conf}")

        # MQTT로 분석 결과 전송
        if detected_objects:
            mqtt_message = json.dumps({
                "timestamp": datetime.now().isoformat(),
                "objects": detected_objects
            })
            client.publish(PUB_TOPIC, mqtt_message)

        # 처리한 이미지는 삭제 (히트맵 저장, 사용한 이미지 저장 기능 제거)
        if os.path.exists(img0_path):
            os.remove(img0_path)
        if os.path.exists(img1_path):
            os.remove(img1_path)
            
        sleep(1)

except KeyboardInterrupt:
    print("프로그램 종료")
finally:
    client.loop_stop()
    client.disconnect()