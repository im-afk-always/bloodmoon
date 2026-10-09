package com.agustin.bloodmoon;

/** Estado de la ofrenda en el cliente (sin imports de cliente: lo toca el handler del paquete). */
public final class ClientOffering {
    public static volatile boolean active, unskippable;
    public static volatile int done, required;

    private ClientOffering() {}

    public static void set(boolean a, int d, int r, boolean u) {
        active = a; done = d; required = r; unskippable = u;
    }

    public static void reset() {
        set(false, 0, 0, false);
    }
}
