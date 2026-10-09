package com.agustin.bloodmoon;

/** Devoción del jugador local (sin imports de cliente: lo toca el handler del paquete). */
public final class ClientDevotion {
    public static volatile Deity deity;
    public static volatile int reputation;

    private ClientDevotion() {}

    public static void set(String id, int rep) {
        deity = Deity.byId(id);
        reputation = rep;
    }

    public static void reset() {
        deity = null;
        reputation = 0;
    }
}
