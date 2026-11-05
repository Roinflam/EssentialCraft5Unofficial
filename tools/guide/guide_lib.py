# -*- coding: utf-8 -*-
# 研究手册工具的公用部分：路径、名单（物品 / 方块 / 实体 / 饰品 / 配置键）、正文纯文字化、字面数与配置默认值的对照。
# validate_guide.py、prep_rewrite.py 都从这里取，规则只写一份。规范见同目录 FORMAT.md。
# 本文件刻意不写反斜杠（Write / Bash 工具会吃掉一层），需要时用 chr(92) / chr(10)。
import difflib
import glob
import io
import json
import os
import re

BS = chr(92)
NL = chr(10)
TAB = chr(9)
CR = chr(13)
WS = '[ ' + NL + TAB + CR + ']'

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
MODID = 'essentialcraft5'
MAIN_RES = os.path.join(ROOT, 'src', 'main', 'resources')
GEN_RES = os.path.join(ROOT, 'src', 'generated', 'resources')
ASSETS = os.path.join(MAIN_RES, 'assets', MODID)
GUIDE = os.path.join(ASSETS, 'guide')
GEN_ASSETS = os.path.join(GEN_RES, 'assets', MODID)
CONFIG_KEYS_FILE = os.path.join(GEN_ASSETS, 'guide', 'config_keys.json')
JAVA = os.path.join(ROOT, 'src', 'main', 'java')
JAVA_PKG = os.path.join(JAVA, 'com', 'flameforge', MODID)
MCSRC = os.path.join(ROOT, 'build', 'mcsrc', 'net', 'minecraft', 'world').replace(os.sep, '/')

# 汉字（CJK 统一表意文字 + 扩展 A + 兼容）
HAN = '[' + chr(0x3400) + '-' + chr(0x9fff) + chr(0xf900) + '-' + chr(0xfaff) + ']'
HAN_RE = re.compile(HAN)


# ---------------------------------------------------------------- 名单

def _read(path):
    try:
        with io.open(path, encoding='utf-8') as f:
            return f.read()
    except OSError:
        return None


def load_json(path, default=None):
    try:
        with io.open(path, encoding='utf-8') as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def load_mod_ids():
    """命名表 tools/id_map_*.json 里 status 不是 removed 的注册名（物品和方块共用一张名单）"""
    ids = set()
    for name in ('id_map_items.json', 'id_map_blocks.json'):
        for e in load_json(os.path.join(ROOT, 'tools', name), []) or []:
            if e.get('new_id') and e.get('status') != 'removed':
                ids.add(MODID + ':' + e['new_id'])
    return ids


def _vanilla(path, pattern):
    src = _read(path)
    if src is None:
        return None
    return {'minecraft:' + n for n in re.findall(pattern, src)}


def load_vanilla():
    """原版物品 / 方块 / 实体名单（从 build/mcsrc 抓 register("名字")）；没有这份源码返回 None（跳过原版检查）"""
    reg = '(?:register|registerItem)[(]' + WS + '*"([a-z0-9_/.]+)"'
    blocks = _vanilla(MCSRC + '/level/block/Blocks.java', reg)
    items = _vanilla(MCSRC + '/item/Items.java', reg)
    if items is not None and blocks is not None:
        items |= blocks     # 方块物品在 Items 里写成 registerBlock(Blocks.X)，没有字面名字
    entities = _vanilla(MCSRC + '/entity/EntityType.java', 'register[(]' + WS + '*"([a-z0-9_]+)"')
    return items, blocks, entities


def load_lang(lang):
    """datagen 生成的语言文件（src/generated），外加 src/main 里手放的（繁体）"""
    out = {}
    for base in (GEN_ASSETS, ASSETS):
        d = load_json(os.path.join(base, 'lang', lang + '.json'), {})
        if isinstance(d, dict):
            out.update(d)
    return out


def load_entities(lang_keys):
    return {MODID + ':' + k[len('entity.' + MODID + '.'):] for k in lang_keys if k.startswith('entity.' + MODID + '.')}


def load_baubles():
    """本模组的饰品：Curios 各槽位标签里本命名空间的物品"""
    ids = set()
    for base in (GEN_RES, MAIN_RES):
        for f in glob.glob(os.path.join(base, 'data', 'curios', 'tags', 'items', '*.json')):
            for v in (load_json(f, {}) or {}).get('values', []):
                if isinstance(v, str) and v.startswith(MODID + ':'):
                    ids.add(v)
    return ids


def load_config_keys():
    """datagen 导出的配置键表：键 → {type, default, min?, max?, values?}；没有这个文件返回 None"""
    d = load_json(CONFIG_KEYS_FILE)
    if not isinstance(d, dict):
        return None
    return d.get('keys', {})


def advancement_exists(rid):
    ns, path = rid.split(':', 1)
    for base in (MAIN_RES, GEN_RES):
        if os.path.isfile(os.path.join(base, 'data', ns, 'advancements', *path.split('/')) + '.json'):
            return True
    return False


def recipe_exists(rid):
    ns, path = rid.split(':', 1)
    for base in (MAIN_RES, GEN_RES):
        if os.path.isfile(os.path.join(base, 'data', ns, 'recipes', *path.split('/')) + '.json'):
            return True
    return False


_JAVA_SOURCES = None


def java_sources():
    """src/main/java 下全部源码（去掉注释；GameTest 除外），{相对路径: 源码}，读一次缓存"""
    global _JAVA_SOURCES
    if _JAVA_SOURCES is None:
        _JAVA_SOURCES = {}
        for path in glob.glob(os.path.join(JAVA, '**', '*.java'), recursive=True):
            if os.sep + 'gametest' + os.sep in path:
                continue
            src = _read(path)
            if src is None:
                continue
            src = re.sub('/[*].*?[*]/', '', src, flags=re.S)
            src = re.sub('//[^' + NL + ']*', '', src)
            _JAVA_SOURCES[os.path.relpath(path, JAVA_PKG).replace(os.sep, '/')] = src
    return _JAVA_SOURCES


def registered_in_java(call, rid):
    """源码里有没有「call( … 」后面紧跟着写了这个 id（完整 id 或本模组的路径）的地方"""
    ns, path = rid.split(':', 1)
    for src in java_sources().values():
        for m in re.finditer(re.escape(call) + WS + '*[(]', src):
            for lit in re.findall('"([^"]*)"', src[m.end():m.end() + 200])[:2]:
                if lit == rid or (ns == MODID and lit == path):
                    return True
    return False


# ---------------------------------------------------------------- 正文 → 纯文字

TOKEN_RE = re.compile('[{]([a-z]+):([^{}]*)[}]')
LINK_RE = re.compile(re.escape('[[') + '([^]|]*)([|]([^]]*))?' + re.escape(']]'))
TAG_RE = re.compile('</?[a-z]+(:[0-9A-Fa-f]{6})?>')
MARK = chr(0x2610)   # 占位符 / 链接展开后的文字用这个中性字符代替（既不是汉字也不是字母）


def plain(text, keep_link_text=False):
    """去掉标签；占位符换成 MARK；链接换成 MARK（keep_link_text 时换成显示文字）；转义符去掉"""
    if not isinstance(text, str):
        return ''
    t = text.replace(BS + '<', chr(0xE000)).replace(BS + '{', chr(0xE001)).replace(BS + '[', chr(0xE002))
    t = TOKEN_RE.sub(MARK, t)
    if keep_link_text:
        t = LINK_RE.sub(lambda m: m.group(3) if m.group(3) is not None else MARK, t)
    else:
        t = LINK_RE.sub(MARK, t)
    t = TAG_RE.sub('', t)
    return t.replace(chr(0xE000), '<').replace(chr(0xE001), '{').replace(chr(0xE002), '[')


def han_count(text):
    return len(HAN_RE.findall(plain(text, keep_link_text=True)))


# ---------------------------------------------------------------- 字面数 ↔ 配置默认值（GD-10）

NUM_RE = re.compile('(?<![0-9A-Za-z.])([0-9]+(?:[.][0-9]+)?)(?![0-9])')
ROMAN_OR_ID = re.compile('[A-Za-z_]$')


def literal_numbers(text):
    """正文里的字面数（占位符、链接里的不算）：[(数值, 原文, 后面紧跟的几个字)]"""
    t = plain(text)
    out = []
    for m in NUM_RE.finditer(t):
        before = t[max(0, m.start() - 1):m.start()]
        if before and ROMAN_OR_ID.match(before):
            continue        # y=0 这种不算；字母后面的数（MK2）也跳过
        try:
            v = float(m.group(1))
        except ValueError:
            continue
        out.append((v, m.group(1), t[m.end():m.end() + 6]))
    return out


SEC_WORDS = ('秒', ' 秒', 's', ' s', ' sec', ' second')
MIN_WORDS = ('分钟', ' 分钟', ' min', ' minute')
TICK_WORDS = ('刻', ' 刻', ' tick', 't')


def _close(a, b):
    return abs(a - b) <= 1e-9 * max(1.0, abs(a), abs(b))


def number_matches(value, after, key, spec):
    """字面数 value（后面紧跟 after）和配置项 key 的默认值对得上吗？对得上返回建议写法，否则 None"""
    t = spec.get('type')
    if t not in ('int', 'long', 'double'):
        return None
    d = spec.get('default')
    if not isinstance(d, (int, float)) or isinstance(d, bool):
        return None
    after_s = after.lstrip()
    if after_s.startswith('%'):
        if t == 'double' and _close(value / 100.0, d):
            return '{cfg:%s|pct}' % key
        if _close(value, d):
            return '{cfg:%s|pctv}' % key
        return None
    if any(after.startswith(w) for w in SEC_WORDS) and t in ('int', 'long') and _close(value * 20, d):
        return '{cfg:%s|sec}' % key
    if any(after.startswith(w) for w in MIN_WORDS) and t in ('int', 'long') and _close(value * 1200, d):
        return '{cfg:%s|div:1200}' % key
    if _close(value, d):
        return '{cfg:%s}' % key
    return None


# ---------------------------------------------------------------- 条目 → 相关配置小节

_REGISTRY_INDEX = None


def _registry_index():
    """注册名 → 注册语句里出现的类名（registry/EC*.java，按「"名字"」所在的语句切）"""
    global _REGISTRY_INDEX
    if _REGISTRY_INDEX is None:
        _REGISTRY_INDEX = {}
        for rel, src in java_sources().items():
            if not rel.startswith('registry/'):
                continue
            for stmt in src.split(';'):
                names = re.findall('"([a-z0-9_]+)"', stmt)
                if not names:
                    continue
                classes = set(re.findall('([A-Z][A-Za-z0-9]+)(?:::new|[(]|[.]class|<)', stmt))
                for n in names:
                    _REGISTRY_INDEX.setdefault(n, set()).update(classes)
    return _REGISTRY_INDEX


_CLASS_FILES = None


def _class_files():
    global _CLASS_FILES
    if _CLASS_FILES is None:
        _CLASS_FILES = {}
        for rel in java_sources():
            _CLASS_FILES.setdefault(os.path.basename(rel)[:-5], []).append(rel)
    return _CLASS_FILES


def pushes_in(rel):
    return re.findall('[.]push[(]"([a-z0-9_]+)"[)]', java_sources().get(rel, ''))


def entry_item_ids(items):
    """条目 items 里的全部物品 id：条件组 {"if": …, "items": [...]} 展开，不管条件（FORMAT.md §1.2）"""
    out = []
    for i in items if isinstance(items, list) else []:
        if isinstance(i, str):
            out.append(i)
        elif isinstance(i, dict) and isinstance(i.get('items'), list):
            out.extend(x for x in i['items'] if isinstance(x, str))
    return out


def related_sections(entry, cfg_keys):
    """条目 items / icon 的注册名 → 注册语句里的类 → 类文件里 push 的小节 → 实际存在的小节前缀（machines.x / general.x）
    返回 (小节前缀集合, 相关类文件列表)"""
    sections = set()
    files = []
    prefixes = {k.rsplit('.', 1)[0] for k in cfg_keys}
    ids = entry_item_ids(entry.get('items'))
    if entry.get('icon'):
        ids.append(entry['icon'])
    for iid in ids:
        if not isinstance(iid, str) or not iid.startswith(MODID + ':'):
            continue
        path = iid.split(':', 1)[1]
        for top in ('machines', 'general'):
            if top + '.' + path in prefixes:
                sections.add(top + '.' + path)
        for cls in sorted(_registry_index().get(path, ())):
            for rel in _class_files().get(cls, []):
                if rel.startswith('registry/'):
                    continue
                if rel not in files:
                    files.append(rel)
                for p in pushes_in(rel):
                    for top in ('machines', 'general'):
                        if top + '.' + p in prefixes:
                            sections.add(top + '.' + p)
    return sections, files


def keys_by_similarity(words, cfg_keys):
    """没有相关小节时的退路：按和条目 id / 物品名的相似度给配置键排序"""
    def score(k):
        sec = k.rsplit('.', 1)[0].split('.', 1)[-1]
        return max([difflib.SequenceMatcher(None, w, sec).ratio() for w in words] or [0])
    return sorted(cfg_keys, key=lambda k: (-score(k), k))


def readable(text, lang_map, cfg_as='{cfg}', link_as=None):
    """给人看的纯文字：物品 / 方块名按语言文件展开，配置值写成 cfg_as，别的占位符写成 {类型}，
    没写显示文字的链接写成 link_as（None = 写目标 id）"""
    def expand(m):
        kind, val = m.group(1), m.group(2)
        if kind in ('item', 'block') and ':' in val:
            ns, path = val.split(':', 1)
            for pre in ('item', 'block'):
                name = lang_map.get(pre + '.' + ns + '.' + path)
                if name:
                    return name
            return path
        return cfg_as if kind == 'cfg' else '{' + kind + '}'
    t = TOKEN_RE.sub(expand, text if isinstance(text, str) else '')
    t = LINK_RE.sub(lambda m: m.group(3) if m.group(3) is not None else (link_as if link_as is not None else m.group(1)), t)
    return plain(t)


def display_len(text, lang_map):
    """一段显示出来大约多少字：物品 / 方块名按语言文件展开，配置值按 4 个字、别的占位符按 4 个字、没写显示文字的链接按 6 个字算"""
    return len(readable(text, lang_map, cfg_as='xxxx', link_as='xxxxxx'))


# ---------------------------------------------------------------- 条件求值（和引擎 GuideConditions 同一套）

# 离线工具当作「装了」的模组：原版、Forge、本模组、Curios（必需依赖）
LOADED_MODS = {'minecraft', 'forge', MODID, 'curios'}
# 检查时跑的两种情形：默认配置、海纳尔之门关闭（check_progression.py 默认也跑这两种）
SCENARIOS = (('默认配置', {}), ('海纳尔之门关闭', {'general.hoanna.portal_enabled': False}))
INVALID = '?invalid'
_MISSING = object()
_CFG_COND = re.compile('^cfg:' + WS + '*([A-Za-z0-9_.]+)' + WS + '*(?:[|]' + WS + '*(at|stage):([0-9]+)' + WS + '*)?'
                       '(?:(>=|<=|==|!=|>|<)' + WS + '*(-?[0-9]+(?:[.][0-9]+)?))?$')


def list_item(v, sel, index):
    """格式链 / 条件里的 at:N、stage:N（同引擎 GuideConditions.element / stageElement）：
    at:N 取第 N 项，越界取不到；stage:N 按阶取，列表比 N 短时沿用最后一项，空列表取不到。取不到返回 _MISSING"""
    if not isinstance(v, list) or not v:
        return _MISSING
    if sel == 'stage':
        return v[min(index, len(v) - 1)]
    return v[index] if index < len(v) else _MISSING


def _cfg_value(key, cfg_keys, over):
    if over and key in over:
        return over[key]
    spec = (cfg_keys or {}).get(key)
    if not isinstance(spec, dict) or 'default' not in spec:
        return _MISSING
    return spec['default']


def _truthy(v):
    if isinstance(v, bool):
        return v
    if isinstance(v, (int, float)):
        return v != 0
    if isinstance(v, list):
        return len(v) > 0
    return v is not None and str(v) != ''


def _rl(s):
    """ResourceLocation.tryParse 的效果：没写命名空间时补 minecraft"""
    s = str(s).strip()
    return s if ':' in s else 'minecraft:' + s


def _atom(s, cfg_keys, over, creative):
    """一项（已去掉 !）：True / False；判定不了（缺键、语法错、不认识的前缀）为 None"""
    if s.startswith('cfg:'):
        m = _CFG_COND.match(s)
        if not m:
            return None
        v = _cfg_value(m.group(1), cfg_keys, over)
        if v is _MISSING:
            return None
        if m.group(2):
            v = list_item(v, m.group(2), int(m.group(3)))
            if v is _MISSING:
                return None
        if m.group(4) is None:
            return _truthy(v)
        if isinstance(v, bool):
            left = 1.0 if v else 0.0
        elif isinstance(v, (int, float)):
            left = float(v)
        elif isinstance(v, list):
            left = float(len(v))
        else:
            try:
                left = float(v)
            except (TypeError, ValueError):
                return None
        right = float(m.group(5))
        eps = 1e-9
        op = m.group(4)
        return {'>': left > right + eps, '>=': left >= right - eps, '<': left < right - eps,
                '<=': left <= right + eps, '==': abs(left - right) <= eps, '!=': abs(left - right) > eps}[op]
    if s.startswith('mod:'):
        return s[4:].strip() in LOADED_MODS
    if s.startswith('bauble:'):
        disabled = _cfg_value('general.baubles.disabled', cfg_keys, over)
        disabled = disabled if isinstance(disabled, list) else []
        return _rl(s[len('bauble:'):]) not in {_rl(x) for x in disabled}
    if s.startswith('module:'):
        # 宅男套装模块（general.computer_modules.disabled），写法同 bauble:
        disabled = _cfg_value('general.computer_modules.disabled', cfg_keys, over)
        disabled = disabled if isinstance(disabled, list) else []
        return _rl(s[len('module:'):]) not in {_rl(x) for x in disabled}
    if s.startswith('gamemode:'):
        return creative if s[len('gamemode:'):].strip() == 'creative' else None
    return None


def cond_true(cond, cfg_keys, over=None, creative=False):
    """手册条件（if 的写法；按条件可选的 optional 也是这个写法）在给定配置下成不成立。
    cfg_keys 是 config_keys.json 的 keys（取默认值），over 覆盖其中几项（例 SCENARIOS 的门关闭）。
    和引擎一致：数组 = 全部成立，一个字符串里 || = 任一成立；判定不了的项不管有没有 ! 都按不成立。"""
    if cond is None:
        return True
    if isinstance(cond, list):
        return all(cond_true(c, cfg_keys, over, creative) for c in cond)
    if not isinstance(cond, str):
        return False
    if '||' in cond:
        return any(cond_true(p, cfg_keys, over, creative) for p in cond.split('||') if p.strip())
    s = cond.strip()
    neg = False
    while s.startswith('!'):
        neg = not neg
        s = s[1:].strip()
    r = _atom(s, cfg_keys, over, creative)
    return r is not None and neg != r


def optional_rule(raw):
    """条目 optional → (恒可选, 条件, 问题)。和引擎 GuideSteps.OptionalRule.parse 一致：
    布尔照旧；字符串 "true" / "false"（不分大小写）按布尔读；其余字符串、字符串数组是按条件可选；写坏的按必做并给出问题"""
    if raw is None:
        return False, None, None
    if isinstance(raw, bool):
        return raw, None, None
    if isinstance(raw, str):
        s = raw.strip()
        if s.lower() == 'true':
            return True, None, None
        if s.lower() == 'false':
            return False, None, None
        if not s:
            return False, None, 'optional 是空串'
        return False, [s], None
    if isinstance(raw, list):
        if not raw:
            return False, None, 'optional 是空数组'
        conds = [x if isinstance(x, str) else INVALID for x in raw]
        return False, conds, ('optional 数组里只能写字符串条件' if INVALID in conds else None)
    return False, None, 'optional 要写 true / false 或条件（写法同 if）'


def is_optional(raw, cfg_keys, over=None):
    """当前配置下这一步是不是可选"""
    always, conds, _ = optional_rule(raw)
    return always or (conds is not None and cond_true(conds, cfg_keys, over))
