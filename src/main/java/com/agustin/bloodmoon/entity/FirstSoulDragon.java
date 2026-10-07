package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.BloodMoonConfig;
import com.agustin.bloodmoon.BloodMoonManager;
import com.agustin.bloodmoon.MoonType;
import com.agustin.bloodmoon.registry.ModEffects;
import com.agustin.bloodmoon.world.VoidImpact;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.EventHooks;

import java.util.List;

/**
 * El Dragón de la Primera Alma: dragón esquelético de huesos negros, ~10 veces el Ender Dragon.
 * En el aire da vueltas sobre su presa y suelta cargas de fuego púrpura (x3 / x5 / x10 un creeper);
 * después se lanza en picada, aterriza sobre las garras de sus alas (cráteres) y en tierra
 * incinera con sus fauces o golpea con las garras. Vuelve a despegar y repite.
 *
 * Geometría en px de modelo (pies en el origen); 1 px = unit() bloques. Ver dragon_gen.py.
 */
public class FirstSoulDragon extends Monster {
    public static final String NAME_KEY = "entity.bloodmoon.first_soul_dragon";

    private static final EntityDataAccessor<Byte> DATA_ATTACK = SynchedEntityData.defineId(FirstSoulDragon.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_GROUNDED = SynchedEntityData.defineId(FirstSoulDragon.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(FirstSoulDragon.class, EntityDataSerializers.FLOAT);

    public static final int VOLLEY = 1, BREATH = 2, CLAW_L = 3, CLAW_R = 4, LAND = 5;
    private static final int[] DURATIONS = {0, 56, 100, 44, 44, 34};
    public static final int BREATH_START = 20, BREATH_END = 86, CLAW_IMPACT = 26;

    // puntos del modelo (px): (izquierda, arriba, adelante) respecto de los pies
    private static final double[] MOUTH_FLY = {0, 26.5, 53}, MOUTH_GROUND = {0, 44.3, 44.9};
    private static final double[] CLAW = {15.7, 0, 18}, CORE = {0, 25, 4.5};
    private static final double BAR_RANGE = 320;

    public enum Phase { DESCEND, CIRCLE, DIVE, GROUND, TAKEOFF, DEPART }

    private final ServerBossEvent bossEvent;
    private final DragonPart head, neck, body, tail1, tail2, wingL, wingR;
    private final DragonPart[] parts;

    private Phase phase = Phase.TAKEOFF;
    private int phaseTicks, attackTick, cooldown = 60, volleys, groundAttacks;
    private double circleAngle;
    private Vec3 anchor, landing, breathDir;
    private Vec3 vel = Vec3.ZERO;
    private boolean boundToNight;

    /** Cliente. */
    public int clientAttackStart;
    public float groundBlend, groundBlendO, visualPitch, visualPitchO;
    private float flapPhase;

    public FirstSoulDragon(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 2000;
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.bossEvent = new ServerBossEvent(Component.translatable(NAME_KEY).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);
        this.head = new DragonPart(this, "head", 14, 12, 1.5F);
        this.neck = new DragonPart(this, "neck", 10, 10, 1.0F);
        this.body = new DragonPart(this, "body", 20, 16, 1.0F);
        this.tail1 = new DragonPart(this, "tail1", 12, 9, 0.6F);
        this.tail2 = new DragonPart(this, "tail2", 10, 7, 0.6F);
        this.wingL = new DragonPart(this, "wingL", 22, 10, 0.5F);
        this.wingR = new DragonPart(this, "wingR", 22, 10, 0.5F);
        this.parts = new DragonPart[]{head, neck, body, tail1, tail2, wingL, wingR};
        this.setId(this.getId()); // partes = id+1..id+n
        if (!level.isClientSide) setDragonScale(BloodMoonConfig.DRAGON_SCALE.get().floatValue());
        rescaleParts();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1500.0)
                .add(Attributes.ATTACK_DAMAGE, 30.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 256.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    // ------------------------------------------------------------------ datos sincronizados

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK, (byte) 0);
        builder.define(DATA_GROUNDED, false);
        builder.define(DATA_SCALE, 5F);
    }

    public int getAttackId() {
        return this.entityData.get(DATA_ATTACK);
    }

    private void setAttack(int attack) {
        this.entityData.set(DATA_ATTACK, (byte) attack);
        this.attackTick = 0;
    }

    public boolean isGrounded() {
        return this.entityData.get(DATA_GROUNDED);
    }

    public float getDragonScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public void setDragonScale(float scale) {
        this.entityData.set(DATA_SCALE, Mth.clamp(scale, 1F, 12F));
    }

    /** Bloques por px de modelo (el renderer escala el modelo x2,4·escala). */
    public float unit() {
        return 0.15F * getDragonScale();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ATTACK.equals(key)) clientAttackStart = this.tickCount;
        if (DATA_SCALE.equals(key)) rescaleParts();
    }

    private void rescaleParts() {
        if (parts == null) return;
        for (DragonPart p : parts) p.rescale(unit());
        refreshDimensions();
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        float u = unit();
        return EntityDimensions.scalable(10F * u, 14F * u);
    }

    public void bindToNight() {
        this.boundToNight = true;
    }

    public void startDescent(Vec3 center) {
        this.anchor = center;
        setPhase(Phase.DESCEND);
    }

    // ------------------------------------------------------------------ multipart

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (parts == null) return;
        for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        for (int i = 0; i < parts.length; i++) parts[i].setId(packet.getId() + i + 1);
    }

    public boolean hurtPart(DragonPart part, DamageSource source, float amount) {
        return hurt(source, amount * part.damageMultiplier);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker == this || attacker instanceof VoidKnight || attacker instanceof WitherSkeleton) return false;
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_DROWNING)) return false;
        return super.hurt(source, amount);
    }

    /** Coloca las hitboxes según la pose (en vuelo: alas abiertas; en tierra: cuello en S y alas plegadas). */
    private void updateParts() {
        float g = level().isClientSide ? groundBlend : (isGrounded() ? 1F : 0F);
        place(head, mix(new double[]{0, 26, 52}, new double[]{0, 44, 42}, g));
        place(neck, mix(new double[]{0, 26, 30}, new double[]{0, 36, 25}, g));
        place(body, mix(new double[]{0, 24, 0}, new double[]{0, 26, 0}, g));
        place(tail1, mix(new double[]{0, 28, -28}, new double[]{0, 22, -28}, g));
        place(tail2, mix(new double[]{0, 28, -58}, new double[]{0, 12, -55}, g));
        place(wingL, mix(new double[]{38, 29, 5}, new double[]{15, 14, 14}, g));
        place(wingR, mix(new double[]{-38, 29, 5}, new double[]{-15, 14, 14}, g));
    }

    private static double[] mix(double[] a, double[] b, float t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private void place(DragonPart part, double[] p) {
        Vec3 at = local(p[0], p[1], p[2]);
        double ox = part.getX(), oy = part.getY(), oz = part.getZ();
        part.setPos(at.x, at.y - part.halfHeight(), at.z);
        part.xo = ox; part.yo = oy; part.zo = oz;
        part.xOld = ox; part.yOld = oy; part.zOld = oz;
    }

    /** Punto del modelo (px: izquierda, arriba, adelante) a coordenadas del mundo. */
    public Vec3 local(double left, double up, double fwd) {
        double u = unit();
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        double lx = Mth.cos(yaw), lz = Mth.sin(yaw);
        return new Vec3(getX() + (lx * left + fx * fwd) * u, getY() + up * u, getZ() + (lz * left + fz * fwd) * u);
    }

    private Vec3 local(double[] p) {
        return local(p[0], p[1], p[2]);
    }

    private Vec3 forward() {
        return Vec3.directionFromRotation(0F, getYRot());
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        updateParts();
    }

    @Override
    public void travel(Vec3 travelVector) {
        // el movimiento lo maneja la IA de vuelo (sin física)
    }

    @Override
    public void aiStep() {
        super.aiStep();
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());
        if (level().isClientSide) clientTick();
    }

    private void clientTick() {
        groundBlendO = groundBlend;
        groundBlend = Mth.approach(groundBlend, isGrounded() ? 1F : 0F, 0.04F);
        visualPitchO = visualPitch;
        float dy = (float) (getY() - yo);
        visualPitch += (Mth.clamp(-dy * 0.3F / Math.max(0.3F, unit()), -0.45F, 0.45F) * (1F - groundBlend) - visualPitch) * 0.1F;

        // aleteo: sonido grave al bajar las alas
        if (!isGrounded() && !isDeadOrDying()) {
            float before = flapPhase;
            flapPhase += 0.12F;
            if (Mth.sin(before) > 0 && Mth.sin(flapPhase) <= 0) {
                level().playLocalSound(getX(), getY() + 20 * unit(), getZ(), SoundEvents.ENDER_DRAGON_FLAP, getSoundSource(),
                        6F, 0.35F + random.nextFloat() * 0.1F, false);
            }
        }
        // el alma que late dentro del costillar
        if (random.nextInt(2) == 0) {
            Vec3 c = local(CORE);
            double s = 3 * unit();
            level().addAlwaysVisibleParticle(random.nextBoolean() ? ParticleTypes.SOUL : ParticleTypes.WITCH, true,
                    c.x + (random.nextDouble() - 0.5) * s, c.y + (random.nextDouble() - 0.5) * s, c.z + (random.nextDouble() - 0.5) * s,
                    0, 0.05, 0);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        BossBars.update(this, bossEvent, BAR_RANGE);
        if (anchor == null) anchor = position();
        if (boundToNight && phase != Phase.DEPART && BloodMoonManager.current() != MoonType.MOONLESS) setPhase(Phase.DEPART);
        if (tickCount % 20 == 0) retarget();
        LivingEntity target = getTarget();
        phaseTicks++;

        int attack = getAttackId();
        if (attack != 0) {
            attackTick++;
            tickAttack(attack, target);
            if (getAttackId() == attack && attackTick >= DURATIONS[attack]) {
                setAttack(0);
                cooldown = isPhaseTwo() ? 25 + random.nextInt(20) : 40 + random.nextInt(30);
            }
        } else if (cooldown > 0) {
            cooldown--;
        }

        switch (phase) {
            case DESCEND -> tickDescend();
            case CIRCLE -> tickCircle(target);
            case DIVE -> tickDive(target);
            case GROUND -> tickGround(target);
            case TAKEOFF -> tickTakeoff();
            case DEPART -> tickDepart();
        }
        if (phase != Phase.GROUND) setPos(getX() + vel.x, getY() + vel.y, getZ() + vel.z);
        setDeltaMovement(vel);
    }

    private void setPhase(Phase next) {
        this.phase = next;
        this.phaseTicks = 0;
        boolean grounded = next == Phase.GROUND;
        if (isGrounded() != grounded) this.entityData.set(DATA_GROUNDED, grounded);
        if (next == Phase.CIRCLE) volleys = 0;
        if (next == Phase.GROUND) groundAttacks = 0;
        if (getAttackId() != 0 && next != Phase.GROUND) setAttack(0);
    }

    private void retarget() {
        LivingEntity t = getTarget();
        if (t != null && t.isAlive() && !(t instanceof Player p && (p.isCreative() || p.isSpectator()))
                && t.distanceToSqr(this) < 300 * 300) return;
        Player p = level().getNearestPlayer(TargetingConditions.forCombat().range(260).ignoreLineOfSight(), this);
        setTarget(p);
    }

    public boolean isPhaseTwo() {
        return getHealth() <= getMaxHealth() * 0.5F;
    }

    private double groundY(double x, double z) {
        return level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    private double maxSpeed() {
        return 0.5 + 0.09 * getDragonScale();
    }

    private void steerTo(Vec3 goal, double speed, double accel, float turn) {
        Vec3 to = goal.subtract(position());
        double len = to.length();
        Vec3 desired = len < 1e-3 ? Vec3.ZERO : to.scale(Math.min(speed, len * 0.06 + 0.15) / len);
        vel = vel.add(desired.subtract(vel).scale(accel));
        if (vel.horizontalDistanceSqr() > 0.01) {
            float yaw = (float) (Mth.atan2(vel.z, vel.x) * Mth.RAD_TO_DEG) - 90F;
            setYRot(Mth.approachDegrees(getYRot(), yaw, turn));
        }
    }

    private void tickDescend() {
        double u = unit();
        Vec3 goal = new Vec3(anchor.x, groundY(anchor.x, anchor.z) + 45 * u, anchor.z);
        steerTo(goal, maxSpeed(), 0.05, 3F);
        if (position().distanceTo(goal) < 12 * u || phaseTicks > 600) {
            roar();
            setPhase(Phase.CIRCLE);
        }
    }

    private void tickCircle(LivingEntity target) {
        double u = unit();
        if (target != null) anchor = anchor.add(target.position().subtract(anchor).scale(0.05));
        double radius = 55 * u;
        double speed = maxSpeed();
        circleAngle += speed / radius;
        double gy = groundY(anchor.x + Math.cos(circleAngle) * radius, anchor.z + Math.sin(circleAngle) * radius);
        double alt = Math.max(gy, target != null ? target.getY() : anchor.y) + 45 * u;
        Vec3 goal = new Vec3(anchor.x + Math.cos(circleAngle) * radius, alt, anchor.z + Math.sin(circleAngle) * radius);
        steerTo(goal, speed, 0.05, 3.5F);

        if (target == null) return;
        if (getAttackId() == 0 && cooldown <= 0 && phaseTicks > 40) {
            setAttack(VOLLEY);
            volleys++;
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 10F, 0.4F);
        }
        if (getAttackId() == 0 && volleys >= (isPhaseTwo() ? 2 : 3)) {
            Vec3 flat = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
            Vec3 dir = flat.lengthSqr() < 1 ? forward() : flat.normalize();
            double reach = 40 * u;
            double lx = target.getX() - dir.x * reach, lz = target.getZ() - dir.z * reach;
            landing = new Vec3(lx, groundY(lx, lz), lz);
            setPhase(Phase.DIVE);
        }
    }

    private void tickDive(LivingEntity target) {
        double u = unit();
        if (landing == null) {
            setPhase(Phase.CIRCLE);
            return;
        }
        landing = new Vec3(landing.x, groundY(landing.x, landing.z), landing.z);
        steerTo(landing, maxSpeed() * 1.3, 0.07, 5F);
        Vec3 to = landing.subtract(position());
        if (to.horizontalDistance() < 4 * u && Math.abs(to.y) < 3 * u) {
            setPos(landing.x, landing.y, landing.z);
            vel = Vec3.ZERO;
            if (target != null) setYRot(yawTo(target.position()));
            setPhase(Phase.GROUND);
            setAttack(LAND);
        } else if (phaseTicks > 400) {
            setPhase(Phase.TAKEOFF);
        }
    }

    private void tickGround(LivingEntity target) {
        double u = unit();
        vel = Vec3.ZERO;
        if (tickCount % 10 == 0) {
            double gy = groundY(getX(), getZ());
            if (Math.abs(gy - getY()) > 0.5) setPos(getX(), gy, getZ());
        }
        if (target == null || phaseTicks > 700 || groundAttacks >= (isPhaseTwo() ? 5 : 4)) {
            if (getAttackId() == 0) setPhase(Phase.TAKEOFF);
            return;
        }
        double dist = Math.sqrt(target.distanceToSqr(this));
        if (getAttackId() != 0) return;
        if (dist > 130 * u) {
            setPhase(Phase.TAKEOFF);
            return;
        }
        float want = yawTo(target.position());
        setYRot(Mth.approachDegrees(getYRot(), want, 1.6F));
        if (cooldown > 0) return;

        Vec3 clawL = local(CLAW), clawR = local(-CLAW[0], CLAW[1], CLAW[2]);
        double reachClaw = 22 * u;
        double dl = target.position().distanceTo(clawL), dr = target.position().distanceTo(clawR);
        float diff = Math.abs(Mth.wrapDegrees(want - getYRot()));
        if (Math.min(dl, dr) < reachClaw && (random.nextFloat() < 0.65F || diff > 40)) {
            setAttack(dl < dr ? CLAW_L : CLAW_R);
            playSound(SoundEvents.RAVAGER_ROAR, 8F, 0.35F);
        } else if (diff < 30) {
            setAttack(BREATH);
            breathDir = null;
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 10F, 0.3F);
        } else {
            return;
        }
        groundAttacks++;
    }

    private void tickTakeoff() {
        double u = unit();
        if (phaseTicks == 1) {
            this.entityData.set(DATA_GROUNDED, false);
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 10F, 0.3F);
            if (level() instanceof ServerLevel sl) {
                sendForced(sl, ParticleTypes.CLOUD, position(), 120, 18 * u, 1, 18 * u, 0.15);
            }
        }
        Vec3 goal = position().add(forward().scale(30 * u));
        goal = new Vec3(goal.x, groundY(getX(), getZ()) + 48 * u, goal.z);
        steerTo(goal, maxSpeed(), 0.04, 2F);
        if (getY() > groundY(getX(), getZ()) + 40 * u || phaseTicks > 220) setPhase(Phase.CIRCLE);
    }

    private void tickDepart() {
        if (phaseTicks == 1) {
            this.entityData.set(DATA_GROUNDED, false);
            roar();
        }
        steerTo(position().add(forward().scale(60)).add(0, 200, 0), maxSpeed() * 1.5, 0.05, 2F);
        if (phaseTicks > 160 || getY() > level().getMaxBuildHeight() + 120) vanish();
    }

    private float yawTo(Vec3 pos) {
        return (float) (Mth.atan2(pos.z - getZ(), pos.x - getX()) * Mth.RAD_TO_DEG) - 90F;
    }

    // ------------------------------------------------------------------ ataques

    private void tickAttack(int attack, LivingEntity target) {
        if (!(level() instanceof ServerLevel sl)) return;
        double u = unit();
        switch (attack) {
            case VOLLEY -> {
                int last = isPhaseTwo() ? 44 : 36;
                if (target != null && attackTick >= 12 && attackTick <= last && (attackTick - 12) % 8 == 0) fireCharge(sl, target);
            }
            case BREATH -> tickBreath(sl, target);
            case CLAW_L, CLAW_R -> {
                if (attackTick == CLAW_IMPACT) {
                    double side = attack == CLAW_L ? CLAW[0] : -CLAW[0];
                    Vec3 at = local(side, 0, CLAW[2]);
                    if (target != null && target.position().distanceTo(at) < 22 * u) {
                        at = new Vec3(target.getX(), at.y, target.getZ());
                    }
                    at = new Vec3(at.x, groundY(at.x, at.z), at.z);
                    float s = getDragonScale();
                    hitArea(at, 1.2 * s, 34F, 2.5, 1.0, 100);
                    VoidImpact.crater(sl, at, 0.55F * s, 1.0F * s, canGrief());
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 8F, 0.4F);
                    playSound(SoundEvents.ANVIL_LAND, 8F, 0.3F);
                }
            }
            case LAND -> {
                if (attackTick == 1) {
                    float s = getDragonScale();
                    for (double side : new double[]{CLAW[0], -CLAW[0]}) {
                        Vec3 at = local(side, 0, CLAW[2]);
                        at = new Vec3(at.x, groundY(at.x, at.z), at.z);
                        VoidImpact.crater(sl, at, 0.35F * s, 0.8F * s, canGrief());
                    }
                    hitArea(position(), 2.2 * s, 22F, 3.0, 1.4, 0);
                    sendForced(sl, ParticleTypes.EXPLOSION_EMITTER, position(), 6, 10 * u, 1, 10 * u, 0);
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 10F, 0.3F);
                    roar();
                }
            }
            default -> {}
        }
    }

    private void fireCharge(ServerLevel sl, LivingEntity target) {
        Vec3 mouth = local(MOUTH_FLY);
        float roll = random.nextFloat();
        float p10 = isPhaseTwo() ? 0.2F : 0.1F, p5 = isPhaseTwo() ? 0.35F : 0.3F;
        int mult = roll < p10 ? 10 : roll < p10 + p5 ? 5 : 3;
        double speed = 2.2;
        Vec3 aim = target.position().add(0, 0.5, 0);
        double t = aim.distanceTo(mouth) / speed;
        aim = aim.add(target.getDeltaMovement().scale(t * 0.6))
                .add((random.nextDouble() - 0.5) * 6, 0, (random.nextDouble() - 0.5) * 6);
        Vec3 v = aim.subtract(mouth).normalize().scale(speed);
        SoulCharge.shoot(sl, this, mouth, v, mult);
        playSound(SoundEvents.GHAST_SHOOT, 8F, mult == 10 ? 0.3F : 0.5F);
        sendForced(sl, ParticleTypes.DRAGON_BREATH, mouth, 40, 2, 2, 2, 0.1);
    }

    private void tickBreath(ServerLevel sl, LivingEntity target) {
        double u = unit();
        Vec3 mouth = local(MOUTH_GROUND);
        int t = attackTick;
        if (t < BREATH_START) {
            // inspira: el fuego se junta en las fauces
            sendForced(sl, ParticleTypes.REVERSE_PORTAL, mouth, 20, 3 * u, 3 * u, 3 * u, 0.4);
            return;
        }
        if (t > BREATH_END) return;
        Vec3 want = target != null ? target.position().add(0, 1, 0).subtract(mouth).normalize() : forward().add(0, -0.6, 0).normalize();
        breathDir = breathDir == null ? want : breathDir.add(want.subtract(breathDir).scale(0.05)).normalize();
        double maxLen = 140 * u;
        BlockHitResult clip = level().clip(new ClipContext(mouth, mouth.add(breathDir.scale(maxLen)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double len = clip.getType() == HitResult.Type.MISS ? maxLen : clip.getLocation().distanceTo(mouth);

        // chorro de fuego púrpura
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(mouth) > 400 * 400) continue;
            for (int i = 0; i < 14; i++) {
                double d = random.nextDouble() * len;
                double spread = 0.6 + d * 0.1;
                Vec3 at = mouth.add(breathDir.scale(d));
                sl.sendParticles(p, ParticleTypes.DRAGON_BREATH, true, at.x + (random.nextDouble() - 0.5) * spread,
                        at.y + (random.nextDouble() - 0.5) * spread, at.z + (random.nextDouble() - 0.5) * spread,
                        0, breathDir.x, breathDir.y, breathDir.z, 1.2);
            }
            Vec3 at = mouth.add(breathDir.scale(random.nextDouble() * len));
            sl.sendParticles(p, ParticleTypes.SOUL_FIRE_FLAME, true, at.x, at.y, at.z, 6, 1.5, 1.5, 1.5, 0.05);
        }
        if (t % 4 == 0) playSound(SoundEvents.FIRECHARGE_USE, 6F, 0.4F);

        if (t % 5 == 0) {
            AABB box = new AABB(mouth, mouth.add(breathDir.scale(len))).inflate(4 + len * 0.1);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, this::isEnemy)) {
                Vec3 rel = e.position().add(0, e.getBbHeight() / 2, 0).subtract(mouth);
                double along = rel.dot(breathDir);
                if (along < 0 || along > len + 3) continue;
                double perp = rel.subtract(breathDir.scale(along)).length();
                if (perp > 2.5 + along * 0.1) continue;
                if (e.hurt(damageSources().mobAttack(this), 7F)) {
                    e.addEffect(new MobEffectInstance(ModEffects.ASTRAL_BURN, 160, 0));
                }
            }
        }
        if (clip.getType() == HitResult.Type.BLOCK && t % 3 == 0) {
            VoidImpact.scorch(sl, clip.getLocation(), (float) (2 + 0.3 * getDragonScale()), canGrief());
        }
    }

    private boolean isEnemy(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof WitherSkeleton) && !(e instanceof VoidKnight)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private void hitArea(Vec3 center, double radius, float damage, double knock, double lift, int astralTicks) {
        AABB box = new AABB(center, center).inflate(radius, radius * 0.6 + 4, radius);
        List<LivingEntity> list = level().getEntitiesOfClass(LivingEntity.class, box, this::isEnemy);
        for (LivingEntity e : list) {
            double dist = Math.sqrt(e.distanceToSqr(center.x, e.getY(), center.z));
            if (dist > radius) continue;
            float falloff = (float) (0.5 + 0.5 * (1 - dist / radius));
            if (e.hurt(damageSources().mobAttack(this), damage * falloff)) {
                e.knockback(knock, center.x - e.getX(), center.z - e.getZ());
                if (lift > 0) e.push(0, lift, 0);
                e.hurtMarked = true;
                if (astralTicks > 0) e.addEffect(new MobEffectInstance(ModEffects.ASTRAL_BURN, astralTicks, 0));
            }
        }
    }

    private void sendForced(ServerLevel sl, ParticleOptions type, Vec3 at, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(at) < 400 * 400) sl.sendParticles(p, type, true, at.x, at.y, at.z, count, dx, dy, dz, speed);
        }
    }

    private void roar() {
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 12F, 0.3F);
    }

    private boolean canGrief() {
        return EventHooks.canEntityGrief(level(), this);
    }

    public void vanish() {
        if (level() instanceof ServerLevel sl) {
            sendForced(sl, ParticleTypes.REVERSE_PORTAL, local(CORE), 600, 20 * unit(), 10 * unit(), 20 * unit(), 0.5);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 10F, 0.3F);
        }
        discard();
    }

    // ------------------------------------------------------------------ muerte

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (!(level() instanceof ServerLevel sl)) return;
        double gy = groundY(getX(), getZ());
        if (getY() > gy) setPos(getX(), Math.max(gy, getY() - 0.6), getZ()); // cae lentamente
        Vec3 core = local(CORE);
        double u = unit();
        if (deathTime == 1) playSound(SoundEvents.ENDER_DRAGON_DEATH, 12F, 0.4F);
        if (deathTime % 3 == 0) {
            sendForced(sl, ParticleTypes.EXPLOSION, core, 3, 12 * u, 8 * u, 20 * u, 0);
            sendForced(sl, ParticleTypes.REVERSE_PORTAL, core, 60, 6 * u, 6 * u, 6 * u, 0.6);
            sendForced(sl, ParticleTypes.SOUL, core, 20, 10 * u, 6 * u, 10 * u, 0.05);
        }
        if (deathTime >= 110) {
            sendForced(sl, ParticleTypes.EXPLOSION_EMITTER, core, 12, 10 * u, 6 * u, 10 * u, 0);
            sendForced(sl, ParticleTypes.END_ROD, core, 400, 4 * u, 4 * u, 4 * u, 0.6);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 12F, 0.5F);
            this.remove(RemovalReason.KILLED);
        }
    }

    // ------------------------------------------------------------------ varios

    @Override
    public AABB getBoundingBoxForCulling() {
        double u = unit();
        return getBoundingBox().inflate(80 * u, 50 * u, 80 * u);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = 600 + 100 * unit();
        return distance < d * d;
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
    protected void doPush(Entity entity) {}

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return isEnemy(target) && super.canAttack(target);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDER_DRAGON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 10F;
    }

    @Override
    public float getVoicePitch() {
        return 0.4F;
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossEvent.removeAllPlayers();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("BoundToNight", boundToNight);
        tag.putFloat("DragonScale", getDragonScale());
        tag.putString("Phase", phase.name());
        if (anchor != null) {
            tag.putDouble("AnchorX", anchor.x);
            tag.putDouble("AnchorY", anchor.y);
            tag.putDouble("AnchorZ", anchor.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        boundToNight = tag.getBoolean("BoundToNight");
        if (tag.contains("DragonScale")) setDragonScale(tag.getFloat("DragonScale"));
        if (tag.contains("AnchorX")) anchor = new Vec3(tag.getDouble("AnchorX"), tag.getDouble("AnchorY"), tag.getDouble("AnchorZ"));
        Phase saved = Phase.TAKEOFF;
        try {
            saved = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException ignored) {
        }
        // retomar en el aire o en tierra (sin ataques a medio hacer)
        setPhase(saved == Phase.GROUND ? Phase.GROUND : saved == Phase.DEPART ? Phase.DEPART : Phase.TAKEOFF);
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }
}
