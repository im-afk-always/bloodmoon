package com.agustin.bloodmoon.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Emisario Desconocido: caballero no-muerto de ~12 bloques con espadón rúnico.
 *
 *  CLEAVE    Tajo del Vacío: espadón desde lo alto en un cono frontal.
 *  SWEEP     Siega Abismal: giro de 360°.
 *  LEAP/SLAM Salto Sísmico: salta y al caer lanza todo por el aire.
 *  SOUL_RIFT Grieta de Almas: tres líneas de colmillos hacia el objetivo.
 *  SUMMON    Llamado del Vacío (fase 2, una vez): oscuridad y cuatro escoltas wither.
 *  COLLAPSE  Colapso del Abismo (especial): clava el espadón; anillos de colmillos se expanden, el abismo
 *            atrae a todos hacia él durante 2,5 s y luego estalla una columna de luz que lanza por el aire
 *            y prende Quemadura astral.
 */
public class UnknownEmissary extends VoidKnight {
    public static final String NAME_KEY = "entity.bloodmoon.unknown_emissary";

    public static final int CLEAVE = 1, SWEEP = 2, LEAP = 3, SLAM = 4, SOUL_RIFT = 5, SUMMON = 6, COLLAPSE = 7;
    private static final int[] DURATIONS = {0, 40, 36, 60, 24, 50, 60, 96};

    private boolean summoned;
    private int collapseCooldown = 400;

    public UnknownEmissary(EntityType<? extends Monster> type, Level level) {
        super(type, level, Component.translatable(NAME_KEY).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
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
    protected int duration(int attack) {
        return attack > 0 && attack < DURATIONS.length ? DURATIONS[attack] : 0;
    }

    @Override
    protected boolean isFreeRotation(int attack) {
        return attack == LEAP;
    }

    @Override
    protected void customServerAiStep() {
        if (collapseCooldown > 0) collapseCooldown--;
        super.customServerAiStep();
    }

    @Override
    protected int chooseAttack(LivingEntity target, double dist, boolean stuck) {
        if (isPhaseTwo() && !summoned) return SUMMON;
        if (collapseCooldown <= 0 && dist <= 18) return COLLAPSE;
        if (dist <= 8) {
            return nearbyEnemies(8).size() >= 2 || random.nextFloat() < 0.35F ? SWEEP : CLEAVE;
        }
        if (dist <= 22 && hasLineOfSight(target)) return random.nextBoolean() ? SOUL_RIFT : LEAP;
        if (dist > 22 || stuck) return LEAP;
        return 0;
    }

    @Override
    protected void onAttackStart(int attack) {
        switch (attack) {
            case CLEAVE, SOUL_RIFT -> playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 3F, 0.5F);
            case SWEEP -> playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 3F, 0.5F);
            case SUMMON -> playSound(SoundEvents.WARDEN_ROAR, 5F, 0.6F);
            case COLLAPSE -> {
                playSound(SoundEvents.WARDEN_ROAR, 5F, 0.4F);
                collapseCooldown = isPhaseTwo() ? 500 : 800;
            }
            default -> {}
        }
    }

    @Override
    protected void tickAttack(int attack, LivingEntity target) {
        switch (attack) {
            case CLEAVE -> {
                if (attackTick == 22) {
                    Vec3 dir = forward();
                    hitArea(position(), 9.0, dir, 0.5, 26F, 1.6, 0.5, 0);
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
                    hitArea(position(), 9.0, null, -1, 20F, 2.4, 0.4, 0);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 4F, 0.4F);
                }
            }
            case LEAP -> {
                if (attackTick == 10 && target != null) leapAt(target);
                else if (attackTick < 10) faceLocked();
                if (attackTick > 16 && onGround()) {
                    setAttack(SLAM);
                    slam();
                }
            }
            case SOUL_RIFT -> {
                if (attackTick == 20) {
                    impactFx(position().add(forward().scale(3)), 1F, SoundEvents.WITHER_SKELETON_STEP);
                    playSound(SoundEvents.EVOKER_CAST_SPELL, 4F, 0.5F);
                    for (int line = -1; line <= 1; line++) {
                        float yaw = (lockedYaw + 90F + line * 20F) * Mth.DEG_TO_RAD;
                        for (int i = 0; i < 18; i++) {
                            double d = 3.0 + i * 1.3;
                            spawnFang(getX() + Math.cos(yaw) * d, getZ() + Math.sin(yaw) * d, yaw, i);
                        }
                    }
                }
                if (attackTick > 20 && attackTick < 44) {
                    particles(ParticleTypes.SOUL_FIRE_FLAME, position().add(0, 0.5, 0), 6, 2.5, 0.05);
                }
            }
            case SUMMON -> tickSummon(target);
            case COLLAPSE -> tickCollapse();
            default -> {}
        }
    }

    private void slam() {
        hitArea(position(), 10.0, null, -1, 22F, 1.2, 1.1, 0);
        impactFx(position(), 3F, SoundEvents.ANVIL_LAND);
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 4F, 0.5F);
        ring(ParticleTypes.CLOUD, 10, 0.2);
    }

    private void tickSummon(LivingEntity target) {
        if (attackTick == 20 && level() instanceof ServerLevel sl) {
            summoned = true;
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(this) < 48 * 48) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0));
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
        if (attackTick >= 20 && attackTick < 50) particles(ParticleTypes.SCULK_SOUL, position().add(0, 6, 0), 4, 3, 0.05);
    }

    /** Colapso del Abismo. */
    private void tickCollapse() {
        if (!(level() instanceof ServerLevel sl)) return;
        int t = attackTick;
        if (t == 20) {
            impactFx(position(), 2F, SoundEvents.ANVIL_LAND);
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 6F, 0.4F);
        }
        // anillos de colmillos que se expanden desde la espada
        if (t >= 20 && t <= 40 && (t - 20) % 5 == 0) {
            int ring = (t - 20) / 5;
            double r = 4 + ring * 3.5;
            int n = (int) (r * 2.2);
            for (int i = 0; i < n; i++) {
                double a = i * Mth.TWO_PI / n;
                spawnFang(getX() + Math.cos(a) * r, getZ() + Math.sin(a) * r, (float) a, 0);
            }
        }
        // el abismo atrae
        if (t > 22 && t < 72) {
            for (LivingEntity e : nearbyEnemies(26)) {
                Vec3 pull = position().subtract(e.position());
                double d = pull.length();
                if (d < 2.5) continue;
                Vec3 v = pull.normalize().scale(0.11 + 0.09 * (1 - d / 26));
                e.setDeltaMovement(e.getDeltaMovement().add(v.x, 0.01, v.z));
                e.hurtMarked = true;
            }
            for (int i = 0; i < 3; i++) {
                double a = (t * 0.35 + i * Mth.TWO_PI / 3);
                double r = 14 - (t - 22) * 0.2;
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX() + Math.cos(a) * r, getY() + 0.5, getZ() + Math.sin(a) * r,
                        6, 0.3, 0.3, 0.3, 0.02);
            }
            if (t % 10 == 0) playSound(SoundEvents.WARDEN_HEARTBEAT, 6F, 0.5F);
        }
        // estallido
        if (t == 72) {
            hitArea(position(), 13.0, null, -1, 20F, 2.0, 1.7, 120);
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 6F, 0.4F);
            playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 6F, 0.5F);
            for (int y = 0; y < 48; y += 2) {
                sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + y, getZ(), 6, 0.6, 0.4, 0.6, 0.02);
            }
            sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
            ring(ParticleTypes.WITCH, 14, 0.3);
            ring(ParticleTypes.SOUL_FIRE_FLAME, 12, 0.1);
        }
    }

    private void ring(net.minecraft.core.particles.ParticleOptions type, int maxRadius, double lift) {
        if (!(level() instanceof ServerLevel sl)) return;
        for (int r = 2; r <= maxRadius; r += 2) {
            for (int i = 0; i < r * 4; i++) {
                double a = i * Mth.TWO_PI / (r * 4);
                sl.sendParticles(type, getX() + Math.cos(a) * r, getY() + 0.2, getZ() + Math.sin(a) * r, 1, 0, lift, 0, 0.05);
            }
        }
    }

    private void spawnFang(double x, double z, float yaw, int delay) {
        BlockPos pos = BlockPos.containing(x, getY() + 6, z);
        for (int i = 0; i < 12; i++) {
            BlockPos below = pos.below();
            BlockState state = level().getBlockState(below);
            if (state.isFaceSturdy(level(), below, Direction.UP)
                    && level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                level().addFreshEntity(new EvokerFangs(level(), x, pos.getY(), z, yaw, delay, this));
                return;
            }
            pos = below;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Summoned", summoned);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        summoned = tag.getBoolean("Summoned");
    }
}
