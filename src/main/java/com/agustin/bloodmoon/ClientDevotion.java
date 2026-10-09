package com.agustin.bloodmoon;

import com.agustin.bloodmoon.network.DevotionAuraPayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Devoción del jugador local y auras de todos los devotos (sin imports de cliente: lo tocan los handlers de paquetes). */
public final class ClientDevotion {
    public static volatile Deity deity;
    public static volatile int reputation;

    public record Aura(Deity deity, int level) {}
    public static final Map<UUID, Aura> AURAS = new ConcurrentHashMap<>();

    private ClientDevotion() {}

    public static void set(String id, int rep) {
        deity = Deity.byId(id);
        reputation = rep;
    }

    public static void setAuras(DevotionAuraPayload payload) {
        AURAS.clear();
        for (DevotionAuraPayload.Entry e : payload.entries()) {
            Deity d = Deity.byId(e.deity());
            if (d != null && e.level() > 0) AURAS.put(e.id(), new Aura(d, e.level()));
        }
    }

    public static void reset() {
        deity = null;
        reputation = 0;
        AURAS.clear();
    }
}
