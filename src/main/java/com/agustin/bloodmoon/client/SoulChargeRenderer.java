package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.SoulCharge;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Esfera de fuego púrpura (billboard aditivo); el tamaño crece con la potencia de la carga. */
public class SoulChargeRenderer extends EntityRenderer<SoulCharge> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/soul_charge.png");
    private static final RenderType RENDER_TYPE = RenderType.eyes(TEXTURE);

    public SoulChargeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(SoulCharge entity, net.minecraft.core.BlockPos pos) {
        return 15;
    }

    @Override
    public void render(SoulCharge entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float size = 0.9F * entity.multiplier();
        float spin = (entity.tickCount + partialTick) * 12F;
        VertexConsumer vc = buffer.getBuffer(RENDER_TYPE);
        for (int layer = 0; layer < 2; layer++) {
            poseStack.pushPose();
            float s = size * (layer == 0 ? 1F : 1.6F);
            poseStack.scale(s, s, s);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.YP.rotationDegrees(180F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(layer == 0 ? spin : -spin * 0.6F));
            PoseStack.Pose pose = poseStack.last();
            int a = layer == 0 ? 255 : 110;
            // las dos caras: el tipo de render aditivo descarta la cara trasera
            vertex(vc, pose, -0.5F, -0.5F, 0, 1, a);
            vertex(vc, pose, 0.5F, -0.5F, 1, 1, a);
            vertex(vc, pose, 0.5F, 0.5F, 1, 0, a);
            vertex(vc, pose, -0.5F, 0.5F, 0, 0, a);
            vertex(vc, pose, -0.5F, 0.5F, 0, 0, a);
            vertex(vc, pose, 0.5F, 0.5F, 1, 0, a);
            vertex(vc, pose, 0.5F, -0.5F, 1, 1, a);
            vertex(vc, pose, -0.5F, -0.5F, 0, 1, a);
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int bright) {
        vc.addVertex(pose, x, y, 0F).setColor(bright, bright, bright, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0F, 1F, 0F);
    }

    @Override
    public ResourceLocation getTextureLocation(SoulCharge entity) {
        return TEXTURE;
    }
}
