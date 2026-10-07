package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.entity.UnknownEmissary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Barra de jefe propia del Emisario Desconocido (reemplaza la vanilla solo para él):
 * marco oscuro con filo púrpura, relleno con degradado y brillo que recorre la barra,
 * estela blanca que muestra el daño reciente, muescas cada 10 % y marcador de fase al 50 %.
 */
public final class EmissaryBossBar {
    private static final int WIDTH = 240;
    private static final int HEIGHT = 8;
    private static final Map<UUID, Float> GHOST = new HashMap<>();

    private EmissaryBossBar() {}

    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents tc)
                || !UnknownEmissary.NAME_KEY.equals(tc.getKey())) return;

        event.setCanceled(true);
        event.setIncrement(34);

        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        float progress = Mth.clamp(boss.getProgress(), 0F, 1F);
        float ghost = Math.max(progress, GHOST.getOrDefault(boss.getId(), progress) - 0.0025F);
        GHOST.put(boss.getId(), ghost);
        long now = System.currentTimeMillis();

        int x0 = g.guiWidth() / 2 - WIDTH / 2;
        int y = event.getY();
        int barY = y + 11;

        // nombre
        Component title = Component.literal("✦ ").append(boss.getName()).append(" ✦");
        int pulse = (int) (40 * (0.5 + 0.5 * Math.sin(now / 400.0)));
        g.drawCenteredString(font, title, g.guiWidth() / 2, y, 0xFF000000 | (0xB0 + pulse / 2) << 16 | 0x60 << 8 | 0xFF);

        // marco
        g.fill(x0 - 4, barY - 3, x0 + WIDTH + 4, barY + HEIGHT + 3, 0xE0080010);
        border(g, x0 - 4, barY - 3, WIDTH + 8, HEIGHT + 6, 0xFF5A189A);
        g.fill(x0 - 3, barY - 2, x0 + WIDTH + 3, barY - 1, 0xFF9D4EDD);
        // puntas tipo empuñadura
        for (int i = 0; i < 4; i++) {
            g.fill(x0 - 5 - i, barY + 4 - i / 2 - 1, x0 - 4 - i, barY + 4 + i / 2 + 1, 0xFF7B2CBF);
            g.fill(x0 + WIDTH + 4 + i, barY + 4 - i / 2 - 1, x0 + WIDTH + 5 + i, barY + 4 + i / 2 + 1, 0xFF7B2CBF);
        }

        // pista vacía
        g.fill(x0, barY, x0 + WIDTH, barY + HEIGHT, 0xFF12001A);

        // estela de daño reciente
        int ghostW = Math.round(WIDTH * ghost);
        g.fill(x0, barY, x0 + ghostW, barY + HEIGHT, 0xCCE8C8FF);

        // relleno con degradado horizontal + brillo que recorre la barra
        int fillW = Math.round(WIDTH * progress);
        float shine = (now % 2400L) / 2400F;
        for (int i = 0; i < fillW; i++) {
            float f = i / (float) WIDTH;
            float s = Math.max(0F, 1F - Math.abs(f - shine) * 12F);
            int r = (int) Mth.lerp(f, 0x3C, 0xC7) + (int) (60 * s);
            int gg = (int) Mth.lerp(f, 0x09, 0x7D) + (int) (60 * s);
            int b = (int) Mth.lerp(f, 0x6C, 0xFF);
            int col = 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, gg) << 8 | Math.min(255, b);
            g.fill(x0 + i, barY, x0 + i + 1, barY + HEIGHT, col);
        }
        if (fillW > 0) g.fill(x0, barY, x0 + fillW, barY + 1, 0x60FFFFFF);

        // muescas cada 10 %
        for (int n = 1; n < 10; n++) {
            int nx = x0 + WIDTH * n / 10;
            g.fill(nx, barY, nx + 1, barY + HEIGHT, 0x99000000);
        }

        // marcador de fase (50 %): dorado mientras no se cruzó, rojo después
        int mx = x0 + WIDTH / 2;
        int markColor = progress > 0.5F ? 0xFFFFD166 : 0xFFE5383B;
        g.fill(mx - 1, barY - 5, mx + 2, barY - 4, markColor);
        g.fill(mx - 2, barY - 4, mx + 3, barY - 3, markColor);
        g.fill(mx, barY + HEIGHT + 3, mx + 1, barY + HEIGHT + 5, markColor);
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
