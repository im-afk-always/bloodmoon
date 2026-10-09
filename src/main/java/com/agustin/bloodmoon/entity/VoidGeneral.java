package com.agustin.bloodmoon.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * General del Vacío: señor de guerra de ~4 bloques con guja. Rango superior del Dominio (sede: una fortaleza).
 * <ul>
 *   <li>Guja: alcance largo y barrido que hiere a todos los que tenga delante.</li>
 *   <li>Grito de guerra: llama a Centinelas y enardece a los aliados cercanos (Fuerza y Velocidad).</li>
 *   <li>Salto del Vacío: cae sobre su objetivo y clava la guja: onda de choque.</li>
 *   <li>Por debajo de la mitad de vida, todo más seguido.</li>
 * </ul>
 */
public class VoidGeneral extends Monster {
    public static final int NONE = 0, WAR_CRY = 1, LEAP = 2;
    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(VoidGeneral.class, EntityDataSerializers.BYTE);

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.bloodmoon.void_general"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private boolean dominionBound;
    private int actionTick, warCryCd = 200, leapCd = 120;
    private boolean leftGround;
    /** Cliente: tick en que empezó la acción actual (para animar). */
    public float actionStart;

    public VoidGeneral(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 300;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 420.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.ATTACK_DAMAGE, 16.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, (byte) NONE);
    }

    public int getAction() {
        return entityData.get(DATA_ACTION);
    }

    private void setAction(int a) {
        entityData.set(DATA_ACTION, (byte) a);
        actionTick = 0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ACTION.equals(key)) actionStart = tickCount;
    }

    public void bindToDominion() {
        this.dominionBound = true;
    }

    @Override
    public boolean shouldBeSaved() {
        return !dominionBound && super.shouldBeSaved();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** La guja alcanza lejos. */
    @Override
    protected AABB getAttackBoundingBox() {
        return super.getAttackBoundingBox().inflate(1.6, 0, 1.6);
    }

    private boolean phaseTwo() {
        return getHealth() < getMaxHealth() * 0.5F;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && level() instanceof ServerLevel sl) {
            // barrido: todo lo que tenga delante
            Vec3 look = getLookAngle().multiply(1, 0, 1).normalize();
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4.5, 1, 4.5),
                    e -> e != this && e != target && e.isAlive() && !com.agustin.bloodmoon.invasion.VoidAllies.isVoid(e))) {
                Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
                if (to.lengthSqr() < 0.01 || to.normalize().dot(look) < 0.2) continue;
                e.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6F);
            }
            Vec3 at = position().add(look.scale(2.5)).add(0, 1.8, 0);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 3, 1.2, 0.3, 1.2, 0);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 30, 1.5, 0.5, 1.5, 0.05);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.6F);
        }
        return hit;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        BossBars.update(this, bossEvent, 64.0);
        if (!(level() instanceof ServerLevel sl)) return;
        if (warCryCd > 0) warCryCd--;
        if (leapCd > 0) leapCd--;
        LivingEntity target = getTarget();
        int action = getAction();
        if (action == WAR_CRY) {
            getNavigation().stop();
            if (++actionTick == 14) warCry(sl);
            if (actionTick >= 32) setAction(NONE);
            return;
        }
        if (action == LEAP) {
            actionTick++;
            if (!onGround()) leftGround = true;
            if ((leftGround && onGround()) || actionTick > 60) {
                slam(sl);
                setAction(NONE);
            }
            return;
        }
        if (target == null || !target.isAlive()) return;
        double dist = distanceTo(target);
        if (warCryCd <= 0 && dist < 24) {
            setAction(WAR_CRY);
            warCryCd = phaseTwo() ? 340 : 500;
        } else if (leapCd <= 0 && dist > 7 && dist < 26 && onGround()) {
            Vec3 d = target.position().subtract(position());
            double h = Math.sqrt(d.x * d.x + d.z * d.z);
            setDeltaMovement(d.x / h * Math.min(1.6, h * 0.075), 1.05, d.z / h * Math.min(1.6, h * 0.075));
            hasImpulse = true;
            leftGround = false;
            setAction(LEAP);
            playSound(SoundEvents.RAVAGER_ROAR, 2F, 1.3F);
            leapCd = phaseTwo() ? 160 : 260;
        }
    }

    private void warCry(ServerLevel sl) {
        playSound(SoundEvents.RAVAGER_ROAR, 4F, 0.5F);
        playSound(SoundEvents.RAID_HORN.value(), 3F, 0.7F);
        sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 3, getZ(), 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 2, getZ(), 200, 3, 2, 3, 0.2);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(20), com.agustin.bloodmoon.invasion.VoidAllies::isVoid)) {
            e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 240, phaseTwo() ? 1 : 0));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 240, 0));
        }
        int n = phaseTwo() ? 3 : 2;
        for (int i = 0; i < n; i++) {
            VoidSkeleton s = ModEntities.VOID_SENTINEL.get().create(sl);
            if (s == null) continue;
            double a = random.nextDouble() * Math.PI * 2;
            double x = getX() + Math.cos(a) * 4, z = getZ() + Math.sin(a) * 4;
            int y = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            s.moveTo(x, y, z, random.nextFloat() * 360F, 0F);
            s.finalizeSpawn(sl, sl.getCurrentDifficultyAt(s.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            s.equipForTier(phaseTwo() ? 7 : 4);
            s.bindToDominion();
            if (getTarget() != null) s.setTarget(getTarget());
            sl.addFreshEntity(s);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1, z, 30, 0.3, 0.8, 0.3, 0.05);
        }
    }

    private void slam(ServerLevel sl) {
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 3F, 0.5F);
        playSound(SoundEvents.ANVIL_LAND, 2F, 0.4F);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX() + Math.cos(a) * 4, getY() + 0.3, getZ() + Math.sin(a) * 4, 3, 0.2, 0.1, 0.2, 0.05);
            sl.sendParticles(ParticleTypes.SQUID_INK, getX() + Math.cos(a) * 2.5, getY() + 0.2, getZ() + Math.sin(a) * 2.5, 1, 0.1, 0.1, 0.1, 0.02);
        }
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5.5, 2, 5.5),
                e -> e != this && e.isAlive() && !com.agustin.bloodmoon.invasion.VoidAllies.isVoid(e))) {
            double d = e.distanceTo(this);
            float dmg = (float) Math.max(4, 16 * (1 - d / 7));
            e.hurt(damageSources().mobAttack(this), dmg);
            Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.4);
            e.push(push.x, 0.6, push.z);
            e.hurtMarked = true;
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (random.nextInt(3) == 0) {   // ceniza violeta que cae de la capa
                level().addParticle(ParticleTypes.WITCH, getRandomX(0.8), getY() + 1.5 + random.nextDouble() * 1.5, getRandomZ(0.8), 0, -0.02, 0);
            }
        } else if (hasEffect(com.agustin.bloodmoon.registry.ModEffects.ASTRAL_BURN)) {
            removeEffect(com.agustin.bloodmoon.registry.ModEffects.ASTRAL_BURN);
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

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_SKELETON_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 0.45F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.2F, 0.6F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("DominionBound", dominionBound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        dominionBound = tag.getBoolean("DominionBound");
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }
}
