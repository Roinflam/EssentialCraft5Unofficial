#!/usr/bin/env python3
"""
生成魔力凿与花纹方块模块（ECDecorations）的工作台配方：

    python tools/gen/fancy_recipes.py

输出到 src/main/resources/data/essentialcraft5/recipes/decorations/*.json，脚本和结果都提交，改表后重跑。
先删掉目录里旧的 *.json 再写，表里删掉的配方不会留下旧文件。

来源（原作 RecipesCore.registerFancyBlockCrafts L270~373，每种底材一样；魔力凿是原作 assets/essentialcraft/recipes/magical_chisel.json）：
  - 大方砖（big_tile）：无序，底材 + 魔力凿（任意耐久，凿子作为容器物品留在格子里）→ 4
  - 其余 15 种花纹由大方砖一路合出来（表 PATTERNS），产量照原作
  - 魔力凿：无序，元素晶石 ×4（原作矿辞 gemElemental → essentialcraft5:elemental_gem）+ 木棍（stickWood → #forge:rods/wooden）
原作矿辞按 tools/oredict_map.json 换成标签：dustRedstone → forge:dusts/redstone、ingotIron → forge:ingots/iron、leather → forge:leather；
genericItem:3 = magical_essence（魔力精华）、genericItem:12 = mru_shard（MRU 碎片）。

底材表 BASES 要和 Java 的 block/FancyMaterial 一致（拟态方块那一行产出花纹拟态 fancy_mimic_<花纹>）。
加底材时在 BASES 加一行、FancyMaterial 加一行，重跑本脚本（第六批 6a 加了混凝土、虚空石，第七批加了恶魔护板）。
group = essentialcraft5:fancy_<花纹>：配方书里同一种花纹的不同底材并成一个按钮。
"""
import glob
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'essentialcraft5', 'recipes', 'decorations')

MOD = 'essentialcraft5:'

# 注册名里的底材段 → 底材物品（和 FancyMaterial 同顺序；最后一行是花纹拟态）
BASES = [
    ('fancy_mru', 'magical_slag'),
    ('fancy_concrete', 'concrete'),              # 第六批 6a（ECVoid）
    ('fancy_fortified_stone', 'fortified_stone'),
    ('fancy_cold_stone', 'cold_stone'),
    ('fancy_magic_plating', 'magic_plating'),
    ('fancy_pale_plating', 'pale_plating'),
    ('fancy_void_stone', 'void_stone'),          # 第六批 6a（ECVoid）
    ('fancy_mithriline_plating', 'mithriline_plating'),
    ('fancy_demonic_plating', 'demonic_plating'),  # 第七批（ECDemonic）
    ('fancy_mimic', 'mimic'),
]

# 花纹（按原作 meta 顺序）→ (图案, 键, 产量)。键里的字符串是同底材的另一种花纹，dict 是外部原料。
# 大方砖是无序配方，单独处理（pattern 为 None）。
PATTERNS = {
    'ancient_tile': (['# #', '   ', '# #'], {'#': 'big_tile'}, 4),
    'big_tile': (None, None, 4),
    'brick': (['##', '##'], {'#': 'big_tile'}, 4),
    'fancy_tile': (['###', '###', '###'], {'#': 'big_tile'}, 9),
    'pressured_tile': (['###', '# #', '###'], {'#': 'brick'}, 8),
    'small_tiles': (['##', '##'], {'#': 'tiles'}, 4),
    'temple': (['###', '@$@', '###'], {'#': 'big_tile', '@': 'fancy_tile', '$': 'ancient_tile'}, 9),
    'tiles': ([' # ', '# #', ' # '], {'#': 'brick'}, 4),
    'futuristic_tile': ([' # ', '#@#', ' # '], {'#': 'ancient_tile', '@': 'fancy_tile'}, 4),
    'machine': (['###', '#@#', '###'], {'#': 'futuristic_tile', '@': {'tag': 'forge:dusts/redstone'}}, 8),
    'runic': (['###', '#@#', '###'], {'#': 'big_tile', '@': {'item': MOD + 'magical_essence'}}, 8),
    'nether_star': (['###', '#@#', '###'], {'#': 'big_tile', '@': {'item': MOD + 'mru_shard'}}, 8),
    'plate': (['###', '#@#', '###'], {'#': 'big_tile', '@': {'tag': 'forge:ingots/iron'}}, 8),
    'packed_plate': (['###', '#@#', '###'], {'#': 'big_tile', '@': {'tag': 'forge:leather'}}, 8),
    'double_plate': (['##', '##'], {'#': 'packed_plate'}, 4),
    'gem': (['##', '##'], {'#': 'plate'}, 4),
}


def item(name):
    return {'item': name if ':' in name else MOD + name}


def write(name, recipe):
    path = os.path.join(OUT, name + '.json')
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(recipe, f, indent=2, ensure_ascii=False)
        f.write('\n')


def fancy_recipe(prefix, base, pattern):
    shape, key, count = PATTERNS[pattern]
    result = {'item': MOD + prefix + '_' + pattern, 'count': count}
    group = MOD + 'fancy_' + pattern
    if shape is None:
        return {
            'type': 'minecraft:crafting_shapeless',
            'category': 'building',
            'group': group,
            'ingredients': [item(base), item('magical_chisel')],
            'result': result,
        }
    return {
        'type': 'minecraft:crafting_shaped',
        'category': 'building',
        'group': group,
        'pattern': shape,
        'key': {ch: (ing if isinstance(ing, dict) else item(prefix + '_' + ing)) for ch, ing in key.items()},
        'result': result,
    }


def main():
    os.makedirs(OUT, exist_ok=True)
    for old in glob.glob(os.path.join(OUT, '*.json')):
        os.remove(old)
    count = 0
    for prefix, base in BASES:
        for pattern in PATTERNS:
            write(prefix + '_' + pattern, fancy_recipe(prefix, base, pattern))
            count += 1
    write('magical_chisel', {
        'type': 'minecraft:crafting_shapeless',
        'category': 'equipment',
        'group': MOD + 'magical_chisel',
        'ingredients': [item('elemental_gem')] * 4 + [{'tag': 'forge:rods/wooden'}],
        'result': {'item': MOD + 'magical_chisel', 'count': 1},
    })
    count += 1
    print(f'写了 {count} 条配方到 {os.path.relpath(OUT, ROOT)}')


if __name__ == '__main__':
    main()
