package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.BloodMoonConfig;
import com.agustin.bloodmoon.BloodMoonMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/**
 * Jinete del Apocalipsis: wither skeleton x2 con netherite completo y arco (Flame + Punch I)
 * que dispara 5 flechas en abanico (AbstractSkeletonMixin), montado en un caballo esqueleto x2.
 */
public class ApocalypseRider extends WitherSkeleton {
    public static final String MOUNT_TAG = BloodMoonMod.MODID + ":rider_mount";
    private static final double BAR_RANGE = 64.0;
    private static final ResourceLocation MOUNT_SCALE_ID =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "rider_mount_scale");

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.bloodmoon.apocalypse_rider").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);

    public ApocalypseRider(EntityType<? extends WitherSkeleton> type, Level level) {
        super(type, level);
        this.xpReward = 50;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));

        ItemStack bow = new ItemStack(Items.BOW);
        Registry<Enchantment> enchantments = registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        bow.enchant(enchantments.getHolderOrThrow(Enchantments.FLAME), 1);
        bow.enchant(enchantments.getHolderOrThrow(Enchantments.PUNCH), 1);
        setItemSlot(EquipmentSlot.MAINHAND, bow);

        float drop = BloodMoonConfig.SPECIAL_GEAR_DROP_CHANCE.get().floatValue();
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, drop);
    }

    @Override
    public boolean requiresCustomPersistence() {
        // Vanilla nunca despawnea a un mob que va montado; el jinete sí debe poder despawnear.
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        BossBars.update(this, bossEvent, BAR_RANGE);
    }

    @Override
    public void remove(RemovalReason reason) {
        Entity mount = getVehicle();
        super.remove(reason);
        bossEvent.removeAllPlayers();
        // Si el jinete despawnea, su caballo se va con él. Si muere, el caballo queda (domado, montable con montura).
        if (reason == RemovalReason.DISCARDED && mount != null && mount.getPersistentData().getBoolean(MOUNT_TAG)) {
            mount.discard();
        }
    }

    /** Spawnea jinete + caballo esqueleto gigante. Devuelve false si no pudo crearlos. */
    public static boolean spawnWithMount(ServerLevel level, BlockPos pos) {
        SkeletonHorse horse = EntityType.SKELETON_HORSE.create(level);
        ApocalypseRider rider = ModEntities.APOCALYPSE_RIDER.get().create(level);
        if (horse == null || rider == null) return false;

        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        float yaw = level.random.nextFloat() * 360F;
        DifficultyInstance difficulty = level.getCurrentDifficultyAt(pos);

        horse.moveTo(x, y, z, yaw, 0F);
        horse.finalizeSpawn(level, difficulty, MobSpawnType.EVENT, null);
        horse.setTamed(true);
        horse.getPersistentData().putBoolean(MOUNT_TAG, true);
        AttributeInstance horseScale = horse.getAttribute(Attributes.SCALE);
        if (horseScale != null) {
            horseScale.addPermanentModifier(new AttributeModifier(MOUNT_SCALE_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
        }

        rider.moveTo(x, y, z, yaw, 0F);
        rider.finalizeSpawn(level, difficulty, MobSpawnType.EVENT, null);
        AttributeInstance health = rider.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(ModEntities.riderHealth());
            rider.setHealth(rider.getMaxHealth());
        }
        rider.startRiding(horse, true);

        level.addFreshEntityWithPassengers(horse);
        return true;
    }
}
