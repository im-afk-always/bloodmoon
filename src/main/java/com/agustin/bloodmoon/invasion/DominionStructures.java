package com.agustin.bloodmoon.invasion;

import com.agustin.bloodmoon.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estructuras mayores del Dominio (todas caben en su chunk) y los caminos de roca negra que las unen.
 * <ul>
 *   <li>Nido de Ceniza: fosa con fuego de almas que produce tropas.</li>
 *   <li>Atalaya: torre de 7×7 y ~20 de alto con plataforma; arqueros arriba; sede de un Capitán.</li>
 *   <li>Fortaleza: muralla de 15×15 con torres en las esquinas y un torreón; sede de un General.</li>
 *   <li>Puerta de Guerra: arco con un velo violeta, desde donde sale un asalto.</li>
 * </ul>
 * Cada estructura (salvo la puerta) tiene un Núcleo de Obelisco: romperlo la deja en ruinas.
 */
public final class DominionStructures {
    public static final int NONE = 0, NEST = 1, TOWER = 2, FORTRESS = 3;
    /** Direcciones de los 8 vecinos, en el orden de los bits del camino. */
    public static final int[][] DIRS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private DominionStructures() {}

    public static int dirBit(int dx, int dz) {
        for (int i = 0; i < 8; i++) if (DIRS[i][0] == dx && DIRS[i][1] == dz) return 1 << i;
        return 0;
    }

    // ------------------------------------------------------------------ armado de planos

    private static BlockState rock() { return ModBlocks.BLACK_ROCK.get().defaultBlockState(); }

    private static BlockState bricks(int x, int y, int z) {
        return (DominionTerraform.hash(x, z, y) < 0.3 ? ModBlocks.CRACKED_BLACK_ROCK_BRICKS : ModBlocks.BLACK_ROCK_BRICKS).get().defaultBlockState();
    }

    private static BlockState lantern() { return ModBlocks.VOID_LANTERN.get().defaultBlockState(); }

    private static BlockState core() { return ModBlocks.OBELISK_CORE.get().defaultBlockState(); }

    /** Junta bloques relativos a (cx, y0, cz) y arma un plano: despejar, cimientos, y de abajo hacia arriba. */
    private static final class Builder {
        final ServerLevel level;
        final int cx, cz, y0;
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        int minDx = 99, maxDx = -99, minDz = 99, maxDz = -99, maxDy = 0;

        Builder(ServerLevel level, int cx, int cz) {
            this.level = level;
            this.cx = cx;
            this.cz = cz;
            this.y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        }

        void set(int dx, int dy, int dz, BlockState s) {
            blocks.put(new BlockPos(cx + dx, y0 + dy, cz + dz), s);
            minDx = Math.min(minDx, dx); maxDx = Math.max(maxDx, dx);
            minDz = Math.min(minDz, dz); maxDz = Math.max(maxDz, dz);
            maxDy = Math.max(maxDy, dy);
        }

        DominionTerraform.Plan build(int coreDy) {
            List<DominionTerraform.Placement> out = new ArrayList<>();
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int dy = maxDy + 2; dy >= 1; dy--) for (int dx = minDx; dx <= maxDx; dx++) for (int dz = minDz; dz <= maxDz; dz++) {
                BlockPos q = new BlockPos(cx + dx, y0 + dy, cz + dz);
                if (!blocks.containsKey(q) && !level.getBlockState(q).isAir()) out.add(new DominionTerraform.Placement(q, air));
            }
            for (int dx = minDx; dx <= maxDx; dx++) for (int dz = minDz; dz <= maxDz; dz++) {
                List<DominionTerraform.Placement> col = new ArrayList<>();
                for (int d = 1; d <= 10; d++) {
                    BlockPos q = new BlockPos(cx + dx, y0 - d, cz + dz);
                    if (blocks.containsKey(q)) break;
                    BlockState st = level.getBlockState(q);
                    if (!st.isAir() && st.getFluidState().isEmpty() && !st.canBeReplaced() && !st.is(BlockTags.LEAVES)) break;
                    col.add(0, new DominionTerraform.Placement(q, rock()));
                }
                out.addAll(col);
            }
            List<Map.Entry<BlockPos, BlockState>> sorted = new ArrayList<>(blocks.entrySet());
            sorted.sort(Comparator.comparingInt(e -> e.getKey().getY()));
            for (Map.Entry<BlockPos, BlockState> e : sorted) out.add(new DominionTerraform.Placement(e.getKey(), e.getValue()));
            return new DominionTerraform.Plan(out, y0 + coreDy);
        }
    }

    public static DominionTerraform.Plan plan(ServerLevel level, ChunkPos cp, int type) {
        int cx = cp.getMinBlockX() + 8, cz = cp.getMinBlockZ() + 8;
        return switch (type) {
            case NEST -> nest(level, cx, cz);
            case TOWER -> tower(level, cx, cz);
            default -> fortress(level, cx, cz);
        };
    }

    /** Nido de Ceniza: fosa de 9 de diámetro con borde, fuego de almas y el núcleo en el fondo. */
    private static DominionTerraform.Plan nest(ServerLevel level, int cx, int cz) {
        Builder b = new Builder(level, cx, cz);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            double r = Math.sqrt(dx * dx + dz * dz);
            int x = cx + dx, z = cz + dz;
            if (r <= 4.0) {
                b.set(dx, 0, dz, air);
                b.set(dx, -1, dz, air);
                b.set(dx, -2, dz, r < 1.6 ? Blocks.SOUL_FIRE.defaultBlockState() : air);
                b.set(dx, -3, dz, dx == 0 && dz == 0 ? core() : r < 1.6 ? Blocks.SOUL_SOIL.defaultBlockState()
                        : DominionTerraform.hash(x, z, 3) < 0.5 ? rock() : ModBlocks.BARREN_DIRT.get().defaultBlockState());
            } else if (r <= 4.6) {
                for (int dy = -3; dy <= -1; dy++) b.set(dx, dy, dz, rock());
                b.set(dx, 0, dz, bricks(x, 0, z));
            } else if (r <= 5.7) {
                b.set(dx, 0, dz, bricks(x, 0, z));
                if ((dx + dz & 1) == 0) b.set(dx, 1, dz, rock());
            }
        }
        // dientes en las diagonales
        for (int[] d : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
            for (int dy = 1; dy <= 4; dy++) b.set(d[0], dy, d[1], dy == 4 ? lantern() : rock());
        }
        return b.build(-3);
    }

    /** Atalaya: torre hueca de 7×7, ventanas, puerta al sur y plataforma de 9×9 con almenas y faroles. */
    private static DominionTerraform.Plan tower(ServerLevel level, int cx, int cz) {
        Builder b = new Builder(level, cx, cz);
        int h = 20;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            boolean ring = Math.abs(dx) == 3 || Math.abs(dz) == 3;
            boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
            b.set(dx, 0, dz, rock());
            for (int dy = 1; dy < h; dy++) {
                if (!ring) { b.set(dx, dy, dz, air); continue; }
                boolean door = dz == 3 && dx == 0 && dy <= 2;
                boolean window = !corner && (dx == 0 || dz == 0) && dy % 5 == 0;
                b.set(dx, dy, dz, door || window ? air : corner ? rock() : bricks(cx + dx, dy, cz + dz));
            }
        }
        b.set(0, 1, 0, core());
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            b.set(dx, h, dz, bricks(cx + dx, h, cz + dz));
            boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
            if (edge && (dx + dz & 1) == 0) b.set(dx, h + 1, dz, rock());
        }
        for (int[] d : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) b.set(d[0], h + 1, d[1], lantern());
        return b.build(1);
    }

    /** Fortaleza: muralla de 15×15 con almenas, cuatro torres, puerta al sur y un torreón con el núcleo. */
    private static DominionTerraform.Plan fortress(ServerLevel level, int cx, int cz) {
        Builder b = new Builder(level, cx, cz);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            int ax = Math.abs(dx), az = Math.abs(dz);
            int x = cx + dx, z = cz + dz;
            b.set(dx, 0, dz, bricks(x, 0, z));
            boolean towerCol = ax >= 6 && az >= 6;
            boolean wall = ax == 7 || az == 7;
            boolean keep = ax <= 3 && az <= 3;
            boolean keepWall = keep && (ax == 3 || az == 3);
            for (int dy = 1; dy <= 13; dy++) {
                BlockState st = null;
                if (towerCol) {
                    if (dy <= 12) st = rock();
                    else if ((ax == 7 || az == 7) && (dx + dz & 1) == 0) st = rock();
                } else if (wall) {
                    boolean gate = dz == 7 && ax <= 1 && dy <= 4;
                    if (dy <= 8) st = gate ? air : bricks(x, dy, z);
                    else if (dy == 9 && (dx + dz & 1) == 0) st = rock();
                } else if (keep) {
                    if (keepWall) {
                        boolean door = dz == 3 && dx == 0 && dy <= 2;
                        if (dy <= 11) st = door ? air : bricks(x, dy, z);
                    } else if (dy <= 11) st = air;
                    if (dy == 12) st = bricks(x, dy, z);
                } else if (dy <= 8) st = air;
                if (st != null) b.set(dx, dy, dz, st);
            }
        }
        for (int[] d : new int[][]{{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) b.set(d[0], 13, d[1], lantern());
        b.set(0, 13, 0, lantern());
        b.set(0, 5, 7, lantern());   // sobre la puerta (dentro del chunk)
        b.set(0, 1, 0, core());
        return b.build(1);
    }

    // ------------------------------------------------------------------ Puerta de Guerra

    /** Arco de 7 de ancho y 10 de alto con un velo violeta; {@code alongX}: el arco corre en el eje X. */
    public static List<DominionTerraform.Placement> gate(ServerLevel level, int cx, int cz, boolean alongX) {
        int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        List<DominionTerraform.Placement> out = new ArrayList<>();
        for (int a = -3; a <= 3; a++) {
            for (int dy = 0; dy <= 10; dy++) {
                int x = alongX ? cx + a : cx, z = alongX ? cz : cz + a;
                BlockPos q = new BlockPos(x, y0 + dy, z);
                BlockState st;
                if (Math.abs(a) == 3) st = dy <= 9 ? rock() : null;
                else if (dy == 9) st = bricks(x, dy, z);
                else if (dy == 10) st = a == 0 ? lantern() : Math.abs(a) == 2 ? rock() : null;
                else if (dy == 0) st = Math.abs(a) <= 2 ? ModBlocks.BLACK_ROCK_BRICKS.get().defaultBlockState() : null;
                else st = Math.abs(a) <= 2 ? Blocks.PURPLE_STAINED_GLASS.defaultBlockState() : null;
                if (st != null) out.add(new DominionTerraform.Placement(q, st));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ caminos

    /** Traza los tramos de camino de este chunk: de su centro hacia cada vecino unido (3 de ancho, con faroles). */
    static void drawRoads(ServerLevel level, LevelChunk chunk, InvasionData.Cell c) {
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        int skip = c.structure != NONE ? 8 : c.obelisk || c.obeliskBuilt ? 4 : 0;   // no cortar la estructura del chunk
        for (int i = 0; i < 8; i++) {
            if ((c.roadMask & 1 << i) == 0) continue;
            int dx = DIRS[i][0], dz = DIRS[i][1];
            int steps = 9;
            for (int s = 0; s < steps; s++) {
                if (s < skip) continue;
                int lx = 8 + dx * s, lz = 8 + dz * s;
                for (int w = -1; w <= 1; w++) {
                    int px = lx + (dz != 0 ? w : 0), pz = lz + (dz == 0 ? w : 0);
                    if (dx != 0 && dz != 0) { px = lx + w; pz = lz; }
                    if (px < 0 || pz < 0 || px > 15 || pz > 15) continue;
                    paveAt(level, chunk, minX + px, minZ + pz);
                }
                if (s == 6 && dx * dz == 0) {   // farol al costado
                    int fx = lx + (dz != 0 ? 2 : 0), fz = lz + (dz == 0 ? 2 : 0);
                    if (fx >= 0 && fz >= 0 && fx <= 15 && fz <= 15) lamp(level, chunk, minX + fx, minZ + fz);
                }
            }
        }
    }

    private static void paveAt(ServerLevel level, LevelChunk chunk, int x, int z) {
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
        BlockPos top = new BlockPos(x, y, z);
        BlockState st = chunk.getBlockState(top);
        if (st.is(ModBlocks.OBELISK_CORE.get()) || st.is(ModBlocks.VOID_LANTERN.get())) return;
        if (BuiltinRegistriesHolder.isModStructure(st) && !st.is(ModBlocks.DEAD_GRASS_BLOCK.get()) && !st.is(ModBlocks.BARREN_DIRT.get())
                && !st.is(ModBlocks.BLACK_ROCK.get())) return;
        level.setBlock(top, bricks(x, y, z), FLAGS);
        for (int k = 1; k <= 2; k++) {
            BlockPos above = top.above(k);
            BlockState a = chunk.getBlockState(above);
            if (!a.isAir() && a.canBeReplaced()) level.setBlock(above, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    private static void lamp(ServerLevel level, LevelChunk chunk, int x, int z) {
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
        level.setBlock(new BlockPos(x, y, z), rock(), FLAGS);
        level.setBlock(new BlockPos(x, y + 1, z), rock(), FLAGS);
        level.setBlock(new BlockPos(x, y + 2, z), lantern(), FLAGS);
    }

    /** ¿Bloque del mod? (para no pisar con el camino las piezas de otras estructuras). */
    private static final class BuiltinRegistriesHolder {
        static boolean isModStructure(BlockState s) {
            return com.agustin.bloodmoon.BloodMoonMod.MODID.equals(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace());
        }
    }
}
