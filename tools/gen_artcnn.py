#!/usr/bin/env python3
"""Mechanically converts the official Artoriuz/ArtCNN ArtCNN_C4F32_DS.glsl (MIT License)
into GLES 3.10 compute shaders for Android Adreno GPU execution.

Generates 7 compute shader stages (artcnn_p01.comp to artcnn_p07.comp)
and an execution manifest (artcnn_manifest.txt)."""

import os, re

src_path = os.path.join(os.path.dirname(__file__), 'ArtCNN_C4F32_DS.glsl')
if not os.path.exists(src_path):
    src_path = '/data/data/com.termux/files/home/ArtCNN_C4F32_DS.glsl'

out_dir = os.path.join(os.path.dirname(__file__), '..', 'app/src/main/assets/shaders')
os.makedirs(out_dir, exist_ok=True)

with open(src_path, 'r') as f:
    text = f.read()

hooks = text.split('//!HOOK')[1:8] # Layers 1..7
manifest = []

for i, h in enumerate(hooks, 1):
    save_match = re.search(r'//!SAVE (\w+)', h)
    save = save_match.group(1) if save_match else f'layer_{i}'
    binds = re.findall(r'//!BIND (\w+)', h)
    w_match = re.search(r'//!WIDTH LUMA\.w ([\d\.]+) \*', h)
    h_match = re.search(r'//!HEIGHT LUMA\.h ([\d\.]+) \*', h)
    w_scale = float(w_match.group(1)) if w_match else 1.0
    h_scale = float(h_match.group(1)) if h_match else 1.0

    body = h[h.index('void hook()'):]
    body = body.replace('void hook()', 'void main()')
    body = re.sub(r'\bconst (uvec2|ivec2|F|V4)\b', r'\1', body)
    body = re.sub(r'\b(LUMA|conv2d_\d+|conv2d)_mul\b', '1.0', body)
    body = re.sub(r'uvec2 local_xy = gl_LocalInvocationID\.xy;', 'ivec2 local_xy = ivec2(gl_LocalInvocationID.xy);', body)
    body = re.sub(r'uint y = local_xy\.y; y < isize\.y; y \+= wg_size\.y', 'int y = local_xy.y; y < 18; y += 16', body)
    body = re.sub(r'uint x = local_xy\.x; x < isize\.x; x \+= wg_size\.x', 'int x = local_xy.x; x < 14; x += 12', body)
    body = re.sub(r'ivec2\(gl_WorkGroupID\) \* wg_size', 'ivec2(gl_WorkGroupID.xy) * ivec2(12, 16)', body)

    inp_decl = 'shared F inp[1][18][14];' if i == 1 else 'shared V4 inp[8][18][14];'
    samplers = ''.join(f'uniform sampler2D {b}_raw;\n' for b in binds)

    preamble = f'''#version 310 es
precision highp float;
precision highp int;
layout(local_size_x = 12, local_size_y = 16) in;
layout(rgba16f, binding = 0) uniform writeonly highp image2D out_image;
{samplers}
#define V4 vec4
#define M4 mat4
#define F float

const ivec2 ksize = ivec2(3, 3);
const ivec2 offset = ivec2(1, 1);
{inp_decl}
'''
    name = f'artcnn_p{i:02d}.comp'
    out_file = os.path.join(out_dir, name)
    with open(out_file, 'w') as out:
        out.write(preamble + body)

    manifest.append(f'{name} {save} {",".join(binds)} {w_scale} {h_scale}')

manifest_path = os.path.join(out_dir, 'artcnn_manifest.txt')
with open(manifest_path, 'w') as f:
    f.write('\n'.join(manifest) + '\n')

print(f'Generated {len(manifest)} ArtCNN compute shaders successfully:')
print('\n'.join(manifest))
