# -*- coding: utf-8 -*-
# 第二段改写的预处理（方案 tools/plan/guide_kuvalich.md §3.4，GD-10）：按条目列出改写用的输入清单，不改工程文件。
#
#   python tools/guide/prep_rewrite.py [分类id…] [--out 目录]
#   默认输出到系统临时目录下的 ec5_guide_prep/（改写时一起用，不进工程）：每个分类一个 <分类>.md，外加 all.json（同样内容，机器读）。
#
# 清单两部分：
#   1. {cfg} 候选：正文里的字面数 N 等于某个配置项的默认值（也算换算后的：N 秒 = 默认 tick ÷ 20，N% = 小数默认 × 100）。
#      「相关小节」排在前面：条目 items / icon 的注册名 → registry/EC*.java 里的注册语句 → 语句里的类 → 类文件里 push("…") 的小节；
#      找不到相关小节时退回全部配置键，按和条目 id / 物品名的相似度排序，只列前几个。巧合命中（「3 格」）要改写时逐条确认，
#      或者判定是代码里写死的常量（在 _src 里注明）。
#   2. _src 桩：相关类文件的相对路径（相对 com/flameforge/essentialcraft5）+ 条目标题，配置项的定义处写到行号。改写时补上行号和说明。
#
# 读 datagen 导出的 config_keys.json（没有就先跑 runData）。0、1 这两个数不列（太多巧合）。
# 本文件刻意不写反斜杠（Write / Bash 工具会吃掉一层），需要时用 chr(92) / chr(10)。
import io
import json
import os
import re
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import guide_lib as L  # noqa: E402

NL = L.NL
FALLBACK_TOP = 4        # 没有相关小节时，每个数最多列几个候选
FALLBACK_MIN = 10       # 没有相关小节时只给不小于这个数的字面数找候选（2、3 这种小数到处都能撞上）
LANG_ZH = L.load_lang('zh_cn')


def raw_java(rel):
    try:
        with io.open(os.path.join(L.JAVA_PKG, rel), encoding='utf-8') as f:
            return f.read()
    except OSError:
        return ''


_DEF_CACHE = {}


def key_definition(key):
    """配置项的定义处：(相对路径:行号, 注释)。按「push("小节") 之后第一次出现 "项名"」找"""
    if key in _DEF_CACHE:
        return _DEF_CACHE[key]
    section, item = key.rsplit('.', 1)
    sec = section.rsplit('.', 1)[-1]
    found = (None, None)
    for rel in L.java_sources():
        src = raw_java(rel)
        p = src.find('push("' + sec + '")')
        if p < 0:
            continue
        q = src.find('"' + item + '"', p)
        if q < 0:
            continue
        line = src.count(NL, 0, q) + 1
        # 注释：定义前面最近的 .comment( … ) 里的字符串
        head = src[max(p, q - 600):q]
        c = head.rfind('.comment(')
        comment = None
        if c >= 0:
            esc = L.BS + L.BS   # 正则里的一个反斜杠
            lits = re.findall('"((?:[^"' + esc + ']|' + esc + '.)*)"', head[c:])
            if lits:
                comment = ' / '.join(lits)
        found = ('%s:%d' % (rel, line), comment)
        break
    _DEF_CACHE[key] = found
    return found


def context(text, raw, nth):
    """字面数在正文里第 nth 次出现处的上下文（物品名展开）"""
    t = L.readable(text, LANG_ZH)
    i = -1
    for _ in range(nth + 1):
        i = t.find(raw, i + 1)
        if i < 0:
            return t[:40].replace(NL, ' ')
    return t[max(0, i - 16):i + len(raw) + 12].replace(NL, ' ')


def entry_texts(en):
    for bi, b in enumerate(en.get('blocks') or []):
        if not isinstance(b, dict):
            continue
        for k in ('text', 'title', 'caption'):
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


def prep_entry(cat, en, cfg):
    sections, files = L.related_sections(en, cfg)
    related = [k for k in cfg if k.rsplit('.', 1)[0] in sections]
    words = [en.get('id', '')] + [i.split(':', 1)[-1] for i in (en.get('items') or []) if isinstance(i, str)]
    fallback = L.keys_by_similarity(words, cfg) if not related else []
    cands = []
    seen = set()
    for bi, text in entry_texts(en):
        count = {}
        for value, raw, after in L.literal_numbers(text):
            nth = count.get(raw, 0)
            count[raw] = nth + 1
            if value in (0, 1):
                continue
            hits = []
            for k in related:
                sug = L.number_matches(value, after, k, cfg[k])
                if sug:
                    hits.append((k, sug, True))
            if not hits and value >= FALLBACK_MIN:
                for k in fallback:
                    sug = L.number_matches(value, after, k, cfg[k])
                    if sug:
                        hits.append((k, sug, False))
                        if len(hits) >= FALLBACK_TOP:
                            break
            ctx = context(text, raw, nth)
            for k, sug, rel in hits:
                if (bi, ctx, k) in seen:
                    continue
                seen.add((bi, ctx, k))
                where, comment = key_definition(k)
                cands.append({'block': bi, 'literal': raw, 'context': ctx, 'key': k,
                              'default': cfg[k].get('default'), 'suggest': sug, 'related': rel,
                              'defined_at': where, 'comment': comment or cfg[k].get('comment')})
    title = L.readable(en.get('title', ''), LANG_ZH)
    src_stub = ['%s — %s' % (f, title) for f in files]
    for k in related:
        where, _ = key_definition(k)
        if where and not any(s.startswith(where.split(':')[0] + ':') for s in src_stub):
            src_stub.append('%s — 配置 %s' % (where, k.rsplit('.', 1)[0]))
    return {'entry': '%s/%s' % (cat, en.get('id')), 'title': title, 'sections': sorted(sections),
            'src_stub': src_stub, 'cfg_candidates': cands}


def to_md(cat, items):
    out = ['# %s：改写输入清单' % cat, '',
           '由 tools/guide/prep_rewrite.py 生成。候选只是「数字碰巧等于默认值」，逐条确认单位与含义；代码常量在 _src 里注明。', '']
    for it in items:
        out.append('## %s（%s）' % (it['entry'], it['title']))
        out.append('')
        out.append('- 相关配置小节：' + ('、'.join(it['sections']) if it['sections'] else '（没找到，候选按名字相似度退回全部配置）'))
        out.append('- _src 桩：')
        for s in it['src_stub'] or ['（没找到相关类，自己按物品注册名去 registry/ 找）']:
            out.append('  - `%s`' % s)
        if it['cfg_candidates']:
            out.append('- {cfg} 候选：')
            out.append('')
            out.append('| 块 | 字面数 | 上下文 | 建议 | 默认值 | 定义处 | 注释 |')
            out.append('|---|---|---|---|---|---|---|')
            for c in it['cfg_candidates']:
                out.append('| %d | %s | %s | `%s`%s | %s | %s | %s |' % (
                    c['block'], c['literal'], c['context'].replace('|', '/'), c['suggest'], '' if c['related'] else '（非相关小节）',
                    c['default'], c['defined_at'] or '?', (c['comment'] or '').replace('|', '/').replace(NL, ' / ')))
        else:
            out.append('- {cfg} 候选：无')
        out.append('')
    return NL.join(out) + NL


def main():
    args = sys.argv[1:]
    out_dir = os.path.join(tempfile.gettempdir(), 'ec5_guide_prep')
    if '--out' in args:
        i = args.index('--out')
        out_dir = args[i + 1]
        del args[i:i + 2]
    only = set(args)
    cfg = L.load_config_keys()
    if cfg is None:
        sys.exit('没有 %s：先跑一次 runData' % L.CONFIG_KEYS_FILE)
    os.makedirs(out_dir, exist_ok=True)
    everything = {}
    total = 0
    for path in sorted(os.listdir(os.path.join(L.GUIDE, 'zh_cn'))):
        if not path.endswith('.json'):
            continue
        cat = path[:-5]
        if only and cat not in only:
            continue
        with io.open(os.path.join(L.GUIDE, 'zh_cn', path), encoding='utf-8') as f:
            data = json.load(f)
        items = [prep_entry(cat, en, cfg) for en in data.get('entries') or [] if isinstance(en, dict)]
        everything[cat] = items
        total += sum(len(it['cfg_candidates']) for it in items)
        with io.open(os.path.join(out_dir, cat + '.md'), 'w', encoding='utf-8', newline=NL) as f:
            f.write(to_md(cat, items))
    with io.open(os.path.join(out_dir, 'all.json'), 'w', encoding='utf-8', newline=NL) as f:
        f.write(json.dumps(everything, ensure_ascii=False, indent=1) + NL)
    print('写到 %s：%d 个分类，%d 个 {cfg} 候选' % (out_dir, len(everything), total))


if __name__ == '__main__':
    main()
