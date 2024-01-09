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

    player 재생과 동시에 녹음이 가능함.


----
## Auto / Manual 차이
- Profiling device에서 실험 자동화를 위해 만든 모드로
- 라즈베리파이 소켓통신을 수행하며 자동으로 재생 및 녹음을 하도록 하는 Auto mode
- Manual은 기존과 같음.