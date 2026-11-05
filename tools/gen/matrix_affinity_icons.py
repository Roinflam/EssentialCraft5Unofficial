# -*- coding: utf-8 -*-
"""相性图标：从四种矩阵投影的物品贴图裁出中间那块（第十三批 13a，batch13.md §10：脚本生成、不另画）。

输入 textures/item/matrix/mrumatrix_<属性>.png（16×16），裁 (3, 3)~(13, 13) 的 10×10 芯部，原样（不缩放）贴在 16×16 透明底正中，
输出 textures/gui/matrix_affinity/<属性>.png。属性名照投影贴图：chaos、frozen、magic、shade。

改了投影贴图就重跑：python tools/gen/matrix_affinity_icons.py
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEXTURES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'essentialcraft5', 'textures')
AFFINITIES = ('chaos', 'frozen', 'magic', 'shade')
CROP = (3, 3, 13, 13)
SIZE = 16


def main():
    out_dir = os.path.join(TEXTURES, 'gui', 'matrix_affinity')
    os.makedirs(out_dir, exist_ok=True)
    for name in AFFINITIES:
        src = Image.open(os.path.join(TEXTURES, 'item', 'matrix', 'mrumatrix_%s.png' % name)).convert('RGBA')
        if src.size != (16, 16):
            raise SystemExit('投影贴图不是 16×16：%s %s' % (name, src.size))
        core = src.crop(CROP)
        icon = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
        icon.paste(core, ((SIZE - core.width) // 2, (SIZE - core.height) // 2))
        icon.save(os.path.join(out_dir, name + '.png'))
        print('写出', os.path.relpath(os.path.join(out_dir, name + '.png'), ROOT))


if __name__ == '__main__':
    main()
