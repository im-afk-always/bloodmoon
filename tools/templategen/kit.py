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

def flue(v, cx, cz, y0, ytop, mat, mouth=(1, 0)):
    """Chimenea de conducto hueco: columna de aire en (cx,cz) desde el hogar (y0) hasta ytop, encerrada por sus cuatro
    vecinos; la boca del hogar (lado mouth) queda abierta en y0 y y0+1. Fogata encendida en el hogar y otra dentro del
    conducto dos bloques bajo el remate: el humo sale por arriba y el fuego no asoma."""
    for y in range(y0, ytop + 1):
        for a, b in DIRS4:
            if (a, b) == mouth and y < y0 + 2:
                v.air(cx + a, y, cz + b); continue
            v.set(cx + a, y, cz + b, mat(cx + a, y, cz + b))
        if y > y0: v.air(cx, y, cz)
    fire = dict(facing='east', lit='true', signal_fire='false', waterlogged='false')
    v.set(cx, y0, cz, 'campfire', **fire)
    v.set(cx, ytop - 2, cz, 'campfire', **fire)

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
        self.unblock()
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
        """Chimenea con conducto hueco desde el hogar hasta arriba: el fuego del hogar se ve en la sala y otra fogata,
        metida en el conducto bajo el remate, echa el humo sin asomar."""
        v, p = self.v, self.p
        x0, z0, x1, z1 = self.rects[0]
        ytop = self.top + max(self.Hm.values()) + 2
        if x1 - x0 >= 10:
            cx, cz, (mx, mz) = x0 + 1, (z0 + z1) // 2, (1, 0)       # casa ancha: contra el muro lateral
        else:
            cx, cz, (mx, mz) = (x0 + x1) // 2, z0 + 1, (0, 1)       # casa angosta: contra el muro del fondo
        flue(v, cx, cz, 1, ytop, lambda x, y, z: pick(p['chimney'], x, y, z, 8), mouth=(mx, mz))
        face = NAME[(-mx, -mz)]
        for k in (-1, 0, 1):
            hx, hz = cx + mx + k * mz, cz + mz + k * mx           # campana sobre la boca del hogar
            v.stair(hx, 3, hz, 'brick', face, 'top')
            for y in range(1, 4): self.reserved.add((hx, y, hz))
        for f in range(1, self.floors):
            for y in range(f * self.H + 1, f * self.H + 3): self.reserved.add((cx + mx, y, cz + mz))

    # ---------------------------------------------------------------- escalera
    def build_stairs(self):
        """Un tramo recto por piso, contra un muro: H escalones con 3 de alto libre encima de cada uno, la boca de
        llegada abierta en el piso de arriba, una celda libre al pie y otra al llegar, y un pasillo reservado desde la
        puerta (o la llegada anterior) hasta el pie, para que los muebles nunca tapen el paso. 2 de ancho si entra."""
        v, p, H = self.v, self.p, self.H
        dx0, dz0 = self.door
        start = (dx0, dz0 - 1)
        self.stair_cells = set()
        self.arrival = {}
        for f in range(self.floors - 1):
            C, P = self.cells[f], self.per[f]
            Cu, Pu = self.cells[f + 1], self.per[f + 1]
            inner = {c for c in C if c not in P}
            inner_up = {c for c in Cu if c not in Pu}
            y0 = f * H
            run = self.pick_run(inner, inner_up, y0, start, f)
            if run is None:
                raise ValueError(f'sin lugar para la escalera del piso {f}')
            lines, face, feet, exits = run
            for line in lines:
                for i, (x, z) in enumerate(line):
                    y = y0 + 1 + i
                    v.stair(x, y, z, p['wood'], face, 'bottom')
                    for yy in range(y0 + 1, y): v.set(x, yy, z, p['floor'])
                    for yy in range(y + 1, y + 4): v.air(x, yy, z)
                    for yy in range(y0 + 1, y + 4): self.reserved.add((x, yy, z))
                    self.stair_cells.add((x, z, f))
            for (x, z) in feet:
                for yy in range(y0 + 1, y0 + 4): v.air(x, yy, z); self.reserved.add((x, yy, z))
            for (x, z) in exits:
                for yy in range(y0 + H + 1, y0 + H + 4): v.air(x, yy, z); self.reserved.add((x, yy, z))
            # baranda alrededor de la boca, salvo la llegada y los lados contra el muro
            hole = {c for line in lines for c in line[:-1]}
            steps = {c for line in lines for c in line}
            yu = y0 + H + 1
            def reach_up():
                from collections import deque
                seen = {exits[0]}; q = deque([exits[0]])
                while q:
                    c = q.popleft()
                    for a, b in DIRS4:
                        n = (c[0] + a, c[1] + b)
                        if n in seen or n not in inner_up or n in steps: continue
                        if any(v.get(n[0], yy, n[1]) is not None and v.get(n[0], yy, n[1]).n != 'air' for yy in (yu, yu + 1)): continue
                        seen.add(n); q.append(n)
                return len(seen)
            base = reach_up()
            for (x, z) in sorted(hole):
                for a, b in DIRS4:
                    n = (x + a, z + b)
                    if n in steps or n in exits or n not in inner_up: continue
                    if (n[0], yu, n[1]) in self.reserved: continue
                    s = v.get(n[0], yu, n[1])
                    if s is not None and s.n != 'air': continue
                    v.fence(n[0], yu, n[1], p['fence'])
                    r = reach_up()
                    if r < base - 1:            # esta baranda encerraría parte del piso: queda el hueco de paso
                        v.air(n[0], yu, n[1]); continue
                    base = r
                    self.reserved.add((n[0], yu, n[1])); self.reserved.add((n[0], yu + 1, n[1]))
            self.reserve_path(inner, start, feet[0], y0)
            start = exits[0]
            self.arrival[f + 1] = start
        self.last_arrival = start

    def pick_run(self, inner, inner_up, y0, start, f):
        """Elige dónde va el tramo: celdas libres de punta a punta, preferentemente contra el muro del fondo."""
        H, v, p = self.H, self.v, self.p
        decks = {p['floor'], p.get('floor2'), 'air'}

        def free(c, ya, yb, foot=False):
            if (c[0], y0, c[1]) not in v.b or v.b[(c[0], y0, c[1])].n == 'air': return False   # tiene que haber piso
            for yy in range(ya, yb + 1):
                s = v.get(c[0], yy, c[1])
                if yy == y0 + H:
                    if s is not None and s.n not in decks: return False
                elif s is not None and s.n != 'air': return False
                if (c[0], yy, c[1]) in self.reserved and not (foot and c == start): return False
            return True

        def free_up(c):
            for yy in range(y0 + H + 1, y0 + H + 4):
                s = v.get(c[0], yy, c[1])
                if s is not None and s.n != 'air': return False
            s = v.get(c[0], y0 + H, c[1])
            return s is not None and s.n != 'air'

        def open_at(c, y):
            return all((v.get(c[0], yy, c[1]) is None or v.get(c[0], yy, c[1]).n == 'air') for yy in (y, y + 1))

        def flood(seed, cells, y, banned):
            from collections import deque
            if seed not in cells or seed in banned: return set()
            seen = {seed}; q = deque([seed])
            while q:
                c = q.popleft()
                for a, b in DIRS4:
                    n = (c[0] + a, c[1] + b)
                    if n in seen or n not in cells or n in banned or not open_at(n, y): continue
                    seen.add(n); q.append(n)
            return seen

        up_free = {c for c in inner_up if open_at(c, y0 + H + 1)}
        xs = {c[0] for c in inner}; zs = {c[1] for c in inner}
        wide = len(xs) >= 6 and len(zs) >= 6
        cands = []
        for (dx, dz), face in (((-1, 0), 'west'), ((1, 0), 'east'), ((0, -1), 'north'), ((0, 1), 'south')):
            for sx, sz in ((dz, dx), (-dz, -dx)):            # el costado del tramo que da al muro (si lo hay)
                for c0 in inner:
                    against = (c0[0] + sx, c0[1] + sz) not in inner
                    line = [(c0[0] + dx * i, c0[1] + dz * i) for i in range(H)]
                    foot = (c0[0] - dx, c0[1] - dz)
                    ex = (c0[0] + dx * H, c0[1] + dz * H)
                    if not all(c in inner for c in line) or foot not in inner: continue
                    if not all(c in inner_up for c in line[1:]) or ex not in inner_up: continue
                    if start in line: continue
                    if not all(free(c, y0 + 1, y0 + H + 3) for c in line) or not free(foot, y0 + 1, y0 + 3, True): continue
                    if not free_up(ex): continue
                    lines, feet, exits = [line], [foot], [ex]
                    l2 = [(c[0] - sx, c[1] - sz) for c in line]
                    f2, e2 = (foot[0] - sx, foot[1] - sz), (ex[0] - sx, ex[1] - sz)
                    if wide and start not in l2 and all(c in inner for c in l2 + [f2]) and all(c in inner_up for c in l2[1:] + [e2]) \
                            and all(free(c, y0 + 1, y0 + H + 3) for c in l2) and free(f2, y0 + 1, y0 + 3, True) and free_up(e2):
                        lines.append(l2); feet.append(f2); exits.append(e2)
                    steps = {c for ln in lines for c in ln}
                    # abajo: desde la entrada (o la llegada anterior) se llega al pie sin pasar por la escalera
                    down = flood(start, inner | {start}, y0 + 1, steps)
                    if feet[0] not in down and feet[0] != start: continue
                    # y el tramo no deja aislada parte de este piso (con la boca y barandas del tramo de abajo)
                    open_down = {c for c in inner if open_at(c, y0 + 1)} - steps
                    ratio_down = len(down & open_down) / max(1, len(open_down))
                    # arriba: desde la llegada se recorre casi todo el piso (la baranda no lo encierra)
                    hole = {c for ln in lines for c in ln[:-1]}
                    rails = {(x + a, z + b) for (x, z) in hole for a, b in DIRS4} - steps - set(exits)
                    reach = flood(exits[0], up_free, y0 + H + 1, steps | rails)
                    others = up_free - steps - rails
                    ratio = len(reach) / max(1, len(others))
                    if ratio < 0.3: continue
                    back = (sz == -1 and dz == 0)
                    dist = abs(foot[0] - start[0]) + abs(foot[1] - start[1])
                    # lo primero es no partir el piso de arriba en dos; después, ancho y contra el muro del fondo
                    score = (400 if ratio >= 0.95 else 250 if ratio >= 0.8 else 0) + (400 if ratio_down >= 0.95 else 250 if ratio_down >= 0.8 else 0) \
                        + len(lines) * 100 + (40 if back else 0) \
                        + (30 if against else 0) - dist * 0.5 + len(reach) * 0.5 + (5 if (dx > 0) == (f % 2 == 1) else 0)
                    cands.append((score, lines, face, feet, exits))
        if not cands: return None
        cands.sort(key=lambda t: -t[0])
        return cands[0][1:]

    def reserve_path(self, inner, a, b, y0):
        """Reserva (sin muebles) el camino más corto entre a y b por el interior del piso."""
        from collections import deque
        prev = {a: None}; q = deque([a])
        while q:
            c = q.popleft()
            if c == b: break
            for dx, dz in DIRS4:
                n = (c[0] + dx, c[1] + dz)
                if n in prev or n not in inner: continue
                s = self.v.get(n[0], y0 + 1, n[1])
                if s is not None and s.n != 'air': continue
                prev[n] = c; q.append(n)
        c = b if b in prev else None
        while c is not None:
            for yy in (y0 + 1, y0 + 2): self.reserved.add((c[0], yy, c[1]))
            c = prev[c]

    def has_attic(self):
        return False

    MOVABLE = ('chest', 'barrel', 'crafting_table', 'bookshelf', 'furnace', 'smoker', 'water_cauldron', 'composter', 'hay_block',
               'flower_pot', 'lantern')

    def unblock(self):
        """Saca muebles que parten un piso: desde la entrada del piso (puerta o llegada de la escalera) hay que poder
        recorrer casi todo; si un mueble (o una cama) corta el paso, se quita."""
        v, H = self.v, self.H
        def movable(s):
            return s is not None and (s.n in self.MOVABLE or s.n.endswith(('_bed', '_stairs', '_fence', '_pressure_plate', '_carpet'))
                                      or s.n.startswith('potted_'))
        def walk_ok(x, y, z):
            a, b = v.get(x, y, z), v.get(x, y + 1, z)
            return (a is None or a.n == 'air' or a.n.endswith('_carpet')) and (b is None or b.n == 'air')
        for f in range(self.floors):
            y = f * H + 1
            C, P = self.cells[f], self.per[f]
            inner = {c for c in C if c not in P and (c[0], c[1], f) not in getattr(self, 'stair_cells', set())}
            entry = self.arrival.get(f) if hasattr(self, 'arrival') else None
            if entry is None: entry = (self.door[0], self.door[1] - 1)
            def flood():
                from collections import deque
                if entry not in inner and f > 0: return set()
                seen = {entry}; q = deque([entry])
                while q:
                    c = q.popleft()
                    for a, b in DIRS4:
                        n = (c[0] + a, c[1] + b)
                        if n in seen or n not in inner or not walk_ok(n[0], y, n[1]): continue
                        seen.add(n); q.append(n)
                return seen
            for _ in range(12):
                got = flood()
                cand = [c for c in inner if c not in got and walk_ok(c[0], y, c[1])]
                if not cand: break
                # el mueble más cercano a la zona alcanzada que separa una celda libre inalcanzable
                best = None
                for c in inner - got:
                    s = v.get(c[0], y, c[1])
                    if not movable(s): continue
                    if any((c[0] + a, c[1] + b) in got for a, b in DIRS4) and any((c[0] + a, c[1] + b) in cand for a, b in DIRS4):
                        best = c; break
                if best is None: break
                s = v.get(best[0], y, best[1])
                if s.n.endswith('_bed'):
                    for (k, t) in list(v.b.items()):
                        if k[1] == y and t.n == s.n and abs(k[0] - best[0]) + abs(k[2] - best[1]) == 1: v.air(*k)
                v.air(best[0], y, best[1])
                above = v.get(best[0], y + 1, best[1])
                if above is not None and (above.n.endswith('_pressure_plate') or above.n.startswith('potted_') or above.n in ('lantern', 'flower_pot')):
                    v.air(best[0], y + 1, best[1])

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
                roomy = len(set(xs)) >= 7 and len(set(zs)) >= 7       # la mesa con sillas solo si queda lugar para circular
                if roomy and all(self.free(tx + a, y, tz + b) and self.free(tx + a, y + 1, tz + b) for a in (-2, -1, 0, 1, 2) for b in (-1, 0, 1)):
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
                kit = kit[:max(3, len(walls) // 3)]      # un mueble cada tres celdas de muro, no una pared corrida
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

def library_hall(v, hw, hd, H, wood, deck, fence, ring=3, stair_w=2, lantern_every=4):
    """Interior de biblioteca sobre un salón de un solo piso de alto H (muros en |x|=hw, z=-hd..hd, puerta al frente):
    galería en U (fondo y costados) a y=4 con baranda, escalinata central que sube hacia el fondo, estanterías contra los
    muros abajo y arriba (sin tapar ventanas), mesas de lectura, faroles bajo la galería y araña en el centro."""
    ix0, ix1, iz0, iz1 = -hw + 1, hw - 1, -hd + 1, hd - 1
    gy = 4
    def ring_at(x, z): return (z - iz0) < ring or (x - ix0) < ring or (ix1 - x) < ring
    xs = list(range(-(stair_w // 2), -(stair_w // 2) + stair_w))
    zt = iz0 + ring                                   # escalón de arriba, pegado a la galería del fondo
    steps = {(x, zt + (gy - 1 - i)): i for i in range(gy) for x in xs}
    exits = {(x, zt - 1) for x in xs}
    feet = {(x, zt + gy) for x in xs}
    def is_ladder(x, y, z):
        s = v.get(x, y, z); return s is not None and s.n == 'ladder'
    # galería
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            if not ring_at(x, z) or is_ladder(x, gy, z): continue
            v.set(x, gy, z, deck)
            for y in range(gy + 1, H): v.air(x, y, z) if not is_ladder(x, y, z) else None
    # escalinata (con relleno abajo) y cabeza libre
    for (x, z), i in steps.items():
        y = 1 + i
        v.stair(x, y, z, wood, 'north', 'bottom')
        for yy in range(1, y): v.set(x, yy, z, wood + '_planks')
        for yy in range(y + 1, min(H, y + 4)): v.air(x, yy, z)
    # baranda en el borde interior de la galería (salvo la llegada de la escalera)
    rail = set()
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            if not ring_at(x, z) or (x, z) in exits or is_ladder(x, gy + 1, z): continue
            if any(not ring_at(x + a, z + b) and ix0 <= x + a <= ix1 and iz0 <= z + b <= iz1 for a, b in DIRS4):
                v.fence(x, gy + 1, z, fence); rail.add((x, z))
    def wall_dirs(x, z): return [(a, b) for a, b in DIRS4 if not (ix0 <= x + a <= ix1 and iz0 <= z + b <= iz1)]
    def shelf_ok(x, y, z):
        if is_ladder(x, y, z): return False
        for a, b in wall_dirs(x, z):
            w = v.get(x + a, y, z + b)
            if w is not None and (w.n.endswith('pane') or w.n.endswith('_door') or w.n == 'air'): return False
        return True
    # estanterías: bajo la galería (y 1..3) y en la galería (y 5..H-1), contra los muros
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            if not wall_dirs(x, z) or z == iz1 and abs(x) <= 1: continue
            if not ring_at(x, z) and z != iz1: continue
            for y in list(range(1, gy)) + list(range(gy + 1, H)):
                if y > gy and (not ring_at(x, z) or (x, z) in rail): continue
                if y < gy and (x, z) in feet: continue
                s = v.get(x, y, z)
                if (s is None or s.n == 'air') and shelf_ok(x, y, z): v.set(x, y, z, 'bookshelf')
    # mesas de lectura en la nave: a los costados de la escalinata, dejando libre el pasillo central
    for z in range(zt + gy + 1, iz1, 2):
        for sx in (-1, 1):
            tx = sx * (max(xs) + 2)
            if (v.get(tx, 1, z) is not None and v.get(tx, 1, z).n != 'air'): continue
            v.set(tx, 1, z, wood + '_slab', type='top', waterlogged='false')
            v.set(tx, 2, z, 'candle', candles='3', lit='true', waterlogged='false')
            v.stair(tx, 1, z - 1, wood, 'north', 'bottom') if (v.get(tx, 1, z - 1) is None or v.get(tx, 1, z - 1).n == 'air') else None
    # luces
    for x in range(ix0, ix1 + 1, lantern_every):
        for z in range(iz0, iz1 + 1, lantern_every):
            if ring_at(x, z) and (v.get(x, gy - 1, z) is None or v.get(x, gy - 1, z).n == 'air') and (x, z) not in steps:
                v.lantern(x, gy - 1, z, hanging=True)
            if ring_at(x, z) and (x, z) not in rail and (v.get(x, H - 1, z) is None or v.get(x, H - 1, z).n == 'air'):
                v.lantern(x, H - 1, z, hanging=True)
    cz = (zt + gy + iz1) // 2
    for y in range(H - 2, H): v.set(0, y, cz, 'chain', axis='y', waterlogged='false')
    v.lantern(0, H - 3, cz, hanging=True)
    return dict(feet=feet, steps=steps, zt=zt)

def tall_windows(v, y_from, y_to, ys):
    """Repite hacia arriba las ventanas del muro: donde hay vidrio en y_from, también en cada y de ys."""
    for (x, y, z), s in list(v.b.items()):
        if y == y_from and s.n.endswith('pane'):
            for yy in ys:
                w = v.get(x, yy, z)
                if w is not None and full(w): v.pane(x, yy, z)

def cutaway(v, ymax=None, zmax=None, xmax=None):
    g = V(); g.b = {k: s for k, s in v.b.items() if (ymax is None or k[1] <= ymax) and (zmax is None or k[2] <= zmax) and (xmax is None or k[0] <= xmax)}
    g.core = v.core
    return g
