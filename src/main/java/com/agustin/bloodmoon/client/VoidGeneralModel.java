package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.VoidGeneral;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * General del Vacío: yelmo astado con visera encendida, peto con el ojo del Dominio, hombreras con púas, capa raída,
 * faldones, grebas con rodilleras de cuerno y una guja de hoja curva. Detrás de la cabeza gira una aureola de runas.
 * Geometría y textura generadas juntas (el mismo spec arma el atlas y este código). El renderer lo escala ×1,8.
 */
public class VoidGeneralModel extends HierarchicalModel<VoidGeneral> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "void_general"), "main");

    private final ModelPart root, body, head, cape, halo, rightArm, leftArm, glaive, rightLeg, leftLeg;

    public VoidGeneralModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.cape = body.getChild("cape");
        this.halo = head.getChild("halo");
        this.rightArm = body.getChild("rightArm");
        this.leftArm = body.getChild("leftArm");
        this.glaive = rightArm.getChild("glaive");
        this.rightLeg = root.getChild("rightLeg");
        this.leftLeg = root.getChild("leftLeg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(74, 0).addBox(-5F, 0F, -3F, 10F, 12F, 6F)
                .texOffs(0, 64).addBox(-5.5F, 0.5F, -3.5F, 11F, 7F, 1F)
                .texOffs(78, 72).addBox(-1.5F, 2F, -4F, 3F, 3F, 1F)
                .texOffs(78, 43).addBox(-4F, -1F, -3.5F, 8F, 2F, 7F)
                .texOffs(0, 55).addBox(-5.5F, 10F, -3.5F, 11F, 2F, 7F)
                .texOffs(88, 64).addBox(-4.5F, 12F, -3.6F, 9F, 6F, 1F)
                .texOffs(108, 64).addBox(-4.5F, 12F, 2.6F, 9F, 6F, 1F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition cape = body.addOrReplaceChild("cape", CubeListBuilder.create()
                .texOffs(50, 0).addBox(-5.5F, 0F, 0F, 11F, 22F, 1F), PartPose.offsetAndRotation(0F, 0F, 3.2F, 0.08F, 0F, 0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 25).addBox(-4.5F, -9F, -4.5F, 9F, 9F, 9F)
                .texOffs(28, 79).addBox(-3.5F, -6F, -5F, 7F, 1F, 1F)
                .texOffs(44, 79).addBox(-4.5F, -7F, -5F, 9F, 1F, 1F)
                .texOffs(60, 72).addBox(-4.5F, -5F, -5F, 2F, 4F, 1F)
                .texOffs(66, 72).addBox(2.5F, -5F, -5F, 2F, 4F, 1F)
                .texOffs(56, 43).addBox(-1F, -11F, -4F, 2F, 2F, 9F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition hornR = head.addOrReplaceChild("hornR", CubeListBuilder.create()
                .texOffs(24, 64).addBox(-1F, -6F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(-4.5F, -7F, 0F, 0F, 0F, -0.65F));
        PartDefinition hornRTip = hornR.addOrReplaceChild("hornRTip", CubeListBuilder.create()
                .texOffs(16, 72).addBox(-0.5F, -5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(0F, -6F, 0F, 0F, 0F, -0.55F));
        PartDefinition hornL = head.addOrReplaceChild("hornL", CubeListBuilder.create()
                .texOffs(32, 64).addBox(-1F, -6F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(4.5F, -7F, 0F, 0F, 0F, 0.65F));
        PartDefinition hornLTip = hornL.addOrReplaceChild("hornLTip", CubeListBuilder.create()
                .texOffs(20, 72).addBox(-0.5F, -5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(0F, -6F, 0F, 0F, 0F, 0.55F));
        PartDefinition halo = head.addOrReplaceChild("halo", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -5F, 6F, 0F, 0F, 0F));
        PartDefinition rune0 = halo.addOrReplaceChild("rune0", CubeListBuilder.create()
                .texOffs(86, 72).addBox(-0.5F, -1.5F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(7.5F, 0F, 0F, 0F, 0F, 1.5708F));
        PartDefinition rune1 = halo.addOrReplaceChild("rune1", CubeListBuilder.create()
                .texOffs(90, 72).addBox(-0.5F, -1.5F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(6.068F, 4.408F, 0F, 0F, 0F, 2.1991F));
        PartDefinition rune2 = halo.addOrReplaceChild("rune2", CubeListBuilder.create()
                .texOffs(94, 72).addBox(-0.5F, -1.5F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(2.318F, 7.133F, 0F, 0F, 0F, 2.8274F));
        PartDefinition rune3 = halo.addOrReplaceChild("rune3", CubeListBuilder.create()
                .texOffs(98, 72).addBox(-0.5F, -1.5F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(-2.318F, 7.133F, 0F, 0F, 0F, 3.4558F));
        PartDefinition rune4 = halo.addOrReplaceChild("rune4", CubeListBuilder.create()
                .texOffs(102, 72).addBox(-0.5F, -1.5F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(-6.068F, 4.408F, 0F, 0F, 0F, 4.0841F));
        PartDefinition rune5 = halo.addOrReplaceChild("rune5", CubeListBuilder.create()
                .texOffs(106, 72).addBox(-0.5F, -1.5F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(-7.5F, 0F, 0F, 0F, 0F, 4.7124F));
        PartDefinition rune6 = halo.addOrReplaceChild("rune6", CubeListBuilder.create()
                .texOffs(110, 72).addBox(-0.5F, -1.5F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(-6.068F, -4.408F, 0F, 0F, 0F, 5.3407F));
        PartDefinition rune7 = halo.addOrReplaceChild("rune7", CubeListBuilder.create()
                .texOffs(114, 72).addBox(-0.5F, -1.5F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(-2.318F, -7.133F, 0F, 0F, 0F, 5.969F));
        PartDefinition rune8 = halo.addOrReplaceChild("rune8", CubeListBuilder.create()
                .texOffs(118, 72).addBox(-0.5F, -1.5F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(2.318F, -7.133F, 0F, 0F, 0F, 6.5973F));
        PartDefinition rune9 = halo.addOrReplaceChild("rune9", CubeListBuilder.create()
                .texOffs(122, 72).addBox(-0.5F, -1.5F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(6.068F, -4.408F, 0F, 0F, 0F, 7.2257F));
        PartDefinition rightArm = body.addOrReplaceChild("rightArm", CubeListBuilder.create()
                .texOffs(76, 25).addBox(-3F, -2F, -2F, 4F, 12F, 4F)
                .texOffs(0, 43).addBox(-5F, -4F, -3.5F, 7F, 5F, 7F)
                .texOffs(36, 55).addBox(-5.5F, -1F, -3F, 7F, 3F, 6F)
                .texOffs(0, 72).addBox(-3.5F, -9F, -1F, 2F, 5F, 2F)
                .texOffs(62, 55).addBox(-3.5F, 7F, -2.5F, 5F, 4F, 5F), PartPose.offsetAndRotation(-5.5F, 2F, 0F, 0F, 0F, 0F));
        PartDefinition glaive = rightArm.addOrReplaceChild("glaive", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5F, -0.5F, -18F, 1F, 1F, 24F)
                .texOffs(108, 25).addBox(-0.5F, -8F, -23F, 1F, 8F, 6F)
                .texOffs(24, 72).addBox(-0.5F, -11F, -21F, 1F, 3F, 3F)
                .texOffs(72, 72).addBox(-0.5F, 0F, -20F, 1F, 3F, 2F)
                .texOffs(0, 79).addBox(-1F, -1F, 6F, 2F, 2F, 2F)
                .texOffs(8, 79).addBox(-1F, -1F, -14F, 2F, 2F, 2F), PartPose.offsetAndRotation(-1F, 10F, -1F, 0F, 0F, 0F));
        PartDefinition leftArm = body.addOrReplaceChild("leftArm", CubeListBuilder.create()
                .texOffs(92, 25).addBox(-1F, -2F, -2F, 4F, 12F, 4F)
                .texOffs(28, 43).addBox(-2F, -4F, -3.5F, 7F, 5F, 7F)
                .texOffs(82, 55).addBox(-1.5F, -1F, -3F, 7F, 3F, 6F)
                .texOffs(8, 72).addBox(1.5F, -9F, -1F, 2F, 5F, 2F)
                .texOffs(108, 55).addBox(-1.5F, 7F, -2.5F, 5F, 4F, 5F), PartPose.offsetAndRotation(5.5F, 2F, 0F, 0F, 0F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("rightLeg", CubeListBuilder.create()
                .texOffs(36, 25).addBox(-2.5F, 0F, -2.5F, 5F, 12F, 5F)
                .texOffs(32, 72).addBox(-3F, 5F, -3F, 6F, 5F, 1F)
                .texOffs(16, 79).addBox(-1F, 4F, -4F, 2F, 2F, 1F)
                .texOffs(40, 64).addBox(-3F, 10F, -3.5F, 6F, 2F, 6F), PartPose.offsetAndRotation(-2.2F, 12F, 0F, 0F, 0F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("leftLeg", CubeListBuilder.create()
                .texOffs(56, 25).addBox(-2.5F, 0F, -2.5F, 5F, 12F, 5F)
                .texOffs(46, 72).addBox(-3F, 5F, -3F, 6F, 5F, 1F)
                .texOffs(22, 79).addBox(-1F, 4F, -4F, 2F, 2F, 1F)
                .texOffs(64, 64).addBox(-3F, 10F, -3.5F, 6F, 2F, 6F), PartPose.offsetAndRotation(2.2F, 12F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(VoidGeneral e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        float walk = limbSwing * 0.6662F;
        float amt = Math.min(1F, limbSwingAmount);
        rightLeg.xRot = Mth.cos(walk) * 1.2F * amt;
        leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.2F * amt;
        leftArm.xRot = Mth.cos(walk) * 0.8F * amt;
        leftArm.zRot = -0.08F;
        rightArm.xRot = -0.35F + Mth.cos(walk + Mth.PI) * 0.3F * amt;   // la guja al frente
        rightArm.zRot = 0.08F;
        body.yRot = Mth.sin(walk) * 0.05F * amt;
        cape.xRot = 0.08F + amt * 0.55F + Mth.sin(ageInTicks * 0.09F) * 0.05F;
        halo.zRot = ageInTicks * 0.035F;
        halo.y += Mth.sin(ageInTicks * 0.07F) * 0.4F;

        // golpe: barrido amplio de la guja
        if (attackTime > 0F) {
            float s = Mth.sin(Mth.sqrt(attackTime) * Mth.PI);
            rightArm.xRot = -0.35F - s * 1.9F;
            rightArm.yRot = s * 0.9F;
            body.yRot += s * 0.45F;
            glaive.xRot = s * 0.4F;
        }
        float t = ageInTicks - e.actionStart;
        switch (e.getAction()) {
            case VoidGeneral.WAR_CRY -> {   // brazos abiertos, guja al cielo, cabeza atrás
                float k = Math.min(1F, t / 8F);
                rightArm.xRot = Mth.lerp(k, rightArm.xRot, -2.9F);
                leftArm.xRot = Mth.lerp(k, leftArm.xRot, -2.4F);
                leftArm.zRot = Mth.lerp(k, -0.08F, -0.5F);
                head.xRot = Mth.lerp(k, head.xRot, -0.5F);
                cape.xRot = 0.6F + Mth.sin(ageInTicks * 0.8F) * 0.1F;
            }
            case VoidGeneral.LEAP -> {      // en el aire, guja en alto para clavarla al caer
                float k = Math.min(1F, t / 6F);
                rightArm.xRot = Mth.lerp(k, rightArm.xRot, -3.0F);
                leftArm.xRot = Mth.lerp(k, leftArm.xRot, -1.2F);
                rightLeg.xRot = -0.6F * k;
                leftLeg.xRot = 0.3F * k;
                cape.xRot = 1.1F;
            }
            default -> { }
        }
    }
}
