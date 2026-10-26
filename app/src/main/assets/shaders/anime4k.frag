#version 300 es
precision highp float;
precision highp sampler2D;
// Lightweight Anime4K-style line restoration, run at output resolution on an
// already bicubic-upscaled image: unsharp on line edges, line darkening
// (thin lines), and flat-area denoising.
uniform sampler2D u_tex;
uniform vec2  u_inSize;
uniform float u_strength;    // 0.5..2
uniform int   u_mode;        // 0: A restore, 1: B restore+denoise, 2: C line push
in vec2 v_uv;
out vec4 o_color;
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
void main() {
    vec2 px = 1.0 / u_inSize;
    vec3 c  = texture(u_tex, v_uv).rgb;
    vec3 n[8];
    n[0] = texture(u_tex, v_uv + vec2(-px.x, -px.y)).rgb;
    n[1] = texture(u_tex, v_uv + vec2( 0.0,  -px.y)).rgb;
    n[2] = texture(u_tex, v_uv + vec2( px.x, -px.y)).rgb;
    n[3] = texture(u_tex, v_uv + vec2(-px.x,  0.0)).rgb;
    n[4] = texture(u_tex, v_uv + vec2( px.x,  0.0)).rgb;
    n[5] = texture(u_tex, v_uv + vec2(-px.x,  px.y)).rgb;
    n[6] = texture(u_tex, v_uv + vec2( 0.0,   px.y)).rgb;
    n[7] = texture(u_tex, v_uv + vec2( px.x,  px.y)).rgb;
    vec3 blur = (c * 4.0 + (n[1] + n[3] + n[4] + n[6]) * 2.0 + n[0] + n[2] + n[5] + n[7]) / 16.0;
    float l0 = luma(n[0]), l1 = luma(n[1]), l2 = luma(n[2]), l3 = luma(n[3]);
    float l4 = luma(n[4]), l5 = luma(n[5]), l6 = luma(n[6]), l7 = luma(n[7]);
    float sx = (l2 + 2.0 * l4 + l7) - (l0 + 2.0 * l3 + l5);
    float sy = (l5 + 2.0 * l6 + l7) - (l0 + 2.0 * l1 + l2);
    float mag = length(vec2(sx, sy)) * 0.25;
    float edge = smoothstep(0.02, 0.18, mag);
    float s = u_strength;
    vec3 res = c;
    // unsharp restricted to edges
    float amount = (u_mode == 2) ? 1.6 : 1.1;
    res += (c - blur) * amount * s * edge;
    // darken thin dark lines: pixel darker than its blurred surround
    float lc = luma(c), lb = luma(blur);
    float push = (u_mode == 2) ? 0.9 : 0.5;
    res -= vec3(max(lb - lc, 0.0)) * push * s * edge;
    // flat area denoise
    if (u_mode == 1) res = mix(res, blur, (1.0 - edge) * 0.4);
    o_color = vec4(clamp(res, 0.0, 1.0), 1.0);
}
