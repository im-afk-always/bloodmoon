package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.FirstSoulDragon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public class FirstSoulDragonRenderer extends MobRenderer<FirstSoulDragon, FirstSoulDragonModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/first_soul_dragon.png");
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/first_soul_dragon_glow.png");

    public FirstSoulDragonRenderer(EntityRendererProvider.Context context) {
        super(context, new FirstSoulDragonModel(context.bakeLayer(FirstSoulDragonModel.LAYER)), 0F);
        this.addLayer(new SoulGlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(FirstSoulDragon entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(FirstSoulDragon entity, PoseStack poseStack, float partialTick) {
        float k = 2.4F * entity.getDragonScale();
        poseStack.scale(k, k, k);
    }

    @Override
    protected float getFlipDegrees(FirstSoulDragon entity) {
        return 0F; // no se da vuelta al morir: se desarma en el aire
    }

    /** Grietas, ojos, garras y el alma del costillar; el alma late. */
    static class SoulGlowLayer extends RenderLayer<FirstSoulDragon, FirstSoulDragonModel> {
        SoulGlowLayer(RenderLayerParent<FirstSoulDragon, FirstSoulDragonModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, FirstSoulDragon entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            float pulse = 0.78F + 0.22F * Mth.sin(ageInTicks * 0.15F);
            if (entity.deathTime > 0) pulse = Math.min(1F, pulse + entity.deathTime / 60F);
            int c = (int) (255 * pulse);
            VertexConsumer vc = buffer.getBuffer(RenderType.eyes(GLOW));
            getParentModel().renderToBuffer(poseStack, vc, 15728640, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(255, c, c, c));
        }
    }
}
