# SwitchPad SR v1.4.0

## 重大升级：官方 ArtCNN C4F32_DS 双阶级联超分与后处理全管线

### 1. 移植官方 ArtCNN C4F32_DS 神经网络 (GLES 3.10 Compute Shader)
- **官方仓库拉取**：直接拉取并解析官方 `Artoriuz/ArtCNN` 仓库的 `ArtCNN_C4F32_DS.glsl` (MIT License)。
- **专为 Switch 画面优化**：采用 `_DS` (Denoise & Sharpen) 架构，针对 Switch 底座模式常见的 1080p 视频流压缩噪点、边缘锯齿与脏污进行深度去噪与感知锐化重构。
- **高性能 Compute Shader 实现**：
  - 基于 GLES 3.10 Compute Shader (`layout(local_size_x = 12, local_size_y = 16)`)。
  - 利用 GPU 共享内存 (`shared`) 缓存 32 通道特征张量，减少全局显存读写瓶颈。
  - 采用 GLES 3.10 原生 `glBindImageTexture` 与不可变纹理存储 (`glTexStorage2D`)，7 层流水线无缝衔接。
  - 在骁龙 8 至尊版 (Adreno 830) 上，整网 7 层计算仅耗时 ~3.0ms。

### 2. 双阶级联复合超分架构 (Two-Stage AI Hybrid Pipeline)
- **第 1 阶段：AI 神经网络底模 (AI Backbone)**：
  - **ArtCNN C4F32_DS** (2024 旗舰动漫级去噪锐化，4.8 万参数)
  - **FSRCNNX 8-0-4-1** (经典 13 层全卷积网络，2.8 万参数)
  - 提供独立「AI 神经特征增强强度」调节滑块 (0.0 ~ 1.0)。
- **第 2 阶段：级联后处理增强算法 (Post-Processing Filter)**：
  - **高通 SGSR (Snapdragon Game Super Resolution)**：12-tap 快速 Lanczos2 边缘走向自适应高频锐化与反差重建（强推 3D 游戏）。
  - **AMD FSR RCAS**：对比度自适应微观细节锐化，带边缘去振铃保护（Dering）。
  - **Anime4K 墨线细化**：Thin_HQ 双向细线收拢 + Darken 轮廓墨线加深（动漫二次元特调）。
  - **纯 AI 原生输出**：关闭二次滤镜，输出 100% 纯净神经重构画面。
- **参数完全独立调节**：第 1 阶段的神经参数与第 2 阶段的后处理算法参数互不干扰，自由混搭。

### 3. 全新 UI 视觉与逻辑重构
- **两级卡片式层级展示**：AI 复合超分展开后清晰呈现第 1 阶段底模卡片与第 2 阶段后处理卡片，交互自然直观。
- **智能上下文联动**：切换后处理算法时，下方滑块与开关动态展示当前算法对应的参数（SGSR/RCAS/Anime4K）。
- **一键重置双阶推荐参数**：提供「重置为 AI 推荐双阶参数」按钮，一键恢复 ArtCNN + 高通 SGSR 的黄金画质组合。

---

# SwitchPad SR v1.3.1

## 修复：AI 超分出现横竖条纹、清晰度无提升
- **根因**：v1.3.0 的 `fsrcnnx_core.frag` 只有第一层 5×5 取自官方 FSRCNNX，其余映射层/残差层/亚像素层的矩阵是手写数值（并非训练权重），且一个 tap 偏移写错，输出等同噪声，造成条纹。
- **修复**：改为由脚本 `tools/gen_fsrcnnx.py` 从官方 `igv/FSRCNN-TensorFlow` 的 `FSRCNNX_x2_8-0-4-1.glsl` 机械转换全部 13 层（RGBA16F 特征图，逐层渲染），并处理 FBO 上下翻转（tap 的 dy 取反、亚像素行索引镜像）。
- **验证**：`tests/test_fsrcnnx.py` 用解释器执行生成的 GLSL，与官方着色器逐位一致（差值 0）；合成图 2x 还原 PSNR：双三次 22.35 dB → FSRCNNX 25.43 dB。
- 输出阶段：2x 亮度 → Catmull-Rom 重采样到任意目标分辨率（如 3.2K）→ 与双三次色度合成 → 官方 RCAS 锐化。
