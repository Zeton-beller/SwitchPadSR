#version 450 core
precision highp float;

layout(location = 0) in vec2 v_texCoord;
layout(location = 0) out vec4 out_Color;

layout(set = 0, binding = 0) uniform sampler2D u_InputTexture;

layout(push_constant) uniform RCASParams {
    vec2 outputSize;
    float sharpness; // 0.0 to 1.0 (0.0 is maximum sharpness, 1.0 is soft)
    float reserved;
} params;

float rcasLuma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec2 pt = 1.0 / params.outputSize;
    vec3 c = texture(u_InputTexture, v_texCoord).rgb;
    vec3 n = texture(u_InputTexture, v_texCoord + vec2(0.0, -pt.y)).rgb;
    vec3 s = texture(u_InputTexture, v_texCoord + vec2(0.0, pt.y)).rgb;
    vec3 w = texture(u_InputTexture, v_texCoord + vec2(-pt.x, 0.0)).rgb;
    vec3 e = texture(u_InputTexture, v_texCoord + vec2(pt.x, 0.0)).rgb;

    float lc = rcasLuma(c);
    float ln = rcasLuma(n);
    float ls = rcasLuma(s);
    float lw = rcasLuma(w);
    float le = rcasLuma(e);

    float minL = min(min(min(ln, ls), min(lw, le)), lc);
    float maxL = max(max(max(ln, ls), max(lw, le)), lc);

    // Dynamic contrast-adaptive weight
    float contrast = maxL - minL;
    float peak = min(minL, 2.0 - maxL) / max(maxL, 1e-4);
    float lobe = -0.18 * mix(peak, 1.0, clamp(params.sharpness, 0.0, 1.0));

    vec3 sharpened = (c + (n + s + w + e) * lobe) / (1.0 + 4.0 * lobe);
    out_Color = vec4(clamp(sharpened, 0.0, 1.0), 1.0);
}
