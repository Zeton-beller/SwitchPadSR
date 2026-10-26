#version 300 es
precision highp float;
precision highp sampler2D;
// Final composition to the screen: letterbox / ambilight / crop / stretch and
// optional A/B split (left = processed, right = original bilinear).
uniform sampler2D u_sr;
uniform sampler2D u_raw;
uniform vec2  u_screen;     // px
uniform vec4  u_rect;       // x, y, w, h in px, origin bottom-left
uniform int   u_ambient;    // 0/1
uniform int   u_split;      // 0/1
uniform float u_splitPos;   // 0..1
out vec4 o_color;
void main() {
    vec2 frag = gl_FragCoord.xy;
    vec2 uv = (frag - u_rect.xy) / u_rect.zw;
    bool inside = uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0;
    vec3 col = vec3(0.0);
    if (inside) {
        float sx = u_screen.x * u_splitPos;
        if (u_split == 1 && frag.x > sx) col = texture(u_raw, uv).rgb;
        else col = texture(u_sr, uv).rgb;
        if (u_split == 1 && abs(frag.x - sx) < 2.0) col = vec3(1.0);
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
