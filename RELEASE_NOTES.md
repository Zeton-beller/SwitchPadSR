# SwitchPad SR v1.3.1

## 修复：AI 超分出现横竖条纹、清晰度无提升
- **根因**：v1.3.0 的 `fsrcnnx_core.frag` 只有第一层 5×5 取自官方 FSRCNNX，其余映射层/残差层/亚像素层的矩阵是手写数值（并非训练权重），且一个 tap 偏移写错，输出等同噪声，造成条纹。
- **修复**：改为由脚本 `tools/gen_fsrcnnx.py` 从官方 `igv/FSRCNN-TensorFlow` 的 `FSRCNNX_x2_8-0-4-1.glsl` 机械转换全部 13 层（RGBA16F 特征图，逐层渲染），并处理 FBO 上下翻转（tap 的 dy 取反、亚像素行索引镜像）。
- **验证**：`tests/test_fsrcnnx.py` 用解释器执行生成的 GLSL，与官方着色器逐位一致（差值 0）；合成图 2x 还原 PSNR：双三次 22.35 dB → FSRCNNX 25.43 dB。
- 输出阶段：2x 亮度 → Catmull-Rom 重采样到任意目标分辨率（如 3.2K）→ 与双三次色度合成 → 官方 RCAS 锐化。

## 注意
- 官方 FSRCNNX 许可证 LGPL-3.0（`tools/FSRCNNX_x2_8-0-4-1.glsl`）。
- 需要 GPU 支持 RGBA16F 渲染（骁龙 8 至尊版 / Adreno 830 支持）。
