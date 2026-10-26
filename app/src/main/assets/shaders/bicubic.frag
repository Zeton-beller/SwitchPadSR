#version 300 es
precision highp float;
precision highp sampler2D;
// Catmull-Rom bicubic using 4 bilinear taps.
uniform sampler2D u_tex;
uniform vec2 u_inSize;
in vec2 v_uv;
out vec4 o_color;
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
    vec2 inv = 1.0 / u_inSize;
    vec2 pixel = v_uv * u_inSize - 0.5;
    vec2 f = fract(pixel);
    pixel = floor(pixel) + 0.5;
    vec4 xc = cubic(f.x);
    vec4 yc = cubic(f.y);
    vec4 c = vec4(pixel.x - 0.5, pixel.x + 1.5, pixel.y - 0.5, pixel.y + 1.5);
    vec4 s = vec4(xc.x + xc.y, xc.z + xc.w, yc.x + yc.y, yc.z + yc.w);
    vec4 off = c + vec4(xc.y, xc.w, yc.y, yc.w) / s;
    vec3 s0 = texture(u_tex, vec2(off.x, off.z) * inv).rgb;
    vec3 s1 = texture(u_tex, vec2(off.y, off.z) * inv).rgb;
    vec3 s2 = texture(u_tex, vec2(off.x, off.w) * inv).rgb;
    vec3 s3 = texture(u_tex, vec2(off.y, off.w) * inv).rgb;
    float sx = s.x / (s.x + s.y);
    float sy = s.z / (s.z + s.w);
    vec3 col = mix(mix(s3, s2, sx), mix(s1, s0, sx), sy);
    o_color = vec4(clamp(col, 0.0, 1.0), 1.0);
}
