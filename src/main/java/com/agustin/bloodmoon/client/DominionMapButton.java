package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Botón del mapa del Dominio en el inventario, junto al encapuchado (mismo estilo que el libro de recetas). */
public class DominionMapButton extends AbstractButton {
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/gui/map_button.png");
    private static final int OFF_X = 127, OFF_Y = 61, W = 20, H = 18;
    private final InventoryScreen screen;

    public DominionMapButton(InventoryScreen screen) {
        super(screen.getGuiLeft() + OFF_X, screen.getGuiTop() + OFF_Y, W, H, Component.translatable("bloodmoon.map.title"));
        this.screen = screen;
        setTooltip(Tooltip.create(Component.translatable("bloodmoon.map.title")));
    }

    @Override
    public void onPress() {
        Minecraft.getInstance().setScreen(new DominionMapScreen(screen));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        setX(screen.getGuiLeft() + OFF_X);
        setY(screen.getGuiTop() + OFF_Y);
        int x = getX(), y = getY();
        frame(g, x, y, W, H, isHoveredOrFocused());
        g.blit(ICON, x + 2, y + 1, 0, 0, 16, 16, 16, 16);
    }

    /** Botón en relieve como el del libro de recetas (contorno blanco al pasar el mouse). */
    static void frame(GuiGraphics g, int x, int y, int w, int h, boolean hot) {
        int outline = hot ? 0xFFFFFFFF : 0xFF000000;
        g.fill(x + 1, y, x + w - 1, y + 1, outline);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, outline);
        g.fill(x, y + 1, x + 1, y + h - 1, outline);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, outline);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hot ? 0xFFA9A9A9 : 0xFF9A9A9A);
        g.fill(x + 1, y + 1, x + w - 2, y + 2, 0xFFDBDBDB);
        g.fill(x + 1, y + 1, x + 2, y + h - 2, 0xFFDBDBDB);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
