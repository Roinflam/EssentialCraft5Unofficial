# -*- coding: utf-8 -*-
# 扫描研究手册正文里的「元词」：玩家不该在书里读到服务器 / 配置 / 同步之类的字眼（数值跟配置走是后台的事，单人游戏也会读到）。
# 用法：python tools/guide/meta_scan.py [分类id ...]    占位符 {…}、链接目标、if、optional、id、_ 开头的字段不算。必须 0 命中。
# 另外扫语言文件里手册界面的字样（guide.essentialcraft5.*、研究手册物品提示），它们同样显示给玩家。
# 另有一项警告（不算命中、不影响退出码）：optional 恒为 true 的步骤，首段写了「要用 / 要做完 / 必须」却没有「可选」字样——
# 读者会以为非做不可，首页却从不把它指成「下一步」。真要做的改成必做或按条件可选（FORMAT.md §1.2），真可选的在首段写明「可选」。
# 从 KuvaLich 的 meta_scan.py 移植；本文件刻意不写反斜杠（Write / Bash 工具会吃掉一层）。
import glob
import io
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import guide_lib as L  # noqa: E402

ZH = re.compile('服务端|服务器|本服|服主|配置|同步|单人')
EN = re.compile('(?i)server|config|single-?player|sync')
# 繁体（zh_tw / zh_hk 由 make_traditional.py 生成）：同一批词的繁体写法，外加台港用语
ZHT = re.compile('服務端|服务端|伺服器|服務器|本服|服主|配置|同步|單人|单人|設定檔')
PATTERNS = (('zh_cn', ZH), ('en_us', EN), ('zh_tw', ZHT), ('zh_hk', ZHT))
TOKEN = re.compile('[{][^}]*[}]')
# 链接目标（[[分类/条目|  或  [[分类/条目]]）是 id，不是给玩家看的字
LINK_TARGET = re.compile(re.escape('[[') + '[a-z0-9_/]+' + '([|]|' + re.escape(']]') + ')')
# 不是给玩家看的字段
SKIP = {'if', 'optional', 'id', 'icon', 'type', 'style', 'order', 'group', 'complete', 'advancement', 'item', 'recipe',
        'recipe_type', 'structure', 'pattern', 'anchor', 'throw', 'result', 'texture'}
# 「可选却写要用」：首段（第一个 p 块）里的这些字样，同时没有 OPTIONAL_WORDS
NEEDED_WORDS = {'zh_cn': re.compile('要用|要做完|必须'), 'en_us': re.compile('(?i)(?<!as )needed|required|must')}   # 「as needed」是「按需」，不算
OPTIONAL_WORDS = {'zh_cn': re.compile('可选'), 'en_us': re.compile('(?i)optional')}
# 旧手册（引擎切换前）的内容键 guide.essentialcraft5.<旧分类>.*：文字已经搬进 guide/<语言>/，切换完成后这些键整批删除，这里先不扫
OLD_CATEGORIES = {os.path.basename(f)[:-5] for f in glob.glob(os.path.join(L.GUIDE, '*.json'))}


def walk(x, path, hits, pat, parent_type=None):
    if isinstance(x, str):
        t = LINK_TARGET.sub('[[', TOKEN.sub('', x))
        for m in pat.finditer(t):
            a = max(0, m.start() - 12)
            hits.append((path, t[a:m.end() + 12]))
    elif isinstance(x, list):
        for i, y in enumerate(x):
            walk(y, path + '[%d]' % i, hits, pat, parent_type)
    elif isinstance(x, dict):
        btype = x.get('type', parent_type)
        for k, v in x.items():
            if k.startswith('_') or k in SKIP:
                continue
            if k == 'items' and btype not in ('list', 'steps'):
                continue        # 条目 / items 块 / 配方块的物品 id 表
            walk(v, path + '.' + k, hits, pat, btype)


def optional_but_needed(lang, cat, d):
    """optional 恒为 true（写的是布尔 true）的步骤：首段写了「要用 / 要做完 / 必须」又没写「可选」的，返回 [(条目, 片段)]"""
    need, opt = NEEDED_WORDS.get(lang), OPTIONAL_WORDS.get(lang)
    if need is None:
        return []
    out = []
    for e in d.get('entries', []):
        if e.get('optional') is not True or not e.get('complete'):
            continue
        first = next((b for b in e.get('blocks') or [] if isinstance(b, dict) and b.get('type') == 'p'), None)
        text = LINK_TARGET.sub('[[', TOKEN.sub('', first.get('text') or '')) if first else ''
        m = need.search(text)
        if m and not opt.search(text):
            a = max(0, m.start() - 12)
            out.append((e.get('id', '?'), text[a:m.end() + 12]))
    return out


def ui_key(k):
    if k.startswith('tooltip.' + L.MODID + '.research_book') or k == 'item.' + L.MODID + '.research_book':
        return True
    if not k.startswith('guide.' + L.MODID + '.'):
        return False
    seg = k[len('guide.' + L.MODID + '.'):].split('.', 1)[0]
    return seg not in OLD_CATEGORIES


def main():
    only = set(sys.argv[1:])
    total = 0
    warns = 0
    for lang, pat in PATTERNS:
        for f in sorted(glob.glob(os.path.join(L.GUIDE, lang, '*.json'))):
            cat = os.path.basename(f)[:-5]
            if only and cat not in only:
                continue
            with io.open(f, encoding='utf-8') as fh:
                d = json.load(fh)
            hits = []
            for e in d.get('entries', []):
                walk(e, e.get('id', '?'), hits, pat)
            walk({k: v for k, v in d.items() if k != 'entries'}, '(category)', hits, pat)
            for p, ctx in hits:
                print('%s/%s %s: ...%s...' % (lang, cat, p, ctx))
            total += len(hits)
            for eid, ctx in optional_but_needed(lang, cat, d):
                print('WARN %s/%s/%s: 可选步骤（optional: true）的首段写着「要用 / 必须」却没写「可选」: ...%s...' % (lang, cat, eid, ctx))
                warns += 1
    if not only:
        # 界面字样：datagen 的源文件 tools/lang/keys/*.json（中英），以及繁体语言文件
        for f in sorted(glob.glob(os.path.join(L.ROOT, 'tools', 'lang', 'keys', '*.json'))):
            with io.open(f, encoding='utf-8') as fh:
                d = json.load(fh)
            for k, v in d.items():
                if not ui_key(k) or not isinstance(v, dict):
                    continue
                for lang, pat in (('zh', ZH), ('en', EN)):
                    s = v.get(lang) or ''
                    for m in pat.finditer(s):
                        a = max(0, m.start() - 12)
                        print('keys/%s %s（%s）: ...%s...' % (os.path.basename(f), k, lang, s[a:m.end() + 12]))
                        total += 1
        for lang, pat in PATTERNS[2:]:
            f = os.path.join(L.ASSETS, 'lang', lang + '.json')
            if not os.path.exists(f):
                continue
            with io.open(f, encoding='utf-8') as fh:
                d = json.load(fh)
            for k, v in d.items():
                if not ui_key(k) or not isinstance(v, str):
                    continue
                for m in pat.finditer(v):
                    a = max(0, m.start() - 12)
                    print('lang/%s %s: ...%s...' % (lang, k, v[a:m.end() + 12]))
                    total += 1
    print('== %d hits%s' % (total, '；%d 条警告（不算命中）' % warns if warns else ''))
    sys.exit(1 if total else 0)


if __name__ == '__main__':
    main()
