#version 450 core
precision highp float;

layout(location = 0) in vec2 v_texCoord;
layout(location = 0) out vec4 out_Color;

layout(set = 0, binding = 0) uniform sampler2D u_InputTexture;

layout(push_constant) uniform CRTParams {
    vec2 outputSize;
    float scanlineWeight; // 0.0 to 1.0 (default 0.3)
    float curvature;      // 0.0 to 0.3 (default 0.05)
    float maskIntensity;  // 0.0 to 1.0 (default 0.25)
    float reserved;
} params;

// Barrel distortion to mimic curved glass of CRT monitors
vec2 distortCoordinates(vec2 uv, float k) {
    if (k <= 0.001) return uv;
    vec2 cc = uv - 0.5;
    float dist = dot(cc, cc);
    return uv + cc * (dist * k);
}

void main() {
    vec2 uv = distortCoordinates(v_texCoord, params.curvature);

    // Dark border outside CRT curve
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        out_Color = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec3 color = texture(u_InputTexture, uv).rgb;

    // Scanlines (horizontal beam simulation)
    float scanline = sin(uv.y * params.outputSize.y * 3.14159265);
    scanline = mix(1.0, 0.5 + 0.5 * scanline * scanline, params.scanlineWeight);
    color *= scanline;

    // Aperture grille RGB phosphor mask
    float pixelX = floor(uv.x * params.outputSize.x);
    int maskIdx = int(mod(pixelX, 3.0));
    vec3 mask = vec3(1.0);
    if (maskIdx == 0) mask = vec3(1.0, 0.7, 0.7);
    else if (maskIdx == 1) mask = vec3(0.7, 1.0, 0.7);
    else mask = vec3(0.7, 0.7, 1.0);

    color = mix(color, color * mask, params.maskIntensity);

    // Subtle phosphor bloom/boost
    color = pow(color, vec3(1.0 / 1.1)) * 1.05;

    out_Color = vec4(clamp(color, 0.0, 1.0), 1.0);
}
