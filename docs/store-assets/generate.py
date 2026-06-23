import glob, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# ---------------------------------------------------------------- palette
NAVY_C  = (18, 60, 84)     # gradient inner
NAVY_E  = (5, 19, 30)      # gradient outer
NAVY_DK = (3, 13, 21)      # deepest
CYAN    = (88, 214, 255)
CYAN_HI = (150, 234, 255)
SKY     = (96, 234, 255)
BLUE    = (41, 150, 230)
GOLD    = (255, 196, 84)
GOLD_HI = (255, 224, 150)
WHITE   = (242, 251, 255)

OUT_DIR = '/home/kasm-user/ham-radio-wspr-txrx/docs/store-assets/'

def bold(sz):
    return ImageFont.truetype(glob.glob('/usr/share/fonts/**/DejaVuSans-Bold.ttf', recursive=True)[0], sz)
def reg(sz):
    return ImageFont.truetype(glob.glob('/usr/share/fonts/**/DejaVuSans.ttf', recursive=True)[0], sz)

# ---------------------------------------------------------------- helpers
def radial_bg(W, H, cx, cy, spread, c0, c1, c2=None):
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    r = np.sqrt((xx-cx)**2 + (yy-cy)**2) / spread
    r = np.clip(r, 0, 1)
    if c2 is None:
        rr = r[..., None]
        arr = (np.array(c0)*(1-rr) + np.array(c1)*rr)
    else:
        # three-stop: c0 -> c1 (mid) -> c2 (edge)
        t = r[..., None]
        mid = 0.55
        lo = np.clip(t/mid, 0, 1)
        hi = np.clip((t-mid)/(1-mid), 0, 1)
        arr = np.where(t < mid,
                       np.array(c0)*(1-lo) + np.array(c1)*lo,
                       np.array(c1)*(1-hi) + np.array(c2)*hi)
    return Image.fromarray(arr.astype(np.uint8), 'RGB')

def diag_bg(W, H, c0, c1):
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    t = (xx/W*0.55 + yy/H*0.45)[..., None]
    t = np.clip(t, 0, 1)
    arr = (np.array(c0)*(1-t) + np.array(c1)*t).astype(np.uint8)
    return Image.fromarray(arr, 'RGB')

def glow(layer, blur, gain=1.0):
    g = layer.filter(ImageFilter.GaussianBlur(blur))
    if gain != 1.0:
        a = np.asarray(g).astype(np.float32)
        a[..., 3] = np.clip(a[..., 3]*gain, 0, 255)
        g = Image.fromarray(a.astype(np.uint8), 'RGBA')
    return g

def vignette(img, strength=110, soft=0.10):
    N_w, N_h = img.size
    vig = Image.new('L', (N_w, N_h), 0)
    ImageDraw.Draw(vig).ellipse([-N_w*0.16, -N_h*0.16, N_w*1.16, N_h*1.16], fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(min(N_w, N_h)*soft))
    dark = Image.new('RGBA', (N_w, N_h), (0, 0, 0, 0))
    dark.putalpha(Image.eval(vig, lambda v: strength - int(v*strength/255)))
    dark = Image.composite(Image.new('RGBA', (N_w, N_h), (0, 6, 12, 255)),
                           Image.new('RGBA', (N_w, N_h), (0, 0, 0, 0)),
                           Image.eval(vig, lambda v: strength - int(v*strength/255)))
    return Image.alpha_composite(img, dark)

def lerp(a, b, t):
    return tuple(int(a[i]*(1-t) + b[i]*t) for i in range(len(a)))

# ----------------------------------------------- globe with rotated grid
def project_globe(gx, gy, R, tilt_deg, lon_lines, lat_lines, n=160):
    """Return polylines (lists of (x,y,visible)) for a tilted wireframe globe."""
    tilt = math.radians(tilt_deg)
    out = []
    # longitude meridians (vertical great circles)
    for k in range(lon_lines):
        lon = math.pi * k / lon_lines
        pts = []
        for j in range(n+1):
            lat = -math.pi/2 + math.pi*j/n
            # point on unit sphere (lon around vertical axis)
            x = math.cos(lat)*math.sin(lon)
            y = math.sin(lat)
            z = math.cos(lat)*math.cos(lon)
            # tilt around X axis
            y2 = y*math.cos(tilt) - z*math.sin(tilt)
            z2 = y*math.sin(tilt) + z*math.cos(tilt)
            pts.append((gx + R*x, gy - R*y2, z2 >= 0))
        out.append(('lon', pts))
    # latitude parallels
    for s in lat_lines:
        lat = s*math.pi/2
        pts = []
        for j in range(n+1):
            lon = -math.pi + 2*math.pi*j/n
            x = math.cos(lat)*math.sin(lon)
            y = math.sin(lat)
            z = math.cos(lat)*math.cos(lon)
            y2 = y*math.cos(tilt) - z*math.sin(tilt)
            z2 = y*math.sin(tilt) + z*math.cos(tilt)
            pts.append((gx + R*x, gy - R*y2, z2 >= 0))
        out.append(('lat', pts))
    return out

def draw_globe(layer, gx, gy, R, tilt=22, lon_lines=8, lat_lines=(-0.66,-0.33,0,0.33,0.66),
               rim=(96,214,255), front_a=150, back_a=46, w_front=3, w_back=2):
    d = ImageDraw.Draw(layer)
    # outer rim
    d.ellipse([gx-R, gy-R, gx+R, gy+R], outline=rim+(190,), width=w_front+1)
    polys = project_globe(gx, gy, R, tilt, lon_lines, lat_lines)
    # draw back faces first (dim), then front (bright)
    for visible_pass, alpha, wdt in [(False, back_a, w_back), (True, front_a, w_front)]:
        for kind, pts in polys:
            seg = []
            for (x, y, vis) in pts:
                if vis == visible_pass:
                    seg.append((x, y))
                else:
                    if len(seg) > 1:
                        d.line(seg, fill=rim+(alpha,), width=wdt, joint='curve')
                    seg = []
            if len(seg) > 1:
                d.line(seg, fill=rim+(alpha,), width=wdt, joint='curve')

# ---------------------------------------------------------------- ICON
def make_icon(out, S=512, SS=4):
    N = S*SS
    img = radial_bg(N, N, N*0.5, N*0.42, N*0.92, NAVY_C, NAVY_E, NAVY_DK).convert('RGBA')

    # soft top specular highlight (glassy)
    hi = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    ImageDraw.Draw(hi).ellipse([N*0.10, -N*0.34, N*0.90, N*0.40], fill=(255, 255, 255, 30))
    img = Image.alpha_composite(img, hi.filter(ImageFilter.GaussianBlur(N*0.07)))

    cx, cy = N*0.5, N*0.565
    R = N*0.235

    # --- globe atmospheric halo behind
    halo = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse([cx-R*1.18, cy-R*1.18, cx+R*1.18, cy+R*1.18],
                                 fill=(70, 180, 240, 60))
    img = Image.alpha_composite(img, halo.filter(ImageFilter.GaussianBlur(N*0.035)))

    # --- globe sphere subtle fill (darker than bg so grid pops)
    sphere = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    ImageDraw.Draw(sphere).ellipse([cx-R, cy-R, cx+R, cy+R], fill=(10, 38, 56, 150))
    img = Image.alpha_composite(img, sphere)

    # --- wireframe globe (high front/back contrast for clear 3D read)
    globe = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    draw_globe(globe, cx, cy, R, tilt=20, lon_lines=8,
               lat_lines=(-0.6, -0.3, 0, 0.3, 0.6),
               rim=(132, 230, 255), front_a=205, back_a=40,
               w_front=max(2, int(N*0.0046)), w_back=max(1, int(N*0.0026)))
    img = Image.alpha_composite(img, glow(globe, N*0.006, 1.3))
    img = Image.alpha_composite(img, globe)

    # --- propagation arcs emitting upward from top of globe (transmit)
    src = (cx, cy - R*0.92)   # transmit source at top of globe
    arcs = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    da = ImageDraw.Draw(arcs)
    radii = [0.085, 0.155, 0.225]
    cols  = [CYAN_HI, CYAN, lerp(CYAN, BLUE, 0.55)]
    for r, c in zip(radii, cols):
        AR = N*r
        da.arc([src[0]-AR, src[1]-AR, src[0]+AR, src[1]+AR],
               212, 328, fill=c+(255,), width=int(N*0.016))
    img = Image.alpha_composite(img, glow(arcs, N*0.013, 1.45))
    img = Image.alpha_composite(img, arcs)

    # --- gold transmit pulse at the source
    pulse = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    dp = ImageDraw.Draw(pulse)
    pr = N*0.026
    dp.ellipse([src[0]-pr, src[1]-pr, src[0]+pr, src[1]+pr], fill=GOLD+(255,))
    dp.ellipse([src[0]-pr*0.45, src[1]-pr*0.45, src[0]+pr*0.45, src[1]+pr*0.45], fill=GOLD_HI+(255,))
    img = Image.alpha_composite(img, glow(pulse, N*0.022, 1.5))
    img = Image.alpha_composite(img, pulse)

    # --- RX nodes on the front hemisphere (cyan dots) -> worldwide reception
    nodes = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    dn = ImageDraw.Draw(nodes)
    for (nx, ny) in [(cx-R*0.46, cy+R*0.10), (cx+R*0.40, cy-R*0.04), (cx-R*0.04, cy+R*0.50)]:
        nr = N*0.0125
        dn.ellipse([nx-nr, ny-nr, nx+nr, ny+nr], fill=CYAN_HI+(255,))
        dn.ellipse([nx-nr*0.42, ny-nr*0.42, nx+nr*0.42, ny+nr*0.42], fill=WHITE+(255,))
    img = Image.alpha_composite(img, glow(nodes, N*0.015, 1.45))
    img = Image.alpha_composite(img, nodes)

    img = vignette(img, strength=120, soft=0.11)
    img.convert('RGB').resize((S, S), Image.LANCZOS).save(out)

# ------------------------------------------------------- FEATURE GRAPHIC
def make_feature(out, W=1024, H=500, SS=3):
    Wn, Hn = W*SS, H*SS
    img = diag_bg(Wn, Hn, (14, 50, 70), (4, 15, 25)).convert('RGBA')

    # large soft radial light source behind the globe (right side)
    glowbg = radial_bg(Wn, Hn, Wn*0.78, Hn*0.52, Wn*0.45,
                       (26, 78, 108), (10, 34, 50), (4, 15, 25)).convert('RGBA')
    glowbg.putalpha(150)
    img = Image.alpha_composite(img, glowbg)

    # faint star/spot field
    rng = np.random.default_rng(11)
    sf = Image.new('RGBA', (Wn, Hn), (0, 0, 0, 0)); ds = ImageDraw.Draw(sf)
    for _ in range(150):
        x, y = rng.integers(0, Wn), rng.integers(0, Hn)
        a = int(rng.integers(18, 64)); r = rng.integers(1, 3)*SS
        ds.ellipse([x-r, y-r, x+r, y+r], fill=(160, 215, 245, a))
    img = Image.alpha_composite(img, sf)

    gx, gy, R = int(Wn*0.760), int(Hn*0.50), int(Hn*0.395)

    # globe atmospheric halo
    halo = Image.new('RGBA', (Wn, Hn), (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse([gx-R*1.16, gy-R*1.16, gx+R*1.16, gy+R*1.16],
                                 fill=(70, 175, 235, 55))
    img = Image.alpha_composite(img, halo.filter(ImageFilter.GaussianBlur(Wn*0.018)))

    # globe fill
    sphere = Image.new('RGBA', (Wn, Hn), (0, 0, 0, 0))
    ImageDraw.Draw(sphere).ellipse([gx-R, gy-R, gx+R, gy+R], fill=(9, 34, 50, 130))
    img = Image.alpha_composite(img, sphere)

    # wireframe globe
    globe = Image.new('RGBA', (Wn, Hn), (0, 0, 0, 0))
    draw_globe(globe, gx, gy, R, tilt=20, lon_lines=9,
               lat_lines=(-0.66, -0.33, 0, 0.33, 0.66),
               rim=(104, 216, 255), front_a=150, back_a=44,
               w_front=max(2, Wn//900), w_back=max(1, Wn//1300))
    img = Image.alpha_composite(img, glow(globe, Wn*0.003, 1.2))
    img = Image.alpha_composite(img, globe)

    # --- great-circle propagation arcs from one TX node to several RX nodes
    tx = (gx - R*0.34, gy - R*0.14)
    rx_nodes = [(gx + R*0.42, gy - R*0.42),
                (gx + R*0.50, gy + R*0.18),
                (gx - R*0.02, gy + R*0.58),
                (gx - R*0.62, gy + R*0.32)]
    paths = Image.new('RGBA', (Wn, Hn), (0, 0, 0, 0)); dp = ImageDraw.Draw(paths)

    def carc(p, q, col, lift, wd):
        mx, my = (p[0]+q[0])/2, (p[1]+q[1])/2 - lift
        prev = None
        for t in np.linspace(0, 1, 90):
            x = (1-t)**2*p[0] + 2*(1-t)*t*mx + t*t*q[0]
            y = (1-t)**2*p[1] + 2*(1-t)*t*my + t*t*q[1]
            if prev is not None:
                dp.line([prev, (x, y)], fill=col, width=wd)
            prev = (x, y)

    arc_cols = [GOLD+(235,), CYAN_HI+(235,), CYAN+(225,), lerp(CYAN, BLUE, 0.4)+(220,)]
    lifts = [R*0.55, R*0.42, R*0.30, R*0.48]
    for (q, c, lf) in zip(rx_nodes, arc_cols, lifts):
        carc(tx, q, c, lf, max(2, Wn//360))

    # nodes: TX (gold) + RX (cyan)
    for (nx, ny) in rx_nodes:
        nr = Wn*0.0055
        dp.ellipse([nx-nr, ny-nr, nx+nr, ny+nr], fill=CYAN_HI+(255,))
    tr = Wn*0.0085
    dp.ellipse([tx[0]-tr, tx[1]-tr, tx[0]+tr, tx[1]+tr], fill=GOLD+(255,))
    dp.ellipse([tx[0]-tr*0.5, tx[1]-tr*0.5, tx[0]+tr*0.5, tx[1]+tr*0.5], fill=GOLD_HI+(255,))

    img = Image.alpha_composite(img, glow(paths, Wn*0.005, 1.5))
    img = Image.alpha_composite(img, paths)

    # ---------------- text block (left)
    txt = Image.new('RGBA', (Wn, Hn), (0, 0, 0, 0)); dt = ImageDraw.Draw(txt)
    x0 = int(Wn*0.065)
    fT = bold(int(Hn*0.150))
    line_h = int(Hn*0.165)
    y1 = int(Hn*0.235)
    y2 = y1 + line_h
    dt.text((x0, y1), "Ham Radio", font=fT, fill=WHITE+(255,))
    dt.text((x0, y2), "WSPR ", font=fT, fill=WHITE+(255,))
    wname = dt.textlength("WSPR ", font=fT)
    # subtle cyan glow behind TX/RX (transmit accent)
    txglow = Image.new('RGBA', (Wn, Hn), (0, 0, 0, 0))
    ImageDraw.Draw(txglow).text((x0+wname, y2), "TX/RX", font=fT, fill=CYAN+(255,))
    img = Image.alpha_composite(img, glow(txglow, Wn*0.006, 1.2))
    dt.text((x0+wname, y2), "TX/RX", font=fT, fill=CYAN_HI+(255,))
    # accent rule
    ry = y2 + line_h + int(Hn*0.01)
    dt.rounded_rectangle([x0, ry, x0+int(Wn*0.105), ry+int(Hn*0.018)],
                         radius=Hn//110, fill=CYAN+(255,))
    # tagline
    dt.text((x0, ry+int(Hn*0.055)), "See who's hearing you — and transmit WSPR.",
            font=reg(int(Hn*0.058)), fill=(206, 224, 236, 255))

    # text soft drop shadow for legibility against artwork
    sh = txt.filter(ImageFilter.GaussianBlur(Wn*0.0035))
    sh_arr = np.asarray(sh).astype(np.float32)
    sh_arr[..., 0:3] = 0
    sh_arr[..., 3] = np.clip(sh_arr[..., 3]*1.1, 0, 255)
    sh = Image.fromarray(sh_arr.astype(np.uint8), 'RGBA')
    img = Image.alpha_composite(img, sh)
    img = Image.alpha_composite(img, txt)

    img = vignette(img, strength=90, soft=0.13)
    img.convert('RGB').resize((W, H), Image.LANCZOS).save(out, quality=95)

if __name__ == '__main__':
    make_icon(OUT_DIR + 'play-icon-512.png')
    make_feature(OUT_DIR + 'feature-graphic-1024x500.png')
    for p in [OUT_DIR + 'play-icon-512.png', OUT_DIR + 'feature-graphic-1024x500.png']:
        im = Image.open(p)
        import os
        print(p, im.size, im.mode, f'{os.path.getsize(p)/1024:.0f} KB')
    print('done')
