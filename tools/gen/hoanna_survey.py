#!/usr/bin/env python3
"""
海纳尔 64 × 64 区块普查（第六批 6a，batch6a.md §4.4 第 2、3 条）：

    python tools/gen/hoanna_survey.py                 # 工程根目录、后台跑（要起 Gradle 和本地端口）；约 5~10 分钟（Gradle 配置 + 开服 + 生成 4 096 区块）
    python tools/gen/hoanna_survey.py --analyze       # 不开服，只重新统计已经生成好的存档 run/hoanna_survey_<种子>
    python tools/gen/hoanna_survey.py --center 12000,-4000          # 以方块坐标 (12000, −4000) 为中心（第十三批 13d 加）
    python tools/gen/hoanna_survey.py --find-biome essentialcraft5:hoanna_desert   # 开服后先 /locate biome 找一片沙漠，以那里为中心
    （带 --center / --find-biome 时存档名后面加上中心的区块坐标，例 run/hoanna_survey_20260929_c750_-250；--analyze 带同样的参数）

做的事（开服、改 server.properties、RCON 的写法全部借 hoanna_bench.py，被打断后的还原也共用它的备份文件）：
  1. 新存档 run/hoanna_survey_<种子>（种子同 hoanna_bench / ley_projector 的 20260929），不碰 run/world 和别的存档；存档已经在就只统计。
  2. 海纳尔以 (0, 0) 为中心 forceload 64 × 64 区块（区块 −32~31：正好是城镇 32 格的 2 × 2 格子、矿井 16 格的 4 × 4、墓穴 12 格约 5.3 × 5.3），等全部生成完。
  3. /locate：三种结构各 10 次（从离普查区很远、没生成过的地方找，最坏情况），外加原版主世界村庄 5 次作对照；
     耗时读服务端日志「Locating element … took N ms」（LocateCommand L151）。
     （第十三批 13d：普查区可以换中心，见上面的参数；中心记在存档里的 hoanna_survey_area.json，--analyze 读它。）
  4. save-all flush、stop，然后只读区域文件统计：
     - 结构起点（区块 NBT structures.starts，只数起点区块在普查区里的）、件数；
     - 群系占比（每区块 4 × 4 列，取地表那一格的群系）；
     - 恐惧之地表层：地表群系是恐惧之地的列，往下跳过空气和植物后的第一个方块，恐惧泥土占多少（目标 ≥ 90%）；
     - 上古遗迹：数 MRU 聚集体实体（每座遗迹正好放 1 个，ElderMRUCUFeature.spawnPresence），折算每几个区块一座（目标 25~35）；
     - 方块成分：y −64~63（同地脉投影仪实测的口径），只算自己和周围 8 块都生成完的区块，每 10 万格能投影的方块；
       和 ley_projector/survey_result.json 的主世界（同种子、25 × 25）并排对比 EC 矿与原版矿；
     - 第十三批 13d 的新方块（EXPECTED_BLOCKS：喷口、榍石矿、辐晶簇、潮晶芽、影晶簇、霜髓、渊盐结壳、里程碑、巢穴核心）：
       按区块的主群系（16 列里最多的那个）分组，每区块几块，对照 batch13.md §6.1 / HS §2.1.3 推算的范围；巢穴核心两两最近距离（区块）。
     - 第十三批 13e：中景观（冰封营地 / 混沌裂隙 / 辐射陨坑的巢穴核心）所在区块和四周 8 块、里程碑所在区块里不该有地表大结构
       （#essentialcraft5:blocks_midscape：城镇、宝库、前哨、沉城）的引用（区块 NBT structures.References）；违反的列进 midscape_conflicts。
     - 第十三批 13e：带 --forecast <json>（{结构 id: [[区块 x, 区块 z], …]}，GameTest hoannaSurveyForecast13e 在日志里打出来的那份）时，
       普查区里实际的起点和预测逐个对照（应完全一致），结果在 forecast_check。
  结果写 tools/gen/hoanna_survey_result.json。
被打断以后同 hoanna_bench.py：下次运行一开头按备份还原 server.properties 然后退出；先查有没有留下的 runServer / Gradle 进程再重跑。
"""
import argparse
import glob
import json
import math
import os
import tempfile
import re
import secrets
import subprocess
import sys
import time
from collections import Counter

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, 'ley_projector'))
import hoanna_bench as hb  # noqa: E402
import blockcount as bc  # noqa: E402

ROOT = hb.ROOT
RUN = hb.RUN
SEED = hb.SEED
WORLD_NAME = 'hoanna_survey_' + SEED
WORLD = os.path.join(RUN, WORLD_NAME)
HOANNA = 'essentialcraft5:hoanna'
REGION = os.path.join(WORLD, 'dimensions', 'essentialcraft5', 'hoanna', 'region')
ENTITIES = os.path.join(WORLD, 'dimensions', 'essentialcraft5', 'hoanna', 'entities')
RADIUS = 32                  # 区块 −32 … 31（相对普查中心）
CENTER = [0, 0]              # 普查中心（区块坐标）；--center / --find-biome 改它
AREA_FILE = 'hoanna_survey_area.json'


def set_world(name):
    """换存档（带中心的普查各用各的存档）"""
    global WORLD_NAME, WORLD, REGION, ENTITIES
    WORLD_NAME = name
    WORLD = os.path.join(RUN, WORLD_NAME)
    REGION = os.path.join(WORLD, 'dimensions', 'essentialcraft5', 'hoanna', 'region')
    ENTITIES = os.path.join(WORLD, 'dimensions', 'essentialcraft5', 'hoanna', 'entities')


def area_box():
    """普查区（区块，含两端）"""
    cx, cz = CENTER
    return (cx - RADIUS, cz - RADIUS, cx + RADIUS - 1, cz + RADIUS - 1)
LOG = os.path.join(tempfile.gettempdir(), 'ec5_6a_mt_survey_server.log')
RESULT = os.path.join(HERE, 'hoanna_survey_result.json')
OVERWORLD_SURVEY = os.path.join(HERE, 'ley_projector', 'survey_result.json')
STRUCTURES = ('essentialcraft5:hoanna_town', 'essentialcraft5:old_catacombs', 'essentialcraft5:modern_shafts',
              'essentialcraft5:sealed_vault', 'essentialcraft5:inquisitorium_outpost', 'essentialcraft5:sunken_district')
EXPECTED = {'essentialcraft5:hoanna_town': (2, 4), 'essentialcraft5:old_catacombs': (21, 35), 'essentialcraft5:modern_shafts': (12, 20),
            # 第十三批 13e：64 × 64 区块里的数随普查中心的群系变很多，只给宽的界（全图密度看 HoannaStructureCensusTests）
            'essentialcraft5:sealed_vault': (0, 3), 'essentialcraft5:inquisitorium_outpost': (0, 6), 'essentialcraft5:sunken_district': (0, 4)}
PRESENCE = 'essentialcraft5:mru_presence'
# 第十三批 13d 的新方块：每区块（按区块的主群系分组）几块的推算范围（batch13.md §6.1、HS §2.1.3；约 ±50%）。
# 喷口：混沌 rarity 5 + 裂隙 4~6 个 / 196 区块，其余陆地 rarity 24；榍石矿：露头 1/12 × 4~8；辐晶簇：底数 5（1/6）× 1/3 × 约 3 块（14 次 × 每次约 0.21 块；恐惧之地另有陨坑 3~5 个 / 392 区块）；
# 潮晶芽：1/12 × 约 4.5（5 次、每次都落地表，去重扣掉被挡的约 4.3 列）；影晶簇：1/4 × 约 2；霜髓：冰刺 1/14 × 2~5 + 营地；渊盐结壳：1/3 × 约 25；里程碑：1/576；巢穴核心：冰冻 / 混沌 1/196、恐惧之地约 1/392。
LAND_BIOMES = ['essentialcraft5:' + b for b in ('frozen_corruption', 'magic_corruption', 'chaos_corruption', 'dreadlands', 'hoanna_desert',
                                               'shadow_corruption')]
EXPECTED_BLOCKS = {
    'essentialcraft5:ley_vent': dict({b: (0.02, 0.063) for b in LAND_BIOMES}, **{'essentialcraft5:chaos_corruption': (0.11, 0.34)}),
    'essentialcraft5:titanite_ore': {'essentialcraft5:hoanna_desert': (0.25, 0.75)},
    'essentialcraft5:radiant_cluster': {'essentialcraft5:dreadlands': (0.09, 0.27), 'essentialcraft5:hoanna_desert': (0.08, 0.25)},
    'essentialcraft5:tide_bud': {'essentialcraft5:magic_corruption': (0.19, 0.56)},
    'essentialcraft5:umbral_cluster': {'essentialcraft5:shadow_corruption': (0.25, 0.75)},
    'essentialcraft5:frost_marrow': {'essentialcraft5:frozen_corruption': (0.13, 0.40)},
    'essentialcraft5:abyssal_salt_crust': {'essentialcraft5:hoanna_sea': (4.0, 12.5)},
    'essentialcraft5:ruin_milestone': {b: (0.0005, 0.003) for b in LAND_BIOMES},
    'essentialcraft5:nest_core': {'essentialcraft5:frozen_corruption': (0.0025, 0.0077), 'essentialcraft5:chaos_corruption': (0.0025, 0.0077),
                                  'essentialcraft5:dreadlands': (0.0012, 0.0038)},
}
# 画位置的（稀少的那几种）：记下每一块的坐标
POSITIONED = ('essentialcraft5:nest_core', 'essentialcraft5:ruin_milestone')
# 挡中景观的结构（tags/worldgen/structure/blocks_midscape.json）和中景观自己的巢穴类型
BLOCKS_MIDSCAPE = ('essentialcraft5:hoanna_town', 'essentialcraft5:sealed_vault', 'essentialcraft5:inquisitorium_outpost',
                   'essentialcraft5:sunken_district')
MIDSCAPE_NESTS = ('essentialcraft5:frozen_camp', 'essentialcraft5:chaos_fissure', 'essentialcraft5:fallout_crater')
# /locate 的起点：离普查区和出生点都很远、四个方向各不相同（格）
LOCATE_FROM = [(20000, 20000), (-20000, 20000), (20000, -20000), (-20000, -20000), (35000, 0),
               (0, 35000), (-35000, 0), (0, -35000), (50000, 50000), (-50000, 12000)]
LOCATE_RE = re.compile(r'Locating element (\S+) took (\d+) ms')

# 地表：往下跳过的「盖在地面上」的方块（植物、雪层等），剩下第一个就是地表材质
COVER_RE = re.compile(r'(^minecraft:(grass|short_grass|tall_grass|fern|large_fern|dead_bush|snow|vine|cactus|sugar_cane)$)'
                      r'|flower|tulip|orchid|allium|bluet|daisy|poppy|dandelion|cornflower|lily|rose|peony|lilac|sunflower'
                      r'|sapling|mushroom|_leaves$|_log$|_wood$|dread_cactus|hoanna_tall_grass|green_flower|magical_fruit|essentialcraft5:root$')
AIR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}


# ---------------------------------------------------------------- 生成

def generate(locate_rounds, find_biome=None):
    if hb.restore_interrupted():
        return False
    for port in (hb.SERVER_PORT, hb.RCON_PORT):
        if not hb.port_free(port):
            print(f'端口 {port} 被占用（上次留下的 runServer？核对命令行后结束它）')
            return False
    hb.shutil.copyfile(hb.PROPERTIES, hb.BACKUP)
    made_eula = not os.path.exists(hb.EULA)
    password = secrets.token_hex(12)
    process = None
    locates = []
    try:
        hb.patch_properties(hb.PROPERTIES, {
            'level-name': WORLD_NAME, 'level-seed': SEED, 'level-type': 'minecraft\\:normal', 'generate-structures': 'true',
            'server-ip': '127.0.0.1', 'server-port': str(hb.SERVER_PORT),
            'enable-rcon': 'true', 'rcon.port': str(hb.RCON_PORT), 'rcon.password': password,
            'max-tick-time': '-1', 'online-mode': 'false', 'spawn-monsters': 'false', 'spawn-animals': 'false',
            'simulation-distance': '2', 'view-distance': '2',
        })
        if made_eula:
            with open(hb.EULA_MARK, 'w', encoding='utf-8') as f:
                f.write('eula.txt 是 tools/gen/hoanna_survey.py 临时写的，运行结束（或下次运行开头）会删掉\n')
            with open(hb.EULA, 'w', encoding='utf-8') as f:
                f.write('eula=true\n')
        env = dict(os.environ)
        env.setdefault('JAVA_TOOL_OPTIONS', '-Djdk.net.unixdomain.tmpdir=' + os.path.join(ROOT, '.gradle', 'uds-tmp').replace(os.sep, '/'))
        log = open(LOG, 'w', encoding='utf-8', errors='replace')
        process = subprocess.Popen(['cmd', '/c', os.path.join(ROOT, 'gradlew.bat'), 'runServer', '--console=plain'], cwd=ROOT, env=env,
                                   stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
        print(f'runServer PID {process.pid}，日志 {LOG}，存档 run/{WORLD_NAME}', flush=True)
        rcon = None
        deadline = time.time() + hb.START_TIMEOUT
        while time.time() < deadline and process.poll() is None:
            try:
                rcon = hb.Rcon(hb.RCON_PORT, password)
                break
            except OSError:
                time.sleep(10)
        if rcon is None:
            print('服务端没起来（看日志）')
            return False
        time.sleep(15)
        if find_biome:
            x0, z0 = CENTER[0] * 16 + 8, CENTER[1] * 16 + 8
            reply = rcon.command(f'execute in {HOANNA} positioned {x0} 64 {z0} run locate biome {find_biome}')
            found = re.search(r'\[(-?\d+), ~?-?\d*,? ?~?(-?\d+)\]', reply)
            if not found:
                print(f'/locate biome {find_biome} 没找到：{reply.strip()}')
                rcon.command('stop')
                return False
            CENTER[0], CENTER[1] = int(found.group(1)) >> 4, int(found.group(2)) >> 4
            print(f'{find_biome} 在方块 ({found.group(1)}, {found.group(2)})，普查中心区块 {CENTER}', flush=True)
        os.makedirs(WORLD, exist_ok=True)
        with open(os.path.join(WORLD, AREA_FILE), 'w', encoding='utf-8') as f:
            json.dump({'center_chunk': CENTER, 'radius': RADIUS, 'find_biome': find_biome}, f)
        box = area_box()
        t0 = time.time()
        for x0, z0, x1, z1 in hb.forceload_bands(*box):
            rcon.command(f'execute in {HOANNA} run forceload add {x0 * 16} {z0 * 16} {x1 * 16 + 15} {z1 * 16 + 15}')
        hb.AREA_TIMEOUT = 40 * 60
        complete = hb.wait_generated(rcon, HOANNA, box)
        print(f'海纳尔 64 × 64：{"生成完" if complete else "超时没生成完"}，{time.time() - t0:.0f} 秒', flush=True)
        time.sleep(10)
        # /locate：日志里读耗时；每条之间留 1 秒，免得和别的日志行搅在一起
        jobs = [(s, HOANNA, x, z) for s in STRUCTURES for (x, z) in LOCATE_FROM[:locate_rounds]]
        jobs += [('minecraft:village_plains', 'minecraft:overworld', x, z) for (x, z) in LOCATE_FROM[:5]]
        for structure, dimension, x, z in jobs:
            t = time.time()
            reply = rcon.command(f'execute in {dimension} positioned {x} 64 {z} run locate structure {structure}')
            locates.append({'structure': structure, 'dimension': dimension, 'from': [x, z], 'reply': reply.strip(),
                            'rcon_seconds': round(time.time() - t, 3)})
            time.sleep(1)
        rcon.command(f'execute in {HOANNA} run forceload remove all')
        rcon.command('save-all flush')
        time.sleep(5)
        rcon.command('stop')
        rcon.close()
        process.wait(timeout=600)
    finally:
        if process is not None and process.poll() is None:
            print('服务端没按时退出，结束进程树')
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'])
        hb.restore_temporary_files()
    # 日志里的耗时按顺序对回去：只认元素名和这条 /locate 的结构一样的那行（带 --find-biome 时开头那次 /locate biome 也打一行，按顺序硬对会整体错开一条）
    with open(LOG, encoding='utf-8', errors='replace') as f:
        timings = LOCATE_RE.findall(f.read())
    pending = iter(timings)
    for job in locates:
        for element, ms in pending:
            if element == job['structure']:
                job['element'] = element
                job['ms'] = int(ms)
                break
    with open(os.path.join(WORLD, 'hoanna_survey_locate.json'), 'w', encoding='utf-8') as f:
        json.dump(locates, f, indent=1, ensure_ascii=False)
    return True


# ---------------------------------------------------------------- 区域文件

def unpack(longs, bits, count):
    per_long = 64 // bits
    longs = np.asarray(longs).astype(np.uint64)
    mask = np.uint64((1 << bits) - 1)
    shifts = np.arange(per_long, dtype=np.uint64) * np.uint64(bits)
    return ((longs[:, None] >> shifts[None, :]) & mask).reshape(-1)[:count].astype(np.int64)


def section_blocks(section):
    """(调色板名字列表, 4096 个下标)，下标顺序 (y × 16 + z) × 16 + x"""
    states = section.get('block_states') or {}
    palette = [bc.block_name(p) for p in (states.get('palette') or [])]
    data = states.get('data')
    if not palette:
        return None, None
    if data is None or len(palette) == 1:
        return palette, np.zeros(4096, dtype=np.int64)
    bits = max(4, math.ceil(math.log2(len(palette))))
    return palette, unpack(data, bits, 4096)


def section_biomes(section):
    """(调色板, 64 个下标)，下标顺序 (y × 4 + z) × 4 + x"""
    biomes = section.get('biomes') or {}
    palette = list(biomes.get('palette') or [])
    data = biomes.get('data')
    if not palette:
        return None, None
    if data is None or len(palette) == 1:
        return palette, np.zeros(64, dtype=np.int64)
    bits = math.ceil(math.log2(len(palette)))
    return palette, unpack(data, bits, 64)


def heightmap(chunk, name):
    maps = chunk.get('Heightmaps') or {}
    data = maps.get(name)
    if data is None:
        return None
    return unpack(data, 9, 256)   # 1.20.1：9 位、每个 long 7 个、不跨 long；值 = y − 最低高度 + 1（最低 −64）


def analyze():
    area_path = os.path.join(WORLD, AREA_FILE)
    if os.path.exists(area_path):
        with open(area_path, encoding='utf-8') as f:
            CENTER[0], CENTER[1] = json.load(f)['center_chunk']
    ax0, az0, ax1, az1 = area_box()
    in_area = lambda x, z: ax0 <= x <= ax1 and az0 <= z <= az1
    new_blocks = {name: Counter() for name in EXPECTED_BLOCKS}     # 方块 → 主群系 → 块数
    biome_chunks = Counter()                                       # 主群系 → 区块数（只算普查区里 full 的）
    positions = {name: [] for name in POSITIONED}
    starts = {s: [] for s in STRUCTURES}
    other_starts = Counter()
    biome_columns = Counter()
    dread_top = Counter()
    status_count = Counter()
    full = set()
    per_chunk_blocks = {}
    presences = []
    referenced = set()      # 有挡中景观结构引用的区块
    nests = []              # 巢穴核心方块实体：[x, y, z, 类型]
    for path in sorted(glob.glob(os.path.join(REGION, '*.mca'))):
        for chunk in bc.chunks_in_region(path):
            x, z = chunk.get('xPos'), chunk.get('zPos')
            status = str(chunk.get('Status', '')).split(':')[-1]
            if in_area(x, z):
                status_count[status] += 1
            for key, start in ((chunk.get('structures') or {}).get('starts') or {}).items():
                if start.get('id', 'INVALID') == 'INVALID' or not in_area(x, z):
                    continue
                entry = {'chunk': [x, z], 'pieces': len(start.get('Children') or [])}
                if key in starts:
                    starts[key].append(entry)
                else:
                    other_starts[key] += 1
            for key in ((chunk.get('structures') or {}).get('References') or {}):
                if key in BLOCKS_MIDSCAPE:
                    referenced.add((x, z))
            for be in chunk.get('block_entities') or []:
                if be.get('id') == 'essentialcraft5:nest_core':
                    nests.append([int(be.get('x')), int(be.get('y')), int(be.get('z')), str(be.get('type', ''))])
            for entity in chunk.get('entities') or []:     # 没到 full 的区块把实体存在区块里
                if entity.get('id') == PRESENCE and in_area(x, z):
                    presences.append([x, z])
            if status != 'full':
                continue
            full.add((x, z))
            sections = {s.get('Y'): s for s in chunk.get('sections', []) if s.get('Y') is not None}
            decoded = {}

            def blocks(sy):
                if sy not in decoded:
                    decoded[sy] = section_blocks(sections[sy]) if sy in sections else (None, None)
                return decoded[sy]

            # 方块成分 y −64~63（同 blockcount 的口径）
            counts = Counter()
            for sy in range(-4, 4):
                if sy in sections:
                    c = bc.section_counts(sections[sy])
                    if c:
                        counts.update(c)
            per_chunk_blocks[(x, z)] = counts
            if not in_area(x, z):
                continue
            # 13d 新方块：只看调色板里有的段
            found = Counter()
            for sy, section in sections.items():
                palette, idx = blocks(sy)
                if palette is None or not any(name in EXPECTED_BLOCKS for name in palette):
                    continue
                for i, name in enumerate(palette):
                    if name not in EXPECTED_BLOCKS:
                        continue
                    hits = np.nonzero(idx == i)[0]
                    found[name] += len(hits)
                    if name in POSITIONED:
                        for h in hits:
                            positions[name].append([x * 16 + int(h) % 16, sy * 16 + int(h) // 256, z * 16 + (int(h) // 16) % 16])
            chunk_biomes = Counter()
            surface = heightmap(chunk, 'WORLD_SURFACE')
            if surface is None:
                continue
            for cx in range(4):
                for cz in range(4):
                    lx, lz = cx * 4 + 1, cz * 4 + 1
                    top = int(surface[lz * 16 + lx]) - 64 - 1          # 最高的非空气方块
                    # 往下找地表材质
                    ground_name, ground_y = None, None
                    y = top
                    while y > top - 24:
                        palette, idx = blocks(y >> 4)
                        name = 'minecraft:air' if palette is None else palette[idx[((y & 15) * 16 + lz) * 16 + lx]]
                        if name not in AIR and not COVER_RE.search(name):
                            ground_name, ground_y = name, y
                            break
                        y -= 1
                    by = ground_y if ground_y is not None else top
                    bsec = sections.get(by >> 4)
                    if bsec is None:
                        continue
                    bpal, bidx = section_biomes(bsec)
                    if bpal is None:
                        continue
                    biome = bpal[bidx[(((by & 15) >> 2) * 4 + cz) * 4 + cx]]
                    biome_columns[biome] += 1
                    chunk_biomes[biome] += 1
                    if biome == 'essentialcraft5:dreadlands' and ground_name:
                        dread_top[ground_name] += 1
            if chunk_biomes:
                main_biome = chunk_biomes.most_common(1)[0][0]
                biome_chunks[main_biome] += 1
                for name, n in found.items():
                    new_blocks[name][main_biome] += n
    # 实体存储（full 区块）
    for path in sorted(glob.glob(os.path.join(ENTITIES, '*.mca'))):
        for chunk in bc.chunks_in_region(path):
            pos = chunk.get('Position')
            if pos is None:
                continue
            x, z = int(pos[0]), int(pos[1])
            for entity in chunk.get('Entities') or []:
                if entity.get('id') == PRESENCE and in_area(x, z):
                    presences.append([x, z])
    # 方块成分：只算自己和周围 8 块都 full 的
    total = Counter()
    used = 0
    for (x, z), counts in per_chunk_blocks.items():
        if all((x + dx, z + dz) in full for dx in (-1, 0, 1) for dz in (-1, 0, 1)):
            total.update(counts)
            used += 1
    composition = bc.summarize(HOANNA, total, used, len(per_chunk_blocks) - used, [-64, 63])
    area_full = sum(1 for p in full if in_area(*p))
    per_chunk = {}
    for name, by_biome in EXPECTED_BLOCKS.items():
        rows = {}
        for biome, (lo, hi) in by_biome.items():
            chunks = biome_chunks.get(biome, 0)
            value = new_blocks[name][biome] / chunks if chunks else None
            rows[biome] = {'per_chunk': round(value, 4) if value is not None else None, 'chunks': chunks, 'expected': [lo, hi],
                           'ok': None if value is None else lo <= value <= hi}
        stray = {b: n for b, n in new_blocks[name].items() if b not in by_biome}
        per_chunk[name] = {'by_biome': rows, 'elsewhere': stray}
    conflicts = []
    for x, y, z, kind in nests:
        cx, cz = x >> 4, z >> 4
        if kind in MIDSCAPE_NESTS and in_area(cx, cz) and any((cx + dx, cz + dz) in referenced for dx in (-1, 0, 1) for dz in (-1, 0, 1)):
            conflicts.append({'what': kind, 'pos': [x, y, z]})
    for x, y, z in positions['essentialcraft5:ruin_milestone']:
        if (x >> 4, z >> 4) in referenced:
            conflicts.append({'what': 'essentialcraft5:ruin_milestone', 'pos': [x, y, z]})
    nest_kinds = Counter(kind for x, y, z, kind in nests if in_area(x >> 4, z >> 4))
    cores = positions['essentialcraft5:nest_core']
    nearest = None
    for i, a in enumerate(cores):
        for b in cores[i + 1:]:
            d = max(abs((a[0] >> 4) - (b[0] >> 4)), abs((a[2] >> 4) - (b[2] >> 4)))
            nearest = d if nearest is None else min(nearest, d)
    return {
        'center_chunk': list(CENTER),
        'new_blocks': per_chunk,
        'biome_chunks': dict(biome_chunks.most_common()),
        'nest_cores': {'count': len(cores), 'nearest_pair_chunks': nearest, 'list': cores, 'kinds': dict(nest_kinds)},
        'midscape_conflicts': {'referenced_chunks': len(referenced), 'list': conflicts},
        'milestones': positions['essentialcraft5:ruin_milestone'],
        'area_chunks': (2 * RADIUS) ** 2, 'area_full_chunks': area_full, 'area_status': dict(status_count),
        'starts': {k: {'count': len(v), 'expected': EXPECTED[k], 'mean_pieces': round(sum(e['pieces'] for e in v) / len(v), 1) if v else 0,
                       'list': v} for k, v in starts.items()},
        'other_starts': dict(other_starts),
        'biome_columns': dict(biome_columns.most_common()),
        'dreadlands_top': dict(dread_top.most_common()),
        'elder_ruins': {'count': len(presences), 'chunks_per_ruin': round(area_full / len(presences), 1) if presences else None},
        'composition': composition,
    }


# 投影仪那几组产物（和 ley_projector_tables.py 的 TABLES 同一个分法）
ORE_GROUPS = {
    'minecraft:raw_copper': ('minecraft:copper_ore', 'minecraft:deepslate_copper_ore'),
    'minecraft:raw_iron': ('minecraft:iron_ore', 'minecraft:deepslate_iron_ore'),
    'minecraft:redstone': ('minecraft:redstone_ore', 'minecraft:deepslate_redstone_ore'),
    'minecraft:raw_gold': ('minecraft:gold_ore', 'minecraft:deepslate_gold_ore'),
    'minecraft:lapis_lazuli': ('minecraft:lapis_ore', 'minecraft:deepslate_lapis_ore'),
    'minecraft:diamond': ('minecraft:diamond_ore', 'minecraft:deepslate_diamond_ore'),
    'essentialcraft5:elemental_gem': ('essentialcraft5:elemental_infused_ore',),
    'essentialcraft5:fire_drops': ('essentialcraft5:fire_infused_ore',),
    'essentialcraft5:water_drops': ('essentialcraft5:water_infused_ore',),
    'essentialcraft5:earth_drops': ('essentialcraft5:earth_infused_ore',),
    'essentialcraft5:air_drops': ('essentialcraft5:air_infused_ore',),
    'essentialcraft5:mithriline_dust': ('essentialcraft5:mithriline_ore',),
    '(coal → rock)': ('minecraft:coal_ore', 'minecraft:deepslate_coal_ore'),
    '(emerald)': ('minecraft:emerald_ore', 'minecraft:deepslate_emerald_ore'),
}


def ore_compare(hoanna):
    try:
        with open(OVERWORLD_SURVEY, encoding='utf-8') as f:
            over = next(r for r in json.load(f) if r['dimension'] == 'minecraft:overworld')
    except (OSError, StopIteration):
        over = None
    rows = []
    for product, blocks in ORE_GROUPS.items():
        h = sum(hoanna['per_100k'].get(b, 0) for b in blocks)
        hc = sum(hoanna['counts'].get(b, 0) for b in blocks) / hoanna['full_chunks'] if hoanna['full_chunks'] else 0
        o = sum(over['per_100k'].get(b, 0) for b in blocks) if over else None
        oc = sum(over['counts'].get(b, 0) for b in blocks) / over['full_chunks'] if over else None
        rows.append({'product': product, 'hoanna_per_100k': round(h, 1), 'overworld_per_100k': round(o, 1) if o is not None else None,
                     'hoanna_per_chunk': round(hc, 2), 'overworld_per_chunk': round(oc, 2) if oc is not None else None})
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--analyze', action='store_true', help='不开服，只统计已有存档')
    parser.add_argument('--locate', type=int, default=10, help='每种结构 /locate 几次')
    parser.add_argument('--center', help='普查中心（方块坐标 X,Z），默认 0,0')
    parser.add_argument('--find-biome', help='开服后先 /locate biome 找这个群系，以它为中心（从 --center 或 0,0 找起）')
    parser.add_argument('--forecast', help='预测起点 JSON（{结构 id: [[区块 x, 区块 z], …]}），和普查区里的实际起点逐个对照')
    args = parser.parse_args()
    if args.center:
        x, z = (int(v) for v in args.center.split(','))
        CENTER[0], CENTER[1] = x >> 4, z >> 4
    if args.center or args.find_biome:
        tag = args.find_biome.split(':')[-1] if args.find_biome else f'c{CENTER[0]}_{CENTER[1]}'
        set_world(f'{WORLD_NAME}_{tag}')
    if not args.analyze:
        if os.path.exists(REGION) and glob.glob(os.path.join(REGION, '*.mca')):
            print(f'run/{WORLD_NAME} 已经有海纳尔区域文件，直接统计（要重跑就先删掉这个存档）')
        elif not generate(args.locate, args.find_biome):
            return 1
    result = analyze()
    result['ores'] = ore_compare(result['composition'])
    locate_path = os.path.join(WORLD, 'hoanna_survey_locate.json')
    if os.path.exists(locate_path):
        with open(locate_path, encoding='utf-8') as f:
            locates = json.load(f)
        summary = {}
        for job in locates:
            s = summary.setdefault(job['structure'], {'ms': [], 'found': 0})
            if 'ms' in job:
                s['ms'].append(job['ms'])
            if re.search(r'\[-?\d+, ~?-?\d*,? ?~?-?\d+\]|is at|位于', job.get('reply', '')):
                s['found'] += 1
        for s in summary.values():
            s['max_ms'] = max(s['ms']) if s['ms'] else None
            s['mean_ms'] = round(sum(s['ms']) / len(s['ms']), 1) if s['ms'] else None
        result['locate'] = {'summary': summary, 'jobs': locates}
    if args.forecast:
        with open(args.forecast, encoding='utf-8') as f:
            predicted = json.load(f)
        ax0, az0, ax1, az1 = area_box()
        check = {}
        for key, chunks in predicted.items():
            want = sorted(tuple(c) for c in chunks if ax0 <= c[0] <= ax1 and az0 <= c[1] <= az1)
            got = sorted(tuple(e['chunk']) for e in result['starts'].get(key, {}).get('list', []))
            check[key] = {'predicted': [list(c) for c in want], 'actual': [list(c) for c in got], 'match': want == got}
        result['forecast_check'] = check
    composition = result.pop('composition')
    result['composition_top'] = {k: round(v, 1) for k, v in list(composition['per_100k'].items())[:30]}
    result['composition_meta'] = {k: composition[k] for k in ('full_chunks', 'skipped_chunks', 'projectable_blocks', 'projectable_per_chunk')}
    with open(RESULT, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(result, f, indent=1, ensure_ascii=False)
        f.write('\n')
    # 终端摘要
    print(f"普查区 {result['area_chunks']} 区块，full {result['area_full_chunks']}，状态 {result['area_status']}")
    for k, v in result['starts'].items():
        lo, hi = v['expected']
        print(f"  {k}: {v['count']} 座（预期 {lo}~{hi}），平均 {v['mean_pieces']} 件")
    if result['other_starts']:
        print('  其它结构起点：', result['other_starts'])
    total_cols = sum(result['biome_columns'].values())
    print('群系（地表列占比）：', ', '.join(f"{k.split(':')[-1]} {v / total_cols:.1%}" for k, v in result['biome_columns'].items()))
    dt = sum(result['dreadlands_top'].values())
    if dt:
        dd = result['dreadlands_top'].get('essentialcraft5:dread_dirt', 0)
        print(f'恐惧之地地表 {dt} 列，恐惧泥土 {dd / dt:.1%}；前几名 {list(result["dreadlands_top"].items())[:6]}')
    print('上古遗迹：', result['elder_ruins'])
    print(f"13d 新方块（普查中心区块 {result['center_chunk']}，每区块，按区块主群系）：")
    for name, row in result['new_blocks'].items():
        parts = []
        for biome, v in row['by_biome'].items():
            if v['chunks']:
                mark = '' if v['ok'] else ' !!'
                parts.append(f"{biome.split(':')[-1]} {v['per_chunk']}（{v['expected'][0]}~{v['expected'][1]}，{v['chunks']} 区块）{mark}")
        stray = f"；别的群系 {row['elsewhere']}" if row['elsewhere'] else ''
        print(f"  {name.split(':')[-1]}: {', '.join(parts) or '（普查区里没有对应群系）'}{stray}")
    print(f"巢穴核心 {result['nest_cores']['count']} 个（{result['nest_cores']['kinds']}），两两最近 {result['nest_cores']['nearest_pair_chunks']} 区块；"
          f"里程碑 {len(result['milestones'])} 根")
    mc = result['midscape_conflicts']
    print(f"中景观 / 里程碑进了地表大结构引用区块的：{len(mc['list'])} 处（有引用的区块 {mc['referenced_chunks']} 个）{mc['list'] or ''}")
    for key, row in (result.get('forecast_check') or {}).items():
        print(f"预测对照 {key}: {'一致' if row['match'] else '不一致'}，预测 {row['predicted']}，实际 {row['actual']}")
    print('矿（每 10 万格 / 每区块，海纳尔 vs 主世界 ley_survey）：')
    for row in result['ores']:
        print(f"  {row['product']:34s} {row['hoanna_per_100k']:8.1f} {row['overworld_per_100k']!s:>8}   {row['hoanna_per_chunk']:7.2f} {row['overworld_per_chunk']!s:>7}")
    if 'locate' in result:
        for k, v in result['locate']['summary'].items():
            print(f"/locate {k}: {len(v['ms'])} 次，找到 {v['found']}，平均 {v['mean_ms']} ms，最长 {v['max_ms']} ms")
    print('结果写到', os.path.relpath(RESULT, ROOT))
    return 0


if __name__ == '__main__':
    sys.exit(main())
