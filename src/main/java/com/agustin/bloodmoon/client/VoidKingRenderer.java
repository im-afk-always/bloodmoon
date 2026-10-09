package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.VoidKing;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Rey del Vacío (×1,35): oro y obsidiana; ojos, rejilla, gemas, orbes, runas y satélites encendidos. */
public class VoidKingRenderer extends MobRenderer<VoidKing, VoidKingModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_king.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_king_glow.png");
    private static final float SCALE = 1.35F;

    public VoidKingRenderer(EntityRendererProvider.Context context) {
        super(context, new VoidKingModel(context.bakeLayer(VoidKingModel.LAYER)), 1.6F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(VoidKing entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(VoidKing entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }
}
