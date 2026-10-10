"""Validador de plantillas humanas: se puede recorrer cada edificio caminando como un jugador (o un humano de 2 de alto).

Lee los .txt exportados (o un V en memoria) y reporta:
- pisos (niveles con piso interior) a los que no se llega desde la puerta,
- estrecheces: celdas caminables con solo 1 bloque libre encima que están entre dos zonas alcanzables,
- escaleras de mano sin bloque de apoyo detrás (se caen al actualizar),
- chimeneas con fogata a la vista en la punta.
Uso: python validate.py [carpeta]   (por defecto ../../src/main/resources/data/bloodmoon/human)"""
import os, sys
from collections import deque

FACE = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
D4 = list(FACE.values())

# bloques que se atraviesan (no frenan el paso ni ocupan la cabeza)
PASS_SUFFIX = ('_carpet', '_pressure_plate', '_button', '_sign', '_banner', '_torch', '_sapling', '_tulip')
PASS = {'air', 'cave_air', 'water', 'torch', 'wall_torch', 'short_grass', 'tall_grass', 'dandelion', 'poppy', 'cornflower',
        'azure_bluet', 'oxeye_daisy', 'wheat', 'carrots', 'potatoes', 'beetroots', 'snow', 'moss_carpet', 'rail', 'redstone_wire',
        'dead_bush', 'fern', 'vine', 'sweet_berry_bush', 'light', 'tripwire'}


class B:
    __slots__ = ('n', 'p')

    def __init__(self, key):
        if '[' in key:
            name, props = key[:-1].split('[', 1)
            self.p = dict(kv.split('=') for kv in props.split(','))
        else:
            name, self.p = key, {}
        self.n = name.split(':')[-1]


def load(path):
    core, pal, blocks = None, [], {}
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line: continue
            if line.startswith('core '):
                core = tuple(int(t) for t in line.split()[1:4])
            elif line.startswith('P '):
                pal.append(B(line[2:]))
            else:
                x, y, z, i = (int(t) for t in line.split())
                blocks[(x, y, z)] = pal[i]
    return core, blocks


def from_v(v):
    """Convierte un V de hv.py (en memoria) al formato del validador."""
    out = {}
    for k, s in v.b.items():
        b = B.__new__(B); b.n = s.n; b.p = dict(s.p); out[k] = b
    return v.core, out


def passable(b):
    if b is None: return True
    n = b.n
    if n in PASS or n.endswith(PASS_SUFFIX): return True
    if n.endswith('_door'): return True
    if n.endswith('_trapdoor'): return b.p.get('open') == 'true'
    if n.endswith('_fence_gate'): return True
    if n == 'ladder' or n == 'scaffolding': return True
    if n.endswith('_slab') and b.p.get('type') == 'bottom': return False
    return False


def height(b):
    """Altura de la superficie superior de un bloque (0 = no se pisa, 0.5 = losa/escalón, 1 = bloque)."""
    if b is None: return 0
    n = b.n
    if n in PASS or n.endswith(PASS_SUFFIX) or n.endswith('_door') or n == 'ladder': return 0
    if n.endswith('_trapdoor'):
        return 0 if b.p.get('open') == 'true' else (1 if b.p.get('half') == 'top' else 0.2)
    if n.endswith('_slab'): return 1 if b.p.get('type') in ('top', 'double') else 0.5
    if n.endswith('_stairs'): return 1 if b.p.get('half') == 'top' else 0.5   # el escalón se sube como media altura
    if n.endswith('_carpet'): return 0
    if n.endswith(('_fence', '_wall', '_fence_gate')): return 1.5
    if n.endswith('_bed'): return 0.56
    if n in ('farmland', 'dirt_path'): return 0.94
    return 1


class Grid:
    def __init__(self, blocks, ground=0):
        self.b = blocks
        self.ground = ground
        xs = [k[0] for k in blocks]; zs = [k[2] for k in blocks]
        self.x0, self.x1, self.z0, self.z1 = min(xs), max(xs), min(zs), max(zs)
        self.y1 = max(k[1] for k in blocks)

    def get(self, x, y, z):
        b = self.b.get((x, y, z))
        if b is None and y <= self.ground:
            return B('minecraft:grass_block')   # terreno (el patio se nivela a la altura del piso)
        return b

    def inside(self, x, z):
        return self.x0 <= x <= self.x1 and self.z0 <= z <= self.z1

    def clear(self, x, z, y0, y1):
        """Bloques y0..y1 (inclusive) de la columna, todos atravesables."""
        return all(passable(self.get(x, yy, z)) for yy in range(y0, y1 + 1))

    def stand(self, x, y, z):
        """Altura de los pies si alguien de 1.8 de alto puede estar parado en la celda (x,y,z); None si no."""
        here = self.get(x, y, z)
        hh = height(here)
        if hh >= 1: return None
        if hh > 0:                                    # parado sobre una losa o escalón de esta celda
            feet = y + hh
        else:
            if not passable(here): return None
            hb = height(self.get(x, y - 1, z))
            ladder = here is not None and here.n == 'ladder'
            if hb < 0.9 and not ladder: return None
            feet = y + max(0.0, hb - 1)
        top = int(feet + 1.8 - 1e-6)                  # último bloque que ocupa la cabeza
        if not self.clear(x, z, y + 1, top): return None
        return feet


def walk(g, start):
    """BFS de celdas (x,y,z) caminables. Paso: misma altura, subir hasta 0.5 (o 1 por escalera de mano), bajar hasta 3."""
    seen = {}
    q = deque()
    for dy in range(0, 3):
        f = g.stand(start[0], start[1] + dy, start[2])
        if f is not None:
            seen[(start[0], start[1] + dy, start[2])] = f; q.append((start[0], start[1] + dy, start[2])); break
    while q:
        x, y, z = q.popleft()
        f = seen[(x, y, z)]
        here = g.get(x, y, z)
        ladder = here is not None and here.n == 'ladder'
        nbrs = [(x + dx, z + dz) for dx, dz in D4]
        for nx, nz in nbrs:
            if not (g.x0 - 3 <= nx <= g.x1 + 3 and g.z0 - 3 <= nz <= g.z1 + 3): continue
            for ny in range(y - 3, y + 2):
                if (nx, ny, nz) in seen: continue
                nf = g.stand(nx, ny, nz)
                if nf is None: continue
                if nf - f > 1.0 + 1e-6: continue
                # al subir (o saltar) hace falta lugar para la cabeza también sobre la celda de partida
                if nf > f and not g.clear(x, z, y + 1, int(nf + 1.8 - 1e-6)): continue
                # al bajar, la cabeza pasa por encima de la celda de llegada a la altura de partida
                if nf < f and not g.clear(nx, nz, ny + 1, int(f + 1.8 - 1e-6)): continue
                if f - nf > 3: continue
                seen[(nx, ny, nz)] = nf; q.append((nx, ny, nz))
                break
        if ladder or (g.get(x, y + 1, z) is not None and g.get(x, y + 1, z).n == 'ladder'):
            for ny in (y + 1, y - 1):
                if (x, ny, z) in seen: continue
                nb = g.get(x, ny, z)
                if nb is not None and nb.n == 'ladder' or g.stand(x, ny, z) is not None:
                    seen[(x, ny, z)] = ny; q.append((x, ny, z))
        # bajar por la escalera de mano
        below = g.get(x, y - 1, z)
        if below is not None and below.n == 'ladder' and (x, y - 1, z) not in seen:
            seen[(x, y - 1, z)] = y - 1; q.append((x, y - 1, z))
    return seen


FURNITURE = {'chest', 'barrel', 'crafting_table', 'bookshelf', 'chiseled_bookshelf', 'furnace', 'smoker', 'blast_furnace', 'composter',
             'water_cauldron', 'cauldron', 'loom', 'fletching_table', 'cartography_table', 'smithing_table', 'stonecutter', 'lectern',
             'hay_block', 'melon', 'pumpkin', 'decorated_pot', 'grindstone', 'anvil', 'bell', 'brewing_stand', 'campfire', 'gold_block',
             'jukebox', 'note_block', 'beehive', 'carved_pumpkin', 'target'}


def floors(g):
    """Niveles con piso interior: y tales que hay al menos 6 celdas interiores con bloque pisable debajo y aire encima
    (sin contar la parte de arriba de los muebles)."""
    out = {}
    for (x, y, z), b in g.b.items():
        if b.n not in ('air', 'cave_air'): continue
        if y <= g.ground: continue
        if g.stand(x, y, z) is None: continue
        below = g.b.get((x, y - 1, z))
        if below is not None and (below.n in FURNITURE or below.n.endswith(('_bed', '_wool', '_slab', '_stairs', '_fence', '_wall', 'pane'))):
            continue
        # techo encima (interior): algún bloque no pasable más arriba en la columna
        roofed = any(not passable(g.b.get((x, yy, z))) for yy in range(y + 2, g.y1 + 1) if (x, yy, z) in g.b)
        if not roofed: continue
        out.setdefault(y, set()).add((x, z))
    return {y: c for y, c in out.items() if len(c) >= 6}


def loose_ladders(g):
    bad = []
    for (x, y, z), b in g.b.items():
        if b.n != 'ladder': continue
        dx, dz = FACE[b.p.get('facing', 'north')]
        sup = g.get(x - dx, y, z - dz)
        if sup is None or height(sup) < 1 or sup.n.endswith(('_stairs', '_slab', '_trapdoor', '_door', '_pane', '_fence')):
            bad.append((x, y, z))
    return bad


def chimney_tops(g):
    """Fogatas visibles: con aire o nada directamente encima y sin bloque en las 4 caras laterales del nivel superior."""
    bad = []
    for (x, y, z), b in g.b.items():
        if b.n != 'campfire': continue
        if y <= 2: continue          # hogar
        above = g.b.get((x, y + 1, z))
        if (above is None or above.n == 'air') and y >= g.y1 - 1:
            bad.append((x, y, z))
    return bad


def check(core, blocks, name=''):
    g = Grid(blocks)
    errs, warns = [], []
    seen = walk(g, core)
    fl = floors(g)
    reached_y = {}
    for (x, y, z) in seen: reached_y.setdefault(y, set()).add((x, z))
    for y, cells in sorted(fl.items()):
        got = len(cells & reached_y.get(y, set()))
        if got == 0:
            errs.append(f'piso y={y} ({len(cells)} celdas) inalcanzable')
        elif got < len(cells) * 0.5:
            warns.append(f'piso y={y}: solo {got}/{len(cells)} celdas alcanzables')
    lad = loose_ladders(g)
    if lad: errs.append(f'{len(lad)} escaleras de mano sin apoyo, p.ej. {lad[:3]}')
    ch = chimney_tops(g)
    if ch: errs.append(f'fogata a la vista en la punta {ch[:2]}')
    # estrecheces: celda interior con piso, 1 de alto, vecina de dos celdas alcanzadas a la misma altura
    tight = []
    for (x, y, z), b in g.b.items():
        if b.n != 'air' or (x, y, z) in seen: continue
        if height(g.get(x, y - 1, z)) < 1: continue
        if passable(g.get(x, y + 1, z)): continue
        nb = [(x + dx, y, z + dz) in seen for dx, dz in D4]
        if (nb[0] and nb[1]) or (nb[2] and nb[3]):
            tight.append((x, y, z))
    if tight: errs.append(f'{len(tight)} pasos de 1 de alto, p.ej. {tight[:4]}')
    return errs, warns


def main(root):
    total = 0
    for cult in ('plains', 'desert'):
        d = os.path.join(root, cult)
        for fn in sorted(os.listdir(d)):
            if not fn.endswith('.txt'): continue
            core, blocks = load(os.path.join(d, fn))
            errs, warns = check(core, blocks)
            if errs or warns:
                print(f'{cult}/{fn[:-4]}:')
                for e in errs: print('   ERROR', e)
                for w in warns: print('   aviso', w)
            total += len(errs)
    print(f'{total} errores')
    return total


if __name__ == '__main__':
    root = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources',
                                                                    'data', 'bloodmoon', 'human')
    sys.exit(1 if main(root) else 0)
