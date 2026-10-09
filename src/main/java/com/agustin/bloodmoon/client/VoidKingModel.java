package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.VoidKing;
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
 * Rey del Vacío: flota sobre una túnica de tres faldones con tabardo de runas. Máscara de obsidiana con ojos y
 * rejilla encendidos, corona de oro con gemas que orbitan, hombreras reales con púas, capa en dos tramos, cetro con
 * jaula de oro y orbe, un orbe del Vacío con anillos en la otra mano y cuatro satélites de cristal girando alrededor.
 * Geometría y textura generadas juntas (mismo spec). Renderer ×1,35 (~6,5 bloques).
 */
public class VoidKingModel extends HierarchicalModel<VoidKing> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "void_king"), "main");

    private final ModelPart root, robe, body, head, gems, cape, cape2, rightArm, rightForearm, leftArm, leftForearm,
            rightPauldron, leftPauldron, scepter, orb, ring1, ring2, satellites;

    public VoidKingModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.robe = root.getChild("robe");
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.gems = head.getChild("gems");
        this.cape = body.getChild("cape");
        this.cape2 = cape.getChild("cape2");
        this.rightArm = body.getChild("rightArm");
        this.rightForearm = rightArm.getChild("rightForearm");
        this.leftArm = body.getChild("leftArm");
        this.leftForearm = leftArm.getChild("leftForearm");
        this.rightPauldron = body.getChild("rightPauldron");
        this.leftPauldron = body.getChild("leftPauldron");
        this.scepter = rightForearm.getChild("scepter");
        this.orb = leftForearm.getChild("orb");
        this.ring1 = orb.getChild("ring1");
        this.ring2 = orb.getChild("ring2");
        this.satellites = root.getChild("satellites");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition robe = root.addOrReplaceChild("robe", CubeListBuilder.create()
                .texOffs(90, 46).addBox(-8F, 0F, -6F, 16F, 8F, 12F)
                .texOffs(150, 0).addBox(-10F, 7F, -7.5F, 20F, 8F, 15F)
                .texOffs(8, 0).addBox(-12F, 14F, -9F, 24F, 10F, 18F)
                .texOffs(92, 128).addBox(-4F, 0F, -7F, 8F, 7F, 1F)
                .texOffs(110, 128).addBox(-4F, 7F, -8.5F, 8F, 7F, 1F)
                .texOffs(0, 118).addBox(-4.5F, 14F, -10F, 9F, 9F, 1F)
                .texOffs(128, 128).addBox(-3.5F, 0F, 6F, 7F, 7F, 1F)
                .texOffs(144, 128).addBox(-3.5F, 7F, 7.5F, 7F, 7F, 1F), PartPose.offsetAndRotation(0F, -4F, 0F, 0F, 0F, 0F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(126, 69).addBox(-6.5F, -8F, -4.5F, 13F, 8F, 9F)
                .texOffs(208, 137).addBox(-5F, -7F, -5.5F, 10F, 2F, 1F)
                .texOffs(230, 137).addBox(-5F, -4.5F, -5.5F, 10F, 2F, 1F)
                .texOffs(0, 88).addBox(-8.5F, -2F, -6.5F, 17F, 3F, 13F)
                .texOffs(16, 137).addBox(-2.5F, -3F, -7F, 5F, 5F, 1F)
                .texOffs(168, 137).addBox(-1.5F, -2F, -7.5F, 3F, 3F, 1F)
                .texOffs(92, 0).addBox(-9F, -22F, -5.5F, 18F, 14F, 11F)
                .texOffs(36, 104).addBox(-9.5F, -22.5F, -6.5F, 19F, 11F, 2F)
                .texOffs(112, 137).addBox(-7.5F, -11.5F, -6.2F, 15F, 3F, 1F)
                .texOffs(28, 137).addBox(-2.5F, -20F, -7.5F, 5F, 5F, 1F)
                .texOffs(176, 137).addBox(-1.5F, -19F, -8F, 3F, 3F, 1F)
                .texOffs(56, 69).addBox(-10.5F, -25F, -7F, 21F, 4F, 14F)
                .texOffs(0, 104).addBox(-8F, -21F, 5F, 16F, 12F, 2F), PartPose.offsetAndRotation(0F, -4F, 0F, 0F, 0F, 0F));
        PartDefinition cape = body.addOrReplaceChild("cape", CubeListBuilder.create()
                .texOffs(170, 69).addBox(-10F, 0F, 0F, 20F, 16F, 1F), PartPose.offsetAndRotation(0F, -22F, 6.5F, 0.06F, 0F, 0F));
        PartDefinition cape2 = cape.addOrReplaceChild("cape2", CubeListBuilder.create()
                .texOffs(44, 46).addBox(-11F, 0F, 0F, 22F, 20F, 1F), PartPose.offsetAndRotation(0F, 16F, 0F, 0.04F, 0F, 0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 46).addBox(-5.5F, -12F, -5.5F, 11F, 12F, 11F)
                .texOffs(20, 118).addBox(-4.5F, -10F, -6.5F, 9F, 9F, 1F)
                .texOffs(12, 143).addBox(-3.5F, -7.5F, -7F, 2F, 2F, 1F)
                .texOffs(18, 143).addBox(1.5F, -7.5F, -7F, 2F, 2F, 1F)
                .texOffs(0, 143).addBox(-2.5F, -4F, -7F, 5F, 2F, 1F)
                .texOffs(48, 143).addBox(-5F, -9.5F, -7F, 10F, 1F, 1F)
                .texOffs(108, 137).addBox(-0.5F, -9F, -7.2F, 1F, 4F, 1F)
                .texOffs(60, 88).addBox(-6.5F, -14F, -6.5F, 13F, 3F, 13F)
                .texOffs(78, 104).addBox(-5.5F, -13F, -5.5F, 11F, 1F, 11F)
                .texOffs(0, 128).addBox(-1F, -21F, -7F, 2F, 7F, 2F)
                .texOffs(24, 143).addBox(-0.5F, -23F, -6.5F, 1F, 2F, 1F)
                .texOffs(220, 128).addBox(-4.5F, -19F, -7F, 2F, 5F, 2F)
                .texOffs(228, 128).addBox(2.5F, -19F, -7F, 2F, 5F, 2F)
                .texOffs(150, 143).addBox(-4F, -20F, -6.5F, 1F, 1F, 1F)
                .texOffs(154, 143).addBox(3F, -20F, -6.5F, 1F, 1F, 1F)
                .texOffs(52, 137).addBox(-6.5F, -18F, -6.5F, 2F, 4F, 2F)
                .texOffs(60, 137).addBox(4.5F, -18F, -6.5F, 2F, 4F, 2F)
                .texOffs(68, 137).addBox(-6.5F, -18F, 4.5F, 2F, 4F, 2F)
                .texOffs(76, 137).addBox(4.5F, -18F, 4.5F, 2F, 4F, 2F)
                .texOffs(236, 128).addBox(-1F, -19F, 4.5F, 2F, 5F, 2F)
                .texOffs(92, 137).addBox(-6.5F, -17F, -1F, 2F, 3F, 2F)
                .texOffs(100, 137).addBox(4.5F, -17F, -1F, 2F, 3F, 2F), PartPose.offsetAndRotation(0F, -25F, 0F, 0F, 0F, 0F));
        PartDefinition gems = head.addOrReplaceChild("gems", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -27F, 0F, 0F, 0F, 0F));
        PartDefinition gem0 = gems.addOrReplaceChild("gem0", CubeListBuilder.create()
                .texOffs(184, 137).addBox(-1F, -1F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 0F, -6F, 0.6155F, 0.7854F, 0F));
        PartDefinition gem1 = gems.addOrReplaceChild("gem1", CubeListBuilder.create()
                .texOffs(192, 137).addBox(-1F, -1F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(5.1962F, 0F, 3F, 0.6155F, 0.7854F, 0F));
        PartDefinition gem2 = gems.addOrReplaceChild("gem2", CubeListBuilder.create()
                .texOffs(200, 137).addBox(-1F, -1F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(-5.1962F, 0F, 3F, 0.6155F, 0.7854F, 0F));
        PartDefinition rightPauldron = body.addOrReplaceChild("rightPauldron", CubeListBuilder.create()
                .texOffs(146, 46).addBox(-6F, -6F, -6F, 12F, 8F, 12F)
                .texOffs(152, 88).addBox(-6.5F, 1F, -5.5F, 13F, 3F, 11F)
                .texOffs(92, 118).addBox(-6.5F, -7F, -4F, 13F, 1F, 8F), PartPose.offsetAndRotation(-11F, -21F, 0F, 0F, 0F, 0F));
        PartDefinition rightSpike0 = rightPauldron.addOrReplaceChild("rightSpike0", CubeListBuilder.create()
                .texOffs(8, 128).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(-2F, -7F, -3F, 0.3F, 0F, -0.3F));
        PartDefinition rightSpike1 = rightPauldron.addOrReplaceChild("rightSpike1", CubeListBuilder.create()
                .texOffs(170, 104).addBox(-1F, -9F, -1F, 2F, 9F, 2F), PartPose.offsetAndRotation(-2F, -7F, 0F, 0F, 0F, -0.42F));
        PartDefinition rightSpike2 = rightPauldron.addOrReplaceChild("rightSpike2", CubeListBuilder.create()
                .texOffs(16, 128).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(-2F, -7F, 3F, -0.3F, 0F, -0.3F));
        PartDefinition rightArm = body.addOrReplaceChild("rightArm", CubeListBuilder.create()
                .texOffs(0, 69).addBox(-3.5F, -2F, -3.5F, 7F, 12F, 7F), PartPose.offsetAndRotation(-11F, -21F, 0F, 0F, 0F, 0F));
        PartDefinition rightForearm = rightArm.addOrReplaceChild("rightForearm", CubeListBuilder.create()
                .texOffs(186, 104).addBox(-4F, 0F, -4F, 8F, 2F, 8F)
                .texOffs(112, 88).addBox(-2.5F, 0F, -2.5F, 5F, 10F, 5F)
                .texOffs(122, 104).addBox(-3F, 2F, -3F, 6F, 6F, 6F)
                .texOffs(176, 118).addBox(-2.5F, 10F, -2.5F, 5F, 4F, 5F), PartPose.offsetAndRotation(0F, 10F, 0F, -0.4F, 0F, 0F));
        PartDefinition leftPauldron = body.addOrReplaceChild("leftPauldron", CubeListBuilder.create()
                .texOffs(194, 46).addBox(-6F, -6F, -6F, 12F, 8F, 12F)
                .texOffs(200, 88).addBox(-6.5F, 1F, -5.5F, 13F, 3F, 11F)
                .texOffs(134, 118).addBox(-6.5F, -7F, -4F, 13F, 1F, 8F), PartPose.offsetAndRotation(11F, -21F, 0F, 0F, 0F, 0F));
        PartDefinition leftSpike0 = leftPauldron.addOrReplaceChild("leftSpike0", CubeListBuilder.create()
                .texOffs(24, 128).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(2F, -7F, -3F, 0.3F, 0F, 0.3F));
        PartDefinition leftSpike1 = leftPauldron.addOrReplaceChild("leftSpike1", CubeListBuilder.create()
                .texOffs(178, 104).addBox(-1F, -9F, -1F, 2F, 9F, 2F), PartPose.offsetAndRotation(2F, -7F, 0F, 0F, 0F, 0.42F));
        PartDefinition leftSpike2 = leftPauldron.addOrReplaceChild("leftSpike2", CubeListBuilder.create()
                .texOffs(32, 128).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(2F, -7F, 3F, -0.3F, 0F, 0.3F));
        PartDefinition leftArm = body.addOrReplaceChild("leftArm", CubeListBuilder.create()
                .texOffs(28, 69).addBox(-3.5F, -2F, -3.5F, 7F, 12F, 7F), PartPose.offsetAndRotation(11F, -21F, 0F, 0F, 0F, 0F));
        PartDefinition leftForearm = leftArm.addOrReplaceChild("leftForearm", CubeListBuilder.create()
                .texOffs(218, 104).addBox(-4F, 0F, -4F, 8F, 2F, 8F)
                .texOffs(132, 88).addBox(-2.5F, 0F, -2.5F, 5F, 10F, 5F)
                .texOffs(146, 104).addBox(-3F, 2F, -3F, 6F, 6F, 6F)
                .texOffs(196, 118).addBox(-2.5F, 10F, -2.5F, 5F, 4F, 5F), PartPose.offsetAndRotation(0F, 10F, 0F, -0.5F, 0F, 0F));
        PartDefinition scepter = rightForearm.addOrReplaceChild("scepter", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1F, -30F, -1F, 2F, 44F, 2F)
                .texOffs(144, 137).addBox(-1.5F, -12F, -1.5F, 3F, 1F, 3F)
                .texOffs(156, 137).addBox(-1.5F, 4F, -1.5F, 3F, 1F, 3F)
                .texOffs(40, 137).addBox(-1.5F, 14F, -1.5F, 3F, 3F, 3F)
                .texOffs(158, 143).addBox(-0.5F, 15F, -2F, 1F, 1F, 1F)
                .texOffs(0, 137).addBox(-2F, -31F, -2F, 4F, 2F, 4F)
                .texOffs(68, 128).addBox(-3F, -33F, -3F, 6F, 2F, 6F)
                .texOffs(40, 118).addBox(-2.5F, -38F, -2.5F, 5F, 5F, 5F)
                .texOffs(176, 128).addBox(-3.5F, -39F, -3.5F, 1F, 7F, 1F)
                .texOffs(180, 128).addBox(2.5F, -39F, -3.5F, 1F, 7F, 1F)
                .texOffs(184, 128).addBox(-3.5F, -39F, 2.5F, 1F, 7F, 1F)
                .texOffs(188, 128).addBox(2.5F, -39F, 2.5F, 1F, 7F, 1F)
                .texOffs(40, 128).addBox(-3.5F, -40F, -3.5F, 7F, 1F, 7F)
                .texOffs(84, 137).addBox(-1F, -44F, -1F, 2F, 4F, 2F)
                .texOffs(28, 143).addBox(-0.5F, -46F, -0.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(0F, 12F, 0F, 0.75F, 0F, 0F));
        PartDefinition orb = leftForearm.addOrReplaceChild("orb", CubeListBuilder.create()
                .texOffs(160, 128).addBox(-2F, -2F, -2F, 4F, 4F, 4F), PartPose.offsetAndRotation(0F, 19F, 0F, 0F, 0F, 0F));
        PartDefinition ring1 = orb.addOrReplaceChild("ring1", CubeListBuilder.create()
                .texOffs(114, 143).addBox(-4F, -0.5F, -4F, 8F, 1F, 1F)
                .texOffs(132, 143).addBox(-4F, -0.5F, 3F, 8F, 1F, 1F)
                .texOffs(192, 128).addBox(-4F, -0.5F, -3F, 1F, 1F, 6F)
                .texOffs(206, 128).addBox(3F, -0.5F, -3F, 1F, 1F, 6F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition ring2 = orb.addOrReplaceChild("ring2", CubeListBuilder.create()
                .texOffs(70, 143).addBox(-5F, -0.5F, -5F, 10F, 1F, 1F)
                .texOffs(92, 143).addBox(-5F, -0.5F, 4F, 10F, 1F, 1F)
                .texOffs(216, 118).addBox(-5F, -0.5F, -4F, 1F, 1F, 8F)
                .texOffs(234, 118).addBox(4F, -0.5F, -4F, 1F, 1F, 8F), PartPose.offsetAndRotation(0F, 0F, 0F, 1.5708F, 0.7854F, 0F));
        PartDefinition satellites = root.addOrReplaceChild("satellites", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -16F, 0F, 0F, 0F, 0F));
        PartDefinition sat0 = satellites.addOrReplaceChild("sat0", CubeListBuilder.create()
                .texOffs(60, 118).addBox(-1F, -4F, -1F, 2F, 8F, 2F)
                .texOffs(32, 143).addBox(-0.5F, -6F, -0.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(16.2635F, 0F, 16.2635F, 0F, -0.7854F, 0.35F));
        PartDefinition sat1 = satellites.addOrReplaceChild("sat1", CubeListBuilder.create()
                .texOffs(68, 118).addBox(-1F, -4F, -1F, 2F, 8F, 2F)
                .texOffs(36, 143).addBox(-0.5F, -6F, -0.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(-16.2635F, 0F, 16.2635F, 0F, -2.3562F, 0.35F));
        PartDefinition sat2 = satellites.addOrReplaceChild("sat2", CubeListBuilder.create()
                .texOffs(76, 118).addBox(-1F, -4F, -1F, 2F, 8F, 2F)
                .texOffs(40, 143).addBox(-0.5F, -6F, -0.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(-16.2635F, 0F, -16.2635F, 0F, -3.927F, 0.35F));
        PartDefinition sat3 = satellites.addOrReplaceChild("sat3", CubeListBuilder.create()
                .texOffs(84, 118).addBox(-1F, -4F, -1F, 2F, 8F, 2F)
                .texOffs(44, 143).addBox(-0.5F, -6F, -0.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(16.2635F, 0F, -16.2635F, 0F, -5.4978F, 0.35F));
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(VoidKing e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float amt = Math.min(1F, limbSwingAmount);
        float bob = Mth.sin(ageInTicks * 0.06F) * 1.2F;
        robe.y += bob;
        body.y += bob;
        satellites.y += bob * 1.5F;
        robe.xRot = amt * 0.12F + Mth.sin(ageInTicks * 0.05F) * 0.02F;
        body.xRot = amt * 0.08F;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        gems.yRot = ageInTicks * 0.05F;
        gems.y += Mth.sin(ageInTicks * 0.1F) * 0.6F;
        cape.xRot += amt * 0.35F + Mth.sin(ageInTicks * 0.06F) * 0.03F;
        cape2.xRot += amt * 0.25F + Mth.sin(ageInTicks * 0.07F + 1F) * 0.05F;
        rightArm.xRot = -0.3F + Mth.sin(ageInTicks * 0.05F) * 0.03F;
        rightArm.zRot = 0.1F;
        leftArm.xRot = -1.0F + Mth.sin(ageInTicks * 0.05F + 2F) * 0.05F;
        leftArm.zRot = -0.15F;
        orb.y += Mth.sin(ageInTicks * 0.12F) * 0.5F;
        ring1.yRot = ageInTicks * 0.12F;
        ring2.yRot += ageInTicks * -0.09F;
        float spin = 0.03F;

        // golpe con el cetro: lo alza sobre la cabeza y lo descarga
        if (attackTime > 0F) {
            float t = attackTime;
            if (t < 0.45F) {
                float k = t / 0.45F;
                rightArm.xRot = Mth.lerp(k, rightArm.xRot, -2.8F);
                body.xRot -= k * 0.12F;
            } else {
                float k = Mth.sin((t - 0.45F) / 0.55F * Mth.HALF_PI);
                rightArm.xRot = Mth.lerp(k, -2.8F, -0.9F);
                scepter.xRot += k * 0.5F;
                body.xRot += -0.12F + k * 0.3F;
            }
        }
        float t = ageInTicks - e.actionStart;
        switch (e.getAction()) {
            case VoidKing.JUDGEMENT -> {   // ambos brazos al cielo: llama a las lágrimas
                float k = Math.min(1F, t / 8F);
                rightArm.xRot = Mth.lerp(k, rightArm.xRot, -2.7F);
                leftArm.xRot = Mth.lerp(k, leftArm.xRot, -2.7F);
                rightArm.zRot = Mth.lerp(k, 0.1F, 0.3F);
                leftArm.zRot = Mth.lerp(k, -0.15F, -0.3F);
                head.xRot = Mth.lerp(k, head.xRot, -0.45F);
                spin = 0.12F;
            }
            case VoidKing.DECREE -> {      // cetro en alto, la otra mano señala al frente
                float k = Math.min(1F, t / 6F);
                rightArm.xRot = Mth.lerp(k, rightArm.xRot, -3.0F);
                rightForearm.xRot = Mth.lerp(k, rightForearm.xRot, 0F);
                scepter.xRot = Mth.lerp(k, scepter.xRot, 0F);
                leftArm.xRot = Mth.lerp(k, leftArm.xRot, -1.5F);
                body.xRot -= 0.1F * k;
            }
            case VoidKing.NOVA -> {        // brazos abiertos, la corona estalla
                float k = Math.min(1F, t / 6F);
                rightArm.zRot = Mth.lerp(k, 0.1F, 1.3F);
                leftArm.zRot = Mth.lerp(k, -0.15F, -1.3F);
                rightArm.xRot = Mth.lerp(k, rightArm.xRot, -0.2F);
                leftArm.xRot = Mth.lerp(k, leftArm.xRot, -0.2F);
                body.xRot -= 0.2F * k;
                head.xRot = Mth.lerp(k, head.xRot, -0.3F);
                spin = 0.35F;
            }
            default -> { }
        }
        rightPauldron.xRot = rightArm.xRot * 0.2F;
        leftPauldron.xRot = leftArm.xRot * 0.2F;
        rightPauldron.zRot = (rightArm.zRot - 0.1F) * 0.3F;
        leftPauldron.zRot = (leftArm.zRot + 0.15F) * 0.3F;
        satellites.yRot = ageInTicks * 0.03F + (spin - 0.03F) * Math.max(0F, t);
    }
}
