
## 1. 주요 기능
### 1.1 WiFiManager를 이용한 WiFi 설정
ESP32-CAM은 처음 실행될 때 **WiFiManager**를 이용해서 AP(Access Point) 모드로 작동.  
사용자는 ESP32-CAM에 WiFi SSID랑 비밀번호를 입력.   
(재부팅 후에도 입력한 정보를 기억하여 자동으로 WiFi 네트워크에 연결)

### 1.2 Slave ESP32로 데이터 전송
입력된 WiFi 정보는 Slave ESP32-CAM로 보내지고, 그걸로 Slave ESP32-CAM 또한 같은 네트워크에 연결.

### 1.3 ESP32-CAM의 캡처 기능
WiFi 연결이 끝나면 ESP32-CAM이 카메라를 통해 캡처된 이미지를 서버에 전송.

## 2. 개발 환경 설정 (Windows 기준)
### 2.1 USB 드라이버 설치
[다운로드 링크](https://sparks.gogo.co.nz/ch340.html)에 접속하여 <b>Windows CH340 Driver</b>를 다운로드하여 압축을 해제.<br>
<b>CH34x_Install_Windows_v3_4</b>를 실행한 후 INSTALL 버튼을 눌러 설치.<br>
![image](https://github.com/user-attachments/assets/dc43f988-aac0-4dbd-84a0-46ba9b628364)

### 2.2 아두이노 IDE 설치 및 기본 설정
아두이노 공식 홈페이지 [소프트웨어 탭](https://www.arduino.cc/en/software)에서 환경에 맞는 IDE 다운로드 후 설치하고 아래와 같이 설정.<br>
- [File] → [Preference] → [Language] → [한국어]
- [파일] → [기본 설정] → [추가 보드 관리자 URL]에 <b>https://dl.espressif.com/dl/package_esp32_index.json</b> 복사 후 붙여넣기.
- [도구] → [보드] → [보드매니저] → 입력창에 <b>esp32</b>를 검색하여 <b>esp32 by Espressif 버전 2.0.17</b> 설치.<br>
![image](https://github.com/user-attachments/assets/9c2d11a0-a4b4-4b66-a15f-ad4b1be6ae94)<br>
- [도구] → [보드] → [esp32] → 커서를 내려 <b>AI Thinker ESP32-CAM</b> 선택.
- [도구] → [포트] → esp32 보드를 연결한 포트(<i>ex. COMX</i>)를 선택.
  
### 2.3 라이브러리 설치
아두이노 IDE의 라이브러리 매니저를 통해 라이브러리 설치.<br>
- [MQTT](https://github.com/256dpi/arduino-mqtt) by Joel Gaehwiler
- [PubSubClient](https://pubsubclient.knolleary.net/) by Nick O'Leary
- [WiFiManager](https://github.com/tzapu/WiFiManager) by tzabu
