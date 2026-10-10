"""Generador de edificios humanos (aldeas de llanura y desierto) con bloques vanilla.
Coordenadas: x derecha, z hacia la puerta (+z = sur), y=0 = piso (reemplaza la capa de suelo). La puerta mira a +z.
core = punto parado justo afuera de la puerta (y=1). Exporta el mismo formato que las plantillas del Dominio."""
import math, random
import numpy as np
from PIL import Image, ImageDraw

M='minecraft:'
FACE={'north':(0,-1),'south':(0,1),'east':(1,0),'west':(-1,0)}
def ccw(f): return {'north':'west','west':'south','south':'east','east':'north'}[f]
def cw(f): return {'north':'east','east':'south','south':'west','west':'north'}[f]
def opp(f): return {'north':'south','south':'north','east':'west','west':'east'}[f]

class S:
    __slots__=('id','p')
    def __init__(self,id,**p): self.id=id if ':' in id else M+id; self.p=dict(p)
    def key(self): return self.id+('['+','.join(f'{k}={v}' for k,v in sorted(self.p.items()))+']' if self.p else '')
    @property
    def n(self): return self.id.split(':')[1]

NONFULL_SUFFIX=('_stairs','_slab','_fence','_fence_gate','_pane','_wall','_door','_trapdoor','_bed','_carpet','_sign','_banner',
                '_button','_pressure_plate','_torch','_lantern','_candle','_sapling','_flower','_tulip','_head','_rod')
NONFULL={'air','water','lantern','torch','wall_torch','glass_pane','iron_bars','ladder','chain','flower_pot','composter','lectern',
         'brewing_stand','grindstone','stonecutter','cauldron','water_cauldron','anvil','bell','farmland','dirt_path','wheat','carrots',
         'potatoes','beetroots','short_grass','tall_grass','dandelion','poppy','cornflower','azure_bluet','oxeye_daisy','hay_block_x',
         'campfire','cactus','dead_bush','scaffolding','enchanting_table','end_rod','sea_pickle','potted_cactus','potted_red_tulip',
         'potted_dandelion','potted_fern','soul_lantern','snow','moss_carpet','cobweb','sweet_berry_bush','pointed_dripstone'}
def full(s):
    if s is None: return False
    n=s.n
    if n in NONFULL: return False
    if n.endswith('_slab'): return s.p.get('type')=='double'
    if n.startswith('potted_'): return False
    return not n.endswith(NONFULL_SUFFIX)

class V:
    def __init__(self): self.b={}; self.core=(0,1,0); self.meta={}
    def set(self,x,y,z,s,**p):
        if isinstance(s,str): s=S(s,**p)
        self.b[(int(x),int(y),int(z))]=s
    def get(self,x,y,z): return self.b.get((x,y,z))
    def air(self,x,y,z): self.set(x,y,z,'air')
    def box(self,x0,y0,z0,x1,y1,z1,s,**p):
        for x in range(min(x0,x1),max(x0,x1)+1):
            for y in range(min(y0,y1),max(y0,y1)+1):
                for z in range(min(z0,z1),max(z0,z1)+1): self.set(x,y,z,s,**p)
    def hollow(self,x0,y0,z0,x1,y1,z1,s,**p):
        for x in range(x0,x1+1):
            for y in range(y0,y1+1):
                for z in range(z0,z1+1):
                    if x in (x0,x1) or z in (z0,z1): self.set(x,y,z,s,**p)
    def stair(self,x,y,z,mat,facing,half='bottom'): self.set(x,y,z,mat+'_stairs',facing=facing,half=half,shape='straight',waterlogged='false')
    def slab(self,x,y,z,mat,t='bottom'): self.set(x,y,z,mat+'_slab',type=t,waterlogged='false')
    def fence(self,x,y,z,mat): self.set(x,y,z,mat+'_fence',north='false',south='false',east='false',west='false',waterlogged='false')
    def wall(self,x,y,z,mat): self.set(x,y,z,mat+'_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false')
    def pane(self,x,y,z,id='glass_pane'): self.set(x,y,z,id,north='false',south='false',east='false',west='false',waterlogged='false')
    def log(self,x,y,z,mat,axis='y'): self.set(x,y,z,mat,axis=axis)
    def door(self,x,y,z,mat,facing,hinge='left',open_='false'):
        self.set(x,y,z,mat+'_door',facing=facing,half='lower',hinge=hinge,open=open_,powered='false')
        self.set(x,y+1,z,mat+'_door',facing=facing,half='upper',hinge=hinge,open=open_,powered='false')
    def bed(self,x,y,z,color,facing):
        """La cabecera queda en (x,z) y los pies hacia el lado opuesto a facing."""
        dx,dz=FACE[facing]
        self.set(x,y,z,color+'_bed',facing=facing,part='head',occupied='false')
        self.set(x-dx,y,z-dz,color+'_bed',facing=facing,part='foot',occupied='false')
    def trapdoor(self,x,y,z,mat,facing,half='bottom',open_='true'):
        self.set(x,y,z,mat+'_trapdoor',facing=facing,half=half,open=open_,powered='false',waterlogged='false')
    def lantern(self,x,y,z,hanging=False): self.set(x,y,z,'lantern',hanging='true' if hanging else 'false',waterlogged='false')

def hsh(x,y,z,s=0):
    h=(x*73856093)^(y*19349663)^(z*83492791)^(s*2654435761)
    h&=0xffffffff; h^=h>>13; h=(h*0x5bd1e995)&0xffffffff; h^=h>>15
    return (h&0xffff)/65536.0

def post(v):
    for (x,y,z),s in list(v.b.items()):
        n=s.n
        if n.endswith('_wall') and 'up' in s.p:
            for f,(dx,dz) in FACE.items():
                nb=v.get(x+dx,y,z+dz)
                con=nb is not None and (nb.n.endswith('_wall') or full(nb) or nb.n.endswith('pane') or nb.n=='iron_bars')
                if con:
                    ab=v.get(x,y+1,z)
                    s.p[f]='tall' if ab is not None and (full(ab) or ab.n.endswith('_wall')) else 'low'
                else: s.p[f]='none'
            ns=s.p['north']!='none' and s.p['south']!='none' and s.p['east']=='none' and s.p['west']=='none'
            ew=s.p['east']!='none' and s.p['west']!='none' and s.p['north']=='none' and s.p['south']=='none'
            ab=v.get(x,y+1,z)
            s.p['up']='false' if (ns or ew) and not (ab is not None and ab.n!='air') else 'true'
        elif n.endswith('_fence'):
            for f,(dx,dz) in FACE.items():
                nb=v.get(x+dx,y,z+dz)
                con=nb is not None and (nb.n.endswith('_fence') or full(nb) or
                        (nb.n.endswith('_fence_gate') and (nb.p.get('facing') in ('north','south'))==(f in ('east','west'))))
                s.p[f]='true' if con else 'false'
        elif n.endswith('pane') or n=='iron_bars':
            for f,(dx,dz) in FACE.items():
                nb=v.get(x+dx,y,z+dz)
                s.p[f]='true' if nb is not None and (nb.n.endswith('pane') or nb.n=='iron_bars' or nb.n.endswith('_wall') or full(nb)) else 'false'
    def isst(s): return s is not None and s.n.endswith('_stairs')
    for (x,y,z),s in list(v.b.items()):
        if not isst(s): continue
        f=s.p['facing']; h=s.p['half']
        def take(face):
            dx,dz=FACE[face]; b=v.get(x+dx,y,z+dz)
            return not isst(b) or b.p['facing']!=f or b.p['half']!=h
        dx,dz=FACE[f]; b=v.get(x+dx,y,z+dz)
        shape='straight'
        if isst(b) and b.p['half']==h:
            f1=b.p['facing']
            if (FACE[f1][0]==0)!=(FACE[f][0]==0) and take(opp(f1)):
                shape='outer_left' if f1==ccw(f) else 'outer_right'
        if shape=='straight':
            dx,dz=FACE[opp(f)]; b=v.get(x+dx,y,z+dz)
            if isst(b) and b.p['half']==h:
                f2=b.p['facing']
                if (FACE[f2][0]==0)!=(FACE[f][0]==0) and take(f2):
                    shape='inner_left' if f2==ccw(f) else 'inner_right'
        s.p['shape']=shape

def export(v,path):
    post(v)
    pal={}; rows=[]
    for (x,y,z),s in sorted(v.b.items(),key=lambda t:(t[0][1],t[0][0],t[0][2])):
        k=s.key()
        if k not in pal: pal[k]=len(pal)
        rows.append(f'{x} {y} {z} {pal[k]}')
    with open(path,'w') as f:
        c=v.core
        f.write(f'core {c[0]} {c[1]} {c[2]}\n')
        for k,i in sorted(pal.items(),key=lambda t:t[1]): f.write(f'P {k}\n')
        f.write('\n'.join(rows)+'\n')
    return len(rows),len(pal)

# ------------------------------------------------------------------ render isométrico de verificación
BASECOL={
 'cobblestone':(118,118,118),'mossy_cobblestone':(100,118,92),'stone_bricks':(124,124,124),'stone':(125,125,125),
 'oak_planks':(162,131,79),'spruce_planks':(114,84,48),'dark_oak_planks':(66,43,20),'birch_planks':(196,179,123),'jungle_planks':(160,115,80),
 'oak_log':(109,85,50),'spruce_log':(58,37,16),'dark_oak_log':(60,46,26),'stripped_oak_log':(177,144,86),'stripped_spruce_log':(115,89,52),
 'stripped_dark_oak_log':(96,76,49),'birch_log':(216,215,210),
 'white_terracotta':(209,178,161),'terracotta':(152,94,67),'orange_terracotta':(161,83,37),'red_terracotta':(143,61,46),
 'bricks':(150,97,83),'sandstone':(216,203,155),'smooth_sandstone':(223,214,170),'cut_sandstone':(217,206,159),
 'chiseled_sandstone':(216,202,154),'red_sandstone':(186,99,29),'glass_pane':(190,220,230),'white_wool':(233,236,236),
 'red_wool':(160,39,34),'blue_wool':(53,57,157),'yellow_wool':(248,197,39),'green_wool':(84,109,27),'hay_block':(166,139,12),
 'farmland':(110,70,40),'water':(50,90,200),'wheat':(200,180,60),'carrots':(80,160,40),'potatoes':(90,150,50),'beetroots':(120,90,60),
 'grass_block':(90,140,60),'dirt':(134,96,67),'dirt_path':(148,122,65),'lantern':(255,200,100),'torch':(255,210,90),'bookshelf':(120,90,60),
 'barrel':(130,95,55),'composter':(120,85,45),'lectern':(170,130,80),'smoker':(90,85,80),'blast_furnace':(100,100,105),
 'grindstone':(140,140,140),'smithing_table':(60,50,60),'stonecutter':(130,130,130),'cauldron':(70,70,75),'brewing_stand':(120,110,90),
 'loom':(160,130,100),'fletching_table':(200,180,130),'cartography_table':(140,100,70),'anvil':(70,70,75),'crafting_table':(140,100,60),
 'furnace':(110,110,110),'chest':(160,115,50),'bell':(240,200,60),'iron_bars':(140,140,140),'ladder':(150,120,70),'campfire':(240,120,40),
 'gold_block':(246,208,61),'cactus':(80,130,50),'packed_mud':(142,106,79),'mud_bricks':(137,103,79),'acacia_planks':(168,90,50),
 'acacia_log':(103,96,86),'stripped_acacia_log':(174,92,59),'oak_leaves':(60,110,40),'quartz_block':(235,230,225),
 'calcite':(223,224,220),'white_concrete':(207,213,214),'diorite':(188,188,188),'mushroom_stem':(203,196,185),'stripped_birch_wood':(196,176,118),
 'smooth_sandstone':(223,214,170),'granite':(149,103,85),'deepslate_tiles':(54,54,55),'deepslate_tile':(54,54,55),'cobbled_deepslate':(77,77,80),
 'mud_brick':(137,103,79),'mud_bricks':(137,103,79),'stripped_dark_oak_log':(96,76,49),'stripped_spruce_log':(115,89,52),'andesite':(136,136,136),
 'water_cauldron':(70,70,120),'smoker':(90,85,80),'red_wool':(160,39,34),'light_blue_wool':(58,175,217),'green_wool':(84,109,27),
 'yellow_terracotta':(186,133,35),'cyan_terracotta':(86,91,91),'pink_terracotta':(161,78,78),'light_blue_terracotta':(113,108,137),
 'lime_terracotta':(103,117,53),'green_terracotta':(76,83,42),'light_gray_concrete':(125,125,115),'white_terracotta':(209,178,161),
 'mossy_stone_bricks':(115,121,105),'cracked_stone_bricks':(118,117,118),'chiseled_stone_bricks':(119,118,119),'polished_andesite':(132,134,133),
 'oak_leaves':(60,110,40),'dark_oak_leaves':(50,90,30),'iron_bars':(140,140,140),'hay_block':(166,139,12),'chain':(60,60,70),
 'smooth_stone':(158,158,158),'tuff':(108,109,102),'polished_deepslate':(72,72,73),'deepslate_bricks':(70,70,71),'blackstone':(42,35,40),
 'cut_sandstone':(217,206,159),'chiseled_sandstone':(216,202,154),'terracotta':(152,94,67),'orange_terracotta':(161,83,37),
 'red_terracotta':(143,61,46),'white_wool':(233,236,236),'orange_wool':(240,118,19),'cyan_wool':(21,137,145),'jungle_log':(85,67,25),
 'stripped_jungle_log':(171,132,84),'stripped_acacia_log':(174,92,59),'acacia_planks':(168,90,50),'jungle_planks':(160,115,80),
 'smooth_quartz':(236,230,223),'quartz_bricks':(234,229,221),'packed_mud':(142,106,79),'flowering_azalea_leaves':(110,130,70),
 'moss_block':(89,109,45),'grass_block':(90,140,60),'potted_red_tulip':(150,60,40),
}
BASECOL.update({'sand':(219,207,163),'cut_red_sandstone':(186,99,29),'smooth_red_sandstone':(181,98,31),'chiseled_red_sandstone':(183,96,27),
'chiseled_quartz_block':(231,226,218),'jungle_leaves':(60,140,40),'purple_wool':(122,42,173),'decorated_pot':(140,80,60),'lightning_rod':(200,120,90),
'gravel':(130,125,122),'melon':(110,145,30),'oxidized_cut_copper':(80,155,125),'red_stained_glass_pane':(160,50,50),'yellow_stained_glass_pane':(220,200,60),'light_blue_stained_glass_pane':(110,160,210),'white_stained_glass_pane':(235,235,235),'calcite':(223,224,220),'mushroom_stem':(203,196,185),'carved_pumpkin':(200,120,20),'pumpkin':(200,120,20),'candle':(230,210,170),'chain':(60,60,70),'light_gray_concrete':(125,125,115),'white_concrete':(207,213,214),'lime_terracotta':(103,117,52),'pink_terracotta':(161,78,78),'green_terracotta':(76,83,42),'cyan_terracotta':(87,91,91),'quartz_pillar':(235,229,222),'diorite':(188,188,188),'granite':(149,103,85),'calcite':(223,224,220)})

def colof(s):
    n=s.n
    if n in BASECOL: return BASECOL[n]
    for suf in ('_stairs','_slab','_fence_gate','_fence','_wall','_door','_trapdoor','_carpet','_pane','_bed','_pressure_plate','_log','_wood'):
        if n.endswith(suf):
            base=n[:-len(suf)]
            for cand in (base,base+'_planks',base+'s',base.replace('_brick','_bricks'),base+'_wool',base+'_block',base+'_log','stripped_'+base+'_log',base.replace('stripped_','')+'_planks'):
                if cand in BASECOL: return BASECOL[cand]
            if suf=='_bed': return BASECOL.get(base+'_wool',(200,50,50))
            if suf=='_carpet': return BASECOL.get(base+'_wool',(180,180,180))
    if n.startswith('potted') or n=='flower_pot': return (130,70,50)
    return (200,0,200)

def boxes(s):
    n=s.n
    if n.endswith('_slab'):
        t=s.p.get('type','bottom'); return [(0,0,0,1,1,1)] if t=='double' else [(0,0.5,0,1,1,1)] if t=='top' else [(0,0,0,1,0.5,1)]
    if n.endswith('_stairs'):
        top=s.p.get('half')=='top'
        base=(0,0.5,0,1,1,1) if top else (0,0,0,1,0.5,1)
        y0,y1=(0,0.5) if top else (0.5,1)
        f=s.p['facing']; dx,dz=FACE[f]
        x0,x1=(0.5,1) if dx>0 else (0,0.5) if dx<0 else (0,1)
        z0,z1=(0.5,1) if dz>0 else (0,0.5) if dz<0 else (0,1)
        return [base,(x0,y0,z0,x1,y1,z1)]
    if n.endswith('_fence') or n.endswith('_wall') or n.endswith('pane') or n=='iron_bars':
        w=0.375 if n.endswith('_fence') else (0.25 if n.endswith('_wall') else 0.44)
        out=[(w,0,w,1-w,1,1-w)]
        for f,(dx,dz) in FACE.items():
            if s.p.get(f) in ('true','low','tall'):
                out.append((w if dx==0 else (0.5 if dx>0 else 0),0.1,w if dz==0 else (0.5 if dz>0 else 0),
                            1-w if dx==0 else (1 if dx>0 else 0.5),0.9,1-w if dz==0 else (1 if dz>0 else 0.5)))
        return out
    if n.endswith('_door'):
        f=s.p['facing']; dx,dz=FACE[f]
        if s.p.get('open')=='true': return [(0,0,0,1,1,1)] if False else [(0,0,0,0.19,1,1)]
        return [(0,0,0.81,1,1,1)] if f=='north' else [(0,0,0,1,1,0.19)] if f=='south' else [(0.81,0,0,1,1,1)] if f=='west' else [(0,0,0,0.19,1,1)]
    if n.endswith('_trapdoor'):
        if s.p.get('open')=='true':
            f=s.p['facing']
            return [(0,0,0.81,1,1,1)] if f=='north' else [(0,0,0,1,1,0.19)] if f=='south' else [(0.81,0,0,1,1,1)] if f=='west' else [(0,0,0,0.19,1,1)]
        return [(0,0.81,0,1,1,1)] if s.p.get('half')=='top' else [(0,0,0,1,0.19,1)]
    if n.endswith('_carpet'): return [(0,0,0,1,0.06,1)]
    if n.endswith('_bed'): return [(0,0,0,1,0.56,1)]
    if n in ('lantern','torch','flower_pot') or n.startswith('potted'): return [(0.3,0,0.3,0.7,0.5,0.7)]
    if n in ('wheat','carrots','potatoes','beetroots','short_grass'): return [(0.1,0,0.1,0.9,0.6,0.9)]
    if n=='farmland' or n=='dirt_path': return [(0,0,0,1,0.94,1)]
    if n=='ladder': return [(0,0,0.85,1,1,1)]
    if n in ('lectern','brewing_stand','grindstone','stonecutter','cauldron','anvil','composter','bell','campfire'): return [(0.1,0,0.1,0.9,0.8,0.9)]
    return [(0,0,0,1,1,1)]

def render(v,fname,scale=10,views=(0,2),ground=None):
    if ground:
        g=V(); g.b=dict(v.b)
        R,gid=ground
        for x in range(-R,R+1):
            for z in range(-R,R+1):
                if (x,0,z) not in g.b: g.b[(x,0,z)]=S(gid)
        v=g
    post(v)
    imgs=[]
    for view in views:
        def rot(x,z):
            for _ in range(view): x,z=-z,x
            return x,z
        def proj(x,y,z):
            x,z=rot(x,z); return ((x-z)*0.866*scale,(-y+(x+z)*0.5)*scale)
        faces=[]
        for (x,y,z),s in v.b.items():
            if s.n=='air': continue
            col=np.array(colof(s),float)
            bx=boxes(s); isfull=bx==[(0,0,0,1,1,1)]
            for (a0,b0,c0,a1,b1,c1) in bx:
                X0,Y0,Z0,X1,Y1,Z1=x+a0,y+b0,z+c0,x+a1,y+b1,z+c1
                cand=[('top',[(X0,Y1,Z0),(X1,Y1,Z0),(X1,Y1,Z1),(X0,Y1,Z1)],(0,1,0)),
                      ('px',[(X1,Y0,Z0),(X1,Y1,Z0),(X1,Y1,Z1),(X1,Y0,Z1)],(1,0,0)),
                      ('nx',[(X0,Y0,Z0),(X0,Y1,Z0),(X0,Y1,Z1),(X0,Y0,Z1)],(-1,0,0)),
                      ('pz',[(X0,Y0,Z1),(X1,Y0,Z1),(X1,Y1,Z1),(X0,Y1,Z1)],(0,0,1)),
                      ('nz',[(X0,Y0,Z0),(X1,Y0,Z0),(X1,Y1,Z0),(X0,Y1,Z0)],(0,0,-1))]
                for name,q,nrm in cand:
                    nx,nz=rot(nrm[0],nrm[2])
                    if nrm[1]==0 and not (nx>0 or nz>0): continue
                    if isfull:
                        nb=v.get(x+nrm[0],y+nrm[1],z+nrm[2])
                        if nb is not None and full(nb): continue
                    sh=1.0 if nrm[1]==1 else (0.8 if nx>0 else 0.62)
                    c=col*sh*(0.93+0.14*hsh(x,y,z))
                    cx=sum(p[0] for p in q)/4; cy=sum(p[1] for p in q)/4; cz=sum(p[2] for p in q)/4
                    rx,rz=rot(cx,cz)
                    faces.append((rx+rz+cy,[proj(*p) for p in q],tuple(np.clip(c,0,255).astype(int))))
        faces.sort(key=lambda t:t[0])
        allp=[p for f in faces for p in f[1]]
        mnx=min(p[0] for p in allp); mxx=max(p[0] for p in allp); mny=min(p[1] for p in allp); mxy=max(p[1] for p in allp)
        img=Image.new('RGB',(int(mxx-mnx)+30,int(mxy-mny)+30),(160,190,215)); d=ImageDraw.Draw(img)
        for dep,poly,c in faces:
            d.polygon([(p[0]-mnx+15,p[1]-mny+15) for p in poly],fill=c,outline=tuple(max(0,int(k*0.8)) for k in c))
        imgs.append(img)
    W=sum(i.width for i in imgs); H=max(i.height for i in imgs)
    o=Image.new('RGB',(W,H),(160,190,215)); xo=0
    for i in imgs: o.paste(i,(xo,0)); xo+=i.width
    o.save(fname); return o
