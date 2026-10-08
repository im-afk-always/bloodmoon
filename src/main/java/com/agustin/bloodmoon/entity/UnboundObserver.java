package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.network.EyeTitlePayload;
import com.agustin.bloodmoon.registry.ModBlocks;
import com.agustin.bloodmoon.registry.ModDimensions;
import com.agustin.bloodmoon.registry.ModSounds;
import com.agustin.bloodmoon.world.BeyondHoles;
import com.agustin.bloodmoon.world.BeyondRift;
import com.agustin.bloodmoon.world.EyeSanctums;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * El Observador Desatado: la forma final, en el Más Allá de la Grieta. El Ojo, liberado y gigantesco (32 bloques),
 * flota sobre la llanura rodeado de seis anillos de runas, un cinturón de bloques del Vacío, monolitos de obsidiana y
 * una cortina de tentáculos colgantes.
 *
 * - Mirada titánica: un rayo enorme que funde el piso a su paso; en la segunda mitad también Barrido de 360°.
 * - Puño del Vacío: un brazo de energía oscura brota de una grieta junto al Ojo y desciende como un puño a distancia.
 * - Tentáculos del Abismo y Ojos Vigías.
 * - Definitivos (se turnan): Ojo Colosal, Tentáculo Titánico, Las Fauces (la pupila traga el piso) y Lágrimas del Cielo
 *   (meteoros que, al tocar el suelo, se encienden, colapsan y estallan como pequeñas supernovas).
 * - Tras el Ojo Colosal y las Fauces baja exhausto (vulnerable). Al 50% grita, invoca la Palma y todo se acelera.
 * - Juicio Final (una vez, al 20%): anillos que concentran su poder y un rayo colosal desde su propio ojo que
 *   persigue a un jugador más lento de lo que se corre, abriendo una zanja hasta el vacío.
 * - Al morir implosiona y todos vuelven al Santuario con el botín.
 */
public class UnboundObserver extends VoidEye {
    public static final String UNBOUND_KEY = "entity.bloodmoon.unbound_observer";
    public static final int S_EMERGE = 20, S_FIST = 21, S_COLOSSAL = 22, S_TITAN = 23, S_MAW = 24, S_TEARS = 25, S_JUDGMENT = 26;
    /** Juicio Final (una sola vez, al 20%): carga, dispara un rayo colosal desde su propio ojo que persigue despacio, y se apaga. */
    public static final int J_CHARGE = 90, J_FIRE = 180, J_FADE = 20, J_TICKS = J_CHARGE + J_FIRE + J_FADE;
    public static final float J_R = 11F;
    /** Bloques por tick: caminando apenas se escapa (0.216), corriendo sí (0.28). */
    private static final double J_CHASE = 0.12;
    public static final int TITAN_TICKS = 40, MAW_OPEN = 30, MAW_TICKS = 150, TEARS_TICKS = 100, FIST_TICKS = 30;
    public static final int EMERGE_TICKS = 150, COLOSSAL_TICKS = 40, EXPOSED_TICKS = 110, RETURN_DELAY = 40, BIG_GAZE_TICKS = 64;
    public static final float EYE_R = 16F;
    /** Altura de la base del Ojo sobre la llanura. */
    public static final double HOVER = 24;
    private static final double SPEED = 0.11, LEASH = 70;

    private int colossalCd = 120;
    private int lastUltimate = -1;
    private boolean palmDone, judgmentDone;
    private Vec3 jAim = Vec3.ZERO, jLastHole;
    private int jTarget = -1;
    private int palmTimer;
    private final java.util.List<net.minecraft.world.entity.item.FallingBlockEntity> torn = new java.util.ArrayList<>();
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

    public void setReturn(ResourceKey<Level> dim, Vec3 home) {
        this.returnDim = dim;
        this.returnHome = home;
    }

    @Override
    public float eyeRadius() {
        return EYE_R;
    }

    /** La pelea final abarca la llanura entera: huir de la Palma no hace que el jefe se vaya. */
    @Override
    protected double arenaRange() {
        return 260;
    }

    @Override
    protected double beamHalfWidth() {
        return 3.8;
    }

    /** El Barrido gira alrededor de su propia sombra, a 46 bloques. */
    @Override
    protected Vec3 sweepPoint() {
        double a = Math.toRadians(sweepAngle);
        return new Vec3(getX() + Math.cos(a) * 46, getHome().y + 0.7, getZ() + Math.sin(a) * 46);
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
        BossBars.update(this, bossEvent, arenaRange() + 40);

        List<ServerPlayer> arena = arenaPlayers(sl);
        if (arena.isEmpty()) {
            if (++noPlayerTicks > 1200 && palmTimer <= 0) {         // perdieron (o huyeron un minuto): la Grieta se cierra
                madness.clear();
                discard();
            }
            drift(sl, null);
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
        if (!judgmentDone && getPhase() >= 5 && getHealth() <= getMaxHealth() * 0.2F && palmTimer <= 0
                && (state == S_IDLE || state == S_GAZE_CHARGE || state == S_FIST || state == S_TENTACLES || state == S_WATCHERS
                || state == S_EXPOSED)) {
            judgmentDone = true;
            setState(S_JUDGMENT, arena);
            state = S_JUDGMENT;
        }
        LivingEntity target = pickTarget(arena);
        updateMadness(sl, arena);
        if (palmTimer > 0) palmTimer--;
        else if (state == S_IDLE || state == S_GAZE || state == S_GAZE_CHARGE || state == S_TENTACLES || state == S_WATCHERS || state == S_FIST) colossalCd--;
        tickTorn();

        switch (state) {
            case S_EMERGE -> tickEmerge(sl, arena, target);
            case S_IDLE -> {
                lookAt(target.getEyePosition(), 5F);
                if (--cooldown <= 0) chooseAttack(arena, target);
            }
            case S_GAZE_CHARGE -> {
                trackAim(target, 0.09);
                lookAt(beamAim, 30F);
                updateBeam();
                if (stateTick >= 24) setState(S_GAZE, arena);
            }
            case S_GAZE -> {
                trackAim(target, 0.045 + 0.015 * (getPhase() - 3));
                lookAt(beamAim, 30F);
                updateBeam();
                damageBeam(sl, 12F);
                meltTrail(sl);
                if (stateTick >= BIG_GAZE_TICKS) setIdle(cooldownTicks());
            }
            case S_SWEEP_CHARGE -> {
                beamAim = sweepPoint();
                lookAt(beamAim, 30F);
                updateBeam();
                if (stateTick >= 26) setState(S_SWEEP, arena);
            }
            case S_SWEEP -> {
                sweepAngle += sweepDir * 360.0 / SWEEP_TICKS;
                beamAim = sweepPoint();
                lookAt(beamAim, 60F);
                updateBeam();
                damageBeam(sl, 14F);
                meltTrail(sl);
                if (stateTick >= SWEEP_TICKS) setIdle(cooldownTicks());
            }
            case S_FIST -> tickFist(sl, arena);
            case S_TENTACLES -> {
                lookAt(target.getEyePosition(), 3F);
                if (stateTick >= 30) setIdle(cooldownTicks());
            }
            case S_WATCHERS -> {
                lookAt(target.getEyePosition(), 3F);
                if (stateTick >= 24) setIdle(cooldownTicks());
            }
            case S_COLOSSAL -> tickColossal(sl, arena);
            case S_TITAN -> tickTitan(sl, arena);
            case S_MAW -> tickMaw(sl, arena);
            case S_TEARS -> tickTears(sl, arena);
            case S_JUDGMENT -> tickJudgment(sl, arena);
            case S_EXPOSED -> {
                lookAt(center().add(dirFrom(lookYaw(), 35F).scale(20)), 1.5F);
                if (tickCount % 20 == 0) playSound(SoundEvents.WARDEN_HEARTBEAT, 10F, 0.35F);
                if (stateTick >= EXPOSED_TICKS) {
                    playSound(ModSounds.EYE_CHARGE.get(), 8F, 0.5F);
                    setIdle(15);
                }
            }
            case S_SCREAM -> {
                lookAt(center().add(Mth.sin(tickCount * 0.9F) * 8, 30, Mth.cos(tickCount * 0.7F) * 8), 20F);
                if (stateTick == 30 && getPhase() >= 5 && !palmDone) {     // la Palma del Vacío
                    palmDone = true;
                    palmTimer = VoidPalm.BLAST;
                    VoidPalm.summon(sl, this, new Vec3(getX(), getHome().y, getZ()));
                }
                if (stateTick >= SCREAM_TICKS) setIdle(20);
            }
            default -> setIdle(15);
        }
        drift(sl, target);
    }

    @Override
    protected int cooldownTicks() {
        return getPhase() >= 5 ? 10 + random.nextInt(12) : 16 + random.nextInt(14);
    }

    @Override
    protected void chooseAttack(List<ServerPlayer> arena, LivingEntity target) {
        if (colossalCd <= 0 && level().getEntitiesOfClass(ColossalEye.class, getBoundingBox().inflate(200, 300, 200)).isEmpty()
                && level().getEntitiesOfClass(TitanTentacle.class, getBoundingBox().inflate(200, 100, 200)).isEmpty()) {
            int pick;
            if (getPhase() >= 5) {
                // desde la mitad de su vida, uno de cada dos definitivos es el Ojo Colosal (el doble que antes)
                int[] others = {S_TITAN, S_MAW, S_TEARS};
                if (lastUltimate != S_COLOSSAL) pick = S_COLOSSAL;
                else pick = others[random.nextInt(others.length)];
            } else {
                int[] ults = {S_COLOSSAL, S_TITAN, S_MAW, S_TEARS};
                do pick = ults[random.nextInt(ults.length)]; while (pick == lastUltimate);
            }
            lastUltimate = pick;
            colossalCd = getPhase() >= 5 ? 220 : 300;
            setState(pick, arena);
            return;
        }
        int ph = getPhase();
        int[][] table = {
                {S_FIST, 4},
                {S_GAZE_CHARGE, 3},
                {S_SWEEP_CHARGE, ph >= 5 ? 2 : 0},
                {S_TENTACLES, 2},
                {S_WATCHERS, 2}};
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
        if (s == S_FIST) {
            playSound(ModSounds.EYE_TENTACLE.get(), 12F, 0.4F);
        } else if (s == S_TITAN) {
            playSound(ModSounds.EYE_SCREAM.get(), 16F, 0.3F);
            playSound(SoundEvents.WARDEN_ROAR, 10F, 0.5F);
        } else if (s == S_MAW) {
            playSound(ModSounds.EYE_PULSE.get(), 16F, 0.5F);
            playSound(SoundEvents.WARDEN_ROAR, 10F, 0.35F);
        } else if (s == S_TEARS) {
            playSound(ModSounds.EYE_WHISPER.get(), 16F, 0.5F);
            playSound(ModSounds.EYE_AWAKEN.get(), 16F, 1.6F);
        } else if (s == S_COLOSSAL) {
            playSound(ModSounds.EYE_SCREAM.get(), 14F, 0.4F);
            playSound(ModSounds.EYE_AWAKEN.get(), 14F, 1.3F);
        } else if (s == S_JUDGMENT) {
            List<ServerPlayer> valid = arena.stream().filter(p -> !p.isCreative()).toList();
            LivingEntity t = valid.isEmpty() ? target : valid.get(random.nextInt(valid.size()));
            jTarget = t.getId();
            jAim = new Vec3(t.getX(), getHome().y, t.getZ());
            jLastHole = null;
            entityData.set(DATA_BEAM, new org.joml.Vector3f((float) jAim.x, (float) jAim.y, (float) jAim.z));
            playSound(ModSounds.EYE_SCREAM.get(), 20F, 0.3F);
            playSound(ModSounds.EYE_CHARGE.get(), 20F, 0.35F);
            playSound(SoundEvents.BEACON_POWER_SELECT, 16F, 0.4F);
            Component msg = Component.translatable("bloodmoon.judgment.warning").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
            for (ServerPlayer p : arena) p.displayClientMessage(msg, false);
        }
    }

    // ------------------------------------------------------------------ vuelo

    /** Flota sobre la llanura y se acerca despacio al objetivo; exhausto, baja casi hasta el piso. */
    private void drift(ServerLevel sl, LivingEntity target) {
        Vec3 h = getHome();
        int s = getState();
        double y;
        if (s == S_EMERGE) y = Mth.lerp(Math.min(1.0, stateTick / (double) (EMERGE_TICKS - 30)), h.y + 150, h.y + HOVER);
        else if (s == S_EXPOSED) y = h.y + 1.5;
        else if (s == S_JUDGMENT) y = h.y + HOVER + 12;
        else if (s == S_SCREAM || s == S_TEARS || s == S_COLOSSAL) y = h.y + HOVER + 6;
        else y = h.y + HOVER;
        y += (s == S_EXPOSED ? 0.3 : 1.2) * Math.sin(tickCount * 0.04);
        double x = getX(), z = getZ();
        if (target != null && s != S_EXPOSED && s != S_MAW && s != S_EMERGE && s != S_JUDGMENT && !isBeamState()) {
            double dx = target.getX() - x, dz = target.getZ() - z, d = Math.sqrt(dx * dx + dz * dz);
            if (d > 26) {
                double step = SPEED * (getPhase() >= 5 ? 1.3 : 1);
                x += dx / d * step;
                z += dz / d * step;
            }
        }
        double ox = x - h.x, oz = z - h.z, od = Math.sqrt(ox * ox + oz * oz);
        if (od > LEASH) {
            x = h.x + ox / od * LEASH;
            z = h.z + oz / od * LEASH;
        }
        double ny = Mth.lerp(s == S_EMERGE ? 0.5 : 0.05, getY(), y);
        setPos(x, ny, z);
        setDeltaMovement(Vec3.ZERO);
        float yw = lookYaw();
        setYRot(yw);
        yBodyRot = yw;
        yHeadRot = yw;
    }

    // ------------------------------------------------------------------ emerger

    private void tickEmerge(ServerLevel sl, List<ServerPlayer> arena, LivingEntity target) {
        lookAt(target.getEyePosition(), 2F);
        if (stateTick == 1) {
            playSound(ModSounds.EYE_AWAKEN.get(), 20F, 0.6F);
            playSound(ModSounds.EYE_PULSE.get(), 20F, 0.4F);
        }
        if (stateTick % 4 == 0 && stateTick < 120) {
            Vec3 c = center();
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 80, EYE_R, EYE_R, EYE_R, 0.6);
        }
        if (stateTick % 25 == 0) playSound(SoundEvents.WARDEN_HEARTBEAT, 16F, 0.3F);
        if (stateTick == 130) scream(sl, arena);
        if (stateTick >= EMERGE_TICKS) setIdle(20);
    }

    // ------------------------------------------------------------------ Mirada titánica: funde el piso

    private boolean canMelt(ServerLevel sl) {
        return sl.dimension() == ModDimensions.BEYOND || sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    private void meltTrail(ServerLevel sl) {
        if (tickCount % 2 != 0 || !canMelt(sl)) return;
        Vec3 e = getBeamEnd();
        BlockPos c = BlockPos.containing(e);
        float r = 3.4F;
        int R = Mth.ceil(r);
        BlockState rim = ModBlocks.VOID_STONE.get().defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -R; dx <= R; dx++) for (int dy = -R; dy <= R; dy++) for (int dz = -R; dz <= R; dz++) {
            double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
            if (d > r + 1) continue;
            p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
            BlockState st = sl.getBlockState(p);
            if (st.isAir() || st.getDestroySpeed(sl, p) < 0 || st.is(ModBlocks.VOID_PORTAL.get())) continue;
            if (d <= r) sl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            else if (random.nextFloat() < 0.4F) sl.setBlock(p, rim, Block.UPDATE_CLIENTS);
        }
        if (random.nextFloat() < 0.3F) {
            BlockPos f = c.below(R);
            if (sl.getBlockState(f).isAir() && !sl.getBlockState(f.below()).isAir())
                sl.setBlock(f, ModBlocks.ASTRAL_FIRE.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ Puño del Vacío

    private void tickFist(ServerLevel sl, List<ServerPlayer> arena) {
        List<ServerPlayer> valid = arena.stream().filter(p -> !p.isCreative()).toList();
        List<ServerPlayer> pool = valid.isEmpty() ? arena : valid;
        if (!pool.isEmpty()) lookAt(pool.get(0).getEyePosition(), 4F);
        if (stateTick == 4 || (stateTick == 16 && getPhase() >= 5)) {
            ServerPlayer t = pool.get(random.nextInt(pool.size()));
            VoidFist.summon(sl, this, t, stateTick == 4 ? 1 : -1);
        }
        if (stateTick >= FIST_TICKS) setIdle(cooldownTicks());
    }

    // ------------------------------------------------------------------ Ojo Colosal

    private void tickColossal(ServerLevel sl, List<ServerPlayer> arena) {
        lookAt(center().add(0, 40, 0), 6F);
        if (stateTick == 12) {
            List<ServerPlayer> valid = arena.stream().filter(p -> !p.isCreative()).toList();
            ServerPlayer t = valid.isEmpty() ? arena.get(random.nextInt(arena.size())) : valid.get(random.nextInt(valid.size()));
            ColossalEye.summon(sl, this, t);
        }
        if (stateTick >= COLOSSAL_TICKS) setIdle(30);
    }

    // ------------------------------------------------------------------ Tentáculo Titánico

    private void tickTitan(ServerLevel sl, List<ServerPlayer> arena) {
        lookAt(center().add(dirFrom(lookYaw(), 50F).scale(20)), 4F);
        if (stateTick == 14) {
            List<ServerPlayer> valid = arena.stream().filter(p -> !p.isCreative()).toList();
            ServerPlayer t = valid.isEmpty() ? arena.get(random.nextInt(arena.size())) : valid.get(random.nextInt(valid.size()));
            TitanTentacle.summon(sl, this, t);
            if (getPhase() >= 5 && valid.size() > 1) {      // en la fase final, uno por cada uno de dos jugadores
                ServerPlayer t2 = valid.get(random.nextInt(valid.size()));
                if (t2 != t) TitanTentacle.summon(sl, this, t2);
            }
        }
        if (stateTick >= TITAN_TICKS) setIdle(30);
    }

    // ------------------------------------------------------------------ Las Fauces

    /** La "boca": la pupila dilatada como un agujero negro. */
    public Vec3 mawPoint() {
        return center().add(dirFrom(lookYaw(), lookPitch()).scale(EYE_R * 1.05));
    }

    private void tickMaw(ServerLevel sl, List<ServerPlayer> arena) {
        if (!arena.isEmpty()) lookAt(pickTarget(arena).position(), stateTick < MAW_OPEN ? 4F : 0.8F);
        Vec3 m = mawPoint();
        if (stateTick >= MAW_OPEN && stateTick < MAW_TICKS - 10) {
            double k = 0.05 + 0.09 * Math.min(1.0, (stateTick - MAW_OPEN) / 60.0);
            for (ServerPlayer p : arena) {
                if (p.isCreative()) continue;
                Vec3 d = m.subtract(p.getEyePosition());
                double len = d.length();
                if (len > 90) continue;
                // cubrirse detrás de un obelisco corta la succión
                var hit = sl.clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(), m, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, p));
                if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS && hit.getLocation().distanceTo(m) > 8) continue;
                Vec3 h = new Vec3(d.x, Math.max(-0.2, d.y * 0.05), d.z).normalize().scale(k * (p.isShiftKeyDown() ? 0.6 : 1.0));
                p.push(h.x, h.y, h.z);
                p.hurtMarked = true;
                if (len < EYE_R + 6 && stateTick % 10 == 0) {
                    p.hurt(damageSources().indirectMagic(this, this), 9F);
                    addMadness(p, 8F);
                }
            }
            // arranca el piso y lo traga
            if (torn.size() < 120) for (int i = 0; i < 4; i++) tearBlock(sl, m);
            if (stateTick % 20 == 0) playSound(ModSounds.EYE_PULSE.get(), 12F, 0.4F + random.nextFloat() * 0.2F);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, m.x, m.y, m.z, 30, 1.5, 1.5, 1.5, 0.6);
        }
        if (stateTick == MAW_TICKS - 10) {               // la expulsión
            playSound(ModSounds.SUPERNOVA_BLAST.get(), 18F, 1.2F);
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, m.x, m.y, m.z, 4, 2, 2, 2, 0);
            sl.sendParticles(ParticleTypes.SONIC_BOOM, m.x, m.y, m.z, 6, 3, 3, 3, 0);
            for (ServerPlayer p : arena) {
                if (p.isCreative()) continue;
                Vec3 d = p.position().subtract(m.x, p.getY(), m.z);
                double len = Math.max(1, d.length());
                if (len > 34) continue;
                p.hurt(damageSources().indirectMagic(this, this), (float) (16 * (1 - len / 40)));
                p.setDeltaMovement(d.x / len * 2.4, 0.9, d.z / len * 2.4);
                p.hurtMarked = true;
            }
            for (var fb : torn) fb.discard();
            torn.clear();
        }
        if (stateTick >= MAW_TICKS) setState(S_EXPOSED, arena);
    }

    private void tearBlock(ServerLevel sl, Vec3 m) {
        Vec3 f = dirFrom(lookYaw(), 0F);
        double ang = Math.atan2(f.z, f.x) + (random.nextDouble() - 0.5) * 1.8;
        double r = 8 + random.nextDouble() * 30;
        int x = Mth.floor(getX() + Math.cos(ang) * r), z = Mth.floor(getZ() + Math.sin(ang) * r);
        BlockPos top = BlockPos.containing(x, getHome().y - 1, z);
        for (int dy = 2; dy >= -4; dy--) {
            BlockPos p = top.above(dy);
            BlockState st = sl.getBlockState(p);
            if (!st.isAir() && st.getDestroySpeed(sl, p) >= 0 && sl.getBlockState(p.above()).isAir()) {
                var fb = net.minecraft.world.entity.item.FallingBlockEntity.fall(sl, p, st);
                fb.disableDrop();
                fb.setNoGravity(true);
                fb.setDeltaMovement(0, 0.6, 0);
                torn.add(fb);
                return;
            }
        }
    }

    /** Los bloques arrancados vuelan hacia la boca y desaparecen al llegar. */
    private void tickTorn() {
        if (torn.isEmpty()) return;
        Vec3 m = mawPoint();
        var it = torn.iterator();
        while (it.hasNext()) {
            var fb = it.next();
            if (fb.isRemoved()) { it.remove(); continue; }
            Vec3 d = m.subtract(fb.position());
            double len = d.length();
            if (len < EYE_R * 0.5 || fb.tickCount > 110) {
                if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.SQUID_INK, fb.getX(), fb.getY(), fb.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
                fb.discard();
                it.remove();
                continue;
            }
            double sp = Math.min(2.6, 0.35 + fb.tickCount * 0.05);
            Vec3 swirl = new Vec3(-d.z, 0, d.x).normalize().scale(0.25);
            fb.setDeltaMovement(d.scale(sp / len).add(swirl));
            fb.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------ Lágrimas del Cielo

    private void tickTears(ServerLevel sl, List<ServerPlayer> arena) {
        lookAt(center().add(0, 50, 0), 5F);
        if (stateTick >= 16 && stateTick % 6 == 0 && !arena.isEmpty()) {
            ServerPlayer p = arena.get(random.nextInt(arena.size()));
            Vec3 t;
            if (random.nextFloat() < 0.4F && !p.isCreative()) t = new Vec3(p.getX(), getHome().y, p.getZ());
            else {
                double a = random.nextDouble() * Math.PI * 2, r = 6 + random.nextDouble() * 28;
                t = new Vec3(p.getX() + Math.cos(a) * r, getHome().y, p.getZ() + Math.sin(a) * r);
            }
            AbyssTear.spawn(sl, this, t, 95 + random.nextInt(25));
        }
        if (stateTick >= TEARS_TICKS) setIdle(20);
    }

    // ------------------------------------------------------------------ Juicio Final: el rayo colosal desde su propio ojo

    public boolean isJudgmentFiring() {
        return getState() == S_JUDGMENT && stateTick >= J_CHARGE && stateTick < J_CHARGE + J_FIRE;
    }

    private void tickJudgment(ServerLevel sl, List<ServerPlayer> arena) {
        Entity te = sl.getEntity(jTarget);
        LivingEntity tgt = te instanceof ServerPlayer p && p.isAlive() && arena.contains(p) && !p.isCreative() ? p : null;
        if (tgt == null && !arena.isEmpty()) {
            tgt = pickTarget(arena);
            jTarget = tgt.getId();
        }
        Vec3 h = getHome();
        // el punto de impacto persigue al objetivo, más lento que correr
        if (tgt != null && stateTick < J_CHARGE + J_FIRE) {
            Vec3 want = new Vec3(tgt.getX(), h.y, tgt.getZ());
            Vec3 d = want.subtract(jAim);
            double len = d.length();
            if (len > 1e-3) jAim = jAim.add(d.scale(Math.min(J_CHASE, len) / len));
        }
        beamAim = jAim;
        entityData.set(DATA_BEAM, new org.joml.Vector3f((float) jAim.x, (float) jAim.y, (float) jAim.z));
        lookAt(jAim, 8F);
        Vec3 c = center();

        if (stateTick < J_CHARGE) {
            if (stateTick % 20 == 0) playSound(SoundEvents.WARDEN_HEARTBEAT, 18F, 0.35F + stateTick / 200F);
            if (stateTick == J_CHARGE - 40) playSound(ModSounds.SUPERNOVA_CHARGE.get(), 20F, 0.5F);
            if (stateTick % 3 == 0) sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 40, EYE_R * 1.4, EYE_R * 1.4, EYE_R * 1.4, 0.9);
        }
        if (stateTick == J_CHARGE) {
            playSound(ModSounds.SUPERNOVA_BLAST.get(), 24F, 0.6F);
            playSound(ModSounds.EYE_BEAM.get(), 24F, 0.4F);
        }
        if (isJudgmentFiring()) {
            int ft = stateTick - J_CHARGE;
            if (ft % 25 == 0) playSound(ModSounds.EYE_BEAM.get(), 22F, 0.4F);
            if (ft % 5 == 0) judgmentBurn(sl, c);
            if (canMelt(sl) && (jLastHole == null || jLastHole.distanceTo(jAim) >= 7)) {
                BeyondHoles.start(sl, BlockPos.containing(jAim.x, h.y - 1, jAim.z), J_R);
                jLastHole = jAim;
            }
            if (stateTick % 2 == 0) sl.sendParticles(ParticleTypes.EXPLOSION, jAim.x, h.y + 1, jAim.z, 5, J_R * 0.5, 1, J_R * 0.5, 0);
            if (stateTick % 3 == 0) sl.sendParticles(ParticleTypes.END_ROD, jAim.x, h.y + 2, jAim.z, 30, J_R * 0.4, 2, J_R * 0.4, 0.6);
        }
        if (stateTick >= J_TICKS) setState(S_EXPOSED, arena);
    }

    private void judgmentBurn(ServerLevel sl, Vec3 eye) {
        Vec3 h = getHome();
        AABB box = new AABB(Math.min(jAim.x, eye.x) - J_R, h.y - 60, Math.min(jAim.z, eye.z) - J_R,
                Math.max(jAim.x, eye.x) + J_R, eye.y + J_R, Math.max(jAim.z, eye.z) + J_R);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !ally(e))) {
            if (e instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 ec = e.position().add(0, e.getBbHeight() / 2, 0);
            double dx = e.getX() - jAim.x, dz = e.getZ() - jAim.z;
            boolean inColumn = dx * dx + dz * dz <= (J_R + 0.5) * (J_R + 0.5) && e.getY() < h.y + 6;
            if (!inColumn && distToSegment(ec, eye, jAim) > J_R * 0.8) continue;
            e.hurt(damageSources().indirectMagic(this, this), 28F);
            e.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.agustin.bloodmoon.registry.ModEffects.ASTRAL_BURN, 80, 1), this);
            e.setDeltaMovement(e.getDeltaMovement().multiply(0.5, 1, 0.5).add(0, -0.3, 0));
            e.hurtMarked = true;
            if (e instanceof ServerPlayer p) addMadness(p, 12F);
        }
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
        Entity direct = source.getDirectEntity();
        if (direct instanceof ColossalEye || direct instanceof VoidPalm || direct instanceof TitanTentacle || direct instanceof AbyssTear
                || direct instanceof VoidFist || attacker instanceof ColossalEye) return false;
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FIRE)) return false;
        float mult = s == S_EXPOSED ? 1.4F : 0.4F;
        float dmg = amount * mult;
        if (!judgmentDone) {   // no puede morir sin haber lanzado el Juicio Final: la vida se frena en el 20%
            float floor = getMaxHealth() * 0.2F - 0.5F;
            if (getHealth() <= floor + 0.01F) return false;
            dmg = Math.min(dmg, getHealth() - floor);
        }
        return hurtRaw(source, dmg);
    }

    // ------------------------------------------------------------------ muerte: se derrumba e implosiona; todos vuelven

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 c = center();
        Vec3 h = getHome();
        setPos(getX(), Mth.lerp(0.02, getY(), h.y + HOVER), getZ());
        if (deathTime % 4 == 0 && deathTime < DEATH_TICKS) {
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 16, EYE_R * 0.6, EYE_R * 0.6, EYE_R * 0.6, 0.4);
            sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 20, EYE_R * 0.6, EYE_R * 0.6, EYE_R * 0.6, 0.05);
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
        for (var fb : torn) fb.discard();
        torn.clear();
        if (level() instanceof ServerLevel sl) {
            for (ColossalEye c : sl.getEntitiesOfClass(ColossalEye.class, getBoundingBox().inflate(200, 300, 200))) c.discard();
            for (VoidPalm p : sl.getEntitiesOfClass(VoidPalm.class, getBoundingBox().inflate(300, 400, 300))) p.cancel(sl);
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
        tag.putBoolean("PalmDone", palmDone);
        tag.putBoolean("JudgmentDone", judgmentDone);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ReturnDim")) {
            returnDim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("ReturnDim")));
            returnHome = new Vec3(tag.getDouble("ReturnX"), tag.getDouble("ReturnY"), tag.getDouble("ReturnZ"));
        }
        entityData.set(DATA_PHASE, (byte) Math.max(4, getPhase()));
        palmDone = tag.getBoolean("PalmDone");
        judgmentDone = tag.getBoolean("JudgmentDone");
        bossEvent.setName(Component.translatable(UNBOUND_KEY).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
    }
}
