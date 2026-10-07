package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Genera en tiempo de ejecución versiones teñidas de texturas vanilla (no se redistribuye arte de Mojang
 * y respeta los resource packs). Se regeneran tras cada recarga de recursos (F3+T).
 */
public final class TintedTextures {
    private static final Set<ResourceLocation> READY = new HashSet<>();
    private static final Set<ResourceLocation> FAILED = new HashSet<>();

    private TintedTextures() {}

    public static void invalidate() {
        READY.clear();
        FAILED.clear();
    }

    /**
     * @return target si se pudo generar; source si falló (se ve vanilla en vez de crashear).
     */
    public static ResourceLocation get(ResourceLocation source, ResourceLocation target,
                                       int tintR, int tintG, int tintB, float gain) {
        if (READY.contains(target)) return target;
        if (FAILED.contains(target)) return source;
        try {
            NativeImage image;
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(source)) {
                image = NativeImage.read(in);
            }
            tint(image, tintR, tintG, tintB, gain);
            Minecraft.getInstance().getTextureManager().register(target, new DynamicTexture(image));
            READY.add(target);
            return target;
        } catch (Exception e) {
            BloodMoonMod.LOGGER.error("Could not build tinted texture {} from {}", target, source, e);
            FAILED.add(target);
            return source;
        }
    }

    /** Pasa cada píxel a escala de grises y lo multiplica por el color. NativeImage usa formato ABGR. */
    private static void tint(NativeImage img, int tr, int tg, int tb, float gain) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int c = img.getPixelRGBA(x, y);
                int a = (c >>> 24) & 0xFF;
                int b = (c >>> 16) & 0xFF;
                int g = (c >>> 8) & 0xFF;
                int r = c & 0xFF;
                float lum = Math.min(1F, (0.299F * r + 0.587F * g + 0.114F * b) / 255F * gain);
                int nr = Math.round(lum * tr);
                int ng = Math.round(lum * tg);
                int nb = Math.round(lum * tb);
                img.setPixelRGBA(x, y, (a << 24) | (nb << 16) | (ng << 8) | nr);
            }
        }
    }
}
