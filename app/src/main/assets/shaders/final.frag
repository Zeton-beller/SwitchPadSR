#version 300 es
precision highp float;
precision highp sampler2D;
// Final composition to the screen:
// - 4-tap Catmull-Rom resolve for u_sr (preserves 4K supersampled detail when downscaling to screen)
// - Letterbox / Ambilight / Center-Crop / Stretch
// - Optional A/B split-screen comparison (left = SR, right = raw input)
// - Optional 1x / 2x / 4x magnifier zoom for pixel-level inspection
uniform sampler2D u_sr;
uniform sampler2D u_raw;
uniform vec2  u_srSize;     // target FBO resolution (resultW, resultH)
uniform vec2  u_screen;     // screen viewport px
uniform vec4  u_rect;       // x, y, w, h in px, origin bottom-left
uniform int   u_ambient;    // 0/1
uniform int   u_split;      // 0/1
uniform float u_splitPos;   // 0..1
uniform float u_zoom;       // 1.0, 2.0, 4.0
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

vec3 sampleSr(vec2 uv) {
    vec2 sz = max(u_srSize, vec2(16.0));
    vec2 inv = 1.0 / sz;
    vec2 pixel = uv * sz - 0.5;
    vec2 f = fract(pixel);
    pixel = floor(pixel) + 0.5;
    vec4 xc = cubic(f.x);
    vec4 yc = cubic(f.y);
    vec4 c = vec4(pixel.x - 0.5, pixel.x + 1.5, pixel.y - 0.5, pixel.y + 1.5);
    vec4 s = vec4(xc.x + xc.y, xc.z + xc.w, yc.x + yc.y, yc.z + yc.w);
    vec4 off = c + vec4(xc.y, xc.w, yc.y, yc.w) / s;
    vec3 s0 = texture(u_sr, vec2(off.x, off.z) * inv).rgb;
    vec3 s1 = texture(u_sr, vec2(off.y, off.z) * inv).rgb;
    vec3 s2 = texture(u_sr, vec2(off.x, off.w) * inv).rgb;
    vec3 s3 = texture(u_sr, vec2(off.y, off.w) * inv).rgb;
    float sx = s.x / (s.x + s.y);
    float sy = s.z / (s.z + s.w);
    return clamp(mix(mix(s3, s2, sx), mix(s1, s0, sx), sy), 0.0, 1.0);
}

void main() {
    vec2 frag = gl_FragCoord.xy;
    vec2 uv = (frag - u_rect.xy) / u_rect.zw;
    bool inside = uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0;
    vec3 col = vec3(0.0);
    if (inside) {
        float z = max(u_zoom, 1.0);
        vec2 zuv = clamp((uv - 0.5) / z + 0.5, 0.0, 1.0);
        float sx = u_screen.x * u_splitPos;
        if (u_split == 1 && frag.x > sx) col = texture(u_raw, zuv).rgb;
        else col = sampleSr(zuv);
        if (u_split == 1 && abs(frag.x - sx) < 2.0) col = vec3(0.0, 0.88, 1.0);
    } else if (u_ambient == 1) {
        vec2 cuv = clamp(uv, 0.0, 1.0);
        vec3 acc = vec3(0.0);
        for (int j = -1; j <= 1; ++j) {
            for (int i = -1; i <= 1; ++i) {
                vec2 o = vec2(float(i), float(j)) * 0.05;
                acc += texture(u_sr, clamp(cuv + o, 0.0, 1.0)).rgb;
            }
        }
        acc /= 9.0;
        vec2 center = u_rect.xy + 0.5 * u_rect.zw;
        float d = length(max(abs(frag - center) - 0.5 * u_rect.zw, 0.0));
        float fade = exp(-d / (0.25 * min(u_screen.x, u_screen.y)));
        col = acc * 0.6 * fade;
    }
    o_color = vec4(col, 1.0);
}
