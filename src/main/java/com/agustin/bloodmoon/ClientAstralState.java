package com.agustin.bloodmoon;

import java.util.HashMap;
import java.util.Map;

/** Cliente: qué entidades están ardiendo con llamas astrales (sin imports de cliente; lo usa el handler). */
public final class ClientAstralState {
    private static final Map<Integer, Long> BURNING = new HashMap<>();
    private static long clock;

    private ClientAstralState() {}

    public static synchronized void mark(int entityId) {
        BURNING.put(entityId, clock + 30);
    }

    public static synchronized boolean isBurning(int entityId) {
        Long until = BURNING.get(entityId);
        return until != null && until >= clock;
    }

    public static synchronized void tick() {
        clock++;
        if (clock % 100 == 0) BURNING.values().removeIf(t -> t < clock);
    }

    public static synchronized void reset() {
        BURNING.clear();
    }
}
