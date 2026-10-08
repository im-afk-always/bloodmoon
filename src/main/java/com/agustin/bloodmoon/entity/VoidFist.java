package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.registry.ModDimensions;
import com.agustin.bloodmoon.registry.ModEffects;
import com.agustin.bloodmoon.registry.ModSounds;
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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Puño del Vacío: se abre una grieta junto al Ojo y de ella brota un brazo de energía oscura que se estira hasta
 * quedar suspendido sobre un jugador (un sello en el piso lo sigue), se fija y desciende como un puño gigante:
 * onda de choque y cráter.
 */
public class VoidFist extends Entity {
    public static final int OPEN = 16, REACH = 40, LOCK = 46, SLAM = 54, HOLD = 72, END = 90;
    public static final float RADIUS = 7F, HOVER = 16F;
    private static final double CHASE = 0.32;

    private static final EntityDataAccessor<Vector3f> DATA_ANCHOR = SynchedEntityData.defineId(VoidFist.class, EntityDataSerializers.VECTOR3);

    private int ownerId = -1, targetId = -1;
    private Vec3 offset = Vec3.ZERO;

    public VoidFist(EntityType<? extends VoidFist> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** side: 1 a la derecha del Ojo, -1 a la izquierda. */
    public static VoidFist summon(ServerLevel level, VoidEye owner, Player target, int side) {
        VoidFist f = ModEntities.VOID_FIST.get().create(level);
        if (f == null) return null;
        float R = owner.eyeRadius();
        Vec3 look = VoidEye.dirFrom(owner.lookYaw(), 0F);
        Vec3 right = new Vec3(-look.z, 0, look.x);
        f.ownerId = owner.getId();
        f.targetId = target.getId();
        f.offset = right.scale(side * R * 1.45).add(0, R * 0.55, 0).add(look.scale(-R * 0.3));
        f.syncAnchor(owner.center().add(f.offset));
        f.moveTo(target.getX(), owner.getHome().y, target.getZ(), 0F, 0F);
        level.addFreshEntity(f);
        return f;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ANCHOR, new Vector3f());
    }

    public Vec3 anchor() {
        Vector3f v = entityData.get(DATA_ANCHOR);
        return new Vec3(v.x(), v.y(), v.z());
    }

    private void syncAnchor(Vec3 a) {
        entityData.set(DATA_ANCHOR, new Vector3f((float) a.x, (float) a.y, (float) a.z));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) {
            Vec3 a = anchor();
            if (tickCount < HOLD) {
                for (int i = 0; i < 3; i++) {
                    level().addAlwaysVisibleParticle(ParticleTypes.REVERSE_PORTAL, true, a.x + random.nextGaussian() * 1.5,
                            a.y + random.nextGaussian() * 2.5, a.z + random.nextGaussian() * 1.5, 0, 0, 0);
                }
            }
            return;
        }
        int t = tickCount;
        Entity owner = sl.getEntity(ownerId);
        if (owner instanceof VoidEye eye && eye.isAlive()) syncAnchor(eye.center().add(offset));
        if (t < LOCK && sl.getEntity(targetId) instanceof Player p && p.isAlive()) {
            Vec3 cur = position(), want = new Vec3(p.getX(), getY(), p.getZ());
            Vec3 d = want.subtract(cur);
            double len = d.length();
            if (len > 1e-3) {
                Vec3 n = cur.add(d.scale(Math.min(CHASE, len) / len));
                setPos(n.x, n.y, n.z);
            }
        }
        if (t == 1) sl.playSound(null, anchor().x, anchor().y, anchor().z, ModSounds.EYE_PULSE.get(), SoundSource.HOSTILE, 10F, 1.4F);
        if (t == LOCK) sl.playSound(null, getX(), getY() + HOVER, getZ(), ModSounds.EYE_CHARGE.get(), SoundSource.HOSTILE, 10F, 0.6F);
        if (t == SLAM) impact(sl, owner);
        if (t >= END) discard();
    }

    private void impact(ServerLevel sl, Entity owner) {
        Vec3 p = position();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(RADIUS, 5, RADIUS),
                e -> e.isAlive() && !(e instanceof VoidEye))) {
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
            double dx = e.getX() - p.x, dz = e.getZ() - p.z, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            if (d > RADIUS) continue;
            float k = (float) (1 - 0.5 * d / RADIUS);
            e.hurt(damageSources().indirectMagic(this, owner != null ? owner : this), 22F * k);
            e.addEffect(new MobEffectInstance(ModEffects.ASTRAL_BURN, 80, 0));
            e.setDeltaMovement(dx / d * 1.9, 0.8, dz / d * 1.9);
            e.hurtMarked = true;
        }
        if (sl.dimension() == ModDimensions.BEYOND || sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            VoidImpact.meteorCrater(sl, p, 5.5F);
        }
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 0.5, p.z, 3, 2, 0.3, 2, 0);
        sl.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y + 1, p.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y + 0.5, p.z, 80, 4, 0.4, 4, 0.12);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 0.5, p.z, 120, 5, 0.5, 5, 0.4);
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 10F, 0.45F);
        sl.playSound(null, p.x, p.y, p.z, ModSounds.EYE_WAVE.get(), SoundSource.HOSTILE, 12F, 0.8F);
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
