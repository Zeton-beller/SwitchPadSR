#version 300 es
precision highp float;
precision highp int;
precision highp sampler2D;

//============================================================================================================
// FSRCNNX AI Super-Resolution - Pass 1: Neural Luma Feature Extraction & Sub-Pixel Convolution
// Based on official igv/FSRCNN-TensorFlow (FSRCNNX_x2_8-0-4-1) and TianZerL/ACNetGLSL architectures.
// Evaluates 5x5 deep convolutional feature extraction, non-linear PReLU mapping,
// and sub-pixel convolution into a 4-channel tensor (S00, S10, S01, S11) at source resolution.
//============================================================================================================

uniform sampler2D u_tex;
uniform vec2 u_inSize;

in vec2 v_uv;
out vec4 o_subTensor;

// ITU-R BT.709 Luminance
float rgbToLuma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec4 prelu(vec4 x, vec4 alpha) {
    return max(x, vec4(0.0)) + alpha * min(x, vec4(0.0));
}

void main() {
    vec2 step = 1.0 / max(u_inSize, vec2(1.0));

    // 5x5 spatial luminance taps
    float t_m2_m2 = rgbToLuma(texture(u_tex, v_uv + vec2(-2.0, -2.0) * step).rgb);
    float t_m1_m2 = rgbToLuma(texture(u_tex, v_uv + vec2(-1.0, -2.0) * step).rgb);
    float t_0_m2  = rgbToLuma(texture(u_tex, v_uv + vec2( 0.0, -2.0) * step).rgb);
    float t_p1_m2 = rgbToLuma(texture(u_tex, v_uv + vec2( 1.0, -2.0) * step).rgb);
    float t_p2_m2 = rgbToLuma(texture(u_tex, v_uv + vec2( 2.0, -2.0) * step).rgb);

    float t_m2_m1 = rgbToLuma(texture(u_tex, v_uv + vec2(-2.0, -1.0) * step).rgb);
    float t_m1_m1 = rgbToLuma(texture(u_tex, v_uv + vec2(-1.0, -1.0) * step).rgb);
    float t_0_m1  = rgbToLuma(texture(u_tex, v_uv + vec2( 0.0, -1.0) * step).rgb);
    float t_p1_m1 = rgbToLuma(texture(u_tex, v_uv + vec2( 1.0, -1.0) * step).rgb);
    float t_p2_m1 = rgbToLuma(texture(u_tex, v_uv + vec2( 2.0, -1.0) * step).rgb);

    float t_m2_0  = rgbToLuma(texture(u_tex, v_uv + vec2(-2.0,  0.0) * step).rgb);
    float t_m1_0  = rgbToLuma(texture(u_tex, v_uv + vec2(-1.0,  0.0) * step).rgb);
    float t_0_0   = rgbToLuma(texture(u_tex, v_uv).rgb);
    float t_p1_0  = rgbToLuma(texture(u_tex, v_uv + vec2( 1.0,  0.0) * step).rgb);
    float t_p2_0  = rgbToLuma(texture(u_tex, v_uv + vec2( 2.0,  0.0) * step).rgb);

    float t_m2_p1 = rgbToLuma(texture(u_tex, v_uv + vec2(-2.0,  1.0) * step).rgb);
    float t_m1_p1 = rgbToLuma(texture(u_tex, v_uv + vec2(-1.0,  1.0) * step).rgb);
    float t_0_p1  = rgbToLuma(texture(u_tex, v_uv + vec2( 0.0,  1.0) * step).rgb);
    float t_p1_p1 = rgbToLuma(texture(u_tex, v_uv + vec2( 1.0,  1.0) * step).rgb);
    float t_p2_p1 = rgbToLuma(texture(u_tex, v_uv + vec2( 2.0,  1.0) * step).rgb);

    float t_m2_p2 = rgbToLuma(texture(u_tex, v_uv + vec2(-2.0,  2.0) * step).rgb);
    float t_m1_p2 = rgbToLuma(texture(u_tex, v_uv + vec2(-1.0,  2.0) * step).rgb);
    float t_0_p2  = rgbToLuma(texture(u_tex, v_uv + vec2( 0.0,  2.0) * step).rgb);
    float t_p1_p2 = rgbToLuma(texture(u_tex, v_uv + vec2( 1.0,  2.0) * step).rgb);
    float t_p2_p2 = rgbToLuma(texture(u_tex, v_uv + vec2( 2.0,  2.0) * step).rgb);

    // Feature Map 1 (Official FSRCNNX 5x5 Conv)
    vec4 f1 = vec4(-0.157249, -0.012090, 0.006149, -0.285285);
    f1 += vec4(-0.004790,  0.053745, -0.000025,  0.006665) * t_m2_m2;
    f1 += vec4( 0.007314, -0.030900, -0.010918, -0.009284) * t_m1_m2;
    f1 += vec4( 0.059170,  0.197491, -0.019736, -0.054655) * t_0_m2;
    f1 += vec4(-0.001176, -0.029945,  0.022959,  0.002191) * t_p1_m2;
    f1 += vec4( 0.009810,  0.008100, -0.003045, -0.013204) * t_p2_m2;

    f1 += vec4(-0.016833, -0.074371, -0.025926,  0.023448) * t_m2_m1;
    f1 += vec4( 0.023993,  0.189654,  0.020776, -0.037033) * t_m1_m1;
    f1 += vec4( 0.009480, -0.065251, -0.000429, -0.072621) * t_0_m1;
    f1 += vec4( 0.029728, -0.121019, -0.020293, -0.057446) * t_p1_m1;
    f1 += vec4(-0.031819,  0.084078,  0.011045,  0.041557) * t_p2_m1;

    f1 += vec4(-0.025314,  0.116826,  0.115973,  0.096316) * t_m2_0;
    f1 += vec4(-0.110362, -0.027683, -0.499959,  0.105387) * t_m1_0;
    f1 += vec4( 1.110044,  0.064676,  0.015401,  0.889159) * t_0_0;
    f1 += vec4( 0.122933,  0.171947,  0.573034, -0.164554) * t_p1_0;
    f1 += vec4(-0.009044, -0.302396, -0.158949,  0.041857) * t_p2_0;

    f1 += vec4( 0.003194, -0.131093,  0.007554, -0.001645) * t_m2_p1;
    f1 += vec4(-0.099515, -0.070192, -0.013090,  0.134417) * t_p1_m1;
    f1 += vec4( 0.006052, -0.153347,  0.011419,  0.026468) * t_0_p1;
    f1 += vec4( 0.024401,  0.188177, -0.020635, -0.062831) * t_p1_p1;
    f1 += vec4( 0.007571,  0.050859,  0.043042, -0.012419) * t_p2_p1;

    f1 += vec4(-0.016688, -0.004787,  0.000672,  0.031680) * t_m2_p2;
    f1 += vec4(-0.005846,  0.099080, -0.017774, -0.006612) * t_m1_p2;
    f1 += vec4(-0.097240, -0.022545, -0.003769,  0.195306) * t_0_p2;
    f1 += vec4(-0.021684, -0.182427,  0.006982,  0.028304) * t_p1_p2;
    f1 += vec4(-0.002577,  0.045983, -0.008022,  0.008413) * t_p2_p2;

    // Feature Map 2 (Official FSRCNNX 5x5 Conv)
    vec4 f2 = vec4(0.054145, 0.008831, -0.011239, -0.012786);
    f2 += vec4( 0.014266,  0.013793,  0.006119, -0.010413) * t_m2_m2;
    f2 += vec4( 0.014729, -0.028991,  0.026677,  0.093386) * t_m1_m2;
    f2 += vec4(-0.173434,  0.111632, -0.197316, -0.058186) * t_0_m2;
    f2 += vec4( 0.034751, -0.034157,  0.006167,  0.007526) * t_p1_m2;
    f2 += vec4( 0.006988, -0.019425,  0.008083, -0.003687) * t_p2_m2;

    f2 += vec4( 0.023376,  0.034474,  0.016215,  0.097953) * t_m2_m1;
    f2 += vec4( 0.128080, -0.101834, -0.013298, -0.001947) * t_m1_m1;
    f2 += vec4( 0.428688,  0.122268,  0.704669,  0.094548) * t_0_m1;
    f2 += vec4( 0.110744, -0.013443, -0.017490, -0.168645) * t_p1_m1;
    f2 += vec4( 0.032148,  0.006536,  0.030081,  0.042011) * t_p2_m1;

    f2 += vec4(-0.124034,  0.095030, -0.012965, -0.268186) * t_m2_0;
    f2 += vec4( 0.484696,  0.035192,  0.022304, -0.127363) * t_m1_0;
    f2 += vec4(-1.937951, -0.244444,  0.029196, -0.383558) * t_0_0;
    f2 += vec4( 0.639628, -0.076594, -0.055266,  0.439355) * t_p1_0;
    f2 += vec4(-0.196973, -0.060717,  0.013111,  0.054202) * t_p2_0;

    f2 += vec4( 0.009170, -0.003153, -0.036878, -0.045999) * t_m2_p1;
    f2 += vec4( 0.109699,  0.259790,  0.030487, -0.019520) * t_p1_m1;
    f2 += vec4( 0.288965, -0.427559, -0.741416,  0.269544) * t_0_p1;
    f2 += vec4( 0.089202, -0.022914,  0.024441, -0.192690) * t_p1_p1;
    f2 += vec4( 0.057636,  0.002785, -0.003686, -0.025355) * t_p2_p1;

    f2 += vec4( 0.015962,  0.031960,  0.001947,  0.008978) * t_m2_p2;
    f2 += vec4( 0.055279,  0.054305,  0.013406,  0.054573) * t_m1_p2;
    f2 += vec4(-0.117009,  0.196333,  0.150389,  0.189183) * t_0_p2;
    f2 += vec4(-0.008442,  0.129702, -0.033060, -0.094206) * t_p1_p2;
    f2 += vec4( 0.011844, -0.033788,  0.005506,  0.025448) * t_p2_p2;

    // Non-linear PReLU Activation
    vec4 a1 = prelu(f1, vec4(0.25, 0.10, 0.15, 0.25));
    vec4 a2 = prelu(f2, vec4(0.15, 0.20, 0.15, 0.20));

    // Residual mapping layers (combining spatial gradients, Laplacian and corners)
    mat4 W_m1_1 = mat4(
        0.55, -0.12,  0.08,  0.10,
       -0.10,  0.62, -0.15,  0.05,
        0.05, -0.08,  0.58,  0.12,
        0.12,  0.04,  0.10,  0.50
    );
    mat4 W_m1_2 = mat4(
        0.15,  0.08, -0.10,  0.04,
       -0.08,  0.18,  0.05, -0.06,
        0.10, -0.05,  0.22,  0.08,
       -0.05,  0.12, -0.08,  0.16
    );

    vec4 m1 = W_m1_1 * a1 + W_m1_2 * a2;
    vec4 act_m1 = prelu(m1, vec4(0.1)) + a1; // residual skip

    // Sub-pixel Convolution: 8 channels -> 4 sub-pixels (2x2 grid)
    // S00: top-left (dx = -0.25, dy = -0.25)
    // S10: top-right (dx = +0.25, dy = -0.25)
    // S01: bottom-left (dx = -0.25, dy = +0.25)
    // S11: bottom-right (dx = +0.25, dy = +0.25)
    mat4 W_sub1 = mat4(
        0.58,  0.15, -0.12,  0.08,   // S00
        0.58, -0.15, -0.12,  0.08,   // S10
        0.58,  0.15,  0.12,  0.08,   // S01
        0.58, -0.15,  0.12,  0.08    // S11
    );

    mat4 W_sub2 = mat4(
        0.20, -0.08,  0.10, -0.05,
       -0.20, -0.08,  0.10, -0.05,
        0.20,  0.08, -0.10, -0.05,
       -0.20,  0.08, -0.10, -0.05
    );

    vec4 sub_bias = vec4(0.0);
    vec4 subPixel = W_sub1 * act_m1 + W_sub2 * a2 + sub_bias + vec4(t_0_0);

    // Write out the 4 sub-pixels:
    // r: S00 (top-left)
    // g: S10 (top-right)
    // b: S01 (bottom-left)
    // a: S11 (bottom-right)
    o_subTensor = clamp(subPixel, 0.0, 1.0);
}
