package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.BloodMoonManager;
import com.agustin.bloodmoon.MoonType;
import com.agustin.bloodmoon.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.List;

/**
 * Base de los caballeros del Vacío (Emisario, Ejecutor): ataques con duración sincronizada al cliente
 * para animar, barra de jefe, salto, retirada al amanecer y utilidades de área.
 * Ataque 0 = ninguno. Cada subclase define sus ids, duraciones y efectos.
 */
public abstract class VoidKnight extends Monster {
    private static final EntityDataAccessor<Byte> DATA_ATTACK =
            SynchedEntityData.defineId(VoidKnight.class, EntityDataSerializers.BYTE);
    protected static final double BAR_RANGE = 96.0;

    protected final ServerBossEvent bossEvent;
    protected int attackTick;
    protected int cooldown = 40;
    protected int stuckTicks;
    protected float lockedYaw;
    private boolean boundToNight;
    /** Cliente: tick en el que empezó la animación del ataque actual. */
    public int clientAttackStart;

    protected VoidKnight(EntityType<? extends Monster> type, Level level, Component name) {
        super(type, level);
        this.xpReward = 500;
        this.setPersistenceRequired();
        this.bossEvent = new ServerBossEvent(name, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    }

    // ------------------------------------------------------------------ contrato de subclases

    protected abstract int duration(int attack);

    protected abstract void tickAttack(int attack, LivingEntity target);

    /** Elegir ataque (0 = ninguno). */
    protected abstract int chooseAttack(LivingEntity target, double dist, boolean stuck);

    /** Ataques durante los cuales no se fija la rotación (p. ej. el salto). */
    protected boolean isFreeRotation(int attack) {
        return false;
    }

    protected int nextCooldown() {
        return isPhaseTwo() ? 15 + random.nextInt(15) : 30 + random.nextInt(20);
    }

    protected void onAttackStart(int attack) {}

    // ------------------------------------------------------------------ sincronización

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK, (byte) 0);
    }

    public int getAttackId() {
        return this.entityData.get(DATA_ATTACK);
    }

    protected void setAttack(int attack) {
        this.entityData.set(DATA_ATTACK, (byte) attack);
        this.attackTick = 0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ATTACK.equals(key)) clientAttackStart = this.tickCount;
    }

    public boolean isPhaseTwo() {
        return getHealth() <= getMaxHealth() * 0.5F;
    }

    public void bindToNight() {
        this.boundToNight = true;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ------------------------------------------------------------------ IA

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        BossBars.update(this, bossEvent, BAR_RANGE);

        if (boundToNight && BloodMoonManager.current() != MoonType.MOONLESS) {
            vanish();
            return;
        }
        breakLeavesIfBlocked();

        int attack = getAttackId();
        LivingEntity target = getTarget();
        if (attack != 0) {
            this.getNavigation().stop();
            if (!isFreeRotation(attack)) faceLocked();
            attackTick++;
            tickAttack(attack, target);
            if (getAttackId() == attack && attackTick >= duration(attack)) {
                setAttack(0);
                cooldown = nextCooldown();
            }
            return;
        }

        if (target == null || !target.isAlive()) return;
        this.getLookControl().setLookAt(target, 30F, 30F);
        double dist = Math.sqrt(distanceToSqr(target));
        if (dist > 7) {
            this.getNavigation().moveTo(target, 1.0);
            stuckTicks = this.getNavigation().isDone() || this.horizontalCollision ? stuckTicks + 1 : 0;
        } else {
            this.getNavigation().stop();
            stuckTicks = 0;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        int next = chooseAttack(target, dist, stuckTicks > 40);
        if (next != 0) startAttack(next, target);
    }

    protected void startAttack(int attack, LivingEntity target) {
        lockedYaw = yawTo(target.position());
        stuckTicks = 0;
        setAttack(attack);
        onAttackStart(attack);
    }

    protected float yawTo(Vec3 pos) {
        return (float) (Mth.atan2(pos.z - getZ(), pos.x - getX()) * Mth.RAD_TO_DEG) - 90F;
    }

    protected void faceLocked() {
        setYRot(lockedYaw);
        yBodyRot = lockedYaw;
        yHeadRot = lockedYaw;
    }

    protected Vec3 forward() {
        return Vec3.directionFromRotation(0F, lockedYaw);
    }

    /** Salto balístico hacia el objetivo (usado por ambos caballeros). */
    protected void leapAt(LivingEntity target) {
        Vec3 to = target.position().subtract(position());
        double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
        double speed = Math.min(3.2, horizontal / 10.4);
        lockedYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F;
        faceLocked();
        double n = Math.max(horizontal, 0.01);
        setDeltaMovement(to.x / n * speed, 1.35, to.z / n * speed);
        hasImpulse = true;
        particles(ParticleTypes.EXPLOSION, position(), 6, 2, 0);
        playSound(SoundEvents.RAVAGER_ROAR, 3F, 0.6F);
    }

    // ------------------------------------------------------------------ utilidades de combate

    /** Daña en un radio; si dir != null, solo dentro del cono (cosHalf = coseno del semiángulo). */
    protected void hitArea(Vec3 center, double radius, Vec3 dir, double cosHalf, float damage, double knock,
                           double lift, int astralTicks) {
        for (LivingEntity e : nearbyEnemies(radius)) {
            Vec3 to = e.position().subtract(center);
            Vec3 flat = new Vec3(to.x, 0, to.z);
            double dist = flat.length();
            if (dist > radius) continue;
            if (dir != null && dist > 1.5 && flat.normalize().dot(new Vec3(dir.x, 0, dir.z).normalize()) < cosHalf) continue;
            float falloff = (float) (0.55 + 0.45 * (1 - dist / radius));
            if (e.hurt(damageSources().mobAttack(this), damage * falloff)) {
                e.knockback(knock, center.x - e.getX(), center.z - e.getZ());
                if (lift > 0) e.push(0, lift, 0);
                e.hurtMarked = true;
                if (astralTicks > 0) e.addEffect(new MobEffectInstance(ModEffects.ASTRAL_BURN, astralTicks, 0));
            }
        }
    }

    protected List<LivingEntity> nearbyEnemies(double radius) {
        AABB box = getBoundingBox().inflate(radius, 4, radius);
        return level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !(e instanceof WitherSkeleton) && !(e instanceof VoidKnight)
                        && !(e instanceof FirstSoulDragon)
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
    }

    protected void impactFx(Vec3 at, float size, SoundEvent sound) {
        playSound(sound, 4F, 0.5F);
        particles(ParticleTypes.EXPLOSION, at.add(0, 0.5, 0), (int) (4 * size), size, 0);
        particles(ParticleTypes.LARGE_SMOKE, at.add(0, 0.5, 0), (int) (12 * size), size * 1.5, 0.05);
    }

    protected void particles(ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(type, at.x, at.y, at.z, count, spread, spread * 0.5, spread, speed);
        }
    }

    protected boolean canGrief() {
        return EventHooks.canEntityGrief(level(), this);
    }

    private void breakLeavesIfBlocked() {
        if (!this.horizontalCollision || !canGrief()) return;
        AABB box = getBoundingBox().inflate(0.5);
        boolean broke = false;
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            if (level().getBlockState(pos).is(BlockTags.LEAVES)) {
                broke = level().destroyBlock(pos, true, this) || broke;
            }
        }
        if (broke) playSound(SoundEvents.GRASS_BREAK, 2F, 0.6F);
    }

    /** Se retira hacia la grieta. */
    public void vanish() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 6, getZ(), 300, 1.5, 5, 1.5, 0.2);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 5F, 0.4F);
        }
        discard();
    }

    // ------------------------------------------------------------------ cliente

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (random.nextInt(3) == 0) {
                level().addParticle(random.nextBoolean() ? ParticleTypes.SOUL : ParticleTypes.SMOKE,
                        getRandomX(1.2), getY() + random.nextDouble() * getBbHeight(), getRandomZ(1.2), 0, 0.02, 0);
            }
            // brillo de los ojos del yelmo
            if (random.nextInt(2) == 0) {
                float yaw = yHeadRot * Mth.DEG_TO_RAD;
                double ex = getX() - Mth.sin(yaw) * 1.1, ez = getZ() + Mth.cos(yaw) * 1.1;
                level().addParticle(ParticleTypes.WITCH, ex + (random.nextDouble() - 0.5) * 0.8,
                        getY() + getBbHeight() * 0.86, ez + (random.nextDouble() - 0.5) * 0.8, 0, 0.01, 0);
            }
        }
    }

    // ------------------------------------------------------------------ varios

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(7, 4, 7); // el arma sobresale mucho de la hitbox
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof WitherSkeleton) && !(target instanceof VoidKnight) && !(target instanceof FirstSoulDragon)
                && super.canAttack(target);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WARDEN_AMBIENT;
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
    protected float getSoundVolume() {
        return 4F;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 2.5F, 0.5F);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossEvent.removeAllPlayers();
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
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }
}
