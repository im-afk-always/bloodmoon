package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.ClientBloodMoonState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Solo cliente: fundido del cielo y color de niebla/horizonte durante la Luna de Sangre. */
@Mod(value = BloodMoonMod.MODID, dist = Dist.CLIENT)
public class BloodMoonClient {

    public BloodMoonClient() {
        NeoForge.EVENT_BUS.addListener(BloodMoonClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(BloodMoonClient::onFogColor);
        NeoForge.EVENT_BUS.addListener(BloodMoonClient::onLogout);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) return;
        ClientBloodMoonState.tick();
    }

    /** El color de niebla también es el del horizonte y el "fondo" del cielo: lo llevamos a casi negro. */
    private static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE) return;
        float k = ClientBloodMoonState.intensity((float) event.getPartialTick());
        if (k <= 0F) return;
        event.setRed(lerp(event.getRed(), 0.03F, k));
        event.setGreen(lerp(event.getGreen(), 0.0F, k));
        event.setBlue(lerp(event.getBlue(), 0.0F, k));
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBloodMoonState.reset();
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
