package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.network.EyeMadnessPayload;
import com.agustin.bloodmoon.network.EyeTitlePayload;
import com.agustin.bloodmoon.registry.ModEffects;
import com.agustin.bloodmoon.registry.ModSounds;
import com.agustin.bloodmoon.world.EyeSanctums;
import com.agustin.bloodmoon.world.design.SanctumDesign;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * El Observador: el Ojo de la Grieta, jefe pináculo del Vacío. Flota sobre el estrado de su Santuario y
 * todo gira a su alrededor (anillos de runas, bloques del Vacío, tentáculos).
 *
 * Fase 1 (100-66%): Mirada (rayo que sigue al objetivo; los pilares lo bloquean), Tentáculos del Abismo y Llamado.
 * Fase 2 (66-33%): + Ojos Vigías (proyectiles que persiguen y siembran locura) y Singularidad (atrae a todos y
 *   suelta una onda a ras del piso que hay que saltar).
 * Fase 3 (33-0%): Barrido (el rayo gira 360° a ras del piso: cubrirse tras un pilar o meterse bajo el Ojo),
 *   todo más rápido y el cielo se llena de ojos.
 *
 * Locura: mirar al Ojo llena un medidor; al llegar a 100 la mente se quiebra (daño mágico, ceguera, oscuridad).
 * Solo es vulnerable de verdad cuando queda Expuesto tras disparar: baja hasta el estrado, dilata la pupila
 * y no genera locura. El resto del tiempo su mirada desvía el 85% del daño.
 */
public class VoidEye extends Monster {
    public static final String NAME_KEY = "entity.bloodmoon.void_eye";
    public static final float RADIUS = 9F;

    public static final int S_AWAKEN = 0, S_IDLE = 1, S_GAZE_CHARGE = 2, S_GAZE = 3, S_EXPOSED = 4, S_TENTACLES = 5,
            S_PULL = 6, S_WAVE = 7, S_WATCHERS = 8, S_CALL = 9, S_SWEEP_CHARGE = 10, S_SWEEP = 11, S_SCREAM = 12;
    public static final int AWAKEN_TICKS = 170, SCREAM_TICKS = 60, PULL_TICKS = 60, WAVE_TICKS = 52, GAZE_TICKS = 50,
            SWEEP_TICKS = 100, DEATH_TICKS = 150;
    /** Fase final: al "morir" en fase 3 arrastra a todos al Más Allá y renace como el Observador Desatado. */
    public static final int S_ASCEND = 13, ASCEND_TICKS = 100;
    public static final double WAVE_SPEED = 0.9, WAVE_START = SanctumDesign.R_DAIS;
    protected static final double ARENA_RANGE = 110, SWEEP_RADIUS = 42;
    protected static final float GUARDED = 0.15F;

    protected static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(VoidEye.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(VoidEye.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Vector3f> DATA_BEAM = SynchedEntityData.defineId(VoidEye.class, EntityDataSerializers.VECTOR3);
    protected static final EntityDataAccessor<Vector3f> DATA_HOME = SynchedEntityData.defineId(VoidEye.class, EntityDataSerializers.VECTOR3);
    protected static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(VoidEye.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> DATA_PITCH = SynchedEntityData.defineId(VoidEye.class, EntityDataSerializers.FLOAT);

    protected final ServerBossEvent bossEvent;
    protected Vec3 home;
    protected int stateTick;
    protected int cooldown = 40;
    protected int attacksSinceExposed;
    protected int noPlayerTicks;
    protected Vec3 beamAim = Vec3.ZERO;
    protected double sweepAngle, sweepDir = 1;
    protected final Map<UUID, Float> madness = new HashMap<>();
    protected final Map<UUID, Boolean> sight = new HashMap<>();
    protected final Map<Integer, Integer> beamHit = new HashMap<>();
    protected final Set<UUID> waveHit = new HashSet<>();
    protected DamageSource pendingLoot;
    protected boolean lootReleased;
    protected boolean ascended;

    // cliente
    public int clientStateStart;
    public float yawO, yaw, pitchO, pitch;

    public VoidEye(EntityType<? extends VoidEye> type, Level level) {
        super(type, level);
        this.xpReward = 1500;
        this.setPersistenceRequired();
        this.setNoGravity(true);
        this.noPhysics = true;
        this.lookControl = new LookControl(this) {
            @Override
            public void tick() {}
        };
        this.bossEvent = new ServerBossEvent(Component.translatable(NAME_KEY).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        this.bossEvent.setDarkenScreen(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 128.0)
                .add(Attributes.ARMOR, 0.0);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {}
        };
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, (byte) S_AWAKEN);
        builder.define(DATA_PHASE, (byte) 1);
        builder.define(DATA_BEAM, new Vector3f());
        builder.define(DATA_HOME, new Vector3f());
        builder.define(DATA_YAW, 0F);
        builder.define(DATA_PITCH, 0F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_STATE.equals(key)) clientStateStart = this.tickCount;
    }

    // ------------------------------------------------------------------ acceso

    public int getState() {
        return entityData.get(DATA_STATE);
    }

    public int getPhase() {
        return entityData.get(DATA_PHASE);
    }

    public Vec3 getBeamEnd() {
        Vector3f v = entityData.get(DATA_BEAM);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public Vec3 getHome() {
        if (home != null) return home;
        Vector3f v = entityData.get(DATA_HOME);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public void setHome(Vec3 h) {
        this.home = h;
        entityData.set(DATA_HOME, new Vector3f((float) h.x, (float) h.y, (float) h.z));
    }

    /** Ticks (con fracción) desde que empezó el estado actual, en el cliente. */
    public float clientStateAge(float partialTick) {
        return tickCount - clientStateStart + partialTick;
    }

    public Vec3 center() {
        return position().add(0, eyeRadius(), 0);
    }

    /** Radio del globo ocular (el Desatado tiene uno más chico, en el pecho). */
    public float eyeRadius() {
        return RADIUS;
    }

    public float lookYaw() {
        return entityData.get(DATA_YAW);
    }

    public float lookPitch() {
        return entityData.get(DATA_PITCH);
    }

    public static Vec3 dirFrom(float yaw, float pitch) {
        float yr = yaw * Mth.DEG_TO_RAD, pr = pitch * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yr) * Mth.cos(pr), -Mth.sin(pr), Mth.cos(yr) * Mth.cos(pr));
    }

    public boolean isBeamState() {
        int s = getState();
        return s == S_GAZE_CHARGE || s == S_GAZE || s == S_SWEEP_CHARGE || s == S_SWEEP;
    }

    public boolean isFiring() {
        int s = getState();
        return s == S_GAZE || s == S_SWEEP;
    }

    protected static boolean ally(Entity e) {
        return e instanceof VoidEye || e instanceof EyeTentacle || e instanceof VoidSkeleton || e instanceof WatcherEye;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount <= 1) {
                yaw = yawO = lookYaw();
                pitch = pitchO = lookPitch();
            }
            yawO = yaw;
            pitchO = pitch;
            float k = isFiring() ? 0.75F : 0.35F;
            yaw = Mth.rotLerp(k, yaw, lookYaw());
            pitch = Mth.lerp(k, pitch, lookPitch());
            clientParticles();
        }
    }

    @Override
    protected void customServerAiStep() {
        if (!(level() instanceof ServerLevel sl)) return;
        if (home == null) setHome(position());
        bossEvent.setProgress(getHealth() / getMaxHealth());
        BossBars.update(this, bossEvent, ARENA_RANGE + 30);

        List<ServerPlayer> arena = arenaPlayers(sl);
        if (arena.isEmpty()) {
            if (++noPlayerTicks > 400) {           // todos huyeron o murieron: el Ojo vuelve a dormir
                madness.clear();
                discard();
            }
            hover();
            return;
        }
        noPlayerTicks = 0;
        stateTick++;

        int want = getHealth() > getMaxHealth() * 0.66F ? 1 : getHealth() > getMaxHealth() * 0.33F ? 2 : 3;
        int state = getState();
        if (want > getPhase() && state != S_AWAKEN && state != S_SCREAM && state != S_ASCEND) {
            entityData.set(DATA_PHASE, (byte) want);
            setState(S_SCREAM, arena);
            state = S_SCREAM;
        }

        LivingEntity target = pickTarget(arena);
        updateMadness(sl, arena);
        hover();

        switch (state) {
            case S_AWAKEN -> tickAwaken(sl, arena);
            case S_IDLE -> {
                lookAt(target.getEyePosition(), 4F);
                if (--cooldown <= 0) chooseAttack(arena, target);
            }
            case S_GAZE_CHARGE -> {
                trackAim(target, 0.06 + 0.02 * getPhase());
                lookAt(beamAim, 30F);
                updateBeam();
                if (stateTick >= (getPhase() == 3 ? 22 : 30)) setState(S_GAZE, arena);
            }
            case S_GAZE -> {
                trackAim(target, 0.035 + 0.02 * getPhase());
                lookAt(beamAim, 30F);
                updateBeam();
                damageBeam(sl, 6F + 2F * getPhase());
                if (stateTick >= GAZE_TICKS) setState(S_EXPOSED, arena);
            }
            case S_SWEEP_CHARGE -> {
                beamAim = sweepPoint();
                lookAt(beamAim, 30F);
                updateBeam();
                if (stateTick >= 30) setState(S_SWEEP, arena);
            }
            case S_SWEEP -> {
                sweepAngle += sweepDir * 360.0 / SWEEP_TICKS;
                beamAim = sweepPoint();
                lookAt(beamAim, 60F);
                updateBeam();
                damageBeam(sl, 12F);
                if (stateTick >= SWEEP_TICKS) setState(S_EXPOSED, arena);
            }
            case S_EXPOSED -> {
                lookAt(center().add(dirFrom(lookYaw(), 30F).scale(20)).add(Mth.sin(tickCount * 0.05F) * 6, 0, 0), 1.5F);
                if (tickCount % 20 == 0) playSound(SoundEvents.WARDEN_HEARTBEAT, 6F, 0.45F);
                if (stateTick >= exposedTicks()) {
                    playSound(ModSounds.EYE_CHARGE.get(), 5F, 0.6F);
                    setIdle(25);
                }
            }
            case S_TENTACLES -> {
                lookAt(target.getEyePosition(), 3F);
                if (stateTick >= 40) setIdle(cooldownTicks());
            }
            case S_WATCHERS, S_CALL -> {
                lookAt(target.getEyePosition(), 3F);
                if (stateTick >= 30) setIdle(cooldownTicks());
            }
            case S_PULL -> {
                lookAt(target.getEyePosition(), 2F);
                tickPull(arena);
                if (stateTick >= PULL_TICKS) setState(S_WAVE, arena);
            }
            case S_WAVE -> {
                tickWave(sl, arena);
                if (stateTick >= WAVE_TICKS) setIdle(cooldownTicks());
            }
            case S_SCREAM -> {
                lookAt(center().add(Mth.sin(tickCount * 0.9F) * 8, 30, Mth.cos(tickCount * 0.7F) * 8), 20F);
                if (stateTick >= SCREAM_TICKS) setIdle(30);
            }
            case S_ASCEND -> tickAscend(sl, arena);
            default -> setIdle(20);
        }
    }

    protected List<ServerPlayer> arenaPlayers(ServerLevel sl) {
        Vec3 h = getHome();
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double dx = p.getX() - h.x, dz = p.getZ() - h.z;
            if (dx * dx + dz * dz < ARENA_RANGE * ARENA_RANGE && Math.abs(p.getY() - h.y) < 70) list.add(p);
        }
        // los que se fueron: limpiar su locura
        for (UUID id : new ArrayList<>(madness.keySet())) {
            if (list.stream().noneMatch(p -> p.getUUID().equals(id))) {
                madness.remove(id);
                if (sl.getPlayerByUUID(id) instanceof ServerPlayer gone) PacketDistributor.sendToPlayer(gone, new EyeMadnessPayload(getId(), 0F));
            }
        }
        return list;
    }

    protected LivingEntity pickTarget(List<ServerPlayer> arena) {
        LivingEntity t = getTarget();
        if (t instanceof ServerPlayer sp && arena.contains(sp) && !sp.isCreative()) return t;
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : arena) {
            double d = p.distanceToSqr(this) * (p.isCreative() ? 100 : 1);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        setTarget(best != null && !best.isCreative() ? best : null);
        return best;
    }

    protected void setState(int s, List<ServerPlayer> arena) {
        entityData.set(DATA_STATE, (byte) s);
        stateTick = 0;
        ServerLevel sl = (ServerLevel) level();
        LivingEntity target = pickTarget(arena);
        switch (s) {
            case S_GAZE_CHARGE -> {
                beamAim = target.getEyePosition();
                attacksSinceExposed = 0;
                playSound(ModSounds.EYE_CHARGE.get(), 8F, 1F);
            }
            case S_SWEEP_CHARGE -> {
                Vec3 h = getHome();
                sweepAngle = Math.toDegrees(Math.atan2(target.getZ() - h.z, target.getX() - h.x)) + 50;
                sweepDir = random.nextBoolean() ? 1 : -1;
                attacksSinceExposed = 0;
                playSound(ModSounds.EYE_CHARGE.get(), 8F, 0.8F);
            }
            case S_GAZE, S_SWEEP -> {
                beamHit.clear();
                playSound(ModSounds.EYE_BEAM.get(), 10F, s == S_SWEEP ? 0.8F : 1F);
            }
            case S_EXPOSED -> {
                playSound(ModSounds.EYE_SCREAM.get(), 4F, 1.6F);
                sl.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, getX(), getY() + 2, getZ(), 40, 4, 1, 4, 0);
            }
            case S_TENTACLES -> spawnTentacles(sl, arena);
            case S_WATCHERS -> spawnWatchers(sl, arena);
            case S_CALL -> callMinions(sl, target);
            case S_PULL -> playSound(ModSounds.EYE_PULSE.get(), 10F, 1F);
            case S_WAVE -> {
                waveHit.clear();
                playSound(ModSounds.EYE_WAVE.get(), 10F, 1F);
                Vec3 h = getHome();
                sl.sendParticles(ParticleTypes.SONIC_BOOM, h.x, h.y + 1, h.z, 1, 0, 0, 0, 0);
            }
            case S_SCREAM -> scream(sl, arena);
            case S_ASCEND -> {
                for (ServerPlayer p : arena) PacketDistributor.sendToPlayer(p, new EyeTitlePayload(EyeTitlePayload.ASCEND));
                playSound(ModSounds.EYE_SCREAM.get(), 16F, 0.5F);
                playSound(ModSounds.EYE_PULSE.get(), 16F, 0.6F);
                Vec3 c = center();
                sl.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 8, 5, 5, 5, 0);
            }
            default -> onExtraState(s, arena, target);
        }
    }

    protected void setIdle(int cd) {
        entityData.set(DATA_STATE, (byte) S_IDLE);
        stateTick = 0;
        cooldown = cd;
    }

    protected int cooldownTicks() {
        return switch (getPhase()) {
            case 1 -> 45 + random.nextInt(25);
            case 2 -> 32 + random.nextInt(20);
            default -> 22 + random.nextInt(15);
        };
    }

    protected int exposedTicks() {
        return switch (getPhase()) {
            case 1 -> 110;
            case 2 -> 90;
            default -> 70;
        };
    }

    protected void chooseAttack(List<ServerPlayer> arena, LivingEntity target) {
        int ph = getPhase();
        attacksSinceExposed++;
        if (attacksSinceExposed > (ph == 3 ? 2 : 3)) {
            setState(ph == 3 && random.nextBoolean() ? S_SWEEP_CHARGE : S_GAZE_CHARGE, arena);
            return;
        }
        int minions = level().getEntitiesOfClass(VoidSkeleton.class, new AABB(BlockPos.containing(getHome())).inflate(60)).size();
        int[][] table = switch (ph) {
            case 1 -> new int[][]{{S_GAZE_CHARGE, 4}, {S_TENTACLES, 4}, {S_CALL, minions < 4 ? 2 : 0}};
            case 2 -> new int[][]{{S_GAZE_CHARGE, 3}, {S_TENTACLES, 3}, {S_WATCHERS, 3}, {S_PULL, 2}, {S_CALL, minions < 4 ? 1 : 0}};
            default -> new int[][]{{S_SWEEP_CHARGE, 3}, {S_GAZE_CHARGE, 2}, {S_PULL, 3}, {S_WATCHERS, 2}, {S_TENTACLES, 2}};
        };
        int total = 0;
        for (int[] e : table) total += e[1];
        int roll = random.nextInt(total);
        for (int[] e : table) {
            roll -= e[1];
            if (roll < 0) {
                setState(e[0], arena);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ movimiento y mirada

    protected void hover() {
        Vec3 h = getHome();
        int s = getState();
        double baseY = h.y + SanctumDesign.EYE_HEIGHT - RADIUS;
        double y = s == S_EXPOSED ? h.y + 2.6 : s == S_SCREAM ? baseY + 5 : s == S_ASCEND ? baseY + 6 + stateTick * 0.06 : baseY;
        if (isDeadOrDying()) y = baseY + Math.min(10, deathTime * 0.08);
        double bob = s == S_EXPOSED ? 0.3 * Math.sin(tickCount * 0.12) : 0.9 * Math.sin(tickCount * 0.045);
        double a = tickCount * 0.006, drift = s == S_EXPOSED ? 0 : 2.5;
        Vec3 goal = new Vec3(h.x + Math.cos(a) * drift, y + bob, h.z + Math.sin(a) * drift);
        Vec3 next = position().lerp(goal, s == S_EXPOSED ? 0.07 : 0.045);
        setPos(next.x, next.y, next.z);
        setDeltaMovement(Vec3.ZERO);
    }

    protected void lookAt(Vec3 p, float maxStep) {
        Vec3 d = p.subtract(center());
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        float wantYaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        float wantPitch = (float) (-Mth.atan2(d.y, horiz) * Mth.RAD_TO_DEG);
        float y = lookYaw() + Mth.clamp(Mth.wrapDegrees(wantYaw - lookYaw()), -maxStep, maxStep);
        float pt = lookPitch() + Mth.clamp(wantPitch - lookPitch(), -maxStep, maxStep);
        entityData.set(DATA_YAW, Mth.wrapDegrees(y));
        entityData.set(DATA_PITCH, Mth.clamp(pt, -80F, 85F));
        setYRot(y);
        yHeadRot = y;
        yBodyRot = y;
    }

    protected void trackAim(LivingEntity target, double k) {
        Vec3 t = target.position().add(0, target.getBbHeight() * 0.55, 0);
        beamAim = beamAim.lerp(t, k);
    }

    protected Vec3 sweepPoint() {
        Vec3 h = getHome();
        double a = Math.toRadians(sweepAngle);
        return new Vec3(h.x + Math.cos(a) * SWEEP_RADIUS, h.y + 0.7, h.z + Math.sin(a) * SWEEP_RADIUS);
    }

    // ------------------------------------------------------------------ Mirada

    protected Vec3 beamOrigin() {
        Vec3 c = center();
        return c.add(beamAim.subtract(c).normalize().scale(eyeRadius() * 0.95));
    }

    protected void updateBeam() {
        Vec3 o = beamOrigin();
        Vec3 dir = beamAim.subtract(center()).normalize();
        Vec3 far = o.add(dir.scale(140));
        BlockHitResult hit = level().clip(new ClipContext(o, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
        entityData.set(DATA_BEAM, new Vector3f((float) end.x, (float) end.y, (float) end.z));
    }

    protected void damageBeam(ServerLevel sl, float damage) {
        Vec3 o = beamOrigin(), end = getBeamEnd();
        AABB box = new AABB(o, end).inflate(2.5);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !ally(e))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
            double d = distToSegment(c, o, end);
            if (d > 1.3 + e.getBbWidth() / 2 + e.getBbHeight() / 4) continue;
            int last = beamHit.getOrDefault(e.getId(), -100);
            if (tickCount - last < 8) continue;
            beamHit.put(e.getId(), tickCount);
            e.hurt(damageSources().indirectMagic(this, this), damage);
            e.addEffect(new MobEffectInstance(ModEffects.ASTRAL_BURN, 60, 0), this);
            if (e instanceof ServerPlayer p) addMadness(p, 10F);
        }
        if (tickCount % 2 == 0) {
            sl.sendParticles(ParticleTypes.DRAGON_BREATH, end.x, end.y, end.z, 6, 0.6, 0.3, 0.6, 0.02);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, end.x, end.y, end.z, 3, 0.4, 0.2, 0.4, 0.05);
        }
        if (stateTick % 25 == 0) playSound(ModSounds.EYE_BEAM.get(), 8F, getState() == S_SWEEP ? 0.8F : 1F);
    }

    protected static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / Math.max(1e-6, ab.lengthSqr()), 0, 1);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    // ------------------------------------------------------------------ Tentáculos, Vigías, Llamado

    protected void spawnTentacles(ServerLevel sl, List<ServerPlayer> arena) {
        Vec3 h = getHome();
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : arena) if (!p.isCreative()) spots.add(p.position());
        int extra = 2 + getPhase();
        for (int i = 0; i < extra; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 12 + random.nextDouble() * 30;
            spots.add(new Vec3(h.x + Math.cos(a) * r, h.y, h.z + Math.sin(a) * r));
        }
        int n = 0;
        for (Vec3 s : spots) {
            if (n++ >= 10) break;
            Double gy = groundY(s.x, s.z, h.y);
            if (gy == null) continue;
            EyeTentacle t = ModEntities.EYE_TENTACLE.get().create(sl);
            if (t == null) continue;
            t.moveTo(s.x, gy, s.z, random.nextFloat() * 360F, 0F);
            sl.addFreshEntity(t);
        }
        playSound(ModSounds.EYE_TENTACLE.get(), 6F, 0.7F);
    }

    /** Altura del piso firme cerca de y0 (o null si es abismo). */
    protected Double groundY(double x, double z, double y0) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = 6; dy >= -6; dy--) {
            p.set(Mth.floor(x), Mth.floor(y0) + dy, Mth.floor(z));
            if (!level().getBlockState(p).getCollisionShape(level(), p).isEmpty() && level().getBlockState(p.above()).getCollisionShape(level(), p.above()).isEmpty()) {
                return (double) p.getY() + 1;
            }
        }
        return null;
    }

    protected void spawnWatchers(ServerLevel sl, List<ServerPlayer> arena) {
        int count = Math.min(10, (getPhase() == 3 ? 6 : 4) + arena.size() - 1);
        for (int i = 0; i < count; i++) {
            WatcherEye w = ModEntities.WATCHER_EYE.get().create(sl);
            if (w == null) continue;
            ServerPlayer target = arena.get(random.nextInt(arena.size()));
            w.setup(this, target, (float) (Math.PI * 2 * i / count), getPhase() == 3 ? 0.55 : 0.45);
            sl.addFreshEntity(w);
        }
        playSound(SoundEvents.ENDER_EYE_LAUNCH, 6F, 0.4F);
        playSound(ModSounds.EYE_WHISPER.get(), 8F, 0.7F);
    }

    protected void callMinions(ServerLevel sl, LivingEntity target) {
        Vec3 h = getHome();
        int n = 2 + random.nextInt(2);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 16 + random.nextDouble() * 22;
            double x = h.x + Math.cos(a) * r, z = h.z + Math.sin(a) * r;
            Double gy = groundY(x, z, h.y);
            if (gy == null) continue;
            VoidSkeleton s = (random.nextFloat() < 0.4F ? ModEntities.VOID_ARCHER : ModEntities.VOID_SENTINEL).get().create(sl);
            if (s == null) continue;
            s.moveTo(x, gy, z, random.nextFloat() * 360F, 0F);
            s.finalizeSpawn(sl, sl.getCurrentDifficultyAt(BlockPos.containing(x, gy, z)), MobSpawnType.MOB_SUMMONED, null);
            if (target != null && !(target instanceof Player p && p.isCreative())) s.setTarget(target);
            sl.addFreshEntity(s);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, x, gy + 0.5, z, 60, 0.4, 0.8, 0.4, 0.2);
            sl.sendParticles(ParticleTypes.SQUID_INK, x, gy + 0.5, z, 25, 0.4, 0.6, 0.4, 0.02);
            sl.playSound(null, x, gy, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.5F, 0.5F);
        }
    }

    // ------------------------------------------------------------------ Singularidad

    protected void tickPull(List<ServerPlayer> arena) {
        Vec3 h = getHome();
        double k = 0.03 + 0.05 * stateTick / PULL_TICKS;
        for (ServerPlayer p : arena) {
            if (p.isCreative()) continue;
            Vec3 d = new Vec3(h.x - p.getX(), 0, h.z - p.getZ());
            double len = d.length();
            if (len < 3 || len > 70) continue;
            double s = k * (p.isShiftKeyDown() ? 0.5 : 1.0);
            p.push(d.x / len * s, 0, d.z / len * s);
            p.hurtMarked = true;
        }
    }

    protected void tickWave(ServerLevel sl, List<ServerPlayer> arena) {
        Vec3 h = getHome();
        double radius = WAVE_START + stateTick * WAVE_SPEED;
        for (ServerPlayer p : arena) {
            if (p.isCreative() || waveHit.contains(p.getUUID())) continue;
            double dx = p.getX() - h.x, dz = p.getZ() - h.z, d = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(d - radius) > 1.3 || p.getY() > h.y + 0.75 || p.getY() < h.y - 3) continue;
            waveHit.add(p.getUUID());
            p.hurt(damageSources().indirectMagic(this, this), 14F);
            p.setDeltaMovement(dx / d * 2.2, 0.75, dz / d * 2.2);
            p.hurtMarked = true;
            addMadness(p, 12F);
        }
        if (stateTick % 3 == 0) {
            int n = (int) (radius * 1.2);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, h.x + Math.cos(a) * radius, h.y + 0.3, h.z + Math.sin(a) * radius, 1, 0, 0.2, 0, 0.02);
            }
        }
    }

    // ------------------------------------------------------------------ Grito (cambio de fase)

    protected void scream(ServerLevel sl, List<ServerPlayer> arena) {
        playSound(ModSounds.EYE_SCREAM.get(), 14F, 0.8F);
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 10F, 0.5F);
        Vec3 c = center();
        sl.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 6, 4, 4, 4, 0);
        for (ServerPlayer p : arena) {
            if (p.isCreative()) continue;
            Vec3 d = p.position().subtract(c.x, p.getY(), c.z);
            double len = Math.max(1, d.length());
            p.setDeltaMovement(d.x / len * 1.4, 0.45, d.z / len * 1.4);
            p.hurtMarked = true;
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 90, 0), this);
            addMadness(p, 30F);
        }
    }

    // ------------------------------------------------------------------ Despertar

    protected void tickAwaken(ServerLevel sl, List<ServerPlayer> arena) {
        lookAt(pickTarget(arena).getEyePosition(), 1.5F);
        if (stateTick == 1) {
            for (ServerPlayer p : arena) PacketDistributor.sendToPlayer(p, new EyeTitlePayload(EyeTitlePayload.AWAKEN));
            playSound(ModSounds.EYE_AWAKEN.get(), 14F, 1F);
        }
        if (stateTick % 30 == 0 && stateTick < 120) playSound(SoundEvents.WARDEN_HEARTBEAT, 10F, 0.4F);
        if (stateTick == 120) playSound(SoundEvents.ENDER_EYE_DEATH, 8F, 0.3F);
        if (stateTick == 150) scream(sl, arena);
        if (stateTick >= AWAKEN_TICKS) setIdle(30);
    }

    // ------------------------------------------------------------------ Ascenso (fase final)

    protected boolean canAscend() {
        return com.agustin.bloodmoon.BloodMoonConfig.EYE_FINAL_PHASE.get();
    }

    /** Estados propios de subclases. */
    protected void onExtraState(int s, List<ServerPlayer> arena, LivingEntity target) {}

    /** Todo cae hacia el Ojo; la pupila se abre como una grieta y al final arrastra a todos al Más Allá. */
    protected void tickAscend(ServerLevel sl, List<ServerPlayer> arena) {
        if (!arena.isEmpty()) lookAt(pickTarget(arena).getEyePosition(), 4F);
        Vec3 h = getHome();
        double k = 0.05 + 0.1 * stateTick / ASCEND_TICKS;
        for (ServerPlayer p : arena) {
            if (p.isCreative()) continue;
            Vec3 d = new Vec3(h.x - p.getX(), 0, h.z - p.getZ());
            double len = d.length();
            if (len < 2) continue;
            p.push(d.x / len * k, 0.02, d.z / len * k);
            p.hurtMarked = true;
        }
        if (stateTick % 20 == 0) playSound(SoundEvents.WARDEN_HEARTBEAT, 14F, 0.3F + stateTick / 200F);
        if (stateTick % 5 == 0) {
            Vec3 c = center();
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 120, 12, 12, 12, 1.5);
        }
        if (stateTick >= ASCEND_TICKS) {
            if (com.agustin.bloodmoon.world.BeyondRift.drag(sl, this, arena)) {
                discard();
            } else {
                hurt(damageSources().genericKill(), Float.MAX_VALUE);   // sin Más Allá: muere aquí
            }
        }
    }

    // ------------------------------------------------------------------ Locura

    protected boolean looksAtMe(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 to = center().subtract(eye);
        double d = to.length();
        double ang = Math.acos(Mth.clamp(p.getViewVector(1F).dot(to.scale(1 / d)), -1, 1));
        if (ang > Math.atan(eyeRadius() / Math.max(1, d)) + 0.22) return false;
        if (tickCount % 4 == 0 || !sight.containsKey(p.getUUID())) {
            BlockHitResult hit = level().clip(new ClipContext(eye, center(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            sight.put(p.getUUID(), hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(eye) > d - eyeRadius() - 0.5);
        }
        return sight.get(p.getUUID());
    }

    public void addMadness(ServerPlayer p, float amount) {
        madness.merge(p.getUUID(), amount, Float::sum);
    }

    protected void updateMadness(ServerLevel sl, List<ServerPlayer> arena) {
        int s = getState();
        float rate = switch (getPhase()) {
            case 1 -> 0.55F;
            case 2 -> 0.8F;
            default -> 1.05F;
        };
        boolean calm = s == S_AWAKEN || s == S_EXPOSED;
        for (ServerPlayer p : arena) {
            float m = madness.getOrDefault(p.getUUID(), 0F);
            if (p.isCreative()) m = 0;
            else if (looksAtMe(p) && !calm) m += rate;
            else m -= calm ? 0.8F : 1.1F;
            m = Mth.clamp(m, 0F, 100F);
            if (m >= 100F) {
                breakMind(p);
                m = 35F;
            }
            madness.put(p.getUUID(), m);
            if (tickCount % 2 == 0) PacketDistributor.sendToPlayer(p, new EyeMadnessPayload(getId(), m));
        }
    }

    protected void breakMind(ServerPlayer p) {
        p.hurt(damageSources().magic(), getPhase() == 3 ? 12F : 8F);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), this);
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0), this);
        p.playNotifySound(ModSounds.EYE_SCREAM.get(), SoundSource.HOSTILE, 0.9F, 1.5F);
        p.displayClientMessage(Component.translatable("bloodmoon.eye.break").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
    }

    // ------------------------------------------------------------------ daño

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        int s = getState();
        if (s == S_AWAKEN || s == S_SCREAM || s == S_ASCEND || isDeadOrDying()) return false;
        Entity attacker = source.getEntity();
        if (attacker != null && ally(attacker)) return false;
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FIRE)) return false;
        float mult = s == S_EXPOSED ? 1.25F : GUARDED;
        if (mult < 1F && level() instanceof ServerLevel sl) {
            Vec3 at = source.getSourcePosition() != null ? source.getSourcePosition() : center();
            Vec3 c = center();
            Vec3 surf = c.add(at.subtract(c).normalize().scale(eyeRadius()));
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, surf.x, surf.y, surf.z, 12, 0.4, 0.4, 0.4, 0.3);
            playSound(SoundEvents.AMETHYST_BLOCK_HIT, 3F, 0.4F);
        }
        return super.hurt(source, amount * mult);
    }

    /** Daño sin los multiplicadores de defensa (para subclases). */
    protected boolean hurtRaw(DamageSource source, float amount) {
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {}

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isAlwaysExperienceDropper() {
        return true;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected float getSoundVolume() {
        return 6F;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }

    // ------------------------------------------------------------------ muerte: implosión

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        if (!lootReleased) {
            pendingLoot = source;
            return;
        }
        super.dropAllDeathLoot(level, source);
    }

    @Override
    public void die(DamageSource source) {
        if (canAscend() && !ascended && level() instanceof ServerLevel asl) {
            ascended = true;              // no muere: abre la Grieta y se lleva a todos
            setHealth(1F);
            setState(S_ASCEND, arenaPlayers(asl));
            return;
        }
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            entityData.set(DATA_STATE, (byte) S_IDLE);
            AABB box = new AABB(BlockPos.containing(getHome())).inflate(80);
            for (EyeTentacle t : sl.getEntitiesOfClass(EyeTentacle.class, box)) t.discard();
            bossEvent.setProgress(0F);
            for (WatcherEye w : sl.getEntitiesOfClass(WatcherEye.class, box)) w.pop(false);
            playSound(ModSounds.EYE_SCREAM.get(), 16F, 0.6F);
            for (UUID id : madness.keySet()) {
                if (sl.getPlayerByUUID(id) instanceof ServerPlayer p) PacketDistributor.sendToPlayer(p, new EyeMadnessPayload(getId(), 0F));
            }
            madness.clear();
        }
    }

    /**
     * El Ojo se agrieta y tiembla; la luz escapa por las grietas, se encoge hasta un punto e implosiona.
     * Después, oscuridad total y la despedida en la lengua de la Grieta (EyeTitlePayload.FAREWELL).
     */
    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (!(level() instanceof ServerLevel sl)) return;
        hover();
        Vec3 c = center();
        if (deathTime % 4 == 0) {
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 10, eyeRadius() * 0.5, eyeRadius() * 0.5, eyeRadius() * 0.5, 0.3);
            sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 8, eyeRadius() * 0.6, eyeRadius() * 0.6, eyeRadius() * 0.6, 0.05);
        }
        if (deathTime == 20) playSound(ModSounds.EYE_IMPLODE.get(), 16F, 1F);
        if (deathTime % 25 == 0 && deathTime < DEATH_TICKS - 20) playSound(SoundEvents.WARDEN_HEARTBEAT, 12F, 0.4F + deathTime / 300F);
        if (deathTime == DEATH_TICKS) {
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 3, 1, 1, 1, 0);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 400, 6, 6, 6, 1.2);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 16F, 0.3F);
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(c) > ARENA_RANGE * ARENA_RANGE * 1.6) continue;
                PacketDistributor.sendToPlayer(p, new EyeTitlePayload(EyeTitlePayload.FAREWELL));
                if (p.isCreative() || p.isSpectator()) continue;
                Vec3 d = p.position().subtract(c);
                double len = Math.max(1, d.length());
                p.setDeltaMovement(d.x / len * 1.6, 0.6, d.z / len * 1.6);
                p.hurtMarked = true;
            }
            EyeSanctums.markDefeated(sl, getHome());
            lootReleased = true;
            Vec3 h = getHome();
            setPos(h.x, h.y + 2.5, h.z);
            dropAllDeathLoot(sl, pendingLoot != null ? pendingLoot : damageSources().generic());
            remove(RemovalReason.KILLED);
        }
    }

    // ------------------------------------------------------------------ cliente

    protected void clientParticles() {
        Level lv = level();
        Vec3 c = center();
        int s = getState();
        if (isDeadOrDying()) {
            for (int i = 0; i < 6; i++) {
                Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
                Vec3 p = c.add(d.scale(eyeRadius()));
                lv.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, p.x, p.y, p.z, d.x * 0.6, d.y * 0.6, d.z * 0.6);
            }
            return;
        }
        if (random.nextInt(2) == 0) {
            Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(eyeRadius() + 1);
            lv.addParticle(ParticleTypes.SQUID_INK, c.x + d.x, c.y + d.y, c.z + d.z, 0, -0.02, 0);
        }
        Vec3 h = getHome();
        if (s == S_PULL) {
            for (int i = 0; i < 14; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = 20 + random.nextDouble() * 30;
                lv.addAlwaysVisibleParticle(ParticleTypes.PORTAL, true, h.x, h.y + 1 + random.nextDouble() * 3, h.z,
                        Math.cos(a) * r, random.nextDouble() * 4, Math.sin(a) * r);
            }
        } else if (s == S_WAVE) {
            double radius = WAVE_START + (tickCount - clientStateStart) * WAVE_SPEED;
            int n = (int) (radius * 1.5);
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                lv.addAlwaysVisibleParticle(ParticleTypes.SOUL_FIRE_FLAME, true, h.x + Math.cos(a) * radius, h.y + 0.15 + random.nextDouble() * 0.6,
                        h.z + Math.sin(a) * radius, Math.cos(a) * 0.1, 0.02, Math.sin(a) * 0.1);
            }
        } else if (s == S_EXPOSED && random.nextInt(2) == 0) {
            lv.addParticle(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, c.x + random.nextGaussian() * 4, c.y - eyeRadius() * 0.8, c.z + random.nextGaussian() * 4, 0, 0, 0);
        } else if (s == S_AWAKEN) {
            for (int i = 0; i < 4; i++) {
                Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(eyeRadius() + 12);
                lv.addAlwaysVisibleParticle(ParticleTypes.PORTAL, true, c.x, c.y, c.z, d.x, d.y, d.z);
            }
        }
        if (isFiring()) {
            Vec3 e = getBeamEnd();
            for (int i = 0; i < 3; i++) {
                lv.addAlwaysVisibleParticle(ParticleTypes.DRAGON_BREATH, true, e.x, e.y + 0.2, e.z,
                        random.nextGaussian() * 0.08, 0.05 + random.nextDouble() * 0.1, random.nextGaussian() * 0.08);
            }
        }
    }

    // ------------------------------------------------------------------ guardado y barra

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossEvent.removeAllPlayers();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        Vec3 h = getHome();
        tag.putDouble("HomeX", h.x);
        tag.putDouble("HomeY", h.y);
        tag.putDouble("HomeZ", h.z);
        tag.putByte("Phase", (byte) getPhase());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) setHome(new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ")));
        entityData.set(DATA_PHASE, (byte) Mth.clamp(tag.getByte("Phase"), 1, 3));
        entityData.set(DATA_STATE, (byte) S_IDLE);
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }
}
