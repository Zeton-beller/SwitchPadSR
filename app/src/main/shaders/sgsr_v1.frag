#version 450 core
precision highp float;

layout(location = 0) in vec2 v_texCoord;
layout(location = 0) out vec4 out_Color;

layout(set = 0, binding = 0) uniform sampler2D u_InputTexture;

layout(push_constant) uniform SGSRParams {
    vec2 inputSize;
    vec2 outputSize;
    float edgeThreshold;
    float edgeSharpness;
    int useEdgeDirection;
    int reserved;
} params;

// Fast Lanczos2 polynomial approximation
float fastLanczos2(float x) {
    float wA = x - 4.0;
    float wB = x * wA - wA;
    wA *= wA;
    return wB * wA;
}

vec2 edgeDirection(vec4 left, vec4 right) {
    vec2 delta;
    delta.x = (right.x - left.z) + (right.w - left.y);
    delta.y = (right.x - left.z) - (right.w - left.y);
    return delta * inversesqrt(dot(delta, delta) + 3.075740e-05);
}

vec2 weightY(float dx, float dy, float c, vec3 data, int useDir) {
    float x;
    if (useDir == 1) {
        float stdDev = data.x;
        vec2 dir = data.yz;
        float edgeDis = ((dx * dir.y) + (dy * dir.x));
        x = fma(edgeDis * edgeDis, (clamp(c * c * stdDev, 0.0, 1.0) * 0.7 - 1.0), (dx * dx + dy * dy));
    } else {
        float stdDev = data.x;
        x = fma((dx * dx + dy * dy), 0.55, clamp(abs(c) * stdDev, 0.0, 1.0));
    }
    float w = fastLanczos2(x);
    return vec2(w, w * c);
}

// Convert RGB to BT.709 Luma
float getLuma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec2 pt = 1.0 / params.inputSize;
    vec4 baseColor = texture(u_InputTexture, v_texCoord);
    float lumaCenter = getLuma(baseColor.rgb);

    vec2 imgCoord = (v_texCoord * params.inputSize) + vec2(-0.5, 0.5);
    vec2 imgCoordPixel = floor(imgCoord);
    vec2 coord = imgCoordPixel * pt;
    vec2 pl = imgCoord - imgCoordPixel;

    vec4 left = textureGather(u_InputTexture, coord, 0); // Red / Luma channel
    float edgeVote = abs(left.z - left.y) + abs(lumaCenter - left.y) + abs(lumaCenter - left.z);

    if (edgeVote > (params.edgeThreshold / 255.0)) {
        vec4 right = textureGather(u_InputTexture, coord + vec2(pt.x * 2.0, 0.0), 0);
        vec4 upDown;
        upDown.xy = textureGather(u_InputTexture, coord + vec2(0.0, -pt.y), 0).wz;
        upDown.zw = textureGather(u_InputTexture, coord + vec2(0.0, pt.y), 0).yx;

        float mean = (left.y + left.z + right.x + right.w) * 0.25;
        vec4 leftM = left - vec4(mean);
        vec4 rightM = right - vec4(mean);
        vec4 upDownM = upDown - vec4(mean);

        float sum = dot(abs(leftM) + abs(rightM) + abs(upDownM), vec4(1.0));
        float sumMean = 10.14185 / max(sum, 1e-5);
        float stdVal = clamp(sum * 0.035, 0.0, 1.0);

        vec2 dir = vec2(0.0, 1.0);
        if (params.useEdgeDirection == 1) {
            dir = edgeDirection(left, right);
        }
        vec3 data = vec3(stdVal * params.edgeSharpness, dir);

        // 12-tap Lanczos-like directional blending
        vec2 w0 = weightY(pl.x, pl.y, left.w - mean, data, params.useEdgeDirection);
        vec2 w1 = weightY(1.0 - pl.x, pl.y, right.x - mean, data, params.useEdgeDirection);
        vec2 w2 = weightY(pl.x, 1.0 - pl.y, left.z - mean, data, params.useEdgeDirection);
        vec2 w3 = weightY(1.0 - pl.x, 1.0 - pl.y, right.y - mean, data, params.useEdgeDirection);

        float totalWeight = w0.x + w1.x + w2.x + w3.x;
        float reconLuma = (w0.y + w1.y + w2.y + w3.y) / max(totalWeight, 1e-4) + mean;

        // Apply enhanced high-frequency detail back into RGB
        float diff = reconLuma - lumaCenter;
        vec3 finalRgb = clamp(baseColor.rgb + vec3(diff), 0.0, 1.0);
        out_Color = vec4(finalRgb, baseColor.a);
    } else {
        out_Color = baseColor;
    }
}
