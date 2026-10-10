"""Depuración: muestra una planta (capa y) de una plantilla con lo alcanzable marcado. Uso: python dbg.py exp2/plains/x.txt y [y2...]"""
import sys
import validate as V

def ab(n):
    if n is None: return ' . '
    m = n.n
    if m == 'air': return '   '
    if m.endswith('_stairs'): return 'S' + n.p['facing'][0] + ('t' if n.p['half'] == 'top' else 'b')
    if m.endswith('_slab'): return 'sl' + n.p['type'][0]
    if m.endswith('_planks'): return '###'
    if m == 'ladder': return 'LAD'
    if m.endswith('_fence'): return 'fnc'
    if m.endswith('_door'): return 'DOR'
    if m.endswith('_bed'): return 'BED'
    if m.endswith('_carpet'): return 'crp'
    return m[:3]

core, b = V.load(sys.argv[1])
g = V.Grid(b); s = V.walk(g, core)
xs = [k[0] for k in b]; zs = [k[2] for k in b]
for y in map(int, sys.argv[2:]):
    print('y =', y, '(* = alcanzable)')
    print('     ' + ''.join(f'{x:>4}' for x in range(min(xs), max(xs) + 1)))
    for z in range(min(zs), max(zs) + 1):
        print(f'{z:>4} ' + ''.join(('*' if (x, y, z) in s else ' ') + ab(b.get((x, y, z))) for x in range(min(xs), max(xs) + 1)))
