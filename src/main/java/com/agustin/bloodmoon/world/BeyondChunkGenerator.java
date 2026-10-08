package com.agustin.bloodmoon.world;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.world.design.BeyondDesign;
import com.agustin.bloodmoon.world.design.Pal;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Generador del Más Allá: llanura de losas sobre el vacío, obeliscos y círculos de pelea ({@link BeyondDesign}). */
public class BeyondChunkGenerator extends ChunkGenerator {
    public static final MapCodec<BeyondChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, BeyondChunkGenerator::new));

    private static final ResourceLocation SEED_KEY = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "beyond");
    private volatile BeyondDesign design;
    private volatile long designSeed;

    public BeyondChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    public BeyondDesign design(RandomState random) {
        long seed = random.getOrCreateRandomFactory(SEED_KEY).at(0, 0, 0).nextLong();
        BeyondDesign d = design;
        if (d == null || designSeed != seed) {
            synchronized (this) {
                if (design == null || designSeed != seed) {
                    design = new BeyondDesign(seed);
                    designSeed = seed;
                }
                d = design;
            }
        }
        return d;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        BeyondDesign d = design(randomState);
        ChunkPos cp = chunk.getPos();
        int[] col = new int[BeyondDesign.HEIGHT];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                d.fillColumn(cp.getMinBlockX() + lx, cp.getMinBlockZ() + lz, col);
                for (int y = Math.max(0, minY); y < Math.min(BeyondDesign.HEIGHT, maxY); y++) {
                    int code = col[y];
                    if (code == Pal.AIR) continue;
                    BlockState s = DesignBlocks.state(code);
                    pos.set(lx, y, lz);
                    chunk.setBlockState(pos, s, false);
                    ocean.update(lx, y, lz, s);
                    surface.update(lx, y, lz, s);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
    }

    @Override
    public int getGenDepth() {
        return BeyondDesign.HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return -63;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int[] col = new int[BeyondDesign.HEIGHT];
        design(random).fillColumn(x, z, col);
        for (int y = BeyondDesign.HEIGHT - 1; y >= 0; y--) {
            if (col[y] != Pal.AIR && type.isOpaque().test(DesignBlocks.state(col[y]))) return y + 1;
        }
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int[] col = new int[BeyondDesign.HEIGHT];
        design(random).fillColumn(x, z, col);
        int min = level.getMinBuildHeight();
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = min + i;
            states[i] = y >= 0 && y < BeyondDesign.HEIGHT && col[y] != Pal.AIR
                    ? DesignBlocks.state(col[y]) : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(min, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Más Allá de la Grieta");
    }
}
