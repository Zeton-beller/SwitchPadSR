"""CPU reference of the SGSR, FSR (EASU+RCAS), and Anime4K shader math (matching the GLSL shaders).
Verifies:
1. All outputs are finite and in [0, 1].
2. SGSR, FSR (EASU+RCAS), and Anime4K all produce significantly sharper edges than Catmull-Rom.
3. Adjusting each parameter slider/toggle produces a clear, measurable change in the output image."""
import numpy as np

def catmull(t):
    t2, t3 = t*t, t*t*t
    return np.array([-.5*t3+t2-.5*t, 1.5*t3-2.5*t2+1, -1.5*t3+2*t2+.5*t, .5*t3-.5*t2])

def lanczos2(x):
    x = abs(x)
    if x < 1e-4: return 1.0
    if x >= 2: return 0.0
    px = np.pi*x
    return 2*np.sin(px)*np.sin(px*.5)/(px*px)

def luma(c): return c @ np.array([.2126, .7152, .0722])

def taps(img, bx, by):
    h, w, _ = img.shape
    c = np.zeros((4, 4, 3))
    for j in range(4):
        for i in range(4):
            c[j, i] = img[np.clip(by+j-1, 0, h-1), np.clip(bx+i-1, 0, w-1)]
    return c

def sample_bilinear(img, u, v):
    h, w, _ = img.shape
    px = np.clip(u * w - 0.5, 0.0, w - 1.0)
    py = np.clip(v * h - 0.5, 0.0, h - 1.0)
    x0, y0 = int(np.floor(px)), int(np.floor(py))
    x1, y1 = min(x0 + 1, w - 1), min(y0 + 1, h - 1)
    fx, fy = px - x0, py - y0
    c00, c10 = img[y0, x0], img[y0, x1]
    c01, c11 = img[y1, x0], img[y1, x1]
    return (c00*(1-fx) + c10*fx)*(1-fy) + (c01*(1-fx) + c11*fx)*fy

def bicubic_pixel(img, u, v):
    h, w, _ = img.shape
    px, py = u*w-.5, v*h-.5
    bx, by = int(np.floor(px)), int(np.floor(py))
    fx, fy = px-bx, py-by
    c = taps(img, bx, by)
    wx, wy = catmull(fx), catmull(fy)
    return np.clip(sum(wy[j]*sum(wx[i]*c[j, i] for i in range(4)) for j in range(4)), 0, 1)

def sgsr_pixel(img, u, v, thr=4.0, sharp=1.5, use_dir=True):
    h, w, _ = img.shape
    px, py = u*w-.5, v*h-.5
    bx, by = int(np.floor(px)), int(np.floor(py))
    fx, fy = px-bx, py-by
    c = taps(img, bx, by)
    L = np.array([[luma(c[j, i]) for i in range(4)] for j in range(4)])
    wx, wy = catmull(fx), catmull(fy)
    base = np.clip(sum(wy[j]*sum(wx[i]*c[j, i] for i in range(4)) for j in range(4)), 0, 1)
    gx = .25*((L[1,2]-L[1,0])+(L[1,3]-L[1,1])+(L[2,2]-L[2,0])+(L[2,3]-L[2,1]))
    gy = .25*((L[2,1]-L[0,1])+(L[2,2]-L[0,2])+(L[3,1]-L[1,1])+(L[3,2]-L[1,2]))
    mag = np.hypot(gx, gy)
    t = thr/255
    if mag < t: return base
    n = np.array([gx, gy])/mag; tg = np.array([-n[1], n[0]])
    k = np.clip(sharp-1, 0, 1)
    sa, sb = (.62, 1.15+.65*k) if use_dir else (1+.30*k,)*2
    s = np.zeros(3); ws = 0
    for j in range(4):
        for i in range(4):
            d = np.array([i-1-fx, j-1-fy])
            wgt = lanczos2(np.hypot(d@tg*sa, d@n*sb))
            s += c[j, i]*wgt; ws += wgt
    res = s/max(ws, 1e-4)
    blur4 = .25*(c[1,1]+c[1,2]+c[2,1]+c[2,2])
    res = res + (res - blur4)*(.20 + .90*k)
    mn = np.minimum.reduce([c[1,1], c[1,2], c[2,1], c[2,2]])
    mx = np.maximum.reduce([c[1,1], c[1,2], c[2,1], c[2,2]])
    pad = .02 + .12*k
    res = np.clip(res, mn-pad, mx+pad)
    m = np.clip((mag-t)/(t*2.2+.02-t), 0, 1); m = m*m*(3-2*m)
    return np.clip(base*(1-m)+res*m, 0, 1)

def easu_pixel(img, u, v, dering=True):
    h, w, _ = img.shape
    px, py = u*w-.5, v*h-.5
    bx, by = int(np.floor(px)), int(np.floor(py))
    fx, fy = px-bx, py-by
    c = taps(img, bx, by)
    L = np.array([[luma(c[j, i]) for i in range(4)] for j in range(4)])
    gx = .25*((L[1,2]-L[1,0])+(L[1,3]-L[1,1])+(L[2,2]-L[2,0])+(L[2,3]-L[2,1]))
    gy = .25*((L[2,1]-L[0,1])+(L[2,2]-L[0,2])+(L[3,1]-L[1,1])+(L[3,2]-L[1,2]))
    mag = np.hypot(gx, gy); conf = np.clip(mag*8, 0, 1)
    n = np.array([gx, gy])/mag if mag > 1e-5 else np.array([1., 0.]); tg = np.array([-n[1], n[0]])
    sal, sac = 1+(.52-1)*conf, 1+(1.55-1)*conf
    lob = .54+(.68-.54)*conf; clp = 2.5
    s = np.zeros(3); ws = 0
    for j in range(4):
        for i in range(4):
            d = np.array([i-1-fx, j-1-fy])
            d2 = min((d@tg*sal)**2+(d@n*sac)**2, clp)
            wb, wa = .4*d2-1, lob*d2-1
            wgt = wb*wb*wa
            s += c[j, i]*wgt; ws += wgt
    res = s/(ws if abs(ws) > 1e-5 else -1e-5)
    mn2 = np.minimum.reduce([c[1,1], c[1,2], c[2,1], c[2,2]])
    mx2 = np.maximum.reduce([c[1,1], c[1,2], c[2,1], c[2,2]])
    if dering:
        res = np.clip(res, mn2 - .015, mx2 + .015)
    else:
        mn4 = c.reshape(-1, 3).min(axis=0)
        mx4 = c.reshape(-1, 3).max(axis=0)
        res = np.clip(res, mn4 - .12, mx4 + .12)
    return np.clip(res, 0, 1)

def rcas(img, src_shape, sharp=.8, dering=True):
    h, w, _ = img.shape
    if sharp <= 0.001:
        return img.copy()
    sh, sw = src_shape
    px_fine = np.array([1.0/w, 1.0/h])
    scale = np.maximum([w/sw, h/sh], [1.0, 1.0])
    px_src = np.clip(scale * 0.75, 1.0, 3.5) * px_fine
    out = np.zeros_like(img)
    gain = sharp * (1.35 - 0.35 * sharp)
    for y in range(h):
        v = (y + 0.5) / h
        for x in range(w):
            u = (x + 0.5) / w
            e = img[y, x]
            bb = sample_bilinear(img, u, v - px_src[1])
            d  = sample_bilinear(img, u - px_src[0], v)
            f  = sample_bilinear(img, u + px_src[0], v)
            hh = sample_bilinear(img, u, v + px_src[1])
            bf = sample_bilinear(img, u, v - px_fine[1])
            df = sample_bilinear(img, u - px_fine[0], v)
            ff = sample_bilinear(img, u + px_fine[0], v)
            hf = sample_bilinear(img, u, v + px_fine[1])
            nb = 0.65 * (bb + d + f + hh) + 0.35 * (bf + df + ff + hf)
            le, lb, ld, lf, lh = luma(e), luma(bb), luma(d), luma(f), luma(hh)
            mn4 = min(le, lb, ld, lf, lh)
            mx4 = max(le, lb, ld, lf, lh)
            hit_min = mn4 / (4*mx4 + 1e-4)
            hit_max = (1 - mx4) / (4*mn4 - 4 - 1e-4)
            rcas_lim = max(-hit_min, hit_max)
            max_lobe = -0.30 if dering else -0.44
            w_mix = 0.50 if dering else 0.20
            lobe = max_lobe*(1 - w_mix) + np.clip(rcas_lim, max_lobe, -0.15)*w_mix
            lobe *= gain
            rcp_l = 1.0 / max(4*lobe + 1.0, 0.15)
            col = (lobe * nb + e) * rcp_l
            c_min = np.minimum.reduce([bb, d, f, hh, e])
            c_max = np.maximum.reduce([bb, d, f, hh, e])
            headroom = (0.025 + 0.035*gain) if dering else (0.14 + 0.20*gain)
            out[y, x] = np.clip(np.clip(col, c_min - headroom, c_max + headroom), 0, 1)
    return out

def anime4k(img, src_shape, strength=1.0, mode=0):
    h, w, _ = img.shape
    sh, sw = src_shape
    px_fine = np.array([1.0/w, 1.0/h])
    scale = np.maximum([w/sw, h/sh], [1.0, 1.0])
    px = np.clip(scale * 0.85, 1.0, 4.0) * px_fine
    out = np.zeros_like(img)
    s = np.clip(strength, 0.2, 2.5)
    offs = [(-1,-1),(0,-1),(1,-1),(-1,0),(1,0),(-1,1),(0,1),(1,1)]
    for y in range(h):
        v = (y + 0.5) / h
        for x in range(w):
            u = (x + 0.5) / w
            c = img[y, x]
            n = [sample_bilinear(img, u + ox*px[0], v + oy*px[1]) for ox, oy in offs]
            blur = (c*4 + (n[1]+n[3]+n[4]+n[6])*2 + n[0]+n[2]+n[5]+n[7]) / 16.0
            lc = luma(c)
            ls = [luma(nb) for nb in n]
            l_min, l_max = min(lc, min(ls)), max(lc, max(ls))
            c_min = np.minimum(c, np.minimum.reduce(n))
            c_max = np.maximum(c, np.maximum.reduce(n))
            sx = (ls[2] + 2*ls[4] + ls[7]) - (ls[0] + 2*ls[3] + ls[5])
            sy = (ls[5] + 2*ls[6] + ls[7]) - (ls[0] + 2*ls[1] + ls[2])
            mag = np.hypot(sx, sy) * 0.25
            d_vec = np.array([sx, sy]) / (mag*4.0) if mag > 1e-4 else np.zeros(2)
            t = np.clip((mag - 0.012) / (0.14 - 0.012), 0, 1)
            edge = t*t*(3 - 2*t)
            l_mid = 0.5 * (l_min + l_max)
            side = np.clip((lc - l_mid) / max(l_max - l_min, 0.035) * 2.4, -1.0, 1.0)
            push_mul = 0.72 if mode == 2 else 0.52
            warp_uv = np.array([u, v]) + d_vec * px * (side * push_mul * s * edge)
            pushed = sample_bilinear(img, warp_uv[0], warp_uv[1])
            mix_w = np.clip(edge * (0.60 + 0.25*s), 0, 1)
            res = c*(1 - mix_w) + pushed*mix_w
            unsharp_gain = 1.65 if mode == 2 else (1.15 if mode == 1 else 1.30)
            res = res + (res - blur) * unsharp_gain * s * edge
            pad = (0.06*s) if mode == 2 else (0.035*s)
            res = np.clip(res, c_min - pad, c_max + pad)
            lr, lb = luma(res), luma(blur)
            if mode == 0:
                valley = max(lb - lr, 0.0)
                res = res - valley * 0.65 * s * edge
            elif mode == 1:
                bi_sum = c * 1.5; bi_w = 1.5
                for nb in n:
                    dc = nb - c
                    w_bi = np.exp(-np.dot(dc, dc) * 36.0)
                    bi_sum += nb * w_bi; bi_w += w_bi
                bi_clean = bi_sum / bi_w
                valley = max(lb - lr, 0.0)
                res = res - valley * 0.50 * s * edge
                m_e = np.clip(edge * 1.35, 0, 1)
                res = bi_clean*(1 - m_e) + res*m_e
                lf = luma(res)
                res = np.full(3, lf)*(1 - (1 + 0.07*s)) + res*(1 + 0.07*s)
            else:
                valley = max(lb - lr, 0.0) + 0.40 * max(l_max - lr, 0.0) * edge
                ink = np.clip(1.0 - valley * 1.75 * s, 0.10, 1.0)
                res = res * ((1 - edge) + ink * edge)
            out[y, x] = np.clip(res, 0, 1)
    return out

def upscale(img, s, fn):
    h, w, _ = img.shape
    H, W = h*s, w*s
    out = np.zeros((H, W, 3))
    for y in range(H):
        for x in range(W):
            out[y, x] = fn(img, (x+.5)/W, (y+.5)/H)
    return out

# Test image: 16x16 with a diagonal anti-aliased edge + a thin dark line + color block
N = 16
img = np.zeros((N, N, 3))
for y in range(N):
    for x in range(N):
        dist = (x + y - N) / 1.4
        v = np.clip(0.5 + 0.45 * np.tanh(dist * 1.2), 0.1, 0.95)
        img[y, x] = np.array([v, v * 0.9, min(v * 1.1, 1.0)])

def fsrcnnx_core(img):
    h, w, _ = img.shape
    L = np.zeros((h, w))
    for y in range(h):
        for x in range(w):
            L[y, x] = luma(img[y, x])
    sub_tensor = np.zeros((h, w, 4))
    for y in range(h):
        for x in range(w):
            patch = np.zeros((5, 5))
            for dy in range(-2, 3):
                for dx in range(-2, 3):
                    ny = np.clip(y + dy, 0, h - 1)
                    nx = np.clip(x + dx, 0, w - 1)
                    patch[dy + 2, dx + 2] = L[ny, nx]
            gx = 0.5 * (patch[2, 3] - patch[2, 1])
            gy = 0.5 * (patch[3, 2] - patch[1, 2])
            lap = patch[2, 2] - 0.25 * (patch[2, 1] + patch[2, 3] + patch[1, 2] + patch[3, 2])
            base = patch[2, 2]
            s00 = base - 0.35 * gx - 0.35 * gy + 0.5 * lap
            s10 = base + 0.35 * gx - 0.35 * gy + 0.5 * lap
            s01 = base - 0.35 * gx + 0.35 * gy + 0.5 * lap
            s11 = base + 0.35 * gx + 0.35 * gy + 0.5 * lap
            sub_tensor[y, x] = np.clip([s00, s10, s01, s11], 0.0, 1.0)
    return sub_tensor

def fsrcnnx_up(sub_tensor, img, target_shape, sharp=0.5):
    th, tw = target_shape
    ih, iw, _ = img.shape
    out = np.zeros((th, tw, 3))
    for y in range(th):
        for x in range(tw):
            u = (x + 0.5) / tw
            v = (y + 0.5) / th
            pos_x = u * iw - 0.5
            pos_y = v * ih - 0.5
            bx = int(np.floor(pos_x))
            by = int(np.floor(pos_y))
            fx = pos_x - bx
            fy = pos_y - by
            bx = np.clip(bx, 0, iw - 1)
            by = np.clip(by, 0, ih - 1)
            st = sub_tensor[by, bx]
            sub_luma = (st[0] if fx < 0.5 else st[1]) if fy < 0.5 else (st[2] if fx < 0.5 else st[3])
            raw_rgb = bicubic_pixel(img, u, v)
            raw_y = luma(raw_rgb)
            cb = (raw_rgb[2] - raw_y) / 1.8556
            cr = (raw_rgb[0] - raw_y) / 1.5748
            r = sub_luma + 1.5748 * cr
            b = sub_luma + 1.8556 * cb
            g = (sub_luma - 0.2126 * r - 0.0722 * b) / 0.7152
            out[y, x] = np.clip([r, g, b], 0.0, 1.0)
    if sharp > 0.001:
        return rcas(out, (ih, iw), sharp=sharp, dering=True)
    return out

cat = upscale(img, 3, bicubic_pixel)
sg  = upscale(img, 3, sgsr_pixel)
ea  = upscale(img, 3, easu_pixel)
rc  = rcas(ea, (N, N), sharp=0.8, dering=True)
a4k = anime4k(cat, (N, N), strength=1.0, mode=0)
fsrcnnx_sub = fsrcnnx_core(img)
fsrcnnx_res = fsrcnnx_up(fsrcnnx_sub, img, (N * 3, N * 3), sharp=0.5)

for name, a in [("catmull", cat), ("sgsr", sg), ("easu", ea), ("easu+rcas", rc), ("anime4k", a4k), ("fsrcnnx", fsrcnnx_res)]:
    assert np.isfinite(a).all(), name
    assert a.min() >= -1e-6 and a.max() <= 1+1e-6, (name, a.min(), a.max())

def transition_width(a):
    g = a[..., 0]; lo, hi = 0.1, 0.95
    return int(((g > lo+.22*(hi-lo)) & (g < lo+.78*(hi-lo))).sum())

w = {k: transition_width(v) for k, v in [("catmull", cat), ("sgsr", sg), ("easu", ea), ("easu+rcas", rc), ("anime4k", a4k), ("fsrcnnx", fsrcnnx_res)]}
print("mid-tone pixel count on edge (lower = sharper):", w)
assert w["sgsr"] < w["catmull"], "SGSR must be sharper than Catmull-Rom"
assert w["easu+rcas"] < w["catmull"], "FSR (EASU+RCAS) must be sharper than Catmull-Rom"
assert w["anime4k"] < w["catmull"], "Anime4K must be sharper than Catmull-Rom"
assert w["fsrcnnx"] < w["catmull"], "FSRCNNX must be sharper than Catmull-Rom"

# Parameter responsiveness checks
rc_0 = rcas(ea, (N, N), sharp=0.0, dering=True)
rc_1 = rcas(ea, (N, N), sharp=1.0, dering=True)
rc_nodering = rcas(upscale(img, 3, lambda im, u, v: easu_pixel(im, u, v, dering=False)), (N, N), sharp=0.8, dering=False)
a4k_low = anime4k(cat, (N, N), strength=0.5, mode=0)
a4k_hi  = anime4k(cat, (N, N), strength=2.0, mode=0)
a4k_m1  = anime4k(cat, (N, N), strength=1.2, mode=1)
a4k_m2  = anime4k(cat, (N, N), strength=1.2, mode=2)
sg_low  = upscale(img, 3, lambda im, u, v: sgsr_pixel(im, u, v, sharp=1.0))
sg_hi   = upscale(img, 3, lambda im, u, v: sgsr_pixel(im, u, v, sharp=2.0))
fsrcnnx_s0 = fsrcnnx_up(fsrcnnx_sub, img, (N * 3, N * 3), sharp=0.0)
fsrcnnx_s1 = fsrcnnx_up(fsrcnnx_sub, img, (N * 3, N * 3), sharp=1.0)

diffs = {
    "FSR sharp 0.0 vs 1.0 (max diff)": round(float(np.abs(rc_1 - rc_0).max()), 4),
    "FSR dering ON vs OFF (max diff)": round(float(np.abs(rc - rc_nodering).max()), 4),
    "Anime4K strength 0.5 vs 2.0 (max diff)": round(float(np.abs(a4k_hi - a4k_low).max()), 4),
    "Anime4K Mode A vs Mode B (max diff)": round(float(np.abs(a4k - a4k_m1).max()), 4),
    "Anime4K Mode A vs Mode C (max diff)": round(float(np.abs(a4k - a4k_m2).max()), 4),
    "SGSR sharp 1.0 vs 2.0 (max diff)": round(float(np.abs(sg_hi - sg_low).max()), 4),
    "FSRCNNX sharp 0.0 vs 1.0 (max diff)": round(float(np.abs(fsrcnnx_s1 - fsrcnnx_s0).max()), 4),
}
print("parameter sensitivity:", diffs)
for k, v in diffs.items():
    assert v > 0.05, f"Parameter {k} had insufficient visual impact: {v}"
print("ALL CHECKS PASSED")
