package com.agustin.bloodmoon.human;

import com.agustin.bloodmoon.entity.ModEntities;
import com.agustin.bloodmoon.invasion.DominionTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Map;

/**
 * Lo que sabe poner una aldea en el mundo: talar, pavimentar, cimentar, colocar plantillas, faroles y habitantes.
 * Lo usan la generación del mundo (por chunk, {@code wg = true}) y el crecimiento en partida (mundo cargado).
 * {@code box} = {x0, z0, x1, z1}: solo se toca lo que cae adentro.
 */
public final class VillageBuilder {
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private VillageBuilder() {}

    static boolean passable(BlockState st) {
        return st.isAir() || !st.getFluidState().isEmpty() || st.canBeReplaced() || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS)
                || st.is(Blocks.SNOW) || st.is(Blocks.CACTUS) || st.is(Blocks.BAMBOO);
    }

    static boolean in(int[] box, int x, int z) {
        return x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3];
    }

    /** Altura del suelo real (sin árboles ni plantas): la y del bloque sólido o del agua en la superficie. */
    public static int ground(WorldGenLevel level, int x, int z, boolean wg) {
        Heightmap.Types type = wg ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.WORLD_SURFACE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(type, x, z) - 1, z);
        int min = level.getMinBuildHeight();
        for (int k = 0; k < 48 && p.getY() > min; k++) {
            BlockState st = level.getBlockState(p);
            if (!st.getFluidState().isEmpty()) return p.getY();
            if (!passable(st)) return p.getY();
            p.move(0, -1, 0);
        }
        return p.getY();
    }

    /** Tala árboles en las columnas cercanas a edificios o calles. */
    public static void clearTrees(WorldGenLevel level, int[] box, List<VillageLayout.Building> buildings, List<VillageLayout.Road> roads, boolean wg) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = box[0]; x <= box[2]; x++) {
            for (int z = box[1]; z <= box[3]; z++) {
                boolean near = false;
                for (VillageLayout.Building b : buildings) {
                    if (b.contains(x, z, 3)) {
                        near = true;
                        break;
                    }
                }
                if (!near) {
                    for (VillageLayout.Road r : roads) {
                        if (r.dist(x, z) <= r.half() + 2.5) {
                            near = true;
                            break;
                        }
                    }
                }
                if (!near) continue;
                if (!wg && level instanceof ServerLevel sl && !sl.hasChunk(x >> 4, z >> 4)) continue;
                int g = ground(level, x, z, wg);
                for (int y = g + 40; y > g; y--) {
                    p.set(x, y, z);
                    BlockState st = level.getBlockState(p);
                    if (st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.is(Blocks.VINE) || st.is(Blocks.COCOA)
                            || st.is(Blocks.BEE_NEST) || st.is(Blocks.SNOW)) {
                        level.setBlock(p, air, FLAGS);
                    }
                }
            }
        }
    }

    /**
     * Pavimenta las calles (sin pisar edificios), niveladas en rampa según la altura planificada de cada tramo: corta las
     * lomas, rellena los pozos (hasta 5 bloques) y cruza el agua con un puente. {@code stone}: empedrado de ciudad.
     */
    public static void pave(WorldGenLevel level, int[] box, List<VillageLayout.Road> roads, List<VillageLayout.Building> buildings,
                            boolean desert, boolean wg) {
        pave(level, box, roads, buildings, desert, wg, false);
    }

    public static void pave(WorldGenLevel level, int[] box, List<VillageLayout.Road> roads, List<VillageLayout.Building> buildings,
                            boolean desert, boolean wg, boolean stone) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState fill = (desert ? Blocks.SANDSTONE : Blocks.DIRT).defaultBlockState();
        for (int x = box[0]; x <= box[2]; x++) {
            for (int z = box[1]; z <= box[3]; z++) {
                VillageLayout.Road road = null;
                double best = Double.MAX_VALUE;
                for (VillageLayout.Road r : roads) {
                    double d = r.dist(x, z);
                    if (d <= r.half() && d - r.half() < best) {
                        best = d - r.half();
                        road = r;
                    }
                }
                if (road == null) continue;
                if (!wg && level instanceof ServerLevel sl && !sl.hasChunk(x >> 4, z >> 4)) continue;
                boolean inside = false;
                for (VillageLayout.Building b : buildings) {
                    if (b.contains(x, z, 0)) {
                        inside = true;
                        break;
                    }
                }
                if (inside) continue;
                int g = ground(level, x, z, wg);
                p.set(x, g, z);
                BlockState top = level.getBlockState(p);
                if (!wg && !natural(top) && !isPath(top) && !isStone(top)) continue;   // en partida no se pisa lo del jugador
                int want = road.y(x, z);
                int y = want == Integer.MIN_VALUE ? g : want - 1;
                long h = hash(x, z);
                if (!top.getFluidState().isEmpty()) {
                    // puente: a la altura de la calle, nunca por debajo del agua
                    int by = Math.max(y, g);
                    level.setBlock(p.set(x, by, z), (desert ? Blocks.SMOOTH_SANDSTONE : stone ? Blocks.STONE_BRICKS : Blocks.SPRUCE_PLANKS).defaultBlockState(), FLAGS);
                    for (int k = 1; k <= 3; k++) {
                        p.set(x, by + k, z);
                        if (!level.getBlockState(p).isAir() && level.getBlockState(p).getFluidState().isEmpty()) level.setBlock(p, air, FLAGS);
                    }
                    continue;
                }
                if (y - g > 5) y = g + 5;
                if (g - y > 6) y = g - 6;
                // cortar la loma por encima de la calle
                for (int yy = y + 1; yy <= Math.max(g, y) + 3; yy++) {
                    p.set(x, yy, z);
                    BlockState st = level.getBlockState(p);
                    if (!st.isAir() && st.getFluidState().isEmpty() && (wg || natural(st))) level.setBlock(p, air, FLAGS);
                }
                // rellenar el pozo por debajo
                for (int yy = g + 1; yy < y; yy++) level.setBlock(p.set(x, yy, z), fill, FLAGS);
                BlockState path;
                if (stone) {
                    if (desert) path = (h % 6 == 0 ? Blocks.CUT_SANDSTONE : h % 5 == 0 ? Blocks.CHISELED_SANDSTONE : Blocks.SMOOTH_SANDSTONE).defaultBlockState();
                    else path = (h % 4 == 0 ? Blocks.STONE_BRICKS : h % 5 == 0 ? Blocks.CRACKED_STONE_BRICKS : h % 7 == 0 ? Blocks.ANDESITE : Blocks.POLISHED_ANDESITE).defaultBlockState();
                } else if (desert) {
                    path = (h % 5 == 0 ? Blocks.SANDSTONE : h % 7 == 0 ? Blocks.CUT_SANDSTONE : Blocks.SMOOTH_SANDSTONE).defaultBlockState();
                } else {
                    path = (h % 9 == 0 ? Blocks.GRAVEL : h % 13 == 0 ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH).defaultBlockState();
                }
                level.setBlock(p.set(x, y, z), path, FLAGS);
            }
        }
    }

    private static boolean isStone(BlockState st) {
        return st.is(Blocks.STONE_BRICKS) || st.is(Blocks.CRACKED_STONE_BRICKS) || st.is(Blocks.POLISHED_ANDESITE) || st.is(Blocks.ANDESITE)
                || st.is(Blocks.CHISELED_SANDSTONE) || st.is(Blocks.GRAVEL) || st.is(Blocks.COARSE_DIRT);
    }

    /**
     * Nivela el terreno alrededor de un edificio: el patio queda a la altura del piso y los 3 bloques siguientes hacen
     * una pendiente suave hasta el terreno natural (los constructores cortan lomas y rellenan pozos). No toca calles.
     */
    public static void yard(WorldGenLevel level, VillageLayout.Building b, int[] box, List<VillageLayout.Road> roads, boolean desert, boolean wg) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState fill = (desert ? Blocks.SANDSTONE : Blocks.DIRT).defaultBlockState();
        BlockState topBlock = (desert ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState();
        int m = 3;
        for (int x = Math.max(box[0], b.minX() - m); x <= Math.min(box[2], b.maxX() + m); x++) {
            for (int z = Math.max(box[1], b.minZ() - m); z <= Math.min(box[3], b.maxZ() + m); z++) {
                if (!wg && level instanceof ServerLevel sl && !sl.hasChunk(x >> 4, z >> 4)) continue;
                boolean onRoad = false;
                for (VillageLayout.Road r : roads) {
                    if (r.dist(x, z) <= r.half() + 0.5) {
                        onRoad = true;
                        break;
                    }
                }
                if (onRoad) continue;
                int k = Math.max(Math.max(b.minX() - x, x - b.maxX()), Math.max(b.minZ() - z, z - b.maxZ()));
                k = Math.max(0, k);
                int g = ground(level, x, z, wg);
                BlockState top = level.getBlockState(p.set(x, g, z));
                if (!top.getFluidState().isEmpty()) continue;
                if (!wg && !natural(top)) continue;
                int target = b.floorY() + (int) Math.round((g - b.floorY()) * k / (double) (m + 1));
                if (g > target) {
                    for (int y = target + 1; y <= g + 2; y++) {
                        p.set(x, y, z);
                        BlockState st = level.getBlockState(p);
                        if (!st.isAir() && st.getFluidState().isEmpty() && (wg || natural(st))) level.setBlock(p, air, FLAGS);
                    }
                    p.set(x, target, z);
                    BlockState t = level.getBlockState(p);
                    if (t.is(BlockTags.DIRT) || t.is(BlockTags.BASE_STONE_OVERWORLD) || t.is(BlockTags.SAND) || t.is(Blocks.SANDSTONE)) {
                        level.setBlock(p, topBlock, FLAGS);
                    }
                } else if (g < target) {
                    for (int y = Math.max(g + 1, target - 6); y < target; y++) level.setBlock(p.set(x, y, z), fill, FLAGS);
                    level.setBlock(p.set(x, target, z), topBlock, FLAGS);
                }
            }
        }
    }

    private static boolean isPath(BlockState st) {
        return st.is(Blocks.DIRT_PATH) || st.is(Blocks.SMOOTH_SANDSTONE) || st.is(Blocks.CUT_SANDSTONE) || st.is(Blocks.SPRUCE_PLANKS);
    }

    /** Bloques que el terreno trae solo (para no construir encima de obras del jugador). */
    public static boolean natural(BlockState st) {
        return passable(st) || st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(BlockTags.BASE_STONE_OVERWORLD)
                || st.is(BlockTags.TERRACOTTA) || st.is(Blocks.GRAVEL) || st.is(Blocks.SANDSTONE) || st.is(Blocks.RED_SANDSTONE)
                || st.is(Blocks.CLAY) || st.is(Blocks.ICE) || st.is(Blocks.PACKED_ICE) || st.is(Blocks.SNOW_BLOCK)
                || st.is(BlockTags.COAL_ORES) || st.is(BlockTags.IRON_ORES) || st.is(BlockTags.COPPER_ORES) || st.is(Blocks.DIRT_PATH)
                || st.is(Blocks.FARMLAND) || st.is(Blocks.MOSS_BLOCK) || st.is(Blocks.MUD) || st.is(Blocks.POWDER_SNOW);
    }

    /** Cuántos bloques "artificiales" hay en el volumen del edificio (en partida, con los chunks cargados). */
    public static int artificial(ServerLevel level, VillageLayout.Building b, int limit) {
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int y = b.floorY() - 1; y <= b.floorY() + 10; y++) {
                    BlockState st = level.getBlockState(p.set(x, y, z));
                    if (!natural(st) && !st.is(BlockTags.FENCES) && !st.is(Blocks.LANTERN) && !st.is(Blocks.SANDSTONE_WALL)) {
                        if (++n > limit) return n;
                    }
                }
            }
        }
        return n;
    }

    /** Despeja el volumen y cimienta hasta el suelo firme, columna por columna. */
    public static void prepare(WorldGenLevel level, ServerLevel server, VillageLayout.Building b, int[] box, boolean desert) {
        VillageLayout.VTemplate vt = VillageLayout.template(server, b.template());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState found = (desert ? Blocks.SANDSTONE : Blocks.COBBLESTONE).defaultBlockState();
        int fy = b.floorY();
        for (Map.Entry<Long, int[]> col : vt.columns().entrySet()) {
            int tx = (int) (col.getKey() >> 32), tz = (int) (long) col.getKey();
            int[] w = DominionTemplates.rotate(tx, tz, b.rot());
            int x = b.x() + w[0], z = b.z() + w[1];
            if (!in(box, x, z)) continue;
            int lo = col.getValue()[0], hi = col.getValue()[1];
            for (int y = fy + Math.max(lo, 0) + 1; y <= fy + hi + 4; y++) {
                p.set(x, y, z);
                if (!level.getBlockState(p).isAir()) level.setBlock(p, air, FLAGS);
            }
            if (lo <= 0) {
                for (int y = fy + lo - 1, n = 0; n < 24 && y > level.getMinBuildHeight(); y--, n++) {
                    p.set(x, y, z);
                    if (!passable(level.getBlockState(p))) break;
                    level.setBlock(p, found, FLAGS);
                }
            }
        }
    }

    /**
     * Coloca las entradas {@code [from, to)} de la plantilla (están ordenadas de abajo hacia arriba, así una obra
     * a medias se ve subir). Devuelve cuántas cayeron dentro de la caja.
     */
    public static int place(WorldGenLevel level, ServerLevel server, VillageLayout.Building b, int[] box, int from, int to) {
        VillageLayout.VTemplate vt = VillageLayout.template(server, b.template());
        List<DominionTemplates.Entry> blocks = vt.t().blocks();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int n = 0;
        for (int i = Math.max(0, from); i < Math.min(to, blocks.size()); i++) {
            DominionTemplates.Entry e = blocks.get(i);
            int[] w = DominionTemplates.rotate(e.x(), e.z(), b.rot());
            int x = b.x() + w[0], z = b.z() + w[1];
            if (!in(box, x, z)) continue;
            p.set(x, b.floorY() + e.y(), z);
            level.setBlock(p, e.state().rotate(b.rot()), FLAGS);
            n++;
        }
        return n;
    }

    public static int size(ServerLevel server, VillageLayout.Building b) {
        return VillageLayout.template(server, b.template()).t().blocks().size();
    }

    /** Habitantes en la puerta del edificio. {@code worldgen}: sin calcular ofertas todavía. */
    public static int spawnResidents(WorldGenLevel level, ServerLevel server, VillageLayout.Building b, Culture culture, long siteSeed,
                                     long settlement, int count, boolean worldgen) {
        int n = 0;
        for (int i = 0; i < count; i++) {
            Human h = ModEntities.HUMAN.get().create(server);
            if (h == null) continue;
            int seed = (int) (siteSeed * 31 + b.x() * 7919L + b.z() * 104729L + i * 15485863L
                    + (worldgen ? 0 : server.getGameTime() * 2654435761L));
            if (worldgen) h.prepareForWorldgen();
            h.moveTo(b.coreX() + 0.5 + (i - 0.5) * 0.6, b.floorY() + 1, b.coreZ() + 0.5, b.rot().ordinal() * 90F, 0F);
            h.setup(seed, b.job(), culture);
            h.setHome(new BlockPos(b.coreX(), b.floorY() + 1, b.coreZ()));
            h.setSettlement(settlement);
            h.setPersistenceRequired();
            level.addFreshEntity(h);
            n++;
        }
        return n;
    }

    public static void lamp(WorldGenLevel level, int x, int z, boolean desert, boolean wg) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int y = ground(level, x, z, wg);
        p.set(x, y, z);
        BlockState top = level.getBlockState(p);
        if (!top.getFluidState().isEmpty()) return;
        if (!wg && !natural(top)) return;
        BlockState post = (desert ? Blocks.SANDSTONE_WALL : Blocks.SPRUCE_FENCE).defaultBlockState();
        level.setBlock(p.set(x, y + 1, z), post, FLAGS);
        level.setBlock(p.set(x, y + 2, z), post, FLAGS);
        level.setBlock(p.set(x, y + 3, z), Blocks.LANTERN.defaultBlockState(), FLAGS);
    }

    public static long hash(int x, int z) {
        long h = x * 73856093L ^ z * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return Math.abs(h);
    }

    /** Clave del asentamiento: la posición del centro. */
    public static long key(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }
}
