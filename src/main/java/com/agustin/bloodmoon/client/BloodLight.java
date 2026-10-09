package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.ClientMoonState;
import com.agustin.bloodmoon.MoonType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Tiñe el lightmap (la tabla 16x16 luz-del-cielo x luz-de-bloque que ilumina todo el mundo) con el color de la luna:
 * lo que recibe luz del cielo queda bañado en carmesí (la luna aporta un mínimo de luz a cielo abierto) y la luz de las
 * antorchas se vuelve rojo brasa. Todo queda más oscuro y con más contraste.
 */
public final class BloodLight {
    // Cosecha: todo más oscuro y las antorchas casi del todo hacia un rojo brasa.
    // Sin Luna: más oscuro todavía, y las antorchas a medias hacia un lavanda frío.
    //                                      dim    torchTint  torch R/G/B
    private static final float[] HARVEST = {0.85F, 0.85F, 1F, 0.3F, 0.15F};
    private static final float[] MOONLESS = {0.72F, 0.6F, 0.85F, 0.55F, 1F};
    // Providencia: casi sin oscurecer, luz dorada cálida; las antorchas a medias hacia un ámbar.
    private static final float[] PROVIDENCE = {0.97F, 0.55F, 1F, 0.78F, 0.42F};

    private BloodLight() {}

    /**
     * Eclipse Solar: la luz del cielo se ahoga con la luna (hasta ~7% en la totalidad) y vira a un gris pardo apagado.
     * Las antorchas no cambian: en la totalidad son lo único que brilla de verdad.
     */
    private static int eclipse(int block, int sky, int abgr) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return abgr;
        com.agustin.bloodmoon.Eclipse.State st = com.agustin.bloodmoon.ClientEclipse.state(mc.level.getDayTime(),
                mc.getTimer().getGameTimeDeltaPartialTick(false));
        if (st == null) return abgr;
        float dark = st.dark();
        if (dark <= 0.001F) return abgr;
        float r = (abgr & 0xFF) / 255F, g = (abgr >>> 8 & 0xFF) / 255F, b = (abgr >>> 16 & 0xFF) / 255F;
        float s = sky / 15F, bl = block / 15F;
        float dom = s * s / (s * s + 1.4F * bl * bl + 0.001F);
        float lum = 0.2126F * r + 0.7152F * g + 0.0722F * b;
        // gris pardo: desaturado hacia un sepia frío
        float k = Math.min(1F, dark * 1.1F) * dom;
        r += (lum * 0.96F - r) * k * 0.8F;
        g += (lum * 0.88F - g) * k * 0.8F;
        b += (lum * 0.74F - b) * k * 0.8F;
        // y se apaga
        float f = 1F - dom * dark * 0.95F;
        r *= f; g *= f; b *= f;
        int ir = Math.round(Mth.clamp(r, 0F, 1F) * 255F), ig = Math.round(Mth.clamp(g, 0F, 1F) * 255F), ib = Math.round(Mth.clamp(b, 0F, 1F) * 255F);
        return (abgr & 0xFF000000) | ib << 16 | ig << 8 | ir;
    }

    /**
     * @param block índice de luz de bloque (columna x del lightmap, 0..15)
     * @param sky   índice de luz del cielo (fila y, 0..15)
     * @param abgr  color calculado por vanilla (formato NativeImage: ABGR)
     */
    public static int tint(int block, int sky, int abgr) {
        int e = eclipse(block, sky, abgr);
        if (e != abgr) return e;
        MoonType type = ClientMoonState.visual();
        if (!type.tintsLight()) return abgr;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return abgr;
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
        float k = ClientMoonState.intensity(pt);
        if (k <= 0F) return abgr;
        float[] q = type == MoonType.MOONLESS ? MOONLESS : type == MoonType.GOLDEN ? PROVIDENCE : HARVEST;
        float DIM = q[0], TORCH_TINT = q[1], TORCH_R = q[2], TORCH_G = q[3], TORCH_B = q[4];
        // sin luna, la única luz del cielo es la que irradian la grieta y el ojo
        float floor = type.lightFloor * (type == MoonType.MOONLESS ? MoonlessSkyRenderer.riftLight(mc.level, pt) : 1F);

        float r = (abgr & 0xFF) / 255F, g = (abgr >>> 8 & 0xFF) / 255F, b = (abgr >>> 16 & 0xFF) / 255F;
        float s = sky / 15F, bl = block / 15F;
        // cuánto de esta celda es luz del cielo (y no de antorchas)
        float dom = s * s / (s * s + 1.4F * bl * bl + 0.001F);
        float lum = 0.2126F * r + 0.7152F * g + 0.0722F * b;
        // a cielo abierto la luna aporta un mínimo de luz; después todo se oscurece y gana contraste
        float light = lum + (Math.max(lum, floor * (float) Math.pow(s, 1.6)) - lum) * dom;
        light = DIM * (float) Math.pow(light, 1.2);
        // la luz del cielo es carmesí; la de las antorchas, rojo brasa
        float cr = type.lightR + (TORCH_R - type.lightR) * (1F - dom);
        float cg = type.lightG + (TORCH_G - type.lightG) * (1F - dom);
        float cb = type.lightB + (TORCH_B - type.lightB) * (1F - dom);
        float hi = light * light * 0.25F;
        float tr = Math.min(1F, light * cr + hi * 0.2F);
        float tg = Math.min(1F, light * cg + hi * 0.2F);
        float tb = Math.min(1F, light * cb + hi * 0.18F);
        float m = k * (dom + (1F - dom) * TORCH_TINT);
        r += (tr - r) * m;
        g += (tg - g) * m;
        b += (tb - b) * m;
        int ir = Math.round(Math.max(0F, Math.min(1F, r)) * 255F);
        int ig = Math.round(Math.max(0F, Math.min(1F, g)) * 255F);
        int ib = Math.round(Math.max(0F, Math.min(1F, b)) * 255F);
        return (abgr & 0xFF000000) | ib << 16 | ig << 8 | ir;
    }
}
