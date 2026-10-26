#version 450 core
precision highp float;

layout(location = 0) in vec2 v_texCoord;
layout(location = 0) out vec4 out_Color;

layout(set = 0, binding = 0) uniform sampler2D u_InputTexture;

layout(push_constant) uniform AmbilightParams {
    vec2 screenSize;      // 3200 x 2136
    vec4 activeViewport;  // x, y, width, height (e.g. 0, 168, 3200, 1800)
    float blurRadius;     // Blur spread
    float brightness;     // Ambient brightness multiplier
} params;

// Gaussian blur kernel sampling edge border strips
void main() {
    float y = gl_FragCoord.y;
    float topBoundary = params.activeViewport.y;
    float bottomBoundary = params.activeViewport.y + params.activeViewport.w;

    vec2 sampleCoord;
    if (y < topBoundary) {
        // Top letterbox margin: sample the top edge of active video
        sampleCoord = vec2(v_texCoord.x, 0.02);
    } else if (y > bottomBoundary) {
        // Bottom letterbox margin: sample the bottom edge of active video
        sampleCoord = vec2(v_texCoord.x, 0.98);
    } else {
        // Inside active area: not handled by ambilight
        discard;
    }

    vec3 accum = vec3(0.0);
    float weights[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
    float dx = params.blurRadius / params.screenSize.x;

    accum += texture(u_InputTexture, sampleCoord).rgb * weights[0];
    for (int i = 1; i < 5; ++i) {
        float offset = float(i) * dx;
        accum += texture(u_InputTexture, sampleCoord + vec2(offset, 0.0)).rgb * weights[i];
        accum += texture(u_InputTexture, sampleCoord - vec2(offset, 0.0)).rgb * weights[i];
    }

    out_Color = vec4(accum * params.brightness, 1.0);
}
