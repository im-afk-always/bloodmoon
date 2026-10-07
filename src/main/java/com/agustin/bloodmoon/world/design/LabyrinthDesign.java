package com.agustin.bloodmoon.world.design;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SplittableRandom;

import static com.agustin.bloodmoon.world.design.Pal.*;

/**
 * El Laberinto del Vacío: un mundo sin fin de muros colosales en ruinas sobre un abismo.
 *
 * - Celdas de 24 bloques con muros de 4 (alto 26-90) agrupadas en regiones de 12×12. Cada región es un
 *   laberinto perfecto (backtracker recursivo) con algunos lazos; las regiones se conectan por su punto medio
 *   y por pasos al azar en el borde (técnica de laberintos infinitos por chunks). Hay muchísimos callejones
 *   sin salida: "a veces no llevan a nada".
 * - Entre celdas abiertas hay arcos de 8×16, no pasillos lisos; los muros tienen ventanales en arco,
 *   bandas cada 12, pilares en las esquinas, cima erosionada y tramos derrumbados.
 * - Celdas especiales: abismos con puentes rotos, plazas con obelisco, torres huecas de 70-120, santuarios
 *   con un marco de portal del Vacío (salida) y, en el centro de cada región, las ruinas de un coliseo.
 * - Piso a y = 64 sobre una losa; debajo, el vacío.
 */
public final class LabyrinthDesign {
    public static final int FLOOR = 64, HEIGHT = 256, CELL = 24, WALL = 4, REGION = 12;
    static final int T_NORMAL = 0, T_CHASM = 1, T_PLAZA = 2, T_TOWER = 3, T_SHRINE = 4;
    static final int ARENA_R = 56;

    private final long seed;
    private final Noise n;
    private final Map<Long, boolean[]> mazes = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, boolean[]> e) {
            return size() > 256;
        }
    });

    public LabyrinthDesign(long seed) {
        this.seed = seed;
        this.n = new Noise(seed);
    }

    // ------------------------------------------------------------------ laberinto

    /** [openEast(R*R), openSouth(R*R)] de la región. */
    private boolean[] maze(int rx, int rz) {
        long key = ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
        boolean[] m = mazes.get(key);
        if (m != null) return m;
        int R = REGION;
        boolean[] open = new boolean[R * R * 2];
        boolean[] seen = new boolean[R * R];
        SplittableRandom rng = new SplittableRandom(seed ^ (rx * 0x9E3779B97F4A7C15L) ^ (rz * 0xC2B2AE3D27D4EB4FL));
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        int start = rng.nextInt(R * R);
        stack.push(start);
        seen[start] = true;
        int[] dxs = {1, -1, 0, 0}, dzs = {0, 0, 1, -1};
        while (!stack.isEmpty()) {
            int c = stack.peek();
            int cx = c % R, cz = c / R;
            int[] order = {0, 1, 2, 3};
            for (int i = 3; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int t = order[i]; order[i] = order[j]; order[j] = t;
            }
            boolean moved = false;
            for (int o : order) {
                int nx = cx + dxs[o], nz = cz + dzs[o];
                if (nx < 0 || nz < 0 || nx >= R || nz >= R || seen[nz * R + nx]) continue;
                if (o == 0) open[cz * R + cx] = true;
                else if (o == 1) open[cz * R + nx] = true;
                else if (o == 2) open[R * R + cz * R + cx] = true;
                else open[R * R + nz * R + cx] = true;
                seen[nz * R + nx] = true;
                stack.push(nz * R + nx);
                moved = true;
                break;
            }
            if (!moved) stack.pop();
        }
        for (int i = 0; i < R * R * 2; i++) if (!open[i] && rng.nextDouble() < 0.06) open[i] = true;   // algunos lazos
        mazes.put(key, open);
        return open;
    }

    boolean openEast(int cx, int cz) {
        int rx = Math.floorDiv(cx, REGION), rz = Math.floorDiv(cz, REGION);
        int lx = cx - rx * REGION, lz = cz - rz * REGION;
        if (lx == REGION - 1) return lz == REGION / 2 || n.rnd(rx, lz, rz, 101) < 0.12;
        return maze(rx, rz)[lz * REGION + lx];
    }

    boolean openSouth(int cx, int cz) {
        int rx = Math.floorDiv(cx, REGION), rz = Math.floorDiv(cz, REGION);
        int lx = cx - rx * REGION, lz = cz - rz * REGION;
        if (lz == REGION - 1) return lx == REGION / 2 || n.rnd(rx, lx, rz, 102) < 0.12;
        return maze(rx, rz)[REGION * REGION + lz * REGION + lx];
    }

    int openings(int cx, int cz) {
        return (openEast(cx, cz) ? 1 : 0) + (openEast(cx - 1, cz) ? 1 : 0) + (openSouth(cx, cz) ? 1 : 0) + (openSouth(cx, cz - 1) ? 1 : 0);
    }

    int cellType(int cx, int cz) {
        if (inArenaZone(cx * CELL + CELL / 2, cz * CELL + CELL / 2)) return T_NORMAL;
        double v = n.rnd(cx, 0, cz, 110);
        if (v < 0.07) return T_CHASM;
        if (v < 0.12) return T_PLAZA;
        if (v < 0.155) return T_TOWER;
        if (v < 0.18) return T_SHRINE;
        return T_NORMAL;
    }

    /** Centro de la región (x, z) en bloques. */
    static int[] regionCenter(int x, int z) {
        int size = REGION * CELL;
        int rx = Math.floorDiv(x, size), rz = Math.floorDiv(z, size);
        return new int[]{rx * size + size / 2, rz * size + size / 2};
    }

    static boolean inArenaZone(int x, int z) {
        int[] c = regionCenter(x, z);
        double dx = x - c[0], dz = z - c[1];
        return dx * dx + dz * dz < (ARENA_R + 6) * (ARENA_R + 6);
    }

    /** Centro de una celda transitable (no abismo) cerca de (x, z), para construir un portal de llegada. */
    public int[] safeSpot(int x, int z) {
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        for (int ring = 0; ring < 4; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int px = (cx + dx) * CELL + CELL / 2 + 1, pz = (cz + dz) * CELL + CELL / 2 + 1;
                    if (inArenaZone(px, pz)) continue;
                    int t = cellType(cx + dx, cz + dz);
                    if (t == T_NORMAL || t == T_PLAZA) return new int[]{px + (t == T_PLAZA ? 5 : 0), pz + (t == T_PLAZA ? 5 : 0)};
                }
            }
        }
        return new int[]{x, z};
    }

    // ------------------------------------------------------------------ columna

    /** out[y] para y en [0, HEIGHT). */
    public void fillColumn(int x, int z, int[] out) {
        for (int y = 0; y < HEIGHT; y++) out[y] = AIR;
        int[] c = regionCenter(x, z);
        double adx = x - c[0], adz = z - c[1];
        double ar = Math.sqrt(adx * adx + adz * adz);
        if (ar < ARENA_R + 6) {
            arenaRuin(x, z, adx, adz, ar, out);
            return;
        }
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        int ux = x - cx * CELL, uz = z - cz * CELL;
        int type = cellType(cx, cz);
        boolean wallW = ux < WALL, wallN = uz < WALL;

        if (wallW && wallN) {                                   // pilar de esquina
            int h = Math.max(wallHeight(cx, cz, 0), wallHeight(cx, cz, 1)) + 6;
            int bottom = FLOOR - (chasmNear(cx, cz) ? 48 : 8);
            for (int y = bottom; y < FLOOR + h; y++) set(out, y, (y - FLOOR) % 12 == 11 ? CHISELED_DEEPSLATE : POLISHED_DEEPSLATE);
            if (n.rnd(cx, 1, cz, 120) < 0.3 && ux == 1 && uz == 1) {
                set(out, FLOOR + h, GILDED_BLACKSTONE);
                set(out, FLOOR + h + 1, ASTRAL_FIRE);
            }
            return;
        }
        if (wallW || wallN) {
            boolean west = wallW;
            boolean open = west ? openEast(cx - 1, cz) : openSouth(cx, cz - 1);
            int along = west ? uz : ux;                          // posición a lo largo del muro (4..23)
            int across = west ? ux : uz;                         // 0..3 (cara exterior 0 y 3)
            boolean chasmSide = west ? (type == T_CHASM || cellType(cx - 1, cz) == T_CHASM)
                    : (type == T_CHASM || cellType(cx, cz - 1) == T_CHASM);
            wall(x, z, cx, cz, west ? 0 : 1, along, across, open, chasmSide, out);
            return;
        }
        cell(x, z, cx, cz, ux, uz, type, out);
    }

    private static void set(int[] out, int y, int code) {
        if (y >= 0 && y < HEIGHT) out[y] = code;
    }

    private boolean chasmNear(int cx, int cz) {
        return cellType(cx, cz) == T_CHASM || cellType(cx - 1, cz) == T_CHASM || cellType(cx, cz - 1) == T_CHASM
                || cellType(cx - 1, cz - 1) == T_CHASM;
    }

    /** Altura del muro oeste (side 0) o norte (side 1) de la celda. */
    int wallHeight(int cx, int cz, int side) {
        double base = 26 + 34 * n.smooth2(cx * 3 + side, cz * 3, 4, 111);
        if (n.rnd(cx, side, cz, 112) < 0.1) base += 30;
        if (n.rnd(cx, side, cz, 113) < 0.14) base = 4 + n.rnd(cx, side, cz, 114) * 7;   // derrumbado
        return (int) base;
    }

    private int brick(int x, int y, int z, int base, int cracked) {
        double v = n.rnd(x, y, z, 115);
        double t = n.smooth2(x + y * 0.6, z - y * 0.3, 11, 116);
        if (v < 0.2 + 0.15 * Math.min(1, (y - FLOOR) / 80.0)) return cracked;
        if (t > 0.8) return TUFF_BRICKS;
        if (v > 0.992) return CRYING_OBSIDIAN;
        if (v > 0.985) return VOID_STONE;
        return base;
    }

    private void wall(int x, int z, int cx, int cz, int side, int along, int across, boolean open, boolean chasm, int[] out) {
        int h = wallHeight(cx, cz, side);
        int bottom = FLOOR - (chasm ? 48 : 8);
        boolean face = across == 0 || across == WALL - 1;
        for (int y = bottom; y < FLOOR + h; y++) {
            int ly = y - FLOOR;
            // arco de paso 8×16 en las paredes abiertas
            if (open && ly >= 0) {
                double q = (along - 13.5) / 4.5;
                if (Math.abs(q) <= 1 && ly < 12 + 4 * Math.sqrt(Math.max(0, 1 - q * q))) continue;
                if (Math.abs(q) <= 1.25 && Math.abs(q) > 1 && ly < 17 && face) {
                    set(out, y, ly == 16 ? CHISELED_PB : PB_BRICKS);     // jambas
                    continue;
                }
            }
            // ventanales en arco, a través del muro
            if (ly >= 22 && ly + 3 < h) {
                int bay = Math.floorMod(along - 4, 7);
                int storey = ly % 12;
                if (bay >= 2 && bay <= 4 && storey >= 3 && storey <= (bay == 3 ? 9 : 8)) continue;
            }
            int code;
            if (ly >= 0 && ly % 12 == 0 && face) code = POLISHED_BLACKSTONE;
            else if (ly < 0) code = DEEPSLATE_BRICKS;
            else code = brick(x, y, z, DEEPSLATE_BRICKS, CRACKED_DEEPSLATE_BRICKS);
            set(out, y, code);
        }
        // cima erosionada
        for (int y = FLOOR + h - 3; y < FLOOR + h; y++) if (n.rnd(x, y, z, 117) < 0.35 + 0.15 * (y - FLOOR - h + 3)) set(out, y, AIR);
        // muro derrumbado: escombros en la base
        if (h < 12 && n.rnd(x, 0, z, 118) < 0.5) set(out, FLOOR + h, n.rnd(x, 1, z, 119) < 0.5 ? COBBLED_DEEPSLATE : CRACKED_DEEPSLATE_BRICKS);
        // telarañas y amatista al pie del muro
        if (face && n.rnd(x, 2, z, 121) < 0.02) set(out, FLOOR, COBWEB);
        if (face && n.rnd(x, 3, z, 122) < 0.006) set(out, FLOOR + 5 + (int) (n.rnd(x, 4, z, 123) * 20), AMETHYST_CLUSTER);
    }

    private void floor(int x, int z, int[] out) {
        for (int y = FLOOR - 6; y < FLOOR - 1; y++) set(out, y, n.rnd(x, y, z, 130) < 0.5 ? DEEPSLATE_TILES : COBBLED_DEEPSLATE);
        double v = n.smooth2(x, z, 9, 131), r = n.rnd(x, 0, z, 132);
        int top = v > 0.8 ? VOID_STONE : r < 0.25 ? CRACKED_DEEPSLATE_TILES : r < 0.3 ? SOUL_SAND : DEEPSLATE_TILES;
        set(out, FLOOR - 1, top);
    }

    private void cell(int x, int z, int cx, int cz, int ux, int uz, int type, int[] out) {
        double mx = ux - (WALL + (CELL - WALL) / 2.0) + 0.5, mz = uz - (WALL + (CELL - WALL) / 2.0) + 0.5;   // relativo al centro
        double r = Math.sqrt(mx * mx + mz * mz);
        if (type == T_CHASM) {
            chasm(x, z, cx, cz, mx, mz, out);
            return;
        }
        floor(x, z, out);
        switch (type) {
            case T_PLAZA -> plaza(x, z, cx, cz, mx, mz, r, out);
            case T_TOWER -> tower(x, z, cx, cz, mx, mz, r, out);
            case T_SHRINE -> shrine(x, z, cx, cz, mx, mz, out);
            default -> {
                if (Math.abs(mx) < 0.6 && Math.abs(mz) < 0.6) {
                    int open = openings(cx, cz);
                    if (open == 1 && n.rnd(cx, 5, cz, 140) < 0.35) set(out, FLOOR, CHEST | (n.rnd(cx, 6, cz, 141) < 0.5 ? 0 : 1) << 8);
                    else if (n.rnd(cx, 7, cz, 142) < 0.12) {
                        set(out, FLOOR, GILDED_BLACKSTONE);
                        set(out, FLOOR + 1, ASTRAL_FIRE);
                    }
                }
                if (n.rnd(x, 8, z, 143) < 0.006) set(out, FLOOR, BONE_BLOCK);
                else if (n.rnd(x, 9, z, 144) < 0.01) set(out, FLOOR, COBBLED_DEEPSLATE);
            }
        }
    }

    private void chasm(int x, int z, int cx, int cz, double mx, double mz, int[] out) {
        // puentes de 3 hacia cada lado abierto, con tramos caídos
        boolean e = openEast(cx, cz), w = openEast(cx - 1, cz), s = openSouth(cx, cz), nn = openSouth(cx, cz - 1);
        boolean onX = Math.abs(mz) <= 1.5 && ((mx >= 0 && e) || (mx <= 0 && w));
        boolean onZ = Math.abs(mx) <= 1.5 && ((mz >= 0 && s) || (mz <= 0 && nn));
        boolean hub = Math.abs(mx) <= 2.5 && Math.abs(mz) <= 2.5;
        if (!(onX || onZ || hub)) return;
        int seg = (int) Math.floor((onX ? mx : mz) / 4);
        if (!hub && n.rnd(cx * 7 + seg, onX ? 1 : 2, cz, 150) < 0.18) return;   // tramo caído
        set(out, FLOOR - 2, PB_BRICKS);
        set(out, FLOOR - 1, n.rnd(x, 0, z, 151) < 0.2 ? CRACKED_PB_BRICKS : POLISHED_BLACKSTONE);
        boolean edge = (onX && Math.abs(Math.abs(mz) - 1.5) < 0.6) || (onZ && Math.abs(Math.abs(mx) - 1.5) < 0.6);
        if (edge && !hub) set(out, FLOOR, PB_BRICK_WALL);
        if (hub && Math.abs(mx) < 0.6 && Math.abs(mz) < 0.6) {
            set(out, FLOOR, GILDED_BLACKSTONE);
            set(out, FLOOR + 1, ASTRAL_FIRE);
        }
        // cadenas que cuelgan bajo el puente
        if ((onX || onZ) && !edge && n.rnd(x, 1, z, 152) < 0.05) for (int y = 1; y <= 6; y++) set(out, FLOOR - 2 - y, CHAIN);
    }

    private void plaza(int x, int z, int cx, int cz, double mx, double mz, double r, int[] out) {
        double ax = Math.abs(mx), az = Math.abs(mz);
        if (r < 6.5 && (Math.abs(r - 6) < 0.6)) set(out, FLOOR - 1, CHISELED_PB);
        if (ax <= 1.5 && az <= 1.5) {                          // obelisco
            int h = 18 + (int) (n.rnd(cx, 0, cz, 160) * 14);
            for (int y = 0; y < h; y++) {
                int width = y < 3 ? 1 : 0;
                if (Math.max(ax, az) > width + 0.5) break;
                set(out, FLOOR + y, y % 5 == 2 ? CRYING_OBSIDIAN : OBSIDIAN);
            }
            if (ax < 0.6 && az < 0.6) {
                set(out, FLOOR + h, GILDED_BLACKSTONE);
                set(out, FLOOR + h + 1, ASTRAL_FIRE);
            }
            return;
        }
        if (Math.abs(ax - 7) < 0.6 && Math.abs(az - 7) < 0.6) {    // braseros en las esquinas
            set(out, FLOOR, PB_BRICKS);
            set(out, FLOOR + 1, GILDED_BLACKSTONE);
            set(out, FLOOR + 2, ASTRAL_FIRE);
        }
        if (Math.abs(mx - 2.5) < 0.6 && Math.abs(mz) < 0.6 && n.rnd(cx, 1, cz, 161) < 0.5) set(out, FLOOR, CHEST | (1 << 8));
    }

    private void tower(int x, int z, int cx, int cz, double mx, double mz, double r, int[] out) {
        if (r > 7.5) return;
        int h = 70 + (int) (n.rnd(cx, 0, cz, 170) * 50);
        boolean broken = n.rnd(cx, 1, cz, 171) < 0.4;
        double ang = Math.toDegrees(Math.atan2(mz, mx));
        int top = broken ? (int) (h * (0.55 + 0.25 * (0.5 + 0.5 * Math.sin(Math.toRadians(ang) * 3)))) : h;
        boolean shell = r > 5.5;
        // puerta hacia un lado abierto
        int doorDir = openEast(cx, cz) ? 0 : openSouth(cx, cz) ? 90 : openEast(cx - 1, cz) ? 180 : 270;
        double dd = Math.abs(((ang - doorDir) % 360 + 540) % 360 - 180);
        for (int y = 0; y < top; y++) {
            if (shell) {
                boolean door = dd < 12 && y < 7;
                boolean window = (y % 16 >= 6 && y % 16 <= 10) && ((int) ((ang + 360) / 30)) % 2 == 0 && ((ang + 360) % 30) < 6;
                if (door || window) continue;
                set(out, FLOOR + y, y % 16 == 0 ? POLISHED_BLACKSTONE : brick(x, FLOOR + y, z, PB_BRICKS, CRACKED_PB_BRICKS));
            } else if (y > 0 && y % 16 == 0 && r < 4.5) {
                set(out, FLOOR + y, (Math.abs(mx) < 1 && Math.abs(mz - 2) < 1.2) ? AIR : DEEPSLATE_TILES);   // pisos con hueco
            }
        }
        if (!broken && shell && ((int) ((ang + 360) / 20)) % 2 == 0) set(out, FLOOR + top, PB_BRICKS);   // almenas
        if (r < 0.8) {
            if (n.rnd(cx, 2, cz, 172) < 0.5) set(out, FLOOR, SPAWNER);
            if (!broken) {
                set(out, FLOOR + top - 1, GILDED_BLACKSTONE);
                set(out, FLOOR + top, ASTRAL_FIRE);
            }
        }
        if (Math.abs(mx - 2) < 0.6 && Math.abs(mz + 2) < 0.6) set(out, FLOOR, CHEST | (2 << 8));
    }

    private void shrine(int x, int z, int cx, int cz, double mx, double mz, int[] out) {
        double m = Math.max(Math.abs(mx), Math.abs(mz));
        int tiers = m < 2.5 ? 3 : m < 4.5 ? 2 : m < 6.5 ? 1 : 0;
        for (int y = 0; y < tiers * 2; y++) set(out, FLOOR + y, y % 2 == 1 && m > 6.5 - (tiers) * 2 - 1.2 ? CHISELED_PB : PB_BRICKS);
        int base = FLOOR + 6;
        // marco de portal del Vacío (sin encender): interior 2×3, en el plano z = centro
        int ix = (int) Math.floor(mx + 1.5), iz = (int) Math.round(mz - 0.5);
        if (iz == 0 && ix >= 0 && ix <= 3) {
            for (int y = 0; y <= 4; y++) {
                boolean frame = ix == 0 || ix == 3 || y == 0 || y == 4;
                set(out, base + y, frame ? VOID_BLOCK : AIR);
            }
        }
        if (Math.abs(Math.abs(mx) - 5.5) < 0.6 && Math.abs(Math.abs(mz) - 5.5) < 0.6) {
            set(out, FLOOR + 2, GILDED_BLACKSTONE);
            set(out, FLOOR + 3, ASTRAL_FIRE);
        }
        if (Math.abs(mx) < 0.6 && Math.abs(mz - 2) < 0.6) set(out, base, CHEST | (2 << 8));
    }

    // ------------------------------------------------------------------ coliseo en ruinas (centro de región)

    private void arenaRuin(int x, int z, double dx, double dz, double r, int[] out) {
        double deg = Math.toDegrees(Math.atan2(dz, dx));
        if (deg < 0) deg += 360;
        floor(x, z, out);
        if (r < 26) {
            if (Math.abs(r - 12) < 0.7 || Math.abs(r - 22) < 0.7) set(out, FLOOR - 1, CHISELED_PB);
            // pedestal con portal del Vacío (salida del laberinto)
            double m = Math.max(Math.abs(dx), Math.abs(dz));
            if (m < 5.5) {
                for (int y = 0; y < 3; y++) set(out, FLOOR + y, y == 2 ? CRYING_OBSIDIAN : PB_BRICKS);
                int ix = (int) Math.round(dx), iz = (int) Math.round(dz);
                if (iz == 0 && ix >= -2 && ix <= 1) {
                    for (int y = 0; y <= 4; y++) {
                        boolean frame = ix == -2 || ix == 1 || y == 0 || y == 4;
                        set(out, FLOOR + 3 + y, frame ? VOID_BLOCK : AIR);
                    }
                }
                if (Math.abs(Math.abs(dx) - 4) < 0.6 && Math.abs(Math.abs(dz) - 4) < 0.6) {
                    set(out, FLOOR + 3, GILDED_BLACKSTONE);
                    set(out, FLOOR + 4, ASTRAL_FIRE);
                }
                if (ix == 0 && iz == 3) set(out, FLOOR + 3, CHEST | (2 << 8));
            }
            return;
        }
        double c = n.angular(deg, 190);
        double drop = c > 0.55 ? (c - 0.55) / 0.45 : 0;
        boolean gate = (Math.abs(dz) <= 4 || Math.abs(dx) <= 4);
        if (r < 46) {                                            // gradas
            int hs = 4 + (int) ((r - 26) * 1.1) - (int) (drop * 14 * n.rnd(x, 0, z, 191));
            if (gate) hs = Math.min(hs, 0);
            for (int y = 0; y < hs; y++) set(out, FLOOR + y, brick(x, FLOOR + y, z, DEEPSLATE_BRICKS, CRACKED_DEEPSLATE_BRICKS));
            if (hs > 0 && (int) Math.floor(r) % 2 == 0) set(out, FLOOR + hs - 1, stairs(DEEPSLATE_TILE_STAIRS, Pal.dir(dx, dz), false));
            return;
        }
        if (r <= 54) {                                           // fachada de 3 pisos
            int storey = 14;
            int hf = (int) (storey * 3 + 4 - drop * 40 - n.rnd((int) (deg / 4), 0, 0, 192) * 5);
            double p = (deg / 4) - Math.floor(deg / 4);
            for (int y = 0; y < hf; y++) {
                int ly = y % storey;
                boolean outer = r > 50;
                boolean inner = r <= 47.5;
                boolean archOk = p > 0.25 && p < 0.9;
                double q = (p - 0.575) / 0.325;
                boolean arch = archOk && ly >= 1 && ly <= 8 + 4 * Math.sqrt(Math.max(0, 1 - q * q));
                if (gate && y < 16) continue;
                if ((outer || inner) && arch) continue;
                if (!outer && !inner && ly != 0) continue;       // galería entre muros
                set(out, FLOOR + y, ly == 0 ? POLISHED_BLACKSTONE : brick(x, FLOOR + y, z, DEEPSLATE_BRICKS, CRACKED_DEEPSLATE_BRICKS));
            }
            for (int y = hf - 3; y < hf; y++) if (n.rnd(x, y, z, 193) < 0.45) set(out, FLOOR + y, AIR);
            if (r > 53 && p < 0.1 && hf > 30 && drop == 0) set(out, FLOOR + hf, ASTRAL_FIRE);
        }
        // r 54..62: plaza que rodea al coliseo (sin muros del laberinto)
    }
}
