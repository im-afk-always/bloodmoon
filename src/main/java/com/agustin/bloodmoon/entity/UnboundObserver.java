package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.network.EyeTitlePayload;
import com.agustin.bloodmoon.registry.ModDimensions;
import com.agustin.bloodmoon.registry.ModSounds;
import com.agustin.bloodmoon.world.BeyondRift;
import com.agustin.bloodmoon.world.EyeSanctums;
import com.agustin.bloodmoon.world.VoidImpact;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.List;

/**
 * El Observador Desatado: la forma final, en el Más Allá de la Grieta. Una mole tentacular y musculosa de
 * ~33 bloques que camina sobre seis tentáculos; el Ojo le ocupa la parte superior del cuerpo.
 *
 * - Golpe: levanta un brazo y lo estrella donde estabas (aviso en el piso), dejando un cráter.
 * - Mirada desde el pecho, Tentáculos del Abismo y Ojos Vigías (como en el Santuario).
 * - Ojo Colosal: alza los brazos y abre en el cielo un ojo inmenso; un círculo te persigue por el piso,
 *   se fija y cae un rayo de 20 bloques de radio que perfora la llanura hasta el vacío. Después queda
 *   arrodillado, exhausto (vulnerable).
 * - Al 50% grita y todo se acelera. Al morir se derrumba, el ojo implosiona y todos vuelven al Santuario.
 */
public class UnboundObserver extends VoidEye {
    public static final String UNBOUND_KEY = "entity.bloodmoon.unbound_observer";
    public static final int S_EMERGE = 20, S_SLAM = 21, S_COLOSSAL = 22;
    public static final int EMERGE_TICKS = 140, SLAM_TICKS = 34, SLAM_HIT = 20, COLOSSAL_TICKS = 50, EXPOSED_TICKS = 110,
            RETURN_DELAY = 40;
    public static final float EYE_Y = 26F, EYE_R = 6.5F, KNEEL = 6F;
    private static final double SPEED = 0.17, LEASH = 90;

    private static final EntityDataAccessor<Byte> DATA_ARM = SynchedEntityData.defineId(UnboundObserver.class, EntityDataSerializers.BYTE);

    private int colossalCd = 260;
    private ResourceKey<Level> returnDim;
    private Vec3 returnHome;

    public UnboundObserver(EntityType<? extends UnboundObserver> type, Level level) {
        super(type, level);
        this.xpReward = 3000;
        this.bossEvent.setName(Component.translatable(UNBOUND_KEY).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        this.ascended = true;
        this.entityData.set(DATA_STATE, (byte) S_EMERGE);
        this.entityData.set(DATA_PHASE, (byte) 4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ARM, (byte) 1);
    }

    public void setReturn(ResourceKey<Level> dim, Vec3 home) {
        this.returnDim = dim;
        this.returnHome = home;
    }

    /** Brazo del golpe: 1 derecho, -1 izquierdo. */
    public int slamArm() {
        return entityData.get(DATA_ARM);
    }

    public boolean isKneeling() {
        return getState() == S_EXPOSED;
    }

    @Override
    public float eyeRadius() {
        return EYE_R;
    }

    @Override
    public Vec3 center() {
        return position().add(0, EYE_Y - (isKneeling() ? KNEEL : 0), 0);
    }

    @Override
    protected boolean canAscend() {
        return false;
    }

    // ------------------------------------------------------------------ IA

    @Override
    protected void customServerAiStep() {
        if (!(level() instanceof ServerLevel sl)) return;
        if (home == null) setHome(position());
        bossEvent.setProgress(getHealth() / getMaxHealth());
        BossBars.update(this, bossEvent, ARENA_RANGE + 60);

        List<ServerPlayer> arena = arenaPlayers(sl);
        if (arena.isEmpty()) {
            if (++noPlayerTicks > 400) {         // perdieron: la Grieta se cierra
                madness.clear();
                discard();
            }
            return;
        }
        noPlayerTicks = 0;
        stateTick++;

        int state = getState();
        if (getPhase() == 4 && getHealth() < getMaxHealth() * 0.5F && state != S_EMERGE && state != S_SCREAM) {
            entityData.set(DATA_PHASE, (byte) 5);
            setState(S_SCREAM, arena);
            state = S_SCREAM;
        }
        LivingEntity target = pickTarget(arena);
        updateMadness(sl, arena);
        if (state != S_COLOSSAL && state != S_EMERGE) colossalCd--;

        switch (state) {
            case S_EMERGE -> tickEmerge(sl, arena, target);
            case S_IDLE -> {
                lookAt(target.getEyePosition(), 5F);
                walkToward(target, 9);
                if (--cooldown <= 0) chooseAttack(arena, target);
            }
            case S_SLAM -> tickSlam(sl, arena);
            case S_GAZE_CHARGE -> {
                trackAim(target, 0.08);
                lookAt(beamAim, 30F);
                faceToward(beamAim, 4F);
                updateBeam();
                if (stateTick >= 26) setState(S_GAZE, arena);
            }
            case S_GAZE -> {
                trackAim(target, 0.06 + 0.02 * (getPhase() - 3));
                lookAt(beamAim, 30F);
                faceToward(beamAim, 3F);
                updateBeam();
                damageBeam(sl, 9F);
                if (stateTick >= GAZE_TICKS) setIdle(cooldownTicks());
            }
            case S_TENTACLES -> {
                lookAt(target.getEyePosition(), 3F);
                if (stateTick >= 40) setIdle(cooldownTicks());
            }
            case S_WATCHERS -> {
                lookAt(target.getEyePosition(), 3F);
                if (stateTick >= 30) setIdle(cooldownTicks());
            }
            case S_COLOSSAL -> tickColossal(sl, arena);
            case S_EXPOSED -> {
                lookAt(center().add(dirFrom(lookYaw(), 35F).scale(20)), 1.5F);
                if (tickCount % 20 == 0) playSound(SoundEvents.WARDEN_HEARTBEAT, 8F, 0.4F);
                if (stateTick >= EXPOSED_TICKS) setIdle(20);
            }
            case S_SCREAM -> {
                lookAt(center().add(Mth.sin(tickCount * 0.9F) * 8, 30, Mth.cos(tickCount * 0.7F) * 8), 20F);
                if (stateTick >= SCREAM_TICKS) setIdle(25);
            }
            default -> setIdle(20);
        }
        keepOnPlain();
    }

    @Override
    protected int cooldownTicks() {
        return getPhase() >= 5 ? 20 + random.nextInt(16) : 30 + random.nextInt(20);
    }

    @Override
    protected void chooseAttack(List<ServerPlayer> arena, LivingEntity target) {
        if (colossalCd <= 0 && level().getEntitiesOfClass(ColossalEye.class, getBoundingBox().inflate(200, 300, 200)).isEmpty()) {
            setState(S_COLOSSAL, arena);
            return;
        }
        double d = horizontalDist(target);
        int ph = getPhase();
        int[][] table = {
                {S_SLAM, d < 22 ? 5 : 1},
                {S_GAZE_CHARGE, 3},
                {S_TENTACLES, ph >= 5 ? 3 : 2},
                {S_WATCHERS, ph >= 5 ? 3 : 2}};
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

    @Override
    protected void onExtraState(int s, List<ServerPlayer> arena, LivingEntity target) {
        if (s == S_SLAM) {
            Vec3 p = target != null ? target.position() : position();
            entityData.set(DATA_BEAM, new Vector3f((float) p.x, (float) getHome().y, (float) p.z));
            entityData.set(DATA_ARM, (byte) (random.nextBoolean() ? 1 : -1));
            playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 6F, 0.4F);
        } else if (s == S_COLOSSAL) {
            playSound(ModSounds.EYE_SCREAM.get(), 14F, 0.4F);
            playSound(ModSounds.EYE_AWAKEN.get(), 14F, 1.3F);
        }
    }

    private double horizontalDist(Entity e) {
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void walkToward(LivingEntity target, double stopAt) {
        double d = horizontalDist(target);
        faceToward(target.position(), 3F);
        if (d <= stopAt) return;
        double step = Math.min(SPEED * (getPhase() >= 5 ? 1.3 : 1), d - stopAt);
        double dx = (target.getX() - getX()) / d, dz = (target.getZ() - getZ()) / d;
        setPos(getX() + dx * step, getY(), getZ() + dz * step);
    }

    private void faceToward(Vec3 p, float maxStep) {
        float want = (float) (Mth.atan2(-(p.x - getX()), p.z - getZ()) * Mth.RAD_TO_DEG);
        float y = getYRot() + Mth.clamp(Mth.wrapDegrees(want - getYRot()), -maxStep, maxStep);
        setYRot(y);
        yBodyRot = y;
        yHeadRot = y;
    }

    /** Camina "flotando" sobre la llanura (los agujeros no lo detienen) y no se aleja del círculo. */
    private void keepOnPlain() {
        Vec3 h = getHome();
        double dx = getX() - h.x, dz = getZ() - h.z, d = Math.sqrt(dx * dx + dz * dz);
        double x = getX(), z = getZ();
        if (d > LEASH) {
            x = h.x + dx / d * LEASH;
            z = h.z + dz / d * LEASH;
        }
        setPos(x, h.y, z);
        setDeltaMovement(Vec3.ZERO);
    }

    // ------------------------------------------------------------------ emerger

    private void tickEmerge(ServerLevel sl, List<ServerPlayer> arena, LivingEntity target) {
        lookAt(target.getEyePosition(), 2F);
        faceToward(target.position(), 1.5F);
        if (stateTick == 1) {
            playSound(ModSounds.EYE_AWAKEN.get(), 16F, 0.7F);
            playSound(ModSounds.EYE_TENTACLE.get(), 16F, 0.5F);
        }
        if (stateTick % 3 == 0 && stateTick < 120) {
            BlockState ground = sl.getBlockState(blockPosition().below());
            if (!ground.isAir()) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.5, getZ(), 60, 6, 0.5, 6, 0.4);
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 30, 5, 1, 5, 0.05);
        }
        if (stateTick % 25 == 0) playSound(SoundEvents.WARDEN_HEARTBEAT, 14F, 0.35F);
        if (stateTick == 125) scream(sl, arena);
        if (stateTick >= EMERGE_TICKS) setIdle(30);
    }

    // ------------------------------------------------------------------ Golpe

    private Vec3 slamPoint() {
        return getBeamEnd();
    }

    private void tickSlam(ServerLevel sl, List<ServerPlayer> arena) {
        Vec3 p = slamPoint();
        faceToward(p, 6F);
        lookAt(p, 6F);
        if (stateTick == SLAM_HIT) {
            AABB box = new AABB(p, p).inflate(6.5, 4, 6.5);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !ally(e))) {
                if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
                double dx = e.getX() - p.x, dz = e.getZ() - p.z, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
                if (d > 6.5) continue;
                e.hurt(damageSources().mobAttack(this), 18F * (getPhase() >= 5 ? 1.15F : 1F));
                e.setDeltaMovement(dx / d * 1.8, 0.7, dz / d * 1.8);
                e.hurtMarked = true;
            }
            VoidImpact.meteorCrater(sl, p, 4.5F);
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 0.5, p.z, 2, 1, 0, 1, 0);
            sl.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y + 0.5, p.z, 60, 3, 0.5, 3, 0.1);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 8F, 0.5F);
            playSound(SoundEvents.ANVIL_LAND, 5F, 0.3F);
        }
        if (stateTick >= SLAM_TICKS) setIdle(cooldownTicks());
    }

    // ------------------------------------------------------------------ Ojo Colosal

    private void tickColossal(ServerLevel sl, List<ServerPlayer> arena) {
        lookAt(center().add(0, 40, 0), 6F);
        if (stateTick == 12) {
            List<ServerPlayer> valid = arena.stream().filter(p -> !p.isCreative()).toList();
            ServerPlayer t = valid.isEmpty() ? arena.get(random.nextInt(arena.size())) : valid.get(random.nextInt(valid.size()));
            ColossalEye.summon(sl, this, t);
            colossalCd = getPhase() >= 5 ? 520 : 700;
        }
        if (stateTick >= COLOSSAL_TICKS) setIdle(30);
    }

    /** Lo llama el Ojo Colosal cuando termina su rayo: el Desatado queda de rodillas. */
    public void onColossalFired() {
        if (isDeadOrDying() || !(level() instanceof ServerLevel sl)) return;
        int s = getState();
        if (s == S_SCREAM || s == S_EMERGE) return;
        setState(S_EXPOSED, arenaPlayers(sl));
    }

    // ------------------------------------------------------------------ daño

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        int s = getState();
        if (s == S_EMERGE || s == S_SCREAM || isDeadOrDying()) return false;
        Entity attacker = source.getEntity();
        if (attacker != null && ally(attacker)) return false;
        if (attacker instanceof ColossalEye || source.getDirectEntity() instanceof ColossalEye) return false;
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FIRE)) return false;
        float mult = s == S_EXPOSED ? 1.4F : 0.4F;
        return hurtRaw(source, amount * mult);
    }

    // ------------------------------------------------------------------ muerte: se derrumba e implosiona; todos vuelven

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 c = center();
        if (deathTime % 4 == 0 && deathTime < DEATH_TICKS) {
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 12, 4, 6, 4, 0.3);
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 8, getZ(), 20, 5, 6, 5, 0.05);
        }
        if (deathTime == 20) playSound(ModSounds.EYE_IMPLODE.get(), 20F, 0.8F);
        if (deathTime % 25 == 0 && deathTime < DEATH_TICKS - 20) playSound(SoundEvents.WARDEN_HEARTBEAT, 14F, 0.4F + deathTime / 300F);
        if (deathTime == DEATH_TICKS) {
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 4, 2, 2, 2, 0);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 20F, 0.3F);
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(this) < 300 * 300) PacketDistributor.sendToPlayer(p, new EyeTitlePayload(EyeTitlePayload.FAREWELL));
            }
            ServerLevel back = returnLevel(sl);
            if (back != null && returnHome != null) EyeSanctums.markDefeated(back, returnHome);
        }
        if (deathTime == DEATH_TICKS + RETURN_DELAY) {
            lootReleased = true;
            dropAllDeathLoot(sl, pendingLoot != null ? pendingLoot : damageSources().generic());
            ServerLevel back = returnLevel(sl);
            if (back != null && returnHome != null && sl.dimension() == ModDimensions.BEYOND) {
                BeyondRift.moveLoot(sl, position(), back, returnHome.add(0, 2.2, 0));
                for (ServerPlayer p : List.copyOf(sl.players())) {
                    if (p.distanceToSqr(this) < 400 * 400 && p.getPersistentData().contains(BeyondRift.RETURN_KEY)) BeyondRift.sendBack(p);
                }
            }
            remove(RemovalReason.KILLED);
        }
    }

    private ServerLevel returnLevel(ServerLevel sl) {
        return returnDim == null ? null : sl.getServer().getLevel(returnDim);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            for (ColossalEye c : sl.getEntitiesOfClass(ColossalEye.class, getBoundingBox().inflate(200, 300, 200))) c.discard();
        }
    }

    // ------------------------------------------------------------------ guardado

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (returnDim != null && returnHome != null) {
            tag.putString("ReturnDim", returnDim.location().toString());
            tag.putDouble("ReturnX", returnHome.x);
            tag.putDouble("ReturnY", returnHome.y);
            tag.putDouble("ReturnZ", returnHome.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ReturnDim")) {
            returnDim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("ReturnDim")));
            returnHome = new Vec3(tag.getDouble("ReturnX"), tag.getDouble("ReturnY"), tag.getDouble("ReturnZ"));
        }
        entityData.set(DATA_PHASE, (byte) Math.max(4, getPhase()));
        bossEvent.setName(Component.translatable(UNBOUND_KEY).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
    }
}
