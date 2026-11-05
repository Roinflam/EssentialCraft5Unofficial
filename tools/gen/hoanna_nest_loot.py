#!/usr/bin/env python3
"""
生成海纳尔五种巢穴的类型 JSON 和奖励表（第十三批 13d，batch13.md §6.4 巢穴类型表）：

    python tools/gen/hoanna_nest_loot.py

输出（脚本和结果都提交，改表后重跑）：
  - src/main/resources/data/essentialcraft5/hoanna_nests/<id>.json：巢穴类型（格式见 Java 的 hoanna/nest/HoannaNestTypes）。
    一波 = 4 或 3 只这一种怪 + 1 只领头（leader，放出来就是精英）；半径照默认 6 / 16 / 48，回充、收回不写（用配置 general.hoanna_nests.*）。
  - src/main/resources/data/essentialcraft5/loot_tables/hoanna_nests/<id>.json：奖励表，每名出过力的人掷一份（hoanna/nest/HoannaNests.giveReward）。
    每个池都带 essentialcraft5:from_nest + minecraft:killed_by_player（掷表时 THIS_ENTITY 是这一波最后被打死的那只、KILLER 是领奖人，两条都成立）；
    异变之心 1、异变残质 2~4、海纳尔遗物 1 抽（遗物清单直接用 tools/gen/hoanna_loot.py 的 RELICS，和城塔 / 墓穴箱子同一张），
    辐射陨坑另加辐晶 2~4，沉城卫兵另加渊盐 4~8。没有几率条件：清完一整波就一定拿到（check_progression 记成「有条件、不靠运气」）。

用到的物品必须已注册（未知物品 id 会让整张表加载失败）。写完用 json.load 读回来核对一遍。
GameTest HoannaSpawnTests.hoannaNestTypesAndRewards 读这些类型、掷这些表核对产物。
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
import hoanna_loot  # noqa: E402

DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'essentialcraft5')
NESTS = os.path.join(DATA, 'hoanna_nests')
LOOT = os.path.join(DATA, 'loot_tables', 'hoanna_nests')

MOD = 'essentialcraft5:'
NL = '\n'

# 巢穴 id → (怪, 普通几只, 额外奖励 [(物品, 下限, 上限)])；用在哪见 batch13.md §6.4
NEST_TYPES = {
    'frozen_camp': ('frostbitten', 4, []),                          # 冰封营地（13d 中景观）
    'chaos_fissure': ('chaos_aberrant', 3, []),                     # 混沌裂隙（13d 中景观）
    'fallout_crater': ('hoanna_scavenger', 4, [('radiant_crystal', 2, 4)]),  # 辐射陨坑（13d 中景观）
    'outpost_camp': ('inquisitorium_patroller', 4, []),             # 审判庭前哨的两个营地（13e）
    'sunken_guard': ('catacomb_revenant', 3, [('abyssal_salt', 4, 8)]),      # 沉没城区穹顶两侧的沉城卫兵（13e）
}

# 找落点的水平半径：默认 6；沉城卫兵的核心在穹顶里离中心 6 格、穹顶内半径约 8.4，半径 6 会有约四成落点在穹顶外的海里，
# 改成 2（落点 x 离中心 4~8、z 在 ±2 以内，全在穹顶里，也碰不到正中的祭坛和南北两个箱子）
SPAWN_RADIUS = {'sunken_guard': 2}

CONDITIONS = [{'condition': MOD + 'from_nest'}, {'condition': 'minecraft:killed_by_player'}]


def count(low, high):
    return {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': float(low), 'max': float(high)}, 'add': False}


def single(name, item, low=None, high=None):
    entry = {'type': 'minecraft:item', 'name': MOD + item}
    if low is not None:
        entry['functions'] = [count(low, high)]
    return {'name': name, 'rolls': 1.0, 'bonus_rolls': 0.0, 'entries': [entry], 'conditions': [dict(c) for c in CONDITIONS]}


def relic_pool():
    return {'name': 'relics', 'rolls': 1.0, 'bonus_rolls': 0.0,
            'entries': [hoanna_loot.entry(*row) for row in hoanna_loot.RELICS],
            'conditions': [dict(c) for c in CONDITIONS]}


def loot_table(nest_id):
    _, _, extras = NEST_TYPES[nest_id]
    pools = [single('heart', 'aberrant_heart'), single('residue', 'aberrant_residue', 2, 4), relic_pool()]
    pools += [single(item, item, low, high) for item, low, high in extras]
    return {'type': 'minecraft:entity', 'pools': pools, 'random_sequence': MOD + 'hoanna_nests/' + nest_id}


def nest_type(nest_id):
    mob, normal, _ = NEST_TYPES[nest_id]
    return {
        'entities': [{'type': MOD + mob, 'count': normal}, {'type': MOD + mob, 'count': 1, 'leader': True}],
        'spawn_radius': SPAWN_RADIUS.get(nest_id, 6),
        'activate_radius': 16,
        'leash_radius': 48,
        'loot_table': MOD + 'hoanna_nests/' + nest_id,
    }


def write(path, data):
    with open(path, 'w', encoding='utf-8', newline=NL) as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write(NL)
    with open(path, encoding='utf-8') as f:
        json.load(f)
    print(os.path.relpath(path, ROOT).replace(os.sep, '/'))


def main():
    os.makedirs(NESTS, exist_ok=True)
    os.makedirs(LOOT, exist_ok=True)
    for nest_id in NEST_TYPES:
        write(os.path.join(NESTS, nest_id + '.json'), nest_type(nest_id))
        write(os.path.join(LOOT, nest_id + '.json'), loot_table(nest_id))


if __name__ == '__main__':
    main()
