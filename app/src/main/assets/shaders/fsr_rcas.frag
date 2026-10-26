#version 300 es
precision highp float;
precision highp sampler2D;
// FSR 1.0 RCAS (robust contrast adaptive sharpening), 5-tap cross.
uniform sampler2D u_tex;
uniform vec2  u_inSize;     // size of u_tex (== output size of EASU)
uniform float u_sharp;      // 0..1  (1 = sharpest)
in vec2 v_uv;
out vec4 o_color;
vec3 fetchC(ivec2 p) {
    ivec2 s = ivec2(u_inSize);
    return texelFetch(u_tex, clamp(p, ivec2(0), s - 1), 0).rgb;
}
void main() {
    ivec2 ip = ivec2(v_uv * u_inSize);
    vec3 e = fetchC(ip);
    vec3 bb = fetchC(ip + ivec2(0, -1));
    vec3 d = fetchC(ip + ivec2(-1, 0));
    vec3 f = fetchC(ip + ivec2(1, 0));
    vec3 h = fetchC(ip + ivec2(0, 1));
    vec3 mn4 = min(min(bb, d), min(f, h));
    vec3 mx4 = max(max(bb, d), max(f, h));
    vec3 hitMin = mn4 / (4.0 * mx4 + 1e-5);
    vec3 hitMax = (1.0 - mx4) / (4.0 * mn4 - 4.0 - 1e-5);
    vec3 lobeRGB = max(-hitMin, hitMax);
    float lobe = max(-0.1875, min(max(lobeRGB.r, max(lobeRGB.g, lobeRGB.b)), 0.0));
    float stops = (1.0 - clamp(u_sharp, 0.0, 1.0)) * 2.0;
    lobe *= exp2(-stops);
    float rcpL = 1.0 / (4.0 * lobe + 1.0);
    vec3 col = (lobe * (bb + d + f + h) + e) * rcpL;
    o_color = vec4(clamp(col, 0.0, 1.0), 1.0);
}
