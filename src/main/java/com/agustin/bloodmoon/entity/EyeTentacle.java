package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.registry.ModEffects;
import com.agustin.bloodmoon.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tentáculo del Abismo: brota del piso bajo los pies de un jugador tras un aviso (grietas y un círculo púrpura),
 * lanza por el aire a quien siga encima y luego azota lo que tenga cerca hasta que lo cortan o se retira.
 */
public class EyeTentacle extends Monster {
    public static final int EMERGE = 24, ERUPT = 10, LIFE = 180, RETRACT = 16, SLAM_WINDUP = 12, SLAM_LEN = 22;
    public static final float LENGTH = 9F;
    private static final EntityDataAccessor<Integer> DATA_SLAM = SynchedEntityData.defineId(EyeTentacle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SLAM_YAW = SynchedEntityData.defineId(EyeTentacle.class, EntityDataSerializers.FLOAT);

    private int lastSlam = -100;
    private Vec3 slamPoint;

    public EyeTentacle(EntityType<? extends EyeTentacle> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.ATTACK_DAMAGE, 9.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SLAM, -1);
        builder.define(DATA_SLAM_YAW, 0F);
    }

    public int slamStart() {
        return entityData.get(DATA_SLAM);
    }

    public float slamYaw() {
        return entityData.get(DATA_SLAM_YAW);
    }

    /** 0 (bajo tierra) .. 1 (extendido), según la edad. */
    public float extension(float partialTick) {
        float a = tickCount + partialTick;
        if (a < EMERGE) return 0F;
        if (a < EMERGE + ERUPT) {
            float k = (a - EMERGE) / ERUPT;
            return 1F - (1F - k) * (1F - k);
        }
        if (a > LIFE) return Mth.clamp(1F - (a - LIFE) / RETRACT, 0F, 1F);
        return 1F;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            if (tickCount < EMERGE) {
                float k = tickCount / (float) EMERGE;
                for (int i = 0; i < 3; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = 2.2 * Math.sqrt(random.nextDouble());
                    level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + Math.cos(a) * r, getY() + 0.1, getZ() + Math.sin(a) * r, 0, 0.05 + 0.1 * k, 0);
                }
                BlockState ground = level().getBlockState(blockPosition().below());
                if (!ground.isAir() && random.nextFloat() < 0.6F) {
                    level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX() + random.nextGaussian() * 0.8, getY() + 0.1,
                            getZ() + random.nextGaussian() * 0.8, 0, 0.1, 0);
                }
            }
            return;
        }
        if (!(level() instanceof ServerLevel sl) || isDeadOrDying()) return;
        if (tickCount == 1) sl.playSound(null, blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, getSoundSource(), 1.2F, 0.5F);
        if (tickCount == EMERGE) erupt(sl);
        if (tickCount > EMERGE + ERUPT && tickCount < LIFE) tickSlam(sl);
        if (tickCount >= LIFE + RETRACT) discard();
    }

    private void erupt(ServerLevel sl) {
        sl.playSound(null, blockPosition(), ModSounds.EYE_TENTACLE.get(), getSoundSource(), 2F, 0.8F + random.nextFloat() * 0.3F);
        BlockState ground = sl.getBlockState(blockPosition().below());
        if (!ground.isAir()) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.3, getZ(), 60, 1, 0.3, 1, 0.3);
        sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 30, 0.6, 1.2, 0.6, 0.05);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(2.4, 3, 2.4), this::victim)) {
            e.hurt(damageSources().mobAttack(this), 12F);
            e.addEffect(new MobEffectInstance(ModEffects.ASTRAL_BURN, 60, 0), this);
            e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 1.15, e.getDeltaMovement().z * 0.3);
            e.hurtMarked = true;
        }
    }

    private boolean victim(LivingEntity e) {
        if (e == this || e instanceof VoidEye || e instanceof EyeTentacle || e instanceof VoidSkeleton) return false;
        return !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private void tickSlam(ServerLevel sl) {
        int start = slamStart();
        if (start < 0) {
            if (tickCount - lastSlam < 32) return;
            Player p = sl.getNearestPlayer(getX(), getY(), getZ(), 7.5, e -> e instanceof Player pl && !pl.isCreative() && !pl.isSpectator());
            if (p == null) return;
            double dx = p.getX() - getX(), dz = p.getZ() - getZ();
            double d = Math.max(1.5, Math.min(6.5, Math.sqrt(dx * dx + dz * dz)));
            double len = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
            slamPoint = new Vec3(getX() + dx / len * d, getY(), getZ() + dz / len * d);
            entityData.set(DATA_SLAM_YAW, (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG));
            entityData.set(DATA_SLAM, tickCount);
            lastSlam = tickCount;
            sl.playSound(null, blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, getSoundSource(), 0.6F, 0.4F);
            return;
        }
        int t = tickCount - start;
        if (t == SLAM_WINDUP && slamPoint != null) {
            sl.playSound(null, BlockPos.containing(slamPoint), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, getSoundSource(), 1.4F, 0.5F);
            BlockState ground = sl.getBlockState(BlockPos.containing(slamPoint).below());
            if (!ground.isAir()) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), slamPoint.x, slamPoint.y + 0.2, slamPoint.z, 40, 1.2, 0.2, 1.2, 0.2);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(slamPoint, slamPoint).inflate(2.6, 2.5, 2.6), this::victim)) {
                e.hurt(damageSources().mobAttack(this), 9F);
                Vec3 push = e.position().subtract(getX(), e.getY(), getZ()).normalize();
                e.setDeltaMovement(push.x * 1.1, 0.45, push.z * 1.1);
                e.hurtMarked = true;
            }
        }
        if (t >= SLAM_LEN) entityData.set(DATA_SLAM, -1);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (tickCount < EMERGE) return false;
        Entity attacker = source.getEntity();
        if (attacker instanceof VoidEye || attacker instanceof VoidSkeleton) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {}

    @Override
    public boolean isPickable() {
        return tickCount >= EMERGE && super.isPickable();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SLIME_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.4F;
    }
}
