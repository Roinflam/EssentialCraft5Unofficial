#!/usr/bin/env python3
"""
无尽道具（第六批 6b M5，registry/ECCreative）的贴图：从原作贴图派生出一套「创造」外观。

    python tools/gen/creative_textures.py          # 写 6 张动画贴图和各自的 .mcmeta
    python tools/gen/creative_textures.py sheet    # 另外把每张的第 0 / 8 / 16 / 24 帧拼成一张放大预览，写到系统临时目录下的 ec5_creative_sheet.png

输出（src/main/resources/assets/essentialcraft5/textures/）：
    block/creative/infinite_mru_source.png         底稿 block/creativemrustorage.png（原作创造 MRU 源）
    block/creative/infinite_espe_source.png        底稿 block/creativeespestorage.png（原作创造 ESPE 源）
    block/creative/infinite_thermal_source_hot.png  框取 creativemrustorage，芯换成 block/chaos.png 的流动纹（混沌 MRU，红）
    block/creative/infinite_thermal_source_cold.png 框取 creativemrustorage，芯换成 block/frozen.png 的流动纹（冰冻 MRU，蓝）
    item/creative/infinite_mru_crystal.png         底稿 item/misc/mrucrystal.png
    item/creative/creative_ward.png                底稿 item/tools/charmlife.png（生命护符）
脚本和结果都提交；改外观改下面的常量再重跑。只用标准库（PNG 读写在 texlib.py）。

一套外观的三个共同点（一眼认得出是「创造」那一组，又各自保留原作的辨识度）：
  1. 金框：原作的棕色木框 / 淡金护符圈按原亮度映射到一条金色色带（暗金 → 金 → 亮金），轮廓线压成暗金。
  2. 彩虹环：方块芯外那一圈 1 像素的边框改成沿圆周铺开的彩虹，逐帧转一圈；晶体本身、护符的心形宝石按对角线铺彩虹、逐帧流动。
  3. 流光：一道斜向的亮带每个周期从左上扫到右下一次（只扫金框 / 金圈，中间留一段空档不扫，看着不闹）。
方块芯里的东西保持原作的颜色：MRU 源的粉色十字、ESPE 源的绿色方块只做明暗呼吸；冷热源的芯直接用原作混沌 / 冰冻 MRU 的流动纹。
所有贴图都是 32 帧、每帧 2 刻（一个周期 3.2 秒），彩虹、流光、呼吸同一个节奏；无尽 MRU 晶体的耐久条颜色也按 3.2 秒转一圈（InfiniteMRUCrystalItem）。

贴图改动按 CC BY-SA 4.0 记在 CREDITS.md 与 tools/asset_map.tsv（DERIVED 行，import_assets.py 的 EXTRA_ROWS）。
"""
import colorsys
import math
import os
import tempfile
import sys

from texlib import read_png_file, write_png

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
TEXTURES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'essentialcraft5', 'textures')

FRAMES = 32
FRAMETIME = 2

# 金色色带：(位置, (r, g, b))，位置 0 = 最暗、1 = 最亮
GOLD = [
    (0.00, (58, 36, 4)),
    (0.30, (128, 84, 10)),
    (0.58, (212, 160, 23)),
    (0.82, (255, 214, 92)),
    (1.00, (255, 246, 200)),
]
# 彩虹的饱和度、亮度下限（太暗的原像素也要看得出颜色）
RAINBOW_SAT = 0.72
RAINBOW_MIN_VALUE = 0.62
# 流光：亮带半宽（像素，按 32 像素宽的图算，别的尺寸按比例）、最多往白色提多少、扫一遍占周期的比例（剩下的是空档）
SHINE_HALF_WIDTH = 3.0
SHINE_STRENGTH = 0.55
SHINE_SWEEP = 0.6
# 方块芯的明暗呼吸幅度
PULSE = 0.22


def load(rel):
    width, height, pixels = read_png_file(os.path.join(TEXTURES, rel))
    return width, height, pixels


def frame_of(rel, index):
    """竖排动画贴图的第 index 帧（按帧数取模）：(按行的二维像素, 边长)"""
    width, height, pixels = load(rel)
    count = height // width
    top = (index % count) * width
    return [[pixels[(top + y) * width + x] for x in range(width)] for y in range(width)], width


def lum(rgb):
    r, g, b = rgb[:3]
    return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0


def lerp(a, b, t):
    return a + (b - a) * t


def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        if t <= p1:
            k = 0.0 if p1 == p0 else (t - p0) / (p1 - p0)
            return tuple(int(round(lerp(c0[i], c1[i], k))) for i in range(3))
    return stops[-1][1]


def lighten(rgb, amount):
    return tuple(int(round(lerp(c, 255, amount))) for c in rgb[:3])


def scale(rgb, factor):
    return tuple(max(0, min(255, int(round(c * factor)))) for c in rgb[:3])


def hsv(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, s, v)
    return int(round(r * 255)), int(round(g * 255)), int(round(b * 255))


def shine(x, y, size, frame):
    """流光亮带在 (x, y) 的强度 0~1：沿 x + y 方向扫，周期前 SHINE_SWEEP 扫完，后面是空档"""
    phase = frame / FRAMES
    if phase >= SHINE_SWEEP:
        return 0.0
    half = SHINE_HALF_WIDTH * size / 32.0
    span = 2 * (size - 1) + 4 * half
    center = -2 * half + span * (phase / SHINE_SWEEP)
    return max(0.0, 1.0 - abs((x + y) - center) / half)


def gold_by_luminance(rgb, lo, hi):
    t = 0.0 if hi <= lo else (lum(rgb) - lo) / (hi - lo)
    return ramp(GOLD, 0.15 + 0.8 * t)


def breathe(frame):
    return 1.0 + PULSE * math.sin(2 * math.pi * frame / FRAMES)


# ---------------------------------------------------------------- 方块

FRAME_RING = 2      # 外框宽 2 像素（原作 creativemrustorage / creativeespestorage 都是）
CORE_LO, CORE_HI = 8, 23   # 芯外那圈 1 像素的边框（原作 0x381b0b 的棕线）


def block_frames(base_rel, core):
    """32 × 32 方块：金框 + 流光、芯外边框彩虹环、芯（9..22）交给 core(frame, x, y, 原像素) 决定"""
    width, height, base = load(base_rel)
    assert width == 32 and height == 32, base_rel
    frame_px = [base[y * 32 + x] for y in range(32) for x in range(32)
                if min(x, y) < FRAME_RING or max(x, y) >= 32 - FRAME_RING]
    lo = min(lum(p) for p in frame_px)
    hi = max(lum(p) for p in frame_px)
    out = []
    center = (CORE_LO + CORE_HI) / 2.0
    for f in range(FRAMES):
        for y in range(32):
            for x in range(32):
                p = base[y * 32 + x]
                if min(x, y) < FRAME_RING or max(x, y) >= 32 - FRAME_RING:
                    rgb = lighten(gold_by_luminance(p, lo, hi), SHINE_STRENGTH * shine(x, y, 32, f))
                elif CORE_LO <= x <= CORE_HI and CORE_LO <= y <= CORE_HI:
                    if x in (CORE_LO, CORE_HI) or y in (CORE_LO, CORE_HI):
                        angle = math.atan2(y - center, x - center) / (2 * math.pi)
                        rgb = hsv(angle + f / FRAMES, RAINBOW_SAT, 0.95)
                    else:
                        rgb = core(f, x, y, p)
                else:
                    rgb = lighten(p, SHINE_STONE * shine(x, y, 32, f))
                out.append(rgb + (255,))
    return 32, out


def pulsing_core(f, x, y, p):
    """原作芯的颜色不变，亮的部分（十字、方块）随周期呼吸，暗底只轻轻跟着"""
    k = breathe(f)
    weight = min(1.0, lum(p) * 2.5)
    return scale(p, 1.0 + (k - 1.0) * weight)


def flow_core(rel, boost, colors=None):
    """芯换成原作流动纹的第 f 帧：16 像素宽的（chaos）从芯的左上角起平铺，32 像素宽的（frozen）取同一位置。
    给了 colors 就按亮度重新上色（纹路不变），否则只按 boost 提亮"""
    cache = {}
    _, _, every = load(rel)
    lo = min(lum(q) for q in every)
    hi = max(lum(q) for q in every)

    def core(f, x, y, p):
        if f not in cache:
            cache[f] = frame_of(rel, f)
        pixels, size = cache[f]
        q = pixels[(y - CORE_LO) % size][(x - CORE_LO) % size] if size < 32 else pixels[y][x]
        if colors:
            # 按整张流动纹的亮度范围拉开再上色，boost 是拉开后再乘的倍数
            return ramp(colors, (lum(q) - lo) / max(1e-6, hi - lo) * boost)
        return scale(q, boost)
    return core


# 热源的芯：混沌 MRU 的红色流动纹按亮度改成岩浆色（暗红 → 红 → 橙 → 黄），一看就是「热」
HOT = [
    (0.00, (52, 0, 0)),
    (0.30, (160, 18, 4)),
    (0.60, (236, 100, 18)),
    (0.85, (255, 200, 70)),
    (1.00, (255, 244, 186)),
]
# 流光扫过石面时的强度（金框是 SHINE_STRENGTH，石面淡一些，让亮带看得出是扫过整个面）
SHINE_STONE = 0.2


# ---------------------------------------------------------------- 物品

def crystal_frames():
    """无尽 MRU 晶体：轮廓压成暗金；晶体按对角线铺彩虹、逐帧流动，保留原作的明暗；三处星光轮流闪"""
    width, height, base = load(os.path.join('item', 'misc', 'mrucrystal.png'))
    size = width
    sparkles = [(22, 24, 0.0), (40, 30, 0.33), (30, 41, 0.66)]   # (x, y, 相位)，在晶体里面
    out = []
    for f in range(FRAMES):
        layer = []
        for y in range(size):
            for x in range(size):
                r, g, b, a = base[y * size + x]
                if a == 0:
                    layer.append((255, 255, 255, 0))
                    continue
                l = lum((r, g, b))
                if l < 0.12:
                    rgb = ramp(GOLD, 0.05 + l * 2.0)
                else:
                    _, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
                    hue = (x + y) / (2.0 * size) * 1.5 + f / FRAMES
                    rgb = hsv(hue, min(1.0, s * 0.85 + 0.12), max(v, RAINBOW_MIN_VALUE))
                layer.append(rgb + (a,))
        for sx, sy, phase in sparkles:
            t = (f / FRAMES + phase) % 1.0
            strength = max(0.0, 1.0 - abs(t - 0.5) * 6.0)
            if strength <= 0:
                continue
            for dx, dy, k in ((0, 0, 1.0), (1, 0, 0.6), (-1, 0, 0.6), (0, 1, 0.6), (0, -1, 0.6),
                              (2, 0, 0.3), (-2, 0, 0.3), (0, 2, 0.3), (0, -2, 0.3)):
                i = (sy + dy) * size + (sx + dx)
                r, g, b, a = layer[i]
                if a == 0:
                    continue
                layer[i] = lighten((r, g, b), strength * k) + (a,)
        out.extend(layer)
    return size, out


def ward_frames():
    """无尽护佑护符：淡金圈换金色色带 + 流光，轮廓暗金；心形宝石彩虹流动；宝石底的灰换成随周期呼吸的白光；链子照旧"""
    width, height, base = load(os.path.join('item', 'tools', 'charmlife.png'))
    size = width
    ring = (0xde, 0xde, 0x90)
    outline = {(0x44, 0x44, 0x0f), (0x33, 0x33, 0x0b)}
    heart = {(0xff, 0x00, 0x00), (0xd8, 0x2f, 0x2f)}
    backing = (0x77, 0x77, 0x77)
    out = []
    for f in range(FRAMES):
        for y in range(size):
            for x in range(size):
                r, g, b, a = base[y * size + x]
                rgb = (r, g, b)
                if a == 0:
                    out.append((255, 255, 255, 0))
                    continue
                if rgb == ring:
                    # 圈上按位置给一点明暗起伏，免得一整片平涂
                    t = 0.62 + 0.18 * math.sin((x - y) * 0.9)
                    rgb = lighten(ramp(GOLD, t), SHINE_STRENGTH * shine(x, y, size, f))
                elif rgb in outline:
                    rgb = ramp(GOLD, 0.12 if rgb == (0x33, 0x33, 0x0b) else 0.22)
                elif rgb in heart:
                    shade = 0.95 if rgb == (0xff, 0x00, 0x00) else 0.78
                    rgb = hsv((x + y) / (2.0 * size) + f / FRAMES, 0.8, shade)
                elif rgb == backing:
                    rgb = scale((236, 230, 255), 0.82 + 0.12 * math.sin(2 * math.pi * f / FRAMES))
                out.append(rgb + (a,))
    return size, out


# ---------------------------------------------------------------- 输出

OUTPUTS = [
    (os.path.join('block', 'creative', 'infinite_mru_source.png'),
     lambda: block_frames(os.path.join('block', 'creativemrustorage.png'), pulsing_core)),
    (os.path.join('block', 'creative', 'infinite_espe_source.png'),
     lambda: block_frames(os.path.join('block', 'creativeespestorage.png'), pulsing_core)),
    (os.path.join('block', 'creative', 'infinite_thermal_source_hot.png'),
     lambda: block_frames(os.path.join('block', 'creativemrustorage.png'), flow_core(os.path.join('block', 'chaos.png'), 1.0, HOT))),
    (os.path.join('block', 'creative', 'infinite_thermal_source_cold.png'),
     lambda: block_frames(os.path.join('block', 'creativemrustorage.png'), flow_core(os.path.join('block', 'frozen.png'), 1.1))),
    (os.path.join('item', 'creative', 'infinite_mru_crystal.png'), crystal_frames),
    (os.path.join('item', 'creative', 'creative_ward.png'), ward_frames),
]

MCMETA = '{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % FRAMETIME


def main():
    written = []
    for rel, build in OUTPUTS:
        size, pixels = build()
        path = os.path.join(TEXTURES, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        write_png(path, size, size * FRAMES, pixels)
        with open(path + '.mcmeta', 'w', encoding='utf-8', newline='') as f:
            f.write(MCMETA)
        written.append((rel, size, pixels))
        print(f'{rel}: {size} x {size * FRAMES}（{FRAMES} 帧）')
    if len(sys.argv) > 1 and sys.argv[1] == 'sheet':
        write_sheet(written)


def write_sheet(written):
    """预览：每张贴图一行，第 0 / 8 / 16 / 24 帧各放大到 128 × 128，深灰底"""
    cell, picks = 128, (0, 8, 16, 24)
    width, height = cell * len(picks), cell * len(written)
    sheet = [(48, 48, 48, 255)] * (width * height)
    for row, (rel, size, pixels) in enumerate(written):
        for col, frame in enumerate(picks):
            for y in range(cell):
                for x in range(cell):
                    sx, sy = x * size // cell, y * size // cell
                    r, g, b, a = pixels[(frame * size + sy) * size + sx]
                    if a == 0:
                        continue
                    sheet[(row * cell + y) * width + col * cell + x] = (r, g, b, 255)
    sheet_path = os.path.join(tempfile.gettempdir(), 'ec5_creative_sheet.png')
    write_png(sheet_path, width, height, sheet)
    print('预览写到系统临时目录下的 ec5_creative_sheet.png')


if __name__ == '__main__':
    main()
