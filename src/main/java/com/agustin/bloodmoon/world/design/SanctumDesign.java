package com.agustin.bloodmoon.world.design;

import static com.agustin.bloodmoon.world.design.Pal.*;

/**
 * El Santuario del Ojo: la arena del Observador, en el corazón de algunas regiones del Laberinto.
 *
 * - Una plataforma circular flotante (r 46) sobre un abismo sin fondo, con un cono invertido de roca debajo.
 *   Piso de losas con líneas radiales, anillos tallados y un círculo de runas de Bloque del Vacío que brillan.
 *   En el centro, un estrado de dos escalones con el sello del Ojo.
 * - Ocho pilares rotos a r 30: la única cobertura contra la Mirada. Tres están caídos y yacen en el piso.
 * - Cuatro puentes estrechos cruzan el abismo hasta un anillo-muralla colosal (r 88-100) con galerías
 *   abiertas hacia el vacío; de la muralla nacen diez costillas-tentáculo retorcidas que se curvan hacia
 *   arriba y adentro, sobre el Ojo, como una mano que se cierra. Algunas se quebraron a mitad de camino.
 * - Rocas flotantes en el abismo y cadenas que cuelgan de las costillas.
 */
public final class SanctumDesign {
    public static final int R_DAIS = 9, R_PLAT = 46, R_RIM_IN = 88, R_RIM_OUT = 100, R_ZONE = 112;
    public static final int PILLAR_R = 30, PILLARS = 8, RIBS = 10;
    /** Altura del centro del Ojo sobre el piso (FLOOR). */
    public static final int EYE_HEIGHT = 24;
    private static final int F = LabyrinthDesign.FLOOR;

    private final Noise n;

    SanctumDesign(Noise n) {
        this.n = n;
    }

    private static void set(int[] out, int y, int code) {
        if (y >= 0 && y < LabyrinthDesign.HEIGHT) out[y] = code;
    }

    private static double angDiff(double a, double b) {
        double d = (a - b) % 360;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    /** Distancia lateral (bloques) al puente más cercano y si la columna está sobre el eje de un puente. */
    private static double bridgeLateral(double dx, double dz) {
        return Math.min(Math.abs(dx), Math.abs(dz));
    }

    private int stone(int x, int y, int z) {
        double v = n.rnd(x, y, z, 400);
        if (v < 0.18) return CRACKED_DEEPSLATE_BRICKS;
        if (v < 0.24) return COBBLED_DEEPSLATE;
        if (v > 0.985) return VOID_STONE;
        return DEEPSLATE_BRICKS;
    }

    /** sid: identificador del santuario (varía pilares, costillas y puentes entre santuarios). */
    public void fill(int x, int z, double dx, double dz, double r, int sid, int[] out) {
        double deg = Math.toDegrees(Math.atan2(dz, dx));
        if (deg < 0) deg += 360;
        if (r <= R_PLAT) platform(x, z, dx, dz, r, deg, sid, out);
        else if (r < R_RIM_IN) abyss(x, z, dx, dz, r, deg, sid, out);
        else if (r <= R_RIM_OUT) rim(x, z, dx, dz, r, deg, out);
        else plaza(x, z, dx, dz, r, deg, out);
        ribs(x, z, r, deg, sid, out);
    }

    // ------------------------------------------------------------------ plataforma

    private void platform(int x, int z, double dx, double dz, double r, double deg, int sid, int[] out) {
        // cono invertido de roca bajo el piso
        int bottom = F - 2 - (int) ((R_PLAT - r) * 0.95 + n.smooth2(x, z, 6, 401) * 7);
        for (int y = bottom; y < F - 1; y++) {
            double vein = n.smooth2(x + y * 0.7, z - y * 0.4, 7, 402);
            set(out, y, vein > 0.82 ? VOID_STONE : n.rnd(x, y, z, 403) < 0.5 ? BLACKSTONE : COBBLED_DEEPSLATE);
        }
        if (r < 5 && n.rnd(x, 1, z, 404) < 0.35) for (int i = 1; i <= 4 + (int) (n.rnd(x, 2, z, 405) * 10); i++) set(out, bottom - i, CHAIN);
        if (r > 5 && r < 30 && n.rnd(x, 3, z, 406) < 0.012) set(out, bottom - 1, AMETHYST_CLUSTER);

        // piso
        set(out, F - 1, floorCode(x, z, r, deg));

        // estrado central
        if (r <= R_DAIS) {
            int inward = Pal.dir(-dx, -dz);
            if (r > 7.5) {
                set(out, F, stairs(DEEPSLATE_TILE_STAIRS, inward, false));
            } else {
                set(out, F, POLISHED_DEEPSLATE);
                if (r > 6) set(out, F + 1, stairs(DEEPSLATE_TILE_STAIRS, inward, false));
                else set(out, F + 1, r < 1.6 ? VOID_BLOCK : Math.abs(r - 3.6) < 0.6 ? CRYING_OBSIDIAN
                        : Math.abs(angDiff(deg % 90, 45)) < 6 && r > 2 ? VOID_BLOCK : POLISHED_BLACKSTONE);
            }
            return;
        }

        // barandal roto en el borde
        if (r > R_PLAT - 1.2 && bridgeLateral(dx, dz) > 3.5 && n.angular(deg, 407) < 0.62) set(out, F, PB_BRICK_WALL);

        // braseros entre los puentes
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45 + 90 * k);
            double bx = Math.cos(a) * 40, bz = Math.sin(a) * 40;
            double ax = Math.abs(dx - bx), az = Math.abs(dz - bz);
            if (ax < 1.5 && az < 1.5) {
                set(out, F, PB_BRICKS);
                if (ax < 0.6 && az < 0.6) {
                    set(out, F + 1, GILDED_BLACKSTONE);
                    set(out, F + 2, ASTRAL_FIRE);
                } else set(out, F + 1, PB_BRICK_WALL);
            }
        }

        pillars(x, z, dx, dz, sid, out);
    }

    private int floorCode(int x, int z, double r, double deg) {
        if (r >= 12 && r <= 16) {                                   // círculo de runas
            int bin = (int) (deg / 6);
            double sub = (deg % 6) / 6;
            int row = (int) (r - 12);
            double g = n.rnd(bin, row, 0, 410);
            if (r < 12.6 || r > 15.4) return CHISELED_PB;
            return g < 0.45 && sub > 0.15 && sub < 0.85 ? VOID_BLOCK : OBSIDIAN;
        }
        if (Math.abs(r - 22) < 0.6 || Math.abs(r - 38) < 0.6) return CHISELED_PB;
        double lineDist = Math.abs(angDiff(deg, Math.round(deg / 30) * 30)) * Math.PI / 180 * r;
        if (lineDist < 0.6 && r > 16) return POLISHED_BLACKSTONE;
        double v = n.rnd(x, 0, z, 411), s = n.smooth2(x, z, 8, 412);
        if (s > 0.8) return CRACKED_DEEPSLATE_TILES;
        if (v < 0.22) return CRACKED_DEEPSLATE_TILES;
        if (v < 0.27) return POLISHED_DEEPSLATE;
        return DEEPSLATE_TILES;
    }

    /** Ocho pilares de cobertura; tres se derrumbaron y yacen tendidos. */
    private void pillars(int x, int z, double dx, double dz, int sid, int[] out) {
        for (int k = 0; k < PILLARS; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            double px = Math.cos(a) * PILLAR_R, pz = Math.sin(a) * PILLAR_R;
            double ex = dx - px, ez = dz - pz;
            double d = Math.sqrt(ex * ex + ez * ez);
            int slot = Math.floorMod(k + sid, PILLARS);
            boolean fallen = slot == 1 || slot == 4 || slot == 6;                             // siempre 5 en pie
            int h = fallen ? 4 + (int) (n.rnd(k, sid, 1, 421) * 4) : 12 + (int) (n.rnd(k, sid, 1, 421) * 16);
            if (d < 3.6) set(out, F, d < 2.7 ? POLISHED_DEEPSLATE : CHISELED_DEEPSLATE);      // plinto
            if (d < 2.6) {
                for (int y = 1; y < h; y++) {
                    double erode = (y - (h - 4)) / 4.0 + (n.rnd(x, F + y, z, 422) - 0.5);
                    if (erode > 0.6) continue;                                            // cima rota
                    int code = y % 6 == 0 ? CHISELED_DEEPSLATE : POLISHED_DEEPSLATE;
                    if (!fallen && y == h - 5 && d > 1.6) code = VOID_BLOCK;              // banda que brilla
                    if (y == 7 && d > 1.8 && Math.abs(angDiff(Math.toDegrees(Math.atan2(-ez, -ex)), Math.toDegrees(Math.atan2(-pz, -px)))) < 30)
                        code = CRYING_OBSIDIAN;                                            // "ojo" que mira al centro
                    set(out, F + y, code);
                }
            }
            if (fallen) {
                // fuste caído: cilindro tendido en dirección tangente
                double tx = -Math.sin(a), tz = Math.cos(a);
                if (n.rnd(k, sid, 2, 423) < 0.5) { tx = -tx; tz = -tz; }
                double along = ex * tx + ez * tz, lat = Math.abs(-ex * tz + ez * tx);
                double len = 9 + n.rnd(k, sid, 3, 424) * 6;
                if (along > 3.5 && along < 3.5 + len && lat < 2.2) {
                    double half = Math.sqrt(Math.max(0, 2.2 * 2.2 - lat * lat));
                    for (int y = 0; y <= 4; y++) {
                        double cy = y + 0.5 - 2.0;
                        if (Math.abs(cy) <= half && n.rnd(x, F + y, z, 425) > 0.08)
                            set(out, F + y, ((int) along) % 6 == 0 ? CHISELED_DEEPSLATE : POLISHED_DEEPSLATE);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ abismo, puentes y rocas flotantes

    private void abyss(int x, int z, double dx, double dz, double r, double deg, int sid, int[] out) {
        double lat = bridgeLateral(dx, dz);
        if (lat <= 3.6) {
            int idx = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 0 : 2) : (dz > 0 ? 1 : 3);
            bridge(x, z, r, lat, idx + 4 * sid, out);
            return;
        }
        if (lat < 8) return;
        // rocas flotantes: una por celda de 11×11 con 30% de probabilidad
        int gx = Math.floorDiv(x, 11), gz = Math.floorDiv(z, 11);
        if (n.rnd(gx, 0, gz, 430) > 0.3) return;
        double cx = gx * 11 + 3 + n.rnd(gx, 1, gz, 431) * 5, cz = gz * 11 + 3 + n.rnd(gx, 2, gz, 432) * 5;
        double rad = 1.4 + n.rnd(gx, 3, gz, 433) * 2.2;
        double ex = x + 0.5 - cx, ez = z + 0.5 - cz, d = Math.sqrt(ex * ex + ez * ez);
        if (d > rad) return;
        int yc = F - 30 + (int) (n.rnd(gx, 4, gz, 434) * 55);
        double k = Math.sqrt(rad * rad - d * d);
        int top = yc + (int) Math.round(k * 0.6), bot = yc - (int) Math.round(k * 1.9);
        for (int y = bot; y <= top; y++) set(out, y, n.rnd(x, y, z, 435) < 0.25 ? VOID_STONE : n.rnd(x, y, z, 436) < 0.5 ? BLACKSTONE : COBBLED_DEEPSLATE);
        if (d < 0.8 && n.rnd(gx, 5, gz, 437) < 0.3) set(out, top + 1, ASTRAL_FIRE);
    }

    private void bridge(int x, int z, double r, double lat, int bid, int[] out) {
        // dos tramos caídos de 2 bloques por puente (se saltan); nunca junto a la plataforma ni a la muralla
        double along = r;
        int seg = (int) Math.floor(along);
        boolean gap = false;
        for (int g = 0; g < 2; g++) {
            int start = 54 + g * 16 + (int) (n.rnd(bid, g, 0, 440) * 6);
            if (seg >= start && seg < start + 2) gap = true;
        }
        if (lat <= 2.6) {
            if (!gap) {
                set(out, F - 1, n.rnd(x, 0, z, 441) < 0.2 ? CRACKED_PB_BRICKS : POLISHED_BLACKSTONE);
                set(out, F - 2, PB_BRICKS);
                // arco por debajo: más grueso cerca de los extremos
                double t = (along - R_PLAT) / (R_RIM_IN - R_PLAT);
                int depth = 2 + (int) (10 * Math.pow(Math.abs(t - 0.5) * 2, 3));
                for (int y = F - 2 - depth; y < F - 2; y++) set(out, y, PB_BRICKS);
                if (n.rnd(x, 1, z, 442) < 0.05) for (int y = 1; y <= 8; y++) set(out, F - 2 - depth - y, CHAIN);
            }
        } else if (!gap) {
            set(out, F - 1, PB_BRICKS);
            set(out, F - 2, PB_BRICKS);
            boolean post = seg % 8 == 0;
            if (post) {
                set(out, F, PB_BRICKS);
                set(out, F + 1, GILDED_BLACKSTONE);
                if (seg % 16 == 0) set(out, F + 2, ASTRAL_FIRE);
            } else if (n.rnd(x, 2, z, 443) < 0.8) set(out, F, PB_BRICK_WALL);
        }
    }

    // ------------------------------------------------------------------ muralla

    private void rim(int x, int z, double dx, double dz, double r, double deg, int[] out) {
        double lat = bridgeLateral(dx, dz);
        boolean gate = lat <= 4.5;
        double c = n.angular(deg, 450);
        int h = 44 + (int) (c * 26) - (c > 0.75 ? (int) ((c - 0.75) * 120) : 0);           // tramos derrumbados
        boolean inner = r < R_RIM_IN + 3, outer = r > R_RIM_OUT - 4;
        int base = inner ? F - 60 : F - 8;
        double bay = (deg * (Math.PI / 180) * R_RIM_IN) / 9.0;                               // vanos de 9 bloques
        double p = bay - Math.floor(bay);
        boolean buttress = p < 0.22;
        for (int y = base; y < F + h; y++) {
            int ly = y - F;
            if (gate && ly >= 0) {                                                           // portón en arco
                double q = lat / 4.5;
                if (ly < 14 + 5 * Math.sqrt(Math.max(0, 1 - q * q))) continue;
            }
            int storey = Math.floorMod(ly, 16);
            if (!inner && !outer) {                                                          // galerías entre muros
                if (ly < 0 || storey != 0) continue;
                set(out, y, POLISHED_BLACKSTONE);
                continue;
            }
            if (ly > 0 && !buttress && storey >= 3 && storey <= 12) {                        // arcadas hacia el abismo
                double q = (p - 0.61) / 0.39;
                if (inner && storey <= 9 + 3 * Math.sqrt(Math.max(0, 1 - q * q))) continue;
                if (outer && storey >= 5 && storey <= 9 && Math.abs(q) < 0.4) continue;
            }
            int code = storey == 0 && ly > 0 ? POLISHED_BLACKSTONE : buttress && inner ? POLISHED_DEEPSLATE : stone(x, y, z);
            if (buttress && inner && ly > 0 && storey == 8 && p > 0.06 && p < 0.16) code = CRYING_OBSIDIAN;
            set(out, y, code);
        }
        for (int y = F + h - 3; y < F + h; y++) if (n.rnd(x, y, z, 451) < 0.4) set(out, y, AIR);
        if (!gate && outer && n.rnd(x, 0, z, 452) < 0.01) set(out, F + h, ASTRAL_FIRE);
        if (gate) set(out, F - 1, POLISHED_BLACKSTONE);
    }

    private void plaza(int x, int z, double dx, double dz, double r, double deg, int[] out) {
        for (int y = F - 6; y < F - 1; y++) set(out, y, n.rnd(x, y, z, 460) < 0.5 ? DEEPSLATE_TILES : COBBLED_DEEPSLATE);
        double v = n.rnd(x, 0, z, 461);
        set(out, F - 1, bridgeLateral(dx, dz) <= 2.6 ? POLISHED_BLACKSTONE : v < 0.25 ? CRACKED_DEEPSLATE_TILES : DEEPSLATE_TILES);
        if (n.rnd(x, 1, z, 462) < 0.004) set(out, F, BONE_BLOCK);
    }

    // ------------------------------------------------------------------ costillas-tentáculo

    private void ribs(int x, int z, double r, double deg, int sid, int[] out) {
        if (r > R_RIM_IN + 2 || r < 20) return;
        for (int k = 0; k < RIBS; k++) {
            double t = (R_RIM_IN + 2 - r) / 64.0;
            double tEnd = n.rnd(k, 0, sid, 470) < 0.3 ? 0.45 + n.rnd(k, 1, sid, 471) * 0.3 : 1.0;
            if (t < 0 || t > tEnd) continue;
            double twist = (n.rnd(k, 2, sid, 472) < 0.5 ? -1 : 1) * (18 + 14 * n.rnd(k, 3, sid, 473));
            double theta = 18 + 36 * k + twist * t * t;
            double w = 3.4 * (1 - t) + 1.1;
            if (tEnd < 1 && t > tEnd - 0.04) w *= 0.6;                                       // muñón quebrado
            double lat = Math.abs(angDiff(deg, theta)) * Math.PI / 180 * r;
            if (lat > w) continue;
            double yb = F + 34 + n.rnd(k, 4, sid, 474) * 10;
            double rise = 64 + n.rnd(k, 5, sid, 475) * 12;
            double yc = yb + rise * Math.sin(t * Math.PI / 2) - 8 * t * t * t;
            double dydt = rise * Math.PI / 2 * Math.cos(t * Math.PI / 2) - 24 * t * t;
            double slope = dydt / 64.0;
            double half = Math.sqrt(w * w - lat * lat) * Math.sqrt(1 + slope * slope);
            int y0 = (int) Math.floor(yc - half), y1 = (int) Math.ceil(yc + half);
            for (int y = y0; y <= y1; y++) {
                double under = (yc - y) / half;
                int code = ((int) (t * 40)) % 5 == 0 ? CHISELED_PB : n.rnd(x, y, z, 476) < 0.3 ? VOID_STONE : BLACKSTONE;
                if (under > 0.6 && n.rnd(x, y, z, 477) < 0.1) code = CRYING_OBSIDIAN;          // ventosas que brillan
                set(out, y, code);
            }
            if (lat < 0.6 && n.rnd(x, 6, z, 478) < 0.06) {                                   // cadenas colgantes
                int len = 6 + (int) (n.rnd(x, 7, z, 479) * 22);
                for (int y = y0 - 1; y > y0 - 1 - len && y > F + 2; y--) set(out, y, CHAIN);
            }
        }
    }
}
