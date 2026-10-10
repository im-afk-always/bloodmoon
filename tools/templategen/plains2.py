"""Edificios de llanura de calidad (aldea y ciudad), sobre kit.py."""
import random
from kit import *
from buildings import WS

def merge(v, w, ox=0, oy=0, oz=0, only_new=False):
    for (x, y, z), s in w.b.items():
        k = (x + ox, y + oy, z + oz)
        if only_new and k in v.b and v.b[k].n != 'air': continue
        v.b[k] = s

# ------------------------------------------------------------------ casas
def house(i, seed):
    """0: casa de dos pisos con voladizo · 1: casa en T (cuerpo frontal centrado) · 2: casa larga a cuatro aguas ·
    3: casa con ala trasera centrada. Todas simétricas respecto del eje de la puerta."""
    pal = palette(i % 5)
    if i % 4 == 0:
        return House(seed, pal, [(-5, -4, 5, 4)], floors=2, rooms=['living', 'bed']).v
    if i % 4 == 1:
        return House(seed, pal, [(-5, -4, 5, 3), (-2, 3, 2, 7)], floors=2, rooms=['living', 'bed']).v
    if i % 4 == 2:
        return House(seed, pal, [(-6, -3, 6, 3)], floors=2, hip=True, rooms=['living', 'bed'], jetty=False).v
    return House(seed, pal, [(-4, -2, 4, 5), (-2, -7, 2, -2)], floors=2, rooms=['living', 'bed']).v

def house_small(i, seed):
    pal = palette((i + 2) % 5)
    if i % 2 == 0:
        return House(seed, pal, [(-4, -4, 4, 4)], floors=2, rooms=['living', 'bed']).v
    return House(seed, pal, [(-3, -4, 3, 4)], floors=2, rooms=['living', 'bed'], gable_front=True, jetty=False).v

# ------------------------------------------------------------------ talleres
def forge(v, x0, z0, pal, flip=1):
    """Fragua techada adosada: hogar de piedra con fuego, fuelle (trampillas), yunque, piedra de afilar, barriles de agua."""
    xs = range(x0, x0 + 6 * flip, flip)
    for x in xs:
        for z in range(z0 - 3, z0 + 4):
            v.set(x, 0, z, pick([('cobblestone', 4), ('gravel', 1), ('stone', 1)], x, 0, z, 9))
            for y in range(1, 5):
                if (x, y, z) not in v.b: v.air(x, y, z)
    xe = x0 + 5 * flip
    for z in (z0 - 3, z0 + 3):
        for y in range(1, 4): v.log(xe, y, z, pal['frame'], 'y')
    for y in range(1, 4): v.log(xe, y, z0, pal['frame'], 'y')
    # techo de un agua (pendiente hacia afuera)
    for k, x in enumerate(xs):
        for z in range(z0 - 4, z0 + 5):
            v.stair(x, 5 - k // 2 if k < 6 else 3, z, pal['roof'], 'west' if flip > 0 else 'east', 'bottom') if k % 2 == 0 else \
                v.slab(x, 5 - k // 2, z, pal['roof'], 'top')
    # hogar
    hx = x0 + 2 * flip
    for z in (z0 - 3, z0 - 2):
        for x in (hx, hx + flip):
            v.set(x, 1, z, 'stone_bricks'); v.set(x, 2, z, 'stone_bricks')
    v.set(hx + flip, 1, z0 - 2, 'blast_furnace', facing='south', lit='true')
    # chimenea de la fragua: conducto hueco, fuego en la base (se ve por la boca) y humo arriba
    flue(v, hx, z0 - 3, 1, 11, lambda x, y, z: pick([('stone_bricks', 3), ('cobblestone', 1)], x, y, z, 3), mouth=(0, 1))
    v.stair(hx, 3, z0 - 2, 'stone_brick', 'north', 'top')
    v.set(hx + 2 * flip, 1, z0, 'anvil', facing='north')
    v.set(hx + 2 * flip, 1, z0 + 2, 'grindstone', face='floor', facing='east')
    v.set(hx, 1, z0 + 2, 'water_cauldron', level='3')
    v.set(hx - flip, 1, z0 + 2, 'barrel', facing='up', open='false')
    v.set(hx + 3 * flip, 1, z0 - 2, 'smithing_table')
    v.lantern(hx + flip, 3, z0, hanging=True)
    v.set(hx + 3 * flip, 1, z0 + 3, 'chest', facing='west' if flip > 0 else 'east', type='single', waterlogged='false')

def workshop(job, seed, city=False, color=0):
    pal = palette((seed + len(job)) % 5)
    if city:
        pal = palette([0, 2, 3][seed % 3]); pal['infill'] = CITY_COLORS[color % len(CITY_COLORS)]
        if job in ('armorer', 'weaponsmith', 'toolsmith'):
            v = House(seed, pal, [(-3, -5, 3, 5)], floors=3, gable_front=True, rooms=['shop', 'bed', 'bed'], shop=True, job=job).v
            forge(v, 4, 0, pal)
            return v
        return House(seed, pal, [(-3, -5, 3, 5)], floors=3 + (seed % 2), gable_front=True, rooms=['shop', 'bed', 'bed', 'store'],
                     shop=True, job=job).v
    if job in ('armorer', 'weaponsmith', 'toolsmith'):
        v = House(seed, pal, [(-4, -4, 4, 3)], floors=2, rooms=['living', 'bed'], job=job).v
        forge(v, 5, 0, pal)
        return v
    if job == 'shepherd':
        v = House(seed, pal, [(-4, -4, 4, 3)], floors=2, rooms=['shop', 'bed'], job=job).v
        pen(v, 6, -2)
        return v
    if job == 'farmer':
        v = House(seed, pal, [(-5, -4, 5, 3)], floors=2, rooms=['living', 'bed'], job=job).v
        for x, z in ((7, -3), (7, -2), (8, -3), (7, 1)):
            v.set(x, 1, z, 'hay_block', axis='y');
        v.set(7, 2, -3, 'hay_block', axis='x')
        return v
    if job == 'librarian':
        v = House(seed, pal, [(-4, -4, 4, 4)], floors=3, rooms=['shop', 'bed', 'store'], job=job).v
        for (x, y, z), s in list(v.b.items()):
            if s.n == 'barrel' and y > 4: v.set(x, y, z, 'bookshelf')
        return v
    return House(seed, pal, [(-4, -4, 4, 3)], floors=2, rooms=['shop', 'bed'], job=job, shop=(seed % 2 == 0)).v

def pen(v, x0, z0):
    for x in range(x0, x0 + 6):
        for z in range(z0, z0 + 6):
            v.set(x, 0, z, 'grass_block')
            if x in (x0, x0 + 5) or z in (z0, z0 + 5): v.fence(x, 1, z, 'spruce')
    v.set(x0 + 5, 1, z0 + 2, 'spruce_fence_gate', facing='east', open='false', in_wall='false', powered='false')
    v.set(x0 + 2, 1, z0 + 2, 'hay_block', axis='y')
    v.set(x0 + 3, 1, z0 + 3, 'water_cauldron', level='3')

# ------------------------------------------------------------------ biblioteca (reemplaza a la iglesia)
def library(seed, big=False):
    """Biblioteca de piedra: salón de doble altura con galería en U, escalinata central, estanterías y mesas de lectura."""
    pal = palette(2 if big else 0)
    pal['found'] = [('stone_bricks', 6), ('cracked_stone_bricks', 1), ('mossy_stone_bricks', 1)]
    pal['quoin'] = 'polished_andesite'
    hw, hd = (8, 8) if big else (6, 6)
    H = 8
    h = House(seed, pal, [(-hw, -hd, hw, hd)], floors=1, H=H, jetty=False, hip=big, chimney=False, rooms=['none'], porch=True)
    v = h.v
    tall_windows(v, 2, 3, (5, 6))
    info = library_hall(v, hw, hd, H, 'dark_oak', 'dark_oak_planks', 'dark_oak', ring=4 if big else 3, stair_w=3 if big else 2)
    lx = max(x for x, _ in info['feet']) + 2
    v.set(lx, 1, info['zt'] + 4, 'lectern', facing='west', has_book='false', powered='false')
    v.core = (0, 1, hd + 1)
    return v

# ------------------------------------------------------------------ iglesia (ya no se usa en el catálogo)
def church(seed, big=False):
    """Iglesia de piedra: nave con contrafuertes, ventanales, bancos, altar; torre campanario con aguja en el frente."""
    v = V()
    hw = 5 if not big else 7
    z0, z1 = (-9, 8) if not big else (-14, 12)
    Hn = 8 if not big else 12
    stone = [('stone_bricks', 6), ('cracked_stone_bricks', 1), ('mossy_stone_bricks', 1), ('andesite', 1)]
    for x in range(-hw, hw + 1):
        for z in range(z0, z1 + 1):
            edge = abs(x) == hw or z in (z0, z1)
            v.set(x, 0, z, pick(stone, x, 0, z) if edge else ('polished_andesite' if (x + z) % 2 else 'stone_bricks'))
            for y in range(1, Hn + 1):
                if edge: v.set(x, y, z, pick(stone, x, y, z, 2))
                else: v.air(x, y, z)
    # contrafuertes
    for z in range(z0 + 2, z1, 4):
        for sx in (-hw - 1, hw + 1):
            for y in range(1, Hn - 1): v.set(sx, y, z, pick(stone, sx, y, z, 3))
            v.stair(sx, Hn - 1, z, 'stone_brick', 'east' if sx < 0 else 'west', 'bottom')
    # ventanales (vitrales) entre contrafuertes
    for z in range(z0 + 4, z1 - 1, 4):
        for sx in (-hw, hw):
            for y in range(3, Hn - 1):
                v.pane(sx, y, z, 'yellow_stained_glass_pane' if y % 3 else 'white_stained_glass_pane')
            v.stair(sx, Hn - 1, z, 'stone_brick', 'south', 'top')
    # rosetón del ábside
    for x in range(-2, 3):
        for y in range(Hn - 5, Hn):
            if abs(x) + abs(y - (Hn - 3)) <= 2: v.pane(x, y, z0, 'light_blue_stained_glass_pane' if (x + y) % 2 else 'red_stained_glass_pane')
    # techo
    for k in range(0, hw + 2):
        y = Hn + k
        for z in range(z0 - 1, z1 + 2):
            if hw + 1 - k > 0:
                v.stair(-(hw + 1 - k), y, z, 'deepslate_tile', 'east'); v.stair(hw + 1 - k, y, z, 'deepslate_tile', 'west')
            else:
                v.slab(0, y, z, 'deepslate_tile', 'bottom')
        if k >= 1:
            for zz in (z0, z1):
                for x in range(-(hw - k), hw - k + 1): v.set(x, y, zz, pick(stone, x, y, zz, 4))
            for x in range(-(hw - k) + 1, hw - k):
                for z in range(z0 + 1, z1):
                    v.air(x, y, z)
    # interior: bancos, pasillo con alfombra, altar, faroles colgantes
    for z in range(z0 + 5, z1 - 3, 2):
        for x in list(range(-hw + 2, -1)) + list(range(2, hw - 1)):
            v.stair(x, 1, z, 'dark_oak', 'south', 'bottom')
    for z in range(z0 + 2, z1):
        v.set(0, 1, z, 'red_carpet')
    for x in range(-2, 3): v.set(x, 1, z0 + 2, 'stone_brick_slab', type='top', waterlogged='false') if abs(x) == 2 else None
    v.set(0, 1, z0 + 2, 'brewing_stand', has_bottle_0='false', has_bottle_1='false', has_bottle_2='false')
    v.set(0, 1, z0 + 1, 'gold_block'); v.set(0, 2, z0 + 1, 'candle', candles='4', lit='true', waterlogged='false')
    for x in (-1, 1): v.set(x, 1, z0 + 1, 'quartz_pillar', axis='y'); v.set(x, 2, z0 + 1, 'candle', candles='3', lit='true', waterlogged='false')
    for z in range(z0 + 4, z1 - 1, 5):
        for y in range(Hn - 1, Hn + hw - 1): v.set(0, y, z, 'chain', axis='y', waterlogged='false')
        v.lantern(0, Hn - 2, z, hanging=True)
    # torre campanario en el frente (sobre la puerta)
    tz0, tz1 = z1 - 3, z1 + 1
    Ht = Hn + 10
    for y in range(1, Ht + 1):
        for x in range(-2, 3):
            for z in range(tz0, tz1 + 1):
                edge = abs(x) == 2 or z in (tz0, tz1)
                if edge:
                    if Ht - 4 < y < Ht and (x == 0 or z == (tz0 + tz1) // 2) and abs(x) < 2 or (Ht - 4 < y < Ht and z in (tz0, tz1) and x == 0):
                        v.air(x, y, z)
                    else:
                        v.set(x, y, z, pick(stone, x, y, z, 5) if not (abs(x) == 2 and z in (tz0, tz1)) else 'polished_andesite')
                else:
                    v.air(x, y, z) if y < Ht else v.set(x, y, z, 'stone_bricks')
    for x in range(-2, 3):
        for z in (tz0, tz1):
            for y in range(Ht - 3, Ht):
                if x == 0: v.air(x, y, z)
    v.set(0, Ht - 1, (tz0 + tz1) // 2, 'bell', attachment='ceiling', facing='south', powered='false')
    # aguja
    for k in range(0, 9):
        r = 3 - (k * 3) // 9
        for x in range(-r, r + 1):
            for z in range((tz0 + tz1) // 2 - r, (tz0 + tz1) // 2 + r + 1):
                if max(abs(x), abs(z - (tz0 + tz1) // 2)) == r:
                    v.set(x, Ht + 1 + k, z, 'deepslate_tiles' if r > 0 else 'deepslate_tile_wall')
    v.set(0, Ht + 10, (tz0 + tz1) // 2, 'lightning_rod', facing='up', powered='false', waterlogged='false')
    # portal
    for x in (-1, 0, 1):
        for y in (1, 2, 3): v.air(x, y, tz1)
    v.door(0, 1, tz1, 'dark_oak', 'north'); v.door(-1, 1, tz1, 'dark_oak', 'north', hinge='right'); v.air(1, 1, tz1); v.air(1, 2, tz1)
    v.door(1, 1, tz1, 'dark_oak', 'north', hinge='left')
    v.stair(0, 4, tz1, 'stone_brick', 'south', 'top'); v.stair(-1, 4, tz1, 'stone_brick', 'east', 'top'); v.stair(1, 4, tz1, 'stone_brick', 'west', 'top')
    for x in (-2, 2): v.lantern(x, 3, tz1 + 1, hanging=False) if False else v.set(x, 1, tz1 + 1, 'stone_brick_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')
    for x in (-2, 2): v.lantern(x, 2, tz1 + 1)
    for x in range(-1, 2):
        for z in range(tz0 + 1, tz1): v.air(x, 1, z); v.air(x, 2, z)
    v.core = (0, 1, tz1 + 1)
    return v

# ------------------------------------------------------------------ torre de vigía
def watchtower(seed, city=False):
    v = V(); r = 3
    stone = [('cobblestone', 4), ('mossy_cobblestone', 1), ('stone_bricks', 2), ('andesite', 1)]
    Hs = 7 if not city else 14
    for y in range(0, Hs + 1):
        for x in range(-r, r + 1):
            for z in range(-r, r + 1):
                ring = abs(x) == r or abs(z) == r
                if y == 0: v.set(x, y, z, pick(stone, x, y, z)); continue
                if abs(x) == r and abs(z) == r: v.set(x, y, z, 'stone_bricks' if city else 'polished_andesite'); continue
                if ring: v.set(x, y, z, pick(stone, x, y, z, 2))
                else: v.air(x, y, z)
    # pisos de madera y escalera de mano
    for y in range(4, Hs + 1, 5):
        for x in range(-r + 1, r):
            for z in range(-r + 1, r): v.set(x, y, z, 'spruce_planks')
    for y in range(1, Hs + 2): v.set(0, y, -r + 1, 'ladder', facing='south', waterlogged='false')   # hasta la plataforma
    # saeteras
    for y in range(3, Hs, 4):
        for (x, z) in ((0, r), (r, 0), (-r, 0)):
            v.air(x, y, z); v.air(x, y + 1, z)
    # parte alta de madera saliente con baranda
    yt = Hs + 1
    for x in range(-r - 1, r + 2):
        for z in range(-r - 1, r + 2):
            v.set(x, yt, z, 'spruce_planks')
            if (abs(x) == r + 1 or abs(z) == r + 1):
                v.fence(x, yt + 1, z, 'spruce')
            elif abs(x) <= r - 1 and abs(z) <= r - 1:
                pass
    v.set(0, yt, -r + 1, 'ladder', facing='south', waterlogged='false')
    for y in range(4, Hs + 1, 5): v.lantern(1, y - 1, 1, hanging=True)       # luz en cada piso
    for x in (-r - 1, r + 1):
        for z in (-r - 1, r + 1):
            v.stair(x, yt - 1, z, 'spruce', 'east' if x < 0 else 'west', 'top')
            for y in range(yt + 1, yt + 4): v.log(x, y, z, 'spruce_log', 'y')
    for x in range(-r - 1, r + 2):
        for z in (-r - 1, r + 1):
            if abs(x) < r + 1: v.stair(x, yt - 1, z, 'spruce', 'south' if z < 0 else 'north', 'top')
    # techo piramidal
    for k in range(0, r + 3):
        rr = r + 2 - k
        y = yt + 4 + k
        for i in range(-rr, rr + 1):
            v.stair(i, y, -rr, 'dark_oak', 'south'); v.stair(i, y, rr, 'dark_oak', 'north')
            v.stair(-rr, y, i, 'dark_oak', 'east'); v.stair(rr, y, i, 'dark_oak', 'west')
        if rr == 0: v.set(0, y, 0, 'dark_oak_planks'); v.fence(0, y + 1, 0, 'dark_oak')
    for x in range(-r - 1, r + 2):
        for z in range(-r - 1, r + 2):
            for y in range(yt + 1, yt + 4):
                if (x, y, z) not in v.b: v.air(x, y, z)
    v.lantern(0, yt + 3, 0, hanging=True)
    v.set(r - 1, yt + 1, r - 1, 'barrel', facing='up', open='false')
    v.door(0, 1, r, 'spruce', 'north')
    v.lantern(1, 3, r + 1, hanging=False) if False else v.set(1, 3, r + 1, 'lantern', hanging='false', waterlogged='false') if False else None
    v.set(-1, 1, r - 1, 'barrel', facing='up', open='false')
    v.core = (0, 1, r + 1)
    return v

# ------------------------------------------------------------------ granja
def farm(seed, w=6, d=5):
    rng = random.Random(seed)
    v = V()
    crops = ['wheat', 'carrots', 'potatoes', 'beetroots']
    a, b = rng.sample(crops, 2)
    for x in range(-w, w + 1):
        for z in range(-d, d + 1):
            edge = abs(x) == w or abs(z) == d
            if edge:
                v.log(x, 0, z, 'oak_log', 'x' if abs(z) == d and abs(x) != w else 'z' if abs(x) == w and abs(z) != d else 'y')
                if not (x == 0 and z == d): v.fence(x, 1, z, 'oak')
                continue
            if x % 4 == 0:
                v.set(x, 0, z, 'water', level='0')
                v.set(x, -1, z, 'dirt')
                continue
            v.set(x, 0, z, 'farmland', moisture='7')
            c = a if x < 0 else b
            v.set(x, 1, z, c, age=str(rng.randint(2, 3 if c == 'beetroots' else 7)))
    for x in range(-w + 1, w):
        if x % 4 == 0:
            for z in (-d, d): v.log(x, 0, z, 'oak_log', 'x')
    v.set(0, 1, d, 'oak_fence_gate', facing='south', open='false', in_wall='false', powered='false')
    # espantapájaros
    sx, sz = -2, 1
    v.fence(sx, 2, sz, 'oak'); v.set(sx, 3, sz, 'hay_block', axis='y'); v.set(sx, 4, sz, 'carved_pumpkin', facing='south')
    v.set(sx, 1, sz, 'oak_fence', north='false', south='false', east='false', west='false', waterlogged='false')
    v.set(w - 1, 1, d - 1, 'composter', level='4'); v.set(-w + 1, 1, d - 1, 'hay_block', axis='y')
    v.lantern(w, 2, d) if False else v.set(w, 2, d, 'lantern', hanging='false', waterlogged='false')
    v.set(-w, 2, d, 'lantern', hanging='false', waterlogged='false')
    v.core = (0, 1, d + 1)
    return v

# ------------------------------------------------------------------ pozo (centro de la aldea)
def well(seed):
    v = V()
    for x in range(-3, 4):
        for z in range(-3, 4):
            v.set(x, 0, z, pick([('stone_bricks', 3), ('cobblestone', 2), ('mossy_cobblestone', 1)], x, 0, z))
    for x in range(-1, 2):
        for z in range(-1, 2):
            for y in range(-5, 0):
                v.set(x, y, z, 'water', level='0') if y > -5 else v.set(x, y, z, 'cobblestone')
            v.set(x, 0, z, 'water', level='0')
    for x in range(-2, 3):
        for z in range(-2, 3):
            if max(abs(x), abs(z)) == 2:
                for y in range(-5, 0): v.set(x, y, z, 'cobblestone')
                v.set(x, 1, z, 'stone_bricks' if (x + z) % 2 else 'mossy_stone_bricks')
    for x, z in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        v.fence(x, 2, z, 'spruce'); v.fence(x, 3, z, 'spruce'); v.log(x, 4, z, 'spruce_log', 'y')
    for i in range(-3, 4):
        v.stair(i, 5, -3, 'spruce', 'south'); v.stair(i, 5, 3, 'spruce', 'north')
        if abs(i) < 3: v.stair(-3, 5, i, 'spruce', 'east'); v.stair(3, 5, i, 'spruce', 'west')
    for x in range(-2, 3):
        for z in range(-2, 3):
            if max(abs(x), abs(z)) == 2: v.stair(x, 6, z, 'spruce', 'south' if z == -2 else 'north' if z == 2 else 'east' if x == -2 else 'west')
            elif (x, z) != (0, 0): v.set(x, 6, z, 'spruce_planks')
            if max(abs(x), abs(z)) < 2: v.set(x, 5, z, 'spruce_planks') if (x, z) != (0, 0) else None
    v.set(0, 6, 0, 'spruce_planks'); v.slab(0, 7, 0, 'spruce', 'bottom')
    v.set(0, 5, 0, 'spruce_planks')
    v.set(0, 4, 0, 'chain', axis='y', waterlogged='false'); v.set(0, 3, 0, 'chain', axis='y', waterlogged='false')
    v.lantern(0, 2, 0, hanging=True)
    for x, z in ((-3, 0), (3, 0)): v.set(x, 1, z, 'lantern', hanging='false', waterlogged='false')
    v.core = (0, 1, 4)
    return v

# ------------------------------------------------------------------ fuente de plaza (ciudad)
def fountain(seed, stone='stone_bricks', trim='polished_andesite', wall='stone_brick_wall', slab='stone_brick'):
    """Fuente contenida: pileta octogonal de 9 con agua a ras del piso (no derrama), columna central con farol."""
    v = V(); R = 4
    for x in range(-R - 1, R + 2):
        for z in range(-R - 1, R + 2):
            d = max(abs(x), abs(z)) + (1 if abs(x) == abs(z) and abs(x) >= 3 else 0)
            if d > R + 1: continue
            v.set(x, -1, z, stone)
            if d <= R - 1:
                v.set(x, 0, z, 'water', level='0')
            elif d == R:
                v.set(x, 0, z, stone); v.slab(x, 1, z, slab, 'bottom')
            else:
                v.set(x, 0, z, trim)
    for y in range(0, 4): v.set(0, y, 0, 'chiseled_stone_bricks' if y in (0, 3) else stone)
    for (a, b) in DIRS4: v.stair(a, 1, b, slab, NAME[(-a, -b)], 'top') if False else v.set(a, 0, b, 'water', level='0')
    v.set(0, 4, 0, wall, up='true', north='none', south='none', east='none', west='none', waterlogged='false')
    v.lantern(0, 5, 0)
    for (a, b) in DIRS4:
        v.stair(a, 3, b, slab, NAME[(a, b)], 'top')
    for x, z in ((-R, -R + 2), (R, R - 2), (-R + 2, R), (R - 2, -R)):
        pass
    v.core = (0, 1, R + 2)
    return v

# ------------------------------------------------------------------ puesto de mercado
def stall(seed):
    rng = random.Random(seed)
    v = V()
    cols = rng.choice([('red', 'white'), ('blue', 'white'), ('yellow', 'orange'), ('green', 'lime'), ('purple', 'white')])
    for x in range(-3, 4):
        for z in range(-2, 3): v.set(x, 0, z, 'spruce_planks' if abs(z) < 2 else 'stripped_spruce_wood' if False else 'spruce_planks')
    for x, z in ((-3, -2), (3, -2), (-3, 2), (3, 2)):
        for y in (1, 2, 3): v.log(x, y, z, 'spruce_log', 'y')
    # toldo a dos aguas de lana a rayas
    for x in range(-3, 4):
        c = cols[(x + 3) % 2] + '_wool'
        v.set(x, 4, -1, c); v.set(x, 4, 0, c); v.set(x, 4, 1, c)
        v.set(x, 3, -2, c); v.set(x, 3, 2, c)
        v.set(x, 5, 0, cols[0] + '_carpet') if False else None
    # mostrador y mercadería
    for x in range(-2, 3):
        v.slab(x, 1, 2, 'spruce', 'top') if x not in (-2, 2) else v.set(x, 1, 2, 'barrel', facing='up', open='false')
    goods = ['melon', 'pumpkin', 'hay_block', 'barrel', 'composter']
    v.set(-2, 1, -1, rng.choice(['melon', 'pumpkin'])); v.set(2, 1, -1, 'barrel', facing='north', open='false')
    v.set(-1, 1, -2, 'chest', facing='south', type='single', waterlogged='false'); v.set(1, 1, -2, 'crafting_table')
    v.set(0, 2, 2, 'potted_red_tulip') if False else None
    v.lantern(0, 3, 0, hanging=True)
    v.core = (0, 1, 3)
    return v
