# Blood Moon — NeoForge 1.21.1 (v5)

## Lunas
| Luna | Frecuencia (default) | Visual |
|---|---|---|
| Luna de Sangre | cada 3 noches | Luna vanilla teñida de rojo, cielo negro sin estrellas |
| Súper Luna de Sangre | cada 13 noches | Rojo más intenso, horizonte sangriento |
| Luna Dorada | cada 7 noches | Luna vanilla dorada, cielo oscuro con brillo dorado (sin efectos de juego) |
| Noche sin Luna | cada 50 noches | La noche cae lento hasta negro total; una ruptura nace en el cenit, se propaga a los horizontes y se abre en una grieta; dentro, un ojo púrpura de pupila felina que frunce la mirada. A medianoche desciende el **Emisario Desconocido** |

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
Se retira por la grieta al amanecer. Arrasa hojas que lo traban (si mobGriefing está activo).

## Comandos (OP)
- `/bloodmoon force [blood|super|golden|moonless]`
- `/bloodmoon cancel`
- `/bloodmoon status`
- `/bloodmoon summon rider`
- `/bloodmoon summon emissary`
- `/summon bloodmoon:cursed_creeper`

## Configuración
`config/bloodmoon-common.toml` (se crea al primer arranque): frecuencias, multiplicadores de explosión,
probabilidad de Cursed Creeper, tamaño/daño de phantoms, vida del jinete, drop del equipo.
