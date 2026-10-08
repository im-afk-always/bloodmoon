package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.registry.ModEffects;
import com.agustin.bloodmoon.registry.ModSounds;
import com.agustin.bloodmoon.world.BeyondHoles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tentáculo Titánico: revienta la llanura, se alza 70 bloques, apunta (una franja en el piso marca la línea),
 * se desploma como un látigo a lo largo de esa franja y abre una zanja hasta el vacío. Después se hunde.
 */
public class TitanTentacle extends Entity {
    public static final int EMERGE = 34, LOCK = 72, SLAM = 86, LIE = 120, END = 150;
    public static final float LENGTH = 70F, RADIUS = 4.6F, HALF_WIDTH = 4.8F;

    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(TitanTentacle.class, EntityDataSerializers.FLOAT);

    private int ownerId = -1, targetId = -1;

    public TitanTentacle(EntityType<? extends TitanTentacle> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Brota a ~42 bloques del objetivo, en dirección opuesta al jefe, apuntando hacia él. */
    public static TitanTentacle summon(ServerLevel level, UnboundObserver owner, Player target) {
        TitanTentacle t = ModEntities.TITAN_TENTACLE.get().create(level);
        if (t == null) return null;
        Vec3 h = owner.getHome();
        double a = level.random.nextDouble() * Math.PI * 2;
        double bx = target.getX() + Math.cos(a) * 42, bz = target.getZ() + Math.sin(a) * 42;
        t.ownerId = owner.getId();
        t.targetId = target.getId();
        t.moveTo(bx, h.y, bz, 0F, 0F);
        t.aim(target.position());
        level.addFreshEntity(t);
        return t;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_YAW, 0F);
    }

    /** Dirección (grados, matemática: atan2(dz, dx)) en la que cae. */
    public float slamYaw() {
        return entityData.get(DATA_YAW);
    }

    private void aim(Vec3 p) {
        entityData.set(DATA_YAW, (float) Math.toDegrees(Math.atan2(p.z - getZ(), p.x - getX())));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) {
            if (tickCount < EMERGE) {
                BlockState g = level().getBlockState(blockPosition().below());
                for (int i = 0; i < 8 && !g.isAir(); i++) {
                    level().addAlwaysVisibleParticle(new BlockParticleOption(ParticleTypes.BLOCK, g), true,
                            getX() + random.nextGaussian() * 4, getY() + 0.5, getZ() + random.nextGaussian() * 4, 0, 0.6, 0);
                }
            }
            return;
        }
        int t = tickCount;
        if (t == 1) {
            sl.playSound(null, getX(), getY(), getZ(), ModSounds.EYE_TENTACLE.get(), SoundSource.HOSTILE, 20F, 0.3F);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 10F, 0.4F);
            BeyondHoles.start(sl, BlockPos.containing(getX(), getY() - 1, getZ()), RADIUS + 1.5F);
        }
        if (t < LOCK && sl.getEntity(targetId) instanceof Player p && p.isAlive()) {
            float want = (float) Math.toDegrees(Math.atan2(p.getZ() - getZ(), p.getX() - getX()));
            float cur = slamYaw();
            entityData.set(DATA_YAW, cur + Mth.clamp(Mth.wrapDegrees(want - cur), -1.6F, 1.6F));
        }
        if (t == LOCK) sl.playSound(null, getX(), getY() + 40, getZ(), ModSounds.EYE_SCREAM.get(), SoundSource.HOSTILE, 16F, 0.35F);
        if (t == SLAM) slam(sl);
        if (t == LIE + 6) sl.playSound(null, getX(), getY(), getZ(), ModSounds.EYE_TENTACLE.get(), SoundSource.HOSTILE, 14F, 0.4F);
        if (t >= END) discard();
    }

    private void slam(ServerLevel sl) {
        double a = Math.toRadians(slamYaw());
        double dx = Math.cos(a), dz = Math.sin(a);
        Entity owner = sl.getEntity(ownerId);
        AABB box = new AABB(getX(), getY() - 4, getZ(), getX() + dx * LENGTH, getY() + 8, getZ() + dz * LENGTH).inflate(HALF_WIDTH + 1, 0, HALF_WIDTH + 1);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !(e instanceof UnboundObserver))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            double rx = e.getX() - getX(), rz = e.getZ() - getZ();
            double along = rx * dx + rz * dz, side = -rx * dz + rz * dx;
            if (along < 0 || along > LENGTH || Math.abs(side) > HALF_WIDTH + e.getBbWidth() / 2) continue;
            e.hurt(damageSources().indirectMagic(this, owner != null ? owner : this), 30F);
            e.addEffect(new MobEffectInstance(ModEffects.ASTRAL_BURN, 80, 0));
            double s = Math.signum(side == 0 ? 1 : side);
            e.setDeltaMovement(-dz * s * 1.6, 0.9, dx * s * 1.6);
            e.hurtMarked = true;
        }
        // zanja: una hilera de pozos a lo largo del golpe
        for (int i = 8; i <= LENGTH; i += 5) {
            BeyondHoles.start(sl, BlockPos.containing(getX() + dx * i, getY() - 1, getZ() + dz * i), 3.6F + sl.random.nextFloat() * 1.2F);
        }
        for (int i = 0; i <= LENGTH; i += 4) {
            double x = getX() + dx * i, z = getZ() + dz * i;
            sl.sendParticles(ParticleTypes.EXPLOSION, x, getY() + 1, z, 2, 2, 0.5, 2, 0);
            sl.sendParticles(ParticleTypes.SQUID_INK, x, getY() + 1, z, 10, 2, 1, 2, 0.08);
        }
        sl.playSound(null, getX() + dx * 35, getY(), getZ() + dz * 35, ModSounds.SUPERNOVA_BLAST.get(), SoundSource.HOSTILE, 20F, 0.8F);
        sl.playSound(null, getX() + dx * 35, getY(), getZ() + dz * 35, ModSounds.EYE_WAVE.get(), SoundSource.HOSTILE, 20F, 0.6F);
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
