"""
第十三批 13e-S3 审判庭前哨的战利品表（batch13.md §6.4；HS §2.4.3），由 hoanna_loot.py 调 tables(lib)、写文件（单独跑本文件不出表）。

出的表：
  - chests/inquisitorium_outpost：场外两个营地各 1 个箱子（id 在 Java 的 worldgen/hoanna/outpost/OutpostPieces.CHEST_LOOT）。每箱三池：
      遗物池 2~3 抽（同一张原作遗物清单，lib.relic_pool(..., killed_by_player=False)）；
      影晶 1~3 个（第二十二章的材料，前哨就在暗影腐化之地里，顺手给一点；影晶簇可再生，这里不是主要来源）；
      残破勘测图 25%（同沉城箱子的口径）。
    一座前哨两箱合计期望：遗物约 5 抽、影晶约 4 个、勘测图约 0.5 张。
营地的巢穴奖励是 13d 的巢穴类型 outpost_camp 自己的表（hoanna_nests/outpost_camp），不在这里。
表里只能写已注册的物品（见 hoanna_loot.py 文件头）。
"""

TABLE = 'chests/inquisitorium_outpost'
RELIC_ROLLS = (2, 3)
UMBRAL = ('umbral_crystal', 1, 3)
SURVEY_CHANCE = 0.25


def umbral_pool(lib):
    name, low, high = UMBRAL
    return {
        'name': 'umbral',
        'rolls': 1.0,
        'bonus_rolls': 0.0,
        'entries': [{
            'type': 'minecraft:item',
            'name': lib.MOD + name,
            'functions': [{
                'function': 'minecraft:set_count',
                'count': {'type': 'minecraft:uniform', 'min': float(low), 'max': float(high)},
                'add': False,
            }],
        }],
    }


def tables(lib):
    """返回 {相对 loot_tables 的路径（不带 .json）: 表}"""
    return {
        TABLE: {
            'type': 'minecraft:chest',
            'pools': [
                lib.relic_pool(RELIC_ROLLS, killed_by_player=False),
                umbral_pool(lib),
                lib.survey_pool('inquisitorium_outpost', chance=SURVEY_CHANCE),
            ],
        },
    }
