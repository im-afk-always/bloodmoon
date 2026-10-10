# Generador de plantillas de la Humanidad

Genera los `.txt` de `src/main/resources/data/bloodmoon/human/{plains,desert}/`.

## Uso
```
pip install pillow numpy
cd tools/templategen
python3 catalog2.py exp2                      # genera todo en ./exp2/{plains,desert}/*.txt y lo valida (sale con error si algo falla)
python3 catalog2.py exp2 librarian            # solo los que contienen "librarian"
cp exp2/plains/*.txt ../../src/main/resources/data/bloodmoon/human/plains/
cp exp2/desert/*.txt ../../src/main/resources/data/bloodmoon/human/desert/
python3 validate.py                           # valida las plantillas que ya están en el mod
python3 dbg.py exp2/plains/house_large_0.txt 1 5   # planta en y=1 y y=5, con lo alcanzable marcado
python3 -c "import plains2 as P; from preview import sheet; sheet([P.house(1,201)],'out/x.png',scale=10,ground=12)"   # vista isométrica
```
`port.py` genera `fishing_hut` (no está en catalog2).

## Validador (`validate.py`)
Recorre cada plantilla como lo haría un jugador o un humano (2 de alto) desde `core`:
- **pisos inalcanzables** (ERROR) y pisos alcanzables solo en parte (aviso),
- **pasos de 1 de alto** entre dos zonas alcanzables (ERROR),
- **escaleras de mano sin apoyo** detrás (ERROR: se caen al actualizarse),
- **fogata a la vista** en la punta de una chimenea (ERROR).

## Archivos
- `hv.py`: volumen `V` (set/stair/slab/door/...), `export()` al formato `.txt`, render isométrico y `BASECOL` (colores de preview; magenta = bloque sin color).
- `kit.py`: clase `House` (planta por rectángulos, pisos con voladizo, ventanas, puerta, techo, chimenea, escaleras, `furnish`, `unblock`), `palette(kind)`, `flue` (chimenea de conducto hueco), `library_hall` (interior de biblioteca con galería).
- `plains2.py`: casas, talleres (el del clérigo es la botica), biblioteca, torre, granja, pozo, fuente, puesto. `city2.py`: casa urbana, mercado, ayuntamiento, castillo. `desert2.py`: adobe, biblioteca con cúpula, palacio, zoco, torre.
- `catalog2.py`: nombre de archivo -> función + semilla. Es el mapa que usa el juego.
- `buildings.py`: diseños viejos; solo se usa `WS` (estaciones de trabajo) y las paletas.

## Convenciones
y=0 es el piso, la puerta mira a +z, `core` es el punto frente a la puerta.
Formato: `core x y z`, líneas `P estado`, luego `x y z idx`.
Escaleras (`build_stairs`): un tramo recto por piso con 3 de alto libre sobre cada escalón, celda libre al pie y a la llegada,
pasillo reservado desde la puerta, baranda que nunca encierra parte del piso; se elige el lugar que no parte ningún piso.
Chimeneas (`flue`): conducto hueco desde el hogar; la fogata de arriba va dentro del conducto, 2 bajo el remate.
