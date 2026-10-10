package com.agustin.bloodmoon.invasion;

import com.agustin.bloodmoon.BloodMoonManager;
import com.agustin.bloodmoon.entity.FirstSoulDragon;
import com.agustin.bloodmoon.registry.ModBlocks;
import com.agustin.bloodmoon.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.List;
import java.util.Set;

/**
 * La Ofrenda (fase 4): el Dominio usa su esencia para alimentar a la Primera Alma.
 * <ol>
 *   <li><b>Santuario</b>: levanta un santuario de 5×5 chunks (muralla, torres, fosa ritual y ocho pilones).</li>
 *   <li><b>Núcleo</b>: la mitad de su ingreso alimenta un cristal gigante que crece capa a capa sobre el estrado.
 *   Romper su Corazón (el núcleo enterrado en el cristal) tira abajo lo acumulado.</li>
 *   <li><b>Despertar</b>: lleno, el cristal estalla y emerge el Dragón de la Primera Alma, con anuncio a todo el mundo.</li>
 * </ol>
 * Es opcional: si el jugador vence al Dominio antes (el Observador), nada de esto ocurre.
 */
public final class FirstSoul {
    public static final int NONE = 0, SITE = 1, FEEDING = 2, AWAKE = 3, SLAIN = 4;
    /** Esencia que hay que dar para despertarla (~3-4 días con un Dominio grande). */
    public static final double COST = 12000;
    public static final String DRAGON_TAG = "bloodmoon_soul_faction";
    private static final double SITE_COST = 1500;

    private FirstSoul() {}

    /** Parte del ingreso que se desvía a la ofrenda. */
    static double feedShare(Faction f, double income) {
        return f.soulStage == FEEDING && f.soulProgress < COST ? income * 0.5 : 0;
    }

    static void cycle(ServerLevel level, InvasionData data, Faction f, List<Long> dead, List<Long> anchors, Set<Long> changed) {
        if (f.phase < 4 || f.soulStage >= AWAKE) return;
        InvasionData.Cell site = f.soulSite == Faction.RankRecord.NO_SEAT ? null : data.cells.get(f.soulSite);
        if (f.soulStage != NONE && (site == null || site.structure != DominionStructures.SOUL || site.faction != f.id)) {
            f.soulStage = NONE;   // el santuario se perdió: vuelve a empezar
            f.soulSite = Faction.RankRecord.NO_SEAT;
            f.soulProgress = 0;
            f.soulPlaced = 0;
            site = null;
        }
        switch (f.soulStage) {
            case NONE -> chooseSite(level, data, f, dead, anchors, changed);
            case SITE -> {
                if (site.structureBuilt) {
                    f.soulStage = FEEDING;
                    f.soulY = site.coreY;
                    broadcast(level, Component.translatable("bloodmoon.soul.feeding", f.name).withStyle(ChatFormatting.DARK_PURPLE),
                            SoundEvents.BEACON_POWER_SELECT, 0.4F);
                }
            }
            case FEEDING -> {
                int pct = (int) (100 * f.soulProgress / COST);
                if (pct >= 50 && (f.soulWarned & 1) == 0) {
                    f.soulWarned |= 1;
                    broadcast(level, Component.translatable("bloodmoon.soul.half", f.name).withStyle(ChatFormatting.DARK_PURPLE),
                            ModSounds.EYE_WHISPER.get(), 0.5F);
                }
                if (pct >= 90 && (f.soulWarned & 2) == 0) {
                    f.soulWarned |= 2;
                    broadcast(level, Component.translatable("bloodmoon.soul.near", f.name).withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                            ModSounds.EYE_WHISPER.get(), 0.4F);
                }
                growCore(level, f);
                if (f.soulProgress >= COST) startEmergence(level, f);
            }
            default -> { }
        }
        data.setDirty();
    }

    // ------------------------------------------------------------------ 1) santuario

    private static void chooseSite(ServerLevel level, InvasionData data, Faction f, List<Long> dead, List<Long> anchors, Set<Long> changed) {
        if (f.essence < SITE_COST || dead.isEmpty()) return;
        int col = InvasionManager.coliseumChunks() + 7;
        ChunkPos cc = new ChunkPos(f.center);
        int r = InvasionManager.radius();
        Long best = null;
        double bestScore = -1;
        for (int i = 0; i < 400; i++) {
            long k = dead.get(level.random.nextInt(dead.size()));
            ChunkPos cp = new ChunkPos(k);
            if (InvasionManager.d2(cp, cc) <= (long) col * col) continue;
            double d = InvasionManager.distance(f, k);
            if (d < r * 0.2 || d > r * 0.6) continue;
            if (!InvasionManager.footprintFree(data, f, cp, 5)) continue;
            if (InvasionManager.footprintWet(level, f, cp, 5)) continue;   // nada de santuarios sumergidos
            boolean crowded = false;
            for (long a : anchors) if (InvasionManager.d2(new ChunkPos(a), cp) < 64) { crowded = true; break; }
            if (crowded) continue;
            double score = level.random.nextDouble();
            if (score > bestScore) { bestScore = score; best = k; }
        }
        if (best == null) return;
        InvasionData.Cell c = data.cells.get(best);
        c.structure = DominionStructures.SOUL;
        c.structureBuilt = false;
        f.essence -= SITE_COST;
        f.soulSite = best;
        f.soulStage = SITE;
        f.soulProgress = 0;
        f.soulPlaced = 0;
        f.soulWarned = 0;
        changed.add(best);
        ChunkPos cp = new ChunkPos(best);
        broadcast(level, Component.translatable("bloodmoon.soul.site", f.name, cp.getMiddleBlockX(), cp.getMiddleBlockZ())
                .withStyle(ChatFormatting.DARK_PURPLE), SoundEvents.RAID_HORN.value(), 0.4F);
    }

    // ------------------------------------------------------------------ 2) núcleo

    /** El cristal crece en proporción a lo acumulado (solo con sus chunks cargados; si no, se pone al día al cargar). */
    private static void growCore(ServerLevel level, Faction f) {
        ChunkPos cp = new ChunkPos(f.soulSite);
        if (!DominionStructures.footprintLoaded(level, cp, 1)) return;
        DominionTerraform.Plan plan = corePlan(level, f);
        List<DominionTerraform.Placement> ps = plan.placements();
        int target = (int) Math.min(ps.size(), Math.floor(ps.size() * Math.min(1.0, f.soulProgress / COST)));
        if (target <= f.soulPlaced) return;
        for (int i = f.soulPlaced; i < target; i++) {
            DominionTerraform.Placement p = ps.get(i);
            level.setBlock(p.pos(), p.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (i % 12 == 0 && !p.state().isAir()) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
            }
        }
        f.soulPlaced = target;
        level.playSound(null, new BlockPos(cp.getMinBlockX() + 8, f.soulY + 20, cp.getMinBlockZ() + 8), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.HOSTILE, 3F, 0.5F);
    }

    static DominionTerraform.Plan corePlan(ServerLevel level, Faction f) {
        ChunkPos cp = new ChunkPos(f.soulSite);
        return DominionTemplates.planAt(level, DominionTemplates.get(level, "soul_core"), cp.getMinBlockX() + 8, cp.getMinBlockZ() + 8,
                net.minecraft.world.level.block.Rotation.NONE, f.soulY);
    }

    /** El Corazón del cristal se rompió: se pierde lo acumulado y el cristal vuelve a crecer desde cero. */
    static void onHeartBroken(ServerLevel level, Faction f) {
        if (f.soulStage != FEEDING) return;
        f.soulProgress = 0;
        f.soulPlaced = 0;
        f.soulWarned = 0;
        f.essence = Math.max(0, f.essence - 2500);
        broadcast(level, Component.translatable("bloodmoon.soul.heart_broken", f.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F);
    }

    // ------------------------------------------------------------------ 3) despertar

    /** Medidas del santuario (plantillas v2): pilones a 30 con su cristal a +36; el cristal central de 62 de alto. */
    private static final int PYLON_R = 30, PYLON_TOP = 37, CRYSTAL_H = 62, CRYSTAL_R = 12;
    /** Duración de la secuencia del despertar (ticks): temblores, grietas y estallido. */
    private static final int EMERGE_TICKS = 200;
    private static final java.util.Map<Integer, Integer> EMERGING = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Integer> AFTERMATH = new java.util.HashMap<>();

    public static void clear() {
        EMERGING.clear();
        AFTERMATH.clear();
    }

    /** Empieza la secuencia del despertar (solo con el santuario cargado; si no, se reintenta). */
    static boolean startEmergence(ServerLevel level, Faction f) {
        if (EMERGING.containsKey(f.id) || f.soulStage != FEEDING) return EMERGING.containsKey(f.id);
        ChunkPos cp = new ChunkPos(f.soulSite);
        if (!DominionStructures.footprintLoaded(level, cp, 1)) return false;
        EMERGING.put(f.id, 0);
        int cx = cp.getMinBlockX() + 8, cz = cp.getMinBlockZ() + 8;
        Component msg = Component.translatable("bloodmoon.soul.cracking", f.name, cx, cz).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
            p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.MASTER, 1F, 0.5F);
            p.playNotifySound(ModSounds.EYE_WHISPER.get(), SoundSource.MASTER, 1F, 0.5F);
        }
        return true;
    }

    /** El cristal estalla y emerge el Dragón. */
    static boolean emerge(ServerLevel level, InvasionData data, Faction f) {
        ChunkPos cp = new ChunkPos(f.soulSite);
        if (!DominionStructures.footprintLoaded(level, cp, 1)) return false;
        f.soulStage = AWAKE;   // antes de romper el Corazón: no cuenta como un ataque
        data.setDirty();
        int cx = cp.getMinBlockX() + 8, cz = cp.getMinBlockZ() + 8, y0 = f.soulY;
        // el cristal se parte: el interior se libera y la cáscara queda hecha pedazos
        for (int dx = -CRYSTAL_R - 1; dx <= CRYSTAL_R + 1; dx++) for (int dz = -CRYSTAL_R - 1; dz <= CRYSTAL_R + 1; dz++)
            for (int dy = 0; dy <= CRYSTAL_H + 2; dy++) {
                BlockPos q = new BlockPos(cx + dx, y0 + dy, cz + dz);
                BlockState st = level.getBlockState(q);
                boolean crystal = st.is(ModBlocks.VOID_BLOCK.get()) || st.is(ModBlocks.OBELISK_CORE.get()) || st.is(Blocks.CRYING_OBSIDIAN)
                        || st.is(Blocks.AMETHYST_BLOCK) || st.is(Blocks.OBSIDIAN);
                if (!crystal) continue;
                float r = level.random.nextFloat();
                level.setBlock(q, r < 0.8F ? Blocks.AIR.defaultBlockState() : r < 0.92F ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                        : Blocks.AMETHYST_CLUSTER.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        double mid = y0 + CRYSTAL_H * 0.45;
        for (int i = 0; i < 10; i++) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx + 0.5 + level.random.nextGaussian() * 6, mid + level.random.nextGaussian() * 14,
                    cz + 0.5 + level.random.nextGaussian() * 6, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, cx + 0.5, mid, cz + 0.5, 1500, 10, 20, 10, 1.2);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx + 0.5, mid, cz + 0.5, 800, 8, 18, 8, 0.6);
        level.sendParticles(ParticleTypes.END_ROD, cx + 0.5, mid, cz + 0.5, 600, 4, 12, 4, 1.5);
        for (int i = 0; i < 12; i++) bolt(level, cx + Math.cos(i * Math.PI / 6) * (20 + level.random.nextInt(40)), y0,
                cz + Math.sin(i * Math.PI / 6) * (20 + level.random.nextInt(40)));
        level.playSound(null, BlockPos.containing(cx, mid, cz), ModSounds.SUPERNOVA_BLAST.get(), SoundSource.HOSTILE, 10F, 0.6F);
        level.playSound(null, BlockPos.containing(cx, mid, cz), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 10F, 0.4F);
        FirstSoulDragon dragon = BloodMoonManager.spawnSoulDragon(level, new Vec3(cx + 0.5, y0, cz + 0.5), false, false);
        if (dragon != null) {
            dragon.moveTo(cx + 0.5, mid, cz + 0.5, level.random.nextFloat() * 360F, 0F);
            dragon.startDescent(new Vec3(cx + 0.5, y0, cz + 0.5));
            dragon.getPersistentData().putInt(DRAGON_TAG, f.id);
            dragon.setPersistenceRequired();
        }
        AFTERMATH.put(f.id, 0);
        // anuncio a todo el mundo
        Component title = Component.translatable("bloodmoon.soul.awake.title").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        Component sub = Component.translatable("bloodmoon.soul.awake.sub", f.name).withStyle(ChatFormatting.LIGHT_PURPLE);
        Component msg = Component.translatable("bloodmoon.soul.awake", f.name, cx, cz).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(20, 120, 40));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.sendSystemMessage(msg);
            p.playNotifySound(SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.MASTER, 1F, 0.45F);
            p.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.MASTER, 1F, 0.5F);
            p.playNotifySound(ModSounds.EYE_SCREAM.get(), SoundSource.MASTER, 0.8F, 0.6F);
            p.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 1F, 0.35F);
            p.playNotifySound(SoundEvents.WARDEN_EMERGE, SoundSource.MASTER, 1F, 0.5F);
        }
        return true;
    }

    private static void bolt(ServerLevel level, double x, int baseY, double z) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        bolt.moveTo(x, Math.max(baseY, y), z);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    /** Efectos del santuario: rayos de los pilones, torbellino, columna al cielo; y la secuencia del despertar. */
    public static void tick(ServerLevel level) {
        InvasionData data = InvasionData.get(level);
        long now = level.getGameTime();
        for (Faction f : data.factions) {
            if (!f.active || f.soulSite == Faction.RankRecord.NO_SEAT || (f.soulStage != FEEDING && !AFTERMATH.containsKey(f.id))) continue;
            ChunkPos cp = new ChunkPos(f.soulSite);
            int cx = cp.getMinBlockX() + 8, cz = cp.getMinBlockZ() + 8;
            if (level.getChunkSource().getChunkNow(cp.x, cp.z) == null) {
                EMERGING.remove(f.id);   // se reintenta al volver
                continue;
            }
            Integer after = AFTERMATH.get(f.id);
            if (after != null) {
                aftermath(level, f, cx, cz, after);
                if (after >= 80) AFTERMATH.remove(f.id);
                else AFTERMATH.put(f.id, after + 1);
                continue;
            }
            Integer t = EMERGING.get(f.id);
            if (t != null && f.soulProgress < COST) {   // le rompieron el Corazón a último momento
                EMERGING.remove(f.id);
                t = null;
            }
            if (t != null) {
                emergingFx(level, f, cx, cz, t);
                if (t >= EMERGE_TICKS) {
                    EMERGING.remove(f.id);
                    emerge(level, data, f);
                } else EMERGING.put(f.id, t + 1);
                continue;
            }
            if (f.soulProgress >= COST && now % 100 == 0) {
                startEmergence(level, f);
                continue;
            }
            boolean near = false;
            for (ServerPlayer p : level.players()) if (p.distanceToSqr(cx, p.getY(), cz) < 320 * 320) { near = true; break; }
            if (!near) continue;
            ambient(level, f, cx, cz, now);
        }
    }

    private static double crystalTop(Faction f) {
        return f.soulY + 4 + (CRYSTAL_H - 4) * Math.min(1.0, f.soulProgress / COST);
    }

    private static void ambient(ServerLevel level, Faction f, int cx, int cz, long now) {
        double fill = Math.min(1.0, f.soulProgress / COST), top = crystalTop(f);
        double ccx = cx + 0.5, ccz = cz + 0.5;
        if (now % 4 == 0) {   // rayos de esencia desde los ocho pilones
            double py = f.soulY + PYLON_TOP + 0.5, ty = f.soulY + (top - f.soulY) * 0.6;
            for (int k = 0; k < 8; k++) {
                double a = Math.toRadians(k * 45);
                double px = ccx + Math.round(PYLON_R * Math.cos(a)), pz = ccz + Math.round(PYLON_R * Math.sin(a));
                for (int s = 0; s <= 16; s++) {
                    double u = (s + level.random.nextDouble()) / 16.0;
                    double x = px + (ccx - px) * u, y = py + (ty - py) * u + Math.sin(u * Math.PI) * 4, z = pz + (ccz - pz) * u;
                    level.sendParticles(s % 4 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.REVERSE_PORTAL, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
                }
                level.sendParticles(ParticleTypes.WITCH, px, py + 1, pz, 3, 1, 1, 1, 0.02);
            }
        }
        if (now % 2 == 0) {   // torbellino de almas que sube alrededor del cristal
            double ph = now * 0.15;
            for (int i = 0; i < 6; i++) {
                double h = (now * 0.4 + i * 11) % (top - f.soulY + 6);
                double a = ph + i * Math.PI / 3 + h * 0.15, r = CRYSTAL_R + 3 - h * 0.08;
                level.sendParticles(ParticleTypes.SOUL, ccx + Math.cos(a) * r, f.soulY + h, ccz + Math.sin(a) * r, 1, 0, 0.02, 0, 0.01);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, ccx + Math.cos(a + Math.PI) * r, f.soulY + h, ccz + Math.sin(a + Math.PI) * r, 2, 0.1, 0.1, 0.1, 0.02);
            }
        }
        if (now % 5 == 0) {   // columna de luz hacia el cielo y anillo en el borde de la fosa
            for (int i = 0; i < 6; i++) {
                level.sendParticles(ParticleTypes.END_ROD, ccx + level.random.nextGaussian() * 0.6, top + 2 + level.random.nextDouble() * (40 + 60 * fill),
                        ccz + level.random.nextGaussian() * 0.6, 1, 0, 0.3, 0, 0.05);
            }
            for (int k = 0; k < 24; k++) {
                double a = k * Math.PI / 12 + now * 0.01;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ccx + Math.cos(a) * 52, f.soulY + 0.2, ccz + Math.sin(a) * 52, 1, 0, 0.05, 0, 0.01);
            }
        }
        BlockPos core = BlockPos.containing(ccx, f.soulY + (top - f.soulY) / 2, ccz);
        if (now % 80 == 0) level.playSound(null, core, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 6F, 0.5F);
        if (now % 120 == 0) level.playSound(null, core, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 6F, 0.4F);
        if (now % 600 == 300) level.playSound(null, core, ModSounds.EYE_WHISPER.get(), SoundSource.HOSTILE, 8F, 0.5F);
        if (now % 240 == 0 && fill > 0.3) {   // relámpagos sobre los pilones
            double a = Math.toRadians(level.random.nextInt(8) * 45);
            bolt(level, ccx + Math.round(PYLON_R * Math.cos(a)), f.soulY + PYLON_TOP + 2, ccz + Math.round(PYLON_R * Math.sin(a)));
        }
    }

    /** Diez segundos: latidos, temblor, grietas en el cristal y relámpagos cada vez más seguidos. */
    private static void emergingFx(ServerLevel level, Faction f, int cx, int cz, int t) {
        double ccx = cx + 0.5, ccz = cz + 0.5, top = crystalTop(f);
        float k = t / (float) EMERGE_TICKS;
        BlockPos core = BlockPos.containing(ccx, f.soulY + CRYSTAL_H / 2.0, ccz);
        if (t % Math.max(6, (int) (30 - 24 * k)) == 0) {
            level.playSound(null, core, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 12F, 0.5F + 0.4F * k);
            level.sendParticles(ParticleTypes.SONIC_BOOM, ccx, f.soulY + CRYSTAL_H * 0.4, ccz, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 8 + (int) (40 * k); i++) {   // el cristal se resquebraja
            double a = level.random.nextDouble() * Math.PI * 2, h = level.random.nextDouble() * (top - f.soulY);
            double r = 1.0 + 11.0 * Math.pow(Math.sin(Math.PI * (h + 2) / 66.0), 0.9);
            level.sendParticles(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL,
                    ccx + Math.cos(a) * r, f.soulY + h, ccz + Math.sin(a) * r, 1, 0, 0, 0, 0.2 + 0.4 * k);
        }
        if (t % 4 == 0) {   // cascotes que saltan de la cáscara
            double a = level.random.nextDouble() * Math.PI * 2, h = level.random.nextDouble() * (top - f.soulY);
            BlockPos q = BlockPos.containing(ccx + Math.cos(a) * 11, f.soulY + h, ccz + Math.sin(a) * 11);
            for (int i = 0; i < 4; i++) {
                BlockPos qq = q.offset(level.random.nextInt(3) - 1, level.random.nextInt(3) - 1, level.random.nextInt(3) - 1);
                BlockState st = level.getBlockState(qq);
                if (st.is(Blocks.CRYING_OBSIDIAN) || st.is(Blocks.AMETHYST_BLOCK) || st.is(Blocks.OBSIDIAN)) {
                    level.destroyBlock(qq, false);
                }
            }
            level.sendParticles(ParticleTypes.EXPLOSION, q.getX(), q.getY(), q.getZ(), 1, 0, 0, 0, 0);
        }
        if (t % Math.max(8, (int) (40 - 32 * k)) == 0) {
            double a = level.random.nextDouble() * Math.PI * 2, r = 15 + level.random.nextDouble() * 45;
            bolt(level, ccx + Math.cos(a) * r, f.soulY, ccz + Math.sin(a) * r);
        }
        if (t == EMERGE_TICKS - 40) {
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                p.playNotifySound(SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.MASTER, 1F, 0.5F);
            }
        }
    }

    /** Después del estallido: anillos de choque que recorren todo el santuario. */
    private static void aftermath(ServerLevel level, Faction f, int cx, int cz, int t) {
        if (t % 2 != 0) return;
        double r = 6 + t * 0.9;
        int n = (int) (r * 3);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            level.sendParticles(i % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.REVERSE_PORTAL,
                    cx + 0.5 + Math.cos(a) * r, f.soulY + 0.5, cz + 0.5 + Math.sin(a) * r, 1, 0, 0.1, 0, 0.02);
        }
        if (t % 20 == 0) level.sendParticles(ParticleTypes.SONIC_BOOM, cx + 0.5, f.soulY + 20, cz + 0.5, 1, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ el Dragón cae

    public static void onLivingDeath(LivingDeathEvent event) {
        Entity e = event.getEntity();
        if (!(e instanceof FirstSoulDragon) || !(e.level() instanceof ServerLevel level) || !e.getPersistentData().contains(DRAGON_TAG)) return;
        ServerLevel ow = level.getServer().overworld();
        InvasionData data = InvasionData.get(ow);
        Faction f = data.faction(e.getPersistentData().getInt(DRAGON_TAG));
        if (f == null) return;
        f.soulStage = SLAIN;
        f.essence = 0;
        f.haltedUntil = ow.getGameTime() + 2 * 24000L;
        data.setDirty();
        broadcast(ow, Component.translatable("bloodmoon.soul.slain", f.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7F);
    }

    private static void broadcast(ServerLevel level, Component msg, net.minecraft.sounds.SoundEvent sound, float pitch) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
            p.playNotifySound(sound, SoundSource.MASTER, 0.8F, pitch);
        }
    }

    // ------------------------------------------------------------------ pruebas

    /** Para pruebas: lleva la ofrenda a una etapa (1 santuario aquí, 2 alimentando al porcentaje dado, 3 despertar ya). */
    public static String force(ServerLevel level, ServerPlayer p, int stage, int pct) {
        InvasionData data = InvasionData.get(level);
        Faction f = InvasionManager.nearest(level, p.blockPosition());
        if (f == null || !f.active) return "Fuera de un Dominio activo";
        if (stage == SITE) {
            if (!ConstructionSites.forceStructureHere(level, p, DominionStructures.SOUL)) return "No se pudo levantar el santuario aquí";
            f.phase = Math.max(f.phase, 4);
            f.soulSite = ChunkPos.asLong(p.blockPosition());
            f.soulStage = SITE;
            f.soulProgress = 0;
            f.soulPlaced = 0;
            f.soulWarned = 0;
            data.setDirty();
            return "Santuario en obra";
        }
        if (f.soulSite == Faction.RankRecord.NO_SEAT || f.soulStage < SITE) return "Primero: /bloodmoon invasion soul site";
        InvasionData.Cell c = data.cells.get(f.soulSite);
        if (c != null && !c.structureBuilt) return "El santuario todavía está en obra";
        if (f.soulStage == SITE) {
            f.soulStage = FEEDING;
            f.soulY = c.coreY;
        }
        if (stage == FEEDING) {
            f.soulProgress = COST * Math.max(0, Math.min(99, pct)) / 100.0;
            growCore(level, f);
            data.setDirty();
            return "Ofrenda al " + pct + "%";
        }
        f.soulProgress = COST;
        growCore(level, f);
        return startEmergence(level, f) ? "La Primera Alma se agita: despierta en 10 segundos" : "El santuario no está cargado";
    }
}
