package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.UnknownEmissary;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

public class EmissaryRenderer extends MobRenderer<UnknownEmissary, EmissaryModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/unknown_emissary.png");
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/unknown_emissary_glow.png");
    private static final float SCALE = 4.8F;

    public EmissaryRenderer(EntityRendererProvider.Context context) {
        super(context, new EmissaryModel(context.bakeLayer(EmissaryModel.LAYER)), 3.0F);
        this.addLayer(new GlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(UnknownEmissary entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(UnknownEmissary entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    /** Ojos del yelmo, runa del pecho y runas del espadón brillan en la oscuridad. */
    static class GlowLayer extends EyesLayer<UnknownEmissary, EmissaryModel> {
        GlowLayer(RenderLayerParent<UnknownEmissary, EmissaryModel> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return RenderType.eyes(GLOW);
        }
    }
}
