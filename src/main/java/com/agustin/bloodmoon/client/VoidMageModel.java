package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.VoidMage;
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
 * Hechicero del Vacío: calavera encapuchada con ojos encendidos, túnica larga con tabardo de runas, cetro con un
 * cristal enjaulado en oro, grimorio que flota a su lado y tres cristales que orbitan. Generado junto con su textura.
 */
public class VoidMageModel extends HierarchicalModel<VoidMage> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "void_mage"), "main");

    private final ModelPart root, body, robe, head, rightArm, leftArm, staff, book, runes, rightLeg, leftLeg;

    public VoidMageModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.body = root.getChild("body");
        this.robe = body.getChild("robe");
        this.head = body.getChild("head");
        this.rightArm = body.getChild("rightArm");
        this.leftArm = body.getChild("leftArm");
        this.staff = rightArm.getChild("staff");
        this.book = root.getChild("book");
        this.runes = root.getChild("runes");
        this.rightLeg = root.getChild("rightLeg");
        this.leftLeg = root.getChild("leftLeg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(78, 0).addBox(-4F, 0F, -2.5F, 8F, 10F, 5F)
                .texOffs(54, 33).addBox(-5F, -1F, -3.5F, 10F, 4F, 7F)
                .texOffs(96, 47).addBox(-4.5F, 8F, -3F, 9F, 2F, 6F)
                .texOffs(0, 68).addBox(-1F, 8.5F, -3.5F, 2F, 1F, 1F)
                .texOffs(0, 57).addBox(-1.5F, 1F, -3F, 3F, 6F, 1F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition robe = body.addOrReplaceChild("robe", CubeListBuilder.create()
                .texOffs(4, 0).addBox(-5F, 0F, -3.5F, 10F, 13F, 7F)
                .texOffs(44, 33).addBox(-2F, 0F, -4.2F, 4F, 12F, 1F), PartPose.offsetAndRotation(0F, 10F, 0F, 0F, 0F, 0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 33).addBox(-3.5F, -7.5F, -3.5F, 7F, 7F, 7F)
                .texOffs(96, 64).addBox(-2.5F, -5F, -3.8F, 2F, 2F, 1F)
                .texOffs(102, 64).addBox(0.5F, -5F, -3.8F, 2F, 2F, 1F)
                .texOffs(112, 64).addBox(-2.5F, -2F, -3.8F, 5F, 1F, 1F)
                .texOffs(0, 47).addBox(-4.5F, -9F, -4F, 9F, 1F, 9F)
                .texOffs(38, 0).addBox(-4.5F, -8F, -4F, 1F, 8F, 9F)
                .texOffs(58, 0).addBox(3.5F, -8F, -4F, 1F, 8F, 9F)
                .texOffs(36, 47).addBox(-4.5F, -8F, 4F, 9F, 8F, 1F)
                .texOffs(60, 64).addBox(-4.5F, -9F, -4.6F, 9F, 2F, 1F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition hoodTip = head.addOrReplaceChild("hoodTip", CubeListBuilder.create()
                .texOffs(8, 57).addBox(-1.5F, -1F, 0F, 3F, 2F, 4F)
                .texOffs(28, 64).addBox(-0.5F, -0.5F, 4F, 1F, 1F, 3F), PartPose.offsetAndRotation(0F, -8.5F, 3.5F, 0.6F, 0F, 0F));
        PartDefinition rightArm = body.addOrReplaceChild("rightArm", CubeListBuilder.create()
                .texOffs(88, 33).addBox(-2F, -1F, -2F, 4F, 7F, 4F)
                .texOffs(56, 47).addBox(-2.5F, 5F, -2.5F, 5F, 4F, 5F)
                .texOffs(22, 57).addBox(-1F, 8F, -1F, 2F, 4F, 2F)
                .texOffs(74, 57).addBox(-1.5F, 11F, -1.5F, 3F, 2F, 3F), PartPose.offsetAndRotation(-5F, 1F, 0F, 0F, 0F, 0F));
        PartDefinition leftArm = body.addOrReplaceChild("leftArm", CubeListBuilder.create()
                .texOffs(104, 33).addBox(-2F, -1F, -2F, 4F, 7F, 4F)
                .texOffs(76, 47).addBox(-2.5F, 5F, -2.5F, 5F, 4F, 5F)
                .texOffs(30, 57).addBox(-1F, 8F, -1F, 2F, 4F, 2F)
                .texOffs(86, 57).addBox(-1.5F, 11F, -1.5F, 3F, 2F, 3F), PartPose.offsetAndRotation(5F, 1F, 0F, 0F, 0F, 0F));
        PartDefinition staff = rightArm.addOrReplaceChild("staff", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5F, -20F, -0.5F, 1F, 32F, 1F)
                .texOffs(80, 64).addBox(-1F, -4F, -1F, 2F, 1F, 2F)
                .texOffs(88, 64).addBox(-1F, 8F, -1F, 2F, 1F, 2F)
                .texOffs(98, 57).addBox(-1.5F, -22F, -1.5F, 3F, 2F, 3F)
                .texOffs(110, 57).addBox(-1F, -26F, -1F, 2F, 3F, 2F)
                .texOffs(38, 57).addBox(-2F, -27F, -2F, 1F, 5F, 1F)
                .texOffs(42, 57).addBox(1F, -27F, -2F, 1F, 5F, 1F)
                .texOffs(46, 57).addBox(-2F, -27F, 1F, 1F, 5F, 1F)
                .texOffs(50, 57).addBox(1F, -27F, 1F, 1F, 5F, 1F)
                .texOffs(16, 64).addBox(-1.5F, -28F, -1.5F, 3F, 1F, 3F)
                .texOffs(108, 64).addBox(-0.5F, -30F, -0.5F, 1F, 2F, 1F), PartPose.offsetAndRotation(0F, 12F, 0F, 0.4F, 0F, 0F));
        PartDefinition book = root.addOrReplaceChild("book", CubeListBuilder.create()
                .texOffs(54, 57).addBox(-3F, -0.5F, -2F, 6F, 1F, 4F)
                .texOffs(0, 64).addBox(-2.5F, -1.2F, -1.6F, 5F, 1F, 3F), PartPose.offsetAndRotation(7F, 6F, -6F, 0.3F, -0.4F, 0F));
        PartDefinition runes = root.addOrReplaceChild("runes", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 9F, 0F, 0F, 0F, 0F));
        PartDefinition rune0 = runes.addOrReplaceChild("rune0", CubeListBuilder.create()
                .texOffs(36, 64).addBox(-1F, -1F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(9F, 0F, 0F, 0.6155F, 0.7854F, 0F));
        PartDefinition rune1 = runes.addOrReplaceChild("rune1", CubeListBuilder.create()
                .texOffs(44, 64).addBox(-1F, -1F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(-4.5F, 0F, 7.7942F, 0.6155F, 0.7854F, 0F));
        PartDefinition rune2 = runes.addOrReplaceChild("rune2", CubeListBuilder.create()
                .texOffs(52, 64).addBox(-1F, -1F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(-4.5F, 0F, -7.7942F, 0.6155F, 0.7854F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("rightLeg", CubeListBuilder.create()
                .texOffs(28, 33).addBox(-1F, 0F, -1F, 2F, 12F, 2F), PartPose.offsetAndRotation(-2F, 12F, 0F, 0F, 0F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("leftLeg", CubeListBuilder.create()
                .texOffs(36, 33).addBox(-1F, 0F, -1F, 2F, 12F, 2F), PartPose.offsetAndRotation(2F, 12F, 0F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(VoidMage e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        float walk = limbSwing * 0.6662F, amt = Math.min(1F, limbSwingAmount);
        rightLeg.xRot = Mth.cos(walk) * 0.9F * amt;
        leftLeg.xRot = Mth.cos(walk + Mth.PI) * 0.9F * amt;
        robe.xRot = amt * 0.25F + Mth.sin(ageInTicks * 0.08F) * 0.03F;
        rightArm.xRot = -0.35F + Mth.cos(walk + Mth.PI) * 0.2F * amt;
        rightArm.zRot = 0.06F;
        leftArm.xRot = Mth.cos(walk) * 0.4F * amt;
        leftArm.zRot = -0.1F;
        book.y += Mth.sin(ageInTicks * 0.1F) * 0.8F;
        book.yRot += Mth.sin(ageInTicks * 0.04F) * 0.2F;
        runes.yRot = ageInTicks * 0.06F;
        runes.y += Mth.sin(ageInTicks * 0.07F) * 0.6F;
        float t = ageInTicks - e.spellStart;
        int spell = e.getSpell();
        if (spell != VoidMage.NONE) {
            float k = Math.min(1F, t / 6F);
            float shake = Mth.sin(ageInTicks * 1.3F) * 0.05F;
            switch (spell) {
                case VoidMage.LANCE -> {            // apunta el cetro
                    rightArm.xRot = Mth.lerp(k, rightArm.xRot, -1.6F + head.xRot);
                    rightArm.yRot = head.yRot * 0.6F;
                    staff.xRot = Mth.lerp(k, staff.xRot, 1.3F);
                }
                case VoidMage.STARFALL, VoidMage.SPECTERS -> {   // ambos brazos al cielo
                    rightArm.xRot = Mth.lerp(k, rightArm.xRot, -2.9F) + shake;
                    leftArm.xRot = Mth.lerp(k, leftArm.xRot, -2.7F) - shake;
                    leftArm.zRot = Mth.lerp(k, -0.1F, -0.4F);
                    rightArm.zRot = Mth.lerp(k, 0.06F, 0.4F);
                    head.xRot = Mth.lerp(k, head.xRot, -0.4F);
                }
                case VoidMage.PRISON -> {            // la mano izquierda aprieta hacia el frente
                    leftArm.xRot = Mth.lerp(k, leftArm.xRot, -1.5F) + shake;
                    leftArm.yRot = 0.3F;
                    rightArm.xRot = Mth.lerp(k, rightArm.xRot, -0.8F);
                }
                case VoidMage.WARD -> {              // brazos abiertos
                    rightArm.zRot = Mth.lerp(k, 0.06F, 1.2F);
                    leftArm.zRot = Mth.lerp(k, -0.1F, -1.2F);
                }
                default -> { }
            }
            runes.yRot = ageInTicks * 0.3F;
        }
    }
}
