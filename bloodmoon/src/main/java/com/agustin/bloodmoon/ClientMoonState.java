package com.agustin.bloodmoon;

/**
 * Estado visual en el cliente. Sin imports de cliente: lo referencia el handler del paquete (código común).
 * Si cambia el tipo de luna, primero se desvanece el actual y después aparece el nuevo.
 */
public final class ClientMoonState {
    private static final float FADE_PER_TICK = 0.02F; // ~2,5 s

    private static volatile MoonType target = MoonType.NONE;
    private static MoonType visual = MoonType.NONE;
    private static float intensity = 0F;
    private static float prevIntensity = 0F;

    private ClientMoonState() {}

    public static void setTarget(MoonType type) {
        target = type;
    }

    public static void tick() {
        prevIntensity = intensity;
        MoonType t = target;
        if (t != visual) {
            intensity = Math.max(0F, intensity - FADE_PER_TICK);
            if (intensity <= 0F) {
                visual = t;
                prevIntensity = 0F;
            }
        } else if (visual != MoonType.NONE && intensity < 1F) {
            intensity = Math.min(1F, intensity + FADE_PER_TICK);
        }
    }

    public static MoonType visual() {
        return visual;
    }

    /** 0 = noche normal, 1 = efecto pleno (interpolado entre ticks). */
    public static float intensity(float partialTick) {
        if (visual == MoonType.NONE) return 0F;
        return prevIntensity + (intensity - prevIntensity) * partialTick;
    }

    /** true cuando la textura de la luna debe reemplazarse. */
    public static boolean moonSwapped() {
        return visual != MoonType.NONE && intensity > 0.5F;
    }

    public static void reset() {
        target = MoonType.NONE;
        visual = MoonType.NONE;
        intensity = 0F;
        prevIntensity = 0F;
    }
}
