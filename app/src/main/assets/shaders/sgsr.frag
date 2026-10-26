#version 300 es
precision highp float;
precision highp sampler2D;
// Edge-directed 4x4 Lanczos2 upscaler in the spirit of Snapdragon GSR 1.0.
// Gradient of the central 2x2 block gives the edge normal; the kernel is
// stretched along the edge and tightened across it.
uniform sampler2D u_tex;
uniform vec2  u_inSize;
uniform float u_threshold;   // 1..16  (luma gradient, in 1/255 units)
uniform float u_sharp;       // 1..2
uniform int   u_useDir;      // 0/1
in vec2 v_uv;
out vec4 o_color;

vec3 fetchC(ivec2 p) {
    ivec2 s = ivec2(u_inSize);
    return texelFetch(u_tex, clamp(p, ivec2(0), s - 1), 0).rgb;
}
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec4 catmull(float t) {
    float t2 = t * t, t3 = t2 * t;
    return vec4(-0.5 * t3 + t2 - 0.5 * t,
                 1.5 * t3 - 2.5 * t2 + 1.0,
                -1.5 * t3 + 2.0 * t2 + 0.5 * t,
                 0.5 * t3 - 0.5 * t2);
}
float lanczos2(float x) {
    x = abs(x);
    if (x < 1e-4) return 1.0;
    if (x >= 2.0) return 0.0;
    float px = 3.14159265 * x;
    return 2.0 * sin(px) * sin(px * 0.5) / (px * px);
}
void main() {
    vec2 p = v_uv * u_inSize - 0.5;
    ivec2 b = ivec2(floor(p));
    vec2 f = p - vec2(b);
    vec3 c[16];
    float L[16];
    for (int j = 0; j < 4; ++j) {
        for (int i = 0; i < 4; ++i) {
            vec3 v = fetchC(b + ivec2(i - 1, j - 1));
            c[j * 4 + i] = v;
            L[j * 4 + i] = luma(v);
        }
    }
    // Catmull-Rom base result (also the flat-area fallback)
    vec4 wx = catmull(f.x);
    vec4 wy = catmull(f.y);
    vec3 base = vec3(0.0);
    for (int j = 0; j < 4; ++j) {
        vec3 row = c[j * 4] * wx.x + c[j * 4 + 1] * wx.y + c[j * 4 + 2] * wx.z + c[j * 4 + 3] * wx.w;
        base += row * wy[j];
    }
    base = clamp(base, 0.0, 1.0);

    float gx = 0.25 * ((L[6] - L[4]) + (L[7] - L[5]) + (L[10] - L[8]) + (L[11] - L[9]));
    float gy = 0.25 * ((L[9] - L[1]) + (L[10] - L[2]) + (L[13] - L[5]) + (L[14] - L[6]));
    vec2 g = vec2(gx, gy);
    float mag = length(g);
    float thr = u_threshold / 255.0;
    if (mag < thr) {
        o_color = vec4(base, 1.0);
        return;
    }
    vec2 n = g / mag;
    vec2 t = vec2(-n.y, n.x);
    float k = clamp(u_sharp - 1.0, 0.0, 1.0);
    float sa, sb;
    if (u_useDir == 1) { sa = 0.75; sb = 1.0 + 0.6 * k; }
    else { sa = 1.0 + 0.25 * k; sb = sa; }
    vec3 sum = vec3(0.0);
    float ws = 0.0;
    for (int j = 0; j < 4; ++j) {
        for (int i = 0; i < 4; ++i) {
            vec2 d = vec2(float(i - 1) - f.x, float(j - 1) - f.y);
            float a = dot(d, t) * sa;
            float bb = dot(d, n) * sb;
            float w = lanczos2(length(vec2(a, bb)));
            sum += c[j * 4 + i] * w;
            ws += w;
        }
    }
    vec3 res = sum / max(ws, 1e-4);
    vec3 mn = min(min(c[5], c[6]), min(c[9], c[10]));
    vec3 mx = max(max(c[5], c[6]), max(c[9], c[10]));
    res = clamp(res, mn - 0.03, mx + 0.03);
    float m = smoothstep(thr, thr * 2.0 + 0.02, mag);
    o_color = vec4(clamp(mix(base, res, m), 0.0, 1.0), 1.0);
}
