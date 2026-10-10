package com.agustin.bloodmoon.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Tecla M (configurable): abre el mapa del Dominio desde el juego, en cualquier modo; otra vez M lo cierra. */
public final class MapKey {
    public static final KeyMapping OPEN_MAP = new KeyMapping("key.bloodmoon.map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M,
            "key.categories.bloodmoon");

    private MapKey() {}

    public static void onRegister(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAP);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_MAP.consumeClick()) {
            if (mc.player != null && mc.screen == null) mc.setScreen(new DominionMapScreen(null));
        }
    }
}
