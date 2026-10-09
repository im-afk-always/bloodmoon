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
 * Lágrima del Cielo: un meteoro negro enorme que lloran los ojos del cielo. Cae despacio en diagonal hacia un punto
 * marcado en el piso. Al tocarlo no pasa nada... se enciende una luz intensa que crece, colapsa de golpe en un
 * punto y estalla como una pequeña supernova, dejando un cráter.
 */
public class AbyssTear extends Entity {
    public static final float RADIUS = 8F;
    /** Ticks desde que toca el piso: la luz crece, colapsa y estalla. */
    public static final int LIGHT = 34, COLLAPSE = 5, BOOM = LIGHT + COLLAPSE;
    private static final EntityDataAccessor<Vector3f> DATA_TARGET = SynchedEntityData.defineId(AbyssTear.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_FLIGHT = SynchedEntityData.defineId(AbyssTear.class, EntityDataSerializers.INT);

    private int ownerId = -1;
    /** El Rey del Vacío las llama sin cráter: no destroza su propio coliseo. */
    private boolean crater = true;
    private Vec3 start = Vec3.ZERO;

    public AbyssTear(EntityType<? extends AbyssTear> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static void spawn(ServerLevel level, Entity owner, Vec3 target, int flight) {
        spawn(level, owner, target, flight, true);
    }

    public static void spawn(ServerLevel level, Entity owner, Vec3 target, int flight, boolean crater) {
        AbyssTear t = ModEntities.ABYSS_TEAR.get().create(level);
        if (t == null) return;
        t.crater = crater;
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
            if (tickCount < flight()) {
                for (int i = 0; i < 6; i++) {
                    level().addAlwaysVisibleParticle(i < 2 ? ParticleTypes.END_ROD : ParticleTypes.DRAGON_BREATH, true,
                            getX() + random.nextGaussian() * 1.4, getY() + random.nextGaussian() * 1.4, getZ() + random.nextGaussian() * 1.4, 0, 0, 0);
                }
            } else if (tickCount < flight() + LIGHT) {
                Vec3 t = target();
                for (int i = 0; i < 3; i++) {
                    Vec3 d = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()), random.nextGaussian()).normalize().scale(RADIUS);
                    level().addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, t.x + d.x, t.y + d.y, t.z + d.z, -d.x * 0.08, -d.y * 0.08, -d.z * 0.08);
                }
            }
            return;
        }
        if (!(level() instanceof ServerLevel sl)) return;
        if (start == Vec3.ZERO) start = position();
        int f = flight();
        double k = Math.min(1.0, tickCount / (double) f);
        Vec3 p = start.lerp(target(), k * (0.4 + 0.6 * k));
        setPos(p.x, p.y, p.z);
        Vec3 t = target();
        if (tickCount == f) {                              // toca el piso... y no pasa nada
            sl.playSound(null, t.x, t.y, t.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2F, 0.3F);
            sl.sendParticles(ParticleTypes.END_ROD, t.x, t.y + 1, t.z, 24, 1.2, 0.4, 1.2, 0.05);
        }
        if (tickCount == f + 4) sl.playSound(null, t.x, t.y, t.z, com.agustin.bloodmoon.registry.ModSounds.SUPERNOVA_CHARGE.get(), SoundSource.HOSTILE, 5F, 1.4F);
        if (tickCount == f + BOOM) impact(sl);
    }

    private void impact(ServerLevel sl) {
        Vec3 t = target();
        if (isRemoved()) return;
        Entity owner = sl.getEntity(ownerId);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(t, t).inflate(RADIUS, 4, RADIUS),
                e -> e.isAlive() && !(e instanceof UnboundObserver))) {
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
            if (owner != null && com.agustin.bloodmoon.invasion.VoidAllies.isVoid(owner) && com.agustin.bloodmoon.invasion.VoidAllies.isVoid(e)) continue;
            double dx = e.getX() - t.x, dz = e.getZ() - t.z, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            if (d > RADIUS) continue;
            e.hurt(damageSources().indirectMagic(this, owner != null ? owner : this), (float) (26 * (1 - 0.6 * d / RADIUS)));
            e.addEffect(new MobEffectInstance(ModEffects.ASTRAL_BURN, 80, 0));
            e.setDeltaMovement(dx / d * 1.6, 0.8, dz / d * 1.6);
            e.hurtMarked = true;
        }
        if (crater && sl.dimension() == com.agustin.bloodmoon.registry.ModDimensions.BEYOND
                || crater && sl.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) VoidImpact.meteorCrater(sl, t, 5.5F);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, t.x, t.y + 1, t.z, 4, 2, 1, 2, 0);
        sl.sendParticles(ParticleTypes.END_ROD, t.x, t.y + 1, t.z, 120, 0.5, 0.5, 0.5, 1.2);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, t.x, t.y + 0.5, t.z, 120, 3, 0.5, 3, 0.5);
        sl.playSound(null, t.x, t.y, t.z, com.agustin.bloodmoon.registry.ModSounds.SUPERNOVA_BLAST.get(), SoundSource.HOSTILE, 8F, 1.5F + sl.random.nextFloat() * 0.3F);
        sl.playSound(null, t.x, t.y, t.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 6F, 0.5F);
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
