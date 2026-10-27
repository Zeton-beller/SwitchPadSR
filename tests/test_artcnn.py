#!/usr/bin/env python3
"""Validates the ArtCNN C4F32_DS neural pipeline, manifest, and compute shader structure."""

import os, re

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHADER_DIR = os.path.join(BASE_DIR, "app/src/main/assets/shaders")
MANIFEST_PATH = os.path.join(SHADER_DIR, "artcnn_manifest.txt")

def test_manifest():
    assert os.path.exists(MANIFEST_PATH), f"Manifest missing: {MANIFEST_PATH}"
    with open(MANIFEST_PATH, "r") as f:
        lines = [l.strip() for l in f if l.strip()]
    assert len(lines) == 7, f"Expected 7 layers, got {len(lines)}"

    expected = [
        ("artcnn_p01.comp", "conv2d", ["LUMA"], 4.0, 2.0),
        ("artcnn_p02.comp", "conv2d_1", ["conv2d"], 4.0, 2.0),
        ("artcnn_p03.comp", "conv2d_2", ["conv2d_1"], 4.0, 2.0),
        ("artcnn_p04.comp", "conv2d_3", ["conv2d_2"], 4.0, 2.0),
        ("artcnn_p05.comp", "conv2d_4", ["conv2d_3"], 4.0, 2.0),
        ("artcnn_p06.comp", "conv2d_5", ["conv2d_4"], 4.0, 2.0),
        ("artcnn_p07.comp", "conv2d_6", ["conv2d", "conv2d_5"], 1.0, 1.0),
    ]

    for line, (prog, save, binds, ws, hs) in zip(lines, expected):
        parts = line.split(" ")
        assert parts[0] == prog, f"Prog mismatch: {parts[0]} vs {prog}"
        assert parts[1] == save, f"Save mismatch: {parts[1]} vs {save}"
        assert parts[2].split(",") == binds, f"Binds mismatch: {parts[2]} vs {binds}"
        assert float(parts[3]) == ws, f"Width scale mismatch: {parts[3]} vs {ws}"
        assert float(parts[4]) == hs, f"Height scale mismatch: {parts[4]} vs {hs}"

        shader_file = os.path.join(SHADER_DIR, prog)
        assert os.path.exists(shader_file), f"Shader file missing: {shader_file}"
        with open(shader_file, "r") as sf:
            code = sf.read()
            assert "#version 310 es" in code, "Must be GLSL ES 3.10"
            assert "layout(local_size_x = 12, local_size_y = 16) in;" in code, "Workgroup size must be 12x16"
            assert "layout(rgba16f, binding = 0) uniform writeonly highp image2D out_image;" in code
            for b in binds:
                assert f"uniform sampler2D {b}_raw;" in code, f"Missing sampler {b}_raw in {prog}"

    print("ArtCNN manifest & 7 compute shaders verified successfully!")

if __name__ == "__main__":
    test_manifest()
