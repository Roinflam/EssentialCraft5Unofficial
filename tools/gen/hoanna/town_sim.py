# 模拟原作 MapGenTown / StructureTownPieces 的布局（只算包围盒，不放方块），统计小镇大小
# 用法：python town_sim.py <座数> <修分叉 0|1> <离起点的格数上限，原作 600>，例：python town_sim.py 2000 0 600
# 第六批 6a 规划时写的（batch6a §4.1、D15）；移植后的 Java 布局 worldgen/hoanna/town/HoannaTownPieces.layout 照同一算法，
# 2026-09-29 用 2 万个区块核过：件数 23.3 / 23.4、塔 3.24 / 3.25、道路 6.83 / 6.88、最远 55 / 54 格（Java / 本脚本），GameTest townLayoutMatchesOriginal 守着。
import random, statistics, sys
N,S,W,E='N','S','W','E'
def orient(x,y,z,sx,sy,sz,f):
    if f==N: return (x, y, z-sz+1, x+sx-1, y+sy-1, z)
    if f==W: return (x-sz+1, y, z, x, y+sy-1, z+sx-1)
    if f==E: return (x, y, z, x+sz-1, y+sy-1, z+sx-1)
    return (x,y,z,x+sx-1,y+sy-1,z+sz-1)
def inter(a,b): return a[3]>=b[0] and a[0]<=b[3] and a[5]>=b[2] and a[2]<=b[5] and a[4]>=b[1] and a[1]<=b[4]
class P:
    def __init__(s,t,bb,f,idx,floors=0): s.t=t;s.bb=bb;s.f=f;s.idx=idx;s.floors=floors
def run(rng, road_fix=False, rng_limit=600, weights_scale=1.0):
    comps=[]
    x=2;z=2
    start=P('Start',(x,64,z,x+5,78,z+5),rng.choice([N,S,W,E]),0)
    comps.append(start)
    wl=[['House4',4,30,0],['Tower',20,10,0],['House1',20,30,0],['Explosion',3,100,0],['Spreader',15,60,0],['House2',15,30,0],['House3',8,30,0]]
    state={'last':None}
    roads=[];houses=[]
    def isect(bb): return any(inter(c.bb,bb) for c in comps)
    def create(name,x,y,z,f,idx):
        if name=='House4':
            fl=rng.randrange(3)+1; bb=orient(x,y,z,15,5*fl+1,15,f); return None if isect(bb) else P(name,bb,f,idx,fl)
        if name=='Tower':
            bb=orient(x,y,z,9,64,9,f); return None if isect(bb) else P(name,bb,f,idx)
        if name=='House1':
            fl=rng.randrange(2)+1; bb=orient(x,y,z,11,fl*5+1,11,f); return None if isect(bb) else P(name,bb,f,idx,fl)
        if name=='Explosion':
            bb=orient(x,y,z,5,5,5,f); return None if isect(bb) else P(name,bb,f,idx)
        if name=='Spreader':
            bb=orient(x,y,z,3,7,3,f); return None if isect(bb) else P(name,bb,f,idx)
        if name=='House2':
            fl=rng.randrange(5)+1; bb=orient(x,y,z,9,fl*5+1,9,f); return None if isect(bb) else P(name,bb,f,idx,fl)
        if name=='House3':
            fl=rng.randrange(9)+1; bb=orient(x,y,z,7,fl*5+1,7,f); return None if isect(bb) else P(name,bb,f,idx,fl)
    def gen_comp(x,y,z,f,idx):
        flag=any(w[3]<w[2] for w in wl)
        j1=sum(w[1] for w in wl) if flag else -1
        if j1<=0: return None
        for k in range(5):
            l1=rng.randrange(j1)
            for w in list(wl):
                l1-=w[1]
                if l1<0:
                    if not (w[3]<w[2]) or (w is state['last'] and len(wl)>1): break
                    t=create(w[0],x,y,z,f,idx)
                    if t:
                        w[3]+=1; state['last']=w
                        if not (w[3]<w[2]): wl.remove(w)
                        return t
        bb=orient(x,y,z,3,4,3,f)
        if not isect(bb): return P('Torch',bb,f,idx)
        return None
    def add_comp(x,y,z,f,idx):
        if idx>500 or abs(x-start.bb[0])>rng_limit or abs(z-start.bb[2])>rng_limit: return None
        t=gen_comp(x,y,z,f,idx+1)
        if t: comps.append(t); houses.append(t)
        return t
    def add_road(x,y,z,f,idx):
        if idx>50 or abs(x-start.bb[0])>rng_limit or abs(z-start.bb[2])>rng_limit: return None
        bb=None
        i1=7*(rng.randrange(3)+3)
        while i1>=7:
            b=orient(x,y,z,5,5,i1,f)
            if not isect(b): bb=b;break
            i1-=7
        if bb and bb[1]>10:
            p=P('Road',bb,f,idx); comps.append(p); roads.append(p); return p
        return None
    def NN(p,off):
        b=p.bb
        if p.f in (S,N): return add_comp(b[0]-1,b[1],b[2]+off,W,p.idx)
        return add_comp(b[0]+off,b[1],b[2]-1,N,p.idx)
    def PP(p,off):
        b=p.bb
        if p.f in (S,N): return add_comp(b[3]+1,b[1],b[2]+off,E,p.idx)
        return add_comp(b[0]+off,b[1],b[5]+1,S,p.idx)
    def build_road(p):
        b=p.bb; length=max(b[3]-b[0]+1,b[5]-b[2]+1); flag=False
        i=rng.randrange(18)
        while i<length-8:
            c=NN(p,i)
            if c: i+=max(c.bb[3]-c.bb[0]+1,c.bb[5]-c.bb[2]+1); flag=True
            i+=2+rng.randrange(18)
        i=rng.randrange(18)
        while i<length-8:
            c=PP(p,i)
            if c: i+=max(c.bb[3]-c.bb[0]+1,c.bb[5]-c.bb[2]+1); flag=True
            i+=2+rng.randrange(18)
        if flag and rng.randrange(18)>0:
            if p.f==S: add_road(b[0]-1,b[1],b[5]-2,W,p.idx)
            elif p.f==W: add_road(b[0],b[1],b[2]-1,(N if road_fix else S),p.idx)
            elif p.f==N: add_road(b[0]-1,b[1],b[2],W,p.idx)
            else: add_road(b[3]-2,b[1],b[2]-1,(N if road_fix else S),p.idx)
        if flag and rng.randrange(18)>0:
            if p.f==S: add_road(b[3]+1,b[1],b[5]-2,E,p.idx)
            elif p.f==W: add_road(b[0],b[1],b[5]+1,(S if road_fix else N),p.idx)
            elif p.f==N: add_road(b[3]+1,b[1],b[2],E,p.idx)
            else: add_road(b[3]-2,b[1],b[5]+1,(S if road_fix else N),p.idx)
    b=start.bb
    add_road(b[0]-1,b[4]-4,b[2]+1,W,0); add_road(b[3]+1,b[4]-4,b[2]+1,E,0)
    add_road(b[0]+1,b[4]-4,b[2]-1,N,0); add_road(b[0]+1,b[4]-4,b[5]+1,S,0)
    while roads or houses:
        if not roads:
            houses.pop(rng.randrange(len(houses)))
        else:
            build_road(roads.pop(rng.randrange(len(roads))))
    return comps
def stats(fix, n=3000, lim=600):
    rng=random.Random(12345)
    res=[]
    for _ in range(n):
        comps=run(rng, fix, lim)
        cx=5;cz=5
        ext=max(max(abs(c.bb[0]-cx),abs(c.bb[3]-cx),abs(c.bb[2]-cz),abs(c.bb[5]-cz)) for c in comps)
        cnt={}
        for c in comps: cnt[c.t]=cnt.get(c.t,0)+1
        nonroad=sum(v for k,v in cnt.items() if k!='Road')
        # 原作房子里的爆炸期望数：(2r+1)^2 * 5 * sum_{i=0..F} i/(10F)
        rad={'House1':5,'House2':4,'House3':3,'House4':7}
        expl=0.0; blocks=0
        for c in comps:
            if c.t in rad:
                r=rad[c.t];F=c.floors
                expl+=(2*r+1)**2*5*sum(i/(10*F) for i in range(F+1))
        res.append((ext,cnt,nonroad>2,expl))
    valid=[r for r in res if r[2]]
    print(f"road_fix={fix} limit={lim}: valid {len(valid)}/{n}")
    exts=sorted(r[0] for r in valid)
    q=lambda a,p: a[min(len(a)-1,int(p*len(a)))]
    print(" extent(max |dx|,|dz| from center) p50/p90/p99/max:", q(exts,.5),q(exts,.9),q(exts,.99),exts[-1])
    keys=['Road','Torch','House1','House2','House3','House4','Tower','Spreader','Explosion']
    for k in keys:
        a=sorted(r[1].get(k,0) for r in valid)
        print(f"  {k:9s} mean {statistics.mean(a):6.2f} p90 {q(a,.9)} max {a[-1]}")
    tot=sorted(sum(r[1].values()) for r in valid)
    print("  total pieces mean %.1f p90 %d max %d"%(statistics.mean(tot),q(tot,.9),tot[-1]))
    ex=[r[3] for r in valid]
    print("  expected house explosions per town mean %.0f"%statistics.mean(ex))
    print("  tower chests per town mean %.2f"%(4*statistics.mean(r[1].get('Tower',0) for r in valid)))
stats(sys.argv[2]=="1", n=int(sys.argv[1]), lim=int(sys.argv[3]))


