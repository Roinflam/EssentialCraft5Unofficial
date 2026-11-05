# -*- coding: utf-8 -*-
# 一次性转换：旧研究手册 → 冒险指南格式（方案 tools/plan/guide_kuvalich.md §3.3，格式 tools/guide/FORMAT.md）
#
#   旧：src/main/resources/assets/essentialcraft5/guide/<分类>.json（只写翻译键）+ tools/lang/keys/*.json 里的 guide.essentialcraft5.* 文字
#   新：src/main/resources/assets/essentialcraft5/guide/zh_cn/<分类>.json、en_us/<分类>.json（文字直接写进文件，中英结构一字不差）
#
# 用法：python tools/guide/convert_guide.py [--force]
#   新目录里已经有文件时不覆盖（第二段改写过的内容会被冲掉），要重跑加 --force。
#   旧分类文件和 guide.* 语言键不删（引擎切换完成后再删）。
#
# 转换规则（§3.3）：
#   分类 title/description → title/subtitle；flow → group（标题「第…章」= main，「支线」= side）；basics 并进新分类 start（入门）
#   条目 description → summary；省略 title → "{item:<icon>}"；order/icon/advancement/complete/optional/items 原样
#   页 title → h 块；text 按空行分段 → p；连续以「· 」开头的行 → list；只有「**标签**：」一行、后面跟列表的 → h（标签）+ list
#   **x** → <em>x</em>；{entry:a/b} → [[a/b]]（basics/mru → start/mru，basics/mrucu → start/mrucu，basics/mithriline → start/materials）
#   「**下一步**：」段：只指向全书顺序的下一篇、除链接外没别的话 → 删掉（篇末翻页卡代替）；否则 → tip info「下一步」
#   「**做成的标志**：」段 → tip good「做成的标志」；「**小知识**：」段和标题为「小知识」的文字页 → tip lore（每篇最多 1 个，多的退回 h + p）
#   items / recipe / ritual / structure / image 页 → 同名块，页里的 text 接在后面变成 p
#   结构图的 guide.essentialcraft5.* 标签 → 按语言写成文字；其它语言键（structure.essentialcraft5.rule.any_block）保留
#   每个分类加 "_draft": true
#   另按 TEXT_PATCHES 手改几处（3 处元词、basics→入门 的说法、新引擎的翻页方式），按 NEW_ENTRIES 给入门分类补骨架条目。
#
# 本文件刻意不写反斜杠（Write / Bash 工具会吃掉一层），需要时用 chr(92) / chr(10)。
import glob
import io
import json
import os
import re
import sys
from collections import OrderedDict

BS = chr(92)
NL = chr(10)
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
OLD_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'essentialcraft5', 'guide')
KEYS_DIR = os.path.join(ROOT, 'tools', 'lang', 'keys')
MODID = 'essentialcraft5'
LANGS = (('zh_cn', 'zh'), ('en_us', 'en'))

LINK_REMAP = {
    'basics/mru': 'start/mru',
    'basics/mrucu': 'start/mrucu',
    'basics/mithriline': 'start/materials',
}
BASICS_REMAP = {'mru': 'mru', 'mrucu': 'mrucu', 'mithriline': 'materials'}

# 「下一步」段除链接和标点外剩下的字数不超过这个数，而且只指向全书顺序的下一篇 → 删掉（纯导航）
NEXT_DROP_MAX_REST = 16

TIP_TITLES = {
    'next': {'zh': '下一步', 'en': 'Next'},
    'done': {'zh': '做成的标志', 'en': 'Done when'},
}

# ---------------------------------------------------------------- 手改（旧键, 语言, 原文片段, 新片段）
# 每条都必须命中，否则报错（旧文字改过了就回来改这里）
TEXT_PATCHES = [
    # 元词 1：baubles.finding.2 删掉「服务器关掉了的饰品……」那句（逐件介绍的块在第二段挂 bauble: 条件）
    ('guide.essentialcraft5.baubles.finding.2', 'zh',
     '每件的提示框里都写着它做什么。服务器关掉了的饰品，提示框末尾有一行红字：戴着没用，也不会再从箱子里开出来。',
     '每件的提示框里都写着它做什么。'),
    ('guide.essentialcraft5.baubles.finding.2', 'en',
     'Each tooltip says what the bauble does. If the server has turned one off, its tooltip ends with a red line: it does nothing when worn and no longer turns up in chests.',
     'Each tooltip says what the bauble does.'),
    # 元词 2：富集仓外壳尺寸改成跟配置走
    ('guide.essentialcraft5.enrichment.controller.4', 'zh',
     '每条边 3~32 格（服务器可以改）',
     '每条边 {cfg:machines.enrichment_chamber.min_size}~{cfg:machines.enrichment_chamber.max_size} 格'),
    ('guide.essentialcraft5.enrichment.controller.4', 'en',
     '3 to 32 blocks along each side (the server can change this)',
     '{cfg:machines.enrichment_chamber.min_size} to {cfg:machines.enrichment_chamber.max_size} blocks along each side'),
    # 元词 3：风之眷顾去掉「服务器」
    ('guide.essentialcraft5.wind.windbound.3', 'zh',
     '受眷顾以后，服务器每秒替你掷一次骰子：',
     '受眷顾以后，每秒都会替你掷一次骰子：'),
    ('guide.essentialcraft5.wind.windbound.3', 'en',
     'Once you are Windbound, the server rolls for you every second:',
     'Once you are Windbound, the game rolls for you every second:'),
    # 「资料」分类并进了「入门」；新界面的「下一步」写在首页底部
    ('guide.essentialcraft5.elements.book.1', 'zh',
     '首页顶上始终写着你的下一步', '首页底部始终写着你的下一步'),
    ('guide.essentialcraft5.elements.book.1', 'en',
     'the top of the front page always shows your next step', 'the bottom of the front page always shows your next step'),
    ('guide.essentialcraft5.elements.book.1', 'zh',
     '翻到最后的「资料」分类。', '翻到开头的「入门」分类。'),
    ('guide.essentialcraft5.elements.book.1', 'en',
     'see the "Reference" category at the end.', 'see the "Getting Started" category at the front.'),
    # 新界面是竖着滚的正文 + 篇末「上一篇 / 下一篇」，没有 ← → 翻页
    ('guide.essentialcraft5.elements.book.2', 'zh',
     '用 ← →、滚轮或底栏的按钮翻页；翻过一条的最后一页，会接着翻到下一步，一章读完接着读下一章。',
     '用滚轮往下读；每一篇的末尾都有「上一篇 / 下一篇」，一章读完接着读下一章。'),
    ('guide.essentialcraft5.elements.book.2', 'en',
     'Turn pages with ← →, the mouse wheel or the buttons along the bottom. Turning past the last page of an entry takes you to the next step, and the end of a chapter leads into the next one.',
     'Scroll with the mouse wheel to read on. Every entry ends with Previous / Next cards, and the end of a chapter leads into the next one.'),
]

# 分类图标手改：元素水晶的默认栈（大小 0）用的是原作全透明的 elementalcrystal0，首页格子会是空的
CATEGORY_ICON_PATCHES = {
    'crystal': MODID + ':crystal_former',
}

# ---------------------------------------------------------------- 入门分类（start）
START_CATEGORY = {
    'order': 5,
    'icon': MODID + ':research_book',
    'title': {'zh': '入门', 'en': 'Getting Started'},
    'subtitle': {'zh': '先读这里：MRU 是什么、这本书怎么用', 'en': 'Start here: what MRU is and how to use this book'},
}
# 入门分类的条目顺序；None = 从 basics 搬来的条目（值是 basics 里的条目 id）
START_ORDER = ['overview', ('mru', 'mru'), ('mrucu', 'mrucu'), 'glossary', ('materials', 'mithriline'), 'faq', 'numbers']


def T(zh, en):
    return {'zh': zh, 'en': en}


# 新写的骨架条目（第二段 W1 重写；这里只放能从现有正文和代码核实的内容，给第一段截图用）
NEW_ENTRIES = {
    'overview': {
        'title': T('模组概览', 'Overview'),
        'icon': MODID + ':research_book',
        'summary': T('这本书怎么读、主线怎么走', 'How this book and the main line work'),
        'blocks': [
            {'type': 'p', 'text': T(
                '源质魔法围绕一种叫 <hl>MRU</hl> 的魔法辐射展开：用机器收集它、存起来，再拿它驱动别的机器。MRU 是什么，见 [[start/mru]]。',
                'EssentialCraft is built around a magical radiation called <hl>MRU</hl>: machines gather it, store it and use it to run other machines. For what MRU is, see [[start/mru]].')},
            {'type': 'list', 'items': [
                T('<t>入门</t>：基本概念和速查表，碰上不懂的词先来这里。',
                  '<t>Getting Started</t>: basic ideas and quick-reference tables. Come here when a word is new to you.'),
                T('<t>主线</t>：十五章，按游戏进度一步步带你从挖魔晶走到物流。',
                  '<t>Main line</t>: fifteen chapters that walk you from digging up drops to moving items around, one step at a time.'),
                T('<t>支线</t>：装备、器械、饰品等，随时可以翻。',
                  '<t>Side chapters</t>: gear, devices, baubles and more. Read them whenever you like.'),
            ]},
            {'type': 'p', 'text': T('从 [[elements/book]] 开始读。', 'Start with [[elements/book]].')},
        ],
    },
    'glossary': {
        'title': T('名词速查', 'Glossary'),
        'icon': 'minecraft:book',
        'summary': T('书里常见的词，一句话讲清', 'Common terms, one line each'),
        'blocks': [
            {'type': 'table', 'header': [T('词', 'Term'), T('意思', 'Meaning'), T('详见', 'More')], 'widths': [2, 5, 3], 'rows': [
                [T('MRU', 'MRU'),
                 T('魔法辐射单位，机器收集、储存、消耗的魔力', 'Magical Radiation Unit: the power machines gather, store and use'),
                 T('[[start/mru]]', '[[start/mru]]')],
                [T('平衡值', 'Balance'),
                 T('每份 MRU 带的 0~2 的数：{balance:pure} 正好 1.0，{balance:frozen} 低于 1.0，{balance:chaos} 高于 1.0',
                   'A number from 0 to 2 carried by all MRU: {balance:pure} is exactly 1.0, {balance:frozen} is below 1.0, {balance:chaos} is above 1.0'),
                 T('[[start/mru]]', '[[start/mru]]')],
                [T('绑定宝石', 'Bound Gem'),
                 T('记住源头位置的宝石，放进机器就会从源头拉 MRU，不用管道和电线', 'A gem that remembers a source; put it in a machine and the machine pulls MRU from there, no pipes or wires'),
                 T('[[crafting/bound_gem]]', '[[crafting/bound_gem]]')],
                [T('MRU 聚集体', 'MRUCU'),
                 T('悬在空中的一团 MRU，多半是机器漏出来的', 'A floating cloud of MRU, mostly leaked by machines'),
                 T('[[start/mrucu]]', '[[start/mrucu]]')],
                [T('UBMRU', 'UBMRU'),
                 T('亲手击杀生物时吸收的矩阵，攒够了能换成 MRU', 'Matrix you absorb from creatures you kill yourself; save it up to turn into MRU'),
                 T('[[mru/ubmru]]', '[[mru/ubmru]]')],
                [T('矩阵损伤', 'Matrix damage'),
                 T('你的 MRU 矩阵受的暗伤，不会自己消退，攒多了变成腐化效果', 'Hidden harm to your MRU matrix; it never fades and turns into corruption effects'),
                 T('[[corruption/matrix_damage]]', '[[corruption/matrix_damage]]')],
                [T('ESPE', 'ESPE'),
                 T('末影之星脉冲能量，米斯莱熔炉烧的另一种能量', 'Ender Star Pulse Energy, the other kind of energy the Mithriline Furnace runs on'),
                 T('[[mithriline/mithriline_ingot]]', '[[mithriline/mithriline_ingot]]')],
            ]},
        ],
    },
    'faq': {
        'title': T('常见误区', 'Common Mistakes'),
        'icon': 'minecraft:writable_book',
        'summary': T('新手最容易踩的几个坑', 'Traps new players fall into'),
        'blocks': [
            {'type': 'list', 'items': [
                T('<em>死亡会让你失去全部 UBMRU</em>。攒下一些就尽快换成 MRU：[[mru/ubmru]]。',
                  '<em>Dying costs you all your UBMRU.</em> Turn what you have into MRU soon: [[mru/ubmru]].'),
                T('<em>只有你亲手击杀的生物才给 UBMRU</em>，机器、宠物替你杀的不算。',
                  '<em>Only your own kills give UBMRU</em>; kills by machines or pets do not.'),
                T('<em>别让 MRU 聚集体长得太大</em>：存满 1 万 MRU 后会慢慢损伤附近玩家的矩阵：[[start/mrucu]]。',
                  "<em>Don't let an MRUCU grow too big</em>: once it holds 10,000 MRU it slowly harms the matrix of players nearby: [[start/mrucu]]."),
                T('<em>本模组的机器不会替你挖方块、放方块</em>，也不会点火：[[automation/rules]]。',
                  "<em>This mod's machines never mine or place blocks for you</em>, and never light fires: [[automation/rules]]."),
                T('<em>同种饰品只生效一件</em>：[[baubles/finding]]。',
                  '<em>Only one bauble of each kind works</em>: [[baubles/finding]].'),
            ]},
        ],
    },
    'numbers': {
        'title': T('数值速查', 'Numbers at a Glance'),
        'icon': 'minecraft:comparator',
        'summary': T('常用机器的关键数字', 'Key numbers for common machines'),
        'blocks': [
            {'type': 'h', 'text': T('产能器能存多少 MRU', 'How much MRU generators hold')},
            {'type': 'kv', 'rows': [
                [T('{item:essentialcraft5:heat_generator}', '{item:essentialcraft5:heat_generator}'),
                 T('{cfg:machines.heat_generator.max_mru} MRU', '{cfg:machines.heat_generator.max_mru} MRU')],
                [T('{item:essentialcraft5:natural_furnace}', '{item:essentialcraft5:natural_furnace}'),
                 T('{cfg:machines.natural_furnace.max_mru} MRU', '{cfg:machines.natural_furnace.max_mru} MRU')],
                [T('{item:essentialcraft5:cold_distillator}', '{item:essentialcraft5:cold_distillator}'),
                 T('{cfg:machines.cold_distillator.max_mru} MRU', '{cfg:machines.cold_distillator.max_mru} MRU')],
                [T('{item:essentialcraft5:sun_ray_absorber}', '{item:essentialcraft5:sun_ray_absorber}'),
                 T('{cfg:machines.sun_ray_absorber.max_mru} MRU', '{cfg:machines.sun_ray_absorber.max_mru} MRU')],
                [T('{item:essentialcraft5:ender_generator}', '{item:essentialcraft5:ender_generator}'),
                 T('{cfg:machines.ender_generator.max_mru} MRU', '{cfg:machines.ender_generator.max_mru} MRU')],
            ]},
            {'type': 'h', 'text': T('富集仓', 'Enrichment Chamber')},
            {'type': 'kv', 'rows': [
                [T('外壳每边', 'Shell side length'),
                 T('{cfg:machines.enrichment_chamber.min_size}~{cfg:machines.enrichment_chamber.max_size} 格',
                   '{cfg:machines.enrichment_chamber.min_size} to {cfg:machines.enrichment_chamber.max_size} blocks')],
                [T('控制器基础容量', 'Controller base capacity'),
                 T('{cfg:machines.enrichment_chamber.max_mru} MRU', '{cfg:machines.enrichment_chamber.max_mru} MRU')],
                [T('每个存储方块再加', 'Each holding block adds'),
                 T('{cfg:machines.enrichment_chamber.mru_per_storage} MRU', '{cfg:machines.enrichment_chamber.mru_per_storage} MRU')],
                [T('自动复查外壳', 'Shell re-check'),
                 T('每 {cfg:machines.enrichment_chamber.recheck_interval|sec}', 'every {cfg:machines.enrichment_chamber.recheck_interval|sec}')],
            ]},
            {'type': 'h', 'text': T('{item:essentialcraft5:mru_coil}', '{item:essentialcraft5:mru_coil}')},
            {'type': 'kv', 'rows': [
                [T('最大射程', 'Max range'),
                 T('{cfg:machines.mru_coil.max_radius} 格', '{cfg:machines.mru_coil.max_radius} blocks')],
                [T('每打中一个目标', 'Per target hit'),
                 T('{cfg:machines.mru_coil.mru_per_hit} MRU', '{cfg:machines.mru_coil.mru_per_hit} MRU')],
                {'if': 'cfg:machines.mru_coil.hurt_monsters', 'cells': [
                    T('打怪物每下', 'Damage to monsters'),
                    T('{cfg:machines.mru_coil.monster_damage} 点', '{cfg:machines.mru_coil.monster_damage}')]},
                {'if': 'cfg:machines.mru_coil.hurt_players', 'cells': [
                    T('打玩家每下', 'Damage to players'),
                    T('{cfg:machines.mru_coil.player_damage} + 最大生命的 {cfg:machines.mru_coil.player_health_percent|pct}',
                      '{cfg:machines.mru_coil.player_damage} + {cfg:machines.mru_coil.player_health_percent|pct} of max health')]},
            ]},
        ],
    },
}

# ---------------------------------------------------------------- 读旧数据

def load_keys():
    keys = {}
    for path in sorted(glob.glob(os.path.join(KEYS_DIR, '*.json'))):
        with io.open(path, encoding='utf-8') as f:
            for k, v in json.load(f).items():
                if k.startswith('guide.'):
                    keys[k] = dict(v)
    return keys


def apply_patches(keys):
    for key, lang, old, new in TEXT_PATCHES:
        text = keys[key][lang]
        if old not in text:
            sys.exit('TEXT_PATCHES 没命中（旧文字改过了？）：%s %s %r' % (key, lang, old))
        keys[key][lang] = text.replace(old, new, 1)


def load_old_categories():
    cats = []
    for path in sorted(glob.glob(os.path.join(OLD_DIR, '*.json'))):
        with io.open(path, encoding='utf-8') as f:
            data = json.load(f, object_pairs_hook=OrderedDict)
        data['_id'] = os.path.basename(path)[:-5]
        cats.append(data)
    cats.sort(key=lambda c: (c.get('order', 1000), c['_id']))
    return cats


# ---------------------------------------------------------------- 文字转换

def remap_link(target):
    return LINK_REMAP.get(target, target)


def inline(text):
    """**x** → <em>x</em>；{entry:a/b} → [[a/b]]；原文里的 < 与 [[ 转义"""
    if '<' in text:
        text = text.replace('<', BS + '<')
    if '[[' in text:
        text = text.replace('[[', BS + '[[')
    parts = text.split('**')
    if len(parts) % 2 == 0:
        raise ValueError('** 没有成对：' + text[:80])
    out = parts[0]
    for i in range(1, len(parts)):
        out += ('<em>' if i % 2 == 1 else '</em>') + parts[i]
    out = re.sub('[{]entry:([a-z0-9_/]+)[}]', lambda m: '[[' + remap_link(m.group(1)) + ']]', out)
    return out


def split_paragraphs(text):
    paras = []
    cur = []
    for line in text.split(NL):
        if line.strip() == '':
            if cur:
                paras.append(cur)
                cur = []
        else:
            cur.append(line.rstrip())
    if cur:
        paras.append(cur)
    return paras


def is_dot(line):
    return line.startswith('· ')


LABEL_RE = re.compile('^[*][*]([^*]+)[*][*] ?[：:] ?')


def label_of(lines):
    m = LABEL_RE.match(lines[0])
    return (m.group(1), m.end()) if m else (None, 0)


def segments(lines):
    """一段 → [(kind, [行…])]，kind = 'p' 或 'list'"""
    segs = []
    for line in lines:
        kind = 'list' if is_dot(line) else 'p'
        if segs and segs[-1][0] == kind:
            segs[-1][1].append(line)
        else:
            segs.append((kind, [line]))
    return segs


class Ctx:
    """一个条目转换时的状态：全书顺序里的下一篇、已经用掉的 lore 数、统计"""

    def __init__(self, entry_key, next_key, stats):
        self.entry_key = entry_key
        self.next_key = next_key
        self.lore = 0
        self.stats = stats


def rest_len(zh_para_text, label_end):
    rest = zh_para_text[label_end:]
    rest = re.sub('[{]entry:[^}]+[}]', '', rest)
    rest = re.sub('[，。、：；！？「」（）() ,.:;!?]', '', rest)
    rest = ''.join(rest.split())
    return len(rest)


def text_blocks(zh, en, ctx):
    """一段旧正文（中英）→ [(zh 块, en 块)]"""
    zp = split_paragraphs(zh)
    ep = split_paragraphs(en)
    if len(zp) != len(ep):
        raise ValueError('%s：中英段落数不一致（%d / %d）' % (ctx.entry_key, len(zp), len(ep)))
    out = []
    for zl, el in zip(zp, ep):
        if [is_dot(x) for x in zl] != [is_dot(x) for x in el]:
            raise ValueError('%s：中英列表行不一致：%r' % (ctx.entry_key, zl[0][:30]))
        zlabel, zend = label_of(zl)
        elabel, eend = label_of(el)
        ztext = NL.join(zl)
        if zlabel == '下一步':
            links = [remap_link(x) for x in re.findall('[{]entry:([a-z0-9_/]+)[}]', ztext)]
            if links == [ctx.next_key] and len(zl) == 1 and rest_len(ztext, zend) <= NEXT_DROP_MAX_REST:
                ctx.stats['next_dropped'] += 1
                continue
            out.append(tip_pair('info', TIP_TITLES['next'], zl, el, zend, eend))
            ctx.stats['next_tip'] += 1
            continue
        if zlabel == '做成的标志':
            out.append(tip_pair('good', TIP_TITLES['done'], zl, el, zend, eend))
            ctx.stats['done_tip'] += 1
            continue
        if zlabel == '小知识' and ctx.lore == 0:
            ctx.lore += 1
            out.append(tip_pair('lore', None, zl, el, zend, eend))
            ctx.stats['lore_tip'] += 1
            continue
        zsegs = segments(zl)
        esegs = segments(el)
        for i, ((zk, zlines), (ek, elines)) in enumerate(zip(zsegs, esegs)):
            if zk == 'list':
                out.append(({'type': 'list', 'items': [inline(x[2:].strip()) for x in zlines]},
                             {'type': 'list', 'items': [inline(x[2:].strip()) for x in elines]}))
                continue
            # 只有「**标签**：」一行、后面紧跟列表 → h（标签）
            if (i == 0 and len(zlines) == 1 and len(elines) == 1 and zlabel and elabel
                    and zend == len(zlines[0]) and eend == len(elines[0]) and len(zsegs) > 1):
                out.append(({'type': 'h', 'text': inline(zlabel)}, {'type': 'h', 'text': inline(elabel)}))
                ctx.stats['label_h'] += 1
                continue
            out.append(({'type': 'p', 'text': inline(NL.join(zlines))}, {'type': 'p', 'text': inline(NL.join(elines))}))
    return out


def tip_pair(style, title, zl, el, zend, eend):
    zt = NL.join([zl[0][zend:]] + zl[1:]).strip()
    et = NL.join([el[0][eend:]] + el[1:]).strip()
    if et[:1].islower():
        et = et[0].upper() + et[1:]     # 「**Done when**: the chamber…」去掉标签后句首大写
    zb = OrderedDict([('type', 'tip'), ('style', style)])
    eb = OrderedDict([('type', 'tip'), ('style', style)])
    if title:
        zb['title'] = title['zh']
        eb['title'] = title['en']
    zb['text'] = inline(zt)
    eb['text'] = inline(et)
    return zb, eb


# ---------------------------------------------------------------- 结构 / 仪式

def label_text(label, keys, lang):
    if label is None:
        return None
    if label.startswith('guide.' + MODID + '.'):
        return keys[label][lang]
    return label


def convert_key(key, keys, lang):
    out = OrderedDict()
    for ch, v in key.items():
        if isinstance(v, dict):
            nv = OrderedDict()
            for k2, v2 in v.items():
                nv[k2] = label_text(v2, keys, lang) if k2 == 'label' else v2
            out[ch] = nv
        else:
            out[ch] = v
    return out


def figure_block(page, keys, lang):
    t = page['type']
    b = OrderedDict([('type', t)])
    if t == 'items':
        b['items'] = list(page['items'])
        if page.get('per_row'):
            b['per_row'] = page['per_row']
    elif t == 'recipe':
        item = page.get('item')
        items = page.get('items')
        if isinstance(item, list):
            items = list(item) + list(items or [])
            item = None
        if item:
            b['item'] = item
        if items:
            b['items'] = list(items)
        if page.get('recipe'):
            b['recipe'] = page['recipe']
        if page.get('recipe_type'):
            b['recipe_type'] = page['recipe_type']
        if page.get('compact'):
            b['compact'] = True
        if page.get('optional'):
            b['optional'] = True
    elif t in ('ritual', 'structure'):
        if 'structure' in page:
            b['structure'] = page['structure']
        if 'key' in page:
            b['key'] = convert_key(page['key'], keys, lang)
        if 'anchor' in page:
            b['anchor'] = page['anchor']
        if 'pattern' in page:
            b['pattern'] = list(page['pattern'])
        if 'layers' in page:
            layers = []
            for layer in page['layers']:
                nl = OrderedDict()
                nl['pattern'] = list(layer['pattern'])
                if 'key' in layer:
                    nl['key'] = convert_key(layer['key'], keys, lang)
                if 'label' in layer:
                    nl['label'] = label_text(layer['label'], keys, lang)
                layers.append(nl)
            b['layers'] = layers
        for k in ('throw', 'result', 'chance'):
            if k in page:
                b[k] = page[k]
    elif t == 'image':
        b['texture'] = page['texture']
        b['width'] = page.get('width', 256)
        b['height'] = page.get('height', 256)
    else:
        raise ValueError('不认识的页类型 ' + t)
    return b


# ---------------------------------------------------------------- 条目 / 分类

def convert_entry(entry, new_id, keys, ctx):
    """→ (zh 条目, en 条目)"""
    res = {}
    blocks = {'zh': [], 'en': []}
    for page in entry['pages']:
        kind = page.get('type', 'text')
        page_blocks = []
        title = page.get('title')
        if kind == 'text' and title and keys[title]['zh'] == '小知识' and ctx.lore == 0:
            # 整页小知识 → 一个 lore 提示框（段落之间换行）；页里带标签的段（下一步、做成的标志）照常另起块
            ctx.lore += 1
            ctx.stats['lore_tip'] += 1
            zp = split_paragraphs(keys[page['text']]['zh'])
            ep = split_paragraphs(keys[page['text']]['en'])
            keep = [i for i, p in enumerate(zp) if label_of(p)[0] not in ('下一步', '做成的标志')]
            rest = [i for i in range(len(zp)) if i not in keep]
            zt = inline(NL.join(NL.join(zp[i]) for i in keep))
            et = inline(NL.join(NL.join(ep[i]) for i in keep))
            blocks['zh'].append(OrderedDict([('type', 'tip'), ('style', 'lore'), ('text', zt)]))
            blocks['en'].append(OrderedDict([('type', 'tip'), ('style', 'lore'), ('text', et)]))
            if rest:
                zr = (NL + NL).join(NL.join(zp[i]) for i in rest)
                er = (NL + NL).join(NL.join(ep[i]) for i in rest)
                for zb, eb in text_blocks(zr, er, ctx):
                    blocks['zh'].append(zb)
                    blocks['en'].append(eb)
            continue
        if title:
            page_blocks.append(({'type': 'h', 'text': inline(keys[title]['zh'])}, {'type': 'h', 'text': inline(keys[title]['en'])}))
        if kind != 'text':
            page_blocks.append((figure_block(page, keys, 'zh'), figure_block(page, keys, 'en')))
            ctx.stats['figure_' + kind] += 1
        if page.get('text'):
            k = page['text']
            page_blocks.extend(text_blocks(keys[k]['zh'], keys[k]['en'], ctx))
        # 文字页的正文全被删光（只剩「下一步」）时，标题也不要了
        if kind == 'text' and all(b[0]['type'] == 'h' for b in page_blocks):
            page_blocks = []
        for zb, eb in page_blocks:
            blocks['zh'].append(zb)
            blocks['en'].append(eb)
    for lang in ('zh', 'en'):
        e = OrderedDict()
        e['id'] = new_id
        if entry.get('title'):
            e['title'] = inline(keys[entry['title']][lang])
        else:
            e['title'] = '{item:' + entry['icon'] + '}'
        e['icon'] = entry['icon']
        e['summary'] = inline(keys[entry['description']][lang]) if entry.get('description') else ''
        for k in ('complete', 'optional', 'advancement'):
            if k in entry:
                e[k] = entry[k]
        if entry.get('items'):
            e['items'] = list(entry['items'])
        e['blocks'] = blocks[lang]
        res[lang] = e
    return res['zh'], res['en']


def new_entry(eid, spec):
    res = {}
    for lang in ('zh', 'en'):
        e = OrderedDict()
        e['id'] = eid
        e['title'] = spec['title'][lang]
        e['icon'] = spec['icon']
        e['summary'] = spec['summary'][lang]
        e['blocks'] = [pick(b, lang) for b in spec['blocks']]
        res[lang] = e
    return res['zh'], res['en']


def pick(node, lang):
    """NEW_ENTRIES 里 {'zh':…, 'en':…} 取一种语言，其余原样递归"""
    if isinstance(node, dict):
        if set(node.keys()) == {'zh', 'en'}:
            return node[lang]
        out = OrderedDict()
        for k, v in node.items():
            out[k] = pick(v, lang)
        return out
    if isinstance(node, list):
        return [pick(x, lang) for x in node]
    return node


def group_of(zh_title):
    if zh_title.startswith('第') and '章' in zh_title[:6]:
        return 'main'
    if zh_title.startswith('支线'):
        return 'side'
    raise ValueError('分不出主线 / 支线：' + zh_title)


def category_header(cid, order, group, title, subtitle, icon, advancement=None):
    c = OrderedDict()
    c['id'] = cid
    c['order'] = order
    c['group'] = group
    c['title'] = title
    c['subtitle'] = subtitle
    c['icon'] = icon
    if advancement:
        c['advancement'] = advancement
    c['_draft'] = True
    c['entries'] = []
    return c


def main():
    force = '--force' in sys.argv[1:]
    for lang, _ in LANGS:
        d = os.path.join(OLD_DIR, lang)
        if os.path.isdir(d) and glob.glob(os.path.join(d, '*.json')) and not force:
            sys.exit('%s 下已经有内容文件，不覆盖（重跑请加 --force，会冲掉第二段的改写）' % d)
    keys = load_keys()
    apply_patches(keys)
    old = load_old_categories()
    by_id = {c['_id']: c for c in old}
    basics = by_id.pop('basics')
    chapters = [c for c in old if c['_id'] != 'basics']

    # 新的全书顺序（start 在最前），给「下一步」判断用
    order = []
    for item in START_ORDER:
        order.append('start/' + (item if isinstance(item, str) else item[0]))
    for c in chapters:
        for e in c['entries']:
            order.append(c['_id'] + '/' + e['id'])
    next_of = {order[i]: order[i + 1] for i in range(len(order) - 1)}

    stats = {k: 0 for k in ('next_dropped', 'next_tip', 'done_tip', 'lore_tip', 'label_h',
                            'figure_items', 'figure_recipe', 'figure_ritual', 'figure_structure', 'figure_image')}
    out = {}

    # 入门
    zc = category_header('start', START_CATEGORY['order'], 'start', START_CATEGORY['title']['zh'],
                         START_CATEGORY['subtitle']['zh'], START_CATEGORY['icon'])
    ec = category_header('start', START_CATEGORY['order'], 'start', START_CATEGORY['title']['en'],
                         START_CATEGORY['subtitle']['en'], START_CATEGORY['icon'])
    basics_entries = {e['id']: e for e in basics['entries']}
    for item in START_ORDER:
        if isinstance(item, str):
            ze, ee = new_entry(item, NEW_ENTRIES[item])
        else:
            new_id, old_id = item
            key = 'start/' + new_id
            ze, ee = convert_entry(basics_entries[old_id], new_id, keys, Ctx(key, next_of.get(key), stats))
        zc['entries'].append(ze)
        ec['entries'].append(ee)
    if set(basics_entries) != {x[1] for x in START_ORDER if not isinstance(x, str)}:
        sys.exit('basics 里有没安排去处的条目：' + ', '.join(sorted(basics_entries)))
    out['start'] = (zc, ec)

    for c in chapters:
        cid = c['_id']
        ztitle = keys[c['title']]['zh']
        group = group_of(ztitle)
        icon = CATEGORY_ICON_PATCHES.get(cid, c['icon'])
        zc = category_header(cid, c['order'], group, inline(ztitle), inline(keys[c['description']]['zh']), icon, c.get('advancement'))
        ec = category_header(cid, c['order'], group, inline(keys[c['title']]['en']), inline(keys[c['description']]['en']), icon, c.get('advancement'))
        for e in c['entries']:
            key = cid + '/' + e['id']
            ze, ee = convert_entry(e, e['id'], keys, Ctx(key, next_of.get(key), stats))
            zc['entries'].append(ze)
            ec['entries'].append(ee)
        out[cid] = (zc, ec)

    written = 0
    for cid, (zc, ec) in out.items():
        for (lang, _), data in zip(LANGS, (zc, ec)):
            d = os.path.join(OLD_DIR, lang)
            os.makedirs(d, exist_ok=True)
            path = os.path.join(d, cid + '.json')
            with io.open(path, 'w', encoding='utf-8', newline=NL) as f:
                f.write(json.dumps(data, ensure_ascii=False, indent=2) + NL)
            with io.open(path, encoding='utf-8') as f:
                json.load(f)
            written += 1
    entries = sum(len(zc['entries']) for zc, _ in out.values())
    print('写了 %d 个文件（%d 个分类 × 2 种语言），%d 个条目' % (written, len(out), entries))
    for k, v in stats.items():
        print('  %s: %d' % (k, v))


if __name__ == '__main__':
    main()
