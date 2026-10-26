# SwitchPad SR

Android 平板上的 Switch 采集卡接收器 + 本地视频播放器，带 GPU 实时超分（GLES 3.0）。

- 采集卡：UVC（UVCAndroid / libuvc）
- 本地视频：Media3 ExoPlayer
- 算法：SGSR 风格 / FSR1 / Anime4K 风格 / CRT / 双三次
- 着色器位于 `app/src/main/assets/shaders`，用 `tests/test_shaders.sh` 校验。
