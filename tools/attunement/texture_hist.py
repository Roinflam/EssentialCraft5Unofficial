"""同调装备贴图的饱和色直方图：每件取饱和度够、不太暗的像素，按色相 10 度分桶，按 饱和度 x 明度 加权，报主色。"""
import colorsys
import os
import sys
SMIN = float(sys.argv[1]) if len(sys.argv) > 1 else 0.30
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'essentialcraft5', 'textures', '')
SETS = {
    'ember_sword': ['item/tools/elementalsword.png'],
    'verdict_blade': ['item/tools/verdict_blade.png'],
    'gun': [f'item/guns/{g}/{p}/mru.png' for g in ('pistol', 'rifle', 'sniper', 'gatling') for p in ('base', 'device')],
    'mhs': ['special/armor/armorec3magic_0.png', 'special/armor/armorec3magic_1.png'],
    'wind': ['item/armor/wind_helmet.png', 'item/armor/wind_chestplate.png', 'item/armor/wind_leggings.png', 'item/armor/wind_boots.png'],
    'void': ['special/armor/armorec3void_0.png', 'special/armor/armorec3void_1.png'],
    'inquisitorium': ['special/armor/armorec3inquisitorium_0.png', 'special/armor/armorec3inquisitorium_1.png'],
    'computer': ['item/armor/computer_helmet.png', 'item/armor/computer_chestplate.png', 'item/armor/computer_leggings.png', 'item/armor/computer_boots.png'],
}


def analyze(paths):
    buckets = {}
    total = 0
    for p in paths:
        im = Image.open(p if ':' in p else ROOT + p).convert('RGBA')
        if im.width > 256:
            im = im.resize((im.width // 4, im.height // 4), Image.NEAREST)
        for r, g, b, a in im.getdata():
            if a < 128:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s < SMIN or v < 0.25:
                continue
            w = s * v
            k = int(h * 36) % 36
            e = buckets.setdefault(k, [0.0, 0.0, 0.0, 0.0, 0.0])
            e[0] += w
            e[1] += r * w
            e[2] += g * w
            e[3] += b * w
            e[4] += 1
            total += w
    ranked = sorted(buckets.items(), key=lambda kv: -kv[1][0])
    out = []
    for k, e in ranked[:4]:
        c = (round(e[1] / e[0]), round(e[2] / e[0]), round(e[3] / e[0]))
        out.append('h%3d %5.1f%% #%02X%02X%02X n=%d' % (k * 10, 100 * e[0] / total, *c, e[4]))
    return out


for name, paths in SETS.items():
    print(name, ' | '.join(analyze(paths)))
