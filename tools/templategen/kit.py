"""Kit de construcción de calidad para los edificios humanos (técnicas de la comunidad de builders):
- planta como unión de rectángulos (L, T, cruz) y pisos con voladizo (jetty) sostenido por ménsulas,
- planta baja de piedra con gradiente (adoquín, musgo, andesita), esquinas de sillería y zócalo de escalones,
- entramado de madera arriba (postes cada 4, vigas, cabezas de viga salientes) con relleno de revoque,
- ventanas 1×2 con alféizar y postigos abiertos, puerta con dintel, alero y farol,
- techo por mapa de alturas (dos aguas, cuatro aguas, cruces que se resuelven solas), alero de 1 con sofito,
  cumbrera de losas, hastiales con poste central y ventanita, chimenea con hogar,
- interiores amueblados por piso (sala-cocina, taller, dormitorios, desván) y escalera de verdad.
Coordenadas como hv.py: y=0 piso, puerta hacia +z, core = afuera de la puerta."""
import random
from hv import *

DIRS4 = [(1, 0), (-1, 0), (0, 1), (0, -1)]
NAME = {(1, 0): 'east', (-1, 0): 'west', (0, 1): 'south', (0, -1): 'north'}

def pick(options, x, y, z, salt=0):
    tot = sum(w for _, w in options)
    r = hsh(x, y, z, salt) * tot
    for b, w in options:
        r -= w
        if r <= 0: return b
    return options[-1][0]

def cells_of(rects):
    out = set()
    for r in rects:
        x0, z0, x1, z1 = r[:4]
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1): out.add((x, z))
    return out

def perim(cells):
    return {c for c in cells if any((c[0] + dx, c[1] + dz) not in cells for dx, dz in DIRS4)}

def outs(c, cells):
    return [(dx, dz) for dx, dz in DIRS4 if (c[0] + dx, c[1] + dz) not in cells]

def corner(c, cells):
    o = outs(c, cells)
    if len(o) >= 2: return True
    # esquina interior de una L: dos vecinos de perímetro en ángulo
    x, z = c
    diag = [(x + a, z + b) for a in (-1, 1) for b in (-1, 1)]
    return len(o) == 0 or any(d not in cells for d in diag) and len(o) == 1 and False

# --------------------------------------------------------------------- paletas
def palette(kind):
    p = dict(
        found=[('cobblestone', 6), ('mossy_cobblestone', 2), ('andesite', 2), ('stone', 1)],
        quoin='stone_bricks', plinth='cobblestone', frame='dark_oak_log', beamend='stripped_dark_oak_log',
        infill=[('calcite', 6), ('white_concrete', 2), ('diorite', 1)],
        floor='spruce_planks', roof='dark_oak', roof2='spruce', ridge='dark_oak', soffit='dark_oak',
        trap='spruce', door='spruce', fence='spruce', sill='spruce', wood='spruce', bed='red', carpet='red',
        chimney=[('bricks', 6), ('stone_bricks', 1)])
    if kind == 1:   # cálida: arenisca lisa y pino
        p.update(infill=[('smooth_sandstone', 6), ('sandstone', 1)], frame='spruce_log', beamend='stripped_spruce_log', roof='spruce',
                 roof2='dark_oak', ridge='spruce', soffit='spruce', trap='dark_oak', bed='blue', carpet='blue', floor='oak_planks')
    elif kind == 2:  # ladrillo con pizarra
        p.update(infill=[('bricks', 7), ('granite', 1)], frame='dark_oak_log', roof='deepslate_tile', roof2='cobbled_deepslate',
                 ridge='deepslate_tile', soffit='dark_oak', trap='dark_oak', bed='green', carpet='green')
    elif kind == 3:  # abedul y roble oscuro
        p.update(infill=[('stripped_birch_wood', 6), ('birch_planks', 1)], frame='dark_oak_log', roof='spruce', roof2='dark_oak',
                 ridge='dark_oak', trap='dark_oak', bed='yellow', carpet='yellow', floor='dark_oak_planks')
    elif kind == 4:  # revoque de hongo y techo de barro
        p.update(infill=[('mushroom_stem', 6), ('calcite', 2)], frame='spruce_log', beamend='stripped_spruce_log', roof='dark_oak',
                 roof2='mud_brick', ridge='dark_oak', trap='spruce', bed='light_blue', carpet='light_blue')
    return p

CITY_COLORS = [
    [('white_concrete', 6), ('calcite', 1)], [('yellow_terracotta', 6), ('smooth_sandstone', 1)], [('light_gray_concrete', 6), ('andesite', 1)],
    [('cyan_terracotta', 1)], [('pink_terracotta', 6), ('white_terracotta', 1)], [('orange_terracotta', 6), ('terracotta', 1)],
    [('light_blue_terracotta', 1)], [('lime_terracotta', 6), ('green_terracotta', 1)]]

# --------------------------------------------------------------------- casa de entramado
class House:
    def __init__(self, seed, pal, rects, floors=2, jetty=True, hip=False, stone_ground=True, chimney=True, rooms=None, job=None,
                 H=4, gable_front=False, door=None, shop=False, porch=True):
        self.v = V(); self.p = pal; self.rng = random.Random(seed); self.reserved = set()
        self.rects = rects; self.floors = floors; self.H = H; self.hip = hip; self.job = job; self.shop = shop
        self.ridge = ['z' if gable_front else ('x' if (r[2] - r[0]) >= (r[3] - r[1]) else 'z') for r in rects]
        self.levels = []
        override = getattr(self, 'level_rects', None)
        for f in range(floors):
            if override:
                self.levels.append(override(f, rects)); continue
            rr = []
            for (x0, z0, x1, z1), rd in zip(rects, self.ridge):
                if jetty and f > 0:
                    if gable_front: rr.append((x0, z0, x1, z1 + 1))
                    elif rd == 'x': rr.append((x0, z0 - 1, x1, z1 + 1))
                    else: rr.append((x0 - 1, z0, x1 + 1, z1))
                else: rr.append((x0, z0, x1, z1))
            self.levels.append(rr)
        self.cells = [cells_of(rr) for rr in self.levels]
        self.per = [perim(c) for c in self.cells]
        self.top = floors * H
        g = self.cells[0]
        zmax = max(z for _, z in g)
        self.door = door or (0, zmax)
        self.stone_ground = stone_ground
        self.build_walls()
        self.build_windows()
        self.build_door(porch)
        self.build_roof()
        if chimney: self.build_chimney()
        self.build_stairs()
        self.furnish(rooms or [])
        self.v.core = (self.door[0], 1, self.door[1] + 1)

    # ---------------------------------------------------------------- muros
    def build_walls(self):
        v, p, H = self.v, self.p, self.H
        g = self.cells[0]
        dx0, dz0 = self.door
        for (x, z) in g:
            v.set(x, 0, z, pick(p['found'], x, 0, z, 1) if (x, z) in self.per[0] else p['floor'])
        for (x, z) in self.per[0]:
            for (ox, oz) in outs((x, z), g):
                nx, nz = x + ox, z + oz
                if (nx, nz) in g or (nx, nz) == (dx0, dz0 + 1): continue
                v.stair(nx, 0, nz, p['plinth'], NAME[(-ox, -oz)], 'bottom')
        for f in range(self.floors):
            y0 = f * H
            C, P = self.cells[f], self.per[f]
            stone = self.stone_ground and f == 0
            if f > 0:
                for (x, z) in C:
                    if (x, z) in P:
                        o = outs((x, z), C)
                        along_x = any(b != 0 for _, b in o)
                        v.log(x, y0, z, p['frame'], 'y' if len(o) >= 2 else ('x' if along_x else 'z'))
                    else:
                        v.set(x, y0, z, p['floor'])
            for (x, z) in C:
                if (x, z) in P: continue
                for y in range(y0 + 1, y0 + H): v.air(x, y, z)
            for (x, z) in P:
                o = outs((x, z), C)
                is_corner = len(o) >= 2
                along_x = any(b != 0 for _, b in o)
                k = x if along_x else z
                for y in range(y0 + 1, y0 + H + 1):
                    beam = (y == y0 + H)
                    if stone:
                        if beam and f < self.floors - 1:
                            continue   # la viga del piso de arriba ya va en y0 + H
                        if is_corner: v.set(x, y, z, p['quoin'] if y % 2 == 0 else pick(p['found'], x, y, z, 2))
                        else: v.set(x, y, z, pick(p['found'], x, y, z, 3))
                        continue
                    if beam and f < self.floors - 1:
                        continue
                    if beam:
                        v.log(x, y, z, p['frame'], 'y' if is_corner else ('x' if along_x else 'z'))
                    elif is_corner or k % 4 == 0:
                        v.log(x, y, z, p['frame'], 'y')
                    else:
                        v.set(x, y, z, pick(p['infill'], x, y, z, 4))
            if f > 0:
                lower = self.cells[f - 1]
                # ménsulas bajo el voladizo y cabezas de viga
                for (x, z) in P:
                    o = outs((x, z), C)
                    if len(o) != 1: continue
                    ox, oz = o[0]
                    along_x = oz != 0
                    k = x if along_x else z
                    if (x, z) not in lower and k % 4 == 0:
                        v.stair(x, y0 - 1, z, p['wood'], NAME[(-ox, -oz)], 'top')
                    if (x, z) in lower and k % 4 == 2:
                        if (x + ox, y0, z + oz) not in v.b:
                            v.log(x + ox, y0, z + oz, p['beamend'], 'x' if ox != 0 else 'z')

    # ---------------------------------------------------------------- ventanas
    def build_windows(self):
        v, p, H = self.v, self.p, self.H
        dx0, dz0 = self.door
        for f in range(self.floors):
            y0 = f * H
            C, P = self.cells[f], self.per[f]
            for (x, z) in P:
                o = outs((x, z), C)
                if len(o) != 1: continue
                ox, oz = o[0]
                along_x = oz != 0
                k = x if along_x else z
                if k % 4 != 2: continue
                if f == 0 and abs(x - dx0) <= 1 and z == dz0: continue
                if f == 0 and self.shop and oz == 1: continue
                nb = [(x + 1, z), (x - 1, z)] if along_x else [(x, z + 1), (x, z - 1)]
                if any(n not in P or len(outs(n, C)) >= 2 for n in nb): continue
                for y in (y0 + 2, y0 + 3):
                    v.pane(x, y, z)
                sx, sz = x + ox, z + oz
                if (sx, y0 + 1, sz) not in v.b:
                    v.stair(sx, y0 + 1, sz, p['sill'], NAME[(-ox, -oz)], 'top')
                if f > 0 and hsh(x, y0, z, 11) < 0.45: continue
                for n in nb:
                    px, pz = n[0] + ox, n[1] + oz
                    for y in (y0 + 2, y0 + 3):
                        if (px, y, pz) not in v.b:
                            v.trapdoor(px, y, pz, p['trap'], NAME[(ox, oz)], 'bottom', 'true')

    # ---------------------------------------------------------------- puerta
    def build_door(self, porch):
        v, p = self.v, self.p
        dx0, dz0 = self.door
        if self.shop:
            # vidriera de tienda: arcada de 3 abierta con mostrador
            for x in range(dx0 - 1, dx0 + 2):
                for y in (1, 2): v.air(x, y, dz0)
                v.log(x, 3, dz0, p['frame'], 'x')
            v.stair(dx0 - 1, 2, dz0, p['wood'], 'east', 'top') if False else None
            for x in (dx0 - 2, dx0 + 2):
                for y in (1, 2): v.log(x, y, dz0, p['frame'], 'y')
            for x in range(dx0 - 2, dx0 + 3):
                v.set(x, 4, dz0 + 1, 'white_wool' if (x + dx0) % 2 == 0 else self.p['bed'] + '_wool')
            v.lantern(dx0 + 2, 3, dz0 + 1, hanging=True) if (dx0 + 2, 3, dz0 + 1) not in v.b else None
            self.reserved.update({(dx0, 1, dz0 - 1), (dx0, 2, dz0 - 1)})
            return
        v.door(dx0, 1, dz0, p['door'], 'north')
        v.set(dx0, 3, dz0, p['quoin'] if self.stone_ground else p['frame'], **({} if self.stone_ground else {'axis': 'x'}))
        if porch:
            for x in range(dx0 - 1, dx0 + 2):
                if x == dx0: v.slab(x, 3, dz0 + 1, p['wood'], 'top')
                else: v.stair(x, 3, dz0 + 1, p['wood'], 'north', 'top')
            v.lantern(dx0 + 1, 2, dz0 + 1, hanging=True)
        self.reserved.update({(dx0, 1, dz0 - 1), (dx0, 2, dz0 - 1)})

    # ---------------------------------------------------------------- techo
    def build_roof(self):
        v, p = self.v, self.p
        top = self.top
        rects = [(x0 - 1, z0 - 1, x1 + 1, z1 + 1, rd) for (x0, z0, x1, z1), rd in zip(self.levels[-1], self.ridge)]
        Hm = {}
        for (x0, z0, x1, z1, rd) in rects:
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if self.hip: h = min(x - x0, x1 - x, z - z0, z1 - z)
                    elif rd == 'x': h = min(z - z0, z1 - z)
                    else: h = min(x - x0, x1 - x)
                    if h > Hm.get((x, z), -1): Hm[(x, z)] = h
        self.Hm = Hm
        C, P = self.cells[-1], self.per[-1]
        for (x, z), h in Hm.items():
            y = top + h
            hi = max((Hm.get((x + dx, z + dz), -1), (dx, dz)) for dx, dz in DIRS4)
            if hi[0] > h:
                mat = p['roof'] if hsh(x, y, z, 7) > 0.1 else p['roof2']
                v.stair(x, y, z, mat, NAME[hi[1]], 'bottom')
                # sofito: escalón invertido bajo el alero
                if (x, z) not in C and h == 0:
                    pass
            else:
                v.slab(x, y, z, p['ridge'], 'bottom')
                v.set(x, y - 1, z, p['floor'])
            # sin huecos: si un vecino está 2+ más abajo, se rellena la cara vertical con bloque de techo
            lo = min(Hm.get((x + dx, z + dz), -1) for dx, dz in DIRS4)
            if lo >= 0 and lo < h - 1 and (x, z) not in C:
                for yy in range(top + lo + 1, y):
                    v.set(x, yy, z, p['ridge'] + '_planks' if p['ridge'] in ('dark_oak', 'spruce', 'oak', 'birch', 'acacia', 'jungle')
                          else p['ridge'] + 's')
            for yy in range(top + 1, y):
                if (x, z) in P:
                    gc = (x % 4 == 0) if any(b != 0 for _, b in outs((x, z), C)) else (z % 4 == 0)
                    v.set(x, yy, z, pick(p['infill'], x, yy, z, 5)) if not gc else v.log(x, yy, z, p['frame'], 'y')
                elif (x, z) in C:
                    if (x, yy, z) not in v.b: v.air(x, yy, z)
        # ventanita en cada hastial
        for (x, z) in P:
            o = outs((x, z), C)
            if len(o) != 1: continue
            h = Hm.get((x, z), 0)
            if h >= 4 and all(Hm.get((x + a, z + b), 0) >= 3 for a, b in DIRS4 if (x + a, z + b) in P):
                ox, oz = o[0]
                # centro del hastial: altura máxima en su fila
                if Hm.get((x - oz, z - ox), 0) <= h and Hm.get((x + oz, z + ox), 0) <= h:
                    v.pane(x, top + h - 2, z)

    def build_chimney(self):
        v, p = self.v, self.p
        x0, z0, x1, z1 = self.rects[0]
        cx, cz = x0 + 1, (z0 + z1) // 2
        ytop = self.top + max(self.Hm.values()) + 2
        for y in range(2, ytop + 1):
            v.set(cx, y, cz, pick(p['chimney'], cx, y, cz, 8))
        v.set(cx, 1, cz, 'campfire', facing='east', lit='true', signal_fire='false', waterlogged='false')
        v.set(cx, ytop + 1, cz, 'campfire', facing='east', lit='true', signal_fire='false', waterlogged='false')
        for dz in (-1, 1):
            for y in (1, 2): v.set(cx, y, cz + dz, 'bricks')
            v.stair(cx + 1, 3, cz + dz, 'brick', 'west', 'top')
        v.stair(cx + 1, 3, cz, 'brick', 'west', 'top')
        for y in range(1, 4):
            for dz in (-1, 0, 1): self.reserved.add((cx + 1, y, cz + dz))
        for f in range(1, self.floors):
            self.reserved.add((cx + 1, f * self.H + 1, cz))

    # ---------------------------------------------------------------- escalera
    def build_stairs(self):
        v, p, H = self.v, self.p, self.H
        floors = self.floors + (1 if self.has_attic() else 0)
        for f in range(min(floors, self.floors + 1) - 1):
            if f >= self.floors: break
            C, P = self.cells[f], self.per[f]
            inner = [c for c in C if c not in P]
            zmin = min(z for _, z in inner)
            xs = sorted(x for x, z in inner if z == zmin)
            if len(xs) < 6: continue
            if f % 2 == 0: run = [(xs[-1] - i, zmin) for i in range(H)]; face = 'west'
            else: run = [(xs[0] + i, zmin) for i in range(H)]; face = 'east'
            y0 = f * H
            target_cells = self.cells[f + 1] if f + 1 < self.floors else C
            for i, (x, z) in enumerate(run):
                v.stair(x, y0 + 1 + i, z, p['wood'], face, 'bottom')
                for y in range(y0 + 1, y0 + 1 + i): v.set(x, y, z, p['floor'])
                for y in range(y0 + 1 + i + 1, y0 + H + 3):
                    if y >= y0 + H and i >= 1: v.air(x, y, z)
                for y in range(y0 + 1, y0 + H + 3):
                    self.reserved.add((x, y, z)); self.reserved.add((x, y, z + 1))
            # baranda en el piso de arriba
            for i, (x, z) in enumerate(run):
                if i >= 1 and (x, z + 1) in target_cells and (x, z + 1) not in (self.per[f + 1] if f + 1 < self.floors else P):
                    v.fence(x, y0 + H + 1, z + 1, p['fence'])

    def has_attic(self):
        return False

    # ---------------------------------------------------------------- muebles
    def free(self, x, y, z):
        s = self.v.get(x, y, z)
        return (s is None or s.n == 'air') and (x, y, z) not in self.reserved

    def furnish(self, rooms):
        v, p, rng, H = self.v, self.p, self.rng, self.H
        for f in range(self.floors):
            y = f * H + 1
            C, P = self.cells[f], self.per[f]
            inner = sorted(c for c in C if c not in P)
            if not inner: continue
            xs = [x for x, _ in inner]; zs = [z for _, z in inner]
            cx, cz = (min(xs) + max(xs)) // 2, (min(zs) + max(zs)) // 2
            role = rooms[f] if f < len(rooms) else ('bed' if f > 0 else 'living')
            if self.free(cx, y + H - 2, cz): v.lantern(cx, y + H - 2, cz, hanging=True)
            walls = [c for c in inner if any((c[0] + a, c[1] + b) in P for a, b in DIRS4)]
            walls.sort(key=lambda c: hsh(c[0], y, c[1], 21))
            def facing_in(c):
                for a, b in DIRS4:
                    if (c[0] + a, c[1] + b) in P: return NAME[(-a, -b)]
                return 'south'
            def put(c, blk, **pp):
                if self.free(c[0], y, c[1]) and self.free(c[0], y + 1, c[1]):
                    v.set(c[0], y, c[1], blk, **pp); self.reserved.add((c[0], y, c[1])); return True
                return False
            if role in ('living', 'shop'):
                tx, tz = cx, cz + (1 if role == 'shop' else 0)
                if all(self.free(tx + a, y, tz + b) for a in (-1, 0, 1) for b in (-1, 0, 1)):
                    v.fence(tx, y, tz, p['fence']); v.set(tx, y + 1, tz, p['wood'] + '_pressure_plate', powered='false')
                    self.reserved.add((tx, y, tz))
                    for a, b in DIRS4:
                        if rng.random() < 0.85:
                            v.stair(tx + a, y, tz + b, p['wood'], NAME[(a, b)], 'bottom'); self.reserved.add((tx + a, y, tz + b))
                    for a in (-1, 1):
                        for b in (-1, 1): v.set(tx + a, y, tz + b, p['carpet'] + '_carpet')
                kit = [('crafting_table', {}), ('barrel', {'facing': 'up', 'open': 'false'}), ('furnace', {'lit': 'false'}),
                       ('water_cauldron', {'level': '3'}), ('chest', {'type': 'single', 'waterlogged': 'false'}), ('bookshelf', {}),
                       ('barrel', {'facing': 'up', 'open': 'false'}), ('smoker', {'lit': 'false'})]
                i = 0
                for c in walls:
                    if i >= len(kit): break
                    name, pp = kit[i]
                    if name in ('furnace', 'smoker', 'chest'): pp = dict(pp, facing=facing_in(c))
                    if put(c, name, **pp):
                        i += 1
                        if name in ('crafting_table', 'barrel', 'bookshelf') and self.free(c[0], y + 1, c[1]) and rng.random() < 0.5:
                            v.set(c[0], y + 1, c[1], rng.choice(['potted_red_tulip', 'potted_fern', 'potted_dandelion', 'flower_pot']))
            elif role == 'bed':
                beds = 0
                for c in walls:
                    if beds >= 2: break
                    for a, b in DIRS4:
                        if (c[0] + a, c[1] + b) not in P: continue
                        fx, fz = c[0] - a, c[1] - b
                        if (fx, fz) in C and (fx, fz) not in P and self.free(c[0], y, c[1]) and self.free(fx, y, fz) \
                                and self.free(fx - a, y, fz - b):
                            v.bed(c[0], y, c[1], p['bed'], NAME[(a, b)])
                            self.reserved.update({(c[0], y, c[1]), (fx, y, fz), (fx - a, y, fz - b)})
                            side = (c[0] + b, c[1] + a)
                            if side in C and side not in P and self.free(side[0], y, side[1]):
                                v.set(side[0], y, side[1], 'barrel', facing='up', open='false'); self.reserved.add((side[0], y, side[1]))
                                if self.free(side[0], y + 1, side[1]): v.lantern(side[0], y + 1, side[1])
                            beds += 1
                        break
                for c in inner:
                    if self.free(c[0], y, c[1]) and abs(c[0] - cx) <= 1 and abs(c[1] - cz) <= 1:
                        v.set(c[0], y, c[1], p['carpet'] + '_carpet')
                k = 0
                for c in walls:
                    if k >= 3: break
                    nm = ['chest', 'bookshelf', 'barrel'][k]
                    pp = {'facing': facing_in(c), 'type': 'single', 'waterlogged': 'false'} if nm == 'chest' else \
                         ({'facing': 'up', 'open': 'false'} if nm == 'barrel' else {})
                    if put(c, nm, **pp): k += 1
            elif role == 'store':
                k = 0
                for c in walls:
                    if k >= 8: break
                    if put(c, rng.choice(['barrel', 'hay_block', 'chest']), **({'facing': 'up', 'open': 'false'})) if False else \
                            put(c, 'barrel', facing='up', open='false'):
                        k += 1
        if self.job:
            from buildings import WS
            ws, pp = WS[self.job]
            C, P = self.cells[0], self.per[0]
            inner = [c for c in C if c not in P]
            zmin = min(z for _, z in inner)
            for c in sorted(inner, key=lambda c: (c[1] != zmin, abs(c[0]))):
                if self.free(c[0], 1, c[1]) and self.free(c[0], 2, c[1]):
                    v.set(c[0], 1, c[1], ws, **pp); self.reserved.add((c[0], 1, c[1])); break

def cutaway(v, ymax=None, zmax=None, xmax=None):
    g = V(); g.b = {k: s for k, s in v.b.items() if (ymax is None or k[1] <= ymax) and (zmax is None or k[2] <= zmax) and (xmax is None or k[0] <= xmax)}
    g.core = v.core
    return g
