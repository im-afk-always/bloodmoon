import os, sys, shutil
from hv import export
import plains2 as P, city2 as C, desert2 as D
JOBS = ['armorer','butcher','cartographer','cleric','farmer','fisherman','fletcher','leatherworker','librarian','mason','shepherd','toolsmith','weaponsmith']
def catalog():
    o = {}
    for i in range(4):
        o[f'plains/house_small_{i}'] = P.house_small(i, 100+i)
        o[f'plains/house_large_{i}'] = P.house(i, 200+i)
    for j, job in enumerate(JOBS):
        o[f'plains/work_{job}'] = P.church(300+j) if job=='cleric' else P.workshop(job, 300+j)
        o[f'plains/city_work_{job}'] = P.church(950+j, big=True) if job=='cleric' else P.workshop(job, 950+j, city=True, color=j)
    o['plains/farm_0'] = P.farm(1); o['plains/farm_1'] = P.farm(2, 8, 6)
    o['plains/well'] = P.well(1); o['plains/stall_0'] = P.stall(1); o['plains/stall_1'] = P.stall(5)
    o['plains/tower'] = P.watchtower(1)
    for i in range(5): o[f'plains/city_house_{i}'] = C.townhouse(i, 900+i)
    o['plains/fountain'] = P.fountain(1); o['plains/market'] = C.market_hall(1); o['plains/hall'] = C.town_hall(1)
    o['plains/castle'] = C.castle(1); o['plains/city_tower'] = P.watchtower(2, city=True)
    for i in range(3):
        o[f'desert/house_small_{i}'] = D.dhouse_small(i, 400+i)
        o[f'desert/house_large_{i}'] = D.dhouse(i, 500+i)
    for j, job in enumerate(JOBS):
        o[f'desert/work_{job}'] = D.dtemple(600+j) if job=='cleric' else D.dworkshop(job, 600+j)
        o[f'desert/city_work_{job}'] = D.dtemple(1950+j, big=True) if job=='cleric' else D.dworkshop(job, 1950+j, city=True)
    o['desert/farm_0'] = D.dfarm(1); o['desert/farm_1'] = D.dfarm(2)
    o['desert/well'] = D.dwell(1); o['desert/stall_0'] = D.dstall(2); o['desert/stall_1'] = D.dstall(4)
    o['desert/tower'] = D.dtower(1)
    for i in range(4): o[f'desert/city_house_{i}'] = D.dcity_house(i, 1900+i)
    o['desert/fountain'] = D.dfountain(1); o['desert/market'] = D.dsouk(1); o['desert/hall'] = D.dpalace(3, False)
    o['desert/castle'] = D.dpalace(7, True); o['desert/city_tower'] = D.dtower(2, city=True)
    return o
if __name__ == '__main__':
    out = sys.argv[1]
    for name, v in catalog().items():
        p = os.path.join(out, name + '.txt'); os.makedirs(os.path.dirname(p), exist_ok=True)
        n, pal = export(v, p)
        xs=[k[0] for k in v.b]; zs=[k[2] for k in v.b]; ys=[k[1] for k in v.b]
        print(f'{name:32s} {n:6d} x{min(xs)}..{max(xs)} z{min(zs)}..{max(zs)} y{min(ys)}..{max(ys)} core{v.core}')
