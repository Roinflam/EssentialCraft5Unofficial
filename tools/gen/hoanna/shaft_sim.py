# 用法：python shaft_sim.py
# 第六批 6a 规划时写的（batch6a §4.1、D13）：模拟原作现代矿井布局（≈ 原版 1.12 废弃矿井）。
# 注意：这一份原样带着原作「大厅第 4 圈支路从 minX − 1 出、朝西，东边永远没有支路」的 bug（L570）；
# 修过的版本是同目录 shaft_fix.py（每座约 127 件、91 段走廊、约 5 辆矿车、2.6 个刷怪笼、53% 向东有支路），
# Java 布局 worldgen/hoanna/underground/ModernShaftPieces.layout 照修过的写，GameTest shaftLayoutWithinReferenceRange 守着。
# 模拟原作 StructureModernShaftPieces（近似原版 1.12 废弃矿井），统计大小、走廊段数、箱子矿车、刷怪笼、虚空石/栅栏数
import random, statistics
def inter(a,b): return a[3]>=b[0] and a[0]<=b[3] and a[5]>=b[2] and a[2]<=b[5] and a[4]>=b[1] and a[1]<=b[4]
def run(rng):
    comps=[]
    def isect(bb): return any(inter(c['bb'],bb) for c in comps)
    x=2;z=2
    room={'t':'room','bb':(x,50,z,x+7+rng.randrange(6),54+rng.randrange(6),z+7+rng.randrange(6)),'idx':0}
    comps.append(room); parent=room
    def get_next(x,y,z,f,idx):
        if idx>8 or abs(x-parent['bb'][0])>80 or abs(z-parent['bb'][2])>80: return None
        c=rand_comp(x,y,z,f,idx+1)
        if c: comps.append(c); build(c)
        return c
    def rand_comp(x,y,z,f,idx):
        j=rng.randrange(100)
        if j>=80:
            bb=[x,y,z,x,y+2,z]
            if rng.randrange(4)==0: bb[4]+=4
            if f=='S': bb[0]=x-1;bb[3]=x+3;bb[5]=z+4
            elif f=='W': bb[0]=x-4;bb[2]=z-1;bb[5]=z+3
            elif f=='N': bb[0]=x-1;bb[3]=x+3;bb[2]=z-4
            else: bb[3]=x+4;bb[2]=z-1;bb[5]=z+3
            bb=tuple(bb)
            return None if isect(bb) else {'t':'cross','bb':bb,'f':f,'idx':idx,'multi':bb[4]-bb[1]+1>3}
        elif j>=70:
            bb=[x,y-5,z,x,y+2,z]
            if f=='S': bb[3]=x+2;bb[5]=z+8
            elif f=='W': bb[0]=x-8;bb[5]=z+2
            elif f=='N': bb[3]=x+2;bb[2]=z-8
            else: bb[3]=x+8;bb[5]=z+2
            bb=tuple(bb)
            return None if isect(bb) else {'t':'stairs','bb':bb,'f':f,'idx':idx}
        else:
            i1=rng.randrange(3)+2
            bb=None
            while i1>0:
                j1=i1*5; b=[x,y,z,x,y+2,z]
                if f=='S': b[3]=x+2;b[5]=z+j1-1
                elif f=='W': b[0]=x-(j1-1);b[5]=z+2
                elif f=='N': b[3]=x+2;b[2]=z-(j1-1)
                else: b[3]=x+j1-1;b[5]=z+2
                b=tuple(b)
                if not isect(b): bb=b;break
                i1-=1
            if not bb: return None
            rails=rng.randrange(3)==0; spiders=(not rails) and rng.randrange(23)==0
            n=(bb[3]-bb[0]+1)//5 if f in 'WE' else (bb[5]-bb[2]+1)//5
            return {'t':'corr','bb':bb,'f':f,'idx':idx,'rails':rails,'spiders':spiders,'sections':n}
    def build(c):
        b=c['bb'];i=c['idx']
        if c['t']=='corr':
            j=rng.randrange(4); f=c['f']
            if f=='N':
                if j<=1: get_next(b[0],b[1]-1+rng.randrange(3),b[2]-1,'N',i)
                elif j==2: get_next(b[0]-1,b[1]-1+rng.randrange(3),b[2],'W',i)
                else: get_next(b[3]+1,b[1]-1+rng.randrange(3),b[2],'E',i)
            elif f=='S':
                if j<=1: get_next(b[0],b[1]-1+rng.randrange(3),b[5]+1,'S',i)
                elif j==2: get_next(b[0]-1,b[1]-1+rng.randrange(3),b[5]-3,'W',i)
                else: get_next(b[3]+1,b[1]-1+rng.randrange(3),b[5]-3,'E',i)
            elif f=='W':
                if j<=1: get_next(b[0]-1,b[1]-1+rng.randrange(3),b[2],'W',i)
                elif j==2: get_next(b[0],b[1]-1+rng.randrange(3),b[2]-1,'N',i)
                else: get_next(b[0],b[1]-1+rng.randrange(3),b[5]+1,'S',i)
            else:
                if j<=1: get_next(b[3]+1,b[1]-1+rng.randrange(3),b[2],'E',i)
                elif j==2: get_next(b[3]-3,b[1]-1+rng.randrange(3),b[2]-1,'N',i)
                else: get_next(b[3]-3,b[1]-1+rng.randrange(3),b[5]+1,'S',i)
            if i<8:
                if f in 'WE':
                    k=b[0]+3
                    while k+3<=b[3]:
                        l=rng.randrange(5)
                        if l==0: get_next(k,b[1],b[2]-1,'N',i+1)
                        elif l==1: get_next(k,b[1],b[5]+1,'S',i+1)
                        k+=5
                else:
                    k=b[2]+3
                    while k+3<=b[5]:
                        l=rng.randrange(5)
                        if l==0: get_next(b[0]-1,b[1],k,'W',i+1)
                        elif l==1: get_next(b[3]+1,b[1],k,'E',i+1)
                        k+=5
        elif c['t']=='cross':
            f=c['f']
            if f=='N': get_next(b[0]+1,b[1],b[2]-1,'N',i); get_next(b[0]-1,b[1],b[2]+1,'W',i); get_next(b[3]+1,b[1],b[2]+1,'E',i)
            elif f=='S': get_next(b[0]+1,b[1],b[5]+1,'S',i); get_next(b[0]-1,b[1],b[2]+1,'W',i); get_next(b[3]+1,b[1],b[2]+1,'E',i)
            elif f=='W': get_next(b[0]+1,b[1],b[2]-1,'N',i); get_next(b[0]+1,b[1],b[5]+1,'S',i); get_next(b[0]-1,b[1],b[2]+1,'W',i)
            else: get_next(b[0]+1,b[1],b[2]-1,'N',i); get_next(b[0]+1,b[1],b[5]+1,'S',i); get_next(b[3]+1,b[1],b[2]+1,'E',i)
            if c['multi']:
                if rng.random()<.5: get_next(b[0]+1,b[1]+4,b[2]-1,'N',i)
                if rng.random()<.5: get_next(b[0]-1,b[1]+4,b[2]+1,'W',i)
                if rng.random()<.5: get_next(b[3]+1,b[1]+4,b[2]+1,'E',i)
                if rng.random()<.5: get_next(b[0]+1,b[1]+4,b[5]+1,'S',i)
        elif c['t']=='stairs':
            f=c['f']
            if f=='S': get_next(b[0],b[1],b[5]+1,'S',i)
            elif f=='W': get_next(b[0]-1,b[1],b[2],'W',i)
            elif f=='N': get_next(b[0],b[1],b[2]-1,'N',i)
            else: get_next(b[3]+1,b[1],b[2],'E',i)
        elif c['t']=='room':
            xs=b[3]-b[0]+1; zs=b[5]-b[2]+1; k=(b[4]-b[1]+1)-3-1
            if k<=0: k=1
            for (size,fn) in ((xs,lambda j:get_next(b[0]+j,b[1]+rng.randrange(k)+1,b[2]-1,'N',i)),(xs,lambda j:get_next(b[0]+j,b[1]+rng.randrange(k)+1,b[5]+1,'S',i)),(zs,lambda j:get_next(b[0]-1,b[1]+rng.randrange(k)+1,b[2]+j,'W',i)),(zs,lambda j:get_next(b[0]-1,b[1]+rng.randrange(k)+1,b[2]+j,'W',i))):
                j=0
                while j<size:
                    j+=rng.randrange(size)
                    if j+3>size: break
                    fn(j); j+=4
    build(room)
    cx=(room['bb'][0]+room['bb'][3])//2; cz=(room['bb'][2]+room['bb'][5])//2
    ext=max(max(abs(c['bb'][0]-cx),abs(c['bb'][3]-cx),abs(c['bb'][2]-cz),abs(c['bb'][5]-cz)) for c in comps)
    corr=[c for c in comps if c['t']=='corr']
    secs=sum(c['sections'] for c in corr)
    return ext,len(comps),len(corr),secs,sum(1 for c in corr if c['spiders']),secs*0.02
rng=random.Random(99); R=[run(rng) for _ in range(3000)]
q=lambda a,p: sorted(a)[min(len(a)-1,int(p*len(a)))]
for i,n in enumerate(['extent','pieces','corridors','sections','spider_corridors(spawners)','expected_chest_minecarts']):
    a=[r[i] for r in R]; print(f"{n:28s} mean {statistics.mean(a):7.2f} p50 {q(a,.5)} p90 {q(a,.9)} max {max(a)}")
