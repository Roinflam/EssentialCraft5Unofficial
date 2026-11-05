#!/usr/bin/env python3
"""
生成精华与焦点模块（ECEssence）的工作台配方：

    python tools/gen/essence_recipes.py

输出到 src/main/resources/data/essentialcraft5/recipes/essence/*.json，脚本和结果都提交，改表后重跑。
先删掉目录里旧的 *.json 再写，表里删掉的配方不会留下旧文件。

来源（逐条对过原作 assets/essentialcraft/recipes/ 下同名 JSON）：
  - crystal_controller.json、crystal_extractor.json、magical_lantern.json
  - elemental_fuel_<元素>_<稀有度 0~3>.json（16 条）：同元素、同稀有度的精华 ×4 围成十字，中间按元素放
    煤（原作矿辞 gemCoal → 标签 minecraft:coals，木炭也行）/ 雪球 / 小麦种子 / 空着（风）；出 1 / 4 / 8 / 16 个燃料
    （batch2.md §3.4 写的「精华 ×4 + 煤」是简写，原作只有火元素放煤）。
  - <元素>_focus.json（4 条）：四角金锭（essentialcraft5:gold_ingots，金锭或淡青锭）、四边铁锭、中间燃料。
原作矿辞按 tools/oredict_map.json 换成标签或物品。
"""
import glob
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'essentialcraft5', 'recipes', 'essence')

MOD = 'essentialcraft5:'
ELEMENTS = ['fire', 'water', 'earth', 'air']
RARITIES = ['common', 'uncommon', 'rare', 'exceptional']
FUEL_COUNTS = [1, 4, 8, 16]
# 燃料配方中间那格（None = 空着，原作风元素的配方是 " E ", "E E", " E "）
FUEL_CENTER = {
    'fire': {'tag': 'minecraft:coals'},
    'water': {'item': 'minecraft:snowball'},
    'earth': {'item': 'minecraft:wheat_seeds'},
    'air': None,
}


def item(name):
    return {'item': name if ':' in name else MOD + name}


def tag(name):
    return {'tag': name}


def shaped(result, pattern, key, count=1):
    name = result.split(':')[-1]
    return {
        'type': 'minecraft:crafting_shaped',
        'category': 'misc',
        'group': result if ':' in result else MOD + name,
        'pattern': pattern,
        'key': key,
        'result': {'item': result if ':' in result else MOD + result, 'count': count},
    }


def recipes():
    out = {}
    # 原作 crystal_controller.json：SMP / DCD / FXF
    out['crystal_controller'] = shaped('crystal_controller', ['SMP', 'DCD', 'FXF'], {
        'S': item('magical_screen'),
        'M': item('mru_catcher'),
        'P': tag(MOD + 'plates/magic'),
        'D': item('crystal_dust'),
        'C': tag(MOD + 'cores/magic'),
        'F': item('magic_fortified_plating'),
        'X': item('mru_conversion_matrix'),
    })
    # 原作 crystal_extractor.json：SDA / XCX / FDF
    out['crystal_extractor'] = shaped('crystal_extractor', ['SDA', 'XCX', 'FDF'], {
        'S': item('magical_screen'),
        'D': item('crystal_dust'),
        'A': item('combined_magical_alloys'),
        'X': item('mru_conversion_matrix'),
        'C': tag(MOD + 'cores/magic'),
        'F': item('obsidian_plate'),
    })
    for element in ELEMENTS:
        center = FUEL_CENTER[element]
        for rarity, rarity_name in enumerate(RARITIES):
            key = {'E': item(f'{rarity_name}_{element}_essence')}
            if center is None:
                pattern = [' E ', 'E E', ' E ']
            else:
                pattern = [' E ', 'ECE', ' E ']
                key['C'] = center
            recipe = shaped(f'{element}_elemental_fuel', pattern, key, FUEL_COUNTS[rarity])
            out[f'elemental_fuel_{element}_{rarity}'] = recipe
        out[f'{element}_focus'] = shaped(f'{element}_focus', ['GIG', 'IEI', 'GIG'], {
            'G': tag(MOD + 'gold_ingots'),
            'I': tag('forge:ingots/iron'),
            'E': item(f'{element}_elemental_fuel'),
        })
    # 原作 magical_lantern.json：AFM / SPF / PSA
    out['magical_lantern'] = shaped('magical_lantern', ['AFM', 'SPF', 'PSA'], {
        'A': item('air_focus'),
        'F': item('fire_focus'),
        'M': item('red_soul_matter'),
        'S': item('magical_slag'),
        'P': tag(MOD + 'plates/magic'),
    })
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    for old in glob.glob(os.path.join(OUT, '*.json')):
        os.remove(old)
    table = recipes()
    for name, recipe in table.items():
        with open(os.path.join(OUT, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(recipe, f, ensure_ascii=False, indent=2)
            f.write('\n')
    print(f'写了 {len(table)} 条配方到 {os.path.relpath(OUT, ROOT)}')


if __name__ == '__main__':
    main()
