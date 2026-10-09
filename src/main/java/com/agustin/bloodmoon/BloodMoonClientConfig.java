package com.agustin.bloodmoon;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Se genera en config/bloodmoon-client.toml (solo en el cliente). */
public final class BloodMoonClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue HARVEST_POST_EFFECT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("visuals");
        HARVEST_POST_EFFECT = b.comment(
                "Efecto de posprocesado en la Luna de la Cosecha (carmesí) y la Noche sin Luna (violeta): tono, resplandor, contraste y viñeta.",
                "Se desactiva solo si está instalado Iris (los paquetes de shaders reemplazan la imagen).")
                .define("harvestMoonPostEffect", true);
        b.pop();
        SPEC = b.build();
    }

    private BloodMoonClientConfig() {}
}
