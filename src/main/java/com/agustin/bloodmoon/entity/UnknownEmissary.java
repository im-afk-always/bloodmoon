package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.BloodMoonManager;
import com.agustin.bloodmoon.MoonType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.List;

/**
 * Emisario Desconocido: caballero no-muerto de ~12 bloques que desciende de la grieta en la Noche sin Luna.
 *
 * Movimientos:
 *  CLEAVE     Tajo del Vacío: levanta el espadón y lo descarga en un cono frontal.
 *  SWEEP      Siega Abismal: giro completo de 360° que barre todo alrededor.
 *  LEAP       Salto Sísmico: salta hacia su objetivo (también lo usa si se traba)...
 *  SLAM       ...y al caer libera una onda que lanza por el aire.
 *  SOUL_RIFT  Grieta de Almas: clava el espadón y abre tres líneas de colmillos hacia el objetivo.
 *  SUMMON     Llamado del Vacío (fase 2, una vez): rugido, oscuridad y cuatro escoltas wither.
 */
public class UnknownEmissary extends Monster {
    public static final String NAME_KEY = "entity.bloodmoon.unknown_emissary";

    public enum Attack {
        NONE(0), CLEAVE(40), SWEEP(36), LEAP(60), SLAM(24), SOUL_RIFT(50), SUMMON(60);
        public final int duration;
        Attack(int duration) { this.duration = duration; }
        static Attack byId(int id) {
            Attack[] v = values();
            return id >= 0 && id < v.length ? v[id] : NONE;
        }
    }

    private static final EntityDataAccessor<Byte> DATA_ATTACK =
            SynchedEntityData.defineId(UnknownEmissary.class, EntityDataSerializers.BYTE);
    private static final double BAR_RANGE = 96.0;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable(NAME_KEY).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    private int attackTick;
    private int cooldown = 40;
    private int stuckTicks;
    private float lockedYaw;
    private boolean summoned;
    private boolean boundToNight;
    /** Cliente: tick en el que empezó la animación del ataque actual. */
    public int clientAttackStart;

    public UnknownEmissary(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.ATTACK_DAMAGE, 20.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.STEP_HEIGHT, 2.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public Attack getAttack() {
        return Attack.byId(this.entityData.get(DATA_ATTACK));
    }

    private void setAttack(Attack attack) {
        this.entityData.set(DATA_ATTACK, (byte) attack.ordinal());
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

    // ------------------------------------------------------------------ IA

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        BossBars.update(this, bossEvent, BAR_RANGE);

        // Si lo trajo la noche, se retira cuando la noche termina
        if (boundToNight && BloodMoonManager.current() != MoonType.MOONLESS) {
            vanish();
            return;
        }

        breakLeavesIfBlocked();

        Attack attack = getAttack();
        if (attack != Attack.NONE) {
            this.getNavigation().stop();
            if (attack != Attack.LEAP) faceLocked();
            attackTick++;
            tickAttack(attack);
            if (getAttack() == attack && attackTick >= attack.duration) {
                setAttack(Attack.NONE);
                cooldown = isPhaseTwo() ? 15 + random.nextInt(15) : 30 + random.nextInt(20);
            }
            return;
        }

        LivingEntity target = getTarget();
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

        if (isPhaseTwo() && !summoned) {
            startAttack(Attack.SUMMON, target);
        } else if (dist <= 8) {
            int near = nearbyEnemies(8).size();
            startAttack(near >= 2 || random.nextFloat() < 0.35F ? Attack.SWEEP : Attack.CLEAVE, target);
        } else if (dist <= 22 && hasLineOfSight(target)) {
            startAttack(random.nextBoolean() ? Attack.SOUL_RIFT : Attack.LEAP, target);
        } else if (dist > 22 || stuckTicks > 40) {
            startAttack(Attack.LEAP, target);
        }
    }

    private void startAttack(Attack attack, LivingEntity target) {
        lockedYaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90F;
        stuckTicks = 0;
        setAttack(attack);
        switch (attack) {
            case CLEAVE, SOUL_RIFT -> playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 3F, 0.5F);
            case SWEEP -> playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 3F, 0.5F);
            case LEAP -> playSound(SoundEvents.RAVAGER_ROAR, 3F, 0.6F);
            case SUMMON -> playSound(SoundEvents.WARDEN_ROAR, 5F, 0.6F);
            default -> {}
        }
    }

    private void faceLocked() {
        setYRot(lockedYaw);
        yBodyRot = lockedYaw;
        yHeadRot = lockedYaw;
    }

    private void tickAttack(Attack attack) {
        LivingEntity target = getTarget();
        switch (attack) {
            case CLEAVE -> {
                if (attackTick == 22) {
                    Vec3 dir = Vec3.directionFromRotation(0F, lockedYaw);
                    hitArea(position(), 9.0, dir, 0.5, 26F, 1.6, 0.5);
                    Vec3 impact = position().add(dir.scale(6.5));
                    impactFx(impact, 1.5F, SoundEvents.ANVIL_LAND);
                    particles(ParticleTypes.SONIC_BOOM, impact.add(0, 1, 0), 1, 0, 0);
                }
            }
            case SWEEP -> {
                if (attackTick >= 16 && attackTick <= 24) {
                    float a = (attackTick - 16) / 8F * Mth.TWO_PI + lockedYaw * Mth.DEG_TO_RAD;
                    for (int r = 3; r <= 8; r += 2) {
                        particles(ParticleTypes.SWEEP_ATTACK,
                                position().add(-Mth.sin(a) * r, 3 + random.nextDouble(), Mth.cos(a) * r), 1, 0.2, 0);
                    }
                }
                if (attackTick == 20) {
                    hitArea(position(), 9.0, null, -1, 20F, 2.4, 0.4);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 4F, 0.4F);
                }
            }
            case LEAP -> {
                if (attackTick == 10 && target != null) {
                    Vec3 to = target.position().subtract(position());
                    double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
                    double speed = Math.min(3.2, horizontal / 10.4);
                    lockedYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F;
                    faceLocked();
                    setDeltaMovement(to.x / Math.max(horizontal, 0.01) * speed, 1.35, to.z / Math.max(horizontal, 0.01) * speed);
                    hasImpulse = true;
                    particles(ParticleTypes.EXPLOSION, position(), 6, 2, 0);
                } else if (attackTick < 10) {
                    faceLocked();
                }
                if (attackTick > 16 && onGround()) {
                    setAttack(Attack.SLAM);
                    slam();
                }
            }
            case SOUL_RIFT -> {
                if (attackTick == 20) {
                    impactFx(position().add(Vec3.directionFromRotation(0F, lockedYaw).scale(3)), 1F, SoundEvents.WITHER_SKELETON_STEP);
                    playSound(SoundEvents.EVOKER_CAST_SPELL, 4F, 0.5F);
                    for (int line = -1; line <= 1; line++) {
                        float yaw = (lockedYaw + 90F + line * 20F) * Mth.DEG_TO_RAD;
                        for (int i = 0; i < 18; i++) {
                            double d = 3.0 + i * 1.3;
                            double x = getX() + Math.cos(yaw) * d, z = getZ() + Math.sin(yaw) * d;
                            spawnFang(x, z, yaw, i);
                        }
                    }
                }
                if (attackTick > 20 && attackTick < 44) {
                    particles(ParticleTypes.SOUL_FIRE_FLAME, position().add(0, 0.5, 0), 6, 2.5, 0.05);
                }
            }
            case SUMMON -> {
                if (attackTick == 20) {
                    summoned = true;
                    if (level() instanceof ServerLevel sl) {
                        for (ServerPlayer p : sl.players()) {
                            if (p.distanceToSqr(this) < 48 * 48) {
                                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0));
                            }
                        }
                        for (int i = 0; i < 4; i++) {
                            double a = i * Math.PI / 2 + random.nextDouble() * 0.4;
                            BlockPos pos = BlockPos.containing(getX() + Math.cos(a) * 6, getY(), getZ() + Math.sin(a) * 6);
                            WitherSkeleton guard = EntityType.WITHER_SKELETON.create(sl);
                            if (guard == null) continue;
                            guard.moveTo(pos, random.nextFloat() * 360F, 0F);
                            guard.finalizeSpawn(sl, sl.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
                            if (target != null) guard.setTarget(target);
                            sl.addFreshEntity(guard);
                            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                                    40, 0.5, 1, 0.5, 0.05);
                        }
                    }
                }
                if (attackTick >= 20 && attackTick < 50) {
                    particles(ParticleTypes.SCULK_SOUL, position().add(0, 6, 0), 4, 3, 0.05);
                }
            }
            default -> {}
        }
    }

    private void slam() {
        hitArea(position(), 10.0, null, -1, 22F, 1.2, 1.1);
        impactFx(position(), 3F, SoundEvents.ANVIL_LAND);
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 4F, 0.5F);
        if (level() instanceof ServerLevel sl) {
            for (int ring = 2; ring <= 10; ring += 2) {
                for (int i = 0; i < ring * 4; i++) {
                    double a = i * Mth.TWO_PI / (ring * 4);
                    sl.sendParticles(ParticleTypes.CLOUD, getX() + Math.cos(a) * ring, getY() + 0.2, getZ() + Math.sin(a) * ring,
                            1, 0, 0.1, 0, 0.05);
                }
            }
        }
    }

    private void spawnFang(double x, double z, float yaw, int delay) {
        double minY = getY() - 6, maxY = getY() + 6;
        BlockPos pos = BlockPos.containing(x, maxY, z);
        double y = Double.NaN;
        for (int i = 0; i < 12; i++) {
            BlockPos below = pos.below();
            BlockState state = level().getBlockState(below);
            if (state.isFaceSturdy(level(), below, Direction.UP) && level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                y = pos.getY();
                break;
            }
            pos = below;
            if (pos.getY() < minY) break;
        }
        if (!Double.isNaN(y)) {
            level().addFreshEntity(new EvokerFangs(level(), x, y, z, yaw, delay, this));
        }
    }

    /** Daña a los enemigos en un radio; si dir != null, solo dentro del cono (cosHalf = coseno del semiángulo). */
    private void hitArea(Vec3 center, double radius, Vec3 dir, double cosHalf, float damage, double knock, double lift) {
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
            }
        }
    }

    private List<LivingEntity> nearbyEnemies(double radius) {
        AABB box = getBoundingBox().inflate(radius, 4, radius);
        return level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !(e instanceof WitherSkeleton) && !(e instanceof UnknownEmissary)
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
    }

    private void impactFx(Vec3 at, float size, SoundEvent sound) {
        playSound(sound, 4F, 0.5F);
        particles(ParticleTypes.EXPLOSION, at.add(0, 0.5, 0), (int) (4 * size), size, 0);
        particles(ParticleTypes.LARGE_SMOKE, at.add(0, 0.5, 0), (int) (12 * size), size * 1.5, 0.05);
    }

    private void particles(ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(type, at.x, at.y, at.z, count, spread, spread * 0.5, spread, speed);
        }
    }

    /** Ravager style: arrasa hojas que lo traban (respeta mobGriefing). */
    private void breakLeavesIfBlocked() {
        if (!this.horizontalCollision || !EventHooks.canEntityGrief(level(), this)) return;
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
        if (level().isClientSide && random.nextInt(3) == 0) {
            level().addParticle(random.nextBoolean() ? ParticleTypes.SOUL : ParticleTypes.SMOKE,
                    getRandomX(1.2), getY() + random.nextDouble() * getBbHeight(), getRandomZ(1.2), 0, 0.02, 0);
        }
    }

    // ------------------------------------------------------------------ varios

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(6, 3, 6); // el espadón sobresale mucho de la hitbox
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
        tag.putBoolean("Summoned", summoned);
        tag.putBoolean("BoundToNight", boundToNight);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        summoned = tag.getBoolean("Summoned");
        boundToNight = tag.getBoolean("BoundToNight");
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }

    /** Mob: el objetivo puede ser cualquier jugador; no ataca a sus escoltas. */
    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof WitherSkeleton) && super.canAttack(target);
    }
}
