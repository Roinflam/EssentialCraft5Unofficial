# -*- coding: utf-8 -*-
# 研究手册（冒险指南格式）内容校验。用法：
#   PYTHONIOENCODING=utf-8 python tools/guide/validate_guide.py            校验全部分类
#   PYTHONIOENCODING=utf-8 python tools/guide/validate_guide.py mru tesla  只报这几个分类的问题（跨分类链接仍按全部文件解析）
# 退出码：有 ERROR 为 1。规范见同目录 FORMAT.md。
# 从 KuvaLich 的 validate_guide.py 移植，并吸收了旧 check_guide.py 的检查（进度文件、结构 id、层尺寸、仪式字段、order 撞号、中文排版）。
# 配置键读 datagen 导出的 src/generated/resources/assets/essentialcraft5/guide/config_keys.json（改了配置项要重跑 runData）。
# 本文件刻意不写反斜杠（Write / Bash 工具会吃掉一层），需要时用 chr(92) / chr(10)。
import glob
import io
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import guide_lib as L  # noqa: E402

BS = L.BS
LANGS = ['zh_cn', 'en_us', 'zh_tw', 'zh_hk']
GENERATED_LANGS = ('zh_tw', 'zh_hk')   # make_traditional.py 从 zh_cn 生成；目录整个没有时只警告
GROUPS = ('start', 'main', 'side')
LONG_PARA = 150          # 一段显示出来超过这么多字给警告（物品名展开算）
SUBTITLE_MAX = 22
SUMMARY_MAX = 28

# 分类骨架（FORMAT.md §2）：条目 id 不许改名；删之前确认功能不存在
SKELETON = {
    'start': ['overview', 'mru', 'mrucu', 'glossary', 'materials', 'faq', 'numbers'],
    'matrix': ['overview', 'codex', 'crucible', 'ranks', 'alignment', 'reservoir', 'tolerance'],
    'elements': ['book', 'ores', 'rituals', 'elemental_gem', 'parts'],
    'mru': ['soul_stone', 'ubmru', 'matrix_absorber'],
    'crafting': ['bound_gem', 'radiating_chamber', 'corruption', 'fortified', 'magician_table', 'magician_crafts'],
    'generators': ['heat_generator', 'natural_furnace', 'projections', 'cold_distillator', 'ray_tower'],
    'storage': ['crystals', 'soul_storage', 'charging_chamber', 'upgrades'],
    'alloys': ['magical_essence', 'crystal_dust', 'purified_alloys', 'combined_magical_alloys', 'resonating_crystal', 'pale', 'plating', 'crystal_lamps'],
    'enrichment': ['controller', 'io', 'holding_chamber', 'monitoring', 'intersector'],
    'crystal': ['crystal_former', 'elemental_crystal', 'moon_well'],
    'essence': ['crystal_extractor', 'essences', 'fuel_and_focus', 'crystal_controller', 'elemental_decomposer', 'magical_lantern'],
    'solar': ['sun_imbued_glass', 'sun_ray_absorber', 'solar_prism', 'ender_generator'],
    'mithriline': ['mithriline_ingot', 'crystal_pillar', 'furnace', 'conversions'],
    'pentacle': ['pentacle', 'cure', 'pale_block', 'pale_crystal_pillar', 'tier_one', 'tier_two', 'tier_three', 'ec_balancer'],
    'wind': ['wind_rune', 'wind_gem', 'air_potion', 'windbound', 'wishes', 'wind_horn', 'archmage', 'wind_tablet'],
    'automation': ['rules', 'crafter', 'crafting_frame', 'assembly_line', 'mimic', 'redstone_transmitter', 'metadata_manager'],
    'logistics': ['magical_hopper', 'filters', 'advanced_filters', 'magical_chest', 'void_chest', 'magical_display', 'magical_mirror', 'after_main_line'],
    'hoanna': ['legend', 'wind_name', 'open_gate', 'crossing', 'way_home', 'sky', 'lands', 'flora', 'radiation', 'events', 'ruins', 'relics', 'void_stone', 'void_plating', 'collapse'],
    'ley': ['ley_projector', 'ley_lens', 'ley_condenser', 'condensing'],
    'farming': ['item_shuffler', 'breeder', 'shearing_station', 'animal_separator', 'magical_cultivator'],
    'gear': ['magic_monocle', 'mru_mover_t2', 'mrucu_container', 'mhs_armor', 'void_armor', 'elemental_tools', 'projection_use', 'magical_digger', 'paving_wand', 'essential_tools', 'pale_gem', 'ley_detector', 'homeward_compass'],
    'devices': ['magical_repairer', 'magical_enchanter', 'potion_spreader', 'magical_jukebox'],
    'magmatic': ['fortified_furnace', 'furnace_upgrades', 'smeltery'],
    'corruption': ['matrix_damage', 'body', 'mind', 'matrix', 'paradox', 'matrix_tuner'],
    'charms': ['charms', 'consumables', 'magical_wings'],
    'staffs': ['golden_orb', 'magmatic_staff', 'magnetizing_staff', 'staff_of_life', 'shields', 'matrix_weapons'],
    'wind_gear': ['wind_tools', 'wind_armor'],
    'wind_mage': ['wind_mages', 'bottled_winds', 'wind_mage_armor', 'steadfast', 'deflect'],
    'baubles': ['finding', 'kill_ubmru', 'ubmru_other', 'combat', 'harvest', 'wind_corruption', 'radiation', 'mystical_accessory'],
    'tesla': ['mru_magnet', 'mru_coil', 'player_list', 'reactor'],
    'guns': ['weapon_maker', 'parts', 'pistol', 'rifle', 'sniper', 'gatling', 'firing', 'lens_effects'],
    'decorations': ['magical_chisel', 'patterns', 'fancy_mimic'],
    'void_machines': ['ultra_heat_generator', 'ultra_natural_furnace', 'magical_furnace', 'magical_teleporter', 'magical_builder', 'ascended_builder', 'builder_module', 'ember_forge', 'ember_sword', 'ember_inlay'],
    'demons': ['void_crystal_pillar', 'demonic_pentacle', 'summoning', 'demon', 'trade', 'soul_scriber', 'ackronite', 'purge', 'demonic_plating'],
    'hologram': ['neuronic_plating', 'holopad', 'summoning', 'hologram', 'decoy', 'math_cores', 'orbital_remote', 'divider_gun'],
    'inquisitorium': ['legend', 'glitchy_core', 'decoding', 'journey', 'beacon', 'sentinel', 'purify', 'chip', 'armor'],
    'computer': ['force_parts', 'force_field', 'drives', 'senses', 'computer_engine', 'suit', 'antigravity_board', 'seal_field', 'force_dome', 'costs', 'epilogue'],
    'terminal': ['legend', 'blank_module', 'install', 'modules', 'rewind', 'matrix_filter', 'matrix_shunt', 'accumulator', 'shred', 'phase_anchor', 'attraction', 'stride', 'steady_hand', 'veil', 'epilogue'],
    'shade': ['intro', 'attune', 'umbral', 'shade_core', 'shade_pillar', 'tier4', 'oath', 'rank_8'],
    'apex': ['intro', 'beacon', 'summon', 'fight', 'rewards', 'rank_9', 'ascension', 'epilogue'],
    'creative': ['creative_items'],
    'converter': ['mru_converter', 'energy', 'mana', 'upgrades'],
    'scrolls': ['scrolls', 'preface', 'book_1', 'book_2', 'book_3', 'book_4', 'book_5', 'book_6', 'complete_set'],
    'attunement': ['overview', 'points', 'budget', 'stage_up', 'transfer', 'mhs_armor', 'wind_armor', 'void_armor',
                   'inquisitorium_armor', 'computer_armor', 'ember_sword', 'verdict_blade'],
    'hoanna_explore': ['overview', 'tide', 'specialties', 'ley_storm', 'calamity_windows', 'hotspots', 'survey',
                       'survey_trip', 'abyssal_salt', 'midscapes', 'nests', 'sealed_vault', 'sunken_district',
                       'inquisitorium_outpost'],
    'bestiary': ['overview', 'elemental_wisp', 'matrix_wraith', 'storm_sorcerer', 'nether_imp', 'void_stalker',
                 'frostbitten', 'mana_prism', 'chaos_aberrant', 'ember_spirit', 'hoanna_scavenger', 'abyssal_eel',
                 'inquisitorium_patroller', 'catacomb_revenant',
                 'mru_golem', 'matrix_shade', 'town_sentry', 'abyss_tyrant', 'calamity_heart'],
    'from_ec4': ['overview', 'where', 'new', 'changed', 'removed', 'fixed', 'how'],
}
# 骨架里先留好位置、内容还没写的条目：缺了不报（13d 占的宝库、沉城、前哨三篇 13e 已写，眼下没有）
RESERVED = {}

TEXT_BLOCKS = {'h', 'p', 'formula', 'quote'}
BLOCK_FIELDS = {
    'h': {'text'}, 'p': {'text'}, 'list': {'items'}, 'steps': {'items'}, 'kv': {'rows'},
    'table': {'header', 'rows'}, 'tip': {'style', 'text'}, 'formula': {'text'},
    'items': {'items'}, 'recipe': set(), 'entity': {'id'}, 'divider': set(),
    'structure': set(), 'ritual': {'pattern', 'key', 'throw'}, 'image': {'texture'}, 'quote': {'text'},
}
BLOCK_OPTIONAL = {
    'list': {'ordered'}, 'table': {'widths'}, 'tip': {'title'}, 'items': {'caption', 'per_row'},
    'recipe': {'item', 'items', 'recipe', 'recipe_type', 'compact', 'optional', 'caption'},
    'entity': {'height', 'nbt', 'scale', 'name'},
    'structure': {'structure', 'pattern', 'layers', 'key', 'anchor', 'caption'},
    'ritual': {'result', 'chance', 'caption'},
    'image': {'width', 'height', 'caption'},
    'quote': {'by'},
}
# 结构对照时比较的非文字字段（label / caption 等文字不比）
FIGURE_FIELDS = ('items', 'per_row', 'item', 'recipe', 'recipe_type', 'compact', 'optional', 'structure', 'pattern',
                 'anchor', 'throw', 'result', 'chance', 'texture', 'width', 'height', 'id', 'ordered', 'widths', 'style',
                 'nbt', 'scale', 'name')
TIP_STYLES = {'info', 'warn', 'good', 'bad', 'lore'}
TAGS = {'hl', 't', 'w', 'g', 'b', 'dim', 'em', 'c'}
OPS = {'mul', 'div', 'add', 'min', 'max', 'round'}
FMTS = {'raw', 'int', 'pct', 'pctv', 'x', 'sec', 'bool', 'yesno', 'list', 'count'}
NUMERIC_FMTS = {'int', 'pct', 'pctv', 'x', 'sec'}
NUMERIC_TYPES = {'int', 'long', 'double'}
ID_RE = re.compile('^[a-z0-9_]+$')
RL_RE = re.compile('^[a-z0-9_.-]+:[a-z0-9_/.-]+$')
NUM_RE = re.compile('^-?[0-9]+([.][0-9]+)?$')
COND_RE = re.compile('^(?P<neg>!?)(cfg:(?P<key>[A-Za-z0-9_.]+)(?:[|](?P<sel>at|stage):(?P<idx>[0-9]+))?'
                     '(?P<cmp>(>=|<=|==|!=|>|<)(-?[0-9]+([.][0-9]+)?))?'
                     '|mod:(?P<mod>[a-z0-9_]+)'
                     '|bauble:(?P<bauble>[a-z0-9_.-]+:[a-z0-9_/.-]+)'
                     '|gamemode:(creative)'
                     '|module:(?P<module>[a-z0-9_.-]+:[a-z0-9_/.-]+))$')
VANILLA_RECIPE_TYPES = {'minecraft:' + n for n in (
    'crafting', 'smelting', 'blasting', 'smoking', 'campfire_cooking', 'stonecutting', 'smithing')}
LANG_KEY_RE = re.compile('^[a-z0-9_]+([.][a-z0-9_]+)+$')

CFG_KEYS = L.load_config_keys()
MOD_IDS = L.load_mod_ids()
VANILLA_ITEMS, VANILLA_BLOCKS, VANILLA_ENTITIES = L.load_vanilla()
LANG_EN = L.load_lang('en_us')
LANG_ZH = L.load_lang('zh_cn')
ENTITIES = L.load_entities(LANG_EN)
BAUBLES = L.load_baubles()


def load_modules():
    """登记过的宅男套装模块：src/main/java 下 registry/EC*Modules.java 里 ArmorModules.register(ModuleType.builder(EssentialCraft5.id("…")) 的注册名"""
    ids = set()
    pat = re.compile('ArmorModules[.]register[(]ModuleType[.]builder[(]EssentialCraft5[.]id[(]"([a-z0-9_/.-]+)"[)]')
    for f in glob.glob(os.path.join(L.ROOT, 'src', 'main', 'java', '**', 'registry', 'EC*Modules.java'), recursive=True):
        with io.open(f, encoding='utf-8') as fh:
            for m in pat.finditer(fh.read()):
                ids.add(L.MODID + ':' + m.group(1))
    return ids


MODULES = load_modules()


class Report:
    def __init__(self):
        self.errors = []
        self.warns = []

    def e(self, where, msg):
        self.errors.append(where + ': ' + msg)

    def w(self, where, msg):
        self.warns.append(where + ': ' + msg)


REP = Report()


# ---------------------------------------------------------------- id

def check_id(where, rid, kind='item'):
    if not isinstance(rid, str) or not RL_RE.match(rid):
        REP.e(where, 'id 格式不对（要写 命名空间:路径）: %r' % (rid,))
        return
    ns = rid.split(':', 1)[0]
    if ns == L.MODID:
        if rid not in MOD_IDS:
            REP.e(where, '%s 不在命名表 tools/id_map_*.json 里（或已标 removed）' % rid)
    elif ns == 'minecraft':
        pool = VANILLA_BLOCKS if kind == 'block' else VANILLA_ITEMS
        if pool is not None and rid not in pool:
            REP.e(where, '原版%s %s 不存在' % ('方块' if kind == 'block' else '物品', rid))
    else:
        REP.w(where, '引用了别的模组的 %s（整合包里没有这个模组时会被跳过）' % rid)


def check_entity(where, eid):
    if not isinstance(eid, str) or not RL_RE.match(eid):
        REP.e(where, '实体 id 格式不对: %r' % (eid,))
        return
    ns = eid.split(':', 1)[0]
    if ns == L.MODID and eid not in ENTITIES:
        REP.e(where, '实体类型不存在（语言文件里没有 entity.%s）: %s' % (eid.replace(':', '.'), eid))
    elif ns == 'minecraft' and VANILLA_ENTITIES is not None and eid not in VANILLA_ENTITIES:
        REP.e(where, '原版实体 %s 不存在' % eid)


def check_entity_name(where, key):
    """entity.name：框下名字的语言键（nbt 摆的变体换了名字时写），中英语言文件里都要有"""
    if not isinstance(key, str) or not LANG_KEY_RE.match(key):
        REP.e(where, 'entity.name 要写语言键（如 entity.%s.wind_mage.storm）: %r' % (L.MODID, key))
    elif LANG_EN and (key not in LANG_EN or key not in LANG_ZH):
        REP.e(where, 'entity.name 的语言键中英没有都写: %s' % key)


def check_entity_nbt(where, nbt):
    """entity.nbt：SNBT 字符串，最外层是 {…}，括号、引号配得上（细的语法游戏里 TagParser 再查，写错只是不合并）"""
    if not isinstance(nbt, str) or not nbt.strip():
        REP.e(where, 'entity.nbt 要是非空的 SNBT 字符串')
        return
    t = nbt.strip()
    if not (t.startswith('{') and t.endswith('}')):
        REP.e(where, 'entity.nbt 最外层要是 {…}: %s' % nbt)
        return
    depth = []
    quote = None
    esc = False
    pairs = {'}': '{', ']': '['}
    for ch in t:
        if quote:
            if esc:
                esc = False
            elif ch == '\\':
                esc = True
            elif ch == quote:
                quote = None
            continue
        if ch in ('"', "'"):
            quote = ch
        elif ch in '{[':
            depth.append(ch)
        elif ch in '}]':
            if not depth or depth.pop() != pairs[ch]:
                REP.e(where, 'entity.nbt 括号配不上: %s' % nbt)
                return
    if quote or depth:
        REP.e(where, 'entity.nbt 括号或引号没有收尾: %s' % nbt)


def check_advancement(where, rid):
    if not isinstance(rid, str) or not RL_RE.match(rid):
        REP.e(where, '进度 id 格式不对: %r' % (rid,))
        return
    if rid.split(':', 1)[0] != L.MODID:
        REP.w(where, '用了别的命名空间的进度 %s（离线检查不了是否存在）' % rid)
    elif not L.advancement_exists(rid):
        REP.e(where, '进度 %s 没有对应的文件（data/%s/advancements/%s.json）' % (rid, L.MODID, rid.split(':', 1)[1]))


# ---------------------------------------------------------------- 条件

def flatten_cond(cond):
    """if 字段 → 条件字符串列表（数组 = 全部成立；a||b 当成一个整体）"""
    if cond is None:
        return []
    return [c for c in (cond if isinstance(cond, list) else [cond])]


def check_cond(where, cond, what='if'):
    """条件的语法与配置键。what：报错时的字段名（if，或按条件可选的 optional，写法相同）"""
    items = cond if isinstance(cond, list) else [cond]
    for c in items:
        if not isinstance(c, str) or not c.strip():
            REP.e(where, '%s 里必须是非空字符串: %r' % (what, c))
            continue
        for part in (p.strip() for p in c.split('||')):
            m = COND_RE.match(part)
            if not m:
                REP.e(where, '%s 语法不对: %s' % (what, part))
                continue
            key = m.group('key')
            if key:
                spec = cfg_spec(where, key, what)
                t = spec.get('type') if spec else None
                if spec and m.group('sel'):
                    # cfg:键|at:N / cfg:键|stage:N：先取列表的一项再判（取法同格式链），比较的是那一项
                    t = list_item_type(where, key, spec, m.group('sel'), int(m.group('idx')), part)
                if spec and m.group('cmp') and t not in NUMERIC_TYPES:
                    REP.e(where, '%s 里的比较只能用在数字配置上: %s（%s）' % (what, part, t))
            if m.group('bauble') and m.group('bauble') not in BAUBLES:
                REP.e(where, 'bauble: 的 id 不是本模组的饰品（Curios 标签里没有）: ' + m.group('bauble'))
            # module:（宅男套装模块，general.computer_modules.disabled）：id 要是登记过的模块（registry/EC*Modules.java 里 ArmorModules.register 的注册名）
            if m.group('module') and m.group('module') not in MODULES:
                REP.e(where, 'module: 的 id 不是登记过的宅男套装模块: ' + m.group('module'))


def check_entry_items(where, items):
    """条目 items：物品 id 和条件组 {"if": …, "items": [id…]} 混排（FORMAT.md §1.2）；组不能嵌套，同一个 id 只写一次"""
    if not isinstance(items, list):
        REP.e(where, 'items 必须是数组')
        return
    seen = set()

    def one(x):
        if not isinstance(x, str):
            REP.e(where, 'items 的条件组里只能写物品 id（组不能嵌套）: %r' % (x,))
            return
        check_id(where + ' 的 items', x)
        if x in seen:
            REP.w(where, 'items 里 %s 写了不止一次' % x)
        seen.add(x)

    for i in items:
        if not isinstance(i, dict):
            if isinstance(i, str):
                one(i)
            else:
                REP.e(where, 'items 里的项要么是物品 id，要么是 {"if": …, "items": […]} 条件组: %r' % (i,))
            continue
        for k in i:
            if k not in ('if', 'items') and not k.startswith('_'):
                REP.e(where, 'items 的条件组不认识字段 ' + k)
        if 'if' not in i:
            REP.e(where, 'items 的条件组缺少 if（不带条件的物品直接写在外层）')
        else:
            check_cond(where + ' 的 items 条件组', i['if'])
        sub = i.get('items')
        if not isinstance(sub, list) or not sub:
            REP.e(where, 'items 的条件组里 items 必须是非空数组')
            continue
        for x in sub:
            one(x)


def list_item_type(where, key, spec, sel, index, shown):
    """at:N / stage:N 取出来的那一项的类型（boolean / double / string）；键不是列表、默认值取不到时报错并返回 None。
    at:N 越界显示「?」，按默认值查下标够不够；stage:N 越界沿用最后一项，只要默认值不是空列表"""
    t = spec.get('type')
    default = spec.get('default')
    if t != 'list':
        REP.e(where, '%s:N 只能用在列表键上: %s（%s）' % (sel, shown, t))
        return None
    if not isinstance(default, list) or not default or (sel == 'at' and index >= len(default)):
        REP.e(where, '列表 %s 的默认值没有第 %d 项（从 0 数），默认配置下会显示「?」: %s' % (key, index, shown))
        return None
    item = default[min(index, len(default) - 1)]
    return 'boolean' if isinstance(item, bool) else 'double' if isinstance(item, (int, float)) else 'string'


def cfg_spec(where, key, what):
    if CFG_KEYS is None:
        return None
    spec = CFG_KEYS.get(key)
    if spec is None:
        REP.e(where, '%s 里的配置键不存在（config_keys.json 里没有；刚加的配置项要先跑 runData）: %s' % (what, key))
    return spec


# ---------------------------------------------------------------- 行内标记

class Scan:
    """扫一个块时收集的东西：占位符 / 链接 / 条件（给中英对照）、链接及其所在处的条件"""

    def __init__(self, conds):
        self.sink = []
        self.links = []     # (目标, 所在处的条件列表)
        self.conds = conds


def scan_text(where, text, sc, conds=None):
    if not isinstance(text, str):
        REP.e(where, '文本字段必须是字符串: %r' % (text,))
        return
    if chr(167) in text:
        REP.e(where, '不许用 § 颜色码')
    if '$(' in text:
        REP.e(where, '残留帕秋莉宏 $(')
    if '**' in text:
        REP.e(where, '旧格式的 **强调** 要写成 <em>…</em>')
    conds = sc.conds + (conds or [])
    i = 0
    stack = []
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == BS:
            i += 2
            continue
        if ch == '<':
            m = re.match('<(/?)([a-z]+)(:([0-9A-Fa-f]{6}))?>', text[i:])
            if not m or m.group(2) not in TAGS:
                REP.e(where, '未知或残缺的标签（要写尖括号本身请用 ' + BS + '<）: ' + text[i:i + 16])
                i += 1
                continue
            closing, tag, col = m.group(1) == '/', m.group(2), m.group(4)
            if tag == 'c' and not closing and not col:
                REP.e(where, '<c:RRGGBB> 缺颜色')
            if tag != 'c' and col:
                REP.e(where, '只有 <c:> 能带颜色: ' + m.group(0))
            if closing:
                if not stack or stack[-1] != tag:
                    REP.e(where, '标签没有正确闭合: ' + m.group(0) + ' 栈=' + str(stack))
                else:
                    stack.pop()
            else:
                stack.append(tag)
            i += len(m.group(0))
            continue
        if ch == '{':
            j = text.find('}', i)
            if j < 0:
                REP.e(where, '占位符缺右花括号: ' + text[i:i + 30])
                break
            check_token(where, text[i + 1:j], sc)
            i = j + 1
            continue
        if ch == '[' and text.startswith('[[', i):
            j = text.find(']]', i)
            if j < 0:
                REP.e(where, '链接缺 ]]: ' + text[i:i + 30])
                break
            body = text[i + 2:j]
            target = body.split('|', 1)[0]
            parts = target.split('/')
            if len(parts) > 2 or not all(ID_RE.match(p) for p in parts):
                REP.e(where, '链接目标格式不对: ' + body)
            else:
                sc.sink.append(('link', target))
                sc.links.append((target, conds))
            i = j + 2
            continue
        if ch == '}':
            REP.e(where, '多余的右花括号')
        i += 1
    if stack:
        REP.e(where, '标签未闭合: ' + str(stack))


def check_token(where, tok, sc):
    if tok.startswith('cfg:'):
        parts = tok[4:].split('|')
        key = parts[0]
        spec = cfg_spec(where, key, '占位符')
        chain = [p for p in parts[1:] if p != 'sign']
        numeric = ('sign' in parts[1:])
        fmt = 'raw'
        at = None
        sel = None
        for k, p in enumerate(chain):
            if ':' in p:
                op, arg = p.split(':', 1)
                if op in ('at', 'stage'):
                    # 列表取一项（0 起）：at:N 取第 N 项，stage:N 按阶取、列表短了沿用最后一项；只能写在格式链最前面，后面的运算和格式都作用在那一项上
                    if k != 0 or not arg.isdigit():
                        REP.e(where, '%s:N 要写在格式链最前面，N 是从 0 起的整数: %s' % (op, tok))
                    else:
                        at = int(arg)
                        sel = op
                    continue
                if op == 'addcfg':
                    # 加上另一项配置的值：那一项也要是数字配置
                    other = cfg_spec(where, arg, '占位符的 addcfg')
                    if other and other.get('type') not in NUMERIC_TYPES:
                        REP.e(where, 'addcfg 只能加数字配置: %s（%s）' % (arg, other.get('type')))
                    numeric = True
                    continue
                if op not in OPS or not NUM_RE.match(arg):
                    REP.e(where, '格式链里的运算不对: ' + p)
                numeric = True
            elif p not in FMTS:
                REP.e(where, '未知显示格式: ' + p)
            elif k != len(chain) - 1:
                REP.e(where, '显示格式必须放在格式链最后: ' + tok)
            else:
                fmt = p
        if spec:
            t = spec.get('type')
            if at is not None:
                t = list_item_type(where, key, spec, sel, at, '{' + tok + '}')
                if fmt in ('list', 'count'):
                    REP.e(where, '%s:N 取出的是单个值，不能再用 %s 格式: {%s}' % (sel, fmt, tok))
            if (numeric or fmt in NUMERIC_FMTS) and t not in NUMERIC_TYPES:
                REP.e(where, '数字格式 / 运算用在了 %s 类型的键上: {%s}' % (t, tok))
            if fmt in ('bool', 'yesno') and t != 'boolean':
                REP.e(where, '%s 格式只能用在布尔键上: {%s}（%s）' % (fmt, tok, t))
            if fmt in ('list', 'count') and t != 'list':
                REP.e(where, '%s 格式只能用在列表键上: {%s}（%s）' % (fmt, tok, t))
        sc.sink.append(('cfg', tok))
    elif tok.startswith('item:'):
        check_id(where, tok[5:], 'item')
        sc.sink.append(('item', tok[5:]))
    elif tok.startswith('block:'):
        check_id(where, tok[6:], 'block')
        sc.sink.append(('block', tok[6:]))
    elif tok.startswith('balance:'):
        if tok[8:] not in ('frozen', 'pure', 'chaos'):
            REP.e(where, '{balance:…} 只能是 frozen / pure / chaos: ' + tok)
        sc.sink.append(('balance', tok[8:]))
    elif tok.startswith('keybind:'):
        k = tok[8:]
        if k == 'key.essentialcraft5.guide_lookup':
            REP.w(where, '查阅键默认不绑定，别在正文里用 {keybind:key.essentialcraft5.guide_lookup}')
        elif k.startswith('key.' + L.MODID + '.') and k not in LANG_EN:
            REP.e(where, '按键不存在（语言文件里没有 %s）' % k)
        sc.sink.append(('keybind', k))
    elif tok.startswith('lang:'):
        k = tok[5:]
        if k.startswith('guide.' + L.MODID + '.'):
            REP.e(where, '{lang:} 不许引用手册自己的 guide.%s.* 键（文字直接写进内容文件）: %s' % (L.MODID, k))
        elif LANG_EN and (k not in LANG_EN or k not in LANG_ZH) and k.split('.', 2)[1:2] == [L.MODID]:
            REP.e(where, '语言键不存在: ' + k)
        sc.sink.append(('lang', k))
    else:
        REP.e(where, '未知占位符（要写花括号本身请用 ' + BS + '{）: {' + tok + '}')


# ---------------------------------------------------------------- 中文排版（tools/lang/glossary.md 第一节）

HAN = L.HAN
LATIN = '[A-Za-z0-9]'
FULL_PUNCT = '[' + chr(0x3000) + '-' + chr(0x303f) + chr(0xff00) + '-' + chr(0xffef) + ']'


def check_typography(where, text):
    """占位符和链接的边界由引擎补空格，不查（换成中性字符）；其余照 glossary.md"""
    t = L.plain(text)
    for m in re.finditer(HAN + LATIN + '|' + LATIN + HAN, t):
        REP.e(where, '汉字与字母 / 数字之间缺空格：「%s」' % t[max(0, m.start() - 4):m.end() + 4])
    for m in re.finditer(HAN + '[,;:!?()]|[,;:!?()]' + HAN, t):
        REP.e(where, '中文里用了半角标点：「%s」' % t[max(0, m.start() - 4):m.end() + 4])
    if '...' in t or re.search('(?<!' + chr(0x2026) + ')' + chr(0x2026) + '(?!' + chr(0x2026) + ')', t):
        REP.e(where, '省略号要写成「……」')
    if re.search(FULL_PUNCT + ' | ' + FULL_PUNCT, t):
        REP.w(where, '全角标点旁边多了空格：「%s」' % t[:40])


# ---------------------------------------------------------------- 块

def unwrap(where, x, sc, text_key):
    """行级条件：{"if": …, "cells"/"text": …}"""
    if isinstance(x, dict):
        for k in x:
            if k not in ('if', text_key):
                REP.e(where, '带条件的项只认 if / %s: %s' % (text_key, k))
        if 'if' not in x or text_key not in x:
            REP.e(where, '带条件的项要有 if 和 %s' % text_key)
        check_cond(where, x.get('if', ''))
        sc.sink.append(('if', json.dumps(x.get('if'), ensure_ascii=False)))
        return x.get(text_key), flatten_cond(x.get('if'))
    return x, []


def check_key_map(where, key, lang):
    """结构 / 仪式的 key：{字符: 方块 id | "#标签" | "air" | {block, tag, air, label}}"""
    if not isinstance(key, dict):
        REP.e(where, 'key 必须是对象')
        return {}
    for ch, value in key.items():
        if len(ch) != 1 or ch == ' ':
            REP.e(where, 'key 的「%s」必须是一个非空格字符' % ch)
        if isinstance(value, str):
            if value == 'air':
                pass
            elif value.startswith('#'):
                if not RL_RE.match(value[1:]):
                    REP.e(where, '标签 id 格式不对: ' + value)
            else:
                check_id(where, value, 'block')
        elif isinstance(value, dict):
            for k in value:
                if k not in ('block', 'tag', 'air', 'label'):
                    REP.e(where, 'key「%s」的对象不认识字段 %s' % (ch, k))
            if not (value.get('block') or value.get('tag') or value.get('air')):
                REP.e(where, 'key「%s」的对象里至少要有 block、tag、air 之一' % ch)
            blocks = value.get('block')
            for b in (blocks if isinstance(blocks, list) else ([blocks] if blocks else [])):
                check_id(where, b, 'block')
            if value.get('tag') and not RL_RE.match(value['tag']):
                REP.e(where, '标签 id 格式不对: ' + str(value['tag']))
            check_label(where, value.get('label'), lang)
        else:
            REP.e(where, 'key「%s」的值格式不对' % ch)
    return key


def check_label(where, label, lang):
    if label is None:
        return
    if not isinstance(label, str) or not label:
        REP.e(where, 'label 必须是非空字符串')
        return
    if label.startswith('guide.' + L.MODID + '.'):
        REP.e(where, '结构标签不许是 guide.%s.* 键（这些键要删），直接写文字: %s' % (L.MODID, label))
    elif LANG_KEY_RE.match(label):
        if LANG_EN and label not in LANG_EN and label.split('.', 2)[1:2] == [L.MODID]:
            REP.e(where, '结构标签像语言键，但语言文件里没有: ' + label)
    elif any(c in label for c in '{<') or '[[' in label:
        REP.e(where, '结构标签是纯文字，不支持行内标记: ' + label)
    elif lang == 'zh_cn':
        check_typography(where, label)


def check_pattern(where, pattern, key):
    if not isinstance(pattern, list) or not pattern or not all(isinstance(r, str) for r in pattern):
        REP.e(where, 'pattern 必须是非空的字符串数组')
        return None
    for ch in ''.join(pattern):
        if ch != ' ' and ch not in key:
            REP.e(where, 'pattern 里的「%s」在 key 里没有定义' % ch)
    return len(pattern), max(len(r) for r in pattern)


def check_structure(where, b, lang):
    t = b['type']
    if 'structure' in b:
        sid = b['structure']
        if 'pattern' in b or 'layers' in b or 'key' in b:
            REP.e(where, '写了 structure 就不要再写 pattern / layers / key')
        if not isinstance(sid, str) or not RL_RE.match(sid):
            REP.e(where, 'structure「%s」要写成 命名空间:路径' % (sid,))
        elif not L.registered_in_java('StructurePatterns.register', sid):
            REP.w(where, 'Java 源码里没找到结构 %s 的登记（StructurePatterns.register）；离线查不准，进游戏看「[指南]」日志' % sid)
        if t == 'ritual':
            REP.e(where, 'ritual 块不能按 structure id 引用，要写 pattern + key')
        return
    shared = check_key_map(where, b.get('key') or {}, lang)
    patterns = []
    if 'layers' in b:
        if t == 'ritual':
            REP.e(where, 'ritual 块只画单层 pattern')
        if 'pattern' in b:
            REP.e(where, 'pattern 和 layers 只能写一种')
        layers = b['layers']
        if not isinstance(layers, list) or not layers:
            REP.e(where, 'layers 必须是非空数组')
            return
        sizes = []
        for n, layer in enumerate(layers, start=1):
            at = '%s 第 %d 层' % (where, n)
            if not isinstance(layer, dict):
                REP.e(at, '必须是对象 {pattern, key?, label?}')
                continue
            for k in layer:
                if k not in ('pattern', 'key', 'label'):
                    REP.e(at, '层不认识字段 ' + k)
            key = dict(shared)
            key.update(check_key_map(at, layer.get('key') or {}, lang))
            check_label(at, layer.get('label'), lang)
            size = check_pattern(at, layer.get('pattern'), key)
            if size:
                sizes.append(size)
                patterns.append(layer.get('pattern'))
        if len(set(sizes)) > 1:
            REP.e(where, '各层大小不一样（行数 × 最长一行：%s），上下对不齐；空位用空格补齐' % sizes)
    else:
        if not b.get('pattern'):
            REP.e(where, '结构块没有 pattern（或 layers / structure）')
        elif check_pattern(where, b.get('pattern'), shared):
            patterns.append(b.get('pattern'))
    anchor = b.get('anchor')
    if anchor is not None:
        if not isinstance(anchor, str) or len(anchor) != 1 or anchor == ' ':
            REP.e(where, 'anchor 必须是一个非空格字符')
        elif not any(anchor in ''.join(p) for p in patterns):
            REP.e(where, 'anchor「%s」在 pattern 里没有出现' % anchor)
    if t == 'ritual':
        check_id(where + ' 的 throw', b.get('throw'), 'item')
        if b.get('result') is not None:
            check_id(where + ' 的 result', b['result'], 'item')
        chance = b.get('chance')
        if chance is not None and (not isinstance(chance, (int, float)) or isinstance(chance, bool) or not (0 <= chance <= 1)):
            REP.e(where, 'chance 要在 0~1 之间')
    elif any(k in b for k in ('throw', 'result', 'chance')):
        REP.e(where, 'throw / result / chance 只有 ritual 块能写')


def check_recipe(where, b):
    items = []
    if 'item' in b:
        if not isinstance(b['item'], str):
            REP.e(where, 'recipe.item 写一个物品 id；多个请用 items')
        else:
            items.append(b['item'])
    if 'items' in b:
        if not isinstance(b['items'], list) or not b['items']:
            REP.e(where, 'recipe.items 必须是非空数组')
        else:
            items.extend(b['items'])
    for i in items:
        check_id(where, i, 'item')
    if not items and not b.get('recipe') and not b.get('recipe_type'):
        REP.e(where, 'recipe 块要有 item / items / recipe / recipe_type 之一')
    if b.get('recipe'):
        rid = b['recipe']
        if not isinstance(rid, str) or not RL_RE.match(rid):
            REP.e(where, 'recipe「%r」要写成 命名空间:路径' % (rid,))
        elif rid.startswith(L.MODID + ':') and not L.recipe_exists(rid):
            REP.e(where, '配方 %s 没有文件（data/%s/recipes/…）' % (rid, L.MODID))
    rtype = b.get('recipe_type')
    if rtype is not None:
        if not isinstance(rtype, str) or not RL_RE.match(rtype):
            REP.e(where, 'recipe_type「%r」要写成 命名空间:路径' % (rtype,))
        elif rtype.startswith('minecraft:'):
            if rtype not in VANILLA_RECIPE_TYPES:
                REP.e(where, '原版没有配方类型 ' + rtype)
        elif rtype.startswith(L.MODID + ':') and not L.registered_in_java('type', rtype):
            REP.w(where, 'Java 源码里没找到配方类型 %s 的登记；还要在客户端用 GuideRecipeViews.register 登记画法，离线查不到，看游戏日志' % rtype)
    for flag in ('compact', 'optional'):
        if flag in b and not isinstance(b[flag], bool):
            REP.e(where, '%s 要写 true / false' % flag)


def check_block(where, b, sc, lang, stats):
    if not isinstance(b, dict):
        REP.e(where, '块必须是对象')
        return
    t = b.get('type')
    if t not in BLOCK_FIELDS:
        REP.e(where, '未知块类型: %r' % (t,))
        return
    allowed = BLOCK_FIELDS[t] | BLOCK_OPTIONAL.get(t, set()) | {'type', 'if'}
    for k in b:
        if k not in allowed and not k.startswith('_'):
            REP.e(where, '块 %s 不认识字段 %s' % (t, k))
    for k in BLOCK_FIELDS[t]:
        if k not in b:
            REP.e(where, '块 %s 缺字段 %s' % (t, k))
    if 'if' in b:
        check_cond(where, b['if'])
        sc.sink.append(('if', json.dumps(b['if'], ensure_ascii=False)))
    zh = lang == 'zh_cn'

    def text(w, s, conds=None):
        scan_text(w, s, sc, conds)
        if zh and isinstance(s, str):
            check_typography(w, s)

    if t in TEXT_BLOCKS:
        text(where, b.get('text', ''))
        if zh and t == 'p' and L.display_len(b.get('text', ''), LANG_ZH) > LONG_PARA:
            stats['long'].append('%s（%d 字）' % (where, L.display_len(b.get('text', ''), LANG_ZH)))
    elif t == 'tip':
        if b.get('style') not in TIP_STYLES:
            REP.e(where, 'tip.style 只能是 info / warn / good / bad / lore')
        if b.get('style') == 'lore':
            stats['lore'] += 1
        if 'title' in b:
            text(where, b['title'])
        text(where, b.get('text', ''))
    elif t in ('list', 'steps'):
        its = b.get('items')
        if not isinstance(its, list) or not its:
            REP.e(where, t + '.items 必须是非空数组')
        else:
            for x in its:
                s, c = unwrap(where, x, sc, 'text')
                text(where, s, c)
    elif t == 'kv':
        rows = b.get('rows')
        if not isinstance(rows, list) or not rows:
            REP.e(where, 'kv.rows 必须是非空数组')
        else:
            for r in rows:
                r, c = unwrap(where, r, sc, 'cells')
                if not isinstance(r, list) or len(r) != 2:
                    REP.e(where, 'kv 每行必须是 [标签, 值] 或 {"if": …, "cells": [标签, 值]}')
                else:
                    for x in r:
                        text(where, x, c)
    elif t == 'table':
        hd = b.get('header')
        rows = b.get('rows')
        if not isinstance(hd, list) or not hd:
            REP.e(where, 'table.header 必须是非空数组')
            return
        for x in hd:
            text(where, x)
        if not isinstance(rows, list) or not rows:
            REP.e(where, 'table.rows 必须是非空数组')
            return
        for r in rows:
            r, c = unwrap(where, r, sc, 'cells')
            if not isinstance(r, list) or len(r) != len(hd):
                REP.e(where, 'table 行的列数与表头不一致: %r' % (r,))
                continue
            for x in r:
                text(where, x, c)
        if 'widths' in b and (not isinstance(b['widths'], list) or len(b['widths']) != len(hd)):
            REP.e(where, 'table.widths 长度要等于列数')
    elif t == 'items':
        its = b.get('items')
        if not isinstance(its, list) or not its:
            REP.e(where, 'items.items 必须是非空数组')
        else:
            for x in its:
                check_id(where, x, 'item')
        if 'per_row' in b and (not isinstance(b['per_row'], int) or isinstance(b['per_row'], bool) or b['per_row'] < 1):
            REP.e(where, 'per_row 要是正整数')
    elif t == 'recipe':
        check_recipe(where, b)
    elif t in ('structure', 'ritual'):
        check_structure(where, b, lang)
    elif t == 'image':
        tex = b.get('texture', '')
        if not isinstance(tex, str) or not RL_RE.match(tex):
            REP.e(where, '插图 texture 要写成 命名空间:路径')
        else:
            ns, path = tex.split(':', 1)
            if ns == L.MODID and not os.path.isfile(os.path.join(L.MAIN_RES, 'assets', ns, *path.split('/'))):
                REP.e(where, '插图 %s 不存在' % tex)
        for k in ('width', 'height'):
            if k in b and (not isinstance(b[k], int) or isinstance(b[k], bool) or b[k] < 1):
                REP.e(where, '%s 要是正整数' % k)
    elif t == 'entity':
        check_entity(where, b.get('id'))
        sc.sink.append(('entity', b.get('id')))
        if 'nbt' in b:
            check_entity_nbt(where, b['nbt'])
        if 'name' in b:
            check_entity_name(where, b['name'])
        if 'scale' in b:
            v = b['scale']
            if not isinstance(v, (int, float)) or isinstance(v, bool) or not (0.25 <= v <= 2):
                REP.e(where, 'entity.scale 要是 0.25~2 之间的数: %r' % (v,))
    if 'caption' in b:
        text(where, b['caption'])
    if t == 'quote' and 'by' in b:
        text(where, b['by'])


def block_shape(b):
    if not isinstance(b, dict):
        return None
    shape = [b.get('type')]
    for k in ('items', 'rows'):
        if isinstance(b.get(k), list):
            shape.append('%s=%d' % (k, len(b[k])))
    fig = {k: b[k] for k in FIGURE_FIELDS if k in b and not (k == 'items' and b.get('type') in ('list', 'steps'))}
    if 'key' in b:
        fig['key'] = strip_labels(b['key'])
    if 'layers' in b and isinstance(b['layers'], list):
        fig['layers'] = [{k: (strip_labels(v) if k == 'key' else v) for k, v in l.items() if k != 'label'}
                         if isinstance(l, dict) else l for l in b['layers']]
    shape.append(json.dumps(fig, ensure_ascii=False, sort_keys=True))
    return '|'.join(str(x) for x in shape)


def strip_labels(key):
    if not isinstance(key, dict):
        return key
    return {ch: ({k: v for k, v in val.items() if k != 'label'} if isinstance(val, dict) else val) for ch, val in key.items()}


# ---------------------------------------------------------------- 分类

def check_locked_hint(where, o, sc, lang):
    """分类 / 条目的 locked_hint（被 advancement 锁住时的解锁提示，FORMAT.md §1.2）：非空字符串，只能和 advancement 一起出现；
    占位符、链接照正文查（记进 sc，参与中英对照）。返回 ['locked_hint'] / []，并进签名：zh_cn 写了、en_us 没写（或反过来）时对照报错"""
    if 'locked_hint' not in o:
        return []
    hint = o['locked_hint']
    if not isinstance(hint, str) or not hint.strip():
        REP.e(where, 'locked_hint 要写一段非空文字')
        return ['locked_hint']
    if not o.get('advancement'):
        REP.e(where, 'locked_hint 只能和 advancement 一起写（没有进度锁时它永远显示不出来）')
    scan_text(where + '.locked_hint', hint, sc)
    if lang == 'zh_cn':
        check_typography(where + '.locked_hint', hint)
    return ['locked_hint']


def check_category(lang, cat, data, sigs, links, meta, drafts):
    where0 = '%s/%s' % (lang, cat)
    if not isinstance(data, dict):
        REP.e(where0, '根必须是对象')
        return
    if data.get('id') != cat:
        REP.e(where0, 'id 必须等于文件名: %r' % (data.get('id'),))
    for k in ('order', 'group', 'title', 'subtitle', 'icon', 'entries'):
        if k not in data:
            REP.e(where0, '分类缺字段 ' + k)
    for k in data:
        if k not in ('id', 'order', 'group', 'title', 'subtitle', 'icon', 'if', 'advancement', 'locked_hint', 'entries') and not k.startswith('_'):
            REP.e(where0, '分类不认识字段 ' + k)
    order = data.get('order')
    if isinstance(order, bool) or not isinstance(order, int):
        REP.e(where0, 'order 必须是整数: %r' % (order,))
    if data.get('group') not in GROUPS:
        REP.e(where0, 'group 只能是 start / main / side: %r' % (data.get('group'),))
    if 'icon' in data:
        check_id(where0 + ' 的图标', data['icon'])
    if 'if' in data:
        check_cond(where0, data['if'])
    if data.get('advancement'):
        check_advancement(where0 + ' 的 advancement', data['advancement'])
    cat_conds = flatten_cond(data.get('if'))
    sc0 = Scan(cat_conds)
    scan_text(where0 + '.title', data.get('title', ''), sc0)
    scan_text(where0 + '.subtitle', data.get('subtitle', ''), sc0)
    hint0 = check_locked_hint(where0, data, sc0, lang)
    if lang == 'zh_cn':
        check_typography(where0 + '.title', data.get('title', ''))
        check_typography(where0 + '.subtitle', data.get('subtitle', ''))
        sub = L.plain(data.get('subtitle', ''))
        if len(sub) > SUBTITLE_MAX:
            REP.w(where0, '首页副标题偏长（%d 字）' % len(sub))
        if data.get('_draft'):
            drafts.append(cat)
    sigs['__cat__'] = (data.get('icon'), data.get('order'), data.get('group'),
                       json.dumps(data.get('if'), ensure_ascii=False), data.get('advancement'), sorted(sc0.sink) + hint0)
    meta[cat] = {'order': order, 'group': data.get('group'), 'conds': cat_conds,
                 'entries': {}, 'draft': bool(data.get('_draft'))}
    seen = set()
    no_src = []
    for idx, en in enumerate(data.get('entries') or []):
        eid = en.get('id') if isinstance(en, dict) else None
        where = '%s/%s' % (where0, eid or ('#' + str(idx)))
        if not isinstance(en, dict) or not isinstance(eid, str) or not ID_RE.match(eid):
            REP.e(where, '条目 id 缺失或格式不对')
            continue
        if eid in seen:
            REP.e(where, '条目 id 重复')
        seen.add(eid)
        for k in ('title', 'icon', 'summary', 'blocks'):
            if k not in en:
                REP.e(where, '条目缺字段 ' + k)
        for k in en:
            if k not in ('id', 'title', 'icon', 'summary', 'if', 'complete', 'optional', 'advancement', 'locked_hint', 'items', 'blocks') and (
                    not k.startswith('_')):
                REP.e(where, '条目不认识字段 ' + k)
        if 'icon' in en:
            check_id(where + ' 的图标', en['icon'])
        if 'if' in en:
            check_cond(where, en['if'])
        for k in ('complete', 'advancement'):
            if en.get(k):
                check_advancement(where + ' 的 ' + k, en[k])
        # optional：布尔，或「按条件可选」的条件（写法同 if，条件成立时可选、不成立时必做；FORMAT.md §1.2）
        if 'optional' in en:
            opt = en['optional']
            if isinstance(opt, bool):
                pass
            elif isinstance(opt, (str, list)) and opt and opt != []:
                check_cond(where, opt, 'optional')
                if isinstance(opt, str) and opt.strip().lower() in ('true', 'false'):
                    REP.e(where, 'optional 写成了字符串 %r：布尔不要加引号' % opt)
            else:
                REP.e(where, 'optional 要写 true / false，或条件（写法同 if：非空字符串或字符串数组）')
        if en.get('optional') and not en.get('complete'):
            REP.e(where, 'optional 只对写了 complete 的步骤有意义')
        if 'items' in en:
            check_entry_items(where, en['items'])
        entry_conds = cat_conds + flatten_cond(en.get('if'))
        meta[cat]['entries'][eid] = {'conds': entry_conds, 'complete': en.get('complete'), 'optional': en.get('optional')}
        if lang == 'zh_cn':
            src = en.get('_src')
            if not isinstance(src, list) or not src:
                if data.get('_draft'):
                    no_src.append(eid)
                else:
                    REP.e(where, 'zh_cn 条目必须有非空的 _src（代码出处）')
            s = L.plain(en.get('summary', ''))
            if len(s) > SUMMARY_MAX:
                REP.w(where, '摘要偏长（%d 字）' % len(s))
        sc = Scan(entry_conds)
        scan_text(where + '.title', en.get('title', ''), sc)
        scan_text(where + '.summary', en.get('summary', ''), sc)
        hint = check_locked_hint(where, en, sc, lang)
        if lang == 'zh_cn':
            check_typography(where + '.title', en.get('title', ''))
            check_typography(where + '.summary', en.get('summary', ''))
        head = sorted(sc.sink) + hint
        links.extend((where, tgt, c) for tgt, c in sc.links)
        blocks = en.get('blocks')
        if not isinstance(blocks, list) or not blocks:
            REP.e(where, 'blocks 必须是非空数组')
            blocks = []
        bsig = []
        stats = {'lore': 0, 'long': []}
        for bi, b in enumerate(blocks):
            bw = '%s.blocks[%d]' % (where, bi)
            bsc = Scan(entry_conds + (flatten_cond(b.get('if')) if isinstance(b, dict) else []))
            check_block(bw, b, bsc, lang, stats)
            links.extend((bw, tgt, c) for tgt, c in bsc.links)
            bsig.append((block_shape(b), sorted(bsc.sink)))
        if stats['lore'] > 1:
            REP.e(where, 'tip lore（源质小知识）每篇最多 1 个，这篇有 %d 个' % stats['lore'])
        for x in stats['long']:
            REP.w(x, '段落超过 %d 字（物品名按游戏里的名字算），拆短一点' % LONG_PARA)
        sigs[eid] = (en.get('icon'), json.dumps(en.get('if'), ensure_ascii=False), en.get('complete'),
                     json.dumps(en.get('optional'), ensure_ascii=False),
                     en.get('advancement'), json.dumps(en.get('items'), ensure_ascii=False), head, bsig)
    if no_src:
        REP.w(where0, '草稿分类里 %d 个条目缺 _src（第二段补）' % len(no_src))
    if lang == 'zh_cn':
        if cat in SKELETON:
            miss = [x for x in SKELETON[cat] if x not in seen and x not in RESERVED.get(cat, ())]
            if miss:
                REP.w(where0, '骨架条目缺失（确认功能不存在才可删，并在汇报里写明）: ' + ', '.join(miss))
        else:
            REP.w(where0, '分类不在骨架里（FORMAT.md §2 与本脚本 SKELETON 要一起加）')
        entries = [e for e in data.get('entries') or [] if isinstance(e, dict)]
        if data.get('group') == 'main' and entries and CFG_KEYS is not None:
            # 「必做」跟配置有关（按条件可选），按默认配置和海纳尔之门关闭两种情形各看一次；整章被藏掉的情形不看
            for sname, over in L.SCENARIOS:
                if not L.cond_true(data.get('if'), CFG_KEYS, over):
                    continue
                vis = [e for e in entries if L.cond_true(e.get('if'), CFG_KEYS, over)]
                opts = [L.is_optional(e.get('optional'), CFG_KEYS, over) for e in vis]
                if vis and not all(opts) and not any(e.get('complete') and not o for e, o in zip(vis, opts)):
                    REP.w(where0, '主线章里没有写了 complete 的必做步骤（%s），首页不会显示它的进度' % sname)


# ---------------------------------------------------------------- 字面数 ↔ 配置默认值（GD-10）

def check_literal_numbers(cat, data):
    """字面数等于相关小节某个配置项的默认值：多半该写成 {cfg:}。草稿分类只汇总成一行"""
    if CFG_KEYS is None:
        return
    hits = []
    for en in data.get('entries') or []:
        if not isinstance(en, dict):
            continue
        sections, _ = L.related_sections(en, CFG_KEYS)
        if not sections:
            continue
        keys = [k for k in CFG_KEYS if k.rsplit('.', 1)[0] in sections]
        for bi, text in iter_texts(en):
            for value, raw, after in L.literal_numbers(text):
                if value in (0, 1):
                    continue
                for k in keys:
                    sug = L.number_matches(value, after, k, CFG_KEYS[k])
                    if sug:
                        hits.append(('zh_cn/%s/%s.blocks[%s]' % (cat, en.get('id'), bi), raw, sug))
    if not hits:
        return
    if data.get('_draft'):
        REP.w('zh_cn/' + cat, '草稿分类里 %d 处字面数等于相关配置的默认值（跑 prep_rewrite.py 看清单）' % len(hits))
    else:
        for where, raw, sug in hits:
            REP.w(where, '字面数 %s 等于配置默认值，是不是该写 %s？（确是代码常量就在 _src 里注明）' % (raw, sug))


APPENDIX = 'from_ec4'
APPENDIX_CELL_MAX = 30   # 附录三列表每格的显示宽度上限（汉字算 1、半角字符算 0.5；batch10 §5.2「窄屏下每格 ≤ 30 个汉字」）


def _conds(x):
    if x is None:
        return []
    return [x] if isinstance(x, str) else [c for c in x if isinstance(c, str)]


def _strs(v):
    return [v] if isinstance(v, str) else [s for s in (v or []) if isinstance(s, str)]


def _covered(need, have):
    """need 里每个条件 have 都带着（「a||b」带着其中任何一项也算，和 [[链接]] 的规则一样）"""
    for c in need:
        if c in have:
            continue
        if '||' in c and any(p.strip() in have for p in c.split('||')):
            continue
        return False
    return True


def check_item_links(zh_datas):
    """{item:} 物品链接要点得出去：这个物品得有落脚的地方（条目 items、配方块写明的产物、物品块、结构 / 仪式产物），
    而且至少有一处落脚点的条件链接所在处都带着 —— 否则落脚的那篇被条件藏掉以后，链接点了没地方去。
    附录 from_ec4 的开篇写着「表里的物品名点一下就翻到介绍它的那篇」，那里报 ERROR；别的分类报 WARN"""
    land = {}
    refs = []

    def walk(where, v, c):
        if isinstance(v, str):
            for m in L.TOKEN_RE.finditer(v):
                if m.group(1) == 'item':
                    rid = m.group(2).split('|', 1)[0]
                    if rid.startswith(L.MODID + ':'):
                        refs.append((where, rid, c))
        elif isinstance(v, list):
            for x in v:
                walk(where, x, c)
        elif isinstance(v, dict):
            cc = c + _conds(v.get('if'))
            for k, x in v.items():
                if k != 'if' and not k.startswith('_'):
                    walk(where, x, cc)

    for cat, data in sorted(zh_datas.items()):
        cc = _conds(data.get('if'))
        for en in data.get('entries') or []:
            if not isinstance(en, dict):
                continue
            ec = cc + _conds(en.get('if'))
            where = 'zh_cn/%s/%s' % (cat, en.get('id'))
            for x in en.get('items') or []:
                if isinstance(x, str):
                    land.setdefault(x, []).append(ec)
                elif isinstance(x, dict):
                    for rid in _strs(x.get('items')):
                        land.setdefault(rid, []).append(ec + _conds(x.get('if')))
            walk(where, en.get('title'), ec)
            walk(where, en.get('summary'), ec)
            for bi, b in enumerate(en.get('blocks') or []):
                if not isinstance(b, dict):
                    continue
                bc = ec + _conds(b.get('if'))
                t = b.get('type')
                figure = {'recipe': ('item', 'items'), 'items': ('items',), 'structure': ('result',), 'ritual': ('result',)}.get(t, ())
                for f in figure:
                    for rid in _strs(b.get(f)):
                        land.setdefault(rid, []).append(bc)
                walk('%s.blocks[%d]' % (where, bi), {k: v for k, v in b.items() if k not in figure}, bc)
    for where, rid, have in refs:
        places = land.get(rid)
        if not places:
            msg = '{item:%s} 在全书没有落脚的地方（没有哪一篇的条目 items、配方块、物品块、结构 / 仪式收录它），点了跳不过去；把它写进介绍它的那篇的条目 items' % rid
        elif not any(_covered(p, have) for p in places):
            msg = '{item:%s} 落脚的那几处都带条件（%s），链接所在处没带上：那篇被藏掉时这个链接点不出去' % (rid, ' / '.join(str(p) for p in places[:3]))
        else:
            continue
        if where.startswith('zh_cn/' + APPENDIX + '/'):
            REP.e(where, msg)
        else:
            REP.w(where, msg)


def _cell_width(text):
    t = L.readable(text, LANG_ZH, cfg_as='99', link_as='某条目')
    return sum(0.5 if ord(ch) < 128 else 1 for ch in t)


def check_appendix_cells(data):
    """附录的三列表在窄屏下每格不超过 APPENDIX_CELL_MAX（物品名按语言文件展开、配置值按 2 位数算）"""
    for en in data.get('entries') or []:
        if not isinstance(en, dict):
            continue
        for bi, b in enumerate(en.get('blocks') or []):
            if not isinstance(b, dict) or b.get('type') != 'table' or len(b.get('header') or []) != 3:
                continue
            for ri, row in enumerate(b.get('rows') or []):
                cells = row.get('cells') if isinstance(row, dict) else row
                for ci, cell in enumerate(cells or []):
                    if not isinstance(cell, str):
                        continue
                    width = _cell_width(cell)
                    if width > APPENDIX_CELL_MAX:
                        REP.e('zh_cn/%s/%s.blocks[%d].rows[%d][%d]' % (APPENDIX, en.get('id'), bi, ri, ci),
                              '三列表这一格显示约 %g 字，超过 %d：细节交给链接的那篇讲' % (width, APPENDIX_CELL_MAX))


def iter_texts(en):
    """条目里所有给玩家看的文字：(块序号, 文字)"""
    for bi, b in enumerate(en.get('blocks') or []):
        if not isinstance(b, dict):
            continue
        for k in ('text', 'title', 'caption', 'by'):
            if isinstance(b.get(k), str):
                yield bi, b[k]
        if b.get('type') in ('list', 'steps'):
            for x in b.get('items') or []:
                yield bi, (x.get('text') if isinstance(x, dict) else x) or ''
        for k in ('rows', 'header'):
            for r in b.get(k) or []:
                cells = r.get('cells') if isinstance(r, dict) else r
                for x in (cells if isinstance(cells, list) else [cells]):
                    if isinstance(x, str):
                        yield bi, x


# ---------------------------------------------------------------- 主流程

def load_file(lang, cat):
    p = os.path.join(L.GUIDE, lang, cat + '.json')
    if not os.path.exists(p):
        return None, p
    try:
        with io.open(p, encoding='utf-8') as f:
            return json.load(f), p
    except ValueError as ex:
        return ('BAD', str(ex)), p


def main():
    only = set(a for a in sys.argv[1:] if not a.startswith('-'))
    if CFG_KEYS is None:
        REP.e('config_keys', '没有 %s：先跑一次 runData（datagen 的 ConfigKeysProvider 导出配置键）' % L.CONFIG_KEYS_FILE)
    cats = set()
    for lg in LANGS:
        for f in glob.glob(os.path.join(L.GUIDE, lg, '*.json')):
            cats.add(os.path.basename(f)[:-5])
    present_langs = [lg for lg in LANGS if os.path.isdir(os.path.join(L.GUIDE, lg))]
    for lg in GENERATED_LANGS:
        if lg not in present_langs:
            REP.w(lg, '没有 guide/%s（跑 make_traditional.py 生成）' % lg)
    sigs = {lg: {} for lg in LANGS}
    datas = {}
    meta_by_lang = {}
    links = []
    drafts = []
    for cat in sorted(cats):
        for lg in present_langs:
            data, p = load_file(lg, cat)
            if data is None:
                REP.e('%s/%s' % (lg, cat), '文件不存在: ' + p)
                continue
            if isinstance(data, tuple):
                REP.e('%s/%s' % (lg, cat), 'JSON 解析失败: ' + data[1])
                continue
            datas[(lg, cat)] = data
            s = {}
            meta = meta_by_lang.setdefault(lg, {})
            check_category(lg, cat, data, s, links if lg == 'zh_cn' else [], meta, drafts)
            sigs[lg][cat] = s
            if lg == 'zh_cn':
                check_literal_numbers(cat, data)
                if cat == APPENDIX:
                    check_appendix_cells(data)
    # 物品链接要有落脚的地方（中英结构一致，只查 zh_cn）
    check_item_links({cat: d for (lg, cat), d in datas.items() if lg == 'zh_cn'})
    zh_meta = meta_by_lang.get('zh_cn', {})
    # 骨架里的分类都要有
    for cat in SKELETON:
        if cat not in cats:
            REP.w('zh_cn/' + cat, '骨架分类还没有文件')
    # order 撞号
    by_order = {}
    for cat, m in zh_meta.items():
        by_order.setdefault(m['order'], []).append(cat)
    for order, cs in sorted(by_order.items(), key=lambda x: str(x[0])):
        if len(cs) > 1:
            REP.e('zh_cn/' + '、'.join(sorted(cs)), 'order 撞号：都是 %s' % order)
    # 链接：目标存在；目标带条件时链接所在处要带同样的条件（中英结构一致，只查 zh_cn）
    for where, target, conds in links:
        parts = target.split('/')
        cm = zh_meta.get(parts[0])
        if cm is None:
            REP.e(where, '链接目标分类不存在: ' + target)
            continue
        if len(parts) == 2:
            em = cm['entries'].get(parts[1])
            if em is None:
                REP.e(where, '链接目标条目不存在: ' + target)
                continue
            need = em['conds']
        else:
            need = cm['conds']
        # 「a||b」这样的任一条件：链接所在处带着其中任何一项（或整串）就算带上了
        miss = [c for c in need if c not in conds and not ('||' in c and any(p.strip() in conds for p in c.split('||')))]
        if miss:
            REP.e(where, '链接目标 %s 带条件 %s，链接所在的块 / 行也要带上（否则目标藏掉后句子还在）' % (target, miss))
    # 结构对照：每种语言都和 zh_cn 比
    for other in present_langs:
        if other == 'zh_cn':
            continue
        for cat in sorted(cats):
            a, b = sigs['zh_cn'].get(cat), sigs[other].get(cat)
            if a is None or b is None:
                continue
            if a.get('__cat__') != b.get('__cat__'):
                REP.e('parity/%s/%s' % (other, cat), '分类的 icon / order / group / if / advancement / 标题占位符与 zh_cn 不一致')
            ka = [k for k in a if k != '__cat__']
            kb = [k for k in b if k != '__cat__']
            if ka != kb:
                REP.e('parity/%s/%s' % (other, cat), '条目 id 列表或顺序与 zh_cn 不一致: zh=%s %s=%s' % (ka, other, kb))
            for eid in ka:
                if eid not in b:
                    continue
                if a[eid][:7] != b[eid][:7]:
                    REP.e('parity/%s/%s/%s' % (other, cat, eid),
                          '条目 icon / if / complete / optional / advancement / items / 标题摘要占位符与 zh_cn 不一致')
                ba, bb = a[eid][7], b[eid][7]
                if len(ba) != len(bb):
                    REP.e('parity/%s/%s/%s' % (other, cat, eid), '块数量不一致: zh=%d %s=%d' % (len(ba), other, len(bb)))
                    continue
                for i, (x, y) in enumerate(zip(ba, bb)):
                    if x[0] != y[0]:
                        REP.e('parity/%s/%s/%s' % (other, cat, eid), '第 %d 块的类型 / 条数 / 图的字段不一致: zh=%s %s=%s' % (i, x[0], other, y[0]))
                    elif x[1] != y[1]:
                        REP.e('parity/%s/%s/%s' % (other, cat, eid), '第 %d 块的占位符 / 链接 / 条件不一致: zh=%s %s=%s' % (i, x[1], other, y[1]))

    def keep(line):
        if not only:
            return True
        head = line.split(':', 1)[0]
        return any(('/' + c + '/') in ('/' + head + '/') for c in only)

    errs = [x for x in REP.errors if keep(x)]
    warns = [x for x in REP.warns if keep(x)]
    for x in errs:
        print('ERROR ' + x)
    for x in warns:
        print('WARN  ' + x)
    n_entries = sum(len(m['entries']) for m in zh_meta.values())
    print('== %d errors, %d warnings；%d 个分类、%d 个条目；草稿分类（_draft）%d 个%s' % (
        len(errs), len(warns), len(cats), n_entries, len(drafts), '' if VANILLA_ITEMS is not None else '；没找到 build/mcsrc，跳过了原版 id 检查'))
    sys.exit(1 if errs else 0)


if __name__ == '__main__':
    main()
