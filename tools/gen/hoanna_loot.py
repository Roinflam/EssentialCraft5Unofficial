#!/usr/bin/env python3
"""
生成海纳尔遗迹箱子、全息投影和审判庭哨兵的战利品表（第六批 6a M7「虚空材料与海纳尔遗物」，batch6a §5.7；第八批加全息投影，batch8 §4.1；
第十批 10c 加故障核心与审判庭哨兵，batch10 §5.10）：

    python tools/gen/hoanna_loot.py

输出（脚本和结果都提交，改表后重跑）：
  - src/main/resources/data/essentialcraft5/loot_tables/chests/{town_tower,catacombs}.json：
    两张内容相同（原作 assets/essentialcraft/loot_tables/chests/town_tower.json 与 catacombs.json 逐字相同）；id 在 Java 的 loot/HoannaLoot：
    town_tower 是城镇废墟的塔顶那一圈，每座塔 4 个箱子（town_tower 拼件）；catacombs 是古代墓穴出口房间的那一个箱子。
  - src/main/resources/data/essentialcraft5/loot_tables/entities/hologram_{addition,subtraction,multiplication,division}.json：
    全息投影的四张实体表从同一个遗物列表出（id 在 Java 的 entity/HologramEntity.lootTableFor，按最后放完的那一招选一张）。
    每张 = 对应的数学核心 1 个 + 轨道打击遥控器、二分枪各一池（各 25%，batch8 D3 = A；原作各 10%）+ 全息唱片 30% + 遗物 18~20 抽
    （原作 chests/hologram 的 18~20 抽：原作 Boss 死时把所在格换成箱子，这里不放箱子，遗物和核心原地掉出）。
    每个池都带 minecraft:killed_by_player（/kill、没被玩家打过的不掉；原作四张表都没有这个条件）。
    第十批 10c：四张表末尾各加一池「故障核心必掉 1 个」，带 essentialcraft5:random_target_balance（写随机的目标平衡值，第十九章解码用）。
  - src/main/resources/data/essentialcraft5/loot_tables/entities/inquisitorium_sentinel.json（第十批 10c）：
    审判庭哨兵 = 审判庭芯片 1 个 + 故障核心 1 个（带随机目标平衡值）+ 遗物 19~21 抽（同一张遗物清单），全部 killed_by_player。
  - 第十二批（batch12.md §4.1.3，卷数以定稿的「序 + 卷一～卷六」为准）：三张表末尾各加一池源质残卷，卷号由 essentialcraft5:scroll_volume 写
    （0 = 序，1～6 = 卷一～卷六，偏向开箱人 / 打倒它的人还没读过的卷）：城塔每箱 10%、卷 0～5（一座塔十几个箱子，再高就一座塔集齐）；
    墓穴每箱 40%、只出卷五（每处只有一个箱子）；哨兵 killed_by_player 必掉、只出卷六（prefer_unread 0，多人一起打也只掉一卷）。
    遗物池一行不动：城塔、墓穴两张表的遗物池仍逐项相同，只有末尾的残卷池不同。
  - 第十三批 13d（batch13.md §6.3「勘测图」）：城塔、墓穴两张表在残卷池后面再加一池残破勘测图（rolls 1、random_chance 0.12，
    原来的遗物池、残卷池不动；一座城镇约 12.9 个塔顶箱子 → 约 1.5 张）。
  - 第十三批 13e（batch13.md §6.4）：三座新结构的箱子表各在一个子模块里出，主脚本只 import 并写文件（三路各改各的，不碰同一个文件）：
    hoanna_loot_vault.py → chests/sealed_vault_reward（封印宝库核心每人一份）、hoanna_loot_sunken.py → chests/sunken_district、
    hoanna_loot_outpost.py → chests/inquisitorium_outpost。每个子模块给一个 tables(lib) 函数，返回 {相对 loot_tables 的路径（不带 .json）: 表}，
    lib 就是本模块（遗物池 relic_pool、残破勘测图池 survey_pool、残卷池 scroll_pool、单件池 single、entry 都从这里取，别在子模块里另抄一份）。
    首领的实体表（entities/abyss_tyrant、entities/calamity_heart、entities/town_sentry）不在这里，归各路自己的数据 JSON 或脚本。

原作表（24 条、总权重 343、每箱抽 6~17 次均匀）照搬，只换注册名：
  genItem:3 / 5 / 6 / 7 / 8 / 9 / 10 / 11 → magical_essence / magical_ingot / magical_water / magic_fortified_plating /
  magic_purified_ender_scale_alloy / magic_purified_glass_alloy / magic_purified_gold_alloy / magic_purified_redstone_alloy；
  genItem:20 → crystal_dust；genItem:35 / 36 / 37 → void_plating / void_core / void_mru_reactor；magicalSlag → magical_slag；
  ember:0~7 → <种类>_ember；bauble → mystical_accessory（加 essentialcraft5:randomize_accessory 开箱时就抽属性，
  加 essentialcraft5:not_disabled：general.baubles.disabled 列了它就不参加这一抽）。
每箱期望（343 权重、平均抽 11.5 次）：虚空板 / 核心 / 反应核各约 0.20 个、榍石约 4.0、闪耀榍石约 1.34、余烬合计约 9.4、炉渣约 8.2、神秘饰品约 0.50。

**表里只能写已注册的物品**：1.20.1 的 LootItem 解析时遇到未知物品 id 直接抛异常，整张表加载失败、箱子开出来是空的
（forge:item_exists 只是配方条件；not_disabled 在解析之后才判断，救不了）。所以没注册的东西（第七批的材料之类）不要往这里加。
写完用 json.load 读回来核对一遍。GameTest VoidTests.hoannaChestTables 读这两个文件核对条目、权重，并实际开箱检查产物。
"""
import json
import os
import sys

import hoanna_loot_outpost
import hoanna_loot_sunken
import hoanna_loot_vault

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
LOOT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'essentialcraft5', 'loot_tables')
OUT = os.path.join(LOOT, 'chests')
OUT_ENTITIES = os.path.join(LOOT, 'entities')

MOD = 'essentialcraft5:'
NL = '\n'

# (注册名, 权重, 数量下限, 数量上限)；数量都是 1 的写 None。顺序照原作表
RELICS = [
    ('titanite', 20, 2, 10),
    ('twinkling_titanite', 10, 2, 6),
    ('magical_essence', 15, 1, 6),
    ('magical_ingot', 15, 1, 6),
    ('magical_water', 15, 1, 6),
    ('magic_fortified_plating', 15, 1, 6),
    ('magic_purified_ender_scale_alloy', 15, 1, 6),
    ('magic_purified_glass_alloy', 15, 1, 6),
    ('magic_purified_gold_alloy', 15, 1, 6),
    ('magic_purified_redstone_alloy', 15, 1, 6),
    ('crystal_dust', 10, 1, 4),
    ('void_plating', 6, None, None),
    ('void_core', 6, None, None),
    ('void_mru_reactor', 6, None, None),
    ('magical_slag', 70, 1, 6),
    ('acidic_ember', 10, 1, 6),
    ('chaos_ember', 10, 1, 6),
    ('common_ember', 10, 1, 6),
    ('corrupted_ember', 10, 1, 6),
    ('crystal_ember', 10, 1, 6),
    ('divine_ember', 10, 1, 6),
    ('magical_ember', 10, 1, 6),
    ('flame_ember', 10, 1, 6),
    ('mystical_accessory', 15, None, None),
]
ROLLS = (6, 17)
TOTAL_WEIGHT = 343

# 输出的表：文件名 → 池名
TABLES = {
    'town_tower': 'relics',
    'catacombs': 'relics',
}

# 第十二批：源质残卷池（文件名 / 哨兵 → (几率, 卷号下限, 卷号上限, prefer_unread)）；几率 None = 必掉（哨兵带 killed_by_player）
SCROLL = 'ancient_scroll'
SCROLL_POOLS = {
    'town_tower': (0.10, 0, 5, 0.75),
    'catacombs': (0.40, 5, 5, 0.75),
    'inquisitorium_sentinel': (None, 6, 6, 0.0),
}

# 第十三批 13d：残破勘测图池（文件名 → 几率），加在表的最后
SURVEY = 'torn_survey_map'
SURVEY_POOLS = {
    'town_tower': 0.12,
    'catacombs': 0.12,
}

# 全息投影的遗物抽数（原作 chests/hologram）
HOLOGRAM_RELIC_ROLLS = (18, 20)
# 招式 → 核心（原作 genitem 70 / 73 / 72 / 71，RenderHologram 头顶的核心也按这个对应）
HOLOGRAM_CORES = {
    'addition': 'addition_core',
    'subtraction': 'subtraction_core',
    'multiplication': 'multiplication_core',
    'division': 'division_core',
}
# (池名, 注册名, 几率)：遥控器、二分枪 D3 = A 各 25%，唱片照原作 30%
HOLOGRAM_RARE = [
    ('remote', 'orbital_remote', 0.25),
    ('gun', 'divider_gun', 0.25),
    ('record', 'music_disc_ropocalypse', 0.3),
]
KILLED_BY_PLAYER = {'condition': 'minecraft:killed_by_player'}
# 第十批 10c：故障核心（带随机目标平衡值）的池，四张全息表都加在最后；审判庭哨兵另有芯片和约 20 抽遗物
GLITCHY_CORE = 'glitchy_core'
TARGET_BALANCE = {'function': MOD + 'random_target_balance'}
SENTINEL_RELIC_ROLLS = (19, 21)
SENTINEL_CHIP = 'inquisitorium_chip'


def entry(name, weight, low, high):
    item = MOD + name
    result = {'type': 'minecraft:item', 'name': item, 'weight': weight}
    functions = []
    if low is not None:
        functions.append({
            'function': 'minecraft:set_count',
            'count': {'type': 'minecraft:uniform', 'min': float(low), 'max': float(high)},
            'add': False,
        })
    if name == 'mystical_accessory':
        functions.append({'function': MOD + 'randomize_accessory'})
        result['conditions'] = [{'condition': MOD + 'not_disabled', 'item': item}]
    if functions:
        result['functions'] = functions
    return result


def scroll_pool(name, killed_by_player=False):
    """源质残卷一池：几率 / 卷号范围见 SCROLL_POOLS；物品本身不写 Volume，由 scroll_volume 函数开箱时写"""
    chance, low, high, prefer = SCROLL_POOLS[name]
    conditions = [] if chance is None else [{'condition': 'minecraft:random_chance', 'chance': chance}]
    if killed_by_player:
        conditions.append(dict(KILLED_BY_PLAYER))
    pool = {
        'name': SCROLL,
        'rolls': 1.0,
        'bonus_rolls': 0.0,
        'entries': [{'type': 'minecraft:item', 'name': MOD + SCROLL, 'functions': [
            {'function': MOD + 'scroll_volume', 'min': low, 'max': high, 'prefer_unread': prefer}]}],
    }
    if conditions:
        pool['conditions'] = conditions
    return pool


def survey_pool(name, chance=None):
    """残破勘测图一池：一抽、几率见 SURVEY_POOLS（13e 的子模块可以直接给 chance，不往 SURVEY_POOLS 里登记）"""
    return {
        'name': SURVEY,
        'rolls': 1.0,
        'bonus_rolls': 0.0,
        'entries': [{'type': 'minecraft:item', 'name': MOD + SURVEY}],
        'conditions': [{'condition': 'minecraft:random_chance', 'chance': SURVEY_POOLS[name] if chance is None else chance}],
    }


def table(name, pool_name):
    pools = [{
        'name': pool_name,
        'rolls': {'type': 'minecraft:uniform', 'min': float(ROLLS[0]), 'max': float(ROLLS[1])},
        'bonus_rolls': 0.0,
        'entries': [entry(*row) for row in RELICS],
    }, scroll_pool(name)]
    if name in SURVEY_POOLS:
        pools.append(survey_pool(name))
    return {'type': 'minecraft:chest', 'pools': pools}


def single(pool_name, name, chance=None, functions=None):
    """一池一件：被玩家打死才掉；chance 不为 None 时再加一个几率条件；functions 是这件物品的战利品函数"""
    conditions = [] if chance is None else [{'condition': 'minecraft:random_chance', 'chance': chance}]
    conditions.append(dict(KILLED_BY_PLAYER))
    item = {'type': 'minecraft:item', 'name': MOD + name}
    if functions:
        item['functions'] = [dict(f) for f in functions]
    return {
        'name': pool_name,
        'rolls': 1.0,
        'bonus_rolls': 0.0,
        'entries': [item],
        'conditions': conditions,
    }


def relic_pool(rolls, killed_by_player=True, name='relics'):
    """遗物池（同一张原作遗物清单）：rolls = (下限, 上限)；首领表带 killed_by_player，箱子表（13e 子模块）传 False"""
    pool = {
        'name': name,
        'rolls': {'type': 'minecraft:uniform', 'min': float(rolls[0]), 'max': float(rolls[1])},
        'bonus_rolls': 0.0,
        'entries': [entry(*row) for row in RELICS],
    }
    if killed_by_player:
        pool['conditions'] = [dict(KILLED_BY_PLAYER)]
    return pool


def hologram_table(core):
    pools = [single('core', core)]
    pools += [single(pool, name, chance) for pool, name, chance in HOLOGRAM_RARE]
    pools.append(relic_pool(HOLOGRAM_RELIC_ROLLS))
    # 故障核心放在最后（前五个池的顺序不变，第八批的测试按下标核）
    pools.append(single('glitchy_core', GLITCHY_CORE, functions=[TARGET_BALANCE]))
    return {'type': 'minecraft:entity', 'pools': pools}


def sentinel_table():
    # 残卷池放在最后（前三个池的顺序不变，第十批的测试按下标核）
    return {'type': 'minecraft:entity', 'pools': [
        single('chip', SENTINEL_CHIP),
        single('glitchy_core', GLITCHY_CORE, functions=[TARGET_BALANCE]),
        relic_pool(SENTINEL_RELIC_ROLLS),
        scroll_pool('inquisitorium_sentinel', killed_by_player=True),
    ]}


def write(path, data):
    with open(path, 'w', encoding='utf-8', newline=NL) as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write(NL)
    with open(path, encoding='utf-8') as f:
        json.load(f)
    print(os.path.relpath(path, ROOT).replace(os.sep, '/'))


def main():
    total = sum(weight for _, weight, _, _ in RELICS)
    if total != TOTAL_WEIGHT or len(RELICS) != 24:
        raise SystemExit(f'原作表是 24 条、总权重 {TOTAL_WEIGHT}，现在 {len(RELICS)} 条、{total}')
    os.makedirs(OUT, exist_ok=True)
    for name, pool in TABLES.items():
        write(os.path.join(OUT, name + '.json'), table(name, pool))
    os.makedirs(OUT_ENTITIES, exist_ok=True)
    for attack, core in HOLOGRAM_CORES.items():
        write(os.path.join(OUT_ENTITIES, 'hologram_' + attack + '.json'), hologram_table(core))
    write(os.path.join(OUT_ENTITIES, 'inquisitorium_sentinel.json'), sentinel_table())
    # 第十三批 13e：三座新结构的表（各子模块自己出，见文件头）
    lib = sys.modules[__name__]
    for module in (hoanna_loot_vault, hoanna_loot_sunken, hoanna_loot_outpost):
        for path, data in module.tables(lib).items():
            target = os.path.join(LOOT, *path.split('/')) + '.json'
            os.makedirs(os.path.dirname(target), exist_ok=True)
            write(target, data)
    for label, (low_rolls, high_rolls) in (('每箱', ROLLS), ('每只全息投影', HOLOGRAM_RELIC_ROLLS), ('每只审判庭哨兵', SENTINEL_RELIC_ROLLS)):
        rolls = (low_rolls + high_rolls) / 2
        print(f'{label}（平均抽 {rolls} 次）：')
        for name, weight, low, high in RELICS:
            per = weight / total * rolls * (1 if low is None else (low + high) / 2)
            print(f'  {name}: 约 {per:.2f} 个')


if __name__ == '__main__':
    main()
