# Blood Moon — NeoForge 1.21.1 (v11)

## Al entrar por primera vez
La pantalla se oscurece y un ojo púrpura se abre frente al jugador. Encima, un texto en el alfabeto de la mesa de
encantamientos cambia de símbolo hasta fijarse letra por letra: **«Nos encontraremos pronto»**. El ojo se cierra y desaparece.
Una vez por jugador y por mundo; `/bloodmoon intro` lo repite.

## Lunas
| Luna | Frecuencia (default) | Visual |
|---|---|---|
| Luna de la Cosecha | cada 13 noches | Cielo carmesí, luna realista con halo, todo teñido de rojo |
| Luna de la Providencia | cada 7 noches | Cielo ámbar, luna realista dorada con corona tenue, todo bañado en luz dorada |
| Eclipse Solar | por comando (o cada N días) | Ver "Eclipse Solar" |
| Noche sin Luna | cada 50 noches | La noche cae lento hasta negro total; una ruptura nace en el cenit, se propaga a los horizontes y se abre en una grieta; dentro, un ojo púrpura de pupila felina que frunce la mirada. La grieta y el ojo irradian una luz violeta difusa: el mundo se ilumina tenuemente de púrpura a medida que se abre (las antorchas viran a lavanda), con resplandor, contraste y viñeta violeta. Toda la noche surgen hordas de **Centinelas** (espada) y **Arqueros del Vacío** (arco): esqueletos de hueso negro con armadura del Vacío encantada; se desvanecen al amanecer. A medianoche cae del cielo como un bólido negro el **Emisario Desconocido** o **El Ejecutor**, dejando un cráter |

Prioridad si coinciden: Sin Luna > Cosecha > Providencia. Las lunas ya no cambian la dificultad: no hay más mobs, buffs ni
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

**Halo dorado (Providencia):** un anillo de luz dorada rodea al devoto a la altura de la cintura, gira despacio y lo
recorren cuentas brillantes. Con el rango crece y se le suman: motas de luz que suben (III), una aureola sobre la cabeza
(V), un segundo anillo inclinado que gira al revés (VII), un anillo en el suelo con columnas de luz (IX) y un tercer
anillo (XI).

**Prefijo:** el nombre del devoto lleva su rango delante, con el color de la facción: `[Apóstol] Jugador` en el chat,
en la lista de jugadores (Tab) y sobre la cabeza.

## Luna de la Providencia
**Ambiente:** el mundo se baña en una luz dorada cálida (casi sin oscurecer; las antorchas viran a ámbar), cielo y
horizonte ámbar, una luna realista dorada sin halo pero con una **corona** luminosa y tenue pegada al disco, nubes
doradas que se encienden cerca de ella y un posprocesado cálido. Sin música propia.

**Tributos:** la deidad pide días x 5 tributos (tope 150): cada **cultivo maduro cosechado** (trigo, zanahoria, papa,
remolacha, verruga del Nether, cacao, calabaza, sandía...) y cada **mineral extraído** cuenta uno. Lo que un jugador
coloca durante la noche no cuenta (no se puede poner y romper). Arriba a la derecha: «Tributos restantes X/Y».
No hay ofensas: si no se cumple, la Providencia solo lo recuerda. Cumplirlo da reputación y la propuesta de devoción.
**Criar animales** da reputación a sus devotos: +1 por cría, +2 durante su luna.
Una vez cumplido el pedido, cada mineral, cultivo o cría de esa noche da **+0,15** (las fracciones se acumulan).
Config: `providenceTributePerDay` (5), `providenceTributeCap` (150), `providenceSurplusReputation` (0,15).

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

## Invasión del Vacío (etapas 1 a 3)
Encender el portal del zigurat de un **Coliseo del Vacío** despierta un **Dominio** con eje en ese portal. El Dominio
crece solo, aunque nadie lo mire (se simula en una grilla de chunks, sin cargarlos), hasta **1000 bloques** del portal
(`invasionRadius`); avanzar le cuesta más cuanto más lejos y sobre el agua. Cuando un chunk se carga, se aplica:
- **Marchito:** la mitad del césped muere, plantas secas, hojas que caen.
- **Muerto:** **Tierra Muerta**, Tierra Yerma, Hierba Muerta, Troncos Calcinados, manchas de Roca Negra y **todo bloque de
  jugador o de aldea en la superficie se reemplaza** por ladrillos de roca negra (lo subterráneo no se toca). El
  contenido de cofres y barriles va al **Relicario**. El coliseo no se toca.
- **Obeliscos del Dominio:** anclas de roca negra con un **Núcleo** (pico de diamante); romperlo hace retroceder la influencia.
- **Fases:** Despertar, Arraigo, Conquista y Dominio (con los valores por defecto: ~día 7, ~18 y ~33).
- **Rangos con cuerpo:** cuando te acercás a su puesto aparecen el **Capitán del Vacío** (esqueleto de élite con el
  estandarte del Dominio, barra de jefe; potencia a las tropas cercanas; custodia un obelisco) y los **Generales**
  (**General del Vacío**: señor de guerra de ~4 bloques con yelmo astado, peto con el ojo del Dominio, capa raída,
  guja y una aureola de runas; barre con la guja, lanza un Grito de guerra que llama Centinelas y enardece a los suyos,
  y salta sobre vos clavando la guja con una onda de choque; sede: una fortaleza) y, desde la fase Dominio, el
  **Rey del Vacío** (~6,5 bloques, flota en la arena del coliseo frente al portal; máscara de obsidiana, corona con
  gemas orbitantes, cetro-jaula y orbe; 1200 de vida. Golpe de cetro, **Lluvia del Juicio** (lágrimas del cielo),
  **Decreto Real** (guardia de nivel 8 y potenciación de aliados) y **Nova de la Corona** (tres anillos de choque a
  6, 12 y 18 bloques: saltalos o alejate); fase 2 al 50%). Lejos de todos vuelven a ser datos y guardan su vida.
  Matarlos deja el puesto vacío un día; un General caído además le quita esencia al Dominio y lo frena a la mitad un día.
  Matar al Rey abre un **interregno**: el Dominio pierde 2000 de esencia, no gana tierra por 3 días y el trono queda vacío ese tiempo.
- **Guarniciones:** cada obelisco cercano a un jugador tiene de 2 a 5 Centinelas y Arqueros.
- **Forjadores del Vacío:** con un jugador cerca, los obeliscos se levantan bloque a bloque; matarlos detiene la obra
  (vuelven en 30 s si el Dominio tiene). Con un jugador a menos de 64 bloques, la corrupción avanza columna a columna.
- **Estructuras mayores** (cada una con su Núcleo; romperlo la deja en ruinas). Ocupan 3×3 chunks de tierra muerta,
  miran con la puerta hacia el coliseo y, con un jugador a 200 bloques, los Forjadores las levantan a la vista:
  - **Nido de Ceniza:** fosa de 8 en terrazas con rampas, seis costillas de roca que se cierran encima con jaulas
    colgantes y un altar con fuego de almas; muchas tropas en el borde.
  - **Atalaya:** torre de ~46 de alto con contrafuertes, escalera de caracol por dentro, ventanas de vidrio del Vacío y
    una corona con parapeto, runas y cuernos; escalera cuadrada de 5×5 que llega a la corona; arqueros y un Capitán arriba.
  - **Fortaleza:** muralla de 37×37 con camino de ronda y matacanes, cuatro torres cónicas, barbacana con rastrillo
    de cadenas y el ojo del Dominio, patio con braseros y un torreón de 15×15 con salón de columnas y aguja; un General
    en el patio.
  - **Aguja del Dominio:** torre de ~180 de alto (cuatro veces la Atalaya) sobre 5×5 chunks: contrafuertes colosales,
    cuatro tramos que se afinan con balcones, pisos interiores, escalera hasta la corona y un pabellón con aguja.
  Nunca se construyen sumergidas: buscan tierra seca y, si al llegar el lugar resulta inundado, la obra se cancela.
  Nidos y atalayas desde Arraigo; fortalezas y agujas desde Conquista. Las tropas y los rangos aparecen siempre a ras
  del suelo, alrededor de cada estructura. Los **obeliscos** ahora son de 13×13 y ~29 de alto,
  con el núcleo enjaulado en vidrio, columnas con faroles y farolas colgantes.
- **Bloques de arquitectura:** escaleras, losa y muro de ladrillo de roca negra, roca negra pulida, pilar, runa
  cincelada (brilla), cadena y panel de vidrio del Vacío.
- **Faros:** un faro encendido protege su radio de efecto; el Dominio no reclama esa tierra y la que ya tenía retrocede.
- **El Trono:** mientras el Rey vive, el portal del coliseo está sellado; primero hay que derribarlo.
- **La Ofrenda (fase 4):** el Dominio levanta el **Santuario de la Primera Alma** (11×11 chunks: muralla circular de
  137 de diámetro con ocho torres y cuatro puertas, fosa ritual en terrazas con rampas y ocho pilones colosales) y le da la mitad de su ingreso a un **cristal gigante** que crece
  sobre el estrado, alimentado por rayos desde los pilones. Romper su **Corazón** (el núcleo enterrado en el cristal)
  pierde todo lo acumulado. Lleno, tras diez segundos de latidos, grietas y relámpagos, el cristal estalla y **emerge el Dragón de la Primera Alma**, con título y sonido para
  todos los jugadores. Es opcional: si vencés antes al Dominio no pasa. Aviso al 50% y al 90%.
- **Nivel de la horda (0-10):** sube con la esencia ganada. Los soldados nacen con 0-1 piezas y Filo I / Poder I; con el
  nivel ganan piezas, Protección, Irrompibilidad y mejores armas (Capitanes, dos niveles más).
- **Hechicero del Vacío:** mantiene la distancia y lanza Lanza Astral, Lluvia de Estrellas, Prisión del Vacío,
  Espectros (vexes), Paso del Vacío (se teletransporta si lo alcanzás) y Égida. Aparece desde el nivel 2 en guarniciones
  y desde el asalto 3.
- **Mapa:** tecla **M** (configurable) desde cualquier modo; flecha del jugador con rumbo y nombre (en el borde si
  quedás fuera de la vista), **C** centra.
- **Pruebas:** `/bloodmoon invasion raid <1-10>` (asalto de prueba: no avanza tu nivel ni deja cabeza de playa),
  `raid stop`, `soul site`, `soul feed <0-99>`, `soul awaken`.
- **Caminos de roca negra** de 3 de ancho, con farolas de runa, unen cada estructura con la siguiente hacia el coliseo.
- **Asaltos por niveles** (desde Conquista, cada uno o dos días si estás dentro del radio): se abre una **Puerta de
  Guerra** a ~26 bloques y salen oleadas que te buscan y rompen bloques para llegar (si mobGriefing está activo). El
  nivel sube con cada asalto que enfrentás (1 a 10): nivel N = 10×N tropas (máx. 40 vivas a la vez), cada vez mejor
  equipadas (nivel 10: Set del Vacío completo con Protección IV, Irrompibilidad y Espinas, armas al máximo), con
  Capitanes de Asalto desde el nivel 4; duran 5+N minutos. Rechazado: el Dominio pierde 150×N de esencia. Fallido (se
  acaba el tiempo o morís): **cabeza de playa** de 3×3 chunks muertos con un obelisco.
- El bando del Vacío no se hiere entre sí ni se quema con el fuego astral.
- El mapa muestra caminos, nidos, atalayas, fortalezas y los asaltos en curso.
- **Final:** entrar por el portal de ese coliseo y vencer al **Observador Desatado**. El Dominio se quiebra, el Relicario
  aparece en cofres junto al portal y la tierra sana de afuera hacia adentro.
- **Mapa del Dominio:** botón con un mapa en el inventario (junto al encapuchado). Muestra el terreno que ya viste y el
  Dominio completo aunque no esté cargado; arrastrar para mover, rueda para zoom. El **ojo** del margen muestra la
  jerarquía: Rey del Vacío, Generales, Capitanes, Forjadores y tropas.
- Config: `invasionRadius` (1000), `invasionCycleSeconds` (20), `invasionSpeed` (1.0), `invasionReplacePlayerBlocks` (true).

## Comandos (OP)
- `/bloodmoon force [super|golden|moonless]`
- `/bloodmoon cancel`
- `/bloodmoon invasion start|grow <ciclos>|end|status|raid|build` (Invasión del Vacío: despertar el coliseo más cercano, adelantar ciclos, vencerla, estado, forzar un asalto contra vos, obelisco en obra donde estás)
- `/bloodmoon devotion offer [harvest|providence]|add <n>|reset|status|complete` (pruebas del culto; `status` muestra tu devoción y el pedido en curso; `complete` completa el pedido de esta noche)
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
