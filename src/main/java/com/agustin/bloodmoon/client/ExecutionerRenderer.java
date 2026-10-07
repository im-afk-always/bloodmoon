package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.Executioner;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

public class ExecutionerRenderer extends MobRenderer<Executioner, ExecutionerModel> {
    private static final ResourceLocation TEXTURE = tex("executioner");
    private static final ResourceLocation GLOW = tex("executioner_glow");
    private static final ResourceLocation HAMMER_GLOW = tex("executioner_hammer_glow");
    private static final float SCALE = 2.4F;

    public ExecutionerRenderer(EntityRendererProvider.Context context) {
        super(context, new ExecutionerModel(context.bakeLayer(ExecutionerModel.LAYER)), 3.2F);
        this.addLayer(new GlowLayer(this));
        this.addLayer(new HammerGlowLayer(this));
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(Executioner entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Executioner entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    static class GlowLayer extends EyesLayer<Executioner, ExecutionerModel> {
        GlowLayer(RenderLayerParent<Executioner, ExecutionerModel> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return RenderType.eyes(GLOW);
        }
    }

    /** La cabeza del mazo se enciende durante el Juicio Final. */
    static class HammerGlowLayer extends RenderLayer<Executioner, ExecutionerModel> {
        HammerGlowLayer(RenderLayerParent<Executioner, ExecutionerModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Executioner entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            float k = entity.hammerGlow(partialTick);
            if (k <= 0.01F) return;
            int c = (int) (255 * k);
            VertexConsumer vc = buffer.getBuffer(RenderType.eyes(HAMMER_GLOW));
            getParentModel().renderToBuffer(poseStack, vc, 15728640, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(255, c, c, c));
        }
    }
}
