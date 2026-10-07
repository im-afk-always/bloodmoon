package com.agustin.bloodmoon.world.design;

/** Ruido determinista por hash (sin estado), igual en servidor y en pruebas. */
public final class Noise {
    private final long seed;

    public Noise(long seed) {
        this.seed = seed;
    }

    private static long mix(long v) {
        v ^= v >>> 33;
        v *= 0xff51afd7ed558ccdL;
        v ^= v >>> 33;
        v *= 0xc4ceb9fe1a85ec53L;
        v ^= v >>> 33;
        return v;
    }

    /** [0, 1) */
    public double rnd(int x, int y, int z, int salt) {
        long h = mix(seed * 0x9E3779B97F4A7C15L + x * 341873128712L + y * 132897987541L + z * 2654435761L + salt * 0x632BE59BD9B4E019L);
        return (h >>> 11) / (double) (1L << 53);
    }

    /** Ruido suave 2D (value noise bilineal) en [0, 1). */
    public double smooth2(double x, double z, double scale, int salt) {
        double fx = x / scale, fz = z / scale;
        int x0 = (int) Math.floor(fx), z0 = (int) Math.floor(fz);
        double tx = fx - x0, tz = fz - z0;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = rnd(x0, 0, z0, salt), b = rnd(x0 + 1, 0, z0, salt), c = rnd(x0, 0, z0 + 1, salt), d = rnd(x0 + 1, 0, z0 + 1, salt);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
    }

    /** Ruido periódico suave por ángulo (grados), [0, 1). */
    public double angular(double deg, int salt) {
        double n = 0;
        double[][] w = {{2, 0.45}, {3, 0.3}, {7, 0.15}, {13, 0.1}};
        for (int k = 0; k < w.length; k++) {
            double ph = rnd(k, 0, 0, salt) * Math.PI * 2;
            n += w[k][1] * (0.5 + 0.5 * Math.sin(Math.toRadians(deg) * w[k][0] + ph));
        }
        return n;
    }
}
