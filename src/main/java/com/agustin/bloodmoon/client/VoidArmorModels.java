package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.util.EnumMap;
import java.util.Map;

/**
 * Set del Vacío en 3D: un modelo por pieza (yelmo, coraza, quijotes, grebas) con placas que sobresalen
 * del cuerpo. Textura a doble resolución (256x128 real, declarada 128x64). Generado por armor_gen.py.
 */
public final class VoidArmorModels {
    public static final ModelLayerLocation HEAD = layer("head");
    public static final ModelLayerLocation CHEST = layer("chest");
    public static final ModelLayerLocation LEGS = layer("legs");
    public static final ModelLayerLocation FEET = layer("feet");

    private static final Map<EquipmentSlot, HumanoidModel<LivingEntity>> CACHE = new EnumMap<>(EquipmentSlot.class);

    private VoidArmorModels() {}

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "void_armor"), name);
    }

    public static HumanoidModel<LivingEntity> get(EquipmentSlot slot) {
        return CACHE.computeIfAbsent(slot, s -> new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(switch (s) {
            case HEAD -> HEAD;
            case CHEST -> CHEST;
            case LEGS -> LEGS;
            default -> FEET;
        })));
    }

    public static void invalidate() {
        CACHE.clear();
    }

    /** ¿La pieza equipada en este slot es del Set del Vacío? */
    public static boolean isVoidPiece(ItemStack stack, EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> stack.is(ModItems.VOID_HELMET.get());
            case CHEST -> stack.is(ModItems.VOID_CHESTPLATE.get());
            case LEGS -> stack.is(ModItems.VOID_LEGGINGS.get());
            case FEET -> stack.is(ModItems.VOID_BOOTS.get());
            default -> false;
        };
    }

    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HEAD, VoidArmorModels::createHead);
        event.registerLayerDefinition(CHEST, VoidArmorModels::createChest);
        event.registerLayerDefinition(LEGS, VoidArmorModels::createLegs);
        event.registerLayerDefinition(FEET, VoidArmorModels::createFeet);
    }

    /** Reemplaza el modelo plano vanilla por el modelo 3D en cualquier humanoide (jugador, soporte, zombi...). */
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                return isVoidPiece(stack, slot) ? get(slot) : original;
            }
        }, ModItems.VOID_HELMET.get(), ModItems.VOID_CHESTPLATE.get(), ModItems.VOID_LEGGINGS.get(), ModItems.VOID_BOOTS.get());
    }

    public static LayerDefinition createHead() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5F, -9F, -5F, 10F, 10F, 10F)
                .texOffs(0, 48).addBox(-5.5F, -7.5F, -5.5F, 11F, 1.5F, 1F)
                .texOffs(22, 41).addBox(-5.5F, -4F, -5.5F, 4F, 4F, 1F)
                .texOffs(32, 41).addBox(1.5F, -4F, -5.5F, 4F, 4F, 1F)
                .texOffs(92, 0).addBox(-0.5F, -11.5F, -4.5F, 1F, 2.5F, 9.5F)
                .texOffs(66, 41).addBox(-6.5F, -7.5F, -1F, 1.5F, 1.5F, 2F)
                .texOffs(54, 41).addBox(-7F, -10.5F, -0.75F, 1.5F, 3F, 1.5F)
                .texOffs(73, 41).addBox(5F, -7.5F, -1F, 1.5F, 1.5F, 2F)
                .texOffs(60, 41).addBox(5.5F, -10.5F, -0.75F, 1.5F, 3F, 1.5F),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("body", CubeListBuilder.create(),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(),
                PartPose.offset(-5F, 2F, 0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(),
                PartPose.offset(5F, 2F, 0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(),
                PartPose.offset(-1.9F, 12F, 0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(),
                PartPose.offset(1.9F, 12F, 0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition createChest() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create(),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(40, 0).addBox(-5F, -0.5F, -3F, 10F, 11F, 6F)
                .texOffs(0, 41).addBox(-4.5F, 0.5F, -3.5F, 9F, 6.5F, 0.5F)
                .texOffs(19, 41).addBox(-0.5F, 0.5F, -4F, 1F, 6F, 0.5F)
                .texOffs(24, 48).addBox(-4F, 7.5F, -3.5F, 8F, 1.5F, 0.5F)
                .texOffs(58, 32).addBox(-4.5F, 0.5F, 3F, 9F, 7F, 0.5F),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(72, 0).addBox(-3.5F, -2.5F, -2.5F, 5F, 8F, 5F)
                .texOffs(20, 20).addBox(-4.5F, -3.5F, -3.5F, 6F, 4F, 7F)
                .texOffs(34, 32).addBox(-5F, 0.5F, -3.25F, 5.5F, 1.5F, 6.5F)
                .texOffs(80, 41).addBox(-3.5F, -5.5F, -0.75F, 1.5F, 2F, 1.5F)
                .texOffs(46, 20).addBox(-3.75F, 5.5F, -2.75F, 5.5F, 5F, 5.5F)
                .texOffs(77, 32).addBox(-4F, 5.25F, -3F, 6F, 1.5F, 6F),
                PartPose.offset(-5F, 2F, 0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .mirror()
                .texOffs(72, 0).addBox(-1.5F, -2.5F, -2.5F, 5F, 8F, 5F)
                .texOffs(20, 20).addBox(-1.5F, -3.5F, -3.5F, 6F, 4F, 7F)
                .texOffs(34, 32).addBox(-0.5F, 0.5F, -3.25F, 5.5F, 1.5F, 6.5F)
                .texOffs(80, 41).addBox(2F, -5.5F, -0.75F, 1.5F, 2F, 1.5F)
                .texOffs(46, 20).addBox(-1.75F, 5.5F, -2.75F, 5.5F, 5F, 5.5F)
                .texOffs(77, 32).addBox(-2F, 5.25F, -3F, 6F, 1.5F, 6F),
                PartPose.offset(5F, 2F, 0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(),
                PartPose.offset(-1.9F, 12F, 0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(),
                PartPose.offset(1.9F, 12F, 0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition createLegs() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create(),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-5.25F, 9.5F, -3.25F, 10.5F, 2.5F, 6.5F)
                .texOffs(86, 41).addBox(-1F, 9.25F, -3.75F, 2F, 3F, 0.5F),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(),
                PartPose.offset(-5F, 2F, 0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(),
                PartPose.offset(5F, 2F, 0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 20).addBox(-2.5F, -0.5F, -2.5F, 5F, 7F, 5F)
                .texOffs(42, 41).addBox(-2.75F, 0F, -3F, 5.5F, 4.5F, 0.5F)
                .texOffs(90, 20).addBox(-3F, 0F, -2.5F, 0.5F, 4.5F, 5F)
                .texOffs(91, 41).addBox(-2.75F, 5F, -3.25F, 5.5F, 2.5F, 1F),
                PartPose.offset(-1.9F, 12F, 0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .mirror()
                .texOffs(0, 20).addBox(-2.5F, -0.5F, -2.5F, 5F, 7F, 5F)
                .texOffs(42, 41).addBox(-2.75F, 0F, -3F, 5.5F, 4.5F, 0.5F)
                .texOffs(90, 20).addBox(2.5F, 0F, -2.5F, 0.5F, 4.5F, 5F)
                .texOffs(91, 41).addBox(-2.75F, 5F, -3.25F, 5.5F, 2.5F, 1F),
                PartPose.offset(1.9F, 12F, 0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition createFeet() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create(),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("body", CubeListBuilder.create(),
                PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(),
                PartPose.offset(-5F, 2F, 0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(),
                PartPose.offset(5F, 2F, 0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(68, 20).addBox(-2.75F, 7.25F, -2.75F, 5.5F, 5F, 5.5F)
                .texOffs(101, 32).addBox(-3F, 7F, -3F, 6F, 1.5F, 6F)
                .texOffs(104, 41).addBox(-2.75F, 10.25F, -3.75F, 5.5F, 2F, 1F),
                PartPose.offset(-1.9F, 12F, 0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .mirror()
                .texOffs(68, 20).addBox(-2.75F, 7.25F, -2.75F, 5.5F, 5F, 5.5F)
                .texOffs(101, 32).addBox(-3F, 7F, -3F, 6F, 1.5F, 6F)
                .texOffs(104, 41).addBox(-2.75F, 10.25F, -3.75F, 5.5F, 2F, 1F),
                PartPose.offset(1.9F, 12F, 0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
}
