#version 300 es
precision highp float;
precision highp int;
precision highp sampler2D;
//============================================================================================================
// Based on Official Qualcomm Snapdragon Game Super Resolution 1 (SGSR v1):
// sgsr1_shader_mobile_edge_direction.frag
// Copyright (c) 2025, Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// Adapted for GLSL ES 3.0 with runtime uniforms (u_threshold, u_sharp, u_useDir).
//============================================================================================================

uniform sampler2D u_tex;
uniform vec2  u_inSize;
uniform float u_threshold;   // 1..16  (in 1/255 units; official default = 8.0)
uniform float u_sharp;       // 1..2   (1.0 = mild, 1.5 = official 2.0x AC gain, 2.0 = ultra sharp)
uniform int   u_useDir;      // 0/1    (UseEdgeDirection)
in vec2 v_uv;
out vec4 o_color;

float lumaG(vec3 c) {
    // Perceptual luma weighted toward green (matches SGSR RGBY/Green operation mode)
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float fetchL(ivec2 p, ivec2 s) {
    return lumaG(texelFetch(u_tex, clamp(p, ivec2(0), s - 1), 0).rgb);
}

// Official Qualcomm SGSR1 fastLanczos2 polynomial
float fastLanczos2(float x) {
    float wA = x - 4.0;
    float wB = x * wA - wA;
    wA *= wA;
    return wB * wA;
}

vec2 weightY(float dx, float dy, float c, vec3 data, int useDir) {
    float std = data.x;
    float x;
    if (useDir == 1) {
        vec2 dir = data.yz;
        float edgeDis = (dx * dir.y) + (dy * dir.x);
        x = (dx * dx + dy * dy) + (edgeDis * edgeDis * ((clamp((c * c) * std, 0.0, 1.0) * 0.7) - 1.0));
    } else {
        x = (dx * dx + dy * dy) * 0.55 + clamp(abs(c) * std, 0.0, 1.0);
    }
    float w = fastLanczos2(x);
    return vec2(w, w * c);
}

vec2 edgeDirection(vec4 left, vec4 right) {
    float RxLz = right.x - left.z;
    float RwLy = right.w - left.y;
    vec2 delta;
    delta.x = RxLz + RwLy;
    delta.y = RxLz - RwLy;
    float lengthInv = inversesqrt(delta.x * delta.x + 3.075740e-05 + delta.y * delta.y);
    return delta * lengthInv;
}

void main() {
    ivec2 s = ivec2(u_inSize);
    vec3 color = textureLod(u_tex, v_uv, 0.0).rgb;
    float colorL = lumaG(color);

    // Official SGSR1 texel coordinate mapping
    vec2 imgCoord = v_uv * u_inSize - vec2(0.5);
    vec2 imgCoordPixel = floor(imgCoord);
    vec2 pl = imgCoord - imgCoordPixel;
    ivec2 ip = ivec2(imgCoordPixel);

    // Equivalent to textureGather(ps0, coord) ordering: .x=(0,1), .y=(1,1), .z=(1,0), .w=(0,0)
    // In SGSR1, `left` is gathered at (imgCoordPixel - (1, 0)) so:
    // left.x = (ip.x-1, ip.y+1), left.y = (ip.x, ip.y+1), left.z = (ip.x, ip.y), left.w = (ip.x-1, ip.y)
    vec4 left = vec4(
        fetchL(ip + ivec2(-1, 1), s),
        fetchL(ip + ivec2( 0, 1), s),
        fetchL(ip + ivec2( 0, 0), s),
        fetchL(ip + ivec2(-1, 0), s)
    );

    float edgeThreshold = u_threshold / 255.0;
    float edgeVote = abs(left.z - left.y) + abs(colorL - left.y) + abs(colorL - left.z);

    if (edgeVote > edgeThreshold) {
        // right gathered at (ip.x+1, ip.y):
        // right.x = (ip.x+1, ip.y+1), right.y = (ip.x+2, ip.y+1), right.z = (ip.x+2, ip.y), right.w = (ip.x+1, ip.y)
        vec4 right = vec4(
            fetchL(ip + ivec2(1, 1), s),
            fetchL(ip + ivec2(2, 1), s),
            fetchL(ip + ivec2(2, 0), s),
            fetchL(ip + ivec2(1, 0), s)
        );
        // upDown: .xy = top row (ip.x, ip.y-1) and (ip.x+1, ip.y-1), .zw = bottom row (ip.x+1, ip.y+2) and (ip.x, ip.y+2)
        vec4 upDown = vec4(
            fetchL(ip + ivec2(0, -1), s),
            fetchL(ip + ivec2(1, -1), s),
            fetchL(ip + ivec2(1,  2), s),
            fetchL(ip + ivec2(0,  2), s)
        );

        float mean = (left.y + left.z + right.x + right.w) * 0.25;
        left -= vec4(mean);
        right -= vec4(mean);
        upDown -= vec4(mean);
        float cW = colorL - mean;

        float sum = abs(left.x) + abs(left.y) + abs(left.z) + abs(left.w)
                  + abs(right.x) + abs(right.y) + abs(right.z) + abs(right.w)
                  + abs(upDown.x) + abs(upDown.y) + abs(upDown.z) + abs(upDown.w);
        float sumMean = 1.014185e+01 / max(sum, 1e-4);
        float std = sumMean * sumMean;

        vec3 data = vec3(std, edgeDirection(left, right));

        vec2 aWY = weightY(pl.x,       pl.y + 1.0, upDown.x, data, u_useDir);
        aWY     += weightY(pl.x - 1.0, pl.y + 1.0, upDown.y, data, u_useDir);
        aWY     += weightY(pl.x - 1.0, pl.y - 2.0, upDown.z, data, u_useDir);
        aWY     += weightY(pl.x,       pl.y - 2.0, upDown.w, data, u_useDir);
        aWY     += weightY(pl.x + 1.0, pl.y - 1.0, left.x,   data, u_useDir);
        aWY     += weightY(pl.x,       pl.y - 1.0, left.y,   data, u_useDir);
        aWY     += weightY(pl.x,       pl.y,       left.z,   data, u_useDir);
        aWY     += weightY(pl.x + 1.0, pl.y,       left.w,   data, u_useDir);
        aWY     += weightY(pl.x - 1.0, pl.y - 1.0, right.x,  data, u_useDir);
        aWY     += weightY(pl.x - 2.0, pl.y - 1.0, right.y,  data, u_useDir);
        aWY     += weightY(pl.x - 2.0, pl.y,       right.z,  data, u_useDir);
        aWY     += weightY(pl.x - 1.0, pl.y,       right.w,  data, u_useDir);

        float finalY = aWY.y / max(aWY.x, 1e-4);
        float maxY = max(max(left.y, left.z), max(right.x, right.w));
        float minY = min(min(left.y, left.z), min(right.x, right.w));

        // Map user slider u_sharp (1.0 .. 2.0) to EdgeSharpness (1.4 .. 4.4, official default = 2.0)
        float k = clamp(u_sharp - 1.0, 0.0, 1.0);
        float edgeSharpness = 1.4 + 3.0 * k;
        float pad = 0.12 * k;
        float deltaY = clamp(edgeSharpness * finalY, minY - pad, maxY + pad) - cW;
        deltaY += cW * (0.20 + 0.60 * k);

        // Official high-contrast smoothing bound scaled by sharpness
        float lim = (28.0 + 72.0 * k) / 255.0;
        deltaY = clamp(deltaY, -lim, lim);

        color = clamp(color + vec3(deltaY), 0.0, 1.0);
    }

    o_color = vec4(color, 1.0);
}
