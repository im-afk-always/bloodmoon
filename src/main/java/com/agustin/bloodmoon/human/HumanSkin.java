package com.agustin.bloodmoon.human;

import java.util.Random;

/**
 * Pintor de apariencias humanas: arma una skin de jugador de 64×64 (formato estándar, ARGB) a partir de una semilla.
 * No usa skins de nadie: todo sale de rasgos combinados — sexo, edad, tono de piel, ojos, cejas, nariz, boca, pecas,
 * pelo (estilo y color), barba, complexión, ropa por cultura y riqueza, y el atuendo del oficio. Java puro, sin
 * dependencias de Minecraft (se prueba fuera del juego).
 */
public final class HumanSkin {
    public static final int SIZE = 64;
    private static final int CLEAR = 0;

    /** Rasgos derivados de la semilla (el cliente los usa también para decidir brazos finos). */
    public static final class Traits {
        public boolean female, slim, old, young;
        public int skin, skinDark, skinLight, hair, hairDark, eye, lip;
        public int hairStyle, beard, eyeRow, eyeGap, brow, mouthW;
        public boolean freckles, glasses, earring;
        public int wealth;   // 0 pobre, 1 común, 2 acomodado
        public int cloth1, cloth2, cloth3, leather;
    }

    private final int[] px = new int[SIZE * SIZE];
    private final Random r;
    private final Traits t;
    private final HumanJob job;
    private final Culture culture;
    private final int armW;

    private HumanSkin(long seed, HumanJob job, Culture culture) {
        this.r = new Random(seed * 0x5DEECE66DL + 11);
        this.job = job;
        this.culture = culture;
        this.t = traits(seed, culture);
        this.armW = t.slim ? 3 : 4;
    }

    /** Pinta la skin; {@code out[0]} queda en 1 si los brazos son finos. */
    public static int[] paint(long seed, HumanJob job, Culture culture) {
        HumanSkin s = new HumanSkin(seed, job, culture);
        s.paintAll();
        return s.px;
    }

    // ------------------------------------------------------------------ rasgos

    private static final int[][] SKIN = {
            {0xF6, 0xD7, 0xC3}, {0xEE, 0xC1, 0x9E}, {0xE0, 0xAC, 0x82}, {0xC8, 0x8E, 0x62},
            {0xA8, 0x6E, 0x48}, {0x86, 0x55, 0x37}, {0x64, 0x3E, 0x28}, {0x48, 0x2C, 0x1E}};
    private static final int[] HAIR = {0x15110F, 0x2B1D14, 0x4A2E1B, 0x6B4226, 0x8C5A2E, 0xA0522D, 0xB86B2A, 0xD2A24C, 0xE3C780, 0xB9AFA2};
    private static final int[] EYES = {0x3B2314, 0x24170E, 0x6B4E24, 0x4C6A2F, 0x3D6FA8, 0x6C7A85, 0x8A6A1E, 0x2E5E4E};

    public static Traits traits(long seed, Culture culture) {
        Random r = new Random(seed ^ 0x9E3779B97F4A7C15L);
        Traits t = new Traits();
        t.female = r.nextBoolean();
        t.slim = t.female ? r.nextFloat() < 0.8F : r.nextFloat() < 0.1F;
        float age = r.nextFloat();
        t.old = age > 0.85F;
        t.young = age < 0.2F;
        // tono de piel: continuo entre anclas; el desierto tiende a tonos más morenos
        double pos = culture == Culture.DESERT ? 2.0 + r.nextGaussian() * 1.4 : 1.6 + r.nextGaussian() * 1.5;
        pos = Math.max(0, Math.min(SKIN.length - 1.001, pos));
        int i = (int) pos;
        double f = pos - i;
        int rr = (int) (SKIN[i][0] * (1 - f) + SKIN[i + 1][0] * f), gg = (int) (SKIN[i][1] * (1 - f) + SKIN[i + 1][1] * f),
                bb = (int) (SKIN[i][2] * (1 - f) + SKIN[i + 1][2] * f);
        t.skin = rgb(rr + r.nextInt(9) - 4, gg + r.nextInt(9) - 4, bb + r.nextInt(9) - 4);
        t.skinDark = shade(t.skin, 0.8);
        t.skinLight = shade(t.skin, 1.08);
        int hb = HAIR[weighted(r, culture == Culture.DESERT ? new int[]{30, 30, 18, 10, 4, 2, 2, 2, 1, 1} : new int[]{8, 14, 16, 14, 10, 6, 6, 10, 8, 2})];
        if (t.old) hb = r.nextBoolean() ? 0xC8C4BE : 0x9E9890;
        t.hair = jitter(r, hb, 10);
        t.hairDark = shade(t.hair, 0.72);
        t.eye = EYES[culture == Culture.DESERT ? weighted(r, new int[]{30, 30, 20, 8, 3, 3, 4, 2}) : r.nextInt(EYES.length)];
        t.lip = mix(t.skin, t.female ? 0xB0505A : 0x8C4A3E, t.female ? 0.45 : 0.3);
        t.hairStyle = t.female ? 5 + r.nextInt(7) : r.nextInt(8);   // ver hair()
        if (!t.female && t.old && r.nextFloat() < 0.4F) t.hairStyle = 0;   // calvo
        t.beard = t.female || t.young ? 0 : weighted(r, culture == Culture.DESERT ? new int[]{20, 10, 20, 15, 25, 10} : new int[]{40, 15, 15, 10, 15, 5});
        t.eyeRow = 4 + (r.nextFloat() < 0.25F ? -1 : 0);
        t.eyeGap = r.nextFloat() < 0.6F ? 2 : 3;
        t.brow = r.nextInt(3);
        t.mouthW = 2 + r.nextInt(3);
        t.freckles = r.nextFloat() < (pos < 1.2 ? 0.3F : 0.05F);
        t.glasses = r.nextFloat() < 0.06F;
        t.earring = t.female ? r.nextFloat() < 0.3F : r.nextFloat() < 0.06F;
        t.wealth = weighted(r, new int[]{3, 5, 2});
        int[] pal = culture == Culture.DESERT
                ? new int[]{0xE6D7B8, 0xC9A66B, 0xB5542F, 0x2F5D8A, 0xF1EBDD, 0x8C3B2E, 0xD9A441, 0x5E7C4A}
                : new int[]{0x6E4A2E, 0x3E5C3A, 0x5B6B82, 0x8E2F2F, 0x9A8462, 0x3D3A55, 0xC9B58E, 0x2F4A5E};
        t.cloth1 = jitter(r, pal[r.nextInt(pal.length)], 12);
        t.cloth2 = jitter(r, pal[r.nextInt(pal.length)], 12);
        t.cloth3 = jitter(r, pal[r.nextInt(pal.length)], 12);
        t.leather = jitter(r, culture == Culture.DESERT ? 0x8A6238 : 0x5A3A22, 10);
        return t;
    }

    // ------------------------------------------------------------------ geometría del formato de skin

    private interface Shader {
        /** face: 0 arriba, 1 abajo, 2 derecha, 3 frente, 4 izquierda, 5 espalda; (i, j) local; (w, h) de la cara. */
        int at(int face, int i, int j, int w, int h);
    }

    private void box(int u, int v, int w, int h, int d, Shader s) {
        rect(u + d, v, w, d, 0, s);
        rect(u + d + w, v, w, d, 1, s);
        rect(u, v + d, d, h, 2, s);
        rect(u + d, v + d, w, h, 3, s);
        rect(u + d + w, v + d, d, h, 4, s);
        rect(u + 2 * d + w, v + d, w, h, 5, s);
    }

    private void rect(int x0, int y0, int w, int h, int face, Shader s) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            int c = s.at(face, i, j, w, h);
            if (c != -1) px[(y0 + j) * SIZE + x0 + i] = c;
        }
    }

    private int get(int x, int y) {
        return px[y * SIZE + x];
    }

    private void set(int x, int y, int c) {
        px[y * SIZE + x] = c;
    }

    // cajas: cabeza, sombrero, torso, campera, brazos, mangas, piernas, pantalón
    private void head(Shader s) { box(0, 0, 8, 8, 8, s); }
    private void hat(Shader s) { box(32, 0, 8, 8, 8, s); }
    private void body(Shader s) { box(16, 16, 8, 12, 4, s); }
    private void jacket(Shader s) { box(16, 32, 8, 12, 4, s); }
    private void rArm(Shader s) { box(40, 16, armW, 12, 4, s); }
    private void lArm(Shader s) { box(32, 48, armW, 12, 4, s); }
    private void rSleeve(Shader s) { box(40, 32, armW, 12, 4, s); }
    private void lSleeve(Shader s) { box(48, 48, armW, 12, 4, s); }
    private void rLeg(Shader s) { box(0, 16, 4, 12, 4, s); }
    private void lLeg(Shader s) { box(16, 48, 4, 12, 4, s); }
    private void rPants(Shader s) { box(0, 32, 4, 12, 4, s); }
    private void lPants(Shader s) { box(0, 48, 4, 12, 4, s); }

    private void arms(Shader s) { rArm(s); lArm(s); }
    private void sleeves(Shader s) { rSleeve(s); lSleeve(s); }
    private void legs(Shader s) { rLeg(s); lLeg(s); }
    private void pants(Shader s) { rPants(s); lPants(s); }

    // ------------------------------------------------------------------ pintura

    private void paintAll() {
        java.util.Arrays.fill(px, CLEAR);
        // piel en toda la capa base
        Shader skin = (f, i, j, w, h) -> {
            int c = f == 0 ? t.skinLight : f == 1 ? t.skinDark : t.skin;
            if (f >= 2 && j == h - 1) c = shade(c, 0.92);
            return noise(c, 4);
        };
        head(skin);
        body(skin);
        arms(skin);
        legs(skin);
        face();
        clothes();
        hair();
        jobOutfit();
    }

    /** Cara en el frente de la cabeza (8×8, x 8..15, y 8..15). */
    private void face() {
        int fx = 8, fy = 8, er = t.eyeRow;
        int lx = t.eyeGap == 2 ? 2 : 1, rx = t.eyeGap == 2 ? 5 : 6;
        // ojos: blanco + iris (2 px de ancho)
        set(fx + lx - 1 + (t.eyeGap == 2 ? 0 : 0), fy + er, 0xFFEDE8E0);
        set(fx + lx, fy + er, argb(t.eye));
        set(fx + rx, fy + er, argb(t.eye));
        set(fx + rx + 1, fy + er, 0xFFEDE8E0);
        if (t.eyeGap == 3) {
            set(fx + lx + 1, fy + er, argb(shade(t.skin, 0.95)));
            set(fx + rx - 1, fy + er, argb(shade(t.skin, 0.95)));
        }
        // cejas
        int browC = argb(t.old ? 0x9A948C : t.hairDark);
        if (t.brow > 0) {
            set(fx + lx - 1, fy + er - 1, browC);
            set(fx + lx, fy + er - 1, browC);
            set(fx + rx, fy + er - 1, browC);
            set(fx + rx + 1, fy + er - 1, browC);
        } else {
            set(fx + lx, fy + er - 1, browC);
            set(fx + rx, fy + er - 1, browC);
        }
        // nariz: sombra
        set(fx + 3, fy + er + 1, argb(shade(t.skin, 0.86)));
        set(fx + 4, fy + er + 1, argb(shade(t.skin, 0.9)));
        // boca
        int my = fy + er + 3 > fy + 7 ? fy + 7 : fy + er + 3;
        int m0 = 4 - t.mouthW / 2 - (t.mouthW % 2 == 1 ? 0 : 0);
        for (int k = 0; k < t.mouthW; k++) set(fx + m0 + k, my, argb(k == 0 || k == t.mouthW - 1 ? shade(t.lip, 0.85) : t.lip));
        // mejillas, pecas, arrugas
        if (t.female) {
            set(fx + 1, fy + er + 2, argb(mix(t.skin, 0xD47A7A, 0.18)));
            set(fx + 6, fy + er + 2, argb(mix(t.skin, 0xD47A7A, 0.18)));
        }
        if (t.freckles) for (int k = 0; k < 5; k++) {
            int x = fx + 1 + r.nextInt(6), y = fy + er + 1 + r.nextInt(2);
            set(x, y, argb(shade(t.skin, 0.82)));
        }
        if (t.old) {
            set(fx + lx - 1, fy + er + 1, argb(shade(t.skin, 0.88)));
            set(fx + rx + 1, fy + er + 1, argb(shade(t.skin, 0.88)));
        }
        // barba (capa base de la cabeza)
        if (t.beard > 0) beard();
        // anteojos (capa del sombrero)
        if (t.glasses || job == HumanJob.LIBRARIAN && r.nextFloat() < 0.5F) {
            int g = 0xFF3A3A3A, lens = 0x80C8E0F0;
            int hx = 40, hy = 8;
            for (int k : new int[]{lx - 1, lx, rx, rx + 1}) set(hx + k, hy + er, lens);
            set(hx + lx - 1, hy + er - 1, g);
            set(hx + lx, hy + er - 1, g);
            set(hx + rx, hy + er - 1, g);
            set(hx + rx + 1, hy + er - 1, g);
            set(hx + 3, hy + er, g);
            set(hx + 4, hy + er, g);
        }
        if (t.earring) {
            set(0 + 6, 8 + 6, 0xFFE0C050);   // costado derecho
            set(16 + 1, 8 + 6, 0xFFE0C050);  // costado izquierdo
        }
    }

    private void beard() {
        int c = argb(t.old ? 0xBEB8B0 : t.hair), d = argb(t.old ? 0x9A948C : t.hairDark);
        int fx = 8, fy = 8, er = t.eyeRow;
        int my = Math.min(fy + 7, fy + er + 3);
        switch (t.beard) {
            case 1 -> { // barba de pocos días
                for (int x = 1; x <= 6; x++) for (int y = my; y <= fy + 7; y++) if (get(fx + x, y) != 0 && r.nextFloat() < 0.5F) set(fx + x, y, mixArgb(get(fx + x, y), d, 0.45));
            }
            case 2 -> { // bigote
                for (int x = 2; x <= 5; x++) set(fx + x, my - 1, c);
            }
            case 3 -> { // candado
                for (int x = 2; x <= 5; x++) set(fx + x, my - 1, c);
                for (int x = 3; x <= 4; x++) set(fx + x, fy + 7, c);
                set(fx + 2, my, c);
                set(fx + 5, my, c);
            }
            case 4, 5 -> { // barba completa (y larga en la capa del sombrero)
                for (int x = 0; x <= 7; x++) for (int y = my - 1; y <= fy + 7; y++) {
                    if (y == my && x >= 4 - t.mouthW / 2 && x < 4 - t.mouthW / 2 + t.mouthW) continue;   // boca
                    set(fx + x, y, (x + y) % 3 == 0 ? d : c);
                }
                for (int y = fy + er + 1; y <= fy + 7; y++) { set(fx, y, c); set(fx + 7, y, c); }
                // costados y debajo
                for (int y = 8 + er + 1; y < 16; y++) { set(0 + 7, y, c); set(16, y, c); }
                for (int x = 16; x < 24; x++) for (int y = 0; y < 8; y++) if (y >= 5) set(x, y, d);   // abajo
                if (t.beard == 5) for (int x = 41; x <= 46; x++) for (int y = 15; y <= 15; y++) set(x, y, c);
            }
            default -> { }
        }
    }

    /** Ropa base según cultura y riqueza. */
    private void clothes() {
        boolean desert = culture == Culture.DESERT;
        int shirt = t.cloth1, trousers = desert ? t.cloth3 : shade(t.cloth2, 0.8), boot = t.leather;
        boolean longSleeve = desert || r.nextFloat() < 0.6F;
        boolean robe = desert && r.nextFloat() < 0.55F || (!desert && t.female && r.nextFloat() < 0.45F);
        int trim = t.wealth == 2 ? 0xD9B44A : shade(shirt, 0.7);
        // torso: camisa/túnica con cuello, botones o cordones y cinturón
        body((f, i, j, w, h) -> {
            int c = shirt;
            if (f == 3 && j == 0 && (i == 3 || i == 4)) return noise(t.skin, 3);   // escote
            if (f == 3 && i == 4 && j >= 1 && j <= 6 && j % 2 == 1) c = shade(shirt, 0.65);   // botones
            if (j >= 8 && j <= 8 && !robe) c = t.leather;   // cinturón
            if (f == 3 && j == 8 && i == 3 && !robe) c = 0xC0A050;   // hebilla
            if (j >= 10 && !robe) c = trousers;
            if (robe && j == 8) c = trim;
            if (f == 3 && (i == 0 || i == w - 1)) c = shade(c, 0.88);
            if (t.wealth == 0 && r.nextFloat() < 0.04F) c = shade(c, 0.75);   // remiendos
            return noise(c, 5);
        });
        // brazos: manga hasta el codo o larga; manos de piel
        arms((f, i, j, w, h) -> {
            if (f == 1) return noise(t.skin, 3);
            int sleeveEnd = longSleeve ? 9 : 4;
            if (j < sleeveEnd || f == 0) {
                int c = j == sleeveEnd - 1 ? shade(shirt, 0.8) : shirt;
                return noise(c, 5);
            }
            return -1;
        });
        // piernas: pantalón o falda/túnica larga; botas o sandalias
        legs((f, i, j, w, h) -> {
            int c = robe ? shirt : trousers;
            if (robe && j < 9) c = shirt;
            if (f == 3 && j == 0 && robe) c = shade(shirt, 0.9);
            if (j >= 10 || f == 1) {
                if (desert && !robe && r.nextFloat() < 0.0F) return -1;
                c = desert ? (j == 11 ? boot : (f == 3 && j == 10 ? noise(t.skin, 3) : boot)) : boot;
                if (j == 10 && !desert) c = shade(boot, 1.12);
            }
            if (robe && j == 9) c = trim;
            return noise(c, 5);
        });
        if (desert) {   // faja de tela
            jacket((f, i, j, w, h) -> j == 8 || j == 9 ? noise(t.cloth2, 6) : -1);
        }
    }

    /** Peinados: 0 calvo, 1 rapado, 2 corto, 3 raya al costado, 4 despeinado, 5 largo lacio, 6 largo ondulado,
     *  7 cola de caballo, 8 rodete, 9 trenzas, 10 enrulado, 11 media melena. */
    private void hair() {
        int st = t.hairStyle;
        if (st == 0) return;
        int c = t.hair, d = t.hairDark;
        Shader top = (f, i, j, w, h) -> noise((i + j) % 4 == 0 ? d : c, 5);
        // capa base de la cabeza: arriba siempre
        if (st == 1) {
            head((f, i, j, w, h) -> f == 0 ? noise(mixArgb(argb(t.skin), argb(c), 0.6), 4) : -1);
            return;
        }
        int fringe = switch (st) { case 3 -> 2; case 4, 10 -> 2; default -> 1; };
        int back = switch (st) { case 5, 6, 9 -> 8; case 7, 8, 11 -> 6; case 10 -> 5; default -> 3; };
        int sides = switch (st) { case 5, 6, 9, 11 -> 7; case 10 -> 5; case 4 -> 3; default -> 2; };
        head((f, i, j, w, h) -> {
            if (f == 0) return top.at(f, i, j, w, h);
            if (f == 1) return -1;
            if (f == 3) {   // frente: flequillo
                if (j < fringe) return noise(st == 3 && i < 3 ? d : c, 5);
                if (st == 3 && j == fringe && i >= 5) return noise(c, 5);
                if ((st == 5 || st == 6 || st == 11) && (i == 0 || i == 7) && j < sides) return noise(d, 5);
                return -1;
            }
            if (f == 5) return j < back ? noise(j == back - 1 ? d : c, 5) : -1;
            // costados
            if (j < sides && (i < 7 || j < 2)) return noise(j == sides - 1 ? d : c, 5);
            return -1;
        });
        // volumen en la capa del sombrero
        if (st == 4 || st == 6 || st == 10 || st == 5) {
            hat((f, i, j, w, h) -> {
                if (f == 0) return r.nextFloat() < (st == 10 ? 0.9F : 0.5F) ? noise(c, 8) : -1;
                if (f == 1 || f == 3) return f == 3 && j == 0 && r.nextFloat() < 0.5F ? noise(c, 6) : -1;
                int len = st == 5 || st == 6 ? 8 : 3;
                return j < len && r.nextFloat() < 0.75F ? noise((i + j) % 3 == 0 ? d : c, 8) : -1;
            });
        }
        if (st == 7) {   // cola de caballo: espalda de la cabeza + nuca
            hat((f, i, j, w, h) -> f == 5 && i >= 3 && i <= 4 && j >= 2 ? noise(d, 5) : -1);
            jacket((f, i, j, w, h) -> f == 5 && i >= 3 && i <= 4 && j < 4 ? noise(c, 5) : -1);
        }
        if (st == 8) {   // rodete
            hat((f, i, j, w, h) -> (f == 5 && i >= 2 && i <= 5 && j <= 3) || (f == 0 && i >= 2 && i <= 5 && j >= 5) ? noise(c, 5) : -1);
        }
        if (st == 9) {   // trenzas sobre los hombros
            jacket((f, i, j, w, h) -> f == 3 && (i == 1 || i == 6) && j < 7 ? noise(j % 2 == 0 ? c : d, 4) : -1);
        }
    }

    /** Atuendo y marcas del oficio (sobre las capas exteriores). */
    private void jobOutfit() {
        switch (job) {
            case FARMER -> {
                strawHat(0xD8B86A);
                apron(0x5B7A9A, true);   // overol
            }
            case FISHERMAN -> {
                cap(0xD9B040, true);
                pants((f, i, j, w, h) -> j >= 3 ? noise(0x4E6B3A, 5) : -1);   // botas de vadear
            }
            case SHEPHERD -> {
                vest(0xE8E2D4, true);
                hood(0x8C7A5A, false);
            }
            case FLETCHER -> {
                cap(0x3E6B3A, false);
                hat((f, i, j, w, h) -> f == 4 && i == 2 && j <= 2 ? 0xFFE04040 : -1);   // pluma
                vest(0x4E6B3A, false);
            }
            case LIBRARIAN -> {
                robe(0x7A2E2E, 0xD9C08A);
            }
            case CARTOGRAPHER -> {
                vest(0x2F4A6E, false);
                cap(0x6E5A3A, false);
            }
            case CLERIC -> {
                robe(0x6A2C8A, 0xE0C060);
                hood(0x6A2C8A, true);
            }
            case ARMORER, WEAPONSMITH, TOOLSMITH -> {
                apron(0x4A3020, false);
                soot();
                if (job == HumanJob.WEAPONSMITH && r.nextBoolean()) {   // parche en el ojo
                    for (int i = 0; i < 8; i++) set(40 + i, 8 + t.eyeRow - 1, 0xFF201810);
                    set(40 + (t.eyeGap == 2 ? 5 : 6), 8 + t.eyeRow, 0xFF201810);
                }
                if (job == HumanJob.ARMORER) cap(0x3A3A40, false);
            }
            case BUTCHER -> {
                apron(0xE8E8E2, false);
                jacket((f, i, j, w, h) -> f == 3 && j > 2 && r.nextFloat() < 0.07F ? 0xFF9A2A2A : -1);
                hat((f, i, j, w, h) -> j == 2 && f >= 2 ? 0xFFE04848 : -1);   // vincha
            }
            case LEATHERWORKER -> {
                apron(0x7A4A28, false);
                vest(0x5A3A22, false);
            }
            case MASON -> {
                apron(0x8A8A86, false);
                dust();
            }
            case MERCHANT -> {
                robe(culture == Culture.DESERT ? 0x2F5D8A : 0x2E4A2E, 0xE0C060);
                cap(culture == Culture.DESERT ? 0xF0E8D8 : 0x3A2A5A, false);
            }
            case GUARD -> {
                vest(0x8A7A5A, false);   // gambesón (la armadura va encima como ítems)
                sleeves((f, i, j, w, h) -> j < 8 ? noise(0x8A7A5A, 6) : -1);
            }
            case BANDIT -> {
                hood(0x2A2622, true);
                hat((f, i, j, w, h) -> f == 3 && j >= 5 ? noise(0x8A2020, 6) : (f >= 2 && j >= 5 ? noise(0x8A2020, 6) : -1));   // pañuelo
                vest(0x3A302A, false);
            }
            default -> {
                if (culture == Culture.DESERT && r.nextFloat() < 0.5F) headwrap(t.cloth3);
            }
        }
    }

    // ------------------------------------------------------------------ prendas

    private void strawHat(int c) {
        hat((f, i, j, w, h) -> {
            if (f == 0) return noise((i + j) % 2 == 0 ? c : shade(c, 0.85), 6);
            if (f == 1) return -1;
            if (j <= 1) return noise(j == 1 ? 0x8A3A2A : c, 6);   // copa + cinta
            return -1;
        });
    }

    private void cap(int c, boolean brim) {
        hat((f, i, j, w, h) -> {
            if (f == 0) return noise(c, 5);
            if (f == 1) return -1;
            if (j == 0) return noise(c, 5);
            if (j == 1) return noise(shade(c, 0.8), 5);
            return -1;
        });
    }

    private void hood(int c, boolean deep) {
        hat((f, i, j, w, h) -> {
            if (f == 1) return -1;
            if (f == 3) return (j == 0 || ((i == 0 || i == 7) && j < (deep ? 7 : 5))) ? noise(shade(c, 0.9), 5) : -1;
            return noise((i + j) % 4 == 0 ? shade(c, 0.85) : c, 5);
        });
    }

    private void headwrap(int c) {
        hat((f, i, j, w, h) -> {
            if (f == 1) return -1;
            if (f == 3) return j <= 1 ? noise(j == 1 ? shade(c, 0.8) : c, 5) : -1;
            if (f == 0) return noise(c, 6);
            return j <= 3 ? noise((i + j) % 3 == 0 ? shade(c, 0.85) : c, 5) : -1;
        });
    }

    /** Delantal (o peto/overol con tirantes). */
    private void apron(int c, boolean overall) {
        jacket((f, i, j, w, h) -> {
            if (f == 3) {
                if (overall && j < 3) return (i == 1 || i == 6) ? noise(c, 5) : -1;   // tirantes
                if (!overall && j < 2) return (i == 2 || i == 5) ? noise(shade(c, 0.85), 4) : -1;
                if (i >= 1 && i <= 6) return noise(i == 1 || i == 6 ? shade(c, 0.85) : c, 6);
                return -1;
            }
            if (f == 5 && (j == 1 || j == 7) ) return noise(shade(c, 0.8), 4);   // cintas
            return -1;
        });
        pants((f, i, j, w, h) -> f == 3 && j < (overall ? 10 : 6) ? noise(c, 6) : (overall && j < 10 ? noise(c, 6) : -1));
    }

    private void vest(int c, boolean wool) {
        jacket((f, i, j, w, h) -> {
            if (f == 3 && (i == 3 || i == 4) && j < 9) return -1;   // abierto adelante
            if (j > 9) return -1;
            int k = wool ? ((i + j) % 2 == 0 ? c : shade(c, 0.88)) : c;
            return noise(j == 9 ? shade(k, 0.8) : k, 5);
        });
    }

    private void robe(int c, int trim) {
        jacket((f, i, j, w, h) -> {
            if (f == 3 && i >= 3 && i <= 4) return noise(trim, 5);   // franja central
            return noise((f == 3 && (i == 0 || i == w - 1)) ? shade(c, 0.85) : c, 5);
        });
        sleeves((f, i, j, w, h) -> j < 11 ? noise(j == 10 ? trim : c, 5) : -1);
        pants((f, i, j, w, h) -> j < 11 ? noise(j == 10 ? trim : c, 5) : -1);
    }

    private void soot() {
        for (int k = 0; k < 6; k++) {
            int x = 8 + 1 + r.nextInt(6), y = 8 + t.eyeRow + 1 + r.nextInt(3);
            if (y < 16) set(x, y, mixArgb(get(x, y), 0xFF302820, 0.35));
        }
        sleeves((f, i, j, w, h) -> j >= 9 ? noise(0x2A2420, 4) : -1);   // guantes
    }

    private void dust() {
        jacket((f, i, j, w, h) -> r.nextFloat() < 0.06F ? 0xFFC8C4BC : -1);
    }

    // ------------------------------------------------------------------ color

    private int noise(int rgb, int amt) {
        int d = r.nextInt(amt * 2 + 1) - amt;
        return 0xFF000000 | clamp(((rgb >> 16) & 255) + d) << 16 | clamp(((rgb >> 8) & 255) + d) << 8 | clamp((rgb & 255) + d);
    }

    private static int argb(int rgb) {
        return 0xFF000000 | rgb;
    }

    private static int rgb(int r, int g, int b) {
        return clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static int shade(int rgb, double f) {
        return rgb((int) (((rgb >> 16) & 255) * f), (int) (((rgb >> 8) & 255) * f), (int) ((rgb & 255) * f));
    }

    static int mix(int a, int b, double t) {
        return rgb((int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t),
                (int) ((a & 255) * (1 - t) + (b & 255) * t));
    }

    private static int mixArgb(int a, int b, double t) {
        return 0xFF000000 | mix(a & 0xFFFFFF, b & 0xFFFFFF, t);
    }

    private static int jitter(Random r, int rgb, int amt) {
        return rgb(((rgb >> 16) & 255) + r.nextInt(amt * 2 + 1) - amt, ((rgb >> 8) & 255) + r.nextInt(amt * 2 + 1) - amt,
                (rgb & 255) + r.nextInt(amt * 2 + 1) - amt);
    }

    private static int weighted(Random r, int[] w) {
        int sum = 0;
        for (int x : w) sum += x;
        int k = r.nextInt(sum);
        for (int i = 0; i < w.length; i++) {
            k -= w[i];
            if (k < 0) return i;
        }
        return w.length - 1;
    }
}
