package com.agustin.bloodmoon.world;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.world.design.LabyrinthDesign;
import com.agustin.bloodmoon.world.design.Pal;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
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

/** Generador del Laberinto del Vacío: todo sale de {@link LabyrinthDesign}, columna por columna. */
public class LabyrinthChunkGenerator extends ChunkGenerator {
    public static final MapCodec<LabyrinthChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, LabyrinthChunkGenerator::new));

    private static final ResourceLocation SEED_KEY = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "labyrinth");
    private volatile LabyrinthDesign design;
    private volatile long designSeed;

    public LabyrinthChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    private LabyrinthDesign design(RandomState random) {
        long seed = random.getOrCreateRandomFactory(SEED_KEY).at(0, 0, 0).nextLong();
        LabyrinthDesign d = design;
        if (d == null || designSeed != seed) {
            synchronized (this) {
                if (design == null || designSeed != seed) {
                    design = new LabyrinthDesign(seed);
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
        LabyrinthDesign d = design(randomState);
        ChunkPos cp = chunk.getPos();
        int[] col = new int[LabyrinthDesign.HEIGHT];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                d.fillColumn(cp.getMinBlockX() + lx, cp.getMinBlockZ() + lz, col);
                for (int y = Math.max(0, minY); y < Math.min(LabyrinthDesign.HEIGHT, maxY); y++) {
                    int code = col[y];
                    if (code == Pal.AIR || DesignBlocks.special(code)) continue;
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

    /** Cofres con botín y generadores de monstruos (necesitan entidades de bloque). */
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        LabyrinthDesign d = design(level.getLevel().getChunkSource().randomState());
        ChunkPos cp = chunk.getPos();
        int[] col = new int[LabyrinthDesign.HEIGHT];
        RandomSource random = RandomSource.create(level.getSeed() ^ cp.toLong());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
                d.fillColumn(x, z, col);
                for (int y = 0; y < LabyrinthDesign.HEIGHT; y++) {
                    if (DesignBlocks.special(col[y])) {
                        DesignBlocks.placeSpecial(level, pos.set(x, y, z).immutable(), col[y], random, DesignBlocks.LABYRINTH_LOOT);
                    }
                }
            }
        }
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
        return LabyrinthDesign.HEIGHT;
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
        int[] col = new int[LabyrinthDesign.HEIGHT];
        design(random).fillColumn(x, z, col);
        for (int y = LabyrinthDesign.HEIGHT - 1; y >= 0; y--) {
            if (col[y] != Pal.AIR && type.isOpaque().test(DesignBlocks.state(col[y]))) return y + 1;
        }
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int[] col = new int[LabyrinthDesign.HEIGHT];
        design(random).fillColumn(x, z, col);
        int min = level.getMinBuildHeight();
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = min + i;
            states[i] = y >= 0 && y < LabyrinthDesign.HEIGHT && col[y] != Pal.AIR && !DesignBlocks.special(col[y])
                    ? DesignBlocks.state(col[y]) : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(min, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Laberinto del Vacío");
    }
}
