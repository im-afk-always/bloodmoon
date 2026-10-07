package com.agustin.bloodmoon;

/**
 * Estado visual en el cliente. Sin imports de cliente para poder referenciarse desde código común
 * (el handler del paquete) sin romper el servidor dedicado.
 */
public final class ClientBloodMoonState {
    /** ~2,5 s para pasar de cielo normal a Luna de Sangre completa. */
    private static final float FADE_PER_TICK = 0.02F;

    private static volatile boolean target = false;
    private static float intensity = 0F;
    private static float prevIntensity = 0F;

    private ClientBloodMoonState() {}

    public static void setTarget(boolean active) {
        target = active;
    }

    public static void tick() {
        prevIntensity = intensity;
        float goal = target ? 1F : 0F;
        if (intensity < goal) intensity = Math.min(goal, intensity + FADE_PER_TICK);
        else if (intensity > goal) intensity = Math.max(goal, intensity - FADE_PER_TICK);
    }

    /** 0 = noche normal, 1 = Luna de Sangre plena (interpolado entre ticks). */
    public static float intensity(float partialTick) {
        return prevIntensity + (intensity - prevIntensity) * partialTick;
    }

    public static boolean isRedMoon() {
        return intensity > 0.5F;
    }

    public static void reset() {
        target = false;
        intensity = 0F;
        prevIntensity = 0F;
    }
}
