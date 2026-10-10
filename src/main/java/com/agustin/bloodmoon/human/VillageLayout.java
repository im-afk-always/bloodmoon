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
 * Plano de una aldea, determinista a partir del sitio: un pozo en la plaza, 3-4 calles que salen de ella y, a los
 * costados, puestos del mercado, talleres de oficios, casas, granjas y una torre de guardia, todos con la puerta hacia
 * la calle. La altura de cada edificio se toma del terreno generado (antes de árboles), así que todos los chunks
 * coinciden aunque se construyan por separado.
 */
public final class VillageLayout {
    public static final int RADIUS = 80;

    public enum Kind { WELL, STALL, WORK, HOUSE, FARM, TOWER }

    /** Plantilla con su huella por columna (para despejar y cimentar). */
    public record VTemplate(DominionTemplates.Template t, int minX, int maxX, int minZ, int maxZ, Map<Long, int[]> columns) {}

    public record Building(String template, Kind kind, HumanJob job, int residents, int x, int z, Rotation rot, int floorY,
                           int minX, int minZ, int maxX, int maxZ, int coreX, int coreZ) {
        public boolean contains(int wx, int wz, int margin) {
            return wx >= minX - margin && wx <= maxX + margin && wz >= minZ - margin && wz <= maxZ + margin;
        }
    }

    public record Road(double x0, double z0, double x1, double z1, double half) {
        public double dist(double px, double pz) {
            double dx = x1 - x0, dz = z1 - z0;
            double l2 = dx * dx + dz * dz;
            double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * dx + (pz - z0) * dz) / l2));
            double qx = x0 + t * dx - px, qz = z0 + t * dz - pz;
            return Math.sqrt(qx * qx + qz * qz);
        }
    }

    public record Layout(VillageSites.Site site, List<Building> buildings, List<Road> roads, List<int[]> lamps, String stats,
                         double[][] dirs, int[] lens) {}

    /** Hasta dónde puede estirarse una calle cuando la aldea crece. */
    public static final int MAX_LEN = 120;

    /** Contadores de rechazos (solo para diagnóstico). */
    private static final int[] REJ = new int[4];

    private static final Map<Long, Layout> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, VTemplate> TEMPLATES = new ConcurrentHashMap<>();

    private VillageLayout() {}

    public static void clear() {
        CACHE.clear();
        TEMPLATES.clear();
    }

    public static VTemplate template(ServerLevel level, String name) {
        return TEMPLATES.computeIfAbsent(name, n -> {
            DominionTemplates.Template t = DominionTemplates.loadPath(level, "human/" + n);
            int minX = 0, maxX = 0, minZ = 0, maxZ = 0;
            Map<Long, int[]> cols = new HashMap<>();
            for (DominionTemplates.Entry e : t.blocks()) {
                minX = Math.min(minX, e.x()); maxX = Math.max(maxX, e.x());
                minZ = Math.min(minZ, e.z()); maxZ = Math.max(maxZ, e.z());
                long k = (long) e.x() << 32 | (e.z() & 0xFFFFFFFFL);
                int[] mm = cols.computeIfAbsent(k, kk -> new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE});
                mm[0] = Math.min(mm[0], e.y());
                mm[1] = Math.max(mm[1], e.y());
            }
            return new VTemplate(t, minX, maxX, minZ, maxZ, cols);
        });
    }

    public static Layout get(ServerLevel level, VillageSites.Site site) {
        return CACHE.computeIfAbsent(site.seed() ^ ((long) site.x() << 32) ^ site.z(), k -> build(level, site));
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

        Building well = place(level, gen, rs, c + "well", Kind.WELL, HumanJob.NONE, 0, site.x(), site.z(), Rotation.NONE, out, roads, true);
        if (well != null) out.add(well);

        int arms = 3 + rng.nextInt(2);
        double a0 = rng.nextDouble() * Math.PI * 2;
        double[][] dirs = new double[arms][];
        int[] lens = new int[arms];
        for (int i = 0; i < arms; i++) {
            double a = a0 + i * Math.PI * 2 / arms + (rng.nextDouble() - 0.5) * 0.45;
            dirs[i] = new double[]{Math.cos(a), Math.sin(a)};
            lens[i] = 46 + rng.nextInt(22);
            roads.add(new Road(site.x() + dirs[i][0] * 4, site.z() + dirs[i][1] * 4,
                    site.x() + dirs[i][0] * lens[i], site.z() + dirs[i][1] * lens[i], 1.5));
        }
        // plaza alrededor del pozo
        roads.add(new Road(site.x(), site.z(), site.x(), site.z(), 5.5));

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
            String id = jobs.get(i).vanilla;
            works.add(new String[]{c + "work_" + id, "WORK", jobs.get(i).name(), "1"});
        }
        int houses = 4 + rng.nextInt(4);
        List<String[]> homes = new ArrayList<>();
        int pals = site.culture() == Culture.DESERT ? 3 : 4;
        for (int i = 0; i < houses; i++) {
            boolean large = rng.nextInt(3) == 0;
            homes.add(new String[]{c + (large ? "house_large_" : "house_small_") + rng.nextInt(pals), "HOUSE", "NONE", large ? "2" : "1"});
        }
        // talleres y casas intercalados
        int wi = 0, hi = 0;
        while (wi < works.size() || hi < homes.size()) {
            if (wi < works.size()) queue.add(works.get(wi++));
            if (hi < homes.size() && (rng.nextBoolean() || wi >= works.size())) queue.add(homes.get(hi++));
        }
        queue.add(new String[]{c + "tower", "TOWER", "GUARD", "2"});
        int farms = 2 + rng.nextInt(2);
        for (int i = 0; i < farms; i++) queue.add(new String[]{c + "farm_" + rng.nextInt(2), "FARM", "FARMER", i == 0 ? "1" : "0"});

        int[] rejects = new int[1];
        for (String[] want : queue) {
            Plot pl = findPlot(level, site, dirs, lens, 3, want[0], Kind.valueOf(want[1]), HumanJob.valueOf(want[2]),
                    Integer.parseInt(want[3]), out, roads, null);
            if (pl == null) {
                rejects[0]++;
                continue;
            }
            out.add(pl.building());
            roads.add(pl.spur());
        }
        // faroles a lo largo de las calles
        for (int i = 0; i < arms; i++) {
            for (int d = 8; d < lens[i]; d += 13) {
                double side = (d / 13) % 2 == 0 ? 1 : -1;
                int lx = (int) Math.round(site.x() + dirs[i][0] * d - dirs[i][1] * side * 2.6);
                int lz = (int) Math.round(site.z() + dirs[i][1] * d + dirs[i][0] * side * 2.6);
                boolean clash = false;
                for (Building b : out) if (b.contains(lx, lz, 1)) clash = true;
                if (!clash) lamps.add(new int[]{lx, lz});
            }
        }
        String stats = "queued=" + queue.size() + " placed=" + (out.size() - 1) + " tries=" + rejects[0]
                + " wet=" + REJ[0] + " slope=" + REJ[1] + " overlap=" + REJ[2] + " road=" + REJ[3];
        return new Layout(site, out, roads, lamps, stats, dirs, lens);
    }

    public record Plot(Building building, Road spur, int arm, double d) {}

    /**
     * Primer lote libre (de la plaza hacia afuera) donde entra la plantilla, con la puerta hacia la calle.
     * {@code limits[arm] - margin} es hasta dónde se busca en cada calle; {@code accept} puede vetar un lote (p. ej. si
     * el jugador construyó ahí).
     */
    public static Plot findPlot(ServerLevel level, VillageSites.Site site, double[][] dirs, int[] limits, int margin, String template,
                                Kind kind, HumanJob job, int residents, List<Building> others, List<Road> roads,
                                java.util.function.Predicate<Building> accept) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        VTemplate vt = template(level, template);
        if (vt.t().blocks().isEmpty()) return null;
        List<double[]> plots = new ArrayList<>();   // {d, arm, side}
        for (int i = 0; i < dirs.length; i++) {
            for (int d = 9; d < limits[i] - margin; d += 2) {
                plots.add(new double[]{d, i, -1});
                plots.add(new double[]{d, i, 1});
            }
        }
        plots.sort((p, q) -> Double.compare(p[0] + p[1] * 0.01 + p[2] * 0.001, q[0] + q[1] * 0.01 + q[2] * 0.001));
        for (double[] p : plots) {
            int arm = (int) p[1];
            double side = p[2];
            double rx = site.x() + dirs[arm][0] * p[0], rz = site.z() + dirs[arm][1] * p[0];
            double px = -dirs[arm][1] * side, pz = dirs[arm][0] * side;   // perpendicular
            // orientación: la puerta (+z de la plantilla) mira hacia la calle
            Rotation rot = DominionTemplates.facing(0, 0, (int) Math.round(-px * 100), (int) Math.round(-pz * 100));
            // distancia mínima para que ninguna esquina pise la calle
            double minProj = Double.MAX_VALUE;
            for (int cxz = 0; cxz < 4; cxz++) {
                int[] cr = DominionTemplates.rotate((cxz & 1) == 0 ? vt.minX() : vt.maxX(), (cxz & 2) == 0 ? vt.minZ() : vt.maxZ(), rot);
                minProj = Math.min(minProj, cr[0] * px + cr[1] * pz);
            }
            double off = Math.max(2.5 + vt.t().coreZ(), 2.6 - minProj);
            int bx = (int) Math.round(rx + px * off), bz = (int) Math.round(rz + pz * off);
            Building b = place(level, gen, rs, template, kind, job, residents, bx, bz, rot, others, roads, false);
            if (b == null) continue;
            if (accept != null && !accept.test(b)) continue;
            return new Plot(b, new Road(b.coreX(), b.coreZ(), rx, rz, 0.6), arm, p[0]);
        }
        return null;
    }

    /** Intenta ubicar un edificio: sin pisar otros ni las calles, en seco y sin pendiente excesiva. */
    private static Building place(ServerLevel level, ChunkGenerator gen, RandomState rs, String name, Kind kind, HumanJob job,
                                  int residents, int bx, int bz, Rotation rot, List<Building> others, List<Road> roads,
                                  boolean ignoreRoads) {
        VTemplate vt = template(level, name);
        if (vt.t().blocks().isEmpty()) return null;
        int[] a = DominionTemplates.rotate(vt.minX(), vt.minZ(), rot);
        int[] b = DominionTemplates.rotate(vt.maxX(), vt.maxZ(), rot);
        int minX = bx + Math.min(a[0], b[0]), maxX = bx + Math.max(a[0], b[0]);
        int minZ = bz + Math.min(a[1], b[1]), maxZ = bz + Math.max(a[1], b[1]);
        for (Building o : others) {
            if (minX <= o.maxX() + 2 && maxX >= o.minX() - 2 && minZ <= o.maxZ() + 2 && maxZ >= o.minZ() - 2) {
                REJ[2]++;
                return null;
            }
        }
        if (!ignoreRoads) {
            for (Road r : roads) {
                if (r.half() < 1.0) continue;
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if ((x == minX || x == maxX || z == minZ || z == maxZ || (x + z) % 3 == 0) && r.dist(x, z) < r.half() + 0.4) {
                            REJ[3]++;
                            return null;
                        }
                    }
                }
            }
        }
        int[] hs = new int[9];
        int i = 0;
        boolean wet = false;
        for (int sx : new int[]{minX, bx, maxX}) {
            for (int sz : new int[]{minZ, bz, maxZ}) {
                int surf = gen.getBaseHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
                int floor = gen.getBaseHeight(sx, sz, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
                if (floor < surf) wet = true;
                hs[i++] = surf;
            }
        }
        if (wet && kind != Kind.WELL) {
            REJ[0]++;
            return null;
        }
        int[] sorted = hs.clone();
        Arrays.sort(sorted);
        if (kind != Kind.WELL && sorted[8] - sorted[0] > 8) {
            REJ[1]++;
            return null;
        }
        int floorY = sorted[4] - 1;
        int[] core = DominionTemplates.rotate(vt.t().coreX(), vt.t().coreZ(), rot);
        return new Building(name, kind, job, residents, bx, bz, rot, floorY, minX, minZ, maxX, maxZ, bx + core[0], bz + core[1]);
    }
}
