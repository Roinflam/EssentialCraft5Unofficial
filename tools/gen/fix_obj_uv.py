#!/usr/bin/env python3
"""
修原作机器 OBJ 模型的退化贴图坐标（第十一批）：

    python tools/gen/fix_obj_uv.py                  # 从原作读 OBJ、修好后写进 models/block/（可重复跑，结果逐字节相同）
    python tools/gen/fix_obj_uv.py check            # 只打印统计，不写文件
    python tools/gen/fix_obj_uv.py --out <目录>     # 写到别的目录（对比用）
    python tools/gen/fix_obj_uv.py preview <目录> [名字…]
                                                    # 离线渲染修前 / 修后对比图（要 numpy、Pillow），见文末 preview

为什么要修：原作 24 个机器 OBJ（EC4U 1.12 分支 models/block/*.obj，导入时原样拷过来）里，挤出来的侧壁、板子的厚边和外圈、
护角块的侧面、凹槽的内壁，贴图坐标大多没展开——一个四边形四个角的 UV 挤在一条线上（或者一个点上），UV 面积是 0。
游戏只能拿那条线上的一行像素拉满整个面，看上去就是一道道拉长的条纹。22 个模型有这样的面，占表面积 17%~64%
（辐射仓 640 个面里 256 个、43%）；mithrilinecrystal、sphere 没有，不动。贴图是 128×128 / 64×64 的展开图，空白处是黑色。
其中魔力镜（mirror、mirrorinv）和 MRU 特斯拉反应堆核心（mrureactor_btm）的这种面整条线都落在一种颜色里（见下面第 0 步），
跑完文件不变；真正改动的是另外 19 个。

改了什么：只改「UV 面积为 0」的那些面引用的贴图坐标——给它们新加 vt 行（追加在文件末尾），把这些 f 行的 vt 序号改指新行。
顶点、法线、面的顺序、别的面的 UV、MTL、贴图一律不动；除了这些 f 行和末尾新加的 vt，原文逐行保留。处理的是 MODELS 这 22 个。
mirrorinv.obj 在第八批导入后末尾加过玻璃背面（MIRRORINV_TAIL），这里照原样接回去。

怎么展开（_keep_flat → _build_panels → _build_masks → _choose_policies → run）：
  0. 先挑出不用修的：原来那条线整条落在同一种颜色里的面，游戏里画出来就是一块纯色、并没有条纹——好些是原作有意拿几何
     拼出来的花纹（米斯莱熔炉侧面那个绿色的 X，就是一块块三角形各自塌到绿色或深色上），保留原样（check 里记作 flat）。
     它平滑连着（夹角 ≤ 30°）的坏面也得都是纯色才留，免得圆板一圈侧壁里单留一面。挤成一点的面照常修。
     和保留的面同一平面叠在一起的坏面（水晶提取器的十字晶体斜面上就有两层错开的）要是另修一套贴图，两层颜色不同，
     深度打架就会闪（修之前两层颜色差不多，看不出来）：这种坏面直接沿用叠着的那个保留面的贴图坐标（check 里记作 shared），
     两层一样、也还是那块纯色；可它平滑连着的坏面里有要修的（照搬会在一圈侧壁里单留一面纯色），就反过来把叠着的保留面也改成要修，
     和它并成同一块面板一起展开（第 1 步）。
  1. 同一平面上叠在一起的几层坏面（原作有些地方同一块面叠了好几层）并成一块「面板」，整块用同一个映射，两层贴图一致，
     深度打架也看不出来；和正常面叠在一起的坏面直接套那个正常面的映射；和正常面平行、只错开一点、投影过去叠在它上面的
     （凹槽底、垫在面板后面的那层）把那个面投影过来。
  2. 其余的找「锚边」：坏面上 UV 本来就不为零长度的边（侧壁就是贴着顶面和贴着底面的那两条棱；点状的面四条边都算），
     隔着这条边的邻面要是 UV 正常、或者之前几轮已经修好，就能以它为锚——锚边两端的 UV 取邻面在同一位置的 UV，
     接缝处跟邻面严丝合缝。档位从好到差：叠在一起 > 共面 > 平行投影 > 凸棱 > 凹棱（凸棱是板子顶面那一圈、凹槽的外沿，
     正面最显眼）；背对背贴在一起的面（板子压在机身上的底面）不当锚。更好的那档还没修好就先等一轮，没有进展了才退一档；
     这一档的锚边都放不下才看下一档。
  3. 展开：把面板绕锚边转到邻面所在的平面上，再用邻面自己的贴图映射（沿边方向取锚边两端的 UV，垂直方向取邻面拟合出来的
     像素密度）算出其余顶点的 UV。转的方向两种：
       - 折叠（fold）：叠到邻面这一侧，侧壁铺的是邻面贴着棱那一圈的花纹（镜像），看着像同一块料的厚边；
       - 延伸（wrap）：摊到另一侧，等于把邻面的贴图接着往外铺（共面时就是平面投影，正对着看和贴图上画的一样）。
     凸棱 / 凹棱接的侧壁先折叠；共面接的先延伸还是先折叠按颜色、整个模型一起定（_choose_policies）。
     接着一个修过的坏面共面往外铺时只许延伸，不许再镜像回来（一路镜像下去会铺成一条条来回的带子）。
  4. 只许采样同一个展开块里的像素：展开块 = 朝向相同（法线夹角 ≤ 25°）的正常面在贴图上连成的一片（四连通），
     展开块中间画了东西、却没被任何正常面盖住的洞归给最近的那块（展开图外面的空白、衬底不算）。只按贴图上连不连通不够——
     立方体十字展开图的六个面是连着的，正面往外一延伸就会采到顶面的花纹。新的 UV 多边形采样到的像素必须全落在
     锚边那一侧的展开块里（不会采到黑色空白，也不会采到别的面）；修之前颜色一致的面，摆出来的颜色也不许差太多（fits）。
     首选方向压扁到 PREFER_SCALE 还放不下才换方向，再不行两个方向比谁压得少（SCALES）。
  5. 相邻侧壁连续：同一轮修的两面侧壁在共享的远端顶点上各算出一个 UV，连着同一个锚点、偏移在锚边折线同一侧的，
     取两条偏移线的交点（矩形凹槽四角是 45° 斜接，圆柱侧壁一圈连成一条带）。合并后面积缩水一半以上、采样出界，
     或者平均颜色变了不少（角伸进了别的花纹）就不合并。
  6. 整组都是坏面、没有任何正常邻面可锚（几个模型内部的小零件、透过开口看到的炉膛底）：以原来那条线为锚、按那条边的
     密度朝放得下的一侧摊开；只剩一个点的按模型的像素密度平面投影，摆在那个点附近放得下、颜色最接近原来的地方；
     同组其它面再以它为锚接着展开。叠在一起的几层整块摆不下就拆开各自展开。
新加的 UV 都在 [0, 1] 里，不会采到图集里相邻的贴图。

几个原作 OBJ 共用一张展开图：flowerburner.obj 也画 ultraflowerburner（mtl_override），heatgenerator.obj 也画 ultraheatgenerator，
mrureactor_btm 用 flowerburner 的贴图。颜色判断和展开块按 MTL 里那张原贴图算；换贴图的变体排布相同，照样适用（preview 里一起渲染）。

重新导入原作模型（tools/import_assets.py obj …）会把文件盖回原样，导入后重跑本脚本。
素材改动按 CC BY-SA 4.0 记在 CREDITS.md 与 tools/asset_map.tsv（MODIFIED 行）。
"""
import math
import os
import struct
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import texlib  # noqa: E402  同目录的 PNG 读写

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(os.path.dirname(HERE))
SOURCE_REPO = os.path.join(os.path.dirname(PROJECT), 'EssentialCraft4Unofficial')
SRC_MODELS = 'src/main/resources/assets/essentialcraft/models/block/'
ASSETS = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'essentialcraft5')
MODEL_DIR = os.path.join(ASSETS, 'models', 'block')
TEXTURE_DIR = os.path.join(ASSETS, 'textures')

MODELS = [
    'chargingchamber', 'colddistillator', 'crystalcontroller', 'crystalextractor', 'crystalformer', 'endergenerator',
    'flowerburner', 'heatgenerator', 'magicalenchanter', 'magicaljukebox', 'magicalrepairer', 'magiciantable',
    'magmaticsmeltery', 'matrixabsorber', 'mirror', 'mirrorinv', 'mithrilinefurnace', 'mrucoilhardener',
    'mrureactor_btm', 'potionspreader', 'radiatingchamber', 'sunraycollector',
]

# 第八批导入后加在 mirrorinv.obj 末尾的玻璃背面（逐字照原样接回去，见 asset_map.tsv 的 MODIFIED 行）
MIRRORINV_TAIL = [
    '# EC5: back side of the glass (pPlane1 reversed, normal -z). Items are drawn with back-face culling and many views',
    '# look at the back of the plane (the default block/block GUI angle, the left hand, a dropped item turning round),',
    '# so without these the glass vanished there and only the frame was left.',
    "# Only in the inventory model: the block entity renderer draws mirror.obj's glass without culling.",
    '# tools/import_assets.py obj mirrorinv would overwrite this file; re-add these lines after re-importing.',
    'vn 0.0000 0.0000 -1.0000',
    'f 140/181/20 139/180/20 138/179/20 137/178/20',
    'f 139/180/20 142/183/20 141/182/20 138/179/20',
]
EXTRA_TAIL = {'mirrorinv': MIRRORINV_TAIL}

VT_HEADER = [
    '# EC5: texture coordinates for the faces above whose original UVs were collapsed to a line or a point',
    '# (side walls, rims, plate edges). Generated by tools/gen/fix_obj_uv.py from the EC4U original; only the vt',
    '# indices of those faces were changed, everything else is as in the original file.',
]

BAD_UV_AREA = 0.05        # UV 面积（像素²）小于它算退化
MIN_AREA3 = 1e-6          # 几何面积为 0 的面本来就画不出来，不管
COLLAPSED = 0.05          # 一条边两端 UV 距离（像素）小于它算挤成一点
COPLANAR_COS = math.cos(math.radians(15))  # 共面（锚的档位）
SMOOTH_COS = math.cos(math.radians(30))    # 判断「纯色保留」时，夹角在这以内的相邻坏面算同一片
CLUSTER_COS = math.cos(math.radians(25))   # 展开块按朝向分组
SCALES = (1.0, 0.97, 0.94, 0.9, 0.85, 0.8, 0.75, 0.65, 0.5, 0.35, 0.25, 0.15)   # 厚度方向的压扁系数，从大到小试
PREFER_SCALE = 0.75       # 首选方向压到这里还放得下就不换方向
MAX_TRIES = 6             # 每块面板每轮最多试几条锚边
PARALLEL_DIST = 0.25      # 平行投影：两个平面最多错开多远（方块单位）
PARALLEL_OVERLAP = -0.01  # 平行投影：投影过去必须和那个正常面真的叠上（重叠深度 ≥ 0.01 方块单位）；只挨着的不算，
                          # 拿隔壁一小格的映射往外推，推出去的颜色常常对不上（热力产能器顶上齿轮的齿尖会变橙）
PATCH_SCALES = (1.0, 0.75, 0.5, 0.35, 0.25)  # 孤立的整组坏面平面投影时的等比缩小系数
PATCH_RADIUS = 12         # 平面投影在原来那个点附近找位置的范围（像素）
PATCH_CANDIDATES = 12     # 找到这么多个放得下的位置就停，从里面挑颜色最接近的
SAMPLE_STEP = 0.25        # 验有效性的采样间距（像素）
ANCHOR_SLACK = 0.3        # 验有效性时锚边往里收的距离（像素）
SIDE_SLACK = 0.05         # 一头搭在锚线上的侧边往里收的距离（像素）
FAR_SLACK = -0.03         # 远端的边往外多验这么一点（像素）
MERGE_FACTOR = 1.5        # 远端顶点合并距离：厚度（像素）× 这个系数
LOCK_SNAP = 0.5           # 之前几轮定下的远端顶点离得这么近（像素）就直接沿用
MERGE_MAX_DE = 8.0        # 斜接合并前后平均颜色差（CIELAB）超过它就不合并
FLAT_DE = 16.0            # 原来那条线上的颜色都在平均色这么近（CIELAB）以内，就算纯色（顶多一点渐变、拉不出条纹）、保留原样
LOOK_SPREAD = 25.0        # 修之前那条线（两边都算）的颜色散布在这以内，就算颜色一致
LOOK_MAX_DE = 40.0        # 颜色一致的面，摆出来的平均颜色和原来最多差这么多（CIELAB）
CLASS_NAMES = ('coplanar', 'convex', 'concave')


# ---------------------------------------------------------------- 小向量

def sub(a, b):
    return tuple(x - y for x, y in zip(a, b))


def add(a, b):
    return tuple(x + y for x, y in zip(a, b))


def mul(a, s):
    return tuple(x * s for x in a)


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def cross2(a, b):
    return a[0] * b[1] - a[1] * b[0]


def length(a):
    return math.sqrt(dot(a, a))


def unit(a):
    n = length(a)
    return mul(a, 1.0 / n) if n > 1e-12 else a


def rot90(a):
    return (-a[1], a[0])


def newell(points):
    n = (0.0, 0.0, 0.0)
    for i, p in enumerate(points):
        n = add(n, cross(p, points[(i + 1) % len(points)]))
    return n


def signed_area2(points):
    s = 0.0
    for i, p in enumerate(points):
        q = points[(i + 1) % len(points)]
        s += p[0] * q[1] - q[0] * p[1]
    return 0.5 * s


def area2(points):
    return abs(signed_area2(points))


def centroid(points):
    return mul(tuple(map(sum, zip(*points))), 1.0 / len(points))


def pos_key(p):
    return tuple(int(round(x * 10000)) for x in p)


def inset(poly, dists):
    """凸多边形每条边各往里收 dists[i]（负数是往外放）；收没了（或翻面）返回 None"""
    n = len(poly)
    s = signed_area2(poly)
    if abs(s) < 1e-9:
        return None
    sign = 1.0 if s > 0 else -1.0
    lines = []
    for i in range(n):
        d = sub(poly[(i + 1) % n], poly[i])
        if length(d) < 1e-9:
            return None
        lines.append((add(poly[i], mul(unit(rot90(d)), sign * dists[i])), unit(d)))
    out = []
    for i in range(n):
        (p1, d1), (p2, d2) = lines[i - 1], lines[i]
        den = cross2(d1, d2)
        if abs(den) < 1e-9:
            out.append(p2)
            continue
        out.append(add(p1, mul(d1, cross2(sub(p2, p1), d2) / den)))
    s2 = signed_area2(out)
    if s2 * sign <= 1e-9 or (min(dists) >= 0 and abs(s2) > abs(s) + 1e-9):
        return None
    return out


def polygons_gap(p, q):
    """两个凸多边形（二维）之间的间隙：分离轴上最大的分开距离；重叠时是负数或 0"""
    gap = -1e9
    for poly in (p, q):
        n = len(poly)
        for i in range(n):
            e = sub(poly[(i + 1) % n], poly[i])
            if length(e) < 1e-12:
                continue
            ax = unit((-e[1], e[0]))
            a = [dot(x, ax) for x in p]
            b = [dot(x, ax) for x in q]
            gap = max(gap, min(b) - max(a), min(a) - max(b))
    return gap


def polygons_overlap(p, q):
    """两个凸多边形（二维）内部有没有重叠；只碰到边、碰到角不算"""
    for poly in (p, q):
        n = len(poly)
        for i in range(n):
            e = sub(poly[(i + 1) % n], poly[i])
            ax = (-e[1], e[0])
            if length(ax) < 1e-12:
                continue
            a = [dot(x, ax) for x in p]
            b = [dot(x, ax) for x in q]
            if max(a) <= min(b) + 1e-7 or max(b) <= min(a) + 1e-7:
                return False
    return True


def inside(poly, x, y):
    c = False
    n = len(poly)
    for i in range(n):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % n]
        if (y1 > y) != (y2 > y):
            if x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
                c = not c
    return c


# ---------------------------------------------------------------- 读文件

def git_show(rel):
    out = subprocess.run(['git', '-C', SOURCE_REPO, 'show', 'HEAD:' + rel], check=True, capture_output=True)
    return out.stdout.decode('utf-8').replace('\r\n', '\n')


def original_obj(name):
    return git_show(SRC_MODELS + name + '.obj')


def png_size(path):
    with open(path, 'rb') as f:
        head = f.read(24)
    if head[:8] != b'\x89PNG\r\n\x1a\n' or head[12:16] != b'IHDR':
        raise SystemExit(f'不是 PNG：{path}')
    return struct.unpack('>II', head[16:24])


def read_mtl(mtl_name):
    """MTL → {材质名（小写）: 贴图文件路径}"""
    mats, cur = {}, None
    with open(os.path.join(MODEL_DIR, mtl_name), encoding='utf-8') as f:
        for line in f:
            p = line.split()
            if not p:
                continue
            if p[0] == 'newmtl':
                cur = p[1].lower()
            elif p[0] == 'map_Kd' and cur is not None:
                path = p[1].split(':', 1)[1]
                mats[cur] = os.path.join(TEXTURE_DIR, *path.split('/')) + '.png'
    return mats


class Face:
    __slots__ = ('line', 'tokens', 'mat', 'vi', 'ti', 'P', 'keys', 'T', 'normal', 'area3', 'center', 'size')

    def __init__(self, line, tokens, mat):
        self.line, self.tokens, self.mat = line, tokens, mat
        self.vi, self.ti = [], []
        for tok in tokens:
            parts = tok.split('/')
            if len(parts) < 2 or not parts[1]:
                raise SystemExit(f'第 {line + 1} 行的面没有贴图坐标：{tok}')
            v, t = int(parts[0]), int(parts[1])
            if v <= 0 or t <= 0:
                raise SystemExit(f'第 {line + 1} 行用了负序号，脚本没处理：{tok}')
            self.vi.append(v - 1)
            self.ti.append(t - 1)


class Obj:
    def __init__(self, text):
        self.lines = text.split('\n')
        self.v, self.vt, self.faces = [], [], []
        self.mtllib = None
        mat = None
        for no, line in enumerate(self.lines):
            p = line.split()
            if not p:
                continue
            if p[0] == 'v':
                self.v.append(tuple(float(x) for x in p[1:4]))
            elif p[0] == 'vt':
                self.vt.append((float(p[1]), float(p[2])))
            elif p[0] == 'mtllib':
                self.mtllib = p[1]
            elif p[0] == 'usemtl':
                mat = p[1].lower()
            elif p[0] == 'f':
                self.faces.append(Face(no, p[1:], mat))


# ---------------------------------------------------------------- 修一个模型

class Fixer:
    def __init__(self, name, text):
        self.name = name
        self.obj = obj = Obj(text)
        self.textures = read_mtl(obj.mtllib)
        self.size = {m: png_size(p) for m, p in self.textures.items()}
        default = next(iter(self.size))
        for f in obj.faces:
            if f.mat not in self.size:
                f.mat = default
            W, H = f.size = self.size[f.mat]
            f.P = [obj.v[i] for i in f.vi]
            f.keys = [pos_key(p) for p in f.P]
            f.T = [(obj.vt[i][0] * W, obj.vt[i][1] * H) for i in f.ti]
            n = newell(f.P)
            f.area3 = 0.5 * length(n)
            f.normal = unit(n)
            f.center = centroid(f.P)
        self.bad = [i for i, f in enumerate(obj.faces) if area2(f.T) < BAD_UV_AREA and f.area3 > MIN_AREA3]
        self.bad_set = set(self.bad)
        self.uv = {}          # 修好的面：面序号 -> [UV（像素）]
        self.flat_uv = {}     # 沿用叠着的保留面贴图坐标的面（shared）：面序号 -> [UV（像素）]，不当锚、不参与合并
        self.result = {}      # 面序号 -> (档位, 方向, 压扁系数)、('seed' | 'patch', 压扁系数) 或 'left'
        self.anchors = {}     # 修好的面：面序号 -> 落在锚边上的顶点序号
        self._look = {}
        self.edges = {}
        for fi, f in enumerate(obj.faces):
            for k in range(len(f.P)):
                key = frozenset((f.keys[k], f.keys[(k + 1) % len(f.P)]))
                self.edges.setdefault(key, []).append((fi, k))
        self.pixels = {m: self._read_pixels(m) for m in self.size}
        self._keep_flat()
        self._build_panels()
        self._build_masks()
        self._choose_policies()

    # ---- 面板：同一平面上叠在一起的几层坏面并成一块（一起展开）；顺带找和正常面叠在一起、平行叠在一起的
    def _overlaps(self):
        """同一平面、朝向相同、二维投影有重叠（不算只碰到边）的面对。原作有些地方同一块面叠了好几层（自然熔炉的炉膛底最多），
        这些面要拿同一个映射，否则两层贴图不同，深度打架会闪成一片噪点。"""
        buckets = {}
        for fi, f in enumerate(self.obj.faces):
            if f.area3 <= MIN_AREA3 or fi in self.kept:
                continue
            n = f.normal
            buckets.setdefault((round(n[0], 1), round(n[1], 1), round(n[2], 1)), []).append(fi)
        pairs = []
        for key in sorted(buckets):
            fs = buckets[key]
            proj = {}
            for fi in fs:
                f = self.obj.faces[fi]
                a = (1.0, 0.0, 0.0) if abs(f.normal[0]) < 0.9 else (0.0, 1.0, 0.0)
                u = unit(cross(f.normal, a))
                v = cross(f.normal, u)
                proj[fi] = ([(dot(q, u), dot(q, v)) for q in f.P], dot(f.normal, f.P[0]), u, v)
            for i in range(len(fs)):
                a = self.obj.faces[fs[i]]
                for j in range(i + 1, len(fs)):
                    b = self.obj.faces[fs[j]]
                    if (fs[i] not in self.bad_set and fs[j] not in self.bad_set) or a.mat != b.mat:
                        continue
                    if dot(a.normal, b.normal) < 0.999 or abs(dot(a.normal, b.P[0]) - proj[fs[i]][1]) > 1e-3:
                        continue
                    pa = proj[fs[i]][0]
                    u, v = proj[fs[i]][2], proj[fs[i]][3]
                    pb = [(dot(q, u), dot(q, v)) for q in b.P]
                    if polygons_overlap(pa, pb):
                        pairs.append((fs[i], fs[j]))
            # 平行（同朝向、错开一点）、投影过去叠在一起的正常面：凹槽底、垫在面板后面的那层，按正对着它的那个面投影
            for i in range(len(fs)):
                a = self.obj.faces[fs[i]]
                if fs[i] not in self.bad_set:
                    continue
                u, v = proj[fs[i]][2], proj[fs[i]][3]
                pa = proj[fs[i]][0]
                for j in range(len(fs)):
                    b = self.obj.faces[fs[j]]
                    if fs[j] in self.bad_set or b.mat != a.mat or b.area3 <= MIN_AREA3 or area2(b.T) < BAD_UV_AREA:
                        continue
                    if dot(a.normal, b.normal) < 0.999:
                        continue
                    d = abs(dot(a.normal, b.P[0]) - proj[fs[i]][1])
                    if d <= 1e-3 or d > PARALLEL_DIST:
                        continue
                    gap = polygons_gap(pa, [(dot(q, u), dot(q, v)) for q in b.P])
                    if gap <= PARALLEL_OVERLAP:
                        self.parallel_good.setdefault(fs[i], []).append((round(d, 6), round(gap, 6), fs[j]))
        for fi in self.parallel_good:
            self.parallel_good[fi] = [nj for _, _, nj in sorted(self.parallel_good[fi])[:MAX_TRIES]]
        return pairs

    def _build_panels(self):
        """面板 = 一起展开的一组坏面：叠在一起的几层算一块，其余一个面一块（单元 units 记着叠在一起的分组，拆面板时用）"""
        parent = {fi: fi for fi in self.bad}

        def find(x):
            while parent[x] != x:
                parent[x] = parent[parent[x]]
                x = parent[x]
            return x

        def union(a, b):
            ra, rb = find(a), find(b)
            if ra != rb:
                parent[max(ra, rb)] = min(ra, rb)

        self.overlap_good = {}
        self.overlap_bad = {}
        self.parallel_good = {}
        for a, b in self._overlaps():
            if a in self.bad_set and b in self.bad_set:
                union(a, b)
                # 面板整块摆不下拆开以后，先修好的那层给后修的那层当「叠在一起」的锚（run 里），两层还是同一个映射
                self.overlap_bad.setdefault(a, []).append(b)
                self.overlap_bad.setdefault(b, []).append(a)
            elif a in self.bad_set:
                self.overlap_good.setdefault(a, []).append(b)
            else:
                self.overlap_good.setdefault(b, []).append(a)
        units = {}
        for fi in self.bad:
            units.setdefault(find(fi), []).append(fi)
        self.unit_of = {fi: min(g) for g in units.values() for fi in g}
        self.units = {min(g): sorted(g) for g in units.values()}
        self.panels = {uid: list(g) for uid, g in self.units.items()}
        self.panel_of = dict(self.unit_of)

    # ---- 展开块：朝向相同的正常面在贴图上连成的一片
    def _build_masks(self):
        """每个像素记下盖住它的正常面属于哪几个朝向组（法线夹角 ≤ 25°），每个朝向组各自做四连通分块。"""
        self.cluster_normals = []
        face_cluster = {}
        for fi, f in enumerate(self.obj.faces):
            if fi in self.bad_set or f.area3 <= MIN_AREA3 or area2(f.T) < BAD_UV_AREA:
                continue
            for c, n in enumerate(self.cluster_normals):
                if dot(f.normal, n) >= CLUSTER_COS:
                    face_cluster[fi] = c
                    break
            else:
                face_cluster[fi] = len(self.cluster_normals)
                self.cluster_normals.append(f.normal)
        self.texel_clusters, self.comp = {}, {}
        for m, (W, H) in self.size.items():
            grid = [[() for _ in range(W)] for _ in range(H)]
            for fi, c in face_cluster.items():
                f = self.obj.faces[fi]
                if f.mat != m:
                    continue
                xs = [p[0] for p in f.T]
                ys = [p[1] for p in f.T]
                for j in range(max(0, int(math.floor(min(ys)))), min(H, int(math.ceil(max(ys))) + 1)):
                    for i in range(max(0, int(math.floor(min(xs)))), min(W, int(math.ceil(max(xs))) + 1)):
                        if c not in grid[j][i] and inside(f.T, i + 0.5, j + 0.5):
                            grid[j][i] = tuple(sorted(grid[j][i] + (c,)))
            self._fill_painted(m, grid)
            comps = {}
            for c in sorted(set(face_cluster.values())):
                comp = [[-1] * W for _ in range(H)]
                cid = 0
                for j in range(H):
                    for i in range(W):
                        if c in grid[j][i] and comp[j][i] < 0:
                            stack = [(i, j)]
                            comp[j][i] = cid
                            while stack:
                                x, y = stack.pop()
                                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                                    if 0 <= nx < W and 0 <= ny < H and c in grid[ny][nx] and comp[ny][nx] < 0:
                                        comp[ny][nx] = cid
                                        stack.append((nx, ny))
                            cid += 1
                comps[c] = comp
            self.texel_clusters[m], self.comp[m] = grid, comps

    def _read_pixels(self, m):
        """贴图像素，按 UV 的方向（第 0 行在最下面）排成 [行][列] = (r, g, b, a)；读不了返回 None"""
        try:
            with open(self.textures[m], 'rb') as f:
                w, h, px = texlib.read_png(f.read())
        except (OSError, ValueError):
            return None
        if (w, h) != self.size[m]:
            return None
        return [px[(h - 1 - j) * w:(h - j) * w] for j in range(h)]

    def _fill_painted(self, m, grid):
        """贴图上画了东西、却没有任何正常面盖住的「洞」（比如自然熔炉正面开口四周那一圈，原作那些面全是坏面），
        归给离它最近的已覆盖像素所在的朝向组。洞 = 没被覆盖的像素连成的、碰不到贴图边缘的一片（四周都是展开块）；
        碰得到边缘的是展开图外面的空白或衬底（纯黑、全透明，或者像矩阵吸收器那样铺满的石砖），不算，展开块不会伸出去。
        洞里也只收「画了东西」的像素（不透明且不是纯黑）。"""
        px = self.pixels[m]
        if px is None:
            return
        W, H = self.size[m]
        hole = [[False] * W for _ in range(H)]
        seen = [[False] * W for _ in range(H)]
        for j0 in range(H):
            for i0 in range(W):
                if grid[j0][i0] or seen[j0][i0]:
                    continue
                part, edge, stack = [], False, [(i0, j0)]
                seen[j0][i0] = True
                while stack:
                    i, j = stack.pop()
                    part.append((i, j))
                    edge = edge or i in (0, W - 1) or j in (0, H - 1)
                    for ni, nj in ((i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)):
                        if 0 <= ni < W and 0 <= nj < H and not grid[nj][ni] and not seen[nj][ni]:
                            seen[nj][ni] = True
                            stack.append((ni, nj))
                if not edge:
                    for i, j in part:
                        hole[j][i] = px[j][i][3] > 0 and any(px[j][i][:3])
        frontier = [(i, j) for j in range(H) for i in range(W) if grid[j][i]]
        while frontier:
            nxt = []
            for i, j in frontier:
                for ni, nj in ((i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)):
                    if 0 <= ni < W and 0 <= nj < H and hole[nj][ni] and not grid[nj][ni]:
                        grid[nj][ni] = grid[j][i]
                        nxt.append((ni, nj))
            frontier = nxt

    def line_color(self, fi, both_sides=False):
        """修之前游戏里这个面看上去的颜色（line_samples 的平均）"""
        samples = self.line_samples(fi, both_sides)
        return [sum(c[i] for c in samples) / len(samples) for i in range(3)]

    def poly_color(self, m, poly):
        """UV 多边形里像素的平均颜色"""
        px = self.pixels[m]
        W, H = self.size[m]
        xs = [p[0] for p in poly]
        ys = [p[1] for p in poly]
        acc, cnt = [0, 0, 0], 0
        y = math.floor(min(ys) * 2) / 2 + 0.25
        while y < max(ys):
            x = math.floor(min(xs) * 2) / 2 + 0.25
            while x < max(xs):
                if inside(poly, x, y):
                    i, j = min(max(int(x), 0), W - 1), min(max(int(y), 0), H - 1)
                    for c in range(3):
                        acc[c] += px[j][i][c]
                    cnt += 1
                x += 0.5
            y += 0.5
        if not cnt:
            x, y = centroid(poly)
            i, j = min(max(int(x), 0), W - 1), min(max(int(y), 0), H - 1)
            return list(px[j][i][:3])
        return [v / cnt for v in acc]

    def same_look(self, m, a, b):
        """斜接合并前后平均颜色差不多（CIELAB 色差 ≤ MERGE_MAX_DE）：斜接把角往对角线方向伸得更远，
        伸进别的花纹（比如水晶成型器顶上紫水晶的角块）就不合并"""
        if self.pixels[m] is None:
            return True
        return texlib.delta_e76(texlib.srgb_to_lab(self.poly_color(m, a)),
                                texlib.srgb_to_lab(self.poly_color(m, b))) <= MERGE_MAX_DE

    def line_samples(self, fi, both_sides=False):
        """修之前这个面实际取到的像素：沿原来那条线（那个点）按游戏的取样方式（最近邻、v 翻转）取 8 × 边数个。
        both_sides：线正好压在像素边界上（差不到 0.1 像素）时两边的像素都算——游戏里取哪一边只看浮点取整
        （原作同一种护角块的侧壁，有的 UV 写成 35.99、有的写成 59.99），拿来比颜色时不能偏向碰巧取到的那边。"""
        f = self.obj.faces[fi]
        px = self.pixels[f.mat]
        W, H = f.size
        out = []
        n = len(f.T)
        for k in range(n):
            a, b = f.T[k], f.T[(k + 1) % n]
            for step in range(8):
                x, y = add(a, mul(sub(b, a), step / 8))
                xs, ys = [x], [y]
                if both_sides:
                    if abs(x - round(x)) < 0.1:
                        xs = [round(x) - 0.5, round(x) + 0.5]
                    if abs(y - round(y)) < 0.1:
                        ys = [round(y) - 0.5, round(y) + 0.5]
                for xx in xs:
                    for yy in ys:
                        i = min(max(int(math.floor(xx)), 0), W - 1)
                        j = H - 1 - min(max(int(math.floor(H - yy)), 0), H - 1)
                        out.append(px[j][i][:3])
        return out

    def _keep_flat(self):
        """原来那条线整条落在同一种颜色里的（线上取到的像素和平均色的 CIELAB 色差都 ≤ FLAT_DE），游戏里画出来就是一块
        纯色，并没有条纹——好些是原作有意拿几何拼出来的花纹：米斯莱熔炉侧面的绿色 X 就是一块块三角形各自塌到绿色或深色上。
        这种面保留原样，但它平滑连着（夹角 ≤ 30°）的坏面也得都是纯色的才留：圆板一圈 16 面侧壁里哪怕有一面的线碰巧落在
        纯色里，也不能单留它一面（反复剔除到稳定）。挤成一点的面照常修。"""
        uniform = set()
        for fi in self.bad:
            f = self.obj.faces[fi]
            n = len(f.T)
            if self.pixels[f.mat] is None or                     not any(length(sub(f.T[(k + 1) % n], f.T[k])) >= COLLAPSED for k in range(n)):
                continue
            labs = [texlib.srgb_to_lab(c) for c in self.line_samples(fi)]
            mean = [sum(c[i] for c in labs) / len(labs) for i in range(3)]
            if max(texlib.delta_e76(c, mean) for c in labs) <= FLAT_DE:
                uniform.add(fi)
        smooth = {fi: set() for fi in self.bad}
        for key in sorted(self.edges, key=lambda k: sorted(k)):
            fs = [fi for fi, _ in self.edges[key] if fi in self.bad_set]
            for i in range(len(fs)):
                for j in range(i + 1, len(fs)):
                    a, b = self.obj.faces[fs[i]], self.obj.faces[fs[j]]
                    if fs[i] != fs[j] and a.mat == b.mat and dot(a.normal, b.normal) > SMOOTH_COS:
                        smooth[fs[i]].add(fs[j])
                        smooth[fs[j]].add(fs[i])
        while True:
            changed = True
            while changed:
                changed = False
                for fi in sorted(uniform):
                    if any(nb not in uniform for nb in smooth[fi]):
                        uniform.discard(fi)
                        changed = True
            # 和保留面同平面叠着的坏面：平滑连着的坏面也都能照搬（或本来就保留）才照搬，否则把叠着的保留面也改成要修，再来一轮
            over = self._kept_overlaps(uniform)
            blocked = [fi for fi in sorted(over) if any(nb not in uniform and nb not in over for nb in smooth[fi])]
            if not blocked:
                break
            for fi in blocked:
                for ki in over[fi]:
                    uniform.discard(ki)
        self.kept = set(uniform)
        for fi in uniform:
            self.result[fi] = 'flat'
        for fi in sorted(over):
            # 逐顶点取叠着的那个保留面上离它最近的顶点的 UV（几个保留面都叠着时取序号最小的）
            k = self.obj.faces[min(over[fi])]
            f = self.obj.faces[fi]
            self.flat_uv[fi] = [k.T[min(range(len(k.P)), key=lambda j: (length(sub(q, k.P[j])), j))] for q in f.P]
            self.kept.add(fi)
            self.result[fi] = 'shared'
        if self.kept:
            self.bad = [fi for fi in self.bad if fi not in self.kept]
            self.bad_set = set(self.bad)

    def _kept_overlaps(self, uniform):
        """要修的坏面 → 和它同一平面、朝向相同、二维投影叠在一起（不算只碰到边）的保留面（同一张贴图）。分桶同 _overlaps"""
        buckets = {}
        for fi in self.bad:
            if fi in uniform:
                continue
            n = self.obj.faces[fi].normal
            buckets.setdefault((round(n[0], 1), round(n[1], 1), round(n[2], 1)), []).append(fi)
        out = {}
        for ki in sorted(uniform):
            k = self.obj.faces[ki]
            n = k.normal
            fs = buckets.get((round(n[0], 1), round(n[1], 1), round(n[2], 1)))
            if not fs:
                continue
            a = (1.0, 0.0, 0.0) if abs(n[0]) < 0.9 else (0.0, 1.0, 0.0)
            u = unit(cross(n, a))
            v = cross(n, u)
            pk = [(dot(q, u), dot(q, v)) for q in k.P]
            dk = dot(n, k.P[0])
            for bi in fs:
                b = self.obj.faces[bi]
                if b.mat != k.mat or dot(n, b.normal) < 0.999 or abs(dot(n, b.P[0]) - dk) > 1e-3:
                    continue
                if polygons_overlap(pk, [(dot(q, u), dot(q, v)) for q in b.P]):
                    out.setdefault(bi, []).append(ki)
        return out

    def _choose_policies(self):
        """共面接的坏面（板子外圈那一圈、整面墙）先延伸还是先折叠，按颜色、整个模型一起定：拿直接挨着共面正常面的坏面各试
        一下两种摆法，看哪种摆出来的平均颜色更接近修之前那条线的颜色（玩家看惯的样子；CIELAB 色差，按面积加权合计），
        折叠明显更接近（小一成以上）才先折叠，否则照平面投影先延伸。辐射仓四面的圆板这样会折叠成金色的整板（原来就是
        金色条纹），水晶控制器的十字板照平面投影是石头底（原来是深青色的条纹，平均下来更接近石头）。
        整个模型一个方向：对称的几处不会因为原作 UV 里 0.01 像素的取整差别一处折一处摊。
        凸棱 / 凹棱接的侧壁一律先折叠（厚边铺边框花纹）：那边按颜色挑试过，护角块的侧壁会一半折一半摊。"""
        score = [0.0, 0.0]
        for fi in self.bad:
            f = self.obj.faces[fi]
            if self.pixels[f.mat] is None:
                continue
            n = len(f.P)
            open_edges = [k for k in range(n) if length(sub(f.T[(k + 1) % n], f.T[k])) >= COLLAPSED]
            done = False
            for k in open_edges or range(n):
                key = frozenset((f.keys[k], f.keys[(k + 1) % n]))
                for ni, _ in self.edges.get(key, ()):
                    nf = self.obj.faces[ni]
                    if done or ni in self.bad_set or ni in self.kept or nf.mat != f.mat or nf.area3 <= MIN_AREA3:
                        continue
                    if self.dihedral(f, nf, f.P[k]) != 0:
                        continue
                    nb = self.neighbor_frame(fi, ni, k)
                    if nb is None:
                        continue
                    uvA, uvB, g_y, ncen = nb
                    want = self.side_region(f.mat, uvA, uvB, g_y, nf.normal, ncen)
                    if want is None:
                        continue
                    target = texlib.srgb_to_lab(self.line_color(fi, both_sides=True))
                    dist = {}
                    for side, gy in (('wrap', mul(g_y, -1)), ('fold', g_y)):
                        laid = self.lay_faces([fi], self.edge_frame(fi, k), uvA, uvB, gy, 1.0)
                        if laid is not None and self.fits(laid, want):
                            col = texlib.srgb_to_lab(self.poly_color(f.mat, laid[0][fi]))
                            dist[side] = texlib.delta_e76(col, target)
                    if len(dist) == 2:
                        score[0] += dist['wrap'] * f.area3
                        score[1] += dist['fold'] * f.area3
                        done = True
        self.coplanar_first = 'fold' if score[1] < 0.9 * score[0] else 'wrap'

    def region_at(self, m, x, y, normal=None):
        """(x, y) 这个像素所在的展开块 (朝向组, 分块)；几个朝向组都盖住它时取和 normal 最接近的；没有就是 None"""
        W, H = self.size[m]
        i, j = int(math.floor(x)), int(math.floor(y))
        if not (0 <= i < W and 0 <= j < H) or not self.texel_clusters[m][j][i]:
            return None
        cs = self.texel_clusters[m][j][i]
        c = cs[0] if normal is None else max(cs, key=lambda c: (dot(normal, self.cluster_normals[c]), -c))
        return c, self.comp[m][c][j][i]

    def in_region(self, m, x, y, want):
        W, H = self.size[m]
        i, j = int(math.floor(x)), int(math.floor(y))
        if not (0 <= i < W and 0 <= j < H):
            return False
        comp = self.comp[m].get(want[0])
        return comp is not None and comp[j][i] == want[1]

    def side_region(self, m, uvA, uvB, direction, normal=None, fallback_point=None):
        """贴着锚边中点、朝 direction 那一侧的像素所在的展开块"""
        mid = mul(add(uvA, uvB), 0.5)
        d = unit(direction)
        for step in (0.25, 0.75):
            r = self.region_at(m, *add(mid, mul(d, step)), normal)
            if r is not None:
                return r
        return self.region_at(m, *fallback_point, normal) if fallback_point is not None else None

    def coverage(self, m, poly, want, on_line=()):
        """多边形采样到的像素里落在展开块 want 里的比例。
        on_line 是落在锚线上的顶点。锚边往里收 ANCHOR_SLACK 再采（锚边本身是邻面的 UV，邻面压到展开块外的那一丝也照样继承），
        一头搭在锚线上的侧边往里收 SIDE_SLACK（避开正好落在像素边界上的采样点），远端的边往外放 -FAR_SLACK 再采——
        远端离展开块边界至少留这么一点，光栅化时边上的像素不会采到块外的颜色。"""
        n = len(poly)
        dists = []
        for i in range(n):
            ends = (i in on_line) + ((i + 1) % n in on_line)
            dists.append(ANCHOR_SLACK if ends == 2 else SIDE_SLACK if ends == 1 else FAR_SLACK)
        core = None
        for _ in range(4):
            core = inset(poly, dists)
            if core is not None:
                break
            dists = [d * 0.5 for d in dists]
        if core is None:
            core = poly
        xs = [p[0] for p in core]
        ys = [p[1] for p in core]
        total = good = 0
        y = math.floor(min(ys) / SAMPLE_STEP) * SAMPLE_STEP + SAMPLE_STEP / 2
        while y < max(ys):
            x = math.floor(min(xs) / SAMPLE_STEP) * SAMPLE_STEP + SAMPLE_STEP / 2
            while x < max(xs):
                if inside(core, x, y):
                    total += 1
                    good += self.in_region(m, x, y, want)
                x += SAMPLE_STEP
            y += SAMPLE_STEP
        # 边上也采：细长的多边形里头可能一个采样点都没有
        for i, a in enumerate(core):
            b = core[(i + 1) % len(core)]
            k = max(1, int(math.ceil(length(sub(b, a)) / SAMPLE_STEP)))
            for s in range(k):
                total += 1
                good += self.in_region(m, *add(a, mul(sub(b, a), s / k)), want)
        return good / total if total else 0.0

    # ---- 当前 UV
    def cur_uv(self, fi):
        return self.uv[fi] if fi in self.uv else self.obj.faces[fi].T

    def usable(self, fi):
        return fi not in self.bad_set or fi in self.uv

    def uv_at(self, fi, key):
        f = self.obj.faces[fi]
        return self.cur_uv(fi)[f.keys.index(key)]

    def dihedral(self, d, n, a):
        """d、n 两个面隔着过 a 点的边：0 共面、1 凸棱、2 凹棱、None 背对背"""
        c = dot(d.normal, n.normal)
        if c > COPLANAR_COS:
            return 0
        if c < -COPLANAR_COS:
            return None
        return 1 if dot(sub(n.center, a), d.normal) < 0 else 2

    def candidates(self, pid):
        """面板的锚边候选：(档位, 邻面是坏面?, -锚边在邻面上的 UV 长度, 坏面, 边, 邻面)，按优先顺序排好"""
        out = []
        members = set(self.panels[pid])
        for fi in self.panels[pid]:
            f = self.obj.faces[fi]
            n = len(f.P)
            open_edges = [k for k in range(n) if length(sub(f.T[(k + 1) % n], f.T[k])) >= COLLAPSED]
            for k in open_edges or range(n):
                key = frozenset((f.keys[k], f.keys[(k + 1) % n]))
                if len(key) < 2:
                    continue
                for ni, kn in self.edges.get(key, ()):
                    if ni in members or ni in self.kept:
                        continue
                    nf = self.obj.faces[ni]
                    if nf.mat != f.mat or nf.area3 <= MIN_AREA3:
                        continue
                    cls = self.dihedral(f, nf, f.P[k])
                    if cls is None:
                        continue
                    uvlen = length(sub(nf.T[(kn + 1) % len(nf.P)], nf.T[kn])) if ni not in self.bad_set else 0.0
                    out.append((cls, 0 if ni not in self.bad_set else 1, -round(uvlen, 3), fi, k, ni))
        out.sort()
        return out

    # ---- 展开
    def neighbor_frame(self, fi, ni, k):
        """以邻面 ni 为锚：锚边两端的 UV（取邻面的）和邻面在垂直于锚边方向上的像素密度 g_y（指向邻面内部）"""
        f, nf = self.obj.faces[fi], self.obj.faces[ni]
        n = len(f.P)
        A, B = f.P[k], f.P[(k + 1) % n]
        uvA, uvB = self.uv_at(ni, f.keys[k]), self.uv_at(ni, f.keys[(k + 1) % n])
        L = length(sub(B, A))
        if L < 1e-9 or length(sub(uvB, uvA)) < 0.2:
            return None
        t_hat = mul(sub(B, A), 1.0 / L)
        g_t = mul(sub(uvB, uvA), 1.0 / L)
        y_n = unit(cross(nf.normal, t_hat))
        if dot(sub(nf.center, A), y_n) < 0:
            y_n = mul(y_n, -1)
        # 按邻面自己的顶点做一维最小二乘：uv ≈ uvA + g_t·t + g_y·y
        nuv = self.cur_uv(ni)
        su = sv = syy = 0.0
        for p, q in zip(nf.P, nuv):
            t = dot(sub(p, A), t_hat)
            y = dot(sub(p, A), y_n)
            r = sub(q, add(uvA, mul(g_t, t)))
            su += r[0] * y
            sv += r[1] * y
            syy += y * y
        g_y = (su / syy, sv / syy) if syy > 1e-12 else (0.0, 0.0)
        gt_len = length(g_t)
        if not (0.3 * gt_len <= length(g_y) <= 3.0 * gt_len):
            # 邻面在这个方向上本身就挤扁了：按锚边的密度、朝邻面 UV 中心那一侧
            perp = unit(rot90(g_t))
            if dot(sub(centroid(nuv), uvA), perp) < 0:
                perp = mul(perp, -1)
            g_y = mul(perp, gt_len)
        return uvA, uvB, g_y, centroid(nuv)

    def edge_frame(self, fi, k):
        """坏面 fi 的第 k 条边当锚线：(A, B, 离开锚线指向坏面内部的方向, A 的位置键, B 的位置键)"""
        f = self.obj.faces[fi]
        n = len(f.P)
        A, B = f.P[k], f.P[(k + 1) % n]
        t_hat = unit(sub(B, A))
        y_d = unit(cross(f.normal, t_hat))
        if dot(sub(f.center, A), y_d) < 0:
            y_d = mul(y_d, -1)
        return A, B, y_d, f.keys[k], f.keys[(k + 1) % n]

    def lay_panel(self, pid, frame, uvA, uvB, g_y, scale):
        return self.lay_faces(self.panels[pid], frame, uvA, uvB, g_y, scale)

    def lay_faces(self, faces, frame, uvA, uvB, g_y, scale):
        """这几个面按锚线摊平：锚线两端取 uvA、uvB，沿锚线方向按两端 UV 等比，离开锚线（frame 给的方向）每单位走
        g_y × scale。返回 ({面: UV 列表}, {面: 落在锚线上的顶点}, 厚度（像素）, 沿锚线方向)；超出贴图返回 None。"""
        A, B, y_d, kA, kB = frame
        L = length(sub(B, A))
        if L < 1e-9 or length(sub(uvB, uvA)) < 0.2:
            return None
        t_hat = mul(sub(B, A), 1.0 / L)
        g_t = mul(sub(uvB, uvA), 1.0 / L)
        uvs, on_edge, depth = {}, {}, 0.0
        gy_len = length(g_y)
        for gi in faces:
            g = self.obj.faces[gi]
            W, H = g.size
            out, on = [], set()
            for i, (p, key) in enumerate(zip(g.P, g.keys)):
                y0 = dot(sub(p, A), y_d)
                if key == kA:
                    q = uvA
                elif key == kB:
                    q = uvB
                else:
                    t = dot(sub(p, A), t_hat)
                    y = y0 * scale
                    depth = max(depth, abs(y) * gy_len)
                    q = add(add(uvA, mul(g_t, t)), mul(g_y, y))
                if not (0.0 <= q[0] <= W and 0.0 <= q[1] <= H):
                    return None
                if abs(y0) < 1e-6:
                    on.add(i)
                out.append(q)
            uvs[gi], on_edge[gi] = out, on
        return uvs, on_edge, depth, unit(g_t)

    def fits(self, laid, want, color=False):
        """摆出来的 UV 都落在展开块 want 里。color：再看颜色——修之前那条线（两边都算）颜色比较一致的面
        （色差散布 ≤ LOOK_SPREAD），摆出来的平均颜色不许和它差太多（≤ LOOK_MAX_DE）：热力产能器顶上齿轮的齿原来是紫的，
        照常接着铺会铺到中间的橙色晶块上。线本来就跨了几种颜色的（护角块的侧壁一半青一半深）不管。"""
        uvs, on_edge = laid[0], laid[1]
        for gi, uv in uvs.items():
            m = self.obj.faces[gi].mat
            if self.coverage(m, uv, want, on_edge[gi]) < 0.999:
                return False
            if color and gi in self.bad_set and self.pixels[m] is not None:
                target, spread = self.look(gi)
                if spread <= LOOK_SPREAD and                         texlib.delta_e76(texlib.srgb_to_lab(self.poly_color(m, uv)), target) > LOOK_MAX_DE:
                    return False
        return True

    def look(self, fi):
        """修之前这个面的样子：(那条线两边像素的平均色（CIELAB）, 各像素离平均色最远的色差)"""
        if fi not in self._look:
            labs = [texlib.srgb_to_lab(c) for c in self.line_samples(fi, both_sides=True)]
            mean = [sum(c[i] for c in labs) / len(labs) for i in range(3)]
            self._look[fi] = (mean, max(texlib.delta_e76(c, mean) for c in labs))
        return self._look[fi]

    def best_scale(self, pid, frame, uvA, uvB, gy, want, scales):
        """这一侧按 scales 从大到小试，返回第一个放得下的 (压扁系数, lay_panel 结果)，都放不下返回 None"""
        for scale in scales:
            laid = self.lay_panel(pid, frame, uvA, uvB, gy, scale)
            if laid is not None and self.fits(laid, want, color=True):
                return scale, laid
        return None

    def try_anchor(self, pid, cls, fi, k, ni):
        nb = self.neighbor_frame(fi, ni, k)
        if nb is None:
            return None
        uvA, uvB, g_y, ncen = nb
        m = self.obj.faces[fi].mat
        # 展开块按邻面那一侧取：延伸出去也必须还在邻面所在的展开块里
        want = self.side_region(m, uvA, uvB, g_y, self.obj.faces[ni].normal, ncen)
        if want is None:
            return None
        first, second = ('wrap', 'fold') if cls == 0 else ('fold', 'wrap')
        if cls == 0 and ni not in self.bad_set and self.coplanar_first == 'fold':
            first, second = 'fold', 'wrap'
        if cls == 0 and ni in self.bad_set:
            # 共面接着一个修过的坏面：只许接着铺，不许再镜像回来（一路镜像下去会铺成一条条来回的带子）
            second = None
        gys = {'fold': g_y, 'wrap': mul(g_y, -1)}
        hi = [x for x in SCALES if x >= PREFER_SCALE]
        lo = [x for x in SCALES if x < PREFER_SCALE]
        # 首选方向压扁不超过 PREFER_SCALE 就用它（对称的几面墙不会因为 UV 差 0.06 像素一面折、一面摊）；
        # 否则两个方向比谁压得少
        frame = self.edge_frame(fi, k)
        pick = self.best_scale(pid, frame, uvA, uvB, gys[first], want, hi)
        side = first
        if pick is None:
            other = self.best_scale(pid, frame, uvA, uvB, gys[second], want, SCALES) if second else None
            rest = self.best_scale(pid, frame, uvA, uvB, gys[first], want, lo)
            if other is not None and (rest is None or other[0] > rest[0]):
                pick, side = other, second
            else:
                pick = rest
        if pick is None:
            return None
        scale, (uvs, on_edge, depth, t_dir) = pick
        return dict(uvs=uvs, on_edge=on_edge, depth=depth, t_dir=t_dir, want=want, info=(cls, side, scale),
                    face=fi, edge=k)

    def try_overlap(self, pid, fi, ni, parallel=False):
        """坏面 fi 和正常面 ni 在同一平面上重叠：整块面板直接套 ni 的平面映射（两层贴图一致，深度打架也看不出来）。
        parallel：ni 和 fi 平行、错开一点、投影过去叠在一起（凹槽底、垫在面板后面的那层）——同样套 ni 的映射，
        等于把正对着的那个面投影过来。ni 也可以是已经修好的坏面（叠在一起的几层拆开以后，后修的那层套先修的那层）。"""
        nf = self.obj.faces[ni]
        T = self.cur_uv(ni)
        n = len(nf.P)
        k = max(range(n), key=lambda i: (length(sub(T[(i + 1) % n], T[i])), -i))
        A, B = nf.P[k], nf.P[(k + 1) % n]
        L = length(sub(B, A))
        uvA, uvB = T[k], T[(k + 1) % n]
        if L < 1e-9 or length(sub(uvB, uvA)) < 0.2:
            return None
        t_hat = mul(sub(B, A), 1.0 / L)
        g_t = mul(sub(uvB, uvA), 1.0 / L)
        y_n = unit(cross(nf.normal, t_hat))
        if dot(sub(nf.center, A), y_n) < 0:
            y_n = mul(y_n, -1)
        su = sv = syy = 0.0
        for p, q in zip(nf.P, T):
            t = dot(sub(p, A), t_hat)
            y = dot(sub(p, A), y_n)
            r = sub(q, add(uvA, mul(g_t, t)))
            su += r[0] * y
            sv += r[1] * y
            syy += y * y
        if syy < 1e-12:
            return None
        g_y = (su / syy, sv / syy)
        want = self.region_at(nf.mat, *centroid(T), nf.normal)
        if want is None:
            return None
        laid = self.lay_panel(pid, (A, B, y_n, nf.keys[k], nf.keys[(k + 1) % n]), uvA, uvB, g_y, 1.0)
        if laid is None or not self.fits(laid, want, color=True):
            return None
        uvs, on_edge, depth, t_dir = laid
        return dict(uvs=uvs, on_edge={gi: set() for gi in uvs}, depth=depth, t_dir=t_dir, want=want,
                    info=(0, 'project' if parallel else 'copy', 1.0), face=fi, edge=None)

    def seed(self, pid):
        """整组都是坏面、没有任何正常邻面可锚（几个模型内部的零件、透过开口看到的炉膛底）。依次试：
          1. 以 UV 最长的那条原始边为锚（UV 用原来的），按这条边的密度朝放得下的一侧摊开，压扁不超过 PREFER_SCALE；
             几个面（重叠的几层）一起的到这里为止，摆不下就由上层拆开；
          2. 平面投影：按这个面的平面、模型正常面的像素密度（放不下就整体等比缩小，纹理变细但不拉长），
             摆在原来那条线 / 那个点附近放得下的地方，几个候选位置里挑颜色最接近修之前的；
          3. 第 1 步压得更扁。"""
        members = self.panels[pid]
        line = None
        for fi in members:
            f = self.obj.faces[fi]
            n = len(f.P)
            for k in range(n):
                ln = length(sub(f.T[(k + 1) % n], f.T[k]))
                if ln >= COLLAPSED * 4 and (line is None or ln > line[0] + 1e-9):
                    line = (ln, fi, k)

        def from_line(scales):
            if line is None:
                return None
            _, fi, k = line
            f = self.obj.faces[fi]
            n = len(f.P)
            uvA, uvB = f.T[k], f.T[(k + 1) % n]
            g_t = mul(sub(uvB, uvA), 1.0 / length(sub(f.P[(k + 1) % n], f.P[k])))
            perp = mul(unit(rot90(g_t)), length(g_t))
            for scale in scales:
                for sign in (1.0, -1.0):
                    gy = mul(perp, sign)
                    want = self.side_region(f.mat, uvA, uvB, gy)
                    if want is None:
                        continue
                    laid = self.lay_panel(pid, self.edge_frame(fi, k), uvA, uvB, gy, scale)
                    if laid is not None and self.fits(laid, want, color=True):
                        uvs, on_edge, depth, t_dir = laid
                        return dict(uvs=uvs, on_edge=on_edge, depth=depth, t_dir=t_dir, want=want,
                                    info=('seed', scale), face=fi, edge=k)
            return None

        r = from_line([x for x in SCALES if x >= PREFER_SCALE])
        if r is not None or len(members) > 1:
            # 几个面（重叠的几层）一起摆不下，就交给上层拆开各自找锚——平面投影只用在单个的面上
            return r
        big = max(members, key=lambda i: (self.obj.faces[i].area3, -i))
        f = self.obj.faces[big]
        # 只算 UV 正常的面：保留的纯色面（kept）不在 bad_set 里、UV 面积却是 0，算进去中位数会被拉低（自然熔炉 62% 是这种面，中位数成了 0）
        dens = sorted(math.sqrt(area2(g.T) / g.area3) for gi, g in enumerate(self.obj.faces)
                      if g.mat == f.mat and gi not in self.bad_set and g.area3 > MIN_AREA3 and area2(g.T) >= BAD_UV_AREA)
        rho = dens[len(dens) // 2] if dens else 16.0
        n = len(f.P)
        k = max(range(n), key=lambda i: length(sub(f.P[(i + 1) % n], f.P[i])))
        t_hat = unit(sub(f.P[(k + 1) % n], f.P[k]))
        y_hat = unit(cross(f.normal, t_hat))
        c3 = f.center
        c2 = centroid([q for gi in members for q in self.obj.faces[gi].T])
        # 修之前这组面看上去的颜色（按面积加权）：附近放得下的几个位置里挑颜色最接近的
        target = None
        if self.pixels[f.mat] is not None:
            acc, wsum = [0.0, 0.0, 0.0], 0.0
            for gi in members:
                w = self.obj.faces[gi].area3
                acc = [a + c * w for a, c in zip(acc, self.line_color(gi))]
                wsum += w
            target = texlib.srgb_to_lab([a / wsum for a in acc])
        for shrink in PATCH_SCALES:
            local = {gi: [(dot(sub(p, c3), t_hat) * rho * shrink, dot(sub(p, c3), y_hat) * rho * shrink)
                          for p in self.obj.faces[gi].P] for gi in members}
            pts = [q for v in local.values() for q in v]
            box = [(min(x for x, _ in pts), min(y for _, y in pts)), (max(x for x, _ in pts), max(y for _, y in pts))]
            mid = mul(add(box[0], box[1]), 0.5)
            corners = [add(c, mul(unit(sub(mid, c)), 0.1)) for c in
                       (box[0], (box[1][0], box[0][1]), box[1], (box[0][0], box[1][1]))]
            found = []
            for r in range(PATCH_RADIUS + 1):
                for dx in range(-r, r + 1):
                    for dy in range(-r, r + 1):
                        if max(abs(dx), abs(dy)) != r or len(found) >= PATCH_CANDIDATES:
                            continue
                        o = (c2[0] + dx, c2[1] + dy)
                        want = self.region_at(f.mat, *o)
                        if want is None:
                            continue
                        # 先粗筛：包围盒四角都得在同一个展开块里，再逐面验
                        if not all(self.in_region(f.mat, *add(o, c), want) for c in corners):
                            continue
                        uvs = {gi: [add(o, q) for q in local[gi]] for gi in members}
                        if not self.fits((uvs, {gi: set() for gi in members}), want):
                            continue
                        de = 0.0
                        if target is not None:
                            acc, wsum = [0.0, 0.0, 0.0], 0.0
                            for gi in members:
                                w = self.obj.faces[gi].area3
                                acc = [a + c * w for a, c in zip(acc, self.poly_color(f.mat, uvs[gi]))]
                                wsum += w
                            de = texlib.delta_e76(target, texlib.srgb_to_lab([a / wsum for a in acc]))
                        found.append((de, r, dx, dy, uvs, want))
                if len(found) >= PATCH_CANDIDATES:
                    break
            if found:
                de, _, _, _, uvs, want = min(found, key=lambda t: t[:4])
                return dict(uvs=uvs, on_edge={gi: set() for gi in members}, depth=0.0, t_dir=(1.0, 0.0),
                            want=want, info=('patch', shrink), face=big, edge=None)
        return from_line([x for x in SCALES if x < PREFER_SCALE])

    def merge(self, placed, locked):
        """同一轮修的两面侧壁（单面的面板）在共享的远端顶点上取两条偏移线的交点（斜接），让一圈侧壁连成一条带。
        只合并连着同一个锚点（UV 也相同）、而且偏移在锚边折线同一侧的两面——一面往里折、一面往外摊的不合并。
        之前几轮已经定下的远端顶点离得很近（≤ LOCK_SNAP）就直接沿用。"""
        far = {}
        for pid, r in placed.items():
            if len(self.panels[pid]) != 1 or r['edge'] is None:
                continue
            fi, k = r['face'], r['edge']
            uv = r['uvs'][fi]
            n = len(uv)
            for i in range(n):
                if i in (k, (k + 1) % n):
                    continue
                if (i - 1) % n == (k + 1) % n:
                    a, b = (k + 1) % n, k
                elif (i + 1) % n == k:
                    a, b = k, (k + 1) % n
                else:
                    continue
                far.setdefault(self.obj.faces[fi].keys[i], []).append((fi, i, uv[i], r['t_dir'], r['depth'], uv[a], uv[b]))
        changed = {}
        for key in sorted(far):
            items = far[key]
            lock = locked.get(key, [])
            if len(items) == 2:
                p, q = items
                lim = MERGE_FACTOR * max(p[4], q[4], 0.4)
                r0 = p[5]
                if (length(sub(p[5], q[5])) < 1e-6 and length(sub(p[2], q[2])) <= lim
                        and cross2(sub(p[6], r0), sub(p[2], r0)) * cross2(sub(q[6], r0), sub(q[2], r0)) < 0):
                    mean = mul(add(p[2], q[2]), 0.5)
                    den = cross2(p[3], q[3])
                    if abs(den) < 1e-6:
                        x = mean
                    else:
                        x = add(p[2], mul(p[3], cross2(sub(q[2], p[2]), q[3]) / den))
                        if length(sub(x, mean)) > 2 * lim:
                            x = mean
                    near = [z for z in lock if length(sub(z, x)) <= LOCK_SNAP]
                    x = near[0] if near else x
                    changed.setdefault(p[0], {})[p[1]] = x
                    changed.setdefault(q[0], {})[q[1]] = x
                    continue
            for it in items:
                near = [z for z in lock if length(sub(z, it[2])) <= LOCK_SNAP]
                if near:
                    changed.setdefault(it[0], {})[it[1]] = near[0]
        return changed

    def unit_count(self, pid):
        return len({self.unit_of[fi] for fi in self.panels[pid]})

    def _split(self, pid, cands, pending, singles=False):
        """面板拆回一个个单元（重叠的几个面始终在一起）；singles 就拆成一个个面"""
        pending.discard(pid)
        cands.pop(pid, None)
        parts = {}
        for fi in self.panels.pop(pid):
            if singles:
                self.unit_of[fi] = fi
            parts.setdefault(self.unit_of[fi], []).append(fi)
        for uid in sorted(parts):
            self.panels[uid] = sorted(parts[uid])
            for fi in parts[uid]:
                self.panel_of[fi] = uid
            cands[uid] = self.candidates(uid)
            pending.add(uid)

    def run(self):
        cands = {pid: self.candidates(pid) for pid in self.panels}
        pending = set(self.panels)
        strict = True
        while pending:
            batch, split = {}, []
            placed_now = set()
            for pid in sorted(pending):
                # 还能指望的候选：邻面是正常面、已经修好，或者还在等的坏面
                cs = [c for c in cands[pid] if c[1] == 0 or self.panel_of[c[5]] in pending or c[5] in self.uv]
                avail = [c for c in cs if self.usable(c[5])]
                members = self.panels[pid]
                if any(nj in placed_now for fi in members for nj in self.overlap_bad.get(fi, ())):
                    # 叠着的那层这一轮刚摆好：等下一轮套它的映射
                    continue
                overlap = [(fi, ni) for fi in members for ni in self.overlap_good.get(fi, ())]
                overlap += [(fi, nj) for fi in members for nj in self.overlap_bad.get(fi, ()) if nj in self.uv and nj not in members]
                parallel = [(fi, ni) for fi in members for ni in self.parallel_good.get(fi, ())]
                # 档位：重叠 -1、共面 0、平行投影 0.5、凸棱 1、凹棱 2
                ranks = sorted({c[0] for c in avail} | ({-1} if overlap else set()) | ({0.5} if parallel else set()))
                if not ranks:
                    continue
                if strict and cs and ranks[0] > min(c[0] for c in cs):
                    continue
                # 从好的档位往差的试，这一档都放不下才看下一档（每档最多试 MAX_TRIES 个）
                best = None
                for rank in ranks:
                    if rank == -1:
                        tries = [lambda fi=fi, ni=ni: self.try_overlap(pid, fi, ni) for fi, ni in overlap]
                    elif rank == 0.5:
                        tries = [lambda fi=fi, ni=ni: self.try_overlap(pid, fi, ni, parallel=True) for fi, ni in parallel]
                    else:
                        tries = [lambda c=c: self.try_anchor(pid, c[0], c[3], c[4], c[5]) for c in avail if c[0] == rank]
                    for attempt in tries[:MAX_TRIES]:
                        r = attempt()
                        if r is None:
                            continue
                        if best is None or r['info'][2] > best['info'][2]:
                            best = r
                        if r['info'][2] >= PREFER_SCALE:
                            break
                    if best is not None:
                        break
                if self.unit_count(pid) > 1 and (best is None or best['info'][2] < PREFER_SCALE):
                    # 整块放不下（要压扁太多）：拆成一个个面各自展开
                    split.append(pid)
                elif best is not None:
                    batch[pid] = best
                    placed_now.update(members)
            for pid in split:
                self._split(pid, cands, pending)
            if not batch and split:
                continue
            if not batch:
                if strict:
                    strict = False
                    continue
                # 一点进展都没有：剩下的都锚不上，挑面积最大的一块先摆（同组其它面下一轮以它为锚）
                pid = max(sorted(pending), key=lambda p: sum(self.obj.faces[i].area3 for i in self.panels[p]))
                r = self.seed(pid)
                if r is None and len(self.panels[pid]) > 1:
                    # 实在摆不下：先拆成单元，单元还摆不下就拆成一个个面（重叠的几层就各管各的了）
                    self._split(pid, cands, pending, singles=self.unit_count(pid) == 1)
                    continue
                pending.discard(pid)
                if r is None:
                    for fi in self.panels[pid]:
                        self.result[fi] = 'left'
                    continue
                batch[pid] = r
            strict = True
            locked = {}
            for fj, uv in self.uv.items():
                for i, (key, q) in enumerate(zip(self.obj.faces[fj].keys, uv)):
                    if i not in self.anchors[fj]:
                        locked.setdefault(key, []).append(q)
            changed = self.merge(batch, locked)
            for pid in sorted(batch):
                r = batch[pid]
                for fi, uv in r['uvs'].items():
                    if fi in changed:
                        trial = list(uv)
                        for i, q in changed[fi].items():
                            trial[i] = q
                        n = len(uv)
                        m = self.obj.faces[fi].mat
                        a0, a1 = signed_area2(uv), signed_area2(trial)
                        if (a0 * a1 > 0 and abs(a1) >= 0.5 * abs(a0)
                                and self.coverage(m, trial, r['want'], r['on_edge'][fi]) >= 0.999
                                and self.same_look(m, uv, trial)):
                            uv = trial
                    self.uv[fi] = uv
                    self.anchors[fi] = r['on_edge'][fi]
                    self.result[fi] = r['info']
                pending.discard(pid)
        return self

    # ---- 输出
    def text(self):
        obj = self.obj
        index = {}
        for i, (u, v) in enumerate(obj.vt):
            index.setdefault((round(u, 6), round(v, 6)), i)
        new_vt = []
        lines = list(obj.lines)
        if lines and lines[-1] == '':
            lines.pop()
        all_uv = dict(self.uv)
        all_uv.update(self.flat_uv)
        for fi in sorted(all_uv):
            f = obj.faces[fi]
            W, H = f.size
            toks = []
            for tok, q in zip(f.tokens, all_uv[fi]):
                key = (round(min(max(q[0] / W, 0.0), 1.0), 6), round(min(max(q[1] / H, 0.0), 1.0), 6))
                if key not in index:
                    index[key] = len(obj.vt) + len(new_vt)
                    new_vt.append(key)
                parts = tok.split('/')
                parts[1] = str(index[key] + 1)
                toks.append('/'.join(parts))
            lines[f.line] = 'f ' + ' '.join(toks)
        lines += EXTRA_TAIL.get(self.name, [])
        if new_vt:
            lines += VT_HEADER
            lines += [f'vt {u:.6f} {v:.6f}' for u, v in new_vt]
        return '\n'.join(lines) + '\n'

    def stats(self):
        faces = self.obj.faces
        total = sum(f.area3 for f in faces)
        degenerate = sorted(self.bad + list(self.kept))
        bad_area = sum(faces[fi].area3 for fi in degenerate)
        by = {}
        for fi in degenerate:
            r = self.result.get(fi)
            if r is None or r == 'left':
                k = 'left'
            elif r in ('flat', 'shared'):
                k = r
            elif r[0] in ('seed', 'patch'):
                k = r[0] + ('' if r[1] == 1.0 else f'x{r[1]}')
            else:
                cls, side, scale = r
                k = f'{CLASS_NAMES[cls]}/{side}' + ('' if scale == 1.0 else f'x{scale}')
            by[k] = by.get(k, 0) + 1
        return len(faces), len(degenerate), 100.0 * bad_area / total if total else 0.0, by


def fixed_text(name):
    """修好的 Fixer（.text() 是要写出的文件内容）"""
    return Fixer(name, original_obj(name)).run()


def original_text(name):
    """原作 + 第八批加的尾巴（修之前游戏里实际用的那份）"""
    text = original_obj(name)
    tail = EXTRA_TAIL.get(name)
    if tail:
        text = text.rstrip('\n') + '\n' + '\n'.join(tail) + '\n'
    return text


def main():
    args = sys.argv[1:]
    if args and args[0] == 'preview':
        preview(args[1], args[2:])
        return
    out_dir = MODEL_DIR
    write = True
    if args and args[0] == 'check':
        write = False
    elif len(args) == 2 and args[0] == '--out':
        out_dir = args[1]
        os.makedirs(out_dir, exist_ok=True)
    elif args:
        raise SystemExit(__doc__)
    for name in MODELS:
        fx = fixed_text(name)
        nf, nb, pct, by = fx.stats()
        print(f'{name:18s} 面 {nf:4d}  坏面 {nb:4d}（表面积 {pct:4.1f}%）  ' + '  '.join(f'{k} {v}' for k, v in sorted(by.items())))
        if write:
            with open(os.path.join(out_dir, name + '.obj'), 'w', encoding='utf-8', newline='\n') as f:
                f.write(fx.text())


# ---------------------------------------------------------------- 离线渲染（对比图）

PREVIEW_VARIANTS = [(n, n, n) for n in MODELS] + [
    ('ultraflowerburner', 'flowerburner', 'ultraflowerburner'),
    ('ultraheatgenerator', 'heatgenerator', 'ultraheatgenerator'),
]
# (方位角, 仰角, 距离系数)：两个斜上、一个仰视、一个俯视，再加一个贴着侧面的低角度近景（看板子厚边、护角侧面）
PREVIEW_VIEWS = [(35, 28, 1.0), (215, 28, 1.0), (125, -20, 1.0), (305, 58, 1.0), (75, 8, 0.72)]


def render_load(text, mtl_name):
    """渲染用：OBJ 文本 + MTL 名 → (顶点, 贴图坐标, 面, {材质: 贴图数组})"""
    import numpy as np
    from PIL import Image
    obj = Obj(text)
    mats = {}
    for m, path in read_mtl(mtl_name + '.mtl').items():
        mats[m] = np.asarray(Image.open(path).convert('RGB'), dtype=np.float32) / 255.0
    return np.array(obj.v, dtype=np.float64), np.array(obj.vt, dtype=np.float64), obj.faces, mats


def render_views(V):
    """按包围盒取 PREVIEW_VIEWS 的机位 → [(眼睛, 看向)]"""
    import numpy as np
    lo, hi = V.min(0), V.max(0)
    c = (lo + hi) / 2
    rad = np.linalg.norm(hi - lo) / 2
    out = []
    for az, el, k in PREVIEW_VIEWS:
        a, e = math.radians(az), math.radians(el)
        d = rad * 3.9 * k
        out.append((c + d * np.array([math.cos(e) * math.sin(a), math.sin(e), math.cos(e) * math.cos(a)]), c))
    return out


def render(V, VT, faces, mats, eye, target, size, fov=30.0, tint=None):
    """透视正确的贴图插值、z 缓冲、背面剔除（按绕序，同游戏）、原版方块按法线方向的明暗；
    贴图最近邻取样，v 按 flip_v 翻转（同游戏）。tint：{面序号: RGB}，把这些面染色（查哪些面改过用）。"""
    import numpy as np
    from PIL import Image
    eye = np.asarray(eye, float)
    fwd = target - eye
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, (0.0, 1.0, 0.0))
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    img = np.empty((size, size, 3), np.float32)
    img[:] = (0.20, 0.22, 0.26)
    zbuf = np.full((size, size), np.inf)
    foc = 0.5 * size / math.tan(math.radians(fov) / 2)
    rel = V - eye
    cx, cy, cz = rel @ right, rel @ up, rel @ fwd
    sx = size / 2 + foc * cx / np.maximum(cz, 1e-6)
    sy = size / 2 - foc * cy / np.maximum(cz, 1e-6)
    first = next(iter(mats.values()))
    for fi, f in enumerate(faces):
        tex = mats.get(f.mat, first)
        th, tw = tex.shape[:2]
        vi, ti = f.vi, f.ti
        P = V[vi]
        n = np.zeros(3)
        for i in range(len(P)):
            n += np.cross(P[i], P[(i + 1) % len(P)])
        ln = np.linalg.norm(n)
        if ln < 1e-12:
            continue
        n /= ln
        if np.dot(n, P[0] - eye) >= 0:
            continue
        x, y, z = n
        light = min(x * x * 0.6 + y * y * ((3.0 + y) / 4.0) + z * z * 0.8, 1.0)
        for a, b, c in [(0, i, i + 1) for i in range(1, len(vi) - 1)]:
            ia, ib, ic = vi[a], vi[b], vi[c]
            za, zb, zc = cz[ia], cz[ib], cz[ic]
            if min(za, zb, zc) <= 0.01:
                continue
            xa, ya, xb, yb, xc, yc = sx[ia], sy[ia], sx[ib], sy[ib], sx[ic], sy[ic]
            area = (xb - xa) * (yc - ya) - (xc - xa) * (yb - ya)
            if abs(area) < 1e-9:
                continue
            x0, x1 = max(int(math.floor(min(xa, xb, xc))), 0), min(int(math.ceil(max(xa, xb, xc))), size - 1)
            y0, y1 = max(int(math.floor(min(ya, yb, yc))), 0), min(int(math.ceil(max(ya, yb, yc))), size - 1)
            if x0 > x1 or y0 > y1:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            w0 = ((xb - gx) * (yc - gy) - (xc - gx) * (yb - gy)) / area
            w1 = ((xc - gx) * (ya - gy) - (xa - gx) * (yc - gy)) / area
            w2 = 1 - w0 - w1
            m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not m.any():
                continue
            w0, w1, w2 = w0[m], w1[m], w2[m]
            zz = 1 / (w0 / za + w1 / zb + w2 / zc)
            ys, xs = np.nonzero(m)
            ys, xs = ys + y0, xs + x0
            near = zz < zbuf[ys, xs]
            if not near.any():
                continue
            ys, xs, zz, w0, w1, w2 = ys[near], xs[near], zz[near], w0[near], w1[near], w2[near]
            (ua, va), (ub, vb), (uc, vc) = VT[ti[a]], VT[ti[b]], VT[ti[c]]
            u = (w0 * ua / za + w1 * ub / zb + w2 * uc / zc) * zz
            v = (w0 * va / za + w1 * vb / zb + w2 * vc / zc) * zz
            px = np.clip(np.floor(u * tw).astype(int), 0, tw - 1)
            py = np.clip(np.floor((1 - v) * th).astype(int), 0, th - 1)
            col = tex[py, px] * light
            if tint is not None and fi in tint:
                col = col * 0.45 + np.asarray(tint[fi], np.float32) * 0.55
            zbuf[ys, xs] = zz
            img[ys, xs] = col
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def preview(out_dir, names):
    """修前（原作）/ 修后各渲染 PREVIEW_VIEWS 那 5 个角度，每个模型（含换贴图的变体）一张 <名字>.png，另拼几张总览 overview_N.png。"""
    from PIL import Image, ImageDraw

    os.makedirs(out_dir, exist_ok=True)
    size = 320
    variants = [v for v in PREVIEW_VARIANTS if not names or v[0] in names]
    sheets = []
    for label, obj_name, mtl_name in variants:
        before = render_load(original_text(obj_name), mtl_name)
        fx = fixed_text(obj_name)
        after = render_load(fx.text(), mtl_name)
        cams = render_views(before[0])
        nf, nb, pct, by = fx.stats()
        sheet = Image.new('RGB', (size * len(cams) + 70, size * 2 + 26), (20, 22, 26))
        d = ImageDraw.Draw(sheet)
        d.text((6, 6), f'{label}  ({obj_name}.obj, {nb}/{nf} faces fixed, {pct:.1f}% of surface)', fill=(255, 230, 120))
        for row, data in enumerate((before, after)):
            d.text((6, 26 + row * size + size // 2), ('before', 'after')[row], fill=(220, 220, 220))
            for col, (eye, c) in enumerate(cams):
                sheet.paste(render(*data, eye, c, size), (70 + col * size, 26 + row * size))
        path = os.path.join(out_dir, label + '.png')
        sheet.save(path)
        sheets.append(path)
        print('wrote', path)
    per = 4
    for s in range(0, len(sheets), per):
        ims = [Image.open(p) for p in sheets[s:s + per]]
        ims = [im.resize((im.width // 2, im.height // 2), Image.LANCZOS) for im in ims]
        ov = Image.new('RGB', (max(im.width for im in ims), sum(im.height for im in ims)), (20, 22, 26))
        y = 0
        for im in ims:
            ov.paste(im, (0, y))
            y += im.height
        path = os.path.join(out_dir, f'overview_{s // per + 1}.png')
        ov.save(path)
        print('wrote', path)


if __name__ == '__main__':
    main()
