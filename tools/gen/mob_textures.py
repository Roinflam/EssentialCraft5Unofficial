# -*- coding: utf-8 -*-
"""新怪物的派生贴图（第十三批 13c-Fb，batch13.md §10「贴图」）：从主贴图生成发光层、精英层和换色派生，不另画。

贴图位置照 client/render/mob/MobTextures 的约定：textures/entity/<id>/<id>.png 是主贴图，派生图写在同一个目录。

  python tools/gen/mob_textures.py glow <id> [--min-value 0.82] [--min-sat 0.30] [--colors #ff3df0,#7af0ff] [--mask 路径]
      <id>_glow.png：主贴图里会发光的像素原样留下，其余全透明（MobGlowLayer 全亮叠加）。
      默认挑「又亮又艳」的像素（HSV 明度 ≥ min-value 且饱和度 ≥ min-sat）；--colors 只挑这几种颜色（容差 --tolerance，默认 24）；
      --mask 给一张同尺寸的黑白图，白的地方算发光（画师自己圈）。

  python tools/gen/mob_textures.py elite <id> [--color #ffc94a] [--mode ring|edge] [--alpha 0.85]
      <id>_elite.png：精英纹路，叠在主贴图上（MobEliteLayer，受光照）。
      ring（默认）= 发光纹路外面再描一圈（要先有 _glow.png）；edge = 每块不透明区域内沿一圈（没有发光纹路的怪用）。

  python tools/gen/mob_textures.py tint <id> <名字>=<#rrggbb> [...] [--mode multiply|hue]
      <id>_<名字>.png：整张换色。multiply = 按亮度乘颜色（灰度主贴图用，余烬灵、元素游灵这种）；
      hue = 换成那个颜色的色相、保留原来的明暗和饱和度。

  python tools/gen/mob_textures.py placeholder <id> [--from tools/gen/mob_uv/<id>_1x.png] [--color #8a7fb0] [--force]
      主贴图还没画时出一张占位：拿 uv_template.py 的填色图按亮度染成一种颜色，模型进游戏能看出部位。已有主贴图不覆盖（--force 才覆盖）。

  python tools/gen/mob_textures.py all <id>
      glow + elite（默认参数）。

  python tools/gen/mob_textures.py check [--color #ffc94a] [--hue-tolerance 20]
      扫 textures/entity/*/*_elite.png，精英纹路的主色相离 --color 超过容差（度）就报错、退出码 1。
      精英层一律描金，好和本体的发光纹分开；派生时手滑带了别的 --color 会在这里拦下。

共同参数：--src 主贴图路径（默认按 id 找）、--out-dir 输出目录（默认主贴图所在目录；试跑时指到别处）。
"""
import argparse
import colorsys
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ENTITY_TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'essentialcraft5', 'textures', 'entity')
UV_DIR = os.path.join(ROOT, 'tools', 'gen', 'mob_uv')


def parse_color(text):
    t = text.strip().lstrip('#')
    if len(t) != 6:
        raise SystemExit('颜色要写成 #rrggbb：%s' % text)
    return tuple(int(t[i:i + 2], 16) for i in (0, 2, 4))


def default_src(mob_id):
    return os.path.join(ENTITY_TEX, mob_id, mob_id + '.png')


def out_path(args, suffix):
    out_dir = args.out_dir or os.path.dirname(os.path.abspath(args.src))
    os.makedirs(out_dir, exist_ok=True)
    return os.path.join(out_dir, '%s_%s.png' % (args.id, suffix))


def load(path):
    if not os.path.isfile(path):
        raise SystemExit('找不到贴图：%s' % path)
    return Image.open(path).convert('RGBA')


def save(img, path):
    img.save(path)
    print('写出', os.path.relpath(path, ROOT) if path.startswith(ROOT) else path)


# ---------------------------------------------------------------- 发光层

def glow_mask(img, min_value, min_sat, colors, tolerance, mask_path):
    w, h = img.size
    px = img.load()
    if mask_path:
        m = Image.open(mask_path).convert('L')
        if m.size != img.size:
            raise SystemExit('遮罩尺寸 %s 和主贴图 %s 不一样' % (m.size, img.size))
        mp = m.load()
        return {(x, y) for y in range(h) for x in range(w) if mp[x, y] >= 128 and px[x, y][3] > 0}
    out = set()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if colors:
                if any(abs(r - c[0]) <= tolerance and abs(g - c[1]) <= tolerance and abs(b - c[2]) <= tolerance for c in colors):
                    out.add((x, y))
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if v >= min_value and s >= min_sat:
                out.add((x, y))
    return out


def make_glow(img, mask):
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    src, dst = img.load(), out.load()
    for x, y in mask:
        r, g, b, _ = src[x, y]
        dst[x, y] = (r, g, b, 255)
    return out


# ---------------------------------------------------------------- 精英层

NEIGHBOURS = ((1, 0), (-1, 0), (0, 1), (0, -1))


def ring(mask, opaque, size):
    """mask 外面一圈（只在不透明的像素上，不越出部位）"""
    w, h = size
    out = set()
    for x, y in mask:
        for dx, dy in NEIGHBOURS:
            p = (x + dx, y + dy)
            if 0 <= p[0] < w and 0 <= p[1] < h and p not in mask and p in opaque:
                out.add(p)
    return out


def edge(opaque, size):
    """每块不透明区域的内沿：自己不透明、四邻有透明或出界"""
    w, h = size
    out = set()
    for x, y in opaque:
        for dx, dy in NEIGHBOURS:
            p = (x + dx, y + dy)
            if not (0 <= p[0] < w and 0 <= p[1] < h) or p not in opaque:
                out.add((x, y))
                break
    return out


def make_elite(img, pixels, color, alpha):
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    src, dst = img.load(), out.load()
    a = max(0, min(255, int(round(alpha * 255))))
    for x, y in pixels:
        # 纹路颜色跟着底下像素的明暗走一点，不是一片死平的颜色
        r, g, b, _ = src[x, y]
        lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
        k = 0.75 + 0.35 * lum
        dst[x, y] = tuple(min(255, int(c * k)) for c in color) + (a,)
    return out


# ---------------------------------------------------------------- 换色

def tint(img, color, mode):
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    src, dst = img.load(), out.load()
    th, ts, _ = colorsys.rgb_to_hsv(*(c / 255 for c in color))
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            if mode == 'multiply':
                lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
                dst[x, y] = tuple(min(255, int(round(c * lum))) for c in color) + (a,)
            else:
                _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                # 原来几乎没颜色的像素（白、灰、黑）给一点目标饱和度，不然整张换不过去
                s2 = max(s, ts * 0.35) if s < 0.08 else s
                nr, ng, nb = colorsys.hsv_to_rgb(th, s2, v)
                dst[x, y] = (int(round(nr * 255)), int(round(ng * 255)), int(round(nb * 255)), a)
    return out


# ---------------------------------------------------------------- 主程序

def cmd_glow(args):
    img = load(args.src)
    colors = [parse_color(c) for c in args.colors.split(',')] if args.colors else []
    mask = glow_mask(img, args.min_value, args.min_sat, colors, args.tolerance, args.mask)
    if not mask:
        print('警告：一个发光像素都没挑出来（调低 --min-value / --min-sat，或用 --colors / --mask）')
    save(make_glow(img, mask), out_path(args, 'glow'))
    print('  发光像素 %d 个（共 %d×%d）' % (len(mask), img.width, img.height))
    return mask


def cmd_elite(args):
    img = load(args.src)
    px = img.load()
    opaque = {(x, y) for y in range(img.height) for x in range(img.width) if px[x, y][3] > 0}
    if args.mode == 'ring':
        glow_file = out_path(args, 'glow')
        if not os.path.isfile(glow_file):
            raise SystemExit('ring 要先有发光层：%s（先跑 glow，或改用 --mode edge）' % glow_file)
        gp = load(glow_file).load()
        mask = {(x, y) for y in range(img.height) for x in range(img.width) if gp[x, y][3] > 0}
        pixels = ring(mask, opaque, img.size)
        if not pixels:
            print('警告：发光纹路外面没有可描的像素，改用 edge')
            pixels = edge(opaque, img.size)
    else:
        pixels = edge(opaque, img.size)
    save(make_elite(img, pixels, parse_color(args.color), args.alpha), out_path(args, 'elite'))
    print('  精英纹路 %d 个像素' % len(pixels))


def cmd_tint(args):
    img = load(args.src)
    for spec in args.variants:
        if '=' not in spec:
            raise SystemExit('换色写成 名字=#rrggbb：%s' % spec)
        name, color = spec.split('=', 1)
        save(tint(img, parse_color(color), args.mode), out_path(args, name.strip()))


def hue_deg(rgb):
    h, _, _ = colorsys.rgb_to_hsv(*(c / 255 for c in rgb))
    return h * 360


def cmd_check(args):
    want = hue_deg(parse_color(args.color))
    bad = 0
    count = 0
    for mob_id in sorted(os.listdir(ENTITY_TEX)):
        path = os.path.join(ENTITY_TEX, mob_id, mob_id + '_elite.png')
        if not os.path.isfile(path):
            continue
        count += 1
        img = load(path)
        # 按不透明度加权的色相直方图，取最多的那一档当主色相（1 度一档）
        bins = [0.0] * 360
        for r, g, b, a in img.getdata():
            if a == 0:
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s < 0.15 or v < 0.15:
                continue
            bins[int(hue_deg((r, g, b))) % 360] += a
        if not any(bins):
            print('错误：%s 没有带颜色的像素' % os.path.relpath(path, ROOT))
            bad += 1
            continue
        main_hue = max(range(360), key=lambda i: bins[i])
        diff = abs((main_hue - want + 180) % 360 - 180)
        if diff > args.hue_tolerance:
            print('错误：%s 主色相 %d 度，和精英色 %s（%d 度）差 %d 度' % (os.path.relpath(path, ROOT), main_hue, args.color, want, diff))
            bad += 1
    print('精英层 %d 张，%d 张颜色不对' % (count, bad))
    return 1 if bad else 0


def cmd_placeholder(args):
    src = args.from_path or os.path.join(UV_DIR, args.id + '_1x.png')
    base = load(src)
    target = args.src
    if os.path.isfile(target) and not args.force:
        raise SystemExit('主贴图已经有了，不覆盖：%s（要覆盖加 --force）' % target)
    out = tint(base, parse_color(args.color), 'multiply')
    os.makedirs(os.path.dirname(os.path.abspath(target)), exist_ok=True)
    save(out, target)


def main(argv=None):
    ap = argparse.ArgumentParser(description='新怪物的派生贴图')
    sub = ap.add_subparsers(dest='cmd', required=True)

    def common(p):
        p.add_argument('id', help='实体 id（贴图目录名）')
        p.add_argument('--src', help='主贴图（默认 textures/entity/<id>/<id>.png）')
        p.add_argument('--out-dir', help='输出目录（默认主贴图所在目录）')

    def glow_args(p):
        p.add_argument('--min-value', type=float, default=0.82)
        p.add_argument('--min-sat', type=float, default=0.30)
        p.add_argument('--colors', default='')
        p.add_argument('--tolerance', type=int, default=24)
        p.add_argument('--mask')

    def elite_args(p):
        p.add_argument('--color', default='#ffc94a')
        p.add_argument('--mode', choices=('ring', 'edge'), default='ring')
        p.add_argument('--alpha', type=float, default=0.85)

    p = sub.add_parser('glow')
    common(p)
    glow_args(p)
    p = sub.add_parser('elite')
    common(p)
    elite_args(p)
    p = sub.add_parser('tint')
    common(p)
    p.add_argument('variants', nargs='+', help='名字=#rrggbb')
    p.add_argument('--mode', choices=('multiply', 'hue'), default='multiply')
    p = sub.add_parser('placeholder')
    common(p)
    p.add_argument('--from', dest='from_path')
    p.add_argument('--color', default='#8a7fb0')
    p.add_argument('--force', action='store_true')
    p = sub.add_parser('all')
    common(p)
    glow_args(p)
    elite_args(p)
    p = sub.add_parser('check')
    p.add_argument('--color', default='#ffc94a')
    p.add_argument('--hue-tolerance', type=float, default=20)

    args = ap.parse_args(argv)
    if args.cmd == 'check':
        return cmd_check(args)
    args.src = args.src or default_src(args.id)
    if args.cmd == 'glow':
        cmd_glow(args)
    elif args.cmd == 'elite':
        cmd_elite(args)
    elif args.cmd == 'tint':
        cmd_tint(args)
    elif args.cmd == 'placeholder':
        cmd_placeholder(args)
    else:
        cmd_glow(args)
        cmd_elite(args)
    return 0


if __name__ == '__main__':
    sys.exit(main())
