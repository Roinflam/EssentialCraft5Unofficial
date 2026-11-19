# -*- coding: utf-8 -*-
"""建筑魔杖档位的占位贴图：拿建筑魔杖的 staff_void.png 改色，文件名就是正式名，正式图画好后直接覆盖。

  textures/item/spells/paving_wand.png          一阶 铺设魔杖（淡青调）
  textures/item/spells/staff_void_ascended.png  三阶 建筑魔杖·升格，5 个模式物品共用（金调）

都是 16 × 16、RGBA。用法：python tools/gen/builder_placeholders.py（覆盖已有文件前先确认那里还是占位图）。
"""
import colorsys
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPELLS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'essentialcraft5', 'textures', 'item', 'spells')

# 名字 → (色相 0~1, 饱和度倍率, 亮度倍率)
TARGETS = {
    'paving_wand': (0.48, 0.75, 1.10),
    'staff_void_ascended': (0.12, 1.15, 1.15),
}


def recolor(src, hue, sat_mul, val_mul):
    out = Image.new('RGBA', src.size)
    px = src.load()
    dst = out.load()
    for y in range(src.size[1]):
        for x in range(src.size[0]):
            r, g, b, a = px[x, y]
            if a == 0:
                dst[x, y] = (0, 0, 0, 0)
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            # 杖身的灰黑（饱和度低）不改色，只改有颜色的部分
            if s > 0.15:
                h = hue
                s = min(1.0, s * sat_mul)
                v = min(1.0, v * val_mul)
            nr, ng, nb = colorsys.hsv_to_rgb(h, s, v)
            dst[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def main():
    src = Image.open(os.path.join(SPELLS, 'staff_void.png')).convert('RGBA')
    for name, (hue, sat_mul, val_mul) in TARGETS.items():
        path = os.path.join(SPELLS, name + '.png')
        recolor(src, hue, sat_mul, val_mul).save(path)
        print('wrote', os.path.relpath(path, ROOT))
    return 0


if __name__ == '__main__':
    sys.exit(main())
