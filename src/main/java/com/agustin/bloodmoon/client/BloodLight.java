package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.ClientMoonState;
import com.agustin.bloodmoon.MoonType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * Tiñe el lightmap (la tabla 16x16 luz-del-cielo x luz-de-bloque que ilumina todo el mundo) con el color de la luna:
 * lo que recibe luz del cielo queda bañado en carmesí (la luna aporta un mínimo de luz a cielo abierto) y la luz de las
 * antorchas se vuelve naranja de fuego. Todo queda más oscuro y con más contraste.
 */
public final class BloodLight {
    /** Oscurecimiento general del mundo teñido (con más contraste en las sombras). */
    private static final float DIM = 0.85F;
    /** La luz de las antorchas se calienta hacia un naranja de fuego: charcos de luz cálida en el mundo carmesí. */
    private static final float TORCH_TINT = 0.55F, TORCH_R = 1F, TORCH_G = 0.6F, TORCH_B = 0.32F;

    private BloodLight() {}

    /**
     * @param block índice de luz de bloque (columna x del lightmap, 0..15)
     * @param sky   índice de luz del cielo (fila y, 0..15)
     * @param abgr  color calculado por vanilla (formato NativeImage: ABGR)
     */
    public static int tint(int block, int sky, int abgr) {
        MoonType type = ClientMoonState.visual();
        if (!type.tintsLight()) return abgr;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return abgr;
        float k = ClientMoonState.intensity(mc.getTimer().getGameTimeDeltaPartialTick(false));
        if (k <= 0F) return abgr;

        float r = (abgr & 0xFF) / 255F, g = (abgr >>> 8 & 0xFF) / 255F, b = (abgr >>> 16 & 0xFF) / 255F;
        float s = sky / 15F, bl = block / 15F;
        // cuánto de esta celda es luz del cielo (y no de antorchas)
        float dom = s * s / (s * s + 1.4F * bl * bl + 0.001F);
        float lum = 0.2126F * r + 0.7152F * g + 0.0722F * b;
        // a cielo abierto la luna aporta un mínimo de luz; después todo se oscurece y gana contraste
        float light = lum + (Math.max(lum, type.lightFloor * (float) Math.pow(s, 1.6)) - lum) * dom;
        light = DIM * (float) Math.pow(light, 1.2);
        // la luz del cielo es carmesí; la de las antorchas, naranja de fuego
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
