package com.agustin.bloodmoon.human;

import com.agustin.bloodmoon.entity.ModEntities;
import com.agustin.bloodmoon.invasion.DominionTemplates;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Map;
import java.util.Optional;

/** Construye, chunk por chunk, la parte de cada aldea humana que le toca: edificios, calles, faroles y habitantes. */
public class VillageFeature extends Feature<NoneFeatureConfiguration> {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public VillageFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        ServerLevel server = level.getLevel();
        if (server.dimension() != Level.OVERWORLD) return false;
        ChunkPos cp = new ChunkPos(ctx.origin());
        int size = VillageSites.REGION;
        int rx = Math.floorDiv(cp.x, size), rz = Math.floorDiv(cp.z, size);
        boolean any = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Optional<VillageSites.Site> site = VillageSites.site(server, rx + dx, rz + dz);
                if (site.isEmpty()) continue;
                VillageSites.Site s = site.get();
                int r = VillageLayout.RADIUS;
                if (cp.getMaxBlockX() < s.x() - r || cp.getMinBlockX() > s.x() + r
                        || cp.getMaxBlockZ() < s.z() - r || cp.getMinBlockZ() > s.z() + r) continue;
                build(level, server, cp, VillageLayout.get(server, s));
                any = true;
            }
        }
        return any;
    }

    private static boolean passable(BlockState st) {
        return st.isAir() || !st.getFluidState().isEmpty() || st.canBeReplaced() || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS)
                || st.is(Blocks.SNOW) || st.is(Blocks.CACTUS) || st.is(Blocks.BAMBOO);
    }

    /** Altura del suelo real (sin árboles ni plantas): la y del bloque sólido o del agua en la superficie. */
    private static int ground(WorldGenLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
        int min = level.getMinBuildHeight();
        for (int k = 0; k < 40 && p.getY() > min; k++) {
            BlockState st = level.getBlockState(p);
            if (!st.getFluidState().isEmpty()) return p.getY();
            if (!passable(st)) return p.getY();
            p.move(0, -1, 0);
        }
        return p.getY();
    }

    private static void build(WorldGenLevel level, ServerLevel server, ChunkPos cp, VillageLayout.Layout layout) {
        boolean desert = layout.site().culture() == Culture.DESERT;
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ(), x1 = cp.getMaxBlockX(), z1 = cp.getMaxBlockZ();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState found = (desert ? Blocks.SANDSTONE : Blocks.COBBLESTONE).defaultBlockState();

        // calles y plaza (antes que los edificios: lo que pisa un edificio lo tapa el edificio)
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean road = false;
                for (VillageLayout.Road r : layout.roads()) {
                    if (r.dist(x, z) <= r.half()) {
                        road = true;
                        break;
                    }
                }
                if (!road) continue;
                boolean inside = false;
                for (VillageLayout.Building b : layout.buildings()) if (b.contains(x, z, 0)) inside = true;
                if (inside) continue;
                int y = ground(level, x, z);
                p.set(x, y, z);
                BlockState top = level.getBlockState(p);
                long h = hash(x, z);
                if (!top.getFluidState().isEmpty()) {
                    level.setBlock(p, (desert ? Blocks.SMOOTH_SANDSTONE : Blocks.SPRUCE_PLANKS).defaultBlockState(), FLAGS);
                } else {
                    BlockState path;
                    if (desert) path = (h % 5 == 0 ? Blocks.SANDSTONE : h % 7 == 0 ? Blocks.CUT_SANDSTONE : Blocks.SMOOTH_SANDSTONE).defaultBlockState();
                    else path = (h % 9 == 0 ? Blocks.GRAVEL : h % 13 == 0 ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH).defaultBlockState();
                    level.setBlock(p, path, FLAGS);
                }
                for (int k = 1; k <= 3; k++) {
                    p.set(x, y + k, z);
                    BlockState st = level.getBlockState(p);
                    if (!st.isAir() && passable(st) && st.getFluidState().isEmpty()) level.setBlock(p, air, FLAGS);
                }
            }
        }

        // edificios
        for (VillageLayout.Building b : layout.buildings()) {
            if (b.maxX() < x0 || b.minX() > x1 || b.maxZ() < z0 || b.minZ() > z1) continue;
            VillageLayout.VTemplate vt = VillageLayout.template(server, b.template());
            int fy = b.floorY();
            // despejar y cimentar por columna
            for (Map.Entry<Long, int[]> col : vt.columns().entrySet()) {
                int tx = (int) (col.getKey() >> 32), tz = (int) (long) col.getKey();
                int[] w = DominionTemplates.rotate(tx, tz, b.rot());
                int x = b.x() + w[0], z = b.z() + w[1];
                if (x < x0 || x > x1 || z < z0 || z > z1) continue;
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
            for (DominionTemplates.Entry e : vt.t().blocks()) {
                int[] w = DominionTemplates.rotate(e.x(), e.z(), b.rot());
                int x = b.x() + w[0], z = b.z() + w[1];
                if (x < x0 || x > x1 || z < z0 || z > z1) continue;
                p.set(x, fy + e.y(), z);
                level.setBlock(p, e.state().rotate(b.rot()), FLAGS);
            }
            // habitantes: aparecen con el chunk de la puerta
            if (b.coreX() >= x0 && b.coreX() <= x1 && b.coreZ() >= z0 && b.coreZ() <= z1) {
                for (int i = 0; i < b.residents(); i++) {
                    Human h = ModEntities.HUMAN.get().create(server);
                    if (h == null) continue;
                    int seed = (int) (layout.site().seed() * 31 + b.x() * 7919L + b.z() * 104729L + i * 15485863L);
                    h.prepareForWorldgen();
                    h.moveTo(b.coreX() + 0.5 + (i - 0.5) * 0.6, fy + 1, b.coreZ() + 0.5, b.rot().ordinal() * 90F, 0F);
                    h.setup(seed, b.job(), layout.site().culture());
                    h.setHome(new BlockPos(b.coreX(), fy + 1, b.coreZ()));
                    h.setPersistenceRequired();
                    level.addFreshEntity(h);
                }
            }
        }

        // faroles
        for (int[] l : layout.lamps()) {
            int x = l[0], z = l[1];
            if (x < x0 || x > x1 || z < z0 || z > z1) continue;
            int y = ground(level, x, z);
            p.set(x, y, z);
            if (!level.getBlockState(p).getFluidState().isEmpty()) continue;
            BlockState post = (desert ? Blocks.SANDSTONE_WALL : Blocks.SPRUCE_FENCE).defaultBlockState();
            level.setBlock(p.set(x, y + 1, z), post, FLAGS);
            level.setBlock(p.set(x, y + 2, z), post, FLAGS);
            level.setBlock(p.set(x, y + 3, z), Blocks.LANTERN.defaultBlockState(), FLAGS);
        }
    }

    private static long hash(int x, int z) {
        long h = x * 73856093L ^ z * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return Math.abs(h);
    }
}
