#version 450 core
precision highp float;

layout(location = 0) in vec2 v_texCoord;
layout(location = 0) out vec4 out_Color;

layout(set = 0, binding = 0) uniform sampler2D u_InputTexture;

layout(push_constant) uniform Anime4KParams {
    vec2 inputSize;
    float strength; // 0.5 to 2.0
    int mode;       // 0: Mode A, 1: Mode B, 2: Mode C
} params;

// Fast luma calculation
float getLuma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// 3x3 Sobel edge detection & line thinning for Anime art styles
void main() {
    vec2 pt = 1.0 / params.inputSize;
    vec3 c = texture(u_InputTexture, v_texCoord).rgb;

    vec3 tl = texture(u_InputTexture, v_texCoord + vec2(-pt.x, -pt.y)).rgb;
    vec3 tc = texture(u_InputTexture, v_texCoord + vec2(0.0, -pt.y)).rgb;
    vec3 tr = texture(u_InputTexture, v_texCoord + vec2(pt.x, -pt.y)).rgb;
    vec3 ml = texture(u_InputTexture, v_texCoord + vec2(-pt.x, 0.0)).rgb;
    vec3 mr = texture(u_InputTexture, v_texCoord + vec2(pt.x, 0.0)).rgb;
    vec3 bl = texture(u_InputTexture, v_texCoord + vec2(-pt.x, pt.y)).rgb;
    vec3 bc = texture(u_InputTexture, v_texCoord + vec2(0.0, pt.y)).rgb;
    vec3 br = texture(u_InputTexture, v_texCoord + vec2(pt.x, pt.y)).rgb;

    float l_tl = getLuma(tl), l_tc = getLuma(tc), l_tr = getLuma(tr);
    float l_ml = getLuma(ml), l_c  = getLuma(c),  l_mr = getLuma(mr);
    float l_bl = getLuma(bl), l_bc = getLuma(bc), l_br = getLuma(br);

    // Sobel gradients
    float gx = (l_tr + 2.0 * l_mr + l_br) - (l_tl + 2.0 * l_ml + l_bl);
    float gy = (l_bl + 2.0 * l_bc + l_br) - (l_tl + 2.0 * l_tc + l_tr);
    float edge = sqrt(gx * gx + gy * gy);

    // Min/Max in 3x3 neighborhood
    vec3 minC = min(min(min(min(min(min(min(min(tl, tc), tr), ml), c), mr), bl), bc), br);
    vec3 maxC = max(max(max(max(max(max(max(max(tl, tc), tr), ml), c), mr), bl), bc), br);

    vec3 result = c;
    if (params.mode == 0) {
        // Mode A: Aggressive line sharpening & anti-ringing
        if (edge > 0.08) {
            vec3 edgeDirectionSample = (gx > gy) ? (mr + ml) * 0.5 : (tc + bc) * 0.5;
            result = mix(c, minC, clamp(edge * params.strength * 1.5, 0.0, 0.8));
            result = clamp(result, minC, maxC);
        }
    } else if (params.mode == 1) {
        // Mode B: Gentle de-ringing and color banding reduction
        vec3 avg = (tl + tc + tr + ml + c + mr + bl + bc + br) / 9.0;
        result = mix(c, avg, clamp((1.0 - edge) * 0.3 * params.strength, 0.0, 0.5));
    } else {
        // Mode C: High-fidelity line preservation
        if (edge > 0.12) {
            result = c + (c - (tc + bc + ml + mr) * 0.25) * params.strength * 0.7;
            result = clamp(result, minC, maxC);
        }
    }

    out_Color = vec4(clamp(result, 0.0, 1.0), 1.0);
}
