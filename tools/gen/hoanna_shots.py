# -*- coding: utf-8 -*-
"""
海纳尔天空与画面截图的量化验收（第六批 6a M3，tools/plan/batch6a.md §3.6 第 3 条）。

用法：python tools/gen/hoanna_shots.py [--dir run/screenshots] [--strict]
读展示厅拍的 *05_hoanna_*.png（runClient -PecShowroom，阶段 05_hoanna_*，场景清单见 batch6a.md §3.6 第 2 条），逐张量：

  sky      大面积彩度：画面上面 25% 的平均 CIELAB 彩度（天穹 / 雾），应 ≤ 26（护眼规则 §3.1 第 1 条）。
           只量朝天、正午、群系、黄昏那几张（名字带 _up / _noon / _biome_ / _dusk / _sunset / _border_）。
  seam     地平线接缝：在画面中间 60% 宽度里，从上往下找第一行「横向起伏明显」的行（地形开始），
           比较它上面 3 行（天穹下沿）和下面 3 行（最远的地形）的平均色 ΔE76，应 ≤ 3（§3.1 第 5 条）。
           只量平视的那几张（_east / _west / _biome_ / _border_ / _dusk / _sunset / _fumes_outside）；找不到地平线的记 n/a。
  fumes    站在雾团中心（_fumes_inside）整屏平均彩度应 ≤ 20（毒雾不整屏铺色）。
  vignette 辐射晕影（_radiation_10k / _radiation_30k）和同机位不带晕影的 _radiation_off 比：
           画面中间 40% 区域 ΔE 应 ≤ 1（中间不碰），四边 5% 的条带应变暗、偏紫（ΔE > 0.5，只报告）。没有 _radiation_off 就跳过。

结果打印成表；--strict 时有不合格的退出码 1。只读截图，不改任何文件。
"""
import argparse
import glob
import math
import os
import sys

import numpy as np
from PIL import Image

SKY_CHROMA_MAX = 26.0
SEAM_DE_MAX = 3.0
FUMES_CHROMA_MAX = 20.0
VIGNETTE_CENTER_DE_MAX = 1.0


def to_lab(rgb):
    """rgb：H × W × 3 的 0~255 数组 → Lab（sRGB → XYZ D65 → Lab，同 hoanna_biomes.py 的 lab()）"""
    c = rgb.astype(np.float64) / 255.0
    lin = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    x = (0.4124 * lin[..., 0] + 0.3576 * lin[..., 1] + 0.1805 * lin[..., 2]) / 0.95047
    y = 0.2126 * lin[..., 0] + 0.7152 * lin[..., 1] + 0.0722 * lin[..., 2]
    z = (0.0193 * lin[..., 0] + 0.1192 * lin[..., 1] + 0.9505 * lin[..., 2]) / 1.08883

    def f(t):
        return np.where(t > 216.0 / 24389.0, np.cbrt(t), (24389.0 / 27.0 * t + 16.0) / 116.0)

    fx, fy, fz = f(x), f(y), f(z)
    return np.stack([116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz)], axis=-1)


def chroma(lab):
    return np.hypot(lab[..., 1], lab[..., 2])


def delta_e(a, b):
    return float(np.sqrt(np.sum((np.asarray(a) - np.asarray(b)) ** 2)))


def load(path):
    return np.asarray(Image.open(path).convert('RGB'))


def sky_metric(lab):
    h = lab.shape[0]
    return float(chroma(lab[: max(1, h // 4)]).mean())


def seam_metric(lab):
    h, w = lab.shape[:2]
    x0, x1 = int(w * 0.2), int(w * 0.8)
    band = lab[:, x0:x1]
    row_mean = band.mean(axis=1)
    row_std = band[..., 0].std(axis=1)
    top = int(h * 0.1)
    bottom = int(h * 0.9)
    # 天穹 / 雾几乎没有横向起伏；地形一出现，亮度的横向标准差明显跳高
    base = float(np.median(row_std[top: top + max(3, h // 20)]))
    threshold = max(2.0, base * 3.0 + 1.0)
    horizon = None
    for y in range(top, bottom):
        if row_std[y] > threshold and row_std[min(y + 1, h - 1)] > threshold:
            horizon = y
            break
    if horizon is None or horizon < top + 3 or horizon + 4 >= h:
        return None, None
    above = row_mean[horizon - 3: horizon].mean(axis=0)
    below = row_mean[horizon + 1: horizon + 4].mean(axis=0)
    return delta_e(above, below), horizon


def center(lab, frac):
    h, w = lab.shape[:2]
    my, mx = int(h * (1 - frac) / 2), int(w * (1 - frac) / 2)
    return lab[my: h - my, mx: w - mx]


def edges(lab, frac):
    h, w = lab.shape[:2]
    by, bx = max(1, int(h * frac)), max(1, int(w * frac))
    parts = [lab[:by].reshape(-1, 3), lab[h - by:].reshape(-1, 3), lab[:, :bx].reshape(-1, 3), lab[:, w - bx:].reshape(-1, 3)]
    return np.concatenate(parts, axis=0)


def main():
    parser = argparse.ArgumentParser(description='海纳尔天空截图的量化验收')
    parser.add_argument('--dir', default=os.path.join('run', 'screenshots'))
    parser.add_argument('--strict', action='store_true')
    args = parser.parse_args()
    files = sorted(glob.glob(os.path.join(args.dir, '*05_hoanna_*.png')))
    if not files:
        print('没有找到 *05_hoanna_*.png（先跑 runClient -PecShowroom）：' + args.dir)
        return 1 if args.strict else 0
    by_name = {}
    for path in files:
        name = os.path.basename(path)
        key = name[name.index('05_hoanna_') + len('05_hoanna_'):-4]
        by_name[key] = path
    failures = []
    print('%-28s %-9s %-8s %s' % ('截图', '项目', '数值', '判定'))
    for key, path in by_name.items():
        lab = to_lab(load(path))
        if any(tag in key for tag in ('_up', 'noon', 'biome_', 'dusk', 'sunset', 'border_')) and 'fumes' not in key:
            value = sky_metric(lab)
            ok = value <= SKY_CHROMA_MAX
            print('%-28s %-9s %-8.1f %s' % (key, 'sky C*', value, 'ok' if ok else '超过 %.0f' % SKY_CHROMA_MAX))
            if not ok:
                failures.append(key + ' sky')
        if any(tag in key for tag in ('east', 'west', 'biome_', 'border_', 'dusk', 'sunset', 'fumes_outside')):
            value, row = seam_metric(lab)
            if value is None:
                print('%-28s %-9s %-8s %s' % (key, 'seam dE', 'n/a', '找不到地平线'))
            else:
                ok = value <= SEAM_DE_MAX
                print('%-28s %-9s %-8.2f %s（地平线在第 %d 行）' % (key, 'seam dE', value, 'ok' if ok else '超过 %.0f' % SEAM_DE_MAX, row))
                if not ok:
                    failures.append(key + ' seam')
        if key == 'fumes_inside':
            value = float(chroma(lab).mean())
            ok = value <= FUMES_CHROMA_MAX
            print('%-28s %-9s %-8.1f %s' % (key, 'mean C*', value, 'ok' if ok else '超过 %.0f' % FUMES_CHROMA_MAX))
            if not ok:
                failures.append(key + ' fumes')
    base_path = by_name.get('radiation_off')
    if base_path is not None:
        base = to_lab(load(base_path))
        for key in ('radiation_10k', 'radiation_30k'):
            path = by_name.get(key)
            if path is None:
                continue
            lab = to_lab(load(path))
            if lab.shape != base.shape:
                print('%-28s 尺寸和 radiation_off 不同，跳过' % key)
                continue
            center_de = delta_e(center(lab, 0.4).reshape(-1, 3).mean(axis=0), center(base, 0.4).reshape(-1, 3).mean(axis=0))
            edge_de = delta_e(edges(lab, 0.05).mean(axis=0), edges(base, 0.05).mean(axis=0))
            ok = center_de <= VIGNETTE_CENTER_DE_MAX
            print('%-28s %-9s %-8.2f %s（四边 dE %.2f）' % (key, 'center dE', center_de, 'ok' if ok else '中间被晕影碰到了', edge_de))
            if not ok:
                failures.append(key + ' vignette')
    else:
        print('没有 05_hoanna_radiation_off（同机位、不带晕影），跳过晕影中心对比')
    print('不合格：%d 项 %s' % (len(failures), ', '.join(failures)))
    return 1 if (failures and args.strict) else 0


if __name__ == '__main__':
    sys.exit(main())
