package com.agustin.bloodmoon.world.design;

import static com.agustin.bloodmoon.world.design.Pal.*;

/**
 * Coliseo del Vacío (x5): ~500 bloques de diámetro y ~190 de alto. Diseño en anillos, como el real:
 *
 *  r ≤ 140   ARENA: piso con anillos y radios, hipogeo (laberinto subterráneo) visible donde el piso se hundió,
 *            zigurat central de 8 niveles con escalinatas, braseros y el PORTAL del Vacío en la cima,
 *            8 obeliscos de obsidiana con bandas de obsidiana llorona.
 *  140-150   PODIO: muro de 13 con parapeto, braseros y puertas de gladiadores.
 *  150-222   CÁVEA: gradas escalonadas (escaleras como asientos), dos pasillos (praecinctiones),
 *            por dentro dos anillos de galerías abovedadas (ambulacra) y pasajes radiales.
 *  222-247   FACHADA: muro interior con arcadas, galería perimetral de 8 pisos con pisos y fuego,
 *            muro exterior con 144 arcos por piso, pilastras con plinto y capitel, cornisas
 *            con ménsulas, ático con ventanas. Sectores derrumbados y cima erosionada.
 *  247-275   escombros al pie de los sectores caídos.
 *
 * h = altura sobre el piso de la arena (el piso está en h = -1). Todo determinista a partir de la semilla.
 * Principios de diseño aplicados: profundidad (pilastras y cornisas salientes, arcos rehundidos),
 * gradientes de textura (grietas y bloques distintos según altura y ruido), silueta rota (erosión),
 * ritmo (bahías regulares) y luz como composición (fuego astral en braseros, galerías y cima).
 */
public final class ColiseumDesign {
    public static final int R_ARENA = 140, R_PODIUM = 150, R_CAVEA = 222, R_INNER = 226, R_GAL = 230, R_WALL = 238,
            R_FACADE = 242, R_PIL = 245, R_OUT = 247, R_RUBBLE = 275;
    public static final int MIN_H = -16, TOP = 200, STOREY = 22, STOREYS = 8, FACADE_H = STOREY * STOREYS + 12, PODIUM_H = 13;
    public static final int ALTAR_R = 48, ALTAR_TIERS = 8, TIER_H = 5, ALTAR_TOP = ALTAR_TIERS * TIER_H, STAIR_FOOT = 50;
    public static final int H_COUNT = TOP - MIN_H + 1;
    static final double BAY = 2.5;          // grados por bahía (144 arcos)
    static final int TOP_SEAT = seatHeight(R_CAVEA);

    private final Noise n;

    public ColiseumDesign(long seed) {
        this.n = new Noise(seed);
    }

    /** ¿La columna pertenece a la estructura (se aplana y se rellena)? */
    public static boolean inFootprint(double r) {
        return r <= R_OUT;
    }

    // ------------------------------------------------------------------ perfil

    /** Altura (exclusiva) de las gradas en el radio r, sin erosión. */
    static int seatHeight(double r) {
        // tres sectores (maeniana) cada vez más empinados, separados por pasillos planos de 3
        double eff = Math.max(0, r - R_PODIUM);
        int step = (int) Math.floor(Math.min(eff, 24) / 2);
        int h = 14 + 2 * step;
        if (eff >= 27) h += 3 * (int) Math.floor((Math.min(eff, 47) - 27) / 2);
        if (eff >= 50) h += 4 * (int) Math.floor((eff - 50) / 2);
        return h;
    }

    /** Pisos derrumbados de la fachada según el ángulo (0 = intacta). */
    double drop(double deg) {
        double c = n.angular(deg, 1);
        return c > 0.6 ? (c - 0.6) / 0.4 * 6.0 : 0.0;
    }

    int facadeHeight(double deg) {
        int bay = (int) Math.floor(deg / BAY);
        return (int) (FACADE_H - drop(deg) * STOREY - n.rnd(bay, 0, 0, 11) * 7);
    }

    /** Altura de escombros fuera del coliseo (sobre el terreno local). */
    public int rubbleHeight(int dx, int dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r <= R_OUT || r > R_RUBBLE) return 0;
        double deg = angle(dx, dz);
        double d = drop(deg);
        if (d < 1) return 0;
        double k = 1 - (r - R_OUT) / (R_RUBBLE - R_OUT);
        double h = d * 2.6 * k * (0.4 + 0.9 * n.smooth2(dx, dz, 9, 31)) - 1.5;
        return Math.max(0, (int) h);
    }

    public int rubbleBlock(int x, int y, int z) {
        double v = n.rnd(x, y, z, 32);
        return v < 0.35 ? COBBLED_DEEPSLATE : v < 0.6 ? CRACKED_DEEPSLATE_BRICKS : v < 0.75 ? DEEPSLATE_BRICKS
                : v < 0.85 ? CRACKED_PB_BRICKS : v < 0.93 ? BLACKSTONE : VOID_STONE;
    }

    static double angle(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dz, dx));
        return a < 0 ? a + 360 : a;
    }

    // ------------------------------------------------------------------ columna

    /**
     * Llena out[h - MIN_H] para h en [MIN_H, TOP]. AIR = vacío (se despeja).
     * Debajo de MIN_H el generador rellena cimientos hasta el terreno.
     */
    public void fillColumn(int dx, int dz, int[] out) {
        double r = Math.sqrt(dx * dx + dz * dz);
        double deg = angle(dx, dz);
        for (int i = 0; i < H_COUNT; i++) out[i] = AIR;
        if (r > R_OUT) return;

        // cimiento sólido común
        for (int h = MIN_H; h < -1; h++) set(out, h, foundation(dx, h, dz));

        if (r <= R_ARENA) arena(dx, dz, r, deg, out);
        else if (r <= R_PODIUM) podium(dx, dz, r, deg, out);
        else if (r <= R_CAVEA) cavea(dx, dz, r, deg, out);
        else facade(dx, dz, r, deg, out);

        if (r > R_ARENA) carvePassages(dx, dz, r, deg, out);
    }

    private static void set(int[] out, int h, int code) {
        if (h >= MIN_H && h <= TOP) out[h - MIN_H] = code;
    }

    private static int get(int[] out, int h) {
        return h >= MIN_H && h <= TOP ? out[h - MIN_H] : AIR;
    }

    private int foundation(int dx, int h, int dz) {
        return n.rnd(dx, h, dz, 2) < 0.7 ? DEEPSLATE_TILES : COBBLED_DEEPSLATE;
    }

    /** Ladrillo con grietas que aumentan con la altura y en las zonas derrumbadas. */
    private int brick(int dx, int h, int dz, double decay, int base, int cracked) {
        double v = n.rnd(dx, h, dz, 3);
        double crack = 0.12 + 0.25 * Math.min(1, Math.max(0, h) / 180.0) + 0.3 * decay;
        if (v < crack) return cracked;
        double t = n.smooth2(dx + h * 0.7, dz - h * 0.4, 14, 4);
        if (t > 0.78) return TUFF_BRICKS;
        if (v > 0.985) return VOID_STONE;
        return base;
    }

    // ------------------------------------------------------------------ arena + altar + obeliscos

    private void arena(int dx, int dz, double r, double deg, int[] out) {
        // piso
        boolean sunk = r > 56 && r < 128 && n.smooth2(dx, dz, 38, 5) > 0.63;
        double edgeN = n.smooth2(dx, dz, 38, 5);
        if (sunk) {
            hypogeum(dx, dz, out, edgeN);
        } else {
            set(out, -1, arenaFloor(dx, dz, r, deg));
        }

        if (r <= ALTAR_R + 3) {
            altar(dx, dz, r, deg, out);
            return;
        }
        obelisks(dx, dz, out);
        // braseros del borde de la arena
        if (r > 133 && r <= 135 && deg % 7.5 < 0.45) {
            set(out, 0, GILDED_BLACKSTONE);
            set(out, 1, ASTRAL_FIRE);
        }
    }

    private int arenaFloor(int dx, int dz, double r, double deg) {
        if (Math.abs(r - 60) < 0.7 || Math.abs(r - 100) < 0.7 || Math.abs(r - 128) < 0.7) return CHISELED_PB;
        if (Math.abs(r - 61.5) < 0.6 || Math.abs(r - 126.5) < 0.6) return POLISHED_DEEPSLATE;
        if (r > ALTAR_R + 4 && (deg % 22.5) * r * Math.PI / 180 < 2.0) return POLISHED_DEEPSLATE;
        double s = n.smooth2(dx, dz, 11, 6);
        if (s > 0.8) return VOID_STONE;
        if (r > 100 && r < 128) return n.rnd(dx, -1, dz, 7) < 0.2 ? CRACKED_DEEPSLATE_TILES : DEEPSLATE_TILES;
        return n.rnd(dx, -1, dz, 8) < 0.22 ? CRACKED_PB_BRICKS : PB_BRICKS;
    }

    /** Hipogeo: celdas de 8 con muros de 2, puertas al azar; el piso de arena se hundió encima. */
    private void hypogeum(int dx, int dz, int[] out, double edge) {
        int gx = Math.floorMod(dx, 8), gz = Math.floorMod(dz, 8);
        int cx = Math.floorDiv(dx, 8), cz = Math.floorDiv(dz, 8);
        boolean wallX = gx < 2, wallZ = gz < 2;
        boolean door = (wallX && !wallZ && gz >= 3 && gz <= 5 && n.rnd(cx, 0, cz, 41) < 0.55)
                || (wallZ && !wallX && gx >= 3 && gx <= 5 && n.rnd(cx, 1, cz, 41) < 0.55);
        int top = -2 - (int) ((edge - 0.63) * 18 * n.rnd(dx, 0, dz, 42));   // muros más rotos al centro del hundimiento
        set(out, MIN_H, DEEPSLATE_TILES);
        for (int h = MIN_H + 1; h <= -1; h++) {
            boolean wall = (wallX || wallZ) && !door && h <= top;
            if (wall) set(out, h, h == top ? CRACKED_PB_BRICKS : brick(dx, h, dz, 0.4, PB_BRICKS, CRACKED_PB_BRICKS));
            else set(out, h, AIR);
        }
        if (!(wallX || wallZ)) {
            if (n.rnd(dx, 0, dz, 43) < 0.012) set(out, MIN_H + 1, CHEST | (n.rnd(cx, 2, cz, 44) < 0.5 ? 0 : 1 << 8));
            else if (gx == 4 && gz == 4 && n.rnd(cx, 3, cz, 45) < 0.3) set(out, MIN_H + 1, ASTRAL_FIRE);
            else if (n.rnd(dx, 1, dz, 46) < 0.05) set(out, -2, COBWEB);
        }
    }

    private void altar(int dx, int dz, double r, double deg, int[] out) {
        int tiers = Math.min(ALTAR_TIERS, Math.max(0, (int) Math.floor((ALTAR_R - r) / 6.0) + 1));
        if (Math.max(Math.abs(dx), Math.abs(dz)) <= 6) tiers = ALTAR_TIERS;   // plataforma cuadrada en la cima
        int solid = tiers * TIER_H;
        // escalinatas en los 4 ejes, talladas en el zigurat: rampa continua 1:1 desde el suelo
        // (t = 49, h = 0) hasta la cima (t = 10, h = 40) y descanso plano hasta la plataforma
        int ax = Math.abs(dx), az = Math.abs(dz);
        boolean onX = az <= 4 && ax >= 6, onZ = ax <= 4 && az >= 6;
        int along = onX ? ax : onZ ? az : 0;
        int stairTop = (onX || onZ) ? Math.max(1, Math.min(ALTAR_TOP, STAIR_FOOT - along)) : 0;
        int face = Pal.dir(-dx, -dz);       // se sube hacia el centro
        boolean stair = (onX || onZ) && along <= STAIR_FOOT - 1;
        if (!stair && r > ALTAR_R + 0.5) return;
        int top = stair ? stairTop : solid;
        for (int h = 0; h < top; h++) {
            int code;
            if (stair && h == top - 1) code = top < ALTAR_TOP ? stairs(PB_BRICK_STAIRS, face, false) : POLISHED_DEEPSLATE;
            else if (stair) code = h % TIER_H == TIER_H - 1 ? CHISELED_PB : POLISHED_BLACKSTONE;
            else {
                int k = h / TIER_H, ring = ALTAR_R - 6 * k;
                boolean edge = r > ring - 1.2;
                int local = h % TIER_H;
                if (edge && local == TIER_H - 1) code = CHISELED_PB;
                else if (edge && (deg % 10) < 1.2) code = POLISHED_BASALT;
                else if (edge && local == 2 && (deg % 10) > 4 && (deg % 10) < 6) code = CRYING_OBSIDIAN;
                else code = brick(dx, h, dz, 0.15, PB_BRICKS, CRACKED_PB_BRICKS);
            }
            set(out, h, code);
        }
        // barandas de las escalinatas con braseros
        boolean rail = (az == 5 && ax >= 8 || ax == 5 && az >= 8) && (az == 5 ? ax : az) < STAIR_FOOT - 1;
        if (rail) {
            int railAlong = az == 5 ? ax : az;
            int base = Math.max(solid, Math.min(ALTAR_TOP, STAIR_FOOT - railAlong));
            for (int h = solid; h < base; h++) set(out, h, PB_BRICKS);          // muro lateral bajo la baranda
            set(out, base, railAlong % 8 == 0 ? GILDED_BLACKSTONE : PB_BRICK_WALL);
            if (railAlong % 8 == 0) set(out, base + 1, ASTRAL_FIRE);
        }
        // braseros en el borde de cada nivel
        if (!stair && tiers > 0 && tiers < ALTAR_TIERS && r > ALTAR_R - 6 * (tiers - 1) - 1.2 && (deg + 15) % 30 < 1.5) {
            set(out, solid, GILDED_BLACKSTONE);
            set(out, solid + 1, ASTRAL_FIRE);
        }
        if (tiers == ALTAR_TIERS) altarTop(dx, dz, out);
    }

    /**
     * Cima: plataforma de obsidiana y el marco del portal INCOMPLETO (falta la parte superior derecha):
     * hay que completarlo con Bloques del Vacío para poder encenderlo. Dos bloques caídos quedan sobre la plataforma.
     */
    private void altarTop(int dx, int dz, int[] out) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        set(out, ALTAR_TOP - 1, (dx + dz) % 2 == 0 ? CRYING_OBSIDIAN : OBSIDIAN);
        int base = ALTAR_TOP;
        // marco: interior 3 de ancho x 5 de alto, en el plano z = 0
        if (dz == 0 && ax <= 2) {
            for (int y = 0; y <= 6; y++) {
                boolean frame = ax == 2 || y == 0 || y == 6;
                boolean broken = (dx == 2 && y >= 4) || (y == 6 && dx >= 0);
                set(out, base + y, frame && !broken ? VOID_BLOCK : AIR);
            }
        }
        if ((dx == 3 && dz == 2) || (dx == -2 && dz == -3)) set(out, base, VOID_BLOCK);   // restos del marco
        // cuatro pilares con cadenas y fuego
        if (ax == 4 && az == 4) {
            for (int y = 0; y < 9; y++) set(out, base + y, POLISHED_BASALT);
            set(out, base + 9, GILDED_BLACKSTONE);
            set(out, base + 10, ASTRAL_FIRE);
        }
    }

    private void obelisks(int dx, int dz, int[] out) {
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            int ox = (int) Math.round(Math.cos(a) * 82), oz = (int) Math.round(Math.sin(a) * 82);
            int lx = Math.abs(dx - ox), lz = Math.abs(dz - oz);
            if (lx > 3 || lz > 3) continue;
            int m = Math.max(lx, lz);
            int height = 40 + (int) (n.rnd(k, 0, 0, 50) * 14);
            if (m == 3) {                 // pedestal
                for (int h = 0; h < 3; h++) set(out, h, h == 2 ? stairs(PB_BRICK_STAIRS, Pal.dir(dx - ox, dz - oz), false) : CHISELED_PB);
                return;
            }
            for (int h = 0; h < height; h++) {
                int width = h < 6 ? 2 : h < height * 0.75 ? 1 : 0;
                if (m > width) break;
                set(out, h, h % 7 == 3 ? CRYING_OBSIDIAN : OBSIDIAN);
            }
            if (m == 0) {
                set(out, height, GILDED_BLACKSTONE);
                set(out, height + 1, ASTRAL_FIRE);
            }
            return;
        }
    }

    // ------------------------------------------------------------------ podio

    private void podium(int dx, int dz, double r, double deg, int[] out) {
        set(out, -1, DEEPSLATE_TILES);
        for (int h = 0; h < PODIUM_H; h++) {
            int code = brick(dx, h, dz, 0.1, PB_BRICKS, CRACKED_PB_BRICKS);
            if (h == PODIUM_H - 2) code = CHISELED_PB;
            if (r <= R_ARENA + 1.5 && h >= 3 && h <= 8 && (deg % 3) > 1.0 && (deg % 3) < 2.0) code = AIR;   // nichos
            if (r <= R_ARENA + 1.5 && h == 2 && (deg % 3) > 1.2 && (deg % 3) < 1.8) code = CRYING_OBSIDIAN;
            set(out, h, code);
        }
        if (r <= R_ARENA + 1.5) {
            boolean brazier = deg % 6 < 0.6;
            set(out, PODIUM_H, brazier ? GILDED_BLACKSTONE : PB_BRICK_WALL);
            if (brazier) set(out, PODIUM_H + 1, ASTRAL_FIRE);
        } else {
            set(out, PODIUM_H, DEEPSLATE_TILES);   // pasillo del podio
        }
    }

    // ------------------------------------------------------------------ cávea

    private void cavea(int dx, int dz, double r, double deg, int[] out) {
        double d = drop(deg);
        int hs = seatHeight(r);
        if (d > 0.5) hs -= (int) (d * 3.2 * (0.3 + 0.7 * n.smooth2(dx, dz, 7, 12)));
        hs = Math.max(PODIUM_H + 1, hs);
        set(out, -1, DEEPSLATE_TILES);
        double eff = r - R_PODIUM;
        boolean band = (eff >= 24 && eff < 27) || (eff >= 47 && eff < 50);
        boolean lip = !band && (Math.floor(eff) % 2 == 0);
        int outward = Pal.dir(dx, dz);
        for (int h = 0; h < hs; h++) {
            int code;
            if (h == hs - 1) {
                if (band) code = n.rnd(dx, h, dz, 13) < 0.2 ? CRACKED_DEEPSLATE_TILES : POLISHED_DEEPSLATE;
                else if (lip && d < 0.5) code = stairs(DEEPSLATE_TILE_STAIRS, outward, false);
                else code = n.rnd(dx, h, dz, 14) < 0.2 ? CRACKED_DEEPSLATE_TILES : DEEPSLATE_TILES;
            } else {
                code = brick(dx, h, dz, Math.min(1, d / 4), DEEPSLATE_BRICKS, CRACKED_DEEPSLATE_BRICKS);
            }
            set(out, h, code);
        }
        // parapeto (balteus) al borde interior de cada pasillo
        if ((eff >= 24 && eff < 25) || (eff >= 47 && eff < 48)) {
            set(out, hs, deg % 4 < 0.5 ? GILDED_BLACKSTONE : DEEPSLATE_BRICK_WALL);
            if (deg % 4 < 0.5) set(out, hs + 1, ASTRAL_FIRE);
        }
        // grietas con cristales de amatista en las gradas rotas
        if (d > 1 && n.rnd(dx, hs, dz, 15) < 0.02) set(out, hs, AMETHYST_CLUSTER);
    }

    // ------------------------------------------------------------------ fachada

    private void facade(int dx, int dz, double r, double deg, int[] out) {
        double d = drop(deg);
        int hf = facadeHeight(deg);
        int bay = (int) Math.floor(deg / BAY);
        double p = (deg / BAY) - bay;                    // posición dentro de la bahía [0,1)
        boolean pilasterBay = p < 0.22;
        int topSeat = TOP_SEAT;
        set(out, -1, DEEPSLATE_TILES);

        if (r <= R_INNER) {
            // pasillo superior de las gradas
            int ht = Math.max(PODIUM_H + 1, topSeat - (int) (d * 3));
            for (int h = 0; h < ht; h++) set(out, h, brick(dx, h, dz, 0, DEEPSLATE_BRICKS, CRACKED_DEEPSLATE_BRICKS));
            if (r <= R_CAVEA + 1) set(out, ht, deg % 2.5 < 0.4 ? GILDED_BLACKSTONE : DEEPSLATE_BRICK_WALL);
            return;
        }
        if (r <= R_GAL) {
            // muro interior: macizo bajo las gradas, arcadas encima
            int top = hf - 6;
            for (int h = 0; h < top; h++) {
                boolean open = h >= topSeat && archOpen(h, p, false);
                set(out, h, open ? AIR : brick(dx, h, dz, d / 4, PB_BRICKS, CRACKED_PB_BRICKS));
            }
            erodeTop(dx, dz, top, out);
            return;
        }
        if (r <= R_WALL) {
            // galería perimetral: pisos cada 22, fuego cada 3 bahías, muros transversales cada 6
            int top = hf - 3;
            boolean cross = bay % 6 == 0 && p < 0.15;
            for (int h = 0; h < top; h++) {
                int y = h % STOREY;
                int code = AIR;
                if (h >= STOREY && y <= 1) code = y == 1 ? DEEPSLATE_TILES : DEEPSLATE_BRICKS;
                if (cross && !(y >= 2 && y <= 9 && Math.abs(r - 234) < 2.5)) code = brick(dx, h, dz, d / 4, DEEPSLATE_BRICKS, CRACKED_DEEPSLATE_BRICKS);
                set(out, h, code);
            }
            for (int s = 0; s * STOREY < top - 3; s++) {
                int floorTop = s == 0 ? 0 : s * STOREY + 2;
                if (bay % 3 == 1 && p > 0.45 && p < 0.55 && Math.abs(r - 234) < 0.8 && floorTop < top) {
                    set(out, floorTop, ASTRAL_FIRE);
                }
                if (bay % 3 == 2 && p > 0.5 && p < 0.58 && Math.abs(r - 232) < 0.8 && n.rnd(bay, s, 0, 60) < 0.25 && floorTop < top) {
                    set(out, floorTop, CHEST | (Pal.dir(-dx, -dz) << 8));
                }
                // cadenas colgando del techo de cada piso
                if (s > 0 && bay % 4 == 0 && p > 0.6 && p < 0.66 && Math.abs(r - 235) < 0.7) {
                    for (int y = 1; y <= 4; y++) if (s * STOREY - y > 2) set(out, s * STOREY - y, CHAIN);
                }
                if (n.rnd(dx, s, dz, 61) < 0.03 && s * STOREY + 3 < top) set(out, s * STOREY + 3 + (int) (n.rnd(dx, s, dz, 62) * 15), COBWEB);
            }
            return;
        }
        if (r <= R_FACADE) {
            // muro exterior con arcos
            for (int h = 0; h < hf; h++) {
                int s = h / STOREY, y = h % STOREY;
                boolean attic = s >= STOREYS;
                boolean open;
                if (attic) open = p > 0.45 && p < 0.72 && y >= 4 && y <= 8;
                else open = archOpen(h, p, s == 0);
                int code;
                if (open) code = AIR;
                else if (y == STOREY - 1 && !attic) code = POLISHED_BLACKSTONE;        // cornisa
                else if (y == 0 && s > 0 && !attic) code = CHISELED_TUFF_BRICKS;     // banda
                else if (!attic && archKeystone(h, p, s == 0)) code = CHISELED_DEEPSLATE;
                else code = brick(dx, h, dz, d / 4, DEEPSLATE_BRICKS, CRACKED_DEEPSLATE_BRICKS);
                set(out, h, code);
            }
            erodeTop(dx, dz, hf, out);
            return;
        }
        // r in (242, 247]: pilastras, plintos, capiteles, cornisas y ménsulas
        int pilTop = hf;
        if (pilasterBay && n.rnd(bay, 0, 0, 63) < 0.22) pilTop = hf + (int) (STOREY * n.rnd(bay, 1, 0, 64));
        for (int h = 0; h < Math.min(pilTop, FACADE_H); h++) {
            int s = h / STOREY, y = h % STOREY;
            boolean attic = s >= STOREYS;
            int code = AIR;
            if (pilasterBay && !attic) {
                if (r <= R_PIL) code = (y >= STOREY - 3 ? CHISELED_DEEPSLATE : POLISHED_DEEPSLATE);
                else if (r <= R_PIL + 1 && (y <= 1 || y == STOREY - 3)) code = POLISHED_BLACKSTONE;   // plinto y capitel
            }
            if (!attic && y == STOREY - 1 && r <= 243.5) code = stairs(PB_BRICK_STAIRS, Pal.dir(-dx, -dz), true); // cornisa
            if (!attic && y == STOREY - 2 && r <= 243.0 && !pilasterBay && p > 0.3 && p < 0.9 && (int) (p * 10) % 2 == 0)
                code = stairs(DEEPSLATE_BRICK_STAIRS, Pal.dir(-dx, -dz), true);                                    // ménsulas
            if (attic && y == 10 && r <= 243.5) code = stairs(DEEPSLATE_BRICK_STAIRS, Pal.dir(-dx, -dz), true);
            if (code != AIR) set(out, h, code);
        }
        // fuego sobre las pilastras que quedaron en pie
        if (pilasterBay && r <= R_PIL && p > 0.08 && p < 0.14 && pilTop < FACADE_H && get(out, pilTop - 1) != AIR) {
            set(out, pilTop, ASTRAL_FIRE);
        }
        // cadenas colgando de los capiteles
        if (pilasterBay && r > R_PIL && r <= R_PIL + 1 && p > 0.09 && p < 0.13 && bay % 2 == 0) {
            for (int s = 1; s < STOREYS; s++) {
                int base = s * STOREY - 3;
                if (base < hf - 2) for (int y = 1; y <= 5; y++) set(out, base - y, CHAIN);
            }
        }
    }

    /** Arco de medio punto: ancho 60 % de la bahía; planta baja más alta (entradas). */
    private static boolean archOpen(int h, double p, boolean ground) {
        int y = h % STOREY;
        if (p < 0.28 || p > 0.92) return false;
        double q = (p - 0.6) / 0.32;
        double crown = Math.sqrt(Math.max(0, 1 - q * q));
        if (ground) return y >= 0 && y <= 11 + 6 * crown && h < STOREY;
        return y >= 2 && y <= 9 + 7 * crown;
    }

    private static boolean archKeystone(int h, double p, boolean ground) {
        int y = h % STOREY;
        double q = (p - 0.6) / 0.32;
        if (Math.abs(q) > 0.25) return false;
        int crown = ground ? 17 : 16;
        return y == crown + 1;
    }

    /** Cima irregular: quita bloques al azar en los últimos 4 y deja telarañas. */
    private void erodeTop(int dx, int dz, int top, int[] out) {
        for (int h = top - 4; h < top; h++) {
            if (h < 0) continue;
            double k = (h - (top - 4)) / 4.0;
            if (n.rnd(dx, h, dz, 70) < 0.25 + 0.5 * k) set(out, h, AIR);
        }
    }

    // ------------------------------------------------------------------ portones y pasajes

    private void carvePassages(int dx, int dz, double r, double deg, int[] out) {
        // 4 portones monumentales (ejes) y 4 diagonales; pasajes radiales cada 15°
        for (int k = 0; k < 8; k++) {
            double a = k * 45.0;
            boolean grand = k % 2 == 0;
            double perp = perpDistance(dx, dz, a);
            if (perp < 0) continue;
            double hw = grand ? 8 : 5;
            int height = grand ? 26 : 16;
            if (perp <= hw) {
                double crown = Math.sqrt(Math.max(0, 1 - (perp / hw) * (perp / hw)));
                int roof = (int) (height + hw * 0.6 * crown);
                for (int h = 0; h < roof; h++) set(out, h, AIR);
                set(out, -1, perp < 1.5 ? CRYING_OBSIDIAN : DEEPSLATE_TILES);
                if (Math.abs(perp - hw + 0.5) < 0.6 && ((int) r) % 10 == 0) {        // antorchas del túnel
                    set(out, 0, GILDED_BLACKSTONE);
                    set(out, 1, ASTRAL_FIRE);
                }
                return;
            }
            if (perp <= hw + 2 && r > R_FACADE - 1 && r <= R_FACADE) {             // marco del portón
                int roof = (int) (height + hw * 0.6) + 2;
                for (int h = 0; h < roof; h++) set(out, h, h % 4 == 0 ? CRYING_OBSIDIAN : CHISELED_PB);
                return;
            }
        }
        // pasajes radiales hacia la arena y los ambulacros
        if (r < R_GAL) {
            double nearest = Math.round(deg / 15.0) * 15.0;
            if (((int) Math.round(nearest)) % 45 != 0) {
                double perp = perpDistance(dx, dz, nearest);
                if (perp >= 0 && perp <= 2.5) {
                    double crown = Math.sqrt(Math.max(0, 1 - (perp / 2.5) * (perp / 2.5)));
                    for (int h = 0; h < 7 + crown * 2; h++) set(out, h, AIR);
                    return;
                }
            }
        }
        // ambulacros (galerías anulares abovedadas dentro de la cávea)
        ring(dx, dz, r, deg, out, 158, 164, 10);
        ring(dx, dz, r, deg, out, 188, 196, 13);
    }

    private void ring(int dx, int dz, double r, double deg, int[] out, double r0, double r1, int height) {
        if (r < r0 || r >= r1) return;
        double mid = (r0 + r1) / 2, hw = (r1 - r0) / 2;
        double q = (r - mid) / hw;
        double crown = Math.sqrt(Math.max(0, 1 - q * q));
        int roof = (int) (height - 3 + 3 * crown);
        for (int h = 0; h < roof; h++) set(out, h, AIR);
        if (Math.abs(r - mid) < 0.6 && deg % 8 < 0.35) set(out, 0, ASTRAL_FIRE);
        if (Math.abs(r - r0) < 0.8 && n.rnd((int) deg, (int) r0, 0, 80) < 0.08 && deg % 1 < 0.3) {
            set(out, 0, CHEST | (Pal.dir(dx, dz) << 8));
        }
        if (n.rnd(dx, 0, dz, 81) < 0.015) set(out, roof - 1, COBWEB);
    }

    /** Distancia perpendicular al rayo con ángulo a (grados) desde el centro; -1 si está del otro lado. */
    private static double perpDistance(double dx, double dz, double a) {
        double ca = Math.cos(Math.toRadians(a)), sa = Math.sin(Math.toRadians(a));
        double along = dx * ca + dz * sa;
        if (along < 0) return -1;
        return Math.abs(-dx * sa + dz * ca);
    }
}
