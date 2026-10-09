package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.ClientOffering;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Contador de arriba a la derecha durante la Luna de la Cosecha: "Ofrendas restantes 12/30". */
public final class OfferingHud {
    private OfferingHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientOffering.active || mc.options.hideGui || mc.level == null) return;
        Font font = mc.font;
        int required = ClientOffering.required, done = Math.min(ClientOffering.done, required);
        int remaining = Math.max(0, required - done);
        boolean complete = remaining == 0;
        Component line = complete
                ? Component.translatable("bloodmoon.offering.hud.complete")
                : Component.translatable("bloodmoon.offering.hud", remaining, required);
        Component sub = ClientOffering.unskippable ? Component.translatable("bloodmoon.offering.hud.nosleep") : null;

        int w = g.guiWidth();
        int lw = font.width(line), sw = sub == null ? 0 : font.width(sub);
        int boxW = Math.max(lw, sw) + 12, boxH = sub == null ? 22 : 33;
        int x0 = w - boxW - 6, y0 = 6;
        g.fill(x0, y0, x0 + boxW, y0 + boxH, 0x9A120004);
        g.fill(x0, y0, x0 + boxW, y0 + 1, 0xFFB01E1E);
        // barra de progreso
        float frac = required <= 0 ? 1F : done / (float) required;
        int barW = boxW - 12;
        g.fill(x0 + 6, y0 + 15, x0 + 6 + barW, y0 + 18, 0xFF2A0606);
        g.fill(x0 + 6, y0 + 15, x0 + 6 + Math.round(barW * frac), y0 + 18, complete ? 0xFFE8B84A : 0xFFD02A2A);
        float pulse = complete ? 1F : 0.85F + 0.15F * Mth.sin((mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false)) * 0.15F);
        int a = (int) (pulse * 255) << 24;
        g.drawString(font, line, x0 + boxW - 6 - lw, y0 + 4, a | (complete ? 0xF5D27A : 0xF0D0D0), true);
        if (sub != null) g.drawString(font, sub, x0 + boxW - 6 - sw, y0 + 22, 0xFFE04040, true);
    }
}
