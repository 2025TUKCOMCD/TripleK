## 1. 주요 기능
### 1.1 MQTT를 이용한 데이터 송수신 기능
**AWS IoT Core**을 통해 MQTT 프로토콜을 사용하여 하드웨어와의 데이터 송수신 가능.<br>
EC2에서 **AWS IoT Core**를 통한 MQTT 프로토콜 사용을 위해 Bridge 작업이 필요.

### 1.2 주기적으로 저장된 이미지 삭제 기능
저장 공간 확보를 위해 우분투의 **crontab** 기능을 이용해 이미지를 삭제하는 쉘 스크립트 파일을 일정시간마다 실행.

### 1.3 YOLO 학습 모델을 통한 객체 식별 기능
학습된 YOLO 모델을 통해 수신된 이미지에서 객체를 식별하는 것이 가능.

### 1.4 식별된 객체에 대한 가까운 정도 및 위험도 판단 기능
YOLO 모델을 통해 식별된 객체에 가까운 정도를 판단하여 사용자에게 어느 정도의 위험으로 다가오는지 판단.

## 2. 개발 환경 설정 (테스트 환경)
### 2.1 AWS IAM 설정
**AWS** 로그인 후 **IAM** 서비스 페이지 방문.
IAM 리소스에서 **역할**을 클릭하고 우측 상단에 역할 생성을 클릭.
- 신뢰할 수 있는 엔티티 유형: AWS 서비스
- 서비스 또는 사용 사례: EC2, 사용 사례: EC2
- 권한 정책: AWSIoTConfigAccess
- 역할 이름: 임의의 이름(ex. AWS_IoT_Config_Access)

### 2.2 EC2 생성
**EC2** 서비스 페이지 방문.
리소스 왼쪽 상단에 **인스턴스**를 클릭하고 우측 상단에 인스턴스 시작을 클릭.
- 애플리케이션 및 OS 이미지: Ubuntu
- 인바운드 보안 그룹 규칙1: (유형: ssh / 소스 유형: 위치 무관)
- 인바운드 보안 그룹 규칙2: (유형: 사용자 지정 TCP / 포트 범위: 1883 / 소스 유형: 위치 무관)
- 고급 세부 정보 → IAM 인스턴스 프로파일: 2.1에서 설정한 IAM 역할의 이름

### 2.3 EC2 내부 설정
EC2에 putty 또는 ssh로 접속하여 명령어 입력.

<details>
    <summary>기본 패키지 설치</summary> 
  
    # 최신 버전의 Mosquitto가 포함된 저장소 목록 및 패키지 목록을 업데이트
    sudo apt-add-repository ppa:mosquitto-dev/mosquitto-ppa
    sudo apt-get update

    # Mosquitto Broker, Client 그리고 AWS CLI 설치
    sudo apt-get install mosquitto
    sudo apt-get install mosquitto-clients
    sudo apt install awscli
<details>
    <summary>AWS CLI 설치가 안될 경우</summary> 

    sudo apt-get install zip unzip
    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
    unzip awscliv2.zip
    sudo ./aws/install
</details>
</details>
