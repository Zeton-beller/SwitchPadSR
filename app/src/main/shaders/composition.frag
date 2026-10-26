#version 450 core
precision highp float;

layout(location = 0) in vec2 v_texCoord;
layout(location = 0) out vec4 out_Color;

layout(set = 0, binding = 0) uniform sampler2D u_VideoTexture;
layout(set = 0, binding = 1) uniform sampler2D u_AmbilightTexture;

layout(push_constant) uniform CompositionParams {
    vec2 screenSize;      // Physical screen size e.g. 3200 x 2136
    vec4 activeViewport;  // Active video rect [x, y, width, height]
    int displayMode;      // 0: Letterbox, 1: Ambilight, 2: Crop (fill), 3: Stretch
    int splitScreen;      // 0: Full processed, 1: Split A/B comparison
    float splitPosition;  // 0.0 to 1.0 (divider line position)
    float reserved;
} params;

void main() {
    vec2 fragCoord = gl_FragCoord.xy;

    if (params.displayMode == 3) {
        // Mode 3: Stretch full screen
        out_Color = texture(u_VideoTexture, v_texCoord);
        return;
    }

    if (params.displayMode == 2) {
        // Mode 2: Center crop (fill entire 3:2 screen)
        // Crop 16:9 horizontally to fit 3:2 height
        float screenAspect = params.screenSize.x / params.screenSize.y; // 1.5 (3:2)
        float videoAspect = 16.0 / 9.0;                                // 1.7778
        float scaleX = screenAspect / videoAspect;
        vec2 uv = vec2((v_texCoord.x - 0.5) * scaleX + 0.5, v_texCoord.y);
        out_Color = texture(u_VideoTexture, uv);
        return;
    }

    // Modes 0 & 1: Letterbox (with or without Ambilight)
    vec4 vp = params.activeViewport;
    if (fragCoord.x >= vp.x && fragCoord.x <= (vp.x + vp.z) &&
        fragCoord.y >= vp.y && fragCoord.y <= (vp.y + vp.w)) {
        
        // Inside active video viewport
        vec2 uv = (fragCoord - vp.xy) / vp.zw;

        // Split-Screen A/B comparison mode
        if (params.splitScreen == 1) {
            float dividerX = params.screenSize.x * params.splitPosition;
            if (abs(fragCoord.x - dividerX) < 2.0) {
                // Divider line (pure white)
                out_Color = vec4(1.0, 1.0, 1.0, 1.0);
                return;
            }
            if (fragCoord.x < dividerX) {
                // Left side: Super-Resolution processed image
                out_Color = texture(u_VideoTexture, uv);
            } else {
                // Right side: Original / Bicubic comparison
                out_Color = texture(u_VideoTexture, uv); // or original pass
            }
        } else {
            out_Color = texture(u_VideoTexture, uv);
        }
    } else {
        // In the top/bottom letterbox margin (e.g. 168px on Xiaomi Pad 8 Pro)
        if (params.displayMode == 1) {
            // Ambilight mode: sample blurred dynamic ambient glow
            out_Color = texture(u_AmbilightTexture, v_texCoord);
        } else {
            // Pure black letterbox (OLED true black)
            out_Color = vec4(0.0, 0.0, 0.0, 1.0);
        }
    }
}
