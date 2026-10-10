"""Edificios de las aldeas humanas. Dos culturas: llanura (entramado de madera, techo a dos aguas) y desierto
(arenisca, techo plano con parapeto y terraza)."""
import sys, os, random
sys.path.insert(0, os.path.dirname(__file__))
from hv import *

# --------------------------------------------------------------------- paletas
PLAINS_PALS = [
    dict(found='cobblestone', floor='spruce_planks', frame='spruce_log', fill='white_terracotta', roof='dark_oak', ridge='dark_oak_planks',
         door='spruce', trap='spruce', fence='spruce', bed='red', carpet='red'),
    dict(found='cobblestone', floor='oak_planks', frame='oak_log', fill='oak_planks', roof='spruce', ridge='spruce_planks',
         door='oak', trap='oak', fence='oak', bed='blue', carpet='blue'),
    dict(found='stone_bricks', floor='spruce_planks', frame='stripped_spruce_log', fill='bricks', roof='spruce', ridge='spruce_planks',
         door='spruce', trap='spruce', fence='spruce', bed='green', carpet='green'),
    dict(found='cobblestone', floor='birch_planks', frame='dark_oak_log', fill='white_terracotta', roof='spruce', ridge='spruce_planks',
         door='dark_oak', trap='dark_oak', fence='dark_oak', bed='yellow', carpet='yellow'),
]
DESERT_PALS = [
    dict(found='cut_sandstone', floor='smooth_sandstone', wall='smooth_sandstone', trim='cut_sandstone', accent='chiseled_sandstone',
         band='orange_terracotta', door='jungle', trap='jungle', bed='orange', carpet='orange', awning='orange'),
    dict(found='cut_sandstone', floor='cut_sandstone', wall='sandstone', trim='smooth_sandstone', accent='chiseled_sandstone',
         band='terracotta', door='acacia', trap='acacia', bed='red', carpet='red', awning='red'),
    dict(found='sandstone', floor='smooth_sandstone', wall='smooth_sandstone', trim='cut_sandstone', accent='chiseled_sandstone',
         band='white_terracotta', door='birch', trap='birch', bed='cyan', carpet='cyan', awning='cyan'),
]

WS = {  # estación de trabajo por oficio
    'farmer': ('composter', {}), 'fisherman': ('barrel', {'facing': 'up', 'open': 'false'}),
    'shepherd': ('loom', {'facing': 'south'}), 'fletcher': ('fletching_table', {}),
    'librarian': ('lectern', {'facing': 'south', 'has_book': 'false', 'powered': 'false'}),
    'cartographer': ('cartography_table', {}),
    'cleric': ('brewing_stand', {'has_bottle_0': 'false', 'has_bottle_1': 'false', 'has_bottle_2': 'false'}),
    'armorer': ('blast_furnace', {'facing': 'south', 'lit': 'false'}),
    'weaponsmith': ('grindstone', {'face': 'floor', 'facing': 'south'}),
    'toolsmith': ('smithing_table', {}), 'butcher': ('smoker', {'facing': 'south', 'lit': 'false'}),
    'leatherworker': ('cauldron', {}), 'mason': ('stonecutter', {'facing': 'south'}),
}

# --------------------------------------------------------------------- llanura
def plains_house(hw, hd, floors, pal, seed, job=None, height=4, stained=None):
    """Casa de entramado: muros en |x|<=hw, |z|<=hd; techo a dos aguas con alero de 1 (cumbrera a lo largo de x)."""
    rng = random.Random(seed)
    v = V()
    H = height * floors
    # piso y cimiento
    for x in range(-hw, hw + 1):
        for z in range(-hd, hd + 1):
            edge = abs(x) == hw or abs(z) == hd
            v.set(x, 0, z, pal['found'] if edge else pal['floor'])
    # muros
    for y in range(1, H + 1):
        for x in range(-hw, hw + 1):
            for z in range(-hd, hd + 1):
                if not (abs(x) == hw or abs(z) == hd):
                    v.air(x, y, z)
                    continue
                corner = abs(x) == hw and abs(z) == hd
                beam = (y % height == 0)
                post_ = corner or (abs(z) == hd and x % 4 == 0 and abs(x) < hw) or (abs(x) == hw and z % 4 == 0 and abs(z) < hd)
                if corner or (post_ and not beam):
                    v.log(x, y, z, pal['frame'])
                elif beam:
                    v.log(x, y, z, pal['frame'], 'x' if abs(z) == hd else 'z')
                elif y == 1 and floors == 1 and pal['fill'] != 'bricks':
                    v.set(x, y, z, pal['found'])
                else:
                    v.set(x, y, z, pal['fill'])
    # pisos superiores
    for f in range(1, floors):
        y = height * f
        for x in range(-hw + 1, hw):
            for z in range(-hd + 1, hd):
                v.set(x, y, z, pal['floor'])
    # ventanas: en cada muro, entre postes, a la altura 2 (y 2+height en el piso de arriba)
    for f in range(floors):
        wy = 2 + f * height
        for x in range(-hw + 1, hw):
            for z in (-hd, hd):
                if x % 4 == 0 or (z == hd and abs(x) <= 1 and f == 0): continue
                if (x + 100) % 4 in (2,) or (hw <= 3 and abs(x) == 2):
                    v.pane(x, wy, z, stained or 'glass_pane')
                    if height >= 5: v.pane(x, wy + 1, z, stained or 'glass_pane')
        for z in range(-hd + 1, hd):
            for x in (-hw, hw):
                if z % 4 == 0: continue
                if (z + 100) % 4 == 2 or (hd <= 3 and abs(z) == 2):
                    v.pane(x, wy, z, stained or 'glass_pane')
                    if height >= 5: v.pane(x, wy + 1, z, stained or 'glass_pane')
    # puerta
    v.door(0, 1, hd, pal['door'], 'north')
    v.log(0, 3, hd, pal['frame'], 'x') if height == 4 else None
    v.lantern(1 if hw > 2 else 0, 1, hd + 1)  # farol junto a la puerta
    # techo
    ridge_k = hd + 1
    for k in range(0, ridge_k + 1):
        y = H + k
        zz = hd + 1 - k
        for x in range(-hw - 1, hw + 2):
            if zz > 0:
                v.stair(x, y, -zz, pal['roof'], 'south')
                v.stair(x, y, zz, pal['roof'], 'north')
            else:
                v.slab(x, y, 0, pal['roof'], 'bottom')
                v.set(x, y - 1, 0, pal['ridge']) if k > 0 else None
        # frontón: bajo la fila k el muro llega hasta |z| <= hd-k
        if 1 <= k <= hd:
            for x in (-hw, hw):
                for z in range(-(hd - k), hd - k + 1):
                    v.set(x, y, z, pal['fill'] if pal['fill'] != 'white_terracotta' or z % 3 else pal['frame'].replace('_log', '_planks').replace('stripped_', ''))
    # aleros bajo la primera fila (escalones invertidos en los frontones)
    for z in range(-hd, hd + 1):
        for x in (-hw - 1, hw + 1):
            if (x, H, z) not in v.b: pass
    # aire interior bajo el techo
    for k in range(1, ridge_k + 1):
        for x in range(-hw + 1, hw):
            for z in range(-(hd - k) + 1, hd - k):
                if (x, H + k, z) not in v.b: v.air(x, H + k, z)
    # escalera entre pisos
    if floors > 1:
        lx, lz = -hw + 1, -hd + 1
        for y in range(1, H - height + 2):
            v.set(lx, y, lz, 'ladder', facing='south', waterlogged='false')
        v.air(lx, height, lz)
        v.set(lx, height, lz, 'ladder', facing='south', waterlogged='false')
    # interior
    furnish_plains(v, hw, hd, floors, height, pal, rng, job)
    v.core = (0, 1, hd + 1)
    return v

def furnish_plains(v, hw, hd, floors, height, pal, rng, job):
    beds = 1 if job else 2
    top = (floors - 1) * height  # piso de los dormitorios
    # camas contra el muro norte del último piso
    for i in range(beds):
        bx = hw - 1 - 2 * i
        if bx <= -hw: break
        v.bed(bx, top + 1, -hd + 1, pal['bed'], 'north')
    # mesa de trabajo/barril y luz
    v.set(-hw + 1, 1, hd - 1, 'crafting_table') if (-hw + 1, 1, hd - 1) not in [(-hw + 1, 1, -hd + 1)] else None
    v.set(hw - 1, 1, hd - 1, 'barrel', facing='up', open='false')
    v.lantern(hw - 1, 2, hd - 1)
    if floors > 1 or not job:
        v.set(-hw + 1, top + 1, 0 if hd > 2 else -hd + 1, 'chest', facing='east', type='single', waterlogged='false') if hd > 2 else None
    # alfombra
    for x in range(-1, 2):
        for z in range(-1, 2):
            if v.get(x, 1, z) is not None and v.get(x, 1, z).n == 'air' and not (job and x == 0 and z == -hd + 1):
                v.set(x, 1, z, pal['carpet'] + '_carpet')
    if floors > 1:
        v.lantern(0, top + 1, hd - 1)
    if job:
        ws, props = WS[job]
        v.set(0, 1, -hd + 1, ws, **props)
        decor_job(v, hw, hd, job)
    # macetas en ventanas del frente
    if rng.random() < 0.7:
        v.set(-hw + 1, 1, -hd + 1, 'potted_red_tulip') if v.get(-hw + 1, 1, -hd + 1) is None or v.get(-hw + 1, 1, -hd + 1).n == 'air' else None

def decor_job(v, hw, hd, job):
    s = lambda x, z, id, **p: v.set(x, 1, z, id, **p)
    if job == 'librarian':
        for x in range(-hw + 1, hw):
            if x != 0: v.set(x, 1, -hd + 1, 'bookshelf'); v.set(x, 2, -hd + 1, 'bookshelf')
    elif job == 'farmer':
        s(-hw + 1, 0, 'hay_block', axis='y'); s(-hw + 1, -1, 'hay_block', axis='x')
    elif job == 'fisherman':
        s(-hw + 1, 0, 'barrel', facing='up', open='false'); s(-hw + 1, -1, 'barrel', facing='north', open='false')
    elif job == 'shepherd':
        s(-hw + 1, 0, 'white_wool'); s(-hw + 1, -1, 'red_wool'); v.set(-hw + 1, 2, 0, 'yellow_wool')
    elif job == 'fletcher':
        s(-hw + 1, 0, 'target', power='0') if False else s(-hw + 1, 0, 'hay_block', axis='y')
    elif job == 'cartographer':
        s(-hw + 1, 0, 'cartography_table'); s(-hw + 1, -1, 'bookshelf')
    elif job == 'butcher':
        s(-hw + 1, 0, 'smoker', facing='east', lit='false'); s(-hw + 1, -1, 'barrel', facing='up', open='false')
    elif job == 'leatherworker':
        s(-hw + 1, 0, 'barrel', facing='up', open='false')
    elif job == 'mason':
        s(-hw + 1, 0, 'stone_bricks'); s(-hw + 1, -1, 'chiseled_stone_bricks')
    elif job in ('armorer', 'weaponsmith', 'toolsmith'):
        s(-hw + 1, 0, 'anvil', facing='east'); s(-hw + 1, -1, 'furnace', facing='east', lit='false')
    elif job == 'cleric':
        s(-hw + 1, 0, 'bookshelf'); s(hw - 1, 0, 'bookshelf')

def plains_smithy(pal, seed, job):
    """Taller de herrero: casa + ala abierta con fragua, yunque y chimenea de piedra."""
    v = plains_house(3, 3, 1, pal, seed, job)
    # ala abierta al este: x 4..7, techo de losas sostenido por troncos
    for x in range(4, 8):
        for z in range(-3, 4):
            v.set(x, 0, z, 'cobblestone')
            for y in range(1, 4):
                if (x, y, z) not in v.b: v.air(x, y, z)
    for x, z in ((7, -3), (7, 3), (7, 0)):
        for y in range(1, 4): v.log(x, y, z, pal['frame'])
    for x in range(4, 9):
        for z in range(-4, 5):
            v.slab(x, 4, z, 'cobblestone' if x < 8 else pal['roof'], 'bottom') if x < 8 else v.slab(x, 4, z, 'spruce', 'bottom')
    # fragua: bloque de piedra con fogata, chimenea
    v.set(6, 1, -2, 'stone_bricks'); v.set(5, 1, -2, 'blast_furnace', facing='south', lit='false')
    v.set(6, 1, -1, 'campfire', facing='south', lit='true', signal_fire='false', waterlogged='false')
    for y in range(2, 8): v.set(6, y, -2, 'cobblestone' if y < 7 else 'cobblestone_wall');
    v.set(5, 1, 1, 'anvil', facing='east'); v.set(5, 1, 2, 'smithing_table'); v.set(6, 1, 2, 'grindstone', face='floor', facing='south')
    v.set(4, 1, 2, 'barrel', facing='up', open='false'); v.lantern(7, 4 - 1, 0)
    return v

def plains_temple(pal, seed):
    """Templo del clérigo: nave alta con vitrales y campanario."""
    p = dict(pal); p['fill'] = 'stone_bricks'; p['found'] = 'cobblestone'
    v = plains_house(4, 6, 1, p, seed, 'cleric', height=6, stained='yellow_stained_glass_pane')
    # campanario sobre la puerta
    for y in range(7, 14):
        for x in (-1, 1):
            for z in (5, 7):
                v.set(x, y, z, 'stone_bricks' if y < 11 or y == 13 else 'air')
    for x in (-1, 0, 1):
        for z in (5, 6, 7):
            if y: v.set(x, 13, z, 'stone_bricks')
            for y in (11, 12):
                if abs(x) == 1 and z in (5, 7): v.set(x, y, z, 'stone_brick_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')
    v.set(0, 12, 6, 'bell', attachment='ceiling', facing='north', powered='false')
    for k, (r) in enumerate((2, 1, 0)):
        for x in range(-r, r + 1):
            for z in range(6 - r, 6 + r + 1):
                v.set(x, 14 + k, z, 'stone_bricks' if r == 0 else 'stone_brick_slab', type='bottom', waterlogged='false') if r else v.set(x, 14 + k, z, 'stone_brick_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')
    for y in range(7, 11):
        v.set(0, y, 6, 'stone_bricks'); v.set(-1, y, 6, 'stone_bricks'); v.set(1, y, 6, 'stone_bricks')
    # altar
    v.set(0, 1, -5, 'brewing_stand', has_bottle_0='false', has_bottle_1='false', has_bottle_2='false')
    v.set(-1, 1, -5, 'candle', candles='3', lit='true', waterlogged='false') if False else None
    for z in range(-3, 4, 2):
        for x in (-3, -2, 2, 3):
            v.stair(x, 1, z, 'spruce', 'south')
    return v

def plains_farm(seed):
    rng = random.Random(seed)
    v = V()
    crops = ['wheat', 'carrots', 'potatoes', 'beetroots']
    a, b = rng.sample(crops, 2)
    for x in range(-4, 5):
        for z in range(-4, 5):
            if abs(x) == 4 or abs(z) == 4:
                v.log(x, 0, z, 'oak_log', 'x' if abs(z) == 4 and abs(x) != 4 else 'z' if abs(x) == 4 and abs(z) != 4 else 'y')
                continue
            if x == 0:
                v.set(x, 0, z, 'water', level='0'); continue
            v.set(x, 0, z, 'farmland', moisture='7')
            c = a if x < 0 else b
            age = rng.randint(2, 3 if c == 'beetroots' else 7)
            v.set(x, 1, z, c, age=str(age))
    v.set(4, 1, 4, 'composter', level='3')
    v.set(-4, 1, 4, 'lantern', hanging='false', waterlogged='false')
    v.set(0, 0, 4, 'oak_log', axis='x')
    v.core = (0, 1, 5)
    return v

def plains_well():
    v = V()
    for x in range(-2, 3):
        for z in range(-2, 3):
            ring = abs(x) == 2 or abs(z) == 2
            for y in range(-4, 1):
                if ring or y == -4: v.set(x, y, z, 'cobblestone' if (x * 7 + z * 3 + y) % 5 else 'mossy_cobblestone')
                else: v.set(x, y, z, 'water', level='0')
            if ring: v.set(x, 1, z, 'cobblestone' if (x + z) % 3 else 'mossy_cobblestone')
    for x, z in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        for y in (2, 3): v.fence(x, y, z, 'oak')
    for x in range(-2, 3):
        for z in range(-2, 3):
            v.set(x, 4, z, 'oak_planks') if abs(x) < 2 and abs(z) < 2 else None
    for i in range(-3, 4):
        for (x, z, f) in ((i, -3, 'south'), (i, 3, 'north')):
            v.stair(x, 4, z, 'spruce', f)
        if abs(i) < 3:
            v.stair(-3, 4, i, 'spruce', 'east'); v.stair(3, 4, i, 'spruce', 'west')
    for x in range(-2, 3):
        for z in range(-2, 3):
            if abs(x) == 2 or abs(z) == 2: v.stair(x, 5, z, 'spruce', 'south' if z == -2 else 'north' if z == 2 else 'east' if x == -2 else 'west')
            else: v.set(x, 5, z, 'spruce_planks')
    v.slab(0, 6, 0, 'spruce')
    v.set(0, 3, 0, 'chain', axis='y', waterlogged='false')
    v.lantern(0, 2, 0, hanging=True)
    v.core = (0, 1, 3)
    return v

def plains_stall(seed):
    rng = random.Random(seed)
    v = V()
    cols = rng.choice([('red_wool', 'white_wool'), ('blue_wool', 'white_wool'), ('yellow_wool', 'green_wool')])
    for x in range(-2, 3):
        for z in range(-1, 2):
            v.set(x, 0, z, 'spruce_planks')
    for x, z in ((-2, -1), (2, -1), (-2, 1), (2, 1)):
        for y in (1, 2): v.fence(x, y, z, 'spruce')
    for x in range(-2, 3):
        for z in range(-2, 3):
            v.set(x, 3, z, cols[(x + 2) % 2])
    for x in range(-1, 2):
        v.slab(x, 1, 1, 'spruce', 'top') if x != 0 else v.set(x, 1, 1, 'barrel', facing='up', open='false')
    v.set(-1, 1, -1, 'barrel', facing='south', open='false'); v.set(1, 1, -1, 'chest', facing='south', type='single', waterlogged='false')
    v.lantern(0, 2, 1)
    v.core = (0, 1, 2)
    return v

def plains_tower():
    v = V()
    for y in range(0, 9):
        for x in range(-2, 3):
            for z in range(-2, 3):
                ring = abs(x) == 2 or abs(z) == 2
                corner = abs(x) == 2 and abs(z) == 2
                if y == 0: v.set(x, y, z, 'cobblestone'); continue
                if corner: v.log(x, y, z, 'spruce_log'); continue
                if ring:
                    if y < 5: v.set(x, y, z, 'cobblestone' if y < 3 else 'spruce_planks')
                    elif y in (6,):
                        v.set(x, y, z, 'spruce_planks') if (x + z) % 2 else v.pane(x, y, z)
                    else: v.set(x, y, z, 'spruce_planks')
                else:
                    v.air(x, y, z)
    v.door(0, 1, 2, 'spruce', 'north')
    for y in range(1, 9): v.set(0, y, -1, 'ladder', facing='south', waterlogged='false')
    # plataforma
    for x in range(-3, 4):
        for z in range(-3, 4):
            if (x, z) == (0, -1): v.set(0, 9, -1, 'spruce_trapdoor', facing='south', half='bottom', open='false', powered='false', waterlogged='false'); continue
            v.set(x, 9, z, 'spruce_planks')
            if abs(x) == 3 or abs(z) == 3: v.fence(x, 10, z, 'spruce')
    for x, z in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        v.log(x, 10, z, 'spruce_log'); v.log(x, 11, z, 'spruce_log')
        v.lantern(x, 12, z)
    for x in range(-3, 4):
        for z in range(-3, 4):
            if abs(x) == 3 or abs(z) == 3: pass
    for k in range(0, 4):
        r = 4 - k
        for i in range(-r, r + 1):
            v.stair(i, 12 + k, -r, 'dark_oak', 'south'); v.stair(i, 12 + k, r, 'dark_oak', 'north')
            v.stair(-r, 12 + k, i, 'dark_oak', 'east'); v.stair(r, 12 + k, i, 'dark_oak', 'west')
    v.set(0, 16, 0, 'dark_oak_planks'); v.fence(0, 17, 0, 'dark_oak')
    v.lantern(1, 1, 3)
    v.core = (0, 1, 3)
    return v

# --------------------------------------------------------------------- desierto
def desert_house(hw, hd, floors, pal, seed, job=None, height=4):
    rng = random.Random(seed)
    v = V()
    H = height * floors
    for x in range(-hw, hw + 1):
        for z in range(-hd, hd + 1):
            v.set(x, 0, z, pal['found'] if (abs(x) == hw or abs(z) == hd) else pal['floor'])
    for y in range(1, H + 1):
        for x in range(-hw, hw + 1):
            for z in range(-hd, hd + 1):
                if not (abs(x) == hw or abs(z) == hd):
                    v.air(x, y, z); continue
                corner = abs(x) == hw and abs(z) == hd
                if y == H or y % height == 0: v.set(x, y, z, pal['trim'])
                elif corner: v.set(x, y, z, pal['trim'])
                elif y == height - 1 and (x + z) % 2 == 0 and floors == 1: v.set(x, y, z, pal['band'])
                else: v.set(x, y, z, pal['wall'])
    for f in range(1, floors):
        for x in range(-hw + 1, hw):
            for z in range(-hd + 1, hd):
                v.set(x, height * f, z, pal['floor'])
    # techo plano + parapeto
    for x in range(-hw, hw + 1):
        for z in range(-hd, hd + 1):
            v.set(x, H + 1, z, pal['trim'] if (abs(x) == hw or abs(z) == hd) else pal['floor'])
            if abs(x) == hw or abs(z) == hd:
                if (x + z) % 2 == 0: v.set(x, H + 2, z, pal['wall'])
                else: v.slab(x, H + 2, z, 'smooth_sandstone' if 'smooth' in pal['wall'] else 'sandstone')
    for x, z in ((-hw, -hd), (hw, -hd), (-hw, hd), (hw, hd)):
        v.set(x, H + 2, z, pal['accent']); v.lantern(x, H + 3, z) if (x + z) % 4 == 0 else None
    # ventanas: huecos profundos con vidrio y antepecho
    for f in range(floors):
        wy = 2 + f * height
        for x in range(-hw + 2, hw - 1, 3):
            for z in (-hd,):
                v.pane(x, wy, z)
        for z in range(-hd + 2, hd - 1, 3):
            for x in (-hw, hw):
                v.pane(x, wy, z)
    # puerta con arco y toldo
    v.door(0, 1, hd, pal['door'], 'north')
    v.set(0, 3, hd, pal['accent'])
    for x in range(-1, 2):
        v.set(x, 3, hd + 1, pal['awning'] + '_carpet') if False else v.trapdoor(x, 3, hd + 1, pal['trap'], 'south', 'top', 'false')
    for x in (-1, 1):
        v.set(x, 1, hd + 1, 'potted_cactus') if x == -1 else v.set(x, 1, hd + 1, 'flower_pot')
    # escalera a la terraza
    lx, lz = -hw + 1, -hd + 1
    for y in range(1, H + 2):
        v.set(lx, y, lz, 'ladder', facing='south', waterlogged='false')
    # toldo en la terraza
    for x in range(-hw + 2, hw - 1):
        for z in range(-1, 2):
            v.set(x, H + 4, z, pal['awning'] + '_wool' if (x % 2 == 0) else 'white_wool')
    for x, z in ((-hw + 2, -1), (hw - 2, -1), (-hw + 2, 1), (hw - 2, 1)):
        v.fence(x, H + 2, z, 'jungle'); v.fence(x, H + 3, z, 'jungle')
    # interior
    beds = 1 if job else 2
    top = (floors - 1) * height
    for i in range(beds):
        bx = hw - 1 - 2 * i
        if bx <= -hw + 1: break
        v.bed(bx, top + 1, -hd + 1, pal['bed'], 'north')
    v.set(hw - 1, 1, hd - 1, 'barrel', facing='up', open='false'); v.lantern(hw - 1, 2, hd - 1)
    v.set(-hw + 1, 1, hd - 1, 'decorated_pot', facing='north', cracked='false', waterlogged='false')
    for x in range(-1, 2):
        for z in range(-1, 2):
            if v.get(x, 1, z) is not None and v.get(x, 1, z).n == 'air': v.set(x, 1, z, pal['carpet'] + '_carpet')
    if job:
        ws, props = WS[job]
        v.set(0, 1, -hd + 1, ws, **props)
        decor_job(v, hw, hd, job)
        # el bloque decor de piedra/lana del desierto
    v.core = (0, 1, hd + 1)
    return v

def desert_temple(pal, seed):
    v = desert_house(4, 6, 1, pal, seed, 'cleric', height=6)
    for k in [k for k, b in v.b.items() if k[1] >= 8 and (b.n.endswith('_wool') or b.n.endswith('_fence'))]: del v.b[k]
    # cúpula escalonada sobre el centro
    for k, r in enumerate((3, 2, 1)):
        for x in range(-r, r + 1):
            for z in range(-r, r + 1):
                v.set(x, 8 + k, z, pal['trim'] if k < 2 else pal['accent'])
    v.set(0, 11, 0, 'chiseled_sandstone'); v.set(0, 12, 0, 'lightning_rod', facing='up', powered='false', waterlogged='false')
    for z in range(-3, 4, 2):
        for x in (-3, -2, 2, 3): v.stair(x, 1, z, 'sandstone', 'south')
    return v

def desert_smithy(pal, seed, job):
    v = desert_house(3, 3, 1, pal, seed, job)
    for x in range(4, 8):
        for z in range(-3, 4):
            v.set(x, 0, z, 'cut_sandstone')
            for y in range(1, 4):
                if (x, y, z) not in v.b: v.air(x, y, z)
    for x, z in ((7, -3), (7, 3)):
        for y in range(1, 4): v.set(x, y, z, 'cut_sandstone')
    for x in range(4, 8):
        for z in range(-3, 4): v.slab(x, 4, z, 'smooth_sandstone')
    v.set(6, 1, -2, 'cut_sandstone'); v.set(5, 1, -2, 'blast_furnace', facing='south', lit='false')
    v.set(6, 1, -1, 'campfire', facing='south', lit='true', signal_fire='false', waterlogged='false')
    for y in range(2, 8): v.set(6, y, -2, 'sandstone' if y < 7 else 'sandstone_wall')
    v.set(5, 1, 1, 'anvil', facing='east'); v.set(5, 1, 2, 'smithing_table'); v.set(6, 1, 2, 'grindstone', face='floor', facing='south')
    return v

def desert_farm(seed):
    v = plains_farm(seed)
    for (x, y, z), s in list(v.b.items()):
        if s.n == 'oak_log': v.set(x, y, z, 'cut_sandstone')
    v.set(0, 0, 4, 'cut_sandstone')
    return v

def desert_well():
    v = V()
    for x in range(-2, 3):
        for z in range(-2, 3):
            ring = abs(x) == 2 or abs(z) == 2
            for y in range(-4, 1):
                if ring or y == -4: v.set(x, y, z, 'cut_sandstone')
                else: v.set(x, y, z, 'water', level='0')
            if ring: v.set(x, 1, z, 'smooth_sandstone')
    for x, z in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        v.set(x, 2, z, 'sandstone_wall'); v.set(x, 3, z, 'sandstone_wall'); v.set(x, 4, z, 'chiseled_sandstone')
    for x in range(-2, 3):
        for z in range(-2, 3):
            if not (abs(x) == 2 and abs(z) == 2): v.slab(x, 4, z, 'smooth_sandstone')
    v.set(0, 5, 0, 'cut_sandstone'); v.set(0, 6, 0, 'lantern', hanging='false', waterlogged='false')
    v.set(0, 3, 0, 'chain', axis='y', waterlogged='false'); v.lantern(0, 2, 0, hanging=True)
    v.set(0, 4, 0, 'smooth_sandstone')
    v.core = (0, 1, 3)
    return v

def desert_stall(seed):
    v = plains_stall(seed)
    for (x, y, z), s in list(v.b.items()):
        if s.n == 'spruce_planks': v.set(x, y, z, 'smooth_sandstone')
        elif s.n == 'spruce_fence': v.set(x, y, z, 'jungle_fence', **s.p)
        elif s.n == 'spruce_slab': v.set(x, y, z, 'smooth_sandstone_slab', **s.p)
    return v

def desert_tower():
    v = V()
    for y in range(0, 11):
        for x in range(-2, 3):
            for z in range(-2, 3):
                ring = abs(x) == 2 or abs(z) == 2
                if y == 0: v.set(x, y, z, 'cut_sandstone'); continue
                if ring:
                    v.set(x, y, z, 'cut_sandstone' if (y % 4 == 0 or (abs(x) == 2 and abs(z) == 2)) else 'sandstone')
                    if y in (6, 7) and (x == 0 or z == 0) and not (z == 2): v.air(x, y, z)
                else: v.air(x, y, z)
    v.door(0, 1, 2, 'jungle', 'north')
    for y in range(1, 11): v.set(0, y, -1, 'ladder', facing='south', waterlogged='false')
    for x in range(-3, 4):
        for z in range(-3, 4):
            if (x, z) == (0, -1): v.set(0, 11, -1, 'jungle_trapdoor', facing='south', half='bottom', open='false', powered='false', waterlogged='false'); continue
            v.set(x, 11, z, 'smooth_sandstone')
            if abs(x) == 3 or abs(z) == 3:
                v.set(x, 12, z, 'cut_sandstone') if (x + z) % 2 == 0 else v.slab(x, 12, z, 'smooth_sandstone')
    for x, z in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        v.set(x, 12, z, 'chiseled_sandstone'); v.lantern(x, 13, z)
    for x, z in ((-2, -2), (2, -2), (-2, 2), (2, 2)): v.fence(x, 12, z, 'jungle'); v.fence(x, 13, z, 'jungle')
    for x in range(-2, 3):
        for z in range(-2, 3): v.set(x, 14, z, 'orange_wool' if (x + z) % 2 else 'white_wool')
    v.lantern(1, 1, 3)
    v.core = (0, 1, 3)
    return v

# --------------------------------------------------------------------- catálogo
JOBS = ['farmer', 'fisherman', 'shepherd', 'fletcher', 'librarian', 'cartographer', 'cleric', 'armorer', 'weaponsmith',
        'toolsmith', 'butcher', 'leatherworker', 'mason']
SMITHS = ('armorer', 'weaponsmith', 'toolsmith')

def catalog():
    out = {}
    for i, pal in enumerate(PLAINS_PALS):
        out[f'plains/house_small_{i}'] = plains_house(3, 3, 1, pal, 100 + i)
        out[f'plains/house_large_{i}'] = plains_house(4, 3, 2, pal, 200 + i)
    for j, job in enumerate(JOBS):
        pal = PLAINS_PALS[j % len(PLAINS_PALS)]
        if job == 'cleric': out['plains/work_cleric'] = plains_temple(pal, 300 + j)
        elif job in SMITHS: out[f'plains/work_{job}'] = plains_smithy(pal, 300 + j, job)
        else: out[f'plains/work_{job}'] = plains_house(3, 3, 1, pal, 300 + j, job)
    out['plains/farm_0'] = plains_farm(1); out['plains/farm_1'] = plains_farm(2)
    out['plains/well'] = plains_well(); out['plains/stall_0'] = plains_stall(1); out['plains/stall_1'] = plains_stall(5)
    out['plains/tower'] = plains_tower()
    for i, pal in enumerate(DESERT_PALS):
        out[f'desert/house_small_{i}'] = desert_house(3, 3, 1, pal, 400 + i)
        out[f'desert/house_large_{i}'] = desert_house(4, 3, 2, pal, 500 + i)
    for j, job in enumerate(JOBS):
        pal = DESERT_PALS[j % len(DESERT_PALS)]
        if job == 'cleric': out['desert/work_cleric'] = desert_temple(pal, 600 + j)
        elif job in SMITHS: out[f'desert/work_{job}'] = desert_smithy(pal, 600 + j, job)
        else: out[f'desert/work_{job}'] = desert_house(3, 3, 1, pal, 600 + j, job)
    out['desert/farm_0'] = desert_farm(1); out['desert/farm_1'] = desert_farm(2)
    out['desert/well'] = desert_well(); out['desert/stall_0'] = desert_stall(2); out['desert/stall_1'] = desert_stall(4)
    out['desert/tower'] = desert_tower()
    return out

if __name__ == '__main__':
    import json
    outdir = sys.argv[1]
    cat = catalog()
    info = {}
    for name, v in cat.items():
        p = os.path.join(outdir, name + '.txt')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        n, pal = export(v, p)
        xs = [k[0] for k in v.b]; zs = [k[2] for k in v.b]; ys = [k[1] for k in v.b]
        info[name] = dict(blocks=n, x=(min(xs), max(xs)), z=(min(zs), max(zs)), y=(min(ys), max(ys)), core=v.core)
    for k, i in info.items(): print(k, i)
