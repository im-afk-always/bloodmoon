package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.ClientAstralState;
import com.agustin.bloodmoon.registry.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Llamas púrpuras sobre las entidades con Quemadura astral (como el fuego vanilla, con textura propia)
 * y overlay en primera persona para el jugador que arde.
 */
public final class AstralFlameRenderer {
    public static final int FRAMES = 32;
    private static final ResourceLocation FIRE_0 =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/block/astral_fire_0.png");
    private static final ResourceLocation FIRE_1 =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/block/astral_fire_1.png");

    private AstralFlameRenderer() {}

    private static boolean burning(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player) return mc.player.hasEffect(ModEffects.ASTRAL_BURN);
        return ClientAstralState.isBurning(entity.getId());
    }

    private static int frame() {
        Minecraft mc = Minecraft.getInstance();
        long t = mc.level != null ? mc.level.getGameTime() : 0;
        return (int) ((t / 2) % FRAMES);
    }

    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!burning(entity) || entity.isInvisible()) return;
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player && mc.options.getCameraType().isFirstPerson()) return;

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        float scale = entity.getBbWidth() * 1.4F;
        ps.scale(scale, scale, scale);
        ps.mulPose(Axis.YP.rotationDegrees(-mc.gameRenderer.getMainCamera().getYRot()));
        float remaining = entity.getBbHeight() / scale;
        float halfW = 0.5F, yOff = 0F, z = 0F;
        ps.translate(0F, 0F, 0.3F - (int) remaining * 0.02F);
        int f = frame();
        float v0 = f / (float) FRAMES, v1 = (f + 1) / (float) FRAMES;
        for (int i = 0; remaining > 0F; i++) {
            ResourceLocation tex = i % 2 == 0 ? FIRE_0 : FIRE_1;
            VertexConsumer vc = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(tex));
            PoseStack.Pose pose = ps.last();
            float u0 = 0F, u1 = 1F;
            if (i / 2 % 2 == 0) { u0 = 1F; u1 = 0F; }
            vertex(vc, pose, -halfW, 0F - yOff, z, u1, v1);
            vertex(vc, pose, halfW, 0F - yOff, z, u0, v1);
            vertex(vc, pose, halfW, 1.4F - yOff, z, u0, v0);
            vertex(vc, pose, -halfW, 1.4F - yOff, z, u1, v0);
            remaining -= 0.45F;
            yOff -= 0.45F;
            halfW *= 0.9F;
            z -= 0.03F;
        }
        ps.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v) {
        vc.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0F, 1F, 0F);
    }

    /** Capa de HUD: llamas púrpuras subiendo por los bordes inferiores de la pantalla. */
    public static void renderOverlay(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !mc.options.getCameraType().isFirstPerson() || !player.hasEffect(ModEffects.ASTRAL_BURN)) return;
        int w = g.guiWidth(), h = g.guiHeight();
        int size = (int) (Math.min(w, h) * 0.55F);
        int f = frame();
        RenderSystem.enableBlend();
        g.setColor(1F, 1F, 1F, 0.8F);
        g.blit(FIRE_0, -size / 6, h - size + size / 8, size, size, 0, f * 16, 16, 16, 16, 16 * FRAMES);
        g.blit(FIRE_1, w - size + size / 6, h - size + size / 8, size, size, 0, f * 16, 16, 16, 16, 16 * FRAMES);
        g.setColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }
}
