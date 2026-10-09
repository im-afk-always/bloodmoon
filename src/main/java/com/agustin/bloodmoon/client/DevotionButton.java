package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.ClientDevotion;
import com.agustin.bloodmoon.Deity;
import com.agustin.bloodmoon.DevotionRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** El encapuchado en el inventario (bajo la grilla de crafteo). Al hacer clic abre la pantalla de devoción. */
public class DevotionButton extends AbstractButton {
    private static final int OFF_X = 150, OFF_Y = 59;
    private final InventoryScreen screen;

    public DevotionButton(InventoryScreen screen) {
        super(screen.getGuiLeft() + OFF_X, screen.getGuiTop() + OFF_Y, 20, 20, Component.translatable("bloodmoon.devotion.title"));
        this.screen = screen;
        Deity d = ClientDevotion.deity;
        Component tip = d == null
                ? Component.translatable("bloodmoon.devotion.tooltip.none")
                : Component.translatable("bloodmoon.devotion.tooltip", DevotionRank.of(ClientDevotion.reputation).displayName(), d.displayName());
        setTooltip(Tooltip.create(tip));
    }

    @Override
    public void onPress() {
        Minecraft.getInstance().setScreen(new DevotionScreen(screen));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // el libro de recetas corre el inventario: seguirlo
        setX(screen.getGuiLeft() + OFF_X);
        setY(screen.getGuiTop() + OFF_Y);
        int x = getX(), y = getY();
        // marco hundido, como una ranura
        g.fill(x, y, x + 20, y + 20, 0xFF8B8B8B);
        g.fill(x, y, x + 19, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + 19, 0xFF373737);
        g.fill(x + 1, y + 19, x + 20, y + 20, 0xFFFFFFFF);
        g.fill(x + 19, y + 1, x + 20, y + 20, 0xFFFFFFFF);
        DevotionIcon.draw(g, x + 2, y + 2, 1, ClientDevotion.deity);
        if (isHoveredOrFocused()) g.fill(x + 1, y + 1, x + 19, y + 19, 0x50FFFFFF);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
