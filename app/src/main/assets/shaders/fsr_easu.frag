#version 300 es
precision highp float;
precision highp sampler2D;
// FSR 1.0 EASU-style edge adaptive spatial upscaler (4x4 taps, EASU kernel
// (2/5 x^2-1)^2 (lob x^2-1), anisotropic by local gradient, de-ringed).
uniform sampler2D u_tex;
uniform vec2  u_inSize;
uniform int   u_dering;      // 0/1
in vec2 v_uv;
out vec4 o_color;

vec3 fetchC(ivec2 p) {
    ivec2 s = ivec2(u_inSize);
    return texelFetch(u_tex, clamp(p, ivec2(0), s - 1), 0).rgb;
}
float luma(vec3 c) { return c.g + 0.5 * (c.r + c.b); }

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
    float gx = 0.25 * ((L[6] - L[4]) + (L[7] - L[5]) + (L[10] - L[8]) + (L[11] - L[9]));
    float gy = 0.25 * ((L[9] - L[1]) + (L[10] - L[2]) + (L[13] - L[5]) + (L[14] - L[6]));
    vec2 g = vec2(gx, gy);
    float mag = length(g);
    float conf = clamp(mag * 6.0, 0.0, 1.0);
    vec2 n = mag > 1e-5 ? g / mag : vec2(1.0, 0.0);
    vec2 t = vec2(-n.y, n.x);
    float stretchAlong  = mix(1.0, 0.6, conf);    // longer kernel along the edge
    float stretchAcross = mix(1.0, 1.2, conf);
    float lob = 0.5 + 0.2 * conf;
    float clp = 1.0 / lob;
    vec3 sum = vec3(0.0);
    float ws = 0.0;
    for (int j = 0; j < 4; ++j) {
        for (int i = 0; i < 4; ++i) {
            vec2 d = vec2(float(i - 1) - f.x, float(j - 1) - f.y);
            float a = dot(d, t) * stretchAlong;
            float bb = dot(d, n) * stretchAcross;
            float d2 = min(a * a + bb * bb, clp);
            float wB = 0.4 * d2 - 1.0;
            float wA = lob * d2 - 1.0;
            float w = wB * wB * wA;
            sum += c[j * 4 + i] * w;
            ws += w;
        }
    }
    vec3 res = sum / (abs(ws) > 1e-5 ? ws : 1e-5);
    if (u_dering == 1) {
        vec3 mn = min(min(c[5], c[6]), min(c[9], c[10]));
        vec3 mx = max(max(c[5], c[6]), max(c[9], c[10]));
        res = clamp(res, mn, mx);
    }
    o_color = vec4(clamp(res, 0.0, 1.0), 1.0);
}
