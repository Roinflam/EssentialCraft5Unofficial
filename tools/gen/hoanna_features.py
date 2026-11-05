"""
海纳尔群系的地物表（由 tools/gen/hoanna_biomes.py 读，不单独跑）。第十三批 13d 从 hoanna_biomes.py 拆出来（batch13.md §6.1、§9.4）。

这里写三样东西：
  1. MASTER_BY_STEP：11 步（GenerationStep.Decoration 的顺序）**每一步一份主序列**，每项 (编号, 地物 id, 归属)；
  2. BY_BIOME：各群系每一步取主序列里的哪几个编号（没写的步取 COMMON_BY_STEP，也就是这一步的全部公共地物）；
  3. EC 自己的地物定义 ec_features()（configured / placed feature），只有写出了定义、又登记进 EC_READY 的才放进群系。

为什么要「每一步一份主序列」：同一维度里所有群系的地物在每一步上必须能排成一个不矛盾的顺序，否则启动即崩「Feature order cycle」
（FeatureSorter.buildFeaturesPerStep）。每个群系每一步的列表都从这一步的主序列**按顺序**挑，排出来就一定不矛盾。
biome_modifier 追加进来的地物（FEATURE_MODIFIERS：hoanna_ores、hoanna_elder_mrucu）对 7 个群系一样、都排在各步末尾，
也记进 MODIFIER_TAIL_BY_STEP 一起核：主序列里不能再出现它们。hoanna_biomes.py 的 check_feature_order() 断言这几条，
GameTest HoannaNatureTests.hoannaFeatureOrderConsistent 在游戏里再核一遍（biome_modifier 生效后）。

【加群系专属地物怎么加】（13d-H1b 照这个填）
  1. 在 ec_features() 里写 configured / placed feature（名字 = 注册名，方块按注册名写）；要的话在 PLACED_FEATURE_OF 里写它引用哪个 configured。
  2. 在 MASTER_BY_STEP[该步] 里挑一个位置插一项 (新编号, MOD + 名字, 'H1b')。编号只是标识：这一步里唯一就行，新地物从 101 往上编，
     **先后看它在列表里的位置，不看编号大小**；已有的编号别改（batch6a.md §5.1 的植被序号 1~12 文档里引用着）。
     位置决定它和别的地物谁先放：地物会读 / 改别人放过的方块时（比如要长在草丛之后），把它排在后面。
  3. 在 BY_BIOME[群系][该步] 里按主序列的先后写上这个编号（这一步原来没写的群系，先把 COMMON_BY_STEP[该步] 抄过来再加）。
  4. 名字加进 EC_READY；重跑 python tools/gen/hoanna_biomes.py。
  只挂海纳尔群系（这些 JSON 只被海纳尔群系引用），主世界零变化（policy §4.19 P3）。
"""

MOD = 'essentialcraft5:'

# ---------------------------------------------------------------- 每一步的主序列
# 0~8、10 步是 1.20.1 原版平原的地下部分（OverworldBiomes.plains + BiomeDefaultFeatures，min_y −64 所以
# monster_room_deep、ore_gold_lower、ore_tuff 都在），7 个群系相同；第 9 步（植被）按群系挑。
# 编号：0~8、10 步照列表顺序从 1 编；第 9 步是 batch6a.md §5.1 的植被序号。
VEGETATION_STEP = 9


def _numbered(features):
    return [(i + 1, feature, None) for i, feature in enumerate(features)]


MASTER_BY_STEP = {
    0: [],                                                                       # raw_generation
    1: _numbered(['minecraft:lake_lava_underground', 'minecraft:lake_lava_surface']),  # lakes
    2: _numbered(['minecraft:amethyst_geode']) + [                               # local_modifications
        (101, MOD + 'frost_marrow_spikes', 'H1b'),
        (102, MOD + 'titanite_outcrop', 'H1b'),
        (103, MOD + 'obsidian_stub', 'H1b'),
        (104, MOD + 'ruin_debris', 'H1b'),
        (105, MOD + 'frozen_debris', 'H1b'),
        (106, MOD + 'sunken_debris', 'H1b'),
    ],
    3: _numbered(['minecraft:monster_room', 'minecraft:monster_room_deep']),      # underground_structures
    4: [(101, MOD + 'hoanna_midscape', 'H1b')],                                  # surface_structures：中景观（植被之前）
    5: [],                                                                       # strongholds
    6: _numbered([                                                               # underground_ores：平原的 28 项，顺序照原版
        'minecraft:ore_dirt', 'minecraft:ore_gravel', 'minecraft:ore_granite_upper', 'minecraft:ore_granite_lower',
        'minecraft:ore_diorite_upper', 'minecraft:ore_diorite_lower', 'minecraft:ore_andesite_upper', 'minecraft:ore_andesite_lower',
        'minecraft:ore_tuff',
        'minecraft:ore_coal_upper', 'minecraft:ore_coal_lower', 'minecraft:ore_iron_upper', 'minecraft:ore_iron_middle',
        'minecraft:ore_iron_small', 'minecraft:ore_gold', 'minecraft:ore_gold_lower', 'minecraft:ore_redstone',
        'minecraft:ore_redstone_lower', 'minecraft:ore_diamond', 'minecraft:ore_diamond_large', 'minecraft:ore_diamond_buried',
        'minecraft:ore_lapis', 'minecraft:ore_lapis_buried', 'minecraft:ore_copper', 'minecraft:underwater_magma',
        'minecraft:disk_sand', 'minecraft:disk_clay', 'minecraft:disk_gravel',
    ]) + [(101, MOD + 'abyssal_salt_disk', 'H1b'),
           # 焦痕地面放在植被之前（和原版 disk_* 同一步）：disk 只换目标方块、不管头顶，放在植被之后会把仙人掌 / 枯灌木脚下的沙换掉，
           # 它们第一次邻格更新就掉成物品；先烧焦痕，植被放的时候自己判站不站得住
           (102, MOD + 'scorched_ground', 'H1b')],
    7: [],                                                                       # underground_decoration
    8: _numbered(['minecraft:spring_water', 'minecraft:spring_lava']),           # fluid_springs
    9: [                                                                         # vegetal_decoration（植被，batch6a.md §5.1 的序号）
        (1, 'minecraft:glow_lichen', None),
        (2, MOD + 'hoanna_trees', 'M1'),
        (101, MOD + 'dead_tree', 'H1b'),
        (3, MOD + 'green_flower_patch', 'M1'),
        (4, MOD + 'hoanna_grass_patch', 'M1'),
        (5, 'minecraft:patch_dead_bush_2', None),
        (6, 'minecraft:brown_mushroom_normal', None),
        (7, 'minecraft:red_mushroom_normal', None),
        (8, 'minecraft:patch_sugar_cane', None),
        (9, 'minecraft:patch_sugar_cane_desert', None),
        (10, 'minecraft:patch_pumpkin', None),
        (11, MOD + 'dread_cactus_patch', 'M1'),
        (12, MOD + 'magical_fruit_patch', 'M1'),
        (102, MOD + 'hoanna_ley_vent', 'H1b'),
        (103, MOD + 'hoanna_ley_vent_sparse', 'H1b'),
        (104, MOD + 'hoanna_crimson_growth', 'H1b'),
        (105, MOD + 'tide_bud_bed', 'H1b'),
        (106, MOD + 'radiant_cluster_hotspot', 'H1b'),
        (107, MOD + 'bone_pile', 'H1b'),
        (108, MOD + 'umbral_cluster_patch', 'H1b'),
        (109, 'minecraft:seagrass_normal', None),
        (110, 'minecraft:kelp_cold', None),
        (111, MOD + 'hoanna_milestone', 'H1b'),                                   # 最后：要避开已经长好的树
    ],
    10: _numbered(['minecraft:freeze_top_layer']),                              # top_layer_modification
}
STEP_COUNT = 11

# 没在 BY_BIOME 里写的步：取这一步主序列里的全部原版地物（归属是 None 的，7 个群系都有）。第 9 步没有公共的，每个群系都要写。
# EC 的群系专属地物（归属不是 None）不进公共的：哪个群系要，就在 BY_BIOME[群系][这一步] 里写上（前面先抄 COMMON_BY_STEP[这一步]）
COMMON_BY_STEP = {step: [number for number, _, owner in entries if owner is None]
                  for step, entries in MASTER_BY_STEP.items() if step != VEGETATION_STEP}

# 各群系每一步取哪几个编号（按主序列的先后写）
CORRUPTED = [1, 2, 3, 4, 6, 7, 8, 10]
# 13d-H1b 的编号（batch13.md §6.1、HS §2.1.3）：第 2 步 101 霜髓冰刺、102 榍石露头、103 黑曜残柱、104 残骸堆、105 冻住的残骸堆、106 沉没残骸；
# 第 4 步 101 中景观；第 6 步 101 渊盐结壳、102 焦痕地面；第 9 步 101 枯树、102 地脉喷口、103 稀疏地脉喷口、104 绯红菌丛、105 潮晶床、106 辐晶簇、107 骨堆、
# 108 影晶簇、109 / 110 原版海草 / 海带、111 里程碑。
# 冰冻腐化之地的残骸堆用「冻住的」那一种（105）代替通用的（104），其余五个陆地群系用 104。
LOCAL = 2
SURFACE = 4
ORES = 6
TOP = 10
BY_BIOME = {
    'frozen_corruption': {LOCAL: COMMON_BY_STEP[LOCAL] + [101, 105], SURFACE: [101],
                          VEGETATION_STEP: CORRUPTED + [103, 111]},
    'magic_corruption': {LOCAL: COMMON_BY_STEP[LOCAL] + [104],
                         VEGETATION_STEP: CORRUPTED + [12, 103, 105, 111]},
    'chaos_corruption': {LOCAL: COMMON_BY_STEP[LOCAL] + [104], SURFACE: [101],
                         VEGETATION_STEP: [1, 2, 101, 3, 4, 6, 7, 8, 10, 102, 104, 111]},
    'dreadlands': {LOCAL: COMMON_BY_STEP[LOCAL] + [104], SURFACE: [101],
                   ORES: COMMON_BY_STEP[ORES] + [102], VEGETATION_STEP: [1, 101, 5, 6, 7, 11, 103, 106, 107, 111]},
    'hoanna_desert': {LOCAL: COMMON_BY_STEP[LOCAL] + [102, 104],
                      ORES: COMMON_BY_STEP[ORES] + [102], VEGETATION_STEP: [1, 5, 6, 7, 9, 11, 103, 106, 111]},
    'shadow_corruption': {LOCAL: COMMON_BY_STEP[LOCAL] + [103, 104],
                          VEGETATION_STEP: CORRUPTED + [103, 108, 111]},
    'hoanna_sea': {LOCAL: COMMON_BY_STEP[LOCAL] + [106], ORES: COMMON_BY_STEP[ORES] + [101],
                   VEGETATION_STEP: [1, 6, 7, 109, 110]},
}

# 可以放进群系的 EC 地物 = 本文件写出了 configured / placed feature 的（ec_features）；主序列里归属不是 None 的，不在这里就先不放
EC_READY = {MOD + name for name in ('hoanna_trees', 'green_flower_patch', 'hoanna_grass_patch', 'dread_cactus_patch', 'magical_fruit_patch',
                                    'frost_marrow_spikes', 'titanite_outcrop', 'obsidian_stub', 'ruin_debris', 'frozen_debris',
                                    'sunken_debris', 'hoanna_midscape', 'abyssal_salt_disk', 'dead_tree', 'hoanna_ley_vent',
                                    'hoanna_ley_vent_sparse', 'hoanna_crimson_growth', 'tide_bud_bed', 'radiant_cluster_hotspot',
                                    'bone_pile', 'umbral_cluster_patch', 'hoanna_milestone', 'scorched_ground')}

# ---------------------------------------------------------------- 追加给 7 个群系的地物（forge:add_features）
EC_ORES = ['elemental_infused_ore', 'fire_infused_ore', 'water_infused_ore', 'earth_infused_ore', 'air_infused_ore', 'mithriline_ore']


def feature_modifiers():
    """biome_modifier 名 → 内容（地物那几个；风法师的刷怪在 hoanna_spawns.spawn_modifiers）"""
    tag = '#' + MOD + 'is_hoanna'
    return {
        'hoanna_ores': {'type': 'forge:add_features', 'biomes': tag, 'features': [MOD + ore for ore in EC_ORES], 'step': 'underground_ores'},
        'hoanna_elder_mrucu': {'type': 'forge:add_features', 'biomes': tag, 'features': MOD + 'elder_mrucu_hoanna',
                               'step': 'underground_structures'},
    }


STEP_NAMES = ['raw_generation', 'lakes', 'local_modifications', 'underground_structures', 'surface_structures', 'strongholds',
              'underground_ores', 'underground_decoration', 'fluid_springs', 'vegetal_decoration', 'top_layer_modification']


def modifier_tail_by_step():
    """每一步末尾由 biome_modifier 追加的地物（7 个群系一样），按 feature_modifiers 的顺序"""
    tail = {step: [] for step in range(STEP_COUNT)}
    for body in feature_modifiers().values():
        features = body['features']
        tail[STEP_NAMES.index(body['step'])].extend(features if isinstance(features, list) else [features])
    return tail


# ---------------------------------------------------------------- EC 地物（M1，batch6a.md §5.1「地物」，D11 A / D12 A）
# 数值改了要同步 gametest/HoannaNatureTests 里的对照（稀有度、次数）。方块状态按注册名写，方块由 registry/ECHoannaNature 注册。
GRASS_RARITY = 2        # 草丛：约每 2 区块一簇（D11 A）
FLOWER_RARITY = 8       # 绿花：约每 8 区块一簇
CACTUS_RARITY = 2       # 恐惧仙人掌：沙漠里约每 2 区块一丛（原作 cactiPerChunk 10 是每区块 10 次尝试，量级相当）
FRUIT_RARITY = 24       # 魔力果实块：魔力腐化之地约每 24 区块一处（6a 定 48，第十三批 batch13.md §8.2 / HS D16 改 24：海纳尔没有动物，远征吃不饱）
ELDER_RARITY = 3        # 上古遗迹：D12 A（目标 1/25~1/35 区块，原作 1.12 实测每 27 区块）。普查（种子 20260929，64 × 64）rarity 2 实测每 21.3 区块一座
                        # （1/2 × 落点约 9.4% 过 canGenerateAt）→ 改 3，约每 32 区块一座；新的遗迹正好是旧的里区块掷点 < 1/3 的那些（约 2/3）


def state(block, **properties):
    out = {'Name': block}
    if properties:
        out['Properties'] = {k: str(v) for k, v in properties.items()}
    return out


def simple_block(block_state):
    return {'type': 'minecraft:simple_block', 'config': {'to_place': {'type': 'minecraft:simple_state_provider', 'state': block_state}}}


def only_air():
    return {'type': 'minecraft:matching_blocks', 'blocks': 'minecraft:air'}


def patch(feature_type, tries, xz, y, inner, predicate):
    return {'type': feature_type, 'config': {
        'feature': {'feature': inner, 'placement': [{'type': 'minecraft:block_predicate_filter', 'predicate': predicate}]},
        'tries': tries, 'xz_spread': xz, 'y_spread': y}}


def surface_placement(rarity, heightmap):
    return [{'type': 'minecraft:rarity_filter', 'chance': rarity}, {'type': 'minecraft:in_square'},
            {'type': 'minecraft:heightmap', 'heightmap': heightmap}, {'type': 'minecraft:biome'}]


def ec_features():
    """name → (configured 或 None, placed)。configured 为 None 时 placed 引用别人的 configured feature（PLACED_FEATURE_OF）"""
    cactus = state(MOD + 'dread_cactus', age=0)
    out = {
        # 树：原作 extraTreeChance 0.1（每区块 10% 一棵），橡树 / 大橡树 9 : 1；不带蜂巢（原版 trees_plains 会带出蜜蜂，海纳尔不刷动物）
        'hoanna_trees': (
            {'type': 'minecraft:random_selector', 'config': {
                'features': [{'chance': 0.1, 'feature': 'minecraft:fancy_oak_checked'}],
                'default': 'minecraft:oak_checked'}},
            [{'type': 'minecraft:count', 'count': {'type': 'minecraft:weighted_list', 'distribution': [
                {'data': 0, 'weight': 9}, {'data': 1, 'weight': 1}]}},
             {'type': 'minecraft:in_square'},
             {'type': 'minecraft:surface_water_depth_filter', 'max_water_depth': 0},
             {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR'},
             {'type': 'minecraft:biome'}]),
        # 绿花：type 用 minecraft:flower，草方块用骨粉时 GrassBlock.performBonemeal 会从 getFlowerFeatures 挑到它（海纳尔草方块骨粉长绿花）
        'green_flower_patch': (
            patch('minecraft:flower', 64, 7, 3, simple_block(state(MOD + 'green_flower')), only_air()),
            surface_placement(FLOWER_RARITY, 'MOTION_BLOCKING')),
        # 草丛：代替原版矮草（原版平原每区块 5~10 簇 × 32 次，这里约 0.5 簇 × 64 次）
        'hoanna_grass_patch': (
            patch('minecraft:random_patch', 64, 7, 3, simple_block(state(MOD + 'hoanna_tall_grass')), only_air()),
            surface_placement(GRASS_RARITY, 'WORLD_SURFACE_WG')),
        # 恐惧仙人掌：照原版 PATCH_CACTUS 与原作 WorldGenDreadCacti（10 次、高 1~3）；第二、三格靠 DreadCactusBlock.canSustainPlant 站得住
        'dread_cactus_patch': (
            patch('minecraft:random_patch', 10, 7, 3,
                  {'type': 'minecraft:block_column', 'config': {
                      'direction': 'up', 'allowed_placement': only_air(), 'prioritize_tip': False,
                      'layers': [{'height': {'type': 'minecraft:biased_to_bottom', 'value': {'min_inclusive': 1, 'max_inclusive': 3}},
                                  'provider': {'type': 'minecraft:simple_state_provider', 'state': cactus}}]}},
                  {'type': 'minecraft:all_of', 'predicates': [only_air(), {'type': 'minecraft:would_survive', 'state': cactus}]}),
            surface_placement(CACTUS_RARITY, 'MOTION_BLOCKING')),
        # 魔力果实块：只长在草方块上（D11 A，只挂魔力腐化之地），每处约 2~4 块
        'magical_fruit_patch': (
            patch('minecraft:random_patch', 8, 3, 1, simple_block(state(MOD + 'magical_fruit_block')),
                  {'type': 'minecraft:all_of', 'predicates': [
                      only_air(), {'type': 'minecraft:matching_blocks', 'offset': [0, -1, 0], 'blocks': 'minecraft:grass_block'}]}),
            surface_placement(FRUIT_RARITY, 'MOTION_BLOCKING')),
        # 上古遗迹（海纳尔版）：feature 仍是 essentialcraft5:elder_mrucu，另建 placed feature，不复用主世界那份（主世界带稀有度 16）
        'elder_mrucu_hoanna': (
            None,
            [{'type': 'minecraft:rarity_filter', 'chance': ELDER_RARITY},
             {'type': 'minecraft:in_square'},
             {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform',
                                                            'min_inclusive': {'absolute': 0}, 'max_inclusive': {'absolute': 127}}},
             {'type': 'minecraft:biome'}]),
    }
    out.update(h1b_features())
    return out


PLACED_FEATURE_OF = {'elder_mrucu_hoanna': MOD + 'elder_mrucu', 'hoanna_ley_vent_sparse': MOD + 'hoanna_ley_vent'}


# ---------------------------------------------------------------- 第十三批 13d-H1b：群系特产地物、小景观、中景观、里程碑
# batch13.md §6.1、HS §2.1.3（每区块期望、写方块数）、§2.1.4。数值改了要同步 gametest/HoannaFeaturesTests 的对照表（RARITY_13D）和
# HoannaMidscapeCensusTests；方块按注册名写（预备注册的特产方块、渊盐结壳 / 里程碑、巢穴核心），状态属性名定死：
# ley_vent{charge}、tide_bud{stage}、radiant_cluster / umbral_cluster{facing, waterlogged}、umbral_cluster{ripe}。
LEY_VENT_RARITY = 5            # 混沌：每区块 0.20 处
LEY_VENT_SPARSE_RARITY = 24    # 其余五个陆地群系：0.04
CRIMSON_RARITY = 4             # 混沌：绯红菌丛 0.25 簇
MARROW_SPIKE_RARITY = 14       # 冰冻：0.07 丛
TIDE_BED_RARITY = 12           # 魔力：0.08 床 × 约 4.5 芽
RADIANT_HOTSPOT_BASE = 5       # 辐晶簇只在底数 5 的区块（1/6）
RADIANT_RARITY = 3             # → 每区块 0.056 簇 × 约 3 块
SCORCH_HOTSPOT_BASE = 4        # 焦痕地面在底数 ≥ 4 的区块（1/3），每区块一片
DEAD_TREE_RARITY = 6           # 恐惧之地、混沌：0.17 棵
BONE_PILE_RARITY = 10          # 恐惧之地：0.10 堆
OUTCROP_RARITY = 12            # 沙漠：0.08 块
UMBRAL_RARITY = 4              # 暗影：0.25 簇 × 约 2 块
OBSIDIAN_RARITY = 8            # 暗影：0.13 根
SALT_DISK_RARITY = 3           # 海：0.33 盘 × 约 25 块
SUNKEN_DEBRIS_RARITY = 16      # 海：0.06 堆
RUIN_DEBRIS_RARITY = 24        # 陆地：0.04 堆（冰冻用冻住的那种）
MIDSCAPE_CELL = {'spacing': 14, 'separation': 7, 'salt': 202610261}    # 三种中景观共用（= HoannaMidscape.SPACING / SEPARATION / SALT）
MILESTONE_CELL = {'spacing': 24, 'separation': 12, 'salt': 202610264}  # 里程碑（= HoannaMidscapeCensusTests 的对照）


def rarity_surface(rarity, heightmap='WORLD_SURFACE_WG'):
    return surface_placement(rarity, heightmap)


def random_patch(tries, xz, y, inner, placement):
    return {'type': 'minecraft:random_patch', 'config': {
        'feature': {'feature': inner, 'placement': placement}, 'tries': tries, 'xz_spread': xz, 'y_spread': y}}


def predicate_filter(predicate):
    return {'type': 'minecraft:block_predicate_filter', 'predicate': predicate}


def attached_cluster(block_state):
    return {'type': MOD + 'attached_cluster', 'config': {'to_place': {'type': 'minecraft:simple_state_provider', 'state': block_state}}}


def weighted(*entries):
    return {'type': 'minecraft:weighted_state_provider', 'entries': [{'data': st, 'weight': w} for st, w in entries]}


def air_or_water():
    return {'type': 'minecraft:any_of', 'predicates': [only_air(), {'type': 'minecraft:matching_blocks', 'blocks': 'minecraft:water'}]}


def uniform(lo, hi):
    return {'type': 'minecraft:uniform', 'value': {'min_inclusive': lo, 'max_inclusive': hi}}


def h1b_features():
    soil = ['minecraft:grass_block', 'minecraft:dirt', 'minecraft:coarse_dirt', MOD + 'dread_dirt', 'minecraft:sand']
    return {
        # 地脉喷口：喷口（charge 2）+ 一圈 3 × 3 焦土（LeyVentFeature）；稀疏版引用同一个 configured
        'hoanna_ley_vent': ({'type': MOD + 'ley_vent_patch', 'config': {}}, rarity_surface(LEY_VENT_RARITY)),
        'hoanna_ley_vent_sparse': (None, rarity_surface(LEY_VENT_SPARSE_RARITY)),
        # 绯红菌丛：原版绯红菌根 / 绯红菌（3 : 1），都能长在草方块（#dirt）上
        'hoanna_crimson_growth': (
            random_patch(24, 5, 2, {'type': 'minecraft:simple_block', 'config': {'to_place': weighted(
                (state('minecraft:crimson_roots'), 3), (state('minecraft:crimson_fungus'), 1))}}, [predicate_filter(only_air())]),
            rarity_surface(CRIMSON_RARITY, 'MOTION_BLOCKING')),
        # 霜髓冰刺：浮冰刺 3~6 根、最高 9 格，嵌 2~5 块霜髓（MarrowSpikeFeature）
        'frost_marrow_spikes': ({'type': MOD + 'marrow_spike', 'config': {}}, rarity_surface(MARROW_SPIKE_RARITY)),
        # 冻住的残骸堆（冰冻）/ 残骸堆（其余陆地）/ 沉没残骸（海底，含水）：DebrisPileFeature
        'frozen_debris': ({'type': MOD + 'debris_pile', 'config': {'style': 'frozen'}}, rarity_surface(RUIN_DEBRIS_RARITY)),
        'ruin_debris': ({'type': MOD + 'debris_pile', 'config': {'style': 'ruin'}}, rarity_surface(RUIN_DEBRIS_RARITY)),
        'sunken_debris': ({'type': MOD + 'debris_pile', 'config': {'style': 'sunken'}}, rarity_surface(SUNKEN_DEBRIS_RARITY, 'OCEAN_FLOOR_WG')),
        # 潮晶床：5 次、水平 3 格；每次落到这一列挡路的最高处（头顶露天），脚下要是草方块或石头；阶段随机 0~3。
        # 每次都落在地表，所以几次就差不多是几个不同的列：5 次去重约 4.7 列，扣掉被草丛、树叶挡住的约 4.3，贴 HS §2.1.3 的每床约 4.5 芽
        'tide_bud_bed': (
            random_patch(5, 3, 1, {'type': 'minecraft:simple_block', 'config': {'to_place': {
                'type': 'minecraft:randomized_int_state_provider', 'property': 'stage', 'values': uniform(0, 3),
                'source': {'type': 'minecraft:simple_state_provider', 'state': state(MOD + 'tide_bud', stage=0)}}}},
                [{'type': 'minecraft:heightmap', 'heightmap': 'MOTION_BLOCKING'},
                 predicate_filter({'type': 'minecraft:all_of', 'predicates': [
                     only_air(),
                     {'type': 'minecraft:matching_blocks', 'offset': [0, -1, 0], 'blocks': ['minecraft:grass_block', 'minecraft:stone']}]})]),
            rarity_surface(TIDE_BED_RARITY, 'MOTION_BLOCKING')),
        # 辐晶簇：只在辐射底数 5 的区块；14 次，贴地或贴坡面 / 洞壁（AttachedClusterFeature 按附着面定朝向）。
        # y 偏移 ±3 只有约 1/4 正好落在地表那一格（落进土里、悬空没附着面的都放不下），实测每次约 0.21 块，14 次约 3 块（HS §2.1.3）
        'radiant_cluster_hotspot': (
            random_patch(14, 4, 3, attached_cluster(state(MOD + 'radiant_cluster', facing='up', waterlogged='false')),
                         [predicate_filter(air_or_water())]),
            [{'type': MOD + 'radiation_hotspot', 'min_base': RADIANT_HOTSPOT_BASE}] + rarity_surface(RADIANT_RARITY, 'MOTION_BLOCKING')),
        # 焦痕地面：底数 ≥ 4 的区块每块一片，地表（恐惧泥土、沙、泥土类）换成粗泥 / 灵魂土（3 : 1），半径 2~4
        'scorched_ground': (
            {'type': 'minecraft:disk', 'config': {
                'state_provider': {'fallback': weighted((state('minecraft:coarse_dirt'), 3), (state('minecraft:soul_soil'), 1)), 'rules': []},
                'target': {'type': 'minecraft:matching_blocks', 'blocks': soil},
                'radius': uniform(2, 4),
                'half_height': 2}},
            [{'type': MOD + 'radiation_hotspot', 'min_base': SCORCH_HOTSPOT_BASE}, {'type': 'minecraft:in_square'},
             {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR_WG'}, {'type': 'minecraft:biome'}]),
        # 枯树：深色橡木原木 / 去皮原木、没有树叶（DeadTreeFeature）
        'dead_tree': ({'type': MOD + 'dead_tree', 'config': {}}, rarity_surface(DEAD_TREE_RARITY)),
        # 骨堆：骨块（朝向随机）8 次、水平 2 格，落在实心地面或别的骨块上
        'bone_pile': (
            random_patch(8, 2, 1, {'type': 'minecraft:simple_block', 'config': {'to_place': {
                'type': 'minecraft:rotated_block_provider', 'state': state('minecraft:bone_block', axis='y')}}},
                [predicate_filter({'type': 'minecraft:all_of', 'predicates': [
                    only_air(), {'type': 'minecraft:solid', 'offset': [0, -1, 0]}]})]),
            rarity_surface(BONE_PILE_RARITY, 'MOTION_BLOCKING')),
        # 榍石露头：砂岩 / 平滑砂岩巨石，表层嵌 4~8 块榍石矿（OutcropFeature）
        'titanite_outcrop': ({'type': MOD + 'outcrop', 'config': {}}, rarity_surface(OUTCROP_RARITY)),
        # 影晶簇：6 次，成熟（ripe = true），贴附面定朝向
        'umbral_cluster_patch': (
            random_patch(6, 4, 2, attached_cluster(state(MOD + 'umbral_cluster', facing='up', waterlogged='false', ripe='true')),
                         [predicate_filter(air_or_water())]),
            rarity_surface(UMBRAL_RARITY, 'MOTION_BLOCKING')),
        # 黑曜残柱：黑曜石 / 哭泣黑曜石（3 : 1）2~5 格，脚下要实心
        'obsidian_stub': (
            {'type': 'minecraft:block_column', 'config': {
                'direction': 'up', 'allowed_placement': only_air(), 'prioritize_tip': False,
                'layers': [{'height': uniform(2, 5),
                            'provider': weighted((state('minecraft:obsidian'), 3), (state('minecraft:crying_obsidian'), 1))}]}},
            rarity_surface(OBSIDIAN_RARITY) + [predicate_filter({'type': 'minecraft:solid', 'offset': [0, -1, 0]})]),
        # 渊盐结壳：海底圆盘（同原版 disk_clay），换掉沙 / 沙砾，半径 2~4
        'abyssal_salt_disk': (
            {'type': 'minecraft:disk', 'config': {
                'state_provider': {'fallback': {'type': 'minecraft:simple_state_provider', 'state': state(MOD + 'abyssal_salt_crust')},
                                   'rules': []},
                'target': {'type': 'minecraft:matching_blocks', 'blocks': ['minecraft:sand', 'minecraft:gravel']},
                'radius': uniform(2, 4),
                'half_height': 1}},
            [{'type': 'minecraft:rarity_filter', 'chance': SALT_DISK_RARITY}, {'type': 'minecraft:in_square'},
             {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR_WG'},
             predicate_filter({'type': 'minecraft:matching_fluids', 'fluids': 'minecraft:water'}),
             {'type': 'minecraft:biome'}]),
        # 中景观：三种共用一套格子，挑中的区块按中心群系定种类（MidscapeFeature / HoannaMidscape）
        'hoanna_midscape': ({'type': MOD + 'midscape', 'config': {}},
                            [dict({'type': MOD + 'cell_spread'}, **MIDSCAPE_CELL)]),
        # 里程碑：3 格强化石 + 顶上 ruin_milestone（MilestoneFeature）；柱脚取挡路的最高处，脚下实心、不是树
        'hoanna_milestone': ({'type': MOD + 'milestone', 'config': {}},
                             [dict({'type': MOD + 'cell_spread'}, **MILESTONE_CELL), {'type': 'minecraft:in_square'},
                              {'type': 'minecraft:heightmap', 'heightmap': 'MOTION_BLOCKING'}, {'type': 'minecraft:biome'}]),
    }
