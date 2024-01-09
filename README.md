# Android application for audio recorder & player

- Player 
  - 
    .wav 형식 파일만 재생 가능 

    wav파일의 최대,최소 조절 가능 : 음원 볼륨을 통해 절대값 최댓값 지정 가능
    
    stereo 혹은 mono 재생 지원 (자동지원 x, 사용자가 직접 입력해야 함)
- Recorder
  -   
    Record file name 에 입력한 파일 이름 + .wav로 저장됨.
    
    저장 경로 : /sdcard/Android/data/com.example.playrecord/files/Music/ (내부 저장소)

    bottom mic : 음성 통화에 사용하는 마이크, 일반적인 마이크

    back : 스테레오 녹음에 사용되는 마이크 카메라 옆 혹은 화면 상단에 부착되어 있음.

    player 재생과 동시에 녹음이 가능함.