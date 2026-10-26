#version 300 es
precision highp float;
precision highp int;
precision highp sampler2D;
//============================================================================================================
// Official AMD FidelityFX Super Resolution 1.0 (FSR 1.0) - RCAS Pass (FsrRcasF)
// Ported from GPUOpen-Effects/FidelityFX-FSR (ffx_fsr1.h lines 710-769, MIT License),
// with source-scale + target-scale cross sampling so sharpening remains effective at any upscale factor.
//============================================================================================================

uniform sampler2D u_tex;
uniform vec2  u_inSize;     // output size of EASU FBO
uniform vec2  u_srcSize;    // original input video/capture size
uniform float u_sharp;      // 0..1 (0 = off, 1 = maximum sharpness)
uniform int   u_dering;     // 1 = strict anti-ringing, 0 = high-acutance micro-contrast
in vec2 v_uv;
out vec4 o_color;

float rcasLuma2(vec3 c) {
    // Official ffx_fsr1.h luma*2: b*0.5 + (r*0.5 + g)
    return c.b * 0.5 + (c.r * 0.5 + c.g);
}

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

    // 5-tap cross at source-texel scale & target-texel scale
    vec3 bS = texture(u_tex, v_uv + vec2(0.0, -pxSrc.y)).rgb;
    vec3 dS = texture(u_tex, v_uv + vec2(-pxSrc.x, 0.0)).rgb;
    vec3 fS = texture(u_tex, v_uv + vec2( pxSrc.x, 0.0)).rgb;
    vec3 hS = texture(u_tex, v_uv + vec2(0.0,  pxSrc.y)).rgb;

    vec3 bF = texture(u_tex, v_uv + vec2(0.0, -pxFine.y)).rgb;
    vec3 dF = texture(u_tex, v_uv + vec2(-pxFine.x, 0.0)).rgb;
    vec3 fF = texture(u_tex, v_uv + vec2( pxFine.x, 0.0)).rgb;
    vec3 hF = texture(u_tex, v_uv + vec2(0.0,  pxFine.y)).rgb;

    vec3 b = mix(bF, bS, 0.65);
    vec3 d = mix(dF, dS, 0.65);
    vec3 f = mix(fF, fS, 0.65);
    vec3 h = mix(hF, hS, 0.65);

    // Official FsrRcasF luma*2 & noise detection (ffx_fsr1.h lines 731-739)
    float bL = rcasLuma2(b);
    float dL = rcasLuma2(d);
    float eL = rcasLuma2(e);
    float fL = rcasLuma2(f);
    float hL = rcasLuma2(h);

    float nz = 0.25 * (bL + dL + fL + hL) - eL;
    float rangeL = max(max(max(bL, dL), max(eL, fL)), hL) - min(min(min(bL, dL), min(eL, fL)), hL);
    nz = clamp(abs(nz) / max(rangeL, 1e-4), 0.0, 1.0);
    nz = -0.5 * nz + 1.0;

    // Official FsrRcasF min/max ring and peak limiters (ffx_fsr1.h lines 741-759)
    vec3 mn4 = min(min(b, d), min(f, h));
    vec3 mx4 = max(max(b, d), max(f, h));
    vec3 hitMin = min(mn4, e) / max(4.0 * mx4, vec3(1e-4));
    vec3 hitMax = (1.0 - max(mx4, e)) / min(4.0 * mn4 - 4.0, vec3(-1e-4));
    vec3 lobeRGB = max(-hitMin, hitMax);

    // Luma-domain limiter prevents a single saturated/clipped RGB channel from killing edge sharpening
    float mn4L = min(min(min(bL, dL), min(fL, hL)), eL) * 0.5;
    float mx4L = max(max(max(bL, dL), max(fL, hL)), eL) * 0.5;
    float hitMinL = mn4L / max(4.0 * mx4L, 1e-4);
    float hitMaxL = (1.0 - mx4L) / min(4.0 * mn4L - 4.0, -1e-4);
    float lobeL = max(-hitMinL, hitMaxL);

    float strictLobe = max(max(lobeRGB.r, lobeRGB.g), lobeRGB.b);
    float adaptiveLobe = mix(lobeL, strictLobe, 0.35);

    // Official FSR_RCAS_LIMIT is 0.25 - 1/16 = 0.1875; allow stronger lobe when dering is disabled
    float rcasLimit = (u_dering == 1) ? 0.28 : 0.42;
    float lobe = max(-rcasLimit, min(adaptiveLobe, -0.15));

    float gain = sharp * (1.35 - 0.35 * sharp);
    lobe *= gain * mix(1.0, nz, (u_dering == 1) ? 0.5 : 0.15);

    float rcpL = 1.0 / max(4.0 * lobe + 1.0, 0.18);
    vec3 pix = (lobe * (b + d + h + f) + e) * rcpL;

    if (u_dering == 1) {
        vec3 cMin = min(mn4, e) - vec3(0.03 * gain);
        vec3 cMax = max(mx4, e) + vec3(0.03 * gain);
        pix = clamp(pix, cMin, cMax);
    }

    o_color = vec4(clamp(pix, 0.0, 1.0), 1.0);
}
