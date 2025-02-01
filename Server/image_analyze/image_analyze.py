import os
import paho.mqtt.client as mqtt
from time import sleep
from datetime import datetime

# MQTT 설정
BROKER_ADDRESS = "localhost"
PORT = 1883
PUB_TOPIC = "esp32/cam_processed"
SAVE_DIR = "./images"

# 디렉토리 생성
os.makedirs(SAVE_DIR, exist_ok=True)

# MQTT 클라이언트 설정
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
client.connect(BROKER_ADDRESS, PORT, 60)
client.loop_start()

model = YOLO("yolo11n.pt")

# 이미지 분석 함수
def process_image(image_path):
    image = cv2.imread(image_path)
    if image is None:
        print(f"이미지를 로드할 수 없습니다: {image_path}")
        return None

    results = model(image)
    detected_objects = []

    for result in results[0].boxes:
        x1, y1, x2, y2 = map(int, result.xyxy[0])
        conf = result.conf[0].item()
        cls = int(result.cls[0])
        label = model.names[cls]

        detected_objects.append({
            "object": label,
            "confidence": round(conf, 2)
        })

    return detected_objects