#pragma once

#include <atomic>
#include <cstdint>

namespace switchpad {

struct EngineMetrics {
    std::atomic<float> currentFps{60.0f};
    std::atomic<float> gpuPassTimeMs{0.22f};
    std::atomic<float> glassToGlassLatencyMs{28.5f};
    std::atomic<uint64_t> totalFramesRendered{0};
    std::atomic<uint64_t> droppedFrames{0};
    std::atomic<int> activeAlgorithm{0}; // 0: SGSR, 1: FSR, 2: Anime4K, 3: CRT, 4: Bicubic
    std::atomic<int> inputMode{0};       // 0: Capture Card, 1: Video Player
    std::atomic<int> targetWidth{3200};
    std::atomic<int> targetHeight{1800};
};

} // namespace switchpad
