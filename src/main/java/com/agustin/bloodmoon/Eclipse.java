package com.agustin.bloodmoon;

/**
 * Geometría y cronología del Eclipse Solar (código común: lo usan el servidor y el cliente).
 * <pre>
 *  1900  primer contacto: la luna empieza a morder el sol (el sol va por ~45° de altura)
 *  4100  máximo: totalidad centrada, el sol a ~61° de altura (≈ los 60° pedidos)
 *  6300  último contacto: la luna termina de salir
 * </pre>
 * Antes (~1230) la luna asoma por el horizonte este y persigue al sol, más rápida, hasta alcanzarlo.
 * El sol mide 1 (radio) y la luna 1,06: la totalidad dura ~24 s. La luna avanza en línea recta, algo inclinada, y
 * se frena cerca del centro para que la totalidad y el anillo de diamante tengan su momento.
 */
public final class Eclipse {
    public static final long START = 1900L, MID = 4100L, END = 6300L;
    private static final float HALF = (END - START) / 2F;
    public static final float MOON_R = 1.06F;
    /** Inclinación de la trayectoria de la luna sobre el disco solar. */
    public static final float TILT = (float) Math.toRadians(18);
    public static final float DIR_X = (float) Math.cos(TILT), DIR_Y = (float) Math.sin(TILT);
    /** d a la que empieza la totalidad (el borde de la luna toca el del sol por dentro). */
    public static final float TOTAL_D = MOON_R - 1F;

    private Eclipse() {}

    /** Progreso -1..1 del evento según la hora del día (con fracción). Fuera de rango: más allá de ±1. */
    public static float u(double tod) {
        return (float) ((tod - MID) / HALF);
    }

    /** Posición de la luna sobre la trayectoria, en radios solares (negativa antes del máximo). */
    public static float offset(float u) {
        return 2.15F * (0.25F * u + 0.75F * u * u * u);
    }

    /** Fracción del disco solar tapada por la luna, con sus centros a distancia d. */
    public static float coverage(float d) {
        float r1 = 1F, r2 = MOON_R;
        if (d >= r1 + r2) return 0F;
        if (d <= r2 - r1) return 1F;
        double a1 = Math.acos(Math.max(-1, Math.min(1, (d * d + r1 * r1 - r2 * r2) / (2 * d * r1))));
        double a2 = Math.acos(Math.max(-1, Math.min(1, (d * d + r2 * r2 - r1 * r1) / (2 * d * r2))));
        double k = (-d + r1 + r2) * (d + r1 - r2) * (d - r1 + r2) * (d + r1 + r2);
        double area = r1 * r1 * a1 + r2 * r2 * a2 - 0.5 * Math.sqrt(Math.max(0, k));
        return (float) Math.min(1.0, area / Math.PI);
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0F, Math.min(1F, (x - e0) / (e1 - e0)));
        return t * t * (3F - 2F * t);
    }

    /** Luz del día que queda (1 = normal, ~0,07 en la totalidad). Casi no cambia hasta que la luna tapa mucho. */
    public static float brightness(float coverage) {
        float x = smooth(0.55F, 1F, coverage);
        return 1F - 0.93F * x * x;
    }

    /** Intensidad del anillo de diamante: el último (y el primer) destello de sol por el borde, en torno a la totalidad. */
    public static float diamond(float d) {
        if (d >= TOTAL_D) return (float) Math.pow(Math.max(0F, 1F - (d - TOTAL_D) / 0.016F), 2.2);
        return Math.max(0F, 1F - (TOTAL_D - d) / 0.006F);
    }

    /** Cuentas de Baily: puntos de luz entre los valles del borde lunar, justo antes del anillo de diamante. */
    public static float beads(float d) {
        if (d < TOTAL_D) return 0F;
        float x = (d - TOTAL_D - 0.012F) / 0.03F;
        return Math.max(0F, 1F - Math.abs(x * 2F - 1F)) * (d < TOTAL_D + 0.045F ? 1F : 0F);
    }

    /** Visibilidad de la corona: aparece cuando el sol queda reducido a una astilla. */
    public static float corona(float d) {
        return 1F - smooth(TOTAL_D, TOTAL_D + 0.05F, d);
    }

    /** Estado completo para un instante. */
    public record State(float u, float s, float d, float coverage, float brightness, float diamond, float beads, float corona) {
        public boolean active() {
            return u > -1.08F && u < 1.08F;
        }

        public boolean totality() {
            return d <= TOTAL_D;
        }

        /** Oscuridad 0..1 (para tintes y posprocesado). */
        public float dark() {
            return 1F - brightness;
        }
    }

    public static State at(double tod) {
        float u = u(tod);
        float uc = Math.max(-1.3F, Math.min(1.3F, u));
        float s = offset(uc);
        float d = Math.abs(s);
        float c = coverage(d);
        return new State(u, s, d, c, brightness(c), diamond(d), beads(d), corona(d));
    }
}
