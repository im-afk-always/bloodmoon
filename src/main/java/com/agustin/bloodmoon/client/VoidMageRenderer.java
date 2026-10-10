package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.VoidMage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Hechicero del Vacío: ojos, runas del tabardo, cristal del cetro y cristales orbitantes encendidos. */
public class VoidMageRenderer extends MobRenderer<VoidMage, VoidMageModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_mage.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_mage_glow.png");
    private static final float SCALE = 1.0F;

    public VoidMageRenderer(EntityRendererProvider.Context context) {
        super(context, new VoidMageModel(context.bakeLayer(VoidMageModel.LAYER)), 0.6F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(VoidMage entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(VoidMage entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }
}
