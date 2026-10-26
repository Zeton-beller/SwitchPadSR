#version 300 es
precision highp float;
precision highp sampler2D;
uniform sampler2D u_tex;
uniform vec2  u_outSize;
uniform float u_scanline;   // 0..1
uniform float u_curvature;  // 0..0.3
uniform float u_mask;       // 0..1
in vec2 v_uv;
out vec4 o_color;
vec2 curve(vec2 uv, float k) {
    if (k <= 0.001) return uv;
    vec2 cc = uv - 0.5;
    return uv + cc * (dot(cc, cc) * k * 4.0);
}
void main() {
    vec2 uv = curve(v_uv, u_curvature);
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        o_color = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec3 col = texture(u_tex, uv).rgb;
    float sl = sin(uv.y * u_outSize.y * 3.14159265 / 2.0);
    sl = mix(1.0, 0.5 + 0.5 * sl * sl, u_scanline);
    col *= sl;
    int idx = int(mod(floor(uv.x * u_outSize.x), 3.0));
    vec3 m = idx == 0 ? vec3(1.0, 0.7, 0.7) : (idx == 1 ? vec3(0.7, 1.0, 0.7) : vec3(0.7, 0.7, 1.0));
    col = mix(col, col * m, u_mask);
    col = pow(col, vec3(1.0 / 1.1)) * 1.05;
    o_color = vec4(clamp(col, 0.0, 1.0), 1.0);
}
