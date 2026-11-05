# -*- coding: utf-8 -*-
# 从简体生成繁体：研究手册内容 guide/zh_cn -> guide/zh_tw（台湾用语）、guide/zh_hk（香港用语），
# 以及整个模组的语言文件：datagen 生成的 src/generated/.../lang/zh_cn.json -> src/main/resources/.../lang/zh_tw.json、zh_hk.json。
#
# 用法：python tools/guide/make_traditional.py [--guide-only | --lang-only] [--dry-run] [-v]
#       （需要 pip install opencc-python-reimplemented）
#   默认两样都做。语言文件是 runData 的产物，**每次 runData 之后要重跑一次**（加 --lang-only 就行）。
#   --dry-run 只转换、报数，不写文件；-v 列出一次都没命中的 OVERRIDES。
# 规矩：zh_tw / zh_hk 全是生成物，不要手改 —— 简体改了就重跑本脚本；
#       个别转错 / 不地道的词写进下面的 OVERRIDES，重跑时自动套上。
# 从 KuvaLich 的 make_traditional.py 移植（OVERRIDES 去掉了只和星际战甲内容有关的条目）。
# 本文件刻意不写反斜杠（Write / Bash 工具会吃掉一层）。
import glob
import io
import json
import os
import sys
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import guide_lib as L  # noqa: E402

try:
    from opencc import OpenCC
except ImportError:
    sys.exit('需要先 pip install opencc-python-reimplemented')

NL = chr(10)
GUIDE = L.GUIDE
LANG_SRC = os.path.join(L.GEN_ASSETS, 'lang', 'zh_cn.json')
LANG_DST = os.path.join(L.ASSETS, 'lang')

TARGETS = {
    'zh_tw': 's2twp',   # 台湾：词汇级转换（伺服器、滑鼠、設定……）
    'zh_hk': 's2hk',    # 香港：字形转换 + 香港用字
}

# 转换后再套的修正：键是转换后的字符串片段，值是替换成的片段；按语言分开，按顺序逐条套用：具体的词在前，整字替换放最后。
# 键要写「转换后」的样子（台湾版已经过 s2twp 的词表，香港版是 s2hk 的字形），写错了就是静默不生效——加 -v 看「没命中」。

# 一对多字选错（两种繁体共有）
_CHAR_FIXES = [
    ('隻影響', '只影響'),
    ('相沖突', '相衝突'),
    ('看下錶', '看下表'),
    ('摺算', '折算'),
    ('回覆', '回復'),            # 生命「回复」是恢复，不是答复
    ('揹包', '背包'),            # 原版繁中两边都用「背包」
    ('熔燬', '熔毀'),
    ('小隻有', '小只有'),        # 「大小只有」切成了「小隻」
    ('少髮', '少發'),            # 「少发网络包」是少发送，不是头发
]

# 原版物品 / 生物 / 附魔 / 维度名：简体正文用的是原版简中译名，繁体按原版 zh_tw / zh_hk 的译名改（照 KuvaLich 核过的那份）
_VANILLA_BOTH = [
    ('時運', '幸運'),              # 时运（附魔）
    ('精準採集', '絲綢之觸'),      # 精准采集
    ('搶奪', '掠奪'),              # 抢夺
    ('亡靈殺手', '不死剋星'),      # 亡灵杀手
    ('急迫', '挖掘加速'),          # 急迫（状态效果）
    ('幻翼', '夜魅'),
    ('惡魂', '地獄幽靈'),
    ('凋靈', '凋零怪'),            # 凋灵（Boss）；凋零效果本来就叫「凋零」
    ('潛影貝', '界伏蚌'),
    ('末影人', '終界使者'),
    ('末影龍', '終界龍'),
    ('末影蟎', '終界蟎'),
    ('蠹蟲', '蠹魚'),
    ('劫掠獸', '劫毀獸'),
    ('藥箭', '藥水箭'),
    ('光靈箭', '追跡之箭'),
    ('下界石英礦石', '地獄石英礦'),
    ('末地', '終界'),
    ('蘑菇島', '蘑菇地'),
]

OVERRIDES = {
    'zh_tw': _CHAR_FIXES + [
        ('捱打', '挨打'),
        ('和麵朝', '和面朝'),          # 「位置和面朝的方向」
        ('繫結', '綁定'),              # 绑定宝石：s2twp 按程序员用语转成「繫結」，物品名要「綁定寶石」
        ('同排程', '同調度'),          # 同调度：s2twp 把「调度」当程序员用语转成「排程」
        # s2twp 词表在游戏语境里用错的词
        ('遠端', '遠程'),              # 远程（攻击 / 读写）；「遠端」是网络上的远端
        ('型別', '類型'),              # 程序员术语，生物 / 配方类型都用「類型」
        ('迴圈', '循環'),
        ('重新整理', '刷新'),          # 刷新（怪物刷新）；「重新整理」是网页重新载入
        ('全域性', '全域'),
        ('擴充套件', '擴展'),
        ('攻擊物件', '攻擊對象'),
        ('解除安裝', '卸載'),          # 区块卸载
        ('專案', '項目'),
        ('自定義', '自訂'),            # 原版繁中统一用「自訂」
        ('客戶端', '用戶端'),
        ('實時', '即時'),
        ('教程', '教學'),
    ] + _VANILLA_BOTH + [
        ('荊棘', '尖刺'),              # 荆棘（附魔）
        ('守衛者', '深海守衛'),
        ('疣豬獸', '豬布獸'),
        ('岩漿怪', '岩漿立方怪'),
        ('傀儡', '魔像'),              # 铁傀儡 → 鐵魔像
        ('粗鐵', '鐵原礦'),
        ('刷怪蛋', '生怪蛋'),
        ('下界合金', '獄髓'),
        ('下界', '地獄'),              # 维度名；放在下界合金 / 下界石英之后
        ('生物群系', '生態域'),
        ('臺', '台'),                  # 原版繁中「工作台」「附魔台」用「台」
        ('巖', '岩'),
    ],
    'zh_hk': _CHAR_FIXES + [
        # s2hk 只转字形不转词，下面这些大陆用词按香港习惯（与原版 zh_hk 一致）改
        ('默認', '預設'),
        ('鼠標', '滑鼠'),
        ('信息', '資訊'),
        ('設置', '設定'),
        ('保存', '儲存'),
        ('搜索', '搜尋'),
        ('圖標', '圖示'),
        ('鏈接', '連結'),
        ('自定義', '自訂'),
        ('兼容', '相容'),
        ('加載', '載入'),
        ('存盤', '存檔'),
        ('文本', '文字'),
        ('緩存', '快取'),
    ] + _VANILLA_BOTH + [
        ('守衞者', '深海守衞'),
        ('疣豬獸', '野豬獸'),
        ('岩漿怪', '巖漿史萊姆'),
        ('鐵傀儡', '鐵人'),
        ('刷怪蛋', '生成蛋'),
        ('下界合金', '地獄合金'),
        ('下界', '地獄'),
        ('生物羣系', '生物羣落'),
        ('殭屍', '喪屍'),              # 香港「殭屍」指中式跳尸，zombie 叫「喪屍」
        ('魷魚', '墨魚'),
        ('岩', '巖'),                  # 原版 zh_hk 的深板巖、基巖、熔巖、巖漿都用「巖」
    ],
}

# 这些字段是 id / 条件 / 类型 / 数字，不是给玩家看的字，原样保留
SKIP_KEYS = {'id', 'icon', 'if', 'type', 'style', 'order', 'group', 'height', 'width', 'widths', 'item', 'recipe',
             'recipe_type', 'ordered', 'per_row', 'compact', 'optional', 'complete', 'advancement', 'structure',
             'pattern', 'anchor', 'throw', 'result', 'chance', 'texture', 'block', 'tag', 'air'}

HITS = set()


def make_converter(lang):
    cc = OpenCC(TARGETS[lang])
    fixes = OVERRIDES.get(lang, [])

    def conv(s):
        out = cc.convert(s)
        for a, b in fixes:
            if a in out:
                HITS.add((lang, a))
                out = out.replace(a, b)
        return out
    return conv


def convert_node(node, conv, btype=None):
    """递归转换内容 JSON：只动文字；下划线字段（_src、_draft）不带进繁体文件"""
    if isinstance(node, str):
        return conv(node)
    if isinstance(node, list):
        return [convert_node(x, conv, btype) for x in node]
    if isinstance(node, dict):
        out = OrderedDict()
        own = node.get('type', btype) if isinstance(node.get('type'), str) else btype
        for k, v in node.items():
            if k.startswith('_'):
                continue
            if k in SKIP_KEYS:
                out[k] = v
            elif k == 'items' and own not in ('list', 'steps'):
                out[k] = v              # 条目 / items 块 / 配方块的物品 id 表
            elif k == 'blocks':
                out[k] = [convert_node(b, conv, None) for b in v]
            else:
                out[k] = convert_node(v, conv, own)
        return out
    return node


def write_json(path, data, dry):
    text = json.dumps(data, ensure_ascii=False, indent=2) + NL
    json.loads(text)
    if not dry:
        with io.open(path, 'w', encoding='utf-8', newline=NL) as f:
            f.write(text)


def gen_guide(lang, dry):
    conv = make_converter(lang)
    src_dir = os.path.join(GUIDE, 'zh_cn')
    dst_dir = os.path.join(GUIDE, lang)
    if not dry:
        os.makedirs(dst_dir, exist_ok=True)
    wanted = set()
    for f in sorted(glob.glob(os.path.join(src_dir, '*.json'))):
        name = os.path.basename(f)
        wanted.add(name)
        with io.open(f, encoding='utf-8') as fh:
            data = json.load(fh, object_pairs_hook=OrderedDict)
        write_json(os.path.join(dst_dir, name), convert_node(data, conv), dry)
    # 简体删掉的分类，繁体也删掉
    if not dry:
        for f in glob.glob(os.path.join(dst_dir, '*.json')):
            if os.path.basename(f) not in wanted:
                os.remove(f)
    return len(wanted)


def gen_lang(lang, dry):
    if not os.path.exists(LANG_SRC):
        print('  没有 %s（先跑 runData），跳过语言文件' % LANG_SRC)
        return 0
    conv = make_converter(lang)
    with io.open(LANG_SRC, encoding='utf-8') as fh:
        src = json.load(fh, object_pairs_hook=OrderedDict)
    out = OrderedDict((k, conv(v) if isinstance(v, str) and not k.startswith('_') else v) for k, v in src.items())
    if not dry:
        os.makedirs(LANG_DST, exist_ok=True)
    write_json(os.path.join(LANG_DST, lang + '.json'), out, dry)
    return len(out)


def main():
    args = set(sys.argv[1:])
    dry = '--dry-run' in args
    do_guide = '--lang-only' not in args
    do_lang = '--guide-only' not in args
    for lang in TARGETS:
        n = gen_guide(lang, dry) if do_guide else 0
        m = gen_lang(lang, dry) if do_lang else 0
        print('%s: guide %d files, lang %d keys%s' % (lang, n, m, '（dry run，没写文件）' if dry else ''))
        misses = [(a, b) for a, b in OVERRIDES.get(lang, []) if (lang, a) not in HITS]
        if misses and '-v' in args:
            for a, b in misses:
                print('  OVERRIDES 没命中：%s -> %s' % (a, b))
        elif misses:
            print('  OVERRIDES 有 %d 条这次没命中（-v 列出；多半是正文里暂时没有这些词）' % len(misses))


if __name__ == '__main__':
    main()
