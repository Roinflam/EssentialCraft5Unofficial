"""附魔增幅台 / 升华台的贴图和方块模型（自绘，纯 Python + PIL）。

输出：
  src/main/resources/assets/essentialcraft5/textures/block/enchant_amplifier.png   64×64
  src/main/resources/assets/essentialcraft5/textures/block/enchant_ascender.png    64×64
  src/main/resources/assets/essentialcraft5/models/block/enchant_amplifier.json
  src/main/resources/assets/essentialcraft5/models/block/enchant_ascender.json

贴图是 4×4 个 16×16 的小格（图集），模型的 UV 按小格取。改配色、图案改这里再重跑；预览：python enchant_upgrade_assets.py --preview <目录>。
增幅台：紫底青纹的讲台，四角青晶；升华台：黑曜石色的高台，红纹，四角红晶。
"""
import json
import os
import random
import sys

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'essentialcraft5'))

TILE = 16
ATLAS = 4  # 4×4 个小格


def hexc(s, a=255):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


class Tile:
    def __init__(self, base):
        self.px = [[base for _ in range(TILE)] for _ in range(TILE)]

    def set(self, x, y, c):
        if 0 <= x < TILE and 0 <= y < TILE:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x]

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def frame(self, x0, y0, x1, y1, c):
        for x in range(x0, x1 + 1):
            self.set(x, y0, c)
            self.set(x, y1, c)
        for y in range(y0, y1 + 1):
            self.set(x0, y, c)
            self.set(x1, y, c)

    def noise(self, rng, palette, density=1.0):
        """用调色板里的颜色铺一层抖动的底，保持像素画的颗粒感。"""
        for y in range(TILE):
            for x in range(TILE):
                if rng.random() < density:
                    self.px[y][x] = palette[rng.randrange(len(palette))]


def build_atlas(tiles):
    img = Image.new('RGBA', (TILE * ATLAS, TILE * ATLAS), (0, 0, 0, 0))
    for index, tile in enumerate(tiles):
        ox = (index % ATLAS) * TILE
        oy = (index // ATLAS) * TILE
        for y in range(TILE):
            for x in range(TILE):
                img.putpixel((ox + x, oy + y), tile.px[y][x])
    return img


# ---------------------------------------------------------------- 图案

GLYPHS = [
    # 5×7 的小符文，照附魔台那种「银河字母」的笔画感拼的
    ["..#..", ".###.", "#.#.#", "..#..", "#.#.#", ".###.", "..#.."],
    ["#...#", "##.##", "#.#.#", ".###.", "#.#.#", "##.##", "#...#"],
    ["###..", "..#..", ".###.", "..#..", ".###.", "..#..", "..###"],
    [".#.#.", "#####", ".#.#.", "..#..", ".#.#.", "#####", ".#.#."],
]


def stamp(tile, glyph, ox, oy, color, glow=None):
    for gy, row in enumerate(glyph):
        for gx, ch in enumerate(row):
            if ch == '#':
                tile.set(ox + gx, oy + gy, color)
    if glow is not None:
        for gy, row in enumerate(glyph):
            for gx, ch in enumerate(row):
                if ch != '#':
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    x, y = ox + gx + dx, oy + gy + dy
                    if 0 <= x < TILE and 0 <= y < TILE and tile.get(x, y) not in (color,):
                        tile.set(x, y, mix(tile.get(x, y), glow, 0.55))


def ring(tile, cx, cy, r, color):
    for y in range(TILE):
        for x in range(TILE):
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            if abs(d - r) < 0.55:
                tile.set(x, y, color)


# ---------------------------------------------------------------- 两套配色

AMPLIFIER = {
    'name': 'enchant_amplifier',
    'stone': [hexc('2a1b3c'), hexc('2f1f44'), hexc('36234f'), hexc('3d2a58')],
    'stone_dark': [hexc('1d1229'), hexc('231632'), hexc('291a3a')],
    'trim': hexc('8b69c9'),
    'trim_dark': hexc('5a3f8c'),
    'shadow': hexc('150c20'),
    'accent': hexc('6ff3ff'),
    'accent_mid': hexc('2ab4d4'),
    'accent_dark': hexc('176b88'),
    'glow': hexc('1f7f9c'),
    'cloth': [hexc('4a2a78'), hexc('52308a'), hexc('5d3a99')],
    'cloth_edge': hexc('e0b85a'),
    'crystal_hi': hexc('d6fbff'),
    'crystal_mid': hexc('55d8f2'),
    'crystal_low': hexc('1d8aae'),
    'crystal_dark': hexc('135574'),
    'seed': 1017,
    'glyphs': [0, 2, 3, 1],
}

ASCENDER = {
    'name': 'enchant_ascender',
    'stone': [hexc('0f0b16'), hexc('15101e'), hexc('1b1426'), hexc('211830')],
    'stone_dark': [hexc('08060d'), hexc('0c0911'), hexc('100c17')],
    'trim': hexc('7d5a8f'),
    'trim_dark': hexc('4b3358'),
    'shadow': hexc('050308'),
    'accent': hexc('ff5b84'),
    'accent_mid': hexc('c21e4a'),
    'accent_dark': hexc('6e1030'),
    'glow': hexc('7a1636'),
    'cloth': [hexc('2a1030'), hexc('33143c'), hexc('3d1848')],
    'cloth_edge': hexc('c9a24a'),
    'crystal_hi': hexc('ffd0dc'),
    'crystal_mid': hexc('ff3d6b'),
    'crystal_low': hexc('b3123c'),
    'crystal_dark': hexc('600a22'),
    'seed': 2050,
    'glyphs': [1, 3, 0, 2],
}


def paint(pal):
    rng = random.Random(pal['seed'])

    # 0：底座侧面——暗石 + 上沿亮边 + 下沿阴影 + 两块嵌板 + 铆钉
    base_side = Tile(pal['stone'][0])
    base_side.noise(rng, pal['stone'])
    base_side.rect(0, 0, 15, 0, pal['trim'])
    base_side.rect(0, 1, 15, 1, pal['trim_dark'])
    base_side.rect(0, 14, 15, 15, pal['shadow'])
    for x0 in (2, 9):
        base_side.frame(x0, 4, x0 + 4, 11, pal['trim_dark'])
        base_side.rect(x0 + 1, 5, x0 + 3, 10, rng.choice(pal['stone_dark']))
        base_side.set(x0 + 2, 7, pal['accent_dark'])
        base_side.set(x0 + 2, 8, pal['accent_mid'])
    for x, y in ((1, 3), (14, 3), (1, 12), (14, 12)):
        base_side.set(x, y, pal['trim'])

    # 1：底座 / 台面顶面——暗石 + 四角方块 + 一圈细边
    top = Tile(pal['stone'][1])
    top.noise(rng, pal['stone'])
    top.frame(0, 0, 15, 15, pal['trim_dark'])
    top.frame(1, 1, 14, 14, pal['shadow'])
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        top.rect(x, y, x + 1, y + 1, pal['trim'])

    # 2：立柱侧面——深色嵌板 + 竖向符文 + 青（红）光槽
    column = Tile(pal['stone_dark'][0])
    column.noise(rng, pal['stone_dark'])
    column.frame(0, 0, 15, 15, pal['shadow'])
    column.rect(0, 0, 15, 1, pal['trim_dark'])
    column.rect(0, 14, 15, 15, pal['shadow'])
    for y in range(2, 14):
        column.set(3, y, pal['accent_dark'])
        column.set(12, y, pal['accent_dark'])
    for y in range(2, 14, 3):
        column.set(3, y, pal['accent_mid'])
        column.set(12, y, pal['accent_mid'])
    stamp(column, GLYPHS[pal['glyphs'][0]], 5, 4, pal['accent'], pal['glow'])
    column.set(7, 13, pal['accent_mid'])
    column.set(8, 13, pal['accent_mid'])

    # 3：台面侧边——亮边 + 暗石 + 一排小方孔
    plate_side = Tile(pal['stone'][2])
    plate_side.noise(rng, pal['stone'])
    plate_side.rect(0, 0, 15, 0, pal['trim'])
    plate_side.rect(0, 1, 15, 1, pal['trim_dark'])
    plate_side.rect(0, 14, 15, 15, pal['shadow'])
    for x in range(2, 14, 3):
        plate_side.rect(x, 5, x + 1, 10, pal['stone_dark'][1])
        plate_side.set(x, 8, pal['accent_dark'])
        plate_side.set(x + 1, 8, pal['accent_mid'])

    # 4：台面顶（圆阵）——暗底，一圈发光环，四个刻度，中心留给书
    plate_top = Tile(pal['stone_dark'][1])
    plate_top.noise(rng, pal['stone_dark'])
    plate_top.frame(0, 0, 15, 15, pal['trim_dark'])
    plate_top.frame(1, 1, 14, 14, pal['shadow'])
    ring(plate_top, 8, 8, 5.2, pal['accent_mid'])
    ring(plate_top, 8, 8, 3.2, pal['accent_dark'])
    for x, y in ((8, 2), (8, 13), (2, 8), (13, 8)):
        plate_top.set(x, y, pal['accent'])
        plate_top.set(x, y + (1 if y < 8 else -1) if x == 8 else y, pal['accent'])
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        plate_top.set(x, y, pal['accent'])

    # 5：讲台斜面（布面）——暗紫布纹 + 金边
    cloth = Tile(pal['cloth'][0])
    for y in range(TILE):
        for x in range(TILE):
            cloth.set(x, y, pal['cloth'][(x + y) % 3] if (x + y) % 2 == 0 else pal['cloth'][((x - y) % 3 + 3) % 3])
    cloth.frame(0, 0, 15, 15, pal['cloth_edge'])
    cloth.frame(1, 1, 14, 14, mix(pal['cloth_edge'], pal['cloth'][0], 0.55))
    stamp(cloth, GLYPHS[pal['glyphs'][1]], 5, 4, mix(pal['cloth_edge'], (255, 255, 255, 255), 0.25))

    # 6：晶体侧面——竖向渐变 + 左亮右暗的切面
    crystal = Tile(pal['crystal_mid'])
    for y in range(TILE):
        t = y / (TILE - 1)
        for x in range(TILE):
            if x < 5:
                c = mix(pal['crystal_hi'], pal['crystal_mid'], t * 0.9)
            elif x < 11:
                c = mix(pal['crystal_mid'], pal['crystal_low'], t)
            else:
                c = mix(pal['crystal_low'], pal['crystal_dark'], t)
            crystal.set(x, y, c)
    for y in range(0, TILE, 4):
        crystal.set(4, y + 1, pal['crystal_hi'])
        crystal.set(10, y + 2, pal['crystal_mid'])

    # 7：晶体顶 / 底——亮心
    crystal_top = Tile(pal['crystal_mid'])
    crystal_top.rect(0, 0, 15, 15, pal['crystal_low'])
    crystal_top.rect(3, 3, 12, 12, pal['crystal_mid'])
    crystal_top.rect(5, 5, 10, 10, pal['crystal_hi'])
    crystal_top.frame(0, 0, 15, 15, pal['crystal_dark'])

    # 8：台面底 / 底座底——纯暗石
    under = Tile(pal['stone_dark'][0])
    under.noise(rng, pal['stone_dark'])
    under.frame(0, 0, 15, 15, pal['shadow'])

    # 9：讲台侧边——金边的薄板
    stand_side = Tile(pal['cloth'][0])
    stand_side.noise(rng, pal['cloth'])
    stand_side.rect(0, 0, 15, 1, pal['cloth_edge'])
    stand_side.rect(0, 14, 15, 15, pal['shadow'])

    return [base_side, top, column, plate_side, plate_top, cloth, crystal, crystal_top, under, stand_side,
            Tile((0, 0, 0, 0)), Tile((0, 0, 0, 0)), Tile((0, 0, 0, 0)), Tile((0, 0, 0, 0)), Tile((0, 0, 0, 0)), Tile((0, 0, 0, 0))]


# 小格序号
BASE_SIDE, TOP, COLUMN, PLATE_SIDE, PLATE_TOP, CLOTH, CRYSTAL, CRYSTAL_TOP, UNDER, STAND_SIDE = range(10)


def uv(index, flip=False):
    """第 index 格的 UV（JSON 里是 0~16 的整图坐标）。"""
    col = index % ATLAS
    row = index // ATLAS
    size = 16 / ATLAS
    u0, v0 = col * size, row * size
    u1, v1 = u0 + size, v0 + size
    return [u1, v0, u0, v1] if flip else [u0, v0, u1, v1]


def faces(side, top=None, bottom=None, cull_bottom=False):
    top = side if top is None else top
    bottom = side if bottom is None else bottom
    out = {}
    for name in ('north', 'south', 'east', 'west'):
        out[name] = {'uv': uv(side), 'texture': '#0'}
    out['up'] = {'uv': uv(top), 'texture': '#0'}
    out['down'] = {'uv': uv(bottom), 'texture': '#0'}
    if cull_bottom:
        out['down']['cullface'] = 'down'
    return out


def element(frm, to, face_map, rotation=None):
    e = {'from': frm, 'to': to, 'faces': face_map}
    if rotation:
        e['rotation'] = rotation
    return e


def model(name, tall):
    """tall=False：增幅台（矮），True：升华台（高柱 + 大晶体）。"""
    base_h = 4 if tall else 3
    col_h = 10 if tall else 9
    plate_h = 12 if tall else 11
    col_in = 3 if tall else 3
    elements = [
        element([0, 0, 0], [16, base_h, 16], faces(BASE_SIDE, TOP, UNDER, True)),
        element([col_in, base_h, col_in], [16 - col_in, col_h, 16 - col_in], faces(COLUMN, TOP, UNDER)),
        element([1, col_h, 1], [15, plate_h, 15], faces(PLATE_SIDE, PLATE_TOP, UNDER)),
    ]
    # 讲台：斜着放的一块板（绕 x 轴 −22.5°）
    elements.append(element([4, plate_h, 5], [12, plate_h + 1, 12], faces(STAND_SIDE, CLOTH, STAND_SIDE),
                            {'origin': [8, plate_h, 8], 'axis': 'x', 'angle': -22.5}))
    # 四角晶体
    spire = 16 if tall else 15
    for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        elements.append(element([x, plate_h, z], [x + 2, spire, z + 2], faces(CRYSTAL, CRYSTAL_TOP, CRYSTAL_TOP)))
    if tall:
        # 升华台的晶体再加一层尖
        for x, z in ((1.5, 1.5), (13.5, 1.5), (1.5, 13.5), (13.5, 13.5)):
            elements.append(element([x, 16, z], [x + 1, 16, z + 1], {'up': {'uv': uv(CRYSTAL_TOP), 'texture': '#0'}}))
    return {
        'parent': 'minecraft:block/block',
        'textures': {'0': 'essentialcraft5:block/' + name, 'particle': 'essentialcraft5:block/' + name},
        'elements': elements,
        'render_type': 'minecraft:cutout_mipped',
        'display': {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.62, 0.62, 0.62]},
            'fixed': {'rotation': [0, 180, 0], 'scale': [0.5, 0.5, 0.5]},
        },
    }


def main():
    preview = None
    if '--preview' in sys.argv:
        preview = sys.argv[sys.argv.index('--preview') + 1]
        os.makedirs(preview, exist_ok=True)
    for pal, tall in ((AMPLIFIER, False), (ASCENDER, True)):
        img = build_atlas(paint(pal))
        tex_path = os.path.join(ROOT, 'textures', 'block', pal['name'] + '.png')
        os.makedirs(os.path.dirname(tex_path), exist_ok=True)
        img.save(tex_path)
        model_path = os.path.join(ROOT, 'models', 'block', pal['name'] + '.json')
        with open(model_path, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(model(pal['name'], tall), f, ensure_ascii=False, indent=2)
            f.write('\n')
        if preview:
            img.resize((img.width * 8, img.height * 8), Image.NEAREST).save(os.path.join(preview, pal['name'] + '_atlas.png'))
        print('写出', tex_path, model_path)


if __name__ == '__main__':
    main()
