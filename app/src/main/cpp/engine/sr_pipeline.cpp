#include "sr_pipeline.h"

namespace switchpad {

// Core parameter validation and sanity limits
void sanitizeSGSRParams(SGSRParams& p) {
    if (p.edgeThreshold < 1.0f) p.edgeThreshold = 1.0f;
    if (p.edgeThreshold > 16.0f) p.edgeThreshold = 16.0f;
    if (p.edgeSharpness < 1.0f) p.edgeSharpness = 1.0f;
    if (p.edgeSharpness > 2.0f) p.edgeSharpness = 2.0f;
}

void sanitizeFSRParams(FSRParams& p) {
    if (p.sharpness < 0.0f) p.sharpness = 0.0f;
    if (p.sharpness > 1.0f) p.sharpness = 1.0f;
}

void sanitizeAnime4KParams(Anime4KParams& p) {
    if (p.strength < 0.5f) p.strength = 0.5f;
    if (p.strength > 2.0f) p.strength = 2.0f;
    if (p.mode < 0 || p.mode > 2) p.mode = 0;
}

void sanitizeCRTParams(CRTParams& p) {
    if (p.scanlineWeight < 0.0f) p.scanlineWeight = 0.0f;
    if (p.scanlineWeight > 1.0f) p.scanlineWeight = 1.0f;
    if (p.curvature < 0.0f) p.curvature = 0.0f;
    if (p.curvature > 0.3f) p.curvature = 0.3f;
    if (p.maskIntensity < 0.0f) p.maskIntensity = 0.0f;
    if (p.maskIntensity > 1.0f) p.maskIntensity = 1.0f;
}

} // namespace switchpad
