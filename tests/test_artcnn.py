#!/usr/bin/env python3
"""Validates the ArtCNN C4F32_DS neural pipeline, manifest, compute shaders, and UBO weight binaries."""

import os, struct

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHADER_DIR = os.path.join(BASE_DIR, "app/src/main/assets/shaders")
MANIFEST_PATH = os.path.join(SHADER_DIR, "artcnn_manifest.txt")

def test_manifest():
    assert os.path.exists(MANIFEST_PATH), f"Manifest missing: {MANIFEST_PATH}"
    with open(MANIFEST_PATH, "r") as f:
        lines = [l.strip() for l in f if l.strip()]
    assert len(lines) == 7, f"Expected 7 layers, got {len(lines)}"

    expected = [
        ("artcnn_p01.comp", "conv2d", ["LUMA"], 4.0, 2.0, "artcnn_w01.bin", 1280),
        ("artcnn_conv.comp", "conv2d_1", ["conv2d"], 4.0, 2.0, "artcnn_w02.bin", 36992),
        ("artcnn_conv.comp", "conv2d_2", ["conv2d_1"], 4.0, 2.0, "artcnn_w03.bin", 36992),
        ("artcnn_conv.comp", "conv2d_3", ["conv2d_2"], 4.0, 2.0, "artcnn_w04.bin", 36992),
        ("artcnn_conv.comp", "conv2d_4", ["conv2d_3"], 4.0, 2.0, "artcnn_w05.bin", 36992),
        ("artcnn_conv.comp", "conv2d_5", ["conv2d_4"], 4.0, 2.0, "artcnn_w06.bin", 36992),
        ("artcnn_p07.comp", "conv2d_6", ["conv2d", "conv2d_5"], 1.0, 1.0, "artcnn_w07.bin", 4624),
    ]

    for line, (prog, save, binds, ws, hs, weight_bin, expected_size) in zip(lines, expected):
        parts = line.split(" ")
        assert parts[0] == prog, f"Prog mismatch: {parts[0]} vs {prog}"
        assert parts[1] == save, f"Save mismatch: {parts[1]} vs {save}"
        assert parts[2].split(",") == binds, f"Binds mismatch: {parts[2]} vs {binds}"
        assert float(parts[3]) == ws, f"Width scale mismatch: {parts[3]} vs {ws}"
        assert float(parts[4]) == hs, f"Height scale mismatch: {parts[4]} vs {hs}"
        assert parts[5] == weight_bin, f"Weight bin mismatch: {parts[5]} vs {weight_bin}"

        shader_file = os.path.join(SHADER_DIR, prog)
        assert os.path.exists(shader_file), f"Shader file missing: {shader_file}"
        with open(shader_file, "r") as sf:
            code = sf.read()
            assert "#version 310 es" in code, f"Must be GLSL ES 3.10: {prog}"
            assert "layout(local_size_x = 12, local_size_y = 16) in;" in code, f"Workgroup size must be 12x16: {prog}"
            assert "layout(rgba16f, binding = 0) uniform writeonly highp image2D out_image;" in code
            assert "layout(std140, binding = 1) uniform" in code, f"Must use std140 UBO layout: {prog}"

        bin_file = os.path.join(SHADER_DIR, weight_bin)
        assert os.path.exists(bin_file), f"Binary weight file missing: {bin_file}"
        sz = os.path.getsize(bin_file)
        assert sz == expected_size, f"Weight binary size mismatch for {weight_bin}: {sz} vs {expected_size}"

    print("ArtCNN manifest, UBO shaders & binary weight assets verified successfully!")

if __name__ == "__main__":
    test_manifest()
