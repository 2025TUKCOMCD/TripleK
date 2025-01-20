
## 1. 주요 기능
### 1.1 WiFiManager를 이용한 WiFi 설정
ESP32-CAM은 처음 실행될 때 **WiFiManager**를 이용해서 AP(Access Point) 모드로 작동.  
사용자는 ESP32-CAM에 WiFi SSID랑 비밀번호를 입력.   
(재부팅 후에도 입력한 정보를 기억하여 자동으로 WiFi 네트워크에 연결)

### 1.2 Slave ESP32로 데이터 전송
입력된 WiFi 정보는 Slave ESP32-CAM로 보내지고, 그걸로 Slave ESP32-CAM 또한 같은 네트워크에 연결.

### 1.3 ESP32-CAM의 캡처 기능
WiFi 연결이 끝나면 ESP32-CAM이 카메라를 통해 캡처된 이미지를 서버에 전송.

## 2. 초기 설정
### USB 드라이버 설치
[다운로드 링크](https://sparks.gogo.co.nz/ch340.html)에 접속하여 <b>Windows CH340 Driver</b>를 다운로드하여 압축을 해제<br>
<b>CH34x_Install_Windows_v3_4</b>를 실행한 후 INSTALL 버튼을 눌러 설치<br>
![image](https://github.com/user-attachments/assets/dc43f988-aac0-4dbd-84a0-46ba9b628364)
### 아두이노 IDE 설치 및 기본 설정
### 라이브러리 설치
아두이노 IDE의 라이브러리 매니저를 통해 라이브러리 설치<br>
- [MQTT](https://github.com/256dpi/arduino-mqtt) by Joel Gaehwiler
- [PubSubClient](https://pubsubclient.knolleary.net/) by Nick O'Leary
- [WiFiManager](https://github.com/tzapu/WiFiManager) by tzabu
