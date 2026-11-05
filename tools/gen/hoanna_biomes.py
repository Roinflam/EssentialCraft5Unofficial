#!/usr/bin/env python3
"""
生成海纳尔的 7 个群系、维度（multi_noise 参数表）、群系标签和群系名键（第六批 6a；框架 F6a 写第一版，M1「群系与海纳尔植物」接手）：

    python tools/gen/hoanna_biomes.py          # 工程根目录下跑；改了表就重跑，结果和脚本一起提交

输出（都是静态 JSON，不走 datagen；别手改，改这里的表）：
  src/main/resources/data/essentialcraft5/worldgen/biome/<群系>.json            ×7
  src/main/resources/data/essentialcraft5/dimension/hoanna.json                 维度：类型 essentialcraft5:hoanna、噪声设置 essentialcraft5:hoanna（runData 生成）、multi_noise
  src/main/resources/data/essentialcraft5/tags/worldgen/biome/is_hoanna.json    7 个
  src/main/resources/data/essentialcraft5/tags/worldgen/biome/hoanna_land.json  6 个（不含海）
  src/main/resources/data/minecraft/tags/worldgen/biome/snow_golem_melts.json   恐惧之地、海纳尔沙漠（replace false，追加到原版标签）
  src/main/resources/data/essentialcraft5/worldgen/configured_feature/<地物>.json、placed_feature/<地物>.json
      海纳尔的植被地物（M1，D11 A：树、草丛、绿花、恐惧仙人掌、魔力果实块）+ placed feature elder_mrucu_hoanna（D12 A）
  src/main/resources/data/essentialcraft5/worldgen/configured_carver/hoanna/placeholder_{0,1,2}.json
      群系 carver 列表前面的 3 个占位（不开挖，只为把真正的洞穴、峡谷的随机种子和主世界错开，见 CARVER_PLACEHOLDERS）
  src/main/resources/data/essentialcraft5/forge/biome_modifier/hoanna_ores.json、hoanna_elder_mrucu.json、wind_mage_spawns_hoanna.json
      主世界 6 种 EC 矿、上古遗迹、风法师进海纳尔（biomes = #essentialcraft5:is_hoanna；和维度同批上线，否则已探索的区块永远没有矿和遗迹）
  tools/lang/keys/echoanna.json 里的 biome.essentialcraft5.<群系>（只改这几个键，文件里别的键原样保留）
维度类型 dimension_type/hoanna.json 是手写的静态文件，不归这个脚本。
改了 PALETTE 要同步 gametest/HoannaNatureTests.PALETTE；改了 MULTI_NOISE、稀有度要重跑 HoannaNatureTests（普查、地物数值都在那里核）。

第十三批 13d 拆成三个文件（batch13.md §6.1、§6.5、§9.4）：本文件管群系本身（配色、粒子、气候、carver、multi_noise、群系标签、名字键）和写盘；
  tools/gen/hoanna_features.py  地物：每一步一份主序列 MASTER_BY_STEP、各群系每一步取哪几个、EC 地物定义、追加地物的 biome_modifier；
  tools/gen/hoanna_spawns.py    刷怪：各群系的怪物表 MONSTERS_BY_BIOME、spawn_costs、追加刷怪的 biome_modifier。
加地物、加怪改那两个文件（各自文件头写了怎么加），再重跑本脚本；拆分本身不改任何输出（拆前拆后生成的 JSON 逐字节相同）。
写盘前 check_feature_order() 断言：每个群系每一步的列表都是这一步主序列的子序列、biome_modifier 追加的地物不在主序列里。

依据（tools/plan/batch6a.md）：
  - 配色 D3 A「暗天 + 暖尘地平线」（§3.2 表 A；大面积颜色 CIELAB 彩度 ≤ 26、雾 L* 高于天 L*，脚本末尾自检并打印）。
    天空模块合入后先拍截图看效果，调色只改 PALETTE 重跑，不碰代码。
  - 群系粒子（§3.2 已定）、气候（§5.1）、刷怪表 = 原版平原的怪物表（7 个相同，沙漠不加尸壳），其余类别全空（不刷被动生物，policy §4.4）。
  - 地物（§5.1）：11 步，照 1.20.1 原版平原的地下部分（OverworldBiomes.plains + BiomeDefaultFeatures，min_y −64 所以
    monster_room_deep、ore_gold_lower、ore_tuff 都在）；每一步按这一步的主序列（hoanna_features.MASTER_BY_STEP）取子序列——
    **每个群系只能从主序列里按顺序挑**，否则同一维度里两个群系的地物顺序矛盾，启动即崩「Feature order cycle」。
    主序列里 EC 自己的地物（归属不是 None）由 hoanna_features.ec_features() 写出 configured / placed feature，写好的才放进群系（EC_READY）。
    biome_modifier 加的 EC 矿、遗迹、风法师对 7 个群系一样（都追加在各步末尾），不影响顺序。
  - multi_noise（§6.1 第 5 条）：温度从冷到热分五段（冰冻 → 魔力 → 混沌 → 恐惧之地 → 沙漠，色相依次过渡、边界不刺眼），
    湿度高的一角是暗影腐化之地（目标 1~3%），大陆度 < −0.19 是海纳尔之海；侵蚀 / 奇异度全范围，河流用当地群系。
    目标占比（M1 的普查测试 HoannaNatureTests.hoannaBiomeCensus 守）：陆地五个主群系各 17~23%、暗影 1~3%。
    【M1 实测改】F6a 第一版照原版的温度分段（−0.45 / −0.15 / 0.2 / 0.55）和湿度 0.7 画，按真噪声普查（tools/gen/hoanna_climate/：
    照 RandomState 的种子派生、shifted_noise 公式，用原版 PerlinNoise 复算温度 / 湿度 / 大陆度，不起游戏）：沙漠只有 4~13%、混沌 30~37%、暗影 0.1~0.3%——
    原版的温度分段本来就不是等分。改成陆地上温度的五分位点（6 个种子 × 131 072² 格合计 157 万点：−0.326 / −0.098 / 0.102 / 0.331，取两位小数）
    和湿度的 98% 分位点 0.5（暗影约 2.1%）；复算：各种子 131 072² 格五个主群系 17.5~21.4%、暗影 1.7~2.6%，海约 30%。
    温度噪声一个起伏约 4 000 格，普查窗口小了占比会随种子乱跳（16 384² 格窗口 12~30%），所以 GameTest 用 65 536² 格、步长 512。
  - 群系标签只挂 essentialcraft5:*，**不进任何 #minecraft:is_* / #forge:is_***（否则主世界整套 biome_modifier、结构都会灌进来）。
群系名的中英文按 tools/lang/glossary.md（原作英文名在 BiomeRegistry L27~34：X Corrupted Land、Hoanna Dreadlands、Hoanna Desert）。
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'essentialcraft5')
DATA_MC = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'minecraft')
LANG = os.path.join(ROOT, 'tools', 'lang', 'keys', 'echoanna.json')
MOD = 'essentialcraft5:'

sys.path.insert(0, HERE)
import hoanna_features as features  # noqa: E402
import hoanna_spawns as spawns  # noqa: E402

# 顺序 = 维度 JSON 里 multi_noise 的顺序 = ECHoanna.BIOMES
BIOMES = ['frozen_corruption', 'magic_corruption', 'chaos_corruption', 'dreadlands', 'hoanna_desert', 'shadow_corruption', 'hoanna_sea']
LAND = [b for b in BIOMES if b != 'hoanna_sea']

NAMES = {
    'frozen_corruption': ('Frozen Corrupted Land', '冰冻腐化之地'),
    'magic_corruption': ('Magic Corrupted Land', '魔力腐化之地'),
    'chaos_corruption': ('Chaos Corrupted Land', '混沌腐化之地'),
    'dreadlands': ('Dreadlands', '恐惧之地'),
    'hoanna_desert': ('Hoanna Desert', '海纳尔沙漠'),
    'shadow_corruption': ('Shadow Corrupted Land', '暗影腐化之地'),
    'hoanna_sea': ('Hoanna Sea', '海纳尔之海'),
}

# D3 A：sky、fog、water、water_fog、grass（= foliage）
PALETTE = {
    'dreadlands':        ('#3B4260', '#857B73', '#3D4A5C', '#1F2630', '#7F8A7C'),
    'hoanna_desert':     ('#474560', '#A08A6C', '#4A4A34', '#2A2818', '#A8906A'),
    'magic_corruption':  ('#3C3A5C', '#7C6A8C', '#45385F', '#221C30', '#86688C'),
    'frozen_corruption': ('#37465E', '#7F8FA3', '#3A5878', '#16263A', '#5F7FA8'),
    'chaos_corruption':  ('#43384E', '#85655C', '#3A1A1A', '#1E1010', '#6E4440'),
    'shadow_corruption': ('#2A2A36', '#4A4852', '#1E2230', '#0E1016', '#3A3A3E'),
    'hoanna_sea':        ('#3B4260', '#7E8A94', '#35505E', '#132028', '#707C70'),
}

# 群系粒子（§3.2；恐惧之地、沙漠、海不加）：群系 → (粒子 options, 概率)。客户端每刻在身边随机抽 1 334 格（ClientLevel.animateTick），
# 抽到的不是整格方块就按这个概率冒一颗。改了要同步 gametest/HoannaNatureTests.PARTICLES。
# 魔力腐化之地原来用 minecraft:witch：女巫身边冒的那种品红闪点（SpellParticle.WitchProvider：颜色 (1, 0, 1) × 0.35~0.85，彩度远超护眼线），
# 满地飘着既扎眼、又像附近有女巫。改成 EC 的淡紫 #9684BE（(0.59, 0.52, 0.75)）小尘粒，个头 0.6、概率降到 0.002，
# 和冰冻的雪花、暗影的灰烬一样只是飘着的细尘（原版尘粒还会把颜色随机压暗到 0.6~1 倍）。
PARTICLES = {
    'magic_corruption': ({'type': 'minecraft:dust', 'color': [0.59, 0.52, 0.75], 'scale': 0.6}, 0.002),
    'chaos_corruption': ({'type': 'minecraft:crimson_spore'}, 0.003),
    'frozen_corruption': ({'type': 'minecraft:snowflake'}, 0.003),
    'shadow_corruption': ({'type': 'minecraft:ash'}, 0.006),
}

# 气候（§5.1；冰冻腐化之地照原作 0.5：下雨不下雪）
WET = {'has_precipitation': True, 'temperature': 0.5, 'downfall': 0.5}
DRY = {'has_precipitation': False, 'temperature': 2.0, 'downfall': 0.0}
CLIMATE = {b: (DRY if b in ('dreadlands', 'hoanna_desert') else WET) for b in BIOMES}

CARVERS = ['minecraft:cave', 'minecraft:cave_extra_underground', 'minecraft:canyon']

# 雕刻器（老式洞穴、峡谷）不走噪声：NoiseBasedChunkGenerator.applyCarvers 给第 l 个 carver 的随机种子是
# setLargeFeatureSeed(世界种子 + l, 区块 x, z)，和 carver 的 id、所在维度都无关。所有维度共用世界种子，主世界群系的列表也是
# [cave, cave_extra_underground, canyon]，照抄的话海纳尔的洞穴、峡谷会和主世界同坐标一模一样（噪声洞穴已随 HoannaTerrain 的副本换掉，这里是另一半）。
# 所以列表前面垫 3 个不开挖的占位，真正的三种排到 3~5 号，种子就和主世界的 0~2 号错开了。
# 占位照抄 minecraft:cave 的配置（1.20.1 Carvers.bootstrap / data/minecraft/worldgen/configured_carver/cave.json），只把 probability 改成 0：
# isStartChunk 是 nextFloat() <= probability，只有 nextFloat 恰好为 0（2^-24）时才会动手，可以不计。
# 占位的个数、真 carver 的顺序一改，新生成区块的洞穴就和已生成的区块接不上；以后增减 carver 只往 CARVERS 末尾加，占位留着别动。
# GameTest HoannaFrameworkTests.hoannaTerrainNotOverworld 查：前 3 个是 probability 0 的占位、真 carver 的序号 ≥ 3、同一序号上没有和主世界平原相同的 carver。
CARVER_PLACEHOLDERS = [f'hoanna/placeholder_{i}' for i in range(3)]
PLACEHOLDER_CARVER = {
    'type': 'minecraft:cave',
    'config': {
        'debug_settings': {
            'air_state': {'Name': 'minecraft:crimson_button', 'Properties': {'face': 'wall', 'facing': 'north', 'powered': 'false'}},
            'barrier_state': {'Name': 'minecraft:glass'},
            'lava_state': {'Name': 'minecraft:orange_stained_glass'},
            'water_state': {'Name': 'minecraft:candle', 'Properties': {'candles': '1', 'lit': 'false', 'waterlogged': 'false'}},
        },
        'floor_level': {'type': 'minecraft:uniform', 'value': {'max_exclusive': -0.4, 'min_inclusive': -1.0}},
        'horizontal_radius_multiplier': {'type': 'minecraft:uniform', 'value': {'max_exclusive': 1.4, 'min_inclusive': 0.7}},
        'lava_level': {'above_bottom': 8},
        'probability': 0.0,
        'replaceable': '#minecraft:overworld_carver_replaceables',
        'vertical_radius_multiplier': {'type': 'minecraft:uniform', 'value': {'max_exclusive': 1.3, 'min_inclusive': 0.8}},
        'y': {'type': 'minecraft:uniform', 'max_inclusive': {'absolute': 180}, 'min_inclusive': {'above_bottom': 8}},
        'yScale': {'type': 'minecraft:uniform', 'value': {'max_exclusive': 0.9, 'min_inclusive': 0.1}},
    },
}

# multi_noise 参数（§6.1 第 5 条）：temperature、humidity、continentalness；erosion / weirdness 全范围、depth [0, 1]、offset 0
# 分界见文件头「M1 实测改」：温度取陆地五分位点，湿度 0.5 以上是暗影（约 2%）。改了要同步 HoannaNatureTests 的普查阈值说明
LAND_C = [-0.19, 1.0]
T_BOUNDS = [-0.33, -0.1, 0.1, 0.33]
SHADOW_HUMIDITY = 0.5
MAIN_H = [-1.0, SHADOW_HUMIDITY]
MULTI_NOISE = {
    'frozen_corruption': ([-1.0, T_BOUNDS[0]], MAIN_H, LAND_C),
    'magic_corruption':  ([T_BOUNDS[0], T_BOUNDS[1]], MAIN_H, LAND_C),
    'chaos_corruption':  ([T_BOUNDS[1], T_BOUNDS[2]], MAIN_H, LAND_C),
    'dreadlands':        ([T_BOUNDS[2], T_BOUNDS[3]], MAIN_H, LAND_C),
    'hoanna_desert':     ([T_BOUNDS[3], 1.0], MAIN_H, LAND_C),
    'shadow_corruption': ([-1.0, 1.0], [SHADOW_HUMIDITY, 1.0], LAND_C),
    'hoanna_sea':        ([-1.0, 1.0], [-1.0, 1.0], [-1.2, -0.19]),
}

SNOW_GOLEM_MELTS = ['dreadlands', 'hoanna_desert']


def color(hex_code):
    return int(hex_code.lstrip('#'), 16)



def biome_steps(biome):
    """这个群系每一步的地物 id（不含 biome_modifier 追加的）：按 BY_BIOME / COMMON_BY_STEP 的编号从主序列取，没写好的 EC 地物跳过"""
    chosen = features.BY_BIOME.get(biome, {})
    out = []
    for step in range(features.STEP_COUNT):
        wanted = chosen.get(step, features.COMMON_BY_STEP.get(step, []))
        master = features.MASTER_BY_STEP[step]
        position = {number: i for i, (number, _, _) in enumerate(master)}
        missing = [n for n in wanted if n not in position]
        assert not missing, f'{biome} 第 {step} 步的编号 {missing} 不在这一步的主序列里'
        order = [position[n] for n in wanted]
        assert order == sorted(order) and len(set(order)) == len(order), \
            f'{biome} 第 {step} 步的编号 {wanted} 要按主序列的先后写、不重复（主序列的子序列）'
        out.append([feature for number, feature, owner in master
                    if number in wanted and (owner is None or feature in features.EC_READY)])
    return out


def check_feature_order():
    """
    断言 7 个群系（加上 biome_modifier 追加的地物以后）每一步都是「主序列 + 追加尾巴」的子序列，所以 FeatureSorter 排得出来；
    编号在每一步里唯一；追加的地物不在主序列里（否则同一地物在一个群系里出现两次）；同一个地物不出现在两步里。
    """
    tail = features.modifier_tail_by_step()
    seen_step = {}
    for step in range(features.STEP_COUNT):
        master = features.MASTER_BY_STEP[step]
        numbers = [number for number, _, _ in master]
        assert len(set(numbers)) == len(numbers), f'第 {step} 步主序列的编号重复：{numbers}'
        ids = [feature for _, feature, _ in master]
        assert len(set(ids)) == len(ids), f'第 {step} 步主序列里地物重复：{ids}'
        clash = sorted(set(ids) & set(tail[step]))
        assert not clash, f'第 {step} 步：{clash} 由 biome_modifier 追加，不能再写进主序列'
        for feature in ids + tail[step]:
            assert seen_step.setdefault(feature, step) == step, f'{feature} 同时出现在第 {seen_step[feature]} 步和第 {step} 步'
    full_master = {step: [f for _, f, _ in features.MASTER_BY_STEP[step]] + tail[step] for step in range(features.STEP_COUNT)}
    for biome in BIOMES:
        for step, listed in enumerate(biome_steps(biome)):
            full = listed + tail[step]
            index = [full_master[step].index(f) for f in full]
            assert index == sorted(index), f'{biome} 第 {step} 步不是主序列的子序列：{full}'


def biome_json(biome):
    sky, fog, water, water_fog, grass = PALETTE[biome]
    effects = {
        'sky_color': color(sky),
        'fog_color': color(fog),
        'water_color': color(water),
        'water_fog_color': color(water_fog),
        'grass_color': color(grass),
        'foliage_color': color(grass),
        'mood_sound': {'sound': 'minecraft:ambient.cave', 'tick_delay': 6000, 'block_search_extent': 8, 'offset': 2.0},
    }
    if biome in PARTICLES:
        options, probability = PARTICLES[biome]
        effects['particle'] = {'options': dict(options), 'probability': probability}
    out = dict(CLIMATE[biome])
    out['effects'] = effects
    out['spawners'] = spawns.spawners(biome)
    out['spawn_costs'] = spawns.spawn_costs(biome)
    out['carvers'] = {'air': [MOD + name for name in CARVER_PLACEHOLDERS] + list(CARVERS)}
    out['features'] = biome_steps(biome)
    return out


def dimension_json():
    entries = []
    for biome in BIOMES:
        temperature, humidity, continentalness = MULTI_NOISE[biome]
        entries.append({
            'biome': MOD + biome,
            'parameters': {
                'temperature': temperature,
                'humidity': humidity,
                'continentalness': continentalness,
                'erosion': [-1.0, 1.0],
                'weirdness': [-1.0, 1.0],
                'depth': [0.0, 1.0],
                'offset': 0.0,
            },
        })
    return {
        'type': MOD + 'hoanna',
        'generator': {
            'type': 'minecraft:noise',
            'settings': MOD + 'hoanna',
            'biome_source': {'type': 'minecraft:multi_noise', 'biomes': entries},
        },
    }


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write('\n')
    with open(path, encoding='utf-8') as f:
        json.load(f)


def update_lang():
    keys = {}
    if os.path.exists(LANG):
        with open(LANG, encoding='utf-8') as f:
            keys = json.load(f)
    for biome in BIOMES:
        en, zh = NAMES[biome]
        keys['biome.essentialcraft5.' + biome] = {'en': en, 'zh': zh}
    with open(LANG, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(keys, f, indent=1, ensure_ascii=False)
        f.write('\n')


# ---------------------------------------------------------------- 自检：CIELAB（sRGB → XYZ D65 → Lab）

def lab(hex_code):
    rgb = [int(hex_code[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    lin = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in rgb]
    x = (0.4124 * lin[0] + 0.3576 * lin[1] + 0.1805 * lin[2]) / 0.95047
    y = 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
    z = (0.0193 * lin[0] + 0.1192 * lin[1] + 0.9505 * lin[2]) / 1.08883

    def f(t):
        return t ** (1 / 3) if t > 216 / 24389 else (24389 / 27 * t + 16) / 116

    fx, fy, fz = f(x), f(y), f(z)
    return 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)


def check_palette():
    problems = []
    print('群系                 天L*  雾L*  最高彩度')
    for biome in BIOMES:
        values = PALETTE[biome]
        chroma = max(math.hypot(*lab(v)[1:]) for v in values)
        sky_l, fog_l = lab(values[0])[0], lab(values[1])[0]
        print(f'{biome:20s} {sky_l:5.1f} {fog_l:5.1f} {chroma:6.1f}')
        if chroma > 26.05:
            problems.append(f'{biome} 有大面积颜色彩度 {chroma:.1f} > 26（护眼规则 batch6a.md §3.1 第 1 条）')
        if fog_l <= sky_l:
            problems.append(f'{biome} 雾 L* {fog_l:.1f} 不高于天 L* {sky_l:.1f}（地平线应比天顶亮，§3.1 第 5 条）')
    return problems



def write_features():
    defined = features.ec_features()
    ec_owned = {feature for step in features.MASTER_BY_STEP.values() for _, feature, owner in step if owner is not None}
    missing = sorted(f for f in features.EC_READY if f[len(MOD):] not in defined)
    assert not missing, f'EC_READY 里的 EC 地物没有定义：{missing}'
    unknown = sorted(f for f in features.EC_READY if f not in ec_owned)
    assert not unknown, f'EC_READY 里的地物不在任何一步的主序列里：{unknown}'
    for name, (configured, placement) in defined.items():
        if configured is not None:
            write(os.path.join(DATA, 'worldgen', 'configured_feature', name + '.json'), configured)
        write(os.path.join(DATA, 'worldgen', 'placed_feature', name + '.json'),
              {'feature': features.PLACED_FEATURE_OF.get(name, MOD + name), 'placement': placement})
    modifiers = dict(features.feature_modifiers())
    modifiers.update(spawns.spawn_modifiers())
    for name, body in modifiers.items():
        write(os.path.join(DATA, 'forge', 'biome_modifier', name + '.json'), body)
    write(os.path.join(DATA_MC, 'tags', 'worldgen', 'biome', 'snow_golem_melts.json'),
          {'replace': False, 'values': [MOD + b for b in SNOW_GOLEM_MELTS]})
    for name in CARVER_PLACEHOLDERS:
        write(os.path.join(DATA, 'worldgen', 'configured_carver', *name.split('/')) + '.json', PLACEHOLDER_CARVER)
    return len(defined), len(modifiers)


def main():
    assert spawns.BIOMES == BIOMES, 'hoanna_spawns.BIOMES 要和本文件的 BIOMES 一样'
    assert set(features.BY_BIOME) <= set(BIOMES), 'hoanna_features.BY_BIOME 里有不认识的群系'
    check_feature_order()
    for biome in BIOMES:
        write(os.path.join(DATA, 'worldgen', 'biome', biome + '.json'), biome_json(biome))
    write(os.path.join(DATA, 'dimension', 'hoanna.json'), dimension_json())
    write(os.path.join(DATA, 'tags', 'worldgen', 'biome', 'is_hoanna.json'), {'replace': False, 'values': [MOD + b for b in BIOMES]})
    write(os.path.join(DATA, 'tags', 'worldgen', 'biome', 'hoanna_land.json'), {'replace': False, 'values': [MOD + b for b in LAND]})
    feature_count, modifier_count = write_features()
    update_lang()
    print(f'写了 {len(BIOMES)} 个群系、dimension/hoanna.json、三个群系标签、{feature_count} 个地物、{modifier_count} 个 biome_modifier、'
          f'{len(BIOMES)} 个群系名键')
    problems = check_palette()
    for problem in problems:
        print('!!', problem)
    return 1 if problems else 0


if __name__ == '__main__':
    raise SystemExit(main())
