package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.BloodMoonConfig;
import com.agustin.bloodmoon.world.VoidImpact;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * El Ejecutor: caballero no-muerto con un mazo colosal.
 *
 *  SMASH     Golpe de Condena: mazazo vertical; abre un cráter, calcina el suelo en Piedra del Vacío
 *            y deja llamas astrales (Quemadura astral).
 *  SWING     Barrido Brutal: arco horizontal de 180° con un empujón enorme.
 *  LEAP/SLAM Salto que aterriza con un cráter.
 *  JUDGMENT  Juicio Final (especial): alza el mazo al cielo, la cabeza se ilumina y todo se oscurece;
 *            3,5 s después lo descarga con una explosión de potencia 60 (x20 la de un creeper).
 */
public class Executioner extends VoidKnight {
    public static final String NAME_KEY = "entity.bloodmoon.executioner";

    public static final int SMASH = 1, SWING = 2, LEAP = 3, SLAM = 4, JUDGMENT = 5;
    private static final int[] DURATIONS = {0, 46, 36, 60, 26, 100};
    public static final int JUDGMENT_IMPACT = 70;

    private int judgmentCooldown = 600;

    public Executioner(EntityType<? extends Monster> type, Level level) {
        super(type, level, Component.translatable(NAME_KEY).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 480.0)
                .add(Attributes.ATTACK_DAMAGE, 24.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
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
        if (judgmentCooldown > 0) judgmentCooldown--;
        super.customServerAiStep();
    }

    @Override
    protected int chooseAttack(LivingEntity target, double dist, boolean stuck) {
        if (judgmentCooldown <= 0 && dist <= 40 && hasLineOfSight(target)) return JUDGMENT;
        if (dist <= 8) return nearbyEnemies(8).size() >= 2 || random.nextFloat() < 0.35F ? SWING : SMASH;
        if (dist > 16 || stuck) return LEAP;
        return 0;
    }

    @Override
    protected void onAttackStart(int attack) {
        switch (attack) {
            case SMASH -> playSound(SoundEvents.RAVAGER_ATTACK, 3F, 0.5F);
            case SWING -> playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 3F, 0.4F);
            case JUDGMENT -> {
                judgmentCooldown = isPhaseTwo() ? 700 : 1000;
                playSound(SoundEvents.WARDEN_ROAR, 6F, 0.35F);
            }
            default -> {}
        }
    }

    @Override
    protected void tickAttack(int attack, LivingEntity target) {
        switch (attack) {
            case SMASH -> {
                if (attackTick == 26) smashAt(position().add(forward().scale(6)), 3.5F, 7F, 28F);
            }
            case SWING -> {
                if (attackTick >= 14 && attackTick <= 22) {
                    float a = (lockedYaw + 90F - 90F + (attackTick - 14) / 8F * 180F) * Mth.DEG_TO_RAD;
                    for (int r = 4; r <= 9; r += 2) {
                        particles(ParticleTypes.SWEEP_ATTACK, position().add(Math.cos(a) * r, 3, Math.sin(a) * r), 1, 0.2, 0);
                    }
                }
                if (attackTick == 20) {
                    hitArea(position(), 10.0, forward(), 0.0, 22F, 3.5, 0.6, 0);
                    playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 4F, 0.4F);
                }
            }
            case LEAP -> {
                if (attackTick == 10 && target != null) leapAt(target);
                else if (attackTick < 10) faceLocked();
                if (attackTick > 16 && onGround()) {
                    setAttack(SLAM);
                    smashAt(position().add(forward().scale(4)), 3F, 6F, 22F);
                }
            }
            case JUDGMENT -> tickJudgment();
            default -> {}
        }
    }

    /** Mazazo: daño, cráter, Piedra del Vacío y llamas astrales. */
    private void smashAt(Vec3 at, float craterRadius, float scorchRadius, float damage) {
        hitArea(at, scorchRadius, null, -1, damage, 1.2, 0.9, 80);
        impactFx(at, 2F, SoundEvents.ANVIL_LAND);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 3F, 0.5F);
        if (level() instanceof ServerLevel sl) VoidImpact.crater(sl, at, craterRadius, scorchRadius, canGrief());
    }

    private void tickJudgment() {
        if (!(level() instanceof ServerLevel sl)) return;
        int t = attackTick;
        Vec3 head = position().add(0, 23, 0);
        if (t == 10) {
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(this) < 96 * 96) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
            }
            playSound(SoundEvents.BEACON_POWER_SELECT, 6F, 0.5F);
        }
        if (t > 20 && t < JUDGMENT_IMPACT) {
            float k = (t - 20) / 50F;
            sl.sendParticles(ParticleTypes.END_ROD, head.x, head.y, head.z, 2 + (int) (6 * k), 0.8, 0.8, 0.8, 0.05);
            sl.sendParticles(ParticleTypes.WITCH, head.x, head.y, head.z, 4, 1.5 * k, 1.5 * k, 1.5 * k, 0.1);
            if (t % 12 == 0) playSound(SoundEvents.WARDEN_SONIC_CHARGE, 5F, 0.4F + k * 0.6F);
        }
        if (t == JUDGMENT_IMPACT) {
            Vec3 at = position().add(forward().scale(6));
            float power = BloodMoonConfig.JUDGMENT_POWER.get().floatValue();
            // "sin excepciones": rompe bloques aunque mobGriefing esté desactivado
            level().explode(this, at.x, at.y, at.z, power, Level.ExplosionInteraction.TNT);
            VoidImpact.crater(sl, at, 0F, 12F, true);
            sl.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y + 2, at.z, 3, 2, 1, 2, 0);
        }
    }

    /** Intensidad del brillo de la cabeza del mazo (cliente), 0..1. */
    public float hammerGlow(float partialTick) {
        if (getAttackId() != JUDGMENT) return 0F;
        float t = tickCount - clientAttackStart + partialTick;
        if (t < 20) return 0F;
        if (t < JUDGMENT_IMPACT - 4) return Mth.clamp((t - 20) / 40F, 0F, 1F) * (0.85F + 0.15F * Mth.sin(t * 0.8F));
        if (t < JUDGMENT_IMPACT + 2) return 1F;
        return Mth.clamp(1F - (t - JUDGMENT_IMPACT - 2) / 14F, 0F, 1F);
    }
}
