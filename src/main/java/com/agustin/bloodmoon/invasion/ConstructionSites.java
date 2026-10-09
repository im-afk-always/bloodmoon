package com.agustin.bloodmoon.invasion;

import com.agustin.bloodmoon.entity.ModEntities;
import com.agustin.bloodmoon.entity.VoidForger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Obras a la vista: si hay un jugador cerca cuando el Dominio levanta un obelisco, aparecen Forjadores y lo construyen
 * bloque a bloque, de abajo hacia arriba. Sin Forjadores vivos la obra se detiene (vuelven a los 30 s si el Dominio
 * tiene). Si todos se alejan, la obra se termina de golpe.
 */
public final class ConstructionSites {
    private static final Map<Long, Site> SITES = new HashMap<>();
    /** Con un jugador a esta distancia la obra se hace a la vista; sin nadie a {@link #ABANDON} se termina de golpe. */
    public static final int WATCH = 200, ABANDON = 256;

    private ConstructionSites() {}

    private static final class Site {
        final long key;
        final int faction;
        final List<DominionTerraform.Placement> plan;
        final int coreY;
        final boolean structure;
        /** Sin nadie a la vista: sin Forjadores, se completa sola con un presupuesto de tiempo por tick. */
        boolean silent;
        final BlockPos center;
        final List<UUID> forgers = new ArrayList<>();
        int index;
        long lastForgers = Long.MIN_VALUE;

        Site(long key, int faction, DominionTerraform.Plan plan, BlockPos center, boolean structure) {
            this.key = key;
            this.structure = structure;
            this.faction = faction;
            this.plan = plan.placements();
            this.coreY = plan.coreY();
            this.center = center;
        }
    }

    public static boolean building(long key) {
        return SITES.containsKey(key);
    }

    public static void clear() {
        SITES.clear();
    }

    /** Obra de un obelisco ({@code structure} = false) o de una estructura mayor. */
    /** Para pruebas: obelisco en obra en el chunk del jugador (si está dentro de un Dominio). */
    public static boolean forceHere(ServerLevel level, net.minecraft.server.level.ServerPlayer p) {
        InvasionData data = InvasionData.get(level);
        Faction f = InvasionManager.nearest(level, p.blockPosition());
        if (f == null || !f.active) return false;
        long key = ChunkPos.asLong(p.blockPosition());
        InvasionData.Cell c = InvasionManager.claim(data, key, f);
        if (c == null || c.structure != DominionStructures.NONE) return false;
        c.influence = 100;
        c.applied = 3;
        c.obelisk = true;
        c.obeliskBuilt = false;
        data.setDirty();
        start(level, key, f, DominionTerraform.obeliskPlan(level, new ChunkPos(key)), false, false);
        return true;
    }

    /** Para pruebas: estructura mayor en obra centrada en el chunk del jugador (sus 3×3 chunks pasan a ser del Dominio). */
    public static boolean forceStructureHere(ServerLevel level, net.minecraft.server.level.ServerPlayer p, int type) {
        InvasionData data = InvasionData.get(level);
        Faction f = InvasionManager.nearest(level, p.blockPosition());
        if (f == null || !f.active) return false;
        ChunkPos cp = new ChunkPos(p.blockPosition());
        long key = cp.toLong();
        if (SITES.containsKey(key)) return false;
        for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) {
            InvasionData.Cell n = InvasionManager.claim(data, ChunkPos.asLong(cp.x + ox, cp.z + oz), f);
            if (n == null) return false;
            n.influence = 100;
            n.applied = 3;
        }
        InvasionData.Cell c = data.cells.get(key);
        c.obelisk = false;
        c.structure = type;
        c.structureBuilt = false;
        data.setDirty();
        start(level, key, f, DominionStructures.plan(level, cp, type, f), true, false);
        return true;
    }

    static void start(ServerLevel level, long key, Faction f, DominionTerraform.Plan plan, boolean structure, boolean silent) {
        if (SITES.containsKey(key)) return;
        ChunkPos cp = new ChunkPos(key);
        int baseY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cp.getMiddleBlockX(), cp.getMiddleBlockZ());
        BlockPos center = new BlockPos(cp.getMiddleBlockX(), baseY, cp.getMiddleBlockZ());
        Site s = new Site(key, f.id, plan, center, structure);
        s.silent = silent;
        SITES.put(key, s);
        if (!silent) spawnForgers(level, s, f);
    }

    private static void spawnForgers(ServerLevel level, Site s, Faction f) {
        int n = s.structure ? Math.min(6, Math.max(3, f.forgers / 2)) : Math.min(3, Math.max(1, f.forgers / 3));
        for (int i = 0; i < n; i++) {
            VoidForger fo = ModEntities.VOID_FORGER.get().create(level);
            if (fo == null) continue;
            double a = level.random.nextDouble() * Math.PI * 2;
            double rr = s.structure ? 12 + level.random.nextDouble() * 8 : 5;
            int x = s.center.getX() + (int) Math.round(Math.cos(a) * rr), z = s.center.getZ() + (int) Math.round(Math.sin(a) * rr);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            fo.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360F, 0F);
            fo.finalizeSpawn(level, level.getCurrentDifficultyAt(new BlockPos(x, y, z)), MobSpawnType.EVENT, null);
            fo.bindToDominion();
            fo.setWorkSite(s.center);
            level.addFreshEntity(fo);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x + 0.5, y + 1, z + 0.5, 40, 0.3, 0.8, 0.3, 0.05);
            s.forgers.add(fo.getUUID());
        }
        s.lastForgers = level.getGameTime();
    }

    /** Presupuesto de las obras sin espectadores, por tick (todas juntas). */
    private static final long SILENT_BUDGET_NS = 2_000_000L;

    public static void tick(ServerLevel level) {
        if (SITES.isEmpty()) return;
        boolean step = level.getGameTime() % 5 == 0;
        long t0 = System.nanoTime();
        InvasionData data = InvasionData.get(level);
        for (Iterator<Site> it = SITES.values().iterator(); it.hasNext(); ) {
            Site s = it.next();
            InvasionData.Cell c = data.cells.get(s.key);
            Faction f = data.faction(s.faction);
            ChunkPos cp = new ChunkPos(s.key);
            boolean wanted = s.structure ? c != null && c.structure != DominionStructures.NONE : c != null && c.obelisk;
            if (!wanted || f == null || !f.active || level.getChunkSource().getChunkNow(cp.x, cp.z) == null) {
                dismiss(level, s);
                it.remove();
                continue;
            }
            if (step && !s.silent) {
                double near = Double.MAX_VALUE;
                for (ServerPlayer p : level.players()) near = Math.min(near, p.distanceToSqr(s.center.getX(), p.getY(), s.center.getZ()));
                if (near > (double) ABANDON * ABANDON) {   // nadie mira: los Forjadores se van y la obra sigue sola
                    dismiss(level, s);
                    s.forgers.clear();
                    s.silent = true;
                }
            }
            if (s.silent) {
                while (s.index < s.plan.size() && System.nanoTime() - t0 < SILENT_BUDGET_NS) {
                    if (!place(level, s.plan.get(s.index), false)) break;   // un chunk de la huella se descargó: esperar
                    s.index++;
                }
            } else if (step) {
                List<VoidForger> workers = new ArrayList<>();
                double reach = s.structure ? 40 : 24;
                for (UUID u : s.forgers) {
                    Entity e = level.getEntity(u);
                    if (e instanceof VoidForger fo && fo.isAlive() && fo.distanceToSqr(s.center.getX(), s.center.getY(), s.center.getZ()) < reach * reach) workers.add(fo);
                }
                if (workers.isEmpty()) {
                    if (f.forgers > 0 && level.getGameTime() - s.lastForgers > 600) spawnForgers(level, s, f);
                    continue;   // obra detenida
                }
                // el aire (despejar) va rápido; cada Forjador coloca más bloques cuanto más grande la obra
                int perWorker = Math.max(1, Math.min(24, s.plan.size() / 1000));
                int solid = workers.size() * perWorker;
                int airBudget = 400;
                while (s.index < s.plan.size() && solid > 0 && airBudget > 0) {
                    DominionTerraform.Placement p = s.plan.get(s.index);
                    boolean isAir = p.state().isAir();
                    if (!place(level, p, !isAir && solid % 3 == 0)) break;
                    s.index++;
                    if (isAir) airBudget--;
                    else {
                        workers.get((solid - 1) % workers.size()).hammer(p.pos());
                        solid--;
                    }
                }
            }
            if (s.index >= s.plan.size()) {
                if (s.structure) c.structureBuilt = true;
                else c.obeliskBuilt = true;
                c.coreY = s.coreY;
                data.setDirty();
                DominionTerraform.enqueue(s.key);   // ahora sí, los caminos de este chunk
                for (UUID u : s.forgers) {
                    Entity e = level.getEntity(u);
                    if (e instanceof VoidForger fo) fo.setWorkSite(null);
                }
                if (!s.silent) level.playSound(null, s.center, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2F, 0.5F);
                it.remove();
            }
        }
    }

    /** Coloca un bloque de la obra; {@code false} si su chunk no está cargado (no se fuerza la carga). */
    private static boolean place(ServerLevel level, DominionTerraform.Placement p, boolean fx) {
        if (level.getChunkSource().getChunkNow(p.pos().getX() >> 4, p.pos().getZ() >> 4) == null) return false;
        level.setBlock(p.pos(), p.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);   // formas ya resueltas en la plantilla
        if (!fx) return true;
        BlockState st = p.state();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5,
                8, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, p.pos(), st.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 0.7F);
        return true;
    }

    private static void dismiss(ServerLevel level, Site s) {
        for (UUID u : s.forgers) {
            Entity e = level.getEntity(u);
            if (e != null) e.discard();
        }
    }
}
