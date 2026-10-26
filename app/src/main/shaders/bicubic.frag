#version 450 core
precision highp float;

layout(location = 0) in vec2 v_texCoord;
layout(location = 0) out vec4 out_Color;

layout(set = 0, binding = 0) uniform sampler2D u_InputTexture;

layout(push_constant) uniform BicubicParams {
    vec2 inputSize;
    vec2 outputSize;
} params;

// Catmull-Rom cubic weights
vec4 cubic(float v) {
    vec4 n = vec4(1.0, 2.0, 3.0, 4.0) - v;
    vec4 s = n * n * n;
    float x = s.x;
    float y = s.y - 4.0 * s.x;
    float z = s.z - 4.0 * s.y + 6.0 * s.x;
    float w = 6.0 - x - y - z;
    return vec4(x, y, z, w) * (1.0 / 6.0);
}

void main() {
    vec2 invTexSize = 1.0 / params.inputSize;
    vec2 pixel = v_texCoord * params.inputSize - 0.5;
    vec2 f = fract(pixel);
    pixel = floor(pixel) + 0.5;

    vec4 xCubic = cubic(f.x);
    vec4 yCubic = cubic(f.y);

    vec4 c = vec4(pixel.x - 0.5, pixel.x + 1.5, pixel.y - 0.5, pixel.y + 1.5);
    vec4 s = vec4(xCubic.x + xCubic.y, xCubic.z + xCubic.w, yCubic.x + yCubic.y, yCubic.z + yCubic.w);
    vec4 offset = c + vec4(xCubic.y, xCubic.w, yCubic.y, yCubic.w) / s;

    vec4 sample0 = texture(u_InputTexture, vec2(offset.x, offset.z) * invTexSize);
    vec4 sample1 = texture(u_InputTexture, vec2(offset.y, offset.z) * invTexSize);
    vec4 sample2 = texture(u_InputTexture, vec2(offset.x, offset.w) * invTexSize);
    vec4 sample3 = texture(u_InputTexture, vec2(offset.y, offset.w) * invTexSize);

    float sx = s.x / (s.x + s.y);
    float sy = s.z / (s.z + s.w);

    vec4 color = mix(mix(sample3, sample2, sx), mix(sample1, sample0, sx), sy);
    out_Color = clamp(color, 0.0, 1.0);
}
