# -*- coding: utf-8 -*-
"""研究手册老存档迁移表：生成 data/essentialcraft5/guide_migration/v<N>.json（读表的是 player/GuideMigration，规则见 batch10.md §2.6）

做什么
  主线加了新的必做步骤以后，已经走过那一段的老玩家登录时要补上这些步骤的勾（只补勾，不给物品）。
  手册在客户端资源里、服务端读不到，所以由本脚本从 zh_cn 手册算出迁移表，生成结果进仓库。

  要迁移的步骤 = 主线里的必做步骤（按默认配置算，海纳尔之门开着；optional 写条件的照引擎同一套求值），
                 去掉基线（baseline_steps.txt：4dacc01 时的 64 个必做步骤）和更早版本迁移表里已经补过的。
  每一步的「证明进度」（老玩家有任一个就补）：
    - 一般：排在它所在章之后的所有主线必做步骤的进度，加上排在那之后的 main/* 进度；
    - 过门规则：门开着才必做、门关着时可选或藏掉、又排在过门那一步（GATE_STEP，第十六章 open_gate）之前的步骤——
      风之药水、受眷顾、心愿、号角、大法师、风之石板、风之真名这一类。老存档里它们原来是可选或根本没有，走到第十四、十五章的人
      多半跳过了；风之石板是开门的前提，过了门才说明他走完了这一段，所以证明进度从过门那一步算起（含它）。
    - NEW_CONTENT 里的章是整章新加的内容，不进表（已经毕业的人照常从首页看到它们）；EXPLICIT 写明的步骤例外，证明进度照写的
      （写成 (LATER, [...]) 的照「后面的章」算、再加上列出的几个）。
    - NEW_CONTENT 里的章也不拿来当证明进度（那几章的步骤、位置落在那几章里的 main/*）：老玩家走过的地方由别的章证明，
      以后整章加新内容（加进 NEW_CONTENT）时，还没发出的那一版的内容也不会跟着变（batch13.md §2.6：13e 加两章后 v3 不变）。
    - EXTRA：某一步额外的补勾检查（if_check）和补上以后的动作（then），名字由 GuideMigration.registerCheck / registerAction 登记。
    - EXTRA_GRANTS：不是手册步骤的补发（隐藏进度 + 动作，例：契约之誉的下限），按版本写在这里，从那一版起进表、以后的版本不再重复。
  main/* 进度不是手册步骤的，按进度树里最近的、是手册步骤的祖先定位置（只会把它排得更早，不会把没走到的人算成走过了）。

用法（在仓库根目录跑）
  python tools/guide/gen_migration.py                 生成当前版本的表（版本号读 GuideMigration.CURRENT_VERSION），并打印每一条
  python tools/guide/gen_migration.py --dry-run       只打印，不写文件
  python tools/guide/gen_migration.py --check         和仓库里的文件比，不一样退出码 1（改完手册后核对用）
  python tools/guide/gen_migration.py --version 2     指定版本：出下一版时先把 GuideMigration.CURRENT_VERSION 加一，再跑这一句
  python tools/guide/gen_migration.py --book-dir <目录>   用别处的 zh_cn 手册（试算片段合进去以后的样子）
  python tools/guide/gen_migration.py --make-baseline 4dacc01   从某个提交的手册重新生成 baseline_steps.txt（一次性，已经生成过）

退出码：0 = 成功；1 = --check 时和仓库里的文件不一样，或者要写 / 要核对的版本已经发出（SHIPPED）却会被改动、发出的文件被手改过；
  2 = 主线条件里用到的配置键不在 config_keys.json 里（先跑 runData，不然这些条件照引擎的规矩算成「不成立」，表会漏步骤）

什么时候要重跑
  改了主线必做步骤（加步骤、可选改必做、挪章、换完成进度）以后。忘了也查得出来：check_progression.py 的「迁移表覆盖检查」
  会报「不在基线里的必做主线步骤没有迁移项」，GameTest GuideMigrationTests.guideMigrationShippedTables 会报表里引用了不存在的进度。
  已经发出去的版本别改内容：玩家迁过那一版就不会再跑它，要补的东西放进下一版。发出时把那一版的哈希记进 SHIPPED，
  之后照「重跑」的提示做却忘了先把 CURRENT_VERSION 加一，脚本会直接拒绝，不会把新步骤写进已经发出的表。
"""
import argparse
import glob
import hashlib
import io
import json
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import guide_lib as L  # noqa: E402

BASELINE = os.path.join(L.HERE, 'baseline_steps.txt')
GUIDE_ZH = os.path.join(L.GUIDE, 'zh_cn')
DATA = os.path.join(L.MAIN_RES, 'data', L.MODID)
TABLE_DIR = os.path.join(DATA, 'guide_migration')
ADV_MAIN = os.path.join(DATA, 'advancements', 'main')
JAVA_MIGRATION = os.path.join(L.JAVA_PKG, 'player', 'GuideMigration.java')
GUIDE_GIT_DIR = 'src/main/resources/assets/' + L.MODID + '/guide/zh_cn/'
CLOSED = {'general.hoanna.portal_enabled': False}


def ec(path):
    return L.MODID + ':' + path


# 过门那一步（第十六章 hoanna/open_gate 的完成进度）
GATE_STEP = ec('guide/hoanna/portal')
# 整章新加的内容：不当成「老存档没做完」（batch10.md §2.6 第 3 条；10c 的两章）。
# 第十三批 13e 的第二十二章「暗影」、第二十三章「九阶」也是整章新加（batch13.md §2.6）：不进表、不出 v4，
# 已经毕业的人首页「下一步」会指过去；加进来以后 v3 的内容不变（--check 照过）
NEW_CONTENT = {'inquisitorium', 'terminal', 'shade', 'apex'}
# EXPLICIT 的值写成 (LATER, [补充的证明进度…]) 时：证明进度照「后面的章」的默认规则算，所在章在 NEW_CONTENT 里也迁移
LATER = 'later'
# 写明证明进度的步骤（优先于 NEW_CONTENT）：
# v2：3 档五芒星只补给已经毕业（做出宅男套装）的人，batch10.md §2.6
# v3：七阶在第十九章审判庭（NEW_CONTENT），照默认规则会被当成「新内容，不迁移」，做到第二十、二十一章的老玩家只补到六阶；
#     改成第二十章起的必做进度 + main/computer_armor（batch13.md §2.6）
EXPLICIT = {
    ec('guide/pentacle/tier3'): [ec('main/computer_armor')],
    ec('guide/matrix/rank_7'): (LATER, [ec('main/computer_armor')]),
}
# 额外的检查与动作：{进度: {'if_check': [检查名…], 'then': [动作名…]}}，名字由 GuideMigration.registerCheck / registerAction 登记。
# v1：补上风之真名以后把心愿数记满（WindWishes.LEGACY_ACTION）。好感已到门槛的老存档由 WindWishes.migrateLegacy 在登录时记满、
# 登录时（排在迁移之后）补进度；这里管的是好感低于（服主调高后的）门槛、却已经走过开门那一段的老玩家，证明进度和这一条的完全一样
EXTRA = {ec('guide/hoanna/true_name'): {'then': [ec('wind_wishes_legacy')]}}
# v3：补上一~七阶的步骤以后把矩阵阶抬到那一阶（只抬不降），不然补了勾、人还是零阶，过不了海纳尔之门和全息投影台的门（batch13.md §2.6）
MATRIX_RANK_FLOORS = 7
EXTRA.update({ec('guide/matrix/rank_%d' % n): {'then': [ec('matrix_rank_floor_%d' % n)]} for n in range(1, MATRIX_RANK_FLOORS + 1)})
# 不是手册步骤的补发：{从哪一版起: [条目…]}，条目照迁移表的写法（grant 是隐藏进度，只为记「补过了」，要有进度文件）。
# v3：契约之誉的下限——打过恶魔交易（拿到过阿克罗尼特）的老玩家至少 8（熟客），做出过宅男套装的至少 40（血契），batch13.md §2.6
EXTRA_GRANTS = {
    3: [
        {'grant': ec('guide/matrix/legacy_standing_8'), 'if_any': [ec('guide/demons/ackronite')],
         'then': [ec('demon_standing_floor_8')]},
        {'grant': ec('guide/matrix/legacy_standing_40'), 'if_any': [ec('main/computer_armor')],
         'then': [ec('demon_standing_floor_40')]},
    ],
}
GROUP_RANK = {'start': 0, 'main': 1}


def extra_grant_ids():
    """EXTRA_GRANTS 里所有的 grant（不是手册步骤，覆盖检查不当它是「不再是必做步骤」）"""
    return {it['grant'] for items in EXTRA_GRANTS.values() for it in items}


# ---------------------------------------------------------------- 读

def _sort_book(cats):
    """和引擎一样排：group（入门 → 主线 → 支线）→ order → id"""
    def key(c):
        order = c.get('order')
        return (GROUP_RANK.get(c.get('group'), 2), order if isinstance(order, int) and not isinstance(order, bool) else 1000, str(c.get('id')))
    return sorted((c for c in cats if isinstance(c, dict)), key=key)


def load_book(book_dir=None):
    cats = []
    for f in sorted(glob.glob(os.path.join(book_dir or GUIDE_ZH, '*.json'))):
        d = L.load_json(f)
        if isinstance(d, dict):
            d.setdefault('id', os.path.basename(f)[:-5])
            cats.append(d)
    return _sort_book(cats)


def _git(*args):
    return subprocess.run(['git'] + list(args), cwd=L.ROOT, capture_output=True, text=True, encoding='utf-8', check=True).stdout


def load_book_from_git(commit):
    cats = []
    for name in _git('ls-tree', '--name-only', commit, GUIDE_GIT_DIR).split():
        if name.endswith('.json'):
            d = json.loads(_git('show', commit + ':' + name))
            d.setdefault('id', os.path.basename(name)[:-5])
            cats.append(d)
    return _sort_book(cats)


def load_baseline(path=None):
    """基线：每行一个进度 id，# 后面是注释"""
    out = []
    for line in io.open(path or BASELINE, encoding='utf-8'):
        line = line.split('#', 1)[0].strip()
        if line:
            out.append(line)
    return out


def load_tables(table_dir=None):
    """仓库里的迁移表：版本号 → 条目列表"""
    out = {}
    for f in glob.glob(os.path.join(table_dir or TABLE_DIR, 'v*.json')):
        m = re.match('^v([1-9][0-9]*)[.]json$', os.path.basename(f))
        if m:
            out[int(m.group(1))] = L.load_json(f, []) or []
    return out


def current_version():
    m = re.search('CURRENT_VERSION = ([0-9]+);', io.open(JAVA_MIGRATION, encoding='utf-8').read())
    if not m:
        sys.exit('读不到 %s 里的 CURRENT_VERSION' % JAVA_MIGRATION)
    return int(m.group(1))


# ---------------------------------------------------------------- 算

def evaluators(cfg_keys=None):
    """(条件求值, 可选求值)：都是 (原文, 覆盖配置) → bool，默认照引擎（guide_lib）"""
    keys = cfg_keys if cfg_keys is not None else L.load_config_keys()
    if keys is None:
        sys.exit('没有 %s：先跑一次 runData' % L.CONFIG_KEYS_FILE)
    return (lambda expr, over: L.cond_true(expr, keys, over)), (lambda raw, over: L.is_optional(raw, keys, over))


def steps_of(book, cond, optional, over):
    """主线里可见的、写了 complete 的条目，按全书顺序。
    pos = (章号, 章里第几个可见条目)：章号和首页一样只数可见、且至少有一个可见条目的主线分类"""
    out = []
    chapter = 0
    for c in book:
        if c.get('group') != 'main' or not cond(c.get('if'), over):
            continue
        visible = [e for e in c.get('entries') or [] if isinstance(e, dict) and cond(e.get('if'), over)]
        if not visible:
            continue
        chapter += 1
        for i, e in enumerate(visible):
            if e.get('complete'):
                out.append({'cat': c.get('id'), 'entry': e.get('id'), 'complete': e['complete'], 'pos': (chapter, i),
                            'required': not optional(e.get('optional'), over)})
    return out


def required_steps(steps):
    """进度 id → 它作为必做步骤第一次出现的那一篇（同一个进度被几篇用作 complete 时，取必做的那篇定位置）"""
    out = {}
    for s in steps:
        if s['required'] and s['complete'] not in out:
            out[s['complete']] = s
    return out


def main_positions(steps, adv_dir=None):
    """main/* 进度的位置：本身是手册步骤的取那一步；不是的沿 parent 往上找最近的、是手册步骤的祖先；找不到的不收"""
    step_pos = {}
    for s in steps:
        step_pos.setdefault(s['complete'], s['pos'])
    parents = {}
    for f in glob.glob(os.path.join(adv_dir or ADV_MAIN, '*.json')):
        parents[ec('main/' + os.path.basename(f)[:-5])] = (L.load_json(f, {}) or {}).get('parent')
    out = {}
    for rid in sorted(parents):
        cur, seen = rid, set()
        while cur and cur not in step_pos and cur not in seen:
            seen.add(cur)
            cur = parents.get(cur)
        if cur in step_pos:
            out[rid] = step_pos[cur]
    return out


def proofs_for(step, ordered_required, mains, rule, gate, skip_chapters=()):
    """一步的证明进度（按位置排、去重、不含它自己）；skip_chapters 里的章（NEW_CONTENT 的章号）不拿来当证明"""
    ordered_required = [x for x in ordered_required if x['cat'] not in NEW_CONTENT]
    mains = {m: p for m, p in mains.items() if p[0] not in skip_chapters}
    if rule == 'gate':
        later = [x['complete'] for x in ordered_required if x['pos'] >= gate['pos']]
        later_main = [m for m, p in sorted(mains.items(), key=lambda kv: (kv[1], kv[0])) if p >= gate['pos']]
    else:
        later = [x['complete'] for x in ordered_required if x['pos'][0] > step['pos'][0]]
        later_main = [m for m, p in sorted(mains.items(), key=lambda kv: (kv[1], kv[0])) if p[0] > step['pos'][0]]
    out = []
    for rid in later + later_main:
        if rid != step['complete'] and rid not in out:
            out.append(rid)
    return out


def build(book, cond, optional, baseline, earlier_grants, version=None):
    """算出一版迁移表。返回 (条目列表, 说明列表)：说明是 (进度, 分类/条目, 规则或跳过原因)。
    version：生成的是第几版（EXTRA_GRANTS 从写明的那一版起进表；不给时不加）"""
    open_steps = steps_of(book, cond, optional, {})
    closed_steps = steps_of(book, cond, optional, CLOSED)
    required_closed = {s['complete'] for s in closed_steps if s['required']}
    req = required_steps(open_steps)
    ordered = sorted(req.values(), key=lambda s: s['pos'])
    mains = main_positions(open_steps)
    gate = req.get(GATE_STEP)
    new_chapters = {s['pos'][0] for s in open_steps if s['cat'] in NEW_CONTENT}
    covered = set(baseline) | set(earlier_grants)
    items, notes = [], []
    for s in ordered:
        adv = s['complete']
        where = '%s/%s' % (s['cat'], s['entry'])
        if adv in covered:
            continue
        if adv in EXPLICIT:
            spec = EXPLICIT[adv]
            if isinstance(spec, tuple) and spec[0] == LATER:
                proofs = proofs_for(s, ordered, mains, 'later', gate, new_chapters)
                proofs += [p for p in spec[1] if p not in proofs and p != adv]
                rule = '写明（后面的章 + %s）' % '、'.join(spec[1])
            else:
                proofs, rule = list(spec), '写明'
        elif s['cat'] in NEW_CONTENT:
            notes.append((adv, where, '新内容，不迁移'))
            continue
        elif gate is not None and adv not in required_closed and s['pos'] < gate['pos']:
            proofs, rule = proofs_for(s, ordered, mains, 'gate', gate, new_chapters), '过门规则（从 %s 起）' % GATE_STEP
        else:
            proofs, rule = proofs_for(s, ordered, mains, 'later', gate, new_chapters), '后面的章'
        if not proofs:
            notes.append((adv, where, '后面没有能证明它的进度，不迁移'))
            continue
        item = {'grant': adv, 'if_any': proofs}
        item.update(EXTRA.get(adv, {}))
        items.append(item)
        notes.append((adv, where, rule))
    for v in sorted(EXTRA_GRANTS):
        if version is None or v > version:
            continue
        for it in EXTRA_GRANTS[v]:
            if it['grant'] in covered:
                continue
            items.append(dict(it))
            notes.append((it['grant'], '（不是手册步骤）', '非步骤补发（v%d 起）' % v))
    return items, notes


_CFG_KEY = re.compile('cfg:' + L.WS + '*([A-Za-z0-9_.]+)')


def missing_keys(book, cfg_keys):
    """主线分类 / 条目的 if、optional 里引用了、config_keys.json 里却没有的配置键。
    这种键照引擎的规矩算成「不成立」：条目会被当成藏掉、必做会被当成可选，算出来的表就漏了步骤"""
    out = set()

    def scan(cond):
        for c in cond if isinstance(cond, list) else [cond]:
            if isinstance(c, str):
                out.update(k for k in _CFG_KEY.findall(c) if k not in (cfg_keys or {}))

    for c in book:
        if c.get('group') != 'main':
            continue
        scan(c.get('if'))
        for e in c.get('entries') or []:
            if isinstance(e, dict):
                scan(e.get('if'))
                if not isinstance(e.get('optional'), bool):
                    scan(e.get('optional'))
    return sorted(out)


def render(items):
    return json.dumps(items, ensure_ascii=False, indent=2) + chr(10)


# ---------------------------------------------------------------- 基线

def make_baseline(commit, cond, optional):
    steps = steps_of(load_book_from_git(commit), cond, optional, {})
    lines = ['# 研究手册老存档迁移的基线：%s 时主线的必做步骤（按默认配置），共 %d 个，按全书顺序。' % (commit, 0),
             '# 由 tools/guide/gen_migration.py --make-baseline %s 生成（一次性）；不在这里的必做主线步骤要有迁移项（见那个脚本开头）。' % commit,
             '# 每行一个进度 id，# 后面是它在手册里的位置。']
    seen = []
    for s in steps:
        if s['required'] and s['complete'] not in seen:
            seen.append(s['complete'])
            lines.append('%-56s # %s/%s' % (s['complete'], s['cat'], s['entry']))
    lines[0] = lines[0].replace('共 0 个', '共 %d 个' % len(seen))
    io.open(BASELINE, 'w', encoding='utf-8', newline='').write(chr(10).join(lines) + chr(10))
    print('写入 %s：%d 个必做步骤' % (os.path.relpath(BASELINE, L.ROOT), len(seen)))


# ---------------------------------------------------------------- 已发出的版本

# 已经随正式包上服 / 对外发布的版本：{版本号: 发出时 v<N>.json 的 sha256（换行统一成 LF 后按字节算，见 file_sha256）}。
# 这个版本第一次随正式包上服 / 对外发布时填上，填了就不许再改：玩家迁过那一版就不会再跑它，改了内容的话，
# 已经迁到这一版的玩家永远补不到改进来的步骤。要补的东西放进下一版（先把 GuideMigration.CURRENT_VERSION 加一）。
SHIPPED = {
    1: 'e2154f69ed21015d370428144ab567601bb6ad57fe521cfc8d1b3327f51ee7a8',
    2: '9984e8776177059b62489088eece7028ae31b8db83d04ae2ff5c2f75a3607bb4',
    3: 'f048142e37ec08ba33237911f7ac41e5435b6cf87af56f3300a800519161eac1',
}


def file_sha256(path):
    # 换行先统一成 LF：仓库开着 core.autocrlf，Windows 上新检出的文件是 CRLF，按原样算的话同一份表在不同的检出里哈希不一样
    with open(path, 'rb') as f:
        return hashlib.sha256(f.read().replace(b'\r\n', b'\n')).hexdigest()


def shipped_hint(version):
    return 'v%d 已发出：先把 GuideMigration.CURRENT_VERSION 加一，再跑 python tools/guide/gen_migration.py --version %d' % (
        version, version + 1)


def shipped_problems():
    """已发出的版本里，文件不见了或者和发出时不一样（被手改、被重新生成）的：[(版本, 说明)]"""
    out = []
    for v, digest in sorted(SHIPPED.items()):
        path = os.path.join(TABLE_DIR, 'v%d.json' % v)
        rel = os.path.relpath(path, L.ROOT)
        if not os.path.exists(path):
            out.append((v, 'v%d 已发出，%s 却不见了' % (v, rel)))
        elif file_sha256(path) != digest:
            out.append((v, 'v%d 已发出，%s 和发出时不一样（sha256 对不上）：发出过的表不能改，从版本库里恢复它，要补的东西放进下一版' % (v, rel)))
    return out


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    ap = argparse.ArgumentParser(description='研究手册老存档迁移表生成')
    ap.add_argument('--version', type=int, help='版本号（默认读 GuideMigration.CURRENT_VERSION）')
    ap.add_argument('--dry-run', action='store_true', help='只打印，不写文件')
    ap.add_argument('--check', action='store_true', help='和仓库里的文件比，不一样退出码 1')
    ap.add_argument('--book-dir', help='zh_cn 手册目录（默认仓库里的）')
    ap.add_argument('--make-baseline', metavar='COMMIT', help='从这个提交的手册重新生成 baseline_steps.txt')
    args = ap.parse_args()
    cond, optional = evaluators()
    if args.make_baseline:
        make_baseline(args.make_baseline, cond, optional)
        return 0
    version = args.version or current_version()
    tables = load_tables()
    earlier = [it.get('grant') for v, items in tables.items() if v < version for it in items if isinstance(it, dict)]
    book = load_book(args.book_dir or GUIDE_ZH)
    missing = missing_keys(book, L.load_config_keys())
    if missing:
        print('主线条件里用到的配置键不在 %s 里：%s' % (os.path.relpath(L.CONFIG_KEYS_FILE, L.ROOT), '、'.join(missing)))
        print('先跑 runData 重新导出配置键表再生成（不然这些条件按「不成立」算，表会漏步骤）')
        return 2
    items, notes = build(book, cond, optional, load_baseline(), earlier, version)
    print('第 %d 版：%d 条（基线 %d 个步骤，更早的版本已补 %d 个）' % (version, len(items), len(load_baseline()), len(earlier)))
    by_grant = {it['grant']: it for it in items}
    for adv, where, rule in notes:
        it = by_grant.get(adv)
        if it:
            print('  + %-48s %-28s %s：%d 个证明进度，最早 %s%s' % (adv, where, rule, len(it['if_any']), it['if_any'][0],
                                                           '；then %s' % '、'.join(it['then']) if it.get('then') else
                                                           ('；另有检查' if it.get('if_check') else '')))
        else:
            print('  - %-48s %-28s %s' % (adv, where, rule))
    # 写明的步骤是这一版才有的必做步骤时一定要进表（证明进度一个都算不出来、被当成新内容跳过都是漏项）
    required_now = {s['complete'] for s in steps_of(book, cond, optional, {}) if s['required']}
    lost = [adv for adv in EXPLICIT if adv in required_now and adv not in by_grant and adv not in earlier
            and adv not in load_baseline()]
    if lost:
        print('写明的步骤没进表：%s（核对 EXPLICIT 和手册）' % '、'.join(lost))
        return 1
    path = os.path.join(TABLE_DIR, 'v%d.json' % version)
    text = render(items)
    if args.check:
        bad = shipped_problems()
        for _, msg in bad:
            print(msg)
        old = io.open(path, encoding='utf-8').read() if os.path.exists(path) else None
        if old != text:
            if version in SHIPPED:
                print('%s 和手册算出来的不一样，而 %s' % (os.path.relpath(path, L.ROOT), shipped_hint(version)))
            else:
                print('%s 和手册算出来的不一样：重跑 python tools/guide/gen_migration.py' % os.path.relpath(path, L.ROOT))
            return 1
        if bad:
            return 1
        print('%s 和手册一致' % os.path.relpath(path, L.ROOT))
        return 0
    if args.dry_run:
        print(text)
        return 0
    if version in SHIPPED:
        # 发出过的版本一律不重写：内容一样也没必要写，不一样就是忘了先加版本号
        print('不写 %s：%s' % (os.path.relpath(path, L.ROOT), shipped_hint(version)))
        return 1
    os.makedirs(TABLE_DIR, exist_ok=True)
    io.open(path, 'w', encoding='utf-8', newline='').write(text)
    print('写入 %s' % os.path.relpath(path, L.ROOT))
    return 0


if __name__ == '__main__':
    sys.exit(main())
