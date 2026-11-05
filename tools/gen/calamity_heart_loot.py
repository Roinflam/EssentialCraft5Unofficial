# -*- coding: utf-8 -*-
"""天灾之心的实体战利品表（第十三批 13e，batch13.md §5.3.5）：data/essentialcraft5/loot_tables/entities/calamity_heart.json。

整张表都要 killed_by_player；BossLoot 按「每名伤害 ≥ 10% 的参战者一份」各掷一次、直接进背包。池按下标排，GameTest 按下标核：
  0 remnant        天灾残核 ×1（每人第一次打倒另 +first_kill_bonus，由代码给，不在表里）
  1 aberrant_heart 异变之心 ×2
  2 relics         海纳尔遗物 19~21 抽（同一张原作遗物清单，hoanna_loot.relic_pool）
  3~6 主灾奖励     条件 essentialcraft5:calamity_main{event}：日阵闪耀榍石 ×4、毒雾 MRU 反应核 ×2、黑暗虚空核心 ×2、地震永恒物质 ×1
经验 300 由实体给（配置 experience），不在表里。

用法：python tools/gen/calamity_heart_loot.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hoanna_loot as lib  # noqa: E402

RELIC_ROLLS = (19, 21)
MAIN_BONUS = [
    ('main_sun_array', 'sun_array', 'twinkling_titanite', 4),
    ('main_fumes', 'fumes', 'void_mru_reactor', 2),
    ('main_darkness', 'darkness', 'void_core', 2),
    ('main_earthquake', 'earthquake', 'matter_of_eternity', 1),
]


def counted(pool_name, name, count):
    pool = lib.single(pool_name, name)
    if count != 1:
        pool['entries'][0]['functions'] = [{'function': 'minecraft:set_count', 'count': float(count)}]
    return pool


def main_bonus(pool_name, event, name, count):
    pool = counted(pool_name, name, count)
    pool['conditions'] = [{'condition': lib.MOD + 'calamity_main', 'event': event}, dict(lib.KILLED_BY_PLAYER)]
    return pool


def table():
    pools = [
        counted('remnant', 'calamity_remnant', 1),
        counted('aberrant_heart', 'aberrant_heart', 2),
        lib.relic_pool(RELIC_ROLLS),
    ]
    pools += [main_bonus(*row) for row in MAIN_BONUS]
    return {'type': 'minecraft:entity', 'pools': pools}


def main():
    os.makedirs(lib.OUT_ENTITIES, exist_ok=True)
    lib.write(os.path.join(lib.OUT_ENTITIES, 'calamity_heart.json'), table())
    return 0


if __name__ == '__main__':
    sys.exit(main())
