package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.UnknownEmissary;
import com.agustin.bloodmoon.entity.UnknownEmissary.Attack;
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
 * Emisario Desconocido: caballero de armadura negra con yelmo astado, capa hecha jirones y espadón rúnico.
 * Mide 40 px en el modelo; el renderer lo escala x4,8 (~12 bloques). Las animaciones son por código.
 */
public class EmissaryModel extends HierarchicalModel<UnknownEmissary> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "unknown_emissary"), "main");

    // pose de guardia a dos manos
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
                .texOffs(72, 0).addBox(-6.0F, -12.0F, -3.5F, 12.0F, 12.0F, 7.0F)
                .texOffs(102, 27).addBox(-6.5F, -3.0F, -4.0F, 13.0F, 3.0F, 8.0F),
                PartPose.offset(0.0F, 8.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-4.0F, -9.0F, -4.5F, 8.0F, 9.0F, 9.0F)
                .texOffs(42, 27).addBox(-0.5F, -12.0F, -4.0F, 1.0F, 4.0F, 9.0F)
                .texOffs(62, 27).addBox(-5.0F, -1.0F, -5.0F, 10.0F, 2.0F, 10.0F)
                .texOffs(180, 27).addBox(-6.0F, -8.0F, -1.5F, 2.0F, 2.0F, 3.0F)
                .texOffs(152, 27).addBox(-7.0F, -12.0F, -1.0F, 2.0F, 4.0F, 2.0F)
                .texOffs(190, 27).addBox(4.0F, -8.0F, -1.5F, 2.0F, 2.0F, 3.0F)
                .texOffs(160, 27).addBox(5.0F, -12.0F, -1.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, -12.0F, 0.0F));
        PartDefinition cape = body.addOrReplaceChild("cape", CubeListBuilder.create()
                .texOffs(8, 0).addBox(-5.5F, 0.0F, 0.0F, 11.0F, 22.0F, 1.0F),
                PartPose.offset(0.0F, -11.5F, 3.5F));
        PartDefinition rightArm = body.addOrReplaceChild("rightArm", CubeListBuilder.create()
                .texOffs(180, 0).addBox(-4.5F, -3.0F, -4.0F, 7.0F, 6.0F, 8.0F)
                .texOffs(200, 27).addBox(-3.5F, -6.0F, -1.0F, 2.0F, 3.0F, 2.0F)
                .texOffs(144, 0).addBox(-3.0F, -1.0F, -2.5F, 4.0F, 13.0F, 5.0F),
                PartPose.offset(-7.5F, -10.0F, 0.0F));
        PartDefinition sword = rightArm.addOrReplaceChild("sword", CubeListBuilder.create()
                .texOffs(144, 27).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F)
                .texOffs(208, 27).addBox(-5.0F, -6.0F, -1.5F, 10.0F, 2.0F, 3.0F)
                .texOffs(0, 0).addBox(-1.5F, -32.0F, -0.5F, 3.0F, 26.0F, 1.0F)
                .texOffs(14, 41).addBox(-0.5F, -34.0F, -0.5F, 1.0F, 2.0F, 1.0F)
                .texOffs(168, 27).addBox(-1.5F, 4.0F, -1.5F, 3.0F, 3.0F, 3.0F),
                PartPose.offset(-1.0F, 11.0F, 0.0F));
        PartDefinition leftArm = body.addOrReplaceChild("leftArm", CubeListBuilder.create()
                .texOffs(210, 0).addBox(-2.5F, -3.0F, -4.0F, 7.0F, 6.0F, 8.0F)
                .texOffs(234, 27).addBox(1.5F, -6.0F, -1.0F, 2.0F, 3.0F, 2.0F)
                .texOffs(162, 0).addBox(-1.0F, -1.0F, -2.5F, 4.0F, 13.0F, 5.0F),
                PartPose.offset(7.5F, -10.0F, 0.0F));
        PartDefinition faulds = root.addOrReplaceChild("faulds", CubeListBuilder.create()
                .texOffs(0, 27).addBox(-6.5F, -1.0F, -4.0F, 13.0F, 6.0F, 8.0F),
                PartPose.offset(0.0F, 8.0F, 0.0F));
        PartDefinition rightLeg = root.addOrReplaceChild("rightLeg", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 16.0F, 5.0F)
                .texOffs(242, 27).addBox(-3.0F, 5.0F, -3.5F, 6.0F, 3.0F, 1.0F),
                PartPose.offset(-3.0F, 8.0F, 0.0F));
        PartDefinition leftLeg = root.addOrReplaceChild("leftLeg", CubeListBuilder.create()
                .texOffs(52, 0).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 16.0F, 5.0F)
                .texOffs(0, 41).addBox(-3.0F, 5.0F, -3.5F, 6.0F, 3.0F, 1.0F),
                PartPose.offset(3.0F, 8.0F, 0.0F));
        return LayerDefinition.create(mesh, 256, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(UnknownEmissary entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);

        // ---- base: guardia, respiración, caminar pesado
        float armX = ARM_X, swordX = SWORD_X, bodyX = Mth.sin(ageInTicks * 0.05F) * 0.02F, bodyY = 0F;
        float headX = headPitch * Mth.DEG_TO_RAD * 0.5F;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.6F;

        float walk = Mth.cos(limbSwing * 0.35F) * 0.7F * limbSwingAmount;
        rightLeg.xRot = walk;
        leftLeg.xRot = -walk;
        body.zRot = Mth.sin(limbSwing * 0.35F) * 0.04F * limbSwingAmount;
        cape.xRot = 0.08F + limbSwingAmount * 0.5F + Mth.sin(ageInTicks * 0.07F) * 0.04F;

        // ---- ataques
        Attack attack = entity.getAttack();
        float t = ageInTicks - entity.clientAttackStart;
        switch (attack) {
            case CLEAVE -> {
                armX = key(t, 0, ARM_X, 18, -3.1F, 22, -0.35F, 32, -0.35F, 40, ARM_X);
                bodyX = key(t, 0, 0, 18, -0.2F, 22, 0.45F, 32, 0.45F, 40, 0);
                cape.xRot += key(t, 0, 0, 18, 0.1F, 22, 0.6F, 40, 0);
            }
            case SWEEP -> {
                bodyY = key(t, 0, 0, 14, 0.9F, 16, 0.9F, 24, 0.9F - 2 * PI, 36, -2 * PI);
                armX = key(t, 0, ARM_X, 14, -1.5F, 26, -1.5F, 36, ARM_X);
                swordX = key(t, 0, SWORD_X, 14, 3.0F, 26, 3.0F, 36, SWORD_X);
                cape.xRot += key(t, 0, 0, 16, 0.2F, 24, 0.9F, 36, 0);
            }
            case LEAP -> {
                bodyX = key(t, 0, 0, 8, 0.35F, 12, -0.1F);
                armX = key(t, 0, ARM_X, 10, -2.9F);
                rightLeg.xRot = key(t, 0, 0, 8, -0.7F, 12, 0.3F);
                leftLeg.xRot = key(t, 0, 0, 8, 0.4F, 12, -0.3F);
                cape.xRot += key(t, 0, 0, 12, 1.0F);
            }
            case SLAM -> {
                armX = key(t, 0, -2.9F, 3, -0.3F, 18, -0.3F, 24, ARM_X);
                swordX = key(t, 0, SWORD_X, 3, 1.9F, 18, 1.9F, 24, SWORD_X);
                bodyX = key(t, 0, 0.2F, 3, 0.6F, 18, 0.6F, 24, 0);
                rightLeg.xRot = key(t, 0, -0.5F, 3, -0.5F, 24, 0);
            }
            case SOUL_RIFT -> {
                armX = key(t, 0, ARM_X, 16, -2.7F, 20, -0.45F, 44, -0.45F, 50, ARM_X);
                swordX = key(t, 0, SWORD_X, 16, PI + 2.7F, 20, PI + 0.45F, 44, PI + 0.45F, 50, SWORD_X);
                bodyX = key(t, 0, 0, 16, -0.15F, 20, 0.5F, 44, 0.5F, 50, 0);
            }
            case SUMMON -> {
                armX = key(t, 0, ARM_X, 14, -3.0F, 50, -3.0F, 60, ARM_X);
                swordX = key(t, 0, SWORD_X, 14, 3.14F, 50, 3.14F, 60, SWORD_X);
                headX = key(t, 0, headX, 14, -0.6F, 50, -0.6F, 60, headX);
                bodyX = key(t, 0, 0, 14, -0.15F, 50, -0.15F, 60, 0);
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

    /** Interpola entre claves (tiempo, valor, tiempo, valor, ...) con suavizado. */
    private static float key(float t, float... kv) {
        if (t <= kv[0]) return kv[1];
        for (int i = 2; i < kv.length; i += 2) {
            if (t <= kv[i]) {
                float a = (t - kv[i - 2]) / (kv[i] - kv[i - 2]);
                a = a * a * (3F - 2F * a);
                return Mth.lerp(a, kv[i - 1], kv[i + 1]);
            }
        }
        return kv[kv.length - 1];
    }
}
