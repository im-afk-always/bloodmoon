package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.entity.UnknownEmissary;
import com.agustin.bloodmoon.BloodMoonMod;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Emisario Desconocido: armadura negra en capas (peto, cresta, hombreras con lama, guanteletes, rodilleras,
 * escarpes), yelmo astado y espadón rúnico con la hoja de canto (filo hacia el golpe).
 * Modelo a doble resolución (80 px de alto); el renderer lo escala x2,4 (~12 bloques).
 */
public class EmissaryModel extends HierarchicalModel<UnknownEmissary> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "unknown_emissary"), "main");

    private static final float ARM_X = -0.9F;
    private static final float SWORD_X = 1.54F;
    private static final float PI = Mth.PI;

    private final ModelPart root, body, head, cape, rightArm, leftArm, sword, rightLeg, leftLeg;

    public EmissaryModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.cape = body.getChild("cape");
        this.rightArm = body.getChild("rightArm");
        this.leftArm = body.getChild("leftArm");
        this.sword = rightArm.getChild("sword");
        this.rightLeg = root.getChild("rightLeg");
        this.leftLeg = root.getChild("leftLeg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(144, 0).addBox(-12.0F, -24.0F, -7.0F, 24.0F, 24.0F, 14.0F)
                .texOffs(146, 84).addBox(-13.0F, -23.0F, -9.0F, 26.0F, 14.0F, 2.0F)
                .texOffs(202, 84).addBox(-1.0F, -23.0F, -10.0F, 2.0F, 14.0F, 1.0F)
                .texOffs(72, 103).addBox(-11.0F, -9.0F, -9.0F, 22.0F, 3.0F, 2.0F)
                .texOffs(280, 58).addBox(-13.0F, -6.0F, -8.0F, 26.0F, 6.0F, 16.0F)
                .texOffs(100, 84).addBox(-11.0F, -23.0F, 7.0F, 22.0F, 16.0F, 1.0F),
                PartPose.offset(0.0F, -8.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(220, 0).addBox(-8.0F, -18.0F, -9.0F, 16.0F, 18.0F, 18.0F)
                .texOffs(120, 103).addBox(-9.0F, -15.0F, -10.0F, 18.0F, 3.0F, 2.0F)
                .texOffs(308, 84).addBox(-9.0F, -10.0F, -10.0F, 4.0F, 9.0F, 2.0F)
                .texOffs(320, 84).addBox(5.0F, -10.0F, -10.0F, 4.0F, 9.0F, 2.0F)
                .texOffs(0, 58).addBox(-1.0F, -24.0F, -8.0F, 2.0F, 8.0F, 18.0F)
                .texOffs(112, 58).addBox(-10.0F, -2.0F, -10.0F, 20.0F, 4.0F, 20.0F)
                .texOffs(376, 84).addBox(-12.0F, -16.0F, -3.0F, 4.0F, 4.0F, 6.0F)
                .texOffs(252, 84).addBox(-14.0F, -24.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                .texOffs(396, 84).addBox(8.0F, -16.0F, -3.0F, 4.0F, 4.0F, 6.0F)
                .texOffs(268, 84).addBox(10.0F, -24.0F, -2.0F, 4.0F, 8.0F, 4.0F),
                PartPose.offset(0.0F, -24.0F, 0.0F));
        PartDefinition cape = body.addOrReplaceChild("cape", CubeListBuilder.create()
                .texOffs(16, 0).addBox(-11.0F, 0.0F, 0.0F, 22.0F, 44.0F, 2.0F),
                PartPose.offset(0.0F, -23.0F, 8.0F));
        PartDefinition rightArm = body.addOrReplaceChild("rightArm", CubeListBuilder.create()
                .texOffs(288, 0).addBox(-9.0F, -6.0F, -8.0F, 14.0F, 12.0F, 16.0F)
                .texOffs(364, 58).addBox(-10.0F, 5.0F, -9.0F, 14.0F, 4.0F, 18.0F)
                .texOffs(416, 84).addBox(-7.0F, -12.0F, -2.0F, 4.0F, 6.0F, 4.0F)
                .texOffs(40, 58).addBox(-6.0F, -2.0F, -5.0F, 8.0F, 16.0F, 10.0F)
                .texOffs(192, 58).addBox(-7.0F, 14.0F, -6.0F, 10.0F, 12.0F, 12.0F),
                PartPose.offset(-15.0F, -20.0F, 0.0F));
        PartDefinition sword = rightArm.addOrReplaceChild("sword", CubeListBuilder.create()
                .texOffs(492, 58).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 16.0F, 4.0F)
                .texOffs(432, 84).addBox(-10.0F, -12.0F, -3.0F, 20.0F, 4.0F, 6.0F)
                .texOffs(0, 0).addBox(-1.0F, -64.0F, -3.0F, 2.0F, 52.0F, 6.0F)
                .texOffs(60, 103).addBox(-1.0F, -68.0F, -2.0F, 2.0F, 4.0F, 4.0F)
                .texOffs(284, 84).addBox(-3.0F, 8.0F, -3.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(-2.0F, 22.0F, 0.0F));
        PartDefinition leftArm = body.addOrReplaceChild("leftArm", CubeListBuilder.create()
                .texOffs(348, 0).addBox(-5.0F, -6.0F, -8.0F, 14.0F, 12.0F, 16.0F)
                .texOffs(428, 58).addBox(-4.0F, 5.0F, -9.0F, 14.0F, 4.0F, 18.0F)
                .texOffs(484, 84).addBox(3.0F, -12.0F, -2.0F, 4.0F, 6.0F, 4.0F)
                .texOffs(76, 58).addBox(-2.0F, -2.0F, -5.0F, 8.0F, 16.0F, 10.0F)
                .texOffs(236, 58).addBox(-3.0F, 14.0F, -6.0F, 10.0F, 12.0F, 12.0F),
                PartPose.offset(15.0F, -20.0F, 0.0F));
        PartDefinition faulds = root.addOrReplaceChild("faulds", CubeListBuilder.create()
                .texOffs(408, 0).addBox(-13.0F, -2.0F, -8.0F, 26.0F, 12.0F, 16.0F)
                .texOffs(208, 84).addBox(-12.0F, 0.0F, -9.0F, 10.0F, 12.0F, 1.0F)
                .texOffs(230, 84).addBox(2.0F, 0.0F, -9.0F, 10.0F, 12.0F, 1.0F),
                PartPose.offset(0.0F, -8.0F, 0.0F));
        PartDefinition rightLeg = root.addOrReplaceChild("rightLeg", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-5.0F, 0.0F, -5.0F, 10.0F, 32.0F, 10.0F)
                .texOffs(332, 84).addBox(-5.0F, 16.0F, -6.0F, 10.0F, 10.0F, 1.0F)
                .texOffs(0, 103).addBox(-6.0F, 10.0F, -7.0F, 12.0F, 6.0F, 3.0F)
                .texOffs(0, 84).addBox(-6.0F, 26.0F, -8.0F, 12.0F, 6.0F, 13.0F),
                PartPose.offset(-6.0F, -8.0F, 0.0F));
        PartDefinition leftLeg = root.addOrReplaceChild("leftLeg", CubeListBuilder.create()
                .texOffs(104, 0).addBox(-5.0F, 0.0F, -5.0F, 10.0F, 32.0F, 10.0F)
                .texOffs(354, 84).addBox(-5.0F, 16.0F, -6.0F, 10.0F, 10.0F, 1.0F)
                .texOffs(30, 103).addBox(-6.0F, 10.0F, -7.0F, 12.0F, 6.0F, 3.0F)
                .texOffs(50, 84).addBox(-6.0F, 26.0F, -8.0F, 12.0F, 6.0F, 13.0F),
                PartPose.offset(6.0F, -8.0F, 0.0F));
        return LayerDefinition.create(mesh, 512, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(UnknownEmissary entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);

        float armX = ARM_X, swordX = SWORD_X, bodyX = Mth.sin(ageInTicks * 0.05F) * 0.02F, bodyY = 0F;
        float headX = headPitch * Mth.DEG_TO_RAD * 0.5F;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.6F;

        float walk = Mth.cos(limbSwing * 0.35F) * 0.7F * limbSwingAmount;
        rightLeg.xRot = walk;
        leftLeg.xRot = -walk;
        body.zRot = Mth.sin(limbSwing * 0.35F) * 0.04F * limbSwingAmount;
        cape.xRot = 0.08F + limbSwingAmount * 0.5F + Mth.sin(ageInTicks * 0.07F) * 0.04F;

        float t = ageInTicks - entity.clientAttackStart;
        switch (entity.getAttackId()) {
            case UnknownEmissary.CLEAVE -> {
                armX = KeyFrames.key(t, 0, ARM_X, 18, -3.1F, 22, -0.35F, 32, -0.35F, 40, ARM_X);
                bodyX = KeyFrames.key(t, 0, 0, 18, -0.2F, 22, 0.45F, 32, 0.45F, 40, 0);
                cape.xRot += KeyFrames.key(t, 0, 0, 18, 0.1F, 22, 0.6F, 40, 0);
            }
            case UnknownEmissary.SWEEP -> {
                bodyY = KeyFrames.key(t, 0, 0, 14, 0.9F, 16, 0.9F, 24, 0.9F - 2 * PI, 36, -2 * PI);
                armX = KeyFrames.key(t, 0, ARM_X, 14, -1.5F, 26, -1.5F, 36, ARM_X);
                swordX = KeyFrames.key(t, 0, SWORD_X, 14, 3.0F, 26, 3.0F, 36, SWORD_X);
                cape.xRot += KeyFrames.key(t, 0, 0, 16, 0.2F, 24, 0.9F, 36, 0);
            }
            case UnknownEmissary.LEAP -> {
                bodyX = KeyFrames.key(t, 0, 0, 8, 0.35F, 12, -0.1F);
                armX = KeyFrames.key(t, 0, ARM_X, 10, -2.9F);
                rightLeg.xRot = KeyFrames.key(t, 0, 0, 8, -0.7F, 12, 0.3F);
                leftLeg.xRot = KeyFrames.key(t, 0, 0, 8, 0.4F, 12, -0.3F);
                cape.xRot += KeyFrames.key(t, 0, 0, 12, 1.0F);
            }
            case UnknownEmissary.SLAM -> {
                armX = KeyFrames.key(t, 0, -2.9F, 3, -0.3F, 18, -0.3F, 24, ARM_X);
                swordX = KeyFrames.key(t, 0, SWORD_X, 3, 1.9F, 18, 1.9F, 24, SWORD_X);
                bodyX = KeyFrames.key(t, 0, 0.2F, 3, 0.6F, 18, 0.6F, 24, 0);
                rightLeg.xRot = KeyFrames.key(t, 0, -0.5F, 3, -0.5F, 24, 0);
            }
            case UnknownEmissary.SOUL_RIFT -> {
                armX = KeyFrames.key(t, 0, ARM_X, 16, -2.7F, 20, -0.45F, 44, -0.45F, 50, ARM_X);
                swordX = KeyFrames.key(t, 0, SWORD_X, 16, PI + 2.7F, 20, PI + 0.45F, 44, PI + 0.45F, 50, SWORD_X);
                bodyX = KeyFrames.key(t, 0, 0, 16, -0.15F, 20, 0.5F, 44, 0.5F, 50, 0);
            }
            case UnknownEmissary.SUMMON -> {
                armX = KeyFrames.key(t, 0, ARM_X, 14, -3.0F, 50, -3.0F, 60, ARM_X);
                swordX = KeyFrames.key(t, 0, SWORD_X, 14, 3.14F, 50, 3.14F, 60, SWORD_X);
                headX = KeyFrames.key(t, 0, headX, 14, -0.6F, 50, -0.6F, 60, headX);
                bodyX = KeyFrames.key(t, 0, 0, 14, -0.15F, 50, -0.15F, 60, 0);
            }
            case UnknownEmissary.COLLAPSE -> {
                // alza el espadón con la punta hacia abajo, lo clava, resiste temblando y lo arranca al estallar
                armX = KeyFrames.key(t, 0, ARM_X, 16, -2.8F, 20, -0.4F, 70, -0.4F, 74, -2.6F, 84, -2.6F, 96, ARM_X);
                swordX = KeyFrames.key(t, 0, SWORD_X, 16, PI + 2.8F, 20, PI + 0.4F, 70, PI + 0.4F, 74, 3.0F, 84, 3.0F, 96, SWORD_X);
                bodyX = KeyFrames.key(t, 0, 0, 16, -0.2F, 20, 0.55F, 70, 0.55F, 74, -0.25F, 84, -0.25F, 96, 0);
                headX = KeyFrames.key(t, 0, headX, 20, 0.3F, 70, 0.3F, 74, -0.7F, 84, -0.7F, 96, headX);
                if (t > 22 && t < 70) bodyX += Mth.sin(t * 2.3F) * 0.03F;
                cape.xRot += KeyFrames.key(t, 0, 0, 20, 0.2F, 70, 0.4F, 74, 1.1F, 96, 0);
            }
            default -> {}
        }

        body.xRot = bodyX;
        body.yRot = bodyY;
        head.xRot = headX;
        rightArm.xRot = armX;
        rightArm.yRot = -0.1F;
        leftArm.xRot = armX - 0.05F;
        leftArm.yRot = 0.55F;
        sword.xRot = swordX;
    }
}
