#version 300 es
precision highp float;
precision highp sampler2D;
//============================================================================================================
// Official Anime4K v3.2/v4 Algorithm Pipeline by bloc97 (MIT License):
// Combines:
//  1. Anime4K_Thin_HQ.glsl     (Sobel edge-magnitude ridge gradient warp: thins thick lines from BOTH sides)
//  2. Anime4K_Deblur_DoG.glsl  (7-tap Difference-of-Gaussians power-curve deblur clamped to 3x3 min/max)
//  3. Anime4K_Darken_HQ.glsl   (Negative-lobe Gaussian valley ink-line darkening)
//  4. Anime4K_Denoise_Bilateral_Mean.glsl (Mode B cel surface bilateral denoise)
//============================================================================================================

uniform sampler2D u_tex;       // rgb = bicubic upscaled image, a = Pass-1 pow(sobel_norm, 0.7)
uniform vec2  u_inSize;        // target FBO size
uniform vec2  u_srcSize;       // source video size
uniform float u_strength;      // 0.5..2.0
uniform int   u_mode;          // 0: Mode A (Restore/Deblur), 1: Mode B (Restore+Bilateral Denoise), 2: Mode C (Thin+Darken HQ)
in vec2 v_uv;
out vec4 o_color;

float get_luma(vec3 rgb) {
    // Official Anime4K BT.601/BT.709 luma (Anime4K_Deblur_DoG.glsl line 31)
    return dot(vec3(0.299, 0.587, 0.114), rgb);
}

void main() {
    vec2 pt = 1.0 / max(u_inSize, vec2(1.0));
    vec2 scale = max(u_inSize / max(u_srcSize, vec2(1.0)), vec2(1.0));
    vec2 d = clamp(scale * 0.85, vec2(1.0), vec2(4.0)) * pt;
    float s = clamp(u_strength, 0.2, 2.5);

    // -------------------------------------------------------------------------
    // Stage 1: Official Anime4K_Thin_HQ Warp (Anime4K_Thin_HQ.glsl lines 165-221)
    // Reads the Pass-1 Sobel edge magnitude stored in .a and computes its spatial
    // gradient (dn). Shifting pos -= dn pulls both sides of a thick line inward!
    // -------------------------------------------------------------------------
    float e00 = texture(u_tex, v_uv + vec2(-d.x, -d.y)).a;
    float e10 = texture(u_tex, v_uv + vec2( 0.0, -d.y)).a;
    float e20 = texture(u_tex, v_uv + vec2( d.x, -d.y)).a;
    float e01 = texture(u_tex, v_uv + vec2(-d.x,  0.0)).a;
    float e21 = texture(u_tex, v_uv + vec2( d.x,  0.0)).a;
    float e02 = texture(u_tex, v_uv + vec2(-d.x,  d.y)).a;
    float e12 = texture(u_tex, v_uv + vec2( 0.0,  d.y)).a;
    float e22 = texture(u_tex, v_uv + vec2( d.x,  d.y)).a;

    vec2 dn;
    dn.x = ((e20 - e00) + 2.0 * (e21 - e01) + (e22 - e02)) / 8.0;
    dn.y = ((e02 + 2.0 * e12 + e22) - (e00 + 2.0 * e10 + e20)) / 8.0;

    float thinStrength = (u_mode == 2) ? (0.85 * s) : (0.55 * s);
    float relstr = (u_inSize.y / 1080.0) * thinStrength;
    vec2 dd = (dn / (length(dn) + 0.01)) * pt * relstr;
    vec2 pos = v_uv - dd;

    // -------------------------------------------------------------------------
    // Stage 2: Official Anime4K_Deblur_DoG (Anime4K_Deblur_DoG.glsl lines 54-150)
    // Evaluates 2D 7-tap Gaussian and 3x3 morphological [min, max] on warped pos,
    // then applies non-linear power-curve deblur (BLUR_CURVE = 0.6).
    // -------------------------------------------------------------------------
    vec3 centerRgb = texture(u_tex, pos).rgb;
    float lC = get_luma(centerRgb);

    vec3 n[8];
    n[0] = texture(u_tex, pos + vec2(-d.x, -d.y)).rgb;
    n[1] = texture(u_tex, pos + vec2( 0.0, -d.y)).rgb;
    n[2] = texture(u_tex, pos + vec2( d.x, -d.y)).rgb;
    n[3] = texture(u_tex, pos + vec2(-d.x,  0.0)).rgb;
    n[4] = texture(u_tex, pos + vec2( d.x,  0.0)).rgb;
    n[5] = texture(u_tex, pos + vec2(-d.x,  d.y)).rgb;
    n[6] = texture(u_tex, pos + vec2( 0.0,  d.y)).rgb;
    n[7] = texture(u_tex, pos + vec2( d.x,  d.y)).rgb;

    float l0 = get_luma(n[0]), l1 = get_luma(n[1]), l2 = get_luma(n[2]), l3 = get_luma(n[3]);
    float l4 = get_luma(n[4]), l5 = get_luma(n[5]), l6 = get_luma(n[6]), l7 = get_luma(n[7]);

    // 3x3 minmax envelope (minmax3 in Anime4K_Deblur_DoG.glsl)
    float lMin = min(lC, min(min(min(l0, l1), min(l2, l3)), min(min(l4, l5), min(l6, l7))));
    float lMax = max(lC, max(max(max(l0, l1), max(l2, l3)), max(max(l4, l5), max(l6, l7))));

    // Outer 2-step taps for 7-tap Gaussian (lumGaussian7 weights: 0.06136, 0.24477, 0.38774)
    float lL2 = get_luma(texture(u_tex, pos + vec2(-2.0 * d.x, 0.0)).rgb);
    float lR2 = get_luma(texture(u_tex, pos + vec2( 2.0 * d.x, 0.0)).rgb);
    float lU2 = get_luma(texture(u_tex, pos + vec2(0.0, -2.0 * d.y)).rgb);
    float lD2 = get_luma(texture(u_tex, pos + vec2(0.0,  2.0 * d.y)).rgb);

    float gCross = (lL2 + lR2 + lU2 + lD2) * 0.06136
                 + (l1 + l3 + l4 + l6) * 0.24477
                 + (l0 + l2 + l5 + l7) * 0.12238
                 + lC * 0.38774;
    float normG = 4.0 * 0.06136 + 4.0 * 0.24477 + 4.0 * 0.12238 + 0.38774;
    float lumGauss = gCross / normG;

    // Official Anime4K_Deblur_DoG power-curve formula (lines 123-149)
    float deblurStrength = 0.65 * s;
    float blurCurve = clamp(0.75 - 0.18 * s, 0.32, 0.85);
    float blurThreshold = 0.12;
    float noiseThreshold = 0.001;

    float cDiff = (lC - lumGauss) * deblurStrength;
    float tRange = blurThreshold - noiseThreshold;
    float cT = abs(cDiff);
    if (cT > noiseThreshold) {
        cT = (cT - noiseThreshold) / tRange;
        cT = pow(clamp(cT, 0.0, 2.0), blurCurve);
        cT = cT * tRange + noiseThreshold;
        cT = cT * sign(cDiff);
    } else {
        cT = cDiff;
    }

    float pad = (u_mode == 2) ? (0.04 * s) : (0.02 * s);
    float cc = clamp(cT + lC, lMin - pad, lMax + pad) - lC;
    vec3 res = centerRgb + vec3(cc);

    // -------------------------------------------------------------------------
    // Stage 3: Official Anime4K Mode-Specific Pass
    //  Mode 0 (A 还原): Deblur_DoG + Thin_HQ + mild Darken_DoG
    //  Mode 1 (B 还原+降噪): Anime4K_Denoise_Bilateral_Mean on flat cel areas + Deblur_DoG
    //  Mode 2 (C 线条加深): Full Anime4K_Darken_HQ (negative-lobe DoG line darkening)
    // -------------------------------------------------------------------------
    float negDoG = min(lC - lumGauss, 0.0); // Only dark valleys (ink outlines)
    float edgeMask = smoothstep(0.01, 0.12, lMax - lMin);

    if (u_mode == 0) {
        // Mode A: Clean line restore + moderate line darkening
        res += vec3(negDoG * 0.85 * s);
    } else if (u_mode == 1) {
        // Mode B: Official Anime4K_Denoise_Bilateral_Mean on flat cel surfaces + crisp edges
        vec3 biSum = centerRgb * 1.5;
        float biW = 1.5;
        for (int i = 0; i < 8; ++i) {
            vec3 dc = n[i] - centerRgb;
            float w = exp(-dot(dc, dc) * 42.0);
            biSum += n[i] * w;
            biW += w;
        }
        vec3 biClean = biSum / biW;
        res += vec3(negDoG * 0.65 * s);
        res = mix(biClean, res, edgeMask);
        float lOut = get_luma(res);
        res = mix(vec3(lOut), res, 1.0 + 0.06 * s);
    } else {
        // Mode C: Official Anime4K_Darken_HQ (lines 114 & 191-196: negDoG * STRENGTH, default 1.5)
        res += vec3(negDoG * 1.75 * s);
    }

    o_color = vec4(clamp(res, 0.0, 1.0), 1.0);
}
