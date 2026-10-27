#version 300 es
precision highp float;
precision highp int;
precision highp sampler2D;
// Final reconstruction stage for AI Neural Super-Resolution (ArtCNN C4F32_DS & FSRCNNX).
// PixelShuffles sub-pixel luma (2x) -> Catmull-Rom resamples to target resolution ->
// recombines with bicubic source chroma -> encodes Sobel edge response in alpha for post-processors.
uniform sampler2D SUBCONV1;   // RGBA16F, subpixel luma tensor at source resolution
uniform sampler2D u_raw;      // source RGB
uniform ivec2 u_size;         // neural grid size
uniform ivec2 u_rawSize;      // source RGB size
uniform int u_isArtCnn;       // 1 for ArtCNN (row-major), 0 for FSRCNNX (col-major)
uniform float u_nnWeight;     // AI detail strength

in vec2 v_uv;
out vec4 o_color;

float lumaNN(ivec2 q) {
    q = clamp(q, ivec2(0), u_size * 2 - 1);
    vec4 v = texelFetch(SUBCONV1, q >> 1, 0);
    int i = q.x & 1;                          // image column parity (0: left, 1: right)
    int j = 1 - (q.y & 1);                    // image row parity (0: top, 1: bottom; FBO bottom-up)
    if (u_isArtCnn != 0) {
        return v[j * 2 + i];                  // ArtCNN row-major: row * 2 + col
    } else {
        return v[i * 2 + j];                  // FSRCNNX col-major: col * 2 + row
    }
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
    ivec2 rawSz = (u_rawSize.x > 0) ? u_rawSize : u_size;
    vec2 p = v_uv * vec2(rawSz) - 0.5;
    ivec2 b = ivec2(floor(p));
    vec2 f = p - floor(p);
    vec4 wx = cr(f.x), wy = cr(f.y);
    vec3 rgb = vec3(0.0);
    for (int j = 0; j < 4; j++)
        for (int i = 0; i < 4; i++)
            rgb += texelFetch(u_raw, clamp(b + ivec2(i - 1, j - 1), ivec2(0), rawSz - 1), 0).rgb * wx[i] * wy[j];

    // Blend neural reconstruction with adjustable AI weight (0.0 = pure bicubic, 1.0 = standard neural, 1.5 = enhanced)
    rgb += (yNN - luma(rgb)) * (0.5 + u_nnWeight);
    rgb = clamp(rgb, 0.0, 1.0);

    // Compute Sobel magnitude for downstream Anime4K compatibility
    float l00 = dot(texelFetch(u_raw, clamp(b + ivec2(-1, -1), ivec2(0), rawSz - 1), 0).rgb, vec3(0.299, 0.587, 0.114));
    float l20 = dot(texelFetch(u_raw, clamp(b + ivec2( 1, -1), ivec2(0), rawSz - 1), 0).rgb, vec3(0.299, 0.587, 0.114));
    float l02 = dot(texelFetch(u_raw, clamp(b + ivec2(-1,  1), ivec2(0), rawSz - 1), 0).rgb, vec3(0.299, 0.587, 0.114));
    float l22 = dot(texelFetch(u_raw, clamp(b + ivec2( 1,  1), ivec2(0), rawSz - 1), 0).rgb, vec3(0.299, 0.587, 0.114));
    float l01 = dot(texelFetch(u_raw, clamp(b + ivec2(-1,  0), ivec2(0), rawSz - 1), 0).rgb, vec3(0.299, 0.587, 0.114));
    float l21 = dot(texelFetch(u_raw, clamp(b + ivec2( 1,  0), ivec2(0), rawSz - 1), 0).rgb, vec3(0.299, 0.587, 0.114));
    float l10 = dot(texelFetch(u_raw, clamp(b + ivec2( 0, -1), ivec2(0), rawSz - 1), 0).rgb, vec3(0.299, 0.587, 0.114));
    float l12 = dot(texelFetch(u_raw, clamp(b + ivec2( 0,  1), ivec2(0), rawSz - 1), 0).rgb, vec3(0.299, 0.587, 0.114));
    float gx = (l20 - l00) + 2.0 * (l21 - l01) + (l22 - l02);
    float gy = (l02 - l00) + 2.0 * (l12 - l10) + (l22 - l20);
    float edgeNorm = sqrt(gx * gx + gy * gy);
    float sobelAlpha = clamp(pow(edgeNorm * 2.0, 0.7), 0.0, 1.0);

    o_color = vec4(rgb, sobelAlpha);
}
