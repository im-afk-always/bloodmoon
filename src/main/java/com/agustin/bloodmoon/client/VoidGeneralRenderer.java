package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.VoidGeneral;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** General del Vacío (×0,9 sobre un modelo de doble resolución): armadura oscura con la visera, el ojo del peto, la hoja y las runas encendidas. */
public class VoidGeneralRenderer extends MobRenderer<VoidGeneral, VoidGeneralModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_general.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_general_glow.png");
    private static final float SCALE = 0.9F;

    public VoidGeneralRenderer(EntityRendererProvider.Context context) {
        super(context, new VoidGeneralModel(context.bakeLayer(VoidGeneralModel.LAYER)), 1.2F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(VoidGeneral entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(VoidGeneral entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }
}
