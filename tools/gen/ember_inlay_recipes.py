#!/usr/bin/env python3
"""
生成余烬嵌片（ember_inlay，第十批 10b，tools/plan/batch10.md §5.7.2）的工作台配方：

    python tools/gen/ember_inlay_recipes.py          # 写文件
    python tools/gen/ember_inlay_recipes.py --check  # 只核对现有文件和表一致（不一致退出码 1）

输出到 src/main/resources/data/essentialcraft5/recipes/ember_inlay/<余烬 a>_<余烬 b>.json，脚本和结果都提交，改表后重跑。
写之前先删掉目录里旧的 *.json，表里删掉的配方不会留下旧文件。

规则：
  - 每条是无序配方：两个不同的余烬 + 淡青宝石（pale_gem）+ 注魔合金（combined_magical_alloys）→ 1 片余烬嵌片；
  - 结果带 NBT {"essentialcraft5:embers": [两个余烬的注册名]}，按注册名的字典序排（和 Java 的 EmberInlayItem.Pair 一致）；
  - 只做带命中效果的对：效果为空的（只加伤害、或什么都没有）不做；「混沌 + 阴燃」只有伤害 + 反噬，也不做（EmberInlayItem.craftable）。
组合表直接从 Java 的 item/ember/EmberEffect.java 里 COMBOS 那一段读（combo(A, B, 伤害, 效果…) 一行一对），不另抄一份：
第十批补了「神圣 + 魔力」驱散、「神圣 + 燃烧」腐蚀以后共 14 条。
group = essentialcraft5:ember_inlay：配方书里并成一个按钮。
"""
import argparse
import glob
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'flameforge', 'essentialcraft5', 'item', 'ember', 'EmberEffect.java')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'essentialcraft5', 'recipes', 'ember_inlay')

MOD = 'essentialcraft5:'
EMBERS_TAG = MOD + 'embers'
EXTRA = [MOD + 'pale_gem', MOD + 'combined_magical_alloys']
RESULT = MOD + 'ember_inlay'

COMBO_RE = re.compile(r'^\s*combo\(\s*([A-Z_]+)\s*,\s*([A-Z_]+)\s*,\s*(\d+)\s*((?:,\s*[A-Z_]+\s*)*)\)\s*[,;)]')


def ember_id(kind):
    """EmberKind 常量 → 注册名（EmberKind 的 id：<小写种类>_ember）"""
    return MOD + kind.lower() + '_ember'


def read_combos():
    with open(JAVA, encoding='utf-8') as f:
        text = f.read()
    start = text.index('public static final List<Combo> COMBOS = List.of(')
    end = text.index(');', start)
    combos = []
    for line in text[start:end + 2].splitlines():
        m = COMBO_RE.match(line)
        if not m:
            continue
        effects = [e.strip() for e in m.group(4).split(',') if e.strip()]
        combos.append((m.group(1), m.group(2), int(m.group(3)), effects))
    if len(combos) != 28:
        sys.exit('EmberEffect.COMBOS 应有 28 对，读到 %d 对：解析规则和 Java 对不上了' % len(combos))
    return combos


def craftable(damage, effects):
    return bool(effects) and not (damage > 0 and effects == ['DAMAGE_SELF'])


def recipes():
    out = {}
    for a, b, damage, effects in read_combos():
        if not craftable(damage, effects):
            continue
        pair = sorted([ember_id(a), ember_id(b)])
        name = pair[0][len(MOD):].replace('_ember', '') + '_' + pair[1][len(MOD):].replace('_ember', '')
        out[name + '.json'] = {
            'type': 'minecraft:crafting_shapeless',
            'category': 'misc',
            'group': MOD + 'ember_inlay',
            'ingredients': [{'item': pair[0]}, {'item': pair[1]}] + [{'item': item} for item in EXTRA],
            'result': {'item': RESULT, 'count': 1, 'nbt': {EMBERS_TAG: pair}},
        }
    return out


def dump(data):
    return json.dumps(data, ensure_ascii=False, indent=2) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    parser.add_argument('--check', action='store_true', help='只核对，不写')
    args = parser.parse_args()
    wanted = recipes()
    if args.check:
        existing = {os.path.basename(p) for p in glob.glob(os.path.join(OUT, '*.json'))}
        bad = sorted(existing.symmetric_difference(wanted))
        for name, data in wanted.items():
            path = os.path.join(OUT, name)
            if os.path.exists(path):
                with open(path, encoding='utf-8') as f:
                    if f.read() != dump(data):
                        bad.append(name)
        if bad:
            print('和表不一致：' + ', '.join(sorted(set(bad))))
            sys.exit(1)
        print('一致，%d 条' % len(wanted))
        return
    os.makedirs(OUT, exist_ok=True)
    for old in glob.glob(os.path.join(OUT, '*.json')):
        os.remove(old)
    for name, data in sorted(wanted.items()):
        with open(os.path.join(OUT, name), 'w', encoding='utf-8', newline='\n') as f:
            f.write(dump(data))
    print('写了 %d 条配方到 %s' % (len(wanted), os.path.relpath(OUT, ROOT)))


if __name__ == '__main__':
    main()
