import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from hv import *
from buildings import PLAINS_PALS, DESERT_PALS

def hut(desert):
    v = V()
    plank = 'acacia_planks' if desert else 'spruce_planks'
    log = 'stripped_acacia_log' if desert else 'spruce_log'
    roof = 'acacia' if desert else 'spruce'
    for x in range(-2, 3):
        for z in range(-2, 3):
            v.set(x, 0, z, plank)
    for y in range(1, 4):
        for x in range(-2, 3):
            for z in range(-2, 3):
                if abs(x) == 2 and abs(z) == 2: v.log(x, y, z, log)
                elif abs(x) == 2 or abs(z) == 2: v.set(x, y, z, plank if y != 2 or (x + z) % 2 else 'air')
                else: v.air(x, y, z)
    for x in range(-2, 3):
        for z in (-2, 2):
            if v.get(x, 2, z).n == 'air' and abs(x) < 2: v.pane(x, 2, z)
    for z in range(-1, 2):
        for x in (-2, 2):
            if v.get(x, 2, z).n == 'air': v.pane(x, 2, z)
    v.door(0, 1, 2, 'acacia' if desert else 'spruce', 'north')
    # techo a dos aguas con alero
    for k in range(0, 4):
        y = 4 + k; zz = 3 - k
        for x in range(-3, 4):
            if zz > 0:
                v.stair(x, y, -zz, roof, 'south'); v.stair(x, y, zz, roof, 'north')
            else:
                v.slab(x, y, 0, roof, 'bottom')
        if 1 <= k <= 2:
            for x in (-2, 2):
                for z in range(-(2 - k), 3 - k): v.set(x, y, z, plank)
    v.set(1, 1, -1, 'barrel', facing='up', open='false')
    v.set(-1, 1, -1, 'barrel', facing='north', open='false')
    v.set(1, 2, -1, 'lantern', hanging='false', waterlogged='false')
    v.set(-1, 1, 1, 'composter', level='0') if False else v.set(-1, 1, 1, 'cauldron')
    # redes secándose: telarañas no; cuerdas de valla con faroles afuera
    v.fence(3, 1, 1, roof); v.fence(3, 2, 1, roof); v.lantern(3, 3, 1)
    v.core = (0, 1, 3)
    return v

if __name__ == '__main__':
    out = sys.argv[1]
    for name, v in (('plains/fishing_hut', hut(False)), ('desert/fishing_hut', hut(True))):
        p = os.path.join(out, name + '.txt')
        print(name, export(v, p))
    render(hut(False), 'out/hut.png', scale=14, views=(0, 2), ground=(5, 'grass_block'))
