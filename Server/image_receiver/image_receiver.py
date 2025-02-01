import os
import paho.mqtt.client as mqtt

# 기본 설정
BROKER_ADDRESS = "localhost"
PORT = 1883
TOPIC = "esp32/cam_0"
SAVE_DIR = "./images"

# 디렉토리 생성
os.makedirs(SAVE_DIR, exist_ok=True)