#!/usr/bin/env python3
"""
虚空石 / 虚空玻璃的纹路贴图（第六批 6a M7-2）：

    python tools/gen/void_veins.py          # 写两张纹路贴图，并打印 16 色的 CIELAB 色差表
    python tools/gen/void_veins.py check    # 只打印色差表
    python tools/gen/void_veins.py seed     # 打印起稿用的自动遮罩（见下）

输出 src/main/resources/assets/essentialcraft5/textures/block/{voidstone_veins,voidglass_veins}.png，脚本和结果都提交，改纹路改下面的 VEINS 再重跑。

为什么要纹路：原作整张贴图乘染料色，而 voidstone.png 平均亮度只有 24 / 255（voidglass 32），乘色后 16 色的 L* 只有 0.7~7.6，
120 对颜色里有 18 对肉眼分不出（原作也是这样）。现在模型分两层（models/block/dyeable_veined*.json，datagen 见 ECMaterials.veinedDyeableModel）：
底层是不染色的原贴图（暗色的虚空底子和紫色边框不变），上面一层是这张灰度纹路、面带 tintindex 0、shade false，染料色只落在纹路上。
纹路核心是满亮度（255），乘出来就是原版烟花色本身，16 色两两的 ΔE2000 最小 12.8（灰 / 黑），见 check 的输出。

怎么画的：先取 voidstone.png 按 2 × 2 合成 16 × 16 后最亮的约 20% 做遮罩（seed 打印的那张）——结果几乎全是紫色边框，
内部只有右中和左下两小团稍亮，直接拿来会是一圈亮框加几粒噪点。所以纹路是照「虚空里的能量脉络」手画的，走向贴着那两团稍亮的底子：
  - 一条主脉从左边缘第 9 行进、右边缘第 9 行出，中段拱起、右段下沉；上方一条支流从顶边第 5 列汇入，下方一条从底边第 5 列汇入，
    另有两根渐隐的细枝。四个出口在对边同一位置，所以相邻两格（虚空石和虚空玻璃之间也一样）的纹路能接成一张网。
  - 主脉满亮，支流 215，细枝 170 / 120 渐隐；主脉两侧各有一圈暗光（石头 60 的灰、玻璃是半透明的白，见下），看着像在发光而不是裂缝。
  - 16 × 16：底图是 32 × 32，纹路一格正好盖住底图 2 × 2，线宽和原版一个像素相同，远一点也不会糊成噪点。

两张的区别只在主脉两侧那圈暗光：虚空石的纹路层画在 cutout_mipped 层，只有全透 / 不透，暗光是不透明的 60 灰（乘色后是本色的暗版）；
虚空玻璃画在半透明层，暗光改成白色、alpha 72，叠在玻璃上是一层淡淡的本色光晕，不会在玻璃上压出一圈不透明的暗边。
透明格的 RGB 写成白色（原版生成 mipmap 时跳过 alpha 为 0 的格子，写什么都一样，白色只是方便看图）。

贴图改动按 CC BY-SA 4.0 记在 CREDITS.md 与 tools/asset_map.tsv（DERIVED 行，import_assets.py 的 EXTRA_ROWS）。
GameTest HoannaTextureTests 核对两张图的尺寸、灰度、出口对齐和 16 色的色差下限。
"""
import os
import sys

from texlib import delta_e76, delta_e2000, linear_to_lab, read_png_file, srgb_to_lab, srgb_to_linear, write_png

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
TEXTURES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'essentialcraft5', 'textures', 'block')

# 纹路（16 × 16，第 0 行 / 列与第 15 行 / 列是底图的紫色边框，只在四个出口处压过去）
#   #  主脉 255     o  支流 215     +  细枝 170     -  细枝末梢 120     h  主脉两侧的暗光     .  透明
VEINS = [
    '.....o..........',
    '.....o..........',
    '....o...........',
    '....o...........',
    '...+.o..........',
    '..-..o......-...',
    '....hhoh...+....',
    '..hh####hho.....',
    '.h##hhhh##h..hh.',
    '##hh....hh#hh###',
    '.h.......oh##hh.',
    '........o..hh...',
    '.......o........',
    '......o.........',
    '.....o..........',
    '.....o..........',
]
LEVELS = {'#': 255, 'o': 215, '+': 170, '-': 120}
# 暗光：虚空石是不透明的暗灰，虚空玻璃是半透明的白（见文件头）
HALO_STONE = (60, 255)
HALO_GLASS = (255, 72)
# 四个出口：左右在第 9 行，上下在第 5 列
PORT_ROW = 9
PORT_COLUMN = 5

# 原版 DyeColor.getFireworkColor()（可染色方块的色值，见 DyeableBlock.tintColor）
FIREWORK = [
    ('white', 0xF0F0F0), ('orange', 0xEB8844), ('magenta', 0xC354CD), ('light_blue', 0x6689D3),
    ('yellow', 0xDECF2A), ('lime', 0x41CD34), ('pink', 0xD88198), ('gray', 0x434343),
    ('light_gray', 0xABABAB), ('cyan', 0x287697), ('purple', 0x7B2FBE), ('blue', 0x253192),
    ('brown', 0x51301A), ('green', 0x3B511A), ('red', 0xB3312C), ('black', 0x1E1B1B),
]


# ---------------------------------------------------------------- 贴图

def texels(halo):
    """[(r, g, b, a)] × 256，按行"""
    assert len(VEINS) == 16
    out = []
    for row in VEINS:
        assert len(row) == 16, row
        for ch in row:
            if ch == '.':
                out.append((255, 255, 255, 0))
            elif ch == 'h':
                gray, alpha = halo
                out.append((gray, gray, gray, alpha))
            else:
                gray = LEVELS[ch]
                out.append((gray, gray, gray, 255))
    return out


def check_ports():
    for y in range(16):
        assert (VEINS[y][0] != '.') == (VEINS[y][15] != '.'), f'第 {y} 行左右出口没对齐'
    for x in range(16):
        assert (VEINS[0][x] != '.') == (VEINS[15][x] != '.'), f'第 {x} 列上下出口没对齐'
    frame = [(x, 0) for x in range(16)] + [(x, 15) for x in range(16)] + [(0, y) for y in range(16)] + [(15, y) for y in range(16)]
    ports = {(x, y) for x, y in frame if VEINS[y][x] != '.'}
    assert ports == {(0, PORT_ROW), (15, PORT_ROW), (PORT_COLUMN, 0), (PORT_COLUMN, 15)}, ports
    assert all(VEINS[y][x] in '#o' for x, y in ports), '出口要压在主脉或支流上'


# ---------------------------------------------------------------- 色差

def rgb(color):
    return (color >> 16) & 255, (color >> 8) & 255, color & 255


def face_average(base_path, halo, tint):
    """整面在远处看到的平均色（线性光按面积平均）：纹路格用 纹路灰 × 染料色，其余格用不染色的底图"""
    width, height, base = read_png_file(base_path)
    scale = width // 16
    veins = texels(halo)
    total = [0.0, 0.0, 0.0]
    tr, tg, tb = rgb(tint)
    for y in range(height):
        for x in range(width):
            r, g, b, a = base[y * width + x]
            vr, vg, vb, va = veins[(y // scale) * 16 + x // scale]
            under = [srgb_to_linear(c) for c in (r, g, b)]
            over = [srgb_to_linear(vr * t / 255) for t in (tr, tg, tb)]
            alpha = va / 255
            for i in range(3):
                total[i] += over[i] * alpha + under[i] * (1 - alpha)
    return linear_to_lab([t / (width * height) for t in total])


def report():
    core = {name: srgb_to_lab(rgb(color)) for name, color in FIREWORK}
    faces = {name: face_average(os.path.join(TEXTURES, 'voidstone.png'), HALO_STONE, color) for name, color in FIREWORK}
    old = {}
    _, _, base = read_png_file(os.path.join(TEXTURES, 'voidstone.png'))
    for name, color in FIREWORK:
        tr, tg, tb = rgb(color)
        lin = [0.0, 0.0, 0.0]
        for r, g, b, _ in base:
            for i, (c, t) in enumerate(((r, tr), (g, tg), (b, tb))):
                lin[i] += srgb_to_linear(c * t / 255)
        old[name] = linear_to_lab([v / len(base) for v in lin])
    print('颜色          纹路 L*  a*     b*    | 整面平均 L*（原来整张乘色）')
    for name, _ in FIREWORK:
        l, a, b = core[name]
        print(f'  {name:<11} {l:6.1f} {a:6.1f} {b:6.1f} | {faces[name][0]:5.1f}（{old[name][0]:4.1f}）')
    for label, table in (('纹路核心', core), ('整面平均', faces), ('原来整张乘色', old)):
        pairs = []
        names = [n for n, _ in FIREWORK]
        for i in range(len(names)):
            for j in range(i + 1, len(names)):
                a, b = table[names[i]], table[names[j]]
                pairs.append((delta_e2000(a, b), delta_e76(a, b), names[i], names[j]))
        pairs.sort()
        under5 = sum(1 for p in pairs if p[0] < 5)
        print(f'{label}：120 对 ΔE2000 最小 {pairs[0][0]:.1f}（{pairs[0][2]} / {pairs[0][3]}，ΔE76 {pairs[0][1]:.1f}），'
              f'< 5 的 {under5} 对；最小的五对 ' + '、'.join(f'{a}/{b} {d:.1f}' for d, _, a, b in pairs[:5]))


def seed():
    width, height, base = read_png_file(os.path.join(TEXTURES, 'voidstone.png'))
    scale = width // 16
    cells = []
    for y in range(16):
        for x in range(16):
            lin = [0.0, 0.0, 0.0]
            for dy in range(scale):
                for dx in range(scale):
                    px = base[(y * scale + dy) * width + x * scale + dx]
                    for i in range(3):
                        lin[i] += srgb_to_linear(px[i]) / (scale * scale)
            cells.append(linear_to_lab(lin)[0])
    threshold = sorted(cells, reverse=True)[int(0.2 * len(cells))]
    print(f'voidstone.png 合成 16 × 16 后最亮的 20%（L* ≥ {threshold:.1f}）：')
    for y in range(16):
        print('  ' + ''.join('#' if cells[y * 16 + x] >= threshold else '.' for x in range(16)))


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else 'write'
    check_ports()
    if mode == 'seed':
        seed()
        return
    if mode == 'write':
        write_png(os.path.join(TEXTURES, 'voidstone_veins.png'), 16, 16, texels(HALO_STONE))
        write_png(os.path.join(TEXTURES, 'voidglass_veins.png'), 16, 16, texels(HALO_GLASS))
        for name in ('voidstone_veins.png', 'voidglass_veins.png'):
            width, height, px = read_png_file(os.path.join(TEXTURES, name))
            assert (width, height) == (16, 16) and px[PORT_ROW * 16] == (255, 255, 255, 255), name
        print('写好 voidstone_veins.png、voidglass_veins.png')
    report()


if __name__ == '__main__':
    main()
