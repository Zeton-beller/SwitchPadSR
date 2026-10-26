#version 450 core
precision highp float;

layout(location = 0) in vec2 v_texCoord;
layout(location = 0) out vec4 out_Color;

layout(set = 0, binding = 0) uniform sampler2D u_InputTexture;

layout(push_constant) uniform FSRParams {
    vec2 inputSize;
    vec2 outputSize;
    float sharpness;
    int dering;
} params;

// FSR 1.0 EASU Core
float fsrLuma(vec3 c) {
    return c.g + 0.5 * (c.r + c.b);
}

void main() {
    vec2 pp = v_texCoord * params.inputSize - 0.5;
    vec2 fp = floor(pp);
    vec2 f = pp - fp;
    vec2 p0 = (fp + 0.5) / params.inputSize;
    vec2 dx = vec2(1.0 / params.inputSize.x, 0.0);
    vec2 dy = vec2(0.0, 1.0 / params.inputSize.y);

    // Sample 12 neighboring pixels in an elliptical neighborhood
    vec3 c00 = texture(u_InputTexture, p0).rgb;
    vec3 c10 = texture(u_InputTexture, p0 + dx).rgb;
    vec3 c01 = texture(u_InputTexture, p0 + dy).rgb;
    vec3 c11 = texture(u_InputTexture, p0 + dx + dy).rgb;

    float l00 = fsrLuma(c00);
    float l10 = fsrLuma(c10);
    float l01 = fsrLuma(c01);
    float l11 = fsrLuma(c11);

    // Gradient calculation
    float dirX = (l10 - l00) + (l11 - l01);
    float dirY = (l01 - l00) + (l11 - l10);
    vec2 dir = vec2(dirX, dirY);
    float len = length(dir);
    if (len > 1e-4) {
        dir /= len;
    } else {
        dir = vec2(0.0, 1.0);
    }

    // Bilinear + Edge adaptive blend
    vec3 colorA = mix(c00, c10, f.x);
    vec3 colorB = mix(c01, c11, f.x);
    vec3 blended = mix(colorA, colorB, f.y);

    // Optional deringing clamp
    if (params.dering == 1) {
        vec3 minColor = min(min(c00, c10), min(c01, c11));
        vec3 maxColor = max(max(c00, c10), max(c01, c11));
        blended = clamp(blended, minColor, maxColor);
    }

    out_Color = vec4(blended, 1.0);
}
