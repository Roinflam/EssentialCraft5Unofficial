#!/usr/bin/env python3
"""
生成地脉投影仪（ECLeyProjector，第五批）的投影表：

    python tools/gen/ley_projector_tables.py

输出到 src/main/resources/data/essentialcraft5/loot_tables/ley_projector/{ores,rock}/<维度 ns>/<维度 path>.json，
脚本和结果都提交，**改数值只改下面的 TABLES / ROCK，再重跑**。先删掉 ley_projector/ 下旧的 *.json 再写，表里删掉的维度不会留下旧文件。

表的格式（batch5.md §3.4、policy §4.8）：
  - type 一律 minecraft:fishing（必需 ORIGIN、TOOL，不带 BLOCK_STATE，不会被当成方块掉落）；1 个池、rolls 1；
  - 矿石表里「岩石」是 minecraft:empty 条目（投影仪默认丢掉岩石，「保留岩石」开关打开时另掷 rock 表）；
  - 不写 explosion_decay（没有爆炸）、不引用方块掉落表（minecraft:blocks/*、essentialcraft5:blocks/*），条目自己写；
  - 数量公式照原版 BlockLootSubProvider L161~191：ore_drops（铜、铁、金、青金石、钻石、石英、金粒）、
    uniform_bonus_count bonusMultiplier 1（红石）；EC 自己的矿照生成的方块掉落表用 essentialcraft5:fortune_scaled_count；
  - **不许出燃料和矿石方块**：投影仪运行时也会丢掉有燃烧时间的、#forge:ores 里的产物（防热力产能器、岩浆冶炼炉循环），
    GameTest LeyProjectorTests.neverOutputsFuel 读这些 JSON 核对。

权重 = 每 10 万格「能投影的方块」里有几格（加起来正好 100000，岩石 = 剩下的）。
  **实测**（2026-09-29 第五批集成，tools/gen/ley_projector/：survey.py 起临时专用服、强制加载主世界和下界各 25×25 区块，
  blockcount.py 数方块，结果 survey_result.json；种子 20260929，装了本模组，只算自己和周围 8 块都生成完的 729 个区块）：
  - 主世界 y −64~63，每区块能投影的方块约 2.93 万（除空气、水、岩浆、基岩、火以外都算）。矿石含深层变种合并；
    煤（292 + 3）并进岩石（不出燃料）；y < 64 没有绿宝石；粗矿块、紫水晶这类不是矿石的也算岩石。
    四种元素矿和米斯莱矿的生成规则完全一样（矿脉 8、概率相同），实测 7.8~8.4 只是抽样误差，取平均 8.1 → 各 8。
  - 下界 y 0~127，每区块约 2.00 万。下界石英矿 615、下界金矿 215（→ 金粒）；EC 下界矿元素晶石 52、其余五种平均 15.4 → 各 15；
    远古残骸（7.6）、萤石（15.3）、镀金黑石不出（并进岩石）。
  之前的估算（主世界 fvtest 400 区块、下界按 OreFeature 分析）和实测对照写在 survey.md。
  岩石表（权重合计 100）：每个维度只留 2 种（审查 LEY-1：投影仪里每种岩石最多占一个输出格、岩石合计最多占 2 格，
    种类多了只会被丢掉），按实测比例并组、取整（最大余数法）；并组只用占比 ≥ 约 0.8% 的几种（和改之前的 8 种表同一批方块）：
    主世界：深板岩、凝灰岩（都在深板岩层）→ 深板岩圆石 49053；石头、安山岩、闪长岩、花岗岩、沙砾、泥土（含草方块）→ 圆石 48443
      （每 10 万格）→ 50 : 50；
    下界：下界岩（含绯红 / 诡异菌岩，挖了掉下界岩）、灵魂沙、灵魂土、沙砾 → 下界岩 88564；玄武岩、黑石、岩浆块（玄武岩三角洲）→ 玄武岩 9890
      → 90 : 10。
  - 海纳尔（第六批 6a，2026-09-30 集成后按审查 M7-1 重写）：tools/gen/hoanna_survey.py 普查 64 × 64 区块（矿和岩石按生成完的 4 356 个区块数），种子 20260929，
    结果 tools/gen/hoanna_survey_result.json（ores、composition_top）。每 10 万格：粗铜 277.6、粗铁 251.0（关了铜铁大矿脉，比主世界少约 10%）、
    红石 125.9、粗金 88.9、青金石 84.3、钻石 52.8、元素晶石 25.8、四种魔晶与米斯莱粉 7.4~7.7（生成规则和主世界相同，照主世界取 8）；
    煤 288.2 并进岩石。岩石：深板岩 47452.5 + 凝灰岩 3844.4 = 51297；石头、安山岩、闪长岩、花岗岩、沙砾、泥土、草方块 = 46542 → 52 : 48。
    遗迹里的强化石、虚空石、虚空栅栏等结构方块也算在岩石里（投影仪不出这些）。
"""
import glob
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'essentialcraft5', 'loot_tables', 'ley_projector')

TOTAL = 100000


# ---------------- 数量函数 ----------------

def ore_drops(min_count=None, max_count=None):
    """原版矿石：可选 set_count，再 apply_bonus ore_drops（时运 L：nextInt(L+2)−1，小于 0 取 0，再 +1 倍）"""
    functions = []
    if min_count is not None:
        functions.append({
            'function': 'minecraft:set_count',
            'count': {'type': 'minecraft:uniform', 'min': float(min_count), 'max': float(max_count)},
            'add': False,
        })
    functions.append({'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'})
    return functions


def uniform_bonus(min_count, max_count, multiplier=1):
    """原版红石矿：set_count 再 uniform_bonus_count（每级时运多 0~multiplier×L 个）"""
    return [
        {
            'function': 'minecraft:set_count',
            'count': {'type': 'minecraft:uniform', 'min': float(min_count), 'max': float(max_count)},
            'add': False,
        },
        {
            'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune',
            'formula': 'minecraft:uniform_bonus_count', 'parameters': {'bonusMultiplier': multiplier},
        },
    ]


def fortune_scaled(min_count, spread):
    """EC 矿：数量 = min + nextInt(spread × (时运 + 1))，和生成的方块掉落表一致（loot/FortuneScaledCount）"""
    return [{'function': 'essentialcraft5:fortune_scaled_count', 'min': min_count, 'spread': spread}]


# ---------------- 表（改数值只改这里） ----------------

# 维度 id → [(物品, 每 10 万格权重, 数量函数)]；岩石（minecraft:empty）自动补足到 TOTAL
TABLES = {
    # 实测（见文件头），合计 1000 / 10 万格
    'minecraft:overworld': [
        ('minecraft:raw_copper', 309, ore_drops(2, 5)),
        ('minecraft:raw_iron', 282, ore_drops()),
        ('minecraft:redstone', 120, uniform_bonus(4, 5)),
        ('minecraft:raw_gold', 86, ore_drops()),
        ('minecraft:lapis_lazuli', 84, ore_drops(4, 9)),
        ('minecraft:diamond', 52, ore_drops()),
        ('essentialcraft5:elemental_gem', 27, fortune_scaled(1, 2)),
        ('essentialcraft5:fire_drops', 8, fortune_scaled(1, 2)),
        ('essentialcraft5:water_drops', 8, fortune_scaled(1, 2)),
        ('essentialcraft5:earth_drops', 8, fortune_scaled(1, 2)),
        ('essentialcraft5:air_drops', 8, fortune_scaled(1, 2)),
        ('essentialcraft5:mithriline_dust', 8, fortune_scaled(3, 8)),
    ],
    # 实测（见文件头），合计 957 / 10 万格
    'minecraft:the_nether': [
        ('minecraft:quartz', 615, ore_drops()),
        ('minecraft:gold_nugget', 215, ore_drops(2, 6)),
        ('essentialcraft5:elemental_gem', 52, fortune_scaled(1, 2)),
        ('essentialcraft5:fire_drops', 15, fortune_scaled(1, 2)),
        ('essentialcraft5:water_drops', 15, fortune_scaled(1, 2)),
        ('essentialcraft5:earth_drops', 15, fortune_scaled(1, 2)),
        ('essentialcraft5:air_drops', 15, fortune_scaled(1, 2)),
        ('essentialcraft5:mithriline_dust', 15, fortune_scaled(3, 8)),
    ],
}
# 海纳尔（第六批 6a M7，batch6a §5.7）：维度 id 和维度类型 id 都是 essentialcraft5:hoanna，一张表两头都覆盖。
# 实测（见文件头「海纳尔」），合计 947 / 10 万格：地下地物照搬原版平原 + 同样的 6 种 EC 矿，但关了铜铁大矿脉，所以铜铁比主世界少约 10%。
# **不许放榍石、虚空物品、余烬、神秘饰品**（removed_redesign L172：保留探索；GameTest LeyProjectorTests.leyHoannaTable 守着）。
TABLES['essentialcraft5:hoanna'] = [
    ('minecraft:raw_copper', 278, ore_drops(2, 5)),
    ('minecraft:raw_iron', 251, ore_drops()),
    ('minecraft:redstone', 126, uniform_bonus(4, 5)),
    ('minecraft:raw_gold', 89, ore_drops()),
    ('minecraft:lapis_lazuli', 84, ore_drops(4, 9)),
    ('minecraft:diamond', 53, ore_drops()),
    ('essentialcraft5:elemental_gem', 26, fortune_scaled(1, 2)),
    ('essentialcraft5:fire_drops', 8, fortune_scaled(1, 2)),
    ('essentialcraft5:water_drops', 8, fortune_scaled(1, 2)),
    ('essentialcraft5:earth_drops', 8, fortune_scaled(1, 2)),
    ('essentialcraft5:air_drops', 8, fortune_scaled(1, 2)),
    ('essentialcraft5:mithriline_dust', 8, fortune_scaled(3, 8)),
]

# 「保留岩石」打开时，没出矿的格子另掷这张表（权重合计 100，每次 1 个；实测比例，见文件头）
ROCK = {
    'minecraft:overworld': [
        ('minecraft:cobbled_deepslate', 50),
        ('minecraft:cobblestone', 50),
    ],
    'minecraft:the_nether': [
        ('minecraft:netherrack', 90),
        ('minecraft:basalt', 10),
    ],
}
# 海纳尔：实测深板岩 + 凝灰岩 51297 : 其余 46542（见文件头）→ 52 : 48
ROCK['essentialcraft5:hoanna'] = [
    ('minecraft:cobbled_deepslate', 52),
    ('minecraft:cobblestone', 48),
]


def ores_table(entries):
    used = sum(weight for _, weight, _ in entries)
    if used >= TOTAL:
        raise SystemExit(f'矿的权重合计 {used} 不能 ≥ {TOTAL}')
    pool_entries = [{'type': 'minecraft:empty', 'weight': TOTAL - used}]
    for name, weight, functions in entries:
        entry = {'type': 'minecraft:item', 'name': name, 'weight': weight}
        if functions:
            entry['functions'] = functions
        pool_entries.append(entry)
    return {
        'type': 'minecraft:fishing',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': pool_entries}],
    }


def rock_table(entries):
    return {
        'type': 'minecraft:fishing',
        'pools': [{
            'rolls': 1.0, 'bonus_rolls': 0.0,
            'entries': [{'type': 'minecraft:item', 'name': name, 'weight': weight} for name, weight in entries],
        }],
    }


def write(kind, dimension, table):
    namespace, path = dimension.split(':', 1)
    target = os.path.join(OUT, kind, namespace, path + '.json')
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(table, f, indent=2, ensure_ascii=False)
        f.write('\n')
    return os.path.relpath(target, ROOT)


def main():
    for old in glob.glob(os.path.join(OUT, '**', '*.json'), recursive=True):
        os.remove(old)
    written = []
    for dimension, entries in TABLES.items():
        written.append(write('ores', dimension, ores_table(entries)))
    for dimension, entries in ROCK.items():
        written.append(write('rock', dimension, rock_table(entries)))
    for path in written:
        print(path.replace(os.sep, '/'))
    # 每千格约多少（手册 ley/ley_lens 的表照这个写）
    for dimension, entries in TABLES.items():
        print(dimension, '，每千格约：', '、'.join(f'{name.split(":")[1]} {weight / 100:g}' for name, weight, _ in entries))


if __name__ == '__main__':
    main()
