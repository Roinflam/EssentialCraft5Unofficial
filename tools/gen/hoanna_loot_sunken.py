"""
第十三批 13e-S2 沉没城区的战利品表（batch13.md §5.3.4、§6.4；HS §2.4.2），由 hoanna_loot.py 调 tables(lib)、写文件（单独跑本文件不出表）。

出的表：
  - chests/sunken_district：穹顶里 2 个、沉房 50% 各 1 个箱子（拼件 worldgen/hoanna/sunken/SunkenPieces.CHEST_TABLE）。
    渊盐 SALT_COUNT 个、遗物池 1 次（同城塔那一池，6~17 抽，lib.relic_pool(lib.ROLLS, killed_by_player=False)）、
    残破勘测图 SURVEY_CHANCE（lib.survey_pool）。装备预留池不放（13e 已定：这一批没给沉城任何装备材料，以后要加再加一池）。
  - entities/abyss_tyrant：渊潮领主，每名分到掉落的参战者各掷一次（ECBossMonster / BossLoot），每个池 killed_by_player：
    异变之心 ×2、上古晶核 ×1、海纳尔遗物 6 抽（同一张遗物清单）。「每人每游戏日只领一份」在 Java（AbyssTyrantEntity）里管，不在表里。
表里只能写已注册的物品（见 hoanna_loot.py 文件头）。
"""

CHEST = 'chests/sunken_district'
TYRANT = 'entities/abyss_tyrant'

# 每箱渊盐几个（均匀）
SALT_COUNT = (2, 5)
# 残破勘测图几率
SURVEY_CHANCE = 0.25
# 渊潮领主：异变之心几个、上古晶核几个、遗物几抽
TYRANT_HEARTS = 2
TYRANT_CORES = 1
TYRANT_RELIC_ROLLS = (6, 6)


def counted(lib, pool_name, name, count):
    """一池一件、固定 count 个、killed_by_player（lib.single 加 set_count）"""
    functions = None
    if count != 1:
        functions = [{'function': 'minecraft:set_count', 'count': float(count), 'add': False}]
    return lib.single(pool_name, name, functions=functions)


def chest_table(lib):
    salt = {
        'name': 'abyssal_salt',
        'rolls': 1.0,
        'bonus_rolls': 0.0,
        'entries': [{'type': 'minecraft:item', 'name': lib.MOD + 'abyssal_salt', 'functions': [{
            'function': 'minecraft:set_count',
            'count': {'type': 'minecraft:uniform', 'min': float(SALT_COUNT[0]), 'max': float(SALT_COUNT[1])},
            'add': False,
        }]}],
    }
    return {'type': 'minecraft:chest', 'pools': [
        salt,
        lib.relic_pool(lib.ROLLS, killed_by_player=False),
        lib.survey_pool('sunken_district', chance=SURVEY_CHANCE),
    ]}


def tyrant_table(lib):
    return {'type': 'minecraft:entity', 'pools': [
        counted(lib, 'heart', 'aberrant_heart', TYRANT_HEARTS),
        counted(lib, 'core', 'ancient_core', TYRANT_CORES),
        lib.relic_pool(TYRANT_RELIC_ROLLS),
    ]}


def tables(lib):
    """返回 {相对 loot_tables 的路径（不带 .json）: 表}"""
    return {
        CHEST: chest_table(lib),
        TYRANT: tyrant_table(lib),
    }
