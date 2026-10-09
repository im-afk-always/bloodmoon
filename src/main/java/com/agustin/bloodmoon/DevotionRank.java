package com.agustin.bloodmoon;

import net.minecraft.network.chat.Component;

/** Rangos de adoración, de menor a mayor. El nivel de adoración es la posición (1 = Iniciado). */
public enum DevotionRank {
    INITIATE("initiate", 0),
    ACOLYTE("acolyte", 50),
    BELIEVER("believer", 120),
    FAITHFUL("faithful", 250),
    DEACON("deacon", 450),
    PRIEST("priest", 700),
    BISHOP("bishop", 1000),
    ARCHBISHOP("archbishop", 1500),
    APOSTLE("apostle", 2200),
    PROPHET("prophet", 3200),
    CHOSEN("chosen", 4500);

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"};
    private final String id;
    public final int minReputation;

    DevotionRank(String id, int minReputation) {
        this.id = id;
        this.minReputation = minReputation;
    }

    public Component displayName() { return Component.translatable("bloodmoon.rank." + id); }

    public int level() { return ordinal() + 1; }

    public String roman() { return ROMAN[ordinal()]; }

    /** Reputación en centésimas como texto: "12" o "12.45". */
    public static String format(int centi) {
        if (centi % 100 == 0) return Integer.toString(centi / 100);
        return String.format(java.util.Locale.ROOT, "%d.%02d", centi / 100, Math.abs(centi % 100));
    }

    /** Siguiente rango, o null si es el máximo. */
    public DevotionRank next() {
        int i = ordinal() + 1;
        return i < values().length ? values()[i] : null;
    }

    public static DevotionRank of(int reputation) {
        DevotionRank r = INITIATE;
        for (DevotionRank k : values()) if (reputation >= k.minReputation) r = k;
        return r;
    }
}
