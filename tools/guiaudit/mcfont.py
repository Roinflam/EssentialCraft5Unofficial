"""原版 1.20.1 默认字体的字宽（Font.width），不进游戏就能算一句话画出来多宽。

照原版的三层：space（空格 4）→ 位图（nonlatin_european / accented / ascii，宽 = 最右一列有像素的位置 × 缩放 + 1）
→ unihex（unifont，宽 = 点阵宽 / 2 + 1，汉字和全角标点按 size_overrides 固定 16 列 = 9）。
§ 格式码不占宽；粗体每字加 1。资源从 client-extra.jar 和资源索引里的 unifont.zip 解出来，目录用 EC5_VANILLA_ASSETS 的上一级：
  <目录>/assets/minecraft/font/include/*.json、<目录>/assets/minecraft/textures/font/*.png、<目录>/unifont.json、<目录>/unifont_all_no_pua-15.0.06.hex
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.environ.get('EC5_VANILLA_ASSETS', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'build', 'vanilla', 'assets', 'minecraft')).rstrip('\\/'))
ROOT = os.path.dirname(ROOT)

_advance = None


def _load():
    adv = {}
    unihex = {}
    font_dir = os.path.join(ROOT, 'assets', 'minecraft', 'font', 'include')
    with open(os.path.join(font_dir, 'space.json'), encoding='utf-8') as f:
        for p in json.load(f)['providers']:
            for ch, a in p['advances'].items():
                adv.setdefault(ord(ch), a)
    with open(os.path.join(font_dir, 'default.json'), encoding='utf-8') as f:
        providers = json.load(f)['providers']
    for p in providers:
        img = Image.open(os.path.join(ROOT, 'assets', 'minecraft', 'textures', p['file'].split(':', 1)[1])).convert('RGBA')
        rows = p['chars']
        cw = img.width // len(rows[0])
        chh = img.height // len(rows)
        height = p.get('height', 8)
        scale = height / chh
        alpha = img.getchannel('A').load()
        for r, line in enumerate(rows):
            for c, ch in enumerate(line):
                cp = ord(ch)
                if cp == 0 or cp in adv:
                    continue
                width = 0
                for i in range(cw - 1, -1, -1):
                    if any(alpha[c * cw + i, r * chh + k] != 0 for k in range(chh)):
                        width = i + 1
                        break
                adv[cp] = int(0.5 + width * scale) + 1
    with open(os.path.join(ROOT, 'unifont.json'), encoding='utf-8') as f:
        overrides = []
        for p in json.load(f)['providers']:
            for o in p.get('size_overrides', []):
                overrides.append((ord(o['from']), ord(o['to']), o['left'], o['right']))
    with open(os.path.join(ROOT, 'unifont_all_no_pua-15.0.06.hex'), encoding='ascii') as f:
        for line in f:
            code, bits = line.strip().split(':')
            cp = int(code, 16)
            if cp in adv:
                continue
            ov = next((o for o in overrides if o[0] <= cp <= o[1]), None)
            if ov:
                left, right = ov[2], ov[3]
            else:
                bw = 16 if len(bits) == 64 else 8
                rows = [int(bits[i:i + bw // 4], 16) for i in range(0, len(bits), bw // 4)]
                mask = 0
                for v in rows:
                    mask |= v
                if mask == 0:
                    left, right = 0, bw
                else:
                    left = bw - mask.bit_length()
                    right = bw - ((mask & -mask).bit_length() - 1) - 1
            unihex[cp] = (right - left + 1) // 2 + 1
    for cp, a in unihex.items():
        adv.setdefault(cp, a)
    return adv


def width(text):
    """Font.width：§ 格式码跳过，§l 之后每字加 1"""
    global _advance
    if _advance is None:
        _advance = _load()
    total = 0
    bold = False
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == '\u00a7' and i + 1 < len(text):
            code = text[i + 1].lower()
            if code == 'l':
                bold = True
            elif code in '0123456789abcdefr':
                bold = False
            i += 2
            continue
        total += _advance.get(ord(ch), 0) + (1 if bold else 0)
        i += 1
    return total


if __name__ == '__main__':
    import sys
    for s in sys.argv[1:]:
        print(width(s), s)
