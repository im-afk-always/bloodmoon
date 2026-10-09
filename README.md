# Blood Moon — NeoForge 1.21.1 (v11)

## Al entrar por primera vez
La pantalla se oscurece y un ojo púrpura se abre frente al jugador. Encima, un texto en el alfabeto de la mesa de
encantamientos cambia de símbolo hasta fijarse letra por letra: **«Nos encontraremos pronto»**. El ojo se cierra y desaparece.
Una vez por jugador y por mundo; `/bloodmoon intro` lo repite.

## Lunas
| Luna | Frecuencia (default) | Visual |
|---|---|---|
| Luna de la Cosecha | cada 13 noches | Cielo carmesí, luna realista con halo, todo teñido de rojo |
| Luna Dorada | cada 7 noches | Luna vanilla dorada, cielo oscuro con brillo dorado (sin efectos de juego) |
| Eclipse Solar | por comando (o cada N días) | Ver "Eclipse Solar" |
| Noche sin Luna | cada 50 noches | La noche cae lento hasta negro total; una ruptura nace en el cenit, se propaga a los horizontes y se abre en una grieta; dentro, un ojo púrpura de pupila felina que frunce la mirada. La grieta y el ojo irradian una luz violeta difusa: el mundo se ilumina tenuemente de púrpura a medida que se abre (las antorchas viran a lavanda), con resplandor, contraste y viñeta violeta. Toda la noche surgen hordas de **Centinelas** (espada) y **Arqueros del Vacío** (arco): esqueletos de hueso negro con armadura del Vacío encantada; se desvanecen al amanecer. A medianoche cae del cielo como un bólido negro el **Emisario Desconocido** o **El Ejecutor**, dejando un cráter |

Prioridad si coinciden: Sin Luna > Cosecha > Dorada. Las lunas ya no cambian la dificultad: no hay más mobs, buffs ni
criaturas especiales (los Cursed Creepers y el Jinete del Apocalipsis siguen existiendo solo por comando).

## Luna de la Cosecha
**Ambiente:** el mundo entero queda bañado en luz carmesí (la luz del cielo se tiñe, la luna ilumina a cielo abierto y
las antorchas arden en rojo brasa), más oscuro y con más contraste; cielo y horizonte rojo sangre, bruma más cercana,
una luna realista con un halo tenue y nubes generadas en tiempo real (nunca se repiten, cambian de forma y el viento las arrastra) que se encienden cerca de ella (reemplazan a las cúbicas).
**Música:** suena el *Lacrimosa* del Réquiem de Mozart desde que sale la luna, en lugar de la música normal, con una
pausa de 30-60 s entre repeticiones; al amanecer termina de sonar sola.
Encima, un posprocesado propio (tono carmesí, contraste, resplandor de la luna y las luces, viñeta) que entra y sale
solo con la luna; la mano y la interfaz no se tiñen. Se puede apagar en `config/bloodmoon-client.toml`
(`harvestMoonPostEffect`, también apaga el de la Noche sin Luna) y se desactiva solo si está Iris. Con paquetes de shaders el tinte de la luz puede perderse en parte.

### Ofrendas a la deidad de la cosecha
Cada Luna de la Cosecha hay que ofrendar mobs **hostiles**: días transcurridos x 1,5 (redondeo hacia arriba). Arriba a
la derecha aparece **«Ofrendas restantes X/Y»**. La cuenta es compartida por todos los jugadores del Overworld.
- Cada ofrenda tiene un 30 % de dar una bendición de 20 s: Fuerza, Resistencia, Velocidad o Regeneración (I a III).
- Matar un mob no hostil (un pollo, un aldeano...) suma 2 a la cuenta: *«Has ofendido a la deidad con una ofrenda deshonrosa»*.
- Dormir para saltear la luna sin completar la ofrenda (o llegar al amanecer sin completarla) es una ofensa: *«Has
  ofendido a la deidad de la cosecha al no ofrendarle nada»*.
- A las 3 ofensas, la siguiente Luna de la Cosecha **no deja dormir**. Al terminar esa luna, las ofensas vuelven a cero.
- Config: `offeringPerDay` (1,5), `offeringCap` (tope; 0 = sin tope), `offeringBlessingChance` (0,3).

### Devoción
Completar el pedido de una deidad da **reputación** a cada jugador que aportó: entre el 50 % y el 100 % de la dificultad
del pedido, según su parte (las ofrendas deshonrosas no la inflan). Una vez cumplida la cuota de la noche, cada criatura
hostil extra da **+1** de reputación a sus devotos. Quien no es devoto recibe en el chat
*«¿Quieres ser devoto de la Luna de la Cosecha? - [SÍ] [NO]»* (clic con el chat abierto). SÍ: entra como Iniciado con
reputación 0. NO: la deidad lo recuerda; la propuesta vuelve con el próximo pedido cumplido.

| Nivel | Rango | Reputación |
|---|---|---|
| I | Iniciado | 0 |
| II | Acólito | 50 |
| III | Creyente | 120 |
| IV | Fiel | 250 |
| V | Diácono | 450 |
| VI | Sacerdote | 700 |
| VII | Obispo | 1000 |
| VIII | Arzobispo | 1500 |
| IX | Apóstol | 2200 |
| X | Profeta | 3200 |
| XI | Elegido | 4500 |

En el inventario (bajo la grilla de crafteo) aparece un encapuchado; sus ojos toman el color de la deidad (rojo sangre
para la Luna de la Cosecha). Al hacer clic muestra deidad, rango, nivel de adoración y reputación.

**Aura de sangre:** los devotos de la Luna de la Cosecha arden en llamas translúcidas color sangre que nacen en los pies
y suben (todos las ven). En Iniciado solo lamen los pies; con cada rango suben más hasta envolver a la persona entera
en Elegido. Desde Creyente las recorren rayos de estática rojos, fractales y ramificados, en cuatro formas que se
suman con el rango: arcos sobre el cuerpo (III), descargas hacia afuera (V), rayos que reptan por el suelo (VII) y
espirales que se enroscan subiendo (IX). En primera
persona las propias llamas quedan bajas y ralas.

**Prefijo:** el nombre del devoto lleva su rango delante, con el color de la facción: `[Apóstol] Jugador` en el chat,
en la lista de jugadores (Tab) y sobre la cabeza.

## Eclipse Solar
`/bloodmoon eclipse` lleva la hora al amanecer y arranca uno (o cada N días con `solarEclipseEveryDays`; 0 = solo por
comando). `/bloodmoon eclipse cancel` lo cancela. `/bloodmoon eclipse permanent` lo congela en el instante actual
(detiene el ciclo día/noche; si no hay uno en marcha, arranca uno ya en la totalidad); repetirlo lo libera y sigue su curso. Todo ocurre en ~3 min 40 s de juego:
- Ese día el sol es realista (granulación, borde más oscuro y anaranjado, resplandor). Un rato después del amanecer la
  luna asoma pálida por el horizonte, lo persigue más rápido y lo alcanza; al acercarse se vuelve una silueta oscura.
- La luz se ahoga poco a poco (casi no cambia hasta ~75% tapado y después cae en picada): el mundo vira a un gris pardo
  apagado, el cielo a pizarra casi negro, las nubes se apagan y el horizonte queda encendido todo alrededor como un
  atardecer de 360°. Las antorchas no cambian: en la totalidad son lo único que brilla.
- Justo antes de la totalidad, las **cuentas de Baily** titilan en el borde y estalla el **anillo de diamante**: el
  último rayo de sol con un destello cegador y rayos larguísimos (encandila si lo mirás).
- **Totalidad** (~24 s, con el sol a ~61°): un halo perlado que contornea el disco lunar, con protuberancias rosadas, estrellas
  en pleno día, un acorde grave. Despiertan criaturas de la noche alrededor de quienes estén a cielo abierto y los
  no-muertos no se queman mientras dura la oscuridad (`solarEclipseCreatures`).
- Al salir, el anillo de diamante vuelve por el borde opuesto y la luz regresa.

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
- `/bloodmoon force [super|golden|moonless]`
- `/bloodmoon cancel`
- `/bloodmoon devotion offer|add <n>|reset` (pruebas del culto)
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
`config/bloodmoon-common.toml` (se crea al primer arranque): frecuencias, ofrendas, multiplicadores de explosión, potencia del Juicio Final (`executionerJudgmentPower`) y del especial del mazo (`maulSpecialPower`).
