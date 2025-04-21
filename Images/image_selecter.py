import os
import shutil
import random

def select_and_copy_images(src_folder, dst_folder, min_images=1000, max_images=3000):
    # 원본 폴더에서 모든 이미지 파일 가져오기
    image_files = [f for f in os.listdir(src_folder) if f.lower().endswith(('png', 'jpg', 'jpeg'))]
    
    # 선택할 이미지 개수 랜덤 설정
    num_images = random.randint(min_images, max_images)
    
    # 이미지 랜덤 선택
    selected_images = random.sample(image_files, min(num_images, len(image_files)))
    
    # 대상 폴더가 없으면 생성
    os.makedirs(dst_folder, exist_ok=True)
    
    # 선택한 이미지를 대상 폴더로 복사
    for img in selected_images:
        shutil.copy(os.path.join(src_folder, img), os.path.join(dst_folder, img))
    
    print(f"총 {len(selected_images)}장의 이미지가 {dst_folder}에 저장되었습니다.")

# 사용 예시
src_folder = "person_image"  # 원본 폴더 경로
dst_folder = "study_data"  # 저장할 폴더 경로
select_and_copy_images(src_folder, dst_folder)