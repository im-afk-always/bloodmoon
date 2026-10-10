package com.agustin.bloodmoon.human;

import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Terrazas del territorio de un asentamiento: la altura a la que los constructores dejan el suelo en cada columna.
 * Sale solo del sitio y del terreno generado (antes de árboles), así que la generación por chunks, las etapas que se
 * aplican después en partida, los lotes y las calles coinciden siempre.
 * <ul>
 *   <li>Parcelas de {@link #CELL}×{@link #CELL} alrededor de nodos de una grilla centrada en la plaza.</li>
 *   <li>Cada nodo toma la altura natural suavizada (5×5 nodos) y la redondea a escalones de {@link #STEP} respecto de
 *   la plaza: terrazas anchas que siguen la forma del terreno (una loma queda en dos o tres terrazas).</li>
 *   <li>El centro (24 bloques) queda a la altura de la plaza.</li>
 *   <li>Donde habría que cortar o rellenar más de {@link #MAX_CUT}, o hay agua, el nodo no se construye ni se toca.</li>
 *   <li>Entre terrazas, taludes de pasto a 45° tallados en la terraza más alta (sin muros de contención).</li>
 * </ul>
 * Alturas en "bloque de superficie" (el bloque sólido de arriba; se camina en y+1).
 */
public final class TerraceField {
    public static final int CELL = 16, STEP = 4, MAX_CUT = 12, CENTER = 24;
    /** Sin terraza: la columna queda como está. */
    public static final int NONE = Integer.MIN_VALUE;

    private static final Map<Long, TerraceField> CACHE = new ConcurrentHashMap<>();

    private final StreetPlanner.Terrain terrain;
    private final int cx, cz, base;
    private final Map<Long, Integer> raw = new ConcurrentHashMap<>();
    private final Map<Long, Integer> lvl = new ConcurrentHashMap<>();

    private TerraceField(StreetPlanner.Terrain terrain, int cx, int cz) {
        this.terrain = terrain;
        this.cx = cx;
        this.cz = cz;
        this.base = terrain.height(cx, cz) - 1;
    }

    public static TerraceField of(ServerLevel level, VillageSites.Site site) {
        long k = site.seed() ^ ((long) site.x() << 32) ^ (site.z() & 0xFFFFFFFFL);
        TerraceField f = CACHE.get(k);
        if (f == null) {
            if (CACHE.size() > 256) CACHE.clear();
            f = new TerraceField(VillageLayout.terrain(level), site.x(), site.z());
            CACHE.put(k, f);
        }
        return f;
    }

    public static void clear() {
        CACHE.clear();
    }

    /** Altura de la plaza (bloque de superficie). */
    public int base() {
        return base;
    }

    private static long key(int i, int j) {
        return ((long) i << 32) | (j & 0xFFFFFFFFL);
    }

    private int nodeX(int i) { return cx + i * CELL; }

    private int nodeZ(int j) { return cz + j * CELL; }

    private int cellI(int x) { return Math.floorDiv(x - cx + CELL / 2, CELL); }

    private int cellJ(int z) { return Math.floorDiv(z - cz + CELL / 2, CELL); }

    /** Altura natural en un nodo; NONE si es agua. */
    private int raw(int i, int j) {
        return raw.computeIfAbsent(key(i, j), k -> {
            int x = nodeX(i), z = nodeZ(j);
            return terrain.wet(x, z) ? NONE : terrain.height(x, z) - 1;
        });
    }

    /** Nivel de la terraza de una parcela, o NONE si no se construye. */
    public int cellLevel(int i, int j) {
        return lvl.computeIfAbsent(key(i, j), k -> {
            int r = raw(i, j);
            if (r == NONE) return NONE;
            double d = Math.hypot(i * CELL, j * CELL);
            if (d <= CENTER) return base;
            double sum = 0, n = 0;
            for (int a = -2; a <= 2; a++) {
                for (int b = -2; b <= 2; b++) {
                    int v = raw(i + a, j + b);
                    if (v == NONE) continue;
                    double w = 3 - Math.max(Math.abs(a), Math.abs(b));
                    sum += v * w;
                    n += w;
                }
            }
            double s = n == 0 ? r : sum / n;
            int l = base + STEP * (int) Math.round((s - base) / STEP);
            if (Math.abs(l - r) > MAX_CUT) return NONE;
            return l;
        });
    }

    /**
     * Altura del suelo terminado en la columna (bloque de superficie), o NONE si la columna no se toca. Cerca de una
     * parcela más baja, el borde se talla en talud a 45°.
     */
    public int level(int x, int z) {
        int i = cellI(x), j = cellJ(z);
        int own = cellLevel(i, j);
        if (own == NONE) return NONE;
        int t = own;
        int half = CELL / 2;
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                int lq = cellLevel(i + a, j + b);
                if (lq == NONE || lq >= own) continue;
                // distancia (Chebyshev) de la columna a la parcela vecina
                int qx0 = nodeX(i + a) - half, qx1 = qx0 + CELL - 1, qz0 = nodeZ(j + b) - half, qz1 = qz0 + CELL - 1;
                int dx = x < qx0 ? qx0 - x : x > qx1 ? x - qx1 : 0;
                int dz = z < qz0 ? qz0 - z : z > qz1 ? z - qz1 : 0;
                int d = Math.max(dx, dz);
                t = Math.min(t, lq + d);
            }
        }
        return t;
    }

    public boolean usable(int x, int z) {
        return level(x, z) != NONE;
    }

    /** Primer bloque libre para una calle en (x, z): sobre la terraza, o el terreno natural si ahí no hay terraza. */
    public int streetY(double x, double z) {
        int ix = (int) Math.round(x), iz = (int) Math.round(z);
        int l = level(ix, iz);
        return l == NONE ? terrain.height(ix, iz) : l + 1;
    }

    /**
     * Piso para un edificio que ocupa el rectángulo: el nivel de la terraza si toda la huella queda en una sola terraza
     * (a lo sumo un bloque de diferencia en algún borde), o NONE si cae sobre un talud, el agua o fuera del territorio.
     */
    public int floor(int minX, int minZ, int maxX, int maxZ) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int x = minX; x <= maxX; x += Math.max(1, (maxX - minX) / 4)) {
            for (int z = minZ; z <= maxZ; z += Math.max(1, (maxZ - minZ) / 4)) {
                int l = level(x, z);
                if (l == NONE) return NONE;
                lo = Math.min(lo, l);
                hi = Math.max(hi, l);
            }
        }
        for (int[] c : new int[][]{{maxX, maxZ}, {maxX, minZ}, {minX, maxZ}}) {
            int l = level(c[0], c[1]);
            if (l == NONE) return NONE;
            lo = Math.min(lo, l);
            hi = Math.max(hi, l);
        }
        // hasta un escalón de diferencia: el patio del edificio empareja el borde (corta o rellena el talud)
        return hi - lo <= STEP ? hi : NONE;
    }

    /** Terreno "como lo dejan los constructores", para trazar calles: evita taludes y lo que no es territorio. */
    public StreetPlanner.Terrain asTerrain() {
        return new StreetPlanner.Terrain() {
            @Override
            public int height(int x, int z) {
                int l = level(x, z);
                return l == NONE ? terrain.height(x, z) : l + 1;
            }

            @Override
            public boolean wet(int x, int z) {
                return terrain.wet(x, z) || level(x, z) == NONE && raw(cellI(x), cellJ(z)) == NONE;
            }
        };
    }

    /** Radio del territorio (aplanado en terrazas) según la etapa: aldea, pueblo, ciudad, capital. */
    public static int radius(int stage) {
        return switch (Math.max(0, Math.min(3, stage))) {
            case 0 -> 110;
            case 1 -> 140;
            case 2 -> 180;
            default -> 230;
        };
    }

    /** Hasta dónde talan los leñadores: el territorio y una franja de holgura. */
    public static int clearRadius(int stage) {
        return radius(stage) + 32;
    }
}
