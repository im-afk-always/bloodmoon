package com.agustin.bloodmoon;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Se genera en config/bloodmoon-common.toml. Editable sin recompilar (reiniciar el juego para aplicar). */
public final class BloodMoonConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue BLOOD_INTERVAL;
    public static final ModConfigSpec.IntValue SUPER_INTERVAL;
    public static final ModConfigSpec.IntValue GOLDEN_INTERVAL;
    public static final ModConfigSpec.IntValue MOONLESS_INTERVAL;

    public static final ModConfigSpec.IntValue CREEPER_EXPLOSION_MULT;
    public static final ModConfigSpec.IntValue CURSED_EXPLOSION_MULT;
    public static final ModConfigSpec.DoubleValue CURSED_CHANCE;
    public static final ModConfigSpec.BooleanValue CURSED_FIRE;
    public static final ModConfigSpec.DoubleValue PHANTOM_SCALE;
    public static final ModConfigSpec.DoubleValue PHANTOM_DAMAGE_MULT;
    public static final ModConfigSpec.IntValue PHANTOM_MIN_SECONDS;
    public static final ModConfigSpec.IntValue PHANTOM_MAX_SECONDS;
    public static final ModConfigSpec.BooleanValue RIDER_ENABLED;
    public static final ModConfigSpec.DoubleValue RIDER_HEALTH;
    public static final ModConfigSpec.DoubleValue SPECIAL_GEAR_DROP_CHANCE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Cada cuántas noches ocurre cada luna (0 = desactivada).",
                "Prioridad si coinciden: Moonless > Super > Blood > Golden.").push("cycle");
        BLOOD_INTERVAL = b.defineInRange("bloodMoonEveryNights", 3, 0, 1000);
        SUPER_INTERVAL = b.defineInRange("superBloodMoonEveryNights", 13, 0, 1000);
        GOLDEN_INTERVAL = b.defineInRange("goldenMoonEveryNights", 7, 0, 1000);
        MOONLESS_INTERVAL = b.defineInRange("moonlessNightEveryNights", 50, 0, 1000);
        b.pop();

        b.push("superBloodMoon");
        CREEPER_EXPLOSION_MULT = b.comment("Multiplicador de explosión de creepers normales (vanilla = 3 de radio).")
                .defineInRange("creeperExplosionMultiplier", 10, 1, 40);
        CURSED_EXPLOSION_MULT = b.comment("Multiplicador de explosión del Cursed Creeper respecto a un creeper normal.")
                .defineInRange("cursedCreeperExplosionMultiplier", 20, 1, 80);
        CURSED_CHANCE = b.comment("Probabilidad de que un creeper natural sea reemplazado por un Cursed Creeper.")
                .defineInRange("cursedCreeperChance", 0.10, 0.0, 1.0);
        CURSED_FIRE = b.comment("El Cursed Creeper deja fuego al explotar.")
                .define("cursedCreeperFire", true);
        PHANTOM_SCALE = b.defineInRange("giantPhantomScale", 3.0, 1.0, 16.0);
        PHANTOM_DAMAGE_MULT = b.defineInRange("giantPhantomDamageMultiplier", 2.0, 1.0, 20.0);
        PHANTOM_MIN_SECONDS = b.comment("Intervalo entre oleadas de phantoms por jugador (segundos).")
                .defineInRange("phantomMinSeconds", 60, 5, 3600);
        PHANTOM_MAX_SECONDS = b.defineInRange("phantomMaxSeconds", 120, 5, 3600);
        RIDER_ENABLED = b.comment("Un Jinete del Apocalipsis por jugador a la medianoche de cada Súper Luna.")
                .define("apocalypseRiderEnabled", true);
        RIDER_HEALTH = b.defineInRange("apocalypseRiderHealth", 80.0, 1.0, 1024.0);
        SPECIAL_GEAR_DROP_CHANCE = b.comment("Probabilidad de drop del equipo de diamante/netherite (0 = nunca).")
                .defineInRange("specialGearDropChance", 0.0, 0.0, 1.0);
        b.pop();

        SPEC = b.build();
    }

    private BloodMoonConfig() {}
}
