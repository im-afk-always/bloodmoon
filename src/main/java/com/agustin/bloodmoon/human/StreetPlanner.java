package com.agustin.bloodmoon.human;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Trazado de calles de un asentamiento (Java puro, sin Minecraft: se prueba aparte). Cada aldea elige un patrón y
 * sus calles siguen el terreno: avanzan en tramos cortos eligiendo el rumbo más parejo y seco, así que esquivan lomas y
 * agua y salen curvas. Las calles secundarias nacen de las principales y, a veces, de otras secundarias.
 * <ul>
 *   <li>ORGANIC: 2-4 calles principales sinuosas con ramas (el pueblo medieval típico).</li>
 *   <li>LINEAR: un camino largo que atraviesa la plaza ("pueblo-calle") con callejones cortos.</li>
 *   <li>CROSSROADS: cruce de dos caminos con pocas ramas.</li>
 *   <li>GRID: manzanas regulares alrededor de la plaza (desierto: trama compacta de medina).</li>
 * </ul>
 */
public final class StreetPlanner {
    public enum Pattern { ORGANIC, LINEAR, CROSSROADS, GRID }

    /** Terreno consultado: altura de la superficie (primer bloque libre) y si hay agua. */
    public interface Terrain {
        int height(int x, int z);

        boolean wet(int x, int z);
    }

    /** Tramo de calle. {@code parent}: tramo del que sale (-1 = plaza). {@code dist}: distancia por la red desde la plaza. */
    public record Seg(double x0, double z0, double x1, double z1, double half, int parent, double dist, int y0, int y1) {
        public double length() {
            return Math.hypot(x1 - x0, z1 - z0);
        }
    }

    public static final int STEP = 7;

    private StreetPlanner() {}

    public static Pattern choose(long seed, boolean desert) {
        Random r = new Random(seed * 0x2545F4914F6CDD1DL + 17);
        double v = r.nextDouble();
        if (desert) return v < 0.65 ? Pattern.GRID : v < 0.85 ? Pattern.ORGANIC : Pattern.CROSSROADS;
        return v < 0.55 ? Pattern.ORGANIC : v < 0.8 ? Pattern.LINEAR : Pattern.CROSSROADS;
    }

    public static List<Seg> plan(long seed, int cx, int cz, Pattern pattern, Terrain t, int maxR) {
        Random rng = new Random(seed ^ 0x5DEECE66DL);
        List<Seg> out = new ArrayList<>();
        double a0 = rng.nextDouble() * Math.PI * 2;
        switch (pattern) {
            case ORGANIC -> {
                int trunks = 2 + rng.nextInt(3);
                List<int[]> trunkSegs = new ArrayList<>();
                for (int i = 0; i < trunks; i++) {
                    double a = a0 + i * Math.PI * 2 / trunks + (rng.nextDouble() - 0.5) * 0.6;
                    int len = 70 + rng.nextInt(50);
                    trunkSegs.add(walk(out, rng, t, cx, cz, cx + Math.cos(a) * 6, cz + Math.sin(a) * 6, a, len, 1.5, -1, 6, 0.45, maxR));
                }
                for (int[] ts : trunkSegs) branches(out, rng, t, cx, cz, ts, 0.38, 18, 42, 1.2, maxR, true);
            }
            case LINEAR -> {
                for (int k = 0; k < 2; k++) {
                    double a = a0 + k * Math.PI + (rng.nextDouble() - 0.5) * 0.3;
                    int[] ts = walk(out, rng, t, cx, cz, cx + Math.cos(a) * 6, cz + Math.sin(a) * 6, a, 100 + rng.nextInt(30), 1.6, -1, 6, 0.3, maxR);
                    branches(out, rng, t, cx, cz, ts, 0.3, 12, 26, 1.1, maxR, false);
                }
            }
            case CROSSROADS -> {
                for (int k = 0; k < 4; k++) {
                    double a = a0 + k * Math.PI / 2 + (rng.nextDouble() - 0.5) * 0.35;
                    int[] ts = walk(out, rng, t, cx, cz, cx + Math.cos(a) * 6, cz + Math.sin(a) * 6, a, 75 + rng.nextInt(35), 1.5, -1, 6, 0.3, maxR);
                    branches(out, rng, t, cx, cz, ts, 0.22, 14, 30, 1.2, maxR, false);
                }
            }
            case GRID -> grid(out, rng, t, cx, cz, maxR);
        }
        return out;
    }

    /**
     * Camina desde (x, z) con rumbo {@code a}: en cada tramo prueba varios giros y se queda con el más barato
     * (pendiente, agua, alejarse de la plaza, no girar de más). Devuelve {primer tramo, último tramo}.
     */
    private static int[] walk(List<Seg> out, Random rng, Terrain t, int cx, int cz, double x, double z, double a, int len, double half,
                              int parent, double dist, double wiggle, int maxR) {
        int first = -1, last = parent;
        int steps = Math.max(1, len / STEP);
        double px = x, pz = z;
        int py = t.height((int) Math.round(x), (int) Math.round(z));
        // tramo de arranque desde el padre (o la plaza) hasta el primer punto
        if (parent < 0) {
            out.add(new Seg(cx, cz, px, pz, half, -1, 0, t.height(cx, cz), py));
            first = last = out.size() - 1;
        }
        double drift = (rng.nextDouble() - 0.5) * 0.08;   // curvatura propia de esta calle
        for (int s = 0; s < steps; s++) {
            double best = Double.MAX_VALUE, bx = 0, bz = 0, ba = a;
            int by = py;
            for (int k = -2; k <= 2; k++) {
                double na = a + drift + k * wiggle * 0.5 + (rng.nextDouble() - 0.5) * 0.15;
                double nx = px + Math.cos(na) * STEP, nz = pz + Math.sin(na) * STEP;
                int ix = (int) Math.round(nx), iz = (int) Math.round(nz);
                if (Math.hypot(nx - cx, nz - cz) > maxR) continue;
                int ny = t.height(ix, iz);
                double cost = Math.abs(ny - py) * 2.5 + Math.abs(k) * 0.6 + rng.nextDouble() * 0.8;
                if (t.wet(ix, iz)) cost += 40;
                if (Math.hypot(nx - cx, nz - cz) < Math.hypot(px - cx, pz - cz)) cost += 6;   // no volver a la plaza
                if (Math.abs(ny - py) > 4) cost += 10;                                          // barranco: se evita si se puede
                if (cost < best) {
                    best = cost;
                    bx = nx;
                    bz = nz;
                    ba = na;
                    by = ny;
                }
            }
            if (best >= 40) break;   // agua por todos lados: la calle termina acá
            out.add(new Seg(px, pz, bx, bz, half, last, dist + s * STEP, py, by));
            last = out.size() - 1;
            if (first < 0) first = last;
            px = bx;
            pz = bz;
            py = by;
            a = ba;
        }
        return new int[]{first, last};
    }

    private static void branches(List<Seg> out, Random rng, Terrain t, int cx, int cz, int[] trunk, double chance, int minLen, int maxLen,
                                 double half, int maxR, boolean nested) {
        if (trunk[0] < 0) return;
        int since = 0;
        int end = trunk[1];
        for (int i = trunk[0]; i <= end && i < out.size(); i++) {
            Seg s = out.get(i);
            since++;
            if (since < 3 || rng.nextDouble() > chance) continue;
            since = 0;
            double a = Math.atan2(s.z1() - s.z0(), s.x1() - s.x0());
            double side = rng.nextBoolean() ? 1 : -1;
            double ba = a + side * (Math.PI / 2 + (rng.nextDouble() - 0.5) * 0.7);
            int len = minLen + rng.nextInt(Math.max(1, maxLen - minLen));
            int[] br = walk(out, rng, t, cx, cz, s.x1(), s.z1(), ba, len, half, i, s.dist() + s.length(), 0.4, maxR);
            if (nested && br[0] >= 0 && rng.nextDouble() < 0.35) {
                branches(out, rng, t, cx, cz, br, 0.3, 12, 22, 1.0, maxR, false);
            }
        }
    }

    /** Trama de manzanas: calles cada {@code B} bloques, en anillos desde la plaza; se corta donde hay agua o barranco. */
    private static void grid(List<Seg> out, Random rng, Terrain t, int cx, int cz, int maxR) {
        int b = 20 + rng.nextInt(4);
        int n = Math.max(2, Math.min(5, maxR / b));
        // índice del tramo que llega a cada intersección (para la distancia por la red)
        java.util.Map<Long, Integer> reach = new java.util.HashMap<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        queue.add(new int[]{0, 0});
        reach.put(0L, -1);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        java.util.Map<Long, Double> dist = new java.util.HashMap<>();
        dist.put(0L, 0.0);
        java.util.Set<Long> made = new java.util.HashSet<>();
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            long ck = key(c[0], c[1]);
            int x0 = cx + c[0] * b, z0 = cz + c[1] * b;
            int y0 = t.height(x0, z0);
            for (int[] d : dirs) {
                int gx = c[0] + d[0], gz = c[1] + d[1];
                if (Math.abs(gx) > n || Math.abs(gz) > n) continue;
                if (Math.hypot(gx, gz) > n + 0.3) continue;
                long nk = key(gx, gz);
                long ek = c[0] + c[1] * 1000L < gx + gz * 1000L ? ck * 31 + nk : nk * 31 + ck;
                if (made.contains(ek)) continue;
                int x1 = cx + gx * b, z1 = cz + gz * b;
                int y1 = t.height(x1, z1);
                if (t.wet(x1, z1) || t.wet((x0 + x1) / 2, (z0 + z1) / 2) || Math.abs(y1 - y0) > 6) continue;
                if (rng.nextDouble() < 0.12 && (gx != 0 && gz != 0)) continue;   // algún hueco: la trama no es perfecta
                made.add(ek);
                boolean avenue = gx == 0 || gz == 0;
                double dd = dist.get(ck);
                out.add(new Seg(x0, z0, x1, z1, avenue ? 1.6 : 1.1, reach.get(ck), dd, y0, y1));
                if (!reach.containsKey(nk)) {
                    reach.put(nk, out.size() - 1);
                    dist.put(nk, dd + b);
                    queue.add(new int[]{gx, gz});
                }
            }
        }
    }

    private static long key(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }
}
