package com.agustin.bloodmoon.invasion;

import net.minecraft.network.chat.Component;

/** Jerarquía de un Dominio del Vacío, de mayor a menor. */
public enum InvasionRank {
    KING("king", 1),
    GENERAL("general", 4),
    CAPTAIN("captain", 8),
    FORGER("forger", 0),
    TROOPS("troops", 0);

    private final String id;
    /** Máximo de puestos con nombre (0 = solo se cuentan). */
    public final int max;

    InvasionRank(String id, int max) {
        this.id = id;
        this.max = max;
    }

    public Component displayName() {
        return Component.translatable("bloodmoon.invasion.rank." + id);
    }

    public Component pluralName() {
        return Component.translatable("bloodmoon.invasion.rank." + id + ".plural");
    }

    public static InvasionRank byId(int ordinal) {
        InvasionRank[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : TROOPS;
    }
}
