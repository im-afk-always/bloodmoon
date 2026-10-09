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

/**
 * El encapuchado en el inventario, a la altura del botón del libro de recetas y con su mismo estilo (botón en relieve,
 * contorno blanco al pasar el mouse). Al hacer clic abre la pantalla de devoción.
 */
public class DevotionButton extends AbstractButton {
    private static final int OFF_X = 150, OFF_Y = 61, W = 20, H = 18;
    private final InventoryScreen screen;

    public DevotionButton(InventoryScreen screen) {
        super(screen.getGuiLeft() + OFF_X, screen.getGuiTop() + OFF_Y, W, H, Component.translatable("bloodmoon.devotion.title"));
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
        boolean hot = isHoveredOrFocused();
        int outline = hot ? 0xFFFFFFFF : 0xFF000000;
        // contorno con esquinas recortadas
        g.fill(x + 1, y, x + W - 1, y + 1, outline);
        g.fill(x + 1, y + H - 1, x + W - 1, y + H, outline);
        g.fill(x, y + 1, x + 1, y + H - 1, outline);
        g.fill(x + W - 1, y + 1, x + W, y + H - 1, outline);
        // cara y relieve: luz arriba-izquierda, sombra abajo-derecha
        g.fill(x + 1, y + 1, x + W - 1, y + H - 1, hot ? 0xFFA9A9A9 : 0xFF9A9A9A);
        g.fill(x + 1, y + 1, x + W - 2, y + 2, 0xFFDBDBDB);
        g.fill(x + 1, y + 1, x + 2, y + H - 2, 0xFFDBDBDB);
        g.fill(x + 2, y + H - 2, x + W - 1, y + H - 1, 0xFF555555);
        g.fill(x + W - 2, y + 2, x + W - 1, y + H - 1, 0xFF555555);
        DevotionIcon.draw(g, x + 2, y + 1, 1, ClientDevotion.deity);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
