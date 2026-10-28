#!/usr/bin/env python3
"""Converts official CuNNy-4x16-DS.glsl (funnyplanter/CuNNy, LGPL-3.0)
into high-performance GLSL ES 3.00 fragment shaders for mobile Adreno GPUs.

Generates:
  - app/src/main/assets/shaders/cunny_p01.frag .. cunny_p06.frag
  - app/src/main/assets/shaders/cunny_manifest.txt
"""
import os, re

src_path = os.path.join(os.path.dirname(__file__), 'CuNNy-4x16-DS.glsl')
out_dir = os.path.join(os.path.dirname(__file__), '..', 'app/src/main/assets/shaders')
os.makedirs(out_dir, exist_ok=True)

with open(src_path, 'r', encoding='utf-8') as f:
    text = f.read()

hooks = text.split('//!HOOK')[1:]
assert len(hooks) == 6, f"Expected 6 hooks, got {len(hooks)}"

HEAD = """#version 300 es
precision highp float;
precision highp int;
precision highp sampler2D;
#define V4 vec4
#define M4 mat4
#define F float
"""

manifest = []

for layer_idx, h in enumerate(hooks):
    lines = h.splitlines()
    filename = f"cunny_p{layer_idx+1:02d}.frag"
    out_path = os.path.join(out_dir, filename)
    
    if layer_idx == 0:
        # Hook 0: Input LUMA -> cunny_in (2x)
        acc_lines = []
        for line in lines:
            line_s = line.strip()
            if any(k in line_s for k in ['r0 +=', 'r1 +=', 'r2 +=', 'r3 +=']):
                acc_lines.append(line_s)

        code = HEAD + """
uniform sampler2D LUMA; // Source high-precision luma (W x H)
uniform ivec2 u_size;   // LUMA size (w, h)
out vec4 o_color;

#define TX(dx, dy) texelFetch(LUMA, clamp(srcPos + ivec2(dx, -(dy)), ivec2(0), u_size - 1), 0).r

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    ivec2 srcPos = p >> 1;
    int subIdx = (p.y & 1) * 2 + (p.x & 1);

    F s0_0_0 = TX(-1, -1); F s0_0_1 = TX( 0, -1); F s0_0_2 = TX( 1, -1);
    F s0_1_0 = TX(-1,  0); F s0_1_1 = TX( 0,  0); F s0_1_2 = TX( 1,  0);
    F s0_2_0 = TX(-1,  1); F s0_2_1 = TX( 0,  1); F s0_2_2 = TX( 1,  1);

    V4 r0 = V4(0.0), r1 = V4(0.0), r2 = V4(0.0), r3 = V4(0.0);
"""
        for l in acc_lines:
            code += f"    {l}\n"
        code += """
    if (subIdx == 0) o_color = clamp(r0, V4(0.0), V4(1.0));
    else if (subIdx == 1) o_color = clamp(r1, V4(0.0), V4(1.0));
    else if (subIdx == 2) o_color = clamp(r2, V4(0.0), V4(1.0));
    else o_color = clamp(r3, V4(0.0), V4(1.0));
}
"""
        with open(out_path, 'w', encoding='utf-8') as f:
            f.write(code)
        manifest.append(f"{filename} cunny_in LUMA 2")

    elif layer_idx in [1, 2, 3, 4]:
        # Hidden conv layers (16 -> 16 channels, 2W x 2H)
        prev_name = "cunny_in" if layer_idx == 1 else f"conv{layer_idx-1}"
        save_name = f"conv{layer_idx}"
        acc_lines = []
        for line in lines:
            line_s = line.strip()
            if any(k in line_s for k in ['r0 +=', 'r1 +=', 'r2 +=', 'r3 +=']):
                acc_lines.append(line_s)

        code = HEAD + f"""
// CuNNy-4x16-DS Hidden Conv Layer {layer_idx} (16 -> 16 channels)
uniform sampler2D {prev_name}; // Previous 16-channel packed texture (2W x 2H)
uniform ivec2 u_size;          // LUMA size (w, h)
out vec4 o_color;

#define FETCH16(dx, dy, g0, g1, g2, g3) {{ \\
    ivec2 base = clamp(srcPos + ivec2(dx, -(dy)), ivec2(0), u_size - 1) * 2; \\
    g0 = texelFetch({prev_name}, base + ivec2(0, 0), 0); \\
    g1 = texelFetch({prev_name}, base + ivec2(1, 0), 0); \\
    g2 = texelFetch({prev_name}, base + ivec2(0, 1), 0); \\
    g3 = texelFetch({prev_name}, base + ivec2(1, 1), 0); \\
}}

void main() {{
    ivec2 p = ivec2(gl_FragCoord.xy);
    ivec2 srcPos = p >> 1;
    int subIdx = (p.y & 1) * 2 + (p.x & 1);

    V4 s0_0_0, s1_0_0, s2_0_0, s3_0_0; FETCH16(-1, -1, s0_0_0, s1_0_0, s2_0_0, s3_0_0);
    V4 s0_0_1, s1_0_1, s2_0_1, s3_0_1; FETCH16( 0, -1, s0_0_1, s1_0_1, s2_0_1, s3_0_1);
    V4 s0_0_2, s1_0_2, s2_0_2, s3_0_2; FETCH16( 1, -1, s0_0_2, s1_0_2, s2_0_2, s3_0_2);

    V4 s0_1_0, s1_1_0, s2_1_0, s3_1_0; FETCH16(-1,  0, s0_1_0, s1_1_0, s2_1_0, s3_1_0);
    V4 s0_1_1, s1_1_1, s2_1_1, s3_1_1; FETCH16( 0,  0, s0_1_1, s1_1_1, s2_1_1, s3_1_1);
    V4 s0_1_2, s1_1_2, s2_1_2, s3_1_2; FETCH16( 1,  0, s0_1_2, s1_1_2, s2_1_2, s3_1_2);

    V4 s0_2_0, s1_2_0, s2_2_0, s3_2_0; FETCH16(-1,  1, s0_2_0, s1_2_0, s2_2_0, s3_2_0);
    V4 s0_2_1, s1_2_1, s2_2_1, s3_2_1; FETCH16( 0,  1, s0_2_1, s1_2_1, s2_2_1, s3_2_1);
    V4 s0_2_2, s1_2_2, s2_2_2, s3_2_2; FETCH16( 1,  1, s0_2_2, s1_2_2, s2_2_2, s3_2_2);

    V4 r0 = V4(0.0), r1 = V4(0.0), r2 = V4(0.0), r3 = V4(0.0);
"""
        for l in acc_lines:
            code += f"    {l}\n"
        code += """
    if (subIdx == 0) o_color = clamp(r0, V4(0.0), V4(1.0));
    else if (subIdx == 1) o_color = clamp(r1, V4(0.0), V4(1.0));
    else if (subIdx == 2) o_color = clamp(r2, V4(0.0), V4(1.0));
    else o_color = clamp(r3, V4(0.0), V4(1.0));
}
"""
        with open(out_path, 'w', encoding='utf-8') as f:
            f.write(code)
        manifest.append(f"{filename} {save_name} {prev_name} 2")

    elif layer_idx == 5:
        # Hook 5: Output pass (16 channels -> 4 subpixels SUBCONV1 at 1x resolution W x H)
        acc_lines = []
        for line in lines:
            line_s = line.strip()
            if any(k in line_s for k in ['r0 +=']):
                acc_lines.append(line_s)

        code = HEAD + """
// CuNNy-4x16-DS Output Reconstruction Layer (16 channels -> 4 subpixel luma)
uniform sampler2D conv4; // conv4 16-channel texture (2W x 2H)
uniform sampler2D LUMA;  // Source high-precision luma (W x H)
uniform ivec2 u_size;    // LUMA size (w, h)
out vec4 o_color;

#define FETCH16(dx, dy, g0, g1, g2, g3) { \\
    ivec2 base = clamp(srcPos + ivec2(dx, -(dy)), ivec2(0), u_size - 1) * 2; \\
    g0 = texelFetch(conv4, base + ivec2(0, 0), 0); \\
    g1 = texelFetch(conv4, base + ivec2(1, 0), 0); \\
    g2 = texelFetch(conv4, base + ivec2(0, 1), 0); \\
    g3 = texelFetch(conv4, base + ivec2(1, 1), 0); \\
}

void main() {
    ivec2 srcPos = ivec2(gl_FragCoord.xy);

    V4 s0_0_0, s1_0_0, s2_0_0, s3_0_0; FETCH16(-1, -1, s0_0_0, s1_0_0, s2_0_0, s3_0_0);
    V4 s0_0_1, s1_0_1, s2_0_1, s3_0_1; FETCH16( 0, -1, s0_0_1, s1_0_1, s2_0_1, s3_0_1);
    V4 s0_0_2, s1_0_2, s2_0_2, s3_0_2; FETCH16( 1, -1, s0_0_2, s1_0_2, s2_0_2, s3_0_2);

    V4 s0_1_0, s1_1_0, s2_1_0, s3_1_0; FETCH16(-1,  0, s0_1_0, s1_1_0, s2_1_0, s3_1_0);
    V4 s0_1_1, s1_1_1, s2_1_1, s3_1_1; FETCH16( 0,  0, s0_1_1, s1_1_1, s2_1_1, s3_1_1);
    V4 s0_1_2, s1_1_2, s2_1_2, s3_1_2; FETCH16( 1,  0, s0_1_2, s1_1_2, s2_1_2, s3_1_2);

    V4 s0_2_0, s1_2_0, s2_2_0, s3_2_0; FETCH16(-1,  1, s0_2_0, s1_2_0, s2_2_0, s3_2_0);
    V4 s0_2_1, s1_2_1, s2_2_1, s3_2_1; FETCH16( 0,  1, s0_2_1, s1_2_1, s2_2_1, s3_2_1);
    V4 s0_2_2, s1_2_2, s2_2_2, s3_2_2; FETCH16( 1,  1, s0_2_2, s1_2_2, s2_2_2, s3_2_2);

    V4 r0 = V4(0.0);
"""
        for l in acc_lines:
            code += f"    {l}\n"
        code += """
    float luma = texelFetch(LUMA, srcPos, 0).r;
    // Row-major subpixel ordering: [0]=(0,0), [1]=(1,0), [2]=(0,1), [3]=(1,1)
    o_color = clamp(r0 + luma, 0.0, 1.0);
}
"""
        with open(out_path, 'w', encoding='utf-8') as f:
            f.write(code)
        manifest.append(f"{filename} SUBCONV1 conv4,LUMA 1")

manifest_path = os.path.join(out_dir, 'cunny_manifest.txt')
with open(manifest_path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(manifest) + '\n')

print("Generated CuNNy shaders:")
for m in manifest:
    print(" ", m)
