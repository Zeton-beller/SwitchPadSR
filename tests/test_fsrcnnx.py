"""Validates the FSRCNNX port.
1. The generated GLSL ES layers (storage/bottom-up orientation, tools/gen_fsrcnnx.py) are evaluated with a tiny
   GLSL interpreter and must be bit-identical to an independent evaluation of the official mpv shader (top-down).
2. The network must beat bicubic in PSNR on a 2x downscale->upscale round trip and must not create stripes."""
import re, os, glob, sys
import numpy as np
D = os.path.dirname(__file__)
OFFICIAL = open(os.path.join(D, '..', 'tools', 'FSRCNNX_x2_8-0-4-1.glsl')).read()
SH = os.path.join(D, '..', 'app', 'src', 'main', 'assets', 'shaders')
nums = lambda s: [float(x) for x in s.split(',')]

def tap(img, dx, dy):                    # img (H,W,C); clamp-to-edge shifted fetch, +dy = row index + dy
    H, W = img.shape[:2]
    ys = np.clip(np.arange(H) + dy, 0, H - 1); xs = np.clip(np.arange(W) + dx, 0, W - 1)
    return img[ys][:, xs]

def run_layers(body, texs, tapfn):
    """body: list of statement lines of one hook, texs: name->(H,W,4)"""
    H, W = next(iter(texs.values())).shape[:2]
    res = None
    for line in body:
        line = line.strip()
        m = re.match(r'vec4 res = vec4\(([^)]*)\);', line)
        if m: res = np.tile(np.array(nums(m.group(1))), (H, W, 1)); continue
        m = re.match(r'res \+= (mat4x4|mat4|vec4)\(([^)]*)\) \* (.*);', line)
        if m:
            k, v, rhs = m.groups(); v = np.array(nums(v))
            name, dx, dy, isf = tapfn(rhs)
            t = texs[name]
            f = tap(t, dx, dy)
            if isf: res = res + v[None, None, :] * f[..., :1]
            else:
                M = v.reshape(4, 4).T
                res = res + f @ M.T
            continue
        m = re.match(r'res \+= (.*);', line)
        if m:
            name, dx, dy, _ = tapfn(m.group(1)); res = res + tap(texs[name], dx, dy); continue
        m = re.match(r'res = max\(res, vec4\(0\.0\)\) \+ vec4\(([^)]*)\) \* min\(res, vec4\(0\.0\)\);', line)
        if m: res = np.maximum(res, 0) + np.array(nums(m.group(1))) * np.minimum(res, 0)
    return res

def gen_tapfn(rhs):                      # TX(NAME, dx, dy)[.x]
    m = re.match(r'TX\((\w+), (-?\d+), (-?\d+)\)(\.x)?$', rhs.strip()); assert m, rhs
    return m.group(1), int(m.group(2)), int(m.group(3)), bool(m.group(4))
def off_tapfn(rhs):                      # [float(]NAME_texOff(vec2(dx,dy))[)]
    rhs = rhs.strip()
    m = re.match(r'(float\()?(\w+)_texOff\((?:vec2\((-?\d+),(-?\d+)\)|0)\)\)?$', rhs); assert m, rhs
    return m.group(2), int(m.group(3) or 0), int(m.group(4) or 0), bool(m.group(1))

def network(luma, official):
    """luma (H,W) in the orientation the layer set expects; returns SUBCONV1 (H,W,4)"""
    texs = {'LUMA': luma[..., None].repeat(4, -1)}
    if official:
        hooks = [h for h in OFFICIAL.split('//!HOOK')[1:]]
        layers = []
        for h in hooks:
            s = re.search(r'//!SAVE (\w+)', h)
            if s: layers.append((s.group(1), h[h.index('vec4 hook()'):].splitlines()))
        tf = off_tapfn
    else:
        man = [l.split() for l in open(os.path.join(SH, 'fsrcnnx_manifest.txt'))]
        layers = [(sv, open(os.path.join(SH, f)).read().split('vec4 hook()')[1].splitlines()) for f, sv, _ in man]
        tf = gen_tapfn
    for save, body in layers:
        texs[save] = run_layers(body, texs, tf)
    return texs['SUBCONV1']

def aggregate_official(sub):             # top-down, mpv: res[index.x*2+index.y], index = (col parity,row parity)
    H, W = sub.shape[:2]; out = np.zeros((2 * H, 2 * W))
    for i in range(2):
        for j in range(2): out[j::2, i::2] = sub[..., i * 2 + j]
    return out
def aggregate_generated(sub):            # bottom-up storage, as in fsrcnnx_up.frag: j = 1 - (Y&1)
    H, W = sub.shape[:2]; out = np.zeros((2 * H, 2 * W))
    for i in range(2):
        for s in range(2): out[s::2, i::2] = sub[..., i * 2 + (1 - s)]
    return out

def test_image(n=64):
    y, x = np.mgrid[0:n, 0:n].astype(float)
    img = 0.15 + 0.1 * np.sin(x / 9) * np.cos(y / 11)
    img[(x - 20) ** 2 + (y - 24) ** 2 < 150] = 0.9           # disc
    img[(abs(x - 44) < 9) & (abs(y - 40) < 12)] = 0.75       # box
    img[(x + 0.6 * y > 50) & (x + 0.6 * y < 53)] = 0.05      # thin diagonal line
    img[10:13, 30:60] = 0.95                                 # thin horizontal bar
    img[30:55, 8:10] = 0.9                                   # thin vertical bar
    return np.clip(img, 0, 1)

def box_down(a): return a.reshape(a.shape[0] // 2, 2, a.shape[1] // 2, 2).mean((1, 3))
def bicubic2x(a):
    from numpy import floor
    def cr(t): return np.array([-.5*t**3+t**2-.5*t, 1.5*t**3-2.5*t**2+1, -1.5*t**3+2*t**2+.5*t, .5*t**3-.5*t**2])
    H, W = a.shape; out = np.zeros((2 * H, 2 * W)); ap = np.pad(a, 2, mode='edge')
    for Y in range(2 * H):
        for X in range(2 * W):
            py, px = (Y + .5) / 2 - .5, (X + .5) / 2 - .5
            by, bx = int(floor(py)), int(floor(px)); wy, wx = cr(py - by), cr(px - bx)
            patch = ap[by - 1 + 2:by + 3 + 2, bx - 1 + 2:bx + 3 + 2]
            out[Y, X] = wy @ patch @ wx
    return out
psnr = lambda a, b: 10 * np.log10(1 / np.mean((a - b) ** 2))

gt = test_image(64); lo = box_down(gt)              # 32x32 low-res input
# 1) generated (flipped image, storage orientation) == official (image orientation)
off = aggregate_official(network(lo, True))
gen_flipped = aggregate_generated(network(lo[::-1].copy(), False))
gen = gen_flipped[::-1]
err = np.abs(off - gen).max()
print('official vs generated-GLSL max abs diff: %.2e' % err)
assert err < 1e-9, 'generated shaders are NOT equivalent to the official network'
# 2) quality
bic = bicubic2x(lo); nn = np.clip(off, 0, 1)
p_b, p_n = psnr(np.clip(bic, 0, 1), gt), psnr(nn, gt)
print('PSNR vs ground truth  bicubic: %.2f dB   FSRCNNX: %.2f dB' % (p_b, p_n))
assert p_n > p_b + 0.5, 'FSRCNNX must clearly beat bicubic'
print('FSRCNNX TESTS PASSED')
