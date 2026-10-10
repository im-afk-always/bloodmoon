package com.agustin.bloodmoon.human;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.world.ColiseumSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
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
 * Dónde nace una aldea humana. Como los coliseos: una por región (40×40 chunks) en un lugar determinista, solo en
 * biomas habitables (llanura, pradera, sabana, taiga, nevado → cultura de llanura; desierto → cultura del desierto)
 * y sobre terreno seco y razonablemente parejo. Reemplaza a las aldeas vanilla.
 */
public final class VillageSites {
    public static final int REGION = 40, SPREAD = 28;
    private static final int SALT = 470183921;
    private static final int TRIES = 3;
    public static final TagKey<Biome> PLAINS_BIOMES = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "human_village_plains"));
    public static final TagKey<Biome> DESERT_BIOMES = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "human_village_desert"));
    private static final Map<Long, Optional<Site>> CACHE = new ConcurrentHashMap<>();

    private VillageSites() {}

    public record Site(int x, int y, int z, long seed, Culture culture) {
        public BlockPos center() {
            return new BlockPos(x, y, z);
        }
    }

    public static void clear() {
        CACHE.clear();
        VillageLayout.clear();
    }

    public static Optional<Site> site(ServerLevel level, int rx, int rz) {
        long key = (level.getSeed() * 31 + rx) * 0x9E3779B97F4A7C15L + rz;
        Optional<Site> cached = CACHE.get(key);
        if (cached != null) return cached;
        Optional<Site> s = compute(level, rx, rz);
        if (CACHE.size() > 8192) CACHE.clear();
        CACHE.put(key, s);
        return s;
    }

    private static Optional<Site> compute(ServerLevel level, int rx, int rz) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        int sea = level.getSeaLevel();
        WorldgenRandom rnd = new WorldgenRandom(new LegacyRandomSource(0L));
        rnd.setLargeFeatureWithSalt(level.getSeed(), rx, rz, SALT);
        for (int attempt = 0; attempt < TRIES; attempt++) {
            int cx = rx * REGION + rnd.nextInt(SPREAD);
            int cz = rz * REGION + rnd.nextInt(SPREAD);
            int x = cx * 16 + 8, z = cz * 16 + 8;
            Holder<Biome> biome = gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(sea), QuartPos.fromBlock(z), rs.sampler());
            Culture culture;
            if (biome.is(DESERT_BIOMES)) culture = Culture.DESERT;
            else if (biome.is(PLAINS_BIOMES)) culture = Culture.PLAINS;
            else continue;
            int[] hs = new int[9];
            int wet = 0;
            for (int i = 0; i < 9; i++) {
                double a = i * Math.PI * 2 / 8;
                int r = i == 8 ? 0 : 36;
                int sx = x + (int) (Math.cos(a) * r), sz = z + (int) (Math.sin(a) * r);
                int surf = gen.getBaseHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
                int floor = gen.getBaseHeight(sx, sz, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
                hs[i] = surf;
                if (floor < surf || surf <= sea) wet++;
            }
            if (wet > 2 || hs[8] <= sea) continue;
            int[] sorted = hs.clone();
            Arrays.sort(sorted);
            if (sorted[8] - sorted[0] > 16) continue;
            if (nearColiseum(level, x, z)) continue;
            long seed = level.getSeed() ^ ((long) rx * 0x5DEECE66DL) ^ ((long) rz << 24) ^ 0xA11CE;
            return Optional.of(new Site(x, hs[8], z, seed, culture));
        }
        return Optional.empty();
    }

    static boolean nearColiseum(ServerLevel level, int x, int z) {
        int size = ColiseumSites.REGION * 16;
        int rx = Math.floorDiv(x, size), rz = Math.floorDiv(z, size);
        int keep = ColiseumSites.reach() + 120;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Optional<ColiseumSites.Site> s = ColiseumSites.site(level, rx + dx, rz + dz);
                if (s.isPresent()) {
                    long ddx = s.get().x() - x, ddz = s.get().z() - z;
                    if (ddx * ddx + ddz * ddz < (long) keep * keep) return true;
                }
            }
        }
        return false;
    }

    /** Aldea más cercana (busca en un radio de regiones). */
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
            if (best != null && Math.sqrt(bestD) < ring * size) break;
        }
        return Optional.ofNullable(best);
    }
}
