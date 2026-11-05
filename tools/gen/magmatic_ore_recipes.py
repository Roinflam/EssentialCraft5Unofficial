#!/usr/bin/env python3
"""
生成矿石加工的矿表（岩浆冶炼炉、岩浆熔炉共用的配方类型 essentialcraft5:ore_smelting）：

    python tools/gen/magmatic_ore_recipes.py

输出到 src/main/resources/data/essentialcraft5/recipes/magmatic/ore_<名字>.json，脚本和结果都提交，改表后重跑。
先删掉目录里旧的 ore_*.json 再写，表里删掉的矿不会留下旧文件。

来源：原作 api/OreSmeltingRecipe 的 static 块（写死的矿表，颜色、掉落数照抄）。
按 D8：只带原版和本模组的矿（原作表里锡、铅、银、钴、铝、铀、铱、硝石、硫、铂、秘银、Alchemite 等别的模组的矿不带，
整合包要加就照这个格式写数据包）；铁、金、铜另收粗矿（倍率减半，机器里算）；下界金矿单列一条、掉落数 1、优先级 1
（forge:ores/gold 里也有它，优先级高的先匹配）。
"""
import glob
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'essentialcraft5', 'recipes', 'magmatic')

# (文件名, 矿石, 粗矿或 None, 产物, 掉落数, 颜色, 优先级)
# 矿石 / 粗矿以 # 开头的是物品标签，否则是物品 id
TABLE = [
    ('coal', '#forge:ores/coal', None, 'minecraft:coal', 1, 0x343434, 0),
    ('iron', '#forge:ores/iron', '#forge:raw_materials/iron', 'minecraft:iron_ingot', 1, 0xE2C0AA, 0),
    ('gold', '#forge:ores/gold', '#forge:raw_materials/gold', 'minecraft:gold_ingot', 1, 0xF8AF2B, 0),
    # 原作没有下界金矿（1.16 才加）。颜色取偏红的金色，和金矿的合金分得开
    ('nether_gold', 'minecraft:nether_gold_ore', None, 'minecraft:gold_ingot', 1, 0xE8912B, 1),
    ('copper', '#forge:ores/copper', '#forge:raw_materials/copper', 'minecraft:copper_ingot', 1, 0xBC4800, 0),
    ('diamond', '#forge:ores/diamond', None, 'minecraft:diamond', 1, 0x5DECF5, 0),
    ('emerald', '#forge:ores/emerald', None, 'minecraft:emerald', 1, 0x17DD62, 0),
    ('quartz', '#forge:ores/quartz', None, 'minecraft:quartz', 1, 0xD1BEB1, 0),
    ('redstone', '#forge:ores/redstone', None, 'minecraft:redstone', 8, 0x8F0303, 0),
    ('lapis', '#forge:ores/lapis', None, 'minecraft:lapis_lazuli', 16, 0x1C40A9, 0),
    ('fire_elemental', '#forge:ores/fire_elemental', None, 'essentialcraft5:fire_drops', 3, 0xFF0000, 0),
    ('water_elemental', '#forge:ores/water_elemental', None, 'essentialcraft5:water_drops', 3, 0x0000FF, 0),
    ('earth_elemental', '#forge:ores/earth_elemental', None, 'essentialcraft5:earth_drops', 3, 0x7D5A3A, 0),
    ('air_elemental', '#forge:ores/air_elemental', None, 'essentialcraft5:air_drops', 3, 0xFFFFFF, 0),
    ('elemental', '#forge:ores/elemental', None, 'essentialcraft5:elemental_gem', 3, 0xFF00FF, 0),
    ('mithriline', '#forge:ores/mithriline', None, 'essentialcraft5:mithriline_dust', 8, 0x00FF00, 0),
]


def ingredient(ref):
    return {'tag': ref[1:]} if ref.startswith('#') else {'item': ref}


def main():
    os.makedirs(OUT, exist_ok=True)
    for old in glob.glob(os.path.join(OUT, 'ore_*.json')):
        os.remove(old)
    for name, ore, raw, output, drop, color, priority in TABLE:
        recipe = {'type': 'essentialcraft5:ore_smelting', 'ore': ingredient(ore)}
        if raw:
            recipe['raw'] = ingredient(raw)
        recipe['output'] = {'item': output}
        recipe['drop_amount'] = drop
        recipe['color'] = '#%06X' % color
        if priority:
            recipe['priority'] = priority
        path = os.path.join(OUT, 'ore_%s.json' % name)
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(recipe, f, indent=2, ensure_ascii=False)
            f.write('\n')
    print('写了 %d 条矿石加工配方 → %s' % (len(TABLE), os.path.relpath(OUT, ROOT)))


if __name__ == '__main__':
    main()
