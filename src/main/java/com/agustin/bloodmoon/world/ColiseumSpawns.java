package com.agustin.bloodmoon.world;

import com.agustin.bloodmoon.entity.ModEntities;
import com.agustin.bloodmoon.entity.VoidSkeleton;
import com.agustin.bloodmoon.world.design.ColiseumDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Spawn natural de Centinelas y Arqueros del Vacío dentro del Coliseo (Overworld), a cualquier hora:
 * como cualquier monstruo, solo donde está oscuro (brillo ≤ 7). De noche aparecen en la arena y las gradas;
 * de día, en las galerías techadas. No se atan a la noche: se desvanecen como un monstruo común al alejarse.
 */
public final class ColiseumSpawns {
    private static final int PERIOD = 60, CAP = 10;

    private ColiseumSpawns() {}

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % PERIOD != 7) return;
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        RandomSource random = level.random;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || random.nextFloat() > 0.4F) continue;
            var site = ColiseumSites.nearest(level, player.blockPosition(), 1);
            if (site.isEmpty()) continue;
            ColiseumSites.Site s = site.get();
            double pdx = player.getX() - s.x(), pdz = player.getZ() - s.z();
            if (pdx * pdx + pdz * pdz > (double) ColiseumDesign.R_OUT * ColiseumDesign.R_OUT) continue;
            int near = level.getEntitiesOfClass(VoidSkeleton.class, player.getBoundingBox().inflate(48, 24, 48)).size();
            if (near >= CAP) continue;
            BlockPos p = findSpot(level, player, s, random);
            if (p == null) continue;
            int group = 1 + random.nextInt(2);
            for (int i = 0; i < group; i++) {
                BlockPos q = i == 0 ? p : p.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                if (!standable(level, q)) continue;
                EntityType<VoidSkeleton> type = random.nextFloat() < 0.4F ? ModEntities.VOID_ARCHER.get() : ModEntities.VOID_SENTINEL.get();
                VoidSkeleton mob = type.create(level);
                if (mob == null) continue;
                mob.moveTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5, random.nextFloat() * 360F, 0F);
                if (!level.noCollision(mob)) continue;
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(q), MobSpawnType.NATURAL, null);
                level.addFreshEntity(mob);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, q.getX() + 0.5, q.getY() + 0.2, q.getZ() + 0.5, 30, 0.3, 0.1, 0.3, 0.1);
            }
        }
    }

    /** Punto oscuro con piso firme a 16-40 bloques del jugador, dentro del coliseo. */
    private static BlockPos findSpot(ServerLevel level, ServerPlayer player, ColiseumSites.Site s, RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double a = random.nextDouble() * Math.PI * 2, d = 16 + random.nextDouble() * 24;
            int x = (int) Math.floor(player.getX() + Math.cos(a) * d), z = (int) Math.floor(player.getZ() + Math.sin(a) * d);
            double dx = x - s.x(), dz = z - s.z();
            if (dx * dx + dz * dz > (double) ColiseumDesign.R_FACADE * ColiseumDesign.R_FACADE) continue;
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            // de arriba hacia abajo cerca de la altura del jugador: gradas, galerías o arena
            int from = Math.min(top, (int) player.getY() + 24), to = Math.max(level.getMinBuildHeight() + 1, (int) player.getY() - 24);
            for (int y = from; y >= to; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (standable(level, p) && level.getMaxLocalRawBrightness(p) <= 7) return p;
            }
        }
        return null;
    }

    private static boolean standable(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)
                && level.getFluidState(p.below()).isEmpty();
    }
}
