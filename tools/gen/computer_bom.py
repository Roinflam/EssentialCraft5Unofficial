"""宅男套装与反重力板的材料总账（第八批 M2，batch8.md §2.2 / §6.4）。

从配方 JSON 逐级展开：data/essentialcraft5/recipes/computer/*.json（21 条，力场穹顶除外）和恶魔核心（demonic/demonic_core.json），
其余（恶魔板、风之水晶、MRU 反应核、永恒物质……）当原料不再展开。

展开规则：几件东西要的中间件先合在一起算总数，每种中间件按「整次合成」向上取整（一次出 4 个、要 5 个就做 2 次），
做多出来的零头留给别的件用。所以「合起来做」比「逐件单算再相加」省一点。

用法：
    python tools/gen/computer_bom.py            # 打印全套 + 反重力板、只做四件、逐件单算的总账
    python tools/gen/computer_bom.py --json     # 同样的数字输出成 JSON

手册 computer/costs 条目的表、gametest/ComputerCostTests 的期望值都是这个脚本的输出；改了配方产量先重跑，再同步那两处。
"""

import argparse
import json
import math
import os
import sys
from collections import defaultdict

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), '..', '..'))
RECIPES = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'essentialcraft5', 'recipes')
NS = 'essentialcraft5:'

ARMOR = ['computer_helmet', 'computer_chestplate', 'computer_leggings', 'computer_boots']
NOT_SUIT = {'force_dome.json'}
BOARD = 'computer_board'
# 账里单独列出的原料（顺序即输出顺序），其余原料归到「其它」
HEADLINE = ['demonic_plate', 'wind_gem', 'void_mru_reactor', 'demonic_core', 'matter_of_eternity', 'elemental_gem',
            'mru_resonating_crystal', 'mithriline_crystal', 'fading_crystal', 'pale_plate', 'combined_magical_alloys',
            'sun_imbued_glass', 'magic_purified_glass_alloy',
            'addition_core', 'subtraction_core', 'multiplication_core', 'division_core']


def short(item_id):
    return item_id[len(NS):] if item_id.startswith(NS) else item_id


def load_recipe(path):
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    counts = defaultdict(int)
    for row in data['pattern']:
        for ch in row:
            if ch == ' ':
                continue
            ingredient = data['key'][ch]
            name = '#' + ingredient['tag'] if 'tag' in ingredient else short(ingredient['item'])
            counts[name] += 1
    result = data['result']
    return short(result['item']), int(result.get('count', 1)), dict(counts)


def load_all():
    recipes = {}
    folder = os.path.join(RECIPES, 'computer')
    for name in sorted(os.listdir(folder)):
        # 力场穹顶（第十批 10c）的配方也放在 computer/ 下，不是套装的零件
        if name.endswith('.json') and name not in NOT_SUIT:
            out, count, ingredients = load_recipe(os.path.join(folder, name))
            recipes[out] = (count, ingredients)
    out, count, ingredients = load_recipe(os.path.join(RECIPES, 'demonic', 'demonic_core.json'))
    recipes[out] = (count, ingredients)
    return recipes


def topo_order(recipes):
    """用的在前、被用的在后：处理到某个中间件时，所有要它的东西都已经展开过了"""
    order, seen = [], set()

    def visit(item):
        if item in seen or item not in recipes:
            return
        seen.add(item)
        for ingredient in recipes[item][1]:
            visit(ingredient)
        order.append(item)

    for item in sorted(recipes):
        visit(item)
    order.reverse()
    return order


def expand(recipes, targets):
    """targets: {物品: 个数} → (原料总数, 各中间件合成次数)"""
    demand = defaultdict(int)
    for item, n in targets.items():
        demand[item] += n
    crafts = {}
    for item in topo_order(recipes):
        need = demand.get(item, 0)
        if need <= 0:
            continue
        yield_count, ingredients = recipes[item]
        times = math.ceil(need / yield_count)
        crafts[item] = times
        for ingredient, n in ingredients.items():
            demand[ingredient] += times * n
    base = {item: n for item, n in demand.items() if item not in recipes and n > 0}
    return base, crafts


def report(recipes):
    sets = {
        'full_set_and_board': {item: 1 for item in ARMOR + [BOARD]},
        'four_pieces': {item: 1 for item in ARMOR},
    }
    for item in ARMOR + [BOARD]:
        sets[item] = {item: 1}
    out = {}
    for label, targets in sets.items():
        base, crafts = expand(recipes, targets)
        out[label] = {'base': dict(sorted(base.items())), 'crafts': dict(sorted(crafts.items()))}
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--json', action='store_true', help='输出 JSON')
    args = parser.parse_args()
    recipes = load_all()
    data = report(recipes)
    if args.json:
        json.dump(data, sys.stdout, ensure_ascii=False, indent=2)
        print()
        return
    labels = list(data)
    print('原料'.ljust(28) + ''.join(label[:18].rjust(20) for label in labels))
    names = HEADLINE + sorted({k for v in data.values() for k in v['base']} - set(HEADLINE))
    for name in names:
        row = [data[label]['base'].get(name, 0) for label in labels]
        if any(row):
            print(name.ljust(28) + ''.join(str(n).rjust(20) for n in row))
    print()
    print('全套 + 反重力板的合成次数：')
    for item, times in data['full_set_and_board']['crafts'].items():
        print('  ' + item.ljust(28) + str(times))


if __name__ == '__main__':
    main()
