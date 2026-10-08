package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonClientConfig;
import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.ClientMoonState;
import com.agustin.bloodmoon.MoonType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Posprocesado de la Luna de la Cosecha: corrección de color carmesí, contraste, resplandor de lo brillante y viñeta.
 * Se aplica al terminar de dibujar el mundo (la mano y la interfaz quedan intactas), sube y baja con el mismo fundido
 * que el resto del cielo y no cuesta nada cuando no hay luna. Si los shaders no cargan, se desactiva y se registra.
 */
public final class HarvestPostEffect {
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "shaders/post/harvest_moon.json");
    private static PostChain chain;
    private static boolean failed;
    private static int width = -1, height = -1;
    private static final boolean IRIS = ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");

    private HarvestPostEffect() {}

    /** Tras una recarga de recursos (F3+T) se reconstruye. */
    public static void invalidate() {
        if (chain != null) chain.close();
        chain = null;
        failed = false;
        width = height = -1;
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (failed || IRIS || !BloodMoonClientConfig.HARVEST_POST_EFFECT.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return;
        MoonType type = ClientMoonState.visual();
        if (!type.customSky()) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float k = ClientMoonState.intensity(pt);
        if (k <= 0.01F) return;

        RenderTarget main = mc.getMainRenderTarget();
        if (chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, CHAIN);
            } catch (Exception e) {
                BloodMoonMod.LOGGER.error("Harvest Moon post effect could not load; disabling it", e);
                failed = true;
                return;
            }
        }
        if (main.width != width || main.height != height) {
            width = main.width;
            height = main.height;
            chain.resize(width, height);
        }
        chain.setUniform("Intensity", k);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(pt);
        main.bindWrite(false);
        RenderSystem.enableDepthTest();
    }
}
