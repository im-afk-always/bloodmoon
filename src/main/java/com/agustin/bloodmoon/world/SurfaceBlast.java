package com.agustin.bloodmoon.world;

import com.agustin.bloodmoon.block.AstralFireBlock;
import com.agustin.bloodmoon.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Explosión que se esparce por la superficie (la Palma del Vacío): un anillo que avanza desde el centro arrasando todo
 * lo que sobresale del piso (obeliscos incluidos), rebajando el suelo en un cuenco poco profundo y dejándolo
 * vitrificado en Piedra del Vacío con llamas astrales. No abre ningún agujero al vacío.
 */
public final class SurfaceBlast {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final List<Task> TASKS = new ArrayList<>();

    private SurfaceBlast() {}

    private static final class Task {
        final ServerLevel level;
        final int cx, cy, cz;
        final float radius, speed;
        float r;

        Task(ServerLevel level, BlockPos c, float radius, float speed) {
            this.level = level; this.cx = c.getX(); this.cy = c.getY(); this.cz = c.getZ();
            this.radius = radius; this.speed = speed;
        }
    }

    /** center: punto en el piso (y = superficie). */
    public static void start(ServerLevel level, BlockPos center, float radius, float speed) {
        TASKS.add(new Task(level, center, radius, speed));
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) tick(level);
    }

    public static void tick(ServerLevel level) {
        if (TASKS.isEmpty()) return;
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.level != level) continue;
            float r1 = Math.min(t.radius, t.r + t.speed);
            ring(t, t.r, r1);
            t.r = r1;
            if (t.r >= t.radius) it.remove();
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
    }

    private static void ring(Task t, float r0, float r1) {
        ServerLevel level = t.level;
        int R = Mth.ceil(r1);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState glass = ModBlocks.VOID_STONE.get().defaultBlockState();
        BlockState fire = ModBlocks.ASTRAL_FIRE.get().defaultBlockState();
        if (fire.hasProperty(AstralFireBlock.ETERNAL)) fire = fire.setValue(AstralFireBlock.ETERNAL, false);
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < r0 || d >= r1) continue;
                int x = t.cx + dx, z = t.cz + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                double k = 1 - d / t.radius;
                int depth = (int) Math.round(1 + 6 * k * k);                       // cuenco poco profundo
                // arrasar lo que sobresale (hasta 60 bloques de alto)
                for (int y = t.cy + 60; y >= t.cy - depth; y--) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || s.getDestroySpeed(level, p) < 0) continue;
                    level.setBlock(p, air, FLAGS);
                }
                // nuevo suelo vitrificado
                p.set(x, t.cy - depth - 1, z);
                BlockState under = level.getBlockState(p);
                if (!under.isAir() && under.getDestroySpeed(level, p) >= 0) {
                    float v = level.random.nextFloat();
                    level.setBlock(p, v < 0.12F ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : v < 0.3F ? Blocks.BLACKSTONE.defaultBlockState() : glass, FLAGS);
                    if (level.random.nextFloat() < 0.04F) level.setBlock(p.above(), fire, Block.UPDATE_ALL);
                }
            }
        }
    }
}
