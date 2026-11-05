#!/usr/bin/env python3
"""
海纳尔区块生成开销基准（第六批 6a，batch6a.md §4.4 第 1 条；说明见同目录 hoanna_bench.md）：

    python tools/gen/hoanna_bench.py                  # 工程根目录下跑；Windows（要起 Gradle 和本地端口）；约 5~10 分钟
    python tools/gen/hoanna_bench.py --rounds 3       # 多跑几轮（默认 2 轮，每轮主世界、海纳尔各一次、先后顺序轮换）
    python tools/gen/hoanna_bench.py --analyze <存档目录>   # 不开服，只重新统计某次留下的 JFR 录像
    python tools/gen/hoanna_bench.py --datapack <目录>      # 新存档带这个数据包（结构集 frequency 0 对照），结果另写一份

做的事（照 ley_projector/survey.py 的写法）：
  1. 备份 run/server.properties，临时改成：新存档 hoanna_bench_<时间>（不碰 run/world 和别的存档）、固定种子、只听 127.0.0.1、
     非默认端口、开 RCON（随机密码）、关看门狗、模拟距离 2；run/ 下没有 eula.txt 就临时写一个。
  2. 后台 `gradlew runServer`（专用服：本模组 + Curios + run/mods 里的东西），等 RCON 连上，确认海纳尔维度在（新建世界会并入数据包维度）。
  3. 每轮、每个维度：`/jfr start` → 在从没生成过的地方 forceload 25 × 25 区块（两个维度同一轮用同一块坐标，轮与轮之间换地方）→
     抽样 `execute if loaded` 等全部生成完 → `/jfr stop` → `forceload remove all`。一段录像只含一个维度的生成
     （JFR 自带的 json 汇总把所有维度的 chunkGen 合在一起，分不开，所以每个维度单独录；原因见 batch6a.md §4.4 核查）。
  4. 不管成功失败都把 server.properties 还原、删掉临时 eula。进程被硬杀（比如外层超时）不走 finally：
     下次运行一开头按备份和标记文件还原然后退出，先查有没有上次留下的 runServer / Gradle 进程再重跑。
  5. 用 JDK 的 `jfr print --json --events minecraft.ChunkGeneration` 读每段录像，按事件自带的 level 字段只取这个维度、这块区域
     （外扩 12 区块，算上为了生成 FULL 连带生成到低阶段的邻居），按生成阶段汇总耗时；结果写 tools/gen/hoanna_bench_result.json。

指标（两个维度同样的量法，比的是比值）：
  - sum：所有 ChunkGeneration 事件的耗时之和。噪声等阶段是丢给后台线程池做的，事件耗时含排队，绝对值偏大，但两个维度一样偏。
  - wall：这一段里第一个事件开始到最后一个事件结束（生成整块区域的墙钟）。
  - 通过线：海纳尔 / 主世界 ≤ 1.0；> 1.2 算回归（batch6a.md §4.2）。F6a 合入时先跑一次「只有地形」的基线（D1），集成时再跑全量。
环境变量：JAVA_TOOL_OPTIONS 带 unixdomain.tmpdir（脚本会补上，指到项目的 .gradle/uds-tmp）。同一时间只跑一个。
"""
import argparse
import datetime as dt
import glob
import json
import os
import tempfile
import re
import secrets
import shutil
import socket
import struct
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
RUN = os.path.join(ROOT, 'run')
PROPERTIES = os.path.join(RUN, 'server.properties')
BACKUP = PROPERTIES + '.hoanna_bench_backup'     # 在 = 上次改过 server.properties 还没还原
EULA = os.path.join(RUN, 'eula.txt')
EULA_MARK = EULA + '.hoanna_bench_made'          # 在 = eula.txt 是本脚本临时写的
RESULT = os.path.join(HERE, 'hoanna_bench_result.json')
SEED = '20260929'
SERVER_PORT = 25995
RCON_PORT = 25996
LOG = os.path.join(tempfile.gettempdir(), 'ec5_6a_hoanna_bench_server.log')
START_TIMEOUT = 20 * 60
AREA_TIMEOUT = 15 * 60
POLL_SECONDS = 3
DIMENSIONS = ('minecraft:overworld', 'essentialcraft5:hoanna')
NEIGHBOR_MARGIN = 12        # 区块：统计时区域外扩多少（FULL 要邻居先生成到低阶段）


# ---------------------------------------------------------------- RCON（同 ley_projector/survey.py）

class Rcon:
    def __init__(self, port, password):
        self.sock = socket.create_connection(('127.0.0.1', port), timeout=60)
        self.next_id = 1
        if self._send(3, password) is None:
            raise RuntimeError('RCON 登录失败')

    def _send(self, kind, body):
        request = self.next_id
        self.next_id += 1
        payload = struct.pack('<ii', request, kind) + body.encode('utf-8') + b'\x00\x00'
        self.sock.sendall(struct.pack('<i', len(payload)) + payload)
        length = struct.unpack('<i', self._recv(4))[0]
        data = self._recv(length)
        response_id = struct.unpack('<i', data[:4])[0]
        if response_id == -1:
            return None
        return data[8:-2].decode('utf-8', errors='replace')

    def _recv(self, n):
        data = b''
        while len(data) < n:
            part = self.sock.recv(n - len(data))
            if not part:
                raise ConnectionError('RCON 断开')
            data += part
        return data

    def command(self, text):
        return self._send(2, text) or ''

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


# ---------------------------------------------------------------- server.properties / eula

def patch_properties(path, changes):
    with open(path, encoding='utf-8') as f:
        lines = f.read().splitlines()
    seen = set()
    out = []
    for line in lines:
        key = line.split('=', 1)[0] if '=' in line and not line.startswith('#') else None
        if key in changes:
            out.append(f'{key}={changes[key]}')
            seen.add(key)
        else:
            out.append(line)
    for key, value in changes.items():
        if key not in seen:
            out.append(f'{key}={value}')
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(out) + '\n')


def port_free(port):
    with socket.socket() as s:
        try:
            s.bind(('127.0.0.1', port))
            return True
        except OSError:
            return False


def restore_temporary_files():
    restored = False
    if os.path.exists(BACKUP):
        shutil.copyfile(BACKUP, PROPERTIES)
        os.remove(BACKUP)
        restored = True
    if os.path.exists(EULA_MARK):
        if os.path.exists(EULA):
            os.remove(EULA)
        os.remove(EULA_MARK)
        restored = True
    return restored


def restore_interrupted():
    if not restore_temporary_files():
        return False
    print('上次运行被打断：已按备份还原 run/server.properties，删掉了临时 eula.txt。')
    print('先确认没有留下的 runServer / Gradle 进程（命令行带 EssentialCraft5Unofficial 的 java；核对清楚再结束，别动自己在玩的游戏），再重跑。')
    return True


# ---------------------------------------------------------------- 区域与等待

def area(round_index, radius):
    """第 round_index 轮的区块范围（两个维度同一块坐标；离出生点足够远、轮与轮不重叠）"""
    cx = 2500 + round_index * (2 * radius + 40)
    cz = 2500
    return cx - radius, cz - radius, cx + radius, cz + radius


def forceload_bands(x0, z0, x1, z1):
    """forceload 一条最多 256 个区块：按行切"""
    width = x1 - x0 + 1
    rows = max(1, 256 // width)
    bands = []
    z = z0
    while z <= z1:
        bands.append((x0, z, x1, min(z1, z + rows - 1)))
        z += rows
    return bands


def sample_points(x0, z0, x1, z1, step=4):
    xs = sorted(set(list(range(x0, x1 + 1, step)) + [x1]))
    zs = sorted(set(list(range(z0, z1 + 1, step)) + [z1]))
    return [(x, z) for x in xs for z in zs]


def loaded(rcon, dimension, cx, cz):
    reply = rcon.command(f'execute in {dimension} if loaded {cx * 16 + 8} 64 {cz * 16 + 8}')
    return 'passed' in reply.lower() or '通过' in reply


def wait_generated(rcon, dimension, box):
    points = sample_points(*box)
    pending = list(points)
    deadline = time.time() + AREA_TIMEOUT
    while pending and time.time() < deadline:
        time.sleep(POLL_SECONDS)
        pending = [p for p in pending if not loaded(rcon, dimension, *p)]
    return not pending


# ---------------------------------------------------------------- JFR

DURATION_RE = re.compile(r'^PT(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?$')
TIME_RE = re.compile(r'^(\d{4})-(\d\d)-(\d\d)T(\d\d):(\d\d):(\d\d)(?:\.(\d+))?(Z|[+-]\d\d:\d\d)?$')


def parse_duration(text):
    m = DURATION_RE.match(text or '')
    if not m:
        return 0.0
    hours, minutes, seconds = m.groups()
    return int(hours or 0) * 3600 + int(minutes or 0) * 60 + float(seconds or 0)


def parse_time(text):
    m = TIME_RE.match(text or '')
    if not m:
        return None
    y, mo, d, h, mi, s, frac, zone = m.groups()
    base = dt.datetime(int(y), int(mo), int(d), int(h), int(mi), int(s), tzinfo=dt.timezone.utc)
    offset = 0
    if zone and zone != 'Z':
        sign = 1 if zone[0] == '+' else -1
        offset = sign * (int(zone[1:3]) * 3600 + int(zone[4:6]) * 60)
    return base.timestamp() - offset + (float('0.' + frac) if frac else 0.0)


def jfr_events(path):
    tool = shutil.which('jfr')
    if tool is None:
        java = shutil.which('java')
        candidate = os.path.join(os.path.dirname(java), 'jfr.exe' if os.name == 'nt' else 'jfr') if java else None
        tool = candidate if candidate and os.path.exists(candidate) else None
    if tool is None:
        raise RuntimeError('找不到 JDK 的 jfr 工具（PATH 或 java 同目录），装个 JDK 17+ 再跑 --analyze')
    out = subprocess.run([tool, 'print', '--json', '--events', 'minecraft.ChunkGeneration', path],
                         capture_output=True, check=True)
    data = json.loads(out.stdout.decode('utf-8', errors='replace'))
    return data.get('recording', {}).get('events', [])


def summarize(path, dimension, box):
    x0, z0, x1, z1 = box
    x0, z0, x1, z1 = x0 - NEIGHBOR_MARGIN, z0 - NEIGHBOR_MARGIN, x1 + NEIGHBOR_MARGIN, z1 + NEIGHBOR_MARGIN
    per_status = {}
    starts, ends = [], []
    total = 0.0
    count = 0
    for event in jfr_events(path):
        values = event.get('values', {})
        if dimension not in str(values.get('level', '')):
            continue
        cx, cz = values.get('chunkPosX'), values.get('chunkPosZ')
        if cx is None or cz is None or not (x0 <= cx <= x1 and z0 <= cz <= z1):
            continue
        duration = parse_duration(values.get('duration'))
        start = parse_time(values.get('startTime'))
        status = str(values.get('status', '?')).split(':')[-1]
        entry = per_status.setdefault(status, {'count': 0, 'seconds': 0.0})
        entry['count'] += 1
        entry['seconds'] += duration
        total += duration
        count += 1
        if start is not None:
            starts.append(start)
            ends.append(start + duration)
    wall = (max(ends) - min(starts)) if starts else None
    for entry in per_status.values():
        entry['seconds'] = round(entry['seconds'], 4)
    return {'events': count, 'sum_seconds': round(total, 4), 'wall_seconds': round(wall, 3) if wall is not None else None,
            'per_status': dict(sorted(per_status.items()))}


def ratio(a, b):
    return round(a / b, 3) if a and b else None


def report(runs):
    rows = []
    for run in runs:
        by_dim = {m['dimension']: m for m in run['measurements']}
        over, hoanna = by_dim.get(DIMENSIONS[0]), by_dim.get(DIMENSIONS[1])
        if not over or not hoanna or not over.get('stats') or not hoanna.get('stats'):
            continue
        rows.append({
            'round': run['round'],
            'sum_ratio': ratio(hoanna['stats']['sum_seconds'], over['stats']['sum_seconds']),
            'wall_ratio': ratio(hoanna['stats']['wall_seconds'], over['stats']['wall_seconds']),
            'poll_wall_ratio': ratio(hoanna['poll_seconds'], over['poll_seconds']),
        })
    print('轮  sum 比   墙钟比   轮询墙钟比   （海纳尔 / 主世界；≤ 1.0 通过，> 1.2 回归）')
    for row in rows:
        print(f"{row['round']:<3} {row['sum_ratio']!s:<8} {row['wall_ratio']!s:<8} {row['poll_wall_ratio']!s}")
    sums = [r['sum_ratio'] for r in rows if r['sum_ratio']]
    verdict = None
    if sums:
        mean = sum(sums) / len(sums)
        verdict = 'pass' if mean <= 1.0 else ('warn' if mean <= 1.2 else 'fail')
        print(f'sum 比平均 {mean:.3f} → {verdict}')
    return rows, verdict


# ---------------------------------------------------------------- 主流程

def analyze_only(world):
    meta_path = os.path.join(world, 'hoanna_bench_meta.json')
    with open(meta_path, encoding='utf-8') as f:
        runs = json.load(f)
    for run in runs:
        for m in run['measurements']:
            m['stats'] = summarize(m['jfr'], m['dimension'], tuple(m['box']))
    rows, verdict = report(runs)
    write_result(runs, rows, verdict, world)
    return 0


def write_result(runs, rows, verdict, world):
    with open(RESULT, 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'world': os.path.relpath(world, ROOT), 'seed': SEED, 'runs': runs, 'ratios': rows, 'verdict': verdict},
                  f, indent=1, ensure_ascii=False)
        f.write('\n')
    print('结果写到', os.path.relpath(RESULT, ROOT))


def newest_jfr(since):
    files = [p for p in glob.glob(os.path.join(RUN, 'debug', '*.jfr')) if os.path.getmtime(p) >= since - 1]
    return max(files, key=os.path.getmtime) if files else None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--rounds', type=int, default=2)
    parser.add_argument('--radius', type=int, default=12, help='区块半径（12 → 25 × 25）')
    parser.add_argument('--analyze', metavar='WORLD', help='只重新统计这个存档目录里记下的录像')
    parser.add_argument('--datapack', metavar='DIR', help='开服前把这个数据包目录拷进新存档的 datapacks/（新建世界时自动启用），'
                        '例：三个结构集 frequency 0 的对照（batch6a §4.4 第 1 条）；结果另写 hoanna_bench_result_<数据包目录名>.json')
    args = parser.parse_args()
    global RESULT
    if args.datapack:
        RESULT = os.path.join(HERE, f'hoanna_bench_result_{os.path.basename(os.path.normpath(args.datapack))}.json')
    if args.analyze:
        return analyze_only(os.path.abspath(args.analyze))
    if restore_interrupted():
        return 1
    for port in (SERVER_PORT, RCON_PORT):
        if not port_free(port):
            print(f'端口 {port} 被占用：可能是上次留下的 runServer（核对命令行后结束它），或者被别的程序占着（改顶上的端口常量）')
            return 1
    if os.path.exists(BACKUP):
        print(f'{BACKUP} 已经存在：另一个 hoanna_bench.py 正在跑？')
        return 1
    world_name = 'hoanna_bench_' + dt.datetime.now().strftime('%Y%m%d_%H%M%S')
    world = os.path.join(RUN, world_name)
    if args.datapack:
        shutil.copytree(args.datapack, os.path.join(world, 'datapacks', os.path.basename(os.path.normpath(args.datapack))))
    shutil.copyfile(PROPERTIES, BACKUP)
    made_eula = not os.path.exists(EULA)
    password = secrets.token_hex(12)
    process = None
    runs = []
    try:
        patch_properties(PROPERTIES, {
            'level-name': world_name, 'level-seed': SEED, 'level-type': 'minecraft\\:normal', 'generate-structures': 'true',
            'server-ip': '127.0.0.1', 'server-port': str(SERVER_PORT),
            'enable-rcon': 'true', 'rcon.port': str(RCON_PORT), 'rcon.password': password,
            'max-tick-time': '-1', 'online-mode': 'false', 'spawn-monsters': 'false', 'spawn-animals': 'false',
            'simulation-distance': '2', 'view-distance': '2',
        })
        if made_eula:
            with open(EULA_MARK, 'w', encoding='utf-8') as f:
                f.write('eula.txt 是 tools/gen/hoanna_bench.py 临时写的，运行结束（或下次运行开头）会删掉\n')
            with open(EULA, 'w', encoding='utf-8') as f:
                f.write('eula=true\n')
        env = dict(os.environ)
        env.setdefault('JAVA_TOOL_OPTIONS', '-Djdk.net.unixdomain.tmpdir=' + os.path.join(ROOT, '.gradle', 'uds-tmp').replace(os.sep, '/'))
        log = open(LOG, 'w', encoding='utf-8', errors='replace')
        process = subprocess.Popen(['cmd', '/c', os.path.join(ROOT, 'gradlew.bat'), 'runServer', '--console=plain'], cwd=ROOT, env=env,
                                   stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
        print(f'runServer PID {process.pid}，日志 {LOG}，存档 run/{world_name}')
        rcon = None
        deadline = time.time() + START_TIMEOUT
        while time.time() < deadline and process.poll() is None:
            try:
                rcon = Rcon(RCON_PORT, password)
                break
            except OSError:
                time.sleep(10)
        if rcon is None:
            print('服务端没起来（看日志）')
            return 1
        probe = rcon.command(f'execute in {DIMENSIONS[1]} run forceload query')
        if 'unknown' in probe.lower() or '未知' in probe or 'incorrect' in probe.lower():
            print('服务端里没有海纳尔维度：', probe)
            rcon.command('stop')
            return 1
        time.sleep(20)   # 让出生点区块生成、JIT 热身告一段落
        # 热身（不录像、不计入）：两个维度各生成一小块。2026-09-29 合入基线第 1 轮先跑的主世界是冷的（full 阶段 58 秒、其余轮 3~14 秒），
        # sum 比 0.44 把平均拉低了；热身后第 1 轮也能算数
        warm = (-2500 - args.radius // 2, -2500 - args.radius // 2, -2500 + args.radius // 2, -2500 + args.radius // 2)
        for dimension in DIMENSIONS:
            for x0, z0, x1, z1 in forceload_bands(*warm):
                rcon.command(f'execute in {dimension} run forceload add {x0 * 16} {z0 * 16} {x1 * 16 + 15} {z1 * 16 + 15}')
            print(f'  热身 {dimension}：{"生成完" if wait_generated(rcon, dimension, warm) else "超时没生成完"}')
            rcon.command(f'execute in {dimension} run forceload remove all')
            time.sleep(5)
        for round_index in range(args.rounds):
            box = area(round_index, args.radius)
            order = DIMENSIONS if round_index % 2 == 0 else tuple(reversed(DIMENSIONS))
            run = {'round': round_index + 1, 'box': list(box), 'measurements': []}
            for dimension in order:
                started = time.time()
                print(rcon.command('jfr start'))
                time.sleep(2)
                t0 = time.time()
                for band in forceload_bands(*box):
                    x0, z0, x1, z1 = band
                    reply = rcon.command(f'execute in {dimension} run forceload add {x0 * 16} {z0 * 16} {x1 * 16 + 15} {z1 * 16 + 15}')
                    print(' ', dimension, band, reply)
                complete = wait_generated(rcon, dimension, box)
                poll_seconds = time.time() - t0
                time.sleep(2)
                reply = rcon.command('jfr stop')
                match = re.search(r'(debug[\\/][^\s\]]+\.jfr)', reply)
                path = os.path.join(RUN, match.group(1)) if match else newest_jfr(started)
                rcon.command(f'execute in {dimension} run forceload remove all')
                print(f'  {dimension}：{"生成完" if complete else "超时没生成完"}，轮询墙钟 {poll_seconds:.1f} 秒，录像 {path}')
                run['measurements'].append({'dimension': dimension, 'box': list(box), 'complete': complete,
                                            'poll_seconds': round(poll_seconds, 1), 'jfr': path})
                time.sleep(5)
            runs.append(run)
        rcon.command('stop')
        rcon.close()
        process.wait(timeout=600)
    finally:
        if process is not None and process.poll() is None:
            print('服务端没按时退出，结束进程树')
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'])
        restore_temporary_files()
    if not runs:
        return 1
    with open(os.path.join(world, 'hoanna_bench_meta.json'), 'w', encoding='utf-8') as f:
        json.dump(runs, f, indent=1, ensure_ascii=False)
    for run in runs:
        for m in run['measurements']:
            m['stats'] = summarize(m['jfr'], m['dimension'], tuple(m['box'])) if m['jfr'] else None
    rows, verdict = report(runs)
    write_result(runs, rows, verdict, world)
    return 0


if __name__ == '__main__':
    sys.exit(main())
