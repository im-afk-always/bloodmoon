package com.agustin.bloodmoon.human;

/**
 * Oficios humanos. Los trece de los aldeanos (con sus mismas ofertas, pagadas en monedas) más los propios de la Humanidad.
 * {@code vanilla}: id de la profesión de aldeano cuyas ofertas usa (null si no comercia así).
 */
public enum HumanJob {
    NONE(null),
    FARMER("farmer"),
    FISHERMAN("fisherman"),
    SHEPHERD("shepherd"),
    FLETCHER("fletcher"),
    LIBRARIAN("librarian"),
    CARTOGRAPHER("cartographer"),
    CLERIC("cleric"),
    ARMORER("armorer"),
    WEAPONSMITH("weaponsmith"),
    TOOLSMITH("toolsmith"),
    BUTCHER("butcher"),
    LEATHERWORKER("leatherworker"),
    MASON("mason"),
    MERCHANT(null),
    GUARD(null),
    BANDIT(null),
    /** Leñador: no comercia; su aserradero suma madera al mercado (como número) y la aldea tala alrededor por etapas. */
    LUMBERJACK(null);

    public final String vanilla;

    HumanJob(String vanilla) {
        this.vanilla = vanilla;
    }

    /** Nombre del taller en las plantillas ({@code work_<nombre>}): la profesión vanilla, o el oficio propio. */
    public String workshop() {
        return vanilla != null ? vanilla : name().toLowerCase(java.util.Locale.ROOT);
    }

    public static HumanJob byId(int id) {
        HumanJob[] v = values();
        return id >= 0 && id < v.length ? v[id] : NONE;
    }

    /** Los que comercian con el jugador. */
    public boolean trades() {
        return vanilla != null || this == MERCHANT;
    }
}
