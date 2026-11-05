#!/usr/bin/env python3
"""
海纳尔气候普查（第六批 6a M1）：定 tools/gen/hoanna_biomes.py 里 multi_noise 的分界（T_BOUNDS、SHADOW_HUMIDITY）用。

    python tools/gen/hoanna_climate/climate.py                 # 默认 6 个种子、131 072² 格、步长 256，打印分位点和各群系占比
    python tools/gen/hoanna_climate/climate.py --test          # 只算 GameTest 普查那一份（种子 20260929、65 536² 格、步长 512）

不起游戏、不用 Gradle：用 build/classpath/runGameTestServer_minecraftClasspath.txt（跑过一次 Gradle 就有）编译同目录的 ClimateCensus.java，
它照 RandomState 的种子派生和 shifted_noise 公式复算温度 / 湿度 / 大陆度（见它的类注释）。编译产物放系统临时目录，不进工程。
群系判定和维度 JSON 一致：大陆度 < −0.19 是海；否则湿度 > SHADOW_HUMIDITY 是暗影；否则按温度落在 T_BOUNDS 的哪一段。
占比按陆地（不含海）算，和 gametest/HoannaNatureTests.hoannaBiomeCensus 一样（目标：五个主群系各 17~23%、暗影 1~3%）。
要 numpy。
"""
import argparse
import os
import subprocess
import sys
import tempfile

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
sys.path.insert(0, os.path.dirname(HERE))
import hoanna_biomes  # noqa: E402

CLASSPATH_FILE = os.path.join(ROOT, 'build', 'classpath', 'runGameTestServer_minecraftClasspath.txt')
NAMES = ['frozen', 'magic', 'chaos', 'dread', 'desert']


def classpath():
    with open(CLASSPATH_FILE, encoding='utf-8') as f:
        return os.pathsep.join(line.strip() for line in f if line.strip())


def compile_census(out_dir):
    subprocess.run(['javac', '-encoding', 'UTF-8', '-proc:none', '-nowarn', '-cp', classpath(), '-d', out_dir,
                    os.path.join(HERE, 'ClimateCensus.java')], check=True)


def sample(out_dir, seed, half, step):
    result = subprocess.run(['java', '-cp', classpath() + os.pathsep + out_dir, 'ClimateCensus', str(seed), str(half), str(step)],
                            check=True, capture_output=True, text=True)
    rows = [line.split() for line in result.stdout.splitlines()]
    return np.array([row for row in rows if len(row) == 3], dtype=np.float64)


def shares(data, bounds, shadow):
    t, h, c = data[:, 0], data[:, 1], data[:, 2]
    land = c >= -0.19
    main = land & (h <= shadow)
    n = land.sum()
    edges = [-np.inf] + list(bounds) + [np.inf]
    out = {'sea%': 100 * (~land).mean(), 'shadow%': 100 * (land & (h > shadow)).sum() / n}
    for i, name in enumerate(NAMES):
        out[name + '%'] = 100 * (main & (t >= edges[i]) & (t < edges[i + 1])).sum() / n
    return out


def show(label, result):
    print(label, ' '.join(f'{k}={v:.1f}' for k, v in result.items()))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--test', action='store_true', help='只算 GameTest 普查那一份')
    parser.add_argument('--seeds', default='5,11,22,33,44,20260929')
    parser.add_argument('--half', type=int, default=65536)
    parser.add_argument('--step', type=int, default=256)
    args = parser.parse_args()
    bounds, shadow = hoanna_biomes.T_BOUNDS, hoanna_biomes.SHADOW_HUMIDITY
    with tempfile.TemporaryDirectory(prefix='ec5_climate_') as out_dir:
        compile_census(out_dir)
        if args.test:
            show('GameTest（种子 20260929，65 536² 格，步长 512）', shares(sample(out_dir, 20260929, 32768, 512), bounds, shadow))
            return 0
        data = []
        for seed in [int(s) for s in args.seeds.split(',')]:
            d = sample(out_dir, seed, args.half, args.step)
            data.append(d)
            show(f'种子 {seed}', shares(d, bounds, shadow))
        allrows = np.concatenate(data)
        land = allrows[:, 2] >= -0.19
        humidity = allrows[land, 1]
        print('陆地湿度 97 / 98 / 99% 分位点', np.percentile(humidity, [97, 98, 99]).round(3))
        main_land = land & (allrows[:, 1] <= shadow)
        print('陆地（去掉暗影）温度五分位点', np.percentile(allrows[main_land, 0], [20, 40, 60, 80]).round(3))
        show('合计', shares(allrows, bounds, shadow))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
