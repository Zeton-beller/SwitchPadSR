## SwitchPad SR v1.1.0

这是一次底层重构，不是小修小补。v1.0.x 里本地视频无法播放、采集卡无法出画、超分没有真正生效，原因见下。

### 为什么旧版不能用
1. **本地视频无法播放**：Vulkan 交换链和 MediaPlayer 争用同一个 Surface，Android 一个 Surface 只允许一个生产者，视频根本连不上。
2. **超分从未生效**：Vulkan 管线（Pipeline）从来没有创建，着色器也没有加载，画面只会清屏。
3. **采集卡无法出画**：旧代码用 `USBDEVFS_BULK` 直接读端点 `0x81`，这不是 UVC 协议（没有 Probe/Commit 协商，也没有等时传输）。
4. 音频环形缓冲从未被写入；HUD 的 FPS、GPU 耗时是写死的常量。

### 新架构
- 渲染：GLES 3.0。输入 → SurfaceTexture → 超分 Pass（目标分辨率 FBO）→ 屏幕合成。
- 本地视频：Media3 ExoPlayer（MP4/MKV/WebM 等，倍速、拖动进度、断点续播、最近播放）。
- 采集卡：基于 libuvc 的 UVCAndroid（com.herohan:UVCAndroid，saki4510t/UVCCamera 的维护分支），原生 MJPEG/YUYV 解码；自动选择 1080p 最高帧率；USB 音频直通。
- 算法：SGSR 风格边缘定向、FSR1（EASU+RCAS）、Anime4K 风格、复古 CRT、双三次、原画直通；每个算法有独立可调参数。
- 全新界面：首页双入口、播放页顶部一键切换「采集卡 / 本地视频」、右侧画质面板（算法 / 画面 / 调参）、A/B 分屏对比。

### 说明
- Anime4K、SGSR、FSR 均为按公开算法思路自行实现的 GLSL，并非官方源码逐行移植。
