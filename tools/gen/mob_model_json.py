# -*- coding: utf-8 -*-
"""从手写 LayerDefinition 的模型源码生成 mob_preview.py 用的模型表（tools/gen/mob_preview/<id>.json）。

  python tools/gen/mob_model_json.py --java src/main/java/.../client/model/mob/XxxModel.java --id xxx [--out 目录]

读法（正则，够读本模组手写的模型）：
  - LayerDefinition.create(mesh, 宽, 高) 取贴图尺寸；mesh.getRoot() 赋给的变量当根；
  - [PartDefinition 变量 =] 父变量.addOrReplaceChild(名字, CubeListBuilder..., PartPose...) 取部位、父子关系和 PartPose
    （ZERO / offset / rotation / offsetAndRotation）；texOffs / mirror / addBox（带 CubeDeformation 的读外扩）照写；
  - 数字可以是 static final 的 float / int 常量和四则运算。
读不到的写法（循环里加部位、拼出来的名字）会跳过并打印出来，手动补进生成的 JSON。
生成的是 createLayer 的姿势，不含 pose() 里每帧加的角度；要和游戏里的静止姿势一致就在 JSON 里改 rot。
"""
import argparse
import json
import math
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'gen'))
from uv_template import _matching, _split_args, _part_name  # noqa: E402

DEFAULT_OUT = os.path.join(ROOT, 'tools', 'gen', 'mob_preview')


def _strip(src):
    src = re.sub(r'//[^\n]*', '', src)
    return re.sub(r'/\*.*?\*/', '', src, flags=re.S)


def _consts(src):
    vals = {'PI': math.pi}
    for name, expr in re.findall(r'static\s+final\s+(?:float|int|double)\s+(\w+)\s*=\s*([^;]+);', src):
        v = _eval(expr, vals)
        if v is not None:
            vals[name] = v
    return vals


def _eval(expr, consts):
    e = expr.strip()
    e = e.replace('Mth.PI', 'PI').replace('Math.PI', 'PI').replace('(float)', '')
    e = re.sub(r'(?<=[0-9.])[fFdD]\b', '', e)
    for name in sorted(consts, key=len, reverse=True):
        e = re.sub(r'\b%s\b' % re.escape(name), repr(consts[name]), e)
    if not re.fullmatch(r'[0-9eE.+\-*/() ]+', e):
        return None
    try:
        return float(eval(e, {'__builtins__': {}}))
    except Exception:
        return None


def _pose(text, consts):
    t = text.strip()
    if t == 'PartPose.ZERO':
        return [0, 0, 0], [0, 0, 0]
    m = re.match(r'PartPose\.(offset|rotation|offsetAndRotation)\(', t)
    if not m:
        return None
    inner = t[m.end():_matching(t, m.end() - 1)]
    nums = [_eval(a, consts) for a in _split_args(inner)]
    if any(n is None for n in nums):
        return None
    if m.group(1) == 'offset':
        return nums[:3], [0, 0, 0]
    if m.group(1) == 'rotation':
        return [0, 0, 0], nums[:3]
    return nums[:3], nums[3:6]


def read_java(path):
    raw = open(path, encoding='utf-8').read()
    src = _strip(raw)
    names = dict(re.findall(r'static\s+final\s+String\s+(\w+)\s*=\s*"([^"]*)"', src))
    consts = _consts(src)
    m = re.search(r'LayerDefinition\.create\(\s*\w+\s*,\s*(\d+)\s*,\s*(\d+)\s*\)', src)
    size = [int(m.group(1)), int(m.group(2))] if m else None
    root_vars = set(re.findall(r'(\w+)\s*=\s*\w+\.getRoot\(\)', src))
    var_part = {}
    parts, skipped = [], []
    for m in re.finditer(r'(?:PartDefinition\s+(\w+)\s*=\s*)?(\w+)\.addOrReplaceChild\(', src):
        open_at = m.end() - 1
        close_at = _matching(src, open_at)
        args = _split_args(src[open_at + 1:close_at])
        line = src[:m.start()].count('\n') + 1
        if len(args) < 3:
            skipped.append((line, 'addOrReplaceChild 参数不够'))
            continue
        name = _part_name(args[0], names)
        if name.startswith('"') or not re.fullmatch(r'[\w.#-]+', name):
            skipped.append((line, '名字读不懂：%s' % args[0]))
            continue
        parent_var = m.group(2)
        parent = None if parent_var in root_vars else var_part.get(parent_var)
        if parent_var not in root_vars and parent is None:
            skipped.append((line, '%s 的父变量 %s 认不出来' % (name, parent_var)))
            continue
        pose = _pose(args[2], consts)
        if pose is None:
            skipped.append((line, '%s 的 PartPose 读不懂：%s' % (name, args[2])))
            continue
        boxes = []
        u = v = 0
        mirror = False
        for call in re.finditer(r'\.(texOffs|mirror|addBox)\(', args[1]):
            c_open = call.end() - 1
            inner = args[1][c_open + 1:_matching(args[1], c_open)]
            kind = call.group(1)
            if kind == 'texOffs':
                a = _split_args(inner)
                u, v = _eval(a[0], consts), _eval(a[1], consts)
            elif kind == 'mirror':
                mirror = inner.strip() != 'false'
            else:
                a = _split_args(inner)
                if a and a[0].startswith('"'):
                    a = a[1:]
                nums = [_eval(x, consts) for x in a[:6]]
                if len(nums) < 6 or any(n is None for n in nums):
                    skipped.append((line, '%s 的 addBox 读不懂：%s' % (name, inner)))
                    continue
                inflate = 0.0
                for extra in a[6:]:
                    d = re.match(r'new\s+CubeDeformation\((.*)\)$', extra.strip())
                    if d:
                        inflate = _eval(_split_args(d.group(1))[0], consts) or 0.0
                    elif extra.strip() == 'true':
                        mirror = True
                box = {'uv': [u, v], 'from': nums[:3], 'size': nums[3:6]}
                if inflate:
                    box['inflate'] = inflate
                if mirror:
                    box['mirror'] = True
                boxes.append(box)
        if m.group(1):
            var_part[m.group(1)] = name
        parts.append({'name': name, 'parent': parent, 'offset': pose[0], 'rot': pose[1], 'boxes': boxes})
    return size, parts, skipped


def main():
    ap = argparse.ArgumentParser(description='模型源码 → mob_preview 模型表')
    ap.add_argument('--java', required=True)
    ap.add_argument('--id', required=True)
    ap.add_argument('--out', default=DEFAULT_OUT)
    args = ap.parse_args()
    size, parts, skipped = read_java(args.java)
    if not size:
        sys.exit('没找到 LayerDefinition.create(mesh, 宽, 高)')
    os.makedirs(args.out, exist_ok=True)
    out = os.path.join(args.out, args.id + '.json')
    data = {'_src': os.path.basename(args.java) + ' 的 createLayer（不含 pose() 每帧加的角度）', 'texture': size, 'parts': parts}
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    print('%s：%d 个部位、%d 个方块盒' % (out, len(parts), sum(len(p['boxes']) for p in parts)))
    for line, why in skipped:
        print('  第 %d 行跳过：%s' % (line, why))


if __name__ == '__main__':
    main()
