#version 300 es
precision highp float;
precision highp int;
precision highp sampler2D;
//============================================================================================================
// Official AMD FidelityFX Super Resolution 1.0 (FSR 1.0) - EASU Pass (FsrEasuF / FsrEasuSetF / FsrEasuTapF)
// Ported directly from GPUOpen-Effects/FidelityFX-FSR (ffx_fsr1.h, MIT License).
//============================================================================================================

uniform sampler2D u_tex;
uniform vec2  u_inSize;
uniform int   u_dering;      // 1 = official min4/max4 deringing, 0 = unclipped negative lobe
in vec2 v_uv;
out vec4 o_color;

vec3 fetchC(ivec2 p, ivec2 s) {
    return texelFetch(u_tex, clamp(p, ivec2(0), s - 1), 0).rgb;
}

// Official FsrEasuTapF from ffx_fsr1.h (lines 239-272)
void FsrEasuTapF(
    inout vec3 aC,
    inout float aW,
    vec2 off,
    vec2 dir,
    vec2 len,
    float lob,
    float clp,
    vec3 c
) {
    vec2 v;
    v.x = (off.x * dir.x) + (off.y * dir.y);
    v.y = (off.x * (-dir.y)) + (off.y * dir.x);
    v *= len;
    float d2 = v.x * v.x + v.y * v.y;
    d2 = min(d2, clp);
    // (25/16 * (2/5 * x^2 - 1)^2 - (25/16 - 1)) * (lob * x^2 - 1)^2
    float wB = (2.0 / 5.0) * d2 - 1.0;
    float wA = lob * d2 - 1.0;
    wB *= wB;
    wA *= wA;
    wB = (25.0 / 16.0) * wB - (25.0 / 16.0 - 1.0);
    float w = wB * wA;
    aC += c * w;
    aW += w;
}

// Official FsrEasuSetF from ffx_fsr1.h (lines 275-313)
void FsrEasuSetF(
    inout vec2 dir,
    inout float len,
    vec2 pp,
    bool biS, bool biT, bool biU, bool biV,
    float lA, float lB, float lC, float lD, float lE
) {
    float w = 0.0;
    if (biS) w = (1.0 - pp.x) * (1.0 - pp.y);
    if (biT) w = pp.x * (1.0 - pp.y);
    if (biU) w = (1.0 - pp.x) * pp.y;
    if (biV) w = pp.x * pp.y;
    float dc = lD - lC;
    float cb = lC - lB;
    float lenX = 1.0 / max(max(abs(dc), abs(cb)), 1e-5);
    float dirX = lD - lB;
    dir.x += dirX * w;
    lenX = clamp(abs(dirX) * lenX, 0.0, 1.0);
    lenX *= lenX;
    len += lenX * w;
    float ec = lE - lC;
    float ca = lC - lA;
    float lenY = 1.0 / max(max(abs(ec), abs(ca)), 1e-5);
    float dirY = lE - lA;
    dir.y += dirY * w;
    lenY = clamp(abs(dirY) * lenY, 0.0, 1.0);
    lenY *= lenY;
    len += lenY * w;
}

float easuLuma(vec3 c) {
    // Official ffx_fsr1.h luma*2: b*0.5 + (r*0.5 + g)
    return c.b * 0.5 + (c.r * 0.5 + c.g);
}

void main() {
    ivec2 s = ivec2(u_inSize);
    vec2 pp = v_uv * u_inSize - 0.5;
    vec2 fp = floor(pp);
    pp -= fp;
    ivec2 ip = ivec2(fp);

    // 12-tap kernel layout from ffx_fsr1.h:
    //    b c
    //  e f g h
    //  i j k l
    //    n o
    vec3 b = fetchC(ip + ivec2( 0, -1), s);
    vec3 c = fetchC(ip + ivec2( 1, -1), s);
    vec3 e = fetchC(ip + ivec2(-1,  0), s);
    vec3 f = fetchC(ip + ivec2( 0,  0), s);
    vec3 g = fetchC(ip + ivec2( 1,  0), s);
    vec3 h = fetchC(ip + ivec2( 2,  0), s);
    vec3 i = fetchC(ip + ivec2(-1,  1), s);
    vec3 j = fetchC(ip + ivec2( 0,  1), s);
    vec3 k = fetchC(ip + ivec2( 1,  1), s);
    vec3 l = fetchC(ip + ivec2( 2,  1), s);
    vec3 n = fetchC(ip + ivec2( 0,  2), s);
    vec3 o = fetchC(ip + ivec2( 1,  2), s);

    float bL = easuLuma(b), cL = easuLuma(c);
    float eL = easuLuma(e), fL = easuLuma(f), gL = easuLuma(g), hL = easuLuma(h);
    float iL = easuLuma(i), jL = easuLuma(j), kL = easuLuma(k), lL = easuLuma(l);
    float nL = easuLuma(n), oL = easuLuma(o);

    vec2 dir = vec2(0.0);
    float len = 0.0;
    FsrEasuSetF(dir, len, pp, true,  false, false, false, bL, eL, fL, gL, jL);
    FsrEasuSetF(dir, len, pp, false, true,  false, false, cL, fL, gL, hL, kL);
    FsrEasuSetF(dir, len, pp, false, false, true,  false, fL, iL, jL, kL, nL);
    FsrEasuSetF(dir, len, pp, false, false, false, true,  gL, jL, kL, lL, oL);

    vec2 dir2 = dir * dir;
    float dirR = dir2.x + dir2.y;
    bool zro = dirR < (1.0 / 32768.0);
    dirR = inversesqrt(max(dirR, 1e-8));
    dirR = zro ? 1.0 : dirR;
    dir.x = zro ? 1.0 : dir.x;
    dir *= vec2(dirR);

    len = len * 0.5;
    len *= len;

    float stretch = (dir.x * dir.x + dir.y * dir.y) / max(max(abs(dir.x), abs(dir.y)), 1e-5);
    vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 - 0.5 * len);
    float lob = 0.5 + ((1.0 / 4.0 - 0.04) - 0.5) * len;
    float clp = 1.0 / lob;

    vec3 min4 = min(min(f, g), min(j, k));
    vec3 max4 = max(max(f, g), max(j, k));

    vec3 aC = vec3(0.0);
    float aW = 0.0;
    FsrEasuTapF(aC, aW, vec2( 0.0, -1.0) - pp, dir, len2, lob, clp, b);
    FsrEasuTapF(aC, aW, vec2( 1.0, -1.0) - pp, dir, len2, lob, clp, c);
    FsrEasuTapF(aC, aW, vec2(-1.0,  1.0) - pp, dir, len2, lob, clp, i);
    FsrEasuTapF(aC, aW, vec2( 0.0,  1.0) - pp, dir, len2, lob, clp, j);
    FsrEasuTapF(aC, aW, vec2( 0.0,  0.0) - pp, dir, len2, lob, clp, f);
    FsrEasuTapF(aC, aW, vec2(-1.0,  0.0) - pp, dir, len2, lob, clp, e);
    FsrEasuTapF(aC, aW, vec2( 1.0,  1.0) - pp, dir, len2, lob, clp, k);
    FsrEasuTapF(aC, aW, vec2( 2.0,  1.0) - pp, dir, len2, lob, clp, l);
    FsrEasuTapF(aC, aW, vec2( 2.0,  0.0) - pp, dir, len2, lob, clp, h);
    FsrEasuTapF(aC, aW, vec2( 1.0,  0.0) - pp, dir, len2, lob, clp, g);
    FsrEasuTapF(aC, aW, vec2( 1.0,  2.0) - pp, dir, len2, lob, clp, o);
    FsrEasuTapF(aC, aW, vec2( 0.0,  2.0) - pp, dir, len2, lob, clp, n);

    vec3 pix = aC / max(aW, 1e-5);
    if (u_dering == 1) {
        pix = min(max4, max(min4, pix));
    }
    o_color = vec4(clamp(pix, 0.0, 1.0), 1.0);
}
