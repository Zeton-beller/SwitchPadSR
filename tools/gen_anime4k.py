#!/usr/bin/env python3
"""Converts official bloc97/Anime4K Anime4K_Upscale_CNN_x2_M.glsl (MIT License)
into high-performance GLSL ES 3.00 fragment shaders for mobile Adreno GPUs.

Generates:
  - app/src/main/assets/shaders/a4k_p01.frag .. a4k_p08.frag
  - app/src/main/assets/shaders/a4k_manifest.txt
"""
import os, re

src_path = os.path.join(os.path.dirname(__file__), 'Anime4K_Upscale_CNN_x2_M.glsl')
out_dir = os.path.join(os.path.dirname(__file__), '..', 'app/src/main/assets/shaders')
os.makedirs(out_dir, exist_ok=True)

with open(src_path, 'r', encoding='utf-8') as f:
    text = f.read()

hooks = text.split('//!HOOK')[1:]
assert len(hooks) >= 8, f"Expected at least 8 hooks, got {len(hooks)}"

HEAD = """#version 300 es
precision highp float;
precision highp int;
precision highp sampler2D;
uniform ivec2 u_size;
out vec4 o_color;
#define TX(S, dx, dy) texelFetch(S, clamp(ivec2(gl_FragCoord.xy) + ivec2(dx, -(dy)), ivec2(0), u_size - 1), 0)
"""

manifest = []

# Process the 8 CNN convolution layers (Layers 0..7)
# Layer 7 outputs SUBCONV1 (the 4 subpixel luma values + base luma) which feeds directly into fsrcnnx_up.frag
for layer_idx in range(8):
    h = hooks[layer_idx]
    save_m = re.search(r'//!SAVE (.*)', h)
    save_name = save_m.group(1).strip() if save_m else f"save_{layer_idx}"
    binds = [b.strip() for b in re.findall(r'//!BIND (.*)', h)]
    
    filename = f"a4k_p{layer_idx+1:02d}.frag"
    out_path = os.path.join(out_dir, filename)
    
    body = h[h.index('vec4 hook()'):]
    
    if layer_idx == 0:
        # Layer 0: takes MAIN (RGB)
        unis = "uniform sampler2D MAIN;\n"
        body = re.sub(r'MAIN_texOff\(vec2\(([-0-9.]+),\s*([-0-9.]+)\)\)',
                      lambda m: f"TX(MAIN, int({m.group(1)}), int({m.group(2)}))", body)
        body = re.sub(r'#define go_0\(.*?\).*\n', '', body)
        prefix = "#define go_0(dx, dy) TX(MAIN, int(dx), int(dy))\n"
        shader_code = HEAD + unis + prefix + body + "\nvoid main() { o_color = hook(); }\n"
        manifest.append(f"{filename} {save_name} MAIN 1")
        
    elif layer_idx in range(1, 7):
        # Layer 1..6: takes single previous conv2d texture
        prev_name = binds[0]
        unis = f"uniform sampler2D {prev_name};\n"
        body = re.sub(r'#define go_0\(.*?\).*\n', '', body)
        body = re.sub(r'#define go_1\(.*?\).*\n', '', body)
        prefix = f"""#define go_0(dx, dy) max(TX({prev_name}, int(dx), int(dy)), vec4(0.0))
#define go_1(dx, dy) max(-TX({prev_name}, int(dx), int(dy)), vec4(0.0))
"""
        shader_code = HEAD + unis + prefix + body + "\nvoid main() { o_color = hook(); }\n"
        manifest.append(f"{filename} {save_name} {prev_name} 1")
        
    elif layer_idx == 7:
        # Layer 7: 1x1 dense fusion of conv2d_tf .. conv2d_6_tf + LUMA base
        unis = "".join(f"uniform sampler2D {b};\n" for b in binds) + "uniform sampler2D LUMA;\n"
        body = re.sub(r'#define g_\d+.*?\n', '', body)
        prefix = ""
        for i, b in enumerate(binds):
            prefix += f"#define g_{2*i} max(TX({b}, 0, 0), vec4(0.0))\n"
            prefix += f"#define g_{2*i+1} max(-TX({b}, 0, 0), vec4(0.0))\n"
        
        main_code = """
void main() {
    float baseLuma = texelFetch(LUMA, ivec2(gl_FragCoord.xy), 0).r;
    // Row-major subpixel ordering: [0]=(0,0), [1]=(1,0), [2]=(0,1), [3]=(1,1)
    o_color = clamp(hook() + baseLuma, 0.0, 1.0);
}
"""
        shader_code = HEAD + unis + prefix + body + main_code
        all_binds = binds + ["LUMA"]
        manifest.append(f"{filename} SUBCONV1 {','.join(all_binds)} 1")

    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(shader_code)

manifest_path = os.path.join(out_dir, 'a4k_manifest.txt')
with open(manifest_path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(manifest) + '\n')

print("Generated Anime4K shaders:")
for m in manifest:
    print(" ", m)
