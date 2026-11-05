"""界面版式检查：读 runClient -PecShowroom -PecGuiAudit 录下的 run/guiaudit/<语言>/*.json，查文字和元素的摆放问题。

用法：python tools/guiaudit/analyze.py [run/guiaudit] [--lang zh_cn] [--all]
    默认只列有问题的截图；--all 连没问题的也列出来（看覆盖了哪些界面）。

坐标都是界面坐标（未乘界面缩放）。文字的墨迹框按原版字体算：宽 = font.width - 1（最后一个字后面那 1 像素间距不算），
高 8；带阴影各加 1。查的项目：
  OUT      文字有一部分落在界面底板外面（「飞出去」）
  EDGE     文字离底板边缘不到 3 像素，压在边框上
  OFFSCR   文字超出屏幕
  SLOT     文字压到格子的框（18×18）
  ITEM     文字压到不在格子里的物品图标
  TEXT     两段文字互相重叠
  CROSS    文字骑在某个贴图元素（文字条、进度条、按钮……）的边上：一部分在里面一部分在外面
  RIM      文字在元素里面，但贴着元素的边框（不到 1 像素）
  TIGHT    带数字的文字离装它的框（文字条或底板内边）右边不到 12 像素，数值变长两位就出框
  CLIP     文字比裁剪区宽，被裁掉了
  SMALL    文字被缩到 80% 以下
  OVERLAP  两个贴图元素部分重叠（不是一个套在另一个里面）
  SLOTOUT  格子的框落在底板外面
  SLOTS    两个格子重叠
  BUSY     （像素级）把贴图和填色按绘制顺序叠出来，文字墨迹框和外圈 1 像素里有和底色差很多的像素：压着边框、花纹或格子边
  NEAR     （像素级）文字离边框 / 花纹只隔 1～2 像素（原版标题离边框 4 像素）
  ALIGN    几段文字的左边只差 1～4 像素，像是想对齐没对上
  LOWCON   （像素级）文字颜色和底下的底色对比度不到 3
"""
import json
import os
import sys
from collections import defaultdict

from PIL import Image

EDGE_MARGIN = 3
PANEL_MIN = 40


def rect(o):
    return (o['x0'], o['y0'], o['x1'], o['y1'])


def ink(o):
    """文字的墨迹框"""
    scale = o.get('scale', 1.0)
    shadow = 1 if o.get('shadow') else 0
    w = max(0, o['w'] - 1 + shadow) * scale
    h = (8 + shadow) * scale
    return (o['x0'], o['y0'], o['x0'] + w, o['y0'] + h)


def inter(a, b):
    x0, y0 = max(a[0], b[0]), max(a[1], b[1])
    x1, y1 = min(a[2], b[2]), min(a[3], b[3])
    if x1 - x0 <= 0.01 or y1 - y0 <= 0.01:
        return None
    return (x0, y0, x1, y1)


def contains(outer, inner, pad=0.0):
    return (inner[0] >= outer[0] + pad - 1e-3 and inner[1] >= outer[1] + pad - 1e-3
            and inner[2] <= outer[2] - pad + 1e-3 and inner[3] <= outer[3] - pad + 1e-3)


def area(r):
    return max(0, r[2] - r[0]) * max(0, r[3] - r[1])


def fmt(r):
    return '(%g,%g)-(%g,%g)' % tuple(round(v, 1) for v in r)


def outside_pixels(box, rects):
    """box 里不被 rects 盖住的像素数（按 1 像素网格采样）"""
    n = 0
    y = box[1] + 0.5
    while y < box[3]:
        x = box[0] + 0.5
        while x < box[2]:
            if not any(r[0] <= x < r[2] and r[1] <= y < r[3] for r in rects):
                n += 1
            x += 1
        y += 1
    return n


def merge_rects(items):
    """把同一张贴图横着 / 竖着拼起来的几段（文字条的左端、中段、右端）合成一个元素"""
    items = [dict(i) for i in items]
    changed = True
    while changed:
        changed = False
        for i in range(len(items)):
            for j in range(i + 1, len(items)):
                a, b = items[i], items[j]
                if a.get('tex') != b.get('tex') or a['type'] != b['type']:
                    continue
                ra, rb = a['r'], b['r']
                same_row = abs(ra[1] - rb[1]) < 1e-3 and abs(ra[3] - rb[3]) < 1e-3 and ra[0] <= rb[2] + 1e-3 and rb[0] <= ra[2] + 1e-3
                if same_row:
                    a['r'] = (min(ra[0], rb[0]), min(ra[1], rb[1]), max(ra[2], rb[2]), max(ra[3], rb[3]))
                    a['at'] = a['at'] if a['at'] == b['at'] else a['at'] + ' + ' + b['at']
                    items.pop(j)
                    changed = True
                    break
            if changed:
                break
    return items


def analyze(doc):
    issues = []
    ops = doc.get('ops', [])
    sw, sh = doc['width'], doc['height']
    panel = None
    if 'panel' in doc:
        p = doc['panel']
        panel = (p['x'], p['y'], p['x'] + p['w'], p['y'] + p['h'])
    texts = [o for o in ops if o['type'] == 'text' and o.get('text', '').strip()]
    shapes = []
    for o in ops:
        if o['type'] in ('blit', 'sprite', 'fill', 'gradient'):
            r = rect(o)
            # 全屏的暗底（renderBackground）不算
            if r[0] <= 0 and r[1] <= 0 and r[2] >= sw and r[3] >= sh:
                continue
            if area(r) <= 0:
                continue
            shapes.append({'type': o['type'], 'tex': o.get('tex'), 'r': r, 'at': o.get('at', '')})
    # 底板：容器界面的 imageWidth × imageHeight，外加 40×40 以上、不在底板里的大块贴图（侧栏）
    panels = []
    if panel:
        panels.append(panel)
    for s in shapes:
        r = s['r']
        # 非容器界面（研究手册、宅男终端）的面板是填色画的，也算底板
        kinds = ('blit', 'sprite') if panel else ('blit', 'sprite', 'fill', 'gradient')
        if s['type'] in kinds and r[2] - r[0] >= PANEL_MIN and r[3] - r[1] >= PANEL_MIN:
            if not panel or not contains(panel, r):
                panels.append(r)
    if not panels:
        issues.append(('NOPANEL', '', '找不到底板（没有容器尺寸，也没有 40×40 以上的贴图）'))
    # 和底板同一张贴图的小块（盖掉发射器 3×3 的补丁、各格子的框）算底板的一部分；格子另有 SLOT 检查
    panel_tex = {s['tex'] for s in shapes if s['type'] == 'blit' and panel and abs(s['r'][0] - panel[0]) < 1e-3
                 and abs(s['r'][1] - panel[1]) < 1e-3 and abs(s['r'][2] - panel[2]) < 1e-3 and abs(s['r'][3] - panel[3]) < 1e-3}
    shapes_el = [s for s in shapes if not (s['type'] == 'blit' and s['tex'] in panel_tex and panel and contains(panel, s['r']))]
    elements = merge_rects([s for s in shapes_el if not any(abs(s['r'][0] - p[0]) < 1e-3 and abs(s['r'][1] - p[1]) < 1e-3
                                                          and abs(s['r'][2] - p[2]) < 1e-3 and abs(s['r'][3] - p[3]) < 1e-3 for p in panels)])
    # 元素里不算底板那几块，也不算比底板还大的
    elements = [e for e in elements if not (panel and contains(e['r'], panel))]
    slots = [(s['x'] - 1, s['y'] - 1, s['x'] + 17, s['y'] + 17, s['index']) for s in doc.get('slots', [])]
    slot_items = {(s['x'], s['y']) for s in doc.get('slots', [])}
    items = [o for o in ops if o['type'] == 'item' and (round(o['x0']), round(o['y0'])) not in slot_items]

    for t in texts:
        box = ink(t)
        label = '「%s」 %s @%s' % (t['text'], fmt(box), t.get('at', ''))
        if panels:
            out = outside_pixels(box, panels)
            if out > 0:
                issues.append(('OUT', t.get('at', ''), '%s 有 %d 像素在底板外' % (label, out)))
            else:
                holder = next((p for p in panels if contains(p, box)), None)
                if holder is not None:
                    m = min(box[0] - holder[0], box[1] - holder[1], holder[2] - box[2], holder[3] - box[3])
                    if m < EDGE_MARGIN:
                        issues.append(('EDGE', t.get('at', ''), '%s 离底板边 %.1f 像素' % (label, m)))
        if box[0] < 0 or box[1] < 0 or box[2] > sw or box[3] > sh:
            issues.append(('OFFSCR', t.get('at', ''), '%s 超出屏幕 %dx%d' % (label, sw, sh)))
        for s in slots:
            if inter(box, s[:4]):
                issues.append(('SLOT', t.get('at', ''), '%s 压到格子 #%d %s' % (label, s[4], fmt(s[:4]))))
        for it in items:
            if inter(box, rect(it)):
                issues.append(('ITEM', t.get('at', ''), '%s 压到物品图标 %s %s' % (label, it.get('item'), fmt(rect(it)))))
        # 骑边 / 贴边按几何判定误报太多（按钮九宫格、几段拼成的框），交给像素级的 BUSY / NEAR；这里只留给填色块用
        for e in elements:
            r = e['r']
            if e['type'] in ('blit', 'sprite') or not inter(box, r):
                continue
            if contains(r, box):
                if not contains(r, box, 1.0) and e['type'] in ('blit', 'sprite') and r[3] - r[1] > 9:
                    issues.append(('RIM', t.get('at', ''), '%s 贴着元素边 %s %s @%s' % (label, e.get('tex') or e['type'], fmt(r), e['at'])))
            elif not contains(box, r):
                issues.append(('CROSS', t.get('at', ''), '%s 骑在元素边上 %s %s @%s' % (label, e.get('tex') or e['type'], fmt(r), e['at'])))
        # 带数字的文字离装它的框右边不到 12 像素（两位数字）：数值一大就会出框
        if any(ch.isdigit() for ch in t['text']):
            holders = [e['r'] for e in elements if e['type'] in ('blit', 'sprite') and contains(e['r'], box)]
            if holders:
                holder, pad = min(holders, key=area), 1
            else:
                holder, pad = next((p for p in panels if contains(p, box)), None), EDGE_MARGIN + 1
            if holder is not None:
                slack = holder[2] - pad - box[2]
                if slack < 12:
                    issues.append(('TIGHT', t.get('at', ''), '%s 离右边界只剩 %.0f 像素（框 %s）' % (label, slack, fmt(holder))))
        if 'clip' in t:
            c = t['clip']
            if box[0] < c[0] - 0.5 or box[2] > c[2] + 0.5:
                issues.append(('CLIP', t.get('at', ''), '%s 比裁剪区 %s 宽，被裁掉' % (label, c)))
        if t.get('scale', 1.0) < 0.8:
            issues.append(('SMALL', t.get('at', ''), '%s 缩到 %d%%' % (label, round(t['scale'] * 100))))
    for i, a in enumerate(texts):
        for b in texts[i + 1:]:
            if a['text'] == b['text'] and abs(a['x0'] - b['x0']) < 1e-3 and abs(a['y0'] - b['y0']) < 1e-3:
                continue
            if inter(ink(a), ink(b)):
                issues.append(('TEXT', a.get('at', ''), '「%s」@%s 和「%s」@%s 重叠' % (a['text'], a.get('at', ''), b['text'], b.get('at', ''))))
    lefts = sorted({round(t['x0']) for t in texts if t.get('scale', 1.0) == 1.0})
    for i, a in enumerate(lefts):
        for b in lefts[i + 1:]:
            if 0 < b - a <= 4:
                ta = [t for t in texts if round(t['x0']) == a]
                tb = [t for t in texts if round(t['x0']) == b]
                issues.append(('ALIGN', '', '左边 x=%d（%s）和 x=%d（%s）差 %d 像素' % (
                    a, '、'.join('「%s」@%s' % (t['text'][:12], t.get('at', '')) for t in ta[:3]), b,
                    '、'.join('「%s」@%s' % (t['text'][:12], t.get('at', '')) for t in tb[:3]), b - a)))
    for i, a in enumerate(elements):
        for b in elements[i + 1:]:
            x = inter(a['r'], b['r'])
            # 1~2 像素宽的是描边线（选中框、悬停框），在角上相交不算
            thin = any(min(r[2] - r[0], r[3] - r[1]) <= 2 for r in (a['r'], b['r']))
            if x and not thin and not contains(a['r'], b['r']) and not contains(b['r'], a['r']):
                issues.append(('OVERLAP', a['at'], '%s %s @%s 和 %s %s @%s 部分重叠 %s' % (
                    a.get('tex') or a['type'], fmt(a['r']), a['at'], b.get('tex') or b['type'], fmt(b['r']), b['at'], fmt(x))))
    for s in slots:
        if panels and outside_pixels(s[:4], panels) > 0:
            issues.append(('SLOTOUT', '', '格子 #%d %s 在底板外' % (s[4], fmt(s[:4]))))
    for i, a in enumerate(slots):
        for b in slots[i + 1:]:
            if inter((a[0] + 1, a[1] + 1, a[2] - 1, a[3] - 1), (b[0] + 1, b[1] + 1, b[2] - 1, b[3] - 1)):
                issues.append(('SLOTS', '', '格子 #%d 和 #%d 重叠' % (a[4], b[4])))
    if 'error' in doc:
        issues.append(('ERROR', '', doc['error']))
    issues.extend(pixel_check(doc))
    return issues


# ---------------------------------------------------------------- 像素级：按绘制顺序把贴图叠出来，看文字底下和外圈

HERE = os.path.dirname(os.path.abspath(__file__))
TEXTURE_ROOTS = {
    'essentialcraft5': [os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'essentialcraft5'),
                        os.path.join(HERE, '..', '..', 'src', 'generated', 'resources', 'assets', 'essentialcraft5')],
    # 原版贴图：从 client-extra.jar 解出 assets/minecraft/textures/gui，路径用环境变量 EC5_VANILLA_ASSETS 指过去
    'minecraft': [os.environ.get('EC5_VANILLA_ASSETS', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'build', 'vanilla', 'assets', 'minecraft'))],
}
_textures = {}
BUSY_DELTA = 60
NEAR_RING = 3
MIN_CONTRAST = 3.0


def texture(name):
    if name not in _textures:
        ns, path = name.split(':', 1) if ':' in name else ('minecraft', name)
        img = None
        for root in TEXTURE_ROOTS.get(ns, []):
            f = os.path.join(root, *path.split('/'))
            if os.path.exists(f):
                img = Image.open(f).convert('RGBA')
                break
        _textures[name] = img
    return _textures[name]


def argb(s):
    v = int(s, 16)
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, (v >> 24) & 255)


def luminance(c):
    def ch(v):
        v /= 255.0
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    return 0.2126 * ch(c[0]) + 0.7152 * ch(c[1]) + 0.0722 * ch(c[2])


def pixel_check(doc):
    issues = []
    sw, sh = doc['width'], doc['height']
    canvas = Image.new('RGBA', (sw, sh), (0, 0, 0, 0))
    for o in doc.get('ops', []):
        x0, y0, x1, y1 = (int(round(v)) for v in rect(o))
        if x1 <= x0 or y1 <= y0 or 'scale' in o:
            if o['type'] != 'text':
                continue
        # 整个画在屏幕外的（手册翻页、滚动时会有）没东西可叠，裁剪区算出来也是负的
        if o['type'] != 'text' and (x1 <= 0 or y1 <= 0 or x0 >= sw or y0 >= sh):
            continue
        if o['type'] == 'blit':
            if x0 <= 0 and y0 <= 0 and x1 >= sw and y1 >= sh:
                continue
            tex = texture(o['tex'])
            if tex is None:
                continue
            kx, ky = tex.width / o['tw'], tex.height / o['th']
            box = (int(round(o['u'] * kx)), int(round(o['v'] * ky)),
                   int(round((o['u'] + o['uw']) * kx)), int(round((o['v'] + o['vh']) * ky)))
            if box[2] <= box[0] or box[3] <= box[1]:
                continue
            part = tex.crop(box).resize((x1 - x0, y1 - y0), Image.NEAREST)
            canvas.alpha_composite(part, (max(0, x0), max(0, y0)),
                                   (max(0, -x0), max(0, -y0)))
        elif o['type'] in ('fill', 'gradient'):
            if x0 <= 0 and y0 <= 0 and x1 >= sw and y1 >= sh:
                continue
            c = argb(o['color'].split('/')[0])
            if c[3] == 0:
                continue
            layer = Image.new('RGBA', (max(1, x1 - x0), max(1, y1 - y0)), c)
            canvas.alpha_composite(layer, (max(0, x0), max(0, y0)), (max(0, -x0), max(0, -y0)))
        elif o['type'] == 'text' and o.get('text', '').strip():
            b = ink(o)
            ib = (int(b[0]), int(b[1]), int(b[2] + 0.999), int(b[3] + 0.999))
            ring = (max(0, ib[0] - NEAR_RING), max(0, ib[1] - NEAR_RING), min(sw, ib[2] + NEAR_RING), min(sh, ib[3] + NEAR_RING))
            if ring[2] <= ring[0] or ring[3] <= ring[1]:
                continue
            under = canvas.crop((max(0, ib[0] - 1), max(0, ib[1] - 1), min(sw, ib[2] + 1), min(sh, ib[3] + 1)))
            px = [p for p in under.getdata() if p[3] > 0]
            if len(px) < 0.5 * under.width * under.height:
                continue
            lums = sorted(luminance(p) for p in px)
            med = lums[len(lums) // 2]
            med_px = min(px, key=lambda p: abs(luminance(p) - med))
            region = canvas.crop(ring)
            w = region.width
            nearest = {}
            for i, p in enumerate(region.getdata()):
                if p[3] == 0 or max(abs(p[0] - med_px[0]), abs(p[1] - med_px[1]), abs(p[2] - med_px[2])) <= BUSY_DELTA:
                    continue
                x, y = ring[0] + i % w, ring[1] + i // w
                dx = ib[0] - x if x < ib[0] else x - (ib[2] - 1) if x >= ib[2] else 0
                dy = ib[1] - y if y < ib[1] else y - (ib[3] - 1) if y >= ib[3] else 0
                d = max(dx, dy)
                side = '中' if d == 0 else ('左' if x < ib[0] else '右' if x >= ib[2] else '上' if y < ib[1] else '下')
                nearest.setdefault(d, set()).add(side)
            label = '「%s」 %s @%s' % (o['text'], fmt(b), o.get('at', ''))
            if nearest:
                d = min(nearest)
                if d <= 1:
                    count = sum(1 for _ in nearest if _ <= 1)
                    issues.append(('BUSY', o.get('at', ''), '%s %s压着边框 / 花纹（%s）' % (
                        label, '墨迹里' if d == 0 else '外圈 1 像素', ''.join(sorted(nearest[d])))))
                else:
                    issues.append(('NEAR', o.get('at', ''), '%s 离边框 / 花纹只隔 %d 像素（%s）' % (label, d - 1, ''.join(sorted(nearest[d])))))
            fg = argb(o['color'])
            if fg[3] == 0:
                fg = (fg[0], fg[1], fg[2], 255)
            la, lb = luminance(fg), luminance(med_px)
            ratio = (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
            if ratio < MIN_CONTRAST:
                issues.append(('LOWCON', o.get('at', ''), '%s 和底色 #%02X%02X%02X 对比度只有 %.1f' % (label, med_px[0], med_px[1], med_px[2], ratio)))
    return issues


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    root = args[0] if args else os.path.join(os.path.dirname(__file__), '..', '..', 'run', 'guiaudit')
    show_all = '--all' in sys.argv
    langs = sorted(os.listdir(root))
    if '--lang' in sys.argv:
        langs = [sys.argv[sys.argv.index('--lang') + 1]]
    for lang in langs:
        folder = os.path.join(root, lang)
        by_screen = defaultdict(list)
        for name in sorted(os.listdir(folder)):
            if not name.endswith('.json'):
                continue
            with open(os.path.join(folder, name), encoding='utf-8') as f:
                doc = json.load(f)
            # 原版界面（创造栏、背包）不是本模组画的
            if doc['screen'].startswith('net.minecraft.') or '.dev.' in doc['screen']:
                continue
            issues = analyze(doc)
            by_screen[doc['screen'].rsplit('.', 1)[-1]].append((doc['name'], issues))
        print('=' * 20, lang, '：%d 种界面，%d 张' % (len(by_screen), sum(len(v) for v in by_screen.values())))
        for screen, shots in sorted(by_screen.items()):
            bad = [(n, i) for n, i in shots if i]
            if not bad and not show_all:
                continue
            print('\n## %s（%d 张，%d 张有问题）' % (screen, len(shots), len(bad)))
            for name, issues in shots:
                if not issues and not show_all:
                    continue
                print('  - %s' % name)
                seen = set()
                for kind, at, msg in issues:
                    if (kind, msg) in seen:
                        continue
                    seen.add((kind, msg))
                    print('      %-7s %s' % (kind, msg))


if __name__ == '__main__':
    main()
