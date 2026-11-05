"""一次性：导出手册附录 from_ec4/where 的候选表。

按原作研究书（common/registry/ResearchRegistry.java）的五大类，把每个条目引用到的物品 / 方块
对上命名表（tools/id_map_*.json），列出原作中英文名和现在的名字，标出改了名的、去掉的。
where 篇的表是从这份候选里人工挑的（附录不是名字表，只收改了名、换了位置或玩法变了的）。

用法：python tools/guide/ec4_where_candidates.py [--ec4 原作仓库目录] [--out 输出.tsv]
原作仓库默认 ../EssentialCraft4Unofficial（只读）。不进 jar。
"""
import argparse
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# 原作研究书的分类：注册函数名 → 原作语言文件里的分类键
CATEGORIES = {
    'Basic': 'essentialcraft.basic',
    'Mru': 'essentialcraft.mru',
    'Engineers': 'essentialcraft.eng',
    'Hoanna': 'essentialcraft.hoanna',
    'Ender': 'essentialcraft.ender',
}

FIELD_ASSIGN = re.compile(r'^\s*(\w+(?:\[\d+\])?)\s*=\s*(.*)$')
REGISTER = re.compile(r'register(?:Item|Block)Simple\(\s*(?:new ItemBlock\w*\(\s*)?(new [^,]+?\)|[\w\[\]]+)\)?\s*,\s*"([^"]+)"')


def field_names(path):
    """字段名 → 原作注册名（小写，和命名表的 old_id 一致）。"""
    out = {}
    pending = None
    for line in open(path, encoding='utf-8'):
        m = REGISTER.search(line)
        a = FIELD_ASSIGN.match(line)
        if m:
            target, name = m.group(1), m.group(2).lower()
            if target.startswith('new '):
                if a:
                    out[a.group(1).split('[')[0]] = name
            else:
                out.setdefault(target.split('[')[0], name)
            pending = None
        elif a:
            pending = a.group(1)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--ec4', default=os.path.join(os.path.dirname(ROOT), 'EssentialCraft4Unofficial'))
    ap.add_argument('--out')
    args = ap.parse_args()

    java = os.path.join(args.ec4, 'src/main/java/essentialcraft/common')
    fields = {
        'ItemsCore': field_names(os.path.join(java, 'item/ItemsCore.java')),
        'BlocksCore': field_names(os.path.join(java, 'block/BlocksCore.java')),
    }
    zh = json.load(open(os.path.join(ROOT, 'tools/legacy_lang/zh_cn.json'), encoding='utf-8'))
    en = json.load(open(os.path.join(ROOT, 'tools/legacy_lang/en_us.json'), encoding='utf-8'))
    rows = []
    for f in ('tools/id_map_items.json', 'tools/id_map_blocks.json'):
        rows += json.load(open(os.path.join(ROOT, f), encoding='utf-8'))
    by_old = {}
    for r in rows:
        if r['old_id']:
            by_old.setdefault(r['old_id'], []).append(r)

    out = io.StringIO()
    out.write('分类\t原作条目\t原作条目(en)\told_id:meta\tnew_id\t状态\t原作中文名\t现中文名\t原作英文名\t现英文名\t改了名\n')
    seen = set()
    cat = disc = None
    local_stacks = {}
    for line in open(os.path.join(java, 'registry/ResearchRegistry.java'), encoding='utf-8'):
        m = re.search(r'public static void register(\w+)Category', line)
        if m:
            cat = m.group(1)
            disc = None
            continue
        # 分类开头预先构造的物品（例如武器台桌页的四把枪），按变量名在后面的页里认
        m = re.search(r'ItemStack (\w+) = new ItemStack\((ItemsCore|BlocksCore)\.(\w+)', line)
        if m and disc is None:
            local_stacks[m.group(1)] = '%s.%s' % (m.group(2), m.group(3))
            continue
        if cat not in CATEGORIES:
            continue
        m = re.search(r'new DiscoveryEntry\("([^"]+)"\)', line)
        if m:
            disc = m.group(1)
        if disc is None:
            continue
        # gen(n) 是 ResearchRegistry 自己的简写：new ItemStack(ItemsCore.genericItem, 1, n)
        line = re.sub(r'\bgen\((\d+)\)', r'ItemsCore.genericItem, 1, \1', line)
        for name, ref in local_stacks.items():
            if re.search(r'\(%s\)' % name, line):
                line += ' ' + ref
        for r in re.finditer(r'(BlocksCore|ItemsCore)\.(\w+)(?:\s*,\s*1\s*,\s*(\d+))?', line):
            old = fields[r.group(1)].get(r.group(2))
            if old is None:
                continue
            meta = int(r.group(3)) if r.group(3) else None
            cands = [x for x in by_old.get(old, []) if meta is None or x['meta'] in (meta, None)]
            if meta is None and len(cands) > 1:
                cands = [x for x in cands if x['meta'] in (0, None)] or cands[:1]
            for x in cands:
                key = (cat, x['old_id'], x['meta'])
                if key in seen:
                    continue
                seen.add(key)
                k = 'ec3book.discovery_%s.name' % disc
                zl = zh.get(x['old_key'] or '', x['zh_legacy'] or '')
                el = en.get(x['old_key'] or '', '')
                renamed = (zl or '').replace(' ', '') != (x['zh'] or '').replace(' ', '') or (el and el != x['en'])
                out.write('\t'.join(str(v) for v in (
                    zh.get('ec3book.category_%s.name' % CATEGORIES[cat], cat), zh.get(k, disc), en.get(k, disc),
                    '%s:%s' % (x['old_id'], x['meta']), x['new_id'], x['status'], zl, x['zh'], el, x['en'],
                    'Y' if renamed else '')) + '\n')

    # 研究书里一处都没提到、现在照常注册的物品（原作研究书里没写的）
    mentioned = {(k[1], k[2]) for k in seen}
    for r in rows:
        if r['old_id'] and r['status'] != 'removed' and r['new_id'] and (r['old_id'], r['meta']) not in mentioned:
            zl = zh.get(r['old_key'] or '', r['zh_legacy'] or '')
            el = en.get(r['old_key'] or '', '')
            out.write('\t'.join(str(v) for v in (
                '（研究书里没写）', '', '', '%s:%s' % (r['old_id'], r['meta']), r['new_id'], r['status'],
                zl, r['zh'], el, r['en'], '')) + '\n')

    text = out.getvalue()
    if args.out:
        open(args.out, 'w', encoding='utf-8').write(text)
    else:
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
        sys.stdout.write(text)


if __name__ == '__main__':
    main()
