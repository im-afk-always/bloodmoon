package com.agustin.bloodmoon;

/**
 * Tipos de luna. Los valores visuales son floats planos (sin clases de cliente) para poder vivir en código común.
 */
public enum MoonType {
    //       sky RGB                 fog/horizon RGB        stars  moon tint RGB    light tint RGB      floor  moon  halo
    NONE   (0F, 0F, 0F,              0F, 0F, 0F,            1F,    255, 255, 255,   0F, 0F, 0F,         0F,    0F,   0F),
    BLOOD  (0.015F, 0F, 0F,          0.03F, 0F, 0F,         0F,    255, 38, 28,     0F, 0F, 0F,         0F,    0F,   0F),
    /** Luna de la Cosecha (internamente SUPER): cielo carmesí, luna realista con halo y todo el mundo teñido de rojo. */
    SUPER  (0.30F, 0.022F, 0.018F,   0.42F, 0.05F, 0.035F,  0F,    255, 22, 16,     1F, 0.17F, 0.15F,   0.5F,  16F,  1F),
    GOLDEN (0.02F, 0.015F, 0.003F,   0.11F, 0.075F, 0.015F, 0.3F,  255, 205, 80,    0F, 0F, 0F,         0F,    0F,   0F),
    /** Sin luna: cielo negro, luna invisible (tinte 0). La grieta y el ojo: MoonlessSkyRenderer. */
    MOONLESS (0.004F, 0F, 0.007F,    0.006F, 0F, 0.01F,     0F,    0, 0, 0,         0F, 0F, 0F,         0F,    0F,   0F);

    public final float skyR, skyG, skyB;
    public final float fogR, fogG, fogB;
    /** Fracción de estrellas visibles (0 = ninguna). */
    public final float starFactor;
    public final int tintR, tintG, tintB;
    /**
     * Tinte de la luz del cielo (lightmap) y luz mínima que da la luna a cielo abierto; 0 = sin tinte. La luz de
     * bloques (antorchas) conserva su color.
     */
    public final float lightR, lightG, lightB, lightFloor;
    /** Radio de la luna realista (unidades del cielo, a 100 de distancia; 0 = luna vanilla) e intensidad del halo. */
    public final float moonSize, halo;

    MoonType(float skyR, float skyG, float skyB, float fogR, float fogG, float fogB, float starFactor,
             int tintR, int tintG, int tintB, float lightR, float lightG, float lightB, float lightFloor,
             float moonSize, float halo) {
        this.skyR = skyR; this.skyG = skyG; this.skyB = skyB;
        this.fogR = fogR; this.fogG = fogG; this.fogB = fogB;
        this.starFactor = starFactor;
        this.tintR = tintR; this.tintG = tintG; this.tintB = tintB;
        this.lightR = lightR; this.lightG = lightG; this.lightB = lightB; this.lightFloor = lightFloor;
        this.moonSize = moonSize; this.halo = halo;
    }

    /** Ilumina el mundo con su propio color. */
    public boolean tintsLight() {
        return lightR + lightG + lightB > 0F;
    }

    /** Usa el cielo realista (luna con halo y nubes propias). */
    public boolean customSky() {
        return moonSize > 0F;
    }

    /** Blood y Super aplican los efectos de la Luna de Sangre. */
    public boolean isBlood() {
        return this == BLOOD || this == SUPER;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static MoonType byId(int id) {
        MoonType[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }
}
