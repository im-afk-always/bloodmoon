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
 * Posprocesado de las lunas especiales: corrección de color, contraste, resplandor de lo brillante y viñeta. Carmesí
 * en la Luna de la Cosecha, violeta en la Noche sin Luna (la grieta y el ojo irradian su luz).
 * Se aplica al terminar de dibujar el mundo (la mano y la interfaz quedan intactas), sube y baja con el mismo fundido
 * que el resto del cielo y no cuesta nada cuando no hay luna. Si los shaders no cargan, se desactiva y se registra.
 */
public final class MoonPostEffect {
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "shaders/post/harvest_moon.json");
    private static PostChain chain;
    private static boolean failed;
    private static int width = -1, height = -1;
    private static final boolean IRIS = ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");

    private MoonPostEffect() {}

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
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        com.agustin.bloodmoon.Eclipse.State ec = EclipseSkyRenderer.state(pt);
        boolean eclipse = ec != null && ec.dark() > 0.01F;
        if (!eclipse && type != MoonType.SUPER && type != MoonType.MOONLESS && type != MoonType.GOLDEN) return;
        float k = eclipse ? Math.min(1F, ec.dark() * 1.05F) : ClientMoonState.intensity(pt);
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
        boolean violet = !eclipse && type == MoonType.MOONLESS;
        boolean gold = !eclipse && type == MoonType.GOLDEN;
        //                        mul R/G/B                 tint R/G/B, mix              bloom R/G/B, gain           viñeta R/G/B
        float[] p = eclipse
                ? new float[]{0.98F, 0.93F, 0.84F, 0.98F, 0.88F, 0.72F, 0.35F, 1F, 0.95F, 0.85F, 1.15F, 0.8F, 0.7F, 0.6F}
                : violet
                ? new float[]{0.86F, 0.72F, 1.08F, 0.75F, 0.45F, 1.25F, 0.3F, 0.75F, 0.45F, 1F, 1.05F, 0.7F, 0.5F, 1F}
                : gold
                ? new float[]{1.06F, 0.97F, 0.8F, 1.15F, 0.88F, 0.45F, 0.22F, 1F, 0.82F, 0.48F, 0.95F, 1F, 0.8F, 0.5F}
                : new float[]{1.05F, 0.72F, 0.74F, 1.2F, 0.32F, 0.28F, 0.25F, 1F, 0.42F, 0.36F, 0.85F, 1F, 0.55F, 0.55F};
        String[] names = {"MulR", "MulG", "MulB", "TintR", "TintG", "TintB", "TintMix", "BloomR", "BloomG", "BloomB", "BloomGain", "VigR", "VigG", "VigB"};
        for (int i = 0; i < names.length; i++) chain.setUniform(names[i], p[i]);
        chain.setUniform("Threshold", eclipse ? 0.62F : violet ? 0.42F : gold ? 0.55F : 0.5F);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(pt);
        main.bindWrite(false);
        RenderSystem.enableDepthTest();
    }
}
