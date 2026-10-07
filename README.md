# Blood Moon — NeoForge 1.21.1 (v2)

## Lunas
| Luna | Frecuencia (default) | Visual |
|---|---|---|
| Luna de Sangre | cada 3 noches | Luna vanilla teñida de rojo, cielo negro sin estrellas |
| Súper Luna de Sangre | cada 13 noches | Rojo más intenso, horizonte sangriento |
| Luna Dorada | cada 7 noches | Luna vanilla dorada, cielo oscuro con brillo dorado (sin efectos de juego) |
| Noche sin Luna | cada 50 noches | Sin luna; grieta negra de horizonte a horizonte con un ojo púrpura colosal de pupila felina que se mueve de lado a lado (sin efectos de juego) |

Prioridad si coinciden: Sin Luna > Súper > Sangre > Dorada.

## Luna de Sangre
Mob cap de hostiles x2, rastreo x2, arañas Velocidad I, zombis Fuerza I, esqueletos 2 flechas.

## Súper Luna de Sangre (incluye todo lo anterior)
- Creepers: Velocidad I, explosión x10.
- **Cursed Creeper** (10% de los creepers naturales): cargado con aura roja, explosión x20, deja fuego, barra de jefe roja.
- Zombis: diamante completo + espada de diamante, Velocidad I + Fuerza I.
- Phantoms gigantes (x3, daño x2) que aparecen sin necesidad de insomnio.
- **Jinete del Apocalipsis** (1 por jugador a medianoche): wither skeleton x2 con netherite y arco Flame + Punch I que dispara 5 flechas en abanico, sobre un caballo esqueleto x2. Barra de jefe.

## Comandos (OP)
- `/bloodmoon force [blood|super|golden|moonless]`
- `/bloodmoon cancel`
- `/bloodmoon status`
- `/bloodmoon summon rider`
- `/summon bloodmoon:cursed_creeper`

## Configuración
`config/bloodmoon-common.toml` (se crea al primer arranque): frecuencias, multiplicadores de explosión,
probabilidad de Cursed Creeper, tamaño/daño de phantoms, vida del jinete, drop del equipo.
