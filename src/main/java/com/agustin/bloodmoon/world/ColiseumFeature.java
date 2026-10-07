package com.agustin.bloodmoon.world;

import com.agustin.bloodmoon.world.design.ColiseumDesign;
import com.agustin.bloodmoon.world.design.Pal;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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

import java.util.Optional;

/**
 * Construye, chunk por chunk, la parte del Coliseo del Vacío que le corresponde. Aplana: rellena
 * cimientos hasta el terreno y despeja todo lo que haya dentro del contorno (incluidas montañas).
 */
public class ColiseumFeature extends Feature<NoneFeatureConfiguration> {
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    public ColiseumFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        ServerLevel server = level.getLevel();
        if (server.dimension() != Level.OVERWORLD) return false;
        ChunkPos cp = new ChunkPos(ctx.origin());
        int size = ColiseumSites.REGION;
        int rx = Math.floorDiv(cp.x, size), rz = Math.floorDiv(cp.z, size);
        boolean any = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Optional<ColiseumSites.Site> site = ColiseumSites.site(server, rx + dx, rz + dz);
                if (site.isEmpty()) continue;
                ColiseumSites.Site s = site.get();
                int reach = ColiseumSites.reach();
                if (cp.getMaxBlockX() < s.x() - reach || cp.getMinBlockX() > s.x() + reach
                        || cp.getMaxBlockZ() < s.z() - reach || cp.getMinBlockZ() > s.z() + reach) continue;
                build(level, cp, s, ctx.random());
                any = true;
            }
        }
        return any;
    }

    private void build(WorldGenLevel level, ChunkPos cp, ColiseumSites.Site site, RandomSource random) {
        ColiseumDesign design = new ColiseumDesign(site.seed());
        int[] col = new int[ColiseumDesign.H_COUNT];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int base = site.y();
        int minY = level.getMinBuildHeight() + 1, maxY = level.getMaxBuildHeight() - 1;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState fill = Blocks.DEEPSLATE.defaultBlockState();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
                int dx = x - site.x(), dz = z - site.z();
                double r = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (r > ColiseumDesign.R_RUBBLE) continue;
                int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                if (!ColiseumDesign.inFootprint(r)) {
                    int rubble = design.rubbleHeight(dx, dz);
                    for (int i = 0; i < rubble; i++) {
                        int y = ground + i;
                        if (y < maxY) level.setBlock(pos.set(x, y, z), DesignBlocks.state(design.rubbleBlock(x, y, z)), FLAGS);
                    }
                    continue;
                }
                design.fillColumn(dx, dz, col);
                // cimientos hasta el terreno
                int bottom = base + ColiseumDesign.MIN_H;
                for (int y = Math.max(minY, ground - 1); y < bottom; y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isSolidRender(level, pos)) level.setBlock(pos, fill, FLAGS);
                }
                // estructura
                for (int i = 0; i < col.length; i++) {
                    int y = bottom + i;
                    if (y < minY || y > maxY) continue;
                    pos.set(x, y, z);
                    int code = col[i];
                    if (code == Pal.AIR) {
                        BlockState cur = level.getBlockState(pos);
                        if (!cur.isAir()) level.setBlock(pos, air, FLAGS);
                    } else if (DesignBlocks.special(code)) {
                        DesignBlocks.placeSpecial(level, pos.immutable(), code, random, DesignBlocks.COLISEUM_LOOT);
                    } else {
                        level.setBlock(pos, DesignBlocks.state(code), FLAGS);
                    }
                }
                // montañas por encima del contorno
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                for (int y = bottom + col.length; y < Math.min(top, maxY); y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, air, FLAGS);
                }
            }
        }
    }
}
