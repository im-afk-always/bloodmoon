# Blood Moon — NeoForge 1.21.1 (v9.1)

## Lunas
| Luna | Frecuencia (default) | Visual |
|---|---|---|
| Luna de Sangre | cada 3 noches | Luna vanilla teñida de rojo, cielo negro sin estrellas |
| Súper Luna de Sangre | cada 13 noches | Rojo más intenso, horizonte sangriento |
| Luna Dorada | cada 7 noches | Luna vanilla dorada, cielo oscuro con brillo dorado (sin efectos de juego) |
| Noche sin Luna | cada 50 noches | La noche cae lento hasta negro total; una ruptura nace en el cenit, se propaga a los horizontes y se abre en una grieta; dentro, un ojo púrpura de pupila felina que frunce la mirada. A medianoche desciende al azar el **Emisario Desconocido**, **El Ejecutor** o el **Dragón de la Primera Alma** |

Prioridad si coinciden: Sin Luna > Súper > Sangre > Dorada.

## Luna de Sangre
Mob cap de hostiles x2, rastreo x2, arañas Velocidad I, zombis Fuerza I, esqueletos 2 flechas.

## Súper Luna de Sangre (incluye todo lo anterior)
- Creepers: Velocidad I, explosión x10.
- **Cursed Creeper** (10% de los creepers naturales): cargado con aura roja, explosión x20, deja fuego, barra de jefe roja.
- Zombis: diamante completo + espada de diamante, Velocidad I + Fuerza I.
- Phantoms gigantes (x3, daño x2) que aparecen sin necesidad de insomnio.
- **Jinete del Apocalipsis** (1 por jugador a medianoche): wither skeleton x2 con netherite y arco Flame + Punch I que dispara 5 flechas en abanico, sobre un caballo esqueleto x2. Barra de jefe.

## Emisario Desconocido (Noche sin Luna, medianoche)
Caballero no-muerto de ~12 bloques, armadura negra y espadón rúnico. 400 de vida, barra de jefe propia.
- **Tajo del Vacío**: espadón desde lo alto en un cono frontal.
- **Siega Abismal**: giro de 360° que barre todo alrededor.
- **Salto Sísmico**: salta hacia su objetivo (o cuando se traba) y al caer lanza todo por el aire.
- **Grieta de Almas**: clava el espadón y abre tres líneas de colmillos.
- **Llamado del Vacío** (fase 2, 50 % de vida): oscuridad + cuatro escoltas wither.
- **Colapso del Abismo** (especial): clava el espadón; anillos de colmillos se expanden, atrae a todo en 26 bloques hacia el centro y estalla en un pilar de luz (daño 20 + Quemadura astral).
Se retira por la grieta al amanecer. Arrasa hojas que lo traban (si mobGriefing está activo).

## El Ejecutor (Noche sin Luna, medianoche)
Caballero colosal con un mazo. 480 de vida.
- **Golpe de Condena**: cráter, el suelo se vuelve Piedra del Vacío y quedan llamas astrales.
- **Barrido Brutal**: arco de 180° con empuje enorme.
- **Salto** con cráter al caer.
- **Juicio Final** (especial): alza el mazo, la cabeza brilla, oscuridad total; 3,5 s después explosión de potencia 60 (x20 creeper). Rompe bloques aunque mobGriefing esté desactivado.

## Dragón de la Primera Alma (Noche sin Luna, medianoche)
Dragón esquelético de huesos negros con el alma encendida dentro del costillar. ~5 veces el Ender Dragon
(~95 bloques de envergadura; `dragonSize` en la config). 1500 de vida, hitboxes por parte (cabeza x1,5 de daño; cola y alas x0,5).
- **En vuelo**: da vueltas sobre su presa y suelta ráfagas de dos **cargas de fuego púrpura** que explotan como x3 (60 %), x5 (30 %) o x10 (10 %) un creeper; en fase 2 (50 % de vida) suben las grandes. Brillan y dejan estela visible desde lejos; un círculo púrpura en el suelo marca dónde van a caer y qué tan grande es la explosión. Al impactar levantan un **hongo de fuego púrpura** (más grande cuanto más potente: ~25, ~40 y ~80 bloques de alto) con destello, resplandor en el cielo, temblor y estruendo que llega con retardo según la distancia. Respetan mobGriefing y calcinan el suelo.
- **Picada y aterrizaje** sobre las garras de las alas: dos cráteres + onda de choque.
- **En tierra**: **Incineración** (chorro de fuego púrpura desde las fauces, Quemadura astral, suelo calcinado) y **Garra del Ala** (cráter donde golpea).
- **Muerte — Supernova**: asciende y una luz nace de su alma (4 s), se vuelve blanca, enceguecedora, y se expande enorme (8 s), se contrae a un punto mientras todo lo demás se oscurece (4 s) y estalla: destello blanco total, onda de choque que arroja todo, sacudida, columna de fuego y un **cráter semiesférico de 100 bloques de profundidad** (`supernovaCraterDepth`; ignora mobGriefing; se excava en ~7 s). El botín cae en el cráter.
- Despega y repite. Al amanecer vuelve a la grieta. Botín: siempre una pieza del Set del Vacío (si lo mata un jugador), 50 % Compás, fragmentos de eco, chatarra de netherita.

## Quemadura astral
Llamas púrpuras: 2 de daño por segundo (el doble que el fuego), atraviesa armadura y Resistencia al fuego. El agua la apaga.

## Botín (jefes del Vacío)
~50 %: el arma del jefe o una pieza del **Set del Vacío** (yelmo con cuernos, coraza con hombreras, quijotes, grebas; modelo 3D propio con runas que brillan; mejor que netherite). 35 %: **Compás hacia el fin del mundo**.
- **Espadón del Emisario** y **Mazo del Ejecutor**: armas grandes. El mazo tiene especial: mantené clic derecho 1,5 s y soltá (cráter + explosión, enfriamiento 30 s).
- **Compás**: clic derecho en el Overworld para fijar el **Coliseo del Vacío** más cercano; la aguja apunta a él.

## Coliseo del Vacío (x5)
Ruina colosal de **~500 bloques de diámetro y ~190 de alto**: arena con hipogeo (laberinto subterráneo donde se hundió el piso),
zigurat de 8 niveles con escalinatas continuas del suelo a la cima y el **marco roto del portal del Vacío** (completalo con Bloques del Vacío), 8 obeliscos, podio con puertas de gladiadores,
gradas en tres sectores con pasillos, galerías abovedadas internas, fachada de 8 pisos con 144 arcos por piso, pilastras, cornisas,
galería perimetral con fuego astral, sectores derrumbados y escombros. Cofres con botín en hipogeo, galerías y ambulacros.
Como supera el límite de 128 bloques de las estructuras de Minecraft, se genera por chunks: uno por región de ~1500 bloques
(no en océanos ni ríos). `/bloodmoon locate coliseum` o el Compás.

## Portal y Laberinto del Vacío
- **Bloque del Vacío**: 9 Piedras del Vacío en la mesa de crafteo (y se deshace en 9).
- Marco rectangular de Bloques del Vacío (interior de 2×3 hasta 21×21), encendido con mechero o carga ígnea → **Portal del Vacío**.
- Lleva al **Laberinto del Vacío** (escala 1:1): muros colosales en ruinas (26-90 de alto) sobre un abismo, laberintos perfectos
  con callejones sin salida, arcos entre celdas, abismos con puentes rotos, plazas con obeliscos, torres huecas con generadores,
  santuarios con marcos de portal, y un coliseo en ruinas en el centro de cada región con un portal de salida.
- Cielo casi negro con horizonte púrpura tenue, nebulosa y estrellas mortecinas; niebla espesa; esqueletos wither, esqueletos y endermans.
- Si no hay portal cerca del destino, se construye uno en una cámara segura.

## Comandos (OP)
- `/bloodmoon force [blood|super|golden|moonless]`
- `/bloodmoon cancel`
- `/bloodmoon status`
- `/bloodmoon summon rider`
- `/bloodmoon summon emissary`
- `/bloodmoon summon executioner`
- `/bloodmoon summon dragon`
- `/bloodmoon locate coliseum`
- `/summon bloodmoon:cursed_creeper`

## Configuración
`config/bloodmoon-common.toml` (se crea al primer arranque): frecuencias, multiplicadores de explosión,
probabilidad de Cursed Creeper, tamaño/daño de phantoms, vida del jinete, drop del equipo, potencia del Juicio Final (`executionerJudgmentPower`) y del especial del mazo (`maulSpecialPower`).
