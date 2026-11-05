# -*- coding: utf-8 -*-
"""实体贴图的 UV 底图（第十三批 13c-Fb，batch13.md §10「贴图」：先出底图再画，免得画完对不上模型返工）。

读一个模型（手写 LayerDefinition 的 Java 源码，或一份部位表 JSON），按原版方块盒的 UV 排法把每个部位的六个面画出来：

    (u+d, v)      w×d  顶        (u+d+w, v)      w×d  底
    (u, v+d)      d×h  右        (u+d, v+d)      w×h  前
    (u+d+w, v+d)  d×h  左        (u+2d+w, v+d)   w×h  后

左右按怪物自己的左右（模型 -X 是它的右手边）；顶面的下沿接前面的上沿，侧面、前后面的上沿都是部位的上方。

输出两张（默认放 tools/gen/mob_uv/）：
  <id>.png     放大的底图：每个面填色、描边，写上部位名和面名，底下一行图例。给画师对着画。
  <id>_1x.png  原尺寸的填色图：每个部位一个色相、六个面明暗不同，没画到的地方透明。可以直接当占位贴图，
               或交给 mob_textures.py placeholder 放进资源目录。

用法：
  python tools/gen/uv_template.py --java src/main/java/.../client/model/XxxModel.java --id xxx
  python tools/gen/uv_template.py --parts parts.json --id xxx [--out 目录] [--scale 8]

部位表 JSON：
  {"texture": [64, 64],
   "parts": [{"name": "head", "u": 0, "v": 0, "size": [8, 8, 8]},
             {"name": "left_arm", "u": 40, "v": 16, "size": [4, 12, 4], "mirror": true}]}

Java 源码的读法（正则，够读本模组手写的模型）：
  - LayerDefinition.create(mesh, 宽, 高) 取贴图尺寸；
  - addOrReplaceChild("名字" 或常量, CubeListBuilder.create().texOffs(u, v).addBox(...)...) 取部位，
    同一个部位连着几个 texOffs / addBox 的各算一块（名字后面加 #2、#3，addBox 第一个参数带名字的加那个名字）；.mirror() 记为镜像；
  - 名字是 private static final String 常量、PartNames.XXX 的都认；
  - 用了 HumanoidModel.createMesh 的补上原版人形的七个部位（64×32 / 64×64 两种贴图都按这套 UV）。
读不到的写法（循环里加部位、拼出来的名字）改用部位表。
重叠的面（镜像共用的除外）和超出贴图的面会打印警告。
"""
import argparse
import colorsys
import json
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_OUT = os.path.join(ROOT, 'tools', 'gen', 'mob_uv')

# 原版 HumanoidModel.createMesh（1.20.1）的 UV
HUMANOID = [
    ('head', 0, 0, (8, 8, 8), False),
    ('hat', 32, 0, (8, 8, 8), False),
    ('body', 16, 16, (8, 12, 4), False),
    ('right_arm', 40, 16, (4, 12, 4), False),
    ('left_arm', 40, 16, (4, 12, 4), True),
    ('right_leg', 0, 16, (4, 12, 4), False),
    ('left_leg', 0, 16, (4, 12, 4), True),
]

# 面：名字、在部位矩形里的位置（按 w/h/d 算）、明暗
FACES = (
    ('顶', lambda w, h, d: (d, 0, w, d), 1.00),
    ('底', lambda w, h, d: (d + w, 0, w, d), 0.55),
    ('右', lambda w, h, d: (0, d, d, h), 0.75),
    ('前', lambda w, h, d: (d, d, w, h), 0.92),
    ('左', lambda w, h, d: (d + w, d, d, h), 0.70),
    ('后', lambda w, h, d: (2 * d + w, d, w, h), 0.62),
)


class Part:
    def __init__(self, name, u, v, size, mirror=False):
        self.name = name
        self.u = int(round(u))
        self.v = int(round(v))
        self.w, self.h, self.d = (int(round(abs(x))) for x in size)
        self.mirror = mirror

    def faces(self):
        """[(面名, x, y, 宽, 高, 明暗)]，宽或高为 0 的面跳过（薄片只有前后两面）"""
        out = []
        for label, rect, shade in FACES:
            x, y, fw, fh = rect(self.w, self.h, self.d)
            if fw > 0 and fh > 0:
                out.append((label, self.u + x, self.v + y, fw, fh, shade))
        return out


# ---------------------------------------------------------------- 读 Java

def _split_args(s):
    parts, depth, cur = [], 0, ''
    for ch in s:
        if ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth -= 1
        if ch == ',' and depth == 0:
            parts.append(cur.strip())
            cur = ''
        else:
            cur += ch
    if cur.strip():
        parts.append(cur.strip())
    return parts


def _num(s):
    s = s.strip()
    s = re.sub(r'(?<=[0-9.])[fFdD]$', '', s)
    try:
        return float(s)
    except ValueError:
        return None


def _matching(src, start):
    """src[start] 是 '('，返回配对的 ')' 的位置"""
    depth = 0
    i = start
    in_str = False
    while i < len(src):
        ch = src[i]
        if in_str:
            if ch == '\\':
                i += 1
            elif ch == '"':
                in_str = False
        elif ch == '"':
            in_str = True
        elif ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError('括号没配上')


def read_java(path):
    src = open(path, encoding='utf-8').read()
    src = re.sub(r'//[^\n]*', '', src)
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    consts = dict(re.findall(r'static\s+final\s+String\s+(\w+)\s*=\s*"([^"]*)"', src))

    size = None
    m = re.search(r'LayerDefinition\.create\(\s*[\w.()]+\s*,\s*(\d+)\s*,\s*(\d+)\s*\)', src)
    if m:
        size = (int(m.group(1)), int(m.group(2)))

    parts = []
    if 'HumanoidModel.createMesh' in src:
        parts.extend(Part(n, u, v, s, mi) for n, u, v, s, mi in HUMANOID)

    for m in re.finditer(r'addOrReplaceChild\(', src):
        open_at = m.end() - 1
        try:
            close_at = _matching(src, open_at)
        except ValueError:
            continue
        args = _split_args(src[open_at + 1:close_at])
        if len(args) < 2 or 'CubeListBuilder' not in args[1]:
            continue
        name = _part_name(args[0], consts)
        chain = args[1]
        u = v = 0
        mirror = False
        count = 0
        for call in re.finditer(r'\.(texOffs|mirror|addBox)\(', chain):
            c_open = call.end() - 1
            c_close = _matching(chain, c_open)
            inner = chain[c_open + 1:c_close]
            kind = call.group(1)
            if kind == 'texOffs':
                a = _split_args(inner)
                u, v = _num(a[0]), _num(a[1])
            elif kind == 'mirror':
                mirror = inner.strip() != 'false'
            else:
                a = _split_args(inner)
                comment = None
                if a and a[0].startswith('"'):
                    comment = a[0].strip('"')
                    a = a[1:]
                nums = [_num(x) for x in a[:6]]
                if len(nums) < 6 or any(n is None for n in nums):
                    print('  跳过读不懂的 addBox（%s）：%s' % (name, inner), file=sys.stderr)
                    continue
                count += 1
                if comment:
                    label = '%s#%s' % (name, comment)
                else:
                    label = name if count == 1 else '%s#%d' % (name, count)
                parts.append(Part(label, u, v, nums[3:6], mirror))
    return size, parts


def _part_name(token, consts):
    token = token.strip()
    if token.startswith('"'):
        return token.strip('"')
    if token in consts:
        return consts[token]
    if token.startswith('PartNames.'):
        return token.split('.', 1)[1].lower()
    return token


def read_parts(path):
    data = json.load(open(path, encoding='utf-8'))
    size = tuple(data['texture'])
    parts = [Part(p['name'], p['u'], p['v'], p['size'], p.get('mirror', False)) for p in data['parts']]
    return size, parts


# ---------------------------------------------------------------- 检查

def check(size, parts):
    tw, th = size
    owner = {}
    warned = set()
    problems = 0
    for p in parts:
        for label, x, y, fw, fh, _ in p.faces():
            if x < 0 or y < 0 or x + fw > tw or y + fh > th:
                print('警告：%s 的%s面 (%d,%d %d×%d) 超出贴图 %d×%d' % (p.name, label, x, y, fw, fh, tw, th))
                problems += 1
            for yy in range(y, y + fh):
                for xx in range(x, x + fw):
                    key = (xx, yy)
                    prev = owner.get(key)
                    mine = (p.u, p.v, p.w, p.h, p.d, label)
                    if prev is None:
                        owner[key] = (mine, p.name)
                    elif prev[0] != mine:
                        pair = tuple(sorted((prev[1], p.name)))
                        if pair not in warned:
                            warned.add(pair)
                            print('警告：%s 和 %s 的面在 (%d,%d) 起重叠' % (pair[0], pair[1], xx, yy))
                            problems += 1
    return problems


# ---------------------------------------------------------------- 画

_FONTS = {}


def _font(px):
    if px not in _FONTS:
        _FONTS[px] = _load_font(px)
    return _FONTS[px]


def _load_font(px):
    for name in ('msyh.ttc', 'simhei.ttf', 'arial.ttf'):
        path = os.path.join(os.environ.get('WINDIR', 'C:/Windows'), 'Fonts', name)
        if os.path.isfile(path):
            try:
                return ImageFont.truetype(path, px)
            except OSError:
                pass
    return ImageFont.load_default()


def part_color(i, n, shade):
    hue = (i * 0.61803398875) % 1.0
    r, g, b = colorsys.hsv_to_rgb(hue, 0.45, 0.35 + 0.6 * shade)
    return int(r * 255), int(g * 255), int(b * 255)


def draw(size, parts, scale, out_big, out_small, title):
    tw, th = size
    small = Image.new('RGBA', (tw, th), (0, 0, 0, 0))
    sp = small.load()
    for i, p in enumerate(parts):
        for label, x, y, fw, fh, shade in p.faces():
            col = part_color(i, len(parts), shade) + (255,)
            for yy in range(max(0, y), min(th, y + fh)):
                for xx in range(max(0, x), min(tw, x + fw)):
                    sp[xx, yy] = col
    small.save(out_small)

    legend_h = 3 * max(14, scale * 2) + 12
    big = Image.new('RGBA', (tw * scale, th * scale + legend_h), (255, 255, 255, 255))
    # 棋盘底：没用到的地方一眼看得出
    cell = max(scale, 4)
    dr = ImageDraw.Draw(big)
    for yy in range(0, th * scale, cell):
        for xx in range(0, tw * scale, cell):
            if (xx // cell + yy // cell) % 2 == 0:
                dr.rectangle([xx, yy, xx + cell - 1, yy + cell - 1], fill=(232, 232, 232, 255))
    # 每 8 像素一道参考线
    for xx in range(0, tw + 1, 8):
        dr.line([xx * scale, 0, xx * scale, th * scale], fill=(205, 205, 215, 255))
    for yy in range(0, th + 1, 8):
        dr.line([0, yy * scale, tw * scale, yy * scale], fill=(205, 205, 215, 255))
    big.alpha_composite(small.resize((tw * scale, th * scale), Image.NEAREST), (0, 0))

    dr = ImageDraw.Draw(big)
    font_px = max(9, min(16, scale * 2))
    small_px = max(8, font_px - 3)
    for p, names in _merge_shared(parts):
        for label, x, y, fw, fh, _ in p.faces():
            x0, y0, x1, y1 = x * scale, y * scale, (x + fw) * scale - 1, (y + fh) * scale - 1
            dr.rectangle([x0, y0, x1, y1], outline=(30, 30, 40, 255))
            if label == '前':
                wrapped = [n.replace('_', '_\n') for n in names]
                _label(dr, x0, y0, x1, y1, ['\n'.join(names) + '\n前', '\n'.join(wrapped) + '\n前', label], font_px)
            else:
                _label(dr, x0, y0, x1, y1, [label], small_px)
    # 图例
    ly = th * scale + 4
    lines = [
        '%s  贴图 %d×%d，每格放大 %d 倍；参考线每 8 像素一道' % (title, tw, th, scale),
        '面名按怪物自己的方向：右 = 它的右手边（模型 -X）；顶面的下沿接前面的上沿',
        '镜像的部位和另一边共用同一块，画一次两边都用',
    ]
    for line in lines:
        dr.text((6, ly), line, fill=(20, 20, 30, 255), font=_font(max(11, min(16, scale * 2))))
        ly += max(14, scale * 2) + 2
    big.save(out_big)


def _merge_shared(parts):
    """镜像共用同一块 UV 的部位合成一项画（名字写在一起），免得后画的盖住先画的名字"""
    out = []
    seen = {}
    for p in parts:
        key = (p.u, p.v, p.w, p.h, p.d)
        if key in seen and p.mirror:
            out[seen[key]][1].append(p.name + '（镜像）')
            continue
        seen.setdefault(key, len(out))
        out.append((p, [p.name + ('（镜像）' if p.mirror else '')]))
    return out


def _label(dr, x0, y0, x1, y1, candidates, font_px):
    """按顺序试几种写法、字号从大到小，第一个放得下的写上；都放不下就不写"""
    w, h = x1 - x0, y1 - y0
    for text in candidates:
        for px in range(font_px, 7, -1):
            font = _font(px)
            box = dr.multiline_textbbox((0, 0), text, font=font, align='center')
            tw, th = box[2] - box[0], box[3] - box[1]
            if tw <= w - 2 and th <= h - 2:
                _put(dr, (x0 + x1) / 2, (y0 + y1) / 2, text, font, box)
                return


def _put(dr, cx, cy, text, font, box):
    tw, th = box[2] - box[0], box[3] - box[1]
    x, y = cx - tw / 2 - box[0], cy - th / 2 - box[1]
    for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        dr.multiline_text((x + ox, y + oy), text, fill=(255, 255, 255, 255), font=font, align='center')
    dr.multiline_text((x, y), text, fill=(10, 10, 20, 255), font=font, align='center')


def main(argv=None):
    ap = argparse.ArgumentParser(description='实体贴图的 UV 底图')
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument('--java', help='模型的 Java 源码（手写 LayerDefinition）')
    src.add_argument('--parts', help='部位表 JSON')
    ap.add_argument('--id', required=True, help='输出文件名（实体 id）')
    ap.add_argument('--out', default=DEFAULT_OUT, help='输出目录（默认 tools/gen/mob_uv）')
    ap.add_argument('--scale', type=int, default=0, help='放大倍数（默认按贴图宽度凑到约 1024 像素）')
    ap.add_argument('--texture', help='贴图尺寸 宽x高（Java 里读不到时用）')
    ap.add_argument('--skip', default='', help='不画的部位（逗号分隔；用别的贴图画的部位，比如单独一张翅膀贴图）')
    args = ap.parse_args(argv)

    if args.java:
        size, parts = read_java(args.java)
    else:
        size, parts = read_parts(args.parts)
    if args.texture:
        w, h = args.texture.lower().split('x')
        size = (int(w), int(h))
    if size is None:
        raise SystemExit('读不到贴图尺寸（LayerDefinition.create 的后两个参数），用 --texture 64x64 指定')
    skip = {x.strip() for x in args.skip.split(',') if x.strip()}
    parts = [p for p in parts if p.name.split('#')[0] not in skip]
    if not parts:
        raise SystemExit('一个部位都没读到，改用 --parts 部位表')

    scale = args.scale or max(2, min(16, 1024 // size[0]))
    os.makedirs(args.out, exist_ok=True)
    out_big = os.path.join(args.out, args.id + '.png')
    out_small = os.path.join(args.out, args.id + '_1x.png')
    problems = check(size, parts)
    draw(size, parts, scale, out_big, out_small, args.id)
    print('%s：%d 个部位，贴图 %d×%d，放大 %d 倍' % (args.id, len(parts), size[0], size[1], scale))
    for p in parts:
        print('  %-14s texOffs(%d, %d) 尺寸 %d×%d×%d%s' % (p.name, p.u, p.v, p.w, p.h, p.d, ' 镜像' if p.mirror else ''))
    print('写出', out_big)
    print('写出', out_small)
    if problems:
        print('有 %d 处警告，见上' % problems)
    return 0


if __name__ == '__main__':
    sys.exit(main())
