#!/usr/bin/env python3
"""
提亮恐惧仙人掌、恐惧泥土的贴图（第六批 6a M1-5）：

    python tools/gen/dread_textures.py          # 从原作仓库读原图，写四张提亮后的贴图，并打印前后的 L*
    python tools/gen/dread_textures.py check    # 只打印项目里现有四张的 L*

输入是原作（../EssentialCraft4Unofficial 的 HEAD，textures/blocks/firstworld/）的原图，不是项目里的文件，所以重跑结果不变、不会越提越亮。
输出 src/main/resources/assets/essentialcraft5/textures/block/firstworld/{cactus_side,cactus_top,cactus_bottom,dreaddirt}.png，脚本和结果都提交。
tools/import_assets.py 重新导入贴图时跳过这四张（MODIFIED 表），改完原图要重跑本脚本。

为什么：原作仙人掌侧面平均 L* 17（sRGB 平均色 #2F2B00）、恐惧泥土 L* 10.5（#29190E），海纳尔沙漠里的仙人掌是一个个黑桩，
恐惧之地（约占海纳尔陆地的 19%）是一片近乎纯黑的地。目标：仙人掌侧面 L* 30~35、恐惧泥土 22~28（原版灵魂土 25.8、枯灌木 35.7、
原版仙人掌 49.7 作对照），还是暗、还是「恐惧」，只是不再是黑块。

怎么提：在 CIELCh 里逐像素改，色相角不动：
  - 明度：抬黑场，L' = L × (1 − k / 100) + k。是一条直线，暗像素抬得多、亮像素抬得少，原来的明暗顺序和纹理都保留，对比度只收一点
    （仙人掌 k = 18.7 收 19%，泥土 k = 16.2 收 16%）；原图里 L* 只有 1~3 的「黑洞」点被抬到 17~21，不再像破了个洞。
    仙人掌三张用同一条曲线：侧面 17 → 32.5、顶面 29.4 → 42.6、底面 70.2 → 75.8，顶比侧亮的关系照旧（底面原本就很亮，几乎不动）。
  - 彩度：C' = C × ((L' + 1) / (L + 1))^0.3，跟着明度加一点，免得提亮后发灰；超出 sRGB 时保持 L* 和色相降彩度。
  - alpha 不动（仙人掌侧面两边的刺、顶底的透明边照旧）。

贴图改动按 CC BY-SA 4.0 记在 CREDITS.md 与 tools/asset_map.tsv（MODIFIED 行）。GameTest HoannaTextureTests 核对 L* 区间和色相。
"""
import math
import os
import subprocess
import sys

from texlib import lch_to_srgb, read_png, read_png_file, srgb_to_lab, texture_stats, write_png

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SOURCE_REPO = os.path.join(os.path.dirname(ROOT), 'EssentialCraft4Unofficial')
SOURCE_DIR = 'src/main/resources/assets/essentialcraft/textures/blocks/firstworld/'
OUT_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'essentialcraft5', 'textures', 'block', 'firstworld')

CACTUS_BLACK_POINT = 18.7
DIRT_BLACK_POINT = 16.2
CHROMA_EXPONENT = 0.3
# 文件名 → 黑场
TEXTURES = {
    'cactus_side.png': CACTUS_BLACK_POINT,
    'cactus_top.png': CACTUS_BLACK_POINT,
    'cactus_bottom.png': CACTUS_BLACK_POINT,
    'dreaddirt.png': DIRT_BLACK_POINT,
}


def original(name):
    out = subprocess.run(['git', '-C', SOURCE_REPO, 'cat-file', 'blob', 'HEAD:' + SOURCE_DIR + name],
                         check=True, capture_output=True)
    return read_png(out.stdout)


def lift(pixels, black_point):
    out = []
    for r, g, b, a in pixels:
        if a == 0:
            out.append((r, g, b, a))
            continue
        l, a_, b_ = srgb_to_lab((r, g, b))
        c, h = math.hypot(a_, b_), math.atan2(b_, a_)
        l2 = l * (1 - black_point / 100) + black_point
        c2 = c * ((l2 + 1) / (l + 1)) ** CHROMA_EXPONENT
        out.append(lch_to_srgb(l2, c2, h) + (a,))
    return out


def describe(stats):
    return (f"平均 L* {stats['mean_l']:.1f}、平均色 {stats['mean_hex']}（L* {stats['l_of_mean']:.1f}）、"
            f"色相 {stats['hue']:.1f}°、彩度 {stats['chroma']:.1f}")


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else 'write'
    for name, black_point in TEXTURES.items():
        path = os.path.join(OUT_DIR, name)
        if mode == 'check':
            print(f'{name}: {describe(texture_stats(read_png_file(path)[2]))}')
            continue
        width, height, pixels = original(name)
        lifted = lift(pixels, black_point)
        write_png(path, width, height, lifted)
        print(f'{name}: 原图 {describe(texture_stats(pixels))}')
        print(f'{" " * len(name)}  提亮 {describe(texture_stats(lifted))}')


if __name__ == '__main__':
    main()
