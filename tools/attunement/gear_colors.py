"""配色原型：贴图主色 → 提示框上读得清的亮度 → 逐阶往源质色（MRU 薰衣草 / 紫罗兰 / 矩阵品红）收拢。输出色样图和 Java 用的表。"""
import math
import sys
from PIL import Image, ImageDraw, ImageFont

OWN = {  # 贴图饱和色直方图第一名（hist.py）
    'ember_sword': (0xC10000, 2),
    'verdict_blade': (0xF45BE9, 5),
    'mru_gun': (0xBD5388, 4),
    'mhs_armor': (0x721560, 2),
    'wind_armor': (0x34AC34, 3),
    'void_armor': (0x10011D, 4),
    'inquisitorium_armor': (0x6F2929, 4),
    'computer_armor': (0x960004, 5),
}
LAVENDER = 0xC9A6FF
VIOLET = 0x9A5CFF
MAGENTA = 0xFF3FD2


def srgb_to_lin(c):
    c /= 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def lin_to_srgb(c):
    c = max(0.0, min(1.0, c))
    c = 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055
    return round(c * 255)


def to_lab(rgb):
    r, g, b = (srgb_to_lin((rgb >> s) & 255) for s in (16, 8, 0))
    l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    l, m, s = (x ** (1 / 3) if x > 0 else 0 for x in (l, m, s))
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)


def from_lab(lab):
    L, a, b = lab
    l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    bb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    return (lin_to_srgb(r) << 16) | (lin_to_srgb(g) << 8) | lin_to_srgb(bb)


def readable(rgb, L=0.76, cmin=0.10, cmax=0.17):
    """提示框深底上读得清：亮度定到 L，彩度夹在 [cmin, cmax]"""
    l, a, b = to_lab(rgb)
    c = math.hypot(a, b)
    h = math.atan2(b, a)
    c = max(cmin, min(cmax, c))
    return (L, c * math.cos(h), c * math.sin(h))


def lerp(p, q, t):
    return tuple(x + (y - x) * t for x, y in zip(p, q))


def ladder(own, max_stage):
    base = readable(own)
    lav, vio, mag = readable(LAVENDER, 0.80), readable(VIOLET, 0.72), readable(MAGENTA, 0.74)
    out = []
    for s in range(1, max_stage):
        f = (s - 1) / max(1, max_stage - 2) if max_stage > 2 else 0.0
        # 1 阶 = 自身色；越往上越往紫罗兰收，最高的非满阶收一半
        out.append(from_lab(lerp(base, vio, 0.6 * f)))
    grad = [from_lab(base), from_lab(vio), from_lab(mag)]
    return out, grad


def contrast(c1, c2):
    def lum(c):
        return 0.2126 * srgb_to_lin((c >> 16) & 255) + 0.7152 * srgb_to_lin((c >> 8) & 255) + 0.0722 * srgb_to_lin(c & 255)
    a, b = sorted((lum(c1), lum(c2)), reverse=True)
    return (a + 0.05) / (b + 0.05)


if __name__ == '__main__':
    BG = 0x100010
    font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 18)
    rows = []
    for name, (own, mx) in OWN.items():
        st, gr = ladder(own, mx)
        rows.append((name, own, st, gr))
        print(name, 'own #%06X' % own, 'stages', ['#%06X' % c for c in st], 'grad', ['#%06X' % c for c in gr],
              'contrast', [round(contrast(c, BG), 1) for c in st + gr])
    W, H = 900, 44 * len(rows) + 10
    im = Image.new('RGB', (W, H), (16, 0, 16))
    d = ImageDraw.Draw(im)
    for i, (name, own, st, gr) in enumerate(rows):
        y = 8 + i * 44
        d.rectangle([8, y, 36, y + 28], fill='#%06X' % own)
        x = 50
        for s, c in enumerate(st, 1):
            d.text((x, y + 2), '%d阶 名字' % s, fill='#%06X' % c, font=font)
            x += 110
        text = name + ' 满阶'
        n = len(text)
        for j, ch in enumerate(text):
            t = j / (n - 1) * 2
            k = min(1, int(t))
            c = from_lab(lerp(to_lab(gr[k]), to_lab(gr[k + 1]), t - k))
            d.text((x, y + 2), ch, fill='#%06X' % c, font=font)
            x += font.getlength(ch)
    im.save(sys.argv[1] if len(sys.argv) > 1 else 'proto.png')
