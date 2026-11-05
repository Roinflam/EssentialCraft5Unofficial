# -*- coding: utf-8 -*-
"""实体贴图预览：把 64x64 之类的实体贴图按原版 ModelPart 的 UV 排法贴到方块盒模型上，从几个角度画出来。

用法：
  python tools/gen/mob_preview.py tools/gen/mob_preview/<id>.json <主贴图.png> --out <输出.png> [--glow <发光层.png>] [--elite <精英层.png>]
                       [--scale 7] [--hero]

模型 JSON（照模型类的 createLayer 和 pose 里的静止姿势抄）：
  {"texture": [64, 64],
   "parts": [
     {"name": "wraith", "parent": null, "offset": [0, -2, 0], "rot": [0, 0, 0], "boxes": []},
     {"name": "head", "parent": "wraith", "offset": [0, 0, 0], "rot": [0, 0, 0],
      "boxes": [{"uv": [0, 0], "from": [-4, -8, -4], "size": [8, 8, 8], "inflate": 0, "mirror": false}]}
   ]}
  offset / from / size 是模型像素（1/16 格），rot 是弧度（x, y, z），和 PartPose 一样按 Z·Y·X 顺序转。
  部位写 "visible": false 就连同子部位都不画（模型里默认藏起来的部件）。
  模型表可以用 tools/gen/mob_model_json.py 从模型源码生成。

画法和游戏里的对应：
  - 主贴图按 entityCutoutNoCull：alpha < 0.1 的像素不画，其余当不透明，不剔背面；
  - 两盏方向光（原版 Lighting 的 (0.2,1,-0.7) / (-0.2,1,0.7)），环境 0.4、漫射 0.6；
  - 发光层按 eyes：加色、全亮，不受环境光；
  - 精英层按 entityTranslucent 叠在主贴图上，受光照。
上排是亮处（光照等级 15），下排是暗处（光照约 4，洞穴里刚刷出来的样子），发光层两排都一样亮。
"""
import argparse
import json
import math

from PIL import Image, ImageDraw, ImageFont

LIGHT0 = (0.2, 1.0, -0.7)
LIGHT1 = (-0.2, 1.0, 0.7)


def _norm(v):
    n = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / n for c in v)


L0 = _norm(LIGHT0)
L1 = _norm(LIGHT1)


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def mat_apply(m, p):
    x, y, z = p
    return (m[0][0] * x + m[0][1] * y + m[0][2] * z + m[0][3],
            m[1][0] * x + m[1][1] * y + m[1][2] * z + m[1][3],
            m[2][0] * x + m[2][1] * y + m[2][2] * z + m[2][3])


def translate(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


IDENT = translate(0, 0, 0)


def part_matrices(parts):
    by_name = {p['name']: p for p in parts}
    cache = {}

    def get(name):
        if name in cache:
            return cache[name]
        p = by_name[name]
        parent = get(p['parent']) if p.get('parent') else IDENT
        ox, oy, oz = p.get('offset', [0, 0, 0])
        rx, ry, rz = p.get('rot', [0, 0, 0])
        m = mat_mul(parent, translate(ox / 16.0, oy / 16.0, oz / 16.0))
        # PartPose：rotationZYX(z, y, x) = Rz·Ry·Rx
        m = mat_mul(m, mat_mul(rot_z(rz), mat_mul(rot_y(ry), rot_x(rx))))
        cache[name] = m
        return m

    return {p['name']: get(p['name']) for p in parts}


def cube_faces(box, tex_w, tex_h):
    """照 1.20.1 ModelPart.Cube：返回 [(四个顶点(模型像素), 四个 uv(贴图像素), 面名)]。"""
    u, v = box['uv']
    x, y, z = box['from']
    w, h, d = box['size']
    g = box.get('inflate', 0.0)
    mirror = box.get('mirror', False)
    x0, y0, z0 = x - g, y - g, z - g
    x1, y1, z1 = x + w + g, y + h + g, z + d + g
    if mirror:
        x0, x1 = x1, x0
    V = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
         (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    fw, fh, fd = math.floor(w), math.floor(h), math.floor(d)
    f4 = u
    f5 = u + fd
    f6 = u + fd + fw
    f7 = u + fd + fw + fw
    f8 = u + fd + fw + fd
    f9 = u + fd + fw + fd + fw
    f10 = v
    f11 = v + fd
    f12 = v + fd + fh
    polys = [
        ((V[5], V[1], V[2], V[6]), f6, f11, f8, f12, 'left'),    # EAST  (+X = 怪物的左手边)
        ((V[0], V[4], V[7], V[3]), f4, f11, f5, f12, 'right'),   # WEST
        ((V[5], V[4], V[0], V[1]), f5, f10, f6, f11, 'top'),     # DOWN（模型 y 朝下，这面在上）
        ((V[2], V[3], V[7], V[6]), f6, f11, f7, f10, 'bottom'),  # UP
        ((V[1], V[0], V[3], V[2]), f5, f11, f6, f12, 'front'),   # NORTH
        ((V[4], V[5], V[6], V[7]), f8, f11, f9, f12, 'back'),    # SOUTH
    ]
    out = []
    for verts, u1, v1, u2, v2, name in polys:
        uvs = [(u2, v1), (u1, v1), (u1, v2), (u2, v2)]
        vs = list(verts)
        if mirror:
            vs = vs[::-1]
            uvs = uvs[::-1]
        out.append((vs, uvs, name))
    return out


def build_quads(model, tex_w, tex_h):
    """每个贴图像素一块小四边形：[(四个世界坐标, 贴图像素坐标, 法线)]。"""
    mats = part_matrices(model['parts'])
    by_name = {p['name']: p for p in model['parts']}

    def hidden(p):
        while p:
            if p.get('visible', True) is False:
                return True
            p = by_name.get(p.get('parent'))
        return False

    quads = []
    for p in model['parts']:
        if hidden(p):
            continue
        m = mats[p['name']]
        for box in p.get('boxes', []):
            for verts, uvs, _ in cube_faces(box, tex_w, tex_h):
                wv = [mat_apply(m, (a / 16.0, b / 16.0, c / 16.0)) for a, b, c in verts]
                # 原版渲染：scale(-1,-1,1) 再往上抬 1.501 格（模型 y=24 落在地面）
                wv = [(-a, -b + 1.501, c) for a, b, c in wv]
                # 顶点 1 是 (u1,v1)，顶点 0 是 (u2,v1)，顶点 2 是 (u1,v2)
                P1, P0, P2 = wv[1], wv[0], wv[2]
                (uA, vA), (uB, _), (_, vB) = uvs[1], uvs[0], uvs[2]
                du, dv = uB - uA, vB - vA
                if du == 0 or dv == 0:
                    continue
                e_u = tuple((P0[i] - P1[i]) / du for i in range(3))
                e_v = tuple((P2[i] - P1[i]) / dv for i in range(3))
                n = _norm((e_u[1] * e_v[2] - e_u[2] * e_v[1],
                           e_u[2] * e_v[0] - e_u[0] * e_v[2],
                           e_u[0] * e_v[1] - e_u[1] * e_v[0]))
                for tu in range(int(min(uA, uB)), int(max(uA, uB))):
                    for tv in range(int(min(vA, vB)), int(max(vA, vB))):
                        if not (0 <= tu < tex_w and 0 <= tv < tex_h):
                            continue
                        corners = []
                        for cu, cv in ((tu, tv), (tu + 1, tv), (tu + 1, tv + 1), (tu, tv + 1)):
                            a, b = cu - uA, cv - vA
                            corners.append(tuple(P1[i] + e_u[i] * a + e_v[i] * b for i in range(3)))
                        quads.append((corners, (tu, tv), n))
    return quads


def shade_for(n):
    # 两面都画：背面用反向法线算光
    s0 = max(0.0, n[0] * L0[0] + n[1] * L0[1] + n[2] * L0[2])
    s1 = max(0.0, n[0] * L1[0] + n[1] * L1[1] + n[2] * L1[2])
    return min(1.0, 0.4 + 0.6 * (s0 + s1))


def render_view(quads, layers, yaw_deg, pitch_deg, scale, size, light):
    """layers: [(贴图, 模式)]，模式 base / elite / glow。"""
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    cy, sy = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)

    def cam(p):
        x, y, z = p
        # 绕 Y 转（看的人绕着怪走），再俯仰
        x, z = x * cy + z * sy, -x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z

    def cam_dir(n):
        x, y, z = n
        x, z = x * cy + z * sy, -x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z

    W, H = size
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    glow_img = Image.new('RGB', (W, H), (0, 0, 0))
    dr = ImageDraw.Draw(img)
    dg = ImageDraw.Draw(glow_img)
    ox, oy = W / 2, H * 0.86
    items = []
    for corners, (tu, tv), n in quads:
        cc = [cam(p) for p in corners]
        depth = sum(c[2] for c in cc) / 4.0
        pts = [(ox - c[0] * scale * 16, oy - c[1] * scale * 16) for c in cc]
        cn = cam_dir(n)
        # 朝着看的人那面用原法线，背面翻过来
        facing = cn[2] < 0
        ln = n if facing else (-n[0], -n[1], -n[2])
        items.append((depth, pts, tu, tv, shade_for(ln)))
    items.sort(key=lambda t: -t[0])
    base = layers[0][0]
    others = layers[1:]
    for depth, pts, tu, tv, sh in items:
        r, g, b, a = base.getpixel((tu, tv))
        if a < 26:
            continue
        k = sh * light
        col = [r * k, g * k, b * k]
        for tex, mode in others:
            er, eg, eb, ea = tex.getpixel((tu, tv))
            if mode == 'elite' and ea > 0:
                t = ea / 255.0
                col = [col[0] * (1 - t) + er * k * t, col[1] * (1 - t) + eg * k * t, col[2] * (1 - t) + eb * k * t]
        fill = tuple(int(max(0, min(255, c))) for c in col) + (255,)
        dr.polygon(pts, fill=fill, outline=fill)
        gl = (0, 0, 0)
        for tex, mode in others:
            if mode == 'glow':
                gr, gg, gb, ga = tex.getpixel((tu, tv))
                if ga > 0:
                    t = ga / 255.0
                    gl = (int(gr * t), int(gg * t), int(gb * t))
        # 发光层要被前面的东西挡住：每块都画（黑色 = 不加），后画的盖前画的
        dg.polygon(pts, fill=gl, outline=gl)
    return img, glow_img


def compose(img, glow_img, bg):
    out = Image.new('RGB', img.size, bg)
    out.paste(img, (0, 0), img)
    # 加色：只在有身体的地方加
    px, gp, mp = out.load(), glow_img.load(), img.load()
    W, H = img.size
    for y in range(H):
        for x in range(W):
            if mp[x, y][3] and any(gp[x, y]):
                r, g, b = px[x, y]
                gr, gg, gb = gp[x, y]
                px[x, y] = (min(255, r + gr), min(255, g + gg), min(255, b + gb))
    return out


def font(px):
    for f in ('C:/Windows/Fonts/msyh.ttc', 'C:/Windows/Fonts/simhei.ttf'):
        try:
            return ImageFont.truetype(f, px)
        except OSError:
            pass
    return ImageFont.load_default()


# 偏航角：0 = 从正面看，正数往怪物自己的右手边绕（90 = 看它的右侧）
VIEWS = [('正面', 0, -10), ('右前', 35, -14), ('右侧', 90, -8), ('背面', 180, -10), ('左后', -145, -14), ('左前', -35, -14)]


def main():
    ap = argparse.ArgumentParser(description='实体贴图预览')
    ap.add_argument('model')
    ap.add_argument('texture')
    ap.add_argument('--glow')
    ap.add_argument('--elite')
    ap.add_argument('--out', required=True)
    ap.add_argument('--scale', type=float, help='每个模型像素画多少屏幕像素（默认按模型大小自动定，一格约 330 像素高）；--scale 2 约等于十几格外看到的大小')
    ap.add_argument('--hero', action='store_true', help='另外出一张大的左前 3/4 图（<out>_hero.png，左亮右暗）')
    args = ap.parse_args()
    model = json.load(open(args.model, encoding='utf-8'))
    tw, th = model['texture']
    base = Image.open(args.texture).convert('RGBA')
    assert base.size == (tw, th), f'贴图尺寸 {base.size} 和模型 {tw}x{th} 不一致'
    layers = [(base, 'base')]
    if args.elite:
        layers.append((Image.open(args.elite).convert('RGBA'), 'elite'))
    if args.glow:
        layers.append((Image.open(args.glow).convert('RGBA'), 'glow'))
    quads = build_quads(model, tw, th)

    # 格子按模型外框留够地方：地面在格子 86% 高处，横向按离中心最远的顶点
    top = max(c[1] for q in quads for c in q[0])
    reach = max(math.hypot(c[0], c[2]) for q in quads for c in q[0])
    cell_h = max(2.9, (top + 0.25) / 0.86)
    cell_w = max(2.0, 2 * (reach + 0.3))
    if args.scale is None:
        args.scale = max(3.0, min(14.0, 330.0 / (cell_h * 16)))
    cell = (int(args.scale * 16 * cell_w), int(args.scale * 16 * cell_h))
    rows = [('亮处', 1.0, (118, 128, 104)), ('暗处', 0.30, (22, 20, 28))]
    W = cell[0] * len(VIEWS)
    H = (cell[1] + 22) * len(rows)
    sheet = Image.new('RGB', (W, H), (60, 60, 66))
    dr = ImageDraw.Draw(sheet)
    f = font(16)
    for ri, (rname, light, bg) in enumerate(rows):
        for vi, (vname, yaw, pitch) in enumerate(VIEWS):
            img, glow = render_view(quads, layers, yaw, pitch, args.scale, cell, light)
            sheet.paste(compose(img, glow, bg), (vi * cell[0], ri * (cell[1] + 22) + 22))
            dr.text((vi * cell[0] + 6, ri * (cell[1] + 22) + 2), f'{rname} · {vname}', fill=(235, 235, 235), font=f)
    sheet.save(args.out)
    if args.hero:
        hs = args.scale * 2.2
        hcell = (int(hs * 16 * cell_w), int(hs * 16 * cell_h))
        img, glow = render_view(quads, layers, -32, -12, hs, hcell, 1.0)
        img2, glow2 = render_view(quads, layers, -32, -12, hs, hcell, 0.30)
        hero = Image.new('RGB', (hcell[0] * 2, hcell[1]))
        hero.paste(compose(img, glow, (118, 128, 104)), (0, 0))
        hero.paste(compose(img2, glow2, (22, 20, 28)), (hcell[0], 0))
        hero.save(args.out[:-4] + '_hero.png')
    print('ok', args.out)


if __name__ == '__main__':
    main()
