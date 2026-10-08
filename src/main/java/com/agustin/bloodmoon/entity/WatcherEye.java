package com.agustin.bloodmoon.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ojo Vigía: un ojo pequeño que brota del Observador, orbita un momento y después persigue a un jugador
 * sin dejar de mirarlo. Se revienta de un golpe o un flechazo; si te alcanza, siembra locura.
 */
public class WatcherEye extends Projectile {
    public static final int ORBIT = 30, LIFE = 260;
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(WatcherEye.class, EntityDataSerializers.INT);

    private float orbitPhase;
    private double speed = 0.45;

    public WatcherEye(EntityType<? extends WatcherEye> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setup(VoidEye owner, Player target, float phase, double speed) {
        setOwner(owner);
        entityData.set(DATA_TARGET, target.getId());
        this.orbitPhase = phase;
        this.speed = speed;
        Vec3 p = orbitPos(owner, 0);
        moveTo(p.x, p.y, p.z, 0F, 0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TARGET, -1);
    }

    public Entity target() {
        return level().getEntity(entityData.get(DATA_TARGET));
    }

    private Vec3 orbitPos(VoidEye owner, int age) {
        double a = orbitPhase + age * 0.09;
        double r = VoidEye.RADIUS + 1.5 + age * 0.12;
        Vec3 c = owner.center();
        return new Vec3(c.x + Math.cos(a) * r, c.y + Math.sin(age * 0.2 + orbitPhase) * 2.5, c.z + Math.sin(a) * r);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.WITCH, getX(), getY() + 0.3, getZ(), 0, 0, 0);
            return;
        }
        if (tickCount > LIFE) {
            pop(false);
            return;
        }
        if (tickCount < ORBIT && getOwner() instanceof VoidEye owner && owner.isAlive()) {
            Vec3 p = orbitPos(owner, tickCount);
            setDeltaMovement(p.subtract(position()));
            setPos(p.x, p.y, p.z);
            return;
        }
        Entity t = target();
        if (!(t instanceof Player pl) || !t.isAlive() || pl.isSpectator() || pl.isCreative()) {
            Player near = level().getNearestPlayer(this, 64);
            if (near == null || near.isCreative() || near.isSpectator()) {
                pop(false);
                return;
            }
            entityData.set(DATA_TARGET, near.getId());
            t = near;
        }
        Vec3 want = t.position().add(0, t.getBbHeight() * 0.6, 0).subtract(position()).normalize().scale(speed);
        Vec3 v = getDeltaMovement().scale(0.9).add(want.scale(0.1));
        if (v.lengthSqr() < 1e-4) v = want;
        v = v.normalize().scale(speed);
        setDeltaMovement(v);
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            hitTargetOrDeflectSelf(hit);
            if (isRemoved()) return;
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5F);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return e instanceof Player p && !p.isSpectator() && !p.isCreative() && p.isAlive();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel)) return;
        Entity e = result.getEntity();
        Entity owner = getOwner();
        e.hurt(damageSources().indirectMagic(this, owner != null ? owner : this), 5F);
        if (owner instanceof VoidEye eye && e instanceof ServerPlayer sp) eye.addMadness(sp, 25F);
        pop(true);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (tickCount >= ORBIT) pop(false);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (!level().isClientSide) pop(false);
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public float getPickRadius() {
        return 0.4F;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    public void pop(boolean struck) {
        if (level() instanceof ServerLevel sl && !isRemoved()) {
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.4, getZ(), 12, 0.2, 0.2, 0.2, 0.05);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.4, getZ(), 20, 0.2, 0.2, 0.2, 0.2);
            sl.playSound(null, getX(), getY(), getZ(), struck ? SoundEvents.ENDER_EYE_DEATH : SoundEvents.SLIME_SQUISH_SMALL,
                    SoundSource.HOSTILE, 1F, struck ? 0.5F : 0.7F);
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        discard();   // no sobreviven a una recarga
    }
}
