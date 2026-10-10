"""Ciudad de llanura: casas altas de frente a la calle, mercado cubierto, ayuntamiento con torre del reloj y castillo."""
import random
from kit import *
from plains2 import church, watchtower, fountain, merge

def townhouse(i, seed):
    pal = palette([0, 2, 3, 4, 0][i % 5]); pal['infill'] = CITY_COLORS[(i * 3 + seed) % len(CITY_COLORS)]
    pal['roof'] = ['deepslate_tile', 'dark_oak', 'spruce', 'deepslate_tile', 'mud_brick'][i % 5]
    pal['roof2'] = 'cobbled_deepslate' if pal['roof'] == 'deepslate_tile' else 'dark_oak'
    pal['ridge'] = pal['roof'] if pal['roof'] != 'mud_brick' else 'dark_oak'
    if i % 3 == 2:
        return House(seed, pal, [(-4, -5, 4, 5)], floors=3, rooms=['living', 'bed', 'bed'], jetty=True).v
    return House(seed, pal, [(-3, -5, 3, 5)], floors=3 + (i % 2), gable_front=True, rooms=['living', 'bed', 'bed', 'store']).v

def market_hall(seed):
    """Mercado cubierto: planta baja abierta en arcadas de piedra con puestos; salón de gremios arriba (entramado)."""
    pal = palette(0); pal['infill'] = CITY_COLORS[1]
    hw, hd = 8, 5
    v = V()
    stone = [('stone_bricks', 5), ('cracked_stone_bricks', 1), ('andesite', 1)]
    for x in range(-hw, hw + 1):
        for z in range(-hd, hd + 1):
            v.set(x, 0, z, 'polished_andesite' if (x + z) % 2 else 'stone_bricks')
    # pilares y arcos
    for x in range(-hw, hw + 1):
        for z in range(-hd, hd + 1):
            edge = abs(x) == hw or abs(z) == hd
            pillar = (abs(x) == hw or x % 4 == 0) and (abs(z) == hd or z % 4 == 0 if abs(x) == hw else abs(z) == hd)
            for y in range(1, 5):
                if pillar and edge: v.set(x, y, z, pick(stone, x, y, z))
                elif edge and y == 4: v.set(x, y, z, pick(stone, x, y, z, 2))
                elif edge and y == 3:
                    # arranque del arco
                    near = any(((x + d) % 4 == 0 if abs(z) == hd else (z + d) % 4 == 0) for d in (-1, 1))
                    if near: v.stair(x, y, z, 'stone_brick', 'east' if (x - 1) % 4 == 0 and abs(z) == hd else 'west' if abs(z) == hd else
                                     ('south' if (z - 1) % 4 == 0 else 'north'), 'top')
                    else: v.air(x, y, z)
                else: v.air(x, y, z)
    for x in range(-hw, hw + 1):
        for z in range(-hd, hd + 1):
            v.set(x, 5, z, 'spruce_planks' if not (abs(x) == hw or abs(z) == hd) else 'dark_oak_log', **({} if not (abs(x) == hw or abs(z) == hd) else {'axis': 'x' if abs(z) == hd else 'z'}))
    # salón superior de entramado
    for y in range(6, 10):
        for x in range(-hw, hw + 1):
            for z in range(-hd, hd + 1):
                edge = abs(x) == hw or abs(z) == hd
                if not edge: v.air(x, y, z); continue
                if y == 9: v.log(x, y, z, 'dark_oak_log', 'y' if abs(x) == hw and abs(z) == hd else ('x' if abs(z) == hd else 'z'))
                elif (abs(x) == hw and abs(z) == hd) or (x % 4 == 0 if abs(z) == hd else z % 4 == 0): v.log(x, y, z, 'dark_oak_log', 'y')
                elif y in (7, 8) and (x % 4 == 2 if abs(z) == hd else z % 4 == 2): v.pane(x, y, z)
                else: v.set(x, y, z, pick(pal['infill'], x, y, z))
    # techo a cuatro aguas
    for k in range(0, hd + 2):
        y = 9 + k
        r_x, r_z = hw + 1 - k, hd + 1 - k
        if r_z < 0: break
        for x in range(-r_x, r_x + 1):
            if r_z > 0:
                v.stair(x, y, -r_z, 'deepslate_tile', 'south'); v.stair(x, y, r_z, 'deepslate_tile', 'north')
            else: v.slab(x, y, 0, 'deepslate_tile', 'bottom')
        for z in range(-r_z + 1, r_z):
            v.stair(-r_x, y, z, 'deepslate_tile', 'east'); v.stair(r_x, y, z, 'deepslate_tile', 'west')
        for x in range(-r_x + 1, r_x):
            for z in range(-r_z + 1, r_z):
                if (x, y, z) not in v.b: v.air(x, y, z)
    # puestos bajo las arcadas
    rng = random.Random(seed)
    cols = ['red', 'yellow', 'blue', 'green', 'orange', 'white']
    for i, x in enumerate(range(-hw + 2, hw - 1, 4)):
        for z in (-hd + 2, hd - 2):
            v.set(x, 1, z, 'barrel', facing='up', open='false'); v.set(x + 1, 1, z, rng.choice(['melon', 'pumpkin', 'hay_block', 'composter']),
                                                                       **({'axis': 'y'} if False else {}))
            v.set(x - 1, 1, z, 'spruce_slab', type='top', waterlogged='false')
            for dx in (-1, 0, 1): v.set(x + dx, 3, z, cols[(i + dx + (z > 0)) % len(cols)] + '_wool')
    for x in range(-hw + 2, hw - 1, 4): v.lantern(x, 4, 0, hanging=True)
    # escalera de mano al salón: atraviesa el piso para poder salir arriba
    for y in range(1, 6): v.set(-hw + 1, y, 0, 'ladder', facing='east', waterlogged='false')
    # salón: mesa larga de gremio
    for x in range(-4, 5): v.fence(x, 6, 0, 'dark_oak'); v.set(x, 7, 0, 'dark_oak_pressure_plate', powered='false')
    for x in range(-4, 5, 2): v.stair(x, 6, -1, 'dark_oak', 'north'); v.stair(x, 6, 1, 'dark_oak', 'south')
    v.lantern(0, 8, 0, hanging=True)
    v.core = (0, 1, hd + 1)
    return v

def town_hall(seed):
    """Ayuntamiento: piedra abajo, entramado arriba, torre del reloj de piedra con chapitel y campana."""
    pal = palette(0); pal['infill'] = CITY_COLORS[0]
    h = House(seed, pal, [(-6, -5, 6, 5)], floors=3, rooms=['living', 'store', 'bed'], jetty=True, chimney=True)
    v = h.v
    # torre frontal
    tz0, tz1 = 6, 10
    stone = [('stone_bricks', 5), ('cracked_stone_bricks', 1), ('andesite', 1)]
    Ht = 22
    for y in range(0, Ht + 1):
        for x in range(-2, 3):
            for z in range(tz0, tz1 + 1):
                edge = abs(x) == 2 or z in (tz0, tz1)
                if y == 0: v.set(x, y, z, 'stone_bricks'); continue
                if edge: v.set(x, y, z, pick(stone, x, y, z) if not (abs(x) == 2 and z in (tz0, tz1)) else 'polished_andesite')
                else: v.air(x, y, z)
    # reloj (cara frontal)
    v.set(0, 15, tz1, 'gold_block'); v.set(0, 14, tz1, 'chiseled_stone_bricks'); v.set(-1, 15, tz1, 'chiseled_stone_bricks'); v.set(1, 15, tz1, 'chiseled_stone_bricks')
    for x in range(-1, 2):
        for z in (tz0, tz1):
            for y in range(Ht - 3, Ht): v.air(x, y, z) if x == 0 else None
    v.set(0, Ht - 1, (tz0 + tz1) // 2, 'bell', attachment='ceiling', facing='south', powered='false')
    for k in range(0, 7):
        r = 3 - k // 2
        for x in range(-r, r + 1):
            for z in range((tz0 + tz1) // 2 - r, (tz0 + tz1) // 2 + r + 1):
                if max(abs(x), abs(z - (tz0 + tz1) // 2)) == r: v.set(x, Ht + 1 + k, z, 'deepslate_tiles' if r else 'deepslate_tile_wall')
    v.set(0, Ht + 8, (tz0 + tz1) // 2, 'lightning_rod', facing='up', powered='false', waterlogged='false')
    for x in (-1, 0, 1):
        for y in (1, 2, 3): v.air(x, y, tz1)
    v.door(0, 1, tz1, 'dark_oak', 'north'); v.door(-1, 1, tz1, 'dark_oak', 'north', hinge='right'); v.door(1, 1, tz1, 'dark_oak', 'north', hinge='left')
    for z in range(tz0, tz1): v.air(0, 1, z); v.air(0, 2, z); v.air(-1, 1, z); v.air(-1, 2, z); v.air(1, 1, z); v.air(1, 2, z)
    # escalera de mano contra el muro lateral de la torre (el del fondo tiene el paso abierto abajo)
    for y in range(1, Ht - 2): v.set(1, y, tz0 + 2, 'ladder', facing='west', waterlogged='false')
    v.air(0, 1, 5); v.air(0, 2, 5); v.air(-1, 1, 5); v.air(-1, 2, 5); v.air(1, 1, 5); v.air(1, 2, 5)
    for x in (-2, 2): v.lantern(x, 3, tz1 + 1, hanging=False) if False else v.set(x, 1, tz1 + 1, 'stone_brick_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')
    for x in (-2, 2): v.lantern(x, 2, tz1 + 1)
    v.set(0, 1, -3, 'lectern', facing='south', has_book='false', powered='false')
    v.core = (0, 1, tz1 + 1)
    return v

def castle(seed):
    """Castillo: recinto de 41×41 con muralla de 3 de espesor y 9 de alto con almenas y adarve, cuatro torres
    octogonales con chapitel, torre-puerta con rastrillo, torre del homenaje de cuatro pisos con salón y trono,
    cuartel, establo, pozo y caminos de piedra en el patio."""
    v = V(); R = 20
    rng = random.Random(seed)
    stone = [('stone_bricks', 6), ('cracked_stone_bricks', 1), ('mossy_stone_bricks', 1), ('andesite', 1), ('cobblestone', 1)]
    Hw = 9
    # patio
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            v.set(x, 0, z, pick([('grass_block', 6), ('coarse_dirt', 1), ('gravel', 1)], x, 0, z, 1))
            for y in range(1, 6):
                v.air(x, y, z)
    # muralla
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            d = max(abs(x), abs(z))
            if d < R - 2: continue
            for y in range(0, Hw + 1): v.set(x, y, z, pick(stone, x, y, z, 2))
            if d == R:
                if (x + z) % 2 == 0: v.set(x, Hw + 1, z, pick(stone, x, Hw + 1, z, 3))
                # matacanes: ménsulas en la cara externa
                if (x + z) % 4 == 0:
                    o = (1 if x == R else -1 if x == -R else 0, 1 if z == R else -1 if z == -R else 0)
                    if o != (0, 0) and abs(o[0]) + abs(o[1]) == 1:
                        v.stair(x + o[0], Hw - 1, z + o[1], 'stone_brick', NAME[(-o[0], -o[1])], 'top')
            elif d == R - 1:
                v.slab(x, Hw + 1, z, 'stone_brick', 'bottom') if False else None
    # adarve interior más bajo: escalones de acceso
    for k in range(0, Hw):
        v.stair(-R + 3 + k, k + 1, -R + 3, 'stone_brick', 'east')
        for y in range(1, k + 1): v.set(-R + 3 + k, y, -R + 3, pick(stone, -R + 3 + k, y, -R + 3))
    # torres octogonales en las esquinas
    def tower(cx, cz, rr=4, Ht=15):
        for y in range(0, Ht + 1):
            for x in range(cx - rr, cx + rr + 1):
                for z in range(cz - rr, cz + rr + 1):
                    dx, dz = abs(x - cx), abs(z - cz)
                    if dx + dz > rr + rr // 2: continue
                    edge = dx == rr or dz == rr or dx + dz == rr + rr // 2
                    if edge or y == 0: v.set(x, y, z, pick(stone, x, y, z, 4))
                    elif y in (5, 10): v.set(x, y, z, 'spruce_planks')
                    else: v.air(x, y, z)
            if y in (3, 8, 13):
                # saeteras (no en la cara de la escalera de mano, que se apoya ahí)
                for (ax, az) in ((cx + rr, cz), (cx - rr, cz), (cx, cz + rr)):
                    v.air(ax, y, az); v.air(ax, y + 1, az)
        # puerta hacia el patio, en la cara diagonal que mira al centro
        sx_, sz_ = (1 if cx > 0 else -1), (1 if cz > 0 else -1)
        for y in (1, 2): v.air(cx - sx_ * 3, y, cz - sz_ * 3)
        for x in range(cx - rr - 1, cx + rr + 2):
            for z in range(cz - rr - 1, cz + rr + 2):
                dx, dz = abs(x - cx), abs(z - cz)
                if dx + dz > rr + 1 + (rr + 1) // 2: continue
                v.set(x, Ht + 1, z, pick(stone, x, Ht + 1, z, 5))
                if (dx == rr + 1 or dz == rr + 1 or dx + dz == rr + 1 + (rr + 1) // 2) and (x + z) % 2 == 0:
                    v.set(x, Ht + 2, z, pick(stone, x, Ht + 2, z, 6))
        # chapitel cónico
        for k in range(0, 10):
            r2 = rr - (k * rr) // 9
            y = Ht + 3 + k
            for x in range(cx - r2, cx + r2 + 1):
                for z in range(cz - r2, cz + r2 + 1):
                    dx, dz = abs(x - cx), abs(z - cz)
                    if dx + dz > r2 + r2 // 2: continue
                    if dx == r2 or dz == r2 or dx + dz == r2 + r2 // 2 or r2 == 0:
                        v.set(x, y, z, 'deepslate_tiles' if r2 > 0 else 'deepslate_tile_wall')
        v.set(cx, Ht + 13, cz, 'lightning_rod', facing='up', powered='false', waterlogged='false')
        for y in range(1, Ht + 1): v.set(cx, y, cz - rr + 1, 'ladder', facing='south', waterlogged='false')
        v.lantern(cx, 4, cz, hanging=True); v.lantern(cx, 9, cz, hanging=True)
    for sx in (-R, R):
        for sz in (-R, R): tower(sx, sz)
    # torre-puerta (sur)
    for gx in (-4, 4):
        for y in range(0, 15):
            for x in range(gx - 2, gx + 3):
                for z in range(R - 3, R + 3):
                    edge = abs(x - gx) == 2 or z in (R - 3, R + 2)
                    v.set(x, y, z, pick(stone, x, y, z, 7)) if edge or y == 0 else v.air(x, y, z)
        for x in range(gx - 3, gx + 4):
            for z in range(R - 4, R + 4):
                v.set(x, 15, z, pick(stone, x, 15, z))
                if (abs(x - gx) == 3 or z in (R - 4, R + 3)) and (x + z) % 2 == 0: v.set(x, 16, z, pick(stone, x, 16, z, 2))
    for x in range(-2, 3):
        for z in range(R - 3, R + 3):
            for y in range(1, 6): v.air(x, y, z)
            for y in range(6, 13): v.set(x, y, z, pick(stone, x, y, z, 8))
        for y in range(5, 7): v.set(x, y, R + 2, 'iron_bars', north='false', south='false', east='true' if x < 2 else 'false', west='true' if x > -2 else 'false', waterlogged='false')
        v.stair(x, 5, R + 2, 'stone_brick', 'north', 'top') if False else None
    for x in range(-2, 3): v.set(x, 5, R + 2, 'iron_bars', north='false', south='false', east='true' if x < 2 else 'false', west='true' if x > -2 else 'false', waterlogged='false')
    for z in range(R - 3, R + 3): v.set(0, 0, z, 'polished_andesite')
    for x in (-3, 3): v.lantern(x, 4, R + 3)
    # torre del homenaje
    kx0, kx1, kz0, kz1 = -7, 7, -14, -2
    Hk = 18
    for y in range(0, Hk + 1):
        for x in range(kx0, kx1 + 1):
            for z in range(kz0, kz1 + 1):
                edge = x in (kx0, kx1) or z in (kz0, kz1)
                if edge or y == 0: v.set(x, y, z, pick(stone, x, y, z, 9))
                elif y in (6, 12): v.set(x, y, z, 'dark_oak_planks')
                else: v.air(x, y, z)
    for x in range(kx0 - 1, kx1 + 2):
        for z in range(kz0 - 1, kz1 + 2):
            v.set(x, Hk + 1, z, pick(stone, x, Hk + 1, z))
            if (x in (kx0 - 1, kx1 + 1) or z in (kz0 - 1, kz1 + 1)) and (x + z) % 2 == 0: v.set(x, Hk + 2, z, pick(stone, x, Hk + 2, z, 3))
    # contrafuertes y ventanas de la torre del homenaje
    for x in range(kx0, kx1 + 1, 4):
        for z in (kz0 - 1, kz1 + 1):
            for y in range(1, Hk - 2): v.set(x, y, z, pick(stone, x, y, z, 10))
    for f in range(3):
        y = f * 6 + 3
        for x in range(kx0 + 2, kx1 - 1, 4):
            for z in (kz0, kz1):
                v.pane(x, y, z); v.pane(x, y + 1, z)
        for z in range(kz0 + 2, kz1 - 1, 4):
            for x in (kx0, kx1): v.pane(x, y, z); v.pane(x, y + 1, z)
    # puerta y escalinata
    for x in (-1, 0, 1):
        for y in (1, 2, 3): v.air(x, y, kz1)
    v.door(-1, 1, kz1, 'dark_oak', 'north', hinge='right'); v.door(1, 1, kz1, 'dark_oak', 'north', hinge='left'); v.air(0, 1, kz1); v.air(0, 2, kz1)
    v.door(0, 1, kz1, 'dark_oak', 'north')
    v.stair(0, 4, kz1, 'stone_brick', 'south', 'top')
    # salón del trono
    for z in range(kz0 + 2, kz1): v.set(0, 1, z, 'red_carpet')
    v.set(0, 1, kz0 + 1, 'gold_block'); v.stair(0, 2, kz0 + 1, 'dark_oak', 'south'); v.stair(0, 1, kz0 + 2, 'dark_oak', 'south') if False else None
    v.stair(0, 2, kz0 + 1, 'dark_oak', 'south')
    for x in (-1, 1):
        v.set(x, 1, kz0 + 1, 'polished_andesite'); v.lantern(x, 2, kz0 + 1)
        for y in range(1, 6): v.set(x * 4, y, kz0 + 1, 'red_wool' if y > 1 else 'dark_oak_log', **({} if y > 1 else {'axis': 'y'}))
    for x in range(-4, 5):
        if x == 0: continue
        v.fence(x, 1, kz0 + 6, 'dark_oak'); v.set(x, 2, kz0 + 6, 'dark_oak_pressure_plate', powered='false')
        v.stair(x, 1, kz0 + 5, 'dark_oak', 'north') if x % 2 else v.stair(x, 1, kz0 + 7, 'dark_oak', 'south')
    for z in range(kz0 + 3, kz1 - 1, 4):
        for y in (4, 5): v.set(0, y, z, 'chain', axis='y', waterlogged='false')
        v.lantern(0, 3, z, hanging=True) if False else v.lantern(0, 4, z, hanging=True)
    for y in range(1, Hk + 1): v.set(kx1 - 1, y, kz0 + 1, 'ladder', facing='west', waterlogged='false')
    # pisos superiores: dormitorios y armería
    for f in (1, 2):
        y = f * 6 + 1
        for i, x in enumerate(range(kx0 + 2, kx1 - 1, 3)):
            v.bed(x, y, kz0 + 1, ['red', 'blue', 'yellow'][i % 3], 'north')
        for x in range(kx0 + 1, kx1, 3): v.set(x, y, kz1 - 1, 'chest', facing='north', type='single', waterlogged='false')
        v.lantern(0, y + 3, (kz0 + kz1) // 2, hanging=True)
    # edificios del patio: cuartel (este) y establo (oeste), pozo
    from kit import House, palette
    pal = palette(2)
    barr = House(seed + 1, pal, [(-3, -6, 3, 6)], floors=2, rooms=['living', 'bed'], jetty=False, chimney=False).v
    merge(v, barr, 12, 0, 4)
    for x in range(-15, -9):
        for z in range(-1, 12):
            if x in (-15, -10) or z in (-1, 11): v.fence(x, 1, z, 'spruce')
            v.set(x, 0, z, 'hay_block' if (x + z) % 7 == 0 else 'coarse_dirt', **({'axis': 'y'} if (x + z) % 7 == 0 else {}))
    for z in range(-1, 12, 4):
        for y in (1, 2, 3): v.log(-15, y, z, 'spruce_log', 'y')
    for z in range(-2, 13):
        for x in range(-16, -9): v.slab(x, 4, z, 'spruce', 'bottom')
    v.set(-10, 1, 5, 'spruce_fence_gate', facing='east', open='false', in_wall='false', powered='false')
    # pozo y caminos
    for x in range(-1, 2):
        for z in range(4, 7):
            v.set(x, 0, z, 'water', level='0') if (x, z) == (0, 5) else v.set(x, 0, z, 'stone_bricks')
            if (x, z) != (0, 5): v.set(x, 1, z, 'stone_brick_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false')
    for z in range(kz1 + 1, R - 3):
        for x in (-1, 0, 1): v.set(x, 0, z, 'polished_andesite' if (x + z) % 2 else 'stone_bricks')
    for z in range(kz1 + 2, R - 3, 5):
        for x in (-2, 2): v.set(x, 1, z, 'stone_brick_wall', up='true', north='none', south='none', east='none', west='none', waterlogged='false'); v.lantern(x, 2, z)
    v.core = (0, 1, R + 3)
    return v
