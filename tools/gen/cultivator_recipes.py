#!/usr/bin/env python3
"""
生成魔力栽培器的配方（配方类型 essentialcraft5:cultivator，第十二批 batch12.md §4.2.2）：

    python tools/gen/cultivator_recipes.py

输出到 src/main/resources/data/essentialcraft5/recipes/cultivator/<样本的注册名>.json，脚本和结果都提交，改表后重跑。
先删掉目录里旧的栽培配方（只删 type 是 essentialcraft5:cultivator 的，机器本身的工作台配方 magical_cultivator.json 不动），
表里删掉的条目不会留下旧文件。

表照 batch12.md §4.2.2 的显式配方表，共 31 个样本（作物 18、树 / 菌 / 紫颂 13）。
每株产物：先按 chance（默认 1）判出不出，再在 [min, max] 里取数量。都不出树苗、树叶、疣块、花（batch12.md §1.2：
树苗进自然熔炉、树叶进终极自然熔炉会让「种树发电」的倍数翻几番）。
kind 是 crop / tree，时长、每株 MRU、株数上限不写时按 kind 取（作物 2400 刻 / 800 MRU / 16 株，树 6000 / 6000 / 4），
要不要水、能不能用骨粉默认都是要 / 能。这里把每个字段都写出来，数据包作者照着改一目了然。

生成前检查：样本不重复；产物物品不是样本以外的树苗、树叶、花（防手误）；min ≤ max、max ≥ 1、0 < chance ≤ 1；
满株数时每种产物取上限的最坏情况，按 64 一堆拆开不超过 9 格（机器开工前要按最坏情况放得下，超了永远开不了工）。
"""
import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'essentialcraft5', 'recipes', 'cultivator')

TYPE = 'essentialcraft5:cultivator'
MC = 'minecraft:'
OUTPUT_SLOTS = 9
KIND_DEFAULTS = {'crop': (2400, 800, 16), 'tree': (6000, 6000, 4)}

# 产物写法：(物品, min, max) 或 (物品, min, max, chance)
def out(item, lo=1, hi=None, chance=1.0):
    return (MC + item, lo, lo if hi is None else hi, chance)


# 样本, kind, 产物, 时长（None = 按 kind）, 要水, 能用骨粉
TABLE = [
    # ---- 作物（18） ----
    ('wheat_seeds', 'crop', [out('wheat'), out('wheat_seeds', chance=0.25)], None, True, True),
    ('beetroot_seeds', 'crop', [out('beetroot'), out('beetroot_seeds', chance=0.25)], None, True, True),
    ('carrot', 'crop', [out('carrot', 1, 2)], None, True, True),
    ('potato', 'crop', [out('potato', 1, 2), out('poisonous_potato', chance=0.02)], None, True, True),
    ('torchflower_seeds', 'crop', [out('torchflower'), out('torchflower_seeds', chance=0.25)], None, True, True),
    ('pitcher_pod', 'crop', [out('pitcher_plant'), out('pitcher_pod', chance=0.25)], None, True, True),
    ('melon_seeds', 'crop', [out('melon_slice', 3, 7)], 3000, True, True),
    ('pumpkin_seeds', 'crop', [out('pumpkin')], 3000, True, True),
    ('sugar_cane', 'crop', [out('sugar_cane', 2)], None, True, False),
    ('cactus', 'crop', [out('cactus', 2)], None, False, False),
    ('bamboo', 'crop', [out('bamboo', 4)], None, True, True),
    ('kelp', 'crop', [out('kelp', 3)], None, True, True),
    ('sweet_berries', 'crop', [out('sweet_berries', 2, 3)], None, True, True),
    ('glow_berries', 'crop', [out('glow_berries', 1, 2)], None, True, True),
    ('cocoa_beans', 'crop', [out('cocoa_beans', 2, 3)], None, True, True),
    ('nether_wart', 'crop', [out('nether_wart', 2, 4)], None, False, False),
    ('red_mushroom', 'crop', [out('red_mushroom', 1, 2)], None, False, True),
    ('brown_mushroom', 'crop', [out('brown_mushroom', 1, 2)], None, False, True),
    # ---- 树、菌、紫颂（13） ----
    ('oak_sapling', 'tree', [out('oak_log', 4, 6), out('stick', 0, 2), out('apple', chance=0.05)], None, True, True),
    ('spruce_sapling', 'tree', [out('spruce_log', 4, 6), out('stick', 0, 2)], None, True, True),
    ('birch_sapling', 'tree', [out('birch_log', 4, 6), out('stick', 0, 2)], None, True, True),
    ('acacia_sapling', 'tree', [out('acacia_log', 4, 6), out('stick', 0, 2)], None, True, True),
    ('cherry_sapling', 'tree', [out('cherry_log', 4, 6), out('stick', 0, 2)], None, True, True),
    ('jungle_sapling', 'tree', [out('jungle_log', 4, 6), out('stick', 0, 2), out('cocoa_beans', chance=0.10)], None, True, True),
    ('dark_oak_sapling', 'tree', [out('dark_oak_log', 4, 6), out('stick', 0, 2), out('apple', chance=0.05)], None, True, True),
    ('mangrove_propagule', 'tree', [out('mangrove_log', 4, 6), out('mangrove_roots', 0, 1)], None, True, True),
    ('azalea', 'tree', [out('oak_log', 4, 6), out('stick', 0, 2)], None, True, True),
    ('flowering_azalea', 'tree', [out('oak_log', 4, 6), out('stick', 0, 2)], None, True, True),
    ('crimson_fungus', 'tree', [out('crimson_stem', 4, 6), out('shroomlight', chance=0.10)], None, False, True),
    ('warped_fungus', 'tree', [out('warped_stem', 4, 6), out('shroomlight', chance=0.10)], None, False, True),
    ('chorus_flower', 'tree', [out('chorus_fruit', 4, 8)], None, False, False),
]

# 不许出现在产物里的东西（样本自己除外：作物的「多出来的种子」是配方写明的 25%）
FORBIDDEN_SUFFIXES = ('_sapling', '_leaves', '_propagule', '_fungus', '_wart_block')
FORBIDDEN = {MC + 'azalea', MC + 'flowering_azalea', MC + 'chorus_flower', MC + 'chorus_plant',
             MC + 'nether_wart_block', MC + 'warped_wart_block'}

# 堆叠上限不是 64 的产物（原版里这张表用到的都是 64，留着防以后加东西）
STACK = {}


def check(rows):
    errors = []
    seen = set()
    for sample, kind, outputs, ticks, water, bonemeal in rows:
        if sample in seen:
            errors.append('样本重复：' + sample)
        seen.add(sample)
        if kind not in KIND_DEFAULTS:
            errors.append('%s 的 kind 不对：%s' % (sample, kind))
        plants = KIND_DEFAULTS[kind][2]
        slots = 0
        for item, lo, hi, chance in outputs:
            if item != MC + sample and (item in FORBIDDEN or item.endswith(FORBIDDEN_SUFFIXES)):
                errors.append('%s 出了不该出的东西：%s' % (sample, item))
            if lo < 0 or hi < lo or hi < 1:
                errors.append('%s 的 %s 数量范围不对：%d～%d' % (sample, item, lo, hi))
            if not 0 < chance <= 1:
                errors.append('%s 的 %s 几率不对：%s' % (sample, item, chance))
            stack = STACK.get(item, 64)
            slots += -(-hi * plants // stack)
        if slots > OUTPUT_SLOTS:
            errors.append('%s 满 %d 株的最坏情况要 %d 格，输出格只有 %d 格' % (sample, plants, slots, OUTPUT_SLOTS))
    if len(rows) != 31:
        errors.append('表里应是 31 个样本，实际 %d 个' % len(rows))
    return errors


def recipe_json(sample, kind, outputs, ticks, water, bonemeal):
    default_ticks, mru, plants = KIND_DEFAULTS[kind]
    data = {
        'type': TYPE,
        'kind': kind,
        'input': {'item': MC + sample},
        'ticks': ticks if ticks is not None else default_ticks,
        'mru_per_plant': mru,
        'max_plants': plants,
        'needs_water': water,
        'bonemeal': bonemeal,
        'outputs': [],
    }
    for item, lo, hi, chance in outputs:
        entry = {'item': item, 'min': lo, 'max': hi}
        if chance < 1:
            entry['chance'] = chance
        data['outputs'].append(entry)
    return data


def main():
    errors = check(TABLE)
    if errors:
        print('\n'.join(errors))
        return 1
    os.makedirs(OUT, exist_ok=True)
    removed = 0
    for path in glob.glob(os.path.join(OUT, '*.json')):
        with open(path, encoding='utf-8') as f:
            try:
                old = json.load(f)
            except ValueError:
                continue
        if old.get('type') == TYPE:
            os.remove(path)
            removed += 1
    for row in TABLE:
        path = os.path.join(OUT, row[0] + '.json')
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(recipe_json(*row), f, ensure_ascii=False, indent=2)
            f.write('\n')
    print('删掉旧的 %d 个，写了 %d 个栽培配方到 %s' % (removed, len(TABLE), os.path.relpath(OUT, ROOT)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
