package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.WatcherEye;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Ojo Vigía: siempre te mira (billboard) con un halo púrpura. */
public class WatcherEyeRenderer extends EntityRenderer<WatcherEye> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_eye/watcher.png");
    private static final ResourceLocation HALO = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_eye/flare.png");

    public WatcherEyeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(WatcherEye entity) {
        return TEX;
    }

    @Override
    protected int getBlockLightLevel(WatcherEye entity, net.minecraft.core.BlockPos pos) {
        return 15;
    }

    @Override
    public void render(WatcherEye e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        float t = e.tickCount + pt;
        ps.pushPose();
        ps.translate(0F, 0.45F, 0F);
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        ps.mulPose(Axis.YP.rotationDegrees(180F));
        float s = 1.1F + 0.08F * Mth.sin(t * 0.5F);
        ps.pushPose();
        ps.scale(s * 2.4F, s * 2.4F, s * 2.4F);
        quad(ps.last(), buf.getBuffer(RenderType.eyes(HALO)), 0.55F, 0.12F, 0.8F, true);
        ps.popPose();
        ps.scale(s, s, s);
        quad(ps.last(), buf.getBuffer(RenderType.entityCutoutNoCull(TEX)), 1F, 1F, 1F, false);
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buf, packedLight);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float r, float g, float b, boolean both) {
        v(vc, pose, -0.5F, -0.5F, 0, 1, r, g, b);
        v(vc, pose, 0.5F, -0.5F, 1, 1, r, g, b);
        v(vc, pose, 0.5F, 0.5F, 1, 0, r, g, b);
        v(vc, pose, -0.5F, 0.5F, 0, 0, r, g, b);
        if (!both) return;
        v(vc, pose, -0.5F, 0.5F, 0, 0, r, g, b);
        v(vc, pose, 0.5F, 0.5F, 1, 0, r, g, b);
        v(vc, pose, 0.5F, -0.5F, 1, 1, r, g, b);
        v(vc, pose, -0.5F, -0.5F, 0, 1, r, g, b);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, float r, float g, float b) {
        vc.addVertex(pose, x, y, 0F).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0F, 0F, 1F);
    }
}
