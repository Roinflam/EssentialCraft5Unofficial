# -*- coding: utf-8 -*-
"""注册完整性检查：命名表（tools/id_map_items.json、id_map_blocks.json）对照 runData 生成的 en_us.json。

列出三类：
  1. 命名表里没标 removed、却没注册的（按 status 分组）——移植还差什么；
  2. 注册了、却不在命名表里的——新加的东西忘了登记命名表；
  3. 命名表标了 removed、却注册了的——报错（删掉的东西又出现了）。

「注册了」= 生成的 en_us.json 里有 block.essentialcraft5.<名> 或 item.essentialcraft5.<名>（datagen 按注册表逐个出名字，
方块的物品形态用方块的键）。改了注册要先跑 runData 再跑这个脚本。

用法：
  python tools/check_registration.py                     # 只报告；第 3 类非空时退出码 1
  python tools/check_registration.py --expect-complete   # 第 1 类非空也退出码 1（全部批次移植完以后用）
"""
import argparse
import collections
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAPS = [os.path.join(ROOT, 'tools', 'id_map_items.json'), os.path.join(ROOT, 'tools', 'id_map_blocks.json')]
LANG = os.path.join(ROOT, 'src', 'generated', 'resources', 'assets', 'essentialcraft5', 'lang', 'en_us.json')
MODID = 'essentialcraft5'


def load(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def registered_names(lang):
    names = {}
    for key in lang:
        parts = key.split('.')
        if len(parts) == 3 and parts[1] == MODID and parts[0] in ('block', 'item'):
            names.setdefault(parts[2], parts[0])
    return names


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--expect-complete', action='store_true', help='命名表里没 removed 的都必须已注册')
    args = parser.parse_args()
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')

    if not os.path.exists(LANG):
        print('找不到生成的 en_us.json，先跑 runData：' + LANG)
        return 2
    registered = registered_names(load(LANG))

    # 同一个新名可能在命名表里出现多次（原作一个 id 带多个 meta、或方块 / 物品两张表都写了）：有一条不是 removed 就算要移植
    wanted = {}
    removed = {}
    for path in MAPS:
        table = os.path.basename(path)
        for row in load(path):
            name = row.get('new_id')
            if not name:
                continue
            if row.get('status') == 'removed':
                removed.setdefault(name, (table, row.get('reason') or ''))
            else:
                wanted.setdefault(name, (table, row.get('status')))
    for name in wanted:
        removed.pop(name, None)

    missing = collections.defaultdict(list)
    for name, (table, status) in sorted(wanted.items()):
        if name not in registered:
            missing[status].append(f'{name}（{table}）')
    extra = sorted(n for n in registered if n not in wanted and n not in removed)
    revived = sorted(f'{n}（{removed[n][0]}：{removed[n][1]}）' for n in registered if n in removed)

    blocks = sum(1 for k in registered.values() if k == 'block')
    items = len(registered) - blocks
    done = sum(1 for n in wanted if n in registered)
    print(f'已注册 {len(registered)} 个（方块 {blocks}、物品 {items}）；命名表要移植 {len(wanted)} 个，已注册 {done} 个'
          f'（{done * 100.0 / max(1, len(wanted)):.1f}%）；标了 removed 的 {len(removed)} 个')

    print(f'\n[1] 没 removed 却没注册：{sum(len(v) for v in missing.values())} 个')
    for status, names in sorted(missing.items()):
        print(f'  status={status}（{len(names)}）')
        for n in names:
            print('    ' + n)
    print(f'\n[2] 注册了却不在命名表：{len(extra)} 个')
    for n in extra:
        print('    ' + n)
    print(f'\n[3] 标了 removed 却注册了：{len(revived)} 个')
    for n in revived:
        print('    ' + n)

    failed = bool(revived) or (args.expect_complete and any(missing.values()))
    print('\n结果：' + ('有问题' if failed else '通过'))
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
