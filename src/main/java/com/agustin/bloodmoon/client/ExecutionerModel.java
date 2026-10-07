package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.entity.Executioner;
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
 * El Ejecutor: armadura más pesada, yelmo de cubo con visor en cruz y corona de púas, malla, tabardo
 * y un mazo colosal con cabeza rúnica (brilla durante el Juicio Final). Doble resolución, escala x2,4.
 */
public class ExecutionerModel extends HierarchicalModel<Executioner> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "executioner"), "main");

    private static final float ARM_X = -0.85F;
    private static final float MAUL_X = 1.54F;

    private final ModelPart root, body, head, cape, rightArm, leftArm, maul, rightLeg, leftLeg;

    public ExecutionerModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.cape = body.getChild("cape");
        this.rightArm = body.getChild("rightArm");
        this.leftArm = body.getChild("leftArm");
        this.maul = rightArm.getChild("maul");
        this.rightLeg = root.getChild("rightLeg");
        this.leftLeg = root.getChild("leftLeg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(164, 0).addBox(-14.0F, -24.0F, -8.0F, 28.0F, 24.0F, 16.0F)
                .texOffs(266, 112).addBox(-15.0F, -23.0F, -10.0F, 30.0F, 16.0F, 2.0F)
                .texOffs(330, 112).addBox(-1.0F, -23.0F, -11.0F, 2.0F, 16.0F, 1.0F)
                .texOffs(0, 112).addBox(-15.0F, -6.0F, -9.0F, 30.0F, 6.0F, 18.0F)
                .texOffs(68, 136).addBox(-3.0F, -7.0F, -10.0F, 6.0F, 7.0F, 1.0F)
                .texOffs(212, 112).addBox(-13.0F, -23.0F, 8.0F, 26.0F, 18.0F, 1.0F),
                PartPose.offset(0.0F, -8.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(332, 0).addBox(-9.0F, -20.0F, -9.0F, 18.0F, 20.0F, 18.0F)
                .texOffs(162, 136).addBox(-10.0F, -15.0F, -10.0F, 20.0F, 2.0F, 2.0F)
                .texOffs(82, 136).addBox(-8.0F, -25.0F, -8.0F, 3.0F, 5.0F, 3.0F)
                .texOffs(94, 136).addBox(5.0F, -25.0F, -8.0F, 3.0F, 5.0F, 3.0F)
                .texOffs(392, 112).addBox(-1.5F, -27.0F, -8.0F, 3.0F, 7.0F, 3.0F)
                .texOffs(106, 136).addBox(-8.0F, -25.0F, 5.0F, 3.0F, 5.0F, 3.0F)
                .texOffs(118, 136).addBox(5.0F, -25.0F, 5.0F, 3.0F, 5.0F, 3.0F)
                .texOffs(164, 80).addBox(-11.0F, -4.0F, -11.0F, 22.0F, 6.0F, 22.0F),
                PartPose.offset(0.0F, -24.0F, 0.0F));
        PartDefinition cape = body.addOrReplaceChild("cape", CubeListBuilder.create()
                .texOffs(112, 0).addBox(-12.0F, 0.0F, 0.0F, 24.0F, 40.0F, 2.0F),
                PartPose.offset(0.0F, -23.0F, 9.0F));
        PartDefinition rightArm = body.addOrReplaceChild("rightArm", CubeListBuilder.create()
                .texOffs(404, 0).addBox(-10.0F, -7.0F, -9.0F, 16.0F, 14.0F, 18.0F)
                .texOffs(360, 112).addBox(-8.0F, -14.0F, -5.0F, 4.0F, 7.0F, 4.0F)
                .texOffs(444, 112).addBox(-8.0F, -12.0F, 2.0F, 4.0F, 5.0F, 4.0F)
                .texOffs(252, 80).addBox(-7.0F, -2.0F, -6.0F, 10.0F, 16.0F, 12.0F)
                .texOffs(366, 80).addBox(-8.0F, 14.0F, -7.0F, 12.0F, 12.0F, 14.0F),
                PartPose.offset(-17.0F, -20.0F, 0.0F));
        PartDefinition maul = rightArm.addOrReplaceChild("maul", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.0F, -60.0F, -2.0F, 4.0F, 76.0F, 4.0F)
                .texOffs(404, 112).addBox(-3.0F, -60.0F, -3.0F, 6.0F, 4.0F, 6.0F)
                .texOffs(460, 112).addBox(-3.0F, -28.0F, -3.0F, 6.0F, 3.0F, 6.0F)
                .texOffs(252, 0).addBox(-8.0F, -76.0F, -12.0F, 16.0F, 16.0F, 24.0F)
                .texOffs(428, 112).addBox(-2.0F, -82.0F, -2.0F, 4.0F, 6.0F, 4.0F)
                .texOffs(130, 136).addBox(-12.0F, -70.0F, -2.0F, 4.0F, 4.0F, 4.0F)
                .texOffs(146, 136).addBox(8.0F, -70.0F, -2.0F, 4.0F, 4.0F, 4.0F)
                .texOffs(336, 112).addBox(-3.0F, 16.0F, -3.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(-2.0F, 22.0F, 0.0F));
        PartDefinition leftArm = body.addOrReplaceChild("leftArm", CubeListBuilder.create()
                .texOffs(0, 80).addBox(-6.0F, -7.0F, -9.0F, 16.0F, 14.0F, 18.0F)
                .texOffs(376, 112).addBox(4.0F, -14.0F, -5.0F, 4.0F, 7.0F, 4.0F)
                .texOffs(484, 112).addBox(4.0F, -12.0F, 2.0F, 4.0F, 5.0F, 4.0F)
                .texOffs(296, 80).addBox(-3.0F, -2.0F, -6.0F, 10.0F, 16.0F, 12.0F)
                .texOffs(418, 80).addBox(-4.0F, 14.0F, -7.0F, 12.0F, 12.0F, 14.0F),
                PartPose.offset(17.0F, -20.0F, 0.0F));
        PartDefinition faulds = root.addOrReplaceChild("faulds", CubeListBuilder.create()
                .texOffs(68, 80).addBox(-15.0F, -2.0F, -9.0F, 30.0F, 12.0F, 18.0F)
                .texOffs(340, 80).addBox(-6.0F, -2.0F, -10.0F, 12.0F, 26.0F, 1.0F),
                PartPose.offset(0.0F, -8.0F, 0.0F));
        PartDefinition rightLeg = root.addOrReplaceChild("rightLeg", CubeListBuilder.create()
                .texOffs(16, 0).addBox(-6.0F, 0.0F, -6.0F, 12.0F, 32.0F, 12.0F)
                .texOffs(0, 136).addBox(-7.0F, 10.0F, -8.0F, 14.0F, 6.0F, 3.0F)
                .texOffs(96, 112).addBox(-7.0F, 26.0F, -9.0F, 14.0F, 6.0F, 15.0F),
                PartPose.offset(-7.0F, -8.0F, 0.0F));
        PartDefinition leftLeg = root.addOrReplaceChild("leftLeg", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-6.0F, 0.0F, -6.0F, 12.0F, 32.0F, 12.0F)
                .texOffs(34, 136).addBox(-7.0F, 10.0F, -8.0F, 14.0F, 6.0F, 3.0F)
                .texOffs(154, 112).addBox(-7.0F, 26.0F, -9.0F, 14.0F, 6.0F, 15.0F),
                PartPose.offset(7.0F, -8.0F, 0.0F));
        return LayerDefinition.create(mesh, 512, 256);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Executioner entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);

        float armX = ARM_X, maulX = MAUL_X, bodyX = Mth.sin(ageInTicks * 0.045F) * 0.025F, bodyY = 0F;
        float headX = headPitch * Mth.DEG_TO_RAD * 0.5F;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.6F;

        float walk = Mth.cos(limbSwing * 0.3F) * 0.6F * limbSwingAmount;
        rightLeg.xRot = walk;
        leftLeg.xRot = -walk;
        body.zRot = Mth.sin(limbSwing * 0.3F) * 0.05F * limbSwingAmount;
        cape.xRot = 0.06F + limbSwingAmount * 0.4F + Mth.sin(ageInTicks * 0.06F) * 0.04F;

        float t = ageInTicks - entity.clientAttackStart;
        switch (entity.getAttackId()) {
            case Executioner.SMASH -> {
                armX = KeyFrames.key(t, 0, ARM_X, 22, -3.2F, 26, -0.25F, 38, -0.25F, 46, ARM_X);
                bodyX = KeyFrames.key(t, 0, 0, 22, -0.25F, 26, 0.55F, 38, 0.55F, 46, 0);
                cape.xRot += KeyFrames.key(t, 0, 0, 22, 0.1F, 26, 0.7F, 46, 0);
            }
            case Executioner.SWING -> {
                bodyY = KeyFrames.key(t, 0, 0, 14, 1.2F, 20, -1.0F, 26, -1.0F, 36, 0);
                armX = KeyFrames.key(t, 0, ARM_X, 12, -1.5F, 28, -1.5F, 36, ARM_X);
                maulX = KeyFrames.key(t, 0, MAUL_X, 12, 3.0F, 28, 3.0F, 36, MAUL_X);
                cape.xRot += KeyFrames.key(t, 0, 0, 20, 0.8F, 36, 0);
            }
            case Executioner.LEAP -> {
                bodyX = KeyFrames.key(t, 0, 0, 8, 0.35F, 12, -0.15F);
                armX = KeyFrames.key(t, 0, ARM_X, 10, -3.1F);
                rightLeg.xRot = KeyFrames.key(t, 0, 0, 8, -0.7F, 12, 0.3F);
                leftLeg.xRot = KeyFrames.key(t, 0, 0, 8, 0.4F, 12, -0.3F);
                cape.xRot += KeyFrames.key(t, 0, 0, 12, 1.0F);
            }
            case Executioner.SLAM -> {
                armX = KeyFrames.key(t, 0, -3.1F, 3, -0.25F, 20, -0.25F, 26, ARM_X);
                bodyX = KeyFrames.key(t, 0, 0.2F, 3, 0.6F, 20, 0.6F, 26, 0);
            }
            case Executioner.JUDGMENT -> {
                // alza el mazo al cielo (cabeza arriba), tiembla cargando y lo descarga
                armX = KeyFrames.key(t, 0, ARM_X, 30, -3.05F, 66, -3.05F, 70, -0.2F, 88, -0.2F, 100, ARM_X);
                maulX = KeyFrames.key(t, 0, MAUL_X, 30, 3.1F, 66, 3.1F, 70, MAUL_X, 100, MAUL_X);
                bodyX = KeyFrames.key(t, 0, 0, 30, -0.2F, 66, -0.25F, 70, 0.6F, 88, 0.6F, 100, 0);
                headX = KeyFrames.key(t, 0, headX, 30, -0.7F, 66, -0.7F, 70, 0.3F, 100, headX);
                if (t > 30 && t < 66) armX += Mth.sin(t * 1.9F) * 0.025F;
                cape.xRot += KeyFrames.key(t, 0, 0, 30, 0.3F, 66, 0.5F, 70, 1.2F, 100, 0);
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
        maul.xRot = maulX;
    }
}
