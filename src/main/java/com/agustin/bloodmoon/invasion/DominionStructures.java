package com.agustin.bloodmoon.invasion;

import com.agustin.bloodmoon.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estructuras mayores del Dominio (plantillas de {@link DominionTemplates}, 3×3 chunks, la puerta mira al eje) y los
 * caminos de roca negra que las unen.
 * <ul>
 *   <li>Nido de Ceniza: fosa de 8 en terrazas bajo seis costillas que se cierran; altar con el núcleo y fuego de almas.</li>
 *   <li>Atalaya: torre de ~46 de alto con contrafuertes, escalera de caracol, corona con parapeto y cuernos; sede de un Capitán.</li>
 *   <li>Fortaleza: muralla de 37×37 con camino de ronda, cuatro torres cónicas, barbacana con rastrillo y un torreón de
 *   15×15 con aguja; sede de un General.</li>
 *   <li>Puerta de Guerra: arco de 15×23 con un velo de vidrio del Vacío, desde donde sale un asalto.</li>
 * </ul>
 * Cada estructura (salvo la puerta) tiene un Núcleo de Obelisco: romperlo la deja en ruinas.
 */
public final class DominionStructures {
    public static final int NONE = 0, NEST = 1, TOWER = 2, FORTRESS = 3, SOUL = 4, SPIRE = 5;
    /** Direcciones de los 8 vecinos, en el orden de los bits del camino. */
    public static final int[][] DIRS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private DominionStructures() {}

    public static int dirBit(int dx, int dz) {
        for (int i = 0; i < 8; i++) if (DIRS[i][0] == dx && DIRS[i][1] == dz) return 1 << i;
        return 0;
    }

    // ------------------------------------------------------------------ planos (plantillas)

    private static BlockState bricks(int x, int y, int z) {
        return (DominionTerraform.hash(x, z, y) < 0.3 ? ModBlocks.CRACKED_BLACK_ROCK_BRICKS : ModBlocks.BLACK_ROCK_BRICKS).get().defaultBlockState();
    }

    private static BlockState rock() { return ModBlocks.BLACK_ROCK.get().defaultBlockState(); }

    private static BlockState lantern() { return ModBlocks.VOID_LANTERN.get().defaultBlockState(); }

    public static String templateName(int type) {
        return switch (type) {
            case NEST -> "nest";
            case TOWER -> "tower";
            case SOUL -> "soul_site";
            case SPIRE -> "spire";
            default -> "fortress";
        };
    }

    /** Las estructuras mayores miran (la puerta) hacia el eje del Dominio. */
    public static Rotation rotation(ChunkPos cp, Faction f) {
        return DominionTemplates.facing(cp.getMiddleBlockX(), cp.getMiddleBlockZ(), f.center.getX(), f.center.getZ());
    }

    /** Desplazamiento (del diseño, puerta a +Z) girado según la orientación de la estructura del chunk. */
    public static int[] offset(ChunkPos cp, Faction f, int dx, int dz) {
        return DominionTemplates.rotate(dx, dz, rotation(cp, f));
    }

    /** Nido de Ceniza, Atalaya o Fortaleza: ocupan 3×3 chunks con el núcleo en el centro del chunk central. */
    public static DominionTerraform.Plan plan(ServerLevel level, ChunkPos cp, int type, Faction f) {
        DominionTemplates.Template t = DominionTemplates.get(level, templateName(type));
        return DominionTemplates.plan(level, t, cp.getMinBlockX() + 8, cp.getMinBlockZ() + 8, f == null ? Rotation.NONE : rotation(cp, f));
    }

    /** Radio en chunks de la huella: 1 (3×3) para las estructuras mayores, 2 (5×5) para el Santuario de la Primera Alma. */
    public static int footprint(int type) {
        return type == SOUL ? 5 : type == SPIRE ? 2 : 1;
    }

    /** ¿Están cargados todos los chunks que pisa una estructura mayor? */
    public static boolean footprintLoaded(ServerLevel level, ChunkPos cp) {
        return footprintLoaded(level, cp, 1);
    }

    public static boolean footprintLoaded(ServerLevel level, ChunkPos cp, int r) {
        for (int ox = -r; ox <= r; ox++) for (int oz = -r; oz <= r; oz++) {
            if (level.getChunkSource().getChunkNow(cp.x + ox, cp.z + oz) == null) return false;
        }
        return true;
    }

    /** ¿Este chunk es parte de la huella (3×3) de alguna estructura mayor? */
    public static boolean inFootprint(InvasionData data, ChunkPos cp) {
        for (int ox = -5; ox <= 5; ox++) for (int oz = -5; oz <= 5; oz++) {
            InvasionData.Cell c = data.cells.get(ChunkPos.asLong(cp.x + ox, cp.z + oz));
            if (c != null && c.structure != NONE && Math.abs(ox) <= footprint(c.structure) && Math.abs(oz) <= footprint(c.structure)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Puerta de Guerra

    /** Arco monumental de 15 de ancho y 23 de alto con un velo de vidrio del Vacío; {@code alongX}: el arco corre en el eje X. */
    public static List<DominionTerraform.Placement> gate(ServerLevel level, int cx, int cz, boolean alongX) {
        int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        DominionTemplates.Template t = DominionTemplates.get(level, "gate");
        Rotation rot = alongX ? Rotation.NONE : Rotation.CLOCKWISE_90;
        List<DominionTerraform.Placement> out = new ArrayList<>();
        for (DominionTemplates.Entry e : t.blocks()) {
            if (e.state().isAir()) continue;
            int[] d = DominionTemplates.rotate(e.x(), e.z(), rot);
            out.add(new DominionTerraform.Placement(new BlockPos(cx + d[0], y0 + e.y(), cz + d[1]), e.state().rotate(rot)));
        }
        return out;
    }

    // ------------------------------------------------------------------ caminos

    /**
     * Traza los tramos de camino de este chunk: de su centro hacia cada vecino unido (3 de ancho; en diagonal, 5 en cruz
     * para que los tramos se toquen), con farolas. Dentro de la huella de una estructura llega hasta sus muros.
     */
    static void drawRoads(ServerLevel level, LevelChunk chunk, InvasionData.Cell c) {
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        boolean inside = inFootprint(InvasionData.get(level), chunk.getPos());
        int skip = c.obelisk || c.obeliskBuilt ? 7 : 0;   // no cortar el basamento del obelisco
        for (int i = 0; i < 8; i++) {
            if ((c.roadMask & 1 << i) == 0) continue;
            int dx = DIRS[i][0], dz = DIRS[i][1];
            boolean diag = dx != 0 && dz != 0;
            for (int s = skip; s <= 8; s++) {
                int lx = 8 + dx * s, lz = 8 + dz * s;
                for (int w = -1; w <= 1; w++) {
                    if (diag) {
                        pave(level, chunk, minX, minZ, lx + w, lz);
                        pave(level, chunk, minX, minZ, lx, lz + w);
                    } else {
                        pave(level, chunk, minX, minZ, lx + (dz != 0 ? w : 0), lz + (dz == 0 ? w : 0));
                    }
                }
                if (!inside && s == 5 && !diag) {   // farola al costado
                    int fx = lx + (dz != 0 ? 2 : 0), fz = lz + (dz == 0 ? 2 : 0);
                    if (fx >= 0 && fz >= 0 && fx <= 15 && fz <= 15) lamp(level, chunk, minX + fx, minZ + fz);
                }
            }
        }
    }

    private static void pave(ServerLevel level, LevelChunk chunk, int minX, int minZ, int px, int pz) {
        if (px < 0 || pz < 0 || px > 15 || pz > 15) return;
        paveAt(level, chunk, minX + px, minZ + pz);
    }

    /** Suelo real de la columna: se saltan troncos (también los calcinados), hojas, plantas y bloques reemplazables. */
    private static int groundY(LevelChunk chunk, int x, int z) {
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
        int min = chunk.getMinBuildHeight();
        for (int k = 0; k < 40 && y > min; k++) {
            BlockState st = chunk.getBlockState(new BlockPos(x, y, z));
            if (st.isAir() || st.is(BlockTags.LOGS) || st.is(ModBlocks.CHARRED_LOG.get()) || st.is(BlockTags.LEAVES)
                    || (st.canBeReplaced() && st.getFluidState().isEmpty())) y--;
            else break;
        }
        return y;
    }

    /** Tierra natural (o corrompida) sobre la que se puede pavimentar o plantar una farola. */
    private static boolean natural(BlockState st) {
        if (!BuiltinRegistriesHolder.isModStructure(st)) return !st.is(BlockTags.STAIRS) && !st.is(BlockTags.SLABS) && !st.is(BlockTags.WALLS);
        return st.is(ModBlocks.DEAD_GRASS_BLOCK.get()) || st.is(ModBlocks.BARREN_DIRT.get()) || st.is(ModBlocks.BLACK_ROCK.get());
    }

    private static void paveAt(ServerLevel level, LevelChunk chunk, int x, int z) {
        int y = groundY(chunk, x, z);
        BlockPos top = new BlockPos(x, y, z);
        BlockState st = chunk.getBlockState(top);
        if (!natural(st)) return;   // muros, pisos y escalones de las estructuras (o camino ya hecho)
        level.setBlock(top, bricks(x, y, z), FLAGS);
        // paso libre: fuera troncos, hojas y plantas en 3 de alto (y el resto del tronco, para que no flote)
        for (int k = 1; k <= 12; k++) {
            BlockPos above = top.above(k);
            BlockState a = chunk.getBlockState(above);
            boolean trunk = a.is(BlockTags.LOGS) || a.is(ModBlocks.CHARRED_LOG.get()) || a.is(BlockTags.LEAVES);
            if (k <= 3 && !a.isAir() && (a.canBeReplaced() || trunk)) level.setBlock(above, Blocks.AIR.defaultBlockState(), FLAGS);
            else if (k > 3 && trunk) level.setBlock(above, Blocks.AIR.defaultBlockState(), FLAGS);
            else if (k > 3) break;
        }
    }

    private static void lamp(ServerLevel level, LevelChunk chunk, int x, int z) {
        int g = groundY(chunk, x, z);
        BlockState under = chunk.getBlockState(new BlockPos(x, g, z));
        if (!natural(under)) return;
        int y = g + 1;
        BlockState wall = ModBlocks.BLACK_ROCK_BRICK_WALL.get().defaultBlockState();
        level.setBlock(new BlockPos(x, y, z), ModBlocks.CHISELED_BLACK_ROCK_BRICKS.get().defaultBlockState(), FLAGS);
        level.setBlock(new BlockPos(x, y + 1, z), wall, FLAGS);
        level.setBlock(new BlockPos(x, y + 2, z), wall, FLAGS);
        level.setBlock(new BlockPos(x, y + 3, z), lantern(), FLAGS);
    }

    /** ¿Bloque del mod? (para no pisar con el camino las piezas de otras estructuras). */
    private static final class BuiltinRegistriesHolder {
        static boolean isModStructure(BlockState s) {
            return com.agustin.bloodmoon.BloodMoonMod.MODID.equals(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace());
        }
    }
}
