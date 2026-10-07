# Blood Moon — NeoForge 1.21.1 (v8)

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
Dragón esquelético de huesos negros con el alma encendida dentro del costillar. ~10 veces el Ender Dragon
(~180 bloques de envergadura; `dragonScale` en la config). 1500 de vida, hitboxes por parte (cabeza x1,5 de daño; cola y alas x0,5).
- **En vuelo**: da vueltas sobre su presa y suelta **cargas de fuego púrpura** que explotan como x3 (60 %), x5 (30 %) o x10 (10 %) un creeper; en fase 2 (50 % de vida) suben las grandes. Respetan mobGriefing y calcinan el suelo.
- **Picada y aterrizaje** sobre las garras de las alas: dos cráteres + onda de choque.
- **En tierra**: **Incineración** (chorro de fuego púrpura desde las fauces, Quemadura astral, suelo calcinado) y **Garra del Ala** (cráter donde golpea).
- Despega y repite. Al amanecer vuelve a la grieta. Botín: siempre una pieza del Set del Vacío (si lo mata un jugador), 50 % Compás, fragmentos de eco, chatarra de netherita.

## Quemadura astral
Llamas púrpuras: 2 de daño por segundo (el doble que el fuego), atraviesa armadura y Resistencia al fuego. El agua la apaga.

## Botín (jefes del Vacío)
~50 %: el arma del jefe o una pieza del **Set del Vacío** (yelmo con cuernos, coraza con hombreras, quijotes, grebas; modelo 3D propio con runas que brillan; mejor que netherite). 35 %: **Compás hacia el fin del mundo**.
- **Espadón del Emisario** y **Mazo del Ejecutor**: armas grandes. El mazo tiene especial: mantené clic derecho 1,5 s y soltá (cráter + explosión, enfriamiento 30 s).
- **Compás**: clic derecho en el Overworld para fijar el **Coliseo del Vacío** más cercano; la aguja apunta a él.

## Coliseo del Vacío
Ruina colosal (≈100 bloques de diámetro, 30 de alto) de piedra negra con llamas astrales eternas y un altar escalonado con un arco de obsidiana en la cima (futura entrada a la dimensión). `/locate structure bloodmoon:void_coliseum`.

## Comandos (OP)
- `/bloodmoon force [blood|super|golden|moonless]`
- `/bloodmoon cancel`
- `/bloodmoon status`
- `/bloodmoon summon rider`
- `/bloodmoon summon emissary`
- `/bloodmoon summon executioner`
- `/bloodmoon summon dragon`
- `/summon bloodmoon:cursed_creeper`

## Configuración
`config/bloodmoon-common.toml` (se crea al primer arranque): frecuencias, multiplicadores de explosión,
probabilidad de Cursed Creeper, tamaño/daño de phantoms, vida del jinete, drop del equipo, potencia del Juicio Final (`executionerJudgmentPower`) y del especial del mazo (`maulSpecialPower`).
