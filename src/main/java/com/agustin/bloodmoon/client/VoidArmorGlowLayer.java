package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/** Ojos del yelmo, runa del pecho y gemas del Set del Vacío brillan en la oscuridad. */
public class VoidArmorGlowLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/models/armor/void_glow.png");
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public VoidArmorGlowLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        VertexConsumer consumer = null;
        M parent = getParentModel();
        for (EquipmentSlot slot : SLOTS) {
            if (!VoidArmorModels.isVoidPiece(entity.getItemBySlot(slot), slot)) continue;
            HumanoidModel<LivingEntity> model = VoidArmorModels.get(slot);
            model.head.copyFrom(parent.head);
            model.hat.copyFrom(parent.hat);
            model.body.copyFrom(parent.body);
            model.rightArm.copyFrom(parent.rightArm);
            model.leftArm.copyFrom(parent.leftArm);
            model.rightLeg.copyFrom(parent.rightLeg);
            model.leftLeg.copyFrom(parent.leftLeg);
            if (consumer == null) consumer = buffer.getBuffer(RenderType.eyes(GLOW));
            model.renderToBuffer(poseStack, consumer, 0xF000F0, OverlayTexture.NO_OVERLAY);
        }
    }
}
