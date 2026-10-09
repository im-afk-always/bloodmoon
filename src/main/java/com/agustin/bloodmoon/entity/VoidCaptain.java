package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

/**
 * Capitán del Vacío: un Centinela de élite, más grande, con el Set del Vacío completo y el estandarte del Dominio en la
 * cabeza. Custodia un obelisco y potencia a las tropas del Vacío que lo rodean.
 */
public class VoidCaptain extends VoidSkeleton {
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.bloodmoon.void_captain"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    public VoidCaptain(EntityType<? extends AbstractSkeleton> type, Level level) {
        super(type, level);
        this.xpReward = 80;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return VoidSkeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, 110.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.SCALE, 1.35);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        Registry<Enchantment> ench = registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        ItemStack[] armor = {new ItemStack(ModItems.VOID_CHESTPLATE.get()), new ItemStack(ModItems.VOID_LEGGINGS.get()),
                new ItemStack(ModItems.VOID_BOOTS.get())};
        EquipmentSlot[] slots = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 3; i++) {
            armor[i].enchant(ench.getHolderOrThrow(Enchantments.PROTECTION), 3);
            setItemSlot(slots[i], armor[i]);
            setDropChance(slots[i], 0F);
        }
        setItemSlot(EquipmentSlot.HEAD, banner());
        setDropChance(EquipmentSlot.HEAD, 1F);
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.enchant(ench.getHolderOrThrow(Enchantments.SHARPNESS), 4);
        setItemSlot(EquipmentSlot.MAINHAND, sword);
        setDropChance(EquipmentSlot.MAINHAND, 0.15F);
    }

    /** Estandarte del Dominio: negro con calavera y borde violetas. */
    private ItemStack banner() {
        ItemStack b = new ItemStack(Items.BLACK_BANNER);
        var patterns = registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        BannerPatternLayers layers = new BannerPatternLayers.Builder()
                .addIfRegistered(patterns, BannerPatterns.GRADIENT_UP, DyeColor.PURPLE)
                .addIfRegistered(patterns, BannerPatterns.SKULL, DyeColor.MAGENTA)
                .addIfRegistered(patterns, BannerPatterns.BORDER, DyeColor.PURPLE)
                .build();
        b.set(net.minecraft.core.component.DataComponents.BANNER_PATTERNS, layers);
        b.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.translatable("item.bloodmoon.dominion_banner"));
        return b;
    }

    @Override
    public boolean isArcher() {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossEvent.setProgress(getHealth() / getMaxHealth());
        BossBars.update(this, bossEvent, 48.0);
        // arenga: las tropas del Vacío cercanas pelean con más fuerza
        if (tickCount % 40 == 0 && level() instanceof ServerLevel sl) {
            for (VoidSkeleton s : sl.getEntitiesOfClass(VoidSkeleton.class, getBoundingBox().inflate(16), e -> e != this)) {
                s.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, true, true));
            }
        }
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        if (name != null) bossEvent.setName(name);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossEvent.removeAllPlayers();
    }
}
