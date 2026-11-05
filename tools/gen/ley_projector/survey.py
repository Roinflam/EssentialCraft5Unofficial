#!/usr/bin/env python3
"""
地脉投影仪投影表实测的驱动脚本（第五批集成，batch5.md §9「投影表实测」）：

    python tools/gen/ley_projector/survey.py            # 工程根目录下跑；Windows（要起 Gradle 和本地端口）

做的事：
  1. 备份 run/server.properties，临时改成：存档名 ley_survey_b5（不碰 run/world 和别的存档）、固定种子、只监听 127.0.0.1、
     非默认端口、开 RCON（随机密码）、关看门狗；run/ 下没有 eula.txt 就临时写一个。
  2. 后台跑 `gradlew runServer`（专用服，只装本模组和 Curios），等 RCON 连得上。
  3. 用 RCON 在主世界、下界各强制加载以 (0, 0) 为中心的 25×25 个区块（forceload 每条最多 256 个区块，分三条），
     每分钟 save-all flush 一次、数一下区域文件里生成完的区块，齐了（或超时）就 stop。
  4. 不管成功失败都把 server.properties 还原、删掉临时 eula.txt。
     进程被硬杀（比如外层超时的 TerminateProcess）不会走 finally：下次运行一开头先按备份和标记文件还原，然后退出，
     让人先查有没有上次留下的 runServer / Gradle 进程再重跑（见 restore_interrupted）。
  5. 跑 blockcount.py 统计，结果写 tools/gen/ley_projector/survey_result.json。存档留在 run/ley_survey_b5（要重算直接对它跑 blockcount.py；不要了可以删）。
之后按结果改 ../ley_projector_tables.py 的 TABLES / ROCK 并重跑它（这一步手动：要判断哪些产物进表，见那边的注释）。

环境变量：JAVA_TOOL_OPTIONS 带 unixdomain.tmpdir（脚本会补上，指到项目的 .gradle/uds-tmp）。
"""
import os
import tempfile
import secrets
import shutil
import socket
import struct
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
RUN = os.path.join(ROOT, 'run')
PROPERTIES = os.path.join(RUN, 'server.properties')
BACKUP = PROPERTIES + '.survey_backup'          # 在 = 上次改过 server.properties 还没还原
EULA = os.path.join(RUN, 'eula.txt')
EULA_MARK = EULA + '.survey_made'               # 在 = eula.txt 是本脚本临时写的
WORLD = 'ley_survey_b5'
SEED = '20260929'
SERVER_PORT = 25997
RCON_PORT = 25998
RADIUS = 12          # 区块 -12..12 → 25×25
LOG = os.path.join(tempfile.gettempdir(), 'ec5_b5_survey_server.log')
START_TIMEOUT = 20 * 60
GENERATE_TIMEOUT = 25 * 60

sys.path.insert(0, HERE)
import blockcount  # noqa: E402


# ---------------------------------------------------------------- RCON

class Rcon:
    def __init__(self, port, password):
        self.sock = socket.create_connection(('127.0.0.1', port), timeout=30)
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
        return self._send(2, text)

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


# ---------------------------------------------------------------- server.properties

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
    """还原 server.properties（有备份才还原）、删掉本脚本写的临时 eula.txt；可重复调用（被打断在一半时下次再来一遍）"""
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
    """
    上次运行被硬杀（没走 finally）留下的改动：先还原，返回 True 让 main 退出。
    必须在判断存档之前做——存档已经有 level.dat 时会直接去统计，不进 try/finally，不在这里还原就永远还原不了；
    也不能接着跑：上次的 runServer / Gradle 可能还活着（杀掉 python 杀不到子进程），再起一个会抢存档和端口。
    """
    if not restore_temporary_files():
        return False
    print('上次运行被打断：已按备份还原 run/server.properties，删掉了临时 eula.txt。')
    print('先确认没有留下的 runServer / Gradle 进程（命令行带 EssentialCraft5Unofficial 的 java；核对清楚再结束，别动自己在玩的游戏），再重跑。')
    return True


def full_chunks(region_dir):
    count = 0
    for path in blockcount.glob.glob(os.path.join(region_dir, '*.mca')):
        for chunk in blockcount.chunks_in_region(path):
            if str(chunk.get('Status', '')).split(':')[-1] == 'full':
                count += 1
    return count


def main():
    if restore_interrupted():
        return 1
    for port in (SERVER_PORT, RCON_PORT):
        if not port_free(port):
            print(f'端口 {port} 被占用：可能是上次留下的 runServer（核对命令行后结束它），或者被别的程序占着（改顶上的端口常量）')
            return 1
    target = (2 * RADIUS + 1) ** 2
    world = os.path.join(RUN, WORLD)
    if os.path.exists(os.path.join(world, 'level.dat')):
        print(f'run/{WORLD} 已经存在：直接统计（要重新生成就先删掉它）')
        over = full_chunks(os.path.join(world, 'region'))
        nether = full_chunks(os.path.join(world, 'DIM-1', 'region'))
        if over < target or nether < target:
            # 生成循环里被打断的存档：统计照样能跑，但区块数比 survey.md 记的少，结果不能直接和它比
            print(f'注意：存档没生成完（主世界 {over}、下界 {nether}，目标各 ≥ {target}），多半是上次被打断；要完整结果就删掉 run/{WORLD} 重跑')
    else:
        # 备份只在没有时写。正常走到这里一定没有（restore_interrupted 刚处理过）；万一有（两个实例同时跑），不覆盖
        if os.path.exists(BACKUP):
            print(f'{BACKUP} 已经存在：另一个 survey.py 正在跑？')
            return 1
        shutil.copyfile(PROPERTIES, BACKUP)
        made_eula = not os.path.exists(EULA)
        password = secrets.token_hex(12)
        process = None
        try:
            patch_properties(PROPERTIES, {
                'level-name': WORLD, 'level-seed': SEED, 'level-type': 'minecraft\\:normal', 'generate-structures': 'true',
                'server-ip': '127.0.0.1', 'server-port': str(SERVER_PORT),
                'enable-rcon': 'true', 'rcon.port': str(RCON_PORT), 'rcon.password': password,
                'max-tick-time': '-1', 'online-mode': 'false', 'spawn-monsters': 'false', 'spawn-animals': 'false',
            })
            if made_eula:
                # 先写标记再写 eula：被杀在两步之间，下次还原时删一个不存在的 eula 也无妨
                with open(EULA_MARK, 'w', encoding='utf-8') as f:
                    f.write('eula.txt 是 tools/gen/ley_projector/survey.py 临时写的，运行结束（或下次运行开头）会删掉\n')
                with open(EULA, 'w', encoding='utf-8') as f:
                    f.write('eula=true\n')
            env = dict(os.environ)
            env.setdefault('JAVA_TOOL_OPTIONS', '-Djdk.net.unixdomain.tmpdir=' + os.path.join(ROOT, '.gradle', 'uds-tmp').replace(os.sep, '/'))
            log = open(LOG, 'w', encoding='utf-8', errors='replace')
            process = subprocess.Popen(['cmd', '/c', os.path.join(ROOT, 'gradlew.bat'), 'runServer', '--console=plain'], cwd=ROOT, env=env,
                                       stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
            print(f'runServer PID {process.pid}，日志 {LOG}')
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
            print('RCON 连上了，开始强制加载')
            low, high = -RADIUS * 16, RADIUS * 16 + 15
            bands = [(-RADIUS, -RADIUS + 9), (-RADIUS + 10, -RADIUS + 19), (-RADIUS + 20, RADIUS)]
            for dimension in ('minecraft:overworld', 'minecraft:the_nether'):
                for z0, z1 in bands:
                    reply = rcon.command(f'execute in {dimension} run forceload add {low} {z0 * 16} {high} {z1 * 16 + 15}')
                    print(dimension, z0, z1, reply)
            deadline = time.time() + GENERATE_TIMEOUT
            while time.time() < deadline:
                time.sleep(60)
                rcon.command('save-all flush')
                time.sleep(5)
                over = full_chunks(os.path.join(world, 'region'))
                nether = full_chunks(os.path.join(world, 'DIM-1', 'region'))
                print(f'已生成完：主世界 {over}，下界 {nether}（目标各 ≥ {target}）', flush=True)
                if over >= target and nether >= target:
                    break
            rcon.command('stop')
            rcon.close()
            process.wait(timeout=600)
        finally:
            if process is not None and process.poll() is None:
                print('服务端没按时退出，结束进程树')
                subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'])
            restore_temporary_files()
    out = os.path.join(HERE, 'survey_result.json')
    sys.argv = ['blockcount.py', os.path.join(RUN, WORLD), '--out', out]
    return blockcount.main()


if __name__ == '__main__':
    sys.exit(main())
