#!/usr/bin/env python3
"""Converts the official Artoriuz/ArtCNN ArtCNN_C4F32_DS.glsl (MIT License)
into high-performance, low-register-pressure GLES 3.10 UBO-driven compute shaders
and binary weight assets for Android Adreno GPUs.

Generates:
  - app/src/main/assets/shaders/artcnn_p01.comp (1 -> 32 channel compute shader)
  - app/src/main/assets/shaders/artcnn_conv.comp (32 -> 32 shared compute shader for layers 2..6)
  - app/src/main/assets/shaders/artcnn_p07.comp (32 -> 4 channel reconstruction compute shader)
  - app/src/main/assets/shaders/artcnn_w01.bin .. artcnn_w07.bin (binary UBO weight buffers)
  - app/src/main/assets/shaders/artcnn_manifest.txt (execution manifest)
"""

import os, re, struct

src_path = os.path.join(os.path.dirname(__file__), 'ArtCNN_C4F32_DS.glsl')
if not os.path.exists(src_path):
    src_path = '/data/data/com.termux/files/home/ArtCNN_C4F32_DS.glsl'

out_dir = os.path.join(os.path.dirname(__file__), '..', 'app/src/main/assets/shaders')
os.makedirs(out_dir, exist_ok=True)

with open(src_path, 'r') as f:
    text = f.read()

hooks = text.split('//!HOOK')[1:8] # Layers 1..7

# 1. Generate Binary Weights (artcnn_w01.bin .. artcnn_w07.bin)
for i, h in enumerate(hooks, 1):
    lines = h.splitlines()
    biases = []
    for line in lines:
        m = re.match(r'\s*(?:const\s+)?V4\s+result(\d+)\s*=\s*V4\((.*?)\);', line)
        if m:
            r_idx = int(m.group(1))
            vals = [float(x.strip()) for x in m.group(2).split(',')]
            biases.append((r_idx, vals))
    biases.sort(key=lambda x: x[0])

    if i == 1:
        # Layer 1: 8 outputs, 1 input channel, 9 taps
        ops = []
        for line in lines:
            m = re.match(r'\s*result(\d+)\s*\+=\s*V4\((.*?)\)\s*\*\s*inp_0_(\d+)_(\d+);', line)
            if m:
                out_idx = int(m.group(1))
                dx = int(m.group(3))
                dy = int(m.group(4))
                vals = [float(x.strip()) for x in m.group(2).split(',')]
                ops.append((out_idx, dx, dy, vals))
        ops.sort(key=lambda x: (x[0], x[2], x[1])) # out_idx, dy, dx

        bin_data = bytearray()
        for op in ops:
            bin_data.extend(struct.pack('4f', *op[3]))
        for b in biases:
            bin_data.extend(struct.pack('4f', *b[1]))

    elif 2 <= i <= 6:
        # Layers 2..6: 8 outputs, 8 inputs, 9 taps = 576 mat4
        # Ordered by [c][o][dy][dx]
        ops = []
        for line in lines:
            m = re.match(r'\s*result(\d+)\s*\+=\s*M4\((.*?)\)\s*\*\s*inp_(\d+)_(\d+)_(\d+);', line)
            if m:
                out_idx = int(m.group(1))
                vals = [float(x.strip()) for x in m.group(2).split(',')]
                in_idx = int(m.group(3))
                dx = int(m.group(4))
                dy = int(m.group(5))
                ops.append((in_idx, out_idx, dy, dx, vals))
        ops.sort(key=lambda x: (x[0], x[1], x[2], x[3])) # c, o, dy, dx

        bin_data = bytearray()
        for op in ops:
            bin_data.extend(struct.pack('16f', *op[4]))
        for b in biases:
            bin_data.extend(struct.pack('4f', *b[1]))

    elif i == 7:
        # Layer 7: 1 output, 8 inputs, 9 taps = 72 mat4
        ops = []
        for line in lines:
            m = re.match(r'\s*result0\s*\+=\s*M4\((.*?)\)\s*\*\s*inp_(\d+)_(\d+)_(\d+);', line)
            if m:
                vals = [float(x.strip()) for x in m.group(1).split(',')]
                in_idx = int(m.group(2))
                dx = int(m.group(3))
                dy = int(m.group(4))
                ops.append((in_idx, dy, dx, vals))
        ops.sort(key=lambda x: (x[0], x[1], x[2])) # c, dy, dx

        bin_data = bytearray()
        for op in ops:
            bin_data.extend(struct.pack('16f', *op[3]))
        for b in biases:
            bin_data.extend(struct.pack('4f', *b[1]))

    w_path = os.path.join(out_dir, f'artcnn_w{i:02d}.bin')
    with open(w_path, 'wb') as f_out:
        f_out.write(bin_data)
    print(f"Generated {os.path.basename(w_path)}: {len(bin_data)} bytes")

# 2. Write artcnn_p01.comp
p01_src = """#version 310 es
precision mediump float;
precision mediump int;

layout(local_size_x = 12, local_size_y = 16) in;
layout(rgba16f, binding = 0) uniform writeonly mediump image2D out_image;
uniform sampler2D LUMA_raw;

layout(std140, binding = 1) uniform Layer1Weights {
    highp vec4 weights[72];
    highp vec4 biases[8];
};

shared mediump float inp[1][18][14];

void main() {
    ivec2 local_xy = ivec2(gl_LocalInvocationID.xy);
    ivec2 base = ivec2(gl_WorkGroupID.xy) * ivec2(12, 16);
    for (int y = local_xy.y; y < 18; y += 16) {
        for (int x = local_xy.x; x < 14; x += 12) {
            ivec2 input_base = base + ivec2(x, y) - ivec2(1, 1);
            inp[0][y][x] = texelFetch(LUMA_raw, input_base, 0).x;
        }
    }
    barrier();

    int y0 = local_xy.y, y1 = y0 + 1, y2 = y0 + 2;
    int x0 = local_xy.x, x1 = x0 + 1, x2 = x0 + 2;
    mediump float v00 = inp[0][y0][x0], v01 = inp[0][y0][x1], v02 = inp[0][y0][x2];
    mediump float v10 = inp[0][y1][x0], v11 = inp[0][y1][x1], v12 = inp[0][y1][x2];
    mediump float v20 = inp[0][y2][x0], v21 = inp[0][y2][x1], v22 = inp[0][y2][x2];

    ivec2 output_base = ivec2(gl_GlobalInvocationID.xy) * ivec2(4, 2);
    const ivec2 out_offsets[8] = ivec2[8](
        ivec2(0, 0), ivec2(1, 0), ivec2(2, 0), ivec2(3, 0),
        ivec2(0, 1), ivec2(1, 1), ivec2(2, 1), ivec2(3, 1)
    );

    for (int o = 0; o < 8; o++) {
        int wb = o * 9;
        mediump vec4 acc = biases[o];
        acc += weights[wb + 0] * v00 + weights[wb + 1] * v01 + weights[wb + 2] * v02;
        acc += weights[wb + 3] * v10 + weights[wb + 4] * v11 + weights[wb + 5] * v12;
        acc += weights[wb + 6] * v20 + weights[wb + 7] * v21 + weights[wb + 8] * v22;
        imageStore(out_image, output_base + out_offsets[o], acc);
    }
}
"""
with open(os.path.join(out_dir, 'artcnn_p01.comp'), 'w') as f:
    f.write(p01_src)

# 3. Write artcnn_conv.comp (shared for layers 2..6)
conv_src = """#version 310 es
precision mediump float;
precision mediump int;

layout(local_size_x = 12, local_size_y = 16) in;
layout(rgba16f, binding = 0) uniform writeonly mediump image2D out_image;
uniform sampler2D in_tex;

layout(std140, binding = 1) uniform LayerWeights {
    highp mat4 weights[576]; // ordered by [c][o][tap]
    highp vec4 biases[8];
};

shared mediump vec4 inp[8][18][14];

void main() {
    ivec2 local_xy = ivec2(gl_LocalInvocationID.xy);
    ivec2 base = ivec2(gl_WorkGroupID.xy) * ivec2(12, 16);
    for (int y = local_xy.y; y < 18; y += 16) {
        for (int x = local_xy.x; x < 14; x += 12) {
            ivec2 input_base = (base + ivec2(x, y) - ivec2(1, 1)) * ivec2(4, 2);
            for (int c = 0; c < 4; c++) inp[c][y][x] = texelFetch(in_tex, input_base + ivec2(c, 0), 0);
            for (int c = 0; c < 4; c++) inp[c + 4][y][x] = texelFetch(in_tex, input_base + ivec2(c, 1), 0);
        }
    }
    barrier();

    int y0 = local_xy.y, y1 = y0 + 1, y2 = y0 + 2;
    int x0 = local_xy.x, x1 = x0 + 1, x2 = x0 + 2;
    mediump vec4 acc[8];
    for (int o = 0; o < 8; o++) acc[o] = biases[o];

    int w_idx = 0;
    for (int c = 0; c < 8; c++) {
        mediump vec4 v00 = inp[c][y0][x0], v01 = inp[c][y0][x1], v02 = inp[c][y0][x2];
        mediump vec4 v10 = inp[c][y1][x0], v11 = inp[c][y1][x1], v12 = inp[c][y1][x2];
        mediump vec4 v20 = inp[c][y2][x0], v21 = inp[c][y2][x1], v22 = inp[c][y2][x2];
        for (int o = 0; o < 8; o++) {
            acc[o] += weights[w_idx + 0] * v00 + weights[w_idx + 1] * v01 + weights[w_idx + 2] * v02;
            acc[o] += weights[w_idx + 3] * v10 + weights[w_idx + 4] * v11 + weights[w_idx + 5] * v12;
            acc[o] += weights[w_idx + 6] * v20 + weights[w_idx + 7] * v21 + weights[w_idx + 8] * v22;
            w_idx += 9;
        }
    }

    ivec2 output_base = ivec2(gl_GlobalInvocationID.xy) * ivec2(4, 2);
    imageStore(out_image, output_base + ivec2(0, 0), max(acc[0], vec4(0.0)));
    imageStore(out_image, output_base + ivec2(1, 0), max(acc[1], vec4(0.0)));
    imageStore(out_image, output_base + ivec2(2, 0), max(acc[2], vec4(0.0)));
    imageStore(out_image, output_base + ivec2(3, 0), max(acc[3], vec4(0.0)));
    imageStore(out_image, output_base + ivec2(0, 1), max(acc[4], vec4(0.0)));
    imageStore(out_image, output_base + ivec2(1, 1), max(acc[5], vec4(0.0)));
    imageStore(out_image, output_base + ivec2(2, 1), max(acc[6], vec4(0.0)));
    imageStore(out_image, output_base + ivec2(3, 1), max(acc[7], vec4(0.0)));
}
"""
with open(os.path.join(out_dir, 'artcnn_conv.comp'), 'w') as f:
    f.write(conv_src)

# 4. Write artcnn_p07.comp
p07_src = """#version 310 es
precision mediump float;
precision mediump int;

layout(local_size_x = 12, local_size_y = 16) in;
layout(rgba16f, binding = 0) uniform writeonly mediump image2D out_image;
uniform sampler2D conv2d_raw;
uniform sampler2D conv2d_5_raw;

layout(std140, binding = 1) uniform Layer7Weights {
    highp mat4 weights[72]; // for c in 0..7: for tap in 0..8: mat4
    highp vec4 bias;
};

shared mediump vec4 inp[8][18][14];

void main() {
    ivec2 local_xy = ivec2(gl_LocalInvocationID.xy);
    ivec2 base = ivec2(gl_WorkGroupID.xy) * ivec2(12, 16);
    for (int y = local_xy.y; y < 18; y += 16) {
        for (int x = local_xy.x; x < 14; x += 12) {
            ivec2 input_base = (base + ivec2(x, y) - ivec2(1, 1)) * ivec2(4, 2);
            for (int c = 0; c < 4; c++) {
                inp[c][y][x] = texelFetch(conv2d_5_raw, input_base + ivec2(c, 0), 0) +
                               texelFetch(conv2d_raw, input_base + ivec2(c, 0), 0);
            }
            for (int c = 0; c < 4; c++) {
                inp[c + 4][y][x] = texelFetch(conv2d_5_raw, input_base + ivec2(c, 1), 0) +
                                   texelFetch(conv2d_raw, input_base + ivec2(c, 1), 0);
            }
        }
    }
    barrier();

    int y0 = local_xy.y, y1 = y0 + 1, y2 = y0 + 2;
    int x0 = local_xy.x, x1 = x0 + 1, x2 = x0 + 2;
    mediump vec4 acc = bias;

    int w_idx = 0;
    for (int c = 0; c < 8; c++) {
        mediump vec4 v00 = inp[c][y0][x0], v01 = inp[c][y0][x1], v02 = inp[c][y0][x2];
        mediump vec4 v10 = inp[c][y1][x0], v11 = inp[c][y1][x1], v12 = inp[c][y1][x2];
        mediump vec4 v20 = inp[c][y2][x0], v21 = inp[c][y2][x1], v22 = inp[c][y2][x2];
        acc += weights[w_idx + 0] * v00 + weights[w_idx + 1] * v01 + weights[w_idx + 2] * v02;
        acc += weights[w_idx + 3] * v10 + weights[w_idx + 4] * v11 + weights[w_idx + 5] * v12;
        acc += weights[w_idx + 6] * v20 + weights[w_idx + 7] * v21 + weights[w_idx + 8] * v22;
        w_idx += 9;
    }

    ivec2 output_base = ivec2(gl_GlobalInvocationID.xy);
    imageStore(out_image, output_base, acc);
}
"""
with open(os.path.join(out_dir, 'artcnn_p07.comp'), 'w') as f:
    f.write(p07_src)

# 5. Remove obsolete bloated shaders if present
for old_idx in range(2, 7):
    old_p = os.path.join(out_dir, f'artcnn_p{old_idx:02d}.comp')
    if os.path.exists(old_p):
        os.remove(old_p)
        print(f"Removed obsolete bloated shader: {os.path.basename(old_p)}")

# 6. Write manifest
manifest = [
    "artcnn_p01.comp conv2d LUMA 4.0 2.0 artcnn_w01.bin",
    "artcnn_conv.comp conv2d_1 conv2d 4.0 2.0 artcnn_w02.bin",
    "artcnn_conv.comp conv2d_2 conv2d_1 4.0 2.0 artcnn_w03.bin",
    "artcnn_conv.comp conv2d_3 conv2d_2 4.0 2.0 artcnn_w04.bin",
    "artcnn_conv.comp conv2d_4 conv2d_3 4.0 2.0 artcnn_w05.bin",
    "artcnn_conv.comp conv2d_5 conv2d_4 4.0 2.0 artcnn_w06.bin",
    "artcnn_p07.comp conv2d_6 conv2d,conv2d_5 1.0 1.0 artcnn_w07.bin",
]
manifest_path = os.path.join(out_dir, 'artcnn_manifest.txt')
with open(manifest_path, 'w') as f:
    f.write('\n'.join(manifest) + '\n')

print(f"Generated ArtCNN UBO pipeline successfully in {out_dir}:")
print('\n'.join(manifest))
