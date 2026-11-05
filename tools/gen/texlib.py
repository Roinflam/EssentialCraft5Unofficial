"""
贴图脚本共用的小工具（只用标准库）：8 位 PNG 读写、sRGB ↔ CIELAB（D65）、色差 ΔE76 / ΔE2000。
void_veins.py、dread_textures.py 用；GameTest HoannaTextureTests 里有同样公式的 Java 版。
"""
import math
import struct
import zlib

WHITE_X, WHITE_Z = 0.95047, 1.08883


# ---------------------------------------------------------------- PNG

def read_png(data):
    """8 位 RGB / RGBA / 灰度 / 调色板（带 tRNS）、不隔行的 PNG → (宽, 高, [(r, g, b, a)] 按行)"""
    pos, idat, palette, trns = 8, b'', None, None
    width = height = ctype = None
    while pos < len(data):
        length, kind = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        if kind == b'IHDR':
            width, height, depth, ctype, _, _, interlace = struct.unpack('>IIBBBBB', body)
            if depth != 8 or interlace != 0:
                raise ValueError(f'只支持 8 位、不隔行的 PNG（位深 {depth}，隔行 {interlace}）')
        elif kind == b'PLTE':
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b'tRNS':
            trns = list(body)
        elif kind == b'IDAT':
            idat += body
        pos += 12 + length
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    stride = width * channels
    prev, i, out = bytearray(stride), 0, []
    for _ in range(height):
        filt, line = raw[i], bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - channels] if x >= channels else 0
            b = prev[x]
            c = prev[x - channels] if x >= channels else 0
            if filt == 1:
                line[x] = (line[x] + a) & 255
            elif filt == 2:
                line[x] = (line[x] + b) & 255
            elif filt == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif filt == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        for x in range(width):
            px = line[x * channels:(x + 1) * channels]
            if ctype == 3:
                r, g, b = palette[px[0]]
                out.append((r, g, b, trns[px[0]] if trns and px[0] < len(trns) else 255))
            elif ctype == 6:
                out.append(tuple(px))
            elif ctype == 2:
                out.append((px[0], px[1], px[2], 255))
            elif ctype == 4:
                out.append((px[0], px[0], px[0], px[1]))
            else:
                out.append((px[0], px[0], px[0], 255))
        prev = line
    return width, height, out


def read_png_file(path):
    with open(path, 'rb') as f:
        return read_png(f.read())


def write_png(path, width, height, pixels):
    """写 8 位 RGBA PNG，pixels 为按行的 (r, g, b, a)"""
    raw = b''.join(b'\x00' + b''.join(struct.pack('4B', *pixels[y * width + x]) for x in range(width)) for y in range(height))

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = (b'\x89PNG\r\n\x1a\n'
           + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
           + chunk(b'IDAT', zlib.compress(raw, 9))
           + chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(png)


# ---------------------------------------------------------------- 颜色

def srgb_to_linear(c):
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def linear_to_srgb(c):
    c = min(1.0, max(0.0, c))
    return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


def linear_to_lab(rgb):
    r, g, b = rgb
    x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
    y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b

    def f(t):
        return t ** (1 / 3) if t > 216 / 24389 else (24389 / 27 * t + 16) / 116

    fx, fy, fz = f(x / WHITE_X), f(y), f(z / WHITE_Z)
    return 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)


def srgb_to_lab(rgb):
    return linear_to_lab([srgb_to_linear(c) for c in rgb])


def lab_to_linear(lab):
    l, a, b = lab
    fy = (l + 16) / 116
    fx, fz = fy + a / 500, fy - b / 200

    def finv(t):
        return t ** 3 if t ** 3 > 216 / 24389 else (116 * t - 16) / (24389 / 27)

    x, y, z = finv(fx) * WHITE_X, finv(fy), finv(fz) * WHITE_Z
    return (3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
            -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
            0.0556434 * x - 0.2040259 * y + 1.0572252 * z)


def lch_to_srgb(l, c, h):
    """CIELCh（h 为弧度）→ 8 位 sRGB；超出 sRGB 色域时保持 L* 和色相，逐步降彩度直到装得下"""
    for _ in range(60):
        lin = lab_to_linear((l, c * math.cos(h), c * math.sin(h)))
        if min(lin) >= -1e-4 and max(lin) <= 1 + 1e-4:
            break
        c *= 0.95
    return tuple(max(0, min(255, round(linear_to_srgb(v) * 255))) for v in lin)


def delta_e76(a, b):
    return math.dist(a, b)


def delta_e2000(lab1, lab2):
    l1, a1, b1 = lab1
    l2, a2, b2 = lab2
    c_bar = (math.hypot(a1, b1) + math.hypot(a2, b2)) / 2
    g = 0.5 * (1 - math.sqrt(c_bar ** 7 / (c_bar ** 7 + 25 ** 7)))
    a1p, a2p = (1 + g) * a1, (1 + g) * a2
    c1p, c2p = math.hypot(a1p, b1), math.hypot(a2p, b2)
    h1p = math.degrees(math.atan2(b1, a1p)) % 360
    h2p = math.degrees(math.atan2(b2, a2p)) % 360
    dl, dc = l2 - l1, c2p - c1p
    if c1p * c2p == 0:
        dh = 0
    else:
        dh = h2p - h1p
        if dh > 180:
            dh -= 360
        elif dh < -180:
            dh += 360
    d_h = 2 * math.sqrt(c1p * c2p) * math.sin(math.radians(dh / 2))
    l_bar, cp_bar = (l1 + l2) / 2, (c1p + c2p) / 2
    if c1p * c2p == 0:
        h_bar = h1p + h2p
    elif abs(h1p - h2p) <= 180:
        h_bar = (h1p + h2p) / 2
    else:
        h_bar = (h1p + h2p + 360) / 2 if h1p + h2p < 360 else (h1p + h2p - 360) / 2
    t = (1 - 0.17 * math.cos(math.radians(h_bar - 30)) + 0.24 * math.cos(math.radians(2 * h_bar))
         + 0.32 * math.cos(math.radians(3 * h_bar + 6)) - 0.20 * math.cos(math.radians(4 * h_bar - 63)))
    d_theta = 30 * math.exp(-((h_bar - 275) / 25) ** 2)
    r_c = 2 * math.sqrt(cp_bar ** 7 / (cp_bar ** 7 + 25 ** 7))
    s_l = 1 + 0.015 * (l_bar - 50) ** 2 / math.sqrt(20 + (l_bar - 50) ** 2)
    s_c = 1 + 0.045 * cp_bar
    s_h = 1 + 0.015 * cp_bar * t
    r_t = -math.sin(math.radians(2 * d_theta)) * r_c
    return math.sqrt((dl / s_l) ** 2 + (dc / s_c) ** 2 + (d_h / s_h) ** 2 + r_t * (dc / s_c) * (d_h / s_h))


def texture_stats(pixels):
    """不透明像素（alpha ≥ 128）的平均 L*、sRGB 平均色的 L*、平均 a* b* 的色相角（度）与彩度"""
    labs, total = [], [0, 0, 0]
    for r, g, b, a in pixels:
        if a < 128:
            continue
        labs.append(srgb_to_lab((r, g, b)))
        total[0] += r
        total[1] += g
        total[2] += b
    n = len(labs)
    mean = tuple(round(v / n) for v in total)
    a_mean = sum(lab[1] for lab in labs) / n
    b_mean = sum(lab[2] for lab in labs) / n
    return {
        'mean_l': sum(lab[0] for lab in labs) / n,
        'l_of_mean': srgb_to_lab(mean)[0],
        'mean_hex': '#%02X%02X%02X' % mean,
        'hue': math.degrees(math.atan2(b_mean, a_mean)) % 360,
        'chroma': math.hypot(a_mean, b_mean),
    }
