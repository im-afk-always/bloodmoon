package com.agustin.bloodmoon.human;

import com.agustin.bloodmoon.invasion.DominionTemplates;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plano de una aldea, determinista a partir del sitio: un pozo en la plaza, una red de calles con forma propia
 * ({@link StreetPlanner}: orgánica, pueblo-calle, cruce de caminos o trama de manzanas) que sigue el terreno y, a los
 * costados, puestos del mercado, talleres de oficios, casas, granjas y una torre de guardia, todos con la puerta hacia
 * la calle. Las alturas salen del terreno generado (antes de árboles), así que todos los chunks coinciden aunque se
 * construyan por separado. La red ya trae las calles futuras: el asentamiento las pavimenta a medida que crece.
 */
public final class VillageLayout {
    public static final int RADIUS = 130;

    /** Radio de la plaza: amplia, con la fuente o el pozo en el medio y lugar para pasar alrededor. */
    public static final double PLAZA = 11.5, PLAZA_DESERT = 12.5;
    /** Separación mínima entre edificios (hay lugar de sobra: que no se amontonen). */
    public static final int GAP = 4;
    /** Medio ancho del acceso de cada edificio a su calle (1.0 = 3 bloques). */
    public static final double SPUR_HALF = 1.0;

    public enum Kind { WELL, STALL, WORK, HOUSE, FARM, TOWER, HALL, MARKET, CASTLE }

    /** Plantilla con su huella por columna (para despejar y cimentar). */
    public record VTemplate(DominionTemplates.Template t, int minX, int maxX, int minZ, int maxZ, Map<Long, int[]> columns,
                            java.util.Set<Long> ground) {
        /** ¿La columna (en coordenadas de la plantilla) tiene algo sólido a la altura de la calle? Los voladizos no cuentan. */
        public boolean onGround(int tx, int tz) {
            return ground.contains((long) tx << 32 | (tz & 0xFFFFFFFFL));
        }
    }

    /** Pasa de coordenadas del mundo a las de la plantilla del edificio. */
    public static int[] local(Building b, int wx, int wz) {
        Rotation inv = switch (b.rot()) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> b.rot();
        };
        return DominionTemplates.rotate(wx - b.x(), wz - b.z(), inv);
    }

    /** ¿El edificio ocupa el suelo en esa columna? (bajo un alero o un voladizo se puede pasar y pavimentar) */
    public static boolean footprint(ServerLevel level, Building b, int wx, int wz) {
        if (!b.contains(wx, wz, 0)) return false;
        int[] l = local(b, wx, wz);
        return template(level, b.template()).onGround(l[0], l[1]);
    }

    public record Building(String template, Kind kind, HumanJob job, int residents, int x, int z, Rotation rot, int floorY,
                           int minX, int minZ, int maxX, int maxZ, int coreX, int coreZ) {
        public boolean contains(int wx, int wz, int margin) {
            return wx >= minX - margin && wx <= maxX + margin && wz >= minZ - margin && wz <= maxZ + margin;
        }
    }

    /**
     * Tramo de calle. {@code y0}/{@code y1}: primer bloque libre en cada punta según el terreno generado (la calle se
     * nivela en rampa entre ambas); MIN_VALUE = seguir el suelo.
     */
    public record Road(double x0, double z0, double x1, double z1, double half, int y0, int y1) {
        public Road(double x0, double z0, double x1, double z1, double half) {
            this(x0, z0, x1, z1, half, Integer.MIN_VALUE, Integer.MIN_VALUE);
        }

        public double t(double px, double pz) {
            double dx = x1 - x0, dz = z1 - z0;
            double l2 = dx * dx + dz * dz;
            return l2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * dx + (pz - z0) * dz) / l2));
        }

        public double dist(double px, double pz) {
            double t = t(px, pz);
            double qx = x0 + t * (x1 - x0) - px, qz = z0 + t * (z1 - z0) - pz;
            return Math.sqrt(qx * qx + qz * qz);
        }

        /** Primer bloque libre de la calle a la altura de (px, pz), o MIN_VALUE si sigue el suelo. */
        public int y(double px, double pz) {
            if (y0 == Integer.MIN_VALUE) return Integer.MIN_VALUE;
            return (int) Math.round(y0 + (y1 - y0) * t(px, pz));
        }

        public double length() {
            return Math.hypot(x1 - x0, z1 - z0);
        }
    }

    /**
     * Plano completo. {@code roads}: lo que se pavimenta al generar el mundo (plaza, tramos usados y accesos).
     * {@code net}: toda la red planificada, también la futura ({@code parent}: tramo del que sale; {@code dist}: distancia
     * por la red). {@code pavedInit}: tramos pavimentados de entrada. {@code buildingSeg}/{@code spurs}: tramo y acceso de
     * cada edificio original (en el mismo orden que {@code buildings}).
     */
    public record Layout(VillageSites.Site site, List<Building> buildings, List<Road> roads, List<int[]> lamps, String stats,
                         StreetPlanner.Pattern pattern, List<Road> net, int[] parent, double[] dist, boolean[] pavedInit,
                         int[] buildingSeg, List<Road> spurs) {
        public Road plaza() {
            return roads.get(0);
        }
    }

    /** Hasta dónde (distancia por la red) puede crecer un asentamiento. */
    public static final int MAX_DIST = 170;

    /** Radio del núcleo aplanado a la altura de la plaza, y ancho de la transición hasta el terreno natural. */
    public static final double FLAT_R = 64, FLAT_BLEND = 28;

    /** Altura (primer bloque libre) de la calle en un punto: la de la plaza en el núcleo, mezclada con el terreno afuera. */
    static int flat(double x, double z, VillageSites.Site site, int cy, int natural) {
        if (natural == Integer.MIN_VALUE) return natural;
        double d = Math.hypot(x - site.x(), z - site.z());
        double f = Math.max(0, Math.min(1, (d - FLAT_R) / FLAT_BLEND));
        return (int) Math.round(cy + (natural - cy) * f);
    }

    /** ¿El punto está en el núcleo aplanado? (ahí los constructores cortan y rellenan más para dejar todo parejo) */
    static boolean core(VillageSites.Site site, double x, double z) {
        return Math.hypot(x - site.x(), z - site.z()) <= FLAT_R + FLAT_BLEND / 2;
    }

    /** Contadores de rechazos (solo para diagnóstico). */
    public static final int[] REJ = new int[6];

    private static final Map<Long, Layout> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, VTemplate> TEMPLATES = new ConcurrentHashMap<>();

    private VillageLayout() {}

    public static void clear() {
        CACHE.clear();
        TEMPLATES.clear();
        TERRAIN.clear();
        COLUMNS.clear();
    }

    public static VTemplate template(ServerLevel level, String name) {
        return TEMPLATES.computeIfAbsent(name, n -> {
            DominionTemplates.Template t = DominionTemplates.loadPath(level, "human/" + n);
            int minX = 0, maxX = 0, minZ = 0, maxZ = 0;
            Map<Long, int[]> cols = new HashMap<>();
            java.util.Set<Long> ground = new java.util.HashSet<>();
            for (DominionTemplates.Entry e : t.blocks()) {
                if (e.y() <= 2 && !e.state().isAir()) ground.add((long) e.x() << 32 | (e.z() & 0xFFFFFFFFL));
                minX = Math.min(minX, e.x()); maxX = Math.max(maxX, e.x());
                minZ = Math.min(minZ, e.z()); maxZ = Math.max(maxZ, e.z());
                long k = (long) e.x() << 32 | (e.z() & 0xFFFFFFFFL);
                int[] mm = cols.computeIfAbsent(k, kk -> new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE});
                mm[0] = Math.min(mm[0], e.y());
                mm[1] = Math.max(mm[1], e.y());
            }
            return new VTemplate(t, minX, maxX, minZ, maxZ, cols, ground);
        });
    }

    public static Layout get(ServerLevel level, VillageSites.Site site) {
        return CACHE.computeIfAbsent(site.seed() ^ ((long) site.x() << 32) ^ site.z(), k -> build(level, site));
    }

    /** Terreno generado (sin árboles) como lo ve el trazador de calles. */
    static StreetPlanner.Terrain terrain(ServerLevel level) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        int sea = level.getSeaLevel();
        return new StreetPlanner.Terrain() {
            @Override
            public int height(int x, int z) {
                return (int) (column(gen, level, rs, x, z) >> 32);
            }

            @Override
            public boolean wet(int x, int z) {
                long c = column(gen, level, rs, x, z);
                int surf = (int) (c >> 32), floor = (int) c;
                return floor < surf || surf <= sea;
            }
        };
    }

    private static Layout build(ServerLevel level, VillageSites.Site site) {
        Random rng = new Random(site.seed());
        java.util.Arrays.fill(REJ, 0);
        String c = site.culture() == Culture.DESERT ? "desert/" : "plains/";
        List<Building> out = new ArrayList<>();
        List<Road> roads = new ArrayList<>();
        List<int[]> lamps = new ArrayList<>();
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        StreetPlanner.Terrain terrain = terrain(level);

        // red de calles según el patrón de esta aldea
        StreetPlanner.Pattern pattern = StreetPlanner.choose(site.seed(), site.culture() == Culture.DESERT);
        List<StreetPlanner.Seg> segs = StreetPlanner.plan(site.seed(), site.x(), site.z(), pattern, terrain, MAX_DIST - 40);
        int cy = terrain.height(site.x(), site.z());
        List<Road> net = new ArrayList<>();
        int[] parent = new int[segs.size()];
        double[] dist = new double[segs.size()];
        for (int i = 0; i < segs.size(); i++) {
            StreetPlanner.Seg g = segs.get(i);
            // el núcleo de la aldea queda a un solo nivel (el de la plaza); afuera, las calles vuelven a seguir el terreno
            int y0 = flat(g.x0(), g.z0(), site, cy, g.y0()), y1 = flat(g.x1(), g.z1(), site, cy, g.y1());
            net.add(new Road(g.x0(), g.z0(), g.x1(), g.z1(), g.half(), y0, y1));
            parent[i] = g.parent();
            dist[i] = g.dist();
        }
        Road plaza = new Road(site.x(), site.z(), site.x(), site.z(), site.culture() == Culture.DESERT ? PLAZA_DESERT : PLAZA, cy, cy);
        roads.add(plaza);

        Building well = place(level, gen, rs, c + "well", Kind.WELL, HumanJob.NONE, 0, site.x(), site.z(), Rotation.NONE, out, roads, true);
        if (well != null) {
            // el pozo, a nivel de la plaza
            well = new Building(well.template(), well.kind(), well.job(), well.residents(), well.x(), well.z(), well.rot(), cy - 1,
                    well.minX(), well.minZ(), well.maxX(), well.maxZ(), well.coreX(), well.coreZ());
            out.add(well);
        }

        // qué se construye, en orden de cercanía a la plaza
        List<String[]> queue = new ArrayList<>();   // {template, kind, job, residents}
        int stalls = 2 + rng.nextInt(2);
        for (int i = 0; i < stalls; i++) queue.add(new String[]{c + "stall_" + rng.nextInt(2), "STALL", "MERCHANT", "1"});
        List<HumanJob> jobs = new ArrayList<>();
        for (HumanJob j : HumanJob.values()) if (j.vanilla != null && j != HumanJob.FARMER) jobs.add(j);
        java.util.Collections.shuffle(jobs, rng);
        int nJobs = 5 + rng.nextInt(4);
        List<String[]> works = new ArrayList<>();
        works.add(new String[]{c + "work_farmer", "WORK", "FARMER", "1"});
        for (int i = 0; i < nJobs && i < jobs.size(); i++) {
            works.add(new String[]{c + "work_" + jobs.get(i).vanilla, "WORK", jobs.get(i).name(), "1"});
        }
        int houses = 4 + rng.nextInt(4);
        List<String[]> homes = new ArrayList<>();
        int pals = site.culture() == Culture.DESERT ? 3 : 4;
        for (int i = 0; i < houses; i++) {
            boolean large = rng.nextInt(3) == 0;
            homes.add(new String[]{c + (large ? "house_large_" : "house_small_") + rng.nextInt(pals), "HOUSE", "NONE", large ? "2" : "1"});
        }
        int wi = 0, hi = 0;
        while (wi < works.size() || hi < homes.size()) {
            if (wi < works.size()) queue.add(works.get(wi++));
            if (hi < homes.size() && (rng.nextBoolean() || wi >= works.size())) queue.add(homes.get(hi++));
        }
        int farms = 2 + rng.nextInt(2);
        for (int i = 0; i < farms; i++) queue.add(new String[]{c + "farm_" + rng.nextInt(2), "FARM", "FARMER", i == 0 ? "1" : "0"});
        queue.add(new String[]{c + "tower", "TOWER", "GUARD", "2"});   // la torre de guardia, en el borde

        List<Road> blocking = new ArrayList<>(net);
        blocking.add(plaza);
        int[] rejects = new int[1];
        List<Integer> bSeg = new ArrayList<>();
        List<Road> spurs = new ArrayList<>();
        bSeg.add(-1);
        spurs.add(null);
        boolean[] paved = new boolean[net.size()];
        for (String[] want : queue) {
            Kind wk = Kind.valueOf(want[1]);
            Plot pl = findPlot(level, site, net, dist, MAX_DIST - 60, want[0], wk, HumanJob.valueOf(want[2]),
                    Integer.parseInt(want[3]), out, blocking, wk == Kind.TOWER ? periphery(site.x(), site.z(), out) : null, false);
            if (pl == null) {
                rejects[0]++;
                continue;
            }
            out.add(pl.building());
            bSeg.add(pl.seg());
            spurs.add(pl.spur());
            for (int k = pl.seg(); k >= 0 && !paved[k]; k = parent[k]) paved[k] = true;
        }
        // el centro siempre tiene calles aunque no tengan casas todavía
        for (int i = 0; i < net.size(); i++) {
            if (dist[i] <= 24) for (int k = i; k >= 0 && !paved[k]; k = parent[k]) paved[k] = true;
        }
        for (int i = 0; i < net.size(); i++) if (paved[i]) roads.add(net.get(i));
        for (Road sp : spurs) if (sp != null) roads.add(sp);
        lamps.addAll(lamps(net, paved, out, 0));
        String stats = pattern + " streets=" + net.size() + " queued=" + queue.size() + " placed=" + (out.size() - 1) + " miss=" + rejects[0]
                + " wet=" + REJ[0] + " slope=" + REJ[1] + " overlap=" + REJ[2] + " road=" + REJ[3];
        int[] bs = new int[bSeg.size()];
        for (int i = 0; i < bs.length; i++) bs[i] = bSeg.get(i);
        return new Layout(site, out, roads, lamps, stats, pattern, net, parent, dist, paved, bs, spurs);
    }

    /**
     * Lotes de la periferia: a por lo menos el 85 % del radio de lo construido y a 40 bloques de otras torres (las
     * torres de guardia vigilan desde el borde, no desde el medio del pueblo).
     */
    public static java.util.function.Predicate<Building> periphery(int cx, int cz, List<Building> built) {
        double r = 0;
        List<Building> towers = new ArrayList<>();
        for (Building b : built) {
            r = Math.max(r, Math.hypot(b.x() - cx, b.z() - cz));
            if (b.kind() == Kind.TOWER) towers.add(b);
        }
        double min = r * 0.85;
        return b -> {
            if (Math.hypot(b.x() - cx, b.z() - cz) < min) return false;
            for (Building t : towers) if (Math.hypot(b.x() - t.x(), b.z() - t.z()) < 40) return false;
            return true;
        };
    }

    /** Faroles cada ~12 bloques a lo largo de los tramos marcados, alternando de lado; nunca dentro de un edificio. */
    public static List<int[]> lamps(List<Road> net, boolean[] which, List<Building> buildings, int salt) {
        return lamps(net, which, buildings, salt, 12, false);
    }

    /** {@code step}: cada cuántos bloques; {@code both}: a los dos lados (calles de ciudad). */
    public static List<int[]> lamps(List<Road> net, boolean[] which, List<Building> buildings, int salt, int step, boolean both) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < net.size(); i++) {
            if (!which[i]) continue;
            Road r = net.get(i);
            double len = r.length();
            if (len < 1) continue;
            double dx = (r.x1() - r.x0()) / len, dz = (r.z1() - r.z0()) / len;
            for (double t = 3 + ((i + salt) % 3) * (step / 3); t < len; t += step) {
                for (int sd = 0; sd < (both ? 2 : 1); sd++) {
                    double side = both ? (sd == 0 ? 1 : -1) : (((int) (t / step) + i) % 2 == 0 ? 1 : -1);
                    double off = r.half() + 1.2;
                    int lx = (int) Math.round(r.x0() + dx * t - dz * side * off);
                    int lz = (int) Math.round(r.z0() + dz * t + dx * side * off);
                    boolean clash = false;
                    for (Building b : buildings) {
                        if (b.contains(lx, lz, 1) || onAccess(b, net, lx, lz)) {
                            clash = true;
                            break;
                        }
                    }
                    for (int k = 0; k < net.size() && !clash; k++) if (net.get(k).dist(lx, lz) <= net.get(k).half() + 0.3) clash = true;
                    if (!clash) out.add(new int[]{lx, lz});
                }
            }
        }
        return out;
    }

    /** ¿El punto cae sobre el acceso de un edificio (de su puerta a la calle más cercana)? Ahí no van faroles. */
    static boolean onAccess(Building b, List<Road> net, int x, int z) {
        if (Math.abs(x - b.coreX()) > 12 || Math.abs(z - b.coreZ()) > 12) return false;
        Road best = null;
        double bd = Double.MAX_VALUE;
        for (Road r : net) {
            double d = r.dist(b.coreX(), b.coreZ());
            if (d < bd) { bd = d; best = r; }
        }
        if (best == null || bd > 12) return false;
        double t = best.t(b.coreX(), b.coreZ());
        Road access = new Road(b.coreX(), b.coreZ(), best.x0() + (best.x1() - best.x0()) * t, best.z0() + (best.z1() - best.z0()) * t, SPUR_HALF);
        return access.dist(x, z) <= SPUR_HALF + 0.8;
    }

    public record Plot(Building building, Road spur, int seg, double t) {}

    /**
     * Primer lote libre (por distancia en la red desde la plaza) donde entra la plantilla, al costado de algún tramo y con
     * la puerta hacia él. {@code maxDist}: hasta dónde se busca. {@code accept} puede vetar un lote (p. ej. si el jugador
     * construyó ahí). {@code coarse}: terreno en grilla cacheada (búsquedas en partida).
     */
    public static Plot findPlot(ServerLevel level, VillageSites.Site site, List<Road> net, double[] dist, double maxDist, String template,
                                Kind kind, HumanJob job, int residents, List<Building> others, List<Road> blocking,
                                java.util.function.Predicate<Building> accept, boolean coarse) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        VTemplate vt = template(level, template);
        if (vt.t().blocks().isEmpty()) return null;
        List<double[]> plots = new ArrayList<>();   // {orden, tramo, t, lado}
        for (int i = 0; i < net.size(); i++) {
            Road r = net.get(i);
            double len = r.length();
            for (double t = 2; t < len - 0.5; t += 2) {
                if (dist[i] + t > maxDist) break;
                plots.add(new double[]{dist[i] + t, i, t, -1});
                plots.add(new double[]{dist[i] + t + 0.5, i, t, 1});
            }
        }
        plots.sort((p, q) -> Double.compare(p[0], q[0]));
        for (double[] p : plots) {
            int i = (int) p[1];
            Road r = net.get(i);
            double len = r.length();
            double dx = (r.x1() - r.x0()) / len, dz = (r.z1() - r.z0()) / len;
            double side = p[3];
            double rx = r.x0() + dx * p[2], rz = r.z0() + dz * p[2];
            double px = -dz * side, pz = dx * side;   // perpendicular
            // la puerta (+z de la plantilla) mira hacia la calle
            Rotation rot = DominionTemplates.facing(0, 0, (int) Math.round(-px * 100), (int) Math.round(-pz * 100));
            double minProj = Double.MAX_VALUE;
            for (int cxz = 0; cxz < 4; cxz++) {
                int[] cr = DominionTemplates.rotate((cxz & 1) == 0 ? vt.minX() : vt.maxX(), (cxz & 2) == 0 ? vt.minZ() : vt.maxZ(), rot);
                minProj = Math.min(minProj, cr[0] * px + cr[1] * pz);
            }
            double off = Math.max(r.half() + 1 + vt.t().coreZ(), r.half() + 1.1 - minProj);
            int bx = (int) Math.round(rx + px * off), bz = (int) Math.round(rz + pz * off);
            Building b = place(level, gen, rs, template, kind, job, residents, bx, bz, rot, others, blocking, false, coarse);
            if (b == null) continue;
            // la puerta queda a la altura de la calle: el piso se toma de la calle, no del terreno (los constructores
            // nivelan el resto); si la diferencia con el terreno es mucha, el lote no sirve
            int ry = r.y(rx, rz);
            if (ry != Integer.MIN_VALUE) {
                int floor = ry - 1;
                if (Math.abs(floor - b.floorY()) > (core(site, rx, rz) ? 9 : 5)) { REJ[4]++; continue; }
                b = new Building(b.template(), b.kind(), b.job(), b.residents(), b.x(), b.z(), b.rot(), floor, b.minX(), b.minZ(), b.maxX(),
                        b.maxZ(), b.coreX(), b.coreZ());
            }
            if (accept != null && !accept.test(b)) { REJ[5]++; continue; }
            // acceso de 3 de ancho desde la puerta hasta el eje de la calle
            return new Plot(b, new Road(b.coreX(), b.coreZ(), rx, rz, SPUR_HALF, b.floorY() + 1, b.floorY() + 1), i, p[2]);
        }
        return null;
    }

    /**
     * Ubica una plantilla con la puerta en {@code (coreX, coreZ)} y el piso a {@code floorY} (renovación urbana: el edificio
     * nuevo ocupa el lugar del viejo). Null si pisa otro edificio (dejando {@code margin}) o una calle.
     */
    public static Building placeAt(ServerLevel level, String name, Kind kind, HumanJob job, int residents, int coreX, int coreZ,
                                   Rotation rot, int floorY, List<Building> others, List<Road> blocking, int margin) {
        VTemplate vt = template(level, name);
        if (vt.t().blocks().isEmpty()) return null;
        int[] c = DominionTemplates.rotate(vt.t().coreX(), vt.t().coreZ(), rot);
        int bx = coreX - c[0], bz = coreZ - c[1];
        int[] a = DominionTemplates.rotate(vt.minX(), vt.minZ(), rot);
        int[] b = DominionTemplates.rotate(vt.maxX(), vt.maxZ(), rot);
        int minX = bx + Math.min(a[0], b[0]), maxX = bx + Math.max(a[0], b[0]);
        int minZ = bz + Math.min(a[1], b[1]), maxZ = bz + Math.max(a[1], b[1]);
        for (Building o : others) {
            if (minX <= o.maxX() + margin && maxX >= o.minX() - margin && minZ <= o.maxZ() + margin && maxZ >= o.minZ() - margin) return null;
        }
        for (Road r : blocking) {
            if (r.half() < 1.0) continue;
            double pad = r.half() + 1;
            if (maxX < Math.min(r.x0(), r.x1()) - pad || minX > Math.max(r.x0(), r.x1()) + pad
                    || maxZ < Math.min(r.z0(), r.z1()) - pad || minZ > Math.max(r.z0(), r.z1()) + pad) continue;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (r.dist(x, z) >= r.half() + 0.4) continue;
                    int[] tl = invRotate(x - bx, z - bz, rot);
                    if (vt.onGround(tl[0], tl[1])) return null;
                }
            }
        }
        return new Building(name, kind, job, residents, bx, bz, rot, floorY, minX, minZ, maxX, maxZ, coreX, coreZ);
    }

    private static int[] invRotate(int dx, int dz, Rotation rot) {
        Rotation inv = switch (rot) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> rot;
        };
        return DominionTemplates.rotate(dx, dz, inv);
    }

    private static boolean onGroundWorld(VTemplate vt, int dx, int dz, Rotation rot) {
        int[] l = invRotate(dx, dz, rot);
        return vt.onGround(l[0], l[1]);
    }

    /** Intenta ubicar un edificio: sin pisar otros ni las calles, en seco y sin pendiente excesiva. */
    private static Building place(ServerLevel level, ChunkGenerator gen, RandomState rs, String name, Kind kind, HumanJob job,
                                  int residents, int bx, int bz, Rotation rot, List<Building> others, List<Road> roads,
                                  boolean ignoreRoads) {
        return place(level, gen, rs, name, kind, job, residents, bx, bz, rot, others, roads, ignoreRoads, false);
    }

    private static Building place(ServerLevel level, ChunkGenerator gen, RandomState rs, String name, Kind kind, HumanJob job,
                                  int residents, int bx, int bz, Rotation rot, List<Building> others, List<Road> roads,
                                  boolean ignoreRoads, boolean coarse) {
        VTemplate vt = template(level, name);
        if (vt.t().blocks().isEmpty()) return null;
        int[] a = DominionTemplates.rotate(vt.minX(), vt.minZ(), rot);
        int[] b = DominionTemplates.rotate(vt.maxX(), vt.maxZ(), rot);
        int minX = bx + Math.min(a[0], b[0]), maxX = bx + Math.max(a[0], b[0]);
        int minZ = bz + Math.min(a[1], b[1]), maxZ = bz + Math.max(a[1], b[1]);
        for (Building o : others) {
            if (minX <= o.maxX() + GAP && maxX >= o.minX() - GAP && minZ <= o.maxZ() + GAP && maxZ >= o.minZ() - GAP) {
                REJ[2]++;
                return null;
            }
        }
        if (!ignoreRoads) {
            for (Road r : roads) {
                if (r.half() < 1.0) continue;
                double pad = r.half() + 1;
                if (maxX < Math.min(r.x0(), r.x1()) - pad || minX > Math.max(r.x0(), r.x1()) + pad
                        || maxZ < Math.min(r.z0(), r.z1()) - pad || minZ > Math.max(r.z0(), r.z1()) + pad) continue;
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if ((x == minX || x == maxX || z == minZ || z == maxZ || (x + z) % 3 == 0) && r.dist(x, z) < r.half() + 0.4
                                && onGroundWorld(vt, x - bx, z - bz, rot)) {
                            REJ[3]++;
                            return null;
                        }
                    }
                }
            }
        }
        long tkey = ((((((long) bx << 32) ^ (bz & 0xFFFFFFFFL)) * 31 + minX) * 31 + minZ) * 31 + maxX) * 31 + maxZ;
        tkey = tkey * 4 + (kind == Kind.WELL ? 1 : 0) + (coarse ? 2 : 0);
        Integer cachedFloor = TERRAIN.get(tkey);
        int floorY;
        if (cachedFloor != null) {
            if (cachedFloor == Integer.MIN_VALUE) return null;
            floorY = cachedFloor;
        } else {
            floorY = terrain(gen, level, rs, kind, minX, minZ, maxX, maxZ, bx, bz, coarse);
            if (TERRAIN.size() > 200_000) TERRAIN.clear();
            TERRAIN.put(tkey, floorY);
            if (floorY == Integer.MIN_VALUE) return null;
        }
        int[] core = DominionTemplates.rotate(vt.t().coreX(), vt.t().coreZ(), rot);
        return new Building(name, kind, job, residents, bx, bz, rot, floorY, minX, minZ, maxX, maxZ, bx + core[0], bz + core[1]);
    }

    /** Altura del piso según el terreno generado, o MIN_VALUE si es agua o demasiada pendiente. */
    private static int terrain(ChunkGenerator gen, ServerLevel level, RandomState rs, Kind kind, int minX, int minZ, int maxX, int maxZ,
                               int bx, int bz, boolean coarse) {
        int[] hs = new int[9];
        int i = 0;
        boolean wet = false;
        // primero el centro: si es agua, no vale la pena muestrear el resto
        int[] order = {4, 0, 2, 6, 8, 1, 3, 5, 7};
        int[] xs = {minX, bx, maxX}, zs = {minZ, bz, maxZ};
        for (int k : order) {
            int sx = xs[k / 3], sz = zs[k % 3];
            int surf, floor;
            if (coarse) {
                long c = column(gen, level, rs, Math.round(sx / 4f) * 4, Math.round(sz / 4f) * 4);
                surf = (int) (c >> 32);
                floor = (int) c;
            } else {
                surf = gen.getBaseHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
                floor = gen.getBaseHeight(sx, sz, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
            }
            if (floor < surf) {
                wet = true;
                if (kind != Kind.WELL) break;
            }
            hs[i++] = surf;
        }
        if (wet && kind != Kind.WELL) {
            REJ[0]++;
            return Integer.MIN_VALUE;
        }
        int[] sorted = hs.clone();
        Arrays.sort(sorted);
        if (kind != Kind.WELL && sorted[8] - sorted[0] > 10) {
            REJ[1]++;
            return Integer.MIN_VALUE;
        }
        return sorted[4] - 1;
    }

    private static final Map<Long, Long> COLUMNS = new ConcurrentHashMap<>();

    /** Superficie y fondo (bajo el agua) del terreno generado en una columna, cacheados. */
    private static long column(ChunkGenerator gen, ServerLevel level, RandomState rs, int x, int z) {
        long k = ((long) x << 32) | (z & 0xFFFFFFFFL);
        Long v = COLUMNS.get(k);
        if (v != null) return v;
        int surf = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
        int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
        long c = ((long) surf << 32) | (floor & 0xFFFFFFFFL);
        if (COLUMNS.size() > 400_000) COLUMNS.clear();
        COLUMNS.put(k, c);
        return c;
    }

    private static final Map<Long, Integer> TERRAIN = new ConcurrentHashMap<>();
}
