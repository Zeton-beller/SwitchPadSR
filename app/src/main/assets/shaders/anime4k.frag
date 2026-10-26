#version 300 es
precision highp float;
precision highp sampler2D;
// Anime4K-style real-time line reconstruction:
// 1. Source-scale Sobel gradient & local min/max envelope
// 2. Iterative Gradient Line Push (condenses wide blurry edges into thin crisp vector lines)
// 3. Mode A: Clean line restore + edge unsharp
//    Mode B: Bilateral flat-area cel denoise + line restore + vibrance
//    Mode C: Aggressive line thinning + morphological ink-outline darkening
uniform sampler2D u_tex;
uniform vec2  u_inSize;      // target FBO size
uniform vec2  u_srcSize;     // source video size
uniform float u_strength;    // 0.5..2.0
uniform int   u_mode;        // 0: A restore, 1: B restore+denoise, 2: C line push & darken
in vec2 v_uv;
out vec4 o_color;

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

void main() {
    vec2 pxFine = 1.0 / max(u_inSize, vec2(1.0));
    vec2 scale = max(u_inSize / max(u_srcSize, vec2(1.0)), vec2(1.0));
    // Sample at source-texel radius so the 3x3 kernel spans the full anti-aliased edge ramp
    vec2 px = clamp(scale * 0.85, vec2(1.0), vec2(4.0)) * pxFine;

    vec3 c = texture(u_tex, v_uv).rgb;
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

    float lc = luma(c);
    float l0 = luma(n[0]), l1 = luma(n[1]), l2 = luma(n[2]), l3 = luma(n[3]);
    float l4 = luma(n[4]), l5 = luma(n[5]), l6 = luma(n[6]), l7 = luma(n[7]);

    float lMin = min(lc, min(min(min(l0, l1), min(l2, l3)), min(min(l4, l5), min(l6, l7))));
    float lMax = max(lc, max(max(max(l0, l1), max(l2, l3)), max(max(l4, l5), max(l6, l7))));

    vec3 cMin = c, cMax = c;
    for (int i = 0; i < 8; ++i) { cMin = min(cMin, n[i]); cMax = max(cMax, n[i]); }

    float sx = (l2 + 2.0 * l4 + l7) - (l0 + 2.0 * l3 + l5);
    float sy = (l5 + 2.0 * l6 + l7) - (l0 + 2.0 * l1 + l2);
    vec2 g = vec2(sx, sy);
    float mag = length(g) * 0.25;
    vec2 dir = (mag > 1e-4) ? normalize(g) : vec2(0.0);
    float edge = smoothstep(0.012, 0.14, mag);

    float s = clamp(u_strength, 0.2, 2.5);

    // Stage 1: Anime4K Gradient Line Push (warps UV along gradient normal toward bright/dark side)
    float lMid = 0.5 * (lMin + lMax);
    float side = clamp((lc - lMid) / max(lMax - lMin, 0.035) * 2.4, -1.0, 1.0);
    float pushMul = (u_mode == 2) ? 0.72 : 0.52;
    vec2 warpUv = v_uv + dir * px * (side * pushMul * s * edge);
    vec3 pushed = texture(u_tex, warpUv).rgb;

    vec3 res = mix(c, pushed, clamp(edge * (0.60 + 0.25 * s), 0.0, 1.0));

    // Stage 2: Edge-directed unsharp mask with anti-ringing envelope
    float unsharpGain = (u_mode == 2) ? 1.65 : ((u_mode == 1) ? 1.15 : 1.30);
    res += (res - blur) * unsharpGain * s * edge;
    float clampPad = (u_mode == 2) ? (0.06 * s) : (0.035 * s);
    res = clamp(res, cMin - clampPad, cMax + clampPad);

    // Stage 3: Mode-specific processing
    float lr = luma(res);
    float lb = luma(blur);
    if (u_mode == 0) {
        // Mode A (Restore): balanced thin-line darkening
        float valley = max(lb - lr, 0.0);
        res -= vec3(valley * 0.65 * s * edge);
    } else if (u_mode == 1) {
        // Mode B (Restore + Bilateral Cel Denoise): smooth flat color blocks while keeping crisp lines
        vec3 biSum = c * 1.5;
        float biW = 1.5;
        for (int i = 0; i < 8; ++i) {
            vec3 dc = n[i] - c;
            float w = exp(-dot(dc, dc) * 36.0);
            biSum += n[i] * w;
            biW += w;
        }
        vec3 biClean = biSum / biW;
        float valley = max(lb - lr, 0.0);
        res -= vec3(valley * 0.50 * s * edge);
        res = mix(biClean, res, clamp(edge * 1.35, 0.0, 1.0));
        // Subtle cel-shading color vibrance boost
        float lFinal = luma(res);
        res = mix(vec3(lFinal), res, 1.0 + 0.07 * s);
    } else {
        // Mode C (Line Push & Darken): strong ink-outline darkening & high-contrast cel lines
        float valley = max(lb - lr, 0.0) + 0.40 * max(lMax - lr, 0.0) * edge;
        float inkFactor = clamp(1.0 - valley * 1.75 * s, 0.10, 1.0);
        res *= mix(1.0, inkFactor, edge);
    }

    o_color = vec4(clamp(res, 0.0, 1.0), 1.0);
}
