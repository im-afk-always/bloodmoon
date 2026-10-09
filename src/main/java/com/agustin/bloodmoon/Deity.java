package com.agustin.bloodmoon;

import net.minecraft.network.chat.Component;

/** Deidades a las que un jugador puede rendir culto. Por ahora solo la de la cosecha. */
public enum Deity {
    HARVEST("harvest", 0xFFD01818, 0xB0FF2020),
    PROVIDENCE("providence", 0xFFFFC23A, 0xB0FFD060);

    private final String id;
    /** Color de los ojos del encapuchado (ARGB) y de su resplandor. */
    public final int eyeColor, glowColor;

    Deity(String id, int eyeColor, int glowColor) {
        this.id = id;
        this.eyeColor = eyeColor;
        this.glowColor = glowColor;
    }

    public String id() { return id; }

    /** "Luna de la Cosecha". */
    public Component displayName() { return Component.translatable("bloodmoon.deity." + id); }

    /** "la Luna de la Cosecha" (para usar dentro de una frase). */
    public Component inSentence() { return Component.translatable("bloodmoon.deity." + id + ".the"); }

    public static Deity byId(String id) {
        for (Deity d : values()) if (d.id.equals(id)) return d;
        return null;
    }
}
