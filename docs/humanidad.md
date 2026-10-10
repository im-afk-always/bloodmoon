# La Humanidad — plan de diseño y desarrollo

**Mod:** Blood Moon (NeoForge 1.21.1) · **Estado:** plan (no hay código todavía) · **Depende de:** la Invasión del Vacío (misma arquitectura de simulación abstracta).

---

## 0. La verdad incómoda primero

1. **Es el desarrollo más grande del mod, por lejos.** La Invasión es una sola facción con un objetivo (crecer). La Humanidad son N facciones que producen, comercian, negocian, votan y guerrean entre sí y contra el Vacío. Si se encara todo junto no termina nunca o termina hueco. El plan está en **fases jugables**: cada fase deja algo que funciona en partida aunque las siguientes no existan.
2. **Los sistemas de gobierno solo valen si cambian números del juego.** "Democracia" o "comunismo" como etiqueta de color no aportan nada y además se leen como opinión política. Acá cada gobierno es un **paquete de reglas con ventajas y costos** (impuestos, precios, estabilidad, ejército, diplomacia) y ninguno es "el bueno". Se tratan como mecánicas, sin referencias a personas, partidos o países reales.
3. **Sacar aldeas y aldeanos vanilla rompe cosas que los jugadores usan.** Comercio de libros encantados (Reparación), granjas de hierro, cura de aldeanos zombi, Héroe de la Aldea, gatos, asaltos de saqueadores, logros. Todo eso tiene que tener **reemplazo** en la Humanidad o el mod se siente más pobre, no más rico (§3).
4. **"Skins aleatorias de Minecraft" no puede significar skins de jugadores reales.** Bajarlas de los servidores de Mojang exige internet, se rompe offline y usa la imagen de personas reales sin permiso. La propuesta: un **generador procedural de skins** (tono de piel, cara, pelo, ropa) con miles de combinaciones, más capas de profesión. Es lo mismo que ya hicimos con las texturas de los mobs.
5. **El realismo cuesta procesamiento.** Simular cada humano como entidad es inviable con más de unas decenas. Igual que el Dominio: **todo vive como datos** (población, bienes, tesoro, ejércitos) y solo se **materializa** cerca del jugador.

---

## 1. Pilares (lo que hace que valga la pena)

| Pilar | Qué siente el jugador |
|---|---|
| **Un mundo que vive sin vos** | Volvés después de 10 días y una aldea es un pueblo amurallado, hay un camino nuevo y dos reinos están en guerra. |
| **Economía real** | El precio del hierro sube porque hay guerra; vender 300 trigo en un pueblo chico hunde el precio; el oro tiene respaldo. |
| **Política con consecuencias** | Una república sube impuestos tras perder una guerra y cae en una revuelta; un imperio absorbe a su vecino. |
| **Gente, no máquinas expendedoras** | Humanos con nombre, cara propia, oficio visible, horarios, familia y opinión sobre vos. |
| **El Vacío como plaga común** | Cuando un Dominio despierta, los reinos (rivales incluidos) mandan ejércitos o se derrumban. |

---

## 2. Arquitectura: la misma que el Dominio, generalizada

- **Simulación abstracta por ciclos** (`HumanityManager`, ciclo de 20 s como el Dominio, configurable). No carga chunks.
- **Territorio por chunk:** la grilla de influencia del Dominio se generaliza a una capa de **dueños políticos** (polity id + influencia). Dominio y reinos compiten por la misma grilla: frontera viva.
- **Entidades de datos** (SavedData): `Settlement` (asentamiento), `Polity` (reino/república/imperio), `Household` opcional (familias), `Market`, `Army`, `Treaty`, `Route`.
- **Materialización** (como `DominionPresence`): humanos, guardias, caravanas y ejércitos aparecen a < 96 bloques de un jugador con su estado guardado (vida, oficio, nombre, skin) y vuelven a ser datos al alejarse.
- **Construcción** con el pipeline que ya existe: plantillas generadas (`DominionTemplates`), obra a la vista con trabajadores (como los Forjadores), presupuesto por tick sin espectadores, chequeo de agua y de suelo real.
- **Datos por datapack** (JSON): culturas, oficios, edificios, bienes, tipos de gobierno. Así se ajusta el balance sin recompilar y otros pueden extenderlo.

**Presupuestos duros** (no negociables): ciclo de simulación < 5 ms promedio; humanos materializados por jugador ≤ 40 (configurable); caravanas/ejércitos visibles ≤ 3 por jugador; plantillas grandes con el mismo presupuesto de 2 ms/tick.

---

## 3. Reemplazar lo vanilla (Fase 1)

| Vanilla | Cómo se quita | Reemplazo humano |
|---|---|---|
| Aldeas (estructuras) | Override de `minecraft:worldgen/structure_set/villages` vacío (datapack del mod) | **Aldeas fundacionales** prediseñadas por cultura (§5) |
| Aldeanos | Sin aldeas no nacen; se bloquean huevos/curas que creen aldeanos y se convierten los existentes en humanos al cargar | **Humanos** con oficio |
| Aldeano zombi | `neoforge:remove_spawns` + conversión | **Humano infectado** (se puede curar igual: poción de debilidad + manzana dorada) |
| Comerciante errante | Cancelar su spawner | **Caravanas** reales entre asentamientos (se pueden escoltar o asaltar) |
| Gólem de hierro | Ya no aparecen solos | **Guardias** (y en reinos ricos, gólems forjados como unidad cara) |
| Comercio de libros / Reparación | — | Bibliotecario humano vende libros a precio de mercado (escaso y caro) |
| Saqueadores / asaltos | Se quedan, re-ambientados como **bandidos** sin reino; los asaltos atacan asentamientos humanos | Defender un pueblo da **reputación** en vez de Héroe de la Aldea |
| Mundos existentes | Las aldeas ya generadas se **adoptan**: el mod las detecta (camas + estaciones de trabajo) y crea un asentamiento con esos edificios | Nada se borra del mapa del jugador |

---

## 4. Los humanos

### 4.1 Entidad
- `Human` (PathfinderMob) con **modelo de jugador** (brazos finos/anchos), animaciones de trabajo y capas: piel → ropa → equipo de oficio → armadura.
- **Skins procedurales**: semilla por humano → tono de piel, rasgos, ojos, pelo (estilo y color), barba, ropa base por cultura y riqueza. Se generan en el cliente y se cachean como textura dinámica (no viajan por red: solo la semilla).
- **Identidad**: nombre y apellido por cultura, edad (niño → adulto → anciano), familia, hogar, lugar de trabajo, riqueza, lealtad, opinión del jugador.
- **Rutina diaria**: dormir, trabajar, mercado, taberna/templo, guardia nocturna. Comportamiento visible: el herrero martilla, el granjero cosecha, el pescador pesca.

### 4.2 Oficios (equipo que los delata)
| Oficio | Equipo visible | Produce |
|---|---|---|
| Granjero | sombrero de paja, azada, delantal | comida, trigo |
| Pastor | cayado, ropa de lana | lana, carne |
| Pescador | caña, gorro | pescado |
| Leñador | hacha, camisa a cuadros | madera |
| Minero / cantero | pico, casco con vela | piedra, minerales |
| Herrero / armero | delantal de cuero, martillo, guantes | herramientas, armas, armaduras |
| Carpintero / albañil | martillo, cinturón de herramientas | construcción (obra a la vista) |
| Panadero / carnicero | gorro, cuchillo | comida elaborada |
| Tejedor / sastre | tijeras | tela, ropa |
| Bibliotecario / escriba | libro, gafas | libros, administración (impuestos +eficiencia) |
| Clérigo | túnica, incensario | estabilidad, curación |
| Mercader | bolsa de monedas, ropa fina | comercio, caravanas |
| Guardia / soldado | según nivel (§8) | defensa |
| Noble / funcionario | ropa rica, joyas | gobierno |

La profesión se asigna según la **necesidad del asentamiento** (déficit de comida → más granjeros), no al azar.

---

## 5. Asentamientos: de aldea a imperio

### 5.1 Culturas (por bioma)
Llanura (madera y piedra, techos a dos aguas), desierto (adobe, patios), taiga (troncos, empalizadas), sabana (barro y paja), nieve (piedra gruesa), costa (palafitos y puertos). Cada una: arquitectura, nombres, bienes típicos, inclinación inicial de gobierno.

### 5.2 Niveles
| Nivel | Población | Rasgos | Gobierno típico |
|---|---|---|---|
| Aldea | 10–40 | casas, granjas, pozo, milicia | consejo de ancianos / jefe |
| Pueblo | 40–150 | mercado, herrería, empalizada, templo | señorío o asamblea |
| Ciudad | 150–600 | muralla de piedra, castillo o ayuntamiento, puerto, gremios | según la entidad política |
| Capital | 600+ | ciudadela, palacio, catedral, ceca (acuña monedas) | sede del gobierno |

- **Crecimiento** = comida + vivienda + seguridad + estabilidad. Sin comida no crecen; con hambre emigran o se rebelan.
- **Expansión**: un asentamiento grande manda **colonos** a fundar aldeas nuevas (en tierra libre y seca, cerca de recursos), que nacen ligadas a su entidad política.
- **Obras**: cada mejora es una plantilla que se paga desde el tesoro y la levantan albañiles a la vista del jugador.

### 5.3 Entidades políticas
- **Señorío / ciudad-estado**: 1 asentamiento.
- **Reino**: ≥ 3 asentamientos con una capital.
- **Imperio**: ≥ 3 reinos vasallos o ≥ 20 asentamientos.
- Pueden **fragmentarse** (guerra civil, secesión), **unirse** (alianza matrimonial, anexión pactada) o **convertirse en vasallos**.

---

## 6. Economía a gran escala

### 6.1 Moneda
- **Monedas de oro** (ítem). Propuesta de denominaciones: moneda de cobre (1) · plata (10) · oro (100), con bolsas para guardarlas. *Abierto: si querés solo oro, se simplifica.*
- **Respaldo real**: solo una **ceca** (capitales) acuña, y para acuñar consume oro de su tesoro. Cada reino lleva la cuenta de sus monedas en circulación. Un reino en crisis puede **devaluar** (acuñar con menos oro): más monedas, **inflación** en sus mercados.
- Evita el clásico exploit de "dinero infinito": las monedas que el jugador recibe salen de un tesoro que existe.

### 6.2 Bienes y mercados
- ~20 **bienes abstractos** (comida, grano, madera, piedra, hierro, oro, carbón, herramientas, armas, armaduras, tela, lana, cuero, libros, sal/especias, caballos, materiales de construcción, lujo).
- Cada asentamiento: **producción** (oficios × recursos del bioma × tecnología), **consumo** (población × nivel de vida) y **stock**.
- **Precio** por oferta y demanda con inercia: `precio = base × (demanda / oferta)^elasticidad`, suavizado por ciclo. Precios distintos en cada mercado → oportunidad de comercio.
- **Rutas comerciales**: mercaderes llevan bienes de donde sobran a donde faltan si el margen supera costo + riesgo. Las rutas son caminos reales y caravanas visibles; los bandidos y la guerra las cortan.
- **El jugador** compra y vende en el mercado de cada asentamiento con los precios vivos. Lo que vende sube la oferta y baja el precio (no puede vender 1000 trigo al mismo precio). También puede tomar **contratos**: entregas, escoltas, recompensas por bandidos, suministros de guerra.

### 6.3 Tesoro e inversiones
- **Ingresos**: impuestos (sobre producción, comercio, tierra, personas, según el gobierno), aranceles, tributos de vasallos, botín.
- **Gastos**: mantenimiento del ejército, obras, administración, subsidios, deuda.
- **Decisiones de inversión** (IA por prioridades con pesos del gobierno): murallas si hay amenaza, granjas si hay hambre, mercado si hay comercio, cuarteles si hay guerra, colonias si sobra población.
- **Deuda y bancarrota**: un reino puede pedir prestado a otro (o al jugador) con interés; si no paga, crisis.

---

## 7. Gobiernos (paquetes de reglas, no ideología)

Cada gobierno es un JSON con modificadores. Ninguno domina: todos tienen un costo. Valores iniciales tentativos para simular y ajustar.

| Gobierno | Cómo se elige el líder | Economía | Fortalezas | Debilidades |
|---|---|---|---|---|
| **Jefatura tribal** | El más fuerte o anciano | trueque, impuesto bajo | expansión rápida, milicia barata | poca producción, sucesión inestable |
| **Monarquía feudal** | Herencia | impuestos a la tierra, nobles | ejército fuerte (levas), estabilidad si el rey es querido | crisis de sucesión, nobles rebeldes |
| **Monarquía absoluta** | Herencia | centralizada | decisiones rápidas, gran ejército permanente | descontento acumulado, revoluciones |
| **República / democracia** | Elecciones cada N días (candidatos con programa) | impuestos moderados, comercio abierto | alta estabilidad mientras cumple, comercio +, innovación + | lenta para la guerra, cambia de rumbo entre elecciones |
| **República mercantil (capitalista)** | Consejo de los más ricos | mercado libre, impuesto bajo, gremios | comercio y riqueza máximos, mejores precios al jugador | desigualdad → malestar en pobres, ejército mercenario caro |
| **Comuna colectivista (comunista)** | Asamblea / partido único | producción estatal, precios fijados, reparto | sin hambre ni desigualdad, obras baratas | menos eficiencia, mercado negro, comercio exterior limitado |
| **Teocracia** | Clero | diezmo | estabilidad y moral altas, cruzadas contra el Vacío | ciencia y comercio −, intolerancia diplomática |
| **Junta militar** | Golpe de estado | economía de guerra | ejército máximo | economía en declive, golpes sucesivos |
| **Imperio** | Emperador (herencia o aclamación) | tributo de vasallos | escala, legiones | sobreextensión, secesiones |

**Dinámica política** (lo que lo hace inmersivo):
- **Estabilidad** (0–100) por asentamiento: comida, impuestos vs. gobierno, guerras perdidas, plaga del Vacío, desigualdad, lealtad.
- **Eventos**: elecciones (con resultados visibles y cambio de políticas), crisis de sucesión, golpes, revueltas, revoluciones que **cambian el tipo de gobierno**, secesiones, guerras civiles.
- **El jugador** puede votar si es ciudadano de una república, financiar un candidato, apoyar o sofocar una revuelta.

---

## 8. Ejército y guerra

### 8.1 Progresión
| Etapa | Tropa | Equipo |
|---|---|---|
| Aldea | Milicia | ropa, horquillas, hachas, arcos de caza |
| Pueblo | Guardia | cuero, escudos, espadas de piedra/hierro |
| Ciudad | Hombres de armas, arqueros | cota de malla, hierro, ballestas |
| Reino | Caballeros, sargentos | hierro completo, caballos con armadura, estandartes |
| Imperio | Legiones, élite | diamante/encantado, máquinas de asedio (simuladas) |

Las tropas **cuestan** (reclutamiento + mantenimiento + equipo, que sale del mercado: si no hay hierro, no hay armaduras).

### 8.2 Guerra
- **Causas**: fronteras, recursos, deudas, religión/gobierno opuestos, agresión al aliado, ambición del líder.
- **Simulación abstracta** de ejércitos que marchan por la grilla; batallas resueltas por fuerza, terreno, moral y comandante. **Cerca de un jugador se materializa la batalla** (como las oleadas del Dominio) con tope de unidades y el resultado mezcla lo visible y lo abstracto.
- **Asedios**: bloqueo (hambre), asalto a murallas, toma de la ciudad → cambio de dueño del territorio.
- **Paz**: territorio, reparaciones, vasallaje, tributo, matrimonio.

### 8.3 Diplomacia
- **Opinión** entre entidades (−100 a 100) con memoria (traiciones, ayudas, comercio).
- **Tratados**: no agresión, comercio, alianza defensiva/ofensiva, vasallaje, tributo, paso militar, embargo.
- **Reputación del jugador** por entidad: precios, acceso, misiones, ciudadanía, títulos (escudero → caballero → noble con feudo), y el castigo si atacás a sus súbditos.

---

## 9. La Humanidad contra el Vacío

- La **misma grilla**: un chunk es humano, del Dominio o libre. La corrupción avanza sobre tierra humana y los asentamientos alcanzados **resisten, caen o huyen** (refugiados a otro asentamiento: presión económica).
- Cuando un Dominio despierta, cada entidad decide (según gobierno, cercanía y opinión): **cruzada**, defensa propia o indiferencia. Las teocracias lo combaten primero.
- Los ejércitos humanos **destruyen obeliscos**, recuperan chunks (curación) y pueden encontrarse con Generales y el Rey del Vacío.
- Una **alianza contra la plaga** es un evento diplomático: rivales firman tregua temporal.
- Si la Humanidad pierde, la horda tiene más tierra para la Ofrenda; si gana, el mapa se rehace a favor de los reinos.

---

## 10. El jugador dentro de la Humanidad

- Moneda, mercados y contratos (Fase 3).
- Ciudadanía y propiedad: comprar una casa, pagar impuestos (Fase 4).
- Servir en un ejército o como mercenario, ganar títulos (Fase 5).
- **Abierto:** ¿el jugador puede fundar su propio asentamiento o ser rey? Es la extensión natural, pero duplica el alcance. Propuesta: fuera de este plan, como Fase 8.

---

## 11. Interfaz

- **Mapa político** (extiende el mapa del Dominio, tecla M): fronteras con colores, capitales, caminos, rutas comerciales, ejércitos en marcha, guerras activas y territorio del Vacío.
- **Heráldica procedural** por entidad (estandartes vanilla generados): se ve en banderas, escudos y en el mapa.
- **Libro de cuentas** del asentamiento (precios, stocks, tesoro), **panel diplomático**, **pantalla de comercio** con precios vivos e historial.
- **Pregonero y tablón de noticias**: rumores sobre guerras, elecciones, precios y avances del Vacío. Así se entera el jugador de un mundo que se mueve sin él.

---

## 12. Fases de desarrollo (cada una jugable)

| Fase | Contenido | Verificación |
|---|---|---|
| **0. Investigación y prototipos** | Revisar mods de referencia (Millénaire, MineColonies, Minecraft Comes Alive) y juegos de simulación política/económica (Victoria, Crusader Kings, Dwarf Fortress) para robar lo que funciona y evitar lo que no; **simulación offline en Python** de economía (inflación, precios, hambre) y expansión antes de escribir Java | gráficas de 100 días simulados estables (sin inflación desbocada ni colapsos) |
| **1. Fundación** | Quitar aldeas/aldeanos vanilla y reemplazos (§3); entidad Humano con skins procedurales; 4 oficios con equipo; aldeas fundacionales de 2 culturas; adopción de aldeas existentes | prueba de humo: no hay aldeanos ni aldeas vanilla; una aldea humana genera y materializa |
| **2. Asentamientos vivos** | Simulación abstracta de población, producción y crecimiento; niveles aldea → pueblo; obras a la vista; colonias; caminos | 30 días simulados: crecen sin explotar el rendimiento |
| **3. Economía** | Monedas, bienes, mercados con precios vivos, comercio con el jugador, caravanas, tesoro e inversiones | sim offline coincide con la del juego; no hay forma de generar oro de la nada |
| **4. Política** | Entidades políticas (reino, imperio), tipos de gobierno, estabilidad, eventos, elecciones, revueltas, ciudadanía del jugador | cada gobierno gana en algo y pierde en algo en la simulación |
| **5. Guerra y diplomacia** | Ejércitos por etapa, guerra abstracta, batallas materializadas, asedios, tratados, opinión, reputación | una guerra completa de punta a punta en un mundo de prueba |
| **6. Contra el Vacío** | Grilla compartida, cruzadas, refugiados, alianzas contra la plaga | un Dominio y dos reinos peleando por la misma tierra |
| **7. Inmersión** | Rutinas, familias, festivales, pregonero, noticias, más culturas y edificios | jugar 2 horas sin ver un humano "parado como máquina" |

---

## 13. Riesgos

| Riesgo | Mitigación |
|---|---|
| Rendimiento con cientos de humanos | todo abstracto + materialización con tope; ciclos con presupuesto de tiempo |
| Economía que se rompe (inflación, deflación, exploits) | simulación offline antes del código; oro con respaldo; mercados con elasticidad e inercia |
| IA política aburrida o caótica | pocos eventos pero con consecuencias; registro de "por qué" visible en noticias |
| Contenido infinito (edificios, culturas) | generador de plantillas ya probado; arrancar con 2 culturas |
| Romper mundos existentes | adopción de aldeas vanilla; nada se borra sin reemplazo |
| Sensibilidad política | gobiernos como mecánicas con pros y contras; nombres inventados; sin referencias reales |

---

## 14. Decisiones tomadas

1. **Apariencia:** nada de skins de jugadores. Modelos de humanos genéricos generados por el mod con miles de variables (tono de piel, cara, ojos, cejas, nariz, boca, pelo, barba, complexión, ropa por cultura, oficio y riqueza) para que se parezcan a personas distintas.
2. **Comercio:** todo lo que daban los aldeanos vanilla se consigue comerciando con humanos (los 13 oficios con sus mismas ofertas y niveles, pagadas en monedas).
3. **Monedas:** cobre (1), plata (10), oro (100).
4. **Saqueadores → bandidos:** humanos malos (patrullas y campamentos de bandidos en lugar de puestos de saqueadores).
5. **El jugador:** neutral para todos al inicio; puede comerciar, hacerse ciudadano, servir, llegar a rey de un reino existente o fundar el suyo y ser su rey (se suma como Fase 8).
6. **Primeras culturas:** llanura y desierto.

---

## 15. Estado de la Fase 1 (Fundación)

Hecho y verificado en CI (compila + prueba de humo con servidor real):

- **Monedas** de cobre/plata/oro; cambio hacia abajo en la mesa (1 oro → 10 plata → 10 cobre). Hacia arriba solo con el mercader.
- **Humano** (`human/Human`): skin generada (rasgos, peinados, barbas, ropa por cultura y atuendo por oficio), modelo de jugador ancho o fino, nombre por cultura y sexo, título de oficio.
- **Comercio:** los 13 oficios usan las listas vanilla con esmeraldas convertidas (1 esmeralda = 1 plata; ≥10 → oro + resto). Niveles 1-5 con experiencia y reposición. **Mercader:** cambio de monedas (10 cobre → 1 plata, 10 plata → 1 oro), esmeraldas viejas → plata, compra de lingotes y diamantes.
- **Guardias** (espada, cuero teñido, escudo) defienden a los humanos atacados — también del jugador. **Bandidos** (ex saqueadores y vindicadores) atacan jugadores y humanos; sueltan cobre.
- **Conversión:** todo aldeano que aparece se vuelve humano conservando oficio y nivel; el vendedor ambulante y sus llamas desaparecen.
- **Aldeas humanas** de llanura (entramado de madera) y desierto (arenisca, terraza): pozo y plaza, 3-4 calles, mercado, 6-9 talleres de oficio (templo, herrerías con fragua), 4-7 casas, 2-3 granjas, torre de guardia, faroles. Los habitantes nacen en sus puertas y no se alejan de casa. **Las aldeas vanilla ya no se generan.**
- Comandos de prueba: `/bloodmoon village` (aldea más cercana), `/bloodmoon human <oficio> [plains|desert]`.

Pendiente honesto de la Fase 1: aldeanos zombi siguen con aspecto vanilla (al curarlos salen humanos); los bandidos no usan ballesta; las aldeas no tienen todavía datos de asentamiento (población, tesoro) — eso es la Fase 2.

---

## 16. Estado de la Fase 2 (Asentamientos vivos)

Hecho y verificado en CI:

- **Registro:** cada aldea del mundo se vuelve un asentamiento (datos guardados) la primera vez que carga su centro — también en mundos viejos. Nombre propio por cultura (Coldstead, Wadi Nur…).
- **Simulación por ciclo** (1 minuto = 1/20 de día): comida (granjas, pescadores, carniceros, huertas) contra consumo; nacimientos al 3 %/día si hay comida y vivienda; emigración con hambre; impuestos al tesoro (cobre), mercados suman y torres cuestan.
- **Obras por necesidad, pagadas del tesoro:** granja si falta comida, casa si falta lugar, torre al ser pueblo, puestos según población, talleres de oficios que faltan. Lote libre junto a las calles; las calles se estiran (con faroles) a medida que el pueblo crece.
- **Obra a la vista:** con chunks cargados la obra sube bloque a bloque (de abajo hacia arriba, con polvo) y un albañil trabaja en la puerta; lejos de los jugadores avanza como datos y se coloca entera al volver. **No pisa construcciones del jugador:** si el lote tiene bloques artificiales, se descarta y se devuelve el dinero.
- **Población visible:** bebés en las puertas de las casas al nacer; trabajadores nuevos al terminar talleres, puestos y torres. Tope de 40 humanos materializados por asentamiento; el resto es población abstracta.
- **Niveles:** aldea → **pueblo** a los 40 habitantes (aviso a los jugadores cercanos).
- **Colonias:** un pueblo de 60+ habitantes con comida y 8 de oro manda 8 colonos y 300 de cobre a fundar una aldea a 200-320 bloques (tierra seca, pareja, lejos de otras aldeas y coliseos), que se construye desde cero, edificio por edificio.
- **Prueba de humo (servidor real):** 30 días simulados → 22 → 48 habitantes, vivienda 34 → 58, comida nunca negativa, 19 obras colocadas (94 % de bloques exactos, el resto pisado por calles), colonia fundada, **1,4 ms por ciclo** (presupuesto: 5 ms).
- Comandos: `/bloodmoon village` (estado), `village grow <días>` (adelantar), `village colony` (forzar colonia).

Pendiente honesto: la primera búsqueda de lote de un pueblo puede costar ~100-300 ms una vez (después queda cacheada); los tramos de calle que crucen chunks descargados no se pavimentan después; la profesión de los adultos sin oficio todavía no se asigna por necesidad; las colonias aún no aparecen en el mapa.

---

## 17. Calles con forma propia y ciudades (renovación urbana)

- **Trazado variable** (`StreetPlanner`): cada aldea elige un patrón — **orgánico** (2-4 calles principales sinuosas con ramas y sub-ramas), **pueblo-calle** (un camino largo que atraviesa la plaza), **cruce de caminos**, o **manzanas** (desierto, trama de medina). Las calles avanzan en tramos de 7 bloques eligiendo el rumbo más parejo y seco: esquivan lomas y agua, y terminan donde todo es agua.
- **Terreno trabajado por los constructores:** calles niveladas en rampa entre las alturas planificadas (cortan lomas, rellenan pozos hasta 5 bloques, puentes sobre agua); cada edificio con **patio nivelado** y una pendiente suave de 3 bloques hasta el terreno natural; tala de árboles alrededor.
- **Crecimiento:** la red trae calles futuras; se pavimentan cuando se construye a su lado. Si no quedan lotes, el asentamiento **abre calles nuevas** desde las más alejadas.
- **Niveles:** aldea → pueblo (40) → **ciudad (100)** → **capital (220)**; se baja recién con 25 % menos de gente.
- **Ciudad = renovación con riqueza:** fuente en lugar del pozo, **ayuntamiento** con torre del reloj, **mercado cubierto**; casas, talleres y torres de madera del centro se **demuelen y reemplazan** por edificios de **piedra y concreto/terracota de color de 3 plantas** (5 paletas de llanura, 4 de desierto con cúpulas y terrazas), **catedral** para el clérigo, torres de guardia de piedra; las granjas del centro se mudan afuera. Calles **empedradas** (andesita pulida/ladrillo de piedra; arenisca labrada en el desierto) con **faroles a ambos lados**. **Muralla** de 3×6 con almenas, torres cada 4 tramos y portones donde cruza una calle, encerrando el centro (los arrabales quedan afuera). **Capital:** castillo con torre del homenaje y cuatro torres (llanura) o palacio con patio, cúpula y minaretes (desierto).
- Prueba de humo: Coldstead llega a **ciudad**, con 14 edificios de piedra, 118 tramos empedrados y muralla.

## 18. Estado de la Fase 3 (Economía)

- **12 bienes** (comida, madera, piedra, hierro, herramientas, armas, armaduras, tela, cuero, libros, oro, lujos) con **stock y precio por asentamiento**: precio = base × (objetivo / stock)^0,6, entre 0,3× y 4×, suavizado.
- **Producción por oficio** (herreros, pastores, curtidores, bibliotecarios, albañiles…) + producción doméstica; **consumo por población** (más lujos a mayor nivel); guardias gastan armas y armaduras.
- **Efectos:** sin herramientas rinde menos el campo (−25 %); sin ropa ni lujos nacen menos (−40 %); las **obras gastan madera y piedra** y lo que falta se **importa más caro** desde el tesoro.
- **Caravanas** (una vez por día, abstractas): cada bien viaja del pueblo donde es barato al que lo necesita si la diferencia paga el viaje (hasta 1500 bloques).
- **El jugador comercia a precio local:** las ofertas de cada humano se reajustan al abrir el comercio; lo que comprás **sale del stock** (si no hay, agotado) y tu dinero **entra al tesoro**; lo que vendés entra al stock y **se paga con monedas del tesoro** (si no alcanza, no te compran). El **mercader** compra y vende al por mayor (trigo/pan, troncos, piedra, hierro, lana, cuero, libros, oro) a precio del día.
- `/bloodmoon village` muestra stock y precio de cada bien (▲ caro, ▼ barato) y los tratos con jugadores.

Pendiente honesto: las caravanas no se ven todavía; no hay ceca ni inflación (las monedas aún no se acuñan con respaldo); el comercio de cada humano no cambia según tu reputación (Fase 4).

## 19. Puertos pesqueros

- Si hay mar, río o lago a menos de ~140 bloques de la plaza (los charcos se saltan), el asentamiento levanta un **puerto**: muelles de madera sobre pilotes (piedra en la ciudad) que entran al agua, con barandas y faroles, una **casilla de pescadores** en la orilla y un camino hasta las calles.
- **Crece con el asentamiento mientras el agua lo permita:** aldea 1 muelle; pueblo 2 con **espigones y botes amarrados**; ciudad 3 y un **faro**; capital 4. La cantidad máxima depende del tamaño del cuerpo de agua y el largo de cada muelle de cuánta agua libre queda adelante (nunca cierra un río). Cada muelle cuesta 250 de cobre.
- **Pescadores:** cada muelle trae dos que pescan desde la punta (miran el agua, tiran la línea, salpica, a veces sacan algo) y suman 6 de comida por día al pueblo. El oficio de pescador ya existía (comercia lo mismo que el pescador vanilla); ahora además trabaja en el puerto.
- La búsqueda de agua corre en segundo plano (no traba el servidor). Prueba de humo: Coldstead, junto al mar, levanta 4 muelles y faro.

## 20. Arquitectura rehecha (calidad de construcción)

Referencias: guías de casas y castillos medievales de la comunidad de building (entramado con voladizo, techos de dos aguas cruzados, base de piedra, chimeneas, contraventanas; castillos con muralla gruesa, torres poligonales, matacanes, torre del homenaje, patio de armas) y arquitectura de adobe del norte de África (terrazas escalonadas, vigas salientes, celosías, cúpulas de cobre oxidado, minaretes).

- **Generadores nuevos** (kit de casa con plantas, roles de habitación y amueblado): cada habitación tiene uso — sala con mesa, sillas y hogar; dormitorio con cama, cofre y alfombra; tienda/taller con su estación de oficio; escaleras reales entre pisos.
- **Llanura:** 4 tipos de casa grande (dos pisos con voladizo, L con techo cruzado, casa larga a cuatro aguas, ala trasera) y pequeñas; talleres con fragua abierta (herrerías), corral (pastor), pajar (granjero), biblioteca de 3 plantas; iglesia con nave y campanario; torre de guardia; granjas con cerco, portón, espantapájaros y canales de agua contenidos; pozo y fuente con pileta contenida.
- **Ciudad:** casas de 3-4 plantas de colores, mercado cubierto, ayuntamiento con torre, **castillo 51×51** (muralla de 3 de espesor y 9 de alto, torres octogonales con chapiteles, barbacana con rastrillo, torre del homenaje de 4 plantas con salón del trono, cuarteles, establo, pozo).
- **Desierto:** casas de adobe con terrazas, vigas, ventanas en arco con celosía, toldos, cúpulas; templo con cúpula y minarete; palacio con patio, alberca y minaretes; zoco; torre.
- **Plaza amplia:** radio 11,5 (12,5 en el desierto), la fuente/pozo en el centro y lugar para circular alrededor.
- **Lotes separados:** 4 bloques mínimos entre edificios.
- **Muralla:** a 20 bloques de todo lo construido, nunca atraviesa un edificio (los lotes nuevos tampoco la pisan: franja de 4 bloques). Puertas con arco donde cruza una calle y torres a ambos lados; si más de 8 edificios quedan fuera, se traza **otro anillo por fuera** (+24 bloques mínimo). Calles nuevas que cruzan una muralla ya hecha abren su puerta.
- **Muros de contención** de piedra (arenisca en el desierto) en el borde de las terrazas en vez de barrancos de tierra.
- Humanos: la boca nunca queda tapada por la barba o el borde de la cara.

## 21. Correcciones de generación, muralla y ejército (tras la prueba en partida)

Lo que se vio jugando y cómo quedó:

- **Edificios transitables** (generador `tools/templategen`, validado por `validate.py` en las 97 plantillas: cero pisos inalcanzables, cero pasos de 1 de alto, cero escaleras de mano sin apoyo):
  - Escaleras de un tramo recto por piso, de 2 de ancho cuando entra, con 3 bloques libres sobre cada escalón (antes el primer escalón tenía el techo encima y no se podía subir), celda libre al pie y a la llegada, pasillo sin muebles desde la puerta y barandas que nunca encierran parte del piso. Se elige el lugar que no parte ningún piso en dos.
  - Muebles: uno cada tres celdas de muro; la mesa con sillas solo en salas amplias; si un mueble corta el paso, se saca.
  - Casas simétricas respecto de la puerta (la casa en L pasó a T; el ala trasera, centrada); las chicas, de 9×9.
  - Chimeneas de conducto hueco desde el hogar hasta arriba; la fogata de arriba va dentro del conducto, dos bloques bajo el remate (sale el humo, no se ve el fuego). En casas angostas la chimenea va contra el muro del fondo.
  - **Sin iglesia**: el clérigo trabaja en una botica (taller chico con el soporte para pociones) y el bibliotecario en una **biblioteca amplia** de doble altura con galería en U, escalinata central, estanterías, mesas de lectura y araña (en el desierto, con cúpula abierta al salón).
  - Torres de vigía, ayuntamiento, mercado, castillo y palacio: escaleras de mano con apoyo, puertas a las torres del castillo, cúpulas sin huecos cerrados.
- **Terreno**:
  - El núcleo de la aldea (64 bloques desde la plaza) se aplana a la altura de la plaza, con transición suave hasta el terreno natural; se despejan los árboles del centro.
  - Bajo calles, plaza y patios se sellan los huecos de cuevas y minas hasta 8 bloques de profundidad (en partida, solo cuevas naturales).
  - La lava cerca de edificios y calles se convierte en piedra y se apaga el fuego; nunca más puentes de madera sobre lava.
  - Los sitios de aldea exigen terreno parejo y seco hasta ~72 bloques (lugar para crecer).
- **Calles**: principales de 5 de ancho, secundarias de 3; cada edificio tiene un acceso de 3 de ancho hasta su calle, sin faroles encima.
- **Muralla**:
  - Perfil de base planificado y suavizado (pendiente de a lo sumo 1 cada 2 bloques): el borde de arriba ya no copia cada loma ni hace picos en las uniones de tramos.
  - Adarve caminable con medias losas donde sube, sin huecos; sobre el agua queda un arco.
  - Faroles sobre las almenas cada 6 bloques y antorchas en la cara de adentro.
  - Torres de 7×7 transitables: puerta del lado de la ciudad, escalera de piedra en caracol hasta el adarve (con salidas a los dos lados), escalera de mano a la terraza, faroles.
  - Portones de 5 de alto donde cruza una calle, con torres a los lados, y portones garantizados en los cuatro puntos cardinales con su calle hacia adentro y un trecho hacia afuera.
- **Ejército** (nuevo, `human/Army.java`):
  - Soldados buscados: aldea 3, pueblo 6, ciudad 12, capital 20 (+4 con muralla), con reclutas y sueldos que salen del tesoro (el tesoro ya no crece sin destino).
  - Etapas de equipo: cuero y piedra → cuero completo, hierro y escudo → cota de malla → hierro completo con espada afilada; cada mejora cuesta tesoro y gasta armas y armaduras del mercado.
  - Con un jugador cerca: hasta 16 soldados en pie, repuestos si mueren, con puesto (torres, ayuntamiento, plaza) o ronda por las calles y por el adarve.
  - Guardias: ven hostiles a 64 bloques (32 sin verlos) y acuden a 48 bloques si alguien lastima a un humano.
  - Torres de guardia: 2 en un pueblo, 4 en una ciudad, 6 en una capital. Las ciudades siguen abriendo calles más lejos según su nivel.
- **Puertos**: se busca agua hasta el borde del área de influencia; si la ciudad crece y llega a un lago o al mar, se vuelve a buscar y nace el puerto.
- **Mapa**: con el cursor sobre un territorio se ven nombre, nivel, habitantes y soldados.
- **Mundo**: no se generan puestos de saqueadores ni mansiones.

Pendiente: tótems (hoy solo los dan los evocadores, que no se convierten) y un reemplazo de "Héroe de la aldea". Los cambios de generación valen para chunks nuevos: para verlos conviene un mundo nuevo.
