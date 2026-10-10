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
                if (f.soulProgress >= COST) emerge(level, data, f);
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
        level.playSound(null, new BlockPos(cp.getMinBlockX() + 8, f.soulY + 8, cp.getMinBlockZ() + 8), SoundEvents.AMETHYST_BLOCK_RESONATE,
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

    /** El cristal estalla y emerge el Dragón (solo con el santuario cargado; si no, espera a que alguien se acerque). */
    static boolean emerge(ServerLevel level, InvasionData data, Faction f) {
        ChunkPos cp = new ChunkPos(f.soulSite);
        if (!DominionStructures.footprintLoaded(level, cp, 1)) return false;
        f.soulStage = AWAKE;   // antes de romper el Corazón: no cuenta como un ataque
        data.setDirty();
        int cx = cp.getMinBlockX() + 8, cz = cp.getMinBlockZ() + 8, y0 = f.soulY;
        // el cristal se parte: el interior se libera y la cáscara queda hecha pedazos
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) for (int dy = 0; dy <= 32; dy++) {
            BlockPos q = new BlockPos(cx + dx, y0 + dy, cz + dz);
            BlockState st = level.getBlockState(q);
            boolean crystal = st.is(ModBlocks.VOID_BLOCK.get()) || st.is(ModBlocks.OBELISK_CORE.get()) || st.is(Blocks.CRYING_OBSIDIAN)
                    || st.is(Blocks.AMETHYST_BLOCK) || st.is(Blocks.OBSIDIAN);
            if (!crystal) continue;
            float r = level.random.nextFloat();
            level.setBlock(q, r < 0.75F ? Blocks.AIR.defaultBlockState() : r < 0.9F ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                    : Blocks.AMETHYST_CLUSTER.defaultBlockState(), Block.UPDATE_ALL);
        }
        Vec3 core = new Vec3(cx + 0.5, y0 + 12, cz + 0.5);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, core.x, core.y, core.z, 6, 4, 6, 4, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, core.x, core.y, core.z, 600, 6, 10, 6, 0.6);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, core.x, core.y, core.z, 300, 5, 8, 5, 0.3);
        for (int i = 0; i < 6; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) continue;
            double a = i * Math.PI / 3;
            bolt.moveTo(cx + Math.cos(a) * 15, y0 + 18, cz + Math.sin(a) * 15);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        FirstSoulDragon dragon = BloodMoonManager.spawnSoulDragon(level, new Vec3(cx + 0.5, y0, cz + 0.5), false, false);
        if (dragon != null) {
            dragon.moveTo(cx + 0.5, y0 + 14, cz + 0.5, level.random.nextFloat() * 360F, 0F);
            dragon.startDescent(new Vec3(cx + 0.5, y0, cz + 0.5));
            dragon.getPersistentData().putInt(DRAGON_TAG, f.id);
            dragon.setPersistenceRequired();
        }
        // anuncio a todo el mundo
        Component title = Component.translatable("bloodmoon.soul.awake.title").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        Component sub = Component.translatable("bloodmoon.soul.awake.sub", f.name).withStyle(ChatFormatting.LIGHT_PURPLE);
        Component msg = Component.translatable("bloodmoon.soul.awake", f.name, cx, cz).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(20, 100, 40));
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

    /** Se intenta despertar si quedó pendiente (el santuario estaba descargado). */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 10 != 0) return;
        InvasionData data = InvasionData.get(level);
        for (Faction f : data.factions) {
            if (!f.active || f.soulStage != FEEDING || f.soulSite == Faction.RankRecord.NO_SEAT) continue;
            ChunkPos cp = new ChunkPos(f.soulSite);
            int cx = cp.getMinBlockX() + 8, cz = cp.getMinBlockZ() + 8;
            if (level.getChunkSource().getChunkNow(cp.x, cp.z) == null) continue;
            if (f.soulProgress >= COST && level.getGameTime() % 100 == 0) {
                emerge(level, data, f);
                continue;
            }
            boolean near = false;
            for (ServerPlayer p : level.players()) if (p.distanceToSqr(cx, p.getY(), cz) < 160 * 160) { near = true; break; }
            if (!near) continue;
            beams(level, f, cx, cz);
        }
    }

    /** Rayos de esencia desde los cristales de los ocho pilones hasta el cristal central. */
    private static void beams(ServerLevel level, Faction f, int cx, int cz) {
        double top = f.soulY + 19.5, coreY = f.soulY + 6 + 20 * Math.min(1.0, f.soulProgress / COST);
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(k * 45);
            double px = cx + 0.5 + Math.round(15 * Math.cos(a)), pz = cz + 0.5 + Math.round(15 * Math.sin(a));
            for (int s = 0; s <= 12; s++) {
                double t = (s + level.random.nextDouble()) / 12.0;
                double x = px + (cx + 0.5 - px) * t, y = top + (coreY - top) * t, z = pz + (cz + 0.5 - pz) * t;
                level.sendParticles(s % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.REVERSE_PORTAL, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
        level.sendParticles(ParticleTypes.END_ROD, cx + 0.5, coreY, cz + 0.5, 4, 2, 3, 2, 0.02);
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
        return emerge(level, data, f) ? "La Primera Alma despierta" : "El santuario no está cargado";
    }
}
