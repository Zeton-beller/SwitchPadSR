#!/usr/bin/env python3
"""Validates CuNNy-4x16-DS and Anime4K v4.0.1 CNN shader configurations, manifests,
and layer input/output pipeline consistency.
"""
import os, re

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHADER_DIR = os.path.join(BASE_DIR, "app/src/main/assets/shaders")

def test_cunny_manifest():
    manifest_path = os.path.join(SHADER_DIR, "cunny_manifest.txt")
    assert os.path.exists(manifest_path), f"Missing {manifest_path}"
    with open(manifest_path, "r", encoding="utf-8") as f:
        lines = [l.strip() for l in f if l.strip()]

    assert len(lines) == 6, f"CuNNy should have exactly 6 layers, got {len(lines)}"
    
    # Layer 0: cunny_p01.frag cunny_in LUMA 2
    # Layer 1..4: cunny_p02..p05 conv1..conv4 2
    # Layer 5: cunny_p06.frag SUBCONV1 conv4,LUMA 1
    for idx, line in enumerate(lines):
        parts = line.split()
        assert len(parts) >= 4, f"Invalid manifest format line {idx}: {line}"
        frag, save, binds, scale = parts[0], parts[1], parts[2].split(","), int(parts[3])
        assert os.path.exists(os.path.join(SHADER_DIR, frag)), f"Missing shader {frag}"
        if idx == 0:
            assert save == "cunny_in" and "LUMA" in binds and scale == 2
        elif idx in [1, 2, 3, 4]:
            assert scale == 2
        elif idx == 5:
            assert save == "SUBCONV1" and "conv4" in binds and "LUMA" in binds and scale == 1

    print("CuNNy manifest verification: OK (6 layers, scale 2x intermediate, 1x subconv1)")

def test_anime4k_manifest():
    manifest_path = os.path.join(SHADER_DIR, "a4k_manifest.txt")
    assert os.path.exists(manifest_path), f"Missing {manifest_path}"
    with open(manifest_path, "r", encoding="utf-8") as f:
        lines = [l.strip() for l in f if l.strip()]

    assert len(lines) == 8, f"Anime4K should have exactly 8 layers, got {len(lines)}"
    
    for idx, line in enumerate(lines):
        parts = line.split()
        assert len(parts) >= 4, f"Invalid manifest format line {idx}: {line}"
        frag, save, binds, scale = parts[0], parts[1], parts[2].split(","), int(parts[3])
        assert os.path.exists(os.path.join(SHADER_DIR, frag)), f"Missing shader {frag}"
        assert scale == 1, f"Anime4K layer {idx} scale should be 1"
        if idx == 0:
            assert save == "conv2d_tf" and "MAIN" in binds
        elif idx in range(1, 7):
            expected_prev = "conv2d_tf" if idx == 1 else f"conv2d_{idx-1}_tf"
            assert binds[0] == expected_prev, f"Expected {expected_prev}, got {binds[0]}"
        elif idx == 7:
            assert save == "SUBCONV1" and len(binds) == 8 and "LUMA" in binds

    print("Anime4K manifest verification: OK (8 layers, 1x scale, dense fusion + LUMA)")

def test_shader_uniform_matching():
    # Verify that all binds in manifests actually exist as uniforms in the respective shaders
    for m_name in ["cunny_manifest.txt", "a4k_manifest.txt"]:
        manifest_path = os.path.join(SHADER_DIR, m_name)
        with open(manifest_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line: continue
                parts = line.split()
                frag, save, binds = parts[0], parts[1], parts[2].split(",")
                shader_path = os.path.join(SHADER_DIR, frag)
                with open(shader_path, "r", encoding="utf-8") as sf:
                    src = sf.read()
                for b in binds:
                    pattern = rf"uniform\s+sampler2D\s+{b}\b"
                    assert re.search(pattern, src), f"Shader {frag} missing declared uniform for bound FBO '{b}'"

    print("Shader uniform declarations match manifest bindings: 100% OK")

if __name__ == "__main__":
    test_cunny_manifest()
    test_anime4k_manifest()
    test_shader_uniform_matching()
    print("ALL CUNNY AND ANIME4K CNN VERIFICATIONS PASSED")
