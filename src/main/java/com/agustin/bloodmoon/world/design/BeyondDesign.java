package com.agustin.bloodmoon.world.design;

import static com.agustin.bloodmoon.world.design.Pal.*;

/**
 * El Más Allá de la Grieta: una llanura sin fin de losas negras bajo un cielo púrpura, con muy pocos obeliscos.
 * La losa tiene 64 bloques de espesor sobre el vacío: el Ojo Colosal la perfora de lado a lado.
 * Cada pelea final ocurre en un "círculo" nuevo, a lo largo del eje X (x = n·SPACING): ahí hay un sello
 * de Bloques del Vacío y cuatro obeliscos rituales que sirven de cobertura.
 */
public final class BeyondDesign {
    public static final int FLOOR = 64, HEIGHT = 256, SPACING = 3000, SIGIL_R = 30, RITUAL_R = 46;
    private static final int CELL = 112;

    private final Noise n;

    public BeyondDesign(long seed) {
        this.n = new Noise(seed);
    }

    /** Centro (x, z) del círculo de la pelea número k. */
    public static int[] fightCenter(int k) {
        return new int[]{k * SPACING + SPACING / 2, 0};
    }

    private static void set(int[] out, int y, int code) {
        if (y >= 0 && y < HEIGHT) out[y] = code;
    }

    public void fillColumn(int x, int z, int[] out) {
        for (int y = 0; y < HEIGHT; y++) out[y] = AIR;
        // losa
        for (int y = 0; y < FLOOR - 4; y++) set(out, y, ((x * 7 + y * 13 + z * 3) & 7) < 3 ? BLACKSTONE : COBBLED_DEEPSLATE);
        for (int y = FLOOR - 4; y < FLOOR - 1; y++) set(out, y, n.rnd(x, y, z, 600) < 0.5 ? BLACKSTONE : POLISHED_BLACKSTONE);
        set(out, FLOOR - 1, surface(x, z));

        // círculo de pelea más cercano
        int k = Math.floorDiv(x, SPACING);
        int[] c = fightCenter(k);
        double dx = x + 0.5 - c[0], dz = z + 0.5 - c[1];
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r < RITUAL_R + 8) {
            fight(x, z, dx, dz, r, out);
            return;
        }
        obelisk(x, z, out);
    }

    private int surface(int x, int z) {
        boolean grid = Math.floorMod(x, 11) == 0 || Math.floorMod(z, 11) == 0;
        double vein = n.smooth2(x, z, 23, 601);
        double ridge = 1 - Math.abs(2 * n.smooth2(x * 1.7, z * 1.7, 9, 602) - 1);
        double v = n.rnd(x, 0, z, 603);
        if (ridge > 0.965 && vein > 0.45) return v < 0.12 ? CRYING_OBSIDIAN : VOID_STONE;    // vetas que brillan
        if (grid) return POLISHED_BLACKSTONE;
        if (v < 0.18) return CRACKED_DEEPSLATE_TILES;
        if (v < 0.21) return OBSIDIAN;
        return DEEPSLATE_TILES;
    }

    private void fight(int x, int z, double dx, double dz, double r, int[] out) {
        double deg = Math.toDegrees(Math.atan2(dz, dx));
        if (deg < 0) deg += 360;
        // sello
        if (Math.abs(r - SIGIL_R) < 0.8) set(out, FLOOR - 1, ((int) (deg / 3)) % 2 == 0 ? VOID_BLOCK : CHISELED_PB);
        else if (Math.abs(r - SIGIL_R + 4) < 0.6 || Math.abs(r - 8) < 0.6) set(out, FLOOR - 1, CHISELED_PB);
        else if (r < SIGIL_R && Math.abs(Math.sin(Math.toRadians(deg) * 3)) * r < 0.7 && r > 8) set(out, FLOOR - 1, POLISHED_BLACKSTONE);
        else if (r < 1.6) set(out, FLOOR - 1, VOID_BLOCK);
        // cuatro obeliscos rituales (cobertura)
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45 + 90 * i);
            double ox = Math.cos(a) * RITUAL_R, oz = Math.sin(a) * RITUAL_R;
            obeliskAt(x, z, dx - ox, dz - oz, 34 + (int) (n.rnd(i, 0, 0, 610) * 12), false, out);
        }
    }

    private void obelisk(int x, int z, int[] out) {
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        if (n.rnd(cx, 0, cz, 620) > 0.3) return;                       // pocos
        double ox = cx * CELL + 14 + n.rnd(cx, 1, cz, 621) * (CELL - 28);
        double oz = cz * CELL + 14 + n.rnd(cx, 2, cz, 622) * (CELL - 28);
        int[] fc = fightCenter(Math.floorDiv((int) ox, SPACING));
        if (Math.hypot(ox - fc[0], oz - fc[1]) < RITUAL_R + 40) return;   // deja libre el círculo de pelea
        boolean broken = n.rnd(cx, 3, cz, 623) < 0.35;
        int h = 30 + (int) (n.rnd(cx, 4, cz, 624) * 30);
        obeliskAt(x, z, x + 0.5 - ox, z + 0.5 - oz, broken ? h / 2 : h, broken, out);
    }

    /** Obelisco de obsidiana: plinto 7×7, fuste 3×3 con bandas de obsidiana llorosa y piramidión. */
    private void obeliskAt(int x, int z, double ex, double ez, int h, boolean broken, int[] out) {
        double m = Math.max(Math.abs(ex), Math.abs(ez));
        if (m > 7) return;
        if (broken && m > 3.5 && m < 7 && n.rnd(x, 1, z, 630) < 0.18) {   // escombros alrededor
            set(out, FLOOR, n.rnd(x, 2, z, 631) < 0.5 ? OBSIDIAN : BLACKSTONE);
            return;
        }
        if (m <= 3.5) {
            set(out, FLOOR, POLISHED_BLACKSTONE);
            if (m <= 2.5) set(out, FLOOR + 1, CHISELED_PB);
        }
        int top = FLOOR + h;
        for (int y = FLOOR + 2; y < top; y++) {
            int ly = y - FLOOR;
            double half = ly > h - 5 ? 1.5 * (top - y) / 5.0 : 1.5;
            if (m > half + 0.01) continue;
            if (broken && y > top - 4 && n.rnd(x, y, z, 632) < 0.5) continue;
            set(out, y, ly % 9 == 0 ? CRYING_OBSIDIAN : OBSIDIAN);
        }
        if (!broken && m < 0.6) set(out, top, VOID_BLOCK);
    }
}
