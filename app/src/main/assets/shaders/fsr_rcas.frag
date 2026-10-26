#version 300 es
precision highp float;
precision highp sampler2D;
// FSR 1.0 RCAS (Robust Contrast Adaptive Sharpening), source-scale aware + luma-guided limiter.
uniform sampler2D u_tex;
uniform vec2  u_inSize;     // output size of EASU FBO
uniform vec2  u_srcSize;    // original input video/capture size
uniform float u_sharp;      // 0..1 (0 = off, 1 = maximum sharpness)
uniform int   u_dering;     // 0/1
in vec2 v_uv;
out vec4 o_color;

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

void main() {
    vec3 e = texture(u_tex, v_uv).rgb;
    float sharp = clamp(u_sharp, 0.0, 1.0);
    if (sharp <= 0.001) {
        o_color = vec4(e, 1.0);
        return;
    }

    vec2 pxFine = 1.0 / max(u_inSize, vec2(1.0));
    vec2 scale = max(u_inSize / max(u_srcSize, vec2(1.0)), vec2(1.0));
    vec2 pxSrc = clamp(scale * 0.75, vec2(1.0), vec2(3.5)) * pxFine;

    // Source-scale 4-neighbor cross (captures real edge transitions after upscaling)
    vec3 bb = texture(u_tex, v_uv + vec2(0.0, -pxSrc.y)).rgb;
    vec3 d  = texture(u_tex, v_uv + vec2(-pxSrc.x, 0.0)).rgb;
    vec3 f  = texture(u_tex, v_uv + vec2( pxSrc.x, 0.0)).rgb;
    vec3 h  = texture(u_tex, v_uv + vec2(0.0,  pxSrc.y)).rgb;

    // Fine 1-target-pixel cross (captures high-frequency micro-detail)
    vec3 bf = texture(u_tex, v_uv + vec2(0.0, -pxFine.y)).rgb;
    vec3 df = texture(u_tex, v_uv + vec2(-pxFine.x, 0.0)).rgb;
    vec3 ff = texture(u_tex, v_uv + vec2( pxFine.x, 0.0)).rgb;
    vec3 hf = texture(u_tex, v_uv + vec2(0.0,  pxFine.y)).rgb;

    vec3 nb = 0.65 * (bb + d + f + h) + 0.35 * (bf + df + ff + hf);

    float le = luma(e);
    float lb = luma(bb), ld = luma(d), lf = luma(f), lh = luma(h);
    float mn4 = min(min(min(lb, ld), min(lf, lh)), le);
    float mx4 = max(max(max(lb, ld), max(lf, lh)), le);

    // Luma-based RCAS contrast limiter with active floor so edges touching 0 or 1 still sharpen
    float hitMin = mn4 / (4.0 * mx4 + 1e-4);
    float hitMax = (1.0 - mx4) / (4.0 * mn4 - 4.0 - 1e-4);
    float rcasLim = max(-hitMin, hitMax);
    float maxLobe = (u_dering == 1) ? -0.30 : -0.44;
    float lobe = mix(maxLobe, clamp(rcasLim, maxLobe, -0.15), (u_dering == 1) ? 0.50 : 0.20);

    // Responsive gain curve across 0..1
    float gain = sharp * (1.35 - 0.35 * sharp);
    lobe *= gain;

    float rcpL = 1.0 / max(4.0 * lobe + 1.0, 0.15);
    vec3 col = (lobe * nb + e) * rcpL;

    vec3 cMin = min(min(min(bb, d), min(f, h)), e);
    vec3 cMax = max(max(max(bb, d), max(f, h)), e);
    float headroom = (u_dering == 1) ? (0.025 + 0.035 * gain) : (0.14 + 0.20 * gain);
    col = clamp(col, cMin - headroom, cMax + headroom);

    o_color = vec4(clamp(col, 0.0, 1.0), 1.0);
}
