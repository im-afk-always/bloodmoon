package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.ClientDevotion;
import com.agustin.bloodmoon.Deity;
import com.agustin.bloodmoon.DevotionRank;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pantalla de devoción. Por ahora: deidad, rango, nivel de adoración y reputación (con la barra al siguiente rango). */
public class DevotionScreen extends Screen {
    private static final int W = 236, H = 112;
    private final Screen parent;

    public DevotionScreen(Screen parent) {
        super(Component.translatable("bloodmoon.devotion.title"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Deity deity = ClientDevotion.deity;
        int accent = deity == null ? 0xFF5A5458 : deity.eyeColor;
        int x0 = (width - W) / 2, y0 = (height - H) / 2;

        // panel
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, 0xFF000000);
        g.fillGradient(x0, y0, x0 + W, y0 + H, 0xF0161013, 0xF0080506);
        g.fill(x0, y0, x0 + W, y0 + 1, accent);
        g.fill(x0, y0 + H - 1, x0 + W, y0 + H, accent & 0x60FFFFFF);
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), x0 + W / 2, y0 + 7, 0xFFE8DCD0);

        // retrato
        int px = x0 + 12, py = y0 + 26;
        g.fill(px - 2, py - 2, px + 66, py + 66, 0xFF050304);
        g.fill(px - 2, py - 2, px + 66, py - 1, accent & 0x90FFFFFF);
        DevotionIcon.draw(g, px, py, 4, deity);

        int tx = px + 78, ty = py + 2;
        if (deity == null) {
            g.drawString(font, Component.translatable("bloodmoon.devotion.screen.none"), tx, ty + 6, 0xFFB0A8AC, false);
            g.drawWordWrap(font, Component.translatable("bloodmoon.devotion.screen.none.hint"), tx, ty + 22, W - (tx - x0) - 10, 0xFF706870);
            return;
        }
        int rep = ClientDevotion.reputation;
        DevotionRank rank = DevotionRank.of(rep);
        DevotionRank next = rank.next();
        g.drawString(font, deity.displayName(), tx, ty, accent | 0xFF000000, true);
        g.drawString(font, Component.translatable("bloodmoon.devotion.screen.rank", rank.displayName()), tx, ty + 14, 0xFFE8DCD0, false);
        g.drawString(font, Component.translatable("bloodmoon.devotion.screen.level", rank.roman()), tx, ty + 26, 0xFFC8BCB0, false);
        int centi = rep * 100 + ClientDevotion.partial;
        g.drawString(font, Component.translatable("bloodmoon.devotion.screen.reputation", DevotionRank.format(centi)), tx, ty + 38, 0xFFC8BCB0, false);

        // barra al siguiente rango
        int bw = W - (tx - x0) - 12, by = ty + 52;
        g.fill(tx, by, tx + bw, by + 4, 0xFF2A1A1E);
        float frac = next == null ? 1F : (centi / 100F - rank.minReputation) / (float) (next.minReputation - rank.minReputation);
        g.fill(tx, by, tx + Math.round(bw * Math.min(1F, frac)), by + 4, accent);
        Component prog = next == null
                ? Component.translatable("bloodmoon.devotion.screen.max")
                : Component.translatable("bloodmoon.devotion.screen.next", DevotionRank.format(centi), next.minReputation, next.displayName());
        g.drawString(font, prog, tx, by + 7, 0xFF8A8086, false);
    }
}
