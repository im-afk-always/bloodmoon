package com.agustin.bloodmoon.human;

import com.agustin.bloodmoon.entity.ModEntities;
import com.agustin.bloodmoon.invasion.DominionTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    static boolean lava(BlockState st) {
        return st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
    }

    private static BlockState rock(boolean desert) {
        return (desert ? Blocks.SANDSTONE : Blocks.COBBLESTONE).defaultBlockState();
    }

    /** Apaga la columna: la lava pasa a piedra y el fuego se va (una casa de madera junto a la lava se quema entera). */
    static void douse(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int z, int y0, int y1, boolean desert) {
        for (int y = y0; y <= y1; y++) {
            BlockState st = level.getBlockState(p.set(x, y, z));
            if (lava(st)) level.setBlock(p, rock(desert), FLAGS);
            else if (st.is(BlockTags.FIRE)) level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /**
     * Sella el subsuelo bajo un suelo nuevo (calle, plaza o patio): los huecos de cuevas y minas y la lava de los 8 bloques
     * de abajo se rellenan, así no quedan bocas de cueva en medio del pueblo.
     */
    static void seal(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int z, int top, boolean desert, boolean wg) {
        BlockState dirt = (desert ? Blocks.SANDSTONE : Blocks.DIRT).defaultBlockState();
        BlockState stone = (desert ? Blocks.SANDSTONE : Blocks.STONE).defaultBlockState();
        for (int y = top - 1; y >= top - 8 && y > level.getMinBuildHeight(); y--) {
            BlockState st = level.getBlockState(p.set(x, y, z));
            // en partida solo se sellan las cuevas naturales (cave_air), nunca lo que cavó un jugador
            boolean hole = wg ? st.isAir() : st.is(Blocks.CAVE_AIR);
            if (hole || lava(st)) level.setBlock(p, y >= top - 2 ? dirt : stone, FLAGS);
        }
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

    /**
     * Etapa del territorio sobre una caja (un chunk): dentro del radio de la etapa el suelo queda en las terrazas del
     * {@link TerraceField} (corta lomas, rellena pozos, taludes de pasto, sella cuevas y apaga la lava) y, hasta el radio
     * de tala, los leñadores se llevan los árboles. Los edificios y su entorno inmediato no se tocan. En partida
     * ({@code wg = false}) solo se tocan bloques naturales, nunca lo que construyó un jugador.
     */
    public static void applyStage(WorldGenLevel level, int[] box, VillageSites.Site site, TerraceField field, int stage,
                                  List<VillageLayout.Building> buildings, boolean desert, boolean wg) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = TerraceField.radius(stage), cr = TerraceField.clearRadius(stage);
        for (int x = box[0]; x <= box[2]; x++) {
            for (int z = box[1]; z <= box[3]; z++) {
                double d = Math.hypot(x - site.x(), z - site.z());
                if (d > cr) continue;
                VillageLayout.Building inside = null;
                for (VillageLayout.Building b : buildings) if (b.contains(x, z, 1)) { inside = b; break; }
                if (inside != null) continue;
                fellColumn(level, p, x, z, wg);
                if (d > r) continue;
                int t = field.level(x, z);
                if (t != TerraceField.NONE) terraceColumn(level, p, x, z, t, desert, wg);
            }
        }
    }

    /** Leñadores: saca el árbol de la columna (troncos, hojas naturales, lianas, nidos); las hojas puestas a mano quedan. */
    static void fellColumn(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int z, boolean wg) {
        int g = ground(level, x, z, wg);
        BlockState air = Blocks.AIR.defaultBlockState();
        // ¿es un árbol? hay hojas naturales en la columna o justo al lado (un tronco suelto de un jugador no lo es)
        boolean tree = false;
        for (int y = g + 1; y <= g + 40 && !tree; y++) {
            BlockState st = level.getBlockState(p.set(x, y, z));
            if (st.is(BlockTags.LEAVES) && st.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)
                    && !st.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)) tree = true;
        }
        if (!tree) return;
        for (int y = g + 40; y > g; y--) {
            BlockState st = level.getBlockState(p.set(x, y, z));
            boolean leaf = st.is(BlockTags.LEAVES) && (!st.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)
                    || !st.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT));
            if (leaf || st.is(BlockTags.LOGS) || st.is(Blocks.VINE) || st.is(Blocks.COCOA) || st.is(Blocks.BEE_NEST)
                    || st.is(Blocks.MANGROVE_ROOTS)) {
                level.setBlock(p, air, FLAGS);
            }
        }
        // el tocón: si el árbol nacía en el suelo, el tronco de abajo también se va
        BlockState base = level.getBlockState(p.set(x, g, z));
        if (base.is(BlockTags.LOGS)) level.setBlock(p, (wg ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), FLAGS);
    }

    /** Deja la columna a la altura {@code t} de la terraza: corta o rellena, pasto o arena arriba, sella cuevas. */
    static void terraceColumn(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int z, int t, boolean desert, boolean wg) {
        if (!wg && level instanceof ServerLevel sl && !sl.hasChunk(x >> 4, z >> 4)) return;
        int g = ground(level, x, z, wg);
        BlockState top = level.getBlockState(p.set(x, g, z));
        if (lava(top)) {
            douse(level, p, x, z, g - 8, g + 1, desert);
            top = level.getBlockState(p.set(x, g, z));
        }
        if (!top.getFluidState().isEmpty()) return;              // arroyos y charcos quedan
        if (Math.abs(t - g) > TerraceField.MAX_CUT + 2) return;  // un barranco no se rellena entero
        if (!wg && !natural(top) && !isPath(top) && !isStone(top)) return;   // obra del jugador
        douse(level, p, x, z, t - 8, t + 2, desert);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState fill = (desert ? Blocks.SANDSTONE : Blocks.DIRT).defaultBlockState();
        BlockState surface = (desert ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState();
        if (g > t) {
            for (int y = t + 1; y <= g + 2; y++) {
                BlockState st = level.getBlockState(p.set(x, y, z));
                if (st.isAir() || !st.getFluidState().isEmpty()) continue;
                if (wg || natural(st)) level.setBlock(p, air, FLAGS);
            }
            level.setBlock(p.set(x, t, z), surface, FLAGS);
        } else if (g < t) {
            // lo que había arriba del suelo viejo (pasto, flores) se pierde bajo el relleno
            for (int y = g + 1; y < t; y++) level.setBlock(p.set(x, y, z), fill, FLAGS);
            level.setBlock(p.set(x, t, z), surface, FLAGS);
            BlockState above = level.getBlockState(p.set(x, t + 1, z));
            if (!above.isAir() && above.canBeReplaced()) level.setBlock(p, air, FLAGS);
        }
        seal(level, p, x, z, t, desert, wg);
    }

    /** Tala árboles en las columnas cercanas a edificios o calles. */
    public static void clearTrees(WorldGenLevel level, int[] box, List<VillageLayout.Building> buildings, List<VillageLayout.Road> roads, boolean wg) {
        clearTrees(level, box, buildings, roads, buildings, wg);
    }

    private static int top(WorldGenLevel level, VillageLayout.Building b) {
        return VillageLayout.template(level.getLevel(), b.template()).t().maxY();
    }

    /** {@code protect}: edificios cuyos troncos y hojas no se tocan (solo se tala por encima de su techo). */
    public static void clearTrees(WorldGenLevel level, int[] box, List<VillageLayout.Building> buildings, List<VillageLayout.Road> roads,
                                  List<VillageLayout.Building> protect, boolean wg) {
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
                // lo que está dentro de un edificio (marcos de troncos, bordes de granjas) no es un árbol: solo por encima
                int from = g + 1;
                for (VillageLayout.Building b : protect) {
                    if (b.contains(x, z, 0)) from = Math.max(from, b.floorY() + top(level, b) + 1);
                }
                for (int y = g + 40; y >= from; y--) {
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
        pave(level, box, roads, buildings, desert, wg, stone, null);
    }

    /** {@code field}: terrazas del asentamiento; la calle va sobre la terraza (y sube los taludes de a un bloque). */
    public static void pave(WorldGenLevel level, int[] box, List<VillageLayout.Road> roads, List<VillageLayout.Building> buildings,
                            boolean desert, boolean wg, boolean stone, TerraceField field) {
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
                    // el umbral de la puerta queda bajo el alero pero es calle: se pavimenta
                    if (b.contains(x, z, 0) && !(x == b.coreX() && z == b.coreZ()) && VillageLayout.footprint(level.getLevel(), b, x, z)) {
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
                if (field != null) {
                    int l = field.level(x, z);
                    if (l != TerraceField.NONE) y = l;
                }
                long h = hash(x, z);
                if (lava(top)) {
                    // lava: se tapa con piedra y la calle pasa por encima como por suelo firme (nunca un puente de madera)
                    douse(level, p, x, z, g - 10, g + 1, desert);
                    top = level.getBlockState(p.set(x, g, z));
                }
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
                if (y - g > 8) y = g + 8;
                if (g - y > 9) y = g - 9;
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
                seal(level, p, x, z, y, desert, wg);
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
        yard(level, b, box, roads, List.of(), desert, wg);
    }

    /** {@code others}: edificios vecinos, cuyas columnas no se tocan (el patio de uno no le corta el techo al otro). */
    public static void yard(WorldGenLevel level, VillageLayout.Building b, int[] box, List<VillageLayout.Road> roads,
                            List<VillageLayout.Building> others, boolean desert, boolean wg) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState fill = (desert ? Blocks.SANDSTONE : Blocks.DIRT).defaultBlockState();
        BlockState topBlock = (desert ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState();
        int m = 3;
        // primero se apaga la lava y el fuego alrededor del lote (con margen: la lava enciende la madera a distancia)
        for (int x = Math.max(box[0], b.minX() - 5); x <= Math.min(box[2], b.maxX() + 5); x++) {
            for (int z = Math.max(box[1], b.minZ() - 5); z <= Math.min(box[3], b.maxZ() + 5); z++) {
                if (!wg && level instanceof ServerLevel sl && !sl.hasChunk(x >> 4, z >> 4)) continue;
                douse(level, p, x, z, b.floorY() - 8, b.floorY() + 4, desert);
            }
        }
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
                boolean neighbour = false;
                for (VillageLayout.Building o : others) {
                    if (o != b && o.contains(x, z, 1)) {
                        neighbour = true;
                        break;
                    }
                }
                if (neighbour) continue;
                int k = Math.max(Math.max(b.minX() - x, x - b.maxX()), Math.max(b.minZ() - z, z - b.maxZ()));
                k = Math.max(0, k);
                int g = ground(level, x, z, wg);
                BlockState top = level.getBlockState(p.set(x, g, z));
                if (!top.getFluidState().isEmpty()) continue;
                if (!wg && !natural(top)) continue;
                // patio plano hasta 2 bloques del edificio; en el borde, una pendiente suave de tierra hacia el suelo de la
                // terraza (sin muros de contención: el acceso de la puerta y el patio quedan abiertos a la calle)
                int target = k < m ? b.floorY() : b.floorY() + Math.max(-2, Math.min(2, g - b.floorY()));
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
                    for (int y = Math.max(g + 1, target - 10); y < target; y++) level.setBlock(p.set(x, y, z), fill, FLAGS);
                    level.setBlock(p.set(x, target, z), topBlock, FLAGS);
                }
                if (k < m) seal(level, p, x, z, target, desert, wg);
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

    /**
     * Demuele un edificio: saca sus bloques (los que la plantilla puso, sin tocar lo que no es suyo) y deja el piso como
     * suelo natural. Los cimientos quedan enterrados.
     */
    public static void demolish(WorldGenLevel level, ServerLevel server, VillageLayout.Building b, boolean desert) {
        VillageLayout.VTemplate vt = VillageLayout.template(server, b.template());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState top = (desert ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState();
        BlockState fill = (desert ? Blocks.SANDSTONE : Blocks.DIRT).defaultBlockState();
        List<DominionTemplates.Entry> blocks = vt.t().blocks();
        // de arriba hacia abajo, así no quedan cosas colgando
        for (int i = blocks.size() - 1; i >= 0; i--) {
            DominionTemplates.Entry e = blocks.get(i);
            int[] w = DominionTemplates.rotate(e.x(), e.z(), b.rot());
            p.set(b.x() + w[0], b.floorY() + e.y(), b.z() + w[1]);
            if (e.y() > 0) {
                if (!level.getBlockState(p).isAir()) level.setBlock(p, air, FLAGS);
            } else if (e.y() == 0) {
                level.setBlock(p, top, FLAGS);
            } else {
                level.setBlock(p, fill, FLAGS);   // pozo de un aljibe: se rellena
            }
        }
        if (level instanceof ServerLevel sl) {
            sl.levelEvent(2001, new BlockPos(b.coreX(), b.floorY() + 1, b.coreZ()), Block.getId(Blocks.STONE_BRICKS.defaultBlockState()));
        }
    }

    /** Altura del muro sobre su base y alto de las torres. */
    public static final int WALL_H = 6, TOWER_H = 12;

    /**
     * Un tramo de muralla: 3 de ancho y {@link #WALL_H} de alto sobre un perfil suave (la base viene planificada en
     * {@code seg.y0/y1} en medios bloques, sacada del terreno natural y suavizada, así el borde de arriba no copia cada
     * loma), adarve parejo con medias losas donde sube, almenas del lado de afuera, faroles sobre las almenas y antorchas
     * en la cara de adentro. Si {@code tower}, una torre transitable en su arranque. Donde cruza una calle deja un portón
     * de 5 de alto con torres a los lados; sobre el agua queda un arco. No pisa edificios ni construcciones del jugador.
     */
    public static void wall(ServerLevel level, VillageLayout.Road seg, int cx, int cz, boolean tower, List<VillageLayout.Road> streets,
                            List<VillageLayout.Building> buildings, boolean desert) {
        BlockState body = (desert ? Blocks.CUT_SANDSTONE : Blocks.STONE_BRICKS).defaultBlockState();
        BlockState body2 = (desert ? Blocks.SANDSTONE : Blocks.CRACKED_STONE_BRICKS).defaultBlockState();
        BlockState cap = (desert ? Blocks.SMOOTH_SANDSTONE : Blocks.POLISHED_ANDESITE).defaultBlockState();
        BlockState slab = (desert ? Blocks.SMOOTH_SANDSTONE_SLAB : Blocks.STONE_BRICK_SLAB).defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        double len = seg.length();
        if (len < 0.5) return;
        double dx = (seg.x1() - seg.x0()) / len, dz = (seg.z1() - seg.z0()) / len;
        double mx = (seg.x0() + seg.x1()) / 2 - cx, mz = (seg.z0() + seg.z1()) / 2 - cz;
        double ox = -dz, oz = dx;   // hacia afuera de la ciudad
        if (ox * mx + oz * mz < 0) {
            ox = -ox;
            oz = -oz;
        }
        double b0, b1;
        if (seg.y0() == Integer.MIN_VALUE) {
            // muralla planificada antes del perfil: base plana a la altura del suelo en el medio del tramo
            b0 = b1 = ground(level, (int) Math.round((seg.x0() + seg.x1()) / 2), (int) Math.round((seg.z0() + seg.z1()) / 2), false);
        } else {
            b0 = seg.y0() / 2.0;
            b1 = seg.y1() / 2.0;
        }
        if (tower) tower(level, seg.x0(), seg.z0(), (int) Math.round(b0), dx, dz, ox, oz, streets, buildings, desert);
        Direction inward = Direction.getNearest(-ox, 0, -oz);
        BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, inward);
        java.util.Set<Long> done = new java.util.HashSet<>();
        boolean wasGate = false;
        for (double t = 0; t <= len; t += 0.25) {
            double base = b0 + (b1 - b0) * t / len;
            int h2 = (int) Math.round(base * 2);
            int full = Math.floorDiv(h2, 2);
            boolean half = (h2 & 1) == 1;
            int top = full + WALL_H;
            int x0 = (int) Math.round(seg.x0() + dx * t), z0 = (int) Math.round(seg.z0() + dz * t);
            VillageLayout.Road street = null;
            for (VillageLayout.Road r : streets) {
                if (r.dist(x0, z0) > r.half() + 0.6) continue;
                double rl = r.length();
                // una calle que corre a lo largo de la muralla no la abre: solo las que la cruzan
                if (rl > 0.5 && Math.abs(((r.x1() - r.x0()) * dx + (r.z1() - r.z0()) * dz) / rl) > 0.87) continue;
                street = r;
            }
            boolean gateHere = street != null;
            // torres a los dos lados de cada puerta
            if (gateHere != wasGate && t > 0) {
                double tt = gateHere ? t - 4.5 : t + 4;
                tower(level, seg.x0() + dx * tt, seg.z0() + dz * tt, full, dx, dz, ox, oz, streets, buildings, desert);
            }
            wasGate = gateHere;
            boolean light = Math.abs(t - Math.round(t / 6.0) * 6.0) < 0.13;
            boolean torchHere = Math.abs(t - 3 - Math.round((t - 3) / 8.0) * 8.0) < 0.13;
            for (double off = -1; off <= 1.01; off += 0.5) {
                int row = off <= -0.75 ? -1 : off >= 0.75 ? 1 : 0;   // -1 adentro, 1 afuera
                int x = (int) Math.round(seg.x0() + dx * t + ox * off), z = (int) Math.round(seg.z0() + dz * t + oz * off);
                if (!done.add(key(x, z))) continue;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                boolean inside = false;
                for (VillageLayout.Building b : buildings) if (b.contains(x, z, 1)) inside = true;
                if (inside) continue;
                int g = ground(level, x, z, false);
                BlockState gs = level.getBlockState(p.set(x, g, z));
                // un farol de calle en el trazado: se saca (si no, la muralla quedaba con un hueco)
                while (gs.is(Blocks.SPRUCE_FENCE) || gs.is(Blocks.SANDSTONE_WALL) || gs.is(Blocks.LANTERN)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                    g = ground(level, x, z, false);
                    gs = level.getBlockState(p.set(x, g, z));
                }
                if (lava(gs)) {
                    douse(level, p, x, z, g - 6, g + 1, desert);
                    gs = level.getBlockState(p.set(x, g, z));
                }
                boolean water = !gs.getFluidState().isEmpty();
                if (!water && !natural(gs) && !isPath(gs) && !isStone(gs) && !isWall(gs)) continue;   // obra del jugador
                if (isWall(gs) && g > full) g = full;   // muro ya levantado (unión de tramos): no es suelo
                if (gateHere) {
                    // portón: paso libre (4 o 5 de alto) al nivel de la calle, arco y adarve encima, a la altura del muro
                    int want = street.y(x, z);
                    int floor = want == Integer.MIN_VALUE ? g : want - 1;
                    int arch = Math.max(top, floor + 5);
                    for (int y = floor + 1; y < arch; y++) {
                        BlockState st = level.getBlockState(p.set(x, y, z));
                        if (!st.isAir() && (natural(st) || isWall(st) || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS))) level.setBlock(p, air, FLAGS);
                    }
                    for (int y = floor + 6; y <= arch; y++) level.setBlock(p.set(x, y, z), row == 0 && y == arch ? cap : body, FLAGS);
                    if (row == 1 && (x + z) % 2 == 0) level.setBlock(p.set(x, arch + 1, z), body, FLAGS);
                    continue;
                }
                // sobre el agua, un arco: el muro arranca 3 por encima del agua y el río sigue pasando
                int from = water ? Math.max(g + 3, full + 1) : Math.min(g, full) + 1;
                for (int y = from; y <= top; y++) {
                    level.setBlock(p.set(x, y, z), y == top && row == 0 ? cap : hash(x, z + y) % 6 == 0 ? body2 : body, FLAGS);
                }
                int walk = top + 1;
                if (half && row <= 0) {
                    level.setBlock(p.set(x, top + 1, z), slab, FLAGS);   // media losa: el adarve sube de a medio bloque
                }
                if (row == 1) {
                    // parapeto con almenas del lado de afuera
                    int py = half ? top + 1 : top;
                    if (half) level.setBlock(p.set(x, py, z), body, FLAGS);
                    boolean merlon = (x + z) % 2 == 0;
                    if (merlon) level.setBlock(p.set(x, py + 1, z), body, FLAGS);
                    if (light) level.setBlock(p.set(x, py + (merlon ? 2 : 1), z), Blocks.LANTERN.defaultBlockState(), FLAGS);
                    walk = py + 2;
                } else if (half) {
                    walk = top + 2;
                }
                // despejar encima del adarve (lomas, árboles)
                for (int y = walk; y <= walk + 3; y++) {
                    BlockState st = level.getBlockState(p.set(x, y, z));
                    if (st.isAir() || st.is(Blocks.LANTERN)) continue;
                    if (natural(st) || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS)) level.setBlock(p, air, FLAGS);
                }
                // antorchas en la cara de adentro
                if (row == -1 && torchHere && !water) {
                    int ix = x + (int) Math.round(-ox), iz = z + (int) Math.round(-oz);
                    if (level.getBlockState(p.set(ix, full + 3, iz)).isAir()) level.setBlock(p, torch, FLAGS);
                }
            }
        }
    }

    /** Puerta abierta después en un tramo ya levantado: se vacía el paso (5 de alto) y queda el arco encima. */
    public static void gate(ServerLevel level, VillageLayout.Road seg, VillageLayout.Road road, boolean desert) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        double len = seg.length();
        if (len < 0.5) return;
        double dx = (seg.x1() - seg.x0()) / len, dz = (seg.z1() - seg.z0()) / len;
        // solo si la calle cruza la muralla: una calle que corre pegada a lo largo no la abre
        double rl = road.length();
        if (rl > 0.5 && Math.abs(((road.x1() - road.x0()) * dx + (road.z1() - road.z0()) * dz) / rl) > 0.87) return;
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (double t = 0; t <= len; t += 0.25) {
            for (double off = -1; off <= 1.01; off += 0.5) {
                int x = (int) Math.round(seg.x0() + dx * t - dz * off), z = (int) Math.round(seg.z0() + dz * t + dx * off);
                if (!done.add(key(x, z)) || road.dist(x, z) > road.half() + 0.6) continue;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int g = ground(level, x, z, false);
                BlockState topState = level.getBlockState(p.set(x, g, z));
                int base = g;
                while (base > g - 12 && isWall(level.getBlockState(p.set(x, base, z)))) base--;
                if (base == g) continue;   // acá no había muralla
                int want = road.y(x, z);
                int floor = want == Integer.MIN_VALUE ? base : Math.max(base, want - 1);
                // paso de 4 de alto; el arco queda siempre (si la calle va alta, se levanta encima del paso)
                for (int y = floor + 1; y <= floor + 4; y++) {
                    if (isWall(level.getBlockState(p.set(x, y, z)))) level.setBlock(p, air, FLAGS);
                }
                for (int y = floor + 5; y <= Math.max(g, floor + 5); y++) {
                    if (level.getBlockState(p.set(x, y, z)).isAir()) level.setBlock(p, isWall(topState) ? topState : rock(desert), FLAGS);
                }
            }
        }
    }

    static boolean isWall(BlockState st) {
        return st.is(Blocks.STONE_BRICKS) || st.is(Blocks.CRACKED_STONE_BRICKS) || st.is(Blocks.POLISHED_ANDESITE)
                || st.is(Blocks.CUT_SANDSTONE) || st.is(Blocks.SANDSTONE) || st.is(Blocks.SMOOTH_SANDSTONE)
                || st.is(Blocks.STONE_BRICK_SLAB) || st.is(Blocks.SMOOTH_SANDSTONE_SLAB) || st.is(Blocks.STONE_BRICK_STAIRS)
                || st.is(Blocks.SANDSTONE_STAIRS);
    }

    /**
     * Torre de muralla de 7×7, hueca y transitable: puerta del lado de la ciudad, escalera de piedra en caracol hasta el
     * piso del adarve (con salidas al adarve a los dos lados), escalera de mano hasta la terraza almenada y faroles
     * adentro y arriba. {@code base}: altura del suelo (la del muro). No se levanta sobre calles ni edificios.
     */
    private static void tower(ServerLevel level, double cxd, double czd, int base, double dx, double dz, double ox, double oz,
                              List<VillageLayout.Road> streets, List<VillageLayout.Building> buildings, boolean desert) {
        BlockState body = (desert ? Blocks.CUT_SANDSTONE : Blocks.STONE_BRICKS).defaultBlockState();
        BlockState cap = (desert ? Blocks.SMOOTH_SANDSTONE : Blocks.POLISHED_ANDESITE).defaultBlockState();
        BlockState deckBlock = (desert ? Blocks.SMOOTH_SANDSTONE : Blocks.SPRUCE_PLANKS).defaultBlockState();
        net.minecraft.world.level.block.Block stairBlock = desert ? Blocks.SANDSTONE_STAIRS : Blocks.STONE_BRICK_STAIRS;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int tx = (int) Math.round(cxd), tz = (int) Math.round(czd);
        if (!level.hasChunk((tx - 4) >> 4, (tz - 4) >> 4) || !level.hasChunk((tx + 4) >> 4, (tz + 4) >> 4)) return;
        int h = 3;
        for (VillageLayout.Road r : streets) if (r.dist(tx, tz) <= r.half() + h + 1) return;
        for (VillageLayout.Building b : buildings) if (b.contains(tx, tz, h + 2)) return;
        int B = base, deck = B + WALL_H, roof = B + TOWER_H;
        // casco: cimiento, muros, piso del adarve, techo y almenas
        for (int x = tx - h; x <= tx + h; x++) {
            for (int z = tz - h; z <= tz + h; z++) {
                boolean ring = Math.abs(x - tx) == h || Math.abs(z - tz) == h;
                int g = ground(level, x, z, false);
                BlockState gs = level.getBlockState(p.set(x, g, z));
                if (!gs.getFluidState().isEmpty() && !lava(gs)) g = Math.min(g, B);
                if (!natural(gs) && !isWall(gs) && !isPath(gs) && !isStone(gs) && gs.getFluidState().isEmpty()) continue;
                if (isWall(gs) && g > B) g = B;
                for (int y = Math.min(g, B) + 1; y <= roof; y++) {
                    BlockState st;
                    if (y <= B || ring || y == roof) st = body;
                    else if (y == deck) st = deckBlock;
                    else st = air;
                    level.setBlock(p.set(x, y, z), st, FLAGS);
                }
                for (int y = roof + 1; y <= roof + 3; y++) level.setBlock(p.set(x, y, z), air, FLAGS);
                if (ring) level.setBlock(p.set(x, roof + 1, z), (x + z) % 2 == 0 ? body : cap, FLAGS);
            }
        }
        // ejes: hacia afuera (o) y a lo largo del muro (d), en la dirección dominante
        int sx = Math.abs(ox) >= Math.abs(oz) ? (int) Math.signum(ox) : 0, sz = sx == 0 ? (int) Math.signum(oz) : 0;
        int ax = Math.abs(dx) >= Math.abs(dz) ? (int) Math.signum(dx) : 0, az = ax == 0 ? (int) Math.signum(dz) : 0;
        if (ax == sx && az == sz) { ax = -sz; az = sx; }
        // puerta del lado de la ciudad (2 de alto) y salidas al adarve a los dos lados
        for (int y = B + 1; y <= B + 2; y++) level.setBlock(p.set(tx - sx * h, y, tz - sz * h), air, FLAGS);
        for (int k = -1; k <= 1; k += 2) {
            for (int y = deck + 1; y <= deck + 2; y++) level.setBlock(p.set(tx + ax * h * k, y, tz + az * h * k), air, FLAGS);
        }
        // anillo interior (5×5) empezando por la celda de la entrada
        List<int[]> ringCells = new java.util.ArrayList<>();
        int r = h - 1;
        for (int i = -r; i < r; i++) ringCells.add(new int[]{tx + i, tz - r});
        for (int i = -r; i < r; i++) ringCells.add(new int[]{tx + r, tz + i});
        for (int i = r; i > -r; i--) ringCells.add(new int[]{tx + i, tz + r});
        for (int i = r; i > -r; i--) ringCells.add(new int[]{tx - r, tz + i});
        int ex = tx - sx * r, ez = tz - sz * r, start = 0;
        for (int i = 0; i < ringCells.size(); i++) if (ringCells.get(i)[0] == ex && ringCells.get(i)[1] == ez) start = i;
        int n = ringCells.size();
        int steps = WALL_H;   // de B+1 a B+6 (el último, al nivel del piso del adarve)
        for (int k = 1; k <= steps; k++) {
            int[] c = ringCells.get((start + k) % n), nx = ringCells.get((start + k + 1) % n);
            int y = B + k;
            Direction face = Direction.getNearest(nx[0] - c[0], 0, nx[1] - c[1]);
            level.setBlock(p.set(c[0], y, c[1]), stairBlock.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, face), FLAGS);
            for (int yy = y + 1; yy <= y + 3 && yy < roof; yy++) level.setBlock(p.set(c[0], yy, c[1]), air, FLAGS);
        }
        // llegada al piso del adarve, libre
        int[] arrive = ringCells.get((start + steps + 1) % n);
        for (int yy = deck + 1; yy <= deck + 3; yy++) level.setBlock(p.set(arrive[0], yy, arrive[1]), air, FLAGS);
        // escalera de mano a la terraza, en la esquina opuesta a la llegada, contra el muro
        int[] lc = ringCells.get((start + n / 2 + steps / 2 + 2) % n);
        int wx = lc[0] - tx, wz = lc[1] - tz;
        Direction away = Math.abs(wx) >= Math.abs(wz) ? Direction.getNearest(-Math.signum(wx), 0, 0) : Direction.getNearest(0, 0, -Math.signum(wz));
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, away);
        for (int y = deck + 1; y <= roof; y++) level.setBlock(p.set(lc[0], y, lc[1]), ladder, FLAGS);
        // luces: bajo el piso del adarve, bajo el techo y arriba
        BlockState hang = Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
        level.setBlock(p.set(tx, deck - 1, tz), hang, FLAGS);
        level.setBlock(p.set(tx, roof - 1, tz), hang, FLAGS);
        level.setBlock(p.set(tx, roof + 1, tz), Blocks.LANTERN.defaultBlockState(), FLAGS);
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
