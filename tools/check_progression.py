# -*- coding: utf-8 -*-
"""主线可达性检查：从开局拿得到的东西出发，算研究手册的每一步做不做得到；做不到的列出断在哪。

把下面这些连成一张「东西从哪来」的图（节点 = 物品 / 世界里的方块 / 实体 / 维度 / 多方块结构 / MRU、ESPE 这类能力 / 进度），
再从开局能拿到的东西出发反复推，直到推不动为止：

  数据（自动读）
    - 配方：data/*/recipes 下全部 JSON（工作台、保留 NBT、魔术师台、辐射仓、米斯莱熔炉、矿石加工、注风，原版熔炉类），
      forge:item_exists / mod_loaded 条件照游戏里的判定；引用了不存在的物品的配方整条不加载（游戏里也一样），记为数据问题
    - 战利品表：方块（方块在世界里 → 掉什么）、实体、箱子、注入表、地脉投影表，loot_table 引用照着展开
    - 世界生成：forge 群系修改器（矿、聚集体遗迹、刷怪）、海纳尔的群系（地物、刷怪）、地表规则、结构集
    - 标签：本模组 main + generated，原版和 Forge 的标签从 Gradle 缓存里的 jar 读（找不到 jar 时原版 / Forge 标签按「有东西」算并给出警告）
    - 恶魔交易池 data/*/demon_trades
    - 矩阵阶 data/*/matrix_ranks/rank_<n>.json（第十三批 13a，batch13.md §2.7、§3.1.3）：每阶一条规则，
      要上一阶 + 淬炼台 + 灵魂石 + 祭品 + 能量（UBMRU / MRU / 五芒星的 ESPE）+ 站点（几档五芒星）+ 图谱条件，给 cap:matrix_rank_<n>；
      图谱条件按「凑够几种」算（下面的 CODEX_* 和标签），能杀到的种类不够数就推不出来
    - 同调升阶配方（essentialcraft5:attunement_ascension）：给 cap:attunement/<物品>@<阶>，要同调度、上一阶、矩阵阶戳；
      写了 result 的是升格，产出别的物品。只能靠升格拿到的物品（ASCENSION_ONLY，裁决之刃）另查来源：要有对应家族、对应阶的升格配方，
      别的配方、战利品、代码规则也产出它算数据问题；还没注册（别的路正在做）时只提示
    - 进度：data/essentialcraft5/advancements 下全部（手册的 complete 指向这里）
    - 研究手册：assets/essentialcraft5/guide/zh_cn/*.json 里每个带 complete 的条目算一步，if 条件按配置默认值判
  代码（写在本文件 CODE_RULES 里，每条注明出处文件）
    - 地面仪式、各台产能器提供的 MRU（含平衡值）、ESPE 水晶柱、多方块结构、召唤恶魔 / 全息投影、恶魔交易、
      灵魂刻刀、水晶成型器 / 提取器、余烬锻炉、武器工作台、风之好感与海纳尔之门、只由代码发的进度、
      结构里摆的方块和箱子表、实体变种对应的战利品表等

开局（不用推就有的）：生存模式拿得到的全部原版物品（去掉命令方块、刷怪蛋、基岩之类拿不到的，见 VANILLA_UNOBTAINABLE）、
原版方块和生物、主世界 / 下界 / 末地、击杀生物（攒 UBMRU），以及第一次进游戏送的研究手册。
数量、概率、耗时都不算（那是「毕业时长」的事），只问「有没有路」。

报告里有：
  - 每一步做不做得到、获取链的深度（最长那条支路几步）、要过的门槛（下界 / 末地 / 凋灵 / 远古残骸 / 海纳尔）；
    推的时候分四遍（先只许主世界，再依次放开下界、末地、海纳尔），所以记下的获取链是「去的维度最近」的那条
  - 断链：做不到的步骤沿最近的根源往下追，并找「只要补上它这一步就通」的那一样东西（单点修复），按它归类
  - 生存拿不到的物品（不算 CREATIVE_ONLY / WORLD_ONLY），按要补的地方归类
  - 按手册顺序走：必做主线步骤的获取链里有没有后面的章 / 支线才讲的东西
  - 手册里提到、生存拿不到的东西；交不出来的恶魔交易条目；数据问题（配方 / 战利品 / 进度引用不存在的东西、空标签、缺字段，
    代码规则写的出处文件不存在）
  - 第二种情形「海纳尔之门关闭」：看关掉以后还看得见的步骤是不是都做得到；第三种「矩阵阶关闭」（general.matrix_rank.enabled = false）：
    门全开、阶的步骤藏掉、同调升阶不看阶，主线照样要连得通
  - 矩阵阶的门（海纳尔之门、全息投影台、天灾信标）按配置 general.matrix_rank.gates.* 的值要 cap:matrix_rank_<n>，设 0 就不要
  - 主线统计：有步骤的主线分类几章、必做几步、照手册顺序最后一步是哪个（应是 MAIN_FINAL_STEP，第二十三章的九阶）
  - 代码规则里按群系判的地点写成 biome:#<群系标签>（BIOME_SITES），按标签对应的维度给；别的路还没合进来的出处文件
    列在 PENDING_SOURCES（不存在时只记提示）
  - 「必做」跟配置有关：条目 optional 可以写条件（按条件可选，写法同 if），每种情形各按配置求一次
  - 保底检查：去掉只靠运气的来源（CODE_RULES 里标 luck=True 的：自然刷出的大法师、原版地牢箱子的饰品表；战利品表里带
    essentialcraft5:fair_kill / kill_fatigue 条件的池和条目，即野外怪物的关键掉落）再推一遍，
    必做主线步骤仍要做得到；做不到的列为「只靠运气的必做步骤」，--strict 下算失败
  - 老存档迁移表覆盖检查：不在基线（tools/guide/baseline_steps.txt）里的必做主线步骤，要在 data/essentialcraft5/guide_migration/v*.json
    里有迁移项（整章新加的内容除外，名单在 tools/guide/gen_migration.py）；每个证明进度要存在、排在它后面、做得到（默认配置下）

用法：
  python tools/check_progression.py                      报告（默认配置、海纳尔之门关闭、矩阵阶关闭三种情形）
  python tools/check_progression.py --strict             有断链或数据问题时退出码 1
  python tools/check_progression.py --cfg general.hoanna.portal_enabled=false   只按给定配置跑一种情形
  python tools/check_progression.py --item essentialcraft5:void_plating        打印这件东西最短的获取链
  python tools/check_progression.py --step hologram/hologram                   打印手册某一步的获取链
  python tools/check_progression.py --out report.txt     报告另存一份
  python tools/check_progression.py --mc-jar <client-extra.jar> --forge-jar <forge.jar>   指定原版 / Forge 数据（默认在 Gradle 缓存里找）
  python tools/check_progression.py --item cap:matrix_rank_5                             能力节点也能查（矩阵阶、同调阶 cap:attunement/<物品>@<阶>、图谱 cap:codex@<组>>=<种数>）

退出码：0 = 全部可达；1 = --strict 下有断链 / 数据问题 / 只靠运气的必做步骤 / 迁移表漏项；2 = 缺文件（例如没跑过 runData）。
"""
import argparse
import collections
import copy
import glob
import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODID = 'essentialcraft5'
MAIN_RES = os.path.join(ROOT, 'src', 'main', 'resources')
GEN_RES = os.path.join(ROOT, 'src', 'generated', 'resources')
RES_ROOTS = [MAIN_RES, GEN_RES]
LANG_FILE = os.path.join(GEN_RES, 'assets', MODID, 'lang', 'en_us.json')
ZH_LANG_FILE = os.path.join(GEN_RES, 'assets', MODID, 'lang', 'zh_cn.json')
GUIDE_DIR = os.path.join(MAIN_RES, 'assets', MODID, 'guide', 'zh_cn')
CONFIG_KEYS_FILE = os.path.join(GEN_RES, 'assets', MODID, 'guide', 'config_keys.json')
ID_MAPS = [os.path.join(ROOT, 'tools', 'id_map_items.json'), os.path.join(ROOT, 'tools', 'id_map_blocks.json')]
JAVA_PKG = 'src/main/java/com/flameforge/essentialcraft5/'

# 运行时一定在的模组（forge:mod_loaded 条件按这个判）
LOADED_MODS = {'minecraft', 'forge', MODID, 'curios'}


def rid(s, default_ns='minecraft'):
    s = s.strip()
    return s if ':' in s else default_ns + ':' + s


def ec(path):
    return MODID + ':' + path


# ======================================================================
# 开局就有、生存拿不到的原版东西
# ======================================================================

# 1.20.1 生存模式拿不到的原版物品（刷怪蛋、被虫蚀的方块另按前后缀判）
VANILLA_UNOBTAINABLE = {
    'air', 'bedrock', 'barrier', 'light', 'structure_block', 'structure_void', 'jigsaw', 'command_block',
    'chain_command_block', 'repeating_command_block', 'command_block_minecart', 'debug_stick', 'knowledge_book',
    'spawner', 'end_portal_frame', 'budding_amethyst', 'reinforced_deepslate', 'petrified_oak_slab', 'player_head',
    'farmland', 'dirt_path', 'frogspawn', 'chorus_plant', 'suspicious_sand', 'suspicious_gravel', 'bundle',
    'end_portal', 'nether_portal', 'end_gateway', 'fire', 'soul_fire', 'water', 'lava', 'moving_piston',
    'piston_head', 'void_air', 'cave_air', 'bubble_column', 'tall_seagrass', 'cocoa',
}

# 原版门槛：报告里标出每一步的获取链经过了哪些（只是提示，不影响判定）
VANILLA_GATES = collections.OrderedDict([
    ('下界', {'soul_sand', 'soul_soil', 'quartz', 'nether_quartz_ore', 'quartz_block', 'chiseled_quartz_block',
             'quartz_pillar', 'blaze_rod', 'blaze_powder', 'nether_wart', 'ghast_tear', 'magma_cream', 'netherrack',
             'glowstone', 'glowstone_dust', 'ender_eye', 'wither_skeleton_skull', 'brewing_stand', 'crying_obsidian',
             'basalt', 'blackstone', 'shroomlight', 'warped_fungus', 'crimson_fungus'}),
    ('末地', {'chorus_fruit', 'popped_chorus_fruit', 'shulker_shell', 'shulker_box', 'end_stone', 'end_rod',
             'elytra', 'dragon_breath', 'dragon_egg', 'purpur_block', 'end_crystal'}),
    ('凋灵', {'nether_star', 'beacon'}),
    ('远古残骸', {'ancient_debris', 'netherite_scrap', 'netherite_ingot', 'netherite_block'}),
])

# 第十三批 13d 的八种海纳尔本地怪（batch13.md §6.5；顺序同实体标签 hoanna_natives）
HOANNA_NATIVES_13D = ('frostbitten', 'mana_prism', 'chaos_aberrant', 'ember_spirit', 'hoanna_scavenger', 'abyssal_eel',
                      'inquisitorium_patroller', 'catacomb_revenant')

# 只放创造栏、或者原作本来就拿不到的东西（policy.md §4.7）：拿不到是预期，不算断链
# 暗渎水晶柱第十三批 13e 起有生存配方（第二十二章，魔力工作台），不再在这里
CREATIVE_ONLY = {
    ec('dimensional_transceiver'), ec('balance_setter'), ec('creative_mru_storage'),
    ec('creative_espe_storage'), ec('lapis_core'),
    ec('infinite_mru_source'), ec('infinite_espe_source'), ec('infinite_thermal_source'), ec('infinite_mru_crystal'),
    ec('creative_ward'), ec('infinite_mru_converter'),
    ec('demon_spawn_egg'), ec('hologram_spawn_egg'), ec('mru_presence_spawn_egg'), ec('player_clone_spawn_egg'),
    ec('poison_fume_spawn_egg'), ec('wind_mage_spawn_egg'), ec('inquisitorium_sentinel_spawn_egg'),
    # 第十三批 13c 的刷怪蛋
    *[ec(x + '_spawn_egg') for x in ('elemental_wisp', 'matrix_wraith', 'nether_imp', 'void_stalker', 'mru_golem', 'matrix_shade')],
    # 第十三批 13d 的海纳尔本地怪刷怪蛋（幼体没有蛋）
    *[ec(x + '_spawn_egg') for x in HOANNA_NATIVES_13D],
    # 第十三批 13e 的三只首领刷怪蛋
    *[ec(x + '_spawn_egg') for x in ('town_sentry', 'abyss_tyrant', 'calamity_heart')],
}
# 只在世界里出现的方块：仪式直接生成在地上、敲碎掉魔晶，物品形态只在创造栏（原作 BlockDrops 也是这样）
WORLD_ONLY = {ec('fire_drops_cluster'), ec('water_drops_cluster'), ec('earth_drops_cluster'), ec('air_drops_cluster'),
              # 第十三批 13d：只由海纳尔的世界生成摆放、挖掉不掉自己的方块（喷口什么都不掉，里程碑掉强化石，巢穴核心什么都不掉）
              ec('ley_vent'), ec('ruin_milestone'), ec('nest_core'),
              # 第十三批 13e：封印宝库的五种谜题方块、沉城祭坛（挖不动、只由结构摆放）
              ec('elder_stele'), ec('vault_pedestal'), ec('vault_keyhole'), ec('vault_seal'), ec('vault_core'), ec('sunken_altar')}
# 不是「物品」（没有物品形态，或者只是机器放出来的功能方块），不进物品清单
NOT_ITEMS = {ec('hoanna_portal'), ec('magic_light')}


# ======================================================================
# 代码里定义的来源（数据文件里看不出来的）
# ======================================================================
# 节点写法：item:<id>  block:<id>（世界里有这个方块）  entity:<id>  dim:<id>  struct:<id>（多方块结构搭成）
#           summon:<id>（召唤出来）  effect:<id>  loot:<表 id>  adv:<进度 id>  structure:<世界生成结构 id>
#           soul:<实体 id>（那种生物的被俘灵魂）  cap:<能力>
#           #<物品标签>  #block:<方块标签>  （标签 = 里面任一个）
# needs 里每一项是「全部要有」；一项里用 | 分开的是「有一个就行」。
# MRU / MRU[下限,上限]：任一台产能器接上绑定宝石（带平衡值区间时只算平衡值落在区间里的，随机平衡值的产能器都算）。
# cfg：规则只在这个配置条件成立时有效（写法同手册的 if）。

PILLAR_ANY = 'block:{0}:mithriline_crystal_pillar|block:{0}:pale_crystal_pillar|block:{0}:void_crystal_pillar|block:{0}:demonic_crystal_pillar'.format(MODID)
PILLAR_PALE_UP = 'block:{0}:pale_crystal_pillar|block:{0}:void_crystal_pillar|block:{0}:demonic_crystal_pillar'.format(MODID)
PILLAR_VOID_UP = 'block:{0}:void_crystal_pillar|block:{0}:demonic_crystal_pillar'.format(MODID)
FOCUS_ANY = '|'.join('item:%s:%s_focus' % (MODID, e) for e in ('fire', 'water', 'earth', 'air'))
GUN_BODY = '|'.join('item:' + ec(x) for x in ('magical_ingot', 'pale_ingot', 'mithriline_ingot', 'void_plating', 'ackronite_ingot'))
GUN_SCOPE = GUN_BODY
GUN_LENS = '|'.join('item:' + ec(x) for x in ('chaos_matrix_projection', 'frozen_matrix_projection', 'magic_matrix_projection', 'shade_matrix_projection'))
ESSENCES = ['item:%s:%s_%s_essence' % (MODID, r, e) for r in ('common', 'uncommon', 'rare', 'exceptional')
            for e in ('fire', 'water', 'earth', 'air')]
# 第十批 10c 宅男套装模块：头盔上的、其余的分成两组（胸甲两格，护腿 / 靴子各一格），给「装上两块模块」那步用；
# 第十二批的四块：隐匿在头盔，吸附、攀行、稳手在护腿 / 靴子
SUIT4 = ['item:' + ec(x) for x in ('computer_helmet', 'computer_chestplate', 'computer_leggings', 'computer_boots')]
HELMET_MODULES = '|'.join('item:' + ec(m) for m in ('rewind_module', 'matrix_filter_module', 'veil_module'))
BODY_MODULES_A = '|'.join('item:' + ec(m) for m in ('matrix_shunt_module', 'accumulator_module'))
BODY_MODULES_B = '|'.join('item:' + ec(m) for m in ('shred_module', 'phase_anchor_module', 'attraction_module', 'stride_module', 'steady_hand_module'))


# 1 档以上的灵魂五芒星（矩阵之影召唤用；同下面 PENTACLE_TIER_NODES 的 1~3 档）
PENTACLE_TIER_NODES_13C = ['struct:%s_tier%d' % (ec('player_pentacle'), t) for t in (1, 2, 3)]


def R(label, src, needs, gives, cfg=None, luck=False):
    """一条代码规则。luck=True：这条来源只靠运气（概率极低、没办法找、也刷不出来），保底检查会去掉它再推一遍。
    「磨」不算运气：次数不限、多做总能拿到的（凋灵骷髅头 2.5%、多开几个遗迹箱子）不标"""
    return {'label': label, 'src': src, 'needs': needs, 'gives': gives, 'cfg': cfg, 'luck': luck}


# 第十三批 13d：地脉风暴的条件（风暴只在大潮夜掷，潮汐关掉就没有大潮，batch13.md §6.2）
LEY_STORM_CFG = ['cfg:general.hoanna_events.ley_storm_enabled', 'cfg:general.hoanna_events.ley_storm_chance>0',
                 'cfg:general.hoanna_tide.enabled']
# 第十三批 13d 的五种巢穴 → 一波召出的怪（batch13.md §6.4「巢穴核心」）；前三种在中景观里（13d），后两种归 13e 的结构
HOANNA_NESTS_13D = (('frozen_camp', 'frostbitten'), ('chaos_fissure', 'chaos_aberrant'), ('fallout_crater', 'hoanna_scavenger'))
# 第十三批 13e：结构里放的两种巢穴 → (结构, 一波召出的怪)；类型 JSON 在 data/essentialcraft5/hoanna_nests（13d 已有）
HOANNA_NESTS_13E = (('outpost_camp', 'inquisitorium_outpost', 'inquisitorium_patroller'),
                    ('sunken_guard', 'sunken_district', 'catacomb_revenant'))
# 第十三批 13e 的三座海纳尔结构（勘测图 HoannaSurveyMapItem.KINDS 的顺序：宝库 → 沉城 → 前哨 → 城镇）
HOANNA_STRUCTURES_13E = ('sealed_vault', 'sunken_district', 'inquisitorium_outpost')
# 封印宝库一次尝试要放的东西（batch13.md §6.4）：锁孔里 1 个地脉结晶，四座祭座各放对应元素的晶石（第一章四种魔晶）
VAULT_ATTEMPT = ['item:' + ec('ley_crystal')] + ['item:' + ec(e + '_drops') for e in ('fire', 'water', 'earth', 'air')]

# 别的路还在写、合进来以前不存在的出处文件：不存在时记提示（不算数据问题），合进来以后这里删掉对应一行
# （文件已经在了、还列在这里会提示删）。新批次开工时 P 路先照细则写出处，集成核对
PENDING_SOURCES = set()


CODE_RULES = [
    # ---------------- 开局 ----------------
    R('第一次进游戏送研究手册', JAVA_PKG + 'player/ResearchBookGift.java', [], ['item:' + ec('research_book')],
      cfg='cfg:general.give_research_book_on_first_join'),
    R('击杀生物攒 UBMRU', JAVA_PKG + 'player/UBMRUEvents.java', ['entity:minecraft:zombie'], ['cap:ubmru']),

    # ---------------- 地面仪式 ----------------
    R('仪式：烈焰粉丢在下界岩上、四面岩浆', JAVA_PKG + 'ritual/GroundRituals.java',
      ['item:minecraft:blaze_powder', 'block:minecraft:netherrack', 'block:minecraft:lava'], ['block:' + ec('fire_drops_cluster')]),
    R('仪式：黏土球丢在冰上、四面水', JAVA_PKG + 'ritual/GroundRituals.java',
      ['item:minecraft:clay_ball', 'block:minecraft:ice', 'block:minecraft:water'], ['block:' + ec('water_drops_cluster')]),
    R('仪式：黏液球丢在黏液块上、四面草方块', JAVA_PKG + 'ritual/GroundRituals.java',
      ['item:minecraft:slime_ball', 'block:minecraft:slime_block', 'block:minecraft:grass_block'], ['block:' + ec('earth_drops_cluster')]),
    R('仪式：火药丢在石英块上、四面沙子', JAVA_PKG + 'ritual/GroundRituals.java',
      ['item:minecraft:gunpowder', 'block:minecraft:quartz_block|block:minecraft:chiseled_quartz_block|block:minecraft:quartz_pillar',
       'block:minecraft:sand|block:minecraft:red_sand'],
      ['block:' + ec('air_drops_cluster')]),
    R('仪式：绿宝石丢在绿宝石块上、四面灵魂沙 → 灵魂石', JAVA_PKG + 'ritual/GroundRituals.java',
      ['item:minecraft:emerald', 'block:minecraft:emerald_block', 'block:minecraft:soul_sand'], ['item:' + ec('soul_stone')]),
    R('仪式：钻石丢在绿宝石块上、四面灵魂沙 → MRU 聚集体', JAVA_PKG + 'ritual/GroundRituals.java',
      ['item:minecraft:diamond', 'block:minecraft:emerald_block', 'block:minecraft:soul_sand'], ['entity:' + ec('mru_presence')]),

    # ---------------- MRU（产能器 + 绑定宝石；平衡值见 tools/plan/batch1.md §1.2） ----------------
    R('矩阵换能器：灵魂石 + 主人的 UBMRU（平衡值固定，默认 1.0）', JAVA_PKG + 'blockentity/MatrixAbsorberBlockEntity.java',
      ['block:' + ec('matrix_absorber'), 'item:' + ec('soul_stone'), 'cap:ubmru', 'item:' + ec('bound_gem')], ['cap:mru@fixed:matrix_absorber']),
    R('热力产能器：原版燃料，四面下界岩 / 岩浆（平衡值放下时随机；每个燃料出 1 个魔力炉渣）', JAVA_PKG + 'blockentity/HeatGeneratorBlockEntity.java',
      ['block:' + ec('heat_generator'), 'item:' + ec('bound_gem')], ['cap:mru@random', 'item:' + ec('magical_slag')]),
    R('自然熔炉：烧放进去的花 / 树苗 / 草（平衡值放下时随机）', JAVA_PKG + 'blockentity/NaturalFurnaceBlockEntity.java',
      ['block:' + ec('natural_furnace'), 'item:' + ec('bound_gem')], ['cap:mru@random']),
    R('末影产能器：打周围的末影人（平衡值放下时随机）', JAVA_PKG + 'blockentity/EnderGeneratorBlockEntity.java',
      ['block:' + ec('ender_generator'), 'entity:minecraft:enderman', 'item:' + ec('bound_gem')], ['cap:mru@random']),
    R('冷凝器：周围的冰雪 / 霜冻石（平衡值固定，默认 0）', JAVA_PKG + 'blockentity/ColdDistillatorBlockEntity.java',
      ['block:' + ec('cold_distillator'), 'item:' + ec('bound_gem')], ['cap:mru@fixed:cold_distillator']),
    R('月亮井：夜里露天（平衡值固定，默认 1.0）', JAVA_PKG + 'blockentity/MoonWellBlockEntity.java',
      ['block:' + ec('moon_well'), 'item:' + ec('bound_gem')], ['cap:mru@fixed:moon_well']),
    R('日光吸收器：被太阳棱镜的光束照到（平衡值固定，默认 2.0）', JAVA_PKG + 'blockentity/SunRayAbsorberBlockEntity.java',
      ['block:' + ec('sun_ray_absorber'), 'struct:' + ec('solar_prism'), 'item:' + ec('bound_gem')], ['cap:mru@fixed:sun_ray_absorber']),
    R('特斯拉反应堆：结构成形就发电（平衡值在 0~2 之间来回摆）', JAVA_PKG + 'blockentity/ReactorBlockEntity.java',
      ['struct:' + ec('reactor'), 'item:' + ec('bound_gem')], ['cap:mru@random']),
    R('终极热力产能器（平衡值放下时随机；出魔力炉渣）', JAVA_PKG + 'blockentity/UltraHeatGeneratorBlockEntity.java',
      ['block:' + ec('ultra_heat_generator'), 'item:' + ec('bound_gem')], ['cap:mru@random', 'item:' + ec('magical_slag')]),
    R('终极自然熔炉（平衡值放下时随机）', JAVA_PKG + 'blockentity/UltraNaturalFurnaceBlockEntity.java',
      ['block:' + ec('ultra_natural_furnace'), 'item:' + ec('bound_gem')], ['cap:mru@random']),

    # ---------------- 多方块结构 ----------------
    R('结构：米斯莱熔炉（24 块米斯莱护板 + ESPE 水晶柱）', JAVA_PKG + 'registry/ECMithriline.java',
      ['block:' + ec('mithriline_furnace'), 'block:' + ec('mithriline_plating'), PILLAR_ANY], ['struct:' + ec('mithriline_furnace')]),
    R('结构：风之符文祭坛（米斯莱 / 淡青 / 魔力护板 + 8 根水晶柱）', JAVA_PKG + 'registry/ECWind.java',
      ['block:' + ec('wind_rune'), 'block:' + ec('mithriline_plating'), 'block:' + ec('pale_plating'),
       'block:' + ec('magic_plating'), PILLAR_ANY], ['struct:' + ec('wind_rune')]),
    R('结构：灵魂五芒星 0 档（米斯莱护板 + 8 根任意档水晶柱）', JAVA_PKG + 'registry/ECPentacle.java',
      ['block:' + ec('player_pentacle'), 'block:' + ec('mithriline_plating'), PILLAR_ANY], ['struct:' + ec('player_pentacle')]),
    R('结构：灵魂五芒星 1 档（+ 淡青块、淡青以上的柱子）', JAVA_PKG + 'registry/ECPentacle.java',
      ['struct:' + ec('player_pentacle'), 'block:' + ec('pale_block'), PILLAR_PALE_UP], ['struct:' + ec('player_pentacle_tier1')]),
    R('结构：灵魂五芒星 2 档（+ 虚空石、虚空以上的柱子）', JAVA_PKG + 'registry/ECPentacle.java',
      ['struct:' + ec('player_pentacle_tier1'), 'block:' + ec('void_stone'), PILLAR_VOID_UP], ['struct:' + ec('player_pentacle_tier2')]),
    R('结构：灵魂五芒星 3 档（+ 恶魔护板、恶魔水晶柱）', JAVA_PKG + 'registry/ECPentacle.java',
      ['struct:' + ec('player_pentacle_tier2'), 'block:' + ec('demonic_plating'), 'block:' + ec('demonic_crystal_pillar')],
      ['struct:' + ec('player_pentacle_tier3')]),
    R('结构：恶魔五芒星（虚空石、淡青 / 米斯莱护板、荧石、16 根柱子：任意 4 / 淡青以上 8 / 虚空以上 4）', JAVA_PKG + 'registry/ECDemon.java',
      ['block:' + ec('demonic_pentacle'), 'block:' + ec('void_stone'), 'block:' + ec('pale_plating'),
       'block:' + ec('mithriline_plating'), 'item:minecraft:glowstone', PILLAR_ANY, PILLAR_PALE_UP, PILLAR_VOID_UP],
      ['struct:' + ec('demonic_pentacle')]),
    R('结构：余烬锻炉（虚空石、淡青护板、魔力护板）', JAVA_PKG + 'registry/ECEmberForge.java',
      ['block:' + ec('ember_forge'), 'block:' + ec('void_stone'), 'block:' + ec('pale_plating'), 'block:' + ec('magic_plating')],
      ['struct:' + ec('ember_forge')]),
    R('结构：特斯拉反应堆（支架 + 魔力护板）', JAVA_PKG + 'registry/ECReactor.java',
      ['block:' + ec('reactor'), 'block:' + ec('reactor_support'), 'block:' + ec('magic_plating')], ['struct:' + ec('reactor')]),
    R('结构：太阳棱镜（四周任意方块 + 空气）', JAVA_PKG + 'registry/ECSolar.java',
      ['block:' + ec('solar_prism')], ['struct:' + ec('solar_prism')]),
    R('结构：MRU 特斯拉线圈（底座用 #mru_coil_base）', JAVA_PKG + 'registry/ECTesla.java',
      ['block:' + ec('mru_coil'), '#block:' + ec('mru_coil_base')], ['struct:' + ec('mru_coil')]),
    R('结构：魔力传送器（虚空石 + 魔力护板）', JAVA_PKG + 'registry/ECVoidMachines.java',
      ['block:' + ec('magical_teleporter'), 'block:' + ec('void_stone'), 'block:' + ec('magic_plating')],
      ['struct:' + ec('magical_teleporter')]),
    R('结构：魔力熔炉（虚空石 + 4 台热力产能器）', JAVA_PKG + 'registry/ECVoidMachines.java',
      ['block:' + ec('magical_furnace'), 'block:' + ec('void_stone'), 'block:' + ec('heat_generator')],
      ['struct:' + ec('magical_furnace')]),
    R('结构：富集仓（控制器 + #mrucuec_casing 外壳）', JAVA_PKG + 'enrichment/CuboidShellScanner.java',
      ['block:' + ec('ec_controller'), '#block:' + ec('mrucuec_casing')], ['struct:' + ec('enrichment_chamber')]),
    R('结构：富集仓带 10 个以上存储方块', JAVA_PKG + 'enrichment/EnrichmentTrigger.java',
      ['struct:' + ec('enrichment_chamber'), 'block:' + ec('ec_holding_chamber')], ['struct:' + ec('enrichment_chamber_big')]),

    # ---------------- 机器产物 ----------------
    R('水晶成型器：水桶 ×3 + 玻璃 ×2 + 钻石 → 元素水晶', JAVA_PKG + 'blockentity/CrystalFormerBlockEntity.java',
      ['block:' + ec('crystal_former'), 'MRU', 'item:minecraft:water_bucket', 'item:minecraft:glass', 'item:minecraft:diamond'],
      ['item:' + ec('elemental_crystal')]),
    R('水晶提取器：贴着元素水晶抽 16 种精华', JAVA_PKG + 'blockentity/CrystalExtractorBlockEntity.java',
      ['block:' + ec('crystal_extractor'), 'block:' + ec('elemental_crystal'), 'MRU'], ESSENCES),
    R('魔力熔炉：熔炼时出魔力炉渣', JAVA_PKG + 'blockentity/MagicalFurnaceBlockEntity.java',
      ['struct:' + ec('magical_furnace'), 'MRU'], ['item:' + ec('magical_slag')]),
    R('岩浆冶炼炉加工矿石时出魔力炉渣', JAVA_PKG + 'blockentity/MagmaticSmelteryBlockEntity.java',
      ['block:' + ec('magmatic_smeltery'), 'MRU'], ['item:' + ec('magical_slag')]),
    R('余烬锻炉：夜里余烬 ×4 + 元素焦点 ×4 → 余烬元素剑', JAVA_PKG + 'blockentity/EmberForgeBlockEntity.java',
      ['struct:' + ec('ember_forge'), '#' + ec('embers'), FOCUS_ANY], ['item:' + ec('forged_elemental_sword')]),
    R('武器工作台（手枪）：同种枪身材料 + #cores/magic + #gun_souls', JAVA_PKG + 'gun/GunAssembly.java',
      ['block:' + ec('pistol_weapon_maker'), '#' + ec('cores/magic'), '#' + ec('gun_souls'), GUN_BODY], ['item:' + ec('pistol')]),
    R('武器工作台（步枪）', JAVA_PKG + 'gun/GunAssembly.java',
      ['block:' + ec('rifle_weapon_maker'), '#' + ec('cores/magic'), '#' + ec('gun_souls'), GUN_BODY], ['item:' + ec('rifle')]),
    R('武器工作台（狙击步枪）：另要魔力纯化玻璃合金 + 狙击镜材料', JAVA_PKG + 'gun/GunAssembly.java',
      ['block:' + ec('sniper_weapon_maker'), '#' + ec('cores/magic'), '#' + ec('gun_souls'), GUN_BODY,
       'item:' + ec('magic_purified_glass_alloy'), GUN_SCOPE], ['item:' + ec('sniper')]),
    R('武器工作台（加特林）：另要日光玻璃 + 两格同种矩阵投影', JAVA_PKG + 'gun/GunAssembly.java',
      ['block:' + ec('gatling_weapon_maker'), '#' + ec('cores/magic'), '#' + ec('gun_souls'), GUN_BODY,
       'item:' + ec('sun_imbued_glass'), GUN_LENS], ['item:' + ec('gatling')]),
    R('地脉投影仪（主世界表）', JAVA_PKG + 'blockentity/LeyProjectorBlockEntity.java',
      ['block:' + ec('ley_projector'), 'MRU'],
      ['loot:%s:ley_projector/ores/minecraft/overworld' % MODID, 'loot:%s:ley_projector/rock/minecraft/overworld' % MODID],
      cfg='cfg:machines.ley_projector.enabled'),
    R('地脉投影仪（下界表）', JAVA_PKG + 'blockentity/LeyProjectorBlockEntity.java',
      ['block:' + ec('ley_projector'), 'MRU', 'dim:minecraft:the_nether'],
      ['loot:%s:ley_projector/ores/minecraft/the_nether' % MODID, 'loot:%s:ley_projector/rock/minecraft/the_nether' % MODID],
      cfg='cfg:machines.ley_projector.enabled'),
    R('地脉投影仪（海纳尔表）', JAVA_PKG + 'blockentity/LeyProjectorBlockEntity.java',
      ['block:' + ec('ley_projector'), 'MRU', 'dim:' + ec('hoanna')],
      ['loot:%s:ley_projector/ores/%s/hoanna' % (MODID, MODID), 'loot:%s:ley_projector/rock/%s/hoanna' % (MODID, MODID)],
      cfg='cfg:machines.ley_projector.enabled'),
    R('地脉凝聚器：有地脉的维度凝原版矿物（只花 MRU；主世界就有地脉）', JAVA_PKG + 'blockentity/LeyCondenserBlockEntity.java',
      ['block:' + ec('ley_condenser'), 'MRU'],
      ['item:minecraft:' + x for x in ('raw_copper', 'raw_iron', 'raw_gold', 'redstone', 'lapis_lazuli', 'quartz',
                                       'diamond', 'emerald', 'ancient_debris')],
      cfg='cfg:machines.ley_condenser.enabled'),
    R('地脉凝聚器：下界之星（MRU + 周围 8 根水晶柱的 ESPE，哪个维度都行）', JAVA_PKG + 'blockentity/LeyCondenserBlockEntity.java',
      ['block:' + ec('ley_condenser'), 'MRU', PILLAR_ANY], ['item:minecraft:nether_star'],
      cfg='cfg:machines.ley_condenser.enabled'),

    # ---------------- 风与海纳尔 ----------------
    R('风之好感：打碎瓶中之风 / 禁锢之风 / 受困之风、喝风之药水、受眷顾后的被动事件', JAVA_PKG + 'player/WindRelations.java',
      ['item:%s:bottled_wind|item:%s:imprisoned_wind|item:%s:wind_keeper|item:%s:air_potion|adv:%s:guide/wind/windbound'
       % (MODID, MODID, MODID, MODID, MODID)], ['cap:wind_affinity']),
    R('捕风挂坠：每次加好感 15% 给一个受困之风', JAVA_PKG + 'player/WindRelations.java',
      ['#' + ec('wind_catcher'), 'cap:wind_affinity'], ['item:' + ec('wind_keeper')]),
    # 第十批 10b 风之试炼（batch10 §5.5）：心愿、号角召来的大法师、风之真名；吹号角也要已受眷顾
    R('风之心愿（受眷顾后在完整的风之符文上问风）', JAVA_PKG + 'player/WindWishes.java',
      ['adv:%s:guide/wind/windbound' % MODID, 'struct:' + ec('wind_rune')], ['cap:wind_wish', 'adv:%s:guide/wind/wish' % MODID],
      cfg='cfg:general.wind_wish.enabled'),
    R('心愿关掉或不要心愿时，风之真名不看心愿', JAVA_PKG + 'item/WindTabletItem.java', [], ['cap:wind_wish'],
      cfg='!cfg:general.wind_wish.enabled||!cfg:general.wind_wish.required>0'),
    R('风之号角（受眷顾、夜里、主世界或海纳尔露天）召来风之大法师', JAVA_PKG + 'item/WindHornItem.java',
      ['item:' + ec('wind_horn'), 'adv:%s:guide/wind/windbound' % MODID, 'dim:minecraft:overworld|dim:' + ec('hoanna')],
      ['entity:' + ec('wind_mage'), 'loot:%s:entities/wind_mage_archmage' % MODID]),
    # 「大法师」一步按拿到受困之风（或风之石板）判，受困之风从哪来都算（号角召来的、自然生成的大法师、捕风挂坠）；进度 JSON 自动生成的规则同样认物品
    R('打倒风之大法师、拿到受困之风（受困之风从哪来都算）', 'data/essentialcraft5/advancements/guide/wind/archmage.json',
      ['item:' + ec('wind_keeper')], ['adv:%s:guide/wind/archmage' % MODID]),
    R('风之真名（好感够 + 心愿够）', JAVA_PKG + 'item/WindTabletItem.java',
      ['cap:wind_affinity', 'cap:wind_wish'], ['adv:%s:guide/hoanna/true_name' % MODID]),
    R('风之石板（好感够、充过 MRU）右键点燃的下界门 → 海纳尔之门', JAVA_PKG + 'item/WindTabletItem.java',
      ['item:' + ec('wind_tablet'), 'cap:wind_affinity', 'cap:wind_wish', 'block:' + ec('charging_chamber'), 'MRU',
       'item:minecraft:obsidian', 'item:minecraft:flint_and_steel'], ['block:' + ec('hoanna_portal')],
      cfg='cfg:general.hoanna.portal_enabled'),
    # 「穿过海纳尔之门」第十三批起要看矩阵阶的门（general.matrix_rank.gates.hoanna_portal），挪到 batch13_rules 按配置生成
    # 辐射不再挂药水效果：剂量 > 0 且辐射开着时 Radiation.refresh 直接发手册步骤 guide/hoanna/radiation（进度是 impossible）
    R('海纳尔里慢慢积累辐射', JAVA_PKG + 'hoanna/Radiation.java', ['dim:' + ec('hoanna')],
      ['effect:' + ec('radiation'), 'adv:%s:guide/hoanna/radiation' % MODID], cfg='cfg:general.radiation.enabled'),

    # ---------------- 腐化 ----------------
    R('靠近 MRU 聚集体受矩阵损伤', JAVA_PKG + 'entity/MRUPresenceEntity.java', ['entity:' + ec('mru_presence')], ['cap:matrix_damage']),
    R('机器工作时向附近聚集体泄漏 MRU（没有就新建）', JAVA_PKG + 'mru/ECCorruption.java', ['MRU'], ['entity:' + ec('mru_presence')]),
    # 腐化效果只从打开过的最高档五芒星治得了的里抽（corruption/CorruptionGate，draw_requires_cure_tier 默认开）：宇宙崩裂是心智类，要 1 档
    # 心智悖论不再是药水效果：CorruptionBehaviors 给上时直接发手册步骤 guide/corruption/paradox（进度是 impossible）
    R('矩阵损伤抽出心智悖论（打开过 1 档五芒星以后）', JAVA_PKG + 'corruption/CorruptionBehaviors.java',
      ['cap:matrix_damage', 'struct:' + ec('player_pentacle_tier1')],
      ['effect:' + ec('mindfold_paradox'), 'adv:%s:guide/corruption/paradox' % MODID],
      cfg='cfg:general.corruption.effects_enabled'),

    # ---------------- 召唤、交易、Boss ----------------
    R('恶魔五芒星花 ESPE 召唤恶魔', JAVA_PKG + 'blockentity/DemonicPentacleBlockEntity.java',
      ['struct:' + ec('demonic_pentacle')], ['summon:' + ec('demon'), 'entity:' + ec('demon')]),
    # 「全息投影台召唤」第十三批起要矩阵阶（general.matrix_rank.gates.holopad），挪到 batch13_rules
    R('全息投影按最后放完的那一招掉四张表之一', JAVA_PKG + 'entity/HologramEntity.java',
      ['entity:' + ec('hologram')], ['loot:%s:entities/hologram_%s' % (MODID, x) for x in ('addition', 'subtraction', 'multiplication', 'division')]),
    R('风法师按类型掉表：学徒 / 法师', JAVA_PKG + 'entity/WindMageEntity.java',
      ['entity:' + ec('wind_mage')], ['loot:%s:entities/wind_mage%s' % (MODID, x) for x in ('', '_apprentice')]),
    # 自然刷出的大法师：三种类型等概率，约占敌对生物的 0.12%，没办法找（batch10.md §2.1），算运气
    R('风法师按类型掉表：自然刷出的大法师', JAVA_PKG + 'entity/WindMageEntity.java',
      ['entity:' + ec('wind_mage')], ['loot:%s:entities/wind_mage_archmage' % MODID], luck=True),
    R('灵魂刻刀收村民的灵魂', JAVA_PKG + 'item/SoulScriberItem.java',
      ['item:' + ec('soul_scriber'), 'entity:minecraft:villager'], ['soul:minecraft:villager', 'item:' + ec('captured_soul')]),
    R('灵魂刻刀收末影人的灵魂', JAVA_PKG + 'item/SoulScriberItem.java',
      ['item:' + ec('soul_scriber'), 'entity:minecraft:enderman'], ['soul:minecraft:enderman', 'item:' + ec('captured_soul')]),
    R('灵魂刻刀收风法师的灵魂', JAVA_PKG + 'item/SoulScriberItem.java',
      ['item:' + ec('soul_scriber'), 'entity:' + ec('wind_mage')], ['soul:' + ec('wind_mage'), 'item:' + ec('captured_soul')]),
    R('ε 腰带：真玩家击杀敌对生物 5% 多掉一个余烬', JAVA_PKG + 'registry/ECVoid.java',
      ['#' + ec('drops_embers')], ['#give:' + ec('embers')]),
    # 地牢箱子里的特殊饰品（捕风挂坠等）：一箱只有一小份几率、地牢本身也稀疏，算运气
    R('原版地牢箱子注入特殊饰品表', JAVA_PKG + 'loot/DungeonLootInjection.java',
      ['loot:minecraft:chests/simple_dungeon'], ['loot:%s:inject/simple_dungeon' % MODID], luck=True),
    R('原版地牢（刷怪笼房）在主世界生成', 'minecraft:monster_room', ['dim:minecraft:overworld'], ['loot:minecraft:chests/simple_dungeon']),

    # ---------------- 只由代码发的进度 ----------------
    R('米斯莱熔炉结构成形（附近玩家 / 打开界面）', JAVA_PKG + 'blockentity/MithrilineFurnaceBlockEntity.java',
      ['struct:' + ec('mithriline_furnace')], ['adv:%s:main/mithriline_furnace' % MODID]),
    R('灵魂五芒星打开界面时 1 档以上', JAVA_PKG + 'blockentity/PlayerPentacleBlockEntity.java',
      ['struct:' + ec('player_pentacle_tier1')], ['adv:%s:guide/pentacle/tier1' % MODID]),
    R('灵魂五芒星打开界面时 2 档以上', JAVA_PKG + 'blockentity/PlayerPentacleBlockEntity.java',
      ['struct:' + ec('player_pentacle_tier2')], ['adv:%s:guide/pentacle/tier2' % MODID]),
    R('灵魂五芒星打开界面时 3 档', JAVA_PKG + 'blockentity/PlayerPentacleBlockEntity.java',
      ['struct:' + ec('player_pentacle_tier3')], ['adv:%s:guide/pentacle/tier3' % MODID, 'adv:%s:main/player_pentacle' % MODID]),
    R('灵魂五芒星治好一个腐化效果（身体类 0 档就能治）', JAVA_PKG + 'blockentity/PlayerPentacleBlockEntity.java',
      ['struct:' + ec('player_pentacle'), 'cap:matrix_damage'], ['adv:%s:guide/pentacle/cure' % MODID],
      cfg='cfg:general.corruption.effects_enabled'),
    R('风之符文注风灵魂石 → 风之眷顾', JAVA_PKG + 'blockentity/WindRuneBlockEntity.java',
      ['struct:' + ec('wind_rune'), 'item:' + ec('soul_stone')], ['adv:%s:guide/wind/windbound' % MODID]),
    # 「矩阵损伤」一步在效果总开关之前发（CorruptionTicker.onPlayerTick 里 updateGuideProgress 在 effects_enabled 判断之前），不带条件；
    # 三类效果只从打开过的最高档五芒星治得了的里抽（corruption/CorruptionGate）：身体 0 档、心智 1 档、矩阵 2 档
    R('有过矩阵损伤', JAVA_PKG + 'corruption/CorruptionTicker.java',
      ['cap:matrix_damage'], ['adv:%s:guide/corruption/matrix_damage' % MODID]),
    R('身体类腐化效果（打开过 0 档五芒星以后才抽）', JAVA_PKG + 'corruption/CorruptionTicker.java',
      ['cap:matrix_damage', 'struct:' + ec('player_pentacle')], ['adv:%s:guide/corruption/body' % MODID],
      cfg='cfg:general.corruption.effects_enabled'),
    R('心智类腐化效果（打开过 1 档五芒星以后才抽）', JAVA_PKG + 'corruption/CorruptionTicker.java',
      ['cap:matrix_damage', 'struct:' + ec('player_pentacle_tier1')], ['adv:%s:guide/corruption/mind' % MODID],
      cfg='cfg:general.corruption.effects_enabled'),
    R('矩阵类腐化效果（开关开着、海纳尔之门开着，打开过 2 档五芒星以后才抽）', JAVA_PKG + 'corruption/CorruptionTicker.java',
      ['cap:matrix_damage', 'struct:' + ec('player_pentacle_tier2')], ['adv:%s:guide/corruption/matrix' % MODID],
      cfg=['cfg:general.corruption.effects_enabled', 'cfg:general.corruption.matrix_effects_enabled', 'cfg:general.hoanna.portal_enabled']),
    R('海纳尔天灾发生时人在海纳尔', JAVA_PKG + 'hoanna/event/HoannaEvents.java',
      ['dim:' + ec('hoanna')], ['adv:%s:guide/hoanna/event' % MODID]),
    # 第十批 10b 中期机制：定向培育、产线、聚集体坍缩（坍缩按仓里的平衡值分两种产物；存 10 万要带存储方块的仓）
    R('水晶控制器把生长中的水晶某元素喂到门槛以上（定向培育）', JAVA_PKG + 'blockentity/CrystalControllerBlockEntity.java',
      ['block:' + ec('crystal_controller'), 'block:' + ec('elemental_crystal'), 'MRU', '|'.join(ESSENCES)],
      ['adv:%s:guide/essence/bred_crystal' % MODID]),
    R('合成器连续多次的原料全经漏斗 / 魔力镜送进来（产线）', JAVA_PKG + 'blockentity/CrafterBlockEntity.java',
      ['block:' + ec('crafter'), 'block:' + ec('magical_hopper') + '|block:minecraft:hopper|block:' + ec('magical_mirror')],
      ['adv:%s:guide/automation/assembly_line' % MODID]),
    R('聚集体坍缩（仓里冰冻）→ 虚空核心', JAVA_PKG + 'blockentity/EnrichmentControllerBlockEntity.java',
      ['struct:' + ec('enrichment_chamber_big'), 'block:' + ec('void_stone') + '|block:' + ec('void_glass') + '|block:' + ec('demonic_plating'),
       'block:' + ec('ec_balancer'), 'entity:' + ec('mru_presence'), 'dim:' + ec('hoanna'), 'MRU[0,0.5]'],
      ['item:' + ec('void_core'), 'adv:%s:guide/hoanna/collapse' % MODID], cfg='cfg:machines.enrichment_chamber.collapse.enabled'),
    R('聚集体坍缩（仓里混沌）→ MRU 反应核', JAVA_PKG + 'blockentity/EnrichmentControllerBlockEntity.java',
      ['struct:' + ec('enrichment_chamber_big'), 'block:' + ec('void_stone') + '|block:' + ec('void_glass') + '|block:' + ec('demonic_plating'),
       'block:' + ec('ec_balancer'), 'entity:' + ec('mru_presence'), 'dim:' + ec('hoanna'), 'MRU[1.5,2]'],
      ['item:' + ec('void_mru_reactor'), 'adv:%s:guide/hoanna/collapse' % MODID], cfg='cfg:machines.enrichment_chamber.collapse.enabled'),
    R('打败全息投影（参战者；四个盔甲槽全空另发一个）', JAVA_PKG + 'entity/HologramEntity.java',
      ['entity:' + ec('hologram')], ['adv:%s:main/hologram' % MODID, 'adv:%s:main/hologram_big' % MODID]),
    # ---------------- 第十批 10c：审判庭（batch10 §5.10；故障核心走四张全息表的数据，脚本自动读） ----------------
    R('辐射仓按核心上的目标平衡值解码故障核心', JAVA_PKG + 'blockentity/GlitchyCoreDecoder.java',
      ['block:' + ec('radiating_chamber'), 'item:' + ec('glitchy_core'), 'MRU'],
      ['item:' + ec('inquisitorium_coordinates'), 'adv:%s:guide/inquisitorium/decoded' % MODID]),
    R('拿着审判庭坐标走到那片暗影腐化之地', JAVA_PKG + 'item/InquisitoriumCoordinatesItem.java',
      ['item:' + ec('inquisitorium_coordinates'), 'dim:' + ec('hoanna')], ['adv:%s:guide/inquisitorium/arrived' % MODID]),
    # 「信标」一步是召唤实体条件（advancements/guide/inquisitorium/beacon.json），由这条给的 summon: 接上；
    # 召唤成功时代码给召唤者补「解码」「到达」（先做好信标再来、没拿着坐标走过来的人）
    R('审判庭信标在坐标处召出审判庭哨兵', JAVA_PKG + 'block/InquisitoriumBeaconBlock.java',
      ['block:' + ec('inquisitorium_beacon'), 'dim:' + ec('hoanna')],
      ['summon:' + ec('inquisitorium_sentinel'), 'entity:' + ec('inquisitorium_sentinel'),
       'adv:%s:guide/inquisitorium/decoded' % MODID, 'adv:%s:guide/inquisitorium/arrived' % MODID]),
    R('打倒审判庭哨兵（参战者）', JAVA_PKG + 'entity/InquisitoriumSentinelEntity.java',
      ['entity:' + ec('inquisitorium_sentinel')], ['loot:%s:entities/inquisitorium_sentinel' % MODID, 'adv:%s:main/inquisitorium' % MODID]),
    R('宅男终端里第一次打开饰品封禁场（要穿齐四件）', JAVA_PKG + 'item/ComputerSealField.java',
      ['item:' + ec('computer_helmet'), 'item:' + ec('computer_chestplate'), 'item:' + ec('computer_leggings'), 'item:' + ec('computer_boots')],
      ['adv:%s:guide/computer/seal_field' % MODID], cfg='cfg:general.computer_seal.enabled'),
    # 第十批 10c 力场穹顶（batch10 §5.13）：放下、接上 MRU，第一次开始工作（有主人、范围里有主人能动的区块、付得起这一秒）时代码发给主人
    R('力场穹顶第一次开始工作', JAVA_PKG + 'blockentity/ForceDomeBlockEntity.java',
      ['block:' + ec('force_dome'), 'MRU'], ['adv:%s:guide/computer/force_dome' % MODID], cfg='cfg:machines.force_dome.enabled'),
    # ---------------- 第十批 10c：终端与模块（batch10 §4.3、§5.11、§5.12） ----------------
    # 「装上两块模块」：ModuleTicker 每秒看到同时生效的模块 ≥ 2 块就发 guide/terminal/modules。R() 会合并重复的 needs 组，
    # 「任意两块不同的模块」写不出来，用两条充分条件（任一条通就算做得到）；六块模块走同一条材料链，不会因此漏报断链。
    # 模块被 general.computer_modules.disabled 停用的情形没算进来（物品照样做得出来，只是装不上、已经装着的不生效）。
    R('穿齐宅男套装、两种模块同时生效（一块头盔模块 + 一块别处的）', JAVA_PKG + 'item/module/ModuleTicker.java',
      SUIT4 + [HELMET_MODULES, BODY_MODULES_A + '|' + BODY_MODULES_B], ['adv:%s:guide/terminal/modules' % MODID]),
    R('穿齐宅男套装、两种模块同时生效（两块非头盔模块）', JAVA_PKG + 'item/module/ModuleTicker.java',
      SUIT4 + [BODY_MODULES_A, BODY_MODULES_B], ['adv:%s:guide/terminal/modules' % MODID]),
    # ---------------- 第十批 10b：风之契约、全息诱饵（batch10 §5.8） ----------------
    R('风之契约：风之真名显全以后好感到门槛（不动如山）', JAVA_PKG + 'player/WindPact.java',
      ['adv:%s:guide/hoanna/true_name' % MODID, 'cap:wind_affinity'], ['adv:%s:guide/wind_mage/steadfast' % MODID],
      cfg='cfg:general.wind.steadfast_points'),
    R('风之契约：风之真名显全以后好感到门槛（风之偏转）', JAVA_PKG + 'player/WindPact.java',
      ['adv:%s:guide/hoanna/true_name' % MODID, 'cap:wind_affinity'], ['adv:%s:guide/wind_mage/deflect' % MODID],
      cfg='cfg:general.wind.deflect_points'),
    R('第一次投出全息诱饵', JAVA_PKG + 'item/HoloDecoyProjectorItem.java',
      ['item:' + ec('holo_decoy_projector'), 'MRU'], ['adv:%s:guide/hologram/decoy' % MODID]),
    # ---------------- 第十批 10b：余烬嵌刻（batch10 §5.7.2） ----------------
    R('第一次从铁砧取出嵌好余烬嵌片的武器', JAVA_PKG + 'item/ember/InlayEvents.java',
      ['item:' + ec('ember_inlay'), 'item:minecraft:anvil', 'item:minecraft:iron_sword|item:minecraft:bow'],
      ['adv:%s:guide/void_machines/ember_inlay' % MODID], cfg='cfg:general.ember_inlay.enabled'),
    # ---------------- 第十二批：源质残卷（batch12.md §3.2、§4.1；卷数照定稿的「序 + 卷一～卷六」） ----------------
    # 图里只有 item:ancient_scroll（卷号是开箱时写的 NBT），按出处拆：序、卷一～卷四主世界的遗迹箱子就有；卷五只在海纳尔的城塔、墓穴；
    # 卷六只在审判庭哨兵身上。多开几个箱子总能凑齐，是「磨」不是运气，不标 luck
    *[R('读源质残卷（%s）' % v, JAVA_PKG + 'item/AncientScrollItem.java', ['item:' + ec('ancient_scroll')],
        ['adv:%s:guide/scrolls/%s' % (MODID, v)]) for v in ('preface', 'book_1', 'book_2', 'book_3', 'book_4')],
    R('读源质残卷（book_5）', JAVA_PKG + 'item/AncientScrollItem.java', ['item:' + ec('ancient_scroll'), 'dim:' + ec('hoanna')],
      ['adv:%s:guide/scrolls/book_5' % MODID], cfg='cfg:general.hoanna.portal_enabled'),
    R('读源质残卷（book_6）', JAVA_PKG + 'item/AncientScrollItem.java', ['item:' + ec('ancient_scroll'), 'entity:' + ec('inquisitorium_sentinel')],
      ['adv:%s:guide/scrolls/book_6' % MODID], cfg='cfg:general.hoanna.portal_enabled'),
    # 集齐只写门关着时的 5 卷：门开着时卷五、卷六在这条以外各自可达，工具要的是「可达」，不是最短路
    R('集齐源质残卷（门关着时 5 卷）', JAVA_PKG + 'lore/ScrollVolumes.java',
      ['adv:%s:guide/scrolls/%s' % (MODID, v) for v in ('preface', 'book_1', 'book_2', 'book_3', 'book_4')],
      ['adv:%s:guide/scrolls/complete' % MODID]),
    # ---------------- 第十二批：元素分解器（batch12.md §3.2、§4.8）：标签里的杂物按元素攒点数，出普通 / 罕见精华 ----------------
    *[R('元素分解器：%s' % e, JAVA_PKG + 'blockentity/ElementalDecomposerBlockEntity.java',
        ['block:' + ec('elemental_decomposer'), '#%s:decomposer/%s' % (MODID, e)],
        ['item:' + ec('common_%s_essence' % e), 'item:' + ec('uncommon_%s_essence' % e)]) for e in ('fire', 'water', 'earth', 'air')],

    # ---------------- 第十三批 13a：矩阵阶、同调、契约之誉（batch13.md §2.7；阶本身由 matrix_ranks 数据生成，门在 batch13_rules） ----------------
    # 三阶要「属性不是中性」：举着任一种矩阵投影 10 秒换属性（原作代价照旧）
    R('举着矩阵投影换矩阵属性（不再是中性）', JAVA_PKG + 'item/MatrixProjectionItem.java', [GUN_LENS], ['cap:matrix_type']),
    # 试炼成功记 1 000 × 新阶的矩阵损伤（走有上限的 addMatrixDamage）
    R('矩阵阶试炼成功记矩阵损伤', JAVA_PKG + 'matrix/MatrixTrials.java', ['cap:matrix_rank_1'], ['cap:matrix_damage'],
      cfg='cfg:general.matrix_rank.enabled'),
    # 同调度：穿着盔甲挨敌对生物打（要先打实它）、拿着武器打实 / 击杀敌对生物；任何能打的 Enemy 都给，确定来源（§3.3.2）
    R('同调度：和敌对生物交手（穿着的盔甲、手里的武器）', JAVA_PKG + 'item/attunement/AttunementGains.java',
      ['entity:minecraft:zombie'], ['cap:attunement_points'], cfg='cfg:general.attunement.enabled'),
    # 第二十一章「宅男四件都到同调 2 阶」：四件都穿着、最低一件到 2 阶时，写盘那一刻代码发（AttunementGains.checkTerminalStep）
    R('宅男套装四件都到同调 2 阶', JAVA_PKG + 'item/attunement/AttunementGains.java',
      ['cap:attunement/%s@2' % x[len('item:'):] for x in SUIT4], ['adv:%s:guide/terminal/attuned' % MODID]),

    # ---------------- 第十三批 13c：新怪物与首领（batch13.md §5；手册支线 bestiary） ----------------
    # 元素游灵、炼狱小鬼走生物群系修改器（forge:add_spawns / essentialcraft5:add_spawn_costs，load_worldgen 读）；野外怪的掉落表
    # entities/<名>.json 照默认规则挂，池子带公平击杀 / 按人递减（fair_kill / kill_fatigue）的自动当运气（LUCK_LOOT_CONDITIONS）；
    # 首领（#forge:bosses）的表不算运气。这里只写代码里定的刷法、掉法和首领。
    # 矩阵游魂不进群系刷怪表：≥ 5 000 MRU、没被稳定器压着的聚集体 24 格内往刷怪表追加（所有维度），刷不刷得出来看运气
    R('矩阵游魂在存着 5 000 MRU 以上、没被稳定器压着的聚集体附近追加刷出', JAVA_PKG + 'entity/mob/MatrixWraithEvents.java',
      ['entity:' + ec('mru_presence')], ['entity:' + ec('matrix_wraith')], luck=True),
    # 风暴术士是风法师的第四种（Type 3），只在自然刷出时掷（雷雨 1/5、海纳尔 10%）；掉的受困之风带「风暴余息」，只当材料（注不进风之好感）
    R('风法师按类型掉表：自然刷出的风暴术士（雷雨时）', JAVA_PKG + 'entity/StormSorcerer.java',
      ['entity:' + ec('wind_mage')], ['loot:%s:entities/wind_mage_storm' % MODID], luck=True),
    R('虚空潜行者：末地外岛独立生成器（不进群系刷怪表）', JAVA_PKG + 'entity/mob/VoidStalkerSpawner.java',
      ['dim:minecraft:the_end'], ['entity:' + ec('void_stalker')], luck=True),
    R('灵魂刻刀收炼狱小鬼的灵魂（精英收不了）', JAVA_PKG + 'item/SoulScriberItem.java',
      ['item:' + ec('soul_scriber'), 'entity:' + ec('nether_imp')], ['soul:' + ec('nether_imp'), 'item:' + ec('captured_soul')]),
    # 第十三批 13d：遗民拾荒者（残血补刀必中，不靠运气；遗民队长和它叫来的收不了）
    R('灵魂刻刀收遗民拾荒者的灵魂（遗民队长、队长叫来的收不了）', JAVA_PKG + 'item/SoulScriberItem.java',
      ['item:' + ec('soul_scriber'), 'entity:' + ec('hoanna_scavenger')], ['soul:' + ec('hoanna_scavenger'), 'item:' + ec('captured_soul')]),
    # 聚能魔像只守 13c 起新生成的上古遗迹（site 节点由 FEATURE_TYPE_OUTPUTS 的 elder_mrucu 给；老存档的遗迹不触发；
    # 主世界每座都有，海纳尔约五分之一），生存 / 冒险玩家走近 8 格、能动那一格时在遗迹正上方的地面上醒来。
    # 前提是配置 general.mobs.mru_golem.trigger_radius > 0（填 0 不再出来）；海纳尔那部分还要 hoanna_chance > 0。
    # 这里按默认配置求；改了这两项、或者主世界和海纳尔都已预生成完的世界，上古晶核就没有来源（只影响四条可选的同调升阶）
    R('走近新遗迹的聚集体，聚能魔像醒来', JAVA_PKG + 'entity/mob/MRUGolemTrigger.java',
      ['site:' + ec('elder_mrucu')], ['entity:' + ec('mru_golem')]),
    # 矩阵之影：手持导流符右键自己能动的、1 档以上的灵魂五芒星中心块，召唤者矩阵损伤 ≥ 18 000，
    # 从那座五芒星的柱子付 a × 0.5 有效 ESPE（1 档柱子攒满 43 200，够 a 封顶 72 000 的 36 000）
    R('矩阵导流符召出矩阵之影', JAVA_PKG + 'entity/boss/MatrixShadeSummon.java',
      ['item:' + ec('matrix_conduit'), '|'.join(PENTACLE_TIER_NODES_13C), 'cap:matrix_damage'],
      ['summon:' + ec('matrix_shade'), 'entity:' + ec('matrix_shade')]),
    # 首领的掉落走默认的 entities/<名> 表（每名打出一成以上伤害的参战者各一份）；召唤者多拿的矩阵结晶由 MatrixShadeEntity 代码给，
    # 表里那一颗已经让矩阵结晶拿得到。异兽录两篇可选步骤的进度只由 BossLoot.recordVictory 发给分到掉落的人（两只首领同一条规则）
    R('打倒聚能魔像：分得到掉落的人发异兽录进度', JAVA_PKG + 'entity/boss/BossLoot.java',
      ['entity:' + ec('mru_golem')], ['adv:%s:guide/bestiary/mru_golem' % MODID]),
    R('打倒矩阵之影：分得到掉落的人发异兽录进度', JAVA_PKG + 'entity/boss/BossLoot.java',
      ['entity:' + ec('matrix_shade')], ['adv:%s:guide/bestiary/matrix_shade' % MODID]),

    # ---------------- 第十三批 13d：海纳尔探索（batch13.md §6；手册支线 hoanna_explore） ----------------
    # 数据里看得出来的不在这里重写：潮晶芽、辐晶簇、影晶簇、渊盐结壳的地物是 random_patch / disk（JSON 里写着方块）；
    # 榍石矿、辐晶簇、霜髓、渊盐结壳、潮晶芽（长熟的不带精准挖掉 1 颗）的掉落、本地怪的掉落（含精英的异变之心）、
    # 勘测图配方都走数据文件。海纳尔的四个战利品条件按「有条件、不算运气」照常收（CONDITIONAL_LOOT_CONDITIONS），
    # from_nest 下面的东西要有巢穴（NEST_LOOT_CONDITIONS）。
    # 自定义 Java 地物放的方块（JSON 里看不出放了什么）
    R('世界生成：地脉喷口（海纳尔六个陆地群系，混沌最密）', JAVA_PKG + 'worldgen/hoanna/feature/LeyVentFeature.java',
      ['dim:' + ec('hoanna')], ['block:' + ec('ley_vent')]),
    R('世界生成：冰冻群系的浮冰刺里嵌着霜髓', JAVA_PKG + 'worldgen/hoanna/feature/MarrowSpikeFeature.java',
      ['dim:' + ec('hoanna')], ['block:' + ec('frost_marrow')]),
    R('世界生成：沙漠群系的砂岩露头嵌着榍石矿', JAVA_PKG + 'worldgen/hoanna/feature/OutcropFeature.java',
      ['dim:' + ec('hoanna')], ['block:' + ec('titanite_ore')]),
    R('世界生成：海纳尔陆地路边的里程碑柱（三格强化石 + 顶上一块里程碑）', JAVA_PKG + 'worldgen/hoanna/feature/MilestoneFeature.java',
      ['dim:' + ec('hoanna')], ['block:' + ec('ruin_milestone')]),
    # 中景观（冰封营地 / 混沌裂隙 / 辐射陨坑）：每处正中一块巢穴核心，裂隙两侧是满充能的喷口、营地外圈冰刺嵌霜髓、陨坑底长辐晶簇
    R('世界生成：中景观里的巢穴核心（冰冻、混沌、恐惧之地）', JAVA_PKG + 'worldgen/hoanna/feature/HoannaMidscape.java',
      ['dim:' + ec('hoanna')], ['block:' + ec('nest_core'), 'block:' + ec('ley_vent'), 'block:' + ec('frost_marrow'),
                                'block:' + ec('radiant_cluster')]),
    # 喷口：充能随时间回（天灾里更快），真玩家空手右键按充能给地脉结晶；可再生，不靠运气
    R('地脉喷口：空手右键收地脉结晶', JAVA_PKG + 'block/hoanna/LeyVentBlock.java',
      ['block:' + ec('ley_vent'), 'dim:' + ec('hoanna')], ['item:' + ec('ley_crystal')]),
    # 地脉风暴（大潮夜黄昏掷）里头顶露天时身边落下落晶，碰到就收；第一次收发进度
    R('地脉风暴的落晶：露天时碰到就收', JAVA_PKG + 'hoanna/event/LeyStorm.java',
      ['dim:' + ec('hoanna')], ['item:' + ec('ley_crystal'), 'adv:%s:guide/hoanna_explore/starfall' % MODID], cfg=LEY_STORM_CFG),
    # 潮晶芽：大潮夜里一阶一阶长，长熟（stage 3）右键收潮晶、退回 0 阶。潮汐关掉时改成每个夜里都长（几率 ×3/8，
    # 平均产量不变），所以不挂潮汐开关：关掉潮汐时潮晶照样能再生
    R('潮晶芽：夜里长熟后右键收潮晶', JAVA_PKG + 'block/hoanna/TideBudBlock.java',
      ['block:' + ec('tide_bud'), 'dim:' + ec('hoanna')], ['item:' + ec('tide_crystal')]),
    # 影晶簇：成熟时右键收（夜里或黑暗天灾中 1~2，白天看几率），退成残簇；残簇在暗影群系夜里回长
    R('影晶簇：成熟时右键收影晶', JAVA_PKG + 'block/hoanna/UmbralClusterBlock.java',
      ['block:' + ec('umbral_cluster'), 'dim:' + ec('hoanna')], ['item:' + ec('umbral_crystal')]),
    # 残破勘测图：城塔、墓穴箱子表里单开一池（tools/gen/hoanna_loot.py 生成）；按定稿当运气
    R('城塔 / 墓穴箱子里的残破勘测图', JAVA_PKG + 'loot/HoannaLoot.java + tools/gen/hoanna_loot.py',
      ['loot:%s|loot:%s' % (ec('chests/town_tower'), ec('chests/catacombs'))], ['item:' + ec('torn_survey_map')], luck=True),
    # 勘测图：在海纳尔里右键绑定最近的一处（13e 起四种目标：宝库 → 沉城 → 前哨 → 城镇，潜行右键换），走到 24 格内发进度并解绑；
    # 到哪一种都发同一个 survey_arrive
    R('勘测图：照着图走到要找的那一处（城镇废墟或 13e 的三座结构）', JAVA_PKG + 'item/HoannaSurveyMapItem.java',
      ['item:' + ec('hoanna_survey_map'), 'dim:' + ec('hoanna'),
       '|'.join('structure:' + ec(x) for x in ('hoanna_town',) + HOANNA_STRUCTURES_13E)],
      ['adv:%s:guide/hoanna_explore/survey_arrive' % MODID]),
    # 巢穴核心：有人走近召出一波（领头的是精英），整波打完给 16 格内出过力的真玩家各一份奖励表、发进度；
    # 奖励表不带公平击杀条件，「有条件、不算运气」。前哨营地、沉城守卫两种由 13e 的结构放（下面 13e 那段）
    *[R('巢穴（%s）：整波打完每名出过力的人各一份奖励' % nid, JAVA_PKG + 'blockentity/NestCoreBlockEntity.java',
        ['block:' + ec('nest_core'), 'dim:' + ec('hoanna')],
        ['summon:' + ec(mob), 'entity:' + ec(mob), 'loot:%s:hoanna_nests/%s' % (MODID, nid),
         'adv:%s:guide/hoanna_explore/nest' % MODID]) for nid, mob in HOANNA_NESTS_13D],

    # ---------------- 第十三批 13e：海纳尔结构与终章（batch13.md §2.2、§3.4、§3.5、§5.3.3~§5.3.5、§6.4） ----------------
    # 跟配置值有关的几条（暗影核心坍缩、立誓、天灾信标召唤、城镇守望者触发）在 batch13_rules；结构里放的方块在 STRUCTURE_OUTPUTS。
    # 首领掉落走默认的 entities/<名> 表（首领表不算运气）；暗渎水晶柱、天灾信标的配方是数据文件，脚本自动读。
    # 4 档五芒星：3 档之上再立 4 根暗渎水晶柱（柱下垫虚空石；坐标见 ECPentacle.PILLAR_OFFSETS 第五份）
    R('结构：灵魂五芒星 4 档（+ 4 根暗渎水晶柱，柱下垫虚空石）', JAVA_PKG + 'registry/ECPentacle.java',
      ['struct:' + ec('player_pentacle_tier3'), 'block:' + ec('shade_crystal_pillar'), 'block:' + ec('void_stone')],
      ['struct:' + ec('player_pentacle_tier4')]),
    R('灵魂五芒星打开界面时 4 档', JAVA_PKG + 'blockentity/PlayerPentacleBlockEntity.java',
      ['struct:' + ec('player_pentacle_tier4')], ['adv:%s:guide/pentacle/tier4' % MODID]),
    # 第二十二章第一步：举着暗影矩阵投影把矩阵属性换成暗影时代码发（七阶以下也换得了，相性八阶起才生效）
    R('举着暗影矩阵投影把矩阵属性换成暗影', JAVA_PKG + 'item/MatrixProjectionItem.java',
      ['item:' + ec('shade_matrix_projection')], ['adv:%s:guide/shade/attune' % MODID]),
    # 封印宝库：一次尝试先往锁孔放 1 个地脉结晶，再按碑文顺序往四座祭座放元素晶石；答对时在前厅里的真玩家进解开名单，
    # 名单上的人右键宝库核心各领一份 chests/sealed_vault_reward（先辈残核必给，每人每座一次）。答错只退一半、要多试几次是「磨」
    R('封印宝库：解开碑文谜题后右键宝库核心领一份（每人每座一次）', JAVA_PKG + 'blockentity/VaultCoreBlockEntity.java',
      ['structure:' + ec('sealed_vault'), 'block:' + ec('vault_core')] + VAULT_ATTEMPT,
      ['loot:%s:chests/sealed_vault_reward' % MODID]),
    # 沉城、前哨的箱子：沉房里的箱子一半几率才有，前哨营地各一个；按定稿当运气（主线不靠它们）
    R('沉没城区的箱子（穹顶里 2 个、沉房 50% 各 1 个）', JAVA_PKG + 'worldgen/hoanna/sunken/SunkenPieces.java + tools/gen/hoanna_loot_sunken.py',
      ['structure:' + ec('sunken_district')], ['loot:%s:chests/sunken_district' % MODID], luck=True),
    R('审判庭前哨营地的箱子', JAVA_PKG + 'worldgen/hoanna/outpost/OutpostPieces.java + tools/gen/hoanna_loot_outpost.py',
      ['structure:' + ec('inquisitorium_outpost')], ['loot:%s:chests/inquisitorium_outpost' % MODID], luck=True),
    # 结构里的两种巢穴（同 13d 的三种：整波打完每名出过力的人各一份奖励，有条件、不算运气）
    *[R('巢穴（%s，%s 里）：整波打完每名出过力的人各一份奖励' % (nid, sid), JAVA_PKG + 'blockentity/NestCoreBlockEntity.java',
        ['structure:' + ec(sid), 'block:' + ec('nest_core')],
        ['summon:' + ec(mob), 'entity:' + ec(mob), 'loot:%s:hoanna_nests/%s' % (MODID, nid),
         'adv:%s:guide/hoanna_explore/nest' % MODID]) for nid, sid, mob in HOANNA_NESTS_13E],
    # 渊潮领主：沉城穹顶中央的祭坛，手持潮晶 ×4 + 异变之心 ×1 右键召（真玩家、非和平、能动祭坛那格、64 格内没有别的首领；
    # 同一座每游戏日一次）。掉落每名玩家每游戏日只领一份——是计时，不是运气
    R('沉城祭坛：潮晶 + 异变之心召出渊潮领主（每座每游戏日一次）', JAVA_PKG + 'block/SunkenAltarBlock.java + ' + JAVA_PKG + 'blockentity/SunkenAltarBlockEntity.java',
      ['block:' + ec('sunken_altar'), 'item:' + ec('tide_crystal'), 'item:' + ec('aberrant_heart')],
      ['summon:' + ec('abyss_tyrant'), 'entity:' + ec('abyss_tyrant')]),
    # 异兽录三只首领 + 天灾之心的主线进度：只由 BossLoot.recordVictory 发给分到掉落的人（每名 ≥ 10% 伤害的参战者）
    R('打倒废城守望者：分得到掉落的人发异兽录进度', JAVA_PKG + 'entity/boss/BossLoot.java',
      ['entity:' + ec('town_sentry')], ['adv:%s:guide/bestiary/town_sentry' % MODID]),
    R('打倒渊潮领主：分得到掉落的人发异兽录进度', JAVA_PKG + 'entity/boss/BossLoot.java',
      ['entity:' + ec('abyss_tyrant')], ['adv:%s:guide/bestiary/abyss_tyrant' % MODID]),
    R('打倒天灾之心：分得到掉落的人发主线进度和异兽录进度', JAVA_PKG + 'entity/boss/BossLoot.java',
      ['entity:' + ec('calamity_heart')], ['adv:%s:main/calamity_heart' % MODID, 'adv:%s:guide/bestiary/calamity_heart' % MODID]),
]


# ======================================================================
# 第十三批 13a：矩阵图谱、矩阵阶的门、凝结、同调
# ======================================================================

# 矩阵图谱按「种类」记（batch13.md §3.1.1）：原版能凑到的敌对生物 35 种（主世界常见 14 + 末影螨、远征 8、下界 9、末地 2、凋灵）。
# 原版实体节点开局就算有，杀它要去的维度另记在这里，不然末地的潜影贝会被当成主世界就杀得到；凋灵的头颅在下界
VANILLA_HOSTILES = collections.OrderedDict(
    [('minecraft:' + x, 'minecraft:overworld') for x in (
        'zombie', 'husk', 'drowned', 'skeleton', 'stray', 'creeper', 'spider', 'cave_spider', 'enderman', 'witch', 'slime',
        'phantom', 'silverfish', 'zombie_villager', 'endermite',
        'pillager', 'vindicator', 'evoker', 'vex', 'ravager', 'guardian', 'elder_guardian', 'warden')]
    + [('minecraft:' + x, 'minecraft:the_nether') for x in (
        'blaze', 'ghast', 'magma_cube', 'wither_skeleton', 'piglin', 'piglin_brute', 'hoglin', 'zoglin', 'zombified_piglin', 'wither')]
    + [('minecraft:' + x, 'minecraft:the_end') for x in ('shulker', 'ender_dragon')])
# 本模组能进图谱的敌对生物（Enemy 或 MobCategory.MONSTER、不在 growth_excluded 里）；首领另按 #forge:bosses 算，不用列在这里。
# 第十三批 13c 起的新怪加在这里（风暴术士是风法师的一种，不另算）
EC_CODEX = [ec('wind_mage')] + [ec(x) for x in ('elemental_wisp', 'matrix_wraith', 'nether_imp', 'void_stalker')]
# 第十三批 13d 的海纳尔本地怪（同时在实体标签 hoanna_natives 里，「海纳尔原生」组按标签数）
EC_CODEX += [ec(x) for x in HOANNA_NATIVES_13D]
# 图谱分组（§3.1.1）：组名 → 中文。total 总数、nether 在下界杀的（按 codex_nether 的九种数）、hoanna 在海纳尔杀的（另存一份）、
# natives 海纳尔原生（标签不够数时按实际数）、bosses 首领（#forge:bosses）、mod 本模组、end 末地（codex_end）、expedition 远征（codex_expedition）
CODEX_GROUPS = collections.OrderedDict([
    ('total', '总数'), ('nether', '下界'), ('hoanna', '海纳尔里'), ('natives', '海纳尔原生'), ('bosses', '首领'),
    ('mod', '本模组'), ('end', '末地'), ('expedition', '远征')])
# 阶数据里图谱条件的写法（别名都认，batch13.md §3.1.3 的列名）
CODEX_KEY_ALIASES = {
    'total': 'total', 'count': 'total', 'any': 'total', 'nether': 'nether', 'hoanna': 'hoanna', 'in_hoanna': 'hoanna',
    'natives': 'natives', 'native': 'natives', 'hoanna_natives': 'natives', 'hoanna_native': 'natives',
    'bosses': 'bosses', 'boss': 'bosses', 'mod': 'mod', 'modded': 'mod', 'own': 'mod', MODID: 'mod',
    'end': 'end', 'the_end': 'end', 'expedition': 'expedition', 'expeditions': 'expedition',
}
CODEX_ENTITY_KEYS = ('entities', 'entity', 'include', 'includes', 'required', 'requires', 'must')
CODEX_TYPE_KEYS = ('matrix_type', 'non_neutral', 'attuned', 'requires_type', 'not_neutral', 'requires_alignment')
# 五芒星站点档位 → 结构节点（4 档第十三批 13e 加）
PENTACLE_TIER_NODES = {0: 'struct:' + ec('player_pentacle')}
PENTACLE_TIER_NODES.update({t: 'struct:%s_tier%d' % (ec('player_pentacle'), t) for t in range(1, 5)})
# 同调：家族标签前缀、部位（升阶配方的 parts）按物品名后缀认
ATTUNEMENT_TAG_PREFIX = MODID + ':attunement/'
ATTUNEMENT_PARTS = {'head': ('_helmet',), 'helmet': ('_helmet',), 'chest': ('_chestplate',), 'chestplate': ('_chestplate',),
                    'legs': ('_leggings',), 'leggings': ('_leggings',), 'feet': ('_boots',), 'boots': ('_boots',)}
ATTUNEMENT_ASCENSION = MODID + ':attunement_ascension'
ATTUNEMENT_TRANSFER = MODID + ':attunement_transfer'
# 只能靠同调升格拿到的物品 → (升格配方中心件的家族标签, 中心件要在的阶)。
# 裁决之刃只从 2 阶余烬元素剑升格来（batch13.md §4.4、§4.5），不进战利品、不另开配方
ASCENSION_ONLY = {
    ec('verdict_blade'): (ATTUNEMENT_TAG_PREFIX + 'ember_sword', 2),
}


def config_value(world, cfg_over, key, default=None):
    return cfg_over.get(key, world.config.get(key, default))


def config_int(world, cfg_over, key, default=0):
    try:
        return int(float(config_value(world, cfg_over, key, default)))
    except (TypeError, ValueError):
        return default


def config_list(world, cfg_over, key, default):
    """列表型配置：--cfg 给的是逗号分开的字符串，TOML 默认值是列表"""
    v = config_value(world, cfg_over, key, default)
    if isinstance(v, str):
        v = [x for x in re.split(r'[,\s\[\]"]+', v) if x]
    return list(v) if isinstance(v, (list, tuple)) else list(default)


def matrix_gate(world, cfg_over, key, ranks_loaded=True):
    """矩阵阶的门要几阶：矩阵阶关掉或配 0 时是 0（不设门）；一份阶数据都没有时也按 0 算（见 batch13_rules）"""
    if not ranks_loaded or not world.cond('cfg:general.matrix_rank.enabled', cfg_over):
        return 0
    return max(config_int(world, cfg_over, 'general.matrix_rank.gates.' + key, 0), 0)


# 矩阵阶的三道门（general.matrix_rank.gates.*）
MATRIX_GATES = ('hoanna_portal', 'holopad', 'calamity_beacon')

# 矩阵阶数据的读取器：它在、数据却一份都没有时是数据问题（游戏里谁都升不了阶、门永远关着）
MATRIX_RANK_READER = JAVA_PKG + 'matrix/MatrixRankData.java'


def batch13_rules(world, cfg_over, ranks_loaded=True):
    """跟配置值有关的代码规则（门要几阶、凝结的平衡值窗口），每种情形按配置现算。
    ranks_loaded：读到了矩阵阶数据。没有数据时门按开着算——矩阵阶的数据和读取器是同一路一起加的，
    读取器也还没有时是那一路还没合进来（只记提示），读取器在、数据没有才是问题（Graph.load_code_rules 报）"""
    rules = []
    portal = matrix_gate(world, cfg_over, 'hoanna_portal', ranks_loaded)
    rules.append(R('穿过海纳尔之门' + ('（要矩阵 %d 阶，general.matrix_rank.gates.hoanna_portal）' % portal if portal else ''),
                   JAVA_PKG + 'hoanna/portal/HoannaTeleport.java + ' + JAVA_PKG + 'matrix/MatrixRank.java',
                   ['block:' + ec('hoanna_portal')] + (['cap:matrix_rank_%d' % portal] if portal else []), ['dim:' + ec('hoanna')]))
    holopad = matrix_gate(world, cfg_over, 'holopad', ranks_loaded)
    rules.append(R('全息投影台放在海纳尔的露天处右键' + ('（要矩阵 %d 阶，general.matrix_rank.gates.holopad）' % holopad if holopad else ''),
                   JAVA_PKG + 'block/HolopadBlock.java + ' + JAVA_PKG + 'matrix/MatrixRank.java',
                   ['block:' + ec('holopad'), 'dim:' + ec('hoanna')] + (['cap:matrix_rank_%d' % holopad] if holopad else []),
                   ['summon:' + ec('hologram'), 'entity:' + ec('hologram')]))
    # 凝结矩阵（§3.5）：任何维度；外壳里魔力 / 淡青护板合计 ≥ 8（#condense_casing）、稳定器、存量够的聚集体、仓里的 MRU、平衡值在中间。
    # 仓不带存储方块时上限是 machines.enrichment_chamber.max_mru，凝结要的 MRU 比它多才要带存储方块的大仓
    key = 'machines.enrichment_chamber.condense.'
    lo = config_value(world, cfg_over, key + 'balance_min', None)
    hi = config_value(world, cfg_over, key + 'balance_max', None)
    # 0 是合法的平衡值（MRUBalance.MIN），只在没配这一项时退回默认
    lo = 0.8 if lo is None else float(lo)
    hi = 1.2 if hi is None else float(hi)
    need_mru = config_int(world, cfg_over, key + 'mru', 50000)
    big = need_mru > config_int(world, cfg_over, 'machines.enrichment_chamber.max_mru', 60000)
    rules.append(R('富集仓凝结（平衡值 %.2f~%.2f）→ 凝结矩阵' % (lo, hi), JAVA_PKG + 'blockentity/EnrichmentControllerBlockEntity.java',
                   ['struct:' + ec('enrichment_chamber_big' if big else 'enrichment_chamber'), '#block:' + ec('condense_casing'),
                    'block:' + ec('ec_balancer'), 'entity:' + ec('mru_presence'), 'MRU[%s,%s]' % (lo, hi)],
                   ['item:' + ec('condensed_matrix')], cfg='cfg:' + key + 'enabled'))
    # 第十三批 13e 暗影核心（§3.5，坍缩第三分支）：海纳尔、控制器中心或周围 site_radius 格内有一点在 #shade_core_sites、
    # 虚空级外壳（#collapse_casing）、稳定器、存满的聚集体、仓里的 MRU、外壳里丢着影晶；平衡值不限。暗影条件满足时优先于坍缩和凝结
    key = 'machines.enrichment_chamber.shade.'
    need_mru = config_int(world, cfg_over, key + 'mru', 100000)
    big = need_mru > config_int(world, cfg_over, 'machines.enrichment_chamber.max_mru', 60000)
    rules.append(R('富集仓坍缩（暗影分支：暗影腐化之地或旁边、丢进影晶）→ 暗影核心', JAVA_PKG + 'blockentity/EnrichmentControllerBlockEntity.java',
                   ['struct:' + ec('enrichment_chamber_big' if big else 'enrichment_chamber'), '#block:' + ec('collapse_casing'),
                    'block:' + ec('ec_balancer'), 'entity:' + ec('mru_presence'), 'dim:' + ec('hoanna'),
                    'biome:#' + ec('shade_core_sites'), 'item:' + ec('umbral_crystal'), 'MRU'],
                   ['item:' + ec('shade_core')], cfg='cfg:' + key + 'enabled'))
    # 挚契（§3.4）：契约之誉到 oath 档，潜行拿着影晶右键自己召的恶魔立誓 → 恶魔之誓。确定路线
    oath = config_int(world, cfg_over, 'general.demon.standing.oath', 70)
    rules.append(R('挚契：契约之誉 %d 以上，潜行拿影晶右键恶魔立誓 → 恶魔之誓' % oath,
                   JAVA_PKG + 'demon/DemonStanding.java + ' + JAVA_PKG + 'entity/DemonEntity.java',
                   ['summon:' + ec('demon'), 'item:' + ec('umbral_crystal')] + (['cap:demon_standing_%d' % oath] if oath > 0 else []),
                   ['item:' + ec('demon_oath')], cfg='cfg:general.demon.standing.enabled'))
    # 天灾之心（§5.3.5）：手持天灾信标右键海纳尔地面（矩阵阶门 gates.calamity_beacon；海纳尔此刻有天灾——等得到，有条件、不算运气；
    # 露天、平地、五处能动）。信标打赢后变「空」回到手里，再召吃暗影核心；没打赢原样退回。
    # 天灾不会自己发生（chance_multiplier 0、天灾全关）时 require_calamity 按关掉算（CalamityHeartSummon.requiresCalamity），
    # 随时能召、多扣暗影核心，所以这条不带天灾的条件
    beacon = matrix_gate(world, cfg_over, 'calamity_beacon', ranks_loaded)
    rules.append(R('天灾信标在海纳尔召出天灾之心' + ('（要矩阵 %d 阶，general.matrix_rank.gates.calamity_beacon）' % beacon if beacon else ''),
                   JAVA_PKG + 'entity/boss/CalamityHeartSummon.java + ' + JAVA_PKG + 'item/CalamityBeaconItem.java + '
                   + JAVA_PKG + 'matrix/MatrixRank.java',
                   ['item:' + ec('calamity_beacon'), 'dim:' + ec('hoanna')] + (['cap:matrix_rank_%d' % beacon] if beacon else []),
                   ['summon:' + ec('calamity_heart'), 'entity:' + ec('calamity_heart')]))
    # 废城守望者（§5.3.3）：海纳尔城镇起点 trigger_radius 格内有人（能动起点那一格）时醒，每座城一只；更新前生成的老城镇也守。
    # trigger_radius 填 0 不再出来（只影响可选的同调升阶材料）
    radius = config_int(world, cfg_over, 'general.town_sentry.trigger_radius', 32)
    if radius > 0:
        rules.append(R('走近海纳尔城镇废墟的起点，废城守望者醒来', JAVA_PKG + 'entity/boss/TownSentryTrigger.java',
                       ['structure:' + ec('hoanna_town')], ['entity:' + ec('town_sentry')]))
    return rules

# 战利品条件里算「运气」的（第十三批 13c：野外怪物的关键掉落；batch13.md §5.4）
LUCK_LOOT_CONDITIONS = {MODID + ':fair_kill', MODID + ':kill_fatigue'}
# 第十三批 13d 的海纳尔条件（loot/condition/HoannaLootConditions）：天灾、头顶露天、夜里或黑暗天灾、潮位。
# 等得到、守得到，「有条件、不算运气」：条目照常收（列在这里只为说明，不进 LUCK_LOOT_CONDITIONS）
CONDITIONAL_LOOT_CONDITIONS = {MODID + ':hoanna_event', MODID + ':sky_visible', MODID + ':night_or_darkness', MODID + ':hoanna_tide'}
# 只在巢穴放出来的怪身上成立（from_nest，没取反）：下面的东西另要有巢穴核心；取反（minecraft:inverted）的照常收
NEST_LOOT_CONDITIONS = {MODID + ':from_nest'}
NEST_NODE = 'block:' + ec('nest_core')

# 代码规则里按群系判的地点（节点 biome:#<群系标签>，load_worldgen 按标签对应的维度给）。
# 第十三批 13e 暗影核心：仓要在暗影腐化之地（shade_core_sites，默认 = inquisitorium_sites）或它旁边
BIOME_SITES = ['#' + ec('shade_core_sites')]

# 世界生成里自定义的地物、结构：放了什么方块、带什么箱子表（代码里写的）
FEATURE_TYPE_OUTPUTS = {
    # 第十二批起箱子用 chests/elder_mrucu（整张引用原版地牢表 + 残卷池，loot_table 引用照着展开）
    # site:elder_mrucu = 上古遗迹本身（第十三批 13c 起聚能魔像守着新生成的遗迹，触发要遗迹、不是随便一团聚集体）
    ec('elder_mrucu'): (JAVA_PKG + 'worldgen/ElderMRUCUFeature.java',
                        ['entity:' + ec('mru_presence'), 'loot:' + ec('chests/elder_mrucu'), 'site:' + ec('elder_mrucu')]),
}
VANILLA_FEATURE_OUTPUTS = {
    'minecraft:monster_room': ['loot:minecraft:chests/simple_dungeon'],
    'minecraft:monster_room_deep': ['loot:minecraft:chests/simple_dungeon'],
}
STRUCTURE_OUTPUTS = {
    ec('hoanna_town'): (JAVA_PKG + 'worldgen/hoanna/town/HoannaTownPieces.java',
                        ['loot:' + ec('chests/town_tower')] + ['block:' + ec(x) for x in ('void_stone', 'void_fence', 'concrete', 'levitator', 'spreader')]),
    ec('old_catacombs'): (JAVA_PKG + 'worldgen/hoanna/underground/OldCatacombsPieces.java',
                          ['loot:' + ec('chests/catacombs')] + ['block:' + ec(x) for x in ('void_stone', 'concrete', 'root')]),
    ec('modern_shafts'): (JAVA_PKG + 'worldgen/hoanna/underground/ModernShaftPieces.java',
                          ['loot:minecraft:chests/abandoned_mineshaft'] + ['block:' + ec(x) for x in ('void_stone', 'void_fence')]),
    # 第十三批 13e：只列谜题 / 功能方块和主体墙料；箱子另在 CODE_RULES 里（沉城、前哨的箱子当运气），宝库没有箱子（核心给奖励）
    ec('sealed_vault'): (JAVA_PKG + 'worldgen/hoanna/vault/VaultPieces.java',
                         ['block:' + ec(x) for x in ('elder_stele', 'vault_pedestal', 'vault_keyhole', 'vault_seal', 'vault_core',
                                                     'void_stone')]),
    ec('sunken_district'): (JAVA_PKG + 'worldgen/hoanna/sunken/SunkenPieces.java',
                            ['block:' + ec(x) for x in ('sunken_altar', 'nest_core', 'void_glass')]),
    ec('inquisitorium_outpost'): (JAVA_PKG + 'worldgen/hoanna/outpost/OutpostPieces.java',
                                  ['block:' + ec(x) for x in ('inquisitorium_dais', 'nest_core', 'void_stone')]),
}
# 实体战利品表由代码按变种选的（不按 entities/<实体名> 默认规则挂）
CODE_MAPPED_ENTITY_TABLES = {ec('entities/hologram_' + x) for x in ('addition', 'subtraction', 'multiplication', 'division')} | \
                            {ec('entities/wind_mage' + x) for x in ('', '_apprentice', '_archmage', '_storm')}

# 固定平衡值的产能器 → 配置键（默认值从 config_keys.json 读，读不到用后面的数）
FIXED_BALANCE = {
    'matrix_absorber': ('machines.matrix_absorber.balance', 1.0),
    'cold_distillator': ('machines.cold_distillator.balance', 0.0),
    'moon_well': ('machines.moon_well.balance', 1.0),
    'sun_ray_absorber': ('machines.sun_ray_absorber.balance', 2.0),
}

# 维度对应的群系标签（forge 群系修改器写的是标签）
BIOME_TAG_DIMS = {
    '#minecraft:is_overworld': 'minecraft:overworld',
    '#minecraft:is_nether': 'minecraft:the_nether',
    '#minecraft:is_end': 'minecraft:the_end',
    '#%s:is_hoanna' % MODID: ec('hoanna'),
}
VANILLA_DIMS = ['minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end']
DIM_PREFERENCE = {'minecraft:overworld': 0, 'minecraft:the_nether': 1, 'minecraft:the_end': 2, ec('hoanna'): 3}
# 原版门槛物品的排位（同 DIM_PREFERENCE：要去下界的算 1，要去末地的算 2）
VANILLA_GATE_RANK = {}
for _gate, _rank in (('下界', 1), ('凋灵', 1), ('远古残骸', 1), ('末地', 2)):
    for _name in VANILLA_GATES[_gate]:
        VANILLA_GATE_RANK[_name] = max(VANILLA_GATE_RANK.get(_name, 0), _rank)


# ======================================================================
# 读文件
# ======================================================================

class Warnings:
    def __init__(self):
        self.items = []

    def add(self, kind, text):
        self.items.append((kind, text))


WARN = Warnings()


def load_json(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def iter_data_files(sub):
    """项目 main + generated 里 data/<ns>/<sub>/**.json → (ns, 相对 sub 的 id 路径, 文件路径)"""
    for base in RES_ROOTS:
        for path in glob.glob(os.path.join(base, 'data', '*', *sub.split('/'), '**', '*.json'), recursive=True):
            rel = os.path.relpath(path, os.path.join(base, 'data'))
            parts = rel.replace('\\', '/').split('/')
            ns = parts[0]
            depth = len(sub.split('/'))
            idpath = '/'.join(parts[1 + depth:])[:-5]
            yield ns, idpath, path


class JarData:
    """原版 / Forge jar 里的数据（只读需要的几类）"""

    def __init__(self, path):
        self.path = path
        self.zip = zipfile.ZipFile(path) if path else None

    def names(self, prefix):
        if not self.zip:
            return []
        return [n for n in self.zip.namelist() if n.startswith(prefix) and n.endswith('.json')]

    def json(self, name):
        with self.zip.open(name) as f:
            return json.loads(f.read().decode('utf-8'))


def gradle_homes():
    homes = []
    for env in ('GRADLE_USER_HOME',):
        if os.environ.get(env):
            homes.append(os.environ[env])
    homes += [os.path.join(os.path.expanduser('~'), '.gradle')]
    out = []
    for h in homes:
        if h and os.path.isdir(h) and os.path.abspath(h) not in out:
            out.append(os.path.abspath(h))
    return out


def read_gradle_props():
    props = {}
    path = os.path.join(ROOT, 'gradle.properties')
    if os.path.exists(path):
        with open(path, encoding='latin-1') as f:
            for line in f:
                if '=' in line and not line.lstrip().startswith('#'):
                    k, v = line.split('=', 1)
                    props[k.strip()] = v.strip()
    return props


def find_mc_jar(explicit):
    if explicit:
        return explicit
    mc = read_gradle_props().get('minecraft_version', '1.20.1')
    for h in gradle_homes():
        cand = os.path.join(h, 'caches', 'forge_gradle', 'minecraft_repo', 'versions', mc, 'client-extra.jar')
        if os.path.exists(cand):
            return cand
    return None


def find_forge_jar(explicit):
    if explicit:
        return explicit
    props = read_gradle_props()
    mc = props.get('minecraft_version', '1.20.1')
    fv = props.get('forge_version', '')
    for h in gradle_homes():
        base = os.path.join(h, 'caches', 'forge_gradle', 'minecraft_user_repo', 'net', 'minecraftforge', 'forge')
        for d in sorted(glob.glob(os.path.join(base, '%s-%s_mapped_*' % (mc, fv)))):
            for jar in glob.glob(os.path.join(d, 'forge-*.jar')):
                if jar.endswith('-sources.jar') or jar.endswith('-recomp.jar'):
                    continue
                return jar
    return None


# ======================================================================
# 世界：注册名、标签、配置
# ======================================================================

class World:
    def __init__(self, mc_jar, forge_jar):
        self.mc = JarData(mc_jar)
        self.forge = JarData(forge_jar)
        self.ec_names = set()          # essentialcraft5:x（方块和物品）
        self.ec_blocks = set()
        self.ec_other = set()          # entity:<id> / effect:<id>（语言文件里有名字的实体和效果）
        self.zh_lang = {}
        self.vanilla_names = set()
        self.item_tags = {}
        self.block_tags = {}
        self.biome_tags = {}
        self.entity_tags = {}
        self.config = {}
        self.zh = {}
        self.vanilla_zh = {}
        self.removed = {}
        self.planned = set()           # 命名表里登记了、没标 removed 的本模组注册名（还没注册的是别的路正在做的）
        self._load_names()
        self._load_tags()
        self._load_config()

    def _load_names(self):
        lang = load_json(LANG_FILE)
        for key in lang:
            parts = key.split('.')
            if len(parts) == 3 and parts[1] == MODID and parts[0] in ('block', 'item'):
                self.ec_names.add(ec(parts[2]))
                if parts[0] == 'block':
                    self.ec_blocks.add(ec(parts[2]))
            elif len(parts) == 3 and parts[1] == MODID and parts[0] in ('entity', 'effect'):
                self.ec_other.add(parts[0] + ':' + ec(parts[2]))
        if os.path.exists(ZH_LANG_FILE):
            zh = load_json(ZH_LANG_FILE)
            self.zh_lang = zh
            for key, value in zh.items():
                parts = key.split('.')
                if len(parts) == 3 and parts[1] == MODID and parts[0] in ('block', 'item'):
                    self.zh[ec(parts[2])] = value
        if self.mc.zip:
            # 原版的注册名：语言文件里的 block. / item. 键，再补上配方、战利品表、物品标签里出现过的 id
            # （锻造模板这类几种物品共用一个名字键，语言文件里没有各自的键）
            vlang = self.mc.json('assets/minecraft/lang/en_us.json') if 'assets/minecraft/lang/en_us.json' in self.mc.zip.namelist() else {}
            for key in vlang:
                parts = key.split('.')
                if len(parts) == 3 and parts[1] == 'minecraft' and parts[0] in ('block', 'item'):
                    self.vanilla_names.add('minecraft:' + parts[2])
            for name in self.mc.names('data/minecraft/'):
                if '/recipes/' in name or '/loot_tables/' in name or '/tags/items/' in name:
                    with self.mc.zip.open(name) as f:
                        text = f.read().decode('utf-8')
                    for m in re.findall(r'"(?:item|name|result|id)"\s*:\s*"(minecraft:[a-z0-9_]+)"', text):
                        self.vanilla_names.add(m)
                    if '/tags/items/' in name:
                        for m in re.findall(r'"(minecraft:[a-z0-9_]+)"', text):
                            self.vanilla_names.add(m)
        for path in ID_MAPS:
            if os.path.exists(path):
                for row in load_json(path):
                    if row.get('status') == 'removed' and row.get('new_id'):
                        self.removed[ec(row['new_id'])] = row.get('reason', '')
                    elif row.get('new_id'):
                        self.planned.add(ec(row['new_id']))

    def _merge_tag(self, store, tid, data):
        values = store.setdefault(tid, [])
        if data.get('replace'):
            values.clear()
        for v in data.get('values', []):
            if isinstance(v, dict):
                values.append((v.get('id'), v.get('required', True)))
            else:
                values.append((v, True))

    def _load_tags(self):
        kinds = {'items': self.item_tags, 'blocks': self.block_tags, 'worldgen/biome': self.biome_tags,
                 'entity_types': self.entity_tags}
        for jar in (self.mc, self.forge):
            for kind, store in kinds.items():
                for name in jar.names('data/'):
                    m = re.match(r'data/([^/]+)/tags/%s/(.+)\.json$' % re.escape(kind), name)
                    if m:
                        self._merge_tag(store, m.group(1) + ':' + m.group(2), jar.json(name))
        for kind, store in kinds.items():
            for ns, idpath, path in iter_data_files('tags/' + kind):
                self._merge_tag(store, ns + ':' + idpath, load_json(path))

    def _load_config(self):
        if os.path.exists(CONFIG_KEYS_FILE):
            for key, spec in load_json(CONFIG_KEYS_FILE).get('keys', {}).items():
                self.config[key] = spec.get('default')
        else:
            WARN.add('缺文件', '找不到 %s（先跑 runData），配置条件一律按「成立」算' % os.path.relpath(CONFIG_KEYS_FILE, ROOT))

    def plain(self, text):
        """手册标题里的 {item:id} / {lang:键} 换成中文名"""
        def sub(m):
            kind, val = m.group(1), m.group(2)
            if kind == 'item':
                return self.zh.get(val, val)
            if kind == 'lang':
                return self.zh_lang.get(val, val)
            return val
        return re.sub(r'\{(item|lang|block):([^}]+)\}', sub, text or '')

    def name(self, iid):
        if iid in self.zh:
            return '%s（%s）' % (self.zh[iid], iid)
        return iid

    def known(self, iid):
        ns = iid.split(':', 1)[0]
        if ns == MODID:
            return iid in self.ec_names
        if ns == 'minecraft':
            return not self.vanilla_names or iid in self.vanilla_names
        return False

    def vanilla_obtainable(self, iid):
        if not iid.startswith('minecraft:'):
            return False
        path = iid[len('minecraft:'):]
        if path in VANILLA_UNOBTAINABLE or path.endswith('_spawn_egg') or path.startswith('infested_'):
            return False
        return not self.vanilla_names or iid in self.vanilla_names

    def resolve(self, store, tid, seen=None):
        """标签展开成 id 集合；没有数据的原版 / Forge 标签返回 None（未知）"""
        seen = seen or set()
        if tid in seen:
            return set()
        seen.add(tid)
        if tid not in store:
            ns = tid.split(':', 1)[0]
            if ns in ('minecraft', 'forge', 'c') and not (self.mc.zip and self.forge.zip):
                return None
            return set()
        out = set()
        for v, required in store[tid]:
            if not v:
                continue
            if v.startswith('#'):
                sub = self.resolve(store, v[1:], seen)
                if sub is None:
                    return None
                out |= sub
            else:
                out.add(v)
        return out

    def item_tag(self, tid):
        return self.resolve(self.item_tags, tid)

    def block_tag(self, tid):
        return self.resolve(self.block_tags, tid)

    def entity_tag(self, tid):
        return self.resolve(self.entity_tags, tid)

    def cond(self, expr, cfg_over):
        """手册 if 的写法：字符串（可用 || 连）或数组（全部成立）"""
        if expr is None:
            return True
        if isinstance(expr, list):
            return all(self.cond(e, cfg_over) for e in expr)
        return any(self._atom(a.strip(), cfg_over) for a in expr.split('||'))

    @staticmethod
    def _list_item(val, sel, index):
        """at:N 取第 N 项（越界取不到）；stage:N 按阶取、列表短了沿用最后一项；不是列表、空列表取不到（None）"""
        if not isinstance(val, list) or not val:
            return None
        if sel == 'stage':
            return val[min(index, len(val) - 1)]
        return val[index] if index < len(val) else None

    def _atom(self, a, cfg_over):
        neg = a.startswith('!')
        if neg:
            a = a[1:]
        ok = True
        if a.startswith('cfg:'):
            body = a[4:]
            # cfg:键|at:N、cfg:键|stage:N：列表先取一项（同手册引擎 GuideConditions），取不到按不成立
            sel = re.match(r'^([A-Za-z0-9_.\-]+)\s*[|]\s*(at|stage):([0-9]+)\s*(.*)$', body)
            if sel:
                item = self._list_item(cfg_over.get(sel.group(1), self.config.get(sel.group(1))), sel.group(2), int(sel.group(3)))
                if item is None:
                    return False
                rest = sel.group(4).strip()
                m = re.match(r'^(>=|<=|==|!=|>|<)\s*(-?[0-9.]+)$', rest)
                if m:
                    v, op, num = float(item), m.group(1), float(m.group(2))
                    ok = {'>=': v >= num, '<=': v <= num, '==': v == num, '!=': v != num, '>': v > num, '<': v < num}[op]
                else:
                    ok = bool(item) if not isinstance(item, str) else item != ''
                return not ok if neg else ok
            m = re.match(r'^([A-Za-z0-9_.\-]+)\s*(>=|<=|==|!=|>|<)\s*(-?[0-9.]+)$', body)
            if m:
                key, op, num = m.group(1), m.group(2), float(m.group(3))
                val = cfg_over.get(key, self.config.get(key))
                try:
                    v = float(val)
                except (TypeError, ValueError):
                    v = 0.0
                ok = {'>=': v >= num, '<=': v <= num, '==': v == num, '!=': v != num, '>': v > num, '<': v < num}[op]
            else:
                val = cfg_over.get(body, self.config.get(body, True))
                ok = bool(val) if not isinstance(val, str) else val != ''
        elif a.startswith('mod:'):
            ok = a[4:] in LOADED_MODS
        elif a.startswith('bauble:'):
            disabled = cfg_over.get('general.baubles.disabled', self.config.get('general.baubles.disabled')) or []
            ok = a[len('bauble:'):] not in disabled
        elif a.startswith('module:'):
            # 宅男套装模块：被 general.computer_modules.disabled 关掉时不成立（写法同 bauble:）
            disabled = cfg_over.get('general.computer_modules.disabled', self.config.get('general.computer_modules.disabled')) or []
            ok = a[len('module:'):] not in disabled
        elif a.startswith('gamemode:'):
            ok = False
        return not ok if neg else ok


# ======================================================================
# 图
# ======================================================================

class Rule:
    __slots__ = ('kind', 'label', 'src', 'needs', 'gives', 'cfg', 'id', 'luck', 'count')

    def __init__(self, kind, label, src, needs, gives, cfg=None, luck=False, count=None):
        self.kind = kind
        self.label = label
        self.src = src
        self.needs = list(dict.fromkeys(tuple(g) for g in needs))   # [tuple(节点…)]：每组里有一个就行，组与组全部要有
        self.gives = gives
        self.cfg = cfg
        self.id = None
        self.luck = luck            # 只靠运气的来源（保底检查去掉它再推一遍）
        # 「凑够几种」：只有一组，组里要有 count 个（矩阵图谱的种数）；None = 普通规则
        self.count = count


class Graph:
    def __init__(self, world):
        self.w = world
        self.rules = []
        self.problems = []      # (类别, 文字)：数据问题，--strict 时算失败
        self.notes = []         # 只提示
        self.trades = []        # (条目说明, needs 组)
        self.disabled = {}      # 节点 → [(规则, 配置条件)]：被配置关掉的代码规则本来能给的
        self.cfg = {}           # 这种情形改过的配置（数据里跟配置有关的规则按它算，run_scenario 设）
        self.hoanna_monsters = set()    # 海纳尔群系的怪物刷怪表里有的实体（图谱「海纳尔里」组）
        self.codex_made = set()         # 已经建好的图谱节点（组成员 / 凑够几种）
        self.matrix_ranks = 0           # 读到几阶矩阵阶数据（load_matrix_ranks）
        self.matrix_top = 0             # 读到的最高一阶
        self.ascensions = []            # 读到的升格配方：{recipe, family, from_stage, results}（ascension_check 用）

    def add(self, rule):
        rule.id = len(self.rules)
        self.rules.append(rule)
        return rule

    # ---------- 节点表达式 ----------

    def mru_group(self, lo=0.0, hi=2.0):
        out = ['cap:mru@random']
        for machine, (key, default) in FIXED_BALANCE.items():
            v = self.w.config.get(key, default)
            try:
                v = float(v)
            except (TypeError, ValueError):
                v = default
            if v < 0:
                continue
            if lo - 1e-9 <= v <= hi + 1e-9:
                out.append('cap:mru@fixed:' + machine)
        return tuple(out)

    def expand(self, expr, context):
        """代码规则里的一项 → 一组节点（OR）"""
        alts = []
        for part in expr.split('|'):
            part = part.strip()
            if part == 'MRU':
                alts.extend(self.mru_group())
            elif part.startswith('MRU['):
                lo, hi = [float(x) for x in part[4:-1].split(',')]
                alts.extend(self.mru_group(lo, hi))
            elif part.startswith('#block:'):
                members = self.w.block_tag(part[len('#block:'):])
                if members is None:
                    alts.append('block:minecraft:stone')
                else:
                    if not members:
                        self.problems.append(('空标签', '%s 用到的方块标签 %s 没有成员' % (context, part[1:])))
                    alts.extend('block:' + m for m in sorted(members))
            elif part.startswith('#give:'):
                alts.append(part)
            elif part.startswith('#'):
                members = self.w.item_tag(part[1:])
                if members is None:
                    alts.append('item:minecraft:stone')
                else:
                    if not members:
                        self.problems.append(('空标签', '%s 用到的物品标签 %s 没有成员' % (context, part)))
                    alts.extend('item:' + m for m in sorted(members))
            else:
                alts.append(part)
        return tuple(dict.fromkeys(alts))

    def check_node_ids(self, nodes, context):
        for n in nodes:
            kind, _, body = n.partition(':')
            if kind in ('item', 'block') and body.startswith(MODID + ':') and body not in self.w.ec_names:
                self.problems.append(('引用不存在的注册名', '%s 引用了没注册的 %s' % (context, body)))

    # ---------- 配方 ----------

    def ingredient(self, ing, context):
        """配方原料 → 一组可替代的物品节点；None = 空格；() = 无解"""
        if ing is None:
            return None
        if isinstance(ing, list):
            out = []
            for sub in ing:
                g = self.ingredient(sub, context)
                if g:
                    out.extend(g)
            return tuple(dict.fromkeys(out))
        if isinstance(ing, str):
            ing = {'tag': ing[1:]} if ing.startswith('#') else {'item': ing}
        t = ing.get('type')
        if t in ('forge:compound',):
            return self.ingredient(ing.get('children', []), context)
        if t in ('forge:difference',):
            return self.ingredient(ing.get('base'), context)
        if t in ('forge:intersection',):
            groups = [set(self.ingredient(c, context) or ()) for c in ing.get('children', [])]
            return tuple(sorted(set.intersection(*groups))) if groups else ()
        if 'item' in ing:
            iid = rid(ing['item'])
            if not self.w.known(iid):
                raise KeyError(iid)
            return ('item:' + iid,)
        if 'tag' in ing:
            members = self.w.item_tag(rid(ing['tag']))
            if members is None:
                return ('item:minecraft:stone',)       # 原版 / Forge 标签没数据：按有东西算
            if not members:
                self.problems.append(('空标签', '%s 用到的物品标签 #%s 没有成员（配方合不出来，游戏里也不报错）' % (context, ing['tag'])))
            return tuple(sorted('item:' + m for m in members if self.w.known(m) or not m.startswith(MODID)))
        return ()

    def recipe_conditions_ok(self, conds):
        for c in conds or []:
            if not self.cond_ok(c):
                return False
        return True

    def cond_ok(self, c):
        t = c.get('type')
        if t == 'forge:item_exists':
            return self.w.known(rid(c['item']))
        if t == 'forge:mod_loaded':
            return c.get('modid') in LOADED_MODS
        if t == 'forge:not':
            return not self.cond_ok(c['value'])
        if t == 'forge:and':
            return all(self.cond_ok(x) for x in c.get('values', []))
        if t == 'forge:or':
            return any(self.cond_ok(x) for x in c.get('values', []))
        if t == 'forge:true':
            return True
        if t == 'forge:false':
            return False
        if t == 'forge:tag_empty':
            m = self.w.item_tag(rid(c['tag']))
            return not m
        self.notes.append(('配方条件', '不认识的配方条件 %s，按成立算' % t))
        return True

    def result_items(self, res):
        out = []
        if res is None:
            return out
        if isinstance(res, str):
            out.append(rid(res))
        elif isinstance(res, list):
            for r in res:
                out += self.result_items(r)
        elif isinstance(res, dict):
            if 'item' in res:
                out.append(rid(res['item']))
            elif 'id' in res:
                out.append(rid(res['id']))
            elif 'tag' in res:
                members = self.w.item_tag(rid(res['tag'])) or set()
                vanilla = sorted(m for m in members if m.startswith('minecraft:'))
                out.append(vanilla[0] if vanilla else (sorted(members)[0] if members else 'minecraft:air'))
        return out

    def load_recipes(self):
        counts = collections.Counter()
        for ns, idpath, path in iter_data_files('recipes'):
            rid_ = ns + ':' + idpath
            rel = os.path.relpath(path, ROOT).replace('\\', '/')
            d = load_json(path)
            if not self.recipe_conditions_ok(d.get('conditions')):
                counts['条件不成立'] += 1
                continue
            t = d.get('type', '')
            ctx = '配方 ' + rid_
            if t in (ATTUNEMENT_ASCENSION, ATTUNEMENT_TRANSFER):
                # 一张升阶配方对家族里每一件各算一条（中心件不同、给的同调阶不同）
                try:
                    counts[t] += self.attunement_recipe(t, d, rid_, rel, ctx)
                except KeyError as e:
                    self.problems.append(('配方引用不存在的物品', '%s（%s）引用了 %s：整条配方不会加载' % (rid_, rel, e.args[0])))
                continue
            try:
                needs, gives, label = self.recipe_parts(t, d, ctx)
            except KeyError as e:
                self.problems.append(('配方引用不存在的物品', '%s（%s）引用了 %s：整条配方不会加载' % (rid_, rel, e.args[0])))
                continue
            if needs is None:
                self.notes.append(('配方类型', '%s 的类型 %s 不认识，没算进图' % (rid_, t)))
                continue
            if label == '缺字段':
                continue
            for g in needs:
                if not g:
                    self.problems.append(('配方无解', '%s 有一格原料没有任何物品能放' % rid_))
            for iid in gives:
                if not self.w.known(iid):
                    self.problems.append(('配方产物不存在', '%s 的产物 %s 没注册' % (rid_, iid)))
            self.add(Rule('recipe', '%s：%s' % (label, rid_), rel, needs,
                          ['item:' + i for i in gives] + ['recipe:' + rid_]))
            counts[t] += 1
        return counts

    # 本模组配方类型必填的字段（缺了序列化器抛异常，整条配方不加载；见 recipe/*Recipe.java 的 Serializer）
    REQUIRED_FIELDS = {
        MODID + ':magician_table': ('ingredients', 'result', 'mru'),
        MODID + ':radiating_chamber': ('ingredients', 'result', 'mru'),
        MODID + ':mithriline_furnace': ('ingredient', 'result', 'espe'),
        MODID + ':wind_imbue': ('ingredient', 'result', 'espe'),
        MODID + ':cultivator': ('input', 'outputs'),
        MODID + ':ore_smelting': ('ore', 'output'),
        MODID + ':crafting_shaped_keep_nbt': ('pattern', 'key', 'result', 'nbt_source'),
        MODID + ':crafting_shapeless_keep_nbt': ('ingredients', 'result', 'nbt_source'),
        ATTUNEMENT_ASCENSION: ('gear', 'from_stage', 'ingredients'),
    }

    # ---------- 第十三批 13a：同调（batch13.md §3.3.5） ----------

    def attunement_family(self, gear):
        """升阶配方的 gear → (家族标签 id 或 None, [物品 id])。游戏里（AttunementAscensionRecipe）gear 写的是家族 id，
        省掉命名空间按本模组算，成员是标签 <命名空间>:attunement/<家族>；另外也认 #id、{"tag": id}、不带 # 的标签 id 和单件物品"""
        tid = None
        if isinstance(gear, dict):
            if gear.get('tag'):
                tid = rid(gear['tag'])
            elif gear.get('item'):
                return None, [rid(gear['item'])]
        elif isinstance(gear, str):
            s = gear.strip()
            fam = rid(s, MODID) if not s.startswith('#') else None
            fam_tag = '%s:attunement/%s' % tuple(fam.split(':', 1)) if fam else None
            if s.startswith('#'):
                tid = rid(s[1:])
            elif fam_tag and self.w.item_tag(fam_tag):
                tid = fam_tag
            elif self.w.item_tag(rid(s, MODID)):
                tid = rid(s, MODID)
            else:
                return None, [rid(s)]
        if tid is None:
            return None, []
        return tid, sorted(self.w.item_tag(tid) or [])

    def attunement_recipe(self, t, d, rid_, rel, ctx):
        """同调升阶 / 转移配方（魔力工作台同一个配方类型，新的序列化器）。返回加了几条规则。
        升阶：中心是家族里的一件、到了 from_stage 阶、本阶同调度攒满、物品上的矩阵阶戳够（general.attunement.rank_gate[from_stage]，
        矩阵阶关掉时不看）→ 那一件升一阶（cap:attunement/<物品>@<阶>）；写了 result 的是升格成别的物品（裁决之刃），新物品从 0 阶起。
        转移：把旧件的同调度挪一半给新件，不产新物品，只记配方节点"""
        missing = [k for k in self.REQUIRED_FIELDS.get(t, ()) if k not in d]
        if missing:
            self.problems.append(('配方缺字段', '%s 缺 %s（整条配方不会加载）' % (ctx, '、'.join(missing))))
            return 0
        corners = []
        for ing in d.get('ingredients', []) or []:
            g = self.ingredient(ing, ctx)          # 每格的 count 只是数量，可达性不看
            if g is not None:
                if not g:
                    self.problems.append(('配方无解', '%s 有一格原料没有任何物品能放' % rid_))
                corners.append(g)
        table = [('block:' + ec('magician_table'),), self.mru_group()]
        if t == ATTUNEMENT_TRANSFER:
            self.add(Rule('recipe', '同调转移：%s' % rid_, rel, corners + table, ['recipe:' + rid_]))
            return 1
        tid, members = self.attunement_family(d.get('gear'))
        if not members:
            self.problems.append(('配方无解', '%s 的 gear %s 没有任何物品（家族标签空了或写错）' % (ctx, d.get('gear'))))
            return 0
        parts = d.get('parts')
        if parts:
            parts = [parts] if isinstance(parts, str) else list(parts)
            unknown = [p for p in parts if str(p).lower() not in ATTUNEMENT_PARTS]
            if unknown:
                self.notes.append(('同调配方', '%s 的 parts 里有脚本不认识的部位 %s，按「不限部位」算' % (rid_, '、'.join(map(str, unknown)))))
            else:
                suffixes = tuple(s for p in parts for s in ATTUNEMENT_PARTS[str(p).lower()])
                members = [m for m in members if m.endswith(suffixes)]
                if not members:
                    self.problems.append(('配方无解', '%s 的 parts %s 在家族 %s 里一件都对不上' % (ctx, parts, tid)))
                    return 0
        try:
            stage = int(d.get('from_stage'))
        except (TypeError, ValueError):
            self.problems.append(('配方缺字段', '%s 的 from_stage 不是整数' % ctx))
            return 0
        family = tid[len(ATTUNEMENT_TAG_PREFIX):] if tid and tid.startswith(ATTUNEMENT_TAG_PREFIX) else None
        cfg = self.cfg
        disabled = [str(x) for x in config_list(self.w, cfg, 'general.attunement.disabled_families', [])]
        rank = 0
        if self.w.cond('cfg:general.matrix_rank.enabled', cfg):
            gates = config_list(self.w, cfg, 'general.attunement.rank_gate', [1, 3, 5, 7, 9])
            try:
                rank = int(float(gates[stage])) if stage < len(gates) else 2 * stage + 1
            except (TypeError, ValueError):
                rank = 2 * stage + 1
        result = d.get('result')
        res_ids = self.result_items(result) if result else []
        if res_ids:
            self.ascensions.append({'recipe': rid_, 'family': tid, 'from_stage': stage, 'results': list(res_ids)})
        n = 0
        for m in members:
            needs = [('item:' + m,), ('cap:attunement/%s@%d' % (m, stage),), ('cap:attunement_points',)] + corners + table
            if rank > 0:
                needs.append(('cap:matrix_rank_%d' % rank,))
            if res_ids and res_ids[0] != m:
                for r in res_ids:
                    if not self.w.known(r):
                        self.problems.append(('配方产物不存在', '%s 的产物 %s 没注册' % (rid_, r)))
                gives = ['item:' + r for r in res_ids]
                label = '同调升格：%s %d 阶 → %s' % (self.w.name(m), stage, '、'.join(self.w.name(r) for r in res_ids))
            else:
                gives = ['cap:attunement/%s@%d' % (m, stage + 1)]
                label = '同调升阶：%s %d → %d 阶' % (self.w.name(m), stage, stage + 1)
            if family and family in disabled:
                for g in gives:
                    self.disabled.setdefault(g, []).append((label, 'general.attunement.disabled_families 不含 %s' % family))
                continue
            self.add(Rule('recipe', '%s（%s）' % (label, rid_), rel, needs, gives + ['recipe:' + rid_]))
            n += 1
        return n

    def load_attunement(self):
        """拿到家族里的物品就是 0 阶（物品没有同调键 = 0 阶 0 点）"""
        n = 0
        for tid in sorted(self.w.item_tags):
            if not tid.startswith(ATTUNEMENT_TAG_PREFIX):
                continue
            for m in sorted(self.w.item_tag(tid) or []):
                self.add(Rule('code', '同调 0 阶：拿到 %s' % self.w.name(m), 'tools/tags/attunement.json', [('item:' + m,)],
                              ['cap:attunement/%s@0' % m]))
                n += 1
        return n

    # ---------- 第十三批 13a：矩阵图谱与矩阵阶（batch13.md §3.1） ----------

    def codex_members(self, group):
        """图谱一组里能记的实体 → [(实体 id, 要在哪个维度杀 / None)]（去掉 growth_excluded）。
        口径同 matrix/MatrixCodex.countable：敌对（Enemy / MONSTER，VANILLA_HOSTILES、EC_CODEX）或 #forge:bosses 里的首领
        （全息投影、审判庭哨兵是 MISC 类、不实现 Enemy，靠首领标签算数；13e 的天灾之心同样要进 #forge:bosses）"""
        excluded = self.w.entity_tag(ec('growth_excluded')) or set()
        bosses = self.w.entity_tag('forge:bosses') or set()

        def tag(name):
            return sorted(self.w.entity_tag(ec(name)) or [])

        if group == 'total':
            out = [(e, d) for e, d in VANILLA_HOSTILES.items()]
            extra = list(EC_CODEX) + sorted(bosses) + tag('hoanna_natives')
            out += [(e, VANILLA_HOSTILES.get(e)) for e in extra]
        elif group == 'nether':
            out = [(e, 'minecraft:the_nether') for e in tag('codex_nether')]
        elif group == 'hoanna':
            ents = sorted(self.hoanna_monsters) + list(EC_CODEX) + sorted(b for b in bosses if b.startswith(MODID + ':')) + tag('hoanna_natives')
            out = [(e, ec('hoanna')) for e in ents]
        elif group == 'natives':
            out = [(e, ec('hoanna')) for e in tag('hoanna_natives')]
        elif group == 'bosses':
            out = [(e, VANILLA_HOSTILES.get(e)) for e in sorted(bosses)]
        elif group == 'mod':
            out = [(e, None) for e in list(EC_CODEX) + sorted(bosses) if e.startswith(MODID + ':')]
        elif group == 'end':
            out = [(e, 'minecraft:the_end') for e in tag('codex_end')]
        elif group == 'expedition':
            out = [(e, VANILLA_HOSTILES.get(e)) for e in tag('codex_expedition')]
        else:
            out = []
        seen = collections.OrderedDict()
        for e, d in out:
            if e not in excluded and e not in seen:
                seen[e] = d
        return list(seen.items())

    def codex_member(self, group, entity, dim):
        node = 'cap:codex@%s/%s' % (group, entity)
        if node not in self.codex_made:
            self.codex_made.add(node)
            needs = [('entity:' + entity,)] + ([('dim:' + dim,)] if dim else [])
            self.add(Rule('codex', '矩阵图谱「%s」记下 %s' % (CODEX_GROUPS.get(group, group), entity),
                          'batch13.md §3.1.1（图谱分组）', needs, [node]))
        return node

    def codex_need(self, group, k):
        """图谱「这一组凑够 k 种」→ 节点（k ≤ 0 或原生组标签空着时 None = 不要求）"""
        members = self.codex_members(group)
        if group == 'natives':
            k = min(k, len(members))        # 标签不够数时按实际数（§3.1.3 六阶）
        if k <= 0:
            return None
        node = 'cap:codex@%s>=%d' % (group, k)
        if node not in self.codex_made:
            self.codex_made.add(node)
            nodes = tuple(self.codex_member(group, e, d) for e, d in members)
            if len(nodes) < k:
                self.notes.append(('矩阵图谱', '「%s」组只有 %d 种能记，阶数据要 %d 种' % (CODEX_GROUPS.get(group, group), len(nodes), k)))
            self.add(Rule('count', '矩阵图谱「%s」凑够 %d 种' % (CODEX_GROUPS.get(group, group), k), 'batch13.md §3.1.1',
                          [nodes], [node], count=k))
        return node

    RANK_KNOWN_KEYS = {'rank', 'name', 'title', 'comment', '_comment', '_src', 'echoes', 'echo', 'trial', 'rewards', 'reward', 'forge:conditions',
                       'gate', 'gates', 'tolerance', 'pool', 'capacity', 'resonance', 'icon', 'color', 'colour'}

    def load_matrix_ranks(self):
        """data/*/matrix_ranks/rank_<n>.json → 每阶一条规则（batch13.md §2.7、§3.1.3）。返回读到几阶。
        写法（别名都认，不认识的键记一条提示）：
          {"rank": n（可省，按文件名）,
           "codex": {"total": 21, "nether": 4, "hoanna": 4, "natives": 2, "bosses": 2, "mod": 3, "end": 1, "expedition": 1,
                     "entities": ["essentialcraft5:wind_mage"], "matrix_type": true},
           "site": {"pentacle_tier": 1}（或 "pentacle_tier": 1；不写 / -1 = 淬炼台单独就行）,
           "offerings": [配方原料写法 + "count"],
           "energy": {"ubmru": 5000, "mru": 2000, "espe": 2000}（也可以平铺在顶层）,
           "echoes": {…}（回响数值，可达性不看）}"""
        ranks = {}
        for ns, idpath, path in iter_data_files('matrix_ranks'):
            rel = os.path.relpath(path, ROOT).replace('\\', '/')
            d = load_json(path)
            m = re.match(r'^rank_([1-9][0-9]*)$', os.path.basename(idpath))
            n = d.get('rank') if isinstance(d, dict) else None
            if not isinstance(n, int) or isinstance(n, bool):
                n = int(m.group(1)) if m else None
            if not isinstance(d, dict) or not n:
                self.problems.append(('矩阵阶数据', '%s 读不出是第几阶（文件名 rank_<n>.json 或写 "rank"）' % rel))
                continue
            # forge:conditions 不满足的整阶不读（MatrixRankData 同样静默跳过，例：八、九阶要 13e 的物品）
            if not self.recipe_conditions_ok(d.get('forge:conditions')):
                self.notes.append(('矩阵阶数据', '%s 的 forge:conditions 不满足，这一阶不读（游戏里一样）' % rel))
                continue
            if m and int(m.group(1)) != n:
                self.problems.append(('矩阵阶数据', '%s 文件名和 "rank": %d 对不上' % (rel, n)))
            if n in ranks:
                self.notes.append(('矩阵阶数据', '第 %d 阶有两份（%s、%s），按后读到的算' % (n, ranks[n][0], rel)))
            ranks[n] = (rel, d)
        self.matrix_ranks = len(ranks)
        if not ranks:
            return 0
        top = max(ranks)
        self.matrix_top = top
        for k in range(1, top):
            if k not in ranks:
                self.problems.append(('矩阵阶数据', '缺第 %d 阶（%d 阶以上都升不上去）' % (k, k + 1)))
        enabled = self.w.cond('cfg:general.matrix_rank.enabled', self.cfg)
        for n, (rel, d) in sorted(ranks.items()):
            rule = self.matrix_rank_rule(n, rel, d)
            if rule is None:
                continue
            gives = ['cap:matrix_rank_%d' % n]
            adv = ['adv:%s:guide/matrix/rank_%d' % (MODID, n)]
            if not enabled:
                for g in gives + adv:
                    self.disabled.setdefault(g, []).append((rule[0], 'cfg:general.matrix_rank.enabled'))
                continue
            label, needs = rule
            self.add(Rule('matrix_rank', label, rel + ' + ' + JAVA_PKG + 'matrix/MatrixTrials.java', needs, gives))
            # 升阶成功时代码发手册步骤（进度是 impossible，同 guide/hoanna/radiation）
            self.add(Rule('code', '升到矩阵 %d 阶时发手册步骤' % n, JAVA_PKG + 'matrix/MatrixRank.java', [(gives[0],)], adv))
        return len(ranks)

    def matrix_rank_rule(self, n, rel, d):
        """一阶的 (说明, needs)；数据坏得读不了时 None（游戏里这一阶也会被跳过）"""
        ctx = '矩阵阶 %d 阶（%s）' % (n, rel)
        unknown = []
        codex = d.get('codex', d.get('bestiary'))
        energy = d.get('energy', d.get('cost', d.get('costs')))
        energy = dict(energy) if isinstance(energy, dict) else {}
        for k in ('ubmru', 'mru', 'espe', 'effective_espe'):
            if k in d:
                energy[k] = d[k]
        site = d.get('site', d.get('station', d.get('pentacle_tier', d.get('pentacle'))))
        offerings = None
        for k in ('offerings', 'offering', 'sacrifices', 'items', 'ingredients'):
            if k in d:
                offerings = d[k]
        known = self.RANK_KNOWN_KEYS | {'codex', 'bestiary', 'energy', 'cost', 'costs', 'ubmru', 'mru', 'espe', 'effective_espe',
                                        'site', 'station', 'pentacle_tier', 'pentacle',
                                        'offerings', 'offering', 'sacrifices', 'items', 'ingredients'} | set(CODEX_TYPE_KEYS)
        unknown += [k for k in d if k not in known]
        needs = [('item:' + ec('soul_stone'),), ('block:' + ec('matrix_crucible'),)]
        if n > 1:
            needs.append(('cap:matrix_rank_%d' % (n - 1),))
        # 图谱
        want_type = any(d.get(k) not in (None, False, 'neutral', 'any') for k in CODEX_TYPE_KEYS)
        groups = []
        entities = []
        if isinstance(codex, dict):
            for k, v in codex.items():
                if k in CODEX_TYPE_KEYS:
                    want_type = want_type or v not in (None, False, 'neutral', 'any')
                elif k in CODEX_ENTITY_KEYS:
                    entities += [v] if isinstance(v, str) else list(v or [])
                elif k in CODEX_KEY_ALIASES and isinstance(v, (int, float)) and not isinstance(v, bool):
                    groups.append((CODEX_KEY_ALIASES[k], int(v)))
                else:
                    unknown.append('codex.' + k)
        elif isinstance(codex, list):
            for c in codex:
                g = c.get('group', c.get('type')) if isinstance(c, dict) else None
                v = c.get('min', c.get('count', c.get('at_least'))) if isinstance(c, dict) else None
                if g in CODEX_KEY_ALIASES and isinstance(v, (int, float)):
                    groups.append((CODEX_KEY_ALIASES[g], int(v)))
                elif isinstance(c, dict) and (c.get('entity') or c.get('id')):
                    entities.append(c.get('entity') or c.get('id'))
                else:
                    unknown.append('codex[%s]' % json.dumps(c, ensure_ascii=False))
        elif codex is not None:
            unknown.append('codex')
        for g, k in groups:
            node = self.codex_need(g, k)
            if node:
                needs.append((node,))
        for e in entities:
            e = rid(str(e))
            needs.append((self.codex_member('total', e, VANILLA_HOSTILES.get(e)),))
        if want_type:
            needs.append(('cap:matrix_type',))
        # 祭品
        for ing in offerings if isinstance(offerings, list) else ([] if offerings is None else [offerings]):
            try:
                g = self.ingredient(ing, ctx)
            except KeyError as e:
                self.problems.append(('矩阵阶数据', '%s 的祭品引用了没注册的 %s：游戏里这一阶会被跳过' % (ctx, e.args[0])))
                return None
            if g is not None:
                if not g:
                    self.problems.append(('矩阵阶数据', '%s 有一样祭品没有任何物品能放' % ctx))
                needs.append(g)
        # 能量：UBMRU 要杀生物攒、MRU 由绑定宝石从产能器接进淬炼台、ESPE 从五芒星的柱子抽
        def num(k):
            try:
                return float(energy.get(k) or 0)
            except (TypeError, ValueError):
                unknown.append('energy.' + k)
                return 0.0
        if num('ubmru') > 0:
            needs.append(('cap:ubmru',))
        if num('mru') > 0:
            needs.append(self.mru_group())
        espe = num('espe') + num('effective_espe')
        # 站点
        tier = None
        if isinstance(site, dict):
            for k in ('pentacle_tier', 'tier', 'pentacle', 'min_tier'):
                if k in site:
                    tier = site[k]
            unknown += ['site.' + k for k in site if k not in ('pentacle_tier', 'tier', 'pentacle', 'min_tier', 'radius', 'range')]
        elif isinstance(site, (int, float)) and not isinstance(site, bool):
            tier = site
        elif isinstance(site, str) and site not in ('crucible', 'table', 'none', ''):
            unknown.append('site=' + site)
        if tier is not None:
            try:
                tier = int(tier)
            except (TypeError, ValueError):
                unknown.append('site.tier')
                tier = None
        if tier is not None and tier >= 0:
            node = PENTACLE_TIER_NODES.get(tier)
            if node is None:
                self.problems.append(('矩阵阶数据', '%s 要 %d 档五芒星，没有这一档' % (ctx, tier)))
                return None
            needs.append((node,))
        elif espe > 0:
            self.problems.append(('矩阵阶数据', '%s 要 ESPE 却没写五芒星站点（ESPE 从五芒星的柱子抽）' % ctx))
        if unknown:
            self.notes.append(('矩阵阶数据', '%s 里有脚本不认识的写法，没算进图：%s' % (ctx, '、'.join(unknown))))
        return '矩阵阶 %d 阶突破（淬炼台试炼）' % n, needs

    def recipe_parts(self, t, d, ctx):
        needs = []
        missing = [k for k in self.REQUIRED_FIELDS.get(t, ()) if k not in d]
        if missing:
            self.problems.append(('配方缺字段', '%s 缺 %s（整条配方不会加载）' % (ctx, '、'.join(missing))))
            return [()], [], '缺字段'
        if t.endswith('_keep_nbt'):
            src = self.ingredient(d['nbt_source'], ctx) or ()
            pool = set()
            for ing in (d.get('key') or {}).values() if 'key' in d else d.get('ingredients', []):
                pool.update(self.ingredient(ing, ctx) or ())
            if src and not set(src) & pool:
                self.problems.append(('配方 nbt_source', '%s 的 nbt_source 不在原料里（合成时找不到来源）' % ctx))

        def add_ing(ing):
            g = self.ingredient(ing, ctx)
            if g is not None:
                needs.append(g)

        if t in ('minecraft:crafting_shaped', MODID + ':crafting_shaped_keep_nbt'):
            used = set(''.join(d.get('pattern', [])).replace(' ', ''))
            for key, ing in d.get('key', {}).items():
                if key in used:
                    add_ing(ing)
            return needs, self.result_items(d.get('result')), '工作台'
        if t in ('minecraft:crafting_shapeless', MODID + ':crafting_shapeless_keep_nbt'):
            for ing in d.get('ingredients', []):
                add_ing(ing)
            return needs, self.result_items(d.get('result')), '工作台'
        if t in ('minecraft:smelting', 'minecraft:blasting', 'minecraft:smoking', 'minecraft:campfire_cooking',
                 'minecraft:stonecutting'):
            add_ing(d.get('ingredient'))
            return needs, self.result_items(d.get('result')), '原版机器'
        if t == 'minecraft:smithing_transform':
            for k in ('template', 'base', 'addition'):
                add_ing(d.get(k))
            return needs, self.result_items(d.get('result')), '锻造台'
        if t == MODID + ':magician_table':
            for ing in d.get('ingredients', []):
                add_ing(ing)
            needs.append(('block:' + ec('magician_table'),))
            needs.append(self.mru_group())
            return needs, self.result_items(d.get('result')), '魔术师台'
        if t == MODID + ':radiating_chamber':
            for ing in d.get('ingredients', []):
                add_ing(ing)
            needs.append(('block:' + ec('radiating_chamber'),))
            needs.append(self.mru_group(float(d.get('balance_min', 0.0)), float(d.get('balance_max', 2.0))))
            return needs, self.result_items(d.get('result')), '辐射仓'
        if t == MODID + ':mithriline_furnace':
            add_ing(d.get('ingredient'))
            needs.append(('struct:' + ec('mithriline_furnace'),))
            return needs, self.result_items(d.get('result')), '米斯莱熔炉'
        if t == MODID + ':ore_smelting':
            ore = self.ingredient(d.get('ore'), ctx) or ()
            raw = self.ingredient(d.get('raw'), ctx) if d.get('raw') else ()
            needs.append(tuple(dict.fromkeys(ore + (raw or ()))))
            needs.append(tuple('block:' + ec(m) for m in ('magmatic_smeltery', 'fortified_magmatic_furnace',
                                                         'magic_magmatic_furnace', 'pale_magmatic_furnace', 'void_magmatic_furnace')))
            needs.append(self.mru_group())
            return needs, self.result_items(d.get('output')) + [ec('magmatic_alloy')], '矿石加工'
        if t == MODID + ':wind_imbue':
            add_ing(d.get('ingredient'))
            needs.append(('struct:' + ec('wind_rune'),))
            if d.get('requires_windbound'):
                # 只给已受风之眷顾的玩家注（WindRuneBlockEntity 在扣 ESPE 之前拒绝，例：风之号角）
                needs.append(('adv:%s:guide/wind/windbound' % MODID,))
            return needs, self.result_items(d.get('result')), '注风'
        if t == MODID + ':cultivator':
            # 样本不消耗，但得先有一份；要机器和 MRU；水用水桶或水源（原版就有，不单列）
            add_ing(d.get('input'))
            needs.append(('block:' + ec('magical_cultivator'),))
            needs.append(self.mru_group())
            return needs, [rid(o['item']) for o in d.get('outputs', []) if o.get('item')], '魔力栽培器'
        return None, [], ''

    # ---------- 战利品表 ----------

    @staticmethod
    def loot_lucky(node):
        '''这个池 / 条目带公平击杀或按人递减条件（野外怪的关键掉落）：保底检查当运气'''
        return any(isinstance(c, dict) and rid(c.get('condition', '')) in LUCK_LOOT_CONDITIONS
                   for c in node.get('conditions', []) or [])

    @staticmethod
    def loot_nest(node):
        '''这个池 / 条目只在巢穴放出来的怪身上成立（from_nest 没取反，第十三批 13d）'''
        return any(isinstance(c, dict) and rid(c.get('condition', '')) in NEST_LOOT_CONDITIONS
                   for c in node.get('conditions', []) or [])

    def loot_entries(self, node, items, tables, ctx, lucky=None, nest=None):
        '''lucky：(物品, 表) 两个集合，收运气条件下面的东西；None 时（已经在运气条件下面）照常收进 items / tables。
        nest：同样两个集合，收 from_nest 下面的东西（巢穴的怪掉的不算运气，进去以后不再分运气）'''
        if isinstance(node, dict):
            if nest is not None and self.loot_nest(node):
                items, tables, lucky, nest = nest[0], nest[1], None, None
            if lucky is not None and self.loot_lucky(node):
                items, tables, lucky = lucky[0], lucky[1], None
            t = node.get('type', '')
            name = node.get('name')
            if t == 'minecraft:item' and name:
                items.add(rid(name))
            elif t == 'minecraft:loot_table' and name:
                tables.add(rid(name))
            elif t == 'minecraft:tag' and name:
                members = self.w.item_tag(rid(name))
                if members:
                    items.update(members)
            for key in ('children', 'entries', 'pools'):
                for child in node.get(key, []) or []:
                    self.loot_entries(child, items, tables, ctx, lucky, nest)
        elif isinstance(node, list):
            for child in node:
                self.loot_entries(child, items, tables, ctx, lucky, nest)

    def add_loot_table(self, tid, data, src, boss=False):
        '''boss：首领（#forge:bosses）的表，参战打够一成就分得到，公平击杀条件不算运气（batch13.md §5.3 共同）'''
        items, tables = set(), set()
        luck_items, luck_tables = set(), set()
        nest_items, nest_tables = set(), set()
        self.loot_entries(data, items, tables, tid, None if boss else (luck_items, luck_tables), (nest_items, nest_tables))
        for group_items, group_tables, luck, nest in ((items, tables, False, False),
                                                       (luck_items - items, luck_tables - tables, True, False),
                                                       (nest_items - items, nest_tables - tables, False, True)):
            gives = []
            for iid in sorted(group_items):
                if not self.w.known(iid):
                    self.problems.append(('战利品引用不存在的物品', '战利品表 %s 里的 %s 没注册' % (tid, iid)))
                    continue
                gives.append('item:' + iid)
            gives += ['loot:' + t for t in sorted(group_tables)]
            if gives:
                needs = [('loot:' + tid,)] + ([(NEST_NODE,)] if nest else [])
                note = '（公平击杀 / 按人递减）' if luck else ('（巢穴放出来的怪）' if nest else '')
                self.add(Rule('loot', '战利品表 ' + tid + note, src, needs, gives, luck=luck))

    def load_loot(self):
        n = 0
        ec_tables = set()
        bosses = self.w.entity_tag('forge:bosses') or set()
        for ns, idpath, path in iter_data_files('loot_tables'):
            tid = ns + ':' + idpath
            rel = os.path.relpath(path, ROOT).replace('\\', '/')
            boss = ns == MODID and idpath.startswith('entities/') and ec(idpath[len('entities/'):]) in bosses
            self.add_loot_table(tid, load_json(path), rel, boss)
            ec_tables.add(tid)
            n += 1
            if ns == MODID and idpath.startswith('blocks/'):
                block = ec(idpath[len('blocks/'):])
                if block not in self.w.ec_blocks:
                    self.problems.append(('多余的方块战利品表', '%s 对应的方块没注册' % tid))
                self.add(Rule('block_drop', '挖 / 破坏方块 ' + block, rel, [('block:' + block,)], ['loot:' + tid]))
            elif ns == MODID and idpath.startswith('entities/') and tid not in CODE_MAPPED_ENTITY_TABLES:
                entity = ec(idpath[len('entities/'):])
                self.add(Rule('entity_drop', '击杀 ' + entity, rel, [('entity:' + entity,)], ['loot:' + tid]))
        # 原版箱子表：只要表里引用到的本模组物品 / 表（原版物品本来就在开局里）
        for name in self.w.mc.names('data/minecraft/loot_tables/chests/'):
            tid = 'minecraft:' + name[len('data/minecraft/loot_tables/'):-5]
            if tid not in ec_tables:
                self.add_loot_table(tid, self.w.mc.json(name), 'client-extra.jar!/' + name)
        return n

    # ---------- 方块：物品放下去 ----------

    def load_blocks(self):
        for b in sorted(self.w.ec_blocks):
            if b in NOT_ITEMS:
                continue
            self.add(Rule('place', '放下 ' + b, '', [('item:' + b,)], ['block:' + b]))

    # ---------- 世界生成 ----------

    def biome_dims(self, biomes, hoanna_biomes):
        """群系写法（标签 / id / 列表）→ 维度集合"""
        if isinstance(biomes, list):
            out = set()
            for b in biomes:
                out |= self.biome_dims(b, hoanna_biomes)
            return out
        if isinstance(biomes, dict):
            return self.biome_dims(biomes.get('biomes', []), hoanna_biomes)
        if not isinstance(biomes, str):
            return set()
        if biomes in BIOME_TAG_DIMS:
            return {BIOME_TAG_DIMS[biomes]}
        if biomes.startswith('#'):
            members = self.w.resolve(self.w.biome_tags, biomes[1:]) or set()
            out = set()
            for m in members:
                out |= self.biome_dims(m, hoanna_biomes)
            return out
        if biomes in hoanna_biomes:
            return {ec('hoanna')}
        if biomes.startswith('minecraft:'):
            nether = {'minecraft:nether_wastes', 'minecraft:soul_sand_valley', 'minecraft:crimson_forest',
                      'minecraft:warped_forest', 'minecraft:basalt_deltas'}
            if biomes in nether:
                return {'minecraft:the_nether'}
            if biomes.startswith('minecraft:end') or biomes == 'minecraft:the_end' or biomes == 'minecraft:small_end_islands':
                return {'minecraft:the_end'}
            return {'minecraft:overworld'}
        return set()

    def feature_blocks(self, placed_id):
        """placed feature → 放的本模组方块（configured feature 里所有 "Name": "essentialcraft5:…"）和自定义地物类型"""
        ns, path = placed_id.split(':', 1)
        placed = self.find_data(ns, 'worldgen/placed_feature', path)
        if placed is None:
            return None, None
        cf = placed.get('feature')
        if isinstance(cf, dict):
            conf = cf
        else:
            cns, cpath = rid(cf).split(':', 1)
            conf = self.find_data(cns, 'worldgen/configured_feature', cpath)
        if conf is None:
            return [], None
        text = json.dumps(conf)
        blocks = sorted(set(re.findall(r'"Name": "(%s:[a-z0-9_/]+)"' % MODID, text)))
        return blocks, conf.get('type')

    def find_data(self, ns, sub, path):
        for base in RES_ROOTS:
            p = os.path.join(base, 'data', ns, *sub.split('/'), *path.split('/')) + '.json'
            if os.path.exists(p):
                return load_json(p)
        return None

    def load_worldgen(self):
        dim_file = self.find_data(MODID, 'dimension', 'hoanna')
        hoanna_biomes = set()
        noise_settings = None
        if dim_file:
            gen = dim_file.get('generator', {})
            src = gen.get('biome_source', {})
            for b in src.get('biomes', []):
                if isinstance(b, dict) and b.get('biome'):
                    hoanna_biomes.add(b['biome'])
            noise_settings = gen.get('settings')
        dim_hoanna = 'dim:' + ec('hoanna')

        def feature_rule(placed, dims, src):
            blocks, ftype = self.feature_blocks(placed)
            gives = ['block:' + b for b in (blocks or [])]
            if ftype in FEATURE_TYPE_OUTPUTS:
                fsrc, outs = FEATURE_TYPE_OUTPUTS[ftype]
                gives += outs
                src = src + ' + ' + fsrc
            if placed in VANILLA_FEATURE_OUTPUTS:
                gives += VANILLA_FEATURE_OUTPUTS[placed]
            if not gives:
                return
            for dim in sorted(dims):
                self.add(Rule('worldgen', '世界生成 %s（%s）' % (placed, dim), src, [('dim:' + dim,)], gives))

        for ns, idpath, path in iter_data_files('forge/biome_modifier'):
            d = load_json(path)
            rel = os.path.relpath(path, ROOT).replace('\\', '/')
            dims = self.biome_dims(d.get('biomes'), hoanna_biomes)
            if not dims:
                self.notes.append(('世界生成', '%s 的群系 %s 对应不到维度' % (rel, d.get('biomes'))))
            t = d.get('type')
            if t == 'forge:add_features':
                feats = d.get('features')
                for placed in ([feats] if isinstance(feats, str) else feats or []):
                    feature_rule(rid(placed), dims, rel)
            elif t in ('forge:add_spawns', MODID + ':add_spawn_costs'):
                # add_spawn_costs（13c，worldgen/AddSpawnCostsBiomeModifier）：spawners 写法同 forge:add_spawns，另带刷怪花费
                sp = d.get('spawners')
                for s in ([sp] if isinstance(sp, dict) else sp or []):
                    for dim in sorted(dims):
                        self.add(Rule('spawn', '自然刷出 %s（%s）' % (s['type'], dim), rel, [('dim:' + dim,)], ['entity:' + s['type']]))
        # 海纳尔的群系：地物、刷怪
        for b in sorted(hoanna_biomes):
            bns, bpath = b.split(':', 1)
            d = self.find_data(bns, 'worldgen/biome', bpath)
            if d is None:
                continue
            src = 'data/%s/worldgen/biome/%s.json' % (bns, bpath)
            for step in d.get('features', []):
                for placed in step:
                    if placed.startswith(MODID + ':') or placed in VANILLA_FEATURE_OUTPUTS:
                        feature_rule(placed, {ec('hoanna')}, src)
            for category, group in d.get('spawners', {}).items():
                for s in group:
                    if category == 'monster':
                        self.hoanna_monsters.add(s['type'])
                    if not s['type'].startswith('minecraft:'):
                        self.add(Rule('spawn', '自然刷出 %s（%s）' % (s['type'], b), src, [(dim_hoanna,)], ['entity:' + s['type']]))
        # 地表规则里的方块
        if isinstance(noise_settings, str):
            nns, npath = noise_settings.split(':', 1)
            ns_data = self.find_data(nns, 'worldgen/noise_settings', npath)
            if ns_data:
                blocks = sorted(set(re.findall(r'"Name": "(%s:[a-z0-9_/]+)"' % MODID, json.dumps(ns_data))))
                if blocks:
                    self.add(Rule('worldgen', '海纳尔地表', 'data/%s/worldgen/noise_settings/%s.json' % (nns, npath),
                                  [(dim_hoanna,)], ['block:' + x for x in blocks]))
        # 结构
        for ns, idpath, path in iter_data_files('worldgen/structure'):
            sid = ns + ':' + idpath
            d = load_json(path)
            rel = os.path.relpath(path, ROOT).replace('\\', '/')
            dims = self.biome_dims(d.get('biomes'), hoanna_biomes)
            if not dims:
                self.problems.append(('结构生成', '结构 %s 的群系 %s 对应不到任何维度（生成不出来）' % (sid, d.get('biomes'))))
            for dim in sorted(dims):
                self.add(Rule('structure', '结构 %s 生成在 %s' % (sid, dim), rel, [('dim:' + dim,)], ['structure:' + sid]))
            if sid in STRUCTURE_OUTPUTS:
                ssrc, outs = STRUCTURE_OUTPUTS[sid]
                self.add(Rule('structure', '结构 %s 里的方块和箱子' % sid, ssrc, [('structure:' + sid,)], outs))
            else:
                self.notes.append(('结构', '结构 %s 没登记里面放什么（STRUCTURE_OUTPUTS）' % sid))
        # 代码规则按群系判的地点（biome:#<群系标签>）：标签对应到哪个维度，进了那个维度就算找得到这种群系
        for tag in BIOME_SITES:
            dims = self.biome_dims(tag, hoanna_biomes)
            if not dims:
                self.problems.append(('群系标签', '群系标签 %s 对应不到任何维度的群系（用到它的代码规则做不到）' % tag))
            for dim in sorted(dims):
                self.add(Rule('worldgen', '群系 %s 在 %s' % (tag, dim), 'data/%s/tags/worldgen/biome/%s.json' % tuple(tag[1:].split(':', 1)),
                              [('dim:' + dim,)], ['biome:' + tag]))
        return hoanna_biomes

    # ---------- 恶魔交易 ----------

    def load_trades(self):
        entries = []
        removed = set()
        for ns, idpath, path in iter_data_files('demon_trades'):
            d = load_json(path)
            for e in d.get('entries', []):
                entries.append((e, os.path.relpath(path, ROOT).replace('\\', '/')))
            for r in d.get('remove', []) or []:
                removed.add(json.dumps(r, sort_keys=True))
        alts = []
        for e, src in entries:
            if json.dumps(e, sort_keys=True) in removed:
                continue
            if 'soul' in e:
                node = 'soul:' + rid(e['soul'])
                label = '%s 的灵魂 ×%s~%s' % (e['soul'], e.get('min', 1), e.get('max', 1))
            else:
                iid = rid(e['item'])
                if not self.w.known(iid):
                    self.problems.append(('交易池引用不存在的物品', '%s 里的 %s 没注册' % (src, iid)))
                    continue
                node = 'item:' + iid
                label = '%s ×%s' % (iid, e.get('count', 1))
            self.trades.append((label, node))
            alts.append(node)
        if alts:
            needs = [('summon:' + ec('demon'),), tuple(dict.fromkeys(alts))]
            self.add(Rule('trade', '恶魔交易：交给恶魔它要的东西（交易池任一条）→ 阿克罗尼特锭',
                          JAVA_PKG + 'entity/DemonEntity.java + data/*/demon_trades', needs, ['item:' + ec('ackronite_ingot')]))
            # 契约之誉（第十三批 13a，batch13.md §3.4）：每做成一笔 +1，交易次数不限，四档门槛都推得出来；13e 的立誓要 cap:demon_standing_<挚契>
            levels = []
            for key, default in (('regular', 8), ('patron', 20), ('blood', 40), ('oath', 70)):
                v = config_int(self.w, self.cfg, 'general.demon.standing.' + key, default)
                if v > 0 and v not in levels:
                    levels.append(v)
            gives = ['cap:demon_standing_%d' % v for v in levels]
            label = '契约之誉：和恶魔做成交易攒声望（%s）' % ' / '.join(map(str, levels))
            if self.w.cond('cfg:general.demon.standing.enabled', self.cfg):
                self.add(Rule('trade', label, JAVA_PKG + 'demon/DemonStanding.java + ' + JAVA_PKG + 'entity/DemonEntity.java', needs, gives))
            else:
                for g in gives:
                    self.disabled.setdefault(g, []).append((label, 'cfg:general.demon.standing.enabled'))
        return len(self.trades)

    # ---------- 进度 ----------

    def item_predicate(self, pred, ctx):
        """inventory_changed / consume_item 的物品条件 → 一组节点；None = 任何物品"""
        out = []
        for iid in pred.get('items', []) or []:
            iid = rid(iid)
            if not self.w.known(iid):
                self.problems.append(('进度引用不存在的物品', '%s 引用了 %s' % (ctx, iid)))
                continue
            out.append('item:' + iid)
        if pred.get('tag'):
            members = self.w.item_tag(rid(pred['tag']))
            if members is None:
                return None
            out += ['item:' + m for m in sorted(members)]
        if not out and not pred.get('items') and not pred.get('tag'):
            return None
        return tuple(out)

    def entity_type(self, pred):
        """实体条件 → 实体 id；None = 任何"""
        if isinstance(pred, list):
            for p in pred:
                if p.get('condition') == 'minecraft:entity_properties':
                    return self.entity_type(p.get('predicate', {}))
            return None
        if isinstance(pred, dict):
            t = pred.get('type')
            return rid(t) if isinstance(t, str) and not t.startswith('#') else None
        return None

    def entity_has_extra(self, pred):
        """实体条件里有没有 type 以外的子条件（脚本只解析 type）：字典形式看它自己的键；
        列表形式看每个 entity_properties 的 predicate，另有别的条件种类（不是 entity_properties）也算"""
        if isinstance(pred, list):
            for p in pred:
                if not isinstance(p, dict):
                    continue
                if p.get('condition') != 'minecraft:entity_properties' or self.entity_has_extra(p.get('predicate', {})):
                    return True
            return False
        if isinstance(pred, dict):
            return any(k != 'type' for k in pred)
        return False

    def check_registry(self, kind, rid_, ctx):
        """进度里写的实体类型 / 效果不存在时整个进度加载不了（这一步永远打不了勾）"""
        if rid_.startswith(MODID + ':') and (kind + ':' + rid_) not in self.w.ec_other:
            self.problems.append(('进度引用不存在的' + ('实体' if kind == 'entity' else '效果'),
                                  '%s 引用了 %s（进度加载不了）' % (ctx, rid_)))

    def criterion_needs(self, trig, cond, ctx):
        """一个条件 → needs（组的列表）；None = 不认识"""
        if trig in ('minecraft:player_killed_entity', 'minecraft:summoned_entity'):
            e = self.entity_type(cond.get('entity'))
            if e:
                self.check_registry('entity', e, ctx)
            # 实体条件里有 type 以外的子条件（nbt、location、equipment、flags……）时脚本解析不了，只认 type 会把条件放宽
            # （例：按 nbt {Type:2b} 只认大法师的条件会被当成「杀任何风法师」，绕过只靠运气的标记）：当成只能由 CODE_RULES 的显式规则给
            if self.entity_has_extra(cond.get('entity')):
                return [('code:' + ctx,)]
        if trig == 'minecraft:effects_changed':
            for k in (cond.get('effects') or {}):
                self.check_registry('effect', rid(k), ctx)
        if trig == 'minecraft:inventory_changed':
            groups = []
            for pred in cond.get('items', []) or []:
                g = self.item_predicate(pred, ctx)
                if g is not None:
                    groups.append(g)
            return groups
        if trig == 'minecraft:consume_item':
            g = self.item_predicate(cond.get('item', {}) or {}, ctx)
            return [g] if g is not None else []
        if trig == 'minecraft:impossible':
            return [('code:' + ctx,)]
        if trig == 'minecraft:player_killed_entity':
            e = self.entity_type(cond.get('entity'))
            return [('entity:' + e,)] if e else [('cap:ubmru',)]
        if trig == 'minecraft:summoned_entity':
            e = self.entity_type(cond.get('entity'))
            return [('summon:' + e,)] if e else None
        if trig == 'minecraft:changed_dimension':
            return [('dim:' + rid(cond['to']),)] if cond.get('to') else []
        if trig == 'minecraft:effects_changed':
            return [('effect:' + rid(k),) for k in (cond.get('effects') or {})]
        if trig == 'minecraft:location':
            groups = []
            for p in cond.get('player', []) or []:
                loc = (p.get('predicate') or {}).get('location') or {}
                if loc.get('structure'):
                    groups.append(('structure:' + rid(loc['structure']),))
                if loc.get('dimension'):
                    groups.append(('dim:' + rid(loc['dimension']),))
            return groups
        if trig == 'minecraft:item_used_on_block':
            groups = []
            for p in cond.get('location', []) or []:
                if p.get('condition') == 'minecraft:location_check':
                    for b in ((p.get('predicate') or {}).get('block') or {}).get('blocks', []) or []:
                        groups.append(('block:' + rid(b),))
                elif p.get('condition') == 'minecraft:match_tool':
                    g = self.item_predicate(p.get('predicate') or {}, ctx)
                    if g is not None:
                        groups.append(g)
            return groups
        if trig == MODID + ':structure_formed':
            return [('struct:' + rid(cond['structure']),)]
        if trig == MODID + ':enrichment_chamber_formed':
            hc = cond.get('holding_chambers')
            big = (isinstance(hc, dict) and (hc.get('min') or 0) > 1) or (isinstance(hc, (int, float)) and hc > 1)
            return [('struct:' + ec('enrichment_chamber_big' if big else 'enrichment_chamber'),)]
        if trig == 'minecraft:recipe_unlocked':
            return [('recipe:' + rid(cond['recipe']),)]
        if trig == 'minecraft:tick':
            return []
        return None

    def load_advancements(self):
        advs = {}
        for ns, idpath, path in iter_data_files('advancements'):
            if ns != MODID or idpath.startswith('recipes/'):
                continue
            aid = ns + ':' + idpath
            d = load_json(path)
            rel = os.path.relpath(path, ROOT).replace('\\', '/')
            crits = {}
            for name, c in (d.get('criteria') or {}).items():
                needs = self.criterion_needs(c.get('trigger'), c.get('conditions') or {}, aid)
                if needs is None:
                    self.problems.append(('进度条件不认识', '%s 的条件 %s（%s）脚本不认识，按做不到算' % (aid, name, c.get('trigger'))))
                    needs = [('unknown:' + aid,)]
                crits[name] = needs
            reqs = d.get('requirements') or [[n] for n in crits]
            # 每个 requirement 组：组内任一条件成立；组与组全部要成立 → 组内条件多于一个时拆成多条规则
            alternatives = [[]]
            for group in reqs:
                nxt = []
                for alt in alternatives:
                    for name in group:
                        nxt.append(alt + crits.get(name, []))
                alternatives = nxt
            for needs in alternatives:
                self.add(Rule('advancement', '进度 ' + aid, rel, needs, ['adv:' + aid]))
            advs[aid] = {'path': rel, 'display': bool(d.get('display')),
                         'criteria': {k: c.get('trigger') for k, c in (d.get('criteria') or {}).items()}}
        return advs

    # ---------- 代码规则 ----------

    def pending_ids(self, nodes):
        """节点里还没注册的本模组物品 / 方块：(命名表里登记了的, 命名表里也没有的)。
        实体的名字先写进各路的语言键文件，从语言文件看不出注册没有，改看刷怪蛋：命名表登记了 <实体>_spawn_egg、蛋还没注册的算还没注册"""
        planned, unknown = [], []
        for n in nodes:
            kind, _, body = n.partition(':')
            if kind in ('item', 'block') and body.startswith(MODID + ':') and body not in self.w.ec_names:
                (planned if body in self.w.planned else unknown).append(body)
            elif kind in ('entity', 'summon', 'soul') and body.startswith(MODID + ':'):
                egg = body + '_spawn_egg'
                if egg in self.w.planned and egg not in self.w.ec_names:
                    planned.append(body)
        return planned, unknown

    def load_code_rules(self, cfg_over):
        n = 0
        ranks_loaded = self.matrix_ranks > 0
        if not ranks_loaded and self.w.cond('cfg:general.matrix_rank.enabled', cfg_over):
            if os.path.exists(os.path.join(ROOT, MATRIX_RANK_READER)):
                self.problems.append(('矩阵阶数据', '有读取器 %s，却没有任何 data/*/matrix_ranks/rank_<n>.json：游戏里升不了阶，'
                                                 '海纳尔之门、全息投影台的门永远关着' % MATRIX_RANK_READER))
            else:
                self.notes.append(('矩阵阶数据', '还没有矩阵阶数据和读取器（%s），门按开着算' % MATRIX_RANK_READER))
        if ranks_loaded:
            for gate in MATRIX_GATES:
                need = matrix_gate(self.w, cfg_over, gate, ranks_loaded)
                if need > self.matrix_top:
                    self.notes.append(('矩阵阶的门', 'general.matrix_rank.gates.%s = %d，读到的阶数据最高只到 %d 阶：这道门永远打不开'
                                       % (gate, need, self.matrix_top)))
        for src in sorted(PENDING_SOURCES):
            if os.path.exists(os.path.join(ROOT, src)):
                self.notes.append(('代码规则出处', '%s 已经在了，从 PENDING_SOURCES 删掉这一行' % src))
        for r in CODE_RULES + batch13_rules(self.w, cfg_over, ranks_loaded):
            if r['cfg'] is not None and not self.w.cond(r['cfg'], cfg_over):
                for g in r['gives']:
                    self.disabled.setdefault(g, []).append((r['label'], r['cfg']))
                continue
            needs = [self.expand(e, r['label']) for e in r['needs']]
            gives = []
            for g in r['gives']:
                if g.startswith('#give:'):
                    members = self.w.item_tag(g[len('#give:'):]) or set()
                    gives += ['item:' + m for m in sorted(members)]
                else:
                    gives.append(g)
            # 新批次开工时命名表先登记、各路再注册：用到的东西在命名表里却还没注册的规则先不算（记一条提示），
            # 命名表里也没有的才是写错了（照旧算数据问题）
            planned, unknown = self.pending_ids([x for grp in needs for x in grp] + gives)
            if planned and not unknown:
                self.notes.append(('代码规则', '「%s」用到的 %s 在命名表里、还没注册，这条先不算' % (r['label'], '、'.join(sorted(set(planned))))))
                for g in gives:
                    self.disabled.setdefault(g, []).append((r['label'], '%s 注册以后' % '、'.join(sorted(set(planned)))))
                continue
            for grp in needs:
                self.check_node_ids(grp, '代码规则「%s」' % r['label'])
            self.check_node_ids(gives, '代码规则「%s」' % r['label'])
            for src in r['src'].split(' + '):
                if src.startswith('src/') and not os.path.exists(os.path.join(ROOT, src)):
                    if src in PENDING_SOURCES:
                        self.notes.append(('代码规则出处', '「%s」的出处 %s 还没合进来（PENDING_SOURCES），规则照算' % (r['label'], src)))
                    else:
                        self.problems.append(('代码规则出处', '「%s」写的出处 %s 不存在（类改名或挪了，核对后改 CODE_RULES）' % (r['label'], src)))
            self.add(Rule('code', r['label'], r['src'], needs, gives, r['cfg'], r.get('luck', False)))
            n += 1
        return n


# ======================================================================
# 求解
# ======================================================================

class Solver:
    def __init__(self, graph, world, cfg_over):
        self.g = graph
        self.w = world
        self.cfg = cfg_over
        self.layer = {}         # 节点 → 第几轮推出来
        self.via = {}           # 节点 → 规则
        self.rank = {}          # 节点 → 获取链要去的最远维度（DIM_PREFERENCE）
        self.depth_memo = {}
        self.cap = None         # run 的分遍上限：维度 / 原版门槛排位高于它的当作还没有
        self.producers = collections.defaultdict(list)
        self.root_reason = {}   # 根源 → 原因
        self.closure_memo = {}
        self.root_memo = {}     # 节点 → 根源列表
        for r in graph.rules:
            for n in r.gives:
                self.producers[n].append(r)

    def implicit(self, node):
        kind, _, body = node.partition(':')
        if kind == 'item':
            return self.w.vanilla_obtainable(body)
        if kind == 'block':
            return body.startswith('minecraft:')        # 原版方块世界里都有（岩浆、水这类拿不到物品形态的也在）
        if kind == 'entity':
            return body.startswith('minecraft:')
        if kind == 'dim':
            return body in VANILLA_DIMS
        return False

    def has(self, node):
        if node in self.layer:
            return True
        if not self.implicit(node):
            return False
        return self.cap is None or self.node_rank(node) <= self.cap

    def rule_ok(self, r, extra=None):
        """这条规则要的东西是不是都有了（extra：假设也有的节点，closure_with 用）。「凑够几种」的规则数组里有几个"""
        def got(n):
            return self.has(n) or (extra is not None and n in extra)
        if r.count is not None:
            return sum(1 for n in r.needs[0] if got(n)) >= r.count
        return all(any(got(n) for n in grp) for grp in r.needs)

    def missing_groups(self, r):
        """还缺的组；「凑够几种」缺数时返回一组：还没拿到的那些"""
        if r.count is not None:
            if self.rule_ok(r):
                return []
            return [tuple(n for n in r.needs[0] if not self.has(n))]
        return [grp for grp in r.needs if not any(self.has(n) for n in grp)]

    def run(self):
        """分几遍推：第 0 遍只许用主世界的东西，第 1 遍放开下界，第 2 遍末地，第 3 遍海纳尔（DIM_PREFERENCE、VANILLA_GATE_RANK）。
        每遍里一轮一轮推，每轮把需要的东西都已经有了的规则全部触发。这样每件东西记下的获取链都是「去的维度最近、步数最少」的那条，
        报告里的门槛和深度按它算；能不能拿到和分不分遍无关。"""
        pending = list(self.g.rules)
        layer = 0
        for cap in range(max(DIM_PREFERENCE.values()) + 1):
            self.cap = cap
            while True:
                layer += 1
                fired = []
                rest = []
                for r in pending:
                    if self.rule_ok(r):
                        fired.append(r)
                    else:
                        rest.append(r)
                for r in fired:
                    for n in r.gives:
                        if not self.has(n):
                            self.layer[n] = layer
                            self.via[n] = r
                            self.rank[n] = cap
                pending = rest
                if not fired:
                    break
        self.cap = None
        self.unfired = pending
        self.compute_costs()
        return layer

    def node_rank(self, node):
        if node in self.rank:
            return self.rank[node]
        kind, _, body = node.partition(':')
        if kind == 'dim':
            return DIM_PREFERENCE.get(body, 0)
        if kind in ('item', 'block') and body.startswith('minecraft:'):
            return VANILLA_GATE_RANK.get(body.split(':', 1)[1], 0)
        return 0

    def node_layer(self, node):
        if node in self.layer:
            return self.layer[node]
        return 0 if self.implicit(node) else None

    def depth(self, node, memo=None):
        """获取链的步数（最长的那条支路）：开局就有的是 0"""
        memo = self.depth_memo
        if node in memo:
            return memo[node]
        r = self.via.get(node)
        if r is None:
            memo[node] = 0
            return 0
        memo[node] = 0
        d = 0
        for grp in r.needs:
            alt = self.best_alt(grp)
            if alt:
                d = max(d, self.depth(alt))
        memo[node] = d + 1
        return d + 1

    def best_alt(self, grp):
        best = None
        for n in grp:
            l = self.node_layer(n)
            if l is not None and self.has(n):
                key = (self.node_rank(n), l, n)
                if best is None or key < best:
                    best = key
        return best[2] if best else None

    def chain(self, node, out=None, seen=None):
        """最短获取链：按推出来的顺序列（先列前置）"""
        out = [] if out is None else out
        seen = set() if seen is None else seen
        if node in seen:
            return out
        seen.add(node)
        r = self.via.get(node)
        if r is None:
            return out
        for grp in r.needs:
            alt = self.best_alt(grp)
            if alt:
                self.chain(alt, out, seen)
        if r not in [x for x, _ in out]:
            out.append((r, node))
        return out

    def vanilla_used(self, node):
        used = set()
        stack = [node]
        seen = set()
        while stack:
            n = stack.pop()
            if n in seen:
                continue
            seen.add(n)
            r = self.via.get(n)
            if r is None:
                if n.startswith('item:minecraft:') or n.startswith('block:minecraft:'):
                    used.add(n.split(':', 1)[1])
                continue
            for grp in r.needs:
                alt = self.best_alt(grp)
                if alt:
                    stack.append(alt)
        return used

    def gates(self, node):
        """获取链经过的门槛：原版物品（VANILLA_GATES）和要去的维度"""
        used = {u.split(':', 1)[1] for u in self.vanilla_used(node)}
        out = [g for g, names in VANILLA_GATES.items() if used & names]
        dims = self.dims_used(node)
        if 'minecraft:the_nether' in dims and '下界' not in out:
            out.insert(0, '下界')
        if 'minecraft:the_end' in dims and '末地' not in out:
            out.append('末地')
        if ec('hoanna') in dims:
            out.append('海纳尔')
        return out

    def dims_used(self, node):
        found = set()
        stack = [node]
        seen = set()
        while stack:
            n = stack.pop()
            if n in seen:
                continue
            seen.add(n)
            if n.startswith('dim:'):
                found.add(n[4:])
            r = self.via.get(n)
            if r is None:
                continue
            for grp in r.needs:
                alt = self.best_alt(grp)
                if alt:
                    stack.append(alt)
        return found

    def why_no_source(self, node):
        body = node.split(':', 1)[1] if ':' in node else node
        if node in self.g.disabled:
            return '来源被配置关掉（或用到的东西还没注册）：' + '；'.join('「%s」要 %s' % (label, cfg if isinstance(cfg, str) else ' 且 '.join(cfg))
                                               for label, cfg in self.g.disabled[node])
        if node.startswith('item:') and body in self.w.removed:
            return '已去掉（命名表 removed）'
        if node.startswith('item:') and body in CREATIVE_ONLY:
            return '只放创造栏'
        if node.startswith('item:') and body in WORLD_ONLY:
            return '只在世界里出现（物品形态拿不到）'
        return '没有任何来源'

    def compute_costs(self):
        """拿不到的节点离「根源」（没有来源的节点）有多远：根源 0，其余 = 1 + 最近那条来源里最远的缺项。
        只在彼此之间绕圈、到不了任何根源的节点没有距离（纯循环）。explain 按这个挑路，不会被「挖方块掉自己」这种圈带偏。"""
        INF = float('inf')
        cost = {}
        for n in self.producers_all():
            if not self.has(n) and not self.producers.get(n):
                cost[n] = 0
        changed = True
        while changed:
            changed = False
            for r in self.unfired:
                c = 0
                if r.count is not None:
                    # 凑够几种：还差 k 种时，按最近的那 k 种里最远的一种算
                    short = r.count - sum(1 for n in r.needs[0] if self.has(n))
                    costs = sorted(cost.get(n, INF) for n in r.needs[0] if not self.has(n))
                    c = costs[short - 1] if 0 < short <= len(costs) else (0 if short <= 0 else INF)
                for grp in (r.needs if r.count is None else []):
                    if any(self.has(n) for n in grp):
                        continue
                    best = min((cost.get(n, INF) for n in grp), default=INF)
                    if best == INF:
                        c = INF
                        break
                    c = max(c, best)
                if c == INF:
                    continue
                for n in r.gives:
                    if not self.has(n) and cost.get(n, INF) > c + 1:
                        cost[n] = c + 1
                        changed = True
        self.cost = cost

    def producers_all(self):
        nodes = set(self.producers)
        for r in self.g.rules:
            for grp in r.needs:
                nodes.update(grp)
        return nodes

    def node_cost(self, node):
        if self.has(node):
            return -1
        if not self.producers.get(node):
            return 0
        return self.cost.get(node, float('inf'))

    def best_rule(self, node):
        best = None
        for r in self.producers.get(node, []):
            missing = self.missing_groups(r)
            rc = max((min(self.node_cost(n) for n in grp) for grp in missing if grp), default=0)
            key = (rc, len(missing), r.id)
            if best is None or key < best[0]:
                best = (key, r, missing)
        return best

    CYCLE_REASON = '只能从它自己绕一圈得到（例如只有放下再挖掉落），没有别的来源'

    def choose(self, node, path):
        """拿不到的节点往下追一步：返回 ('root', 原因) 或 ('rule', 规则, [(缺的那组, 往下追的那个)])。
        离根源有距离的按距离走；只在彼此之间绕圈的（距离无穷大）挑缺得最少、不绕回当前这条链的来源，
        每条来源都绕回链上时，这个节点本身就是断点。"""
        INF = float('inf')
        c = self.node_cost(node)
        if c == 0:
            return ('root', self.why_no_source(node))
        prods = self.producers.get(node, [])
        if prods and all(r.count is not None and len(r.needs[0]) < r.count for r in prods):
            return ('root', '能记的种类不够：一共只有 %d 种，要 %d 种' % (len(prods[0].needs[0]), prods[0].count))
        if c != INF:
            _, r, missing = self.best_rule(node)
            return ('rule', r, [(grp, min(grp, key=lambda n: (self.node_cost(n), n))) for grp in missing if grp])
        on_path = set(path) | {node}
        best = None
        heads = []
        for r in prods:
            missing = [grp for grp in self.missing_groups(r) if grp]
            picks = []
            for grp in missing:
                cands = [n for n in grp if n not in on_path]
                if not cands:
                    heads += [n for n in grp if n in on_path]
                    picks = None
                    break
                picks.append((grp, min(cands, key=lambda n: (self.node_cost(n), n))))
            if picks is None:
                continue
            key = (len(missing), r.kind in ('place', 'block_drop', 'loot'), r.id)
            if best is None or key < best[0]:
                best = (key, r, picks)
        if best is None:
            if node in self.g.disabled or not heads:
                return ('root', self.why_no_source(node) if node in self.g.disabled else self.CYCLE_REASON)
            # 每条来源都绕回链上更早的那个节点：断点记在那个节点（圈的入口）上
            order = {n: i for i, n in enumerate(path)}
            head = min((h for h in heads if h in order), key=lambda h: order[h], default=node)
            return ('cycle', head)
        return ('rule', best[1], best[2])

    def root_set(self, node, path=()):
        """拿不到的节点最终断在哪些根源上"""
        if self.has(node):
            return []
        if node in self.root_memo:
            return self.root_memo[node]
        step = self.choose(node, path)
        if step[0] == 'root':
            self.root_reason[node] = step[1]
            out = [node]
        elif step[0] == 'cycle':
            self.root_reason[step[1]] = self.CYCLE_REASON
            out = [step[1]]
        else:
            out = []
            for grp, alt in step[2]:
                for x in self.root_set(alt, path + (node,)):
                    if x not in out:
                        out.append(x)
        self.root_memo[node] = out
        return out

    def explain(self, node, depth=0, done=None, lines=None, max_depth=10, path=()):
        """拿不到的节点：沿最近的根源往下找断在哪，返回（文字行，根源 → 原因）"""
        top = done is None
        done = set() if done is None else done
        lines = [] if lines is None else lines
        pad = '    ' * depth
        if not self.has(node):
            if node in done:
                lines.append(pad + '- %s：同上' % self.label(node))
            else:
                done.add(node)
                step = self.choose(node, path)
                if step[0] == 'root':
                    lines.append(pad + '- %s：%s' % (self.label(node), step[1]))
                elif step[0] == 'cycle':
                    lines.append(pad + '- %s：绕回%s——%s' % (self.label(node), self.label(step[1]), self.CYCLE_REASON))
                else:
                    _, r, picks = step
                    others = len(self.producers.get(node, [])) - 1
                    lines.append(pad + '- %s ← 「%s」（%s）缺 %d 项%s' % (
                        self.label(node), r.label, r.src or '-', len(picks), '；另有 %d 条来源也不通' % others if others else ''))
                    if r.count is not None:
                        have = sum(1 for n in r.needs[0] if self.has(n))
                        lines.append(pad + '    （要凑 %d 种，拿得到 %d 种，还差 %d 种）' % (r.count, have, r.count - have))
                    if depth >= max_depth:
                        lines.append(pad + '    ……（再往下见「根源」）')
                    else:
                        for grp, alt in picks:
                            if len(grp) > 1:
                                lines.append(pad + '    - 以下任一都拿不到：' + '、'.join(self.label(x) for x in grp[:6]) + ('……' if len(grp) > 6 else ''))
                            self.explain(alt, depth + (2 if len(grp) > 1 else 1), done, lines, max_depth, path + (node,))
        if not top:
            return lines, None
        roots = collections.OrderedDict((k, self.root_reason[k]) for k in self.root_set(node))
        return lines, roots

    # ---------- 单点修复：只要让哪一样东西拿得到，这一步就通 ----------

    FIXER_KINDS = ('item', 'block', 'dim', 'struct', 'entity', 'summon', 'cap', 'soul', 'effect', 'structure')

    def upstream_unreached(self, node, limit=200):
        """往上游找拿不到的节点（按离得近的先），只收 FIXER_KINDS 这几类"""
        out = []
        seen = {node}
        queue = collections.deque([node])
        while queue and len(out) < limit:
            n = queue.popleft()
            for r in self.producers.get(n, []):
                for grp in self.missing_groups(r):
                    for x in grp:
                        if x not in seen:
                            seen.add(x)
                            queue.append(x)
                            if x.split(':', 1)[0] in self.FIXER_KINDS:
                                out.append(x)
        return out

    def closure_with(self, extra):
        """假设 extra 拿得到，还能多推出哪些节点（只看还没触发的规则）"""
        if extra in self.closure_memo:
            return self.closure_memo[extra]
        got = {extra}
        pending = self.unfired
        changed = True
        while changed:
            changed = False
            rest = []
            for r in pending:
                if self.rule_ok(r, got):
                    for n in r.gives:
                        if n not in got and not self.has(n):
                            got.add(n)
                            changed = True
                else:
                    rest.append(r)
            pending = rest
        self.closure_memo[extra] = got
        return got

    def fixers(self, node, show=3):
        """单独补上就能让 node 拿得到的节点里，最靠上游的几个（补上它，其余的也都跟着通）"""
        cands = [c for c in self.upstream_unreached(node) if node in self.closure_with(c)]
        if not cands:
            return []
        size = {c: len(self.closure_with(c)) for c in cands}
        top = max(size.values())
        best = [c for c in cands if size[c] == top]
        prio = {k: i for i, k in enumerate(self.FIXER_KINDS)}
        best.sort(key=lambda c: (prio.get(c.split(':', 1)[0], 99), c))
        return best[:show]

    def label(self, node):
        kind, _, body = node.partition(':')
        names = {'item': '物品', 'block': '方块', 'entity': '实体', 'dim': '维度', 'struct': '结构', 'summon': '召唤',
                 'effect': '效果', 'loot': '战利品表', 'adv': '进度', 'structure': '世界结构', 'soul': '灵魂',
                 'cap': '能力', 'recipe': '配方', 'code': '代码发的进度', 'site': '世界生成的地点', 'biome': '群系',
                 'unknown': '不认识的条件'}
        if kind in ('item', 'block'):
            return '%s %s' % (names[kind], self.w.name(body))
        return '%s %s' % (names.get(kind, kind), body)


# ======================================================================
# 手册步骤
# ======================================================================

def step_optional(world, raw, cfg_over):
    """条目 optional 在这种配置下是不是可选：布尔照旧；字符串 "true" / "false" 按布尔读；别的字符串 / 数组是按条件可选，
    和 if 同一套求值（World.cond）；写坏的按必做（和引擎 GuideSteps.OptionalRule 一致）"""
    if isinstance(raw, bool):
        return raw
    if isinstance(raw, str):
        t = raw.strip().lower()
        if t in ('true', 'false'):
            return t == 'true'
        return bool(raw.strip()) and world.cond(raw, cfg_over)
    if isinstance(raw, list):
        return bool(raw) and all(isinstance(x, str) for x in raw) and world.cond(raw, cfg_over)
    return False


def load_steps(world, cfg_over=None):
    steps = []
    for path in sorted(glob.glob(os.path.join(GUIDE_DIR, '*.json'))):
        d = load_json(path)
        cat = os.path.basename(path)[:-5]
        for e in d.get('entries', []):
            if not e.get('complete'):
                continue
            steps.append({
                'category': cat, 'cat_title': world.plain(d.get('title', cat)), 'order': d.get('order', 0), 'zone': d.get('group', ''),
                'cat_if': d.get('if'), 'entry': e.get('id'), 'title': world.plain(e.get('title', e.get('id'))),
                'complete': e['complete'], 'optional': step_optional(world, e.get('optional'), cfg_over or {}), 'if': e.get('if'),
                'optional_raw': e.get('optional'),
            })
    steps.sort(key=lambda s: (s['order'], s['category']))
    return steps


def load_guide_mentions(world, cfg_over):
    """手册里每件本模组物品「第一次出现」在哪一章（条目的 items 表、配方块），以及正文 / 物品表提到的全部物品。
    返回（物品 → (章顺序, 章 id, 条目 id)，[(章 id, 条目 id, 物品)]）；被条件藏掉的章、条目不算。"""
    home = {}
    mentions = []
    pat = re.compile(r'\{item:(%s:[a-z0-9_/]+)\}' % MODID)
    for path in sorted(glob.glob(os.path.join(GUIDE_DIR, '*.json'))):
        d = load_json(path)
        cat = os.path.basename(path)[:-5]
        if not world.cond(d.get('if'), cfg_over):
            continue
        order = d.get('order', 0)
        for e in d.get('entries', []):
            if not world.cond(e.get('if'), cfg_over):
                continue
            entry_items = visible_entry_items(e.get('items'), world, cfg_over)
            listed = list(entry_items)
            for b in e.get('blocks') or []:
                if b.get('type') == 'recipe' and world.cond(b.get('if'), cfg_over):
                    listed += [b['item']] if b.get('item') else []
                    listed += list(b.get('items') or [])
            for iid in listed:
                if isinstance(iid, str) and iid.startswith(MODID + ':'):
                    if iid not in home or (order, cat) < home[iid][:2]:
                        home[iid] = (order, cat, e.get('id'))
            text = json.dumps({'items': entry_items, 'blocks': visible_part(e.get('blocks') or [], world, cfg_over)},
                              ensure_ascii=False)
            seen = set(pat.findall(text)) | {i for i in listed if isinstance(i, str) and i.startswith(MODID + ':')}
            for iid in sorted(seen):
                mentions.append((cat, e.get('id'), iid))
    return home, mentions


def visible_entry_items(items, world, cfg_over):
    """条目 items 里当前配置下算数的物品 id：不在条件组里的，加上 if 成立的条件组里的（FORMAT.md §1.2，和引擎的 EntryItems 一致：
    组缺 if 或 if 不是字符串 / 数组时整组不算）"""
    out = []
    for i in items if isinstance(items, list) else []:
        if isinstance(i, str):
            out.append(i)
        elif (isinstance(i, dict) and isinstance(i.get('if'), (str, list)) and isinstance(i.get('items'), list)
              and world.cond(i['if'], cfg_over)):
            out.extend(x for x in i['items'] if isinstance(x, str))
    return out


def visible_part(node, world, cfg_over):
    """去掉条件不成立的块 / 行（行级 if：list、steps 的项，table、kv 的行）"""
    if isinstance(node, list):
        return [visible_part(x, world, cfg_over) for x in node
                if not (isinstance(x, dict) and 'if' in x and not world.cond(x['if'], cfg_over))]
    if isinstance(node, dict):
        return {k: visible_part(v, world, cfg_over) for k, v in node.items() if k != 'if'}
    return node


def forward_refs(solver, node, home, step_order, zone_of):
    """按手册顺序挑材料（同一格能用几种东西时挑最早讲到的）走一遍获取链，返回链上「讲在后面章节 / 支线」的本模组物品"""
    out = []
    seen = set()
    stack = [node]
    while stack:
        n = stack.pop()
        if n in seen:
            continue
        seen.add(n)
        if n.startswith('item:' + MODID + ':'):
            iid = n[len('item:'):]
            h = home.get(iid)
            if is_late(h, step_order, zone_of):
                out.append((iid, h))
        r = pick_ordered_rule(solver, n, home, step_order, zone_of)
        if r is None:
            continue
        for grp in r.needs:
            stack.append(min((x for x in grp if solver.has(x)), key=lambda x: ordered_key(solver, x, home, step_order, zone_of)))
    return out


def is_late(home_entry, step_order, zone_of):
    return home_entry is not None and (home_entry[0] > step_order or zone_of.get(home_entry[1]) not in ('main', 'start'))


def ordered_key(solver, x, home, step_order, zone_of):
    h = home.get(x[len('item:'):]) if x.startswith('item:') else None
    return (is_late(h, step_order, zone_of), h[0] if h else 0, solver.node_layer(x) or 0, x)


def pick_ordered_rule(solver, n, home, step_order, zone_of):
    """这件东西挑哪条来源：能用的来源里，直接要的东西「讲在后面」最少的；一样多时取最早推出来的"""
    if n not in solver.via:
        return None
    best = None
    for r in solver.producers.get(n, []):
        if not solver.rule_ok(r):
            continue
        late = 0
        depth = 0
        for grp in r.needs:
            k = min(ordered_key(solver, x, home, step_order, zone_of) for x in grp if solver.has(x))
            late += 1 if k[0] else 0
            depth = max(depth, k[2])
        if any(g == (n,) for g in r.needs):
            continue
        key = (late, depth, r.id)
        if best is None or key < best[0]:
            best = (key, r)
    return best[1] if best else solver.via[n]


# ======================================================================
# 报告
# ======================================================================

def run_scenario(name, cfg_over, args, mc_jar, forge_jar, out, detail, known=None):
    """known：这种配置本来就会断（KNOWN_BREAKS 的说明），只列「断在哪」的归类，不逐步展开获取链"""
    world = World(mc_jar, forge_jar)
    g = Graph(world)
    g.cfg = cfg_over
    stats = collections.OrderedDict()
    stats['配方'] = sum(g.load_recipes().values())
    stats['战利品表'] = g.load_loot()
    g.load_blocks()
    g.load_worldgen()
    g.load_attunement()
    stats['矩阵阶'] = g.load_matrix_ranks()
    stats['交易条目'] = g.load_trades()
    advs = g.load_advancements()
    stats['代码规则'] = g.load_code_rules(cfg_over)
    # 代码发的进度：impossible 条件由代码规则给（code:<进度> 节点 = 进度本身）
    for r in g.rules:
        r.needs = [tuple(('adv:' + n[len('code:'):]) if n.startswith('code:') else n for n in grp) for grp in r.needs]
    # impossible 进度的规则 needs 自己（adv:X ← adv:X），删掉这种自环
    g.rules = [r for r in g.rules if not (r.kind == 'advancement' and any(grp == tuple(r.gives) for grp in r.needs))]
    for i, r in enumerate(g.rules):
        r.id = i
    solver = Solver(g, world, cfg_over)
    layers = solver.run()

    steps = load_steps(world, cfg_over)
    visible = [s for s in steps if world.cond(s['cat_if'], cfg_over) and world.cond(s['if'], cfg_over)]
    hidden = len(steps) - len(visible)

    def step_ok(s):
        return solver.has('adv:' + s['complete'])

    broken = [s for s in visible if not step_ok(s)]
    missing_adv = [s for s in visible if s['complete'] not in advs]
    for s in missing_adv:
        g.problems.append(('手册步骤', '%s/%s 的 complete 指向不存在的进度 %s' % (s['category'], s['entry'], s['complete'])))

    # 物品审计
    items = sorted(n for n in world.ec_names if n not in NOT_ITEMS)
    reach_items = [i for i in items if solver.has('item:' + i)]
    expected = [i for i in items if not solver.has('item:' + i) and (i in CREATIVE_ONLY or i in WORLD_ONLY)]
    unreach = [i for i in items if not solver.has('item:' + i) and i not in CREATIVE_ONLY and i not in WORLD_ONLY]
    creative_reachable = [i for i in items if solver.has('item:' + i) and (i in CREATIVE_ONLY or i in WORLD_ONLY)]
    world_only_broken = sorted(i for i in WORLD_ONLY if not solver.has('block:' + i))

    main_req_ok = [s for s in visible if s['zone'] == 'main' and not s['optional'] and step_ok(s)]
    luck_rules, luck_only, luck_solver = luck_check(g, world, cfg_over, main_req_ok)
    migration = migration_check(g, world, solver, visible, check_reach=not cfg_over)
    g.problems.extend(('迁移表', text) for text in migration['problems'])
    asc_problems, asc_notes = ascension_check(g, world)
    g.problems.extend(('升格', text) for text in asc_problems)
    g.notes.extend(('升格', text) for text in asc_notes)

    w = out.write
    w('=' * 78 + '\n')
    w('情形：%s%s\n' % (name, ('（' + '，'.join('%s=%s' % kv for kv in cfg_over.items()) + '）') if cfg_over else ''))
    w('=' * 78 + '\n')
    main = [s for s in visible if s['zone'] == 'main']
    main_req = [s for s in main if not s['optional']]
    side = [s for s in visible if s['zone'] != 'main']
    w('图：%d 条规则（%s），分 4 遍共推了 %d 轮；拿得到的节点 %d 个\n' % (
        len(g.rules), '，'.join('%s %d' % kv for kv in stats.items()), layers - 1, len(solver.layer)))
    w('手册步骤：%d 步（主线 %d，其中必做 %d；支线 %d）；被条件藏掉 %d 步\n' % (len(visible), len(main), len(main_req), len(side), hidden))
    w('  做得到：主线 %d/%d，主线必做 %d/%d，支线 %d/%d\n' % (
        sum(step_ok(s) for s in main), len(main), sum(step_ok(s) for s in main_req), len(main_req),
        sum(step_ok(s) for s in side), len(side)))
    # 主线终点（第十三批 13e 起是第二十三章的九阶）：照手册顺序最后一个必做主线步骤；被条件藏掉时（矩阵阶关闭）只打印实际的最后一步
    chapters = list(dict.fromkeys(s['category'] for s in main))
    last = main_req[-1] if main_req else None
    if last is not None:
        w('主线：%d 章（有步骤的主线分类），必做 %d 步；最后一步 %s/%s（进度 %s）%s\n' % (
            len(chapters), len(main_req), last['category'], last['entry'], last['complete'], '' if step_ok(last) else '，做不到'))
        if last['complete'] != MAIN_FINAL_STEP and any(s['complete'] == MAIN_FINAL_STEP for s in main_req):
            g.notes.append(('主线终点', '主线终点应是 %s，照手册顺序最后一个必做步骤却是 %s/%s（核对 order 和条目顺序）'
                            % (MAIN_FINAL_STEP, last['category'], last['entry'])))
        elif not any(s['complete'] == MAIN_FINAL_STEP for s in steps):
            g.notes.append(('主线终点', '手册里还没有完成条件是 %s 的步骤（第二十三章还没写进来时是这样）' % MAIN_FINAL_STEP))
    w('物品：注册 %d 件（不含门方块这类非物品）；拿得到 %d；只放创造栏 / 只在世界里出现（预期拿不到）%d；生存拿不到 %d\n' % (
        len(items), len(reach_items), len(expected), len(unreach)))
    shown_advs = sorted(a for a, v in advs.items() if v['display'])
    adv_missing = [a for a in shown_advs if not solver.has('adv:' + a)]
    w('进度页（带 display 的进度）：%d 个，做得到 %d%s\n' % (
        len(shown_advs), len(shown_advs) - len(adv_missing), ('；做不到：' + '、'.join(adv_missing)) if adv_missing else ''))
    w('恶魔交易池：%d 条，其中交得出来 %d 条\n' % (len(g.trades), sum(solver.has(n) for _, n in g.trades)))
    w('断链：手册步骤 %d 处；只靠运气的必做步骤 %d 处；数据问题 %d 处（其中迁移表 %d 处）\n\n' % (
        len(broken), len(luck_only), len(g.problems), len(migration['problems'])))
    if known:
        w(('已知会断：%s。\n\n' if broken else '预期这种配置会断链，这次却全通了（%s），核一下 progression.md 的记录和配置注释还对不对。\n\n') % known)

    if detail:
        w('--- 手册步骤（✓ 做得到 / ✗ 断链；深度 = 获取链最长那条支路的步数；门槛 = 获取链要去的维度、用到的原版门槛物品）---\n')
        cur = None
        for s in visible:
            if s['category'] != cur:
                cur = s['category']
                w('\n[%s] %s（%s）\n' % ('主线' if s['zone'] == 'main' else '支线', s['cat_title'], s['category']))
            node = 'adv:' + s['complete']
            ok = solver.has(node)
            gates = solver.gates(node) if ok else []
            w('  %s %-22s %s%s%s\n' % ('✓' if ok else '✗', s['entry'], s['title'],
                                         '（可选）' if s['optional'] else '',
                                         ('  深度 %d' % solver.depth(node)) + ('  门槛：' + '、'.join(gates) if gates else '') if ok else ''))
        w('\n')

    def why_text(node):
        roots = solver.root_set(node)
        if roots == [node]:
            return solver.root_reason.get(node, '')
        return '它本身也拿不到，再往上断在：' + '；'.join('%s（%s）' % (solver.label(k), solver.root_reason.get(k, '')) for k in roots[:3])

    if broken:
        w('--- 断链：做不到的手册步骤 ---\n' if not known else '--- 断链（已知）：只列断在哪 ---\n')
        summary = collections.OrderedDict()
        for s in broken:
            node = 'adv:' + s['complete']
            fx = solver.fixers(node)
            lines, roots = solver.explain(node)
            if not known:
                w('\n✗ %s/%s「%s」%s（进度 %s）\n' % (s['category'], s['entry'], s['title'], '（可选）' if s['optional'] else '', s['complete']))
                if fx:
                    w('    只要补上%s，这一步就通%s\n' % (solver.label(fx[0]), ('（' + '、'.join(solver.label(x) for x in fx[1:]) + ' 同效）') if fx[1:] else ''))
                for line in lines:
                    w('    ' + line + '\n')
            key = ('fix', fx[0]) if fx else ('roots',) + tuple(roots.keys())
            summary.setdefault(key, []).append(s)
        w('\n断在哪（按要补的地方归类）：\n')
        for key, steps_ in summary.items():
            names = '、'.join('%s/%s' % (x['category'], x['entry']) for x in steps_[:8]) + ('……' if len(steps_) > 8 else '')
            if key[0] == 'fix':
                w('  - 补上%s → 能通 %d 步（%s）。现状：%s\n' % (solver.label(key[1]), len(steps_), names, why_text(key[1])))
            else:
                w('  - 根源：%s → %d 步（%s）\n' % ('；'.join('%s（%s）' % (solver.label(k), solver.root_reason.get(k, '')) for k in key[1:]),
                                                len(steps_), names))
        w('\n')

    if unreach:
        w('--- 生存拿不到的物品（不算只放创造栏的），按要补的地方归类 ---\n')
        groups = collections.OrderedDict()
        for i in unreach:
            fx = solver.fixers('item:' + i, show=1)
            if fx:
                key = ('fix', fx[0])
            else:
                _, roots = solver.explain('item:' + i)
                key = ('roots',) + (tuple(roots.keys()) or ('item:' + i,))
            groups.setdefault(key, []).append(i)
        for key, members in sorted(groups.items(), key=lambda kv: -len(kv[1])):
            if key[0] == 'fix':
                w('  [%d 件] 补上%s 就都通。现状：%s\n' % (len(members), solver.label(key[1]), why_text(key[1])))
            else:
                w('  [%d 件] 根源：%s\n' % (len(members), '；'.join('%s（%s）' % (solver.label(k), solver.root_reason.get(k, '?')) for k in key[1:])))
            shown = members[:12]
            w('      ' + '、'.join(world.name(m) for m in shown) + ('……' if len(members) > len(shown) else '') + '\n')
        w('\n')
    if world_only_broken:
        w('--- 只在世界里出现的方块，世界里也出不来 ---\n')
        for i in world_only_broken:
            w('  - %s\n' % world.name(i))
        w('\n')
    if creative_reachable:
        w('--- 标成「只放创造栏」却拿得到的（名单该更新了）---\n')
        for i in creative_reachable:
            w('  - %s\n' % world.name(i))
        w('\n')

    # 手册里提到、生存拿不到的东西（只放创造栏那一章除外）
    home, mentions = load_guide_mentions(world, cfg_over)
    dead = collections.OrderedDict()
    for cat, entry, iid in mentions:
        if cat == 'creative' or iid in NOT_ITEMS or iid in WORLD_ONLY:
            continue
        if not solver.has('item:' + iid) and not solver.has('block:' + iid):
            dead.setdefault(iid, []).append('%s/%s' % (cat, entry))
    dead_real = collections.OrderedDict()
    dead_creative = collections.OrderedDict()
    for iid, where in dead.items():
        for wh in sorted(set(where)):
            (dead_creative if iid in CREATIVE_ONLY else dead_real).setdefault(wh, []).append(iid)
    if dead_real and not known:
        w('--- 手册里提到、生存拿不到的东西（不算只放创造栏的）---\n')
        for wh, iids in dead_real.items():
            w('  - %s：%d 件：%s\n' % (wh, len(iids), '、'.join(world.name(i) for i in iids[:6]) + ('……' if len(iids) > 6 else '')))
        w('\n')
    if dead_creative and detail:
        w('--- 提示：手册里提到的只放创造栏的东西（刷怪蛋写进条目物品表是约定，其余看正文有没有写明「只放创造栏」）---\n')
        for wh, iids in dead_creative.items():
            w('  - %s：%s\n' % (wh, '、'.join(world.name(i) for i in iids)))
        w('\n')

    # 按手册顺序走：必做主线步骤的获取链里有没有「后面的章 / 支线才讲」的东西
    zone_of = {}
    for path in glob.glob(os.path.join(GUIDE_DIR, '*.json')):
        zone_of[os.path.basename(path)[:-5]] = load_json(path).get('group', '')
    order_notes = []
    for s in visible:
        if s['zone'] != 'main' or s['optional'] or not step_ok(s):
            continue
        refs = forward_refs(solver, 'adv:' + s['complete'], home, s['order'], zone_of)
        if refs:
            order_notes.append((s, refs))
    if detail:
        w('--- 按手册顺序走：必做主线步骤要用到「后面的章或支线才讲」的东西 ---\n')
        if not order_notes:
            w('  没有：每一步要的东西都在这一章或前面的章讲过（或是原版物品）\n')
        for s, refs in order_notes:
            w('  - %s/%s「%s」：%s\n' % (s['category'], s['entry'], s['title'], '、'.join(
                '%s（%s %s/%s）' % (world.name(i), '支线' if zone_of.get(h[1]) not in ('main', 'start') else '后面的章', h[1], h[2])
                for i, h in refs)))
        w('\n')

    # 保底检查：去掉只靠运气的来源，必做主线步骤还做不做得到
    if (luck_only and not known) or detail:
        w('--- 保底检查：去掉只靠运气的来源（%s）再推一遍 ---\n' % ('、'.join('「%s」' % r.label for r in luck_rules) or '没有标 luck 的规则'))
        if not luck_only:
            w('  没有只靠运气的必做步骤：%d 个做得到的必做主线步骤去掉这些来源后都还做得到\n' % len(main_req_ok))
        # 按「没有运气来源时补上哪一样就通」归类：一步断在前面，后面整段都会跟着报
        groups = collections.OrderedDict()
        for s in luck_only:
            fx = luck_solver.fixers('adv:' + s['complete'])
            groups.setdefault(fx[0] if fx else None, []).append(s)
        for fix, steps_ in groups.items():
            w('  ✗ 只靠运气才做得到 %d 步：%s%s\n' % (len(steps_), '、'.join('%s/%s' % (x['category'], x['entry']) for x in steps_[:8]),
                                               '……' if len(steps_) > 8 else ''))
            if fix:
                w('    没有运气来源时，补上%s就通。第一步断在：\n' % luck_solver.label(fix))
            lines, _ = luck_solver.explain('adv:' + steps_[0]['complete'], max_depth=8)
            for line in lines[:10]:
                w('      ' + line + '\n')
        w('\n')

    # 老存档迁移表覆盖检查
    if migration['problems'] or detail:
        w('--- 老存档迁移表覆盖检查（基线 %d 个必做步骤；迁移表 %s）---\n' % (
            migration['baseline'], '、'.join('v%d %d 条' % (v, n) for v, n in sorted(migration['tables'].items())) or '没有'))
        w('  不在基线里的必做主线步骤 %d 个：有迁移项 %d、新内容不迁移 %d\n' % (
            migration['new_required'], migration['covered'], migration['new_content']))
        for text in migration['problems']:
            w('  ✗ %s\n' % text)
        for text in migration['notes']:
            w('  - %s\n' % text)
        w('\n')

    bad_trades = [(l, n) for l, n in g.trades if not solver.has(n)]
    if bad_trades:
        w('--- 交不出来的恶魔交易条目 ---\n')
        for l, n in bad_trades:
            w('  - %s\n' % l)
        w('\n')

    if g.problems:
        w('--- 数据问题 ---\n')
        for kind, text in sorted(set(g.problems)):
            w('  [%s] %s\n' % (kind, text))
        w('\n')
    if detail and g.notes:
        w('--- 提示（不算问题）---\n')
        for kind, text in sorted(set(g.notes)):
            w('  [%s] %s\n' % (kind, text))
        w('\n')

    # 查询
    for q in args.item or []:
        prefix = q.split(':', 1)[0]
        node = q if prefix in ('item', 'block', 'entity', 'dim', 'struct', 'adv', 'cap', 'summon', 'effect', 'loot',
                               'structure', 'soul') and q.count(':') >= 2 or prefix == 'cap' else 'item:' + rid(q, MODID)
        write_chain(w, solver, node)
    for q in args.step or []:
        hit = [s for s in steps if '%s/%s' % (s['category'], s['entry']) == q or s['complete'] == q or s['complete'] == ec(q)]
        if not hit:
            w('找不到手册步骤 %s（写成 分类/条目，例如 hologram/hologram）\n' % q)
        for s in hit:
            w('手册步骤 %s/%s「%s」：\n' % (s['category'], s['entry'], s['title']))
            write_chain(w, solver, 'adv:' + s['complete'])
    return {'broken': broken, 'problems': g.problems, 'visible': visible, 'unreach': unreach, 'items': items,
            'reach_items': reach_items, 'expected': expected, 'solver': solver, 'graph': g, 'luck_only': luck_only}


def luck_check(g, world, cfg_over, main_req_ok):
    """保底检查：去掉标了 luck 的代码规则再推一遍。返回 (运气规则, 只靠运气的必做步骤, 去掉以后的求解器)"""
    luck_rules = [r for r in g.rules if r.luck]
    if not luck_rules:
        return [], [], None
    g2 = copy.copy(g)
    g2.rules = [r for r in g.rules if not r.luck]
    solver2 = Solver(g2, world, cfg_over)
    solver2.run()
    return luck_rules, [s for s in main_req_ok if not solver2.has('adv:' + s['complete'])], solver2


def ascension_check(g, world):
    """ASCENSION_ONLY 里的物品只许从升格来：注册了的，要有产出它的升格配方，中心件的家族和阶对得上，别的规则（配方、战利品、
    代码规则）都不产出它。还没注册的（命名表里登记了、别的路正在做）只提示。返回（问题文字列表, 提示文字列表）"""
    problems, notes = [], []
    for item, (family, stage) in sorted(ASCENSION_ONLY.items()):
        name = world.name(item)
        if item not in world.ec_names:
            notes.append('%s 还没注册（%s），等它进来再查升格这条来源' % (
                name, '命名表里登记了' if item in world.planned else '命名表里也没有'))
            continue
        ups = [a for a in g.ascensions if item in a['results']]
        if not ups:
            problems.append('%s 只该从升格来，可没有一条升格配方产出它（要中心件是 %s 家族的 %d 阶）' % (name, family, stage))
            continue
        for a in ups:
            if a['family'] != family:
                problems.append('升格配方 %s 产出 %s，中心件的家族是 %s，应为 %s' % (a['recipe'], name, a['family'], family))
            if a['from_stage'] != stage:
                problems.append('升格配方 %s 产出 %s，中心件要 %d 阶，应为 %d 阶' % (a['recipe'], name, a['from_stage'], stage))
        mine = {'recipe:' + a['recipe'] for a in ups}
        others = [r for r in g.rules if 'item:' + item in r.gives and not mine.intersection(r.gives)]
        if others:
            problems.append('%s 除了升格还有别的来源：%s' % (name, '；'.join('%s（%s）' % (r.label, r.src) for r in others[:5])
                                                     + ('……' if len(others) > 5 else '')))
    return problems, notes


def migration_check(g, world, solver, visible, check_reach):
    """老存档迁移表覆盖检查（规则见 tools/guide/gen_migration.py 开头）：
    不在基线里的必做主线步骤要有迁移项（NEW_CONTENT 的章除外、EXPLICIT 照算）；每个证明进度要有进度文件、排在它后面；
    check_reach（默认配置那一遍）时还要做得到"""
    sys.path.insert(0, os.path.join(ROOT, 'tools', 'guide'))
    import gen_migration as GM
    res = {'problems': [], 'notes': [], 'baseline': 0, 'tables': {}, 'new_required': 0, 'covered': 0, 'new_content': 0}
    if not os.path.exists(GM.BASELINE):
        res['problems'].append('缺 %s（python tools/guide/gen_migration.py --make-baseline 4dacc01）' % os.path.relpath(GM.BASELINE, ROOT))
        return res
    baseline = set(GM.load_baseline())
    res['baseline'] = len(baseline)
    tables = GM.load_tables()
    res['tables'] = {v: len(items) for v, items in tables.items()}
    # 已经发出的版本不能再改：被改过的照报；当前版本已经发出时，漏项要进下一版，提示先加版本号
    for _, msg in GM.shipped_problems():
        res['problems'].append(msg)
    current = GM.current_version()
    rerun_hint = (GM.shipped_hint(current) if current in GM.SHIPPED
                  else '重跑 python tools/guide/gen_migration.py')
    items = {}
    for v in sorted(tables):
        for it in tables[v]:
            if isinstance(it, dict) and isinstance(it.get('grant'), str):
                items.setdefault(it['grant'], (v, it))
    # 先后照 gen_migration 算，而且总按默认配置排（先后是书的结构，不随情形变：门关着时后面几章藏掉，
    # main/* 会被排到很前面）；同一个进度被几篇用作 complete 时，按必做的那篇定位置
    book = GM.load_book(GUIDE_DIR)
    steps = GM.steps_of(book, lambda expr, over: world.cond(expr, over), lambda raw, over: step_optional(world, raw, over), {})
    pos = {}
    for s in steps:
        if s['required']:
            pos.setdefault(s['complete'], s['pos'])
    for s in steps:
        pos.setdefault(s['complete'], s['pos'])
    mains = GM.main_positions(steps)
    seen = set()
    for s in visible:
        if s['zone'] != 'main' or s['optional'] or s['complete'] in baseline or s['complete'] in seen:
            continue
        seen.add(s['complete'])
        res['new_required'] += 1
        where = '%s/%s（%s）' % (s['category'], s['entry'], s['complete'])
        if s['complete'] not in items:
            if s['category'] in GM.NEW_CONTENT and s['complete'] not in GM.EXPLICIT:
                res['new_content'] += 1
                continue
            res['problems'].append('%s 不在基线里、是必做主线步骤，却没有迁移项：%s' % (where, rerun_hint))
            continue
        res['covered'] += 1
        v, it = items[s['complete']]
        here = pos.get(s['complete'])
        proofs = it.get('if_any') or []
        if not proofs and not it.get('if_check'):
            res['problems'].append('%s 的迁移项（v%d）没有证明进度也没有检查' % (where, v))
        for proof in proofs:
            if not isinstance(proof, str) or proof.split(':', 1)[0] != MODID or not os.path.exists(
                    os.path.join(MAIN_RES, 'data', MODID, 'advancements', *proof.split(':', 1)[1].split('/')) + '.json'):
                res['problems'].append('%s 的证明进度 %s 没有进度文件（v%d）' % (where, proof, v))
                continue
            p = pos.get(proof, mains.get(proof))
            if p is not None and here is not None and p <= here:
                res['problems'].append('%s 的证明进度 %s 排在它前面（v%d）：老玩家没走到这一步也会被补勾' % (where, proof, v))
            if check_reach and not solver.has('adv:' + proof):
                res['problems'].append('%s 的证明进度 %s 做不到（v%d）' % (where, proof, v))
    # 非步骤补发（EXTRA_GRANTS）：要补的是隐藏进度，不在手册里；进度文件要在、不带 display，证明进度要在、做得到。
    # 版本号比 CURRENT_VERSION 大的表还没启用（表先生成、进度和动作后合进来），这时只记提示
    extra = GM.extra_grant_ids()
    for grant in sorted(extra):
        if grant not in items:
            continue
        v, it = items[grant]
        out = res['problems'] if v <= current else res['notes']
        tail = '' if v <= current else '（v%d 还没启用，启用前补上）' % v
        where = '非步骤补发 %s（v%d）' % (grant, v)
        path = (os.path.join(MAIN_RES, 'data', MODID, 'advancements', *grant.split(':', 1)[1].split('/')) + '.json'
                if grant.split(':', 1)[0] == MODID else None)
        if path is None or not os.path.exists(path):
            out.append('%s 没有进度文件%s' % (where, tail))
        elif 'display' in (load_json(path) or {}):
            out.append('%s 的进度带 display：补发用的是隐藏进度，不该在进度页出现%s' % (where, tail))
        for proof in it.get('if_any') or []:
            if not isinstance(proof, str) or proof.split(':', 1)[0] != MODID or not os.path.exists(
                    os.path.join(MAIN_RES, 'data', MODID, 'advancements', *proof.split(':', 1)[1].split('/')) + '.json'):
                out.append('%s 的证明进度 %s 没有进度文件%s' % (where, proof, tail))
            elif check_reach and not solver.has('adv:' + proof):
                out.append('%s 的证明进度 %s 做不到%s' % (where, proof, tail))
    if check_reach:
        pending = {}
        for grant, (v, it) in sorted(items.items()):
            if grant in seen or grant in baseline or grant in extra:
                continue
            if v > current:
                pending.setdefault(v, []).append(grant)
            else:
                res['notes'].append('v%d 里的 %s 现在不是必做主线步骤了（已经发出去的版本别改；没发出去就重新生成）' % (v, grant))
        for v, grants in sorted(pending.items()):
            res['notes'].append('v%d 还没启用（GuideMigration.CURRENT_VERSION = %d），其中 %d 条要补的进度现在不是手册步骤：'
                                '手册和进度合进来以后重跑 python tools/guide/gen_migration.py --version %d --check（%s）'
                                % (v, current, len(grants), v, '、'.join(g.split(':', 1)[1] for g in grants)))
    return res


def write_chain(w, solver, node):
    if not solver.has(node):
        w('%s 拿不到：\n' % solver.label(node))
        lines, _ = solver.explain(node)
        for line in lines:
            w('    ' + line + '\n')
        w('\n')
        return
    if solver.implicit(node):
        w('%s：开局就有\n\n' % solver.label(node))
        return
    chain = solver.chain(node)
    used = sorted(solver.vanilla_used(node))
    w('%s 的获取链（%d 步，深度 %d；用到原版物品 %d 种）：\n' % (solver.label(node), len(chain), solver.depth(node), len(used)))
    for i, (r, n) in enumerate(chain, 1):
        w('  %2d. %s → %s   [%s]\n' % (i, r.label, solver.label(n), r.src or '-'))
    gates = solver.gates(node)
    if gates:
        w('  门槛：%s\n' % '、'.join(gates))
    w('\n')


# 主线终点（batch13.md §2.2：第二十三章九阶）；报告的主线统计按手册顺序数到这里
MAIN_FINAL_STEP = MODID + ':guide/matrix/rank_9'


# 默认也跑、已知会断链的配置（服主自己关掉一段时的后果，配置注释里写着怎么配才不断；记在 progression.md「上一版记下的问题」）：
# 只打印断在哪，不算 --strict 的失败；哪天不断了会提示去核对记录
KNOWN_BREAKS = [
    ('凝结关闭（已知断链）', {'machines.enrichment_chamber.condense.enabled': False},
     '凝结矩阵只有富集仓凝结这一个来源，四阶起的祭品要它；配置注释要服主一起关矩阵阶或把门放到 3 阶以下'),
    ('海纳尔之门要 6 阶（已知断链）', {'general.matrix_rank.gates.hoanna_portal': 6},
     '六阶要在海纳尔里杀怪、要海纳尔里才有的东西，这道门最高只能设 5（全息投影台的门同理最高 6，七阶要数学核心）'),
    # 第十三批 13e
    ('暗影核心关闭（已知断链）', {'machines.enrichment_chamber.shade.enabled': False},
     '暗影核心只有富集仓暗影分支这一个来源，第二十二章起（暗影核心、暗渎水晶柱、4 档五芒星、八阶、天灾信标、九阶）都要它；'
     '配置注释给服主四种接法：一起关海纳尔之门（第二十二、二十三章跟着藏）、用数据包给暗影核心加配方、'
     '用数据包改那几条配方和 matrix_ranks 的祭品，或者接受主线停在第二十二章'),
    ('天灾信标要 9 阶（已知断链）', {'general.matrix_rank.gates.calamity_beacon': 9},
     '九阶要打倒天灾之心（图谱条件和天灾残核），这道门最高只能设 8'),
    ('契约之誉关闭（已知断链）', {'general.demon.standing.enabled': False},
     '恶魔之誓只有向恶魔立誓这一个来源，立誓要契约之誉；天灾信标配方要恶魔之誓，第二十二章立誓起、第二十三章都断；'
     '配置注释要服主一起关海纳尔之门，或用数据包改天灾信标配方'),
]


def main():
    parser = argparse.ArgumentParser(description='研究手册主线可达性检查')
    parser.add_argument('--strict', action='store_true', help='有断链或数据问题时退出码 1')
    parser.add_argument('--cfg', action='append', default=[], metavar='KEY=VALUE', help='改配置默认值（只跑这一种情形）')
    parser.add_argument('--item', action='append', help='打印这件东西的获取链（物品 id，或 block:/entity:/dim:/struct:/adv: 开头的节点）')
    parser.add_argument('--step', action='append', help='打印手册某一步的获取链（分类/条目）')
    parser.add_argument('--brief', action='store_true', help='不列每一步，只列总数和断链')
    parser.add_argument('--out', help='报告另存到这个文件')
    parser.add_argument('--mc-jar', help='原版 client-extra.jar（原版物品、标签、箱子表）')
    parser.add_argument('--forge-jar', help='Forge jar（forge 标签）')
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    args = parser.parse_args()

    for need in (LANG_FILE,):
        if not os.path.exists(need):
            print('找不到 %s：先跑 runData' % os.path.relpath(need, ROOT))
            return 2
    mc_jar = find_mc_jar(args.mc_jar)
    forge_jar = find_forge_jar(args.forge_jar)

    class Tee:
        def __init__(self):
            self.parts = []

        def write(self, s):
            self.parts.append(s)
            sys.stdout.write(s)

    out = Tee()
    out.write('源质魔法5 主线可达性检查\n')
    out.write('原版数据：%s\nForge 数据：%s\n\n' % (mc_jar or '（没找到，原版物品全部按拿得到算、原版标签按有东西算）',
                                             forge_jar or '（没找到，forge 标签按有东西算）'))
    if args.cfg:
        cfg_over = {}
        for kv in args.cfg:
            k, _, v = kv.partition('=')
            v = v.strip()
            cfg_over[k.strip()] = {'true': True, 'false': False}.get(v.lower(), v)
        scenarios = [('指定配置', cfg_over, None)]
    else:
        scenarios = [('默认配置', {}, None), ('海纳尔之门关闭', {'general.hoanna.portal_enabled': False}, None),
                     ('矩阵阶关闭', {'general.matrix_rank.enabled': False}, None),
                     ('天灾不会自己发生', {'general.hoanna_events.chance_multiplier': 0}, None)] + KNOWN_BREAKS
    failed = False
    for i, (name, cfg_over, known) in enumerate(scenarios):
        res = run_scenario(name, cfg_over, args if i == 0 else argparse.Namespace(item=None, step=None),
                           mc_jar, forge_jar, out, detail=(i == 0 and not args.brief), known=known)
        # 已知会断的情形只看数据问题；断链、只靠运气是预期结果
        if res['problems'] or (known is None and (res['broken'] or res['luck_only'])):
            failed = True
    if WARN.items:
        out.write('警告：\n')
        for kind, text in WARN.items:
            out.write('  [%s] %s\n' % (kind, text))
    if args.out:
        with open(args.out, 'w', encoding='utf-8') as f:
            f.write(''.join(out.parts))
    return 1 if (args.strict and failed) else 0


if __name__ == '__main__':
    sys.exit(main())
