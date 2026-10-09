package com.agustin.bloodmoon.invasion;

import net.minecraft.util.RandomSource;

/** Nombres procedurales para los Dominios y sus rangos altos. */
public final class DominionNames {
    private static final String[] HEAD = {"Vael", "Khar", "Oss", "Mor", "Thal", "Zir", "Nyr", "Drav", "Ul", "Vor", "Ash", "Kael",
            "Ren", "Xeth", "Gor", "Eth", "Syl", "Lum", "Nar", "Qor", "Irr", "Bael", "Zhal", "Orn"};
    private static final String[] MID = {"a", "e", "o", "u", "i", "ae", "y"};
    private static final String[] TAIL = {"os", "ath", "ion", "eth", "ar", "uun", "is", "or", "ax", "iel", "oth", "ek", "ra", "um"};

    private DominionNames() {}

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "Vael'Kharos" */
    public static String faction(RandomSource r) {
        String a = HEAD[r.nextInt(HEAD.length)];
        String b = HEAD[r.nextInt(HEAD.length)].toLowerCase();
        return a + "'" + cap(b + TAIL[r.nextInt(TAIL.length)]);
    }

    /** "Vorath", "Ossiel"... */
    public static String person(RandomSource r) {
        String n = HEAD[r.nextInt(HEAD.length)];
        if (r.nextFloat() < 0.4F) n += MID[r.nextInt(MID.length)];
        return n + TAIL[r.nextInt(TAIL.length)];
    }
}
