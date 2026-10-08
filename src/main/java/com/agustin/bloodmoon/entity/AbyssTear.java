package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.registry.ModEffects;
import com.agustin.bloodmoon.world.VoidImpact;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Lágrima del Cielo: un meteoro negro que lloran los ojos del cielo. Cae en diagonal hacia un punto marcado en
 * el piso (círculo que se cierra) y estalla dejando un cráter.
 */
public class AbyssTear extends Entity {
    public static final float RADIUS = 4.5F;
    private static final EntityDataAccessor<Vector3f> DATA_TARGET = SynchedEntityData.defineId(AbyssTear.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_FLIGHT = SynchedEntityData.defineId(AbyssTear.class, EntityDataSerializers.INT);

    private int ownerId = -1;
    private Vec3 start = Vec3.ZERO;

    public AbyssTear(EntityType<? extends AbyssTear> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static void spawn(ServerLevel level, Entity owner, Vec3 target, int flight) {
        AbyssTear t = ModEntities.ABYSS_TEAR.get().create(level);
        if (t == null) return;
        double a = level.random.nextDouble() * Math.PI * 2;
        Vec3 s = target.add(Math.cos(a) * 45, 95, Math.sin(a) * 45);
        t.ownerId = owner.getId();
        t.start = s;
        t.entityData.set(DATA_TARGET, new Vector3f((float) target.x, (float) target.y, (float) target.z));
        t.entityData.set(DATA_FLIGHT, flight);
        t.moveTo(s.x, s.y, s.z, 0F, 0F);
        level.addFreshEntity(t);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TARGET, new Vector3f());
        builder.define(DATA_FLIGHT, 50);
    }

    public Vec3 target() {
        Vector3f v = entityData.get(DATA_TARGET);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public int flight() {
        return entityData.get(DATA_FLIGHT);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            for (int i = 0; i < 3; i++) {
                level().addAlwaysVisibleParticle(i == 0 ? ParticleTypes.DRAGON_BREATH : ParticleTypes.SQUID_INK, true,
                        getX() + random.nextGaussian() * 0.6, getY() + random.nextGaussian() * 0.6, getZ() + random.nextGaussian() * 0.6, 0, 0, 0);
            }
            return;
        }
        if (!(level() instanceof ServerLevel sl)) return;
        if (start == Vec3.ZERO) start = position();
        int f = flight();
        double k = Math.min(1.0, tickCount / (double) f);
        Vec3 p = start.lerp(target(), k * k);
        setPos(p.x, p.y, p.z);
        if (tickCount >= f) impact(sl);
    }

    private void impact(ServerLevel sl) {
        Vec3 t = target();
        Entity owner = sl.getEntity(ownerId);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(t, t).inflate(RADIUS, 4, RADIUS),
                e -> e.isAlive() && !(e instanceof UnboundObserver))) {
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
            double dx = e.getX() - t.x, dz = e.getZ() - t.z, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            if (d > RADIUS) continue;
            e.hurt(damageSources().indirectMagic(this, owner != null ? owner : this), 14F);
            e.addEffect(new MobEffectInstance(ModEffects.ASTRAL_BURN, 60, 0));
            e.setDeltaMovement(dx / d * 1.1, 0.6, dz / d * 1.1);
            e.hurtMarked = true;
        }
        VoidImpact.meteorCrater(sl, t, 3.5F);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, t.x, t.y + 0.5, t.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, t.x, t.y + 0.5, t.z, 60, 1.5, 0.5, 1.5, 0.3);
        sl.playSound(null, t.x, t.y, t.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4F, 0.6F + sl.random.nextFloat() * 0.3F);
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 300 * 300;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
