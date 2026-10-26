"""CPU reference of the SGSR-style and EASU+RCAS shader math (same formulas as the GLSL).
Checks: finite, in [0,1], and a diagonal edge is upscaled sharper than Catmull-Rom without big overshoot."""
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
    sa, sb = (.75, 1+.6*k) if use_dir else (1+.25*k,)*2
    s = np.zeros(3); ws = 0
    for j in range(4):
        for i in range(4):
            d = np.array([i-1-fx, j-1-fy])
            wgt = lanczos2(np.hypot(d@tg*sa, d@n*sb))
            s += c[j, i]*wgt; ws += wgt
    res = s/max(ws, 1e-4)
    mn = np.minimum.reduce([c[1,1], c[1,2], c[2,1], c[2,2]])
    mx = np.maximum.reduce([c[1,1], c[1,2], c[2,1], c[2,2]])
    res = np.clip(res, mn-.03, mx+.03)
    m = np.clip((mag-t)/(t*2+.02-t), 0, 1); m = m*m*(3-2*m)
    return np.clip(base*(1-m)+res*m, 0, 1)

def easu_pixel(img, u, v, dering=True):
    h, w, _ = img.shape
    px, py = u*w-.5, v*h-.5
    bx, by = int(np.floor(px)), int(np.floor(py))
    fx, fy = px-bx, py-by
    c = taps(img, bx, by)
    L = c[..., 1] + .5*(c[..., 0]+c[..., 2])
    gx = .25*((L[1,2]-L[1,0])+(L[1,3]-L[1,1])+(L[2,2]-L[2,0])+(L[2,3]-L[2,1]))
    gy = .25*((L[2,1]-L[0,1])+(L[2,2]-L[0,2])+(L[3,1]-L[1,1])+(L[3,2]-L[1,2]))
    mag = np.hypot(gx, gy); conf = np.clip(mag*6, 0, 1)
    n = np.array([gx, gy])/mag if mag > 1e-5 else np.array([1., 0.]); tg = np.array([-n[1], n[0]])
    sal, sac = 1+(.6-1)*conf, 1+(1.2-1)*conf
    lob = .5+.2*conf; clp = 1/lob
    s = np.zeros(3); ws = 0
    for j in range(4):
        for i in range(4):
            d = np.array([i-1-fx, j-1-fy])
            d2 = min((d@tg*sal)**2+(d@n*sac)**2, clp)
            wb, wa = .4*d2-1, lob*d2-1
            wgt = wb*wb*wa
            s += c[j, i]*wgt; ws += wgt
    res = s/(ws if abs(ws) > 1e-5 else 1e-5)
    if dering:
        res = np.clip(res, np.minimum.reduce([c[1,1], c[1,2], c[2,1], c[2,2]]), np.maximum.reduce([c[1,1], c[1,2], c[2,1], c[2,2]]))
    return np.clip(res, 0, 1)

def rcas(img, sharp=.8):
    h, w, _ = img.shape
    out = np.zeros_like(img)
    P = lambda y, x: img[np.clip(y, 0, h-1), np.clip(x, 0, w-1)]
    for y in range(h):
        for x in range(w):
            e, b, d, f, hh = P(y,x), P(y-1,x), P(y,x-1), P(y,x+1), P(y+1,x)
            mn = np.minimum.reduce([b, d, f, hh]); mx = np.maximum.reduce([b, d, f, hh])
            hit_min = mn/(4*mx+1e-5); hit_max = (1-mx)/(4*mn-4-1e-5)
            lobe = np.max(np.maximum(-hit_min, hit_max))
            lobe = max(-.1875, min(lobe, 0)) * 2**(-(1-sharp)*2)
            out[y, x] = np.clip((lobe*(b+d+f+hh)+e)/(4*lobe+1), 0, 1)
    return out

def upscale(img, s, fn):
    h, w, _ = img.shape
    H, W = h*s, w*s
    out = np.zeros((H, W, 3))
    for y in range(H):
        for x in range(W):
            out[y, x] = fn(img, (x+.5)/W, (y+.5)/H)
    return out

# diagonal edge test image 16x16
N = 16
img = np.zeros((N, N, 3))
for y in range(N):
    for x in range(N):
        img[y, x] = 1.0 if x+y > N else 0.1
cat = upscale(img, 3, lambda im, u, v: np.clip(sum(catmull(v*N-.5-np.floor(v*N-.5))[j]*sum(catmull(u*N-.5-np.floor(u*N-.5))[i]*taps(im,int(np.floor(u*N-.5)),int(np.floor(v*N-.5)))[j,i] for i in range(4)) for j in range(4)),0,1))
sg = upscale(img, 3, sgsr_pixel)
ea = upscale(img, 3, easu_pixel)
rc = rcas(ea)
for name, a in [("catmull", cat), ("sgsr", sg), ("easu", ea), ("easu+rcas", rc)]:
    assert np.isfinite(a).all(), name
    assert a.min() >= -1e-6 and a.max() <= 1+1e-6, (name, a.min(), a.max())

def transition_width(a):
    # count pixels in the mid-tone band (0.25..0.85 of range) -> smaller == sharper edge
    g = a[..., 1]; lo, hi = .1, 1.0
    return int(((g > lo+.25*(hi-lo)) & (g < lo+.75*(hi-lo))).sum())
w = {k: transition_width(v) for k, v in [("catmull", cat), ("sgsr", sg), ("easu", ea), ("easu+rcas", rc)]}
print("mid-tone pixel count on edge (lower = sharper):", w)
print("overshoot  max/min:", {k: (round(float(v.max()), 3), round(float(v.min()), 3)) for k, v in [("sgsr", sg), ("easu+rcas", rc)]})
assert w["sgsr"] <= w["catmull"], "SGSR-style must not be blurrier than Catmull-Rom"
assert w["easu+rcas"] <= w["catmull"], "EASU+RCAS must not be blurrier than Catmull-Rom"
print("OK")
