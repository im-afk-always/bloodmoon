package com.agustin.bloodmoon.invasion;

import com.agustin.bloodmoon.BloodMoonConfig;
import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.registry.ModBlocks;
import com.agustin.bloodmoon.world.ColiseumSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Aplica el Dominio a los bloques. Solo toca chunks cargados: los demás quedan anotados en la grilla y se convierten
 * cuando alguien los carga. Trabaja con un presupuesto de tiempo por tick.
 * <ul>
 *   <li>Marchito (2): mitad del césped y de la tierra apagados, plantas muertas, hojas cayendo.</li>
 *   <li>Muerto (3): superficie entera muerta, troncos calcinados, sin hojas, manchas de roca negra y todo bloque de
 *   jugador reemplazado (los cofres van al Relicario). El coliseo y los bloques del mod no se tocan.</li>
 *   <li>Sanar: la Tierra Muerta vuelve a ser césped y la Yerma, tierra (los troncos calcinados quedan como cicatriz).</li>
 * </ul>
 */
public final class DominionTerraform {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final long BUDGET_NS = 3_000_000L;
    private static final ConcurrentLinkedQueue<Long> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Set<Long> QUEUED = ConcurrentHashMap.newKeySet();

    private DominionTerraform() {}

    public static void enqueue(long chunkKey) {
        if (QUEUED.add(chunkKey)) QUEUE.add(chunkKey);
    }

    public static void clear() {
        QUEUE.clear();
        QUEUED.clear();
        LIVE.clear();
    }

    public static int pending() {
        return QUEUE.size();
    }

    /** ¿Hay algo que aplicar en este chunk? */
    static boolean needsWork(InvasionData data, InvasionData.Cell c) {
        if (c.stage() != c.applied) return true;
        if (c.obelisk && c.stage() == 3 && !c.obeliskBuilt) return true;
        Faction f = data.faction(c.faction);
        return !c.obelisk && c.obeliskBuilt && c.coreY != InvasionData.NO_CORE && f != null && !f.active;
    }

    public static void tick(ServerLevel level) {
        if (QUEUE.isEmpty() && LIVE.isEmpty()) return;
        InvasionData data = InvasionData.get(level);
        tickLive(level, data);
        long start = System.nanoTime();
        while (!QUEUE.isEmpty() && System.nanoTime() - start < BUDGET_NS) {
            Long key = QUEUE.poll();
            if (key == null) break;
            QUEUED.remove(key);
            InvasionData.Cell c = data.cells.get(key);
            if (c == null) continue;
            ChunkPos cp = new ChunkPos(key);
            LevelChunk chunk = level.getChunkSource().getChunkNow(cp.x, cp.z);
            if (chunk == null) continue;   // se reintenta cuando se cargue
            try {
                apply(level, data, c, chunk);
            } catch (Exception e) {
                BloodMoonMod.LOGGER.error("Dominion terraform failed at chunk {}", cp, e);
                c.applied = c.stage();
            }
            data.setDirty();
        }
    }

    private static void apply(ServerLevel level, InvasionData data, InvasionData.Cell c, LevelChunk chunk) {
        Faction f = data.faction(c.faction);
        int target = c.stage();
        boolean protectedZone = isProtected(level, chunk.getPos());
        long key = chunk.getPos().toLong();
        if (LIVE.containsKey(key)) return;   // ya se está corrompiendo a la vista
        if (target > c.applied && target >= 2) {
            boolean full = target == 3;
            boolean replace = full && !protectedZone && BloodMoonConfig.INVASION_REPLACE_PLAYER_BLOCKS.get();
            if (playerNear(level, chunk.getPos(), 64)) {   // alguien mira: se extiende columna a columna
                LIVE.put(key, new LiveJob(key, target, full, replace));
                return;
            }
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) corruptColumn(level, chunk, lx, lz, full, replace, f);
        } else if (target < c.applied && target <= 1) {
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) healColumn(level, chunk, lx, lz);
        }
        finish(level, data, c, chunk, target);
    }

    private static void finish(ServerLevel level, InvasionData data, InvasionData.Cell c, LevelChunk chunk, int target) {
        Faction f = data.faction(c.faction);
        long key = chunk.getPos().toLong();
        c.applied = target;
        if (c.obelisk && target == 3 && !c.obeliskBuilt && f != null && f.active && !ConstructionSites.building(key)) {
            if (playerNear(level, chunk.getPos(), 80)) {
                ConstructionSites.start(level, key, f);   // los Forjadores lo levantan a la vista
            } else {
                c.coreY = buildObelisk(level, chunk.getPos());
                c.obeliskBuilt = true;
            }
        }
        // un obelisco de un Dominio vencido pierde su núcleo
        if (!c.obelisk && c.obeliskBuilt && c.coreY != InvasionData.NO_CORE && f != null && !f.active) {
            BlockPos core = new BlockPos(chunk.getPos().getMinBlockX() + 8, c.coreY, chunk.getPos().getMinBlockZ() + 8);
            if (level.getBlockState(core).is(ModBlocks.OBELISK_CORE.get())) {
                level.setBlock(core, ModBlocks.CRACKED_BLACK_ROCK_BRICKS.get().defaultBlockState(), FLAGS);
            }
            c.coreY = InvasionData.NO_CORE;
        }
    }

    /** El coliseo (y su ruina de escombros) no se toca. */
    private static boolean isProtected(ServerLevel level, ChunkPos cp) {
        BlockPos mid = new BlockPos(cp.getMiddleBlockX(), 64, cp.getMiddleBlockZ());
        var site = ColiseumSites.nearest(level, mid, 1);
        if (site.isEmpty()) return false;
        BlockPos s = site.get().center();
        double dx = s.getX() - mid.getX(), dz = s.getZ() - mid.getZ();
        return dx * dx + dz * dz < Math.pow(ColiseumSites.reach() + 24, 2);
    }

    // ------------------------------------------------------------------ clasificación de bloques

    static double hash(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static boolean isGround(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA)
                || s.is(BlockTags.ICE) || s.is(Tags.Blocks.ORES) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SNOW_BLOCK)
                || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.BEDROCK)
                || s.is(Blocks.OBSIDIAN) || s.is(Blocks.CALCITE) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH)
                || s.is(ModBlocks.DEAD_GRASS_BLOCK.get()) || s.is(ModBlocks.BARREN_DIRT.get()) || s.is(ModBlocks.BLACK_ROCK.get());
    }

    private static boolean isDirtLike(BlockState s) {
        return s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.FARMLAND)
                || s.is(Blocks.DIRT_PATH) || s.is(Blocks.MUD);
    }

    private static boolean isGrassLike(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) || s.is(Blocks.MOSS_BLOCK);
    }

    private static boolean isPlant(BlockState s) {
        if (s.is(ModBlocks.DEAD_GRASS.get())) return false;
        return s.getBlock() instanceof BushBlock || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.CROPS)
                || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS) || s.is(Blocks.BAMBOO)
                || s.is(Blocks.VINE) || s.is(Blocks.GLOW_LICHEN) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON) || s.is(Blocks.BEE_NEST)
                || s.is(Blocks.COCOA) || s.is(Blocks.MOSS_CARPET) || s.is(Blocks.PINK_PETALS);
    }

    /** Bloques que no se reemplazan nunca: los del mod, los irrompibles y los portales. */
    private static boolean untouchable(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.getDestroySpeed(level, pos) < 0) return true;
        if (s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.END_PORTAL_FRAME) || s.is(Blocks.END_PORTAL)) return true;
        return BloodMoonMod.MODID.equals(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace());
    }

    // ------------------------------------------------------------------ corromper

    private static void corruptColumn(ServerLevel level, LevelChunk chunk, int lx, int lz, boolean full, boolean replace, Faction f) {
        int x = chunk.getPos().getMinBlockX() + lx, z = chunk.getPos().getMinBlockZ() + lz;
        double h = hash(x, z, 17);
        boolean touch = full || h < 0.5;
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
        int bottom = Math.max(level.getMinBuildHeight(), top - 96);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y >= bottom; y--) {
            p.setY(y);
            BlockState s = chunk.getBlockState(p);
            if (s.isAir()) continue;
            if (!s.getFluidState().isEmpty()) break;   // agua o lava: el Dominio se detiene en la orilla
            if (s.is(Blocks.SNOW)) continue;
            if (isGround(s)) {
                convertGround(level, p, s, full, touch, h);
                if (full) {   // un par de bloques de tierra debajo también se apagan
                    for (int k = 1; k <= 2; k++) {
                        BlockPos q = p.below(k);
                        BlockState u = chunk.getBlockState(q);
                        if (isDirtLike(u) || isGrassLike(u)) level.setBlock(q, ModBlocks.BARREN_DIRT.get().defaultBlockState(), FLAGS);
                    }
                }
                break;
            }
            if (s.is(BlockTags.LEAVES)) {
                if (full || h < 0.3) level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                continue;
            }
            if (s.is(BlockTags.LOGS)) {
                if (full) {
                    BlockState log = ModBlocks.CHARRED_LOG.get().defaultBlockState();
                    if (s.hasProperty(BlockStateProperties.AXIS)) log = log.setValue(RotatedPillarBlock.AXIS, s.getValue(BlockStateProperties.AXIS));
                    level.setBlock(p, log, FLAGS);
                }
                continue;
            }
            if (isPlant(s)) {
                if (touch) {
                    BlockState below = chunk.getBlockState(p.below());
                    boolean onGround = isGround(below);
                    BlockState repl = onGround && hash(x, z, 31) < 0.45 ? ModBlocks.DEAD_GRASS.get().defaultBlockState() : Blocks.AIR.defaultBlockState();
                    level.setBlock(p, repl, FLAGS);
                }
                continue;
            }
            if (replace && !untouchable(level, p, s)) replacePlayerBlock(level, p.immutable(), s, f, x, y, z);
        }
    }

    private static void convertGround(ServerLevel level, BlockPos p, BlockState s, boolean full, boolean touch, double h) {
        if (!touch) return;
        if (isGrassLike(s)) {
            level.setBlock(p, ModBlocks.DEAD_GRASS_BLOCK.get().defaultBlockState(), FLAGS);
        } else if (isDirtLike(s)) {
            level.setBlock(p, ModBlocks.BARREN_DIRT.get().defaultBlockState(), FLAGS);
        } else if (full && h > 0.965 && !s.is(Blocks.BEDROCK) && !s.is(ModBlocks.BLACK_ROCK.get())) {
            level.setBlock(p, ModBlocks.BLACK_ROCK.get().defaultBlockState(), FLAGS);   // manchas de roca negra
        }
    }

    /** Bloque de jugador (o de aldea): sólido → ladrillo de roca negra; lo demás → aire. Los contenedores se vacían en el Relicario. */
    private static void replacePlayerBlock(ServerLevel level, BlockPos p, BlockState s, Faction f, int x, int y, int z) {
        BlockEntity be = level.getBlockEntity(p);
        if (be instanceof Container c && f != null) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack st = c.getItem(i);
                if (!st.isEmpty() && f.relic.size() < Faction.RELIC_CAP) f.relic.add(st.copy());
            }
            c.clearContent();
        }
        BlockState repl;
        if (s.isCollisionShapeFullBlock(level, p)) {
            repl = (hash(x, z, y) < 0.35 ? ModBlocks.CRACKED_BLACK_ROCK_BRICKS : ModBlocks.BLACK_ROCK_BRICKS).get().defaultBlockState();
        } else {
            repl = Blocks.AIR.defaultBlockState();
        }
        level.setBlock(p, repl, FLAGS);
    }

    // ------------------------------------------------------------------ sanar

    private static void healColumn(ServerLevel level, LevelChunk chunk, int lx, int lz) {
        int x = chunk.getPos().getMinBlockX() + lx, z = chunk.getPos().getMinBlockZ() + lz;
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
        int bottom = Math.max(level.getMinBuildHeight(), top - 96);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
        boolean surface = true;
        for (int y = top; y >= bottom; y--) {
            p.setY(y);
            BlockState s = chunk.getBlockState(p);
            if (s.isAir()) continue;
            if (!s.getFluidState().isEmpty()) break;
            if (s.is(ModBlocks.DEAD_GRASS.get())) {
                level.setBlock(p, hash(x, z, 53) < 0.4 ? Blocks.SHORT_GRASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), FLAGS);
                continue;
            }
            if (s.is(ModBlocks.DEAD_GRASS_BLOCK.get()) || (surface && s.is(ModBlocks.BARREN_DIRT.get()))) {
                level.setBlock(p, Blocks.GRASS_BLOCK.defaultBlockState(), FLAGS);
                surface = false;
                continue;
            }
            if (s.is(ModBlocks.BARREN_DIRT.get())) {
                level.setBlock(p, Blocks.DIRT.defaultBlockState(), FLAGS);
                continue;
            }
            if (isGround(s)) {
                if (!surface) break;
                surface = false;
            }
            if (!surface && y < top - 4) break;
        }
    }

    // ------------------------------------------------------------------ obelisco

    public record Placement(BlockPos pos, BlockState state) {}

    public record Plan(java.util.List<Placement> placements, int coreY) {}

    /** Obelisco del Dominio en el centro del chunk, como lista ordenada de bloques (cimientos, despeje, de abajo hacia arriba). */
    static Plan obeliskPlan(ServerLevel level, ChunkPos cp) {
        java.util.List<Placement> out = new java.util.ArrayList<>();
        int cx = cp.getMinBlockX() + 8, cz = cp.getMinBlockZ() + 8;
        int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        int height = 13 + (int) (hash(cx, cz, 7) * 6);
        BlockState rock = ModBlocks.BLACK_ROCK.get().defaultBlockState();
        BlockState bricks = ModBlocks.BLACK_ROCK_BRICKS.get().defaultBlockState();
        BlockState cracked = ModBlocks.CRACKED_BLACK_ROCK_BRICKS.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        // despejar
        for (int dy = height + 1; dy >= 1; dy--) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos q = new BlockPos(cx + dx, y0 + dy, cz + dz);
            if (!level.getBlockState(q).isAir()) out.add(new Placement(q, air));
        }
        // cimientos hasta el suelo firme
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            java.util.List<Placement> col = new java.util.ArrayList<>();
            for (int d = 1; d <= 12; d++) {
                BlockPos q = new BlockPos(cx + dx, y0 - d, cz + dz);
                BlockState st = level.getBlockState(q);
                if (!st.isAir() && st.getFluidState().isEmpty() && !isPlant(st) && !st.is(BlockTags.LEAVES)) break;
                col.add(0, new Placement(q, rock));
            }
            out.addAll(col);
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            out.add(new Placement(new BlockPos(cx + dx, y0, cz + dz), edge ? rock : bricks));
        }
        int coreY = y0 + 3;
        for (int dy = 1; dy <= 7; dy++) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                boolean center = dx == 0 && dz == 0;
                BlockPos q = new BlockPos(cx + dx, y0 + dy, cz + dz);
                BlockState st;
                if (dy == 3) {
                    if (!center && !corner) continue;   // ventanas al núcleo
                    st = center ? ModBlocks.OBELISK_CORE.get().defaultBlockState() : bricks;
                } else if (corner && dy <= 2) st = rock;
                else st = hash(q.getX(), q.getZ(), q.getY()) < 0.3 ? cracked : bricks;
                out.add(new Placement(q, st));
            }
        }
        for (int dy = 8; dy <= height - 2; dy++) {
            out.add(new Placement(new BlockPos(cx, y0 + dy, cz), hash(cx, dy, cz) < 0.25 ? cracked : bricks));
            if (dy == 8) {
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) out.add(new Placement(new BlockPos(cx + d[0], y0 + dy, cz + d[1]), rock));
            }
        }
        out.add(new Placement(new BlockPos(cx, y0 + height - 1, cz), ModBlocks.VOID_LANTERN.get().defaultBlockState()));
        out.add(new Placement(new BlockPos(cx, y0 + height, cz), rock));
        return new Plan(out, coreY);
    }

    /** Obelisco instantáneo (sin nadie mirando). Devuelve la altura del núcleo. */
    static int buildObelisk(ServerLevel level, ChunkPos cp) {
        Plan plan = obeliskPlan(level, cp);
        for (Placement p : plan.placements()) level.setBlock(p.pos(), p.state(), FLAGS);
        return plan.coreY();
    }

    // ------------------------------------------------------------------ propagación a la vista

    /** Un chunk que se corrompe columna a columna porque hay un jugador mirando. */
    private static final class LiveJob {
        final long key;
        final int target;
        final boolean full, replace;
        final int[] order = new int[256];
        int index;

        LiveJob(long key, int target, boolean full, boolean replace) {
            this.key = key;
            this.target = target;
            this.full = full;
            this.replace = replace;
            for (int i = 0; i < 256; i++) order[i] = i;
            java.util.Random r = new java.util.Random(key);
            for (int i = 255; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int t = order[i];
                order[i] = order[j];
                order[j] = t;
            }
        }
    }

    private static final java.util.Map<Long, LiveJob> LIVE = new java.util.LinkedHashMap<>();

    static boolean playerNear(ServerLevel level, ChunkPos cp, double radius) {
        double x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
        for (var p : level.players()) {
            double dx = p.getX() - x, dz = p.getZ() - z;
            if (dx * dx + dz * dz < radius * radius) return true;
        }
        return false;
    }

    private static void tickLive(ServerLevel level, InvasionData data) {
        if (LIVE.isEmpty()) return;
        int jobs = 0;
        for (java.util.Iterator<LiveJob> it = LIVE.values().iterator(); it.hasNext() && jobs < 16; jobs++) {
            LiveJob job = it.next();
            ChunkPos cp = new ChunkPos(job.key);
            LevelChunk chunk = level.getChunkSource().getChunkNow(cp.x, cp.z);
            InvasionData.Cell c = data.cells.get(job.key);
            if (chunk == null || c == null) {
                it.remove();
                continue;
            }
            Faction f = data.faction(c.faction);
            for (int n = 0; n < 6 && job.index < 256; n++) {
                int col = job.order[job.index++];
                int lx = col & 15, lz = col >> 4;
                corruptColumn(level, chunk, lx, lz, job.full, job.replace, f);
                if ((col * 7 + job.index) % 3 == 0) {
                    int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
                    int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz) + 1;
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK, x + 0.5, y + 0.1, z + 0.5, 2, 0.3, 0.05, 0.3, 0.01);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, x + 0.5, y + 0.2, z + 0.5, 2, 0.3, 0.1, 0.3, 0.02);
                }
            }
            if (job.index % 48 == 0) {
                level.playSound(null, new BlockPos(cp.getMiddleBlockX(), chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8), cp.getMiddleBlockZ()),
                        net.minecraft.sounds.SoundEvents.SCULK_BLOCK_SPREAD, net.minecraft.sounds.SoundSource.BLOCKS, 1.2F, 0.6F);
            }
            if (job.index >= 256) {
                it.remove();
                finish(level, data, c, chunk, job.target);
                data.setDirty();
            }
        }
    }
}
