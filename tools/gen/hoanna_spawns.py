"""
海纳尔群系的刷怪表（由 tools/gen/hoanna_biomes.py 读，不单独跑）。第十三批 13d 从 hoanna_biomes.py 拆出来（batch13.md §6.5、§9.4）。

  MONSTERS_BY_BIOME：每个群系 monster 类别的刷怪项 (实体 id, 权重, 每群最少, 每群最多)；
  SPAWN_COSTS_BY_BIOME：每个群系的 spawn_costs（实体 id → {'energy_budget': budget, 'charge': charge}，照原版群系 JSON 的写法）；
  spawn_modifiers()：forge:add_spawns 之类追加刷怪的 biome_modifier（7 个群系一样）。
别的类别（creature、ambient、水生……）一律空：海纳尔不刷被动生物（policy §4.4）。

7 个群系都先放原版平原的怪物表（D26 A：原版平原表合计 515 + 风法师 2 不动），再照 batch13.md §6.5 的定稿追加本地怪（NATIVES）：
每个陆地群系本地怪合计 220（约占 30%，实测后再调到 25~35%），海纳尔之海只加渊鳗 120（怪物类别、水里放）。
spawn_costs 照同一张表（charge / energy_budget），没写的怪不受刷怪花费限制。原版那几项别删别改（权重是普查对过的）。
**权重和每群数量是数据**：服主可以用数据包覆盖群系 JSON（data/essentialcraft5/worldgen/biome/<群系>.json 的 spawners / spawn_costs）。
附近上限、时段、潮汐、天灾换表这些不是群系 JSON 能表达的，写在代码里（各怪的刷怪判定、MobSpawnRules.capped、hoanna/HoannaSpawnHooks、FumeSpawns），不在这里。
古代墓穴的墓穴守灵走结构的 spawn_overrides（data/essentialcraft5/worldgen/structure/old_catacombs.json），也不在这里。
"""

MOD = 'essentialcraft5:'

# 原版平原的怪物表（BiomeDefaultFeatures.monsters(95, 5, 100, false)），7 个群系相同
VANILLA_PLAINS_MONSTERS = [
    ('minecraft:spider', 100, 4, 4),
    ('minecraft:zombie', 95, 4, 4),
    ('minecraft:zombie_villager', 5, 1, 1),
    ('minecraft:skeleton', 100, 4, 4),
    ('minecraft:creeper', 100, 4, 4),
    ('minecraft:slime', 100, 4, 4),
    ('minecraft:enderman', 10, 1, 4),
    ('minecraft:witch', 5, 1, 1),
]

MOB_CATEGORIES = ['monster', 'creature', 'ambient', 'axolotls', 'underground_water_creature', 'water_creature', 'water_ambient', 'misc']

BIOMES = ['frozen_corruption', 'magic_corruption', 'chaos_corruption', 'dreadlands', 'hoanna_desert', 'shadow_corruption', 'hoanna_sea']

# batch13.md §6.5：每个群系追加的本地怪 (实体, 权重, 每群最少, 每群最多)。陆地群系合计 220
NATIVES = {
    'frozen_corruption': [('frostbitten', 180, 2, 3), ('mana_prism', 40, 1, 1)],
    'magic_corruption': [('mana_prism', 140, 1, 2), ('frostbitten', 80, 1, 2)],
    'chaos_corruption': [('chaos_aberrant', 180, 1, 2), ('ember_spirit', 40, 1, 1)],
    'dreadlands': [('ember_spirit', 120, 1, 2), ('hoanna_scavenger', 100, 1, 3)],
    'hoanna_desert': [('hoanna_scavenger', 120, 1, 3), ('ember_spirit', 100, 1, 2)],
    'shadow_corruption': [('inquisitorium_patroller', 160, 2, 3), ('chaos_aberrant', 60, 1, 1)],
    'hoanna_sea': [('abyssal_eel', 120, 1, 3)],
}
LAND_NATIVE_TOTAL = 220

# batch13.md §6.5 的 spawn_costs：怪 → (charge, energy_budget)；同一种怪在哪个群系都一样，只写进它在表里的群系
SPAWN_COST = {
    'frostbitten': (0.7, 0.15),
    'mana_prism': (1.0, 0.1),
    'chaos_aberrant': (0.8, 0.12),
    'ember_spirit': (0.7, 0.15),
}

MONSTERS_BY_BIOME = {biome: list(VANILLA_PLAINS_MONSTERS) + [(MOD + mob, w, lo, hi) for mob, w, lo, hi in NATIVES[biome]] for biome in BIOMES}

SPAWN_COSTS_BY_BIOME = {
    biome: {MOD + mob: {'energy_budget': SPAWN_COST[mob][1], 'charge': SPAWN_COST[mob][0]}
            for mob, _, _, _ in NATIVES[biome] if mob in SPAWN_COST}
    for biome in BIOMES
}

for _biome, _rows in NATIVES.items():
    if _biome != 'hoanna_sea':
        assert sum(w for _, w, _, _ in _rows) == LAND_NATIVE_TOTAL, _biome + ' 的本地怪权重合计应为 220'


def spawn_modifiers():
    """biome_modifier 名 → 内容（追加刷怪的那几个）"""
    tag = '#' + MOD + 'is_hoanna'
    return {
        'wind_mage_spawns_hoanna': {'type': 'forge:add_spawns', 'biomes': tag,
                                    'spawners': {'type': MOD + 'wind_mage', 'weight': 2, 'minCount': 1, 'maxCount': 6}},
    }


def spawners(biome):
    """群系 JSON 的 spawners：monster 按表，别的类别空"""
    out = {category: [] for category in MOB_CATEGORIES}
    out['monster'] = [{'type': t, 'weight': w, 'minCount': lo, 'maxCount': hi} for t, w, lo, hi in MONSTERS_BY_BIOME[biome]]
    return out


def spawn_costs(biome):
    return {entity: dict(cost) for entity, cost in SPAWN_COSTS_BY_BIOME[biome].items()}
