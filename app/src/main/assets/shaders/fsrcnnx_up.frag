#version 300 es
precision highp float;
precision highp int;
precision highp sampler2D;

//============================================================================================================
// FSRCNNX AI Super-Resolution - Pass 2: Continuous PixelShuffle, Bicubic Chroma & AMD FSR RCAS
// Samples the sub-pixel neural luminance tensor (u_tex) at target display resolution (3.2K or preset),
// reconstructs high-fidelity Catmull-Rom chroma (u_raw) to eliminate chromatic aberration,
// and applies Stage 2 AMD FSR RCAS contrast-adaptive sharpness.
//============================================================================================================

uniform sampler2D u_tex;    // Sub-pixel luminance tensor from Pass 1 (u_inSize)
uniform sampler2D u_raw;    // Original input RGB frame (u_inSize)
uniform vec2 u_inSize;      // Input resolution (e.g. 1920x1080)
uniform vec2 u_outSize;     // Target display resolution (e.g. 3200x2000)
uniform float u_sharp;      // RCAS Sharpness: 0.0 = smooth neural, 1.0 = razor-sharp

in vec2 v_uv;
out vec4 o_color;

// Catmull-Rom 4-tap spline weights
vec4 catmullRom(float t) {
    float t2 = t * t;
    float t3 = t2 * t;
    return vec4(
        -0.5 * t3 +        t2 - 0.5 * t,
         1.5 * t3 - 2.5 * t2 + 1.0,
        -1.5 * t3 + 2.0 * t2 + 0.5 * t,
         0.5 * t3 - 0.5 * t2
    );
}

// Sample Catmull-Rom bicubic RGB from raw texture
vec3 sampleCatmullRom(sampler2D tex, vec2 uv, vec2 size) {
    vec2 pos = uv * size - 0.5;
    vec2 f = fract(pos);
    vec2 base = floor(pos);

    vec4 wx = catmullRom(f.x);
    vec4 wy = catmullRom(f.y);

    vec3 col = vec3(0.0);
    for (int j = 0; j < 4; j++) {
        for (int i = 0; i < 4; i++) {
            vec2 cUv = (base + vec2(float(i - 1), float(j - 1)) + 0.5) / size;
            cUv = clamp(cUv, 0.5 / size, 1.0 - 0.5 / size);
            col += texture(tex, cUv).rgb * (wx[i] * wy[j]);
        }
    }
    return clamp(col, 0.0, 1.0);
}

// Sample single neural luma from sub-pixel tensor
float sampleSubpixelLuma(vec2 uv) {
    vec2 pos = uv * u_inSize - 0.5;
    vec2 base = floor(pos);
    vec2 f = fract(pos);

    vec2 baseUv = clamp((base + 0.5) / u_inSize, 0.5 / u_inSize, 1.0 - 0.5 / u_inSize);
    vec4 st = texture(u_tex, baseUv);

    // Sub-pixel quadrant indexing:
    // r: S00 (top-left, f.x < 0.5, f.y < 0.5)
    // g: S10 (top-right, f.x >= 0.5, f.y < 0.5)
    // b: S01 (bottom-left, f.x < 0.5, f.y >= 0.5)
    // a: S11 (bottom-right, f.x >= 0.5, f.y >= 0.5)
    float luma = (f.y < 0.5) ? ((f.x < 0.5) ? st.r : st.g)
                             : ((f.x < 0.5) ? st.b : st.a);
    return luma;
}

// Reconstruct RGB from neural luma and bicubic chroma
vec3 reconstructRgb(vec2 uv) {
    float y = sampleSubpixelLuma(uv);

    vec3 rawRgb = sampleCatmullRom(u_raw, uv, u_inSize);
    float rawY = dot(rawRgb, vec3(0.2126, 0.7152, 0.0722));
    float cb = (rawRgb.b - rawY) / 1.8556;
    float cr = (rawRgb.r - rawY) / 1.5748;

    float r = y + 1.5748 * cr;
    float b = y + 1.8556 * cb;
    float g = (y - 0.2126 * r - 0.0722 * b) / 0.7152;
    return clamp(vec3(r, g, b), 0.0, 1.0);
}

float rcasLuma(vec3 c) {
    return c.b * 0.5 + (c.r * 0.5 + c.g);
}

void main() {
    vec3 c = reconstructRgb(v_uv);
    float sharp = clamp(u_sharp, 0.0, 1.0);

    if (sharp <= 0.001) {
        o_color = vec4(c, 1.0);
        return;
    }

    // AMD FSR RCAS contrast-adaptive sharpening at target resolution
    vec2 px = 1.0 / max(u_outSize, vec2(1.0));
    vec3 b = reconstructRgb(v_uv + vec2(0.0, -px.y));
    vec3 d = reconstructRgb(v_uv + vec2(-px.x, 0.0));
    vec3 f = reconstructRgb(v_uv + vec2( px.x, 0.0));
    vec3 h = reconstructRgb(v_uv + vec2(0.0,  px.y));

    float bL = rcasLuma(b);
    float dL = rcasLuma(d);
    float eL = rcasLuma(c);
    float fL = rcasLuma(f);
    float hL = rcasLuma(h);

    float mn4 = min(min(bL, dL), min(fL, hL));
    float mx4 = max(max(bL, dL), max(fL, hL));
    float mn = min(mn4, eL);
    float mx = max(mx4, eL);

    float nz = 0.25 * (bL + dL + fL + hL) - eL;
    float range = mx - mn;
    float amp = clamp(abs(nz) / max(range, 0.001), 0.0, 1.0);
    float peak = -1.0 / max(amp * 3.0 + 5.0, 0.001);

    float w = clamp(peak * sharp * (1.35 - 0.35 * sharp), -0.22, 0.0);
    vec3 res = (b + d + f + h) * w + c;
    res = res / (1.0 + 4.0 * w);

    // Anti-ringing clamp
    vec3 mnRgb = min(min(b, d), min(f, h));
    vec3 mxRgb = max(max(b, d), max(f, h));
    res = clamp(res, mnRgb, mxRgb);

    o_color = vec4(clamp(res, 0.0, 1.0), 1.0);
}
