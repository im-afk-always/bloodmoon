package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.BloodMoonManager;
import com.agustin.bloodmoon.MoonType;
import com.agustin.bloodmoon.registry.ModEffects;
import com.agustin.bloodmoon.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * No-muertos del Vacío que aparecen en la Noche sin Luna: esqueletos de hueso negro con grietas púrpuras,
 * con piezas del Set del Vacío encantadas. El Centinela pelea con espada y el Arquero con arco; ambos
 * aplican Quemadura astral. No se queman al sol: se deshacen al terminar la noche.
 */
public class VoidSkeleton extends AbstractSkeleton {
    private boolean boundToNight;

    public VoidSkeleton(EntityType<? extends AbstractSkeleton> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSkeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, 34.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ARMOR, 2.0);
    }

    public boolean isArcher() {
        return getType() == ModEntities.VOID_ARCHER.get();
    }

    /** Ligado al Dominio del Vacío: no se guarda con el chunk; el Dominio lo vuelve a crear cuando hace falta. */
    private boolean dominionBound;

    public void bindToDominion() {
        this.dominionBound = true;
        this.setPersistenceRequired();
    }

    public boolean isDominionBound() {
        return dominionBound;
    }

    @Override
    public boolean shouldBeSaved() {
        return !dominionBound && super.shouldBeSaved();
    }

    /** Tropa de asalto: rompe los bloques que la separan de su objetivo (si mobGriefing lo permite). */
    private boolean raider;

    public void setRaider(boolean raider) {
        this.raider = raider;
    }

    private void breakThrough() {
        net.minecraft.world.entity.LivingEntity t = getTarget();
        if (t == null || distanceToSqr(t) < 6.25 || !net.neoforged.neoforge.event.EventHooks.canEntityGrief(level(), this)) return;
        if (!getNavigation().isDone() && !getNavigation().isStuck()) return;
        net.minecraft.core.Direction dir = net.minecraft.core.Direction.getNearest(t.getX() - getX(), 0, t.getZ() - getZ());
        for (int dy = 0; dy <= 1; dy++) {
            net.minecraft.core.BlockPos p = blockPosition().relative(dir).above(dy);
            net.minecraft.world.level.block.state.BlockState st = level().getBlockState(p);
            float hard = st.getDestroySpeed(level(), p);
            if (st.isAir() || hard < 0 || hard > 20) continue;
            if (com.agustin.bloodmoon.BloodMoonMod.MODID.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace())) continue;
            level().destroyBlock(p, true, this);
            swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            return;
        }
    }

    public void bindToNight() {
        this.boundToNight = true;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        Registry<Enchantment> ench = registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        ItemStack[] armor = {
                new ItemStack(ModItems.VOID_HELMET.get()), new ItemStack(ModItems.VOID_CHESTPLATE.get()),
                new ItemStack(ModItems.VOID_LEGGINGS.get()), new ItemStack(ModItems.VOID_BOOTS.get())};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        float[] chance = {1F, 0.65F, 0.45F, 0.55F};
        for (int i = 0; i < 4; i++) {
            if (random.nextFloat() >= chance[i]) continue;
            armor[i].enchant(ench.getHolderOrThrow(Enchantments.PROTECTION), 1 + random.nextInt(3));
            setItemSlot(slots[i], armor[i]);
            setDropChance(slots[i], 0F);              // el set completo se gana con los jefes
        }
        ItemStack weapon;
        if (isArcher()) {
            weapon = new ItemStack(Items.BOW);
            weapon.enchant(ench.getHolderOrThrow(Enchantments.POWER), 1 + random.nextInt(3));
        } else {
            weapon = new ItemStack(random.nextFloat() < 0.3F ? Items.NETHERITE_SWORD : Items.IRON_SWORD);
            weapon.enchant(ench.getHolderOrThrow(Enchantments.SHARPNESS), 1 + random.nextInt(3));
        }
        setItemSlot(EquipmentSlot.MAINHAND, weapon);
        setDropChance(EquipmentSlot.MAINHAND, 0.04F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) living.addEffect(new MobEffectInstance(ModEffects.ASTRAL_BURN, 80, 0), this);
        return hit;
    }

    @Override
    protected AbstractArrow getArrow(ItemStack arrow, float velocity, @Nullable ItemStack weapon) {
        AbstractArrow a = super.getArrow(arrow, velocity, weapon);
        if (a instanceof Arrow tipped) tipped.addEffect(new MobEffectInstance(ModEffects.ASTRAL_BURN, 60, 0));
        return a;
    }

    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (raider && !level().isClientSide && tickCount % 20 == 0) breakThrough();
        if (level().isClientSide) {
            if (random.nextInt(4) == 0) {
                level().addParticle(ParticleTypes.WITCH, getRandomX(0.5), getY() + getBbHeight() * 0.85, getRandomZ(0.5), 0, 0.01, 0);
            }
        } else if (boundToNight && tickCount % 20 == 0 && BloodMoonManager.current() != MoonType.MOONLESS) {
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 40, 0.3, 0.8, 0.3, 0.1);
                sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 15, 0.3, 0.6, 0.3, 0.02);
            }
            playSound(SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.5F);
            discard();
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !boundToNight && super.removeWhenFarAway(distance);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_SKELETON_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.WITHER_SKELETON_STEP;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F + random.nextFloat() * 0.1F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("BoundToNight", boundToNight);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        boundToNight = tag.getBoolean("BoundToNight");
    }
}
