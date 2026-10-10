package com.agustin.bloodmoon.human;

import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Optional;

/** Construye, chunk por chunk, la parte de cada aldea humana que le toca: edificios, calles, faroles y habitantes. */
public class VillageFeature extends Feature<NoneFeatureConfiguration> {
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

    private static void build(WorldGenLevel level, ServerLevel server, ChunkPos cp, VillageLayout.Layout layout) {
        boolean desert = layout.site().culture() == Culture.DESERT;
        int[] box = {cp.getMinBlockX(), cp.getMinBlockZ(), cp.getMaxBlockX(), cp.getMaxBlockZ()};
        long key = VillageBuilder.key(layout.site().x(), layout.site().z());
        VillageBuilder.clearTrees(level, box, layout.buildings(), layout.roads(), true);
        VillageBuilder.pave(level, box, layout.roads(), layout.buildings(), desert, true);
        for (VillageLayout.Building b : layout.buildings()) {
            if (b.maxX() < box[0] || b.minX() > box[2] || b.maxZ() < box[1] || b.minZ() > box[3]) continue;
            VillageBuilder.prepare(level, server, b, box, desert);
            VillageBuilder.place(level, server, b, box, 0, Integer.MAX_VALUE);
            if (VillageBuilder.in(box, b.coreX(), b.coreZ())) {
                VillageBuilder.spawnResidents(level, server, b, layout.site().culture(), layout.site().seed(), key, b.residents(), true);
            }
        }
        for (int[] l : layout.lamps()) {
            if (VillageBuilder.in(box, l[0], l[1])) VillageBuilder.lamp(level, l[0], l[1], desert, true);
        }
    }
}
