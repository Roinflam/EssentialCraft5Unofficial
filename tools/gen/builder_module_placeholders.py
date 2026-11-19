# -*- coding: utf-8 -*-
"""建筑魔杖 6 个模块的占位贴图：拿空白模块 blank_module.png 改色，文件名就是正式名，正式图画好后直接覆盖。

  textures/item/module/builder_module_range.png       扩域（淡青）
  textures/item/module/builder_module_haste.png       疾工（红）
  textures/item/module/builder_module_efficiency.png  节能（绿）
  textures/item/module/builder_module_reach.png       远域（蓝）
  textures/item/module/builder_module_mix.png         混铺（品红）
  textures/item/module/builder_module_salvage.png     回收（琥珀）

都是 16 × 16、RGBA。改色办法同 builder_placeholders.py（有颜色的像素换色相，灰黑的不动）。
用法：python tools/gen/builder_module_placeholders.py（覆盖已有文件前先确认那里还是占位图）。
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from builder_placeholders import recolor  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MODULE = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'essentialcraft5', 'textures', 'item', 'module')

# 短名 → (色相 0~1, 饱和度倍率, 亮度倍率)
TARGETS = {
    'range': (0.48, 1.6, 1.10),
    'haste': (0.00, 1.8, 1.10),
    'efficiency': (0.33, 1.6, 1.05),
    'reach': (0.60, 1.8, 1.10),
    'mix': (0.85, 1.6, 1.10),
    'salvage': (0.10, 1.8, 1.10),
}


def main():
    src = Image.open(os.path.join(MODULE, 'blank_module.png')).convert('RGBA')
    for name, (hue, sat_mul, val_mul) in TARGETS.items():
        path = os.path.join(MODULE, 'builder_module_' + name + '.png')
        recolor(src, hue, sat_mul, val_mul).save(path)
        print('wrote', os.path.relpath(path, ROOT))
    return 0


if __name__ == '__main__':
    sys.exit(main())
