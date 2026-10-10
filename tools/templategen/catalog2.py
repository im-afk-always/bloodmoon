import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hv import export
import plains2 as P, city2 as C, desert2 as D
import validate
JOBS = ['armorer','butcher','cartographer','cleric','farmer','fisherman','fletcher','leatherworker','librarian','mason','shepherd','toolsmith','weaponsmith']
def catalog():
    o = {}
    for i in range(4):
        o[f'plains/house_small_{i}'] = lambda i=i: P.house_small(i, 100+i)
        o[f'plains/house_large_{i}'] = lambda i=i: P.house(i, 200+i)
    for j, job in enumerate(JOBS):
        # el clérigo trabaja en una botica (taller chico); el bibliotecario, en la biblioteca amplia
        o[f'plains/work_{job}'] = (lambda j=j: P.library(300+j)) if job=='librarian' else (lambda j=j, job=job: P.workshop(job, 300+j))
        o[f'plains/city_work_{job}'] = (lambda j=j: P.library(950+j, big=True)) if job=='librarian' else \
            (lambda j=j, job=job: P.workshop(job, 950+j, city=True, color=j))
    o['plains/farm_0'] = lambda: P.farm(1); o['plains/farm_1'] = lambda: P.farm(2, 8, 6)
    o['plains/well'] = lambda: P.well(1); o['plains/stall_0'] = lambda: P.stall(1); o['plains/stall_1'] = lambda: P.stall(5)
    o['plains/tower'] = lambda: P.watchtower(1)
    for i in range(5): o[f'plains/city_house_{i}'] = lambda i=i: C.townhouse(i, 900+i)
    o['plains/fountain'] = lambda: P.fountain(1); o['plains/market'] = lambda: C.market_hall(1); o['plains/hall'] = lambda: C.town_hall(1)
    o['plains/castle'] = lambda: C.castle(1); o['plains/city_tower'] = lambda: P.watchtower(2, city=True)
    for i in range(3):
        o[f'desert/house_small_{i}'] = lambda i=i: D.dhouse_small(i, 400+i)
        o[f'desert/house_large_{i}'] = lambda i=i: D.dhouse(i, 500+i)
    for j, job in enumerate(JOBS):
        o[f'desert/work_{job}'] = (lambda j=j: D.dlibrary(600+j)) if job=='librarian' else (lambda j=j, job=job: D.dworkshop(job, 600+j))
        o[f'desert/city_work_{job}'] = (lambda j=j: D.dlibrary(1950+j, big=True)) if job=='librarian' else \
            (lambda j=j, job=job: D.dworkshop(job, 1950+j, city=True))
    o['desert/farm_0'] = lambda: D.dfarm(1); o['desert/farm_1'] = lambda: D.dfarm(2)
    o['desert/well'] = lambda: D.dwell(1); o['desert/stall_0'] = lambda: D.dstall(2); o['desert/stall_1'] = lambda: D.dstall(4)
    o['desert/tower'] = lambda: D.dtower(1)
    for i in range(4): o[f'desert/city_house_{i}'] = lambda i=i: D.dcity_house(i, 1900+i)
    o['desert/fountain'] = lambda: D.dfountain(1); o['desert/market'] = lambda: D.dsouk(1); o['desert/hall'] = lambda: D.dpalace(3, False)
    o['desert/castle'] = lambda: D.dpalace(7, True); o['desert/city_tower'] = lambda: D.dtower(2, city=True)
    return o
if __name__ == '__main__':
    # uso: python catalog2.py <salida> [filtro]   -> genera los .txt y los valida (sale con error si alguno falla)
    out = sys.argv[1]
    only = sys.argv[2] if len(sys.argv) > 2 else None
    errors = 0
    for name, make in catalog().items():
        if only and only not in name: continue
        try:
            v = make()
        except Exception as e:
            print(f'{name:32s} FALLÓ: {e!r}'); errors += 1; continue
        p = os.path.join(out, name + '.txt'); os.makedirs(os.path.dirname(p), exist_ok=True)
        n, pal = export(v, p)
        core, blocks = validate.load(p)
        errs, warns = validate.check(core, blocks, name)
        xs=[k[0] for k in v.b]; zs=[k[2] for k in v.b]; ys=[k[1] for k in v.b]
        print(f'{name:32s} {n:6d} x{min(xs)}..{max(xs)} z{min(zs)}..{max(zs)} y{min(ys)}..{max(ys)} core{v.core}')
        for e in errs: print('      ERROR', e)
        for w in warns: print('      aviso', w)
        errors += len(errs)
    print(f'{errors} errores')
    sys.exit(1 if errors else 0)
