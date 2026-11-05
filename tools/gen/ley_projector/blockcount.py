#!/usr/bin/env python3
"""
地脉投影仪投影表的实测：数一个存档里主世界、下界的方块成分（第五批集成，batch5.md §3.4、§9「投影表实测」）。

    python tools/gen/ley_projector/blockcount.py <存档目录> [--out 结果.json]

只读 region/*.mca（主世界）和 DIM-1/region/*.mca（下界），只统计状态是 full、且周围 8 个区块也是 full 的区块（其余跳过并计数）。
  - 主世界统计 y −64~63（和 removed_redesign §3.3 的 fvtest 实测同一个口径：投影仪「读」的是地下岩层，不算地表以上的山体）；
  - 下界统计 y 0~127（下界整个可活动的高度，128 以上是基岩顶上的空层）。
「能投影的方块」= 除了空气、水、岩浆、基岩、火以外的所有方块（这些格子掷不出东西，也不算进分母）。
权重 = 每 10 万格能投影的方块里有几格（和 ley_projector_tables.py 的 TABLES 同一个单位）。

怎么得到这个存档（复现步骤，脚本本身不启动游戏）：见同目录 survey.md。
依赖 numpy（解压 block_states 的位数组）。只读，不改存档。
"""
import argparse
import glob
import json
import math
import os
import struct
import sys
import zlib
from collections import Counter

import numpy as np

# ---------------------------------------------------------------- 最小 NBT 读取（只读，够用就行）

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, \
    TAG_INT_ARRAY, TAG_LONG_ARRAY = range(13)


class Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, n):
        chunk = self.data[self.pos:self.pos + n]
        self.pos += n
        return chunk

    def u8(self):
        value = self.data[self.pos]
        self.pos += 1
        return value

    def unpack(self, fmt, size):
        value = struct.unpack_from(fmt, self.data, self.pos)[0]
        self.pos += size
        return value

    def string(self):
        length = self.unpack('>H', 2)
        return self.take(length).decode('utf-8', errors='replace')

    def payload(self, tag):
        if tag == TAG_BYTE:
            return self.unpack('>b', 1)
        if tag == TAG_SHORT:
            return self.unpack('>h', 2)
        if tag == TAG_INT:
            return self.unpack('>i', 4)
        if tag == TAG_LONG:
            return self.unpack('>q', 8)
        if tag == TAG_FLOAT:
            return self.unpack('>f', 4)
        if tag == TAG_DOUBLE:
            return self.unpack('>d', 8)
        if tag == TAG_BYTE_ARRAY:
            length = self.unpack('>i', 4)
            return self.take(length)
        if tag == TAG_STRING:
            return self.string()
        if tag == TAG_LIST:
            inner = self.u8()
            length = self.unpack('>i', 4)
            return [self.payload(inner) for _ in range(length)]
        if tag == TAG_COMPOUND:
            result = {}
            while True:
                inner = self.u8()
                if inner == TAG_END:
                    return result
                name = self.string()
                result[name] = self.payload(inner)
        if tag == TAG_INT_ARRAY:
            length = self.unpack('>i', 4)
            return np.frombuffer(self.take(4 * length), dtype='>i4')
        if tag == TAG_LONG_ARRAY:
            length = self.unpack('>i', 4)
            return np.frombuffer(self.take(8 * length), dtype='>i8')
        raise ValueError(f'未知 NBT 标签 {tag} @ {self.pos}')


def read_nbt(data):
    reader = Reader(data)
    tag = reader.u8()
    reader.string()
    return reader.payload(tag)


# ---------------------------------------------------------------- 区域文件

def chunks_in_region(path):
    with open(path, 'rb') as f:
        data = f.read()
    if len(data) < 8192:
        return
    for index in range(1024):
        entry = struct.unpack_from('>I', data, index * 4)[0]
        offset, sectors = entry >> 8, entry & 0xFF
        if offset == 0 or sectors == 0:
            continue
        start = offset * 4096
        length = struct.unpack_from('>I', data, start)[0]
        compression = data[start + 4]
        payload = data[start + 5:start + 4 + length]
        if compression & 0x80:
            continue  # 外置 .mcc（超大区块），这里的存档不会有
        if compression == 2:
            raw = zlib.decompress(payload)
        elif compression == 1:
            import gzip
            raw = gzip.decompress(payload)
        elif compression == 3:
            raw = payload
        else:
            continue
        yield read_nbt(raw)


def block_name(entry):
    return entry.get('Name', 'minecraft:air')


def section_counts(section):
    """一个 16³ 子区块里每种方块（只看 Name，不看属性）有几格"""
    states = section.get('block_states')
    if not states:
        return None
    palette = states.get('palette') or []
    if not palette:
        return None
    data = states.get('data')
    if data is None or len(palette) == 1:
        return Counter({block_name(palette[0]): 4096})
    bits = max(4, math.ceil(math.log2(len(palette))))
    per_long = 64 // bits
    longs = data.astype(np.uint64)
    mask = np.uint64((1 << bits) - 1)
    shifts = (np.arange(per_long, dtype=np.uint64) * np.uint64(bits))
    values = ((longs[:, None] >> shifts[None, :]) & mask).reshape(-1)[:4096]
    counts = np.bincount(values.astype(np.int64), minlength=len(palette))
    result = Counter()
    for index, count in enumerate(counts):
        if count and index < len(palette):
            result[block_name(palette[index])] += int(count)
    return result


def count_dimension(region_dir, min_section, max_section):
    """
    min_section ~ max_section（含）是子区块的 Y（y = Y × 16）。
    只算「自己和周围 8 个区块都生成完」的区块：矿脉会从邻居区块长过来，邻居还没放地物时这一块的矿偏少（边缘偏差）。
    返回 (合计, 计入的区块数, 跳过的区块数)。
    """
    per_chunk = {}
    skipped = 0
    for path in sorted(glob.glob(os.path.join(region_dir, '*.mca'))):
        for chunk in chunks_in_region(path):
            status = str(chunk.get('Status', ''))
            if status.split(':')[-1] != 'full':
                skipped += 1
                continue
            counts = Counter()
            for section in chunk.get('sections', []):
                y = section.get('Y')
                if y is None or y < min_section or y > max_section:
                    continue
                section_total = section_counts(section)
                if section_total:
                    counts.update(section_total)
            per_chunk[(chunk.get('xPos'), chunk.get('zPos'))] = counts
    total = Counter()
    used = 0
    for (x, z), counts in per_chunk.items():
        if all((x + dx, z + dz) in per_chunk for dx in (-1, 0, 1) for dz in (-1, 0, 1)):
            total.update(counts)
            used += 1
        else:
            skipped += 1
    return total, used, skipped


NOT_PROJECTABLE = {
    'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air', 'minecraft:water', 'minecraft:lava',
    'minecraft:bedrock', 'minecraft:fire', 'minecraft:soul_fire', 'minecraft:bubble_column',
}


def summarize(name, counts, full, partial, y_range):
    projectable = sum(count for block, count in counts.items() if block not in NOT_PROJECTABLE)
    per_100k = {block: count * 100000 / projectable for block, count in counts.items()
                if block not in NOT_PROJECTABLE and projectable}
    return {
        'dimension': name,
        'y_range': y_range,
        'full_chunks': full,
        'skipped_chunks': partial,
        'projectable_blocks': projectable,
        'projectable_per_chunk': projectable / full if full else 0,
        'counts': dict(counts.most_common()),
        'per_100k': dict(sorted(per_100k.items(), key=lambda item: -item[1])),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('world')
    parser.add_argument('--out')
    args = parser.parse_args()
    results = []
    overworld = count_dimension(os.path.join(args.world, 'region'), -4, 3)
    results.append(summarize('minecraft:overworld', *overworld, [-64, 63]))
    nether = count_dimension(os.path.join(args.world, 'DIM-1', 'region'), 0, 7)
    results.append(summarize('minecraft:the_nether', *nether, [0, 127]))
    for result in results:
        print(f"{result['dimension']}: 计入区块 {result['full_chunks']}（跳过没生成完的和边上的 {result['skipped_chunks']}），"
              f"y {result['y_range'][0]}~{result['y_range'][1]} 能投影的方块 {result['projectable_blocks']}"
              f"（每区块 {result['projectable_per_chunk']:.0f}）")
        for block, value in list(result['per_100k'].items())[:40]:
            print(f'    {block:45s} {value:10.2f} / 10 万格')
    if args.out:
        with open(args.out, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(results, f, indent=1, ensure_ascii=False)
            f.write('\n')
    return 0


if __name__ == '__main__':
    sys.exit(main())
