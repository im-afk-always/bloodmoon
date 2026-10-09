package com.agustin.bloodmoon;

/**
 * Estado del Eclipse Solar en el cliente. Sin imports de cliente (lo usan los mixins y el handler del paquete).
 * El servidor solo informa el día; la fase se calcula con la hora del mundo, así que sigue a /time.
 */
public final class ClientEclipse {
    private static volatile long day = -1L;

    private ClientEclipse() {}

    public static void set(long d) {
        day = d;
    }

    public static void reset() {
        day = -1L;
    }

    /** Estado para este instante, o null si hoy no hay eclipse. dayTime = Level#getDayTime() del Overworld. */
    public static Eclipse.State state(long dayTime, float partialTick) {
        long d = day;
        if (d < 0 || dayTime / 24000L != d) return null;
        // sin fracción de tick: si el ciclo día/noche está detenido (eclipse permanente) la escena queda quieta
        Eclipse.State st = Eclipse.at(dayTime % 24000L);
        return st;
    }

    /** true durante todo el día del eclipse (hasta el atardecer): el sol realista reemplaza al vanilla. */
    public static boolean isEclipseDay(long dayTime) {
        long d = day;
        return d >= 0 && dayTime / 24000L == d && dayTime % 24000L < 13000L;
    }
}
