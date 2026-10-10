# Generador de plantillas de la Humanidad

Genera los `.txt` de `src/main/resources/data/bloodmoon/human/{plains,desert}/`.

## Uso
```
pip install pillow numpy
cd tools/templategen
python3 catalog2.py exp2                      # genera todo en ./exp2/{plains,desert}/*.txt
cp exp2/plains/*.txt ../../src/main/resources/data/bloodmoon/human/plains/
cp exp2/desert/*.txt ../../src/main/resources/data/bloodmoon/human/desert/
python3 -c "import plains2 as P; from preview import sheet; sheet([P.house(1,201)],'out/x.png',scale=10,ground=12)"   # vista isométrica
```
`port.py` genera `fishing_hut` (no está en catalog2).

## Archivos
- `hv.py`: volumen `V` (set/stair/slab/door/...), `export()` al formato `.txt`, render isométrico y `BASECOL` (colores de preview; magenta = bloque sin color).
- `kit.py`: clase `House` (planta por rectángulos, pisos con voladizo, ventanas, puerta, techo, chimenea, escaleras, `furnish`), `palette(kind)`.
- `plains2.py`: casas, talleres, iglesia, torre, granja, pozo, fuente, puesto. `city2.py`: casa urbana, mercado, ayuntamiento, castillo. `desert2.py`: adobe, templo, palacio, zoco, torre.
- `catalog2.py`: nombre de archivo -> función + semilla. Es el mapa que usa el juego.
- `buildings.py`: diseños viejos; solo se usa `WS` (estaciones de trabajo) y las paletas.

## Convenciones
y=0 es el piso, la puerta mira a +z, `core` es el punto frente a la puerta.
Formato: `core x y z`, líneas `P estado`, luego `x y z idx`.
Para cambiar interiores, escaleras o chimeneas: `House.furnish`, `build_stairs`, `build_chimney` en `kit.py`.
