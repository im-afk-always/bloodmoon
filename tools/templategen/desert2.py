"""Desierto: casas de adobe/arenisca con muros gruesos, vigas salientes, ventanas con celosía, puertas en arco,
terrazas escalonadas con toldos; ciudad de varios pisos con bandas de color y cúpulas; palacio, templo, zoco y torre."""
import random
from kit import *
from buildings import WS

def dpal(kind):
    p = dict(found=[('cut_sandstone', 5), ('sandstone', 2)], wall=[('smooth_sandstone', 8), ('sandstone', 1)], trim='cut_sandstone',
             accent='chiseled_sandstone', band='orange_terracotta', wood='jungle', floor='cut_sandstone', floor2='jungle_planks',
             fence='jungle', trap='jungle', door='jungle', bed='orange', carpet='orange', plinth='sandstone', awning=['orange', 'white'],
             viga='stripped_jungle_log', dome='oxidized_cut_copper', infill=[('smooth_sandstone', 1)], quoin='cut_sandstone',
             chimney=[('sandstone', 1)], roof='sandstone', roof2='sandstone', ridge='smooth_sandstone', sill='sandstone', frame='stripped_jungle_log',
             beamend='stripped_jungle_log', soffit='jungle')
    if kind == 1:
        p.update(wall=[('white_terracotta', 6), ('smooth_sandstone', 1)], band='cyan_terracotta', bed='cyan', carpet='cyan', awning=['cyan', 'white'],
                 dome='oxidized_cut_copper', wood='acacia', fence='acacia', trap='acacia', door='acacia', floor2='acacia_planks', viga='stripped_acacia_log')
    elif kind == 2:
        p.update(wall=[('smooth_red_sandstone', 6), ('red_sandstone', 1)], trim='cut_red_sandstone', accent='chiseled_red_sandstone', band='white_terracotta',
                 found=[('cut_red_sandstone', 4), ('red_sandstone', 2)], plinth='red_sandstone', bed='red', carpet='red', awning=['red', 'yellow'],
                 dome='orange_terracotta')
    elif kind == 3:
        p.update(wall=[('smooth_quartz', 5), ('smooth_sandstone', 2)], band='yellow_terracotta', accent='chiseled_quartz_block', bed='white',
                 carpet='light_blue', awning=['blue', 'white'], dome='oxidized_cut_copper', trim='quartz_bricks')
    elif kind == 4:
        p.update(wall=[('yellow_terracotta', 5), ('smooth_sandstone', 1)], band='brown_terracotta', bed='yellow', carpet='brown',
                 awning=['brown', 'white'], dome='orange_terracotta')
    return p

class Adobe(House):
    """Reusa la planta, la escalera y el amueblado del kit; cambia muros, aberturas y techo."""
    def __init__(self, seed, pal, rects, floors=2, setback=3, dome=False, terrace=True, **kw):
        self.setback = setback; self.dome = dome; self.terrace = terrace
        def lv(f, rects):
            if f == 0: return list(rects)
            x0, z0, x1, z1 = rects[0]
            # los pisos de arriba retroceden: quedan terrazas al frente
            return [(x0, z0, x1, max(z0 + 4, z1 - setback * f))]
        self.level_rects = lv
        kw.setdefault('chimney', False); kw.setdefault('jetty', False)
        super().__init__(seed, pal, rects, floors=floors, **kw)

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
            if f > 0:
                for (x, z) in C: v.set(x, y0, z, p['trim'] if (x, z) in P else p['floor2'])
            for (x, z) in C:
                if (x, z) in P: continue
                for y in range(y0 + 1, y0 + H): v.air(x, y, z)
            for (x, z) in P:
                o = outs((x, z), C)
                for y in range(y0 + 1, y0 + H + 1):
                    if y == y0 + H: blk = p['trim']
                    elif y == y0 + 1 and f == 0: blk = pick(p['found'], x, y, z, 2)
                    elif y == y0 + H - 1: blk = p['band']
                    else: blk = pick(p['wall'], x, y, z, 3)
                    v.set(x, y, z, blk)
                # vigas salientes bajo el techo
                if len(o) == 1:
                    ox, oz = o[0]
                    k = x if oz != 0 else z
                    if k % 4 == 0 and (x + ox, y0 + H - 1, z + oz) not in v.b:
                        v.log(x + ox, y0 + H - 1, z + oz, p['viga'], 'x' if ox != 0 else 'z')

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
                k = x if oz != 0 else z
                if k % 3 != 1: continue
                if f == 0 and abs(x - dx0) <= 1 and z == dz0: continue
                nb = [(x + 1, z), (x - 1, z)] if oz != 0 else [(x, z + 1), (x, z - 1)]
                if any(n not in P or len(outs(n, C)) >= 2 for n in nb): continue
                for y in (y0 + 2, y0 + 3): v.pane(x, y, z)
                # arco: escalones invertidos a los lados del dintel
                for n in nb:
                    v.stair(n[0], y0 + 3, n[1], 'smooth_sandstone' if 'sandstone' in p['wall'][0][0] else 'sandstone',
                            NAME[(x - n[0], z - n[1])], 'top')
                sx, sz = x + ox, z + oz
                # celosía de madera (mashrabiya) en algunas, alféizar en todas
                if hsh(x, y0, z, 13) < 0.45:
                    for y in (y0 + 2, y0 + 3):
                        if (sx, y, sz) not in v.b: v.trapdoor(sx, y, sz, p['trap'], NAME[(ox, oz)], 'bottom', 'true') if False else \
                            v.set(sx, y, sz, p['trap'] + '_trapdoor', facing=NAME[(-ox, -oz)], half='bottom', open='true', powered='false', waterlogged='false')
                if (sx, y0 + 1, sz) not in v.b: v.slab(sx, y0 + 1, sz, 'smooth_sandstone', 'top')

    def build_door(self, porch):
        v, p = self.v, self.p
        dx0, dz0 = self.door
        v.door(dx0, 1, dz0, p['door'], 'north')
        v.set(dx0, 3, dz0, p['accent'])
        for sx in (-1, 1):
            v.stair(dx0 + sx, 3, dz0, 'sandstone', NAME[(-sx, 0)], 'top')
        # toldo
        aw = p['awning']
        for x in range(dx0 - 1, dx0 + 2):
            v.set(x, 4, dz0 + 1, aw[(x - dx0) % 2] + '_wool')
        for sx in (-2, 2):
            v.set(dx0 + sx, 1, dz0 + 1, 'decorated_pot', facing='north', cracked='false', waterlogged='false') if sx < 0 else v.set(dx0 + sx, 1, dz0 + 1, 'potted_cactus')
        v.lantern(dx0 + 1, 3, dz0 + 1, hanging=True)
        self.reserved.update({(dx0, 1, dz0 - 1), (dx0, 2, dz0 - 1)})

    def build_roof(self):
        v, p, H = self.v, self.p, self.H
        self.Hm = {(0, 0): 0}
        for f in range(self.floors):
            yr = (f + 1) * H
            C, P = self.cells[f], self.per[f]
            upper = self.cells[f + 1] if f + 1 < self.floors else set()
            for (x, z) in C:
                if (x, z) in upper: continue
                v.set(x, yr, z, p['trim'] if (x, z) in P else p['floor'])
                if (x, z) in P:
                    v.set(x, yr + 1, z, pick(p['wall'], x, yr + 1, z, 6)) if (x + z) % 2 == 0 else v.slab(x, yr + 1, z, 'smooth_sandstone', 'bottom')
            # toldo y macetas en la terraza más alta
            if f == self.floors - 1 and self.terrace:
                xs = [x for x, _ in C]; zs = [z for _, z in C]
                cx, cz = (min(xs) + max(xs)) // 2, (min(zs) + max(zs)) // 2
                aw = p['awning']
                for x in range(cx - 2, cx + 3):
                    for z in range(cz - 1, cz + 2): v.set(x, yr + 4, z, aw[(x + z) % 2] + '_wool')
                for x, z in ((cx - 2, cz - 1), (cx + 2, cz - 1), (cx - 2, cz + 1), (cx + 2, cz + 1)):
                    for y in (yr + 1, yr + 2, yr + 3): v.fence(x, y, z, p['fence'])
                v.set(cx, yr + 1, cz, 'decorated_pot', facing='north', cracked='false', waterlogged='false')
                v.stair(cx - 1, yr + 1, cz, p['wood'], 'east'); v.stair(cx + 1, yr + 1, cz, p['wood'], 'west')
            if self.dome and f == self.floors - 1:
                xs = [x for x, _ in C]; zs = [z for _, z in C]
                cx, cz = (min(xs) + max(xs)) // 2, (min(zs) + max(zs)) // 2
                r = max(2, min(max(xs) - min(xs), max(zs) - min(zs)) // 2 - 1)
                dome(v, cx, yr + 1, cz, r, p['dome'], p['accent'])
        # escalera de mano a la terraza
        C, P = self.cells[-1], self.per[-1]
        inner = sorted(c for c in C if c not in P)
        lx, lz = inner[-1]
        for y in range(self.top - H + 1, self.top + 1):
            v.set(lx, y, lz, 'ladder', facing='north', waterlogged='false')
            self.reserved.add((lx, y, lz)); self.reserved.add((lx, y, lz - 1))

    def build_chimney(self):
        pass

def dome(v, cx, y0, cz, r, mat, top):
    """Cúpula: semiesfera de bloques sobre un tambor de una fila, con remate."""
    import math
    for x in range(cx - r - 1, cx + r + 2):
        for z in range(cz - r - 1, cz + r + 2):
            d = math.hypot(x - cx, z - cz)
            if d <= r + 0.5:
                v.set(x, y0, z, top if d > r - 0.5 else 'air')
    for y in range(1, r + 2):
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                d = math.sqrt((x - cx) ** 2 + (z - cz) ** 2 + (y * 1.0) ** 2)
                if r - 0.6 <= d <= r + 0.4: v.set(x, y0 + y, z, mat)
                elif d < r - 0.6 and (x, y0 + y, z) not in v.b: v.air(x, y0 + y, z)
    v.set(cx, y0 + r + 1, cz, 'gold_block'); v.set(cx, y0 + r + 2, cz, 'lightning_rod', facing='up', powered='false', waterlogged='false')

def dhouse(i, seed):
    p = dpal(i % 5)
    if i % 3 == 0: return Adobe(seed, p, [(-5, -4, 5, 4)], floors=2, rooms=['living', 'bed']).v
    if i % 3 == 1: return Adobe(seed, p, [(-4, -5, 4, 5)], floors=2, rooms=['living', 'bed'], setback=4).v
    return Adobe(seed, p, [(-6, -4, 6, 4)], floors=2, rooms=['living', 'bed'], dome=False, setback=3).v

def dhouse_small(i, seed):
    p = dpal((i + 1) % 5)
    return Adobe(seed, p, [(-4, -3, 4, 4)], floors=2, rooms=['living', 'bed'], setback=3).v

def dworkshop(job, seed, city=False):
    p = dpal((seed + len(job)) % 5)
    if city:
        return Adobe(seed, p, [(-4, -5, 4, 5)], floors=3, rooms=['shop', 'bed', 'bed'], setback=2, job=job, dome=(seed % 3 == 0)).v
    v = Adobe(seed, p, [(-4, -4, 4, 4)], floors=2, rooms=['shop', 'bed'], setback=3, job=job).v
    if job in ('armorer', 'weaponsmith', 'toolsmith'):
        from plains2 import forge
        fp = dict(palette(1)); fp['roof'] = 'smooth_sandstone' if False else 'jungle'; fp['frame'] = 'stripped_jungle_log'
        forge(v, 5, 0, fp)
    return v

def dcity_house(i, seed):
    p = dpal(i % 5)
    return Adobe(seed, p, [(-4, -5, 4, 5)], floors=3 + (i % 2), rooms=['living', 'bed', 'bed', 'bed'], setback=2, dome=(i % 3 == 1)).v

def dtemple(seed, big=False):
    p = dpal(3 if big else 1)
    hw, hd = (7, 8) if big else (5, 6)
    a = Adobe(seed, p, [(-hw, -hd, hw, hd)], floors=1, rooms=['shop'], setback=0, dome=True, terrace=False, H=7, job='cleric')
    v = a.v
    # minarete
    mx, mz = hw + 2, hd - 1
    for y in range(0, 20):
        for x in range(mx - 1, mx + 2):
            for z in range(mz - 1, mz + 2):
                v.set(x, y, z, p['trim'] if y % 6 else p['band']) if (x, z) != (mx, mz) or y == 0 else v.set(x, y, z, 'ladder', facing='north', waterlogged='false') if False else v.air(x, y, z)
    for x in range(mx - 2, mx + 3):
        for z in range(mz - 2, mz + 3):
            v.set(x, 20, z, p['trim'])
            if max(abs(x - mx), abs(z - mz)) == 2 and (x + z) % 2 == 0: v.set(x, 21, z, p['accent'])
    for y in range(21, 24): v.set(mx, y, mz, p['trim'])
    dome(v, mx, 24, mz, 1, p['dome'], p['accent'])
    v.lantern(mx, 21, mz + 1)
    for z in range(-hd + 3, hd - 2, 3):
        for x in (-hw + 2, hw - 2): v.lantern(x, 1, z)
    for z in range(-hd + 3, hd - 1):
        for x in range(-2, 3): v.set(x, 1, z, p['carpet'] + '_carpet') if (v.get(x, 1, z) is None or v.get(x, 1, z).n == 'air') else None
    return v

def dpalace(seed, capital=False):
    """Palacio: patio con fuente y jardín, galerías en arcos, salón con cúpula al fondo; la capital suma minaretes."""
    p = dpal(3 if capital else 0)
    R = 13 if capital else 9
    v = V()
    H = 7
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            v.set(x, 0, z, pick(p['found'], x, 0, z) if max(abs(x), abs(z)) == R else 'smooth_sandstone' if (x + z) % 2 else p['trim'])
            outer = max(abs(x), abs(z)) == R
            gallery = max(abs(x), abs(z)) == R - 4
            for y in range(1, H + 1):
                if outer: v.set(x, y, z, p['band'] if y == H - 1 else pick(p['wall'], x, y, z))
                elif gallery:
                    if y <= 3 and (x + z) % 3 != 0: v.air(x, y, z)
                    elif y == 3: v.stair(x, y, z, 'sandstone', 'south', 'top') if False else v.set(x, y, z, p['trim'])
                    else: v.set(x, y, z, pick(p['wall'], x, y, z, 2))
                elif max(abs(x), abs(z)) > R - 4: v.air(x, y, z)
                else: v.air(x, y, z)
            if max(abs(x), abs(z)) >= R - 4:
                v.set(x, H + 1, z, p['trim'])
                if outer and (x + z) % 2 == 0: v.set(x, H + 2, z, pick(p['wall'], x, H + 2, z, 3))
    # patio: fuente, palmeras de bloques (troncos y hojas), canteros
    for x in range(-2, 3):
        for z in range(-2, 3):
            if max(abs(x), abs(z)) == 2: v.set(x, 1, z, p['trim'])
            else: v.set(x, 0, z, 'water', level='0')
    v.set(0, 0, 0, p['accent']); v.set(0, 1, 0, p['accent']); v.set(0, 2, 0, 'water', level='0') if False else v.lantern(0, 2, 0)
    for (px, pz) in ((-R + 6, -R + 6), (R - 6, -R + 6), (-R + 6, R - 6), (R - 6, R - 6)):
        for y in range(1, 6): v.log(px, y, pz, 'jungle_log', 'y')
        for (a, b) in ((1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (-2, 0), (0, 2), (0, -2)):
            v.set(px + a, 6 - (abs(a) + abs(b)) // 2, pz + b, 'jungle_leaves', persistent='true', distance='1', waterlogged='false')
        v.set(px, 6, pz, 'jungle_leaves', persistent='true', distance='1', waterlogged='false')
        v.set(px, 0, pz, 'grass_block')
    # salón con cúpula al fondo
    dome(v, 0, H + 2, -R + 2, 3, p['dome'], p['accent'])
    for x in range(-1, 2):
        for y in (1, 2, 3): v.air(x, y, R)
    v.set(0, 4, R, p['accent'])
    for x in (-2, 2): v.lantern(x, 1, R + 1)
    v.set(0, 1, -R + 2, 'gold_block'); v.stair(0, 1, -R + 3, p['wood'], 'south')
    for z in range(-R + 4, R): v.set(0, 1, z, p['carpet'] + '_carpet') if abs(z) > 2 else None
    if capital:
        for sx in (-R, R):
            for sz in (-R, R):
                for y in range(1, H + 12):
                    for x in range(sx - 1, sx + 2):
                        for z in range(sz - 1, sz + 2): v.set(x, y, z, p['trim'] if y % 5 else p['band'])
                dome(v, sx, H + 12, sz, 1, p['dome'], p['accent'])
    for y in range(1, H + 1): v.set(-R + 1, y, -R + 1, 'ladder', facing='south', waterlogged='false')
    v.core = (0, 1, R + 1)
    return v

def dsouk(seed):
    """Zoco: dos hileras de puestos bajo toldos de colores con pasillo central."""
    rng = random.Random(seed)
    v = V(); hw, hd = 8, 5
    for x in range(-hw, hw + 1):
        for z in range(-hd, hd + 1): v.set(x, 0, z, 'smooth_sandstone' if (x + z) % 3 else 'cut_sandstone')
    cols = [('orange', 'white'), ('red', 'yellow'), ('cyan', 'white'), ('purple', 'yellow'), ('blue', 'white')]
    for i, x0 in enumerate(range(-hw, hw - 2, 4)):
        c = cols[i % len(cols)]
        for side in (-1, 1):
            zz = side * 3
            for x in range(x0, x0 + 4):
                for z in (zz - 1, zz, zz + 1):
                    v.set(x, 4, z, c[(x + z) % 2] + '_wool')
            for x in (x0, x0 + 3):
                for z in (zz - 1, zz + 1):
                    for y in (1, 2, 3): v.fence(x, y, z, 'jungle')
            v.set(x0 + 1, 1, zz - side, 'barrel', facing='up', open='false')
            v.set(x0 + 2, 1, zz - side, rng.choice(['decorated_pot', 'melon', 'hay_block', 'composter']),
                  **({'facing': 'north', 'cracked': 'false', 'waterlogged': 'false'} if False else {}))
            v.slab(x0 + 1, 1, zz, 'jungle', 'top'); v.slab(x0 + 2, 1, zz, 'jungle', 'top')
    for x in range(-hw, hw + 1, 4): v.lantern(x, 3, 0, hanging=False) if False else None
    for x in range(-hw + 2, hw, 4): v.set(x, 1, 0, 'lantern', hanging='false', waterlogged='false') if False else None
    v.core = (0, 1, hd + 1)
    return v

def dtower(seed, city=False):
    v = V(); r = 3
    Hs = 10 if not city else 15
    p = dpal(0)
    for y in range(0, Hs + 1):
        for x in range(-r, r + 1):
            for z in range(-r, r + 1):
                ring = abs(x) == r or abs(z) == r
                if y == 0: v.set(x, y, z, 'cut_sandstone'); continue
                if ring: v.set(x, y, z, 'cut_sandstone' if (abs(x) == r and abs(z) == r) or y % 5 == 0 else pick(p['wall'], x, y, z))
                else: v.air(x, y, z)
    for y in range(5, Hs, 5):
        for x in range(-r + 1, r):
            for z in range(-r + 1, r): v.set(x, y, z, 'jungle_planks')
    for y in range(1, Hs + 1): v.set(0, y, -r + 1, 'ladder', facing='south', waterlogged='false')
    for y in range(3, Hs, 4):
        for (x, z) in ((0, r), (r, 0), (-r, 0)): v.air(x, y, z); v.air(x, y + 1, z)
    for x in range(-r - 1, r + 2):
        for z in range(-r - 1, r + 2):
            v.set(x, Hs + 1, z, 'cut_sandstone' if max(abs(x), abs(z)) == r + 1 else 'smooth_sandstone')
            if max(abs(x), abs(z)) == r + 1 and (x + z) % 2 == 0: v.set(x, Hs + 2, z, 'cut_sandstone')
    v.set(0, Hs + 1, -r + 1, 'jungle_trapdoor', facing='south', half='bottom', open='false', powered='false', waterlogged='false')
    for (x, z) in ((-r, -r), (r, -r), (-r, r), (r, r)):
        for y in (Hs + 2, Hs + 3, Hs + 4): v.fence(x, y, z, 'jungle')
    for x in range(-r, r + 1):
        for z in range(-r, r + 1): v.set(x, Hs + 5, z, 'orange_wool' if (x + z) % 2 else 'white_wool')
    v.door(0, 1, r, 'jungle', 'north')
    v.lantern(1, 3, r + 1, hanging=False) if False else None
    v.core = (0, 1, r + 1)
    return v

def dwell(seed):
    from plains2 import well
    v = well(seed)
    rep = {'stone_bricks': 'cut_sandstone', 'mossy_stone_bricks': 'sandstone', 'cobblestone': 'sandstone', 'mossy_cobblestone': 'sandstone',
           'spruce_stairs': 'smooth_sandstone_stairs', 'spruce_planks': 'smooth_sandstone', 'spruce_slab': 'smooth_sandstone_slab',
           'spruce_fence': 'jungle_fence', 'spruce_log': 'cut_sandstone'}
    for k, s in list(v.b.items()):
        if s.n in rep:
            pp = dict(s.p)
            if rep[s.n] in ('cut_sandstone', 'sandstone', 'smooth_sandstone'): pp = {}
            v.set(*k, rep[s.n], **pp)
    return v

def dfountain(seed):
    from plains2 import fountain
    return fountain(seed, stone='cut_sandstone', trim='smooth_sandstone', wall='sandstone_wall', slab='smooth_sandstone')

def dfarm(seed):
    from plains2 import farm
    v = farm(seed)
    for k, s in list(v.b.items()):
        if s.n == 'oak_log': v.set(*k, 'cut_sandstone')
        elif s.n == 'oak_fence': v.set(*k, 'jungle_fence', **s.p)
        elif s.n == 'oak_fence_gate': v.set(*k, 'jungle_fence_gate', **s.p)
    return v

def dstall(seed):
    from plains2 import stall
    v = stall(seed)
    for k, s in list(v.b.items()):
        if s.n == 'spruce_planks': v.set(*k, 'smooth_sandstone')
        elif s.n == 'spruce_log': v.set(*k, 'stripped_jungle_log', **s.p)
        elif s.n == 'spruce_slab': v.set(*k, 'jungle_slab', **s.p)
    return v
