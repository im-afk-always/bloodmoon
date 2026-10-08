package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.registry.ModSounds;
import com.agustin.bloodmoon.world.BeyondHoles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Ojo Colosal: se abre en el cielo sobre un jugador. Un círculo en el piso lo persigue (más lento que correr),
 * se fija, y un rayo de 20 bloques de radio cae desde el ojo: mata casi todo lo que haya debajo y perfora la
 * llanura hasta el vacío, dejando un agujero gigantesco.
 */
public class ColossalEye extends Entity {
    public static final int OPEN = 40, LOCK = 100, FIRE = 140, FIRE_LEN = 50, END = FIRE + FIRE_LEN + 40;
    public static final float HEIGHT = 140F, BEAM_R = 20F, EYE_R = 24F;
    private static final double CHASE = 0.2;

    private static final EntityDataAccessor<Vector3f> DATA_GROUND = SynchedEntityData.defineId(ColossalEye.class, EntityDataSerializers.VECTOR3);

    private int ownerId = -1, targetId = -1;
    private Vec3 ground = Vec3.ZERO;

    public ColossalEye(EntityType<? extends ColossalEye> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static ColossalEye summon(ServerLevel level, UnboundObserver owner, Player target) {
        ColossalEye eye = ModEntities.COLOSSAL_EYE.get().create(level);
        if (eye == null) return null;
        eye.ownerId = owner.getId();
        eye.targetId = target.getId();
        eye.ground = new Vec3(target.getX(), owner.getHome().y, target.getZ());
        eye.syncGround();
        eye.moveTo(eye.ground.x, eye.ground.y + HEIGHT, eye.ground.z, 0F, 0F);
        level.addFreshEntity(eye);
        return eye;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_GROUND, new Vector3f());
    }

    public Vec3 groundPoint() {
        Vector3f v = entityData.get(DATA_GROUND);
        return new Vec3(v.x(), v.y(), v.z());
    }

    private void syncGround() {
        entityData.set(DATA_GROUND, new Vector3f((float) ground.x, (float) ground.y, (float) ground.z));
    }

    public boolean isFiring() {
        return tickCount >= FIRE && tickCount < FIRE + FIRE_LEN;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) {
            if (isFiring()) {
                Vec3 g = groundPoint();
                for (int i = 0; i < 12; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = BEAM_R * Math.sqrt(random.nextDouble());
                    level().addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, g.x + Math.cos(a) * r, g.y + random.nextDouble() * 6,
                            g.z + Math.sin(a) * r, 0, 0.6 + random.nextDouble(), 0);
                }
            }
            return;
        }
        int age = tickCount;
        if (age < LOCK && sl.getEntity(targetId) instanceof Player p && p.isAlive()) {
            Vec3 want = new Vec3(p.getX(), ground.y, p.getZ());
            Vec3 d = want.subtract(ground);
            double len = d.length();
            if (len > 1e-3) ground = ground.add(d.scale(Math.min(CHASE, len) / len));
            syncGround();
        }
        setPos(ground.x, ground.y + HEIGHT, ground.z);

        if (age == 1) sound(ModSounds.EYE_AWAKEN.get(), 0.5F);
        if (age == LOCK) {
            sound(ModSounds.EYE_CHARGE.get(), 0.4F);
            sound(SoundEvents.BEACON_POWER_SELECT, 0.5F);
        }
        if (age == FIRE) {
            sound(ModSounds.SUPERNOVA_BLAST.get(), 0.7F);
            sound(ModSounds.EYE_BEAM.get(), 0.5F);
            BeyondHoles.start(sl, BlockPos.containing(ground.x, ground.y - 1, ground.z), BEAM_R);
        }
        if (isFiring()) {
            if ((age - FIRE) % 25 == 0) sound(ModSounds.EYE_BEAM.get(), 0.45F);
            if ((age - FIRE) % 5 == 0) burn(sl);
            if (age % 2 == 0) sl.sendParticles(ParticleTypes.EXPLOSION, ground.x, ground.y + 1, ground.z, 6, BEAM_R * 0.5, 1, BEAM_R * 0.5, 0);
        }
        if (age == FIRE + FIRE_LEN && sl.getEntity(ownerId) instanceof UnboundObserver owner) owner.onColossalFired();
        if (age >= END) discard();
    }

    private void burn(ServerLevel sl) {
        Entity owner = sl.getEntity(ownerId);
        AABB box = new AABB(ground.x - BEAM_R, ground.y - 80, ground.z - BEAM_R, ground.x + BEAM_R, ground.y + HEIGHT, ground.z + BEAM_R);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !(e instanceof UnboundObserver))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            double dx = e.getX() - ground.x, dz = e.getZ() - ground.z;
            if (dx * dx + dz * dz > (BEAM_R + 0.5) * (BEAM_R + 0.5)) continue;
            e.hurt(damageSources().indirectMagic(this, owner != null ? owner : this), 26F);
            e.setDeltaMovement(e.getDeltaMovement().multiply(0.5, 1, 0.5).add(0, -0.4, 0));
            e.hurtMarked = true;
        }
    }

    private void sound(net.minecraft.sounds.SoundEvent s, float pitch) {
        level().playSound(null, ground.x, ground.y + 20, ground.z, s, SoundSource.HOSTILE, 24F, pitch);
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
        return distance < 400 * 400;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
