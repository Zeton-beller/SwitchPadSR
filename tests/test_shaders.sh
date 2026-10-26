#!/data/data/com.termux/files/usr/bin/env bash
set -e

echo "=== Verifying and compiling all Vulkan GLSL shaders to SPIR-V ==="
mkdir -p build/spv

SHADER_DIR="app/src/main/shaders"
for shader in "$SHADER_DIR"/*.vert "$SHADER_DIR"/*.frag; do
    name=$(basename "$shader")
    echo "  Compiling $name -> build/spv/$name.spv"
    glslangValidator -V "$shader" -o "build/spv/$name.spv"
done

echo "=== ALL SHADERS COMPILED TO SPIR-V WITH ZERO ERRORS ==="
ls -lh build/spv
