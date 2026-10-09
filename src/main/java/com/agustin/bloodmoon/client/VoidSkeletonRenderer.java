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

/** Esqueleto de hueso negro con grietas y ojos púrpura que brillan, más el brillo del Set del Vacío. */
public class VoidSkeletonRenderer extends SkeletonRenderer<VoidSkeleton> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_skeleton.png");
    private static final ResourceLocation CAPTAIN =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_captain.png");
    private static final ResourceLocation FORGER =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_forger.png");
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_skeleton_glow.png");

    public VoidSkeletonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.addLayer(new EyesLayer<VoidSkeleton, SkeletonModel<VoidSkeleton>>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }
        });
        this.addLayer(new VoidArmorGlowLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(VoidSkeleton entity) {
        if (entity instanceof com.agustin.bloodmoon.entity.VoidCaptain) return CAPTAIN;
        if (entity instanceof com.agustin.bloodmoon.entity.VoidForger) return FORGER;
        return TEXTURE;
    }

    @Override
    protected void scale(VoidSkeleton entity, PoseStack poseStack, float partialTick) {
        // el Capitán y el Forjador ya traen su tamaño en el atributo SCALE
        float s = entity instanceof com.agustin.bloodmoon.entity.VoidCaptain || entity instanceof com.agustin.bloodmoon.entity.VoidForger
                ? 1.0F : !entity.isArcher() ? 1.1F : 1.0F;
        poseStack.scale(s, s, s);
        if (entity instanceof com.agustin.bloodmoon.entity.VoidForger) {   // encorvado
            poseStack.translate(0, 0.08, 0);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(12));
        }
    }
}
