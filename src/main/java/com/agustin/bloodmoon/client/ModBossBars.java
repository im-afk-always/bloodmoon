package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.entity.Executioner;
import com.agustin.bloodmoon.entity.UnknownEmissary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Barras de jefe propias y compactas para todos los jefes del mod (reemplazan a la vanilla solo para ellos).
 * Grandes (caballeros del Vacío): 150 x 5 px, nombre a escala 0,8, estela de daño, brillo y marcador de fase.
 * Menores (Cursed Creeper, Jinete): 110 x 3 px, nombre a escala 0,65.
 */
public final class ModBossBars {
    private record Style(boolean major, int left, int right, int frame, int edge) {}

    private static final Map<String, Style> STYLES = Map.of(
            UnknownEmissary.NAME_KEY, new Style(true, 0x3C096C, 0xC77DFF, 0x5A189A, 0x9D4EDD),
            Executioner.NAME_KEY, new Style(true, 0x5C0A3C, 0xFF5CC8, 0x7A1450, 0xD94C9A),
            "entity.bloodmoon.cursed_creeper", new Style(false, 0x6A040F, 0xE5383B, 0x5A0A0A, 0xA4161A),
            "entity.bloodmoon.apocalypse_rider", new Style(false, 0x2B2B2B, 0xB0B0B0, 0x3A3A3A, 0x6E6E6E));

    private static final Map<UUID, Float> GHOST = new HashMap<>();

    private ModBossBars() {}

    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents tc)) return;
        Style style = STYLES.get(tc.getKey());
        if (style == null) return;

        event.setCanceled(true);
        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int width = style.major() ? 150 : 110;
        int height = style.major() ? 5 : 3;
        float textScale = style.major() ? 0.8F : 0.65F;
        event.setIncrement(style.major() ? 19 : 14);

        float progress = Mth.clamp(boss.getProgress(), 0F, 1F);
        float ghost = Math.max(progress, GHOST.getOrDefault(boss.getId(), progress) - 0.0025F);
        GHOST.put(boss.getId(), ghost);
        long now = System.currentTimeMillis();

        int cx = g.guiWidth() / 2;
        int x0 = cx - width / 2;
        int y = event.getY();
        int barY = y + (style.major() ? 8 : 6);

        // nombre (escalado)
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(textScale, textScale, 1F);
        g.drawCenteredString(font, boss.getName(), 0, 0, 0xFFFFFFFF);
        g.pose().popPose();

        // marco
        int frame = 0xFF000000 | style.frame(), edge = 0xFF000000 | style.edge();
        g.fill(x0 - 2, barY - 2, x0 + width + 2, barY + height + 2, 0xE0080010);
        g.fill(x0 - 2, barY - 2, x0 + width + 2, barY - 1, edge);
        g.fill(x0 - 2, barY + height + 1, x0 + width + 2, barY + height + 2, frame);
        g.fill(x0 - 2, barY - 2, x0 - 1, barY + height + 2, frame);
        g.fill(x0 + width + 1, barY - 2, x0 + width + 2, barY + height + 2, frame);
        if (style.major()) { // puntas tipo empuñadura
            g.fill(x0 - 4, barY + height / 2 - 1, x0 - 2, barY + height / 2 + 2, edge);
            g.fill(x0 + width + 2, barY + height / 2 - 1, x0 + width + 4, barY + height / 2 + 2, edge);
        }

        g.fill(x0, barY, x0 + width, barY + height, 0xFF12001A);
        g.fill(x0, barY, x0 + Math.round(width * ghost), barY + height, 0xCCE8C8FF);

        int fillW = Math.round(width * progress);
        float shine = (now % 2400L) / 2400F;
        for (int i = 0; i < fillW; i++) {
            float f = i / (float) width;
            float s = style.major() ? Math.max(0F, 1F - Math.abs(f - shine) * 12F) : 0F;
            int r = lerpC(style.left() >> 16 & 255, style.right() >> 16 & 255, f, s);
            int gg = lerpC(style.left() >> 8 & 255, style.right() >> 8 & 255, f, s);
            int b = lerpC(style.left() & 255, style.right() & 255, f, s * 0.3F);
            g.fill(x0 + i, barY, x0 + i + 1, barY + height, 0xFF000000 | r << 16 | gg << 8 | b);
        }
        if (fillW > 0 && height > 3) g.fill(x0, barY, x0 + fillW, barY + 1, 0x50FFFFFF);

        if (style.major()) {
            for (int n = 1; n < 10; n++) {
                int nx = x0 + width * n / 10;
                g.fill(nx, barY, nx + 1, barY + height, 0x88000000);
            }
            int mx = x0 + width / 2;
            int mark = progress > 0.5F ? 0xFFFFD166 : 0xFFE5383B;
            g.fill(mx - 1, barY - 4, mx + 2, barY - 3, mark);
            g.fill(mx, barY - 3, mx + 1, barY - 2, mark);
        }
    }

    private static int lerpC(int a, int b, float t, float shine) {
        return Math.min(255, (int) Mth.lerp(t, a, b) + (int) (60 * shine));
    }
}
