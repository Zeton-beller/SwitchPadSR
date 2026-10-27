#version 300 es
precision highp float;
precision highp int;
precision highp sampler2D;
// FSRCNNX final stage. Reproduces the official "aggregation" hook (PixelShuffle of SUBCONV1 -> 2x luma),
// then resamples that 2x luma to ANY target size with Catmull-Rom (so 1080p -> 3.2K has no integer-scale
// constraint) and recombines it with the bicubic chroma of the source frame:  rgb = bicubic(rgb) + (Y_nn - Y_bicubic).
// FBOs are bottom-up, mpv is top-down: the sub-row index is therefore mirrored (see tools/gen_fsrcnnx.py).
uniform sampler2D SUBCONV1;   // RGBA16F, source resolution
uniform sampler2D u_raw;      // source RGB
uniform ivec2 u_size;         // source size

in vec2 v_uv;
out vec4 o_color;

float lumaNN(ivec2 q) {                       // q in 2x grid, storage orientation
    q = clamp(q, ivec2(0), u_size * 2 - 1);
    vec4 v = texelFetch(SUBCONV1, q >> 1, 0);
    int i = q.x & 1;                          // image column parity
    int j = 1 - (q.y & 1);                    // image row parity (mirrored)
    return v[i * 2 + j];
}

vec4 cr(float t) {
    float t2 = t * t, t3 = t2 * t;
    return vec4(-0.5 * t3 + t2 - 0.5 * t, 1.5 * t3 - 2.5 * t2 + 1.0, -1.5 * t3 + 2.0 * t2 + 0.5 * t, 0.5 * t3 - 0.5 * t2);
}

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

void main() {
    vec2 sz = vec2(u_size);
    // Neural luma, Catmull-Rom over the 2x grid
    vec2 p2 = v_uv * sz * 2.0 - 0.5;
    ivec2 b2 = ivec2(floor(p2));
    vec2 f2 = p2 - floor(p2);
    vec4 wx2 = cr(f2.x), wy2 = cr(f2.y);
    float yNN = 0.0;
    for (int j = 0; j < 4; j++)
        for (int i = 0; i < 4; i++)
            yNN += lumaNN(b2 + ivec2(i - 1, j - 1)) * wx2[i] * wy2[j];

    // Bicubic RGB of the source (chroma carrier) at the same position
    vec2 p = v_uv * sz - 0.5;
    ivec2 b = ivec2(floor(p));
    vec2 f = p - floor(p);
    vec4 wx = cr(f.x), wy = cr(f.y);
    vec3 rgb = vec3(0.0);
    for (int j = 0; j < 4; j++)
        for (int i = 0; i < 4; i++)
            rgb += texelFetch(u_raw, clamp(b + ivec2(i - 1, j - 1), ivec2(0), u_size - 1), 0).rgb * wx[i] * wy[j];

    rgb += yNN - luma(rgb);
    o_color = vec4(clamp(rgb, 0.0, 1.0), 1.0);
}
