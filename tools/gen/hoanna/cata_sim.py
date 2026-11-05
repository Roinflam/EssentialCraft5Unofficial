# 用法：python cata_sim.py <出口概率>，例：python cata_sim.py 0.1（D13 A；原作 0.0625）。固定跑修过向南走廊、半径上限 112 的 3 000 座
# 第六批 6a 规划时写的（batch6a §4.1、D13）：模拟原作墓穴布局，算「房间数、出口占比、没入口的墓穴比例、半径截到 112 后的箱子总量」。
# 移植后的 Java 布局 worldgen/hoanna/underground/OldCatacombsPieces.layout 修了向南走廊的错位（原作 L316）、加了半径上限 112、出口 1/10；
# 2026-09-29 M6 用 500 个种子复核：房间 14.49 / 座、出口占比 0.0985、没有出口 24.6%（本脚本 3 000 种子 22%），GameTest catacombLayoutBoundedAndConnected 守着。
# 模拟原作 StructureOldCatacombs 的布局（Room 5x5x5，Corridor 5x5x32~63），统计大小、房间数、出口（箱子）数
import random, statistics, sys
def inter(a,b): return a[3]>=b[0] and a[0]<=b[3] and a[5]>=b[2] and a[2]<=b[5] and a[4]>=b[1] and a[1]<=b[4]
def run(rng, fix_south, guard):
    comps=[]  # (kind, bb, extra)
    x=2; y=rng.randint(6,32); z=2
    cx,cz=x+2,z+2
    def ok(bb):
        if guard and max(abs(bb[0]-cx),abs(bb[3]-cx),abs(bb[2]-cz),abs(bb[5]-cz))>guard: return False
        return not any(inter(c[1],bb) for c in comps)
    def room_place(x,y,z,f):
        bb=[x,y,z,x,y+4,z]
        if f=='S': bb[3]=x+4; bb[5]=z+4
        elif f=='W': bb[0]=x-4; bb[5]=z+4
        elif f=='E': bb[3]=x+4; bb[5]=z+4
        else: bb[3]=x+4; bb[2]=z-4
        bb=tuple(bb); return bb if ok(bb) else None
    def corr_place(x,y,z,f):
        bb=[x,y,z,x,y+4,z]; i=rng.randrange(32)+32
        if f=='S': bb[3]=x+4; bb[5]=z+i-1
        elif f=='W': bb[0]=x-i+1; bb[5]=z+4
        elif f=='E': bb[3]=x+i-1; bb[5]=z+4
        else: bb[3]=x+4; bb[2]=z-i+1
        bb=tuple(bb); return bb if ok(bb) else None
    def new_room(idx,bb,frm):
        broken=rng.random()<0.125; grown=rng.random()<0.125; exit_=rng.random()<EXITP
        r=['room',bb,{'idx':idx,'from':frm,'exit':exit_}]; return r
    def build_room(r):
        idx=r[2]['idx']
        if idx>7: return
        b=r[1]
        for f,(px,pz) in (('S',(b[0],b[5]+1)),('W',(b[0]-1,b[2])),('N',(b[0],b[2]-1)),('E',(b[3]+1,b[2]))):
            if r[2]['from']==f: continue
            bb=corr_place(px,b[1],pz,f)
            if bb:
                c=['corr',bb,{'idx':idx+1,'dir':f}]; comps.append(c); build_corr(c)
    def build_corr(c):
        idx=c[2]['idx']
        if idx>6: return
        b=c[1]; f=c[2]['dir']
        if f=='S':
            if fix_south: bb=room_place(b[0],b[1],b[5]+1,'S')
            else: bb=room_place(b[3]+1,b[1],b[2],'S')
            frm='N'
        elif f=='W': bb=room_place(b[0]-1,b[1],b[2],'W'); frm='E'
        elif f=='N': bb=room_place(b[0],b[1],b[2]-1,'N'); frm='S'
        else: bb=room_place(b[3]+1,b[1],b[2],'E'); frm='W'
        if bb:
            r=new_room(idx+1,bb,frm); comps.append(r); build_room(r)
    root=new_room(0,(x,y,z,x+4,y+4,z+4),'D'); comps.append(root); build_room(root)
    ext=max(max(abs(c[1][0]-cx),abs(c[1][3]-cx),abs(c[1][2]-cz),abs(c[1][5]-cz)) for c in comps)
    rooms=sum(1 for c in comps if c[0]=='room'); corrs=len(comps)-rooms
    exits=sum(1 for c in comps if c[0]=='room' and c[2]['exit'])
    blocks=sum((c[1][3]-c[1][0]+1)*(c[1][5]-c[1][2]+1)*5 for c in comps)
    return ext,rooms,corrs,exits,blocks
def stats(fix,guard,n):
    rng=random.Random(777)
    R=[run(rng,fix,guard) for _ in range(n)]
    q=lambda a,p: sorted(a)[min(len(a)-1,int(p*len(a)))]
    print(f"fix_south={fix} guard={guard}")
    for i,name in enumerate(['extent','rooms','corridors','exit_rooms(chests)','block_volume']):
        a=[r[i] for r in R]; print(f"  {name:18s} mean {statistics.mean(a):8.1f} p50 {q(a,.5)} p90 {q(a,.9)} max {max(a)}")
    print("  P(no chest) = %.2f"%(sum(1 for r in R if r[3]==0)/n))
import sys
EXITP=float(sys.argv[1])
stats(True,112,3000)
