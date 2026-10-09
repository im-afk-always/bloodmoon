package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.Deity;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** El encapuchado (16x16, pixel art) con los ojos del color de su deidad. Sin deidad, los ojos son brasas apagadas. */
public final class DevotionIcon {
    public static final ResourceLocation HOOD = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/gui/devotion_hood.png");
    /** Posición de los ojos en la textura. */
    private static final int[] EYES_X = {6, 9};
    private static final int EYES_Y = 7;

    private DevotionIcon() {}

    /** Dibuja el encapuchado en (x, y) a escala entera {@code s} (16*s píxeles). */
    public static void draw(GuiGraphics g, int x, int y, int s, Deity deity) {
        g.blit(HOOD, x, y, 16 * s, 16 * s, 0F, 0F, 16, 16, 16, 16);
        if (deity == null) {
            for (int ex : EYES_X) g.fill(x + ex * s, y + EYES_Y * s, x + (ex + 1) * s, y + (EYES_Y + 1) * s, 0xFF3A3436);
            return;
        }
        float pulse = 0.6F + 0.4F * Mth.sin(Util.getMillis() / 380F);
        int glowRgb = deity.glowColor & 0xFFFFFF;
        int baseA = (deity.glowColor >>> 24);
        // resplandor: capas que se abren alrededor de cada ojo, dentro del vacío del rostro
        int layers = s >= 3 ? 3 : 1;
        for (int ex : EYES_X) {
            for (int l = layers; l >= 1; l--) {
                int pad = s >= 3 ? l * s / 2 + s / 3 : 1;
                int a = (int) (baseA * pulse * (s >= 3 ? 0.22F : 0.35F) * (layers + 1 - l) / layers);
                g.fill(x + ex * s - pad, y + EYES_Y * s - pad / 2, x + (ex + 1) * s + pad, y + (EYES_Y + 1) * s + pad / 2, a << 24 | glowRgb);
            }
            g.fill(x + ex * s, y + EYES_Y * s, x + (ex + 1) * s, y + (EYES_Y + 1) * s, deity.eyeColor);
            if (s >= 3) {   // brillo en el centro del ojo
                int c = s / 3;
                g.fill(x + ex * s + c, y + EYES_Y * s + c, x + (ex + 1) * s - c, y + (EYES_Y + 1) * s - c,
                        (int) (255 * (0.5F + 0.5F * pulse)) << 24 | 0xFF8A70);
            }
        }
    }
}
