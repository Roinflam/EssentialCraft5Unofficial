"""把界面检查录到的截图按底板裁出来拼成几页，逐像素看对齐用。

用法：python tools/guiaudit/sheet.py <run 目录> <语言> <输出目录> [每页几张=6] [只要名字里带这些字的…]
每张裁的是底板（含侧栏大块贴图）外扩 6 像素，按截图原始像素裁、不缩放；标题写截图名。
"""
import json
import os
import sys

from PIL import Image, ImageDraw


def panel_box(doc):
    rects = []
    if 'panel' in doc:
        p = doc['panel']
        rects.append((p['x'], p['y'], p['x'] + p['w'], p['y'] + p['h']))
    for o in doc.get('ops', []):
        if o['type'] in ('blit', 'sprite') and o['x1'] - o['x0'] >= 40 and o['y1'] - o['y0'] >= 40:
            if not (o['x0'] <= 0 and o['y0'] <= 0 and o['x1'] >= doc['width'] and o['y1'] >= doc['height']):
                rects.append((o['x0'], o['y0'], o['x1'], o['y1']))
    for o in doc.get('ops', []):
        if o['type'] == 'text' and o.get('text', '').strip():
            rects.append((o['x0'], o['y0'], o['x1'], o['y1']))
    if not rects:
        return None
    return (min(r[0] for r in rects), min(r[1] for r in rects), max(r[2] for r in rects), max(r[3] for r in rects))


def main():
    run, lang, out = sys.argv[1], sys.argv[2], sys.argv[3]
    per = int(sys.argv[4]) if len(sys.argv) > 4 else 6
    only = sys.argv[5:]
    os.makedirs(out, exist_ok=True)
    folder = os.path.join(run, 'guiaudit', lang)
    crops = []
    for name in sorted(os.listdir(folder)):
        if not name.endswith('.json') or (only and not any(k in name for k in only)):
            continue
        with open(os.path.join(folder, name), encoding='utf-8') as f:
            doc = json.load(f)
        shot = os.path.join(run, 'screenshots', 'ec5_' + doc['name'] + '.png')
        box = panel_box(doc)
        if not os.path.exists(shot) or box is None:
            continue
        img = Image.open(shot).convert('RGB')
        s = doc['guiScale']
        b = (max(0, int(box[0] * s) - 6), max(0, int(box[1] * s) - 6),
             min(img.width, int(box[2] * s) + 6), min(img.height, int(box[3] * s) + 6))
        crops.append((doc['name'], img.crop(b)))
    page = 0
    for i in range(0, len(crops), per):
        group = crops[i:i + per]
        cols = 2 if len(group) > 1 else 1
        rows = (len(group) + cols - 1) // cols
        cw = max(c.width for _, c in group)
        ch = max(c.height for _, c in group) + 16
        sheet = Image.new('RGB', (cw * cols, ch * rows), (40, 40, 40))
        draw = ImageDraw.Draw(sheet)
        for k, (name, crop) in enumerate(group):
            x, y = (k % cols) * cw, (k // cols) * ch
            draw.text((x + 4, y + 2), name, fill=(255, 255, 0))
            sheet.paste(crop, (x, y + 16))
        page += 1
        sheet.save(os.path.join(out, '%s_%02d.png' % (lang, page)))
        print(os.path.join(out, '%s_%02d.png' % (lang, page)), [n for n, _ in group])


if __name__ == '__main__':
    main()
