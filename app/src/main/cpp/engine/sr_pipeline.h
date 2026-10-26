#pragma once

#include <cstdint>

namespace switchpad {

enum class SRAlgorithm : int {
    SGSR_1_0 = 0,
    FSR_1_0 = 1,
    ANIME4K = 2,
    RETRO_CRT = 3,
    BICUBIC = 4
};

enum class DisplayMode : int {
    LETTERBOX = 0,
    AMBILIGHT = 1,
    CENTER_CROP = 2,
    STRETCH = 3
};

// SGSR Push Constants
struct SGSRParams {
    float inputWidth;
    float inputHeight;
    float outputWidth;
    float outputHeight;
    float edgeThreshold;   // 1.0 - 16.0
    float edgeSharpness;   // 1.0 - 2.0
    int32_t useEdgeDirection; // 0 or 1
    int32_t reserved;
};

// FSR Push Constants
struct FSRParams {
    float inputWidth;
    float inputHeight;
    float outputWidth;
    float outputHeight;
    float sharpness;       // 0.0 - 1.0
    int32_t dering;        // 0 or 1
    float reserved[2];
};

// Anime4K Push Constants
struct Anime4KParams {
    float inputWidth;
    float inputHeight;
    float strength;        // 0.5 - 2.0
    int32_t mode;          // 0: A, 1: B, 2: C
};

// CRT Push Constants
struct CRTParams {
    float outputWidth;
    float outputHeight;
    float scanlineWeight;  // 0.0 - 1.0
    float curvature;       // 0.0 - 0.3
    float maskIntensity;   // 0.0 - 1.0
    float reserved[3];
};

// Composition Push Constants
struct CompositionParams {
    float screenWidth;
    float screenHeight;
    float vpX, vpY, vpWidth, vpHeight;
    int32_t displayMode;
    int32_t splitScreen;
    float splitPosition;
    float reserved;
};

} // namespace switchpad
