"""
第十三批 13e-S1 封印宝库的战利品表（batch13.md §6.4；HS §2.4.1），由 hoanna_loot.py 调 tables(lib)、写文件（单独跑本文件不出表）。

要出的表：
  - chests/sealed_vault_reward：宝库核心给解开名单上每人一份（ECItemEntities.giveOrDrop，不是箱子）。
    先辈残核必给（rolls 1 的一池）、遗物池 2 次（城塔那一池：lib.relic_pool(..., killed_by_player=False)）、地脉结晶 4～8、
    30% 残破勘测图（lib.survey_pool(名, chance=0.3)）；装备预留池不放（13e 已定：这一批没给宝库任何装备材料，以后要加再加一池）。
表里只能写已注册的物品（见 hoanna_loot.py 文件头）。
"""


# 地脉结晶每份几个（均匀）
LEY_CRYSTALS = (4, 8)
# 残破勘测图几率
SURVEY_CHANCE = 0.3


def tables(lib):
    """返回 {相对 loot_tables 的路径（不带 .json）: 表}"""
    # 遗物池 2 次 = 两个和城塔同样的池（各抽 6～17 次，合计期望约 23 件；拆成两个池比一个 12～34 次的池波动小）
    pools = [
        {
            'name': 'elder_remnant',
            'rolls': 1.0,
            'bonus_rolls': 0.0,
            'entries': [{'type': 'minecraft:item', 'name': lib.MOD + 'elder_remnant'}],
        },
        lib.relic_pool(lib.ROLLS, killed_by_player=False, name='relics'),
        lib.relic_pool(lib.ROLLS, killed_by_player=False, name='relics_2'),
        {
            'name': 'ley_crystal',
            'rolls': 1.0,
            'bonus_rolls': 0.0,
            'entries': [{'type': 'minecraft:item', 'name': lib.MOD + 'ley_crystal', 'functions': [{
                'function': 'minecraft:set_count',
                'count': {'type': 'minecraft:uniform', 'min': float(LEY_CRYSTALS[0]), 'max': float(LEY_CRYSTALS[1])},
                'add': False,
            }]}],
        },
        lib.survey_pool('sealed_vault_reward', chance=SURVEY_CHANCE),
    ]
    return {'chests/sealed_vault_reward': {'type': 'minecraft:chest', 'pools': pools}}
