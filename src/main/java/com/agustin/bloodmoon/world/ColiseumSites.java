package com.agustin.bloodmoon.world;

import com.agustin.bloodmoon.world.design.ColiseumDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dónde hay un Coliseo del Vacío. El coliseo (≈550 bloques con escombros) supera el límite de 128 bloques
 * de las estructuras de Minecraft, así que no es una "structure": cada región de 96×96 chunks tiene a lo
 * sumo uno en un lugar determinista; se descarta si cae en océano, río o playa. Cada chunk construye
 * su parte (ColiseumFeature) y el Compás busca con la misma función.
 */
public final class ColiseumSites {
    public static final int REGION = 96, SPREAD = 56;   // en chunks: separación mínima de 40 chunks
    private static final int SALT = 918273645;
    /** Fracción de regiones con coliseo. */
    private static final float KEEP = 0.4F;
    private static final Map<Long, Optional<Site>> CACHE = new ConcurrentHashMap<>();

    private ColiseumSites() {}

    public record Site(int x, int y, int z, long seed) {
        public BlockPos center() {
            return new BlockPos(x, y, z);
        }
    }

    public static void clear() {
        CACHE.clear();
    }

    public static Optional<Site> site(long worldSeed, ChunkGenerator gen, RandomState rs, LevelHeightAccessor height, int seaLevel,
                                      int rx, int rz) {
        long key = (worldSeed * 31 + rx) * 0x9E3779B97F4A7C15L + rz;
        Optional<Site> cached = CACHE.get(key);
        if (cached != null) return cached;
        Optional<Site> s = compute(worldSeed, gen, rs, height, seaLevel, rx, rz);
        if (CACHE.size() > 4096) CACHE.clear();
        CACHE.put(key, s);
        return s;
    }

    private static Optional<Site> compute(long worldSeed, ChunkGenerator gen, RandomState rs, LevelHeightAccessor height, int seaLevel,
                                          int rx, int rz) {
        WorldgenRandom rnd = new WorldgenRandom(new LegacyRandomSource(0L));
        rnd.setLargeFeatureWithSalt(worldSeed, rx, rz, SALT);
        int cx = rx * REGION + rnd.nextInt(SPREAD);
        int cz = rz * REGION + rnd.nextInt(SPREAD);
        // solo 2 de cada 5 regiones tienen coliseo (se sortea después de la posición: los que quedan no se mueven)
        if (rnd.nextFloat() >= KEEP) return Optional.empty();
        int x = cx * 16 + 8, z = cz * 16 + 8;
        Holder<Biome> biome = gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(64), QuartPos.fromBlock(z), rs.sampler());
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
            return Optional.empty();
        }
        int[] hs = new int[9];
        int wet = 0;
        for (int i = 0; i < 9; i++) {
            double a = i * Math.PI * 2 / 8;
            int r = i == 8 ? 0 : 130;
            int sx = x + (int) (Math.cos(a) * r), sz = z + (int) (Math.sin(a) * r);
            hs[i] = gen.getBaseHeight(sx, sz, Heightmap.Types.OCEAN_FLOOR_WG, height, rs);
            if (hs[i] < seaLevel) wet++;
        }
        if (wet > 3) return Optional.empty();
        Arrays.sort(hs);
        int y = Mth.clamp(hs[4], seaLevel + 2, 150);
        return Optional.of(new Site(x, y, z, worldSeed ^ ((long) rx << 20) ^ rz));
    }

    public static Optional<Site> site(ServerLevel level, int rx, int rz) {
        return site(level.getSeed(), level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), level,
                level.getSeaLevel(), rx, rz);
    }

    /** Coliseo más cercano (busca en un radio de regiones). */
    public static Optional<Site> nearest(ServerLevel level, BlockPos from, int radiusRegions) {
        int size = REGION * 16;
        int rx0 = Math.floorDiv(from.getX(), size), rz0 = Math.floorDiv(from.getZ(), size);
        Site best = null;
        double bestD = Double.MAX_VALUE;
        for (int ring = 0; ring <= radiusRegions; ring++) {
            for (int rx = rx0 - ring; rx <= rx0 + ring; rx++) {
                for (int rz = rz0 - ring; rz <= rz0 + ring; rz++) {
                    if (Math.max(Math.abs(rx - rx0), Math.abs(rz - rz0)) != ring) continue;
                    Optional<Site> s = site(level, rx, rz);
                    if (s.isEmpty()) continue;
                    double d = from.distSqr(s.get().center());
                    if (d < bestD) {
                        bestD = d;
                        best = s.get();
                    }
                }
            }
            if (best != null && Math.sqrt(bestD) < (ring) * size) break;   // ya no puede haber uno más cerca
        }
        return Optional.ofNullable(best);
    }

    /** Radio total afectado (fachada + escombros). */
    public static int reach() {
        return ColiseumDesign.R_RUBBLE + 2;
    }
}
