import os
import cv2
import shutil
from ultralytics import YOLO

# YOLO11 모델 로드
model = YOLO('yolo11m.pt')

# 사람 감지 및 신뢰도 체크 함수
def detect_person(image_path, confidence_threshold=0.8):
    results = model(image_path)  # 이미지 분석
    for result in results:
        for box in result.boxes:
            if result.names[int(box.cls)] == 'person' and box.conf >= confidence_threshold:
                return True  # 신뢰도 80% 이상인 사람이 감지됨
    return False  # 80% 미만이거나 사람이 감지되지 않음

# 폴더 내 이미지 처리
def process_images(folder_path):
    image_files = [f for f in os.listdir(folder_path) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
    rmv = 0
    
    for idx, filename in enumerate(image_files, start=1):
        image_path = os.path.join(folder_path, filename)

        print(f"📂 ({idx}/{len(image_files)}) 현재 확인 중: {filename} / 삭제한 파일 수: ", rmv)

        # 신뢰도 80% 이상이 아니면 삭제
        if not detect_person(image_path):
            os.remove(image_path)
            print(f"❌ 삭제됨: {filename}\n")
            rmv += 1
        else:
            print(f"✅ 유지됨: {filename}\n")

image_folder = "이미지 폴더"  # 이미지가 있는 폴더 경로 입력
process_images(image_folder)