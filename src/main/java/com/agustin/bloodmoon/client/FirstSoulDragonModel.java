package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.FirstSoulDragon;
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
 * Dragón de la Primera Alma: columna, costillar con el alma encendida, cuello de 4 vértebras, cráneo
 * con mandíbula, cola de 10 segmentos, alas de hueso con membranas desgarradas y patas con garras.
 * Geometría generada por dragon_gen.py (textura 2x). Las poses de tierra coinciden con ese script.
 */
public class FirstSoulDragonModel extends HierarchicalModel<FirstSoulDragon> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "first_soul_dragon"), "main");

    private static final float[] NECK_GROUND = {-0.55F, -0.3F, 0.25F, 0.45F};
    private static final String[] FINGERS = {"fingerA", "fingerB", "fingerC"};

    private final ModelPart root, body, head, jaw;
    private final ModelPart[] neck = new ModelPart[4];
    private final ModelPart[] tail = new ModelPart[10];
    private final ModelPart[][] wing = new ModelPart[2][3];
    private final ModelPart[][] finger = new ModelPart[2][3];
    private final ModelPart[][] leg = new ModelPart[2][3];

    public FirstSoulDragonModel(ModelPart root) {
        this.root = root;
        this.body = part("body");
        this.head = part("head");
        this.jaw = part("jaw");
        for (int i = 0; i < 4; i++) neck[i] = part("neck" + (i + 1));
        for (int i = 0; i < 10; i++) tail[i] = part("tail" + (i + 1));
        String[] sides = {"L", "R"};
        for (int s = 0; s < 2; s++) {
            for (int i = 0; i < 3; i++) {
                wing[s][i] = part("wing" + sides[s] + (i + 1));
                leg[s][i] = part("leg" + sides[s] + (i + 1));
                finger[s][i] = part(FINGERS[i] + sides[s]);
            }
        }
    }

    private ModelPart part(String name) {
        return root.getAllParts().filter(p -> p.hasChild(name)).findFirst().orElseThrow().getChild(name);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.5F, -6.0F, -15.0F, 3.0F, 3.0F, 30.0F)
                .texOffs(190, 62).addBox(-6.5F, -6.5F, -14.0F, 13.0F, 2.0F, 5.0F)
                .texOffs(67, 51).addBox(-5.0F, -5.5F, 8.0F, 10.0F, 4.0F, 6.0F)
                .texOffs(162, 0).addBox(-1.0F, 5.0F, -12.0F, 2.0F, 1.5F, 18.0F)
                .texOffs(99, 51).addBox(-2.5F, -1.5F, -7.0F, 5.0F, 5.0F, 5.0F)
                .texOffs(108, 71).addBox(-0.5F, -9.0F, -13.0F, 1.0F, 3.0F, 1.5F)
                .texOffs(113, 71).addBox(-0.5F, -9.0F, -9.0F, 1.0F, 3.0F, 1.5F)
                .texOffs(92, 71).addBox(-0.5F, -10.0F, -5.0F, 1.0F, 4.0F, 1.5F)
                .texOffs(97, 71).addBox(-0.5F, -10.0F, -1.0F, 1.0F, 4.0F, 1.5F)
                .texOffs(118, 71).addBox(-0.5F, -9.0F, 3.0F, 1.0F, 3.0F, 1.5F)
                .texOffs(123, 71).addBox(-0.5F, -9.0F, 7.0F, 1.0F, 3.0F, 1.5F)
                .texOffs(128, 71).addBox(-0.5F, -9.0F, 11.0F, 1.0F, 3.0F, 1.5F)
                .texOffs(214, 33).addBox(-7.0F, -4.5F, -11.0F, 1.5F, 9.0F, 1.5F)
                .texOffs(220, 33).addBox(5.5F, -4.5F, -11.0F, 1.5F, 9.0F, 1.5F)
                .texOffs(12, 78).addBox(-6.5F, -5.5F, -11.0F, 13.0F, 1.5F, 1.5F)
                .texOffs(157, 78).addBox(-6.0F, 4.0F, -11.0F, 4.0F, 1.0F, 1.5F)
                .texOffs(168, 78).addBox(2.0F, 4.0F, -11.0F, 4.0F, 1.0F, 1.5F)
                .texOffs(226, 33).addBox(-7.0F, -4.5F, -7.0F, 1.5F, 9.0F, 1.5F)
                .texOffs(232, 33).addBox(5.5F, -4.5F, -7.0F, 1.5F, 9.0F, 1.5F)
                .texOffs(41, 78).addBox(-6.5F, -5.5F, -7.0F, 13.0F, 1.5F, 1.5F)
                .texOffs(179, 78).addBox(-6.0F, 4.0F, -7.0F, 4.0F, 1.0F, 1.5F)
                .texOffs(190, 78).addBox(2.0F, 4.0F, -7.0F, 4.0F, 1.0F, 1.5F)
                .texOffs(238, 33).addBox(-7.0F, -4.5F, -3.0F, 1.5F, 9.0F, 1.5F)
                .texOffs(244, 33).addBox(5.5F, -4.5F, -3.0F, 1.5F, 9.0F, 1.5F)
                .texOffs(70, 78).addBox(-6.5F, -5.5F, -3.0F, 13.0F, 1.5F, 1.5F)
                .texOffs(201, 78).addBox(-6.0F, 4.0F, -3.0F, 4.0F, 1.0F, 1.5F)
                .texOffs(212, 78).addBox(2.0F, 4.0F, -3.0F, 4.0F, 1.0F, 1.5F)
                .texOffs(250, 33).addBox(-7.0F, -4.5F, 1.0F, 1.5F, 9.0F, 1.5F)
                .texOffs(0, 51).addBox(5.5F, -4.5F, 1.0F, 1.5F, 9.0F, 1.5F)
                .texOffs(99, 78).addBox(-6.5F, -5.5F, 1.0F, 13.0F, 1.5F, 1.5F)
                .texOffs(223, 78).addBox(-6.0F, 4.0F, 1.0F, 4.0F, 1.0F, 1.5F)
                .texOffs(234, 78).addBox(2.0F, 4.0F, 1.0F, 4.0F, 1.0F, 1.5F)
                .texOffs(6, 51).addBox(-7.0F, -4.5F, 5.0F, 1.5F, 9.0F, 1.5F)
                .texOffs(12, 51).addBox(5.5F, -4.5F, 5.0F, 1.5F, 9.0F, 1.5F)
                .texOffs(128, 78).addBox(-6.5F, -5.5F, 5.0F, 13.0F, 1.5F, 1.5F)
                .texOffs(245, 78).addBox(-6.0F, 4.0F, 5.0F, 4.0F, 1.0F, 1.5F)
                .texOffs(0, 82).addBox(2.0F, 4.0F, 5.0F, 4.0F, 1.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition neck1 = body.addOrReplaceChild("neck1", CubeListBuilder.create()
                .texOffs(236, 51).addBox(-1.5F, -1.5F, -6.0F, 3.0F, 3.0F, 6.0F)
                .texOffs(195, 71).addBox(-0.5F, -3.5F, -4.5F, 1.0F, 2.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, -4.5F, -15.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition neck2 = neck1.addOrReplaceChild("neck2", CubeListBuilder.create()
                .texOffs(0, 62).addBox(-1.5F, -1.5F, -6.0F, 3.0F, 3.0F, 6.0F)
                .texOffs(200, 71).addBox(-0.5F, -3.5F, -4.5F, 1.0F, 2.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition neck3 = neck2.addOrReplaceChild("neck3", CubeListBuilder.create()
                .texOffs(18, 62).addBox(-1.5F, -1.5F, -6.0F, 3.0F, 3.0F, 6.0F)
                .texOffs(205, 71).addBox(-0.5F, -3.5F, -4.5F, 1.0F, 2.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition neck4 = neck3.addOrReplaceChild("neck4", CubeListBuilder.create()
                .texOffs(90, 62).addBox(-1.25F, -1.25F, -6.0F, 2.5F, 2.5F, 6.0F)
                .texOffs(210, 71).addBox(-0.5F, -3.25F, -4.5F, 1.0F, 2.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition head = neck4.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(152, 33).addBox(-3.5F, -3.5F, -7.0F, 7.0F, 5.0F, 7.0F)
                .texOffs(180, 33).addBox(-2.5F, -2.5F, -15.0F, 5.0F, 3.0F, 8.0F)
                .texOffs(133, 71).addBox(-2.0F, -3.0F, -16.0F, 4.0F, 2.5F, 1.5F)
                .texOffs(14, 71).addBox(-4.0F, -1.0F, -6.5F, 8.0F, 2.0F, 4.0F)
                .texOffs(124, 62).addBox(-2.5F, 0.5F, -15.0F, 5.0F, 1.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, -6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create()
                .texOffs(18, 51).addBox(-2.5F, 0.0F, -9.0F, 5.0F, 1.5F, 9.0F)
                .texOffs(148, 62).addBox(-2.0F, -1.0F, -9.0F, 4.0F, 1.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 1.5F, -6.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition hornL = head.addOrReplaceChild("hornL", CubeListBuilder.create()
                .texOffs(46, 51).addBox(-0.75F, -0.75F, 0.0F, 1.5F, 1.5F, 9.0F),
                PartPose.offsetAndRotation(2.5F, -3.0F, -2.0F, 0.35F, 0.3F, 0.0F));
        PartDefinition hornL2 = head.addOrReplaceChild("hornL2", CubeListBuilder.create()
                .texOffs(38, 71).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 5.0F),
                PartPose.offsetAndRotation(3.5F, -1.0F, -3.0F, 0.1F, 0.65F, 0.0F));
        PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create()
                .texOffs(119, 51).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 7.0F)
                .texOffs(215, 71).addBox(-0.5F, -3.5F, 2.0F, 1.0F, 2.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, -4.5F, 14.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create()
                .texOffs(139, 51).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create()
                .texOffs(159, 51).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 7.0F)
                .texOffs(220, 71).addBox(-0.5F, -3.5F, 2.0F, 1.0F, 2.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail4 = tail3.addOrReplaceChild("tail4", CubeListBuilder.create()
                .texOffs(179, 51).addBox(-1.25F, -1.25F, 0.0F, 2.5F, 2.5F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail5 = tail4.addOrReplaceChild("tail5", CubeListBuilder.create()
                .texOffs(198, 51).addBox(-1.25F, -1.25F, 0.0F, 2.5F, 2.5F, 7.0F)
                .texOffs(225, 71).addBox(-0.5F, -3.25F, 2.0F, 1.0F, 2.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail6 = tail5.addOrReplaceChild("tail6", CubeListBuilder.create()
                .texOffs(217, 51).addBox(-1.25F, -1.25F, 0.0F, 2.5F, 2.5F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail7 = tail6.addOrReplaceChild("tail7", CubeListBuilder.create()
                .texOffs(36, 62).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 7.0F)
                .texOffs(230, 71).addBox(-0.5F, -3.0F, 2.0F, 1.0F, 2.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail8 = tail7.addOrReplaceChild("tail8", CubeListBuilder.create()
                .texOffs(54, 62).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail9 = tail8.addOrReplaceChild("tail9", CubeListBuilder.create()
                .texOffs(107, 62).addBox(-0.75F, -0.75F, 0.0F, 1.5F, 1.5F, 7.0F)
                .texOffs(235, 71).addBox(-0.5F, -2.75F, 2.0F, 1.0F, 2.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition tail10 = tail9.addOrReplaceChild("tail10", CubeListBuilder.create()
                .texOffs(226, 62).addBox(-3.0F, -0.5F, 0.0F, 6.0F, 1.0F, 6.0F)
                .texOffs(72, 62).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 8.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 7.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition wingL1 = body.addOrReplaceChild("wingL1", CubeListBuilder.create()
                .texOffs(50, 71).addBox(0.0F, -1.5F, -1.5F, 18.0F, 3.0F, 3.0F)
                .texOffs(0, 33).addBox(0.0F, 0.0F, 0.0F, 18.0F, 0.0F, 18.0F),
                PartPose.offsetAndRotation(6.0F, -5.0F, -11.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition wingL2 = wingL1.addOrReplaceChild("wingL2", CubeListBuilder.create()
                .texOffs(144, 71).addBox(0.0F, -1.0F, -1.0F, 18.0F, 2.0F, 2.0F)
                .texOffs(72, 33).addBox(0.0F, 0.0F, 0.0F, 18.0F, 0.0F, 16.0F)
                .texOffs(0, 71).addBox(16.0F, -1.5F, -5.0F, 3.0F, 3.0F, 4.0F)
                .texOffs(102, 71).addBox(16.75F, 1.5F, -6.5F, 1.5F, 4.0F, 1.5F),
                PartPose.offsetAndRotation(18.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition wingL3 = wingL2.addOrReplaceChild("wingL3", CubeListBuilder.create()
                .texOffs(66, 0).addBox(0.0F, 0.0F, -4.0F, 26.0F, 0.0F, 22.0F),
                PartPose.offsetAndRotation(18.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition fingerAL = wingL3.addOrReplaceChild("fingerAL", CubeListBuilder.create()
                .texOffs(26, 82).addBox(0.0F, -0.5F, -0.5F, 26.0F, 1.0F, 1.0F)
                .texOffs(11, 82).addBox(25.0F, -0.5F, -1.5F, 1.0F, 1.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.15F, 0.0F));
        PartDefinition fingerBL = wingL3.addOrReplaceChild("fingerBL", CubeListBuilder.create()
                .texOffs(80, 82).addBox(0.0F, -0.5F, -0.5F, 23.0F, 1.0F, 1.0F)
                .texOffs(16, 82).addBox(22.0F, -0.5F, -1.5F, 1.0F, 1.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.5F, 0.0F));
        PartDefinition fingerCL = wingL3.addOrReplaceChild("fingerCL", CubeListBuilder.create()
                .texOffs(128, 82).addBox(0.0F, -0.5F, -0.5F, 19.0F, 1.0F, 1.0F)
                .texOffs(21, 82).addBox(18.0F, -0.5F, -1.5F, 1.0F, 1.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -1.1F, 0.0F));
        PartDefinition legL1 = body.addOrReplaceChild("legL1", CubeListBuilder.create()
                .texOffs(140, 33).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 10.0F, 3.0F)
                .texOffs(184, 71).addBox(-0.5F, 2.0F, -2.5F, 1.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(4.5F, 3.0F, 9.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition legL2 = legL1.addOrReplaceChild("legL2", CubeListBuilder.create()
                .texOffs(206, 33).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F)
                .texOffs(240, 71).addBox(-0.5F, -1.0F, 1.0F, 1.0F, 2.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition legL3 = legL2.addOrReplaceChild("legL3", CubeListBuilder.create()
                .texOffs(170, 62).addBox(-2.0F, 0.0F, -5.0F, 4.0F, 2.0F, 6.0F)
                .texOffs(245, 71).addBox(-2.0F, 0.5F, -7.0F, 1.0F, 1.5F, 2.0F)
                .texOffs(188, 71).addBox(-0.5F, 0.5F, -7.5F, 1.0F, 1.5F, 2.5F)
                .texOffs(0, 78).addBox(1.0F, 0.5F, -7.0F, 1.0F, 1.5F, 2.0F)
                .texOffs(6, 78).addBox(-0.5F, 0.5F, 1.0F, 1.0F, 1.5F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition hornR = head.addOrReplaceChild("hornR", CubeListBuilder.create()
                .mirror()
                .texOffs(46, 51).addBox(-0.75F, -0.75F, 0.0F, 1.5F, 1.5F, 9.0F),
                PartPose.offsetAndRotation(-2.5F, -3.0F, -2.0F, 0.35F, -0.3F, 0.0F));
        PartDefinition hornR2 = head.addOrReplaceChild("hornR2", CubeListBuilder.create()
                .mirror()
                .texOffs(38, 71).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 5.0F),
                PartPose.offsetAndRotation(-3.5F, -1.0F, -3.0F, 0.1F, -0.65F, 0.0F));
        PartDefinition wingR1 = body.addOrReplaceChild("wingR1", CubeListBuilder.create()
                .mirror()
                .texOffs(50, 71).addBox(-18.0F, -1.5F, -1.5F, 18.0F, 3.0F, 3.0F)
                .texOffs(0, 33).addBox(-18.0F, 0.0F, 0.0F, 18.0F, 0.0F, 18.0F),
                PartPose.offsetAndRotation(-6.0F, -5.0F, -11.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition wingR2 = wingR1.addOrReplaceChild("wingR2", CubeListBuilder.create()
                .mirror()
                .texOffs(144, 71).addBox(-18.0F, -1.0F, -1.0F, 18.0F, 2.0F, 2.0F)
                .texOffs(72, 33).addBox(-18.0F, 0.0F, 0.0F, 18.0F, 0.0F, 16.0F)
                .texOffs(0, 71).addBox(-19.0F, -1.5F, -5.0F, 3.0F, 3.0F, 4.0F)
                .texOffs(102, 71).addBox(-18.25F, 1.5F, -6.5F, 1.5F, 4.0F, 1.5F),
                PartPose.offsetAndRotation(-18.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition wingR3 = wingR2.addOrReplaceChild("wingR3", CubeListBuilder.create()
                .mirror()
                .texOffs(66, 0).addBox(-26.0F, 0.0F, -4.0F, 26.0F, 0.0F, 22.0F),
                PartPose.offsetAndRotation(-18.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition fingerAR = wingR3.addOrReplaceChild("fingerAR", CubeListBuilder.create()
                .mirror()
                .texOffs(26, 82).addBox(-26.0F, -0.5F, -0.5F, 26.0F, 1.0F, 1.0F)
                .texOffs(11, 82).addBox(-26.0F, -0.5F, -1.5F, 1.0F, 1.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.15F, 0.0F));
        PartDefinition fingerBR = wingR3.addOrReplaceChild("fingerBR", CubeListBuilder.create()
                .mirror()
                .texOffs(80, 82).addBox(-23.0F, -0.5F, -0.5F, 23.0F, 1.0F, 1.0F)
                .texOffs(16, 82).addBox(-23.0F, -0.5F, -1.5F, 1.0F, 1.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.5F, 0.0F));
        PartDefinition fingerCR = wingR3.addOrReplaceChild("fingerCR", CubeListBuilder.create()
                .mirror()
                .texOffs(128, 82).addBox(-19.0F, -0.5F, -0.5F, 19.0F, 1.0F, 1.0F)
                .texOffs(21, 82).addBox(-19.0F, -0.5F, -1.5F, 1.0F, 1.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.1F, 0.0F));
        PartDefinition legR1 = body.addOrReplaceChild("legR1", CubeListBuilder.create()
                .mirror()
                .texOffs(140, 33).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 10.0F, 3.0F)
                .texOffs(184, 71).addBox(-0.5F, 2.0F, -2.5F, 1.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(-4.5F, 3.0F, 9.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition legR2 = legR1.addOrReplaceChild("legR2", CubeListBuilder.create()
                .mirror()
                .texOffs(206, 33).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F)
                .texOffs(240, 71).addBox(-0.5F, -1.0F, 1.0F, 1.0F, 2.0F, 1.5F),
                PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition legR3 = legR2.addOrReplaceChild("legR3", CubeListBuilder.create()
                .mirror()
                .texOffs(170, 62).addBox(-2.0F, 0.0F, -5.0F, 4.0F, 2.0F, 6.0F)
                .texOffs(245, 71).addBox(1.0F, 0.5F, -7.0F, 1.0F, 1.5F, 2.0F)
                .texOffs(188, 71).addBox(-0.5F, 0.5F, -7.5F, 1.0F, 1.5F, 2.5F)
                .texOffs(0, 78).addBox(-2.0F, 0.5F, -7.0F, 1.0F, 1.5F, 2.0F)
                .texOffs(6, 78).addBox(-0.5F, 0.5F, 1.0F, 1.0F, 1.5F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    @Override
    public void setupAnim(FirstSoulDragon e, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float pt = Mth.clamp(age - e.tickCount, 0F, 1F);
        float g = smooth(Mth.lerp(pt, e.groundBlendO, e.groundBlend));
        float f = 1F - g;
        float pitch = Mth.lerp(pt, e.visualPitchO, e.visualPitch);
        float flap = age * 0.12F;

        body.xRot = f * pitch - g * 0.2F;
        for (int s = 0; s < 2; s++) {
            float sg = s == 0 ? 1F : -1F;
            ModelPart[] w = wing[s];
            w[0].zRot = sg * (f * (Mth.sin(flap) * 0.55F + 0.05F) + g * 1.2F);
            w[0].yRot = sg * g * 0.5F;
            w[1].zRot = sg * (f * Mth.sin(flap - 0.7F) * 0.35F - g * 0.2F);
            w[1].yRot = sg * -g * 1.2F;
            w[2].xRot = g * 1.0F;
            w[2].yRot = sg * -g * 2.25F;
            w[2].zRot = sg * (f * Mth.sin(flap - 1.3F) * 0.3F + g * 0.5F);
            for (ModelPart fp : finger[s]) fp.yRot *= 1F - 0.75F * g;
            leg[s][0].xRot = f * 1.1F + g * 0.2F + f * Mth.sin(flap * 0.5F + s) * 0.05F;
            leg[s][1].xRot = f * 0.5F;
            leg[s][2].xRot = f * 0.6F;
        }
        for (int i = 0; i < 4; i++) {
            neck[i].xRot = f * Mth.sin(age * 0.05F + i * 0.6F) * 0.05F + g * NECK_GROUND[i];
            neck[i].yRot = Mth.sin(age * 0.03F + i * 0.8F) * 0.03F;
        }
        head.xRot = g * 0.4F;
        for (int i = 0; i < 10; i++) {
            tail[i].yRot = Mth.sin(age * 0.07F - i * 0.45F) * 0.09F * (1F + 0.4F * f);
            tail[i].xRot = -g * (i < 5 ? 0.08F : 0.03F) + f * Mth.sin(age * 0.05F - i * 0.5F) * 0.02F;
        }
        jaw.xRot = 0.05F + Mth.sin(age * 0.04F) * 0.03F;

        float t = age - e.clientAttackStart;
        switch (e.getAttackId()) {
            case FirstSoulDragon.VOLLEY -> {
                float shot = (t - 12F) % 8F;
                float open = t >= 8 && t <= 46 ? 0.35F + (shot >= 0 && shot < 3 ? 0.4F : 0F) : 0F;
                jaw.xRot += open;
                head.xRot += 0.3F * smooth(t / 8F) * (1F - smooth((t - 46F) / 8F));
                neck[3].xRot += 0.2F * smooth(t / 8F) * (1F - smooth((t - 46F) / 8F));
            }
            case FirstSoulDragon.BREATH -> {
                float wind = smooth(t / FirstSoulDragon.BREATH_START);
                float fire = smooth((t - FirstSoulDragon.BREATH_START) / 6F) * (1F - smooth((t - FirstSoulDragon.BREATH_END) / 10F));
                float rear = wind * (1F - fire);
                neck[0].xRot += -0.35F * rear + 0.15F * fire;
                neck[1].xRot += -0.2F * rear + 0.15F * fire;
                head.xRot += -0.3F * rear + 0.2F * fire;
                jaw.xRot += 0.3F * rear + (0.75F + Mth.sin(t * 0.9F) * 0.05F) * fire;
                head.yRot += Mth.sin(t * 0.08F) * 0.08F * fire;
            }
            case FirstSoulDragon.CLAW_L, FirstSoulDragon.CLAW_R -> {
                int s = e.getAttackId() == FirstSoulDragon.CLAW_L ? 0 : 1;
                float sg = s == 0 ? 1F : -1F;
                float impact = FirstSoulDragon.CLAW_IMPACT;
                float k = t < impact - 8 ? smooth(t / (impact - 8)) : 1F - smooth((t - (impact - 8)) / 8F);
                float settle = 1F - smooth((t - impact - 6) / 12F);
                wing[s][0].zRot -= sg * 1.7F * k;
                wing[s][0].yRot += sg * 0.35F * k;
                wing[s][1].zRot -= sg * 0.5F * k;
                body.zRot += sg * -0.08F * k;
                body.xRot += -0.1F * k + 0.06F * (1F - k) * settle * (t > impact - 8 ? 1F : 0F);
                jaw.xRot += 0.4F * k;
            }
            case FirstSoulDragon.LAND -> {
                float k = Mth.sin(Mth.clamp(t / 30F, 0F, 1F) * Mth.PI);
                body.xRot += 0.18F * k;
                for (int s = 0; s < 2; s++) {
                    leg[s][0].xRot -= 0.3F * k;
                    leg[s][1].xRot += 0.5F * k;
                }
                jaw.xRot += 0.6F * k;
                neck[0].xRot -= 0.25F * k;
            }
            default -> {}
        }
    }
}
