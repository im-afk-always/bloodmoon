package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.VoidSkeleton;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.AbstractSkeleton;

/** Esqueleto de hueso negro con grietas y ojos púrpura que brillan, más el brillo del Set del Vacío. */
public class VoidSkeletonRenderer extends SkeletonRenderer {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_skeleton.png");
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_skeleton_glow.png");

    public VoidSkeletonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.addLayer(new EyesLayer<AbstractSkeleton, SkeletonModel<AbstractSkeleton>>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }
        });
        this.addLayer(new VoidArmorGlowLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractSkeleton entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(AbstractSkeleton entity, PoseStack poseStack, float partialTick) {
        float s = entity instanceof VoidSkeleton v && !v.isArcher() ? 1.1F : 1.0F;
        poseStack.scale(s, s, s);
    }
}
