# 블루투스 SPP 테스트 앱

안드로이드 블루투스 SPP(Serial Port Profile) 통신을 통한 LED 및 모터 제어 테스트 앱입니다.

## 기능

- **LED 제어**: 3개의 LED ON/OFF 제어
- **모터 제어**: 모터 ON/OFF 제어  
- **상태 조회**: LED 상태, 모터 상태, 전체 상태 조회
- **실시간 로그**: 통신 로그 실시간 표시
- **블루투스 연결 관리**: 디바이스 연결/해제/스캔

## 테스트 명령어

### LED 제어
- `R1` / `R0` - LED 1 (Red) 제어
- `Y1` / `Y0` - LED 2 (Yellow) 제어  
- `G1` / `G0` - LED 3 (Green) 제어

### 모터 제어
- `M1` / `M0` - 모터 제어

### UI 기능
- **토글 스위치**: 각 LED와 모터를 ON/OFF 토글
- **실시간 아이콘**: LED 상태에 따라 색상 변경 (빨강/노랑/초록/회색)
- **모터 아이콘**: 모터 상태에 따라 색상 변경 (갈색/회색)
- **자동 초기화**: 연결 시 모든 디바이스를 OFF 상태로 초기화

## 사용법

1. 앱 실행 후 블루투스 권한 허용
2. 블루투스 디바이스와 페어링
3. 연결 버튼으로 디바이스 연결
4. 각 제어 버튼으로 테스트 명령 전송
5. 하단 로그에서 통신 상태 확인

## 기술 스택

- **언어**: Kotlin
- **최소 SDK**: API 26 (Android 8.0)
- **타겟 SDK**: API 35 (Android 15)
- **통신**: Bluetooth SPP (Serial Port Profile)
- **UI**: Material Design Components

## 권한

- `BLUETOOTH` - 블루투스 사용
- `BLUETOOTH_ADMIN` - 블루투스 관리
- `BLUETOOTH_CONNECT` - 블루투스 연결
- `BLUETOOTH_SCAN` - 블루투스 스캔
- `ACCESS_FINE_LOCATION` - 위치 정보 (블루투스 스캔용)
- `ACCESS_COARSE_LOCATION` - 대략적 위치 정보