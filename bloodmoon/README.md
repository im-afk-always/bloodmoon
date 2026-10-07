# Blood Moon — NeoForge 1.21.1

Cada 3 días (noches de los días 2, 5, 8…) la noche del Overworld es Luna de Sangre.

## Qué hace
| Efecto | Implementación |
|---|---|
| Luna roja brillante | Textura propia `blood_moon_phases.png` (siempre llena) vía `LevelRendererMixin` |
| Cielo negro, sin estrellas | `ClientLevelMixin` (sky color + star brightness) + color de niebla. Fundido de ~2,5 s |
| Spawn de hostiles x2 | Mob cap de `MONSTER` x2 solo durante el tick del Overworld (`MobCategoryMixin`) |
| Rastreo x2 | Modificador permanente `FOLLOW_RANGE` +100% |
| Arañas Velocidad I / Zombis Fuerza I | Efecto infinito |
| Esqueletos 2 flechas | `AbstractSkeletonMixin` repite el disparo vanilla |
| Persistencia | Los buffs viven en el NBT del mob: siguen hasta que muera o despawnee |

Ventana de noche: ticks 13000–23000. Al amanecer termina y los nuevos spawns vuelven a la normalidad.

## Comandos (OP nivel 2)
- `/bloodmoon force` — fuerza la Luna de Sangre en la noche entrante
- `/bloodmoon cancel` — cancela el forzado
- `/bloodmoon status` — estado y días hasta la próxima

## Compilar
Requiere JDK 21.
```
./gradlew build          # jar en build/libs/
./gradlew runClient      # probar
```
Prueba rápida: `/bloodmoon force` y luego `/time set 13000`.

## Ajustes
Constantes en `BloodMoonManager`: `CYCLE_DAYS`, `NIGHT_START`, `NIGHT_END`.
