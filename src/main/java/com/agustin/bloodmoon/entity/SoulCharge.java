package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.BloodMoonConfig;
import com.agustin.bloodmoon.world.VoidImpact;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Carga de fuego púrpura del Dragón de la Primera Alma. Explota como x3, x5 o x10 un creeper
 * (respeta mobGriefing) y deja el suelo calcinado con llamas astrales.
 */
public class SoulCharge extends Projectile {
    private static final EntityDataAccessor<Byte> DATA_MULT = SynchedEntityData.defineId(SoulCharge.class, EntityDataSerializers.BYTE);
    private int life;

    public SoulCharge(EntityType<? extends SoulCharge> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public static SoulCharge shoot(Level level, LivingEntity owner, Vec3 from, Vec3 velocity, int multiplier) {
        SoulCharge charge = new SoulCharge(ModEntities.SOUL_CHARGE.get(), level);
        charge.setOwner(owner);
        charge.entityData.set(DATA_MULT, (byte) multiplier);
        charge.moveTo(from.x, from.y, from.z, 0F, 0F);
        charge.setDeltaMovement(velocity);
        level.addFreshEntity(charge);
        return charge;
    }

    /** 3, 5 o 10 (veces un creeper). */
    public int multiplier() {
        return this.entityData.get(DATA_MULT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MULT, (byte) 3);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            onHit(hit);
            if (isRemoved()) return;
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        setDeltaMovement(v.add(0, -0.01, 0));
        ProjectileUtil.rotateTowardsMovement(this, 0.2F);

        if (level().isClientSide) {
            int m = multiplier();
            for (int i = 0; i < 2 + m / 2; i++) {
                double s = 0.25 * m;
                level().addParticle(i % 2 == 0 ? ParticleTypes.DRAGON_BREATH : ParticleTypes.WITCH,
                        getX() + (random.nextDouble() - 0.5) * s, getY() + (random.nextDouble() - 0.5) * s,
                        getZ() + (random.nextDouble() - 0.5) * s, -v.x * 0.1, -v.y * 0.1, -v.z * 0.1);
            }
        } else if (++life > 400 || getY() < level().getMinBuildHeight()) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof FirstSoulDragon) && !(target instanceof DragonPart)
                && !(target instanceof SoulCharge);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel sl) {
            explode(sl);
            discard();
        }
    }

    private void explode(ServerLevel level) {
        int m = multiplier();
        float power = (float) (3.0 * m * BloodMoonConfig.DRAGON_CHARGE_POWER.get());
        Vec3 at = position();
        if (power > 0.1F) {
            level.explode(this, at.x, at.y, at.z, power, Level.ExplosionInteraction.MOB);
            Entity owner = getOwner();
            VoidImpact.scorch(level, at, power * 0.45F, EventHooks.canEntityGrief(level, owner != null ? owner : this));
        }
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) > 400 * 400) continue;
            level.sendParticles(p, ParticleTypes.EXPLOSION_EMITTER, true, at.x, at.y, at.z, 1 + m / 3, m * 0.4, m * 0.3, m * 0.4, 0);
            level.sendParticles(p, ParticleTypes.DRAGON_BREATH, true, at.x, at.y + 1, at.z, 40 * m, m * 0.6, m * 0.4, m * 0.6, 0.08);
            level.sendParticles(p, ParticleTypes.REVERSE_PORTAL, true, at.x, at.y + 1, at.z, 30 * m, m * 0.5, m * 0.5, m * 0.5, 0.3);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 4F + m, 0.5F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Multiplier", (byte) multiplier());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_MULT, tag.contains("Multiplier") ? tag.getByte("Multiplier") : (byte) 3);
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512 * 512;
    }
}
