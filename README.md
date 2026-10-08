# Blood Moon — NeoForge 1.21.1 (v11)

## Al entrar por primera vez
La pantalla se oscurece y un ojo púrpura se abre frente al jugador. Encima, un texto en el alfabeto de la mesa de
encantamientos cambia de símbolo hasta fijarse letra por letra: **«Nos encontraremos pronto»**. El ojo se cierra y desaparece.
Una vez por jugador y por mundo; `/bloodmoon intro` lo repite.

## Lunas
| Luna | Frecuencia (default) | Visual |
|---|---|---|
| Luna de Sangre | cada 3 noches | Luna vanilla teñida de rojo, cielo negro sin estrellas |
| Luna de la Cosecha | cada 13 noches | Cielo carmesí, luna realista con halo, todo teñido de rojo |
| Luna Dorada | cada 7 noches | Luna vanilla dorada, cielo oscuro con brillo dorado (sin efectos de juego) |
| Noche sin Luna | cada 50 noches | La noche cae lento hasta negro total; una ruptura nace en el cenit, se propaga a los horizontes y se abre en una grieta; dentro, un ojo púrpura de pupila felina que frunce la mirada. Toda la noche surgen hordas de **Centinelas** (espada) y **Arqueros del Vacío** (arco): esqueletos de hueso negro con armadura del Vacío encantada; se desvanecen al amanecer. A medianoche cae del cielo como un bólido negro el **Emisario Desconocido** o **El Ejecutor**, dejando un cráter |

Prioridad si coinciden: Sin Luna > Súper > Sangre > Dorada.

## Luna de Sangre
Mob cap de hostiles x2, rastreo x2, arañas Velocidad I, zombis Fuerza I, esqueletos 2 flechas.

## Luna de la Cosecha (incluye todo lo de la Luna de Sangre)
**Ambiente:** el mundo entero queda bañado en luz carmesí (la luz del cielo se tiñe, la luna ilumina a cielo abierto y
las antorchas dan una luz naranja de fuego), más oscuro y con más contraste; cielo y horizonte rojo sangre, bruma más cercana,
una luna realista con un halo tenue y nubes suaves que se encienden cerca de ella (reemplazan a las cúbicas).
Encima, un posprocesado propio (tono carmesí, contraste, resplandor en dos capas alrededor de la luna, antorchas, fuego y lava, que conservan su color; viñeta) que entra y sale
solo con la luna; la mano y la interfaz no se tiñen. Se puede apagar en `config/bloodmoon-client.toml`
(`harvestMoonPostEffect`) y se desactiva solo si está Iris. Con paquetes de shaders el tinte de la luz puede perderse en parte.

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

## Dragón de la Primera Alma (solo comando o huevo)
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

## El Observador — jefe pináculo (Santuario del Ojo, Laberinto del Vacío)
El Ojo de la Grieta. Un globo de 18 bloques con pupila felina, párpados de carne, tentáculos en la nuca, anillos de runas
y Bloques del Vacío que orbitan a su alrededor. Despierta cuando pisás la plataforma de su Santuario
(una de cada ~1700×1700 bloques del Laberinto; el Compás apunta al más cercano dentro del Laberinto).

**Santuario:** plataforma circular flotante sobre un abismo sin fondo, 8 pilares (3 caídos) como única cobertura,
4 puentes con tramos rotos, muralla colosal con galerías y 10 costillas-tentáculo que se cierran sobre el Ojo.

**Locura:** mirarlo llena un medidor (viñeta, ojos en los bordes, susurros, cámara que se inclina). A 100 la mente
se quiebra: daño mágico, ceguera y oscuridad. Desviar la mirada la baja.

**Expuesto:** tras cada Mirada o Barrido baja exhausto al estrado con la pupila dilatada: recibe daño completo
(x1,25) y no da locura. El resto del tiempo desvía el 85% del daño.

| Fase | Habilidades |
|---|---|
| 1 (100-66%) | Mirada (rayo que te sigue; los pilares lo bloquean), Tentáculos del Abismo (brotan bajo tus pies tras un aviso y azotan), Llamado (esqueletos del Vacío) |
| 2 (66-33%) | + Ojos Vigías (persiguen, se revientan de un golpe, siembran locura), Singularidad (te atrae y suelta una onda a ras del piso: saltala) |
| 3 (33-0%) | + Barrido (el rayo gira 360° a ras del piso: cubrite tras un pilar o metete bajo el Ojo); el cielo se llena de ojos |

Entre fases grita (invulnerable, empuja y oscurece). Al morir se agrieta, la luz escapa, se encoge hasta un punto
e implosiona; la pantalla queda en negro con su despedida. Suelta el Iris del Observador, Bloques del Vacío, ecos y
chatarra de netherite. El Santuario duerme 3 días (`eyeRespawnDays`). Si todos se van, el Ojo vuelve a dormir.

**Fase final — El Más Allá:** al vencerlo en el Santuario no muere: todo cae hacia el Ojo, la pupila se abre como una
grieta y arrastra a todos al Más Allá (llanura negra sobre el vacío, cielo púrpura con ojos que vagan, pocos obeliscos).
Ahí renace como **El Observador Desatado**: el Ojo liberado, de 32 bloques, flotando sobre la llanura con siete anillos
de runas, dos cinturones de bloques (Vacío y obsidiana llorosa), un halo de monolitos, un aura y una cortina de tentáculos.
- **Mirada titánica:** rayo tres veces más ancho que funde el piso a su paso; en la segunda mitad, Barrido de 360°.
- **Puño del Vacío:** de una grieta junto al Ojo brota un brazo de energía oscura que se suspende sobre vos (sello en el piso) y cae como un puño: onda y cráter. Dos en la segunda mitad.
- Tentáculos del Abismo y Ojos Vigías. Definitivos cada ~15 s (~11 s bajo el 50%), sin repetirse:
- **Ojo Colosal:** un ojo de 48 bloques en el cielo; un círculo te persigue, se fija y cae un rayo de 20 de radio que perfora la llanura hasta el vacío. Desde la mitad de su vida, uno de cada dos definitivos es el Ojo Colosal.
- **Juicio Final (una sola vez, al 20%):** cinco pares de anillos mágicos concéntricos se materializan a lo largo de su mirada y
  se cierran sobre ella durante 4,5 s; luego dispara desde su propio ojo un rayo colosal (11 de radio) que persigue a un jugador
  acelerando con la distancia hasta 0,25 bloques/tick —corriendo mantenés la distancia, caminando te alcanza— durante 9 s, abriendo una zanja hasta el vacío. Después queda expuesto.
- **Tentáculo Titánico:** revienta el piso, se alza 70 bloques y azota a lo largo de una franja marcada, abriendo una zanja.
- **Las Fauces:** la pupila se abre como un agujero negro y traga el piso; te arrastra (cubrite detrás de un obelisco) y termina con una expulsión.
- **Lágrimas del Cielo:** orbes de luz con estela caen despacio; al tocar el piso no pasa nada... se enciende una luz, colapsa de golpe y estalla.
**Regeneración (una vez, al 50%):** bebe del Vacío y recupera vida hasta el 55% (0,6%/s, como mucho 30 s); si lo
logra, la Palma vuelve a estar disponible y la invoca otra vez cuando cae de nuevo bajo el 50%.
**Salto:** si un jugador se aleja más de 100 bloques, el Ojo se contrae hasta un punto y reaparece a 22 bloques de él
con una onda que empuja (una vez cada 3 minutos).
**La Palma del Vacío (al 50%):** materializa en el cielo una mano de energía de ~100 bloques con un ojo en la palma;
una barra púrpura arriba de la pantalla (sin texto) da 25 segundos para alejarse. En los últimos 15 s la pantalla se va
oscureciendo; al tocar el suelo, en la negrura brota de golpe la luz y estalla como una supernova: letal hasta ~75 bloques, daño hasta 150, y un muro de luz arrasa la superficie (obeliscos incluidos) sin abrir agujeros al vacío. `/bloodmoon summon palm` para probarla.
Tras el Ojo Colosal y las Fauces baja exhausto hasta el piso (vulnerable). Al morir, todos vuelven al Santuario con el botín.
Morir en el Más Allá no hace perder el inventario. Si pierden, el Santuario se reabre en 5 minutos.

## Comandos (OP)
- `/bloodmoon force [blood|super|golden|moonless]`
- `/bloodmoon cancel`
- `/bloodmoon status`
- `/bloodmoon summon rider`
- `/bloodmoon summon emissary`
- `/bloodmoon summon executioner`
- `/bloodmoon summon dragon`
- `/bloodmoon summon eye` (el estrado es donde estás parado)
- `/bloodmoon locate sanctum`
- `/bloodmoon summon unbound` (la forma final, donde estés)
- `/bloodmoon locate coliseum`
- `/bloodmoon intro`
- `/summon bloodmoon:cursed_creeper`

## Configuración
`config/bloodmoon-common.toml` (se crea al primer arranque): frecuencias, multiplicadores de explosión,
probabilidad de Cursed Creeper, tamaño/daño de phantoms, vida del jinete, drop del equipo, potencia del Juicio Final (`executionerJudgmentPower`) y del especial del mazo (`maulSpecialPower`).
