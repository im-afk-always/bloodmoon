package com.agustin.bloodmoon;

import com.agustin.bloodmoon.entity.CursedCreeper;
import com.agustin.bloodmoon.mixin.CreeperAccessor;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Buffs para hostiles que aparecen durante una Luna de Sangre (normal o Súper).
 * Todo queda en el NBT del mob, así que lo conserva hasta morir o despawnear.
 */
public final class MobBuffs {
    public static final String BUFFED_TAG = BloodMoonMod.MODID + ":buffed";

    private static final ResourceLocation FOLLOW_RANGE_ID = id("follow_range");
    private static final ResourceLocation PHANTOM_SCALE_ID = id("giant_phantom_scale");
    private static final ResourceLocation PHANTOM_DAMAGE_ID = id("giant_phantom_damage");

    private MobBuffs() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, path);
    }

    public static boolean isBuffed(Entity entity) {
        return entity.getPersistentData().getBoolean(BUFFED_TAG);
    }

    /** Súper Luna: un % de los creepers naturales se reemplaza por Cursed Creepers. */
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (BloodMoonManager.current() != MoonType.SUPER) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;
        Mob mob = event.getEntity();
        if (!(mob instanceof Creeper) || mob instanceof CursedCreeper) return;
        if (event.getLevel().getLevel().dimension() != Level.OVERWORLD) return;
        if (mob.getRandom().nextDouble() >= BloodMoonConfig.CURSED_CHANCE.get()) return;

        event.setSpawnCancelled(true);
        BloodMoonManager.queueCursedCreeper(mob.blockPosition());
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.loadedFromDisk()) return;
        if (level.dimension() != Level.OVERWORLD) return;
        MoonType moon = BloodMoonManager.current();
        if (!moon.isBlood()) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Mob mob) || !(entity instanceof Enemy)) return;
        if (isBuffed(mob)) return;
        mob.getPersistentData().putBoolean(BUFFED_TAG, true);

        // ---- Luna de Sangre (también aplica en la Súper)
        addModifier(mob, Attributes.FOLLOW_RANGE, FOLLOW_RANGE_ID, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (mob instanceof Spider) effect(mob, MobEffects.MOVEMENT_SPEED);
        if (mob instanceof Zombie) effect(mob, MobEffects.DAMAGE_BOOST);
        // Esqueletos: doble flecha en AbstractSkeletonMixin (lee BUFFED_TAG)

        if (moon != MoonType.SUPER) return;

        // ---- Súper Luna de Sangre
        if (mob instanceof Creeper creeper) {
            effect(creeper, MobEffects.MOVEMENT_SPEED);
            if (!(creeper instanceof CursedCreeper)) {
                CreeperAccessor acc = (CreeperAccessor) creeper;
                int radius = acc.bloodmoon$getExplosionRadius() * BloodMoonConfig.CREEPER_EXPLOSION_MULT.get();
                acc.bloodmoon$setExplosionRadius(Math.min(127, radius));
            }
        }

        if (mob instanceof Zombie zombie) {
            effect(zombie, MobEffects.MOVEMENT_SPEED);
            equip(zombie, EquipmentSlot.HEAD, Items.DIAMOND_HELMET);
            equip(zombie, EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE);
            equip(zombie, EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS);
            equip(zombie, EquipmentSlot.FEET, Items.DIAMOND_BOOTS);
            if (!(zombie instanceof Drowned)) equip(zombie, EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD);
        }

        if (mob instanceof Phantom phantom) {
            double scale = BloodMoonConfig.PHANTOM_SCALE.get();
            double dmg = BloodMoonConfig.PHANTOM_DAMAGE_MULT.get();
            addModifier(phantom, Attributes.SCALE, PHANTOM_SCALE_ID, scale - 1.0, AttributeModifier.Operation.ADD_VALUE);
            addModifier(phantom, Attributes.ATTACK_DAMAGE, PHANTOM_DAMAGE_ID, dmg - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }

    private static void effect(Mob mob, Holder<MobEffect> effect) {
        mob.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, false, true));
    }

    private static void equip(Mob mob, EquipmentSlot slot, net.minecraft.world.item.Item item) {
        mob.setItemSlot(slot, new ItemStack(item));
        mob.setDropChance(slot, BloodMoonConfig.SPECIAL_GEAR_DROP_CHANCE.get().floatValue());
    }

    private static void addModifier(Mob mob, Holder<Attribute> attribute, ResourceLocation id, double amount,
                                    AttributeModifier.Operation op) {
        if (amount == 0) return;
        AttributeInstance inst = mob.getAttribute(attribute);
        if (inst != null && !inst.hasModifier(id)) {
            inst.addPermanentModifier(new AttributeModifier(id, amount, op));
        }
    }
}
