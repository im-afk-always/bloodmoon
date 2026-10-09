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
 * General del Vacío (v2, doble resolución): yelmo con visera y cresta-filo, cuernos de tres tramos, aureola de runas,
 * peto con el ojo del Dominio, hombreras en capas con púas, brazos articulados (codo), capa en dos tramos, faldones,
 * piernas articuladas (rodilla) y una guja con hoja en media luna que nace del casquillo.
 * Geometría y textura salen del mismo spec (generador propio); no editar los cubos a mano. Renderer ×0,9.
 */
public class VoidGeneralModel extends HierarchicalModel<VoidGeneral> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "void_general"), "main");

    private final ModelPart root, body, head, cape, cape2, halo, rightArm, rightForearm, leftArm, leftForearm, glaive,
            rightLeg, rightShin, leftLeg, leftShin;

    public VoidGeneralModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.cape = body.getChild("cape");
        this.cape2 = cape.getChild("cape2");
        this.halo = head.getChild("halo");
        this.rightArm = body.getChild("rightArm");
        this.rightForearm = rightArm.getChild("rightForearm");
        this.leftArm = body.getChild("leftArm");
        this.leftForearm = leftArm.getChild("leftForearm");
        this.glaive = rightForearm.getChild("glaive");
        this.rightLeg = root.getChild("rightLeg");
        this.rightShin = rightLeg.getChild("rightShin");
        this.leftLeg = root.getChild("leftLeg");
        this.leftShin = leftLeg.getChild("leftShin");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create()
                .texOffs(176, 73).addBox(-7F, -3F, -4.5F, 14F, 3F, 9F)
                .texOffs(208, 117).addBox(-2F, -3.5F, -5.5F, 4F, 4F, 1F)
                .texOffs(212, 122).addBox(-1F, -2.5F, -6F, 2F, 2F, 1F)
                .texOffs(192, 0).addBox(-6.5F, 0F, -4F, 13F, 9F, 8F)
                .texOffs(28, 117).addBox(-5F, 0F, -5.5F, 10F, 4F, 1F)
                .texOffs(72, 117).addBox(-4.5F, 4F, -5.8F, 9F, 4F, 1F)
                .texOffs(132, 117).addBox(-4F, 8F, -6F, 8F, 4F, 1F)
                .texOffs(50, 117).addBox(-5F, 0F, 4.5F, 10F, 4F, 1F)
                .texOffs(92, 117).addBox(-4.5F, 4F, 4.8F, 9F, 4F, 1F)
                .texOffs(150, 117).addBox(-4F, 8F, 5F, 8F, 4F, 1F)
                .texOffs(136, 73).addBox(-7.5F, -0.5F, -3F, 1F, 7F, 6F)
                .texOffs(150, 73).addBox(6.5F, -0.5F, -3F, 1F, 7F, 6F), PartPose.offsetAndRotation(0F, -4F, 0F, 0F, 0F, 0F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(214, 56).addBox(-5.5F, -6F, -3.5F, 11F, 7F, 7F)
                .texOffs(144, 122).addBox(-4.5F, -5F, -4.5F, 9F, 2F, 1F)
                .texOffs(164, 122).addBox(-4.5F, -2.5F, -4.5F, 9F, 2F, 1F)
                .texOffs(8, 0).addBox(-7.5F, -19F, -4.5F, 15F, 13F, 9F)
                .texOffs(122, 86).addBox(-8F, -19.5F, -5.5F, 16F, 9F, 2F)
                .texOffs(0, 117).addBox(-6.5F, -10.5F, -5.2F, 13F, 4F, 1F)
                .texOffs(98, 109).addBox(-3F, -17F, -6.5F, 6F, 5F, 1F)
                .texOffs(68, 122).addBox(-2F, -16F, -7F, 4F, 3F, 1F)
                .texOffs(112, 122).addBox(-0.5F, -16F, -7.4F, 1F, 3F, 1F)
                .texOffs(0, 73).addBox(-5F, -22F, -5F, 10F, 3F, 10F)
                .texOffs(222, 73).addBox(-7F, -18F, 4F, 14F, 10F, 2F)
                .texOffs(218, 122).addBox(-1F, -17F, 5.5F, 2F, 2F, 1F)
                .texOffs(224, 122).addBox(-1F, -14F, 5.5F, 2F, 2F, 1F)
                .texOffs(230, 122).addBox(-1F, -11F, 5.5F, 2F, 2F, 1F)
                .texOffs(78, 122).addBox(-7F, -21F, 4F, 2F, 2F, 2F)
                .texOffs(86, 122).addBox(5F, -21F, 4F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, -6F, 0F, 0F, 0F, 0F));
        PartDefinition cape = body.addOrReplaceChild("cape", CubeListBuilder.create()
                .texOffs(144, 56).addBox(-7F, 0F, 0F, 14F, 14F, 1F), PartPose.offsetAndRotation(0F, -20F, 5.5F, 0.08F, 0F, 0F));
        PartDefinition cape2 = cape.addOrReplaceChild("cape2", CubeListBuilder.create()
                .texOffs(0, 56).addBox(-7.5F, 0F, 0F, 15F, 16F, 1F), PartPose.offsetAndRotation(0F, 14F, 0F, 0.05F, 0F, 0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(56, 0).addBox(-5F, -11F, -5F, 10F, 11F, 10F)
                .texOffs(120, 122).addBox(-5.5F, -9.5F, -6F, 11F, 2F, 1F)
                .texOffs(22, 109).addBox(-4.5F, -7.5F, -6F, 9F, 5F, 1F)
                .texOffs(66, 127).addBox(-3.5F, -6F, -6.5F, 3F, 1F, 1F)
                .texOffs(74, 127).addBox(0.5F, -6F, -6.5F, 3F, 1F, 1F)
                .texOffs(8, 109).addBox(-0.5F, -9F, -7F, 1F, 7F, 1F)
                .texOffs(104, 73).addBox(-5.5F, -7F, -5.5F, 2F, 7F, 6F)
                .texOffs(120, 73).addBox(3.5F, -7F, -5.5F, 2F, 7F, 6F)
                .texOffs(112, 117).addBox(-4F, -2.5F, -6.5F, 8F, 3F, 2F)
                .texOffs(0, 86).addBox(-1F, -13F, -4F, 2F, 2F, 10F), PartPose.offsetAndRotation(0F, -21F, 0F, 0F, 0F, 0F));
        PartDefinition crest = head.addOrReplaceChild("crest", CubeListBuilder.create()
                .texOffs(72, 86).addBox(-0.5F, -4F, -3F, 1F, 4F, 8F)
                .texOffs(146, 98).addBox(-0.5F, -7F, -1F, 1F, 3F, 6F)
                .texOffs(168, 109).addBox(-0.5F, -9F, 1F, 1F, 2F, 4F), PartPose.offsetAndRotation(0F, -13F, 0F, 0F, 0F, 0F));
        PartDefinition hornR1 = head.addOrReplaceChild("hornR1", CubeListBuilder.create()
                .texOffs(62, 109).addBox(-6F, -1.5F, -1.5F, 6F, 3F, 3F), PartPose.offsetAndRotation(-5F, -8F, -1F, 0F, 0.25F, 0.25F));
        PartDefinition hornR2 = hornR1.addOrReplaceChild("hornR2", CubeListBuilder.create()
                .texOffs(4, 122).addBox(-5F, -1F, -1F, 5F, 2F, 2F), PartPose.offsetAndRotation(-6F, 0F, 0F, 0F, 0F, 0.65F));
        PartDefinition hornR3 = hornR2.addOrReplaceChild("hornR3", CubeListBuilder.create()
                .texOffs(46, 127).addBox(-4F, -0.5F, -0.5F, 4F, 1F, 1F), PartPose.offsetAndRotation(-5F, 0F, 0F, 0F, 0F, 0.75F));
        PartDefinition hornL1 = head.addOrReplaceChild("hornL1", CubeListBuilder.create()
                .texOffs(80, 109).addBox(0F, -1.5F, -1.5F, 6F, 3F, 3F), PartPose.offsetAndRotation(5F, -8F, -1F, 0F, -0.25F, -0.25F));
        PartDefinition hornL2 = hornL1.addOrReplaceChild("hornL2", CubeListBuilder.create()
                .texOffs(18, 122).addBox(0F, -1F, -1F, 5F, 2F, 2F), PartPose.offsetAndRotation(6F, 0F, 0F, 0F, 0F, -0.65F));
        PartDefinition hornL3 = hornL2.addOrReplaceChild("hornL3", CubeListBuilder.create()
                .texOffs(56, 127).addBox(0F, -0.5F, -0.5F, 4F, 1F, 1F), PartPose.offsetAndRotation(5F, 0F, 0F, 0F, 0F, -0.75F));
        PartDefinition halo = head.addOrReplaceChild("halo", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -6F, 8F, 0F, 0F, 0F));
        PartDefinition ring0 = halo.addOrReplaceChild("ring0", CubeListBuilder.create()
                .texOffs(186, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(11F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition ring1 = halo.addOrReplaceChild("ring1", CubeListBuilder.create()
                .texOffs(190, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(10.1627F, 4.2095F, 0F, 0F, 0F, 0.3927F));
        PartDefinition ring2 = halo.addOrReplaceChild("ring2", CubeListBuilder.create()
                .texOffs(194, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(7.7782F, 7.7782F, 0F, 0F, 0F, 0.7854F));
        PartDefinition ring3 = halo.addOrReplaceChild("ring3", CubeListBuilder.create()
                .texOffs(198, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(4.2095F, 10.1627F, 0F, 0F, 0F, 1.1781F));
        PartDefinition ring4 = halo.addOrReplaceChild("ring4", CubeListBuilder.create()
                .texOffs(202, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(0F, 11F, 0F, 0F, 0F, 1.5708F));
        PartDefinition ring5 = halo.addOrReplaceChild("ring5", CubeListBuilder.create()
                .texOffs(206, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-4.2095F, 10.1627F, 0F, 0F, 0F, 1.9635F));
        PartDefinition ring6 = halo.addOrReplaceChild("ring6", CubeListBuilder.create()
                .texOffs(210, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-7.7782F, 7.7782F, 0F, 0F, 0F, 2.3562F));
        PartDefinition ring7 = halo.addOrReplaceChild("ring7", CubeListBuilder.create()
                .texOffs(214, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-10.1627F, 4.2095F, 0F, 0F, 0F, 2.7489F));
        PartDefinition ring8 = halo.addOrReplaceChild("ring8", CubeListBuilder.create()
                .texOffs(218, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-11F, 0F, 0F, 0F, 0F, 3.1416F));
        PartDefinition ring9 = halo.addOrReplaceChild("ring9", CubeListBuilder.create()
                .texOffs(222, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-10.1627F, -4.2095F, 0F, 0F, 0F, 3.5343F));
        PartDefinition ring10 = halo.addOrReplaceChild("ring10", CubeListBuilder.create()
                .texOffs(226, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-7.7782F, -7.7782F, 0F, 0F, 0F, 3.927F));
        PartDefinition ring11 = halo.addOrReplaceChild("ring11", CubeListBuilder.create()
                .texOffs(230, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(-4.2095F, -10.1627F, 0F, 0F, 0F, 4.3197F));
        PartDefinition ring12 = halo.addOrReplaceChild("ring12", CubeListBuilder.create()
                .texOffs(234, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(0F, -11F, 0F, 0F, 0F, 4.7124F));
        PartDefinition ring13 = halo.addOrReplaceChild("ring13", CubeListBuilder.create()
                .texOffs(238, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(4.2095F, -10.1627F, 0F, 0F, 0F, 5.1051F));
        PartDefinition ring14 = halo.addOrReplaceChild("ring14", CubeListBuilder.create()
                .texOffs(242, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(7.7782F, -7.7782F, 0F, 0F, 0F, 5.4978F));
        PartDefinition ring15 = halo.addOrReplaceChild("ring15", CubeListBuilder.create()
                .texOffs(246, 109).addBox(-0.5F, -2.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(10.1627F, -4.2095F, 0F, 0F, 0F, 5.8905F));
        PartDefinition rune0 = halo.addOrReplaceChild("rune0", CubeListBuilder.create()
                .texOffs(236, 122).addBox(-1F, -1F, -0.5F, 2F, 2F, 1F), PartPose.offsetAndRotation(10.1627F, 4.2095F, -0.3F, 0F, 0F, 1.1781F));
        PartDefinition rune1 = halo.addOrReplaceChild("rune1", CubeListBuilder.create()
                .texOffs(242, 122).addBox(-1F, -1F, -0.5F, 2F, 2F, 1F), PartPose.offsetAndRotation(4.2095F, 10.1627F, -0.3F, 0F, 0F, 1.9635F));
        PartDefinition rune2 = halo.addOrReplaceChild("rune2", CubeListBuilder.create()
                .texOffs(248, 122).addBox(-1F, -1F, -0.5F, 2F, 2F, 1F), PartPose.offsetAndRotation(-4.2095F, 10.1627F, -0.3F, 0F, 0F, 2.7489F));
        PartDefinition rune3 = halo.addOrReplaceChild("rune3", CubeListBuilder.create()
                .texOffs(0, 127).addBox(-1F, -1F, -0.5F, 2F, 2F, 1F), PartPose.offsetAndRotation(-10.1627F, 4.2095F, -0.3F, 0F, 0F, 3.5343F));
        PartDefinition rune4 = halo.addOrReplaceChild("rune4", CubeListBuilder.create()
                .texOffs(6, 127).addBox(-1F, -1F, -0.5F, 2F, 2F, 1F), PartPose.offsetAndRotation(-10.1627F, -4.2095F, -0.3F, 0F, 0F, 4.3197F));
        PartDefinition rune5 = halo.addOrReplaceChild("rune5", CubeListBuilder.create()
                .texOffs(12, 127).addBox(-1F, -1F, -0.5F, 2F, 2F, 1F), PartPose.offsetAndRotation(-4.2095F, -10.1627F, -0.3F, 0F, 0F, 5.1051F));
        PartDefinition rune6 = halo.addOrReplaceChild("rune6", CubeListBuilder.create()
                .texOffs(18, 127).addBox(-1F, -1F, -0.5F, 2F, 2F, 1F), PartPose.offsetAndRotation(4.2095F, -10.1627F, -0.3F, 0F, 0F, 5.8905F));
        PartDefinition rune7 = halo.addOrReplaceChild("rune7", CubeListBuilder.create()
                .texOffs(24, 127).addBox(-1F, -1F, -0.5F, 2F, 2F, 1F), PartPose.offsetAndRotation(10.1627F, -4.2095F, -0.3F, 0F, 0F, 6.6759F));
        PartDefinition rightArm = body.addOrReplaceChild("rightArm", CubeListBuilder.create()
                .texOffs(32, 56).addBox(-2.5F, -2F, -2.5F, 5F, 11F, 5F)
                .texOffs(72, 56).addBox(-4.5F, -5F, -4.5F, 9F, 6F, 9F)
                .texOffs(158, 86).addBox(-5F, -1F, -4F, 8F, 3F, 8F)
                .texOffs(58, 98).addBox(-5F, 1.5F, -3.5F, 7F, 2F, 7F), PartPose.offsetAndRotation(-9F, -16F, 0F, 0F, 0F, 0F));
        PartDefinition rightSpike1 = rightArm.addOrReplaceChild("rightSpike1", CubeListBuilder.create()
                .texOffs(248, 98).addBox(-1F, -6F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(-1.5F, -5F, 0F, 0F, 0F, -0.35F));
        PartDefinition rightSpike2 = rightArm.addOrReplaceChild("rightSpike2", CubeListBuilder.create()
                .texOffs(234, 117).addBox(-0.5F, -4F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(-1.5F, -5F, -3F, 0.35F, 0F, -0.35F));
        PartDefinition rightSpike3 = rightArm.addOrReplaceChild("rightSpike3", CubeListBuilder.create()
                .texOffs(238, 117).addBox(-0.5F, -4F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(-1.5F, -5F, 3F, -0.35F, 0F, -0.35F));
        PartDefinition rightForearm = rightArm.addOrReplaceChild("rightForearm", CubeListBuilder.create()
                .texOffs(174, 56).addBox(-2.5F, 0F, -2.5F, 5F, 10F, 5F)
                .texOffs(24, 86).addBox(-3F, 2F, -3F, 6F, 6F, 6F)
                .texOffs(182, 98).addBox(-3.5F, 7F, -3.5F, 7F, 1F, 7F)
                .texOffs(222, 86).addBox(-3F, 10F, -3F, 6F, 5F, 6F)
                .texOffs(184, 122).addBox(-3F, 12F, -4F, 6F, 2F, 1F)
                .texOffs(112, 109).addBox(-2.5F, -2F, 1.5F, 5F, 4F, 2F)
                .texOffs(94, 122).addBox(-0.5F, -1F, 3.5F, 1F, 2F, 2F), PartPose.offsetAndRotation(0F, 9F, 0F, -0.5F, 0F, 0F));
        PartDefinition leftArm = body.addOrReplaceChild("leftArm", CubeListBuilder.create()
                .texOffs(52, 56).addBox(-2.5F, -2F, -2.5F, 5F, 11F, 5F)
                .texOffs(108, 56).addBox(-4.5F, -5F, -4.5F, 9F, 6F, 9F)
                .texOffs(190, 86).addBox(-3F, -1F, -4F, 8F, 3F, 8F)
                .texOffs(86, 98).addBox(-2F, 1.5F, -3.5F, 7F, 2F, 7F), PartPose.offsetAndRotation(9F, -16F, 0F, 0F, 0F, 0F));
        PartDefinition leftSpike1 = leftArm.addOrReplaceChild("leftSpike1", CubeListBuilder.create()
                .texOffs(0, 109).addBox(-1F, -6F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(1.5F, -5F, 0F, 0F, 0F, 0.35F));
        PartDefinition leftSpike2 = leftArm.addOrReplaceChild("leftSpike2", CubeListBuilder.create()
                .texOffs(242, 117).addBox(-0.5F, -4F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(1.5F, -5F, -3F, 0.35F, 0F, 0.35F));
        PartDefinition leftSpike3 = leftArm.addOrReplaceChild("leftSpike3", CubeListBuilder.create()
                .texOffs(246, 117).addBox(-0.5F, -4F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(1.5F, -5F, 3F, -0.35F, 0F, 0.35F));
        PartDefinition leftForearm = leftArm.addOrReplaceChild("leftForearm", CubeListBuilder.create()
                .texOffs(194, 56).addBox(-2.5F, 0F, -2.5F, 5F, 10F, 5F)
                .texOffs(48, 86).addBox(-3F, 2F, -3F, 6F, 6F, 6F)
                .texOffs(210, 98).addBox(-3.5F, 7F, -3.5F, 7F, 1F, 7F)
                .texOffs(0, 98).addBox(-3F, 10F, -3F, 6F, 5F, 6F)
                .texOffs(198, 122).addBox(-3F, 12F, -4F, 6F, 2F, 1F)
                .texOffs(126, 109).addBox(-2.5F, -2F, 1.5F, 5F, 4F, 2F)
                .texOffs(100, 122).addBox(-0.5F, -1F, 3.5F, 1F, 2F, 2F), PartPose.offsetAndRotation(0F, 9F, 0F, -0.2F, 0F, 0F));
        PartDefinition glaive = rightForearm.addOrReplaceChild("glaive", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1F, -40F, -1F, 2F, 54F, 2F)
                .texOffs(32, 122).addBox(-1.5F, -14F, -1.5F, 3F, 1F, 3F)
                .texOffs(44, 122).addBox(-1.5F, -26F, -1.5F, 3F, 1F, 3F)
                .texOffs(56, 122).addBox(-1.5F, 6F, -1.5F, 3F, 1F, 3F)
                .texOffs(196, 117).addBox(-1.5F, 14F, -1.5F, 3F, 2F, 3F)
                .texOffs(116, 122).addBox(-0.5F, 16F, -0.5F, 1F, 3F, 1F)
                .texOffs(24, 98).addBox(-2F, -45F, -2F, 4F, 6F, 4F)
                .texOffs(42, 109).addBox(-2.5F, -41F, -2.5F, 5F, 1F, 5F)
                .texOffs(250, 117).addBox(-0.5F, -39F, -2.5F, 1F, 4F, 1F)
                .texOffs(0, 122).addBox(-0.5F, -39F, 1.5F, 1F, 4F, 1F)
                .texOffs(164, 73).addBox(-0.5F, -52F, -5F, 1F, 8F, 5F)
                .texOffs(40, 98).addBox(-1F, -51F, -1F, 2F, 7F, 3F)
                .texOffs(90, 86).addBox(-0.5F, -57F, -8F, 1F, 5F, 7F)
                .texOffs(172, 98).addBox(-1F, -57F, -2F, 2F, 6F, 3F)
                .texOffs(106, 86).addBox(-0.5F, -62F, -9F, 1F, 5F, 7F)
                .texOffs(238, 98).addBox(-1F, -62F, -3F, 2F, 5F, 3F)
                .texOffs(160, 98).addBox(-0.5F, -66F, -8F, 1F, 4F, 5F)
                .texOffs(12, 109).addBox(-1F, -66F, -4F, 2F, 4F, 3F)
                .texOffs(178, 109).addBox(-0.5F, -69F, -6F, 1F, 3F, 3F)
                .texOffs(218, 117).addBox(-1F, -69F, -4F, 2F, 3F, 2F)
                .texOffs(106, 122).addBox(-0.5F, -71F, -4F, 1F, 2F, 2F)
                .texOffs(82, 127).addBox(-0.5F, -72F, -3F, 1F, 1F, 1F)
                .texOffs(226, 117).addBox(-0.5F, -47F, 2F, 1F, 2F, 3F)
                .texOffs(42, 127).addBox(-0.5F, -49F, 4F, 1F, 2F, 1F), PartPose.offsetAndRotation(0F, 12.5F, 0F, 1.1F, 0F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("rightLeg", CubeListBuilder.create()
                .texOffs(96, 0).addBox(-3F, 0F, -3F, 6F, 12F, 6F)
                .texOffs(114, 98).addBox(-3.5F, 2F, -4F, 7F, 8F, 1F), PartPose.offsetAndRotation(-4F, -4F, 0F, 0F, 0F, 0F));
        PartDefinition rightShin = rightLeg.addOrReplaceChild("rightShin", CubeListBuilder.create()
                .texOffs(120, 0).addBox(-3F, 0F, -3F, 6F, 12F, 6F)
                .texOffs(50, 98).addBox(-0.5F, 1F, -3.5F, 1F, 9F, 1F)
                .texOffs(140, 109).addBox(-2.5F, -2F, -4.5F, 5F, 4F, 2F)
                .texOffs(30, 127).addBox(-0.5F, -1F, -6.5F, 1F, 1F, 2F)
                .texOffs(40, 73).addBox(-3.5F, 12F, -6F, 7F, 4F, 9F)
                .texOffs(168, 117).addBox(-2.5F, 13F, -8F, 5F, 3F, 2F), PartPose.offsetAndRotation(0F, 12F, 0F, 0F, 0F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("leftLeg", CubeListBuilder.create()
                .texOffs(144, 0).addBox(-3F, 0F, -3F, 6F, 12F, 6F)
                .texOffs(130, 98).addBox(-3.5F, 2F, -4F, 7F, 8F, 1F), PartPose.offsetAndRotation(4F, -4F, 0F, 0F, 0F, 0F));
        PartDefinition leftShin = leftLeg.addOrReplaceChild("leftShin", CubeListBuilder.create()
                .texOffs(168, 0).addBox(-3F, 0F, -3F, 6F, 12F, 6F)
                .texOffs(54, 98).addBox(-0.5F, 1F, -3.5F, 1F, 9F, 1F)
                .texOffs(154, 109).addBox(-2.5F, -2F, -4.5F, 5F, 4F, 2F)
                .texOffs(36, 127).addBox(-0.5F, -1F, -6.5F, 1F, 1F, 2F)
                .texOffs(72, 73).addBox(-3.5F, 12F, -6F, 7F, 4F, 9F)
                .texOffs(182, 117).addBox(-2.5F, 13F, -8F, 5F, 3F, 2F), PartPose.offsetAndRotation(0F, 12F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 256, 256);
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
        rightLeg.xRot = Mth.cos(walk) * 0.9F * amt;
        leftLeg.xRot = Mth.cos(walk + Mth.PI) * 0.9F * amt;
        rightShin.xRot = Math.max(0F, Mth.sin(walk)) * 0.9F * amt;
        leftShin.xRot = Math.max(0F, Mth.sin(walk + Mth.PI)) * 0.9F * amt;
        leftArm.xRot = Mth.cos(walk) * 0.6F * amt;
        leftArm.zRot = -0.08F;
        rightArm.xRot = -0.35F + Mth.cos(walk + Mth.PI) * 0.2F * amt;   // la guja al frente, erguida
        rightArm.zRot = 0.08F;
        body.yRot = Mth.sin(walk) * 0.05F * amt;
        float breathe = Mth.sin(ageInTicks * 0.08F);
        body.xRot = breathe * 0.015F;
        cape.xRot += amt * 0.5F + breathe * 0.04F;
        cape2.xRot += amt * 0.3F + Mth.sin(ageInTicks * 0.09F + 1F) * 0.06F;
        halo.zRot = ageInTicks * 0.035F;
        halo.y += Mth.sin(ageInTicks * 0.07F) * 0.5F;

        // golpe: levanta la guja por encima del hombro y la baja en un tajo diagonal
        if (attackTime > 0F) {
            float t = attackTime;
            if (t < 0.4F) {
                float k = t / 0.4F;
                rightArm.xRot = Mth.lerp(k, rightArm.xRot, -2.7F);
                rightArm.zRot = Mth.lerp(k, 0.08F, 0.35F);
                body.yRot -= k * 0.3F;
            } else {
                float k = (t - 0.4F) / 0.6F;
                float s = Mth.sin(k * Mth.HALF_PI);
                rightArm.xRot = Mth.lerp(s, -2.7F, -0.7F);
                rightArm.zRot = Mth.lerp(s, 0.35F, -0.15F);
                glaive.xRot += s * 0.9F;
                body.yRot += -0.3F + s * 0.75F;
                body.xRot += s * 0.15F;
            }
        }
        float t = ageInTicks - e.actionStart;
        switch (e.getAction()) {
            case VoidGeneral.WAR_CRY -> {   // guja al cielo, el otro brazo abierto, cabeza atrás
                float k = Math.min(1F, t / 8F);
                rightArm.xRot = Mth.lerp(k, rightArm.xRot, -2.9F);
                rightForearm.xRot = Mth.lerp(k, rightForearm.xRot, 0F);
                leftArm.xRot = Mth.lerp(k, leftArm.xRot, -1.6F);
                leftArm.zRot = Mth.lerp(k, -0.08F, -0.9F);
                head.xRot = Mth.lerp(k, head.xRot, -0.5F);
                cape.xRot = 0.6F + Mth.sin(ageInTicks * 0.8F) * 0.1F;
                cape2.xRot = 0.3F + Mth.sin(ageInTicks * 0.8F + 1F) * 0.15F;
            }
            case VoidGeneral.LEAP -> {      // en el aire, guja en alto para clavarla al caer
                float k = Math.min(1F, t / 6F);
                rightArm.xRot = Mth.lerp(k, rightArm.xRot, -3.0F);
                leftArm.xRot = Mth.lerp(k, leftArm.xRot, -1.2F);
                rightLeg.xRot = -0.7F * k;
                rightShin.xRot = 1.1F * k;
                leftLeg.xRot = 0.3F * k;
                leftShin.xRot = 0.6F * k;
                cape.xRot = 1.1F;
                cape2.xRot = 0.5F;
            }
            default -> { }
        }
    }
}
