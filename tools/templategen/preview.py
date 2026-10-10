import sys; sys.path.insert(0,'.')
from kit import *
from PIL import Image
def sheet(items, fname, scale=18, ground=8, gid='grass_block'):
    ims=[]
    for v in items:
        ims.append(render(v,'out/_.png',scale=scale,views=(0,),ground=(ground,gid)))
    W=sum(i.width for i in ims); H=max(i.height for i in ims)
    o=Image.new('RGB',(W,H),(160,190,215)); x=0
    for i in ims: o.paste(i,(x,H-i.height)); x+=i.width
    o.save(fname); return o.size
